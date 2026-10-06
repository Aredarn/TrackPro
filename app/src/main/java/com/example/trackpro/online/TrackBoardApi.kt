package com.example.trackpro.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server answered, but not with success. [code] is the problem's stable identifier, if any. */
class ApiException(
    val status: Int,
    message: String,
    val code: String? = null,
) : Exception(message)

/** The server could not be reached, or the configured address is unusable. Worth retrying later. */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The TrackBoard v1 endpoints the app uses. Authenticated calls take the access token
 * explicitly; refreshing it is [AuthRepository]'s job, not the transport's.
 *
 * `put*` methods are idempotent create-or-replace under an app-generated id and return true
 * when the call created the record.
 */
interface TrackBoardApi {
    suspend fun register(request: RegisterRequest): AuthResponse
    suspend fun login(request: LoginRequest): AuthResponse
    suspend fun refresh(refreshToken: String): AuthResponse
    suspend fun logout(accessToken: String, refreshToken: String)

    suspend fun putVehicle(accessToken: String, id: String, body: VehicleWrite): Boolean
    suspend fun putTrack(accessToken: String, id: String, body: TrackWrite): Boolean
    suspend fun putSession(accessToken: String, id: String, body: SessionWrite): Boolean
    /** Succeeds when the session is already gone, too: the goal is that it does not exist. */
    suspend fun deleteSession(accessToken: String, id: String)

    /**
     * Succeeds when already gone. Throws a 409 `TrackInUse` when other drivers have sessions on
     * it — the server keeps it then, because their laps depend on it.
     */
    suspend fun deleteTrack(accessToken: String, id: String)

    /** Null when the track does not exist or is not visible to this caller. */
    suspend fun getTrack(accessToken: String?, id: String): TrackSummary?

    /** Like [getTrack], with the geometry. */
    suspend fun getTrackDetail(accessToken: String, id: String): TrackDetail?

    /** One page of the caller's own tracks, published and private, 1-based. */
    suspend fun listMyTracks(accessToken: String, page: Int, pageSize: Int = 100): TrackPage

    /** One page of the caller's sessions, newest first, 1-based. */
    suspend fun listSessions(accessToken: String, page: Int, pageSize: Int = 100): SessionPage

    /** Null when the session no longer exists. */
    suspend fun getSession(accessToken: String, id: String): SessionDetail?

    /** Null when the car no longer exists or is not the caller's. */
    suspend fun getVehicle(accessToken: String, id: String): VehicleResponse?

    /** Null when the track does not exist or is not published. Public: the token is optional. */
    suspend fun getLeaderboard(accessToken: String?, trackId: String, limit: Int): Leaderboard?
}

/**
 * The profile, garage and photo endpoints. Kept apart from [TrackBoardApi] because they serve
 * the account screens and garage sync, not leaderboard sync, and each side is tested against
 * its own fake.
 */
interface TrackBoardAccountApi {
    suspend fun getProfile(accessToken: String): ProfileResponse
    suspend fun updateProfile(accessToken: String, body: UpdateProfileRequest): ProfileResponse
    suspend fun getStats(accessToken: String): ProfileStats
    /** The raw export document, exactly as the server wrote it. */
    suspend fun exportAccount(accessToken: String): String
    /** Needs the account [password] as well as the token. A wrong one is a 403, not a 401. */
    suspend fun deleteAccount(accessToken: String, password: String)

    /** One page of the caller's vehicles, 1-based. */
    suspend fun listVehicles(accessToken: String, page: Int, pageSize: Int = 100): VehiclePage
    /** Succeeds when already gone. Throws a 409 `VehicleInUse` when uploaded sessions still use it. */
    suspend fun deleteVehicle(accessToken: String, id: String)

    suspend fun createUpload(accessToken: String, body: UploadRequest): UploadTarget
    /** PUTs [bytes] to a signed storage URL. No TrackBoard token: the URL carries its own. */
    suspend fun uploadBytes(url: String, bytes: ByteArray, contentType: String)
    suspend fun setAvatar(accessToken: String, path: String?): ProfileResponse
    suspend fun setVehiclePhoto(accessToken: String, vehicleId: String, path: String?): VehicleResponse
    /** Fetches a public photo. Null when it no longer exists. */
    suspend fun download(url: String): ByteArray?
}

/**
 * Track days: joining with a code and reading an event's live board. Its own interface, like
 * [TrackBoardAccountApi], so the sync fakes in tests do not have to grow with it.
 */
interface TrackBoardEventsApi {
    /** Events the caller hosts or joined, newest first. */
    suspend fun myEvents(accessToken: String): List<EventSummary>

    /** Throws a 404 [ApiException] when no event has the code. */
    suspend fun eventByCode(accessToken: String, code: String): EventDetail

    /** Joins, or changes group when already joined. Throws a 409 when the event has finished. */
    suspend fun joinEvent(accessToken: String, code: String, groupId: String?): EventDetail

    /** Succeeds when already gone. */
    suspend fun leaveEvent(accessToken: String, eventId: String, userId: String)

    /** Public: the token only matters for a stale one, which the server answers with 401. */
    suspend fun eventBoard(accessToken: String?, eventId: String): EventBoard?

    /** A published track with its geometry, for driving an event on it. Public. */
    suspend fun publishedTrack(id: String): TrackDetail?
}

class OkHttpTrackBoardApi(
    /** Read on every call, so a changed server address in Settings applies immediately. */
    private val baseUrl: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) : TrackBoardApi, TrackBoardAccountApi, TrackBoardEventsApi {

    override suspend fun register(request: RegisterRequest): AuthResponse =
        send("POST", "auth/register", null, request, RegisterRequest.serializer(), AuthResponse.serializer())

    override suspend fun login(request: LoginRequest): AuthResponse =
        send("POST", "auth/login", null, request, LoginRequest.serializer(), AuthResponse.serializer())

    override suspend fun refresh(refreshToken: String): AuthResponse =
        send("POST", "auth/refresh", null, RefreshRequest(refreshToken), RefreshRequest.serializer(), AuthResponse.serializer())

    override suspend fun logout(accessToken: String, refreshToken: String) {
        call("POST", "auth/logout", accessToken, encode(RefreshRequest.serializer(), RefreshRequest(refreshToken)))
            .use { it.requireSuccess() }
    }

    override suspend fun putVehicle(accessToken: String, id: String, body: VehicleWrite): Boolean =
        put("vehicles/$id", accessToken, encode(VehicleWrite.serializer(), body))

    override suspend fun putTrack(accessToken: String, id: String, body: TrackWrite): Boolean =
        put("tracks/$id", accessToken, encode(TrackWrite.serializer(), body))

    override suspend fun putSession(accessToken: String, id: String, body: SessionWrite): Boolean =
        put("sessions/$id", accessToken, encode(SessionWrite.serializer(), body))

    override suspend fun deleteSession(accessToken: String, id: String) {
        call("DELETE", "sessions/$id", accessToken, null).use { response ->
            if (response.code != 404) response.requireSuccess()
        }
    }

    override suspend fun deleteTrack(accessToken: String, id: String) {
        call("DELETE", "tracks/$id", accessToken, null).use { response ->
            if (response.code != 404) response.requireSuccess()
        }
    }

    override suspend fun getTrack(accessToken: String?, id: String): TrackSummary? =
        call("GET", "tracks/$id", accessToken, null).use { response ->
            if (response.code == 404) return@use null
            response.requireSuccess()
            json.decodeFromString(TrackSummary.serializer(), response.bodyText())
        }

    override suspend fun getTrackDetail(accessToken: String, id: String): TrackDetail? =
        call("GET", "tracks/$id", accessToken, null).use { response ->
            if (response.code == 404) return@use null
            response.requireSuccess()
            json.decodeFromString(TrackDetail.serializer(), response.bodyText())
        }

    override suspend fun listMyTracks(accessToken: String, page: Int, pageSize: Int): TrackPage =
        get("tracks?mine=true&page=$page&pageSize=$pageSize", accessToken, TrackPage.serializer())

    override suspend fun listSessions(accessToken: String, page: Int, pageSize: Int): SessionPage =
        get("sessions?page=$page&pageSize=$pageSize", accessToken, SessionPage.serializer())

    override suspend fun getSession(accessToken: String, id: String): SessionDetail? =
        call("GET", "sessions/$id", accessToken, null).use { response ->
            if (response.code == 404) return@use null
            response.requireSuccess()
            json.decodeFromString(SessionDetail.serializer(), response.bodyText())
        }

    override suspend fun getVehicle(accessToken: String, id: String): VehicleResponse? =
        call("GET", "vehicles/$id", accessToken, null).use { response ->
            if (response.code == 404 || response.code == 403) return@use null
            response.requireSuccess()
            json.decodeFromString(VehicleResponse.serializer(), response.bodyText())
        }

    override suspend fun getLeaderboard(accessToken: String?, trackId: String, limit: Int): Leaderboard? =
        call("GET", "tracks/$trackId/leaderboard?limit=$limit", accessToken, null).use { response ->
            if (response.code == 404) return@use null
            response.requireSuccess()
            json.decodeFromString(Leaderboard.serializer(), response.bodyText())
        }

    // ── Account ──

    override suspend fun getProfile(accessToken: String): ProfileResponse =
        get("me", accessToken, ProfileResponse.serializer())

    override suspend fun updateProfile(accessToken: String, body: UpdateProfileRequest): ProfileResponse =
        send("PATCH", "me", accessToken, body, UpdateProfileRequest.serializer(), ProfileResponse.serializer())

    override suspend fun getStats(accessToken: String): ProfileStats =
        get("me/stats", accessToken, ProfileStats.serializer())

    override suspend fun exportAccount(accessToken: String): String =
        call("GET", "me/export", accessToken, null).use { response ->
            response.requireSuccess()
            response.bodyText()
        }

    override suspend fun deleteAccount(accessToken: String, password: String) {
        call("DELETE", "me", accessToken, encode(DeleteAccountRequest.serializer(), DeleteAccountRequest(password)))
            .use { it.requireSuccess() }
    }

    override suspend fun listVehicles(accessToken: String, page: Int, pageSize: Int): VehiclePage =
        get("vehicles?page=$page&pageSize=$pageSize", accessToken, VehiclePage.serializer())

    override suspend fun deleteVehicle(accessToken: String, id: String) {
        call("DELETE", "vehicles/$id", accessToken, null).use { response ->
            if (response.code != 404) response.requireSuccess()
        }
    }

    override suspend fun createUpload(accessToken: String, body: UploadRequest): UploadTarget =
        send("POST", "me/uploads", accessToken, body, UploadRequest.serializer(), UploadTarget.serializer())

    override suspend fun uploadBytes(url: String, bytes: ByteArray, contentType: String) {
        val target = url.toHttpUrlOrNull() ?: throw NetworkException("The server returned an unusable upload address.")
        external(Request.Builder().url(target).put(bytes.toRequestBody(contentType.toMediaType())).build()).use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, "Photo storage refused the upload (${response.code}).")
            }
        }
    }

    override suspend fun setAvatar(accessToken: String, path: String?): ProfileResponse =
        if (path == null) {
            call("DELETE", "me/avatar", accessToken, null).use { response ->
                response.requireSuccess()
                json.decodeFromString(ProfileResponse.serializer(), response.bodyText())
            }
        } else {
            send("PUT", "me/avatar", accessToken, SetMediaRequest(path), SetMediaRequest.serializer(), ProfileResponse.serializer())
        }

    override suspend fun setVehiclePhoto(accessToken: String, vehicleId: String, path: String?): VehicleResponse =
        if (path == null) {
            call("DELETE", "vehicles/$vehicleId/photo", accessToken, null).use { response ->
                response.requireSuccess()
                json.decodeFromString(VehicleResponse.serializer(), response.bodyText())
            }
        } else {
            send(
                "PUT", "vehicles/$vehicleId/photo", accessToken,
                SetMediaRequest(path), SetMediaRequest.serializer(), VehicleResponse.serializer()
            )
        }

    override suspend fun download(url: String): ByteArray? {
        val target = url.toHttpUrlOrNull() ?: return null
        return external(Request.Builder().url(target).get().build()).use { response ->
            when {
                response.code == 404 || response.code == 400 -> null
                !response.isSuccessful -> throw NetworkException("Could not download a photo (${response.code}).")
                else -> response.body?.bytes()
            }
        }
    }

    // ── Events ──

    override suspend fun myEvents(accessToken: String): List<EventSummary> =
        get("events", accessToken, ListSerializer(EventSummary.serializer()))

    override suspend fun eventByCode(accessToken: String, code: String): EventDetail =
        get("events/code/${code.filter(Char::isLetterOrDigit)}", accessToken, EventDetail.serializer())

    override suspend fun joinEvent(accessToken: String, code: String, groupId: String?): EventDetail =
        send("POST", "events/join", accessToken, JoinEventRequest(code, groupId), JoinEventRequest.serializer(), EventDetail.serializer())

    override suspend fun leaveEvent(accessToken: String, eventId: String, userId: String) {
        call("DELETE", "events/$eventId/entries/$userId", accessToken, null).use { response ->
            if (response.code != 404) response.requireSuccess()
        }
    }

    override suspend fun eventBoard(accessToken: String?, eventId: String): EventBoard? =
        call("GET", "events/$eventId/board", accessToken, null).use { response ->
            if (response.code == 404) return@use null
            response.requireSuccess()
            json.decodeFromString(EventBoard.serializer(), response.bodyText())
        }

    override suspend fun publishedTrack(id: String): TrackDetail? =
        call("GET", "tracks/$id", null, null).use { response ->
            if (response.code == 404) return@use null
            response.requireSuccess()
            json.decodeFromString(TrackDetail.serializer(), response.bodyText())
        }

    // ── Plumbing ──

    private suspend fun <Res> get(path: String, accessToken: String, serializer: KSerializer<Res>): Res =
        call("GET", path, accessToken, null).use { response ->
            response.requireSuccess()
            json.decodeFromString(serializer, response.bodyText())
        }

    /** A request to a host other than the TrackBoard API: photo storage. Never carries our token. */
    private suspend fun external(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException("Could not reach photo storage.", e)
        }
    }

    private suspend fun put(path: String, accessToken: String, body: String): Boolean =
        call("PUT", path, accessToken, body).use { response ->
            response.requireSuccess()
            response.code == 201
        }

    private suspend fun <Req, Res> send(
        method: String,
        path: String,
        accessToken: String?,
        request: Req,
        requestSerializer: KSerializer<Req>,
        responseSerializer: KSerializer<Res>,
    ): Res = call(method, path, accessToken, encode(requestSerializer, request)).use { response ->
        response.requireSuccess()
        json.decodeFromString(responseSerializer, response.bodyText())
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)

    private suspend fun call(method: String, path: String, accessToken: String?, body: String?): Response =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(resolve(path))
                .apply { if (accessToken != null) header("Authorization", "Bearer $accessToken") }
                .method(method, body?.toRequestBody(JSON))
                .build()
            try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw NetworkException("Could not reach the TrackBoard server.", e)
            }
        }

    private fun resolve(path: String): HttpUrl {
        val configured = baseUrl()?.trim().orEmpty()
        if (configured.isEmpty()) throw NetworkException("No TrackBoard server is set. Add one in Settings.")

        // Joined as strings, not through HttpUrl: HttpUrl normalises "http://host:5000" to
        // "http://host:5000/", which turned every request path into "//api/v1/...". Keeping
        // the configured path also lets a server live under a prefix like https://host/trackboard.
        val base = configured.trimEnd('/')
        return "$base/api/v1/$path".toHttpUrlOrNull()
            ?: throw NetworkException("The TrackBoard server address is not a valid URL.")
    }

    private fun Response.bodyText(): String = body?.string().orEmpty()

    private fun Response.requireSuccess() {
        if (isSuccessful) return
        val problem = runCatching { json.decodeFromString(ProblemDetails.serializer(), bodyText()) }.getOrNull()
        throw ApiException(code, describe(code, problem), problem?.code)
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        /** Unknown fields are expected: the app reads only what it needs from each response. */
        internal val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * A sentence a person can act on. Validation errors name the first failing field;
         * otherwise the server's own detail is used when it sent one.
         */
        internal fun describe(status: Int, problem: ProblemDetails?): String {
            problem?.errors?.entries?.firstOrNull()?.let { (field, messages) ->
                return messages.firstOrNull() ?: "The server rejected $field."
            }
            problem?.detail?.takeIf { it.isNotBlank() }?.let { return it }
            return when (status) {
                401 -> "Not signed in, or the sign-in has expired."
                403 -> "That belongs to another account."
                404 -> "Not found on the server."
                429 -> "Too many attempts. Wait a minute and try again."
                in 500..599 -> "The TrackBoard server had a problem. Try again later."
                else -> "The server refused the request ($status)."
            }
        }
    }
}

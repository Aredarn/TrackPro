package com.example.trackpro.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
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

    /** Null when the track does not exist or is not published. Public: the token is optional. */
    suspend fun getLeaderboard(accessToken: String?, trackId: String, limit: Int): Leaderboard?
}

class OkHttpTrackBoardApi(
    /** Read on every call, so a changed server address in Settings applies immediately. */
    private val baseUrl: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) : TrackBoardApi {

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

    override suspend fun getLeaderboard(accessToken: String?, trackId: String, limit: Int): Leaderboard? =
        call("GET", "tracks/$trackId/leaderboard?limit=$limit", accessToken, null).use { response ->
            if (response.code == 404) return@use null
            response.requireSuccess()
            json.decodeFromString(Leaderboard.serializer(), response.bodyText())
        }

    // ── Plumbing ──

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

package com.example.trackpro.online

import com.example.trackpro.dao.SyncDao
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.TrackPublication
import com.example.trackpro.dataClasses.VehicleInformationData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class InMemoryTokenStore(var session: StoredSession? = null) : TokenStore {
    override fun load() = session
    override fun save(session: StoredSession) { this.session = session }
    override fun clear() { session = null }
}

fun signedInStore(expiresAt: Long = Long.MAX_VALUE / 2) = InMemoryTokenStore(
    StoredSession("access-1", expiresAt, "refresh-1", "user-1", "me@example.com", "Me")
)

fun authResponse(access: String, refresh: String, expiresAt: String = "2099-01-01T00:00:00Z") = AuthResponse(
    accessToken = access,
    expiresAt = expiresAt,
    refreshToken = refresh,
    user = ApiUser("user-1", "me@example.com", "Me", "Driver"),
)

/**
 * A TrackBoard stand-in: records every call and holds server-side state for tracks, so tests
 * can assert on exactly what the app sent. Override a hook to make a call fail.
 */
open class FakeApi : TrackBoardApi {
    val calls = mutableListOf<String>()
    val sessions = mutableMapOf<String, SessionWrite>()
    val tracks = mutableMapOf<String, TrackWrite>()
    val vehicles = mutableMapOf<String, VehicleWrite>()
    /** Tracks that exist on the server but belong to someone else. */
    val foreignTracks = mutableSetOf<String>()
    var onPutSession: (SessionWrite) -> Unit = {}
    var onPutVehicle: (VehicleWrite) -> Unit = {}
    var onDeleteTrack: (String) -> Unit = {}
    var refresh: suspend (String) -> AuthResponse = { authResponse("access-2", "refresh-2") }

    override suspend fun register(request: RegisterRequest) = authResponse("access-1", "refresh-1")
    override suspend fun login(request: LoginRequest) = authResponse("access-1", "refresh-1")
    override suspend fun refresh(refreshToken: String): AuthResponse {
        calls += "refresh $refreshToken"
        return refresh.invoke(refreshToken)
    }
    override suspend fun logout(accessToken: String, refreshToken: String) { calls += "logout" }

    override suspend fun putVehicle(accessToken: String, id: String, body: VehicleWrite): Boolean {
        calls += "PUT vehicle $id"
        onPutVehicle(body)
        return vehicles.put(id, body) == null
    }

    override suspend fun putTrack(accessToken: String, id: String, body: TrackWrite): Boolean {
        calls += "PUT track $id ${body.visibility}"
        if (id in foreignTracks) throw ApiException(403, "That belongs to another account.")
        return tracks.put(id, body) == null
    }

    override suspend fun putSession(accessToken: String, id: String, body: SessionWrite): Boolean {
        calls += "PUT session $id"
        onPutSession(body)
        return sessions.put(id, body) == null
    }

    override suspend fun deleteSession(accessToken: String, id: String) {
        calls += "DELETE session $id"
        sessions.remove(id)
    }

    override suspend fun deleteTrack(accessToken: String, id: String) {
        calls += "DELETE track $id"
        onDeleteTrack(id)
        tracks.remove(id)
    }

    override suspend fun getTrack(accessToken: String?, id: String): TrackSummary? {
        calls += "GET track $id"
        return when {
            id in foreignTracks -> TrackSummary(id, "Shared", ApiTrackVisibility.Published)
            id in tracks -> TrackSummary(id, tracks.getValue(id).name, tracks.getValue(id).visibility)
            else -> null
        }
    }

    override suspend fun getLeaderboard(accessToken: String?, trackId: String, limit: Int): Leaderboard? = null

    fun puts(kind: String) = calls.filter { it.startsWith("PUT $kind") }
}

/** An in-memory [SyncDao] over plain lists, standing in for Room. */
class FakeSyncDao : SyncDao {
    val sessions = mutableListOf<SessionData>()
    val laps = mutableListOf<LapTimeData>()
    val sectors = mutableListOf<SectorTimeData>()
    val tracks = mutableListOf<TrackMainData>()
    val points = mutableListOf<TrackCoordinatesData>()
    val vehicles = mutableListOf<VehicleInformationData>()
    val published = mutableSetOf<Long>()
    val links = mutableMapOf<Pair<String, Long>, RemoteLink>()

    override suspend fun getLink(kind: String, localId: Long) = links[kind to localId]
    override suspend fun getLinks(kind: String) = links.values.filter { it.kind == kind }
    override suspend fun putLink(link: RemoteLink) { links[link.kind to link.localId] = link }
    override suspend fun deleteLink(kind: String, localId: Long) { links.remove(kind to localId) }

    override fun observeIsPublished(trackId: Long): Flow<Boolean> = flowOf(trackId in published)
    override suspend fun getPublishedTrackIds() = published.toList()
    override suspend fun publish(publication: TrackPublication) { published += publication.trackId }
    override suspend fun unpublish(trackId: Long) { published -= trackId }

    override suspend fun getFinishedTrackSessions() =
        sessions.filter { it.trackId != null && it.trackId != -1L && it.endTime != null }
    override suspend fun getSession(id: Long) = sessions.find { it.id == id }
    override suspend fun getLaps(sessionId: Long) = laps.filter { it.sessionid == sessionId }.sortedBy { it.lapnumber }
    override suspend fun getSectors(lapIds: List<Long>) = sectors.filter { it.lapid in lapIds }
    override suspend fun getTrack(trackId: Long) = tracks.find { it.trackId == trackId }
    override suspend fun getTrackPoints(trackId: Long) = points.filter { it.trackId == trackId }.sortedBy { it.id }
    override suspend fun getVehicle(vehicleId: Long) = vehicles.find { it.vehicleId == vehicleId }
}

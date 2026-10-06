package com.example.trackpro.online

import android.content.Context
import com.example.trackpro.dao.SyncDao
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant

/**
 * An event this driver has joined, as the phone remembers it. Enough to decide, offline and
 * mid-session, whether a lap belongs on an event board.
 */
@Serializable
data class JoinedEvent(
    val id: String,
    val name: String,
    /** The server track the event runs on. */
    val trackId: String,
    val trackName: String,
    val startsAtMs: Long,
    val endsAtMs: Long,
    val groupId: String? = null,
) {
    fun covers(atMs: Long) = atMs in startsAtMs..endsAtMs
}

/**
 * Track days on TrackBoard: joining with a code, the list of joined events, and the event
 * board. Joining is the driver's consent to share every lap they drive on the event's track
 * during the event — private or not — so [SyncEngine] reads [joined] to decide what goes up.
 */
class EventRepository(
    context: Context,
    private val api: TrackBoardEventsApi,
    private val auth: AuthRepository,
    private val dao: SyncDao,
    /** The server id of a local track, whichever way it is linked. */
    private val remoteTrackIdFor: suspend (Long) -> String?,
    private val transaction: suspend (suspend () -> Unit) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.getSharedPreferences("trackboard_events", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(JoinedEvent.serializer())

    private val _joined = MutableStateFlow(read())

    /** Joined events, kept on the phone so live uploads work without asking the server first. */
    val joined: StateFlow<List<JoinedEvent>> = _joined.asStateFlow()

    /** The joined event running on [remoteTrackId] at [atMs], if any. */
    fun eventAt(remoteTrackId: String, atMs: Long): JoinedEvent? =
        _joined.value.firstOrNull { it.trackId == remoteTrackId && it.covers(atMs) }

    /** Events the driver hosts or joined, newest first; also refreshes [joined]. */
    suspend fun refresh(): List<EventSummary> {
        val events = auth.authorized { token -> api.myEvents(token) }
        remember(events.filter { it.isJoined }.mapNotNull { it.toJoined() })
        return events
    }

    suspend fun lookUp(code: String): EventDetail = auth.authorized { token -> api.eventByCode(token, code) }

    suspend fun join(code: String, groupId: String?): EventDetail {
        val detail = auth.authorized { token -> api.joinEvent(token, code, groupId) }
        detail.event.toJoined()?.let { joined -> remember(_joined.value.filterNot { it.id == joined.id } + joined) }
        return detail
    }

    suspend fun leave(eventId: String) {
        val userId = (auth.state.value as? AccountState.SignedIn)?.userId ?: return
        auth.authorized { token -> api.leaveEvent(token, eventId, userId) }
        remember(_joined.value.filterNot { it.id == eventId })
    }

    suspend fun board(eventId: String): EventBoard? = api.eventBoard(auth.currentAccessTokenOrNull(), eventId)

    /** Forgets every joined event. Used on sign-out. */
    fun clear() = remember(emptyList())

    // ── The event's track ──

    /** The local track timed against the event's gates, or null when the phone does not have it. */
    suspend fun localTrackFor(remoteTrackId: String): Long? =
        dao.getTracks().firstOrNull { remoteTrackIdFor(it.trackId) == remoteTrackId }?.trackId

    /**
     * Downloads a published track so the driver is timed on exactly the gates everyone else on
     * the board is. Linked as shared: it is never uploaded as the driver's own, and deleting the
     * local copy never touches the server's.
     */
    suspend fun downloadTrack(remoteTrackId: String): Long {
        localTrackFor(remoteTrackId)?.let { return it }
        val detail = api.publishedTrack(remoteTrackId)
            ?: throw SyncProblem("That track is no longer published on TrackBoard.")
        val points = detail.points.sortedBy { it.seq }.map { p ->
            TrackCoordinatesData(
                trackId = 0,
                latitude = p.latitude,
                longitude = p.longitude,
                altitude = p.altitude,
                isStartPoint = p.isStartPoint,
                isSectorPoint = p.isSectorPoint,
                sectorIndex = p.sectorIndex,
            )
        }
        if (points.size < 2) throw SyncProblem("That track has no usable outline.")

        var localId = -1L
        transaction {
            localId = dao.insertTrack(
                TrackMainData(
                    trackName = detail.name,
                    // The contract carries metres; the app stores kilometres.
                    totalLength = detail.lengthMeters?.let { it / 1000 },
                    country = detail.country,
                    type = if (detail.type == ApiTrackType.Sprint) "Sprint" else "Circuit",
                )
            )
            dao.insertTrackPoints(points.map { it.copy(trackId = localId) })
            dao.putLink(RemoteLink(RemoteLink.KIND_SHARED_TRACK, localId, remoteTrackId, uploadedHash = "shared", uploadedAt = clock()))
        }
        return localId
    }

    // ── Storage ──

    private fun remember(events: List<JoinedEvent>) {
        prefs.edit().putString(KEY, OkHttpTrackBoardApi.json.encodeToString(serializer, events)).apply()
        _joined.value = events
    }

    private fun read(): List<JoinedEvent> =
        prefs.getString(KEY, null)
            ?.let { runCatching { OkHttpTrackBoardApi.json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private companion object {
        const val KEY = "joined"
    }
}

private fun EventSummary.toJoined(): JoinedEvent? {
    val starts = parseInstant(startsAt) ?: return null
    val ends = parseInstant(endsAt) ?: return null
    return JoinedEvent(id, name, trackId, trackName, starts, ends, myGroupId)
}

internal fun parseInstant(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

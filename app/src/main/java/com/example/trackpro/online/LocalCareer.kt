package com.example.trackpro.online

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.utilities.timed

/** A driver's best lap on one track, from this phone's own records. */
data class LocalBest(
    val trackId: Long,
    val trackName: String,
    val country: String,
    val bestLapMs: Long,
    val setAt: Long,
    val vehicleId: Long?,
    val lapCount: Int,
)

/**
 * The career sheet as this phone knows it. Always available, signed in or not: it is what
 * the Profile tab shows offline, and the only thing it shows without an account.
 */
data class LocalCareer(
    val sessionCount: Int,
    val lapCount: Int,
    val trackCount: Int,
    val vehicleCount: Int,
    /** Null when no counted lap is on a track of known length. */
    val distanceKm: Double?,
    val firstSessionAt: Long?,
    val lastSessionAt: Long?,
    val mainVehicleId: Long?,
    /** Fastest track first would favour short tracks; these are ordered by track name. */
    val bests: List<LocalBest>,
) {
    val isEmpty: Boolean get() = sessionCount == 0

    companion object {
        val EMPTY = LocalCareer(0, 0, 0, 0, null, null, null, null, emptyList())

        /**
         * The same counting rules the server applies to uploaded sessions, so a signed-in
         * driver does not see two different numbers for the same history:
         * voided sessions never count, and neither do laps GPS dropped out of.
         */
        fun compute(
            sessions: List<SessionData>,
            laps: List<LapTimeData>,
            tracks: List<TrackMainData>,
            vehicles: List<VehicleInformationData>,
        ): LocalCareer {
            val counted = sessions.filter { !it.voided }
            if (counted.isEmpty()) return EMPTY.copy(vehicleCount = vehicles.size)

            val sessionById = counted.associateBy { it.id }
            val trackById = tracks.associateBy { it.trackId }

            val validLaps = laps
                .filter { it.sessionid in sessionById && !it.signalGap }
                .timed()
                .mapNotNull { timed ->
                    val session = sessionById.getValue(timed.lap.sessionid)
                    val trackId = session.trackId?.takeIf { it != -1L } ?: return@mapNotNull null
                    Triple(timed, session, trackId)
                }

            val lengths = validLaps.mapNotNull { (_, _, trackId) -> trackById[trackId]?.totalLength?.takeIf { it > 0 } }

            val bests = validLaps
                .groupBy { it.third }
                .mapNotNull { (trackId, group) ->
                    val track = trackById[trackId] ?: return@mapNotNull null
                    // A tie with yourself goes to the lap set first, as on the leaderboard.
                    val (best, session, _) = group.minWith(compareBy({ it.first.millis }, { it.second.startTime }))
                    LocalBest(
                        trackId = trackId,
                        trackName = track.trackName,
                        country = track.country,
                        bestLapMs = best.millis,
                        setAt = session.startTime,
                        vehicleId = session.vehicleId,
                        lapCount = group.size,
                    )
                }
                .sortedBy { it.trackName.lowercase() }

            val vehicleIds = vehicles.mapTo(mutableSetOf()) { it.vehicleId }
            val mainVehicle = counted
                .mapNotNull { s -> s.vehicleId?.takeIf { it in vehicleIds }?.let { it to s.startTime } }
                .groupBy({ it.first }, { it.second })
                .maxWithOrNull(compareBy({ it.value.size }, { it.value.max() }))
                ?.key

            return LocalCareer(
                sessionCount = counted.size,
                lapCount = validLaps.size,
                trackCount = counted.mapNotNull { it.trackId?.takeIf { id -> id != -1L } }.toSet().size,
                vehicleCount = vehicles.size,
                distanceKm = if (lengths.isEmpty()) null else Math.round(lengths.sum() * 10) / 10.0,
                firstSessionAt = counted.minOf { it.startTime },
                lastSessionAt = counted.maxOf { it.startTime },
                mainVehicleId = mainVehicle,
                bests = bests,
            )
        }
    }
}

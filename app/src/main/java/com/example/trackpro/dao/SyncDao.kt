package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.TrackPublication
import com.example.trackpro.dataClasses.VehicleInformationData
import kotlinx.coroutines.flow.Flow

/**
 * Everything TrackBoard sync reads and writes, in one place.
 *
 * The reads are one-shot `suspend` variants of queries other DAOs expose as flows: sync runs
 * in a background worker and wants a snapshot, not a subscription.
 */
@Dao
interface SyncDao {

    // ── Links to server records ──

    @Query("SELECT * FROM remote_link WHERE kind = :kind AND localId = :localId")
    suspend fun getLink(kind: String, localId: Long): RemoteLink?

    @Query("SELECT * FROM remote_link WHERE kind = :kind")
    suspend fun getLinks(kind: String): List<RemoteLink>

    /** Live, so the garage shows a car's backup state change the moment sync finishes. */
    @Query("SELECT * FROM remote_link WHERE kind = :kind")
    fun observeLinks(kind: String): Flow<List<RemoteLink>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLink(link: RemoteLink)

    @Query("DELETE FROM remote_link WHERE kind = :kind AND localId = :localId")
    suspend fun deleteLink(kind: String, localId: Long)

    // ── Track publication ──

    @Query("SELECT EXISTS(SELECT 1 FROM track_publication WHERE trackId = :trackId)")
    fun observeIsPublished(trackId: Long): Flow<Boolean>

    @Query("SELECT trackId FROM track_publication")
    suspend fun getPublishedTrackIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun publish(publication: TrackPublication)

    @Query("DELETE FROM track_publication WHERE trackId = :trackId")
    suspend fun unpublish(trackId: Long)

    // ── Snapshots of local data ──

    /**
     * Finished timed sessions: those with a track (drag sessions carry none, or -1) and an end
     * time. A session still recording is left alone until it closes.
     */
    @Query(
        """
        SELECT * FROM session_data
        WHERE trackId IS NOT NULL AND trackId != -1 AND endTime IS NOT NULL
        """
    )
    suspend fun getFinishedTrackSessions(): List<SessionData>

    @Query("SELECT * FROM session_data WHERE id = :id")
    suspend fun getSession(id: Long): SessionData?

    @Query("SELECT * FROM lap_time_data WHERE sessionid = :sessionId ORDER BY lapnumber ASC")
    suspend fun getLaps(sessionId: Long): List<LapTimeData>

    @Query("SELECT * FROM sector_time_data WHERE lapid IN (:lapIds) ORDER BY lapid, sectorIndex")
    suspend fun getSectors(lapIds: List<Long>): List<SectorTimeData>

    @Query("SELECT * FROM track_main_data WHERE trackId = :trackId")
    suspend fun getTrack(trackId: Long): TrackMainData?

    /** Ordered exactly as the timing code reads them, since that order defines the gates. */
    @Query("SELECT * FROM track_coordinates_data WHERE trackId = :trackId ORDER BY id ASC")
    suspend fun getTrackPoints(trackId: Long): List<TrackCoordinatesData>

    @Query("SELECT * FROM vehicle_information_data WHERE vehicleId = :vehicleId")
    suspend fun getVehicle(vehicleId: Long): VehicleInformationData?

    // ── Garage sync writes ──

    @Query("SELECT * FROM vehicle_information_data ORDER BY vehicleId")
    suspend fun getVehicles(): List<VehicleInformationData>

    @Insert
    suspend fun insertVehicle(vehicle: VehicleInformationData): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleInformationData)

    /** Forgets every server link. Used when the account they point into is deleted. */
    @Query("DELETE FROM remote_link")
    suspend fun deleteAllLinks()
}

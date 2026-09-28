package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData
import kotlinx.coroutines.flow.Flow

/**
 * Snapshots for the career sheet computed on the phone. Observed as flows so the Profile tab
 * updates the moment a session ends or is voided, without a manual refresh.
 */
@Dao
interface CareerDao {
    @Query("SELECT * FROM session_data")
    fun observeSessions(): Flow<List<SessionData>>

    @Query("SELECT * FROM lap_time_data")
    fun observeLaps(): Flow<List<LapTimeData>>

    @Query("SELECT * FROM track_main_data")
    fun observeTracks(): Flow<List<TrackMainData>>

    @Query("SELECT * FROM vehicle_information_data")
    fun observeVehicles(): Flow<List<VehicleInformationData>>
}

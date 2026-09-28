package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.models.VehiclePair
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleInformationDAO {

    @Query("SELECT * FROM vehicle_information_data")
    fun getAllVehicles(): Flow<List<VehicleInformationData>>

    @Query("SELECT vehicleId, manufacturer || ' ' || model FROM vehicle_information_data")
    fun getPairVehicles(): Flow<List<VehiclePair>>


    /** Emits null once the car is deleted, which a screen showing it has to survive. */
    @Query("SELECT * FROM vehicle_information_data WHERE vehicleId =:vehicleId")
    fun getVehicle(vehicleId: Long?): Flow<VehicleInformationData?>

    // One-shot (non-Flow) signature lookup used to dedup default-vehicle seeding on startup.
    @Query("SELECT manufacturer || ' ' || model || ' ' || year FROM vehicle_information_data")
    suspend fun getAllVehicleSignatures(): List<String>

    @Insert
    suspend fun insertVehicle(vehicleInformationData: VehicleInformationData):Long

    @Query("DELETE FROM vehicle_information_data WHERE vehicleId=:vehicleId")
    suspend fun deleteVehicle(vehicleId: Long)

    @Update
    suspend fun updateVehicle(vehicle: VehicleInformationData)

    @Query("UPDATE vehicle_information_data SET photoFile = :photoFile WHERE vehicleId = :vehicleId")
    suspend fun setPhotoFile(vehicleId: Long, photoFile: String?)

    /** Non-voided sessions per vehicle, for the garage list and picking the main car. */
    @Query(
        """
        SELECT vehicleId AS vehicleId, COUNT(*) AS sessions, MAX(startTime) AS lastUsed
        FROM session_data
        WHERE vehicleId IS NOT NULL AND voided = 0
        GROUP BY vehicleId
        """
    )
    fun observeUsage(): Flow<List<VehicleUsage>>
}

/** How much a car has been driven: its non-voided session count and the latest start time. */
data class VehicleUsage(val vehicleId: Long, val sessions: Int, val lastUsed: Long?)



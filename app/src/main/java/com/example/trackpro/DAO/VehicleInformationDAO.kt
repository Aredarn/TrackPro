package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.Models.VehiclePair
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleInformationDAO {

    @Query("SELECT * FROM vehicle_information_data")
    fun getAllVehicles(): Flow<List<VehicleInformationData>>

    @Query("SELECT vehicleId, manufacturer || ' ' || model FROM vehicle_information_data")
    fun getPairVehicles(): Flow<List<VehiclePair>>


    @Query("SELECT * FROM vehicle_information_data WHERE vehicleId =:vehicleId")
    fun getVehicle(vehicleId: Long): Flow<VehicleInformationData>

    @Insert
    suspend fun insertVehicle(vehicleInformationData: VehicleInformationData):Long

    @Query("DELETE FROM vehicle_information_data WHERE vehicleId=:vehicleId")
    suspend fun deleteVehicle(vehicleId: Int)
}



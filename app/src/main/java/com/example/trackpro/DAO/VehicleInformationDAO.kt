package com.example.trackpro.DAO

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.DataClasses.VehicleInformationData
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleInformationDAO {

    @Query("SELECT * FROM vehicle_information_data")
    fun getAllVehicles(): Flow<List<VehicleInformationData>>

    @Query("SELECT vehicleId, manufacturer || ' ' || model FROM vehicle_information_data")
    fun getPairVehicles(): Flow<List<VehiclePair>>


    @Query("SELECT * FROM vehicle_information_data WHERE vehicleId =:vehicleId")
    fun getVehicle(vehicleId: Int): Flow<VehicleInformationData>

    @Insert
    suspend fun insertVehicle(vehicleInformationData: VehicleInformationData):Long

    @Query("DELETE FROM vehicle_information_data WHERE vehicleId=:vehicleId")
    suspend fun deleteVehicle(vehicleId: Int)
}

data class VehiclePair(
    val vehicleId: Long,

    // Use @ColumnInfo to specify the column name to map the concatenated result
    @ColumnInfo(name = "manufacturer || ' ' || model")
    val manufacturerAndModel: String
)

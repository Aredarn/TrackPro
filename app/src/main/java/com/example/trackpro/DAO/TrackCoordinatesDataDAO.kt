package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.DataClasses.TrackCoordinatesData

@Dao
interface TrackCoordinatesDataDAO {
    @Query("SELECT * FROM track_coordinates_data where trackId = :trackId")
    suspend fun getCoordinatesOfTrack(trackId: Int)

    @Insert
    suspend fun insertWholeTrack(data: List<TrackCoordinatesData>)
}
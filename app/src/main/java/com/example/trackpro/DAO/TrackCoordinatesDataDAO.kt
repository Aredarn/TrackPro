package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.DataClasses.TrackCoordinatesData

@Dao
interface TrackCoordinatesDataDAO {
    @Query("SELECT * FROM track_coordinates_data where trackId = :trackId")
    suspend fun getCoordinatesOfTrack(trackId: Int):List<TrackCoordinatesData>

    @Insert
    suspend fun insertTrackPart(data: List<TrackCoordinatesData>)
}
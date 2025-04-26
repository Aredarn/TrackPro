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


    //The user wanted a full track filter, insertion of the full track
    //Same as the insertTrackPart, but made two @Insert for better readability
    @Insert
    suspend fun insertTrack(data: List<TrackCoordinatesData>)


    // IF the user whats to recreate the track
    //OR
    // IF the user filters the coordinates (if the full track is complete)
    @Query("DELETE FROM track_coordinates_data WHERE trackId = :trackId")
    suspend fun deleteTrackCoordinates(trackId: Int)

}
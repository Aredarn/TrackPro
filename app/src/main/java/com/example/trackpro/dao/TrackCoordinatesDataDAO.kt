package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.trackpro.dataClasses.TrackCoordinatesData
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackCoordinatesDataDAO {

    // Ordered explicitly: every consumer treats this list as the track path in the order it
    // was recorded (map polylines, finish/sector gate construction, auto-slicing), which is
    // insertion order, which is id order. SQLite only happens to return that order for an
    // unordered query, so the guarantee is spelled out rather than relied on.
    @Query("SELECT * FROM track_coordinates_data WHERE trackId = :trackId ORDER BY id ASC")
    fun getCoordinatesOfTrack(trackId: Long): Flow<List<TrackCoordinatesData>>

    @Insert
    suspend fun insertTrackPart(data: List<TrackCoordinatesData>)

    //The user wanted a full track filter, insertion of the full track
    //Same as the insertTrackPart, but made two @Insert for better readability
    @Insert
    suspend fun insertTrack(data: List<TrackCoordinatesData>)

    // Bulk-updates existing points in place (matched by primary key) - used to (re)apply
    // sector markers to an already-saved track without re-recording it.
    @Update
    suspend fun updateTrackCoordinates(data: List<TrackCoordinatesData>)

    // IF the user whats to recreate the track
    //OR
    // IF the user filters the coordinates (if the full track is complete)
    @Query("DELETE FROM track_coordinates_data WHERE trackId = :trackId")
    suspend fun deleteTrackCoordinates(trackId: Long)
}
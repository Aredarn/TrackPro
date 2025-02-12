package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.DataClasses.TrackMainData


@Dao
interface TrackMainDataDAO {
    @Insert
    suspend fun insertTrackMainDataDAO(trackMainData: TrackMainData)

    @Query("SELECT * FROM track_main_data ORDER BY trackName ASC")
    suspend fun getAllTrack():List<TrackMainData>

    @Query("DELETE FROM track_main_data WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId : Int)

}
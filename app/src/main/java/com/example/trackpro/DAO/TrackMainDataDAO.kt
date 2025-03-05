package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.DataClasses.TrackMainData
import kotlinx.coroutines.flow.Flow


@Dao
interface TrackMainDataDAO {
    @Insert
    suspend fun insertTrackMainDataDAO(trackMainData: TrackMainData) : Long

    @Query("SELECT * FROM track_main_data ORDER BY trackName ASC")
    fun getAllTrack(): Flow<List<TrackMainData>>

    @Query("Select * from track_main_data where trackId =:trackId")
    fun getTrack(trackId: Int): Flow<TrackMainData>

    @Query("DELETE FROM track_main_data WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId : Int)

}
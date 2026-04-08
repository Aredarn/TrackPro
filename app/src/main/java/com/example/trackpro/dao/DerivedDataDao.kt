package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.dataClasses.DerivedData

@Dao
interface DerivedDataDao {
    @Insert
    suspend fun insertDerivedData(derivedData: DerivedData)

    @Query("SELECT * FROM derived_data WHERE id = :id")
    fun getDerivedDataById(id: Long): DerivedData

    @Query("SELECT * FROM derived_data")
    fun getAllDerivedData(): List<DerivedData>
}

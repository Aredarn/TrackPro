package com.example.trackpro.CalculationClasses

import android.util.Log
import com.example.trackpro.DAO.RawGPSDataDao
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.ESPDatabase
import kotlinx.coroutines.runBlocking


class DragTimeCalculation(
    val sessionid : Long,
    private val database: ESPDatabase
) {
    private lateinit var postProc: PostProcessing
    val ZERO_THRESHOLD = 0.1f // Adjust based on your data


    fun initializePostProcessing() {
        postProc = PostProcessing(database) // Or retrieve an existing instance
    }

    private suspend fun postProcessing() {
        initializePostProcessing()
        if (!::postProc.isInitialized) {
            return
        }
        postProc.postProcessing(sessionid)
    }


    suspend fun timeFromZeroToHundred(): Int {
        runBlocking {
            postProcessing()
        }

        val sessionItems: List<SmoothedGPSData> = database.smoothedDataDao().getSmoothedGPSDataBySession(sessionid)

        var lastZeroTime: Long? = null
        var endTime: Long? = null

        // Step 1: Find the last 0 km/h occurrence
        for (i in sessionItems.indices.reversed()) {
            val currentData = sessionItems[i]
            if (currentData.smoothedSpeed != null && currentData.smoothedSpeed <= ZERO_THRESHOLD) {
                lastZeroTime = currentData.timestamp
                break
            }
        }

        // Step 2: Find when speed reaches 100 km/h after the last 0 km/h
        if (lastZeroTime != null) {
            for (currentData in sessionItems) {
                if (currentData.timestamp > lastZeroTime && currentData.smoothedSpeed != null && currentData.smoothedSpeed >= 100f) {
                    endTime = currentData.timestamp
                    break
                }
            }
        }

        // Step 3: Calculate time duration from last 0 km/h to 100 km/h
        return if (lastZeroTime != null && endTime != null) {
            ((endTime - lastZeroTime) / 1000).toInt() // Convert milliseconds to seconds
        } else {
            -1 // Indicate failure to find 0-100 transition
        }
    }
}

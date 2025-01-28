package com.example.trackpro.CalculationClasses

import android.util.Log
import com.example.trackpro.DAO.RawGPSDataDao
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.ESPDatabase



class DragTimeCalculation(
    val sessionid : Long,
    private val database: ESPDatabase
) {
    private lateinit var postProc: PostProcessing

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
        postProcessing()
        // Step 1: Retrieve the raw GPS data for the given session
        val sessionItems: List<SmoothedGPSData> = database.smoothedDataDao().getSmoothedGPSDataBySession(sessionid.toInt())

        // Step 2: Find the last 0 km/h and track when we reach 100 km/h after that point
        var lastZeroTime: Long? = null
        var endTime: Long? = null

        // Iterate through the data in reverse to find the last occurrence of 0 km/h
        for (i in sessionItems.size - 1 downTo 0) {
            val currentData = sessionItems[i]

            if (currentData.smoothedSpeed != null && currentData.smoothedSpeed <= 0f) {
                lastZeroTime = currentData.timestamp
                break
            }
        }

        // Step 3: track the next 100 km/h after the last 0 km/h
        if (lastZeroTime != null) {
            for (i in sessionItems) {
                val currentData = i

                // If speed exceeds or equals 100 km/h after the last 0 km/h
                if (currentData.smoothedSpeed != null && currentData.smoothedSpeed >= 100f && currentData.timestamp > lastZeroTime) {
                    endTime = currentData.timestamp
                    break
                }
            }
        }

        // Step 4: Calculate the time taken from last 0 km/h to 100 km/h
        return if (lastZeroTime != null && endTime != null) {
            val timeTakenMillis = endTime - lastZeroTime // in seconds
            timeTakenMillis.toInt() // Return seconds directly without dividing by 1000
        } else {
            // If no 0 to 100 transition could be found, return a default value or error
            -1
        }
    }
}
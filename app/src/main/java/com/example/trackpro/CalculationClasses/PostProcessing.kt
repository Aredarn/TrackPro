package com.example.trackpro.CalculationClasses

import android.util.Log
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.ESPDatabase

class PostProcessing(val database: ESPDatabase) {

    suspend fun postProcessing(sessionId: Long) {
        // Step 1: Retrieve raw GPS data for the session
        val sessionItems: List<RawGPSData> = database.rawGPSDataDao().getGPSDataBySession(sessionId.toInt())

        // Step 2: Apply smoothing
        val windowSize = 3
        val smoothedData = applyMovingAverage(sessionItems, windowSize,sessionId)

        // Step 3: Save smoothed data back to the database
        saveSmoothedData(smoothedData)
    }


//------------------------------------//
//------------------------------------//
//------------------------------------//

    fun applyMovingAverage(data: List<RawGPSData>, windowSize: Int, sessionId: Long): List<SmoothedGPSData> {
        val smoothed = mutableListOf<SmoothedGPSData>()

        val latWindow = ArrayDeque<Double>(windowSize)
        val lonWindow = ArrayDeque<Double>(windowSize)
        val altWindow = ArrayDeque<Double?>(windowSize)
        val speedWindow = ArrayDeque<Float?>(windowSize)

        data.forEach { gpsData ->
            // Add values to the respective windows
            if (latWindow.size == windowSize) latWindow.removeFirst()
            if (lonWindow.size == windowSize) lonWindow.removeFirst()
            if (altWindow.size == windowSize) altWindow.removeFirst()
            if (speedWindow.size == windowSize) speedWindow.removeFirst()

            latWindow.addLast(gpsData.latitude)
            lonWindow.addLast(gpsData.longitude)
            altWindow.addLast(gpsData.altitude)
            speedWindow.addLast(gpsData.speed)

            // Compute smoothed values
            val smoothedLat = latWindow.average()
            val smoothedLon = lonWindow.average()
            val smoothedAlt = altWindow.filterNotNull().averageOrNull() ?: gpsData.altitude
            val smoothedSpeed = speedWindow.filterNotNull().averageOrNull()?.toFloat() ?: gpsData.speed

            // Debug log
            Log.d("PostProcessing", "Timestamp: ${gpsData.timestamp}, Smoothed Speed: $smoothedSpeed")

            // Add smoothed result to the list
            smoothed.add(
                SmoothedGPSData(
                    sessionid = sessionId,
                    timestamp = gpsData.timestamp,
                    latitude = smoothedLat,
                    longitude = smoothedLon,
                    altitude = smoothedAlt,
                    smoothedSpeed = smoothedSpeed
                )
            )
        }

        return smoothed
    }





    private fun List<Double?>.averageOrNull(): Double? {
        val nonNullValues = this.filterNotNull()
        return if (nonNullValues.isNotEmpty()) nonNullValues.average() else null
    }
    private fun List<Float?>.averageOrNull(): Float? {
        val nonNullValues = this.filterNotNull()
        return if (nonNullValues.isNotEmpty()) nonNullValues.average().toFloat() else null
    }

    //------------------------------------//

    //------------------------------------//


    suspend fun saveSmoothedData(smoothedData: List<SmoothedGPSData>) {
        database.smoothedDataDao().insertAll(smoothedData)
    }


}
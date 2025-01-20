package com.example.trackpro.CalculationClasses

import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.ESPDatabase

class PostProcessing {

    private lateinit var database: ESPDatabase

    suspend fun postProcessing(sessionId: Long) {
        // Step 1: Retrieve raw GPS data for the session
        val sessionItems: List<RawGPSData> = database.rawGPSDataDao().getGPSDataBySession(sessionId.toInt())

        // Step 2: Apply smoothing
        val windowSize = 5
        val smoothedData = applyMovingAverage(sessionItems, windowSize,sessionId)

        // Step 3: Save smoothed data back to the database
        saveSmoothedData(smoothedData)
    }



//------------------------------------//
//------------------------------------//
//------------------------------------//


    private fun applyMovingAverage(data: List<RawGPSData>, windowSize: Int,sessionId: Long): List<SmoothedGPSData> {
        val smoothed = mutableListOf<SmoothedGPSData>()
        val latWindow = mutableListOf<Double>()
        val lonWindow = mutableListOf<Double>()
        val altWindow = mutableListOf<Double?>()
        val speedWindow = mutableListOf<Float?>()

        data.forEach { gpsData ->
            // Add values to the respective windows
            latWindow.add(gpsData.latitude)
            lonWindow.add(gpsData.longitude)
            gpsData.altitude?.let { altWindow.add(it) }
            gpsData.speed?.let { speedWindow.add(it) }

            // Maintain window size
            if (latWindow.size > windowSize) latWindow.removeAt(0)
            if (lonWindow.size > windowSize) lonWindow.removeAt(0)
            if (altWindow.size > windowSize) altWindow.removeAt(0)
            if (speedWindow.size > windowSize) speedWindow.removeAt(0)

            // Compute smoothed values
            val smoothedLat = if (latWindow.size == windowSize) latWindow.average() else gpsData.latitude
            val smoothedLon = if (lonWindow.size == windowSize) lonWindow.average() else gpsData.longitude
            val smoothedAlt = if (altWindow.size == windowSize) altWindow.filterNotNull().averageOrNull() else gpsData.altitude
            val smoothedSpeed = if (speedWindow.size == windowSize) speedWindow.filterNotNull().averageOrNull()?.toFloat() else gpsData.speed

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
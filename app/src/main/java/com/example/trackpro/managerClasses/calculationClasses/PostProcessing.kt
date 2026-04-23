package com.example.trackpro.managerClasses.calculationClasses

import android.util.Log
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.SmoothedGPSData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.ESPDatabase
import kotlinx.coroutines.flow.first
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PostProcessing(val database: ESPDatabase) {

    suspend fun postProcessing(sessionId: Long) {
        // Step 1: Retrieve raw GPS data for the session
        val sessionItems: List<RawGPSData> = database.rawGPSDataDao().getGPSDataBySession(sessionId)

        // Step 2: Apply smoothing
        val windowSize = 15
        val smoothedData = applyMovingAverage(sessionItems, windowSize,sessionId)

        // Step 3: Save smoothed data back to the database
        saveSmoothedData(smoothedData)
    }

    //------------------------------------//
    //------------------------------------//
    //------------------------------------//

    private fun applyMovingAverage(data: List<RawGPSData>, windowSize: Int, sessionId: Long): List<SmoothedGPSData> {
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
            val smoothedSpeed = speedWindow.filterNotNull().averageOrNull() ?: gpsData.speed

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
    //------------------------------------//

    private suspend fun saveSmoothedData(smoothedData: List<SmoothedGPSData>) {
        database.smoothedDataDao().insertAll(smoothedData)
    }

    suspend fun processTrackPoints(
        trackId: Long,
        isLapTrack: Boolean = false,
        minDistance: Double = 0.2,
        lapThreshold: Double = 50.0
    ): List<TrackCoordinatesData> {
        Log.d("ProcessTrack", "Processing track $trackId (isLapTrack: $isLapTrack)")

        val rawPoints = database.trackCoordinatesDao()
            .getCoordinatesOfTrack(trackId)
            .first()
            .toMutableList()

        if (rawPoints.isEmpty()) {
            Log.d("ProcessTrack", "No rawPoints found")
            return emptyList()
        }

        // Step 1: Remove consecutive duplicates
        val deduplicated = rawPoints.distinct()

        // Step 2: Filter points that are too close to each other
        val distanceFiltered = mutableListOf<TrackCoordinatesData>()
        var lastKeptPoint: TrackCoordinatesData? = null

        deduplicated.forEach { point ->
            if (lastKeptPoint == null) {
                distanceFiltered.add(point)
                lastKeptPoint = point
            } else {
                val distance = haversine(
                    lastKeptPoint!!.latitude, lastKeptPoint!!.longitude,
                    point.latitude, point.longitude
                )
                if (distance >= minDistance) {
                    distanceFiltered.add(point)
                    lastKeptPoint = point
                }
            }
        }

        if (distanceFiltered.isEmpty()) return emptyList()

        // Step 3: Find the start point
        val startPointIndex = distanceFiltered.indexOfFirst { it.isStartPoint }
        val actualStartIndex = if (startPointIndex != -1) startPointIndex else 0

        // Step 4: For LAP tracks only - detect lap completion
        val startPoint = distanceFiltered[actualStartIndex]
        var lapEndIndex: Int? = null

        if (isLapTrack) {
            for (index in (actualStartIndex + 1) until distanceFiltered.size) {
                val point = distanceFiltered[index]
                val distanceFromStart = haversine(
                    startPoint.latitude, startPoint.longitude,
                    point.latitude, point.longitude
                )
                if (distanceFromStart <= lapThreshold) {
                    lapEndIndex = index
                    break
                }
            }
        }

        // Step 5: Extract the final track path
        val finalProcessedList = if (lapEndIndex != null) {
            // Lap completed - take from start to lap end
            distanceFiltered.subList(actualStartIndex, lapEndIndex + 1)
        } else {
            // Sprint or incomplete lap - take all from start onwards
            distanceFiltered.subList(actualStartIndex, distanceFiltered.size)
        }

        Log.d("ProcessTrack", "Processed ${finalProcessedList.size} points (Start: $actualStartIndex, End: $lapEndIndex)")

        return finalProcessedList
    }
    // Haversine distance calculation
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Earth radius in meters
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δφ = Math.toRadians(lat2 - lat1)
        val Δλ = Math.toRadians(lon2 - lon1)

        val a = sin(Δφ / 2) * sin(Δφ / 2) +
                cos(φ1) * cos(φ2) *
                sin(Δλ / 2) * sin(Δλ / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }


}
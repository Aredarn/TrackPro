package com.example.trackpro.CalculationClasses

import android.annotation.SuppressLint
import android.util.Log
import com.example.trackpro.DAO.RawGPSDataDao
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.ESPDatabase
import com.example.trackpro.ExtrasForUI.LatLonOffset
import kotlinx.coroutines.runBlocking
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt


class DragTimeCalculation(
    val sessionid : Long,
    private val database: ESPDatabase
) {
    private lateinit var postProc: PostProcessing
    val ZERO_THRESHOLD = 0.3f // Adjust based on your data


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
    suspend fun timeFromZeroToHundred(): Double {
        // Fetch session data in chronological order.
        var sessionItems = database.smoothedDataDao().getSmoothedGPSDataBySession(sessionid)

        // If no data exists, run postProcessing and re-fetch.
        if (sessionItems.isEmpty()) {
            runBlocking { postProcessing() }
            sessionItems = database.smoothedDataDao().getSmoothedGPSDataBySession(sessionid)
        }

        var minTimeDiff: Double? = null

        // Iterate through each data point in chronological order.
        for (i in sessionItems.indices) {
            val currentSpeed = sessionItems[i].smoothedSpeed
            // Identify a valid "start" point: speed at or near 0.
            if (currentSpeed != null && currentSpeed <= ZERO_THRESHOLD) {
                val startTime = sessionItems[i].timestamp
                // Look forward in time for the first occurrence of speed >= 100.
                for (j in (i + 1) until sessionItems.size) {
                    val candidateSpeed = sessionItems[j].smoothedSpeed
                    if (candidateSpeed != null && candidateSpeed >= 100f) {
                        val diff = sessionItems[j].timestamp - startTime
                        // Save the smallest valid time difference.
                        if (minTimeDiff == null || diff < minTimeDiff) {
                            minTimeDiff = diff.toDouble()
                        }
                        // Stop checking for this start point after a valid 100 km/h is found.
                        break
                    }
                }
            }
        }

        // Return the best (smallest) time difference in seconds, or -1 if no valid sequence was found.
        return minTimeDiff?.div(1000) ?: -1.0  // Convert milliseconds to seconds.
    }



    fun totalDistance(latLngList: List<LatLonOffset>): Double {
        var totalDistance = 0.0
        for (i in 0 until latLngList.size - 1) {
            val point1 = latLngList[i]
            val point2 = latLngList[i + 1]
            totalDistance += haversineDistance(point1.lat, point1.lon, point2.lat, point2.lon)
        }

        return round(totalDistance * 1000) / 1000
    }

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Radius of the Earth in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c // Distance in kilometers
    }
}

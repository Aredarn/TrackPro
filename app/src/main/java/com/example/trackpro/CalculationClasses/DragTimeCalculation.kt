package com.example.trackpro.CalculationClasses

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
    private val ZERO_THRESHOLD = 0.3f // Adjust based on your data


    private fun initializePostProcessing() {
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

        // Iterate through the session items to find all valid start points.
        for (i in sessionItems.indices) {
            val currentSpeed = sessionItems[i].smoothedSpeed

            // Check if the current speed is a valid start point (at or near 0 km/h).
            if (currentSpeed != null && currentSpeed <= ZERO_THRESHOLD) {
                val startTime = sessionItems[i].timestamp

                // Look forward to find the first occurrence of speed >= 100 km/h.
                for (j in (i + 1) until sessionItems.size) {
                    val candidateSpeed = sessionItems[j].smoothedSpeed

                    // Check if the candidate speed is >= 100 km/h.
                    if (candidateSpeed != null && candidateSpeed >= 100f) {
                        val diff = sessionItems[j].timestamp - startTime

                        // Update the minimum time difference if this interval is better.
                        if (minTimeDiff == null || diff < minTimeDiff) {
                            minTimeDiff = diff.toDouble()
                        }

                        // Stop searching for this start point after finding the first 100 km/h.
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

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Radius of the Earth in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c // Distance in kilometers
    }

    suspend fun quarterMile(): Double {
        // Fetch session data in chronological order.
        var sessionItems = database.smoothedDataDao().getSmoothedGPSDataBySession(sessionid)

        // If no data exists, run postProcessing and re-fetch.
        if (sessionItems.isEmpty()) {
            runBlocking { postProcessing() }
            sessionItems = database.smoothedDataDao().getSmoothedGPSDataBySession(sessionid)
        }

        var minQuarterMileTime: Double? = null

        // Iterate through the session items to find all valid start points.
        for (i in sessionItems.indices) {
            val currentSpeed = sessionItems[i].smoothedSpeed

            // Check if the current speed is a valid start point (at or near 0 km/h).
            if (currentSpeed != null && currentSpeed <= ZERO_THRESHOLD) {
                val startTime = sessionItems[i].timestamp
                var totalDistance = 0.0

                // Look forward to accumulate distance until reaching 402.336 meters.
                for (j in (i + 1) until sessionItems.size) {
                    val prevLat = sessionItems[j - 1].latitude
                    val prevLon = sessionItems[j - 1].longitude
                    val currLat = sessionItems[j].latitude
                    val currLon = sessionItems[j].longitude

                    // Calculate distance between consecutive points using Haversine formula.
                    val distance = haversineDistance(prevLat, prevLon, currLat, currLon)
                    totalDistance += distance

                    // Check if the quarter-mile distance has been reached.
                    if (totalDistance >= 402.336) {
                        val diff = sessionItems[j].timestamp - startTime

                        // Update the minimum time difference if this interval is better.
                        if (minQuarterMileTime == null || diff < minQuarterMileTime) {
                            minQuarterMileTime = diff.toDouble()
                        }

                        // Stop searching for this start point after reaching the quarter-mile distance.
                        break
                    }
                }
            }
        }

        // Return the best (smallest) quarter-mile time in seconds, or -1 if no valid sequence was found.
        return minQuarterMileTime?.div(1000) ?: -1.0  // Convert milliseconds to seconds.
    }

}

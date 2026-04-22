package com.example.trackpro.managerClasses.calculationClasses

import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.ESPDatabase
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class DragMetrics(
    val time0to60: Double? = null,
    val time0to100: Double? = null,
    val time0to160: Double? = null,
    val time0to200: Double? = null,
    val time50to150: Double? = null,
    val time100to200: Double? = null,
    val quarterMileTime: Double? = null,
    val quarterMileSpeed: Float? = null,
    val halfMileTime: Double? = null,
    val maxSpeed: Float = 0f,
    val totalDistance: Float = 0f
)

class DragTimeCalculation(
    val session: Long? = null,
    private val database: ESPDatabase
) {
    private val zeroThreshold = 2.0f // Speed threshold to consider as "zero" (km/h)

    // Real-time tracking state
    private var runStartTime: Long = 0L
    private var hasStartedRun: Boolean = false
    private val gpsPoints = mutableListOf<LatLonOffset>()
    private var totalDistanceMeters: Float = 0f
    private var maxSpeedRecorded: Float = 0f

    // Metric tracking
    private var time0to60Result: Double? = null
    private var time0to100Result: Double? = null
    private var time0to160Result: Double? = null
    private var time0to200Result: Double? = null
    private var time50to150Result: Double? = null
    private var time50Timestamp: Long? = null
    private var time100to200Result: Double? = null
    private var time100Timestamp: Long? = null
    private var quarterMileTimeResult: Double? = null
    private var quarterMileSpeedResult: Float? = null
    private var halfMileTimeResult: Double? = null

    /**
     * Process GPS data in real-time and calculate drag metrics
     * Call this method every time new GPS data arrives during a session
     */
    fun processRealtimeGPS(gpsData: RawGPSData, currentTimeMillis: Long): DragMetrics {
        val currentSpeed = gpsData.speed ?: 0f

        // Update max speed
        if (currentSpeed > maxSpeedRecorded) {
            maxSpeedRecorded = currentSpeed
        }

        // Add GPS point for distance calculation
        gpsPoints.add(LatLonOffset(gpsData.latitude, gpsData.longitude))

        // Calculate distance increment
        if (gpsPoints.size >= 2) {
            val lastPoint = gpsPoints[gpsPoints.size - 2]
            val currentPoint = gpsPoints[gpsPoints.size - 1]
            val distanceIncrement = haversineDistance(
                lastPoint.lat, lastPoint.lon,
                currentPoint.lat, currentPoint.lon
            )
            totalDistanceMeters += distanceIncrement.toFloat()
        }

        // Detect run start (speed crosses threshold from below)
        if (!hasStartedRun && currentSpeed > zeroThreshold) {
            hasStartedRun = true
            runStartTime = currentTimeMillis
        }

        // Calculate metrics only after run has started
        if (hasStartedRun) {
            val elapsedMillis = currentTimeMillis - runStartTime

            // 0-60 km/h
            if (time0to60Result == null && currentSpeed >= 60f) {
                time0to60Result = elapsedMillis / 1000.0
            }

            // 0-100 km/h
            if (time0to100Result == null && currentSpeed >= 100f) {
                time0to100Result = elapsedMillis / 1000.0
            }

            // 0-160 km/h (0-100 mph)
            if (time0to160Result == null && currentSpeed >= 160f) {
                time0to160Result = elapsedMillis / 1000.0
            }

            // 0-200 km/h
            if (time0to200Result == null && currentSpeed >= 200f) {
                time0to200Result = elapsedMillis / 1000.0
            }

            // 50-150 km/h (elasticity test)
            if (time50Timestamp == null && currentSpeed >= 50f) {
                time50Timestamp = currentTimeMillis
            }
            if (time50to150Result == null && time50Timestamp != null && currentSpeed >= 150f) {
                time50to150Result = (currentTimeMillis - time50Timestamp!!) / 1000.0
            }

            // 100-200 km/h (high-speed acceleration)
            if (time100Timestamp == null && currentSpeed >= 100f) {
                time100Timestamp = currentTimeMillis
            }
            if (time100to200Result == null && time100Timestamp != null && currentSpeed >= 200f) {
                time100to200Result = (currentTimeMillis - time100Timestamp!!) / 1000.0
            }

            // Quarter mile (402.336 meters)
            if (quarterMileTimeResult == null && totalDistanceMeters >= 402.336f) {
                quarterMileTimeResult = elapsedMillis / 1000.0
                quarterMileSpeedResult = currentSpeed
            }

            // Half mile (804.672 meters)
            if (halfMileTimeResult == null && totalDistanceMeters >= 804.672f) {
                halfMileTimeResult = elapsedMillis / 1000.0
            }
        }

        return getCurrentMetrics()
    }

    /**
     * Get current metrics snapshot
     */
    fun getCurrentMetrics(): DragMetrics {
        return DragMetrics(
            time0to60 = time0to60Result,
            time0to100 = time0to100Result,
            time0to160 = time0to160Result,
            time0to200 = time0to200Result,
            time50to150 = time50to150Result,
            time100to200 = time100to200Result,
            quarterMileTime = quarterMileTimeResult,
            quarterMileSpeed = quarterMileSpeedResult,
            halfMileTime = halfMileTimeResult,
            maxSpeed = maxSpeedRecorded,
            totalDistance = totalDistanceMeters
        )
    }

    /**
     * Reset all real-time tracking (call when starting a new run)
     */
    fun resetRealtimeTracking() {
        runStartTime = 0L
        hasStartedRun = false
        gpsPoints.clear()
        totalDistanceMeters = 0f
        maxSpeedRecorded = 0f

        time0to60Result = null
        time0to100Result = null
        time0to160Result = null
        time0to200Result = null
        time50to150Result = null
        time50Timestamp = null
        time100to200Result = null
        time100Timestamp = null
        quarterMileTimeResult = null
        quarterMileSpeedResult = null
        halfMileTimeResult = null
    }

    // ========== POST-SESSION ANALYSIS (LEGACY) ==========

    /**
     * Calculate 0-100 km/h from stored session data (post-session analysis)
     */
    suspend fun timeFromZeroToHundred(): Double {
        val sessionItems = database.rawGPSDataDao().getGPSDataBySession(session)

        if (sessionItems.isEmpty()) {
            return -1.0
        }

        var minTimeDiff: Double? = null

        for (i in sessionItems.indices) {
            val currentSpeed = sessionItems[i].speed

            if (currentSpeed != null && currentSpeed <= zeroThreshold) {
                val startTime = sessionItems[i].timestamp

                for (j in (i + 1) until sessionItems.size) {
                    val candidateSpeed = sessionItems[j].speed

                    if (candidateSpeed != null && candidateSpeed >= 100f) {
                        val diff = sessionItems[j].timestamp - startTime

                        if (minTimeDiff == null || diff < minTimeDiff) {
                            minTimeDiff = diff.toDouble()
                        }
                        break
                    }
                }
            }
        }

        return minTimeDiff?.div(1000) ?: -1.0
    }

    /**
     * Calculate quarter mile time from stored session data (post-session analysis)
     */
    suspend fun quarterMile(): Double {
        val sessionItems = database.rawGPSDataDao().getGPSDataBySession(session)

        if (sessionItems.isEmpty()) {
            return -1.0
        }

        var minQuarterMileTime: Double? = null

        for (i in sessionItems.indices) {
            val currentSpeed = sessionItems[i].speed

            if (currentSpeed != null && currentSpeed <= zeroThreshold) {
                val startTime = sessionItems[i].timestamp
                var totalDistance = 0.0

                for (j in (i + 1) until sessionItems.size) {
                    val prev = sessionItems[j - 1]
                    val curr = sessionItems[j]

                    val distance = haversineDistance(
                        prev.latitude, prev.longitude,
                        curr.latitude, curr.longitude
                    )
                    totalDistance += distance

                    if (totalDistance >= 402.336) {
                        val diff = curr.timestamp - startTime

                        if (minQuarterMileTime == null || diff < minQuarterMileTime) {
                            minQuarterMileTime = diff.toDouble()
                        }
                        break
                    }
                }
            }
        }

        return minQuarterMileTime?.div(1000) ?: -1.0
    }

    /**
     * Calculate total distance from a list of coordinates
     */
    fun totalDistance(latLngList: List<LatLonOffset>): Double {
        var totalDistance = 0.0
        for (i in 0 until latLngList.size - 1) {
            val point1 = latLngList[i]
            val point2 = latLngList[i + 1]
            totalDistance += haversineDistance(point1.lat, point1.lon, point2.lat, point2.lon)
        }

        return round(totalDistance * 1000) / 1000
    }

    /**
     * Calculate distance between two GPS points using Haversine formula
     * Returns distance in meters
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c // Distance in meters
    }
}
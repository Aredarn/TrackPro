package com.example.trackpro.CalculationClasses

import com.example.trackpro.DAO.RawGPSDataDao
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ESPDatabase


class DragTimeCalculation(
    val sessionid : Long,
    private val database: ESPDatabase
) {

    private lateinit var postProc : PostProcessing;

    // This function is responsible for smoothing the recorded data before any calculation
    suspend fun postProcessing(Sessionid: Long)
    {
        postProc.postProcessing(Sessionid)
    }

    suspend fun timeFromZeroToHundred(sessionId: Int): Int {
        // Step 1: Retrieve the raw GPS data for the given session
        val sessionItems: List<RawGPSData> = database.rawGPSDataDao().getGPSDataBySession(sessionId)

        // Debugging log: Inspect the session items
        println("Session Items: $sessionItems")

        // Step 2: Find the last 0 km/h and track when we reach 100 km/h after that point
        var lastZeroTime: Long? = null
        var endTime: Long? = null

        // Iterate through the data in reverse to find the last occurrence of 0 km/h
        for (i in sessionItems.size - 1 downTo 0) {
            val currentData = sessionItems[i]

            // Debugging log: Inspect the current speed and timestamp
            println("Checking Data - Timestamp: ${currentData.timestamp}, Speed: ${currentData.speed}")

            // Check if we are at 0 km/h
            if (currentData.speed != null && currentData.speed <= 0f) {
                lastZeroTime = currentData.timestamp
                println("Last 0 km/h found at Timestamp: $lastZeroTime")
                break  // Found the last 0 km/h, stop searching
            }
        }

        // Step 3: Now, track the next 100 km/h after the last 0 km/h
        if (lastZeroTime != null) {
            for (i in sessionItems) {
                val currentData = i

                // Debugging log: Inspect the current speed and timestamp again
                println("Checking Data - Timestamp: ${currentData.timestamp}, Speed: ${currentData.speed}")

                // If speed exceeds or equals 100 km/h after the last 0 km/h
                if (currentData.speed != null && currentData.speed >= 100f && currentData.timestamp > lastZeroTime) {
                    endTime = currentData.timestamp
                    println("First 100 km/h found at Timestamp: $endTime")
                    break
                }
            }
        }

        // Step 4: Calculate the time taken from last 0 km/h to 100 km/h
        return if (lastZeroTime != null && endTime != null) {
            val timeTakenMillis = endTime - lastZeroTime // in seconds
            println("Time Taken (seconds): $timeTakenMillis")
            timeTakenMillis.toInt() // Return seconds directly without dividing by 1000
        } else {
            // If no 0 to 100 transition could be found, return a default value or error
            println("No valid 0 to 100 transition found.")
            -1
        }
    }



}
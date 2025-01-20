package com.example.trackpro

import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ESPDatabase
import com.example.trackpro.DAO.RawGPSDataDao
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class PostProcessingTest {


    private lateinit var dragTimes: DragTimeCalculation
    private lateinit var mockDatabase: ESPDatabase
    private lateinit var mockRawGPSDataDao: RawGPSDataDao

    private val sampleSessionData: List<RawGPSData> = listOf(
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 1, speed = 0f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 2, speed = 20f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 3, speed = 50f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 4, speed = 0f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 5, speed = 100f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 6, speed = 120f, fixQuality = 1)
    )

    @Before
    fun setup() {
        // Mock the RawGPSDataDAO and ESPDatabase
        mockRawGPSDataDao = mock()
        mockDatabase = mock()

        // Stub the DAO to return the sample session data inside a suspend function
        runBlocking {
            whenever(mockDatabase.rawGPSDataDao()).thenReturn(mockRawGPSDataDao)
            whenever(mockRawGPSDataDao.getGPSDataBySession(1)).thenReturn(sampleSessionData)
        }

        // Initialize the DragTimeCalculation instance with the mock database
        dragTimes = DragTimeCalculation(1, mockDatabase)
    }

    @Test
    fun testTimeFromZeroToHundred() = runBlocking {
        // Test for the sample data
        val result = dragTimes.timeFromZeroToHundred(1) // Assuming 1 is the session ID for this test

        // Expected result: From the last 0 km/h (at timestamp 4) to when speed hits 100 km/h (timestamp 5).
        assertEquals(1, result) // The time between timestamp 4 and 5 is 1 second
    }
}
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

class PostProcessingTest {

    private lateinit var dragTimes: DragTimeCalculation
    private lateinit var mockDatabase: ESPDatabase
    private lateinit var mockRawGPSDataDao: RawGPSDataDao

    private val sampleSessionData: List<RawGPSData> = listOf(
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 1, speed = 0f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 2, speed = 20f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 3, speed = 50f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 4, speed = 0f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 5, speed = 50f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 6, speed = 70f, fixQuality = 1),
        RawGPSData(sessionid = 1, latitude = 40.0, longitude = -75.0, altitude = null, timestamp = 7, speed = 110f, fixQuality = 1)
    )

    @Before
    fun setup() {
        mockRawGPSDataDao = mock()
        mockDatabase = mock()

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
        val result = dragTimes.timeFromZeroToHundred(1)

        assertEquals(3, result)
    }
}
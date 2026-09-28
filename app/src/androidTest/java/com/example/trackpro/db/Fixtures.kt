package com.example.trackpro.db

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData

// Minimal valid rows for each entity. Every field the tests don't care about gets a fixed,
// plausible value so a test only spells out what it is actually about.

fun vehicle(
    manufacturer: String = "Mazda",
    model: String = "MX-5",
    year: Int = 2019
) = VehicleInformationData(
    manufacturer = manufacturer,
    model = model,
    year = year,
    engineType = "NA",
    horsepower = 181,
    torque = 205,
    weight = 1060.0,
    topSpeed = 219.0,
    acceleration = 6.5,
    drivetrain = "RWD",
    fuelType = "Petrol",
    tireType = "Street",
    fuelCapacity = 45.0,
    transmission = "Manual",
    suspensionType = null
)

fun session(
    vehicleId: Long?,
    trackId: Long? = null,
    startTime: Long = 1_700_000_000_000L,
    eventType: String = "Test session"
) = SessionData(
    startTime = startTime,
    endTime = null,
    eventType = eventType,
    vehicleId = vehicleId,
    trackId = trackId
)

fun track(name: String = "Test Ring", type: String = "circuit") = TrackMainData(
    trackName = name,
    totalLength = 4.3,
    country = "HU",
    type = type
)

fun trackPoint(
    trackId: Long,
    lat: Double,
    lon: Double,
    isStart: Boolean = false
) = TrackCoordinatesData(
    trackId = trackId,
    latitude = lat,
    longitude = lon,
    altitude = null,
    isStartPoint = isStart
)

/** A short straight track: [count] points heading east from a start point. */
fun straightTrack(trackId: Long, count: Int = 11): List<TrackCoordinatesData> =
    (0 until count).map { i ->
        trackPoint(trackId, lat = 47.0, lon = 19.0 + i * 0.0001, isStart = i == 0)
    }

fun lap(sessionId: Long, number: Int, time: String) = LapTimeData(
    sessionid = sessionId,
    lapnumber = number,
    laptime = time
)

fun gpsFix(
    sessionId: Long,
    timestamp: Long,
    speed: Float = 100f,
    valid: Boolean? = null
) = RawGPSData(
    sessionid = sessionId,
    latitude = 47.0,
    longitude = 19.0,
    altitude = null,
    timestamp = timestamp,
    speed = speed,
    fixQuality = 1,
    valid = valid
)

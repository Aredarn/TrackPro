package com.example.trackpro.CalculationClasses

import com.example.trackpro.DataClasses.TrackCoordinatesData

fun toLocalCoords(refLat: Double, refLng: Double, lat: Double, lng: Double): Pair<Double, Double> {
    val earthRadius = 6371000.0 // meters
    val x = Math.toRadians(lng - refLng) * earthRadius * Math.cos(Math.toRadians(refLat))
    val y = Math.toRadians(lat - refLat) * earthRadius
    return x to y
}

fun toGPS(refLat: Double, refLng: Double, x: Double, y: Double): Pair<Double, Double> {
    val earthRadius = 6371000.0
    val newLat = refLat + Math.toDegrees(y / earthRadius)
    val newLng = refLng + Math.toDegrees(x / (earthRadius * Math.cos(Math.toRadians(refLat))))
    return newLat to newLng
}

fun rotate90Clockwise(x: Double, y: Double): Pair<Double, Double> {
    return y to -x
}

fun rotateTrackPoints(
    coordinates: List<TrackCoordinatesData>,
    pivot: TrackCoordinatesData
): List<TrackCoordinatesData> {
    return coordinates.map {
        val (x, y) = toLocalCoords(pivot.latitude, pivot.longitude, it.latitude, it.longitude)
        val (rx, ry) = rotate90Clockwise(x, y)
        val (newLat, newLng) = toGPS(pivot.latitude, pivot.longitude, rx, ry)
        TrackCoordinatesData(it.id, latitude =  newLat, longitude = newLng, trackId = -1, altitude = 1.0)
    }
}
package com.example.trackpro.DataClasses


data class TrackJson(
    val trackName: String,
    val totalLength: Double?,
    val country: String,
    val type: String,
    val coordinates: List<LatLon>
)

data class CoordinateJson(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val isStartPoint: Boolean
)

data class LatLon(
    val lat: Double,
    val lon: Double
)

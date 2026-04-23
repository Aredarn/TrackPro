package com.example.trackpro.dataClasses

data class LatLonOffset(val lat: Double, val lon: Double)

fun convertToLatLonOffsetList(data: List<RawGPSData>): List<LatLonOffset> {
    return data.map { LatLonOffset(lat = it.latitude, lon = it.longitude) }
}



package com.example.trackpro.extrasForUI

data class LatLonOffset(val lat: Double, val lon: Double)

fun convertToLatLonOffsetList(data: List<com.example.trackpro.dataClasses.RawGPSData>): List<LatLonOffset> {
    return data.map { LatLonOffset(lat = it.latitude, lon = it.longitude) }
}



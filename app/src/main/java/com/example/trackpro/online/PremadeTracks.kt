package com.example.trackpro.online

import android.content.Context
import android.util.Log
import com.example.trackpro.R
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Recognises the premade tracks, whose server id every install derives identically. */
fun interface PremadeTracks {
    /** The shared server id if [track] is an unmodified premade track, otherwise null. */
    fun remoteIdFor(track: TrackMainData, points: List<TrackCoordinatesData>): String?
}

/**
 * Backed by the same `res/raw/tracks.json` that TrackSeeder installs from.
 *
 * A track only counts as premade when both its name and its timing geometry match the bundled
 * copy. The name alone is not enough: a driver can build their own track called
 * "Hungaroring", and it must not be posted to the shared one.
 */
class BundledPremadeTracks(private val context: Context) : PremadeTracks {

    private val fingerprintsByName: Map<String, String> by lazy { load() }

    override fun remoteIdFor(track: TrackMainData, points: List<TrackCoordinatesData>): String? {
        val expected = fingerprintsByName[track.trackName] ?: return null
        if (points.size < 2 || PayloadMapper.timingFingerprint(points) != expected) return null
        return PayloadMapper.premadeTrackId(track.trackName, expected)
    }

    private fun load(): Map<String, String> = runCatching {
        val json = context.resources.openRawResource(R.raw.tracks).bufferedReader().use { it.readText() }
        val tracks: List<BundledTrack> = Gson().fromJson(json, object : TypeToken<List<BundledTrack>>() {}.type)

        tracks.associate { bundled ->
            // Rebuilt exactly as TrackSeeder stores them: start flag on the first point only.
            val points = bundled.coordinates.orEmpty().mapIndexed { index, c ->
                TrackCoordinatesData(
                    trackId = 0,
                    latitude = c.lat,
                    longitude = c.lon,
                    altitude = null,
                    isStartPoint = index == 0,
                )
            }
            bundled.trackName to PayloadMapper.timingFingerprint(points)
        }
    }.onFailure { Log.e("BundledPremadeTracks", "Could not read tracks.json", it) }.getOrDefault(emptyMap())

    // Nullable because Gson bypasses Kotlin defaults: a missing array arrives as null.
    private data class BundledTrack(val trackName: String, val coordinates: List<BundledPoint>?)

    private data class BundledPoint(val lat: Double, val lon: Double)
}

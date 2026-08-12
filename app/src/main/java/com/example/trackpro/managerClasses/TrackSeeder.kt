package com.example.trackpro.managerClasses

import android.content.Context
import android.util.Log
import com.example.trackpro.R
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackJson
import com.example.trackpro.dataClasses.TrackMainData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Seeds the bundled premade tracks (res/raw/tracks.json) into the DB. Unlike Room's
 * onCreate callback (which only ever fires for a brand-new database file), this runs on
 * every app start and is matched by track name, so existing installs pick up newly-added
 * premade tracks on the next launch instead of only getting them on a fresh install, and
 * re-running it is always safe - already-seeded tracks are simply skipped.
 */
object TrackSeeder {
    private const val TAG = "TrackSeeder"

    suspend fun syncPremadeTracks(context: Context, database: ESPDatabase) {
        try {
            val jsonString = context.resources.openRawResource(R.raw.tracks)
                .bufferedReader().use { it.readText() }
            val tracks: List<TrackJson> = Gson().fromJson(
                jsonString,
                object : TypeToken<List<TrackJson>>() {}.type
            )

            val existingNames = database.trackMainDao().getAllTrackNames().toSet()
            val missing = tracks.filter { it.trackName !in existingNames }

            if (missing.isEmpty()) {
                Log.d(TAG, "All ${tracks.size} premade tracks already present, nothing to seed")
                return
            }

            for (track in missing) {
                val trackId = database.trackMainDao().insertTrackMainDataDAO(
                    TrackMainData(
                        trackName = track.trackName,
                        totalLength = track.totalLength,
                        country = track.country,
                        type = track.type
                    )
                )

                val coords = track.coordinates.mapIndexed { index, coord ->
                    TrackCoordinatesData(
                        trackId = trackId,
                        latitude = coord.lat,
                        longitude = coord.lon,
                        altitude = null,
                        isStartPoint = index == 0
                    )
                }
                database.trackCoordinatesDao().insertTrack(coords)
                Log.d(TAG, "Seeded ${coords.size} points for ${track.trackName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing premade tracks", e)
        }
    }
}

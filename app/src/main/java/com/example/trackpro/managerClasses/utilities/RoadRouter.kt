package com.example.trackpro.managerClasses.utilities

import android.util.Log
import com.example.trackpro.dataClasses.LatLonOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Snaps a straight line between two tapped points onto the real road network, so a
 * manually-built track follows actual roads instead of cutting across terrain.
 *
 * Uses the public OSRM demo server, which is free and needs no API key but is rate-limited
 * and offers no uptime guarantee - so every failure path here falls back to the straight
 * line rather than blocking the user from building a track.
 */
object RoadRouter {
    private const val TAG = "RoadRouter"
    private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** A routed leg, plus whether it actually came from the router or is a straight-line fallback. */
    data class RouteResult(val points: List<LatLonOffset>, val isRouted: Boolean)

    /**
     * Routes from [from] to [to] along drivable roads. Returns the polyline *excluding* the
     * starting point, so legs can be appended end-to-end without duplicating shared points.
     * Falls back to a direct line (just [to]) if the road network can't be reached or has no
     * route between the two points.
     */
    suspend fun routeLeg(from: LatLonOffset, to: LatLonOffset): RouteResult =
        withContext(Dispatchers.IO) {
            // OSRM takes lon,lat order - not lat,lon.
            val url = "$BASE_URL/${from.lon},${from.lat};${to.lon},${to.lat}" +
                    "?overview=full&geometries=geojson"

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "TrackPro/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Routing failed: HTTP ${response.code}")
                        return@withContext straightLineTo(to)
                    }
                    val body = response.body?.string()
                        ?: return@withContext straightLineTo(to)

                    val json = JSONObject(body)
                    if (json.optString("code") != "Ok") {
                        Log.w(TAG, "Routing failed: ${json.optString("code")}")
                        return@withContext straightLineTo(to)
                    }

                    val routes = json.optJSONArray("routes")
                    if (routes == null || routes.length() == 0) {
                        return@withContext straightLineTo(to)
                    }

                    val coords = routes.getJSONObject(0)
                        .optJSONObject("geometry")
                        ?.optJSONArray("coordinates")
                        ?: return@withContext straightLineTo(to)

                    val points = buildList {
                        for (i in 0 until coords.length()) {
                            val pair = coords.getJSONArray(i)
                            // GeoJSON is [lon, lat].
                            add(LatLonOffset(lat = pair.getDouble(1), lon = pair.getDouble(0)))
                        }
                    }

                    if (points.isEmpty()) return@withContext straightLineTo(to)

                    // Drop the first point: it's the snapped equivalent of `from`, which the
                    // previous leg already contributed.
                    RouteResult(points.drop(1), isRouted = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Routing error, falling back to straight line: ${e.message}")
                straightLineTo(to)
            }
        }

    private fun straightLineTo(to: LatLonOffset) = RouteResult(listOf(to), isRouted = false)
}

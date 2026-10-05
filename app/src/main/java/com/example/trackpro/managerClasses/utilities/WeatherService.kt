package com.example.trackpro.managerClasses.utilities

import com.example.trackpro.R
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the conditions at a session's location, so lap times can be compared with the
 * weather they were actually set in (air temp alone moves both grip and power enough to
 * dwarf most driver improvements).
 *
 * Uses Open-Meteo: free, no API key, no registration, no attribution requirement for
 * non-commercial use. Every failure path returns null rather than throwing - a session
 * recording must never depend on having a network connection.
 */
object WeatherService {
    private const val TAG = "WeatherService"
    private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Conditions in canonical metric units, matching how the rest of the app stores measurements. */
    data class Conditions(
        val temperatureC: Double,
        val humidityPct: Int,
        val precipitationMm: Double,
        val weatherCode: Int,
        val windKph: Double,
        val windDirectionDeg: Int,
        val pressureHpa: Double
    )

    suspend fun fetchCurrent(latitude: Double, longitude: Double): Conditions? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,relative_humidity_2m,precipitation," +
                    "weather_code,surface_pressure,wind_speed_10m,wind_direction_10m"

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "TrackPro/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Weather fetch failed: HTTP ${response.code}")
                        return@withContext null
                    }
                    val body = response.body?.string() ?: return@withContext null
                    val current = JSONObject(body).optJSONObject("current")
                        ?: return@withContext null

                    Conditions(
                        temperatureC = current.optDouble("temperature_2m", Double.NaN)
                            .takeIf { !it.isNaN() } ?: return@withContext null,
                        humidityPct = current.optInt("relative_humidity_2m", -1)
                            .takeIf { it >= 0 } ?: 0,
                        precipitationMm = current.optDouble("precipitation", 0.0),
                        weatherCode = current.optInt("weather_code", -1),
                        windKph = current.optDouble("wind_speed_10m", 0.0),
                        windDirectionDeg = current.optInt("wind_direction_10m", 0),
                        pressureHpa = current.optDouble("surface_pressure", 0.0)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Weather fetch error: ${e.message}")
                null
            }
        }

    /**
     * Maps a WMO weather interpretation code to a short label.
     * Reference: https://open-meteo.com/en/docs (WMO Weather interpretation codes WW)
     */
    fun describeCode(code: Int?): String = when (code) {
        null -> "Unknown"
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    /** The same label as [describeCode], as a string resource for the screen's language. */
    @androidx.annotation.StringRes
    fun describeCodeRes(code: Int?): Int = when (code) {
        null -> R.string.weather_unknown
        0 -> R.string.weather_clear
        1 -> R.string.weather_mainly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_overcast
        45, 48 -> R.string.weather_fog
        51, 53, 55 -> R.string.weather_drizzle
        56, 57 -> R.string.weather_freezing_drizzle
        61 -> R.string.weather_light_rain
        63 -> R.string.weather_rain
        65 -> R.string.weather_heavy_rain
        66, 67 -> R.string.weather_freezing_rain
        71 -> R.string.weather_light_snow
        73 -> R.string.weather_snow
        75 -> R.string.weather_heavy_snow
        77 -> R.string.weather_snow_grains
        80, 81, 82 -> R.string.weather_rain_showers
        85, 86 -> R.string.weather_snow_showers
        95 -> R.string.weather_thunderstorm
        96, 99 -> R.string.weather_thunderstorm_hail
        else -> R.string.weather_unknown
    }

    /**
     * Whether these conditions imply a wet surface - the single most important thing to know
     * when comparing two lap times. Based on measured precipitation or a precipitating code,
     * since a dry code with rain in the last interval still means a wet track.
     */
    fun isWet(precipitationMm: Double?, code: Int?): Boolean {
        if ((precipitationMm ?: 0.0) > 0.0) return true
        return code in setOf(
            51, 53, 55, 56, 57,
            61, 63, 65, 66, 67,
            71, 73, 75, 77,
            80, 81, 82, 85, 86,
            95, 96, 99
        )
    }

    /** Compass point for a wind bearing, e.g. 31 -> "NNE". */
    fun windCompass(degrees: Int?): String {
        if (degrees == null) return "-"
        // Hungarian names the points északi, keleti, déli, nyugati.
        val points = if (java.util.Locale.getDefault().language == "hu") listOf(
            "É", "ÉÉK", "ÉK", "KÉK", "K", "KDK", "DK", "DDK",
            "D", "DDNy", "DNy", "NyDNy", "Ny", "NyÉNy", "ÉNy", "ÉÉNy"
        ) else listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        // Each of the 16 points spans 22.5 degrees, so offset by half a sector (11.25)
        // before bucketing so a bearing lands in the sector it's actually closest to.
        val normalized = (((degrees % 360) + 360) % 360).toDouble()
        val idx = ((normalized + 11.25) / 22.5).toInt() % 16
        return points[idx]
    }
}

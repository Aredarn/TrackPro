package com.example.trackpro.online

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The TrackBoard v1 wire format, mirroring docs/trackpro-api/openapi.yaml in the TrackBoard
 * repo field for field. Durations are integer milliseconds; wall-clock times are ISO-8601 UTC
 * strings; enums travel as their PascalCase names.
 */

// ── Enums ──

@Serializable
enum class ApiGpsSource {
    @SerialName("Wifi") Wifi,
    @SerialName("Bluetooth") Bluetooth,
    @SerialName("PhoneGps") PhoneGps,
}

@Serializable
enum class ApiTrackType {
    @SerialName("Circuit") Circuit,
    @SerialName("Sprint") Sprint,
}

@Serializable
enum class ApiTrackVisibility {
    @SerialName("Private") Private,
    @SerialName("Published") Published,
}

@Serializable
enum class ApiSessionVisibility {
    @SerialName("Private") Private,
    @SerialName("Ranked") Ranked,
}

// ── Auth ──

@Serializable
data class RegisterRequest(val email: String, val displayName: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class ApiUser(val id: String, val email: String, val displayName: String, val role: String)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val expiresAt: String,
    val refreshToken: String,
    val user: ApiUser,
)

// ── Vehicles ──

@Serializable
data class VehicleWrite(
    val manufacturer: String,
    val model: String,
    val year: Int,
    val engineType: String,
    val horsepower: Int,
    val torque: Int? = null,
    val weight: Double,
    val topSpeed: Double? = null,
    val acceleration: Double? = null,
    val drivetrain: String,
    val fuelType: String,
    val tireType: String,
    val fuelCapacity: Double? = null,
    val transmission: String,
    val suspensionType: String? = null,
)

// ── Tracks ──

@Serializable
data class TrackPointWrite(
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val isStartPoint: Boolean = false,
    val isSectorPoint: Boolean = false,
    val sectorIndex: Int? = null,
)

@Serializable
data class TrackWrite(
    val name: String,
    val country: String,
    val type: ApiTrackType,
    val lengthMeters: Double? = null,
    val visibility: ApiTrackVisibility,
    val points: List<TrackPointWrite>,
)

/** Only the parts of a track response the app acts on. Unknown fields are ignored. */
@Serializable
data class TrackSummary(
    val id: String,
    val name: String,
    val visibility: ApiTrackVisibility,
    val ownerDisplayName: String = "",
    val geometryLocked: Boolean = false,
)

// ── Sessions ──

@Serializable
data class WeatherWrite(
    val tempC: Double? = null,
    val humidityPct: Int? = null,
    val precipitationMm: Double? = null,
    val weatherCode: Int? = null,
    val windKph: Double? = null,
    val windDirDeg: Int? = null,
    val pressureHpa: Double? = null,
)

@Serializable
data class SectorSplit(val sectorIndex: Int, val splitMs: Int)

@Serializable
data class LapWrite(
    val lapNumber: Int,
    val timeMs: Int,
    val signalGap: Boolean = false,
    val sectors: List<SectorSplit> = emptyList(),
)

@Serializable
data class SessionWrite(
    val name: String,
    val startedAt: String,
    val endedAt: String? = null,
    val vehicleId: String? = null,
    val trackId: String? = null,
    val gpsSource: ApiGpsSource,
    val visibility: ApiSessionVisibility,
    val voided: Boolean = false,
    val weather: WeatherWrite? = null,
    val appVersion: String? = null,
    val laps: List<LapWrite>,
)

// ── Leaderboards ──

@Serializable
data class LeaderboardVehicle(val manufacturer: String, val model: String, val year: Int)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val lapTimeMs: Int,
    val gapToLeaderMs: Int = 0,
    val sectors: List<SectorSplit> = emptyList(),
    val vehicle: LeaderboardVehicle? = null,
    val setAt: String,
    val gpsSource: ApiGpsSource,
)

@Serializable
data class Leaderboard(
    val trackId: String,
    val trackName: String,
    val entries: List<LeaderboardEntry>,
    val me: LeaderboardEntry? = null,
)

// ── Errors ──

/** RFC 7807 problem body. `code` is set on some 409s, e.g. TrackGeometryLocked. */
@Serializable
data class ProblemDetails(
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val code: String? = null,
    val errors: Map<String, List<String>>? = null,
)

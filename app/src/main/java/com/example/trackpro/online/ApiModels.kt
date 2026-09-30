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

/** A vehicle as the server returns it. Mapped back to [VehicleWrite] to compare against local. */
@Serializable
data class VehicleResponse(
    val id: String,
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
    val photoUrl: String? = null,
) {
    fun toWrite() = VehicleWrite(
        manufacturer = manufacturer,
        model = model,
        year = year,
        engineType = engineType,
        horsepower = horsepower,
        torque = torque,
        weight = weight,
        topSpeed = topSpeed,
        acceleration = acceleration,
        drivetrain = drivetrain,
        fuelType = fuelType,
        tireType = tireType,
        fuelCapacity = fuelCapacity,
        transmission = transmission,
        suspensionType = suspensionType,
    )
}

@Serializable
data class VehiclePage(
    val items: List<VehicleResponse>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
)


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

/** One page of the caller's own tracks (`GET /tracks?mine=true`), without their geometry. */
@Serializable
data class TrackPage(
    val items: List<TrackSummary>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
)

/** A whole track as the server returns it. Points arrive in `seq` order. */
@Serializable
data class TrackDetail(
    val id: String,
    val name: String,
    val country: String = "",
    val type: ApiTrackType = ApiTrackType.Circuit,
    val visibility: ApiTrackVisibility,
    val lengthMeters: Double? = null,
    /** Same shape up and down, so the write type serves for reading too. */
    val points: List<TrackPointWrite> = emptyList(),
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

/** A session in the caller's list (`GET /sessions`): enough to decide whether to fetch it. */
@Serializable
data class SessionSummary(
    val id: String,
    val startedAt: String,
    val trackId: String? = null,
    val vehicleId: String? = null,
    val lapCount: Int = 0,
)

@Serializable
data class SessionPage(
    val items: List<SessionSummary>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
)

/** A whole session as the server returns it. Laps keep the upload's shape; extra fields are ignored. */
@Serializable
data class SessionDetail(
    val id: String,
    val name: String,
    val startedAt: String,
    val endedAt: String? = null,
    val trackId: String? = null,
    val vehicleId: String? = null,
    val gpsSource: ApiGpsSource,
    val voided: Boolean = false,
    val weather: WeatherWrite? = null,
    val laps: List<LapWrite> = emptyList(),
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

// ── Profile ──

@Serializable
data class ProfileResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String = "Driver",
    val bio: String? = null,
    val country: String? = null,
    val avatarUrl: String? = null,
    val memberSince: String? = null,
)

/** Partial update: a null field is left out of the JSON and keeps its value on the server. */
@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val country: String? = null,
)

@Serializable
data class ProfileVehicleRef(
    val id: String,
    val manufacturer: String,
    val model: String,
    val year: Int,
    val photoUrl: String? = null,
)

@Serializable
data class PersonalBest(
    val trackId: String,
    val trackName: String,
    val country: String = "",
    val trackType: ApiTrackType = ApiTrackType.Circuit,
    val bestLapMs: Int,
    val setAt: String,
    val vehicle: ProfileVehicleRef? = null,
    val lapCount: Int = 0,
    val rank: Int? = null,
    val fieldSize: Int? = null,
    val rankedLapMs: Int? = null,
)

@Serializable
data class ProfileStats(
    val sessionCount: Int = 0,
    val lapCount: Int = 0,
    val trackCount: Int = 0,
    val vehicleCount: Int = 0,
    val distanceKm: Double? = null,
    val firstSessionAt: String? = null,
    val lastSessionAt: String? = null,
    val mainVehicle: ProfileVehicleRef? = null,
    val personalBests: List<PersonalBest> = emptyList(),
)

@Serializable
enum class MediaKind {
    @SerialName("Avatar") Avatar,
    @SerialName("VehiclePhoto") VehiclePhoto,
}

@Serializable
data class UploadRequest(val kind: MediaKind, val vehicleId: String? = null, val contentType: String = "image/jpeg")

@Serializable
data class UploadTarget(val uploadUrl: String, val path: String, val publicUrl: String)

@Serializable
data class SetMediaRequest(val path: String)

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

// ── Events (track days) ──

@Serializable
enum class ApiEventStatus {
    @SerialName("Upcoming") Upcoming,
    @SerialName("Live") Live,
    @SerialName("Finished") Finished,
}

@Serializable
data class EventGroup(val id: String, val name: String)

@Serializable
data class EventSummary(
    val id: String,
    val name: String,
    val trackId: String,
    val trackName: String,
    val trackCountry: String = "",
    val startsAt: String,
    val endsAt: String,
    val status: ApiEventStatus,
    val hostDisplayName: String = "",
    val entryCount: Int = 0,
    val isHost: Boolean = false,
    val isJoined: Boolean = false,
    val myGroupId: String? = null,
    /** Only sent to the host. */
    val joinCode: String? = null,
)

@Serializable
data class EventEntry(val userId: String, val displayName: String, val groupId: String? = null)

@Serializable
data class EventDetail(
    val event: EventSummary,
    val groups: List<EventGroup> = emptyList(),
    val entries: List<EventEntry> = emptyList(),
)

@Serializable
data class JoinEventRequest(val code: String, val groupId: String? = null)

@Serializable
data class SetEntryGroupRequest(val groupId: String?)

@Serializable
data class EventBoardEntry(
    val rank: Int? = null,
    val groupRank: Int? = null,
    val userId: String,
    val displayName: String,
    val groupId: String? = null,
    val bestLapMs: Int? = null,
    val gapToLeaderMs: Int? = null,
    val gapToGroupLeaderMs: Int? = null,
    val lapCount: Int = 0,
    val lastLapMs: Int? = null,
    val lastLapIsBest: Boolean = false,
    val onTrack: Boolean = false,
    val lastActivityAt: String? = null,
    val vehicle: LeaderboardVehicle? = null,
    val gpsSource: ApiGpsSource? = null,
)

@Serializable
data class EventBoard(
    val eventId: String,
    val name: String,
    val trackId: String,
    val trackName: String,
    val status: ApiEventStatus,
    val startsAt: String,
    val endsAt: String,
    val groups: List<EventGroup> = emptyList(),
    val entries: List<EventBoardEntry> = emptyList(),
    val generatedAt: String,
)

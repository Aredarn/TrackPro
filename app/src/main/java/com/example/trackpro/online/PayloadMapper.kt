package com.example.trackpro.online

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.utilities.toLapTimeMillisOrNull
import com.example.trackpro.models.GpsProviderType
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Turns Room rows into TrackBoard payloads. Pure: no Android, no I/O, so every rule here is
 * covered by plain JVM tests.
 *
 * Values are fitted to the server's validation ranges *before* sending. The server rejects a
 * whole request over one bad field, so an optional value out of range is dropped rather than
 * costing the driver the entire session — Open-Meteo's surface pressure at a high-altitude
 * circuit, for instance, sits below the server's 800 hPa floor.
 */
object PayloadMapper {

    // ── Vehicles ──

    /** Null when a required field cannot be sent; the session then goes up without a vehicle. */
    fun vehicle(v: VehicleInformationData): VehicleWrite? {
        val manufacturer = v.manufacturer.required(100) ?: return null
        val model = v.model.required(100) ?: return null
        val engineType = v.engineType.required(50) ?: return null
        val drivetrain = v.drivetrain.required(20) ?: return null
        val fuelType = v.fuelType.required(20) ?: return null
        val tireType = v.tireType.required(50) ?: return null
        val transmission = v.transmission.required(50) ?: return null
        if (v.year !in 1885..2100 || v.horsepower !in 0..5000) return null
        if (!(v.weight >= 0.1 && v.weight <= 10_000.0)) return null

        return VehicleWrite(
            manufacturer = manufacturer,
            model = model,
            year = v.year,
            engineType = engineType,
            horsepower = v.horsepower,
            torque = v.torque?.takeIf { it in 0..5000 },
            weight = v.weight,
            topSpeed = v.topSpeed.inRange(0.0, 1000.0),
            acceleration = v.acceleration.inRange(0.0, 60.0),
            drivetrain = drivetrain,
            fuelType = fuelType,
            tireType = tireType,
            fuelCapacity = v.fuelCapacity.inRange(0.0, 1000.0),
            transmission = transmission,
            suspensionType = v.suspensionType?.trim()?.take(100)?.ifBlank { null },
        )
    }

    // ── Tracks ──

    /**
     * Null when the track cannot be sent at all (fewer than two usable points). Points keep
     * the app's own order — row id order, which is what the timing code reads — because the
     * gates are derived from that order.
     */
    fun track(
        track: TrackMainData,
        points: List<TrackCoordinatesData>,
        visibility: ApiTrackVisibility,
    ): TrackWrite? {
        if (points.size !in 2..20_000) return null
        if (points.any { it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 }) return null

        return TrackWrite(
            name = track.trackName.trim().take(120).ifBlank { return null },
            // The builder can save a track with no country; the server requires one.
            country = track.country.trim().take(100).ifBlank { "Unknown" },
            type = if (track.type.equals("Sprint", ignoreCase = true)) ApiTrackType.Sprint else ApiTrackType.Circuit,
            // Stored in km (tracks.json and the builder both); the contract wants metres.
            lengthMeters = track.totalLength?.takeIf { it > 0 }?.let { it * 1000 }?.takeIf { it <= 1_000_000 },
            visibility = visibility,
            points = points.mapIndexed { index, p ->
                TrackPointWrite(
                    seq = index,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    altitude = p.altitude.inRange(-500.0, 9000.0),
                    isStartPoint = p.isStartPoint,
                    isSectorPoint = p.isSectorPoint,
                    sectorIndex = p.sectorIndex?.takeIf { it in 0..999 },
                )
            },
        )
    }

    /**
     * Identifies a track by what timing depends on: point order, position and the start flag.
     * Sector flags are left out on purpose — the driver can re-slice sectors on any track,
     * premade ones included, without that making it a different track.
     */
    fun timingFingerprint(points: List<TrackCoordinatesData>): String =
        sha256(points.joinToString(";") {
            String.format(Locale.ROOT, "%.7f,%.7f,%d", it.latitude, it.longitude, if (it.isStartPoint) 1 else 0)
        }).take(32)

    /**
     * The server id every install uses for a given premade track. Derived from its name and
     * timing geometry, so all drivers of the bundled Hungaroring land on one leaderboard with
     * no coordination — and a future edit to its bundled geometry becomes a different track,
     * rather than silently changing the gates under laps already posted.
     */
    fun premadeTrackId(name: String, fingerprint: String): String =
        UUID.nameUUIDFromBytes("trackpro/premade/v1/$name/$fingerprint".toByteArray(Charsets.UTF_8)).toString()

    // ── Sessions ──

    /**
     * Null when there is nothing to post: no recorded GPS source (sessions from before it was
     * captured), or no timed lap. Laps still in progress or invalid are never sent.
     */
    fun session(
        session: SessionData,
        laps: List<LapTimeData>,
        sectors: List<SectorTimeData>,
        remoteTrackId: String,
        remoteVehicleId: String?,
        appVersion: String?,
    ): SessionWrite? {
        val source = gpsSource(session.gpsSource) ?: return null
        val sectorsByLap = sectors.groupBy { it.lapid }

        val lapWrites = laps
            .mapNotNull { lap ->
                val ms = lap.laptime.toLapTimeMillisOrNull() ?: return@mapNotNull null
                if (lap.lapnumber !in 1..10_000 || ms !in 1..3_600_000L) return@mapNotNull null
                LapWrite(
                    lapNumber = lap.lapnumber,
                    timeMs = ms.toInt(),
                    signalGap = lap.signalGap,
                    sectors = sectorsByLap[lap.id].orEmpty()
                        .filter { it.sectorIndex in 0..49 && it.splitTimeMs in 1..3_600_000L }
                        .distinctBy { it.sectorIndex }
                        .sortedBy { it.sectorIndex }
                        .map { SectorSplit(it.sectorIndex, it.splitTimeMs.toInt()) },
                )
            }
            // The server rejects duplicate lap numbers; keep the first of any repeat.
            .distinctBy { it.lapNumber }
            .sortedBy { it.lapNumber }
            .take(500)

        if (lapWrites.isEmpty()) return null

        return SessionWrite(
            name = session.eventType.trim().take(200).ifBlank { "Session" },
            startedAt = iso(session.startTime),
            endedAt = session.endTime?.takeIf { it >= session.startTime }?.let(::iso),
            vehicleId = remoteVehicleId,
            trackId = remoteTrackId,
            gpsSource = source,
            visibility = ApiSessionVisibility.Ranked,
            voided = session.voided,
            weather = weather(session),
            appVersion = appVersion?.take(40),
            laps = lapWrites,
        )
    }

    fun gpsSource(stored: String?): ApiGpsSource? =
        when (runCatching { GpsProviderType.valueOf(stored ?: "") }.getOrNull()) {
            GpsProviderType.WIFI -> ApiGpsSource.Wifi
            GpsProviderType.BLUETOOTH -> ApiGpsSource.Bluetooth
            GpsProviderType.PHONE_GPS -> ApiGpsSource.PhoneGps
            null -> null
        }

    private fun weather(s: SessionData): WeatherWrite? {
        val w = WeatherWrite(
            tempC = s.weatherTempC.inRange(-60.0, 70.0),
            humidityPct = s.weatherHumidityPct?.takeIf { it in 0..100 },
            precipitationMm = s.weatherPrecipitationMm.inRange(0.0, 1000.0),
            weatherCode = s.weatherCode?.takeIf { it in 0..99 },
            windKph = s.weatherWindKph.inRange(0.0, 500.0),
            windDirDeg = s.weatherWindDirDeg?.takeIf { it in 0..360 },
            pressureHpa = s.weatherPressureHpa.inRange(800.0, 1100.0),
        )
        return w.takeUnless { it == WeatherWrite() }
    }

    // ── Change detection ──

    /** What the server last accepted is remembered by this, so unchanged records are skipped. */
    fun hash(vehicle: VehicleWrite): String = sha256(OkHttpTrackBoardApi.json.encodeToString(VehicleWrite.serializer(), vehicle))

    fun hash(track: TrackWrite): String = sha256(OkHttpTrackBoardApi.json.encodeToString(TrackWrite.serializer(), track))

    fun hash(session: SessionWrite): String = sha256(OkHttpTrackBoardApi.json.encodeToString(SessionWrite.serializer(), session))

    // ── Helpers ──

    private fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    private fun String.required(max: Int): String? = trim().take(max).ifBlank { null }

    private fun Double?.inRange(min: Double, max: Double): Double? =
        this?.takeIf { !it.isNaN() && it >= min && it <= max }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

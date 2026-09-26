package com.example.trackpro.dataClasses

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_data",
    foreignKeys = [androidx.room.ForeignKey(
        entity = VehicleInformationData::class,
        parentColumns = ["vehicleId"],
        childColumns = ["vehicleId"],
        onDelete = androidx.room.ForeignKey.CASCADE
    )],
    indices = [Index("vehicleId"), Index("trackId")]

)
data class SessionData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val eventType: String,
    val vehicleId: Long?,
    val trackId: Long? = null,

    // Conditions captured automatically at session start from the session's GPS position.
    // All nullable: sessions recorded offline, without a GPS fix, or before this feature
    // existed simply have no weather. Stored in canonical metric (C, km/h, hPa, mm) to match
    // how every other measurement in the app is stored; converted at display time.
    val weatherTempC: Double? = null,
    val weatherHumidityPct: Int? = null,
    val weatherPrecipitationMm: Double? = null,
    /** WMO weather interpretation code - see WeatherService.describeCode(). */
    val weatherCode: Int? = null,
    val weatherWindKph: Double? = null,
    val weatherWindDirDeg: Int? = null,
    val weatherPressureHpa: Double? = null,

    /**
     * A session the driver discarded at the end.
     *
     * Voided rather than deleted: a red-flagged run, a session started by mistake, a lap
     * set on the wrong tyres - the driver wants it out of their best times, not gone. The
     * rows survive, the session is stamped VOID in the lists, and it stops counting toward
     * anything. Deleting is still available separately and still means deleting.
     */
    val voided: Boolean = false
)

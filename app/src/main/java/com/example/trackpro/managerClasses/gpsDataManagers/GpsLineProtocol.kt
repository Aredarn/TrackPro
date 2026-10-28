package com.example.trackpro.managerClasses.gpsDataManagers

import com.example.trackpro.dataClasses.RawGPSData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream

// Shared by ESPTcpClient and BluetoothClassicClient: both transports deliver the
// same newline-delimited GPS JSON (plus RATE_OK/RATE_ERR command replies), only
// the underlying byte source (Socket vs BluetoothSocket) differs.

internal val gpsJsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

@Serializable
data class RawGPSDataRaw(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val satellites: Int = 0,
    val valid: Boolean = true,
    val timestamp: String
)

internal fun RawGPSDataRaw.toEntity(timestamp: Long): RawGPSData = RawGPSData(
    sessionid = 0L,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    fixQuality = satellites,
    valid = valid,
    timestamp = timestamp
)

/**
 * Turns the module's own fix time into a phone-scale timestamp.
 *
 * The firmware sends the GPS receiver's UTC time of day as "H:M:S.cc" - fields not zero-padded,
 * so the last one is centiseconds as a plain integer ("5" is 50 ms, not 500). Stamping a fix when
 * it *arrives* instead folds Bluetooth/Wi-Fi latency into every timing: fixes come in bursts a
 * few ms apart and then not at all for 300 ms, which is far more than a sprint's resolution.
 *
 * Time of day has no date and wraps at midnight, and the phone's clock is not the GPS clock, so
 * the module's time is shifted onto the phone's clock. Only the intervals between fixes matter
 * for timing; the shift just keeps stored values near wall-clock time. It starts at the first
 * fix's receipt-minus-fix gap and may only fall, by at most [SLEW_MS] per fix, towards the
 * smallest gap seen (the least-delayed fix). Letting it fall freely would squeeze the intervals
 * around a burst; slewing bounds the error in any one interval to [SLEW_MS]. Timestamps are
 * forced strictly increasing.
 */
internal class GpsClock {
    private var dayShiftMs = 0L
    private var lastTimeOfDay = -1L
    private var shiftToPhone = Long.MAX_VALUE
    private var lastStamp = Long.MIN_VALUE

    /** Forget the link; the next fix starts a fresh offset. Call when a connection is (re)opened. */
    fun reset() {
        dayShiftMs = 0L
        lastTimeOfDay = -1L
        shiftToPhone = Long.MAX_VALUE
        lastStamp = Long.MIN_VALUE
    }

    /** [receivedAt] is returned unchanged when the module's time is missing or not yet valid. */
    fun stamp(moduleTime: String, receivedAt: Long): Long {
        val timeOfDay = parseTimeOfDayMs(moduleTime) ?: return receivedAt

        if (lastTimeOfDay >= 0 && timeOfDay < lastTimeOfDay - HALF_DAY_MS) dayShiftMs += DAY_MS
        lastTimeOfDay = timeOfDay

        val moduleMs = timeOfDay + dayShiftMs
        val gap = receivedAt - moduleMs
        shiftToPhone = if (shiftToPhone == Long.MAX_VALUE) gap else maxOf(gap, shiftToPhone - SLEW_MS).coerceAtMost(shiftToPhone)
        val stamped = maxOf(moduleMs + shiftToPhone, lastStamp + 1)
        lastStamp = stamped
        return stamped
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val HALF_DAY_MS = DAY_MS / 2
        const val SLEW_MS = 1L
    }
}

/**
 * "H:M:S.cc" to milliseconds since midnight, or null if it does not parse or is the firmware's
 * "no valid time" placeholder (0:0:0.0).
 */
internal fun parseTimeOfDayMs(text: String): Long? {
    val clock = text.substringBefore('.').split(':')
    if (clock.size != 3) return null
    val h = clock[0].trim().toIntOrNull() ?: return null
    val m = clock[1].trim().toIntOrNull() ?: return null
    val s = clock[2].trim().toIntOrNull() ?: return null
    val cs = if ('.' in text) text.substringAfter('.').trim().toIntOrNull() ?: return null else 0
    if (h !in 0..23 || m !in 0..59 || s !in 0..60 || cs !in 0..99) return null
    if (h == 0 && m == 0 && s == 0 && cs == 0) return null
    return ((h * 60L + m) * 60 + s) * 1000 + cs * 10L
}

// A command reply line looks like "RATE_OK:20" — returns the confirmed Hz, or
// null if this line isn't a rate acknowledgement (i.e. it's GPS JSON instead).
internal fun parseRateAck(line: String): Int? {
    if (!line.startsWith("RATE_OK:")) return null
    return line.removePrefix("RATE_OK:").trim().toIntOrNull()
}

class DelimitedInputStreamReader(
    private val input: InputStream,
    private val delimiter: ByteArray
) {
    private val buffer = ByteArrayOutputStream()

    fun read(target: ByteArray): Int {
        try {
            while (true) {
                val byte = input.read()
                if (byte == -1) return -1

                buffer.write(byte)

                if (endsWithDelimiter()) {
                    val fullData = buffer.toByteArray()
                    val length = fullData.size - delimiter.size

                    // Ensure we don't overflow the target buffer
                    val finalSize = if (length > target.size) target.size else length
                    System.arraycopy(fullData, 0, target, 0, finalSize)

                    buffer.reset()
                    return finalSize
                }

                // Emergency flush if buffer gets too large (corrupt stream protection)
                if (buffer.size() > 2048) buffer.reset()
            }
        } catch (_: Exception) {
            return -1
        }
    }

    private fun endsWithDelimiter(): Boolean {
        val data = buffer.toByteArray()
        if (data.size < delimiter.size) return false
        for (i in delimiter.indices) {
            if (data[data.size - delimiter.size + i] != delimiter[i]) return false
        }
        return true
    }
}

class BufferPool(private val bufferSize: Int, poolSize: Int) {
    private val pool = ArrayDeque<ByteArray>(poolSize).apply {
        repeat(poolSize) { add(ByteArray(bufferSize)) }
    }

    @Synchronized
    fun obtain(): ByteArray = pool.removeFirstOrNull() ?: ByteArray(bufferSize)

    @Synchronized
    fun recycle(buffer: ByteArray) {
        if (pool.size < 10) pool.addLast(buffer)
    }
}

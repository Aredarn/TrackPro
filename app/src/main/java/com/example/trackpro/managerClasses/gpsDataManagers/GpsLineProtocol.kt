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

internal fun RawGPSDataRaw.toEntity(): RawGPSData = RawGPSData(
    sessionid = 0L,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    fixQuality = satellites,
    valid = valid,
    // Stamped on receipt rather than the ESP32-reported timestamp string:
    // elapsed-time math (0-60, quarter mile, etc.) needs consistent relative
    // precision between samples, not the module's own timestamp.
    timestamp = System.currentTimeMillis()
)

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

package com.example.trackpro.ManagerClasses
import convertToUnixTimestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

// Simple RawGPSData data class to store the received GPS data
@Serializable
data class RawGPSData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Float?,
    val satellites: Int?,
    val timestamp: Long
)

fun com.example.trackpro.ManagerClasses.RawGPSData.toDataClass(): com.example.trackpro.DataClasses.RawGPSData {
    return com.example.trackpro.DataClasses.RawGPSData(
        sessionid = 0,  // Replace with the actual session ID as needed
        latitude = this.latitude,
        longitude = this.longitude,
        altitude = this.altitude,
        timestamp = this.timestamp,
        speed = this.speed,
        fixQuality = this.satellites
    )
}


class ESPTcpClient(
    private val serverAddress: String,
    private val port: Int,
    private val onMessageReceived: (RawGPSData) -> Unit,  // Callback when new data is received
    private val onConnectionStatusChanged: (Boolean) -> Unit  // Callback for connection status
) {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null

    // Start the connection to the server
    fun connect() {
        Thread {
            try {
                socket = Socket(serverAddress, port)
                //reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                val inputStream = socket!!.getInputStream()
                val buffer = ByteArray(256) // Match ESP32 buffer size

                onConnectionStatusChanged(true)  // Notify that the connection was successful

                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        try {
                            val message = String(buffer, 0, bytesRead).trim()
                            val gpsData = parseGpsData(message)
                            onMessageReceived(gpsData)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onConnectionStatusChanged(false)  // Notify that the connection failed
            }
        }.start()
    }

    // Disconnect from the server
    fun disconnect() {
        try {
            socket?.close()
            reader?.close()
            onConnectionStatusChanged(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Simple function to parse the incoming data (assumed to be JSON format)
    private fun parseGpsData(data: String): RawGPSData {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val gpsDataRaw = json.decodeFromString<RawGPSDataRaw>(data)

            // Convert timestamp from String to Long
            val convertedTimestamp = convertToUnixTimestamp(gpsDataRaw.timestamp)

            gpsDataRaw.toRawGPSData(convertedTimestamp)

        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse GPS data: ${e.message} - Raw Data: $data")
        }
    }

    // Temporary Data Class to Handle String Timestamps
    @Serializable
    data class RawGPSDataRaw(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val speed: Float,
        val satellites: Int,
        val timestamp: String
    ) {
        fun toRawGPSData(timestampLong: Long): RawGPSData {
            return RawGPSData(latitude, longitude, altitude, speed, satellites, timestampLong)
        }
    }
}

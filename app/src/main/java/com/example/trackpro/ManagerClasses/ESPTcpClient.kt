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
                reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                onConnectionStatusChanged(true)  // Notify that the connection was successful

                // Continuously read data from the server
                while (true) {
                    val message = reader?.readLine()  // Read message from the server
                    if (message != null) {
                        try {
                            val gpsData = parseGpsData(message)
                            onMessageReceived(gpsData)
                        } catch (e: Exception) {
                            e.printStackTrace()  // If parsing fails, just log the error
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
            val json = Json { ignoreUnknownKeys = true }  // Ignore unexpected fields
            val gpsData = json.decodeFromString<RawGPSData>(data)

            // Convert timestamp from String to Long
            gpsData.copy(timestamp = convertToUnixTimestamp(gpsData.timestamp.toString()))
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse GPS data: ${e.message} - Raw Data: $data")
        }
    }


}

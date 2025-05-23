package com.example.trackpro.ManagerClasses
import android.util.Log
import convertToUnixTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

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

fun RawGPSData.toDataClass(): com.example.trackpro.DataClasses.RawGPSData {
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var running = AtomicBoolean(false)

    // Optimized JSON parser (initialize once)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Recyclable buffer pool to avoid allocation overhead
    private val bufferPool = BufferPool(256, 10)

    // Start the connection to the server
    fun connect() {
        if (running.getAndSet(true)) return

        scope.launch {
            try {
                socket = Socket(serverAddress, port)
                val inputStream = socket!!.getInputStream()

                onConnectionStatusChanged(true)

                val delimiter = "\n".toByteArray()
                val reader = DelimitedInputStreamReader(inputStream, delimiter)

                while (running.get()) {
                    val buffer = bufferPool.obtain()
                    try {
                        val bytesRead = reader.read(buffer)
                        if (bytesRead > 0) {
                            processChunk(buffer, bytesRead)
                        }
                    } finally {
                        bufferPool.recycle(buffer)
                    }
                }
            } catch (e: Exception) {
                Log.e("ESPTcpClient", "Connection error: ${e.message}", e) // <--- ADD THIS
                onConnectionStatusChanged(false)
                disconnect()
            }
        }

    }

    private suspend fun processChunk(buffer: ByteArray, length: Int) {
        val message = buffer.decodeToString(0, length).trim()
        if (message.isNotEmpty()) {
            // Offload parsing to separate coroutine to avoid blocking I/O thread
            val parsed = withContext(Dispatchers.Default) {
                parseGpsData(message)
            }
            onMessageReceived(parsed)
        }
    }

    //@OptIn(ExperimentalSerializationApi::class)
    private fun parseGpsData(data: String): RawGPSData {
        return json.decodeFromString<RawGPSDataRaw>(data)
            .let { raw ->
                RawGPSData(
                   latitude = raw.latitude,
                    longitude = raw.longitude,
                    altitude = raw.altitude,
                    speed = raw.speed,
                    satellites = raw.satellites,
                    timestamp = convertToUnixTimestamp(raw.timestamp)
                )
            }
    }

    // Disconnect from the server
    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onConnectionStatusChanged(false)
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
    )

    // Helper class for message delimiting
    class DelimitedInputStreamReader(
        private val input: InputStream,
        private val delimiter: ByteArray
    ) {
        private val buffer = ByteArrayOutputStream()

        fun read(target: ByteArray): Int {
            while (true) {
                val byte = input.read()
                if (byte == -1) return -1

                buffer.write(byte)

                if (endsWithDelimiter()) {
                    val data = buffer.toByteArray()
                    val length = data.size - delimiter.size
                    System.arraycopy(data, 0, target, 0, length)
                    buffer.reset()
                    return length
                }

                if (buffer.size() > 4096) {
                    buffer.reset()
                }
            }
        }

        private fun endsWithDelimiter(): Boolean {
            val data = buffer.toByteArray()
            if (data.size < delimiter.size) return false
            return delimiter.indices.all { i ->
                data[data.size - delimiter.size + i] == delimiter[i]
            }
        }
    }

    // Buffer pooling to reduce GC pressure
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

}

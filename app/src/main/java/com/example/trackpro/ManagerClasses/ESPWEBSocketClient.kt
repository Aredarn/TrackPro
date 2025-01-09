package com.example.trackpro.ManagerClasses

import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class ESPWebSocketClient(
    private val url: String,
    private val onMessageReceived: (String) -> Unit, // Callback when data is received
    private val onConnectionStatusChanged: (Boolean) -> Unit // Callback to notify connection status
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)  // Send pings every 10 seconds to keep connection alive
        .retryOnConnectionFailure(true)      // Automatically retry connection on failure
        .build()

    private var webSocket: WebSocket? = null

    // Connect to the WebSocket server
    fun connect() {
        val request = Request.Builder()
            .url(url)  // WebSocket URL (e.g., ws://192.168.4.1:81)
            .build()

        // Create a WebSocket connection
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnectionStatusChanged(true) // Notify when the connection is open
                println("WebSocket connected to ESP32!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived(text) // Pass received data to the callback
                println("Received data: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessageReceived(bytes.utf8()) // Pass received data to the callback
                println("Received data (binary): ${bytes.utf8()}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                onConnectionStatusChanged(false) // Notify when connection is closing
                println("WebSocket closing: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onConnectionStatusChanged(false) // Notify when connection is closed
                println("WebSocket closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onConnectionStatusChanged(false) // Notify on failure
                t.printStackTrace()
                println("WebSocket failure: ${t.message}")
            }
        })
    }

    // Send a message to the WebSocket server
    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    // Disconnect the WebSocket
    fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
        println("WebSocket disconnected.")
    }

    // Optionally, trigger a ping manually if needed
    fun sendPing() {
        webSocket?.send("ping")
    }
}

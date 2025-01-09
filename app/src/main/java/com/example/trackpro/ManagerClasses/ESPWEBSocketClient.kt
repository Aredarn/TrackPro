package com.example.trackpro.ManagerClasses

import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class ESPWebSocketClient(
    private val url: String,
    var onMessageReceived: (String) -> Unit,
    var onConnectionStatusChanged: (Boolean) -> Unit
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(1, TimeUnit.SECONDS) // Keep the connection alive by sending pings
        .build()

    private var webSocket: WebSocket? = null
    private var retryAttempts = 0
    private val maxRetries = 5 // Maximum retry attempts

    // Function to connect to the WebSocket
    fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()

        // Establish a WebSocket connection
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnectionStatusChanged(true)
                retryAttempts = 0 // Reset retry attempts on success
                println("WebSocket Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived(text) // Handle incoming messages
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessageReceived(bytes.utf8()) // Handle binary messages
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                onConnectionStatusChanged(false)
                println("WebSocket Closing")
                retryConnection() // Retry the connection after closing
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onConnectionStatusChanged(false)
                println("WebSocket Closed")
                retryConnection() // Retry the connection after closing
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onConnectionStatusChanged(false)
                println("WebSocket Failure: ${t.message}")
                retryConnection() // Retry the connection after failure
                t.printStackTrace()
            }
        })
    }

    // Retry connection with delay between attempts
    private fun retryConnection() {
        if (retryAttempts < maxRetries) {
            retryAttempts++
            println("Retrying connection attempt #$retryAttempts...")
            // Retry after 3 seconds delay
            Thread.sleep(3000)
            connect() // Try reconnecting
        } else {
            println("Max retries reached. Could not establish WebSocket connection.")
        }
    }

    // Function to send a message through the WebSocket
    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    // Function to disconnect the WebSocket
    fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
    }
}


package com.example.trackpro.ManagerClasses

import android.os.Handler
import android.os.Looper
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class ESPWebSocketClient(
    private val url: String,
    var onMessageReceived: (String) -> Unit,
    var onConnectionStatusChanged: (Boolean) -> Unit
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS) // Keep the connection alive by sending pings
        .build()

    private var webSocket: WebSocket? = null
    private var retryAttempts = 0
    private val maxRetries = 5 // Maximum retry attempts

    // Function to connect to the WebSocket
    fun connect() {
        disconnect() // Close any existing connection
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
            val backoffDelay = (2.0.pow(retryAttempts.toDouble()) * 1000).toLong() // Exponential backoff
            println("Retrying connection attempt #$retryAttempts after ${backoffDelay}ms...")
            Thread.sleep(backoffDelay)
            retryAttempts++
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


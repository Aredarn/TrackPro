package com.example.trackpro.managerClasses.gpsDataManagers

import android.util.Log
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.models.CommandableGpsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** How a [LineStreamGpsProvider] paces its reconnects and decides a link has died. */
data class LinkTiming(
    /** Wait before the first retry after a link that had been delivering data drops. */
    val initialBackoffMs: Long = 1_000,
    /** Retries back off by doubling, up to this. */
    val maxBackoffMs: Long = 10_000,
    /**
     * A link that delivers nothing for this long is treated as dead and reopened. The module
     * streams at 1 Hz or faster, so this is several missed fixes in a row at any rate.
     */
    val stallTimeoutMs: Long = 5_000,
    /** How often the stall watchdog checks. */
    val watchdogTickMs: Long = 500
)

/**
 * A GPS source that streams newline-delimited GPS JSON over some byte connection, and keeps
 * that connection up for as long as it is started.
 *
 * Shared by the Wi-Fi TCP and Bluetooth SPP transports, which differ only in how a connection
 * is opened. Reading lines, parsing fixes and rate acknowledgements, sending commands and -
 * above all - reconnecting live here once.
 *
 * Reconnecting is why this exists. Both transports used to connect once and, on any error or
 * closed stream, mark themselves stopped for good: one Wi-Fi blip mid-session ended GPS for
 * the rest of the session, with a small NO SIGNAL label as the only sign. A started link now
 * retries with backoff until [stop].
 *
 * Errors alone are not enough to notice a dead link, which is why there is also a stall
 * watchdog. When a Wi-Fi access point simply disappears - the ESP32 out of range, or powered
 * off - no FIN or RST ever arrives, and a blocking read waits forever without failing. So a
 * link that goes quiet for [LinkTiming.stallTimeoutMs] is closed from the outside, which
 * unblocks the read and sends the loop round again.
 */
abstract class LineStreamGpsProvider(
    private val tag: String,
    private val timing: LinkTiming = LinkTiming()
) : CommandableGpsProvider {

    /** An open byte connection to the GPS module. Closing it must unblock a pending read. */
    protected interface Link : Closeable {
        val input: InputStream
        val output: OutputStream
    }

    /**
     * Opens a connection, or throws if the module cannot be reached right now. Called again
     * after every failure, so it should not hold anything from a previous attempt.
     */
    protected abstract suspend fun open(): Link

    private val _connectionStatus = MutableStateFlow(false)
    override val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    private val _gpsFlow = MutableStateFlow<RawGPSData?>(null)
    override val gpsFlow: StateFlow<RawGPSData?> = _gpsFlow.asStateFlow()
    private val _confirmedRateHz = MutableStateFlow<Int?>(null)
    override val confirmedRateHz: StateFlow<Int?> = _confirmedRateHz.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Whether this provider should be connected - true from [start] until [stop]. */
    private val wanted = AtomicBoolean(false)
    @Volatile private var loopJob: Job? = null

    /**
     * Makes [stop] and a loop registering a freshly opened link mutually exclusive. Without
     * it a stop() landing between open() returning and the link being registered could not
     * close that link, and a loop already cancelled would go on reading it.
     */
    private val lifecycleLock = Any()

    /** The link currently open, if any, so [stop] can close it from outside the read. */
    private val currentLink = AtomicReference<Link?>(null)

    /**
     * Why the last link attempt failed, or null while connected.
     *
     * The reconnect loop already knew this and only wrote it to logcat, which is exactly
     * as useful as nothing to someone who installed the APK from a GitHub release. The
     * connection-test screen turns it into a recovery instruction.
     */
    private val _lastFailure = MutableStateFlow<String?>(null)
    val lastFailure: StateFlow<String?> = _lastFailure.asStateFlow()
    @Volatile private var outputStream: OutputStream? = null
    private val writeMutex = Mutex()
    private val bufferPool = BufferPool(512, 10)

    // start() and stop() arrive from more than one thread - the GpsManager source collector
    // on IO, Settings on main - so both take the lock rather than just the flag.
    override fun start() {
        synchronized(lifecycleLock) {
            if (wanted.getAndSet(true)) return
            loopJob = scope.launch { connectionLoop() }
        }
    }

    override fun stop() {
        synchronized(lifecycleLock) {
            wanted.set(false)
            loopJob?.cancel()
            loopJob = null
            // Closing is what actually interrupts a blocking read; cancellation alone cannot.
            currentLink.getAndSet(null)?.let { runCatching { it.close() } }
        }
        outputStream = null
        _connectionStatus.value = false
    }

    /**
     * Drops the current link and reconnects straight away, e.g. to pick up a new target.
     * A stop/start pair is safe to use for this: every loop checks its own job rather than
     * [wanted], which the start() here sets straight back, so the cancelled loop winds down
     * and never registers a link or touches the connection status again.
     */
    protected fun reconnectNow() {
        if (!wanted.get()) return
        stop()
        start()
    }

    private suspend fun connectionLoop() {
        val self = currentCoroutineContext()[Job] ?: return
        var backoffMs = timing.initialBackoffMs
        while (self.isActive) {
            val deliveredData = try {
                runLink(self)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (self.isActive) {
                    Log.w(tag, "Link failed: ${e.message}")
                    _lastFailure.value = e.message ?: (e::class.simpleName ?: "Link failed")
                }
                false
            }
            if (!self.isActive) break

            // A link that was actually working is worth retrying promptly; repeated failures
            // to connect at all back off so a module that is simply switched off does not
            // cost a connect attempt every second for as long as the app is open.
            if (deliveredData) backoffMs = timing.initialBackoffMs
            Log.i(tag, "Reconnecting in $backoffMs ms")
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(timing.maxBackoffMs)
        }
    }

    /**
     * Opens one link and reads it until it drops. Returns whether it delivered any data.
     * [self] is the loop's own job: the only authority on whether this loop should go on.
     */
    private suspend fun runLink(self: Job): Boolean {
        val link = open()
        // A stop() that landed while open() was blocking could not close this link, because
        // it had not been registered yet. Registering under the same lock as stop() means
        // either stop() sees the link and closes it, or this sees the cancellation and does.
        val registered = synchronized(lifecycleLock) {
            if (self.isActive) {
                currentLink.set(link)
                true
            } else {
                false
            }
        }
        if (!registered) {
            runCatching { link.close() }
            return false
        }
        outputStream = link.output
        _connectionStatus.value = true
        _lastFailure.value = null

        val delivered = AtomicBoolean(false)
        try {
            coroutineScope {
                val lastLineAt = AtomicLong(System.currentTimeMillis())
                val watchdog = launch {
                    while (isActive) {
                        delay(timing.watchdogTickMs)
                        val silentFor = System.currentTimeMillis() - lastLineAt.get()
                        if (silentFor > timing.stallTimeoutMs) {
                            Log.w(tag, "No data for $silentFor ms; treating the link as dead")
                            runCatching { link.close() }
                            break
                        }
                    }
                }
                try {
                    // Blocking reads on this IO thread; the watchdog runs on another.
                    val reader = DelimitedInputStreamReader(link.input, LINE_DELIMITER)
                    while (self.isActive) {
                        val buffer = bufferPool.obtain()
                        try {
                            // -1 covers a closed stream and any read error alike, including
                            // the watchdog or stop() closing the link underneath it.
                            val bytesRead = reader.read(buffer)
                            if (bytesRead == -1) break
                            if (bytesRead > 0) {
                                lastLineAt.set(System.currentTimeMillis())
                                delivered.set(true)
                                processChunk(buffer, bytesRead)
                            }
                        } finally {
                            bufferPool.recycle(buffer)
                        }
                    }
                } finally {
                    watchdog.cancel()
                }
            }
        } finally {
            runCatching { link.close() }
            if (outputStream === link.output) outputStream = null
            // Only the loop that still owns the current link reports it lost. After stop(),
            // or a reconnect that has already moved on, this is someone else's status to set.
            if (currentLink.compareAndSet(link, null)) {
                _connectionStatus.value = false
            }
        }
        return delivered.get()
    }

    // Sends a command (e.g. "RATE:20\n") to the ESP32. Dispatches onto this provider's own IO
    // scope so callers (Compose onClick handlers) never block, and a Mutex serializes
    // concurrent writers against each other - reading and writing the same stream
    // concurrently is safe by contract and needs no lock between them.
    override fun sendCommand(command: String) {
        val out = outputStream ?: return
        scope.launch {
            writeMutex.withLock {
                try {
                    out.write(command.toByteArray(Charsets.US_ASCII))
                    out.flush()
                } catch (e: Exception) {
                    Log.e(tag, "sendCommand failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun processChunk(buffer: ByteArray, length: Int) {
        val message = buffer.decodeToString(0, length).trim()
        if (message.isEmpty()) return

        val ackedHz = parseRateAck(message)
        if (ackedHz != null) {
            _confirmedRateHz.value = ackedHz
            return
        }
        if (message == "RATE_ERR") {
            Log.w(tag, "GPS module rejected rate change")
            return
        }

        withContext(Dispatchers.Default) {
            try {
                val raw = gpsJsonParser.decodeFromString<RawGPSDataRaw>(message)
                _gpsFlow.value = raw.toEntity()
            } catch (e: Exception) {
                Log.e(tag, "JSON Parse Error: ${e.message} for input: $message")
            }
        }
    }

    private companion object {
        val LINE_DELIMITER = "\n".toByteArray()
    }
}

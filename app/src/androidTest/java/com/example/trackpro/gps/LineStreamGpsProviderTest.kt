package com.example.trackpro.gps

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trackpro.managerClasses.gpsDataManagers.LineStreamGpsProvider
import com.example.trackpro.managerClasses.gpsDataManagers.LinkTiming
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The reconnect loop, against a fake link standing in for the ESP32.
 *
 * Instrumented rather than a JVM test only because the provider logs through android.util.Log.
 * Timings are shrunk to milliseconds so reconnects and the stall watchdog can be exercised
 * without real seconds of waiting.
 */
@RunWith(AndroidJUnit4::class)
class LineStreamGpsProviderTest {

    private val fastTiming = LinkTiming(
        initialBackoffMs = 20,
        maxBackoffMs = 50,
        stallTimeoutMs = 300,
        watchdogTickMs = 20
    )

    /** One end of a link: the test writes lines into it as the "module". */
    private class FakeModule {
        private val toProvider = PipedOutputStream()
        val providerInput: InputStream = PipedInputStream(toProvider, 4096)

        fun send(line: String) {
            toProvider.write((line + "\n").toByteArray())
            toProvider.flush()
        }

        /** The module going away: the provider's read sees end of stream. */
        fun hangUp() = runCatching { toProvider.close() }
    }

    /** A provider whose every connection attempt opens a fresh FakeModule. */
    private inner class FakeProvider(
        private val refuseFirst: Int = 0
    ) : LineStreamGpsProvider(tag = "FakeProvider", timing = fastTiming) {
        val opens = AtomicInteger(0)
        val modules = CopyOnWriteArrayList<FakeModule>()

        override suspend fun open(): Link {
            val attempt = opens.incrementAndGet()
            if (attempt <= refuseFirst) throw IOException("module not reachable yet")
            val module = FakeModule()
            modules.add(module)
            return object : Link {
                override val input: InputStream = module.providerInput
                // Commands go nowhere; OutputStream.nullOutputStream() needs API 33.
                override val output: OutputStream = object : OutputStream() {
                    override fun write(b: Int) = Unit
                }
                override fun close() {
                    module.hangUp()
                    runCatching { module.providerInput.close() }
                }
            }
        }
    }

    private var provider: FakeProvider? = null

    @After
    fun stopProvider() {
        provider?.stop()
    }

    private fun fixLine(lat: Double) =
        """{"latitude":$lat,"longitude":19.0,"speed":100.0,"satellites":9,"valid":true,"timestamp":"0"}"""

    private suspend fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) delay(10)
        }
    }

    @Test
    fun parsesFixesFromTheLink() = runBlocking<Unit> {
        val p = FakeProvider().also { provider = it }
        p.start()
        waitUntil { p.modules.isNotEmpty() && p.connectionStatus.value }

        p.modules.last().send(fixLine(47.123))

        val fix = withTimeout(2_000) { p.gpsFlow.first { it != null } }
        assertNotNull(fix)
        assertEquals(47.123, fix!!.latitude, 1e-9)
    }

    @Test
    fun reconnectsAfterTheModuleHangsUp() = runBlocking<Unit> {
        // The regression: a dropped link used to mark the provider stopped for good, ending
        // GPS for the rest of the session.
        val p = FakeProvider().also { provider = it }
        p.start()
        waitUntil { p.modules.size == 1 && p.connectionStatus.value }
        p.modules.last().send(fixLine(47.0))

        p.modules.last().hangUp()

        waitUntil { p.modules.size >= 2 && p.connectionStatus.value }
        p.modules.last().send(fixLine(47.5))
        waitUntil { p.gpsFlow.value?.latitude == 47.5 }
    }

    @Test
    fun keepsTryingUntilTheModuleIsReachable() = runBlocking<Unit> {
        // Switched on after the app: the first attempts fail, and it connects anyway.
        val p = FakeProvider(refuseFirst = 3).also { provider = it }
        p.start()

        waitUntil { p.connectionStatus.value }
        assertTrue("connected on a later attempt", p.opens.get() >= 4)
    }

    @Test
    fun treatsASilentLinkAsDeadAndReopensIt() = runBlocking<Unit> {
        // An access point that vanishes sends no FIN or RST - the read just blocks forever.
        // The watchdog must notice the silence and force a reconnect.
        val p = FakeProvider().also { provider = it }
        p.start()
        waitUntil { p.modules.size == 1 && p.connectionStatus.value }

        // Send nothing. Past the stall timeout, the link should have been replaced.
        waitUntil(timeoutMs = 3_000) { p.opens.get() >= 2 }
    }

    @Test
    fun stopEndsRetrying() = runBlocking<Unit> {
        val p = FakeProvider(refuseFirst = Int.MAX_VALUE).also { provider = it }
        p.start()
        waitUntil { p.opens.get() >= 2 }

        p.stop()
        val opensAtStop = p.opens.get()
        delay(300) // several backoff periods at the test timing

        assertFalse(p.connectionStatus.value)
        assertTrue(
            "no connection attempts after stop (was $opensAtStop, now ${p.opens.get()})",
            p.opens.get() <= opensAtStop + 1 // one attempt may already have been in flight
        )
    }

    @Test
    fun stopDisconnectsALiveLink() = runBlocking<Unit> {
        val p = FakeProvider().also { provider = it }
        p.start()
        waitUntil { p.connectionStatus.value }

        p.stop()

        assertFalse(p.connectionStatus.value)
        val opensAtStop = p.opens.get()
        delay(300)
        assertEquals("a stopped provider does not reconnect", opensAtStop, p.opens.get())
    }
}

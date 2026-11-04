package com.example.trackpro

import com.example.trackpro.managerClasses.gpsDataManagers.GpsClock
import com.example.trackpro.managerClasses.gpsDataManagers.parseTimeOfDayMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsClockTest {

    private val received0 = 1_700_000_000_000L

    @Test
    fun `centiseconds are an unpadded integer not a decimal fraction`() {
        // "12:5:3.5" is 12:05:03 plus 5 cs = 50 ms, not 500 ms.
        assertEquals(((12 * 60L + 5) * 60 + 3) * 1000 + 50, parseTimeOfDayMs("12:5:3.5"))
        assertEquals(((12 * 60L + 5) * 60 + 3) * 1000 + 500, parseTimeOfDayMs("12:5:3.50"))
    }

    @Test
    fun `placeholder and malformed times are rejected`() {
        assertNull(parseTimeOfDayMs("0:0:0.0"))
        assertNull(parseTimeOfDayMs(""))
        assertNull(parseTimeOfDayMs("12:30"))
        assertNull(parseTimeOfDayMs("25:0:0.0"))
        assertNull(parseTimeOfDayMs("1:2:3.100"))
    }

    @Test
    fun `bursty arrival does not disturb spacing between fixes`() {
        val clock = GpsClock()
        // Fixes 100 ms apart by the module's clock; the first three were held up and arrive in a
        // burst, then the link catches up.
        val arrivals = longArrayOf(300, 302, 304, 400, 500, 650, 700)
        val stamps = arrivals.mapIndexed { i, rx ->
            clock.stamp("10:0:0.${i * 10}", received0 + rx)
        }
        for (i in 1 until stamps.size) {
            assertEquals(100.0, (stamps[i] - stamps[i - 1]).toDouble(), 1.0)
        }
    }

    @Test
    fun `stays on the phone clock`() {
        val clock = GpsClock()
        val t = clock.stamp("10:0:0.0", received0 + 80)
        assertEquals(received0 + 80, t)
    }

    @Test
    fun `rolls over midnight`() {
        val clock = GpsClock()
        val before = clock.stamp("23:59:59.90", received0)
        val after = clock.stamp("0:0:0.10", received0 + 200)
        assertEquals(200L, after - before)
    }

    @Test
    fun `timestamps only ever increase`() {
        val clock = GpsClock()
        // First fix had a long delay; a later, prompt one lowers the shift.
        val a = clock.stamp("10:0:0.0", received0 + 2_000)
        val b = clock.stamp("10:0:0.10", received0 + 100)
        assertTrue(b > a)
    }

    @Test
    fun `missing module time falls back to the receipt time`() {
        val clock = GpsClock()
        assertEquals(received0, clock.stamp("0:0:0.0", received0))
    }

    @Test
    fun `reset starts a fresh link`() {
        val clock = GpsClock()
        clock.stamp("10:0:5.0", received0)
        clock.reset()
        assertEquals(received0 + 9_000, clock.stamp("9:0:0.0", received0 + 9_000))
    }
}

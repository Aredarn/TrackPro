package com.example.trackpro

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.managerClasses.utilities.LapStatus
import com.example.trackpro.managerClasses.utilities.timed
import com.example.trackpro.managerClasses.utilities.toLapDeltaString
import com.example.trackpro.managerClasses.utilities.toLapTimeMillisOrNull
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing and filtering of stored lap times.
 *
 * The regression these guard: the previous parser defaulted every unparseable field to
 * zero, so a lap still in progress read back as a 0 ms lap and won every "best lap"
 * comparison in the app.
 */
class LapTimeParsingTest {

    private fun lap(number: Int, time: String) =
        LapTimeData(id = number.toLong(), sessionid = 1L, lapnumber = number, laptime = time)

    // ─────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────

    @Test
    fun `parses a stored lap time`() {
        assertEquals(102_350L, "01:42.35".toLapTimeMillisOrNull())
        assertEquals(0L, "00:00.00".toLapTimeMillisOrNull())
        assertEquals(599_990L, "09:59.99".toLapTimeMillisOrNull())
    }

    @Test
    fun `hundredths are hundredths, not milliseconds`() {
        // "01:42.35" is 42.35 seconds into the minute, not 42.035.
        assertEquals(102_350L, "01:42.35".toLapTimeMillisOrNull())
        assertEquals(1_090L, "00:01.09".toLapTimeMillisOrNull())
    }

    @Test
    fun `round trips through toLapTimeString`() {
        listOf(0L, 1_090L, 102_350L, 599_990L, 3_600_000L).forEach { ms ->
            assertEquals(ms, ms.toLapTimeString().toLapTimeMillisOrNull())
        }
    }

    @Test
    fun `status sentinels do not parse as a time`() {
        assertNull(LapStatus.IN_PROGRESS.toLapTimeMillisOrNull())
        assertNull(LapStatus.INVALID.toLapTimeMillisOrNull())
    }

    @Test
    fun `malformed values do not parse as a time`() {
        listOf("", "   ", "01:42", "42.35", "--:--.--", "abc", "aa:bb.cc", "1:2.3.4")
            .forEach { assertNull("\"$it\" must not parse", it.toLapTimeMillisOrNull()) }
    }

    // ─────────────────────────────────────────────
    // Signed deltas
    // ─────────────────────────────────────────────

    @Test
    fun `a gain and a loss of the same size are not written the same way`() {
        // The regression: toLapTimeString drops the minus sign, and call sites prefixed "+"
        // only for positive values, so both of these rendered as "00:01.20".
        assertEquals("+00:01.20", 1_200L.toLapDeltaString())
        assertEquals("-00:01.20", (-1_200L).toLapDeltaString())
        assertNotEquals(1_200L.toLapDeltaString(), (-1_200L).toLapDeltaString())
    }

    @Test
    fun `a dead heat is signed neither way`() {
        assertEquals("00:00.00", 0L.toLapDeltaString())
    }

    @Test
    fun `the magnitude matches the unsigned format`() {
        listOf(-102_350L, -1L, 1L, 102_350L).forEach { ms ->
            assertEquals(ms.toLapTimeString(), ms.toLapDeltaString().removePrefix("+").removePrefix("-"))
        }
    }

    // ─────────────────────────────────────────────
    // Filtering
    // ─────────────────────────────────────────────

    @Test
    fun `timed drops laps that have no time`() {
        val laps = listOf(
            lap(1, "01:42.35"),
            lap(2, LapStatus.IN_PROGRESS),
            lap(3, "01:43.02"),
            lap(4, LapStatus.INVALID)
        )

        assertEquals(listOf(1, 3), laps.timed().map { it.lap.lapnumber })
        assertEquals(listOf(102_350L, 103_020L), laps.timed().map { it.millis })
    }

    @Test
    fun `an unfinished lap can no longer become the session best`() {
        // The whole point: 00:00.00 used to win this comparison.
        val laps = listOf(lap(1, "01:42.35"), lap(2, LapStatus.IN_PROGRESS))

        val best = laps.timed().minByOrNull { it.millis }

        assertEquals("01:42.35", best?.lap?.laptime)
    }

    @Test
    fun `timed returns laps in lap order regardless of row order`() {
        // The session trend splits this list positionally into first and second half.
        val laps = listOf(lap(3, "01:44.00"), lap(1, "01:42.35"), lap(2, "01:43.02"))

        assertEquals(listOf(1, 2, 3), laps.timed().map { it.lap.lapnumber })
    }

    @Test
    fun `timed is empty when nothing was completed`() {
        assertEquals(emptyList<Int>(), listOf(lap(1, LapStatus.IN_PROGRESS)).timed())
        assertEquals(emptyList<Int>(), emptyList<LapTimeData>().timed())
    }
}

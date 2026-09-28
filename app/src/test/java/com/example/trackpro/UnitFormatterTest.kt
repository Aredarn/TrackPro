package com.example.trackpro

import com.example.trackpro.managerClasses.utilities.UnitFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Conversion and display of speeds, distances and temperatures.
 *
 * The formatters deliberately use the default locale, so a Hungarian phone shows "1,20 km"
 * rather than "1.20 km" - correct for the reader. That makes the expected strings here
 * locale-dependent, so the locale is pinned for the duration of the test rather than the
 * production code being made locale-blind.
 */
class UnitFormatterTest {

    private lateinit var original: Locale

    @Before
    fun pinLocale() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    // ─────────────────────────────────────────────
    // Speed
    // ─────────────────────────────────────────────

    @Test
    fun `metric speed passes through, imperial converts to mph`() {
        assertEquals(100.0, UnitFormatter.convertSpeed(100.0, metric = true), 1e-9)
        assertEquals(62.1371, UnitFormatter.convertSpeed(100.0, metric = false), 1e-4)
    }

    @Test
    fun `converting a display speed back to kmh is the inverse`() {
        listOf(0.0, 60.0, 100.0, 209.2).forEach { kmh ->
            val shown = UnitFormatter.convertSpeed(kmh, metric = false)
            assertEquals(kmh, UnitFormatter.convertSpeedToKmh(shown, metric = false), 1e-6)
        }
    }

    @Test
    fun `speed formatting rounds and does not carry a unit`() {
        assertEquals("100", UnitFormatter.formatSpeed(100.0, metric = true))
        assertEquals("62", UnitFormatter.formatSpeed(100.0, metric = false))
        assertEquals("100.0", UnitFormatter.formatSpeedPrecise(100.0, metric = true))
        assertEquals("62.1", UnitFormatter.formatSpeedPrecise(100.0, metric = false))
    }

    // ─────────────────────────────────────────────
    // Distance
    // ─────────────────────────────────────────────

    @Test
    fun `metric distance steps up to km at one km`() {
        assertEquals("402 m", UnitFormatter.formatDistance(402.336, metric = true))
        assertEquals("999 m", UnitFormatter.formatDistance(999.0, metric = true))
        assertEquals("1.00 km", UnitFormatter.formatDistance(1000.0, metric = true))
        assertEquals("4.36 km", UnitFormatter.formatDistance(4362.0, metric = true))
    }

    @Test
    fun `imperial distance steps up to miles at one mile, not at a thousand feet`() {
        // The regression: the switch was at 1000 ft - a fifth of a mile - so a quarter mile
        // read as "0.25 mi" and anything past 305 m became an awkward fraction.
        assertEquals("1320 ft", UnitFormatter.formatDistance(402.336, metric = false))
        assertEquals("1000 ft", UnitFormatter.formatDistance(304.8, metric = false))
        assertEquals("2640 ft", UnitFormatter.formatDistance(804.672, metric = false))
        assertEquals("1.00 mi", UnitFormatter.formatDistance(1609.344, metric = false))
        assertEquals("2.71 mi", UnitFormatter.formatDistance(4362.0, metric = false))
    }

    @Test
    fun `both systems step up at one of their own larger unit`() {
        // Just below and just above the boundary, in each system.
        assertEquals("m", UnitFormatter.formatDistance(999.9, metric = true).takeLast(1))
        assertEquals("km", UnitFormatter.formatDistance(1000.0, metric = true).takeLast(2))
        assertEquals("ft", UnitFormatter.formatDistance(1609.0, metric = false).takeLast(2))
        assertEquals("mi", UnitFormatter.formatDistance(1609.4, metric = false).takeLast(2))
    }

    // ─────────────────────────────────────────────
    // Temperature
    // ─────────────────────────────────────────────

    @Test
    fun `temperature converts to fahrenheit for imperial`() {
        assertEquals("21.5 °C", UnitFormatter.formatTemperature(21.5, metric = true))
        assertEquals("70.7 °F", UnitFormatter.formatTemperature(21.5, metric = false))
        assertEquals("32.0 °F", UnitFormatter.formatTemperature(0.0, metric = false))
    }
}

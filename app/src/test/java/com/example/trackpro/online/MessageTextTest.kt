package com.example.trackpro.online

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MessageTextTest {

    private lateinit var saved: Locale

    @Before fun remember() { saved = Locale.getDefault() }
    @After fun restore() { Locale.setDefault(saved) }

    @Test
    fun `english stays exactly as it came`() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Could not reach the TrackBoard server.", MessageText.localize("Could not reach the TrackBoard server."))
    }

    @Test
    fun `a known message is translated`() {
        Locale.setDefault(Locale("hu", "HU"))
        assertEquals("Nem sikerült elérni a TrackBoard szervert.", MessageText.localize("Could not reach the TrackBoard server."))
    }

    @Test
    fun `captured parts are carried over and translated again`() {
        Locale.setDefault(Locale("hu", "HU"))
        assertEquals(
            "A(z) Lexus IS200 fotóját nem sikerült szinkronizálni: Nem sikerült elérni a fotótárat.",
            MessageText.localize("The photo of Lexus IS200 could not be synced: Could not reach photo storage.")
        )
    }

    @Test
    fun `the specific pattern wins over the general one`() {
        Locale.setDefault(Locale("hu", "HU"))
        assertEquals("A szerver nem fogadta el.", MessageText.localize("The server rejected it."))
        assertEquals("A szerver nem fogadta el: email.", MessageText.localize("The server rejected email."))
    }

    @Test
    fun `every line of a multi-line message is translated on its own`() {
        Locale.setDefault(Locale("hu", "HU"))
        assertEquals(
            "Hibás jelszó.\nNem vagy bejelentkezve.",
            MessageText.localize("The password is incorrect.\nNot signed in.")
        )
    }

    @Test
    fun `an unknown message is shown as it came`() {
        Locale.setDefault(Locale("hu", "HU"))
        assertEquals("Something nobody translated.", MessageText.localize("Something nobody translated."))
        assertNull(MessageText.localize(null))
    }
}

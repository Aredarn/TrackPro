package com.example.trackpro

import com.example.trackpro.screens.history.SessionFilter
import com.example.trackpro.screens.history.SessionKey
import com.example.trackpro.screens.history.SessionPeriod
import com.example.trackpro.screens.history.SessionSort
import com.example.trackpro.screens.history.applySessionFilter
import com.example.trackpro.screens.history.groupSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Narrowing and ordering the History list. */
class SessionFiltersTest {

    private val zone = ZoneId.of("Europe/Budapest")
    /** Mid-afternoon on 30 September 2026. */
    private val now = ZonedDateTime.of(2026, 9, 30, 15, 0, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private data class S(val name: String, val key: SessionKey)

    private fun s(name: String, car: Long, track: Long?, start: Long, bestLapMs: Long? = null) =
        S(name, SessionKey(car, track, start, bestLapMs))

    private val sessions = listOf(
        s("mx5-hungaroring-sep", car = 1, track = 10, start = at(2026, 9, 28), bestLapMs = 125_000),
        s("mx5-pannonia-sep", car = 1, track = 20, start = at(2026, 9, 12), bestLapMs = 118_000),
        s("gt86-hungaroring-aug", car = 2, track = 10, start = at(2026, 8, 3), bestLapMs = 121_000),
        s("gt86-hungaroring-2025", car = 2, track = 10, start = at(2025, 6, 1), bestLapMs = 130_000),
        s("mx5-hungaroring-nolaps", car = 1, track = 10, start = at(2026, 9, 29), bestLapMs = null),
    )

    private fun List<S>.run(filter: SessionFilter) =
        applySessionFilter(filter, now) { it.key }.map { it.name }

    // ─────────────────────────────────────────────
    // Filtering
    // ─────────────────────────────────────────────

    @Test
    fun `no filter keeps everything, newest first`() {
        assertEquals(
            listOf(
                "mx5-hungaroring-nolaps", "mx5-hungaroring-sep", "mx5-pannonia-sep",
                "gt86-hungaroring-aug", "gt86-hungaroring-2025"
            ),
            sessions.run(SessionFilter())
        )
    }

    @Test
    fun `car and track combine`() {
        assertEquals(
            listOf("gt86-hungaroring-aug", "gt86-hungaroring-2025"),
            sessions.run(SessionFilter(vehicleId = 2, trackId = 10))
        )
    }

    @Test
    fun `last 7 days runs from midnight six days ago`() {
        // 24 Sep 00:00 onwards: the 28th and 29th, not the 12th.
        assertEquals(
            listOf("mx5-hungaroring-nolaps", "mx5-hungaroring-sep"),
            sessions.run(SessionFilter(period = SessionPeriod.LAST_7_DAYS))
        )
    }

    @Test
    fun `last 7 days includes the early morning of its first day`() {
        val early = s("dawn", car = 1, track = 10, start = at(2026, 9, 24, hour = 0) + 60_000)
        val justBefore = s("late", car = 1, track = 10, start = at(2026, 9, 23, hour = 23))

        assertEquals(listOf("dawn"), listOf(early, justBefore).run(SessionFilter(period = SessionPeriod.LAST_7_DAYS)))
    }

    @Test
    fun `this year starts on the first of January`() {
        assertEquals(
            4,
            sessions.run(SessionFilter(period = SessionPeriod.THIS_YEAR)).size
        )
        assertNull(SessionPeriod.ALL.startsFrom(now))
    }

    // ─────────────────────────────────────────────
    // Ordering
    // ─────────────────────────────────────────────

    @Test
    fun `oldest first reverses the order`() {
        assertEquals(
            "gt86-hungaroring-2025",
            sessions.run(SessionFilter(sort = SessionSort.OLDEST)).first()
        )
    }

    @Test
    fun `fastest puts the quickest best lap first and lapless sessions last`() {
        assertEquals(
            listOf(
                "mx5-pannonia-sep", "gt86-hungaroring-aug", "mx5-hungaroring-sep",
                "gt86-hungaroring-2025", "mx5-hungaroring-nolaps"
            ),
            sessions.run(SessionFilter(sort = SessionSort.FASTEST))
        )
    }

    // ─────────────────────────────────────────────
    // Grouping
    // ─────────────────────────────────────────────

    private fun groupsBy(sort: SessionSort): List<Pair<Long?, List<String>>> =
        sessions.applySessionFilter(SessionFilter(sort = sort), now) { it.key }
            .groupSessions(sort, { it.key }) { it.key.trackId }
            .map { (track, members) -> track to members.map { it.name } }

    @Test
    fun `groups keep the chosen order and come most recent first`() {
        val groups = groupsBy(SessionSort.NEWEST)

        assertEquals(listOf(10L, 20L), groups.map { it.first })
        assertEquals(
            listOf("mx5-hungaroring-nolaps", "mx5-hungaroring-sep", "gt86-hungaroring-aug", "gt86-hungaroring-2025"),
            groups.first().second
        )
    }

    @Test
    fun `fastest orders inside each track but not the tracks by lap time`() {
        // Pannonia's 1:58 is quicker than anything at the Hungaroring, but a lap of a shorter
        // circuit is not a better lap: the tracks stay in order of most recent use.
        val groups = groupsBy(SessionSort.FASTEST)

        assertEquals(listOf(10L, 20L), groups.map { it.first })
        assertEquals(
            listOf("gt86-hungaroring-aug", "mx5-hungaroring-sep", "gt86-hungaroring-2025", "mx5-hungaroring-nolaps"),
            groups.first().second
        )
    }

    // ─────────────────────────────────────────────
    // The filter itself
    // ─────────────────────────────────────────────

    @Test
    fun `order alone does not count as narrowing`() {
        assertFalse(SessionFilter(sort = SessionSort.OLDEST).narrows)
        assertTrue(SessionFilter(vehicleId = 1).narrows)
        assertTrue(SessionFilter(period = SessionPeriod.LAST_30_DAYS).narrows)
    }

    @Test
    fun `clearing keeps the order`() {
        val cleared = SessionFilter(vehicleId = 1, trackId = 10, sort = SessionSort.FASTEST).cleared()

        assertEquals(SessionFilter(sort = SessionSort.FASTEST), cleared)
    }
}

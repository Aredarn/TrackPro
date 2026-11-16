package com.example.trackpro

import com.example.trackpro.managerClasses.InterruptedSessions
import org.junit.Assert.assertEquals
import org.junit.Test

class InterruptedSessionsTest {

    @Test
    fun endsAtTheLastRecordedMoment() {
        assertEquals(5_000L, InterruptedSessions.endTimeFor(startTime = 1_000L, lastRecordedAt = 5_000L))
    }

    @Test
    fun nothingRecorded_endsAtTheStart() {
        assertEquals(1_000L, InterruptedSessions.endTimeFor(startTime = 1_000L, lastRecordedAt = null))
    }

    @Test
    fun aFixStampedBeforeTheStart_neverMakesTheSessionNegative() {
        // Fixes carry the receiver's clock, the session the phone's; they can disagree.
        assertEquals(1_000L, InterruptedSessions.endTimeFor(startTime = 1_000L, lastRecordedAt = 400L))
    }
}

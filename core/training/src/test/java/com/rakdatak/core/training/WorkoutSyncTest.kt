package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSyncTest {
    private val now = 1_700_000_000_000L
    private val sessionId = "d1839a0e-5d20-458a-9dd2-9119b19d6b0a"

    private fun command(
        action: String = "PAUSE",
        owner: WorkoutOwner = WorkoutOwner.PHONE,
        origin: WorkoutOwner = WorkoutOwner.WATCH,
        time: Long = now,
        id: String = sessionId,
    ) = WorkoutCommand(action, id, owner, origin, time, 2, 132, gps = false, paused = true)

    @Test
    fun roundTripPreservesSessionAndOwnerAndClock() {
        val original = command(action = "RESUME", time = now - 500)
        assertEquals(original, WorkoutSync.parse(original.encode()))
    }

    @Test
    fun rejectsMalformedOrLegacyCommandsInsteadOfStartingOrStoppingAnUnrelatedWorkout() {
        assertNull(WorkoutSync.parse("START|0|0|true"))
        assertNull(WorkoutSync.parse("STOP"))
        assertNull(WorkoutSync.parse(command(id = "invalid|session").encode()))
        assertNull(WorkoutSync.parse(command().encode().replace("|PAUSE|", "|DELETE|")))
        assertNull(WorkoutSync.parse(command().encode().replace("|132|", "|-3|")))
    }

    @Test
    fun freshnessRejectsStaleAndFutureEvents() {
        assertTrue(WorkoutSync.fresh(now - 500, now))
        assertFalse(WorkoutSync.fresh(now - WorkoutSync.MAX_AGE_MS - 1, now))
        assertFalse(WorkoutSync.fresh(now + 2_001, now))
        assertFalse(WorkoutSync.fresh(0, now))
    }

    @Test
    fun watchAcceptsPhoneStartOnlyWhenNoExistingActiveSession() {
        val start = command(action = "START", owner = WorkoutOwner.PHONE, origin = WorkoutOwner.PHONE)
        assertTrue(WorkoutSync.accepts(start, null, null, WorkoutOwner.WATCH, now))
        assertFalse(WorkoutSync.accepts(start, sessionId, WorkoutOwner.WATCH, WorkoutOwner.WATCH, now))
        assertFalse(WorkoutSync.accepts(start, sessionId, WorkoutOwner.PHONE, WorkoutOwner.WATCH, now))
    }

    @Test
    fun phoneAcceptsWatchControlsOnlyForItsOwnActiveSession() {
        val pause = command()
        assertTrue(WorkoutSync.accepts(pause, sessionId, WorkoutOwner.PHONE, WorkoutOwner.PHONE, now))
        assertFalse(WorkoutSync.accepts(pause, "another-session", WorkoutOwner.PHONE, WorkoutOwner.PHONE, now))
        assertFalse(WorkoutSync.accepts(pause, sessionId, WorkoutOwner.WATCH, WorkoutOwner.PHONE, now))
        assertFalse(WorkoutSync.accepts(pause.copy(origin = WorkoutOwner.PHONE), sessionId, WorkoutOwner.PHONE, WorkoutOwner.PHONE, now))
    }

    @Test
    fun phoneCanControlWatchOwnedSessionWithoutEcho() {
        val stop = command(action = "STOP", owner = WorkoutOwner.WATCH, origin = WorkoutOwner.PHONE)
        assertTrue(WorkoutSync.accepts(stop, sessionId, WorkoutOwner.WATCH, WorkoutOwner.WATCH, now))
        assertFalse(WorkoutSync.accepts(stop, sessionId, WorkoutOwner.WATCH, WorkoutOwner.PHONE, now))
        assertFalse(WorkoutSync.accepts(stop, sessionId, WorkoutOwner.PHONE, WorkoutOwner.WATCH, now))
    }

    @Test
    fun lastProcessedCommandCannotApplyTwiceOrOutOfOrder() {
        val pause = command(time = now - 2_000)
        assertTrue(WorkoutSync.accepts(pause, sessionId, WorkoutOwner.PHONE,
            WorkoutOwner.PHONE, now, lastCommandAt = now - 3_000))
        assertFalse(WorkoutSync.accepts(pause, sessionId, WorkoutOwner.PHONE,
            WorkoutOwner.PHONE, now, lastCommandAt = now - 2_000))
    }
}

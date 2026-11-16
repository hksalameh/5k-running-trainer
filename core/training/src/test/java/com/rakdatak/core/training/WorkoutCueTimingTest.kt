package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCueTimingTest {
    @Test
    fun saysNextPhaseInsidePreviousPhaseAndEndsSpeechAtBoundary() {
        val durationMs = 3_650L
        assertFalse(WorkoutCueTiming.shouldQueue(7, durationMs))
        assertTrue(WorkoutCueTiming.shouldQueue(5, durationMs))
        assertEquals(1_260L, WorkoutCueTiming.delayUntilCue(5, durationMs))
        assertFalse(WorkoutCueTiming.shouldQueue(3, durationMs))
        assertEquals(0L, WorkoutCueTiming.delayUntilCue(3, durationMs))
    }

    @Test
    fun noAudioCueIsQueuedAfterBoundary() {
        assertFalse(WorkoutCueTiming.shouldQueue(0, 2_400L))
        assertFalse(WorkoutCueTiming.shouldQueue(-1, 2_400L))
        assertFalse(WorkoutCueTiming.shouldQueue(4, 0L))
    }
}

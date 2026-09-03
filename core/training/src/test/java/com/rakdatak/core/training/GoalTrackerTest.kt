package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalTrackerTest {

    @Test
    fun reachingFiveKFirstShiftsFocusToTime() {
        val progress = GoalProgress(
            bestDistanceMeters = 5_000.0,
            bestContinuousRunSeconds = 1_500,
        )

        assertEquals(GoalFocus.TIME, progress.focus)
    }

    @Test
    fun reachingThirtyMinutesFirstShiftsFocusToDistance() {
        val progress = GoalProgress(
            bestDistanceMeters = 4_100.0,
            bestContinuousRunSeconds = 1_800,
        )

        assertEquals(GoalFocus.DISTANCE, progress.focus)
    }

    @Test
    fun bothGoalsAreRequiredForCompletion() {
        val progress = GoalProgress(
            bestDistanceMeters = 5_200.0,
            bestContinuousRunSeconds = 1_900,
        )

        assertEquals(GoalFocus.COMPLETE, progress.focus)
    }
}

package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalTrackerTest {

    @Test
    fun reachingFiveKFirstShiftsFocusToTime() {
        val progress = GoalProgress(
            bestRunDistanceMeters = 5_000.0,
            bestContinuousRunSeconds = 1_500,
        )

        assertEquals(GoalFocus.COMPLETE, progress.focus)
    }

    @Test
    fun reachingThirtyMinutesFirstShiftsFocusToDistance() {
        val progress = GoalProgress(
            bestRunDistanceMeters = 4_100.0,
            bestContinuousRunSeconds = 1_800,
        )

        assertEquals(GoalFocus.COMPLETE, progress.focus)
    }

    @Test
    fun bothGoalsCanAlsoBeCompleted() {
        val progress = GoalProgress(
            bestRunDistanceMeters = 5_200.0,
            bestContinuousRunSeconds = 1_900,
        )

        assertEquals(GoalFocus.COMPLETE, progress.focus)
    }

    @Test
    fun walkingFiveKilometersDoesNotCountTowardTheRunningGoal() {
        val progress = GoalProgress(
            bestRunDistanceMeters = 0.0,
            bestContinuousRunSeconds = 0,
        )
        assertEquals(GoalFocus.BOTH, progress.focus)
        assertEquals(0.0, progress.distanceProgress, 0.000001)
    }
}

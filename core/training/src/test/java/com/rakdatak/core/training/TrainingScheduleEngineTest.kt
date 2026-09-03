package com.rakdatak.core.training

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingScheduleEngineTest {

    private val slots = listOf(
        TrainingSlot(DayOfWeek.SUNDAY, LocalTime.of(7, 0)),
        TrainingSlot(DayOfWeek.TUESDAY, LocalTime.of(18, 30)),
        TrainingSlot(DayOfWeek.THURSDAY, LocalTime.of(20, 0)),
    )

    @Test
    fun choosesNextUserSelectedSlot() {
        val next = TrainingScheduleEngine().nextSlot(
            after = LocalDateTime.of(2026, 9, 3, 21, 0),
            slots = slots,
        )

        assertEquals(LocalDateTime.of(2026, 9, 6, 7, 0), next)
    }

    @Test
    fun respectsRestDayAfterCompletedWorkout() {
        val next = TrainingScheduleEngine().nextSlot(
            after = LocalDateTime.of(2026, 9, 6, 8, 0),
            slots = slots,
            lastCompletedAt = LocalDateTime.of(2026, 9, 6, 7, 30),
        )

        assertEquals(LocalDateTime.of(2026, 9, 8, 18, 30), next)
    }

    @Test
    fun missedWorkoutKeepsSamePlanIdAndMovesForward() {
        val missed = ScheduledWorkout(
            planId = "w1s1",
            scheduledAt = LocalDateTime.of(2026, 9, 3, 20, 0),
        )

        val moved = TrainingScheduleEngine().rescheduleMissed(
            missed = missed,
            now = LocalDateTime.of(2026, 9, 3, 21, 0),
            slots = slots,
        )

        assertEquals("w1s1", moved.planId)
        assertEquals(LocalDateTime.of(2026, 9, 6, 7, 0), moved.scheduledAt)
    }
}

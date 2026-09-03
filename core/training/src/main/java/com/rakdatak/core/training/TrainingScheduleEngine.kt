package com.rakdatak.core.training

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class TrainingSlot(
    val dayOfWeek: DayOfWeek,
    val time: LocalTime,
)

data class ScheduledWorkout(
    val planId: String,
    val scheduledAt: LocalDateTime,
)

class TrainingScheduleEngine {

    fun nextSlot(
        after: LocalDateTime,
        slots: List<TrainingSlot>,
        minimumRestDays: Long = 1,
        lastCompletedAt: LocalDateTime? = null,
    ): LocalDateTime {
        require(slots.size == 3) { "Exactly three weekly training slots are required" }

        val earliestByRest = lastCompletedAt
            ?.toLocalDate()
            ?.plusDays(minimumRestDays + 1)
            ?: after.toLocalDate()

        return slots
            .map { slot -> candidate(after, earliestByRest, slot) }
            .minOrNull()
            ?: error("No training slots configured")
    }

    /**
     * A missed workout moves to the next suitable training slot rather than being discarded.
     */
    fun rescheduleMissed(
        missed: ScheduledWorkout,
        now: LocalDateTime,
        slots: List<TrainingSlot>,
        lastCompletedAt: LocalDateTime? = null,
    ): ScheduledWorkout = missed.copy(
        scheduledAt = nextSlot(
            after = now,
            slots = slots,
            lastCompletedAt = lastCompletedAt,
        )
    )

    private fun candidate(
        after: LocalDateTime,
        earliestByRest: LocalDate,
        slot: TrainingSlot,
    ): LocalDateTime {
        val baseDate = maxOf(after.toLocalDate(), earliestByRest)
        var date = baseDate.with(TemporalAdjusters.nextOrSame(slot.dayOfWeek))
        var dateTime = LocalDateTime.of(date, slot.time)

        if (!dateTime.isAfter(after)) {
            date = date.plusWeeks(1)
            dateTime = LocalDateTime.of(date, slot.time)
        }

        if (date.isBefore(earliestByRest)) {
            date = earliestByRest.with(TemporalAdjusters.nextOrSame(slot.dayOfWeek))
            dateTime = LocalDateTime.of(date, slot.time)
        }

        return dateTime
    }
}

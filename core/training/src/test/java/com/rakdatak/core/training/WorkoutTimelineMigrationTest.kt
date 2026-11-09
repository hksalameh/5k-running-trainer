package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTimelineMigrationTest {
    private val legacy = BaselinePlanFactory.create(
        warmUpSeconds = 180,
        coolDownSeconds = 180,
    )
    private val current = BaselinePlanFactory.create()

    @Test
    fun activeWarmupNeverBecomesUnexpectedlyCompleted() {
        val old = legacy.first()
        val new = current.first()

        assertEquals(0, WorkoutTimelineMigration.migrateElapsed(old, new, 0))
        assertEquals(90, WorkoutTimelineMigration.migrateElapsed(old, new, 90))
        // Still in warm-up, even if the old warm-up had exceeded 120s.
        assertEquals(119, WorkoutTimelineMigration.migrateElapsed(old, new, 170))
    }

    @Test
    fun positionWithinFirstRunIsRetained() {
        val old = legacy.first()
        val new = current.first()
        val updatedElapsed = WorkoutTimelineMigration.migrateElapsed(
            old, new, 180 + 40,
        )
        assertEquals(120 + 40, updatedElapsed)

        val oldEngine = WorkoutSessionEngine(old).apply { restore(220, paused = true) }
        val newEngine = WorkoutSessionEngine(new).apply {
            restore(updatedElapsed, paused = true)
        }
        assertEquals(oldEngine.snapshot().phaseIndex, newEngine.snapshot().phaseIndex)
        assertEquals(oldEngine.snapshot().phaseRemainingSeconds,
            newEngine.snapshot().phaseRemainingSeconds)
        assertEquals(WorkoutSessionStatus.PAUSED, newEngine.snapshot().status)
    }

    @Test
    fun resumeEveryPlanAtItsFirstRunWithoutLosingThePhase() {
        legacy.zip(current).forEach { (old, new) ->
            val oldElapsed = old.phases.first().durationSeconds + 7
            val updatedElapsed = WorkoutTimelineMigration.migrateElapsed(
                old, new, oldElapsed,
            )
            val oldSnapshot = WorkoutSessionEngine(old).apply {
                restore(oldElapsed, paused = true)
            }.snapshot()
            val newSnapshot = WorkoutSessionEngine(new).apply {
                restore(updatedElapsed, paused = true)
            }.snapshot()
            assertEquals(old.id, new.id)
            assertEquals(oldSnapshot.phaseIndex, newSnapshot.phaseIndex)
            assertEquals(oldSnapshot.phaseRemainingSeconds, newSnapshot.phaseRemainingSeconds)
        }
    }

    @Test
    fun lastSecondOfOldCooldownDoesNotCompleteAnActiveWorkout() {
        val old = legacy.last()
        val new = current.last()
        val updatedElapsed = WorkoutTimelineMigration.migrateElapsed(
            old, new, old.totalDurationSeconds - 1,
        )
        assertEquals(new.totalDurationSeconds - 1, updatedElapsed)
        val state = WorkoutSessionEngine(new).apply {
            restore(updatedElapsed, paused = true)
        }.snapshot()
        assertEquals(WorkoutSessionStatus.PAUSED, state.status)
    }

    @Test
    fun completedSessionsAndOutOfRangeElapsedStayBounded() {
        val old = legacy.first()
        val new = current.first()
        assertEquals(
            new.totalDurationSeconds,
            WorkoutTimelineMigration.migrateElapsed(old, new, old.totalDurationSeconds),
        )
        assertEquals(0, WorkoutTimelineMigration.migrateElapsed(old, new, -4))
        assertEquals(
            new.totalDurationSeconds,
            WorkoutTimelineMigration.migrateElapsed(old, new, old.totalDurationSeconds + 1_000),
        )
        assertTrue(new.totalDurationSeconds < old.totalDurationSeconds)
    }
}

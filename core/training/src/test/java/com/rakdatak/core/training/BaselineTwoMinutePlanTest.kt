package com.rakdatak.core.training

import com.rakdatak.core.training.model.WorkoutPhaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTwoMinutePlanTest {
    @Test
    fun completeEightWeekPlanUsesTwoMinutePreparationAndRecovery() {
        val plans = BaselinePlanFactory.create()

        assertEquals(24, plans.size)
        assertEquals(8, plans.last().week)
        assertEquals("w1s1", plans.first().id)
        assertEquals("w8s3", plans.last().id)

        for (plan in plans) {
            assertEquals(WorkoutPhaseType.WARM_UP, plan.phases.first().type)
            assertEquals(120, plan.phases.first().durationSeconds)
            assertEquals(WorkoutPhaseType.COOL_DOWN, plan.phases.last().type)
            assertEquals(120, plan.phases.last().durationSeconds)
            assertTrue(plan.totalDurationSeconds > 240)
        }
        // First session: 8 x 60 s running and 7 x 90 s walking,
        // plus the new 2-minute warm-up and 2-minute cool-down.
        assertEquals(1350, plans.first().totalDurationSeconds)
    }

    @Test
    fun explicitlyCustomizedDurationsAreStillHonored() {
        val plan = BaselinePlanFactory.create(warmUpSeconds = 90, coolDownSeconds = 90).first()
        assertEquals(90, plan.phases.first().durationSeconds)
        assertEquals(90, plan.phases.last().durationSeconds)
    }
}

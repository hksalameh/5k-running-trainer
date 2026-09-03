package com.rakdatak.core.training

import com.rakdatak.core.training.model.PainLevel
import com.rakdatak.core.training.model.PerceivedDifficulty
import com.rakdatak.core.training.model.TrainingAction
import com.rakdatak.core.training.model.WorkoutResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingLogicTest {

    @Test
    fun baselineContainsTwentyFourSessionsAndEndsAtThirtyMinutesContinuousRun() {
        val plan = BaselinePlanFactory.create()

        assertEquals(24, plan.size)
        assertEquals(1_800, plan.last().phases.maxOf { it.durationSeconds })
    }

    @Test
    fun strongPainAlwaysTriggersRecovery() {
        val decision = AdaptiveTrainingEngine().decide(
            result = result(pain = PainLevel.STRONG),
            missedWorkoutsThisWeek = 0,
            recentModerateOrStrongPainCount = 0,
            recentStrongPerformanceCount = 0,
        )

        assertEquals(TrainingAction.RECOVERY, decision.action)
    }

    @Test
    fun multipleMissedWorkoutsStepBackOneSession() {
        val decision = AdaptiveTrainingEngine().decide(
            result = result(),
            missedWorkoutsThisWeek = 2,
            recentModerateOrStrongPainCount = 0,
            recentStrongPerformanceCount = 0,
        )

        assertEquals(TrainingAction.STEP_BACK_ONE, decision.action)
    }

    @Test
    fun consistentlyEasyCleanSessionsCanAdvanceFaster() {
        val decision = AdaptiveTrainingEngine().decide(
            result = result(difficulty = PerceivedDifficulty.EASY),
            missedWorkoutsThisWeek = 0,
            recentModerateOrStrongPainCount = 0,
            recentStrongPerformanceCount = 2,
        )

        assertEquals(TrainingAction.ADVANCE_FASTER, decision.action)
    }

    private fun result(
        pain: PainLevel = PainLevel.NONE,
        difficulty: PerceivedDifficulty = PerceivedDifficulty.RIGHT,
    ) = WorkoutResult(
        planId = "w1s1",
        completionRatio = 1.0,
        distanceMeters = 2_000.0,
        continuousRunSeconds = 600,
        averageHeartRateBpm = 125,
        excessiveHeartRateFraction = 0.0,
        unplannedWalkBreaks = 0,
        difficulty = difficulty,
        pain = pain,
    )
}

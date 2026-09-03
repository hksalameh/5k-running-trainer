package com.rakdatak.core.training

import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPhaseType
import com.rakdatak.core.training.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSessionEngineTest {

    private val plan = WorkoutPlan(
        id = "test",
        week = 1,
        session = 1,
        titleAr = "اختبار",
        phases = listOf(
            WorkoutPhase(WorkoutPhaseType.WARM_UP, 2),
            WorkoutPhase(WorkoutPhaseType.RUN, 3),
            WorkoutPhase(WorkoutPhaseType.COOL_DOWN, 2),
        ),
    )

    @Test
    fun tickMovesAcrossPhasesAndCompletes() {
        val engine = WorkoutSessionEngine(plan)

        engine.start()
        val afterWarmup = engine.tick(2)
        assertEquals(WorkoutPhaseType.RUN, afterWarmup.currentPhase.type)

        val completed = engine.tick(5)
        assertEquals(WorkoutSessionStatus.COMPLETED, completed.status)
        assertEquals(1.0, completed.completionRatio, 0.0001)
    }

    @Test
    fun pauseStopsClockUntilResume() {
        val engine = WorkoutSessionEngine(plan)

        engine.start()
        engine.tick(1)
        engine.pause()
        engine.tick(10)
        assertEquals(1, engine.snapshot().totalElapsedSeconds)

        engine.resume()
        engine.tick(1)
        assertEquals(2, engine.snapshot().totalElapsedSeconds)
    }
}

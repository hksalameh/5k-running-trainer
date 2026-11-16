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
    fun phoneTransitionsWithoutStoppingForInstructionsAndCanCrossSeveralPhases() {
        val engine = WorkoutSessionEngine(plan, continuousPhaseTransitions = true)
        assertEquals(WorkoutSessionStatus.RUNNING, engine.start().status)
        val afterWarmUp = engine.tick(2)
        assertEquals(WorkoutSessionStatus.RUNNING, afterWarmUp.status)
        assertEquals(WorkoutPhaseType.RUN, afterWarmUp.currentPhase.type)
        assertEquals(2, afterWarmUp.totalElapsedSeconds)
        val beforeCoolDown = engine.tick(3)
        assertEquals(WorkoutSessionStatus.RUNNING, beforeCoolDown.status)
        assertEquals(WorkoutPhaseType.COOL_DOWN, beforeCoolDown.currentPhase.type)
        assertEquals(3, beforeCoolDown.longestContinuousRunSeconds)
        assertEquals(5, beforeCoolDown.totalElapsedSeconds)
        assertEquals(WorkoutSessionStatus.COMPLETED, engine.tick(2).status)
        assertEquals(7, engine.snapshot().totalElapsedSeconds)
    }

    @Test
    fun phoneResumeOnExactPhaseBoundaryDoesNotWaitForSpeech() {
        val engine = WorkoutSessionEngine(plan, continuousPhaseTransitions = true)
        val restored = engine.restore(elapsedSeconds = 2, paused = true)
        assertEquals(WorkoutSessionStatus.PAUSED, restored.status)
        assertEquals(WorkoutPhaseType.RUN, restored.currentPhase.type)
        assertEquals(WorkoutSessionStatus.RUNNING, engine.resume().status)
        assertEquals(3, engine.tick(1).totalElapsedSeconds)
    }

    @Test
    fun phoneBulkTickCrossesPhasesWithoutDiscardingTime() {
        val engine = WorkoutSessionEngine(plan, continuousPhaseTransitions = true)
        engine.start()
        val completed = engine.tick(10)
        assertEquals(WorkoutSessionStatus.COMPLETED, completed.status)
        assertEquals(7, completed.totalElapsedSeconds)
        assertEquals(3, completed.longestContinuousRunSeconds)
    }

    @Test
    fun tickMovesAcrossPhasesAndCompletes() {
        val engine = WorkoutSessionEngine(plan)

        engine.start()
        assertEquals(WorkoutSessionStatus.ANNOUNCING, engine.snapshot().status)
        engine.tick(10)
        assertEquals(0, engine.snapshot().totalElapsedSeconds)
        engine.instructionFinished()
        val afterWarmup = engine.tick(2)
        assertEquals(WorkoutPhaseType.RUN, afterWarmup.currentPhase.type)
        assertEquals(WorkoutSessionStatus.ANNOUNCING, afterWarmup.status)
        assertEquals(2, afterWarmup.totalElapsedSeconds)

        engine.tick(20)
        assertEquals(2, engine.snapshot().totalElapsedSeconds)
        engine.instructionFinished()
        val beforeCooldown = engine.tick(3)
        assertEquals(WorkoutSessionStatus.ANNOUNCING, beforeCooldown.status)
        assertEquals(3, beforeCooldown.longestContinuousRunSeconds)
        engine.instructionFinished()
        val completed = engine.tick(2)
        assertEquals(WorkoutSessionStatus.COMPLETED, completed.status)
        assertEquals(1.0, completed.completionRatio, 0.0001)
    }

    @Test
    fun pauseStopsClockUntilResume() {
        val engine = WorkoutSessionEngine(plan)

        engine.start()
        engine.instructionFinished()
        engine.tick(1)
        engine.pause()
        engine.tick(10)
        assertEquals(1, engine.snapshot().totalElapsedSeconds)

        engine.resume()
        engine.tick(1)
        assertEquals(2, engine.snapshot().totalElapsedSeconds)
        assertEquals(WorkoutSessionStatus.ANNOUNCING, engine.snapshot().status)
    }

    @Test
    fun pausingWhileAnnouncingDoesNotStartTheClock() {
        val engine = WorkoutSessionEngine(plan)
        engine.start()
        engine.pause()
        engine.instructionFinished()
        engine.tick(10)
        assertEquals(0, engine.snapshot().totalElapsedSeconds)
        assertEquals(WorkoutSessionStatus.PAUSED, engine.snapshot().status)
        engine.resume()
        assertEquals(WorkoutSessionStatus.ANNOUNCING, engine.snapshot().status)
        engine.instructionFinished()
        engine.tick(1)
        assertEquals(1, engine.snapshot().totalElapsedSeconds)
    }

    @Test
    fun restoreAtExactPhaseBoundaryReannouncesWithoutLosingSeconds() {
        val engine = WorkoutSessionEngine(plan)
        val snapshot = engine.restore(elapsedSeconds = 2, paused = true)
        assertEquals(2, snapshot.totalElapsedSeconds)
        assertEquals(WorkoutPhaseType.RUN, snapshot.currentPhase.type)
        engine.resume()
        assertEquals(WorkoutSessionStatus.ANNOUNCING, engine.snapshot().status)
        engine.instructionFinished()
        assertEquals(3, engine.tick(1).totalElapsedSeconds)
    }

    @Test
    fun restoringElapsedTimeNeverInventsContinuousRunningStreak() {
        val engine = WorkoutSessionEngine(plan)
        val restored = engine.restore(elapsedSeconds = 4, paused = true)
        assertEquals(0, restored.longestContinuousRunSeconds)

        val saved = engine.restore(
            elapsedSeconds = 4,
            paused = true,
            recordedLongestContinuousRunSeconds = 2,
        )
        assertEquals(2, saved.longestContinuousRunSeconds)
    }

    @Test
    fun runStreakEndsAtPauseButBestRunIsKept() {
        val engine = WorkoutSessionEngine(plan)
        engine.start()
        engine.instructionFinished()
        engine.tick(2)
        engine.instructionFinished()
        engine.tick(2)
        assertEquals(2, engine.snapshot().longestContinuousRunSeconds)
        engine.pause()
        engine.resume()
        engine.tick(1)
        assertEquals(2, engine.snapshot().longestContinuousRunSeconds)
    }

    @Test
    fun restoreReconstructsPhaseAndPausedState() {
        val engine = WorkoutSessionEngine(plan)

        val restored = engine.restore(
            elapsedSeconds = 4,
            paused = true,
        )

        assertEquals(WorkoutSessionStatus.PAUSED, restored.status)
        assertEquals(WorkoutPhaseType.RUN, restored.currentPhase.type)
        assertEquals(1, restored.phaseRemainingSeconds)
        assertEquals(4, restored.totalElapsedSeconds)
    }

    @Test
    fun restoreAtOrBeyondPlanDurationCompletesSafely() {
        val engine = WorkoutSessionEngine(plan)

        val restored = engine.restore(
            elapsedSeconds = 999,
            paused = true,
        )

        assertEquals(WorkoutSessionStatus.COMPLETED, restored.status)
        assertEquals(7, restored.totalElapsedSeconds)
        assertEquals(1.0, restored.completionRatio, 0.0001)
    }
}

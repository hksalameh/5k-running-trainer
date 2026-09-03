package com.rakdatak.core.training

import com.rakdatak.core.training.model.TrainingAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanProgressionEngineTest {
    private val engine = PlanProgressionEngine()

    @Test
    fun `advance moves one workout`() {
        assertEquals(4, engine.nextIndex(3, 24, TrainingAction.ADVANCE))
    }

    @Test
    fun `advance faster skips one workout conservatively`() {
        assertEquals(5, engine.nextIndex(3, 24, TrainingAction.ADVANCE_FASTER))
    }

    @Test
    fun `repeat stays on current workout`() {
        assertEquals(3, engine.nextIndex(3, 24, TrainingAction.REPEAT))
    }

    @Test
    fun `step back moves one workout and never below zero`() {
        assertEquals(2, engine.nextIndex(3, 24, TrainingAction.STEP_BACK_ONE))
        assertEquals(0, engine.nextIndex(0, 24, TrainingAction.STEP_BACK_ONE))
    }

    @Test
    fun `recovery keeps current training position`() {
        assertEquals(3, engine.nextIndex(3, 24, TrainingAction.RECOVERY))
    }

    @Test
    fun `progression never moves past last baseline workout`() {
        assertEquals(23, engine.nextIndex(23, 24, TrainingAction.ADVANCE))
        assertEquals(23, engine.nextIndex(22, 24, TrainingAction.ADVANCE_FASTER))
    }
}

package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateGuidanceEngineTest {
    @Test
    fun `known max heart rate takes priority over age estimate`() {
        val profile = HeartRateProfile(ageYears = 50, knownMaxHeartRateBpm = 180)
        assertEquals(180, profile.maxHeartRateBpm())
    }

    @Test
    fun `tanaka estimate is used when personal max is not known`() {
        val profile = HeartRateProfile(ageYears = 50)
        assertEquals(173, profile.maxHeartRateBpm())
    }

    @Test
    fun `no profile means no heart rate guidance`() {
        val engine = HeartRateGuidanceEngine(HeartRateProfile())
        assertEquals(
            HeartRateGuidance.NO_GUIDANCE,
            engine.update(heartRateBpm = 150.0).guidance,
        )
    }

    @Test
    fun `brief high heart rate spike does not trigger slow down`() {
        val engine = HeartRateGuidanceEngine(
            profile = HeartRateProfile(knownMaxHeartRateBpm = 200),
        )

        repeat(10) {
            val result = engine.update(heartRateBpm = 175.0)
            assertEquals(HeartRateGuidance.HARD, result.guidance)
        }
    }

    @Test
    fun `sustained high heart rate requests slower pace`() {
        val engine = HeartRateGuidanceEngine(
            profile = HeartRateProfile(knownMaxHeartRateBpm = 200),
        )

        var result = HeartRateGuidanceResult(HeartRateGuidance.NO_GUIDANCE)
        repeat(30) {
            result = engine.update(heartRateBpm = 172.0)
        }

        assertEquals(HeartRateGuidance.SLOW_DOWN, result.guidance)
        assertTrue((result.percentOfEstimatedMax ?: 0.0) >= 0.85)
    }

    @Test
    fun `sustained very high heart rate requests walking recovery`() {
        val engine = HeartRateGuidanceEngine(
            profile = HeartRateProfile(knownMaxHeartRateBpm = 200),
        )

        var result = HeartRateGuidanceResult(HeartRateGuidance.NO_GUIDANCE)
        repeat(15) {
            result = engine.update(heartRateBpm = 184.0)
        }

        assertEquals(HeartRateGuidance.WALK_AND_RECOVER, result.guidance)
    }

    @Test
    fun `returning below threshold clears sustained counter`() {
        val engine = HeartRateGuidanceEngine(
            profile = HeartRateProfile(knownMaxHeartRateBpm = 200),
        )

        repeat(20) { engine.update(heartRateBpm = 172.0) }
        engine.update(heartRateBpm = 135.0)
        repeat(15) { engine.update(heartRateBpm = 172.0) }

        val result = engine.update(heartRateBpm = 172.0)
        assertEquals(HeartRateGuidance.HARD, result.guidance)
    }
}

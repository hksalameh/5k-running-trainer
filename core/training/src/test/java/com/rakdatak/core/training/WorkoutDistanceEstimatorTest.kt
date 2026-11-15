package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutDistanceEstimatorTest {
    @Test fun oneMinuteAtSixKmhIsOneHundredMeters() {
        assertEquals(100.0, WorkoutDistanceEstimator.metersForSeconds(6.0, 60), 0.00001)
    }

    @Test fun aHalfHourAtTenKmhIsFiveKilometers() {
        assertEquals(5000.0, WorkoutDistanceEstimator.metersForSeconds(10.0, 1800), 0.00001)
    }

    @Test fun pausesOrVoiceDoNotContributeWhenNoActiveSecondsElapsed() {
        assertEquals(0.0, WorkoutDistanceEstimator.metersForSeconds(7.0, 0), 0.00001)
    }
}

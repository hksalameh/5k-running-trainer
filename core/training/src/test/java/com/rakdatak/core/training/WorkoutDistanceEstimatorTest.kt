package com.rakdatak.core.training

import org.junit.Assert.assertEquals
import org.junit.Test
import com.rakdatak.core.training.model.WorkoutPhaseType

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

    @Test fun averageSpeedUsesRecordedWorkoutTime() {
        assertEquals(6.0, WorkoutDistanceEstimator.averageSpeedKmh(3000.0, 1800)!!, 0.00001)
    }

    @Test fun averageSpeedIsUnknownWithoutMeasuredOrEstimatedDistance() {
        assertEquals(null, WorkoutDistanceEstimator.averageSpeedKmh(0.0, 1800))
        assertEquals(null, WorkoutDistanceEstimator.averageSpeedKmh(500.0, 0))
    }

    @Test fun fiveKilometersOfWalkingDoesNotAdvanceRunningDistance() {
        assertEquals(0.0, WorkoutDistanceEstimator.runningMetersForPhase(WorkoutPhaseType.WARM_UP, 5000.0), 0.00001)
        assertEquals(0.0, WorkoutDistanceEstimator.runningMetersForPhase(WorkoutPhaseType.WALK, 5000.0), 0.00001)
        assertEquals(0.0, WorkoutDistanceEstimator.runningMetersForPhase(WorkoutPhaseType.COOL_DOWN, 5000.0), 0.00001)
        assertEquals(180.0, WorkoutDistanceEstimator.runningMetersForPhase(WorkoutPhaseType.RUN, 180.0), 0.00001)
    }

    @Test fun userTreadmillWarmupPatternIsDistanceOnlyNotRunningProgress() {
        val slowMinute = WorkoutDistanceEstimator.metersForSeconds(2.0, 60)
        val fasterFourMinutes = WorkoutDistanceEstimator.metersForSeconds(3.5, 240)
        assertEquals(266.6666667, slowMinute + fasterFourMinutes, 0.0001)
        assertEquals(0.0, WorkoutDistanceEstimator.runningMetersForPhase(WorkoutPhaseType.WARM_UP, slowMinute + fasterFourMinutes), 0.00001)
    }
}

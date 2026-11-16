package com.rakdatak.core.training

/**
 * Treadmills without Bluetooth speed telemetry do not provide measured distance to the phone.
 * This computes an explicitly labelled estimate from the speed selected on the treadmill.
 * Active workout seconds are counted only after spoken cues, never during pause/announcements.
 */
object WorkoutDistanceEstimator {
    fun metersForSeconds(speedKilometersPerHour: Double, activeSeconds: Int): Double {
        require(speedKilometersPerHour.isFinite() && speedKilometersPerHour in 0.0..30.0)
        require(activeSeconds >= 0)
        return speedKilometersPerHour * activeSeconds / 3.6
    }

    /** Whole-session average, including warm-up and cool-down but excluding spoken instructions. */
    fun averageSpeedKmh(distanceMeters: Double, activeSeconds: Int): Double? {
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0 || activeSeconds <= 0) {
            return null
        }
        return distanceMeters * 3.6 / activeSeconds
    }
}

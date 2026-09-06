package com.rakdatak.wear.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Direct SensorManager fallback for watches where Health Services cannot start or does not expose
 * live heart rate for the selected exercise configuration.
 */
class WearHeartRateSensor(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val _heartRateBpm = MutableStateFlow<Double?>(null)
    val heartRateBpm: StateFlow<Double?> = _heartRateBpm.asStateFlow()

    var sensorAvailable: Boolean = heartRateSensor != null
        private set

    private var registered = false

    fun start(): Boolean {
        val sensor = heartRateSensor ?: run {
            sensorAvailable = false
            return false
        }
        if (!hasHeartRatePermission()) return false
        if (registered) return true

        registered = sensorManager?.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        ) == true
        return registered
    }

    fun stop() {
        if (registered) {
            sensorManager?.unregisterListener(this)
            registered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_HEART_RATE) return
        val value = event.values.firstOrNull()?.toDouble() ?: return
        if (value in 25.0..240.0) {
            _heartRateBpm.value = value
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun hasHeartRatePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 36) {
            READ_HEART_RATE
        } else {
            Manifest.permission.BODY_SENSORS
        }
        return appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
    }
}

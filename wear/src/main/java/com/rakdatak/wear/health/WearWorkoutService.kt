package com.rakdatak.wear.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.rakdatak.core.training.BaselinePlanFactory
import com.rakdatak.core.training.WorkoutSessionEngine
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.wear.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns the active Wear OS workout while the Activity is backgrounded or the display is off.
 * It can be controlled locally from the watch or mirrored from the paired phone.
 */
class WearWorkoutService : LifecycleService() {
    private val plans by lazy { BaselinePlanFactory.create() }
    private val exerciseManager by lazy { WearExerciseManager(this) }
    private val livePublisher by lazy { WearLiveDataPublisher(this) }

    private var engine: WorkoutSessionEngine? = null
    private var tickerJob: Job? = null
    private var metricsJob: Job? = null
    private var gpsEnabled: Boolean = false
    private var lastPhaseIndex: Int = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        metricsJob = lifecycleScope.launch {
            exerciseManager.metrics.collectLatest { metrics ->
                WearWorkoutRepository.updateMetrics(metrics)
                livePublisher.publish(
                    snapshot = WearWorkoutRepository.state.value.snapshot,
                    metrics = metrics,
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                gpsEnabled = intent.getBooleanExtra(EXTRA_GPS_ENABLED, false)
                val planIndex = intent.getIntExtra(EXTRA_PLAN_INDEX, 0)
                val elapsedSeconds = intent.getIntExtra(EXTRA_ELAPSED_SECONDS, 0)
                if (engine == null || engine?.snapshot()?.status !in ACTIVE_STATUSES) {
                    lifecycleScope.launch {
                        startWorkout(
                            planIndex = planIndex,
                            elapsedSeconds = elapsedSeconds,
                        )
                    }
                }
            }

            ACTION_TOGGLE_PAUSE -> lifecycleScope.launch { togglePause() }
            ACTION_PAUSE -> lifecycleScope.launch { pauseWorkout() }
            ACTION_RESUME -> lifecycleScope.launch { resumeWorkout() }
            ACTION_STOP -> lifecycleScope.launch { stopWorkout() }
        }

        return START_NOT_STICKY
    }

    private suspend fun startWorkout(
        planIndex: Int,
        elapsedSeconds: Int,
    ) {
        val safePlanIndex = planIndex.coerceIn(0, plans.lastIndex)
        val plan = plans[safePlanIndex]
        val newEngine = WorkoutSessionEngine(plan)
        engine = newEngine

        val started = if (elapsedSeconds > 0) {
            newEngine.restore(
                elapsedSeconds = elapsedSeconds,
                paused = false,
            )
        } else {
            newEngine.start()
        }

        if (started.status == WorkoutSessionStatus.COMPLETED) {
            WearWorkoutRepository.start(started, gpsEnabled)
            livePublisher.publish(started, exerciseManager.metrics.value)
            stopSelf()
            return
        }

        startInForeground()
        lastPhaseIndex = started.phaseIndex
        WearWorkoutRepository.start(started, gpsEnabled)
        livePublisher.publish(started, exerciseManager.metrics.value)

        // A timed workout remains usable even if Health Services or one sensor is unavailable.
        exerciseManager.start(gpsEnabled = gpsEnabled)
        vibrateTransition()
        beginTicker()
    }

    private fun beginTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                delay(1_000)
                val activeEngine = engine ?: break
                val before = activeEngine.snapshot()
                if (before.status != WorkoutSessionStatus.RUNNING) continue

                val after = activeEngine.tick()
                WearWorkoutRepository.updateSnapshot(after)
                livePublisher.publish(after, exerciseManager.metrics.value)

                if (after.phaseIndex != lastPhaseIndex) {
                    lastPhaseIndex = after.phaseIndex
                    vibrateTransition()
                    updateNotification()
                }

                if (after.status == WorkoutSessionStatus.COMPLETED) {
                    exerciseManager.end()
                    livePublisher.publish(after, exerciseManager.metrics.value)
                    vibrateFinished()
                    stopForegroundAndSelf()
                    break
                }
            }
        }
    }

    private suspend fun togglePause() {
        val activeEngine = engine ?: return
        when (activeEngine.snapshot().status) {
            WorkoutSessionStatus.RUNNING -> pauseWorkout()
            WorkoutSessionStatus.PAUSED -> resumeWorkout()
            else -> Unit
        }
    }

    private suspend fun pauseWorkout() {
        val activeEngine = engine ?: return
        if (activeEngine.snapshot().status != WorkoutSessionStatus.RUNNING) return

        exerciseManager.pause()
        val paused = activeEngine.pause()
        WearWorkoutRepository.updateSnapshot(paused)
        livePublisher.publish(paused, exerciseManager.metrics.value)
        updateNotification()
    }

    private suspend fun resumeWorkout() {
        val activeEngine = engine ?: return
        if (activeEngine.snapshot().status != WorkoutSessionStatus.PAUSED) return

        exerciseManager.resume()
        val resumed = activeEngine.resume()
        WearWorkoutRepository.updateSnapshot(resumed)
        livePublisher.publish(resumed, exerciseManager.metrics.value)
        updateNotification()
    }

    private suspend fun stopWorkout() {
        val activeEngine = engine ?: return stopForegroundAndSelf()
        val stopped = activeEngine.stop()
        WearWorkoutRepository.updateSnapshot(stopped)
        exerciseManager.end()
        livePublisher.publish(stopped, exerciseManager.metrics.value)
        vibrateFinished()
        stopForegroundAndSelf()
    }

    private fun startInForeground() {
        val foregroundTypes =
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or
                if (gpsEnabled) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            foregroundTypes,
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val state = WearWorkoutRepository.state.value
        val snapshot = state.snapshot
        val paused = snapshot?.status == WorkoutSessionStatus.PAUSED
        val title = when {
            paused -> "ركضتك — متوقف مؤقتًا"
            snapshot != null -> "ركضتك — التمرين مستمر"
            else -> "ركضتك"
        }
        val text = snapshot?.let {
            "${phaseLabel(it.currentPhase.type.name)} • ${formatTime(it.phaseRemainingSeconds)}"
        } ?: "جاري تجهيز التمرين"

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(snapshot?.status in ACTIVE_STATUSES)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تمرين ركضتك",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "يبقي تمرين ركضتك فعالًا أثناء إطفاء الشاشة"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun vibrateTransition() {
        vibrator()?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrateFinished() {
        vibrator()?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 260), -1)
        )
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun stopForegroundAndSelf() {
        tickerJob?.cancel()
        tickerJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        metricsJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "rakdatak_active_workout"
        private const val NOTIFICATION_ID = 5001
        private const val ACTION_START = "com.rakdatak.wear.action.START"
        private const val ACTION_TOGGLE_PAUSE = "com.rakdatak.wear.action.TOGGLE_PAUSE"
        private const val ACTION_PAUSE = "com.rakdatak.wear.action.PAUSE"
        private const val ACTION_RESUME = "com.rakdatak.wear.action.RESUME"
        private const val ACTION_STOP = "com.rakdatak.wear.action.STOP"
        private const val EXTRA_GPS_ENABLED = "gps_enabled"
        private const val EXTRA_PLAN_INDEX = "plan_index"
        private const val EXTRA_ELAPSED_SECONDS = "elapsed_seconds"

        private val ACTIVE_STATUSES = setOf(
            WorkoutSessionStatus.RUNNING,
            WorkoutSessionStatus.PAUSED,
        )

        fun start(
            context: Context,
            gpsEnabled: Boolean,
            planIndex: Int = 0,
            elapsedSeconds: Int = 0,
        ) {
            val intent = Intent(context, WearWorkoutService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GPS_ENABLED, gpsEnabled)
                putExtra(EXTRA_PLAN_INDEX, planIndex)
                putExtra(EXTRA_ELAPSED_SECONDS, elapsedSeconds)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun togglePause(context: Context) {
            context.startService(
                Intent(context, WearWorkoutService::class.java).apply {
                    action = ACTION_TOGGLE_PAUSE
                }
            )
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, WearWorkoutService::class.java).apply {
                    action = ACTION_PAUSE
                }
            )
        }

        fun resume(context: Context) {
            context.startService(
                Intent(context, WearWorkoutService::class.java).apply {
                    action = ACTION_RESUME
                }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WearWorkoutService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }

        private fun formatTime(totalSeconds: Int): String =
            "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

        private fun phaseLabel(raw: String): String = when (raw) {
            "WARM_UP" -> "إحماء"
            "WALK" -> "مشي"
            "RUN" -> "ركض"
            "COOL_DOWN" -> "تهدئة"
            else -> raw
        }
    }
}

package com.rakdatak.app.schedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rakdatak.app.MainActivity
import com.rakdatak.core.training.TrainingScheduleEngine
import com.rakdatak.core.training.TrainingSlot
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class TrainingReminderScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val scheduleEngine = TrainingScheduleEngine()

    fun replaceNextReminder(
        after: LocalDateTime,
        slots: List<TrainingSlot>,
    ) {
        if (slots.size != 3) return
        workManager.cancelAllWorkByTag(TAG)
        enqueueNextReminder(after, slots)
    }

    fun enqueueNextReminder(
        after: LocalDateTime,
        slots: List<TrainingSlot>,
    ) {
        if (slots.size != 3) return
        val scheduledAt = scheduleEngine.nextSlot(after = after, slots = slots)
        val delayMillis = Duration.between(LocalDateTime.now(), scheduledAt)
            .toMillis()
            .coerceAtLeast(0L)

        val request = OneTimeWorkRequest.Builder(TrainingReminderWorker::class.java)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()

        workManager.enqueue(request)
    }

    companion object {
        const val TAG = "rakdatak-training-reminder"
    }
}

class TrainingReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        postReminderIfAllowed()

        val slots = TrainingScheduleRepository(applicationContext).schedule.first().slots
        if (slots.size == 3) {
            // This worker schedules only the next occurrence. That keeps changes to the user's
            // chosen days/times simple: replacing the schedule only needs to cancel tagged work.
            TrainingReminderScheduler(applicationContext).enqueueNextReminder(
                after = LocalDateTime.now().plusMinutes(1),
                slots = slots,
            )
        }
        return Result.success()
    }

    private fun postReminderIfAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تذكيرات التدريب",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "تذكير بموعد تمرين ركضتك القادم"
                }
            )
        }

        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("موعد ركضتك")
            .setContentText("جاهز لخطوتك اليوم؟ افتح ركضتك وابدأ بهدوء.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "rakdatak_training_reminders"
        const val NOTIFICATION_ID = 5101
    }
}

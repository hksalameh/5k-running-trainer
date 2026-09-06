package com.rakdatak.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.rakdatak.app.feedback.PostWorkoutFeedbackScreen
import com.rakdatak.app.profile.OnboardingScreen
import com.rakdatak.app.profile.RunnerProfile
import com.rakdatak.app.profile.RunnerProfileRepository
import com.rakdatak.app.progress.TrainingProgress
import com.rakdatak.app.progress.TrainingProgressRepository
import com.rakdatak.app.schedule.TrainingReminderScheduler
import com.rakdatak.app.schedule.TrainingScheduleRepository
import com.rakdatak.app.schedule.TrainingScheduleScreen
import com.rakdatak.app.schedule.UserTrainingSchedule
import com.rakdatak.app.settings.AppSettings
import com.rakdatak.app.settings.AppSettingsRepository
import com.rakdatak.app.settings.SettingsScreen
import com.rakdatak.app.ui.RakdatakHomeScreen
import com.rakdatak.app.ui.WorkoutScreen
import com.rakdatak.app.workout.ActiveWorkoutRepository
import com.rakdatak.core.training.BaselinePlanFactory
import com.rakdatak.core.training.WorkoutSessionEngine
import com.rakdatak.core.training.WorkoutSessionStatus
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class AppScreen {
    HOME,
    WORKOUT,
    SUMMARY,
    SETTINGS,
    SCHEDULE,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    RakdatakRoot()
                }
            }
        }
    }
}

@Composable
private fun RakdatakRoot() {
    val context = LocalContext.current
    val profileRepository = remember { RunnerProfileRepository(context.applicationContext) }
    val progressRepository = remember { TrainingProgressRepository(context.applicationContext) }
    val settingsRepository = remember { AppSettingsRepository(context.applicationContext) }
    val activeWorkoutRepository = remember { ActiveWorkoutRepository(context.applicationContext) }
    val scheduleRepository = remember { TrainingScheduleRepository(context.applicationContext) }
    val reminderScheduler = remember { TrainingReminderScheduler(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val profile by produceState<RunnerProfile?>(initialValue = null, profileRepository) {
        profileRepository.profile.collectLatest { value = it }
    }
    val progress by produceState(initialValue = TrainingProgress(), progressRepository) {
        progressRepository.progress.collectLatest { value = it }
    }
    val settings by produceState(initialValue = AppSettings(), settingsRepository) {
        settingsRepository.settings.collectLatest { value = it }
    }
    val trainingSchedule by produceState(
        initialValue = UserTrainingSchedule(),
        scheduleRepository,
    ) {
        scheduleRepository.schedule.collectLatest { value = it }
    }

    when {
        profile == null -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {}

        profile?.onboardingComplete != true -> OnboardingScreen(
            onComplete = { age, environment, safetyReviewNeeded ->
                scope.launch {
                    profileRepository.completeOnboarding(
                        ageYears = age,
                        trainingEnvironment = environment,
                        safetyReviewNeeded = safetyReviewNeeded,
                    )
                }
            },
        )

        else -> RakdatakApp(
            profile = profile!!,
            progress = progress,
            progressRepository = progressRepository,
            settings = settings,
            settingsRepository = settingsRepository,
            activeWorkoutRepository = activeWorkoutRepository,
            trainingSchedule = trainingSchedule,
            scheduleRepository = scheduleRepository,
            reminderScheduler = reminderScheduler,
        )
    }
}

@Composable
private fun RakdatakApp(
    profile: RunnerProfile,
    progress: TrainingProgress,
    progressRepository: TrainingProgressRepository,
    settings: AppSettings,
    settingsRepository: AppSettingsRepository,
    activeWorkoutRepository: ActiveWorkoutRepository,
    trainingSchedule: UserTrainingSchedule,
    scheduleRepository: TrainingScheduleRepository,
    reminderScheduler: TrainingReminderScheduler,
) {
    val plans = remember { BaselinePlanFactory.create() }
    val planIndex = progress.currentPlanIndex.coerceIn(0, plans.lastIndex)
    val plan = plans[planIndex]
    val scope = rememberCoroutineScope()

    val restoredWorkout = remember { activeWorkoutRepository.load() }
    val initialEngine = remember {
        WorkoutSessionEngine(plan).also { restoredEngine ->
            restoredWorkout?.let { saved ->
                restoredEngine.restore(
                    elapsedSeconds = saved.elapsedSeconds,
                    paused = true,
                )
            }
        }
    }

    var engine by remember { mutableStateOf(initialEngine) }
    var snapshot by remember { mutableStateOf(initialEngine.snapshot()) }
    var distanceMeters by remember {
        mutableStateOf(restoredWorkout?.distanceMeters?.coerceAtLeast(0.0) ?: 0.0)
    }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var sessionRecorded by remember { mutableStateOf(false) }

    val hasActiveWorkout = snapshot.status == WorkoutSessionStatus.RUNNING ||
        snapshot.status == WorkoutSessionStatus.PAUSED

    LaunchedEffect(screen, snapshot.status) {
        if (screen == AppScreen.WORKOUT && snapshot.status == WorkoutSessionStatus.RUNNING) {
            var lastTickRealtime = SystemClock.elapsedRealtime()

            while (screen == AppScreen.WORKOUT && snapshot.status == WorkoutSessionStatus.RUNNING) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                val elapsedWholeSeconds = ((now - lastTickRealtime) / 1_000L).toInt()
                if (elapsedWholeSeconds <= 0) continue

                lastTickRealtime += elapsedWholeSeconds * 1_000L
                snapshot = engine.tick(elapsedWholeSeconds)
                activeWorkoutRepository.save(snapshot, distanceMeters)

                if (snapshot.status == WorkoutSessionStatus.COMPLETED) {
                    if (!sessionRecorded) {
                        sessionRecorded = true
                        progressRepository.recordWorkout(
                            elapsedSeconds = snapshot.totalElapsedSeconds,
                            completionRatio = snapshot.completionRatio,
                            distanceMeters = distanceMeters,
                        )
                    }
                    activeWorkoutRepository.clear()
                    screen = AppScreen.SUMMARY
                }
            }
        }
    }

    when (screen) {
        AppScreen.HOME -> RakdatakHomeScreen(
            safetyReviewNeeded = profile.safetyReviewNeeded,
            progress = progress,
            currentPlanTitle = plan.titleAr,
            planProgress = (planIndex + 1).toFloat() / plans.size.toFloat(),
            activeWorkout = snapshot.takeIf { hasActiveWorkout },
            activeDistanceMeters = distanceMeters,
            onStartWorkout = {
                if (!profile.safetyReviewNeeded) {
                    engine = WorkoutSessionEngine(plan)
                    snapshot = engine.start()
                    distanceMeters = 0.0
                    sessionRecorded = false
                    activeWorkoutRepository.save(snapshot, distanceMeters)
                    screen = AppScreen.WORKOUT
                }
            },
            onResumeWorkout = {
                snapshot = if (snapshot.status == WorkoutSessionStatus.PAUSED) {
                    engine.resume()
                } else {
                    snapshot
                }
                activeWorkoutRepository.save(snapshot, distanceMeters)
                screen = AppScreen.WORKOUT
            },
            onOpenSettings = { screen = AppScreen.SETTINGS },
        )

        AppScreen.WORKOUT -> WorkoutScreen(
            snapshot = snapshot,
            initialDistanceMeters = distanceMeters,
            soundCuesEnabled = settings.soundCuesEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            keepScreenOn = settings.keepScreenOnDuringWorkout,
            onDistanceChanged = { updatedDistance ->
                if (updatedDistance > distanceMeters) {
                    distanceMeters = updatedDistance
                    activeWorkoutRepository.save(snapshot, updatedDistance)
                }
            },
            onPauseResume = {
                snapshot = if (snapshot.status == WorkoutSessionStatus.PAUSED) {
                    engine.resume()
                } else {
                    engine.pause()
                }
                activeWorkoutRepository.save(snapshot, distanceMeters)
            },
            onBackToHome = {
                if (snapshot.status == WorkoutSessionStatus.RUNNING) {
                    snapshot = engine.pause()
                }
                activeWorkoutRepository.save(snapshot, distanceMeters)
                screen = AppScreen.HOME
            },
            onFinish = {
                val stopped = engine.stop()
                snapshot = stopped
                if (!sessionRecorded) {
                    sessionRecorded = true
                    scope.launch {
                        progressRepository.recordWorkout(
                            elapsedSeconds = stopped.totalElapsedSeconds,
                            completionRatio = stopped.completionRatio,
                            distanceMeters = distanceMeters,
                        )
                    }
                }
                activeWorkoutRepository.clear()
                screen = AppScreen.HOME
            },
        )

        AppScreen.SUMMARY -> PostWorkoutFeedbackScreen(
            planId = plan.id,
            snapshot = snapshot,
            onDone = { _, _, decision ->
                scope.launch {
                    progressRepository.applyTrainingAction(
                        action = decision.action,
                        planCount = plans.size,
                    )
                    screen = AppScreen.HOME
                }
            },
            onSkip = { screen = AppScreen.HOME },
        )

        AppScreen.SETTINGS -> SettingsScreen(
            settings = settings,
            trainingScheduleSummary = if (trainingSchedule.isConfigured) {
                "تم ضبط 3 مواعيد أسبوعية. اضغط للتعديل."
            } else {
                "اختر 3 أيام وأوقات أسبوعية لتذكيرك بالتمرين."
            },
            onBack = { screen = AppScreen.HOME },
            onOpenTrainingSchedule = { screen = AppScreen.SCHEDULE },
            onSoundCuesChanged = { enabled ->
                scope.launch { settingsRepository.setSoundCuesEnabled(enabled) }
            },
            onVibrationChanged = { enabled ->
                scope.launch { settingsRepository.setVibrationEnabled(enabled) }
            },
            onKeepScreenOnChanged = { enabled ->
                scope.launch { settingsRepository.setKeepScreenOnDuringWorkout(enabled) }
            },
        )

        AppScreen.SCHEDULE -> TrainingScheduleScreen(
            existingSlots = trainingSchedule.slots,
            onSave = { slots ->
                scope.launch {
                    scheduleRepository.saveSlots(slots)
                    reminderScheduler.replaceNextReminder(
                        after = LocalDateTime.now(),
                        slots = slots,
                    )
                    screen = AppScreen.SETTINGS
                }
            },
            onCancel = { screen = AppScreen.SETTINGS },
        )
    }
}

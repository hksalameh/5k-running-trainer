package com.rakdatak.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.rakdatak.app.feedback.PostWorkoutFeedbackScreen
import com.rakdatak.app.history.WorkoutHistoryRepository
import com.rakdatak.app.history.WorkoutHistoryScreen
import com.rakdatak.app.profile.OnboardingScreen
import com.rakdatak.app.profile.ProfileSettingsScreen
import com.rakdatak.app.profile.RunnerProfile
import com.rakdatak.app.profile.RunnerProfileRepository
import com.rakdatak.app.profile.TrainingEnvironment
import com.rakdatak.app.progress.TrainingProgress
import com.rakdatak.app.progress.TrainingProgressRepository
import com.rakdatak.app.schedule.TrainingReminderScheduler
import com.rakdatak.app.schedule.TrainingScheduleRepository
import com.rakdatak.app.schedule.TrainingScheduleScreen
import com.rakdatak.app.schedule.UserTrainingSchedule
import com.rakdatak.app.settings.AppSettings
import com.rakdatak.app.settings.AppSettingsRepository
import com.rakdatak.app.settings.SettingsScreen
import com.rakdatak.app.ui.PreWorkoutCountdownScreen
import com.rakdatak.app.ui.RakdatakHomeScreen
import com.rakdatak.app.ui.WorkoutScreen
import com.rakdatak.app.wear.PhoneWearController
import com.rakdatak.app.workout.ActiveWorkoutRepository
import com.rakdatak.core.training.BaselinePlanFactory
import com.rakdatak.core.training.TrainingScheduleEngine
import com.rakdatak.core.training.WorkoutDistanceEstimator
import com.rakdatak.core.training.WorkoutSessionEngine
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.TrainingAction
import com.rakdatak.core.training.model.WorkoutPhaseType
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class AppScreen {
    HOME,
    COUNTDOWN,
    WORKOUT,
    SUMMARY,
    HISTORY,
    SETTINGS,
    PROFILE,
    SCHEDULE,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF111111))
                            .safeDrawingPadding(),
                    ) {
                        RakdatakRoot()
                    }
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
    val historyRepository = remember { WorkoutHistoryRepository(context.applicationContext) }
    val scheduleRepository = remember { TrainingScheduleRepository(context.applicationContext) }
    val reminderScheduler = remember { TrainingReminderScheduler(context.applicationContext) }
    val wearController = remember { PhoneWearController(context.applicationContext) }
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
            profileRepository = profileRepository,
            progress = progress,
            progressRepository = progressRepository,
            settings = settings,
            settingsRepository = settingsRepository,
            activeWorkoutRepository = activeWorkoutRepository,
            historyRepository = historyRepository,
            trainingSchedule = trainingSchedule,
            scheduleRepository = scheduleRepository,
            reminderScheduler = reminderScheduler,
            wearController = wearController,
        )
    }
}

@Composable
private fun RakdatakApp(
    profile: RunnerProfile,
    profileRepository: RunnerProfileRepository,
    progress: TrainingProgress,
    progressRepository: TrainingProgressRepository,
    settings: AppSettings,
    settingsRepository: AppSettingsRepository,
    activeWorkoutRepository: ActiveWorkoutRepository,
    historyRepository: WorkoutHistoryRepository,
    trainingSchedule: UserTrainingSchedule,
    scheduleRepository: TrainingScheduleRepository,
    reminderScheduler: TrainingReminderScheduler,
    wearController: PhoneWearController,
) {
    val context = LocalContext.current
    val plans = remember { BaselinePlanFactory.create() }
    val scheduleEngine = remember { TrainingScheduleEngine() }
    val planIndex = progress.currentPlanIndex.coerceIn(0, plans.lastIndex)
    val plan = plans[planIndex]
    val scope = rememberCoroutineScope()

    val restoredWorkout = remember { activeWorkoutRepository.load() }
    val initialEngine = remember {
        val restoredPlan = restoredWorkout?.planId
            ?.let { savedId -> plans.firstOrNull { it.id == savedId } }
            ?: plan
        WorkoutSessionEngine(restoredPlan).also { restoredEngine ->
            restoredWorkout?.let { saved ->
                restoredEngine.restore(
                    elapsedSeconds = saved.elapsedSeconds,
                    paused = true,
                    recordedLongestContinuousRunSeconds = saved.longestContinuousRunSeconds,
                )
            }
        }
    }

    var engine by remember { mutableStateOf(initialEngine) }
    var snapshot by remember { mutableStateOf(initialEngine.snapshot()) }
    var distanceMeters by remember {
        mutableStateOf(restoredWorkout?.distanceMeters?.coerceAtLeast(0.0) ?: 0.0)
    }
    var runDistanceMeters by remember {
        mutableStateOf(restoredWorkout?.runDistanceMeters?.coerceAtLeast(0.0) ?: 0.0)
    }
    val treadmillMode = profile.trainingEnvironment == TrainingEnvironment.TREADMILL
    val manageWearFromPhone = !context.packageName.endsWith(".preview")
    val speedPreferences = remember {
        context.applicationContext.getSharedPreferences("treadmill_speed", android.content.Context.MODE_PRIVATE)
    }
    var walkSpeedKmh by remember {
        mutableStateOf(speedPreferences.getFloat("walk_kmh", 4.5f).toDouble())
    }
    var runSpeedKmh by remember {
        mutableStateOf(speedPreferences.getFloat("run_kmh", 7f).toDouble())
    }
    var firstWarmupSpeedKmh by remember {
        mutableStateOf(speedPreferences.getFloat("warmup_first_kmh", 2f).toDouble())
    }
    var laterWarmupSpeedKmh by remember {
        mutableStateOf(speedPreferences.getFloat("warmup_later_kmh", 3.5f).toDouble())
    }
    val currentWalkSpeed = rememberUpdatedState(walkSpeedKmh)
    val currentRunSpeed = rememberUpdatedState(runSpeedKmh)
    val currentFirstWarmupSpeed = rememberUpdatedState(firstWarmupSpeedKmh)
    val currentLaterWarmupSpeed = rememberUpdatedState(laterWarmupSpeedKmh)
    val speedForPhase: (WorkoutPhaseType, Int) -> Double = { phaseType, elapsedInPhase ->
        when (phaseType) {
            WorkoutPhaseType.RUN -> runSpeedKmh
            WorkoutPhaseType.WARM_UP -> if (elapsedInPhase < 60) firstWarmupSpeedKmh else laterWarmupSpeedKmh
            else -> walkSpeedKmh
        }
    }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var sessionRecorded by remember { mutableStateOf(false) }

    PhoneWorkoutCoachEffect(
        snapshot = snapshot,
        soundCuesEnabled = settings.soundCuesEnabled,
        workoutVoice = settings.workoutVoice,
        vibrationEnabled = settings.vibrationEnabled,
        onPhaseInstructionFinished = { announcedPhase ->
            if (snapshot.status == WorkoutSessionStatus.ANNOUNCING &&
                snapshot.phaseIndex == announcedPhase
            ) {
                snapshot = engine.instructionFinished()
                activeWorkoutRepository.save(snapshot, distanceMeters, runDistanceMeters)
            }
        },
    )

    // A TTS engine that never initializes must not trap the runner indefinitely.
    LaunchedEffect(snapshot.status, snapshot.phaseIndex) {
        if (snapshot.status == WorkoutSessionStatus.ANNOUNCING) {
            val waitingFor = snapshot.phaseIndex
            delay(12_000)
            if (snapshot.status == WorkoutSessionStatus.ANNOUNCING &&
                snapshot.phaseIndex == waitingFor
            ) {
                snapshot = engine.instructionFinished()
                activeWorkoutRepository.save(snapshot, distanceMeters, runDistanceMeters)
            }
        }
    }

    val hasActiveWorkout = snapshot.status == WorkoutSessionStatus.ANNOUNCING ||
        snapshot.status == WorkoutSessionStatus.RUNNING ||
        snapshot.status == WorkoutSessionStatus.PAUSED
    val workoutPlan = plans.firstOrNull { it.id == snapshot.planId } ?: plan
    val historyEntries = remember(screen, progress.savedWorkouts) {
        if (screen == AppScreen.HISTORY) historyRepository.load() else emptyList()
    }
    val nextWorkoutAt = remember(trainingSchedule.slots, screen) {
        if (!trainingSchedule.isConfigured) {
            null
        } else {
            runCatching {
                scheduleEngine.nextSlot(
                    after = LocalDateTime.now(),
                    slots = trainingSchedule.slots,
                )
            }.getOrNull()
        }
    }

    LaunchedEffect(screen, snapshot.status) {
        if (screen == AppScreen.WORKOUT && snapshot.status == WorkoutSessionStatus.RUNNING) {
            var lastTickRealtime = SystemClock.elapsedRealtime()

            while (screen == AppScreen.WORKOUT && snapshot.status == WorkoutSessionStatus.RUNNING) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                val elapsedWholeSeconds = ((now - lastTickRealtime) / 1_000L).toInt()
                if (elapsedWholeSeconds <= 0) continue

                lastTickRealtime += elapsedWholeSeconds * 1_000L
                val beforeTick = snapshot
                val afterTick = engine.tick(elapsedWholeSeconds)
                if (treadmillMode) {
                    val activeSeconds = afterTick.totalElapsedSeconds - beforeTick.totalElapsedSeconds
                    if (activeSeconds > 0) {
                        val intervalSpeed = when (beforeTick.currentPhase.type) {
                            WorkoutPhaseType.RUN -> currentRunSpeed.value
                            WorkoutPhaseType.WARM_UP -> if (beforeTick.phaseElapsedSeconds < 60) {
                                currentFirstWarmupSpeed.value
                            } else {
                                currentLaterWarmupSpeed.value
                            }
                            else -> currentWalkSpeed.value
                        }
                        val intervalDistance = WorkoutDistanceEstimator.metersForSeconds(
                            speedKilometersPerHour = intervalSpeed,
                            activeSeconds = activeSeconds,
                        )
                        distanceMeters += intervalDistance
                        runDistanceMeters += WorkoutDistanceEstimator.runningMetersForPhase(
                            beforeTick.currentPhase.type,
                            intervalDistance,
                        )
                    }
                }
                snapshot = afterTick
                activeWorkoutRepository.save(snapshot, distanceMeters, runDistanceMeters)

                if (snapshot.status == WorkoutSessionStatus.COMPLETED) {
                    if (!sessionRecorded) {
                        sessionRecorded = true
                        progressRepository.recordWorkout(
                            elapsedSeconds = snapshot.totalElapsedSeconds,
                            completionRatio = snapshot.completionRatio,
                            distanceMeters = distanceMeters,
                            continuousRunSeconds = snapshot.longestContinuousRunSeconds,
                            runDistanceMeters = runDistanceMeters,
                        )
                        historyRepository.recordWorkout(
                            elapsedSeconds = snapshot.totalElapsedSeconds,
                            completionRatio = snapshot.completionRatio,
                            distanceMeters = distanceMeters,
                            estimatedDistance = treadmillMode,
                            runDistanceMeters = runDistanceMeters,
                        )
                        if (manageWearFromPhone) wearController.stop()
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
            currentPlan = plan,
            planProgress = (progress.completedWorkouts.toFloat() / plans.size.toFloat())
                .coerceIn(0f, 1f),
            nextWorkoutAt = nextWorkoutAt,
            scheduleConfigured = trainingSchedule.isConfigured,
            activeWorkout = snapshot.takeIf { hasActiveWorkout },
            activeDistanceMeters = distanceMeters,
            onStartWorkout = {
                if (!profile.safetyReviewNeeded) {
                    engine = WorkoutSessionEngine(plan)
                    snapshot = engine.snapshot()
                    distanceMeters = 0.0
                    runDistanceMeters = 0.0
                    sessionRecorded = false
                    screen = AppScreen.COUNTDOWN
                }
            },
            onResumeWorkout = {
                snapshot = if (snapshot.status == WorkoutSessionStatus.PAUSED) {
                    engine.resume()
                } else {
                    snapshot
                }
                activeWorkoutRepository.save(snapshot, distanceMeters, runDistanceMeters)
                if (manageWearFromPhone) {
                    wearController.start(
                        planIndex = planIndex,
                        elapsedSeconds = snapshot.totalElapsedSeconds,
                        gpsEnabled = profile.trainingEnvironment != TrainingEnvironment.TREADMILL,
                    )
                    wearController.resume()
                }
                screen = AppScreen.WORKOUT
            },
            onOpenHistory = { screen = AppScreen.HISTORY },
            onOpenSettings = { screen = AppScreen.SETTINGS },
            onOpenSchedule = { screen = AppScreen.SCHEDULE },
        )

        AppScreen.COUNTDOWN -> PreWorkoutCountdownScreen(
            firstPhase = plan.phases.first(),
            soundCuesEnabled = settings.soundCuesEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            onFinished = {
                snapshot = engine.start()
                activeWorkoutRepository.save(snapshot, distanceMeters, runDistanceMeters)
                if (manageWearFromPhone) {
                    wearController.start(
                        planIndex = planIndex,
                        elapsedSeconds = 0,
                        gpsEnabled = profile.trainingEnvironment != TrainingEnvironment.TREADMILL,
                    )
                }
                screen = AppScreen.WORKOUT
            },
        )

        AppScreen.WORKOUT -> WorkoutScreen(
            snapshot = snapshot,
            plannedDurationSeconds = workoutPlan.totalDurationSeconds,
            initialDistanceMeters = distanceMeters,
            gpsTrackingEnabled = !treadmillMode,
            treadmillMode = treadmillMode,
            treadmillSpeedKmh = speedForPhase(snapshot.currentPhase.type, snapshot.phaseElapsedSeconds),
            warmupSpeedGuidance = if (treadmillMode && snapshot.currentPhase.type == WorkoutPhaseType.WARM_UP) {
                if (snapshot.phaseElapsedSeconds < 60) {
                    "أول دقيقة: سرعة %.1f كم/س، وبعدها %.1f كم/س".format(firstWarmupSpeedKmh, laterWarmupSpeedKmh)
                } else {
                    "انتهت أول دقيقة • اضبط التردمل على %.1f كم/س".format(laterWarmupSpeedKmh)
                }
            } else null,
            runDistanceMeters = runDistanceMeters,
            onTreadmillSpeedChange = { newSpeed ->
                val adjusted = newSpeed.coerceIn(1.0, 20.0)
                when (snapshot.currentPhase.type) {
                    WorkoutPhaseType.RUN -> {
                        runSpeedKmh = adjusted
                        speedPreferences.edit().putFloat("run_kmh", adjusted.toFloat()).apply()
                    }
                    WorkoutPhaseType.WARM_UP -> {
                        if (snapshot.phaseElapsedSeconds < 60) {
                            firstWarmupSpeedKmh = adjusted
                            speedPreferences.edit().putFloat("warmup_first_kmh", adjusted.toFloat()).apply()
                        } else {
                            laterWarmupSpeedKmh = adjusted
                            speedPreferences.edit().putFloat("warmup_later_kmh", adjusted.toFloat()).apply()
                        }
                    }
                    else -> {
                        walkSpeedKmh = adjusted
                        speedPreferences.edit().putFloat("walk_kmh", adjusted.toFloat()).apply()
                    }
                }
            },
            keepScreenOn = settings.keepScreenOnDuringWorkout,
            onDistanceChanged = { updatedDistance ->
                if (updatedDistance > distanceMeters) {
                    if (snapshot.status == WorkoutSessionStatus.RUNNING) {
                        runDistanceMeters += WorkoutDistanceEstimator.runningMetersForPhase(
                            snapshot.currentPhase.type,
                            updatedDistance - distanceMeters,
                        )
                    }
                    distanceMeters = updatedDistance
                    activeWorkoutRepository.save(snapshot, updatedDistance, runDistanceMeters)
                }
            },
            onPauseResume = {
                snapshot = if (snapshot.status == WorkoutSessionStatus.PAUSED) {
                    if (manageWearFromPhone) wearController.resume()
                    engine.resume()
                } else {
                    if (manageWearFromPhone) wearController.pause()
                    engine.pause()
                }
                activeWorkoutRepository.save(snapshot, distanceMeters, runDistanceMeters)
            },
            onBackToHome = {
                if (snapshot.status == WorkoutSessionStatus.RUNNING ||
                    snapshot.status == WorkoutSessionStatus.ANNOUNCING
                ) {
                    snapshot = engine.pause()
                    if (manageWearFromPhone) wearController.pause()
                }
                activeWorkoutRepository.save(snapshot, distanceMeters, runDistanceMeters)
                screen = AppScreen.HOME
            },
            onFinish = {
                val stopped = engine.stop()
                snapshot = stopped
                if (manageWearFromPhone) wearController.stop()
                if (!sessionRecorded) {
                    sessionRecorded = true
                    historyRepository.recordWorkout(
                        elapsedSeconds = stopped.totalElapsedSeconds,
                        completionRatio = stopped.completionRatio,
                        distanceMeters = distanceMeters,
                        estimatedDistance = treadmillMode,
                        runDistanceMeters = runDistanceMeters,
                    )
                    scope.launch {
                        progressRepository.recordWorkout(
                            elapsedSeconds = stopped.totalElapsedSeconds,
                            completionRatio = stopped.completionRatio,
                            distanceMeters = distanceMeters,
                            continuousRunSeconds = stopped.longestContinuousRunSeconds,
                            runDistanceMeters = runDistanceMeters,
                        )
                    }
                }
                activeWorkoutRepository.clear()
                screen = AppScreen.SUMMARY
            },
        )

        AppScreen.SUMMARY -> PostWorkoutFeedbackScreen(
            planId = plan.id,
            snapshot = snapshot,
            distanceMeters = distanceMeters,
            runDistanceMeters = runDistanceMeters,
            distanceIsEstimated = treadmillMode,
            longestContinuousRunSeconds = snapshot.longestContinuousRunSeconds,
            onDone = { _, _, decision ->
                scope.launch {
                    progressRepository.applyTrainingAction(
                        action = decision.action,
                        planCount = plans.size,
                    )
                    screen = AppScreen.HOME
                }
            },
            onSkip = {
                scope.launch {
                    if (snapshot.status == WorkoutSessionStatus.COMPLETED) {
                        progressRepository.applyTrainingAction(
                            action = TrainingAction.ADVANCE,
                            planCount = plans.size,
                        )
                    }
                    screen = AppScreen.HOME
                }
            },
        )

        AppScreen.HISTORY -> WorkoutHistoryScreen(
            entries = historyEntries,
            onBack = { screen = AppScreen.HOME },
        )

        AppScreen.SETTINGS -> SettingsScreen(
            settings = settings,
            profileSummary = profileSummary(profile),
            currentWeek = plan.week,
            previewWeekSelectionEnabled = context.packageName.endsWith(".preview"),
            onPreviewWeekChanged = { week ->
                scope.launch {
                    progressRepository.selectWeekForPreview(week)
                    screen = AppScreen.HOME
                }
            },
            trainingScheduleSummary = if (trainingSchedule.isConfigured) {
                "تم ضبط 3 مواعيد أسبوعية. اضغط للتعديل."
            } else {
                "اختر 3 أيام وأوقات أسبوعية لتذكيرك بالتمرين."
            },
            onBack = { screen = AppScreen.HOME },
            onOpenProfileSettings = { screen = AppScreen.PROFILE },
            onOpenTrainingSchedule = { screen = AppScreen.SCHEDULE },
            onSoundCuesChanged = { enabled ->
                scope.launch { settingsRepository.setSoundCuesEnabled(enabled) }
            },
            onWorkoutVoiceChanged = { voice ->
                scope.launch { settingsRepository.setWorkoutVoice(voice) }
            },
            onVibrationChanged = { enabled ->
                scope.launch { settingsRepository.setVibrationEnabled(enabled) }
            },
            onKeepScreenOnChanged = { enabled ->
                scope.launch { settingsRepository.setKeepScreenOnDuringWorkout(enabled) }
            },
        )

        AppScreen.PROFILE -> ProfileSettingsScreen(
            profile = profile,
            onSave = { ageYears, environment, safetyReviewNeeded ->
                scope.launch {
                    profileRepository.updateProfile(
                        ageYears = ageYears,
                        trainingEnvironment = environment,
                        safetyReviewNeeded = safetyReviewNeeded,
                    )
                    screen = AppScreen.SETTINGS
                }
            },
            onBack = { screen = AppScreen.SETTINGS },
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

private fun profileSummary(profile: RunnerProfile): String {
    val environment = when (profile.trainingEnvironment) {
        TrainingEnvironment.OUTDOOR -> "خارج المنزل"
        TrainingEnvironment.TREADMILL -> "تردمل"
        TrainingEnvironment.BOTH -> "خارجي + تردمل"
    }
    val age = profile.ageYears?.let { "$it سنة" } ?: "العمر غير محدد"
    val safety = if (profile.safetyReviewNeeded) "مراجعة السلامة مفعلة" else "جاهز للركض"
    return "$age • $environment • $safety"
}

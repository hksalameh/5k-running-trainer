package com.rakdatak.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import com.rakdatak.app.ui.TrainingPlanOverviewScreen
import com.rakdatak.app.ui.WorkoutScreen
import com.rakdatak.app.wear.*
import com.rakdatak.core.training.WorkoutOwner
import com.rakdatak.core.training.WorkoutSync
import com.rakdatak.app.wear.PhoneWearController
import com.rakdatak.app.workout.ActiveWorkoutRepository
import com.rakdatak.core.training.BaselinePlanFactory
import com.rakdatak.core.training.TrainingScheduleEngine
import com.rakdatak.core.training.WorkoutTimelineMigration
import com.rakdatak.core.training.WorkoutSessionEngine
import com.rakdatak.core.training.WorkoutSessionStatus
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
    PLAN,
    SETTINGS,
    PROFILE,
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
    val historyRepository = remember { WorkoutHistoryRepository(context.applicationContext) }
    val scheduleRepository = remember { TrainingScheduleRepository(context.applicationContext) }
    val reminderScheduler = remember { TrainingReminderScheduler(context.applicationContext) }
    val wearController = remember { PhoneWearController(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val profile by produceState<RunnerProfile?>(initialValue = null, profileRepository) {
        profileRepository.profile.collectLatest { value = it }
    }
    // Wait for the persisted plan index before restoring/migrating an active session.
    val progress by produceState<TrainingProgress?>(initialValue = null, progressRepository) {
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
        profile == null || progress == null -> Surface(
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
            progress = progress!!,
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
    val plans = remember { BaselinePlanFactory.create() }
    val scheduleEngine = remember { TrainingScheduleEngine() }
    // The saved training progress remains the source of truth, including sessions from 0.4.9.
    val planIndex = progress.currentPlanIndex.coerceIn(0, plans.lastIndex)
    val plan = plans[planIndex]
    val scope = rememberCoroutineScope()

    val restoredWorkout = remember { activeWorkoutRepository.load() }
    val initialEngine = remember {
        WorkoutSessionEngine(plan).also { restoredEngine ->
            restoredWorkout?.let { saved ->
                val elapsed = if (
                    saved.timelineVersion < ActiveWorkoutRepository.CURRENT_TIMELINE_VERSION
                ) {
                    val legacyPlan = BaselinePlanFactory.create(
                        warmUpSeconds = 180,
                        coolDownSeconds = 180,
                    )[planIndex]
                    WorkoutTimelineMigration.migrateElapsed(
                        oldPlan = legacyPlan,
                        newPlan = plan,
                        oldElapsedSeconds = saved.elapsedSeconds,
                    )
                } else {
                    saved.elapsedSeconds
                }
                restoredEngine.restore(elapsedSeconds = elapsed, paused = true)
                // One-time migration, retaining distance and the entire workout history.
                if (saved.timelineVersion < ActiveWorkoutRepository.CURRENT_TIMELINE_VERSION) {
                    activeWorkoutRepository.save(restoredEngine.snapshot(), saved.distanceMeters)
                }
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
    // Do not charge the next run/walk stage while its spoken instruction is playing.
    var awaitingInstruction by remember { mutableStateOf(false) }
    var instructionStartedAtMs by remember { mutableStateOf(0L) }
    val syncContext = LocalContext.current
    val watch = rememberWearLiveMetrics()
    var syncNow by remember { mutableStateOf(System.currentTimeMillis()) }
    // Never let a stale Wear DataItem block a new phone workout.
    val watchActive = watch.isFresh(syncNow) && watch.owner == "WATCH" &&
        !watch.sessionId.isNullOrBlank() && watch.planIndex in plans.indices &&
        watch.status in setOf("RUNNING", "PAUSED") &&
        snapshot.status !in setOf(WorkoutSessionStatus.RUNNING, WorkoutSessionStatus.PAUSED)
    wearController.elapsed = snapshot.totalElapsedSeconds

    LaunchedEffect(Unit) {
        while (true) {
            syncNow = System.currentTimeMillis()
            for (command in PhoneCommandInbox.pending(syncContext)) {
                val valid = WorkoutSync.accepts(command, wearController.sessionId, WorkoutOwner.PHONE,
                    WorkoutOwner.PHONE, syncNow)
                var applied = false
                if (valid && snapshot.status in setOf(WorkoutSessionStatus.RUNNING, WorkoutSessionStatus.PAUSED)) {
                    applied = true
                    when (command.action) {
                        "PAUSE" -> snapshot = engine.pause()
                        "RESUME" -> {
                            // Narration is an explicit hold and can never be released remotely.
                            if (!awaitingInstruction) {
                                snapshot = engine.resume()
                                screen = AppScreen.WORKOUT
                            } else {
                                wearController.pause()
                            }
                        }
                        "STOP" -> {
                            snapshot = engine.stop()
                            historyRepository.recordWorkout(snapshot.totalElapsedSeconds, distanceMeters,
                                snapshot.completionRatio, sessionId = wearController.sessionId)
                            progressRepository.recordWorkout(snapshot.totalElapsedSeconds, snapshot.completionRatio,
                                distanceMeters, sessionId = wearController.sessionId)
                            sessionRecorded = true
                            awaitingInstruction = false
                            screen = AppScreen.SUMMARY
                        }
                    }
                    activeWorkoutRepository.save(snapshot, distanceMeters)
                }
                PhoneCommandInbox.acknowledge(syncContext, command, applied = applied)
            }
            delay(500)
        }
    }

    // A watch-owned workout has exactly one clock. Only its final snapshot is recorded here.
    LaunchedEffect(watch) {
        if (watch.owner == "WATCH" && !watch.sessionId.isNullOrBlank() &&
            watch.planIndex in plans.indices && watch.status in setOf("STOPPED", "COMPLETED")) {
            // A persisted final DataItem remains valid after disconnect; history uses session ID dedup.
            historyRepository.recordWorkout(watch.totalElapsedSeconds, watch.distanceMeters ?: 0.0,
                watch.completionRatio, sessionId = watch.sessionId)
            progressRepository.recordWorkout(watch.totalElapsedSeconds, watch.completionRatio,
                watch.distanceMeters, sessionId = watch.sessionId)
        }
    }

    PhoneWorkoutCoachEffect(
        snapshot = snapshot,
        soundCuesEnabled = settings.soundCuesEnabled,
        workoutVoice = settings.workoutVoice,
        vibrationEnabled = settings.vibrationEnabled,
        onPhaseInstructionCompleted = {
            if (awaitingInstruction) {
                awaitingInstruction = false
                if (screen == AppScreen.WORKOUT &&
                    snapshot.status == WorkoutSessionStatus.RUNNING
                ) {
                    wearController.resume()
                }
            }
        },
    )

    val hasActiveWorkout = snapshot.status == WorkoutSessionStatus.RUNNING ||
        snapshot.status == WorkoutSessionStatus.PAUSED
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
                if (awaitingInstruction) {
                    // A failed/unavailable voice must never freeze the workout indefinitely.
                    if (now - instructionStartedAtMs >= 15_000L) {
                        awaitingInstruction = false
                        wearController.resume()
                    }
                    lastTickRealtime = now
                    continue
                }
                val elapsedWholeSeconds = ((now - lastTickRealtime) / 1_000L).toInt()
                if (elapsedWholeSeconds <= 0) continue

                val previousPhaseIndex = snapshot.phaseIndex
                // If Android was suspended, stop at the phase boundary instead of
                // skipping the spoken transition or consuming next-stage seconds.
                val advance = minOf(elapsedWholeSeconds, snapshot.phaseRemainingSeconds.coerceAtLeast(1))
                lastTickRealtime += elapsedWholeSeconds * 1_000L
                snapshot = engine.tick(advance)
                wearController.elapsed = snapshot.totalElapsedSeconds
                activeWorkoutRepository.save(snapshot, distanceMeters)

                if (snapshot.status == WorkoutSessionStatus.RUNNING &&
                    snapshot.phaseIndex != previousPhaseIndex &&
                    settings.soundCuesEnabled
                ) {
                    awaitingInstruction = true
                    instructionStartedAtMs = now
                    wearController.pause()
                }

                if (snapshot.status == WorkoutSessionStatus.COMPLETED) {
                    if (!sessionRecorded) {
                        sessionRecorded = true
                        progressRepository.recordWorkout(
                            sessionId = wearController.sessionId,
                            elapsedSeconds = snapshot.totalElapsedSeconds,
                            completionRatio = snapshot.completionRatio,
                            distanceMeters = distanceMeters,
                        )
                        historyRepository.recordWorkout(
                            sessionId = wearController.sessionId,
                            elapsedSeconds = snapshot.totalElapsedSeconds,
                            completionRatio = snapshot.completionRatio,
                            distanceMeters = distanceMeters,
                        )
                        wearController.stop()
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
            syncedWatch = watch.takeIf { watchActive },
            watchFresh = watch.isFresh(syncNow),
            onWatchControl = { action ->
                if (watchActive && watch.isFresh()) wearController.controlWatch(action,
                    watch.sessionId!!, watch.planIndex, watch.totalElapsedSeconds)
            },
            onStartWorkout = {
                if (!profile.safetyReviewNeeded && !watchActive) {
                    wearController.newSession(planIndex)
                    engine = WorkoutSessionEngine(plan)
                    snapshot = engine.snapshot()
                    distanceMeters = 0.0
                    sessionRecorded = false
                    awaitingInstruction = false
                    screen = AppScreen.COUNTDOWN
                }
            },
            onResumeWorkout = {
                if (watchActive) return@RakdatakHomeScreen
                snapshot = if (snapshot.status == WorkoutSessionStatus.PAUSED) {
                    engine.resume()
                } else {
                    snapshot
                }
                activeWorkoutRepository.save(snapshot, distanceMeters)
                wearController.start(
                    planIndex = planIndex,
                    elapsedSeconds = snapshot.totalElapsedSeconds,
                    gpsEnabled = profile.trainingEnvironment != TrainingEnvironment.TREADMILL,
                    paused = awaitingInstruction,
                )
                if (!awaitingInstruction) wearController.resume()
                screen = AppScreen.WORKOUT
            },
            onOpenHistory = { screen = AppScreen.HISTORY },
            onOpenPlan = { screen = AppScreen.PLAN },
            onOpenSettings = { screen = AppScreen.SETTINGS },
            onOpenSchedule = { screen = AppScreen.SCHEDULE },
        )

        AppScreen.COUNTDOWN -> PreWorkoutCountdownScreen(
            firstPhase = plan.phases.first(),
            soundCuesEnabled = settings.soundCuesEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            onFinished = {
                if (watchActive) { screen = AppScreen.HOME; return@PreWorkoutCountdownScreen }
                snapshot = engine.start()
                awaitingInstruction = settings.soundCuesEnabled
                instructionStartedAtMs = SystemClock.elapsedRealtime()
                activeWorkoutRepository.save(snapshot, distanceMeters)
                wearController.start(
                    planIndex = planIndex,
                    elapsedSeconds = 0,
                    gpsEnabled = profile.trainingEnvironment != TrainingEnvironment.TREADMILL,
                    paused = awaitingInstruction,
                )
                if (awaitingInstruction) wearController.pause()
                screen = AppScreen.WORKOUT
            },
        )

        AppScreen.WORKOUT -> WorkoutScreen(
            snapshot = snapshot,
            awaitingInstruction = awaitingInstruction,
            plannedDurationSeconds = plan.totalDurationSeconds,
            initialDistanceMeters = distanceMeters,
            gpsTrackingEnabled = profile.trainingEnvironment != TrainingEnvironment.TREADMILL,
            keepScreenOn = settings.keepScreenOnDuringWorkout,
            onDistanceChanged = { updatedDistance ->
                if (updatedDistance > distanceMeters) {
                    distanceMeters = updatedDistance
                    activeWorkoutRepository.save(snapshot, updatedDistance)
                }
            },
            onPauseResume = {
                snapshot = if (snapshot.status == WorkoutSessionStatus.PAUSED) {
                    if (!awaitingInstruction) wearController.resume()
                    engine.resume()
                } else {
                    wearController.pause()
                    engine.pause()
                }
                activeWorkoutRepository.save(snapshot, distanceMeters)
            },
            onBackToHome = {
                if (snapshot.status == WorkoutSessionStatus.RUNNING) {
                    snapshot = engine.pause()
                    wearController.pause()
                }
                activeWorkoutRepository.save(snapshot, distanceMeters)
                screen = AppScreen.HOME
            },
            onFinish = {
                val stopped = engine.stop()
                snapshot = stopped
                wearController.stop()
                if (!sessionRecorded) {
                    sessionRecorded = true
                    historyRepository.recordWorkout(
                            sessionId = wearController.sessionId,
                        elapsedSeconds = stopped.totalElapsedSeconds,
                        completionRatio = stopped.completionRatio,
                        distanceMeters = distanceMeters,
                    )
                    scope.launch {
                        progressRepository.recordWorkout(
                            sessionId = wearController.sessionId,
                            elapsedSeconds = stopped.totalElapsedSeconds,
                            completionRatio = stopped.completionRatio,
                            distanceMeters = distanceMeters,
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
            manualDistanceEntryEnabled = profile.trainingEnvironment == TrainingEnvironment.TREADMILL,
            onManualDistanceSaved = { manualDistanceMeters ->
                val historyUpdated = historyRepository.fillLatestMissingDistance(manualDistanceMeters)
                if (historyUpdated) {
                    distanceMeters = manualDistanceMeters
                    scope.launch {
                        progressRepository.addDistanceToLatestWorkout(manualDistanceMeters)
                    }
                }
            },
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

        AppScreen.HISTORY -> WorkoutHistoryScreen(
            entries = historyEntries,
            onBack = { screen = AppScreen.HOME },
        )

        AppScreen.PLAN -> TrainingPlanOverviewScreen(
            plans = plans,
            currentIndex = planIndex,
            onBack = { screen = AppScreen.HOME },
        )

        AppScreen.SETTINGS -> SettingsScreen(
            settings = settings,
            profileSummary = profileSummary(profile),
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

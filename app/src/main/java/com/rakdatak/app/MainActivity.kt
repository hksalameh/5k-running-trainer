package com.rakdatak.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakdatak.app.feedback.PostWorkoutFeedbackScreen
import com.rakdatak.app.profile.OnboardingScreen
import com.rakdatak.app.profile.RunnerProfile
import com.rakdatak.app.profile.RunnerProfileRepository
import com.rakdatak.app.progress.TrainingProgress
import com.rakdatak.app.progress.TrainingProgressRepository
import com.rakdatak.app.settings.AppSettings
import com.rakdatak.app.settings.AppSettingsRepository
import com.rakdatak.app.settings.SettingsScreen
import com.rakdatak.app.workout.ActiveWorkoutRepository
import com.rakdatak.app.workout.rememberPhoneWorkoutMetrics
import com.rakdatak.core.training.BaselinePlanFactory
import com.rakdatak.core.training.WorkoutSessionEngine
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.WorkoutPhaseType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val RakdatakOrange = Color(0xFFFF6D00)
private val RakdatakBlack = Color(0xFF141414)
private val RakdatakGray = Color(0xFF747474)
private val RakdatakSurface = Color(0xFFF5F5F5)

private enum class AppScreen {
    HOME,
    WORKOUT,
    SUMMARY,
    SETTINGS,
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
) {
    val plans = remember { BaselinePlanFactory.create() }
    val planIndex = progress.currentPlanIndex.coerceIn(0, plans.lastIndex)
    val plan = plans[planIndex]
    val scope = rememberCoroutineScope()

    val restoredWorkout = remember { activeWorkoutRepository.load() }
    val initialEngine = remember {
        WorkoutSessionEngine(plan).also { restoredEngine ->
            restoredWorkout?.let { saved ->
                // A recovered process starts paused rather than guessing how long the user kept
                // exercising while Android had the process stopped.
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
                distanceMeters = updatedDistance
                activeWorkoutRepository.save(snapshot, updatedDistance)
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
                // Manual stop returns directly home. Feedback never blocks leaving a workout.
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
            onBack = { screen = AppScreen.HOME },
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
    }
}

@Composable
private fun RakdatakHomeScreen(
    safetyReviewNeeded: Boolean,
    progress: TrainingProgress,
    currentPlanTitle: String,
    planProgress: Float,
    activeWorkout: WorkoutSessionSnapshot?,
    activeDistanceMeters: Double,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ركضتك",
                        style = MaterialTheme.typography.headlineMedium,
                        color = RakdatakBlack,
                    )
                    Text(
                        text = "خطوة ثابتة اليوم، فرق كبير بكرة.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = RakdatakGray,
                    )
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "الإعدادات",
                        tint = RakdatakBlack,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            GoalCard(planProgress = planProgress)

            if (safetyReviewNeeded) {
                SafetyReviewCard()
            } else if (activeWorkout != null) {
                ActiveWorkoutCard(
                    snapshot = activeWorkout,
                    distanceMeters = activeDistanceMeters,
                    onResumeWorkout = onResumeWorkout,
                )
            } else {
                NextWorkoutCard(
                    title = currentPlanTitle,
                    onStartWorkout = onStartWorkout,
                )
            }

            Text(
                text = "تقدمك",
                style = MaterialTheme.typography.titleLarge,
                color = RakdatakBlack,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = progress.completedWorkouts.toString(),
                    label = "مكتملة",
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "%.1f".format(progress.totalDistanceMeters / 1_000.0),
                    label = "كم",
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = (progress.totalSeconds / 60L).toString(),
                    label = "دقيقة",
                )
            }

            if (progress.savedWorkouts > progress.completedWorkouts) {
                Text(
                    text = "محفوظ أيضًا ${progress.savedWorkouts - progress.completedWorkouts} تمرين غير مكتمل.",
                    color = RakdatakGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GoalCard(planProgress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RakdatakBlack),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "هدفك",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "5 كم + 30 دقيقة",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "نبدأ من الصفر ونبني قدرتك تدريجيًا",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { planProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(64.dp),
                    color = RakdatakOrange,
                    trackColor = Color(0xFF333333),
                )
                Text(
                    text = "${(planProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SafetyReviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RakdatakSurface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "قبل بدء خطة الركض",
                color = RakdatakBlack,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "بناءً على إجابتك الأولى، لن نرفع شدة التدريب الآن. راجع مختصًا قبل بدء جلسات الركض، ويمكن تحديث الحالة من الإعدادات لاحقًا.",
                color = RakdatakGray,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ActiveWorkoutCard(
    snapshot: WorkoutSessionSnapshot,
    distanceMeters: Double,
    onResumeWorkout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E8)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "لديك تمرين محفوظ",
                color = RakdatakBlack,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "${phaseLabel(snapshot.currentPhase.type)} • ${formatTime(snapshot.totalElapsedSeconds)} • ${formatDistance(distanceMeters)}",
                color = RakdatakGray,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "تم إيقافه مؤقتًا حتى تقرر المتابعة.",
                color = RakdatakGray,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onResumeWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RakdatakOrange),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text("  متابعة التمرين", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun NextWorkoutCard(
    title: String,
    onStartWorkout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RakdatakSurface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "التمرين القادم",
                        color = RakdatakGray,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        color = RakdatakBlack,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = RakdatakOrange,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                text = "إحماء خفيف ثم مشي وركض حسب المرحلة المناسبة لك.",
                color = RakdatakGray,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RakdatakOrange,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "  النبض يظهر من الساعة عند توفرها",
                    color = RakdatakGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onStartWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RakdatakOrange),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "  ابدأ التمرين",
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun WorkoutScreen(
    snapshot: WorkoutSessionSnapshot,
    initialDistanceMeters: Double,
    soundCuesEnabled: Boolean,
    vibrationEnabled: Boolean,
    keepScreenOn: Boolean,
    onDistanceChanged: (Double) -> Unit,
    onPauseResume: () -> Unit,
    onBackToHome: () -> Unit,
    onFinish: () -> Unit,
) {
    PhoneWorkoutCoachEffect(
        snapshot = snapshot,
        soundCuesEnabled = soundCuesEnabled,
        vibrationEnabled = vibrationEnabled,
    )

    val metrics = rememberPhoneWorkoutMetrics(
        active = snapshot.status == WorkoutSessionStatus.RUNNING,
        initialDistanceMeters = initialDistanceMeters,
        onDistanceChanged = onDistanceChanged,
    )

    val view = LocalView.current
    DisposableEffect(view, keepScreenOn) {
        val previousValue = view.keepScreenOn
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = previousValue }
    }

    BackHandler(onBack = onBackToHome)

    var showFinishConfirmation by remember { mutableStateOf(false) }

    if (showFinishConfirmation) {
        AlertDialog(
            onDismissRequest = { showFinishConfirmation = false },
            title = { Text("إنهاء التمرين؟") },
            text = { Text("سيتم حفظ الوقت والمسافة التي أنجزتها ثم العودة للرئيسية مباشرة.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishConfirmation = false
                        onFinish()
                    },
                ) {
                    Text("إنهاء", color = RakdatakOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirmation = false }) {
                    Text("متابعة التمرين", color = RakdatakBlack)
                }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = RakdatakBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = phaseLabel(snapshot.currentPhase.type),
                color = RakdatakOrange,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = formatTime(snapshot.phaseRemainingSeconds),
                color = Color.White,
                fontSize = 64.sp,
            )
            Text(
                text = "متبقي لهذه المرحلة",
                color = Color(0xFFBDBDBD),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                WorkoutMetric(value = formatTime(snapshot.totalElapsedSeconds), label = "الوقت")
                WorkoutMetric(value = "—", label = "النبض")
                WorkoutMetric(value = formatDistance(metrics.distanceMeters), label = "المسافة")
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when {
                    !metrics.locationPermissionGranted -> "فعّل إذن الموقع لحساب المسافة أثناء الركض"
                    !metrics.gpsAvailable -> "GPS غير متاح حاليًا • النبض سيظهر عند ربط الساعة"
                    else -> "المسافة عبر GPS • النبض سيظهر عند ربط الساعة"
                },
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onPauseResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RakdatakOrange),
            ) {
                Text(
                    text = if (snapshot.status == WorkoutSessionStatus.PAUSED) "متابعة" else "إيقاف مؤقت",
                    fontSize = 18.sp,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showFinishConfirmation = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(text = "إنهاء التمرين", color = Color.White)
            }
        }
    }
}

@Composable
private fun WorkoutMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = label,
            color = Color(0xFFBDBDBD),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier
            .background(RakdatakSurface, RoundedCornerShape(18.dp))
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = RakdatakBlack,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = label,
            color = RakdatakGray,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun phaseLabel(type: WorkoutPhaseType): String = when (type) {
    WorkoutPhaseType.WARM_UP -> "إحماء خفيف"
    WorkoutPhaseType.WALK -> "مشي"
    WorkoutPhaseType.RUN -> "ركض"
    WorkoutPhaseType.COOL_DOWN -> "تهدئة"
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatDistance(distanceMeters: Double): String =
    "%.2f كم".format(distanceMeters.coerceAtLeast(0.0) / 1_000.0)

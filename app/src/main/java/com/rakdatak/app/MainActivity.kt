package com.rakdatak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakdatak.app.feedback.PostWorkoutFeedbackScreen
import com.rakdatak.app.profile.OnboardingScreen
import com.rakdatak.app.profile.RunnerProfile
import com.rakdatak.app.profile.RunnerProfileRepository
import com.rakdatak.app.progress.TrainingProgress
import com.rakdatak.app.progress.TrainingProgressRepository
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
    val scope = rememberCoroutineScope()

    val profile by produceState<RunnerProfile?>(initialValue = null, profileRepository) {
        profileRepository.profile.collectLatest { value = it }
    }
    val progress by produceState(initialValue = TrainingProgress(), progressRepository) {
        progressRepository.progress.collectLatest { value = it }
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
        )
    }
}

@Composable
private fun RakdatakApp(
    profile: RunnerProfile,
    progress: TrainingProgress,
    progressRepository: TrainingProgressRepository,
) {
    val plan = remember { BaselinePlanFactory.create().first() }
    val scope = rememberCoroutineScope()
    var engine by remember { mutableStateOf(WorkoutSessionEngine(plan)) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var sessionRecorded by remember { mutableStateOf(false) }

    LaunchedEffect(screen, snapshot.status) {
        if (screen == AppScreen.WORKOUT && snapshot.status == WorkoutSessionStatus.RUNNING) {
            while (snapshot.status == WorkoutSessionStatus.RUNNING) {
                delay(1_000)
                snapshot = engine.tick()
                if (snapshot.status == WorkoutSessionStatus.COMPLETED) {
                    if (!sessionRecorded) {
                        sessionRecorded = true
                        progressRepository.recordWorkout(
                            elapsedSeconds = snapshot.totalElapsedSeconds,
                            completionRatio = snapshot.completionRatio,
                        )
                    }
                    screen = AppScreen.SUMMARY
                }
            }
        }
    }

    when (screen) {
        AppScreen.HOME -> RakdatakHomeScreen(
            safetyReviewNeeded = profile.safetyReviewNeeded,
            progress = progress,
            onStartWorkout = {
                if (!profile.safetyReviewNeeded) {
                    engine = WorkoutSessionEngine(plan)
                    snapshot = engine.start()
                    sessionRecorded = false
                    screen = AppScreen.WORKOUT
                }
            },
        )

        AppScreen.WORKOUT -> WorkoutScreen(
            snapshot = snapshot,
            onPauseResume = {
                snapshot = if (snapshot.status == WorkoutSessionStatus.PAUSED) {
                    engine.resume()
                } else {
                    engine.pause()
                }
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
                        )
                    }
                }
                screen = AppScreen.SUMMARY
            },
        )

        AppScreen.SUMMARY -> PostWorkoutFeedbackScreen(
            planId = plan.id,
            snapshot = snapshot,
            onDone = { _, _, _ -> screen = AppScreen.HOME },
        )
    }
}

@Composable
private fun RakdatakHomeScreen(
    safetyReviewNeeded: Boolean,
    progress: TrainingProgress,
    onStartWorkout: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

            GoalCard()

            if (safetyReviewNeeded) {
                SafetyReviewCard()
            } else {
                NextWorkoutCard(onStartWorkout = onStartWorkout)
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
        }
    }
}

@Composable
private fun GoalCard() {
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
                    progress = { 0.04f },
                    modifier = Modifier.size(64.dp),
                    color = RakdatakOrange,
                    trackColor = Color(0xFF333333),
                )
                Text(
                    text = "ابدأ",
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
private fun NextWorkoutCard(onStartWorkout: () -> Unit) {
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
                        text = "بداية هادئة",
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
                text = "إحماء خفيف ثم مشي وركض بفترات قصيرة ومريحة.",
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
                    text = "  نبض مراقب",
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
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
) {
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
                WorkoutMetric(value = "--", label = "النبض")
                WorkoutMetric(value = "--", label = "المسافة")
            }

            Spacer(modifier = Modifier.height(32.dp))

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
                onClick = onFinish,
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
private fun WorkoutSummaryScreen(
    snapshot: WorkoutSessionSnapshot,
    onDone: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (snapshot.status == WorkoutSessionStatus.COMPLETED) "أحسنت!" else "تم حفظ تمرينك",
                color = RakdatakBlack,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "الوقت: ${formatTime(snapshot.totalElapsedSeconds)}",
                color = RakdatakGray,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "نسبة الإكمال: ${(snapshot.completionRatio * 100).toInt()}%",
                color = RakdatakGray,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RakdatakOrange),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("تم")
            }
        }
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

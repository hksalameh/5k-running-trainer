package com.rakdatak.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakdatak.app.PhoneWorkoutCoachEffect
import com.rakdatak.app.wear.rememberWearLiveMetrics
import com.rakdatak.app.workout.rememberPhoneWorkoutMetrics
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.WorkoutPhaseType

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF141414)
private val LightText = Color(0xFFBDBDBD)
private val MutedText = Color(0xFF9E9E9E)

@Composable
fun WorkoutScreen(
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

    val watch = rememberWearLiveMetrics()
    val watchFresh = watch.isFresh()
    val watchDistance = watch.distanceMeters?.takeIf { watchFresh } ?: 0.0

    val metrics = rememberPhoneWorkoutMetrics(
        active = snapshot.status == WorkoutSessionStatus.RUNNING,
        initialDistanceMeters = initialDistanceMeters,
        onDistanceChanged = { phoneDistance ->
            onDistanceChanged(maxOf(phoneDistance, watchDistance))
        },
    )

    val displayDistance = maxOf(metrics.distanceMeters, watchDistance, initialDistanceMeters)
    val displayHeartRate = watch.heartRateBpm?.takeIf { watchFresh }

    LaunchedEffect(displayDistance) {
        if (displayDistance > initialDistanceMeters) {
            onDistanceChanged(displayDistance)
        }
    }

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
                    Text("إنهاء", color = Orange)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirmation = false }) {
                    Text("متابعة التمرين", color = Black)
                }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Black,
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
                color = Orange,
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
                color = LightText,
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                WorkoutMetric(value = formatTime(snapshot.totalElapsedSeconds), label = "الوقت")
                WorkoutMetric(
                    value = displayHeartRate?.toInt()?.toString() ?: "—",
                    label = "النبض",
                )
                WorkoutMetric(value = formatDistance(displayDistance), label = "المسافة")
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when {
                    watchFresh && displayHeartRate != null -> "النبض من الساعة • المسافة من أفضل مصدر متاح"
                    !metrics.locationPermissionGranted -> "فعّل إذن الموقع لحساب المسافة • النبض يظهر عند اتصال الساعة"
                    !metrics.gpsAvailable -> "GPS غير متاح حاليًا • النبض يظهر عند اتصال الساعة"
                    watchFresh -> "الساعة متصلة • بانتظار قراءة النبض"
                    else -> "المسافة عبر GPS • الساعة غير متصلة حاليًا"
                },
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onPauseResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
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
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(label, color = LightText, style = MaterialTheme.typography.bodySmall)
    }
}

private fun phaseLabel(type: WorkoutPhaseType): String = when (type) {
    WorkoutPhaseType.WARM_UP -> "إحماء خفيف"
    WorkoutPhaseType.WALK -> "مشي"
    WorkoutPhaseType.RUN -> "ركض"
    WorkoutPhaseType.COOL_DOWN -> "تهدئة"
}

private fun formatTime(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatDistance(distanceMeters: Double): String =
    "%.2f كم".format(distanceMeters.coerceAtLeast(0.0) / 1_000.0)

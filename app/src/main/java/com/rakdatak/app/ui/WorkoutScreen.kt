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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakdatak.app.wear.rememberWearLiveMetrics
import com.rakdatak.app.workout.rememberPhoneWorkoutMetrics
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPhaseType

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF141414)
private val LightText = Color(0xFFBDBDBD)
private val MutedText = Color(0xFF9E9E9E)
private val DarkPanel = Color(0xFF202020)

@Composable
fun WorkoutScreen(
    snapshot: WorkoutSessionSnapshot,
    plannedDurationSeconds: Int,
    initialDistanceMeters: Double,
    gpsTrackingEnabled: Boolean,
    treadmillMode: Boolean,
    treadmillSpeedKmh: Double,
    onTreadmillSpeedChange: (Double) -> Unit,
    keepScreenOn: Boolean,
    onDistanceChanged: (Double) -> Unit,
    onPauseResume: () -> Unit,
    onBackToHome: () -> Unit,
    onFinish: () -> Unit,
) {
    val watch = rememberWearLiveMetrics()
    val watchFresh = watch.isFresh()
    val watchDistance = watch.distanceMeters?.takeIf { watchFresh } ?: 0.0

    val metrics = rememberPhoneWorkoutMetrics(
        // Keep the GPS listener alive while a spoken phase cue is playing.
        // Otherwise every RUN/WALK transition discards the previous fix and shortens distance.
        active = gpsTrackingEnabled && (
            snapshot.status == WorkoutSessionStatus.RUNNING ||
                snapshot.status == WorkoutSessionStatus.ANNOUNCING
        ),
        initialDistanceMeters = initialDistanceMeters,
        onDistanceChanged = { phoneDistance ->
            onDistanceChanged(maxOf(phoneDistance, watchDistance))
        },
    )

    val displayDistance = maxOf(metrics.distanceMeters, watchDistance, initialDistanceMeters)
    val displayHeartRate = watch.heartRateBpm?.takeIf { watchFresh }
    val totalRemainingSeconds =
        (plannedDurationSeconds - snapshot.totalElapsedSeconds).coerceAtLeast(0)

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
            text = { Text("سيتم حفظ ما أنجزته ثم عرض ملخص التمرين والتقييم الاختياري.") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (snapshot.status == WorkoutSessionStatus.ANNOUNCING) {
                    "استمع للتعليمات أولاً"
                } else {
                    phaseLabel(snapshot.currentPhase.type)
                },
                color = Orange,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatTime(snapshot.phaseRemainingSeconds),
                color = Color.White,
                fontSize = 64.sp,
            )
            Text(
                text = if (snapshot.status == WorkoutSessionStatus.ANNOUNCING) {
                    "لم يبدأ وقت المرحلة بعد"
                } else {
                    "متبقي لهذه المرحلة"
                },
                color = LightText,
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { snapshot.completionRatio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Orange,
                trackColor = DarkPanel,
            )
            Text(
                text = if (snapshot.status == WorkoutSessionStatus.ANNOUNCING) {
                    "يبدأ العداد بعد انتهاء التعليمات الصوتية مباشرة"
                } else {
                    "أنت الآن في مرحلة ${phaseLabel(snapshot.currentPhase.type)}"
                },
                color = LightText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(18.dp))

            NextPhaseCard(nextPhase = snapshot.nextPhase)

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                WorkoutMetric(value = formatTime(snapshot.totalElapsedSeconds), label = "الوقت الكلي")
                WorkoutMetric(
                    value = displayHeartRate?.toInt()?.toString() ?: "—",
                    label = "النبض",
                )
                WorkoutMetric(
                    value = formatDistance(displayDistance),
                    label = if (treadmillMode) "مسافة تقديرية" else "المسافة",
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkPanel,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (treadmillMode) {
                            "سرعة ${phaseLabel(snapshot.currentPhase.type)} على التردمل"
                        } else {
                            "السرعة الحالية بحسب GPS"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LightText,
                    )
                    Text(
                        text = if (treadmillMode) {
                            "%.1f كم/س".format(treadmillSpeedKmh)
                        } else {
                            metrics.speedKmh?.let { "%.1f كم/س".format(it) } ?: "— كم/س"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (treadmillMode) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onTreadmillSpeedChange(treadmillSpeedKmh - 0.5) },
                            ) {
                                Text("− 0.5", color = Color.White)
                            }
                            Text("عدّلها لتطابق شاشة التردمل", color = LightText)
                            OutlinedButton(
                                onClick = { onTreadmillSpeedChange(treadmillSpeedKmh + 0.5) },
                            ) {
                                Text("+ 0.5", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "مدة الجلسة ${formatTime(plannedDurationSeconds)} • المتبقي الكلي ${formatTime(totalRemainingSeconds)}",
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    treadmillMode && watchFresh && displayHeartRate != null ->
                        "التردمل • المسافة من السرعة المختارة • النبض من الساعة"
                    treadmillMode ->
                        "التردمل • المسافة محسوبة تقديريًا من السرعة والوقت"
                    watchFresh && displayHeartRate != null ->
                        "النبض من الساعة • المسافة من أفضل مصدر متاح"
                    !metrics.locationPermissionGranted ->
                        "فعّل إذن الموقع لحساب المسافة • النبض يظهر عند اتصال الساعة"
                    !metrics.gpsAvailable ->
                        "GPS غير متاح حاليًا • النبض يظهر عند اتصال الساعة"
                    watchFresh -> "الساعة متصلة • بانتظار قراءة النبض"
                    else -> "المسافة عبر GPS • الساعة غير متصلة حاليًا"
                },
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(18.dp))

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
private fun NextPhaseCard(nextPhase: WorkoutPhase?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkPanel,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "التالي",
                color = LightText,
                style = MaterialTheme.typography.labelLarge,
            )
            if (nextPhase == null) {
                Text(
                    text = "نهاية التمرين",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = "${phaseLabel(nextPhase.type)}  •  ${formatTime(nextPhase.durationSeconds)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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

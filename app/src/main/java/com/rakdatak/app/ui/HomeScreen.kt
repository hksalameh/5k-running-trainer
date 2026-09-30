package com.rakdatak.app.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakdatak.app.progress.TrainingProgress
import com.rakdatak.app.wear.rememberWearLiveMetrics
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.model.WorkoutPhaseType
import com.rakdatak.core.training.model.WorkoutPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)
private val SurfaceGray = Color(0xFFF5F5F5)

@Composable
fun RakdatakHomeScreen(
    safetyReviewNeeded: Boolean,
    progress: TrainingProgress,
    currentPlan: WorkoutPlan,
    planProgress: Float,
    nextWorkoutAt: LocalDateTime?,
    scheduleConfigured: Boolean,
    activeWorkout: WorkoutSessionSnapshot?,
    activeDistanceMeters: Double,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSchedule: () -> Unit,
) {
    val watch = rememberWearLiveMetrics()
    val watchConnected = watch.isFresh()

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
                        color = Black,
                    )
                    Text(
                        text = "خطوة ثابتة اليوم، فرق كبير بكرة.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Gray,
                    )
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "الإعدادات",
                        tint = Black,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            GoalCard(planProgress = planProgress)

            WatchStatusCard(
                connected = watchConnected,
                heartRateBpm = watch.heartRateBpm,
            )

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
                    plan = currentPlan,
                    nextWorkoutAt = nextWorkoutAt,
                    scheduleConfigured = scheduleConfigured,
                    watchConnected = watchConnected,
                    onOpenSchedule = onOpenSchedule,
                    onStartWorkout = onStartWorkout,
                )
            }

            Text(
                text = "تقدمك",
                style = MaterialTheme.typography.titleLarge,
                color = Black,
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

            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("سجل التمارين", color = Black)
            }

            if (progress.savedWorkouts > progress.completedWorkouts) {
                Text(
                    text = "محفوظ أيضًا ${progress.savedWorkouts - progress.completedWorkouts} تمرين غير مكتمل.",
                    color = Gray,
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
        colors = CardDefaults.cardColors(containerColor = Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("هدفك", color = Color(0xFFBDBDBD), style = MaterialTheme.typography.labelLarge)
                Text("5 كم + 30 دقيقة", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    "نبدأ من الصفر ونبني قدرتك تدريجيًا",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { planProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(64.dp),
                    color = Orange,
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
private fun WatchStatusCard(
    connected: Boolean,
    heartRateBpm: Double?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) Color(0xFFFFF3E8) else SurfaceGray,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (connected) "الساعة متصلة" else "الساعة غير متصلة",
                    color = Black,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (connected) {
                        "بيانات التمرين والنبض تصل من Wear OS"
                    } else {
                        "يمكن استخدام الهاتف وحده أو تشغيل نسخة الساعة"
                    },
                    color = Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (connected && heartRateBpm != null) {
                Text(
                    text = "${heartRateBpm.toInt()} نبضة",
                    color = Orange,
                    style = MaterialTheme.typography.titleMedium,
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
        colors = CardDefaults.cardColors(containerColor = SurfaceGray),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("قبل بدء خطة الركض", color = Black, style = MaterialTheme.typography.titleLarge)
            Text(
                "بناءً على إجابتك الأولى، لن نرفع شدة التدريب الآن. راجع مختصًا قبل بدء جلسات الركض، ويمكن تحديث الحالة من الإعدادات لاحقًا.",
                color = Gray,
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
            Text("لديك تمرين محفوظ", color = Black, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${phaseLabel(snapshot.currentPhase.type)} • ${formatTime(snapshot.totalElapsedSeconds)} • ${formatDistance(distanceMeters)}",
                color = Gray,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "تم إيقافه مؤقتًا حتى تقرر المتابعة.",
                color = Gray,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onResumeWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("  متابعة التمرين", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun NextWorkoutCard(
    plan: WorkoutPlan,
    nextWorkoutAt: LocalDateTime?,
    scheduleConfigured: Boolean,
    watchConnected: Boolean,
    onOpenSchedule: () -> Unit,
    onStartWorkout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGray),
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
                    Text("التمرين القادم", color = Gray, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(plan.titleAr, color = Black, style = MaterialTheme.typography.titleLarge)
                }

                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = Orange,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                text = "الأسبوع ${plan.week} • الجلسة ${plan.session} • المدة ${formatTime(plan.totalDurationSeconds)}",
                color = Black,
                style = MaterialTheme.typography.titleSmall,
            )

            Text(
                text = workoutBreakdown(plan),
                color = Gray,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (scheduleConfigured && nextWorkoutAt != null) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "موعدك القادم: ${formatScheduledWorkout(nextWorkoutAt)}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        color = Black,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "موعد التمرين غير محدد بعد",
                        color = Gray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenSchedule) {
                        Text("حدد الموعد", color = Orange)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Orange,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (watchConnected) "  الساعة جاهزة لقراءة النبض" else "  النبض يظهر عند اتصال الساعة",
                    color = Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onStartWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("  ابدأ التمرين", fontSize = 17.sp)
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
            .background(SurfaceGray, RoundedCornerShape(18.dp))
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Black, style = MaterialTheme.typography.titleLarge)
        Text(label, color = Gray, style = MaterialTheme.typography.bodySmall)
    }
}

private fun workoutBreakdown(plan: WorkoutPlan): String {
    val warmUpSeconds = plan.phases
        .filter { it.type == WorkoutPhaseType.WARM_UP }
        .sumOf { it.durationSeconds }
    val runSeconds = plan.phases
        .filter { it.type == WorkoutPhaseType.RUN }
        .sumOf { it.durationSeconds }
    val walkSeconds = plan.phases
        .filter { it.type == WorkoutPhaseType.WALK }
        .sumOf { it.durationSeconds }
    val coolDownSeconds = plan.phases
        .filter { it.type == WorkoutPhaseType.COOL_DOWN }
        .sumOf { it.durationSeconds }

    return buildList {
        if (warmUpSeconds > 0) add("إحماء ${formatCompactDuration(warmUpSeconds)}")
        if (runSeconds > 0 && walkSeconds > 0) {
            add("ركض ${formatCompactDuration(runSeconds)} + مشي ${formatCompactDuration(walkSeconds)} بالتناوب")
        } else if (runSeconds > 0) {
            add("ركض ${formatCompactDuration(runSeconds)}")
        } else if (walkSeconds > 0) {
            add("مشي ${formatCompactDuration(walkSeconds)}")
        }
        if (coolDownSeconds > 0) add("تهدئة ${formatCompactDuration(coolDownSeconds)}")
    }.joinToString(" • ")
}

private fun formatScheduledWorkout(value: LocalDateTime): String {
    val today = LocalDate.now()
    val date = value.toLocalDate()
    val dayLabel = when (date) {
        today -> "اليوم"
        today.plusDays(1) -> "غدًا"
        else -> "${arabicDayName(value.dayOfWeek)} ${date.dayOfMonth}/${date.monthValue}"
    }
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("ar"))
    return "$dayLabel • ${value.format(timeFormatter)}"
}

private fun arabicDayName(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SATURDAY -> "السبت"
    DayOfWeek.SUNDAY -> "الأحد"
    DayOfWeek.MONDAY -> "الاثنين"
    DayOfWeek.TUESDAY -> "الثلاثاء"
    DayOfWeek.WEDNESDAY -> "الأربعاء"
    DayOfWeek.THURSDAY -> "الخميس"
    DayOfWeek.FRIDAY -> "الجمعة"
}

private fun phaseLabel(type: WorkoutPhaseType): String = when (type) {
    WorkoutPhaseType.WARM_UP -> "إحماء خفيف"
    WorkoutPhaseType.WALK -> "مشي"
    WorkoutPhaseType.RUN -> "ركض"
    WorkoutPhaseType.COOL_DOWN -> "تهدئة"
}

private fun formatTime(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatCompactDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (seconds == 0) {
        "$minutes د"
    } else {
        "%d:%02d د".format(minutes, seconds)
    }
}

private fun formatDistance(distanceMeters: Double): String =
    "%.2f كم".format(distanceMeters.coerceAtLeast(0.0) / 1_000.0)

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)
private val SurfaceGray = Color(0xFFF5F5F5)

@Composable
fun RakdatakHomeScreen(
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
                    title = currentPlanTitle,
                    watchConnected = watchConnected,
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
    title: String,
    watchConnected: Boolean,
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
                    Text(title, color = Black, style = MaterialTheme.typography.titleLarge)
                }

                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = Orange,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                "إحماء خفيف ثم مشي وركض حسب المرحلة المناسبة لك.",
                color = Gray,
                style = MaterialTheme.typography.bodyMedium,
            )

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

package com.rakdatak.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rakdatak.core.training.BaselinePlanFactory
import com.rakdatak.core.training.WorkoutSessionEngine
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.WorkoutPhaseType
import com.rakdatak.wear.health.WearExerciseManager
import com.rakdatak.wear.health.WearExerciseMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Orange = Color(0xFFFF6D00)
private val Dark = Color(0xFF111111)
private val SoftGray = Color(0xFFB8B8B8)
private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RakdatakWearApp()
            }
        }
    }
}

@Composable
private fun RakdatakWearApp() {
    val context = LocalContext.current
    val plan = remember { BaselinePlanFactory.create().first() }
    val exerciseManager = remember { WearExerciseManager(context) }
    val metrics by exerciseManager.metrics.collectAsState()
    val scope = rememberCoroutineScope()

    var engine by remember { mutableStateOf(WorkoutSessionEngine(plan)) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var gpsEnabled by remember { mutableStateOf(false) }

    val beginWorkout: () -> Unit = {
        scope.launch {
            exerciseManager.start(gpsEnabled = gpsEnabled)
            engine = WorkoutSessionEngine(plan)
            snapshot = engine.start()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // The workout can still run as a timed workout when a sensor permission is declined.
        beginWorkout()
    }

    LaunchedEffect(snapshot.status) {
        if (snapshot.status == WorkoutSessionStatus.RUNNING) {
            while (snapshot.status == WorkoutSessionStatus.RUNNING) {
                delay(1_000)
                snapshot = engine.tick()
                if (snapshot.status == WorkoutSessionStatus.COMPLETED) {
                    exerciseManager.end()
                }
            }
        }
    }

    when (snapshot.status) {
        WorkoutSessionStatus.READY -> ReadyScreen(
            gpsEnabled = gpsEnabled,
            onToggleGps = { gpsEnabled = !gpsEnabled },
            onStart = {
                val missing = requiredExercisePermissions(gpsEnabled).filter { permission ->
                    context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
                }

                if (missing.isEmpty()) {
                    beginWorkout()
                } else {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            },
        )

        WorkoutSessionStatus.RUNNING,
        WorkoutSessionStatus.PAUSED -> WorkoutScreen(
            snapshot = snapshot,
            metrics = metrics,
            onPauseResume = {
                if (snapshot.status == WorkoutSessionStatus.PAUSED) {
                    snapshot = engine.resume()
                    scope.launch { exerciseManager.resume() }
                } else {
                    snapshot = engine.pause()
                    scope.launch { exerciseManager.pause() }
                }
            },
            onFinish = {
                snapshot = engine.stop()
                scope.launch { exerciseManager.end() }
            },
        )

        WorkoutSessionStatus.COMPLETED,
        WorkoutSessionStatus.STOPPED -> SummaryScreen(
            snapshot = snapshot,
            metrics = metrics,
            onDone = {
                engine = WorkoutSessionEngine(plan)
                snapshot = engine.snapshot()
            },
        )
    }
}

@Composable
private fun ReadyScreen(
    gpsEnabled: Boolean,
    onToggleGps: () -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "ركضتك",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "بداية هادئة",
            color = SoftGray,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ActionChip(
            text = if (gpsEnabled) "GPS: مفعّل" else "GPS: بدون",
            background = Dark,
            onClick = onToggleGps,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ActionChip(
            text = "ابدأ التمرين",
            background = Orange,
            onClick = onStart,
        )
    }
}

@Composable
private fun WorkoutScreen(
    snapshot: WorkoutSessionSnapshot,
    metrics: WearExerciseMetrics,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = phaseLabel(snapshot.currentPhase.type),
            color = Orange,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = formatTime(snapshot.phaseRemainingSeconds),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "متبقي لهذه المرحلة",
            color = SoftGray,
            fontSize = 11.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Metric(
                value = metrics.heartRateBpm?.toInt()?.toString() ?: "--",
                label = "نبض",
            )
            Metric(value = formatTime(snapshot.totalElapsedSeconds), label = "الوقت")
            Metric(
                value = metrics.distanceMeters?.let { "%.2f".format(it / 1_000.0) } ?: "--",
                label = "كم",
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            ActionChip(
                text = if (snapshot.status == WorkoutSessionStatus.PAUSED) "متابعة" else "إيقاف مؤقت",
                background = Orange,
                onClick = onPauseResume,
            )
            Spacer(modifier = Modifier.width(8.dp))
            ActionChip(
                text = "إنهاء",
                background = Dark,
                onClick = onFinish,
            )
        }
    }
}

@Composable
private fun SummaryScreen(
    snapshot: WorkoutSessionSnapshot,
    metrics: WearExerciseMetrics,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (snapshot.status == WorkoutSessionStatus.COMPLETED) "أحسنت!" else "تم حفظ التمرين",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "${(snapshot.completionRatio * 100).toInt()}% مكتمل",
            color = SoftGray,
            fontSize = 12.sp,
        )
        metrics.distanceMeters?.let { distance ->
            Text(
                text = "%.2f كم".format(distance / 1_000.0),
                color = SoftGray,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        ActionChip(
            text = "تم",
            background = Orange,
            onClick = onDone,
        )
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = SoftGray,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ActionChip(
    text: String,
    background: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

private fun requiredExercisePermissions(gpsEnabled: Boolean): List<String> = buildList {
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 36) {
        add(READ_HEART_RATE)
    } else {
        add(Manifest.permission.BODY_SENSORS)
    }
    if (gpsEnabled) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun phaseLabel(type: WorkoutPhaseType): String = when (type) {
    WorkoutPhaseType.WARM_UP -> "إحماء"
    WorkoutPhaseType.WALK -> "مشي"
    WorkoutPhaseType.RUN -> "ركض"
    WorkoutPhaseType.COOL_DOWN -> "تهدئة"
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

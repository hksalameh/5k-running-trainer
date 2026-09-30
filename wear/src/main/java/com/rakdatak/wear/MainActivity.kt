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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.WorkoutPhaseType
import com.rakdatak.wear.health.WearExerciseMetrics
import com.rakdatak.wear.health.WearWorkoutRepository
import com.rakdatak.wear.health.WearWorkoutService

private val Orange = Color(0xFFFF6D00)
private val Dark = Color(0xFF111111)
private val SoftGray = Color(0xFFB8B8B8)
private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
private const val READ_HEALTH_DATA_IN_BACKGROUND =
    "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

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
    val workoutState by WearWorkoutRepository.state.collectAsState()
    var gpsEnabled by remember { mutableStateOf(false) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val startWithAvailablePermissions: () -> Unit = {
        val activityGranted =
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        if (!activityGranted) {
            permissionNotice = "يلزم إذن النشاط حتى يستمر التمرين والشاشة مطفأة."
        } else {
            val locationGranted =
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            val backgroundPermission = backgroundHealthPermission()
            val backgroundHealthGranted = backgroundPermission == null ||
                context.checkSelfPermission(backgroundPermission) == PackageManager.PERMISSION_GRANTED
            val useGps = gpsEnabled && locationGranted

            permissionNotice = when {
                !backgroundHealthGranted ->
                    "سيبدأ التمرين، لكن قراءة النبض قد تتوقف عند إطفاء الشاشة لأن إذن الصحة في الخلفية غير مفعّل."
                gpsEnabled && !locationGranted ->
                    "سيبدأ التمرين بدون GPS لأن إذن الموقع غير مفعّل."
                else -> null
            }
            WearWorkoutService.start(context, useGps)
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Background health access is helpful but not mandatory. Start even if the user declines it.
        startWithAvailablePermissions()
    }

    val primaryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val backgroundPermission = backgroundHealthPermission()
        if (backgroundPermission != null &&
            context.checkSelfPermission(backgroundPermission) != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundPermissionLauncher.launch(arrayOf(backgroundPermission))
        } else {
            startWithAvailablePermissions()
        }
    }

    val snapshot = workoutState.snapshot
    when (snapshot?.status) {
        null,
        WorkoutSessionStatus.READY -> ReadyScreen(
            gpsEnabled = gpsEnabled,
            notice = permissionNotice,
            onToggleGps = { gpsEnabled = !gpsEnabled },
            onStart = {
                val missingPrimary = requiredPrimaryExercisePermissions(gpsEnabled).filter { permission ->
                    context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
                }

                if (missingPrimary.isNotEmpty()) {
                    primaryPermissionLauncher.launch(missingPrimary.toTypedArray())
                } else {
                    val backgroundPermission = backgroundHealthPermission()
                    if (backgroundPermission != null &&
                        context.checkSelfPermission(backgroundPermission) != PackageManager.PERMISSION_GRANTED
                    ) {
                        backgroundPermissionLauncher.launch(arrayOf(backgroundPermission))
                    } else {
                        startWithAvailablePermissions()
                    }
                }
            },
        )

        WorkoutSessionStatus.RUNNING,
        WorkoutSessionStatus.PAUSED -> WorkoutScreen(
            snapshot = snapshot,
            metrics = workoutState.metrics,
            gpsEnabled = workoutState.gpsEnabled,
            onPauseResume = { WearWorkoutService.togglePause(context) },
            onFinish = { WearWorkoutService.stop(context) },
        )

        WorkoutSessionStatus.COMPLETED,
        WorkoutSessionStatus.STOPPED -> SummaryScreen(
            snapshot = snapshot,
            metrics = workoutState.metrics,
            onDone = { WearWorkoutRepository.resetAfterSummary() },
        )
    }
}

@Composable
private fun ReadyScreen(
    gpsEnabled: Boolean,
    notice: String?,
    onToggleGps: () -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
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
            text = if (gpsEnabled) "GPS: مفعّل" else "GPS: مغلق",
            background = Dark,
            onClick = onToggleGps,
        )
        notice?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it,
                color = SoftGray,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ActionChip(
            text = "ابدأ التمرين",
            background = Orange,
            onClick = onStart,
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun WorkoutScreen(
    snapshot: WorkoutSessionSnapshot,
    metrics: WearExerciseMetrics,
    gpsEnabled: Boolean,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = phaseLabel(snapshot.currentPhase.type),
            color = Orange,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))

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

        Spacer(modifier = Modifier.height(9.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ActionChip(
                text = if (snapshot.status == WorkoutSessionStatus.PAUSED) "متابعة التمرين" else "إيقاف مؤقت",
                background = Orange,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPauseResume,
            )
            ActionChip(
                text = "إنهاء وحفظ التمرين",
                background = Dark,
                modifier = Modifier.fillMaxWidth(),
                onClick = onFinish,
            )
        }

        Spacer(modifier = Modifier.height(13.dp))

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

        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = when {
                metrics.heartRateBpm != null && gpsEnabled && metrics.distanceMeters != null ->
                    "النبض والمسافة يعملان"
                metrics.heartRateBpm != null && !gpsEnabled ->
                    "النبض يعمل • فعّل GPS قبل التمرين للمسافة"
                metrics.heartRateAvailable ->
                    "جاري قراءة النبض من الساعة..."
                else ->
                    "نحاول قراءة النبض من حساس الساعة"
            },
            color = SoftGray,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )

        if (gpsEnabled && !metrics.distanceAvailable) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "المسافة عبر GPS غير متاحة حاليًا",
                color = SoftGray,
                fontSize = 9.sp,
            )
        }

        metrics.errorMessage?.let {
            if (metrics.heartRateBpm == null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "تعذر تشغيل Health Services؛ التمرين مستمر بالحساس المباشر.",
                    color = SoftGray,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
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
        Spacer(modifier = Modifier.height(20.dp))
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    )
}

private fun requiredPrimaryExercisePermissions(gpsEnabled: Boolean): List<String> = buildList {
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 36) {
        add(READ_HEART_RATE)
    } else {
        add(Manifest.permission.BODY_SENSORS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (gpsEnabled) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun backgroundHealthPermission(): String? = when {
    Build.VERSION.SDK_INT >= 36 -> READ_HEALTH_DATA_IN_BACKGROUND
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.BODY_SENSORS_BACKGROUND
    else -> null
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

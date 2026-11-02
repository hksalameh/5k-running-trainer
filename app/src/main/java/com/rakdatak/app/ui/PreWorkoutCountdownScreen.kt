package com.rakdatak.app.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPhaseType
import kotlinx.coroutines.delay

private val CountdownOrange = Color(0xFFFF6D00)
private val CountdownBlack = Color(0xFF141414)

@Composable
fun PreWorkoutCountdownScreen(
    firstPhase: WorkoutPhase,
    soundCuesEnabled: Boolean,
    vibrationEnabled: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }
    val vibrator = remember { countdownVibrator(context) }
    var seconds by remember { mutableIntStateOf(3) }

    DisposableEffect(tone) {
        onDispose { tone.release() }
    }

    LaunchedEffect(Unit) {
        for (value in 3 downTo 1) {
            seconds = value
            if (soundCuesEnabled) {
                tone.startTone(
                    if (value == 1) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                    180,
                )
            }
            if (vibrationEnabled) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        if (value == 1) 130L else 75L,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    )
                )
            }
            delay(1_000)
        }
        onFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CountdownBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "استعد",
                color = CountdownOrange,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = seconds.toString(),
                color = Color.White,
                fontSize = 88.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${phaseLabel(firstPhase.type)} • ${formatTime(firstPhase.durationSeconds)}",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "سيبدأ التمرين بعد العد التنازلي",
                color = Color(0xFFBDBDBD),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun countdownVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private fun phaseLabel(type: WorkoutPhaseType): String = when (type) {
    WorkoutPhaseType.WARM_UP -> "إحماء خفيف"
    WorkoutPhaseType.WALK -> "مشي"
    WorkoutPhaseType.RUN -> "ركض"
    WorkoutPhaseType.COOL_DOWN -> "تهدئة"
}

private fun formatTime(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

package com.rakdatak.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.WorkoutPhaseType
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight Arabic voice/haptic coach for the phone workout screen.
 * Voice gender selection is intentionally left for the settings layer because Android TTS engines
 * do not expose a reliable cross-device gender field. The active Arabic system voice is used here.
 */
class PhoneWorkoutCoach(context: Context) : TextToSpeech.OnInitListener, AutoCloseable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { }
        .build()
    private val utteranceCounter = AtomicInteger(0)
    private val tts = TextToSpeech(appContext, this)

    private var ready = false
    private var lastPhaseIndex = -1
    private var tenSecondCuePhase = -1
    private var completionAnnounced = false

    init {
        tts.setAudioAttributes(audioAttributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                abandonAudioFocus()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                abandonAudioFocus()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                abandonAudioFocus()
            }
        })
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale("ar"))
        ready = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun onSnapshot(snapshot: WorkoutSessionSnapshot) {
        if (snapshot.status == WorkoutSessionStatus.COMPLETED) {
            if (!completionAnnounced) {
                completionAnnounced = true
                vibrateFinished()
                speak("أحسنت، انتهى التمرين")
            }
            return
        }

        if (snapshot.status != WorkoutSessionStatus.RUNNING) return

        if (snapshot.phaseIndex != lastPhaseIndex) {
            val isFirstPhase = lastPhaseIndex == -1
            lastPhaseIndex = snapshot.phaseIndex
            tenSecondCuePhase = -1
            if (!isFirstPhase) vibrateTransition()
            speak(phasePrompt(snapshot.currentPhase.type))
        }

        if (snapshot.phaseRemainingSeconds == 10 && tenSecondCuePhase != snapshot.phaseIndex) {
            tenSecondCuePhase = snapshot.phaseIndex
            speak("باقي عشر ثواني")
        }
    }

    private fun phasePrompt(type: WorkoutPhaseType): String = when (type) {
        WorkoutPhaseType.WARM_UP -> "ابدأ بإحماء خفيف"
        WorkoutPhaseType.WALK -> "امشِ الآن"
        WorkoutPhaseType.RUN -> "ابدأ الركض"
        WorkoutPhaseType.COOL_DOWN -> "خفف السرعة للتهدئة"
    }

    private fun speak(text: String) {
        if (!ready) return
        audioManager.requestAudioFocus(focusRequest)
        val utteranceId = "rakdatak-${utteranceCounter.incrementAndGet()}"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun vibrateTransition() {
        vibrator()?.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrateFinished() {
        vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 160, 100, 220), -1))
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    override fun close() {
        abandonAudioFocus()
        tts.stop()
        tts.shutdown()
    }
}

@Composable
fun PhoneWorkoutCoachEffect(snapshot: WorkoutSessionSnapshot) {
    val context = LocalContext.current
    val coach = remember { PhoneWorkoutCoach(context) }

    DisposableEffect(coach) {
        onDispose { coach.close() }
    }

    LaunchedEffect(
        snapshot.status,
        snapshot.phaseIndex,
        snapshot.phaseRemainingSeconds,
    ) {
        coach.onSnapshot(snapshot)
    }
}

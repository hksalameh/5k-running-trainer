package com.rakdatak.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPhaseType
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Arabic voice/haptic coach for workout transitions on the phone. */
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
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 90)

    private var ready = false
    private var pendingFirstCue: String? = null
    private var lastPhaseIndex = -1
    private var oneMinuteCuePhase = -1
    private var tenSecondCuePhase = -1
    private var lastFinalBeepSecond = -1
    private var completionAnnounced = false
    private var needsSessionReset = false

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
        ready = configureArabicVoice()
        if (ready) {
            pendingFirstCue?.let { pending ->
                pendingFirstCue = null
                speak(pending)
            }
        }
    }

    fun onSnapshot(
        snapshot: WorkoutSessionSnapshot,
        soundCuesEnabled: Boolean,
        vibrationEnabled: Boolean,
    ) {
        if (snapshot.status == WorkoutSessionStatus.STOPPED) {
            needsSessionReset = true
            return
        }

        if (snapshot.status == WorkoutSessionStatus.COMPLETED) {
            needsSessionReset = true
            if (!completionAnnounced) {
                completionAnnounced = true
                if (vibrationEnabled) vibrateFinished()
                if (soundCuesEnabled) speak("أحسنت. انتهى التمرين.")
            }
            return
        }

        if (snapshot.status != WorkoutSessionStatus.RUNNING) return

        if (
            needsSessionReset ||
            (snapshot.phaseIndex == 0 && snapshot.totalElapsedSeconds == 0 && lastPhaseIndex > 0)
        ) {
            resetSessionCues()
        }

        if (snapshot.phaseIndex != lastPhaseIndex) {
            val isFirstPhase = lastPhaseIndex == -1
            lastPhaseIndex = snapshot.phaseIndex
            oneMinuteCuePhase = -1
            tenSecondCuePhase = -1
            lastFinalBeepSecond = -1
            if (!isFirstPhase && vibrationEnabled) vibrateTransition()
            if (soundCuesEnabled) {
                speak(phaseStartPrompt(snapshot.currentPhase))
            }
        }

        if (
            snapshot.currentPhase.durationSeconds > 60 &&
            snapshot.phaseRemainingSeconds in 55..60 &&
            oneMinuteCuePhase != snapshot.phaseIndex
        ) {
            oneMinuteCuePhase = snapshot.phaseIndex
            if (soundCuesEnabled) speak("باقي دقيقة واحدة على هذه المرحلة.")
        }

        if (
            snapshot.phaseRemainingSeconds in 8..10 &&
            tenSecondCuePhase != snapshot.phaseIndex
        ) {
            tenSecondCuePhase = snapshot.phaseIndex
            if (soundCuesEnabled) {
                speak(preTransitionPrompt(snapshot.nextPhase))
            }
        }

        if (
            snapshot.phaseRemainingSeconds in 1..3 &&
            lastFinalBeepSecond != snapshot.phaseRemainingSeconds
        ) {
            lastFinalBeepSecond = snapshot.phaseRemainingSeconds
            if (soundCuesEnabled) {
                playFinalCountdownBeep(snapshot.phaseRemainingSeconds)
            }
            if (vibrationEnabled) {
                vibrateCountdownTick(snapshot.phaseRemainingSeconds)
            }
        }
    }

    private fun configureArabicVoice(): Boolean {
        val arabicVoices = tts.voices
            ?.filter { it.locale.language.equals("ar", ignoreCase = true) }
            .orEmpty()

        val bestVoice = arabicVoices.maxByOrNull(::voiceScore)
        if (bestVoice != null && tts.setVoice(bestVoice) == TextToSpeech.SUCCESS) {
            tts.language = bestVoice.locale
            tts.setSpeechRate(0.90f)
            tts.setPitch(0.98f)
            return true
        }

        val fallbackLocales = listOf(
            Locale("ar", "JO"),
            Locale("ar", "SA"),
            Locale("ar", "AE"),
            Locale("ar"),
        )
        for (locale in fallbackLocales) {
            val result = tts.setLanguage(locale)
            if (
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.setSpeechRate(0.90f)
                tts.setPitch(0.98f)
                return true
            }
        }
        return false
    }

    private fun voiceScore(voice: Voice): Int {
        val tag = voice.locale.toLanguageTag()
        val localeScore = when {
            tag.equals("ar-JO", ignoreCase = true) -> 10_000
            tag.equals("ar-SA", ignoreCase = true) -> 8_000
            tag.equals("ar-AE", ignoreCase = true) -> 7_000
            voice.locale.language.equals("ar", ignoreCase = true) -> 5_000
            else -> 0
        }
        val offlineBonus = if (!voice.isNetworkConnectionRequired) 250 else 0
        return localeScore + (voice.quality * 10) + offlineBonus - voice.latency
    }

    private fun phaseStartPrompt(phase: WorkoutPhase): String {
        val duration = arabicDuration(phase.durationSeconds)
        return when (phase.type) {
            WorkoutPhaseType.WARM_UP -> "ابدأ إحماءً خفيفًا لمدة $duration."
            WorkoutPhaseType.WALK -> "اِمْشِ الآن لمدة $duration."
            WorkoutPhaseType.RUN -> "اِرْكُضْ الآن لمدة $duration."
            WorkoutPhaseType.COOL_DOWN -> "خفف السرعة للتهدئة لمدة $duration."
        }
    }

    private fun preTransitionPrompt(nextPhase: WorkoutPhase?): String =
        if (nextPhase == null) {
            "استعد. باقي عشر ثوانٍ وينتهي التمرين."
        } else {
            val duration = arabicDuration(nextPhase.durationSeconds)
            val action = when (nextPhase.type) {
                WorkoutPhaseType.WARM_UP -> "سيبدأ الإحماء لمدة $duration"
                WorkoutPhaseType.WALK -> "ستبدأ المشي لمدة $duration"
                WorkoutPhaseType.RUN -> "ستبدأ الركض لمدة $duration"
                WorkoutPhaseType.COOL_DOWN -> "ستبدأ التهدئة لمدة $duration"
            }
            "استعد. بعد عشر ثوانٍ، $action."
        }

    private fun arabicDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val minutesText = when (minutes) {
            0 -> ""
            1 -> "دقيقة واحدة"
            2 -> "دقيقتين"
            3 -> "ثلاث دقائق"
            4 -> "أربع دقائق"
            5 -> "خمس دقائق"
            6 -> "ست دقائق"
            7 -> "سبع دقائق"
            8 -> "ثمان دقائق"
            9 -> "تسع دقائق"
            10 -> "عشر دقائق"
            20 -> "عشرين دقيقة"
            25 -> "خمس وعشرين دقيقة"
            28 -> "ثمان وعشرين دقيقة"
            30 -> "ثلاثين دقيقة"
            else -> "$minutes دقائق"
        }
        val secondsText = when (seconds) {
            0 -> ""
            10 -> "عشر ثوانٍ"
            15 -> "خمس عشرة ثانية"
            20 -> "عشرين ثانية"
            30 -> "ثلاثين ثانية"
            45 -> "خمس وأربعين ثانية"
            else -> "$seconds ثانية"
        }
        return when {
            minutesText.isNotEmpty() && secondsText.isNotEmpty() -> "$minutesText و$secondsText"
            minutesText.isNotEmpty() -> minutesText
            secondsText.isNotEmpty() -> secondsText
            else -> "ثانية واحدة"
        }
    }

    private fun playFinalCountdownBeep(secondsRemaining: Int) {
        val tone = if (secondsRemaining == 1) {
            ToneGenerator.TONE_PROP_ACK
        } else {
            ToneGenerator.TONE_PROP_BEEP
        }
        toneGenerator.startTone(tone, 150)
    }

    private fun speak(text: String) {
        if (!ready) {
            pendingFirstCue = text
            return
        }
        audioManager.requestAudioFocus(focusRequest)
        val utteranceId = "rakdatak-${utteranceCounter.incrementAndGet()}"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun resetSessionCues() {
        lastPhaseIndex = -1
        oneMinuteCuePhase = -1
        tenSecondCuePhase = -1
        lastFinalBeepSecond = -1
        completionAnnounced = false
        needsSessionReset = false
    }

    private fun vibrateTransition() {
        vibrator()?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrateCountdownTick(secondsRemaining: Int) {
        val duration = if (secondsRemaining == 1) 120L else 70L
        vibrator()?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
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
        toneGenerator.release()
        tts.stop()
        tts.shutdown()
    }
}

@Composable
fun PhoneWorkoutCoachEffect(
    snapshot: WorkoutSessionSnapshot,
    soundCuesEnabled: Boolean,
    vibrationEnabled: Boolean,
) {
    val context = LocalContext.current
    val coach = remember { PhoneWorkoutCoach(context) }

    DisposableEffect(coach) {
        onDispose { coach.close() }
    }

    LaunchedEffect(
        snapshot.status,
        snapshot.phaseIndex,
        snapshot.phaseRemainingSeconds,
        soundCuesEnabled,
        vibrationEnabled,
    ) {
        coach.onSnapshot(
            snapshot = snapshot,
            soundCuesEnabled = soundCuesEnabled,
            vibrationEnabled = vibrationEnabled,
        )
    }
}

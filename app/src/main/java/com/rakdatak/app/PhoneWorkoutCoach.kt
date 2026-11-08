package com.rakdatak.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.rakdatak.app.settings.WorkoutVoice
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPhaseType
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private data class VoiceClip(
    val startMs: Int,
    val endMs: Int,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(100).toLong()
}

private data class VoicePackSpec(
    val filename: String,
    val url: String,
    val clips: Map<String, VoiceClip>,
)

/**
 * Arabic workout coach.
 *
 * Primary path: a cached natural neural voice pack, downloaded once and replayed offline.
 * Fallback path: Android TTS if the selected natural pack is not ready.
 */
class PhoneWorkoutCoach(
    context: Context,
    private val workoutVoice: WorkoutVoice,
) : TextToSpeech.OnInitListener, AutoCloseable {
    private val appContext = context.applicationContext
    private val voicePack = if (workoutVoice == WorkoutVoice.FEMALE) FEMALE_PACK else MALE_PACK
    private val voicePackFile = File(appContext.filesDir, voicePack.filename)

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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val voiceExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var naturalPlayer: MediaPlayer? = null

    @Volatile
    private var closed = false

    private var playbackToken = 0
    private var ready = false
    private var pendingFirstCue: String? = null
    private var pendingFirstCueCompletion: (() -> Unit)? = null
    private var activeUtteranceId: String? = null
    private var activeUtteranceCompletion: (() -> Unit)? = null
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
                finishSpokenUtterance(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishSpokenUtterance(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finishSpokenUtterance(utteranceId)
            }
        })
        prepareNaturalVoicePack()
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS && configureArabicVoice()
        val pending = pendingFirstCue
        val completion = pendingFirstCueCompletion
        pendingFirstCue = null
        pendingFirstCueCompletion = null
        if (pending != null && ready) {
            speakWithTts(pending, completion)
        } else {
            // Do not strand the interval counter when the TTS engine fails.
            mainHandler.post { completion?.invoke() }
        }
    }

    private fun finishSpokenUtterance(id: String?) {
        mainHandler.post {
            abandonAudioFocus()
            if (!closed && id != null && id == activeUtteranceId) {
                val complete = activeUtteranceCompletion
                activeUtteranceId = null
                activeUtteranceCompletion = null
                complete?.invoke()
            }
        }
    }

    fun onSnapshot(
        snapshot: WorkoutSessionSnapshot,
        soundCuesEnabled: Boolean,
        vibrationEnabled: Boolean,
        onPhaseInstructionCompleted: () -> Unit,
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
                if (soundCuesEnabled) {
                    speakCue(
                        clipKey = CLIP_FINISHED,
                        fallbackText = "أحسنت. انتهى التمرين.",
                    )
                }
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
                speakCue(
                    clipKey = clipKeyForPhase(snapshot.currentPhase),
                    fallbackText = phaseStartPrompt(snapshot.currentPhase),
                    onComplete = onPhaseInstructionCompleted,
                )
            } else {
                onPhaseInstructionCompleted()
            }
        }

        if (
            snapshot.currentPhase.durationSeconds > 60 &&
            oneMinuteCuePhase != snapshot.phaseIndex &&
            shouldStartAlignedCue(
                remainingSeconds = snapshot.phaseRemainingSeconds,
                targetRemainingSeconds = 60,
                clipKey = CLIP_ONE_MINUTE,
            )
        ) {
            oneMinuteCuePhase = snapshot.phaseIndex
            if (soundCuesEnabled) {
                speakCueAligned(
                    clipKey = CLIP_ONE_MINUTE,
                    fallbackText = "باقي دقيقة واحدة على هذه المرحلة.",
                    currentRemainingSeconds = snapshot.phaseRemainingSeconds,
                    targetRemainingSeconds = 60,
                )
            }
        }

        if (
            tenSecondCuePhase != snapshot.phaseIndex &&
            shouldStartAlignedCue(
                remainingSeconds = snapshot.phaseRemainingSeconds,
                targetRemainingSeconds = 10,
                clipKey = if (snapshot.nextPhase == null) CLIP_FINISH_TEN_SECONDS else CLIP_TRANSITION,
            )
        ) {
            tenSecondCuePhase = snapshot.phaseIndex
            if (soundCuesEnabled) {
                val finishing = snapshot.nextPhase == null
                speakCueAligned(
                    clipKey = if (finishing) CLIP_FINISH_TEN_SECONDS else CLIP_TRANSITION,
                    fallbackText = if (finishing) {
                        "استعد. باقي عشر ثوانٍ وينتهي التمرين."
                    } else {
                        "استعد. المرحلة التالية بعد عشر ثوانٍ."
                    },
                    currentRemainingSeconds = snapshot.phaseRemainingSeconds,
                    targetRemainingSeconds = 10,
                )
            }
        }

        if (
            snapshot.phaseRemainingSeconds in 1..3 &&
            lastFinalBeepSecond != snapshot.phaseRemainingSeconds
        ) {
            lastFinalBeepSecond = snapshot.phaseRemainingSeconds
            if (soundCuesEnabled) playFinalCountdownBeep(snapshot.phaseRemainingSeconds)
            if (vibrationEnabled) vibrateCountdownTick(snapshot.phaseRemainingSeconds)
        }
    }

    private fun shouldStartAlignedCue(
        remainingSeconds: Int,
        targetRemainingSeconds: Int,
        clipKey: String,
    ): Boolean {
        val naturalReady = naturalPlayer != null && voicePack.clips[clipKey] != null
        return if (naturalReady) {
            remainingSeconds in (targetRemainingSeconds + 1)..(targetRemainingSeconds + ALIGNMENT_LOOKAHEAD_SECONDS)
        } else {
            remainingSeconds == targetRemainingSeconds
        }
    }

    private fun speakCueAligned(
        clipKey: String,
        fallbackText: String,
        currentRemainingSeconds: Int,
        targetRemainingSeconds: Int,
    ) {
        val clip = voicePack.clips[clipKey]
        if (clip != null && naturalPlayer != null) {
            val millisecondsUntilTarget =
                (currentRemainingSeconds - targetRemainingSeconds).coerceAtLeast(0) * 1_000L
            val delayMs = (millisecondsUntilTarget - clip.durationMs).coerceAtLeast(0L)
            if (playNaturalClip(clipKey, delayMs)) return
        }
        speakWithTts(fallbackText)
    }

    private fun speakCue(
        clipKey: String?,
        fallbackText: String,
        onComplete: (() -> Unit)? = null,
    ) {
        if (clipKey != null && playNaturalClip(clipKey, 0L, onComplete)) return
        speakWithTts(fallbackText, onComplete)
    }

    private fun playNaturalClip(
        key: String,
        delayMs: Long,
        onComplete: (() -> Unit)? = null,
    ): Boolean {
        val clip = voicePack.clips[key] ?: return false
        val player = naturalPlayer ?: return false
        if (closed) return false

        playbackToken += 1
        val token = playbackToken

        mainHandler.postDelayed(
            {
                if (closed || playbackToken != token) return@postDelayed
                try {
                    audioManager.requestAudioFocus(focusRequest)

                    if (player.isPlaying) player.pause()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        player.seekTo(clip.startMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                    } else {
                        @Suppress("DEPRECATION")
                        player.seekTo(clip.startMs)
                    }
                    player.start()

                    mainHandler.postDelayed(
                        {
                            if (!closed && playbackToken == token) {
                                runCatching {
                                    if (player.isPlaying) player.pause()
                                }
                                abandonAudioFocus()
                                onComplete?.invoke()
                            }
                        },
                        clip.durationMs + 80L,
                    )
                } catch (_: Throwable) {
                    // A broken audio player must never leave the next interval frozen.
                    onComplete?.invoke()
                }
            },
            delayMs,
        )
        return true
    }

    private fun prepareNaturalVoicePack() {
        voiceExecutor.execute {
            try {
                if (!voicePackFile.exists() || voicePackFile.length() < 32_000L) {
                    downloadVoicePack()
                }
                if (closed || !voicePackFile.exists()) return@execute

                val preparedPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(voicePackFile.absolutePath)
                    prepare()
                }

                if (closed) {
                    preparedPlayer.release()
                } else {
                    naturalPlayer = preparedPlayer
                }
            } catch (_: Throwable) {
                // Network failure is harmless: Android TTS remains the fallback.
            }
        }
    }

    private fun downloadVoicePack() {
        val temporaryFile = File(appContext.filesDir, "${voicePack.filename}.tmp")
        runCatching { temporaryFile.delete() }

        val connection = (URL(voicePack.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return
            connection.inputStream.use { input ->
                temporaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (temporaryFile.length() >= 32_000L) {
                if (voicePackFile.exists()) voicePackFile.delete()
                temporaryFile.renameTo(voicePackFile)
            }
        } finally {
            connection.disconnect()
            if (temporaryFile.exists()) temporaryFile.delete()
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
            tts.setPitch(if (workoutVoice == WorkoutVoice.FEMALE) 1.06f else 0.98f)
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
                tts.setPitch(if (workoutVoice == WorkoutVoice.FEMALE) 1.06f else 0.98f)
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
        return localeScore + (voice.quality * 10) - voice.latency
    }

    private fun clipKeyForPhase(phase: WorkoutPhase): String? = when (phase.type) {
        WorkoutPhaseType.WARM_UP ->
            if (phase.durationSeconds == 180) CLIP_WARMUP_180 else null

        WorkoutPhaseType.COOL_DOWN ->
            if (phase.durationSeconds == 180) CLIP_COOLDOWN_180 else null

        WorkoutPhaseType.RUN -> when (phase.durationSeconds) {
            60 -> CLIP_RUN_60
            90 -> CLIP_RUN_90
            180 -> CLIP_RUN_180
            300 -> CLIP_RUN_300
            480 -> CLIP_RUN_480
            600 -> CLIP_RUN_600
            1_200 -> CLIP_RUN_1200
            1_500 -> CLIP_RUN_1500
            1_680 -> CLIP_RUN_1680
            1_800 -> CLIP_RUN_1800
            else -> null
        }

        WorkoutPhaseType.WALK -> when (phase.durationSeconds) {
            90 -> CLIP_WALK_90
            120 -> CLIP_WALK_120
            150 -> CLIP_WALK_150
            180 -> CLIP_WALK_180
            300 -> CLIP_WALK_300
            else -> null
        }
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

    private fun speakWithTts(
        text: String,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!ready) {
            pendingFirstCue = text
            pendingFirstCueCompletion = onComplete
            return
        }
        audioManager.requestAudioFocus(focusRequest)
        val utteranceId = "rakdatak-" + utteranceCounter.incrementAndGet()
        activeUtteranceId = utteranceId
        activeUtteranceCompletion = onComplete
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
            finishSpokenUtterance(utteranceId)
        }
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
        closed = true
        playbackToken += 1
        mainHandler.removeCallbacksAndMessages(null)
        abandonAudioFocus()
        naturalPlayer?.release()
        naturalPlayer = null
        voiceExecutor.shutdownNow()
        toneGenerator.release()
        tts.stop()
        tts.shutdown()
        pendingFirstCueCompletion = null
        activeUtteranceCompletion = null
    }

    private companion object {
        const val ALIGNMENT_LOOKAHEAD_SECONDS = 5

        const val CLIP_WARMUP_180 = "warmup_180"
        const val CLIP_TRANSITION = "transition"
        const val CLIP_ONE_MINUTE = "one_minute"
        const val CLIP_RUN_60 = "run_60"
        const val CLIP_RUN_90 = "run_90"
        const val CLIP_RUN_180 = "run_180"
        const val CLIP_RUN_300 = "run_300"
        const val CLIP_RUN_480 = "run_480"
        const val CLIP_RUN_600 = "run_600"
        const val CLIP_RUN_1200 = "run_1200"
        const val CLIP_RUN_1500 = "run_1500"
        const val CLIP_RUN_1680 = "run_1680"
        const val CLIP_RUN_1800 = "run_1800"
        const val CLIP_WALK_90 = "walk_90"
        const val CLIP_WALK_120 = "walk_120"
        const val CLIP_WALK_150 = "walk_150"
        const val CLIP_WALK_180 = "walk_180"
        const val CLIP_WALK_300 = "walk_300"
        const val CLIP_COOLDOWN_180 = "cooldown_180"
        const val CLIP_FINISH_TEN_SECONDS = "finish_10"
        const val CLIP_FINISHED = "finished"

        val MALE_PACK = VoicePackSpec(
            filename = "rakdatak_ar_male_v1.wav",
            url = "https://resource2.heygen.ai/text_to_speech/90f75bc626b143ae832bc35280491ae2/02733a92a0db457aadf63b235d2aa457/id=52ab6acc-b736-44f4-89ab-c2386b36f621.wav",
            clips = mapOf(
                CLIP_WARMUP_180 to VoiceClip(250, 3_500),
                CLIP_TRANSITION to VoiceClip(3_570, 7_360),
                CLIP_ONE_MINUTE to VoiceClip(7_390, 10_080),
                CLIP_RUN_60 to VoiceClip(10_050, 12_640),
                CLIP_RUN_90 to VoiceClip(12_740, 16_260),
                CLIP_RUN_180 to VoiceClip(16_380, 18_930),
                CLIP_RUN_300 to VoiceClip(19_030, 21_640),
                CLIP_RUN_480 to VoiceClip(21_750, 24_370),
                CLIP_RUN_600 to VoiceClip(24_400, 27_000),
                CLIP_RUN_1200 to VoiceClip(27_050, 29_600),
                CLIP_RUN_1500 to VoiceClip(29_610, 32_740),
                CLIP_RUN_1680 to VoiceClip(32_840, 36_060),
                CLIP_RUN_1800 to VoiceClip(36_110, 38_980),
                CLIP_WALK_90 to VoiceClip(39_030, 42_210),
                CLIP_WALK_120 to VoiceClip(42_220, 44_610),
                CLIP_WALK_150 to VoiceClip(44_730, 47_980),
                CLIP_WALK_180 to VoiceClip(48_140, 50_670),
                CLIP_WALK_300 to VoiceClip(50_790, 53_250),
                CLIP_COOLDOWN_180 to VoiceClip(53_400, 56_550),
                CLIP_FINISH_TEN_SECONDS to VoiceClip(56_590, 60_790),
                CLIP_FINISHED to VoiceClip(60_970, 63_180),
            ),
        )

        val FEMALE_PACK = VoicePackSpec(
            filename = "rakdatak_ar_female_v1.wav",
            url = "https://resource2.heygen.ai/text_to_speech/90f75bc626b143ae832bc35280491ae2/1776ddbd05374fa480e92f0297bbc67e/id=204d91d1-60d4-4948-a616-6697873228ce.wav",
            clips = mapOf(
                CLIP_WARMUP_180 to VoiceClip(220, 3_340),
                CLIP_TRANSITION to VoiceClip(3_400, 7_160),
                CLIP_ONE_MINUTE to VoiceClip(7_300, 9_780),
                CLIP_RUN_60 to VoiceClip(9_780, 12_390),
                CLIP_RUN_90 to VoiceClip(12_600, 15_720),
                CLIP_RUN_180 to VoiceClip(15_840, 18_250),
                CLIP_RUN_300 to VoiceClip(18_400, 20_710),
                CLIP_RUN_480 to VoiceClip(20_790, 23_100),
                CLIP_RUN_600 to VoiceClip(23_220, 25_480),
                CLIP_RUN_1200 to VoiceClip(25_600, 28_020),
                CLIP_RUN_1500 to VoiceClip(28_070, 30_910),
                CLIP_RUN_1680 to VoiceClip(31_010, 33_970),
                CLIP_RUN_1800 to VoiceClip(34_110, 36_510),
                CLIP_WALK_90 to VoiceClip(36_580, 39_360),
                CLIP_WALK_120 to VoiceClip(39_410, 41_510),
                CLIP_WALK_150 to VoiceClip(41_640, 44_830),
                CLIP_WALK_180 to VoiceClip(44_920, 47_020),
                CLIP_WALK_300 to VoiceClip(47_080, 49_380),
                CLIP_COOLDOWN_180 to VoiceClip(49_500, 52_320),
                CLIP_FINISH_TEN_SECONDS to VoiceClip(52_370, 56_170),
                CLIP_FINISHED to VoiceClip(56_300, 58_450),
            ),
        )
    }
}

@Composable
fun PhoneWorkoutCoachEffect(
    snapshot: WorkoutSessionSnapshot,
    soundCuesEnabled: Boolean,
    workoutVoice: WorkoutVoice,
    vibrationEnabled: Boolean,
    onPhaseInstructionCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPhaseInstructionCompleted)
    val coach = remember(context, workoutVoice) { PhoneWorkoutCoach(context, workoutVoice) }

    DisposableEffect(coach) {
        onDispose { coach.close() }
    }

    LaunchedEffect(
        snapshot.status,
        snapshot.phaseIndex,
        snapshot.phaseRemainingSeconds,
        soundCuesEnabled,
        workoutVoice,
        vibrationEnabled,
    ) {
        coach.onSnapshot(
            snapshot = snapshot,
            soundCuesEnabled = soundCuesEnabled,
            vibrationEnabled = vibrationEnabled,
            onPhaseInstructionCompleted = { callback() },
        )
    }
}

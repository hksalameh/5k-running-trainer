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
    val url: String? = null,
    val assetPath: String? = null,
    val clips: Map<String, VoiceClip>,
)

/**
 * Arabic workout coach.
 *
 * Primary path: an offline, bundled Arabic (Jordan) neural voice pack.
 * Fallback path: Android TTS if the selected natural pack is not ready.
 */
class PhoneWorkoutCoach(
    context: Context,
    private val workoutVoice: WorkoutVoice,
) : TextToSpeech.OnInitListener, AutoCloseable {
    private val appContext = context.applicationContext
    private val voicePack = if (workoutVoice == WorkoutVoice.ALI) ALI_PACK else CHARON_PACK
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
    @Volatile
    private var voicePackLoading = true
    private var pendingNaturalCue: (() -> Unit)? = null
    private var pendingFirstCue: String? = null
    private var pendingFirstCueFinished: (() -> Unit)? = null
    private var activeUtteranceId: String? = null
    private var activeUtteranceFinished: (() -> Unit)? = null
    private var announcingPhaseIndex = -1
    private var announcedWarmupSpeedIncrease = false
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
                dispatchUtteranceFinished(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                abandonAudioFocus()
                dispatchUtteranceFinished(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                abandonAudioFocus()
                dispatchUtteranceFinished(utteranceId)
            }
        })
        prepareNaturalVoicePack()
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            mainHandler.post {
                pendingFirstCue = null
                pendingFirstCueFinished?.also { finished ->
                    pendingFirstCueFinished = null
                    finished()
                }
            }
            return
        }
        ready = configureArabicVoice()
        if (ready) {
            pendingFirstCue?.let { pending ->
                pendingFirstCue = null
                val finished = pendingFirstCueFinished
                pendingFirstCueFinished = null
                speakWithTts(pending, finished)
            }
        } else {
            mainHandler.post {
                pendingFirstCue = null
                pendingFirstCueFinished?.also { finished ->
                    pendingFirstCueFinished = null
                    finished()
                }
            }
        }
    }

    private fun dispatchUtteranceFinished(utteranceId: String?) {
        mainHandler.post {
            if (utteranceId != null && utteranceId == activeUtteranceId) {
                activeUtteranceId = null
                val finished = activeUtteranceFinished
                activeUtteranceFinished = null
                finished?.invoke()
            }
        }
    }

    fun onSnapshot(
        snapshot: WorkoutSessionSnapshot,
        soundCuesEnabled: Boolean,
        vibrationEnabled: Boolean,
        onPhaseInstructionFinished: (Int) -> Unit,
    ) {
        if (snapshot.status == WorkoutSessionStatus.STOPPED) {
            cancelCuePlayback()
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

        if (snapshot.status == WorkoutSessionStatus.PAUSED ||
            snapshot.status == WorkoutSessionStatus.READY
        ) {
            cancelCuePlayback()
            return
        }

        if (
            needsSessionReset ||
            (snapshot.phaseIndex == 0 && snapshot.totalElapsedSeconds == 0 && lastPhaseIndex > 0)
        ) {
            resetSessionCues()
        }

        if (snapshot.status == WorkoutSessionStatus.ANNOUNCING) {
            if (announcingPhaseIndex == snapshot.phaseIndex) return
            announcingPhaseIndex = snapshot.phaseIndex
            val isFirstPhase = lastPhaseIndex == -1
            lastPhaseIndex = snapshot.phaseIndex
            oneMinuteCuePhase = -1
            tenSecondCuePhase = -1
            lastFinalBeepSecond = -1
            if (!isFirstPhase && vibrationEnabled) vibrateTransition()
            if (soundCuesEnabled) {
                val announcedPhase = snapshot.phaseIndex
                speakCue(
                    clipKey = clipKeyForPhase(snapshot.currentPhase),
                    fallbackText = phaseStartPrompt(snapshot.currentPhase),
                    onFinished = { onPhaseInstructionFinished(announcedPhase) },
                )
            } else {
                onPhaseInstructionFinished(snapshot.phaseIndex)
            }
            return
        }

        if (snapshot.status != WorkoutSessionStatus.RUNNING) return

        if (snapshot.currentPhase.type == WorkoutPhaseType.WARM_UP &&
            snapshot.phaseElapsedSeconds in 60..65 &&
            !announcedWarmupSpeedIncrease
        ) {
            announcedWarmupSpeedIncrease = true
            if (soundCuesEnabled) {
                speakCue(
                    clipKey = CLIP_WARMUP_SPEED_60,
                    fallbackText = "خلصت أول دقيقة إحماء. إذا جاهز، ارفع سرعة المشي لثلاثة أو أربعة حسب راحتك.",
                )
            }
        }

        if (snapshot.phaseIndex != lastPhaseIndex) {
            val isFirstPhase = lastPhaseIndex == -1
            lastPhaseIndex = snapshot.phaseIndex
            oneMinuteCuePhase = -1
            tenSecondCuePhase = -1
            lastFinalBeepSecond = -1

            if (!isFirstPhase && vibrationEnabled) vibrateTransition()

            // A recovered mid-phase workout does not replay a starting cue.
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
        onFinished: (() -> Unit)? = null,
    ) {
        // The APK contains the recorded cue. Do not play Android's robotic TTS
        // while the offline clip is still loading from the APK assets.
        if (clipKey != null && voicePack.assetPath != null && voicePackLoading) {
            pendingNaturalCue = { speakCue(clipKey, fallbackText, onFinished) }
            return
        }
        if (clipKey != null && playNaturalClip(clipKey, 0L, onFinished)) return
        speakWithTts(fallbackText, onFinished)
    }

    private fun playNaturalClip(
        key: String,
        delayMs: Long,
        onFinished: (() -> Unit)? = null,
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
                                onFinished?.invoke()
                            }
                        },
                        clip.durationMs + 80L,
                    )
                } catch (_: Throwable) {
                    // Future cues will fall back to Android TTS if local playback becomes unavailable.
                    onFinished?.invoke()
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
                    if (voicePack.assetPath != null) {
                        appContext.assets.open(voicePack.assetPath).use { source ->
                            voicePackFile.outputStream().use { target -> source.copyTo(target) }
                        }
                    } else {
                        downloadVoicePack()
                    }
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
                // Only a damaged/unreadable voice pack falls back to Android TTS.
            } finally {
                voicePackLoading = false
                mainHandler.post {
                    if (!closed) {
                        val pending = pendingNaturalCue
                        pendingNaturalCue = null
                        pending?.invoke()
                    }
                }
            }
        }
    }

    private fun downloadVoicePack() {
        val downloadUrl = voicePack.url ?: return
        val temporaryFile = File(appContext.filesDir, "${voicePack.filename}.tmp")
        runCatching { temporaryFile.delete() }

        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
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
        return localeScore + (voice.quality * 10) - voice.latency
    }

    private fun clipKeyForPhase(phase: WorkoutPhase): String? = when (phase.type) {
        WorkoutPhaseType.WARM_UP ->
            if (phase.durationSeconds == 300) CLIP_WARMUP_300 else null

        WorkoutPhaseType.COOL_DOWN ->
            if (phase.durationSeconds == 300) CLIP_COOLDOWN_300 else null

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

    private fun speakWithTts(text: String, onFinished: (() -> Unit)? = null) {
        if (!ready) {
            pendingFirstCue = text
            pendingFirstCueFinished = onFinished
            return
        }
        audioManager.requestAudioFocus(focusRequest)
        val utteranceId = "rakdatak-${utteranceCounter.incrementAndGet()}"
        activeUtteranceId = utteranceId
        activeUtteranceFinished = onFinished
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
            dispatchUtteranceFinished(utteranceId)
        }
    }

    private fun cancelCuePlayback() {
        if (announcingPhaseIndex < 0) return
        announcingPhaseIndex = -1
        pendingNaturalCue = null
        playbackToken += 1
        pendingFirstCue = null
        pendingFirstCueFinished = null
        activeUtteranceId = null
        activeUtteranceFinished = null
        tts.stop()
        runCatching { naturalPlayer?.pause() }
        abandonAudioFocus()
    }

    private fun resetSessionCues() {
        lastPhaseIndex = -1
        announcingPhaseIndex = -1
        announcedWarmupSpeedIncrease = false
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
    }

    private companion object {
        const val ALIGNMENT_LOOKAHEAD_SECONDS = 5

        const val CLIP_WARMUP_300 = "warmup_300"
        const val CLIP_WARMUP_SPEED_60 = "warmup_speed_60"
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
        const val CLIP_COOLDOWN_300 = "cooldown_300"
        const val CLIP_FINISH_TEN_SECONDS = "finish_10"
        const val CLIP_FINISHED = "finished"

        val CHARON_PACK = VoicePackSpec(
            filename = "rakdatak_ar_charon_v3.wav",
            assetPath = "voice_packs/rakdatak_ar_charon_v3.wav",
            clips = mapOf(
                "warmup_300" to VoiceClip(0, 9128),
                "warmup_speed_60" to VoiceClip(9328, 15126),
                "transition" to VoiceClip(15326, 18758),
                "one_minute" to VoiceClip(18958, 21275),
                "run_60" to VoiceClip(21475, 24381),
                "run_90" to VoiceClip(24581, 28198),
                "run_180" to VoiceClip(28398, 32029),
                "run_300" to VoiceClip(32229, 35918),
                "run_480" to VoiceClip(36118, 39596),
                "run_600" to VoiceClip(39796, 42861),
                "run_1200" to VoiceClip(43061, 46397),
                "run_1500" to VoiceClip(46597, 50378),
                "run_1680" to VoiceClip(50578, 54218),
                "run_1800" to VoiceClip(54418, 58970),
                "walk_90" to VoiceClip(59170, 62786),
                "walk_120" to VoiceClip(62986, 66450),
                "walk_150" to VoiceClip(66650, 70139),
                "walk_180" to VoiceClip(70339, 73969),
                "walk_300" to VoiceClip(74169, 77521),
                "cooldown_300" to VoiceClip(77721, 83375),
                "finish_10" to VoiceClip(83575, 86494),
                "finished" to VoiceClip(86694, 90601),
            ),
        )

        val ALI_PACK = VoicePackSpec(
            filename = "rakdatak_ar_ali_v3.wav",
            assetPath = "voice_packs/rakdatak_ar_ali_v3.wav",
            clips = mapOf(
                "warmup_300" to VoiceClip(0, 9079),
                "warmup_speed_60" to VoiceClip(9279, 15832),
                "transition" to VoiceClip(16032, 19057),
                "one_minute" to VoiceClip(19257, 21305),
                "run_60" to VoiceClip(21505, 24641),
                "run_90" to VoiceClip(24841, 28529),
                "run_180" to VoiceClip(28729, 32398),
                "run_300" to VoiceClip(32598, 36290),
                "run_480" to VoiceClip(36490, 40188),
                "run_600" to VoiceClip(40388, 44123),
                "run_1200" to VoiceClip(44323, 48151),
                "run_1500" to VoiceClip(48351, 52511),
                "run_1680" to VoiceClip(52711, 56898),
                "run_1800" to VoiceClip(57098, 61643),
                "walk_90" to VoiceClip(61843, 65537),
                "walk_120" to VoiceClip(65737, 69260),
                "walk_150" to VoiceClip(69460, 73307),
                "walk_180" to VoiceClip(73507, 77134),
                "walk_300" to VoiceClip(77334, 80977),
                "cooldown_300" to VoiceClip(81177, 87257),
                "finish_10" to VoiceClip(87457, 90334),
                "finished" to VoiceClip(90534, 94527),
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
    onPhaseInstructionFinished: (Int) -> Unit,
) {
    val context = LocalContext.current
    val coach = remember(context, workoutVoice) { PhoneWorkoutCoach(context, workoutVoice) }
    val updatedCueFinished = rememberUpdatedState(onPhaseInstructionFinished)

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
            onPhaseInstructionFinished = { index -> updatedCueFinished.value(index) },
        )
    }
}

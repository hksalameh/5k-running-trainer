package com.rakdatak.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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
import com.rakdatak.core.training.WorkoutCueTiming
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
    private val voicePack = if (workoutVoice == WorkoutVoice.DESPINA) DESPINA_PACK else CHARON_PACK
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
    private var preparedNextCuePhase = -1
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
            preparedNextCuePhase = -1
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
            preparedNextCuePhase = -1
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
            preparedNextCuePhase = -1

            if (!isFirstPhase && vibrationEnabled) vibrateTransition()

            // The phone clock runs continuously. Only the initial warm-up is
            // narrated at the start; all other phases were announced BEFORE
            // their boundaries during the previous phase.
            if (isFirstPhase && snapshot.phaseIndex == 0 &&
                snapshot.totalElapsedSeconds <= 1 && soundCuesEnabled
            ) {
                speakCue(
                    clipKey = clipKeyForPhase(snapshot.currentPhase),
                    fallbackText = phaseStartPrompt(snapshot.currentPhase),
                )
            }
        }

        val upcoming = snapshot.nextPhase
        if (upcoming != null && soundCuesEnabled &&
            preparedNextCuePhase != snapshot.phaseIndex
        ) {
            val key = clipKeyForPhase(upcoming)
            val duration = key?.let { voicePack.clips[it]?.durationMs }
            if (duration != null &&
                WorkoutCueTiming.shouldQueue(snapshot.phaseRemainingSeconds, duration)
            ) {
                preparedNextCuePhase = snapshot.phaseIndex
                val delayMs = WorkoutCueTiming.delayUntilCue(
                    snapshot.phaseRemainingSeconds, duration,
                )
                // The runner keeps moving in the current phase while hearing
                // "المرحلة الجاية ... بلّش". The final word lands at the
                // next phase boundary, with no pause or countdown horn.
                if (!playNaturalClip(key!!, delayMs)) {
                    speakCue(key, phaseStartPrompt(upcoming))
                }
            } else if (key == null &&
                snapshot.phaseRemainingSeconds == 6
            ) {
                preparedNextCuePhase = snapshot.phaseIndex
                speakWithTts(phaseStartPrompt(upcoming))
            }
        }
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
            120 -> CLIP_RUN_120
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
            WorkoutPhaseType.WALK -> "المرحلة الجايّة مشي لمدة $duration، بلّش."
            WorkoutPhaseType.RUN -> "المرحلة الجايّة ركض لمدة $duration، بلّش."
            WorkoutPhaseType.COOL_DOWN -> "المرحلة الجايّة تهدئة بالمشي لمدة $duration، بلّش."
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
        preparedNextCuePhase = -1
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
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val CLIP_WARMUP_300 = "warmup_300"
        const val CLIP_WARMUP_SPEED_60 = "warmup_speed_60"
        const val CLIP_RUN_60 = "run_60"
        const val CLIP_RUN_90 = "run_90"
        const val CLIP_RUN_120 = "run_120"
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
        const val CLIP_FINISHED = "finished"

        val CHARON_PACK = VoicePackSpec(
            filename = "rakdatak_ar_charon_v4.wav",
            assetPath = "voice_packs/rakdatak_ar_charon_v4.wav",
            clips = mapOf(
                "warmup_300" to VoiceClip(0, 7077),
                "warmup_speed_60" to VoiceClip(7277, 12093),
                "run_60" to VoiceClip(12293, 15701),
                "run_90" to VoiceClip(15901, 19022),
                "run_120" to VoiceClip(19222, 22836),
                "run_180" to VoiceClip(23036, 26700),
                "run_300" to VoiceClip(26900, 30730),
                "run_480" to VoiceClip(30930, 34780),
                "run_600" to VoiceClip(34980, 38457),
                "run_1200" to VoiceClip(38657, 42391),
                "run_1500" to VoiceClip(42591, 46948),
                "run_1680" to VoiceClip(47148, 51358),
                "run_1800" to VoiceClip(51558, 56146),
                "walk_90" to VoiceClip(56346, 59806),
                "walk_120" to VoiceClip(60006, 63311),
                "walk_150" to VoiceClip(63511, 66907),
                "walk_180" to VoiceClip(67107, 71426),
                "walk_300" to VoiceClip(71626, 75001),
                "cooldown_300" to VoiceClip(75201, 79435),
                "finished" to VoiceClip(79635, 83058),
            ),
        )

        val DESPINA_PACK = VoicePackSpec(
            filename = "rakdatak_ar_despina_v4.wav",
            assetPath = "voice_packs/rakdatak_ar_despina_v4.wav",
            clips = mapOf(
                "warmup_300" to VoiceClip(0, 7004),
                "warmup_speed_60" to VoiceClip(7204, 12139),
                "run_60" to VoiceClip(12339, 15236),
                "run_90" to VoiceClip(15436, 18841),
                "run_120" to VoiceClip(19041, 22313),
                "run_180" to VoiceClip(22513, 26370),
                "run_300" to VoiceClip(26570, 29858),
                "run_480" to VoiceClip(30058, 33378),
                "run_600" to VoiceClip(33578, 37074),
                "run_1200" to VoiceClip(37274, 41266),
                "run_1500" to VoiceClip(41466, 45333),
                "run_1680" to VoiceClip(45533, 49594),
                "run_1800" to VoiceClip(49794, 53934),
                "walk_90" to VoiceClip(54134, 57826),
                "walk_120" to VoiceClip(58026, 61579),
                "walk_150" to VoiceClip(61779, 65454),
                "walk_180" to VoiceClip(65654, 69743),
                "walk_300" to VoiceClip(69943, 73801),
                "cooldown_300" to VoiceClip(74001, 77896),
                "finished" to VoiceClip(78096, 81334),
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

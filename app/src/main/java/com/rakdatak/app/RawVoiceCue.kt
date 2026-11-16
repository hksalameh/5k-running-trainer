package com.rakdatak.app

import com.rakdatak.app.settings.WorkoutVoice

/**
 * Compile-time mapping guarantees all 40 cue files stay inside the APK.
 * Do not look up by runtime package name: debug preview applicationId differs
 * from the Android resource namespace.
 */
internal fun rawVoiceCue(voice: WorkoutVoice, key: String): Int? =
    when (voice) {
        WorkoutVoice.CHARON -> when (key) {
            "warmup_300" -> R.raw.rk_charon_warmup_300
            "warmup_speed_60" -> R.raw.rk_charon_warmup_speed_60
            "run_60" -> R.raw.rk_charon_run_60
            "run_90" -> R.raw.rk_charon_run_90
            "run_120" -> R.raw.rk_charon_run_120
            "run_180" -> R.raw.rk_charon_run_180
            "run_300" -> R.raw.rk_charon_run_300
            "run_480" -> R.raw.rk_charon_run_480
            "run_600" -> R.raw.rk_charon_run_600
            "run_1200" -> R.raw.rk_charon_run_1200
            "run_1500" -> R.raw.rk_charon_run_1500
            "run_1680" -> R.raw.rk_charon_run_1680
            "run_1800" -> R.raw.rk_charon_run_1800
            "walk_90" -> R.raw.rk_charon_walk_90
            "walk_120" -> R.raw.rk_charon_walk_120
            "walk_150" -> R.raw.rk_charon_walk_150
            "walk_180" -> R.raw.rk_charon_walk_180
            "walk_300" -> R.raw.rk_charon_walk_300
            "cooldown_300" -> R.raw.rk_charon_cooldown_300
            "finished" -> R.raw.rk_charon_finished
            else -> null
        }
        WorkoutVoice.DESPINA -> when (key) {
            "warmup_300" -> R.raw.rk_despina_warmup_300
            "warmup_speed_60" -> R.raw.rk_despina_warmup_speed_60
            "run_60" -> R.raw.rk_despina_run_60
            "run_90" -> R.raw.rk_despina_run_90
            "run_120" -> R.raw.rk_despina_run_120
            "run_180" -> R.raw.rk_despina_run_180
            "run_300" -> R.raw.rk_despina_run_300
            "run_480" -> R.raw.rk_despina_run_480
            "run_600" -> R.raw.rk_despina_run_600
            "run_1200" -> R.raw.rk_despina_run_1200
            "run_1500" -> R.raw.rk_despina_run_1500
            "run_1680" -> R.raw.rk_despina_run_1680
            "run_1800" -> R.raw.rk_despina_run_1800
            "walk_90" -> R.raw.rk_despina_walk_90
            "walk_120" -> R.raw.rk_despina_walk_120
            "walk_150" -> R.raw.rk_despina_walk_150
            "walk_180" -> R.raw.rk_despina_walk_180
            "walk_300" -> R.raw.rk_despina_walk_300
            "cooldown_300" -> R.raw.rk_despina_cooldown_300
            "finished" -> R.raw.rk_despina_finished
            else -> null
        }
    }

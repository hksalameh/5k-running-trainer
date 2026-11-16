package com.rakdatak.app

import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rakdatak.app.settings.WorkoutVoice
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordedVoicePlaybackTest {
    private val keys = listOf(
        "warmup_300", "warmup_speed_60", "run_60", "run_90", "run_120",
        "run_180", "run_300", "run_480", "run_600", "run_1200",
        "run_1500", "run_1680", "run_1800", "walk_90", "walk_120",
        "walk_150", "walk_180", "walk_300", "cooldown_300", "finished",
    )

    @Test
    fun bothVoicesHaveTwentyDecodableAndAudiblyPlayableResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (voice in listOf(WorkoutVoice.CHARON, WorkoutVoice.DESPINA)) {
            for (key in keys) {
                val resourceId = rawVoiceCue(voice, key)
                assertNotNull("Missing resource for $voice/$key", resourceId)
                val player = MediaPlayer.create(context, resourceId!!)
                assertNotNull("Cannot decode MP3 for $voice/$key", player)
                try {
                    assertTrue("Invalid duration for $voice/$key", player!!.duration > 500)
                    if (key == "warmup_300") {
                        player.start()
                        Thread.sleep(400)
                        assertTrue("MediaPlayer not playing $voice", player.isPlaying)
                    }
                } finally {
                    player?.release()
                }
            }
        }
    }
}

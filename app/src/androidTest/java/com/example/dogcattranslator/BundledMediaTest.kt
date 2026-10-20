package com.example.dogcattranslator

import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dogcattranslator.data.model.AnimalType
import com.example.dogcattranslator.data.repository.DemoMediaRepository
import com.example.dogcattranslator.media.PlaybackStatus
import com.example.dogcattranslator.media.SoundPlayerManager
import com.example.dogcattranslator.media.rawMediaUri
import com.example.dogcattranslator.ui.common.AnimalPosterView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledMediaTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun everyBundledSoundPlaysToCompletionOnAndroid() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            lateinit var player: SoundPlayerManager
            instrumentation.runOnMainSync {
                player = SoundPlayerManager(instrumentation.targetContext, scope)
            }
            try {
                val sounds = AnimalType.entries.flatMap(DemoMediaRepository::sounds)
                for (sound in sounds) {
                    val resourceId =
                        requireNotNull(sound.audioResId) { "Missing audio: ${sound.id}" }
                    instrumentation.runOnMainSync {
                        player.prepare(rawMediaUri(instrumentation.targetContext, resourceId))
                    }
                    awaitCondition {
                        player.state.value.status == PlaybackStatus.READY ||
                            player.state.value.status == PlaybackStatus.ERROR
                    }
                    assertEquals(sound.id, PlaybackStatus.READY, player.state.value.status)
                    assertTrue("Empty audio: ${sound.id}", player.state.value.duration > 0)
                    // Preparing reads metadata; playing the entire clip exercises the decoder too.
                    val timeoutMs = player.state.value.duration.toLong() + 5000
                    instrumentation.runOnMainSync { player.play() }
                    assertEquals(sound.id, PlaybackStatus.PLAYING, player.state.value.status)
                    awaitCondition(timeoutMs) {
                        player.state.value.status == PlaybackStatus.COMPLETED ||
                            player.state.value.status == PlaybackStatus.ERROR
                    }
                    assertEquals(sound.id, PlaybackStatus.COMPLETED, player.state.value.status)
                }
            } finally {
                instrumentation.runOnMainSync { player.release() }
                scope.cancel()
            }
        }
    }

    @Test
    fun soundAndCallPostersHaveAtLeast512PixelsOnEachSide() {
        instrumentation.runOnMainSync {
            val view = AnimalPosterView(instrumentation.targetContext)
            val soundKeys =
                AnimalType.entries.flatMap(DemoMediaRepository::sounds).map { it.posterKey }
            val animalKeys = DemoMediaRepository.animals.map { it.posterKey }
            for (key in (soundKeys + animalKeys + animalKeys.map { "${it}_call" }).distinct()) {
                view.show(key)
                val bitmap = (view.drawable as BitmapDrawable).bitmap
                assertTrue(
                    "Low-resolution poster: $key",
                    bitmap.width >= 512 && bitmap.height >= 512,
                )
                assertTrue(
                    "Empty poster: $key",
                    bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) != 0,
                )
            }
        }
    }
}

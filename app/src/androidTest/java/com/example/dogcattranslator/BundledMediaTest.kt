package com.example.dogcattranslator

import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: AnimalPosterView
            scenario.onActivity { activity ->
                view = AnimalPosterView(activity)
                activity.findViewById<ViewGroup>(android.R.id.content)
                    .addView(view, FrameLayout.LayoutParams(512, 512))
            }
            val soundKeys =
                AnimalType.entries.flatMap(DemoMediaRepository::sounds).map { it.posterKey }
            val animalKeys = DemoMediaRepository.animals.map { it.posterKey }
            for (key in (soundKeys + animalKeys + animalKeys.map { "${it}_call" }).distinct()) {
                instrumentation.runOnMainSync { view.show(key) }
                awaitCondition {
                    var loaded = false
                    instrumentation.runOnMainSync { loaded = view.drawable is BitmapDrawable }
                    loaded
                }
                instrumentation.runOnMainSync {
                    val bitmap = (view.drawable as BitmapDrawable).bitmap
                    assertTrue("Low-resolution poster: $key", bitmap.width >= 512 && bitmap.height >= 512)
                    assertTrue("Empty poster: $key", bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) != 0)
                    assertTrue("Oversized poster: $key", bitmap.allocationByteCount <= 2 * 1024 * 1024)
                }
            }
        }
    }

    @Test
    fun smallPostersAreSampledAndRapidRebindingKeepsTheLatestImage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: AnimalPosterView
            lateinit var expected: AnimalPosterView
            lateinit var parent: ViewGroup
            scenario.onActivity { activity ->
                parent = activity.findViewById(android.R.id.content)
                view = AnimalPosterView(activity)
                expected = AnimalPosterView(activity)
                parent.addView(view, FrameLayout.LayoutParams(180, 180))
                parent.addView(expected, FrameLayout.LayoutParams(180, 180))
                view.show("husky")
                view.show("persian")
                expected.show("persian")
            }
            awaitCondition {
                var loaded = false
                instrumentation.runOnMainSync {
                    loaded = view.drawable is BitmapDrawable && expected.drawable is BitmapDrawable
                }
                loaded
            }
            instrumentation.runOnMainSync {
                val bitmap = (view.drawable as BitmapDrawable).bitmap
                assertTrue(bitmap.sameAs((expected.drawable as BitmapDrawable).bitmap))
                assertTrue(bitmap.width >= 180 && bitmap.height >= 180)
                assertTrue(bitmap.allocationByteCount < 512 * 1024)
                parent.removeView(view)
                assertEquals(null, view.drawable)
            }
        }
    }
}

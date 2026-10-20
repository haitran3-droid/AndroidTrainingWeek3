package com.example.dogcattranslator

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.dogcattranslator.data.repository.DemoMediaRepository
import com.example.dogcattranslator.databinding.FragmentInCallBinding
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class CallVideoTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun everyContactPlaysItsLocalVideoWithCameraMuteAndLooping() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.fakeCallFragment)).perform(click())
            val resources =
                DemoMediaRepository.animals.map { animal ->
                    requireNotNull(animal.videoResId) { "Missing video: ${animal.id}" }
                }
            assertEquals("Each contact needs its own clip", 4, resources.distinct().size)
            for (animal in DemoMediaRepository.animals) {
                onView(withContentDescription("Start a call: ${animal.name}")).perform(click())
                onView(withId(R.id.accept_button)).perform(click())
                lateinit var player: ExoPlayer
                awaitCondition(10000) {
                    var ready = false
                    scenario.onActivity {
                        val ui = callUi(it)
                        player = ui.playerView.player as ExoPlayer
                        ready =
                            player.isPlaying &&
                                (player.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 &&
                                ui.previewView.previewStreamState.value ==
                                    PreviewView.StreamState.STREAMING
                    }
                    ready
                }
                onView(withId(R.id.status)).check(matches(withText("")))
                scenario.onActivity {
                    assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
                    assertEquals(
                        animal.videoResId.toString(),
                        player.currentMediaItem!!.localConfiguration!!.uri.lastPathSegment,
                    )
                    assertTrue(player.duration > 1000)
                }
                onView(withId(R.id.mic_button)).perform(click())
                scenario.onActivity { assertEquals(0f, player.volume, 0f) }
                onView(withId(R.id.mic_button)).perform(click())
                scenario.onActivity { assertEquals(1f, player.volume, 0f) }
                var duration = 0L
                scenario.onActivity {
                    duration = player.duration
                    player.seekTo(duration - 250)
                }
                awaitCondition {
                    var looped = false
                    scenario.onActivity { looped = player.currentPosition < duration - 500 }
                    looped
                }
                onView(withId(R.id.end_button)).perform(click())
                onView(withId(R.id.animals)).check(matches(isDisplayed()))
                scenario.onActivity { assertFalse(player.isPlaying) }
            }
        }
    }

    private fun callUi(activity: MainActivity): FragmentInCallBinding {
        val host =
            activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        return FragmentInCallBinding.bind(
            host.childFragmentManager.primaryNavigationFragment!!.requireView(),
        )
    }
}

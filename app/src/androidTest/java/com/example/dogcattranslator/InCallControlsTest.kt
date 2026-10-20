package com.example.dogcattranslator

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ImageButton
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.dogcattranslator.databinding.FragmentInCallBinding
import com.example.dogcattranslator.ui.fakecall.FakeCallViewModel
import com.example.dogcattranslator.ui.fakecall.InCallFragment
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InCallControlsTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun clockStartsBeforeTheVideoPlayerIsCreated() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var clockStartedBeforeVideo = false
            scenario.onActivity { activity ->
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                    object : FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentViewCreated(
                            fm: FragmentManager,
                            fragment: Fragment,
                            view: View,
                            savedInstanceState: Bundle?,
                        ) {
                            if (fragment !is InCallFragment) return
                            // Pause before onStart can create ExoPlayer or deliver READY.
                            SystemClock.sleep(25)
                            val model = ViewModelProvider(fragment)[FakeCallViewModel::class.java]
                            clockStartedBeforeVideo =
                                model.elapsedMs() > 0 &&
                                    FragmentInCallBinding.bind(view).playerView.player == null
                        }
                    },
                    true,
                )
            }
            acceptCall()
            scenario.onActivity {
                assertTrue("Clock must not wait for video readiness", clockStartedBeforeVideo)
            }
        }
    }

    @Test
    fun timerRunsFromAcceptAndDoesNotResetOnRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            acceptCall()
            var beforeRecreation = 0
            awaitCondition {
                scenario.onActivity { beforeRecreation = timerSeconds(callUi(it).timer.text) }
                beforeRecreation >= 2
            }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(timerSeconds(callUi(it).timer.text) >= beforeRecreation)
            }
            awaitCondition {
                var elapsed = 0
                scenario.onActivity { elapsed = timerSeconds(callUi(it).timer.text) }
                elapsed > beforeRecreation
            }
        }
    }

    @Test
    fun cameraOffChangesTheActualIconAndSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            acceptCall()
            lateinit var onIcon: Bitmap
            lateinit var offIcon: Bitmap
            scenario.onActivity { onIcon = iconBitmap(callUi(it).cameraButton) }
            onView(withId(R.id.camera_button)).perform(click())
            scenario.onActivity {
                offIcon = iconBitmap(callUi(it).cameraButton)
                assertFalse("Camera off must change the rendered icon", onIcon.sameAs(offIcon))
            }
            onView(withId(R.id.camera_placeholder)).check(matches(withText(R.string.camera_off)))
            onView(withId(R.id.camera_button))
                .check(matches(withContentDescription("Turn camera on")))
            awaitCondition {
                var stopped = false
                scenario.onActivity {
                    val preview = callUi(it).previewView
                    stopped =
                        preview.visibility == View.GONE &&
                            preview.previewStreamState.value == PreviewView.StreamState.IDLE
                }
                stopped
            }
            scenario.recreate()
            scenario.onActivity {
                val ui = callUi(it)
                assertTrue(offIcon.sameAs(iconBitmap(ui.cameraButton)))
                assertEquals(View.GONE, ui.previewView.visibility)
                assertEquals(PreviewView.StreamState.IDLE, ui.previewView.previewStreamState.value)
            }
            onView(withId(R.id.camera_button)).perform(click())
            scenario.onActivity { assertTrue(onIcon.sameAs(iconBitmap(callUi(it).cameraButton))) }
            onView(withId(R.id.camera_button))
                .check(matches(withContentDescription("Turn camera off")))
        }
    }

    @Test
    fun muteChangesTheIconAndKeepsItOnRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            acceptCall()
            onView(withId(R.id.mic_button)).check(matches(isEnabled()))
            lateinit var onIcon: Bitmap
            lateinit var offIcon: Bitmap
            scenario.onActivity { onIcon = iconBitmap(callUi(it).micButton) }
            onView(withId(R.id.mic_button)).perform(click())
            onView(withId(R.id.mic_button))
                .check(matches(withContentDescription(R.string.video_muted)))
            scenario.onActivity {
                offIcon = iconBitmap(callUi(it).micButton)
                assertFalse("Mute must change the rendered icon", onIcon.sameAs(offIcon))
            }
            scenario.recreate()
            scenario.onActivity {
                val ui = callUi(it)
                assertTrue(offIcon.sameAs(iconBitmap(ui.micButton)))
                assertEquals(0f, ui.playerView.player!!.volume, 0f)
            }
            onView(withId(R.id.mic_button))
                .check(matches(withContentDescription(R.string.video_muted)))
            onView(withId(R.id.mic_button)).perform(click())
            scenario.onActivity { assertTrue(onIcon.sameAs(iconBitmap(callUi(it).micButton))) }
            onView(withId(R.id.mic_button))
                .check(matches(withContentDescription(R.string.video_unmuted)))
        }
    }

    private fun acceptCall() {
        onView(withId(R.id.fakeCallFragment)).perform(click())
        onView(withContentDescription("Start a call: Husky Ngao")).perform(click())
        onView(withId(R.id.accept_button)).perform(click())
    }

    private fun callUi(activity: MainActivity): FragmentInCallBinding {
        val host =
            activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val call = host.childFragmentManager.primaryNavigationFragment!!
        return FragmentInCallBinding.bind(call.requireView())
    }

    private fun timerSeconds(text: CharSequence): Int {
        val parts = text.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun iconBitmap(button: ImageButton): Bitmap {
        val icon = button.drawable.constantState!!.newDrawable(button.resources).mutate()
        icon.state = button.drawable.state
        return Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            icon.setBounds(0, 0, bitmap.width, bitmap.height)
            icon.draw(Canvas(bitmap))
        }
    }
}

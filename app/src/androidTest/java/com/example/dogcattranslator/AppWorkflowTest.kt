package com.example.dogcattranslator

import android.Manifest
import android.os.SystemClock
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppWorkflowTest {
    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    @Test
    fun toolbarBackReturnsToHomeWithoutAddingAnotherHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(2) {
                onView(withId(R.id.soundFragment)).perform(click())
                onView(withContentDescription(R.string.back)).perform(click())
                onView(withId(R.id.record_button)).check(matches(isDisplayed()))
            }
            scenario.onActivity { activity ->
                val host =
                    activity.supportFragmentManager.findFragmentById(R.id.nav_host)
                        as NavHostFragment
                assertNull(host.navController.previousBackStackEntry)
            }
            pressBackUnconditionally()
            // Root Back may finish the activity or background its task (Android 12+).
            // The back-stack assertion above still catches an accidental return to Sound.
            awaitCondition {
                val state = scenario.state
                state == Lifecycle.State.CREATED || state == Lifecycle.State.DESTROYED
            }
        }
    }

    @Test
    fun soundSelectionAndRootTabsSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.soundFragment)).perform(click())
            onView(withText("Dog - Angry")).perform(click())
            onView(allOf(withId(R.id.poster), withContentDescription("Dog - Angry")))
                .check(matches(isDisplayed()))
            awaitCondition {
                var ready = false
                scenario.onActivity { activity ->
                    ready =
                        currentFragment(activity)
                            .view
                            ?.findViewById<android.widget.ImageButton>(R.id.play_button)
                            ?.isEnabled == true
                }
                ready
            }
            onView(withId(R.id.play_button)).check(matches(isEnabled()))
            onView(withId(R.id.status)).check(matches(withText("")))
            scenario.recreate()
            onView(allOf(withId(R.id.poster), withContentDescription("Dog - Angry")))
                .check(matches(isDisplayed()))
            onView(withId(R.id.trainingFragment)).perform(click())
            onView(withText(R.string.coming_soon)).check(matches(isDisplayed()))
            onView(withId(R.id.homeFragment)).perform(click())
            onView(withId(R.id.record_button)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun soundShowsBothSpeciesAndPlaysCatsWithoutChangingTranslator() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.soundFragment)).perform(click())
            scenario.onActivity { activity ->
                val list =
                    currentFragment(activity)
                        .requireView()
                        .findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sounds)
                assertEquals(9, list.adapter!!.itemCount)
            }
            onView(withText("Dog - Begging")).check(matches(isDisplayed()))
            for (label in listOf("Cat - Sad", "Cat - Happy", "Cat - Angry")) {
                onView(withText(label)).perform(scrollTo(), click())
                onView(allOf(withId(R.id.poster), withContentDescription(label)))
                    .check(matches(withContentDescription(label)))
                awaitCondition {
                    var ready = false
                    scenario.onActivity { activity ->
                        ready =
                            currentFragment(activity)
                                .requireView()
                                .findViewById<android.widget.ImageButton>(R.id.play_button)
                                .isEnabled
                    }
                    ready
                }
                onView(withId(R.id.play_button)).perform(scrollTo(), click())
                onView(withId(R.id.play_button))
                    .check(matches(withContentDescription(R.string.pause)))
                onView(withId(R.id.status)).check(matches(withText("")))
            }
            scenario.recreate()
            onView(allOf(withId(R.id.poster), withContentDescription("Cat - Angry")))
                .check(matches(isDisplayed()))
            onView(withId(R.id.homeFragment)).perform(click())
            onView(withId(R.id.animal_selector)).check(matches(withText(R.string.dog)))
        }
    }

    @Test
    fun callUsesCameraStreamAndCameraToggleSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.fakeCallFragment)).perform(click())
            onView(withContentDescription("Start a call: Husky Ngao")).perform(click())
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .wait(Until.findObject(By.res("android", "ok")), 2000)
                ?.click()
            onView(withId(R.id.bottom_nav)).check(matches(not(isDisplayed())))
            onView(withId(R.id.decline_button)).perform(click())
            onView(withId(R.id.animals)).check(matches(isDisplayed()))
            onView(withContentDescription("Start a call: Persian Longhair")).perform(click())
            onView(withId(R.id.name)).check(matches(withText("Persian Longhair")))
            onView(withId(R.id.accept_button)).perform(click())
            awaitCamera(scenario)
            awaitCondition {
                var ready = false
                scenario.onActivity { activity ->
                    ready =
                        currentFragment(activity)
                            .requireView()
                            .findViewById<android.widget.TextView>(R.id.status)
                            .text
                            .isEmpty()
                }
                ready
            }
            onView(withId(R.id.status)).check(matches(withText("")))
            onView(withId(R.id.camera_button)).perform(click())
            onView(withId(R.id.camera_placeholder)).check(matches(withText(R.string.camera_off)))
            scenario.recreate()
            onView(withId(R.id.camera_placeholder)).check(matches(withText(R.string.camera_off)))
            onView(withId(R.id.camera_button)).perform(click())
            awaitCamera(scenario)
            onView(withId(R.id.end_button)).perform(click())
            onView(withId(R.id.animals)).check(matches(isDisplayed()))
            onView(withContentDescription("Start a call: Husky Ngao")).perform(click())
            onView(withId(R.id.accept_button)).perform(click())
            awaitCamera(scenario)
            pressBack()
            onView(withId(R.id.animals)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun leavingHomeDuringRecordingStopsAndCleansCache() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.record_button)).perform(click())
            awaitCondition {
                var recording = false
                scenario.onActivity { activity ->
                    recording =
                        currentFragment(activity)
                            .view
                            ?.findViewById<android.widget.ImageButton>(R.id.record_button)
                            ?.contentDescription == activity.getString(R.string.stop_recording)
                }
                recording
            }
            SystemClock.sleep(400)
            onView(withId(R.id.soundFragment)).perform(click())
            awaitCondition {
                var clean = false
                scenario.onActivity {
                    clean =
                        it.cacheDir.listFiles()?.none { file -> file.name.startsWith("voice_") }
                            ?: true
                }
                clean
            }
            onView(withId(R.id.homeFragment)).perform(click())
            onView(withId(R.id.status)).check(matches(withText(R.string.record_hint)))
        }
    }

    private fun awaitCamera(scenario: ActivityScenario<MainActivity>) =
        awaitCondition(10000) {
            var streaming = false
            scenario.onActivity { activity ->
                streaming =
                    currentFragment(activity)
                        .view
                        ?.findViewById<PreviewView>(R.id.preview_view)
                        ?.previewStreamState
                        ?.value == PreviewView.StreamState.STREAMING
            }
            streaming
        }

    private fun currentFragment(activity: MainActivity): Fragment =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment)
            .childFragmentManager
            .primaryNavigationFragment!!
}

internal fun awaitCondition(timeoutMs: Long = 5000, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (condition()) return
        SystemClock.sleep(50)
    }
    assertTrue("Condition not met within $timeoutMs ms", condition())
}

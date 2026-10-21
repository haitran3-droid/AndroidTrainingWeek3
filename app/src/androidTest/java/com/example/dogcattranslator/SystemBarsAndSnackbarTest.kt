package com.example.dogcattranslator

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dogcattranslator.ui.common.appSnackbar
import com.google.android.material.snackbar.Snackbar
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemBarsAndSnackbarTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun systemIconsFollowScreenBackgroundInBothDeviceThemes() {
        val originalMode = AppCompatDelegate.getDefaultNightMode()
        try {
            for (mode in listOf(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_NO)) {
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        val root = activity.findViewById<View>(R.id.root)
                        val controller = WindowCompat.getInsetsController(activity.window, root)
                        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host)
                            as NavHostFragment
                        assertTrue(controller.isAppearanceLightStatusBars)
                        if (Build.VERSION.SDK_INT >= 26) assertTrue(controller.isAppearanceLightNavigationBars)
                        host.navController.navigate(R.id.incomingCallFragment)
                        assertFalse(controller.isAppearanceLightStatusBars)
                        if (Build.VERSION.SDK_INT >= 26) assertFalse(controller.isAppearanceLightNavigationBars)
                        assertEquals(View.GONE, activity.findViewById<View>(R.id.bottom_nav).visibility)
                        host.navController.popBackStack()
                        assertTrue(controller.isAppearanceLightStatusBars)
                        if (Build.VERSION.SDK_INT >= 26) assertTrue(controller.isAppearanceLightNavigationBars)
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(originalMode) }
        }
    }

    @Test
    fun permissionSnackbarIsAboveVisibleTabs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var snackbar: Snackbar
            lateinit var bottomNav: View
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host)
                    as NavHostFragment
                val fragment = requireNotNull(host.childFragmentManager.primaryNavigationFragment)
                bottomNav = activity.findViewById(R.id.bottom_nav)
                snackbar = requireNotNull(fragment.appSnackbar(R.string.microphone_denied))
                assertSame(bottomNav, snackbar.anchorView)
                snackbar.duration = Snackbar.LENGTH_INDEFINITE
                snackbar.show()
            }
            try {
                awaitCondition {
                    var clear = false
                    instrumentation.runOnMainSync {
                        val messageBounds = Rect()
                        val tabBounds = Rect()
                        clear = snackbar.isShown &&
                            snackbar.view.getGlobalVisibleRect(messageBounds) &&
                            bottomNav.getGlobalVisibleRect(tabBounds) &&
                            messageBounds.bottom <= tabBounds.top
                    }
                    clear
                }
            } finally {
                instrumentation.runOnMainSync { snackbar.dismiss() }
            }
        }
    }
}

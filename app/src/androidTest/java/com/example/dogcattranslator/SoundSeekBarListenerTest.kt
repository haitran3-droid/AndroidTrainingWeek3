package com.example.dogcattranslator

import android.widget.SeekBar
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dogcattranslator.ui.sound.SoundSeekBarListener
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundSeekBarListenerTest {
    @Test
    fun keyboardAndAccessibilityChangesSeekWithoutTouchGesture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val seekBar = SeekBar(instrumentation.targetContext)
            val positions = mutableListOf<Int>()
            val listener = SoundSeekBarListener { positions.add(it) }

            listener.onProgressChanged(seekBar, 1200, true)

            assertEquals(listOf(1200), positions)
        }
    }

    @Test
    fun playerUpdatesAreIgnoredAndTouchDragSeeksOnlyOnRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val seekBar = SeekBar(instrumentation.targetContext)
            seekBar.max = 4000
            val positions = mutableListOf<Int>()
            val listener = SoundSeekBarListener { positions.add(it) }

            listener.onProgressChanged(seekBar, 500, false)
            listener.onStartTrackingTouch(seekBar)
            listener.onProgressChanged(seekBar, 1000, true)
            listener.onProgressChanged(seekBar, 2000, true)
            assertEquals(emptyList<Int>(), positions)

            seekBar.progress = 2000
            listener.onStopTrackingTouch(seekBar)
            assertEquals(listOf(2000), positions)
        }
    }
}

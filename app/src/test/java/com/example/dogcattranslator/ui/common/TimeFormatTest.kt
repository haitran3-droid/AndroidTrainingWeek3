package com.example.dogcattranslator.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun timeUsesElapsedWholeSecondsAndDoesNotWrapAtOneHour() {
        assertEquals("00:00", formatElapsed(-1))
        assertEquals("00:15", formatElapsed(15999))
        assertEquals("01:00", formatElapsed(60000))
        assertEquals("60:00", formatElapsed(3600000))
    }
}

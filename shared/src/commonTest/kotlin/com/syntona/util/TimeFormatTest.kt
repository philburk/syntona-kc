package com.syntona.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {

    @Test
    fun testFormatsZero() {
        assertEquals("00:00.000", formatMinutesSecondsMillis(0.0))
    }

    @Test
    fun testPadsAllFields() {
        assertEquals("00:01.000", formatMinutesSecondsMillis(1.0))
        assertEquals("00:01.500", formatMinutesSecondsMillis(1.5))
        assertEquals("00:00.050", formatMinutesSecondsMillis(0.05))
        assertEquals("00:00.005", formatMinutesSecondsMillis(0.005))
    }

    @Test
    fun testRollsOverIntoMinutes() {
        assertEquals("00:59.999", formatMinutesSecondsMillis(59.999))
        assertEquals("01:00.000", formatMinutesSecondsMillis(60.0))
        assertEquals("01:23.456", formatMinutesSecondsMillis(83.456))
        assertEquals("10:00.000", formatMinutesSecondsMillis(600.0))
    }

    @Test
    fun testDoesNotWrapMinutesAtAnHour() {
        // A long recording should keep counting minutes rather than needing an hours field.
        assertEquals("75:00.000", formatMinutesSecondsMillis(4500.0))
    }

    @Test
    fun testClampsNegativeAndHandlesNaN() {
        assertEquals("00:00.000", formatMinutesSecondsMillis(-1.0))
        assertEquals("00:00.000", formatMinutesSecondsMillis(Double.NaN))
    }
}

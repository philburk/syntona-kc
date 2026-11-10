package com.syntona.dataflow.editor

import androidx.compose.ui.graphics.Color
import com.syntona.dataflow.model.ExponentialTaper
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RotaryKnobThemeTest {

    @Test
    fun lightModeHasBrightKnobArcColor() {
        val lightArc = LightGraphColors.knobArc
        val darkArc = DarkGraphColors.knobArc

        // Light mode should have a bright, vibrant arc distinct from dark mode
        assertNotEquals(lightArc, darkArc)
        assertEquals(Color(0xFFFF9500), lightArc)
        assertEquals(Color(0xFFE6A820), darkArc)

        // Light mode arc has full red channel and saturated orange hue
        assertEquals(1.0f, lightArc.red)
        assertTrue(lightArc.green > 0.5f)
        assertEquals(0.0f, lightArc.blue)
    }

    @Test
    fun zeroCrossingRangeCentersAtZero() {
        // Pan knob range: -1.0 .. 1.0
        val min = -1.0
        val max = 1.0
        val isExponential = false

        val zeroFrac = ExponentialTaper.valueToFraction(0.0, min, max, isExponential)
        assertEquals(0.5, zeroFrac, 1e-6)

        // At center (0.0), arc sweep should be 0
        val centerVal = 0.0
        val centerFrac = ExponentialTaper.valueToFraction(centerVal, min, max, isExponential)
        val centerSweep = abs(centerFrac - zeroFrac) * 270.0
        assertEquals(0.0, centerSweep, 1e-6)

        // Panned right to +0.5: arc sweep is 0.25 * 270 = 67.5°
        val rightVal = 0.5
        val rightFrac = ExponentialTaper.valueToFraction(rightVal, min, max, isExponential)
        val rightSweep = abs(rightFrac - zeroFrac) * 270.0
        assertEquals(67.5, rightSweep, 1e-6)
        assertTrue(rightFrac > zeroFrac)

        // Panned left to -0.5: arc sweep is 0.25 * 270 = 67.5°
        val leftVal = -0.5
        val leftFrac = ExponentialTaper.valueToFraction(leftVal, min, max, isExponential)
        val leftSweep = abs(leftFrac - zeroFrac) * 270.0
        assertEquals(67.5, leftSweep, 1e-6)
        assertTrue(leftFrac < zeroFrac)
    }
}

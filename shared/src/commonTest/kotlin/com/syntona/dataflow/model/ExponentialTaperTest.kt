package com.syntona.dataflow.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExponentialTaperTest {

    @Test
    fun testLinearTaper() {
        val min = 0.0
        val max = 100.0
        assertEquals(0.0, ExponentialTaper.fractionToValue(0.0, min, max, isExponential = false))
        assertEquals(50.0, ExponentialTaper.fractionToValue(0.5, min, max, isExponential = false))
        assertEquals(100.0, ExponentialTaper.fractionToValue(1.0, min, max, isExponential = false))

        assertEquals(0.0, ExponentialTaper.valueToFraction(0.0, min, max, isExponential = false))
        assertEquals(0.5, ExponentialTaper.valueToFraction(50.0, min, max, isExponential = false))
        assertEquals(1.0, ExponentialTaper.valueToFraction(100.0, min, max, isExponential = false))
    }

    @Test
    fun testPositiveExponentialRange() {
        val min = 40.0
        val max = 8000.0
        // Boundary values
        assertEquals(min, ExponentialTaper.fractionToValue(0.0, min, max, isExponential = true), 1e-9)
        assertEquals(max, ExponentialTaper.fractionToValue(1.0, min, max, isExponential = true), 1e-9)

        // Midpoint should be geometric mean: sqrt(40 * 8000) = sqrt(320000) ≈ 565.6854
        val midVal = ExponentialTaper.fractionToValue(0.5, min, max, isExponential = true)
        assertEquals(kotlin.math.sqrt(min * max), midVal, 1e-4)

        // Round-trip tests
        for (i in 0..100) {
            val u = i / 100.0
            val v = ExponentialTaper.fractionToValue(u, min, max, isExponential = true)
            val uBack = ExponentialTaper.valueToFraction(v, min, max, isExponential = true)
            assertEquals(u, uBack, 1e-6, "Round-trip fraction mismatch at $u")
        }
    }

    @Test
    fun testZeroAtStartLinearRegionDoesNotExceedOnePercent() {
        val min = 0.0
        val max = 1.0

        // Zero at u = 0.0
        assertEquals(0.0, ExponentialTaper.fractionToValue(0.0, min, max, isExponential = true), 1e-9)
        assertEquals(1.0, ExponentialTaper.fractionToValue(1.0, min, max, isExponential = true), 1e-9)

        // Linear region fraction must not exceed 1% (0.01)
        val uLin = ExponentialTaper.LINEAR_REGION_FRACTION
        assertTrue(uLin <= 0.01, "Linear region must not exceed 1% of manual drag range")

        val vAtLin = ExponentialTaper.fractionToValue(uLin, min, max, isExponential = true)
        assertTrue(vAtLin > 0.0, "Value at linear boundary must be positive")

        // In linear region [0, uLin], value must be linear
        val halfLinFrac = uLin / 2.0
        val vAtHalfLin = ExponentialTaper.fractionToValue(halfLinFrac, min, max, isExponential = true)
        assertEquals(vAtLin / 2.0, vAtHalfLin, 1e-7, "Region [0, uLin] must be strictly linear")

        // Monotonicity check across the entire range
        var prevVal = -1.0
        for (i in 0..1000) {
            val u = i / 1000.0
            val v = ExponentialTaper.fractionToValue(u, min, max, isExponential = true)
            assertTrue(v >= prevVal, "Value must be monotonically non-decreasing at u=$u")
            prevVal = v
        }

        // Round-trip tests
        for (i in 0..100) {
            val u = i / 100.0
            val v = ExponentialTaper.fractionToValue(u, min, max, isExponential = true)
            val uBack = ExponentialTaper.valueToFraction(v, min, max, isExponential = true)
            assertEquals(u, uBack, 1e-5, "Round-trip mismatch at u=$u")
        }
    }

    @Test
    fun testFaderZeroAtStart() {
        val min = 0.0
        val max = 100.0

        assertEquals(0.0, ExponentialTaper.fractionToValue(0.0, min, max, isExponential = true), 1e-9)
        assertEquals(100.0, ExponentialTaper.fractionToValue(1.0, min, max, isExponential = true), 1e-9)

        val uLin = ExponentialTaper.LINEAR_REGION_FRACTION
        val vAtLin = ExponentialTaper.fractionToValue(uLin, min, max, isExponential = true)
        val vAtHalfLin = ExponentialTaper.fractionToValue(uLin / 2.0, min, max, isExponential = true)
        assertEquals(vAtLin / 2.0, vAtHalfLin, 1e-5)

        // Above 1%, it behaves exponentially
        val vMid = ExponentialTaper.fractionToValue(0.5, min, max, isExponential = true)
        assertTrue(vMid < 50.0, "Exponential taper at 50% must be lower than linear midpoint (50.0), was $vMid")
    }

    @Test
    fun testZeroCrossingInsideRange() {
        val min = -1.0
        val max = 1.0

        // Zero should occur at fraction 0.5
        val vZero = ExponentialTaper.fractionToValue(0.5, min, max, isExponential = true)
        assertEquals(0.0, vZero, 1e-9, "Zero should map to fraction 0.5")

        val uZero = ExponentialTaper.valueToFraction(0.0, min, max, isExponential = true)
        assertEquals(0.5, uZero, 1e-9, "Value 0.0 should map to fraction 0.5")

        // Linear region around zero must not exceed 1% total drag range (width <= 0.01)
        val u1 = 0.5 - ExponentialTaper.LINEAR_REGION_FRACTION / 2.0
        val u2 = 0.5 + ExponentialTaper.LINEAR_REGION_FRACTION / 2.0
        val width = u2 - u1
        assertTrue(width <= 0.01 + 1e-9, "Linear region around zero crossing must not exceed 1% of manual drag range")

        // Monotonicity check
        var prevVal = -2.0
        for (i in 0..1000) {
            val u = i / 1000.0
            val v = ExponentialTaper.fractionToValue(u, min, max, isExponential = true)
            assertTrue(v >= prevVal, "Value must be monotonically non-decreasing at u=$u")
            prevVal = v
        }
    }

    @Test
    fun testZeroAtEnd() {
        val min = -100.0
        val max = 0.0

        assertEquals(min, ExponentialTaper.fractionToValue(0.0, min, max, isExponential = true), 1e-9)
        assertEquals(max, ExponentialTaper.fractionToValue(1.0, min, max, isExponential = true), 1e-9)

        val uBoundary = 1.0 - ExponentialTaper.LINEAR_REGION_FRACTION
        val vAtBoundary = ExponentialTaper.fractionToValue(uBoundary, min, max, isExponential = true)
        assertTrue(vAtBoundary < 0.0)

        // In linear region [uBoundary, 1.0], half step should be half value
        val uMidLinear = 1.0 - ExponentialTaper.LINEAR_REGION_FRACTION / 2.0
        val vMidLinear = ExponentialTaper.fractionToValue(uMidLinear, min, max, isExponential = true)
        assertEquals(vAtBoundary / 2.0, vMidLinear, 1e-5)
    }
}

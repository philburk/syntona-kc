package com.syntona.dataflow.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFieldTest {

    @Test
    fun testFormatNumberIntegers() {
        assertEquals("0", formatNumber(0.0))
        assertEquals("1", formatNumber(1.0))
        assertEquals("100", formatNumber(100.0))
        assertEquals("1000", formatNumber(1000.0))
        assertEquals("10000", formatNumber(10000.0))
        assertEquals("50000", formatNumber(50000.0))
        assertEquals("44100", formatNumber(44100.0))
        assertEquals("48000", formatNumber(48000.0))
        assertEquals("1000000", formatNumber(1000000.0))
        assertEquals("9999999", formatNumber(9999999.0))
        assertEquals("-50000", formatNumber(-50000.0))
    }

    @Test
    fun testFormatNumberDecimals() {
        assertEquals("0.5", formatNumber(0.5))
        assertEquals("0.1234", formatNumber(0.1234))
        assertEquals("0.001", formatNumber(0.001))
        assertEquals("0.0001", formatNumber(0.0001))
        assertEquals("123.456", formatNumber(123.456))
        assertEquals("10000.5", formatNumber(10000.5))
        assertEquals("0.1", formatNumber(0.10000000000000002))
    }

    @Test
    fun testFormatNumberScientificOnlyWhenNecessary() {
        // Very small numbers (< 1e-4)
        assertEquals("5e-5", formatNumber(0.00005))
        assertEquals("1e-6", formatNumber(0.000001))

        // Very large numbers (>= 1e8)
        assertEquals("1e8", formatNumber(100000000.0))
        assertEquals("2.5e9", formatNumber(2500000000.0))
    }

    @Test
    fun testFormatNumberSpecialValues() {
        assertEquals("NaN", formatNumber(Double.NaN))
        assertEquals("Infinity", formatNumber(Double.POSITIVE_INFINITY))
        assertEquals("-Infinity", formatNumber(Double.NEGATIVE_INFINITY))
    }
}

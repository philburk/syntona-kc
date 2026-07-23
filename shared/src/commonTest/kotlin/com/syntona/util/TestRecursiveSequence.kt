package com.syntona.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestRecursiveSequence {

    @Test
    fun testDefaultsAndInitialization() {
        val seq = RecursiveSequence()
        assertEquals(25, seq.maxValue)
        assertEquals(7, seq.maxInterval)
        assertEquals(32, seq.maxDelay)
        assertEquals(64, seq.values.size)
        assertEquals(64, seq.enables.size)
        assertEquals(0.5, seq.density)

        // Check values array default initialization
        for (v in seq.values) {
            assertEquals(12, v)
        }
    }

    @Test
    fun testCustomInitialization() {
        val seq = RecursiveSequence(maxValue = 100, maxInterval = 10, arraySize = 30)
        assertEquals(100, seq.maxValue)
        assertEquals(10, seq.maxInterval)
        assertEquals(15, seq.maxDelay)
        assertEquals(30, seq.values.size)

        for (v in seq.values) {
            assertEquals(50, v)
        }
    }

    @Test
    fun testSequenceGeneration() {
        val seq = RecursiveSequence()
        for (i in 0 until 100) {
            val val1 = seq.next()
            assertTrue(val1 >= -1 && val1 < seq.maxValue)
        }
    }

    @Test
    fun testNextWithBeatIndex() {
        val seq = RecursiveSequence()
        for (beat in 0 until 32) {
            val val1 = seq.next(beat)
            assertTrue(val1 >= -1 && val1 < seq.maxValue)
        }
    }

    @Test
    fun testRandomPowerOf2() {
        val seq = RecursiveSequence()
        for (i in 0 until 50) {
            val p2 = seq.randomPowerOf2(4)
            assertTrue(p2 == 1 || p2 == 2 || p2 == 4 || p2 == 8 || p2 == 16)
        }
    }

    @Test
    fun testRandomEvenInterval() {
        val seq = RecursiveSequence(maxInterval = 5)
        for (i in 0 until 50) {
            val interval = seq.randomEvenInterval()
            assertTrue(interval in -5..5)
        }
    }
}

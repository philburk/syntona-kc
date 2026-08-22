package com.syntona.util

import com.softsynth.ksyn.data.SequentialData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentedFloatArraySequentialDataTest {

    @Test
    fun testImplementsSequentialData() {
        val array = SegmentedFloatArray()
        assertTrue(array is SequentialData)
    }

    @Test
    fun testSequentialDataContract() {
        val array = SegmentedFloatArray()
        val data: SequentialData = array

        assertEquals(0, data.numFrames)
        assertEquals(1, data.channelsPerFrame)
        assertEquals(-1, data.sustainBegin)
        assertEquals(-1, data.sustainEnd)
        assertEquals(-1, data.releaseBegin)
        assertEquals(-1, data.releaseEnd)
        assertEquals(48000.0, data.getRateScaler(0, 1.0 / 48000.0))

        // Extend and write samples via SequentialData interface
        array.extend(10)
        assertEquals(10, data.numFrames)

        data.writeSample(0, 0.5f)
        data.writeDouble(1, 0.75)

        assertEquals(0.5f, data.readSample(0))
        assertEquals(0.75, data.readDouble(1), 0.0001)
    }
}

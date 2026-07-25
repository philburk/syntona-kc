package com.syntona.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SegmentedFloatArrayTest {

    @Test
    fun testStartsEmpty() {
        val array = SegmentedFloatArray(segmentSize = 8)
        assertEquals(0, array.length)
        assertEquals(0, array.capacity)
        assertEquals(0, array.spareCapacity)
    }

    @Test
    fun testExtendAllocatesOneSegmentAtATime() {
        val array = SegmentedFloatArray(segmentSize = 8)
        array.extend(3)
        assertEquals(3, array.length)
        assertEquals(8, array.capacity)
        assertEquals(5, array.spareCapacity)

        // Still inside the first segment.
        array.extend(5)
        assertEquals(8, array.length)
        assertEquals(8, array.capacity)

        // Crossing into a second segment.
        array.extend(1)
        assertEquals(9, array.length)
        assertEquals(16, array.capacity)
    }

    @Test
    fun testExtendedValuesReadAsZero() {
        val array = SegmentedFloatArray(segmentSize = 4)
        array.extend(4)
        array.extend(4)
        for (i in 0 until array.length) {
            assertEquals(0f, array[i], "index $i should start at zero")
        }
    }

    @Test
    fun testGetAndSetAcrossSegmentBoundaries() {
        val array = SegmentedFloatArray(segmentSize = 4)
        repeat(5) { array.extend(4) } // 20 values over 5 segments
        for (i in 0 until 20) {
            array[i] = i.toFloat() * 1.5f
        }
        for (i in 0 until 20) {
            assertEquals(i.toFloat() * 1.5f, array[i], "index $i")
        }
    }

    @Test
    fun testExtendRejectsMoreThanSegmentSize() {
        val array = SegmentedFloatArray(segmentSize = 8)
        assertFailsWith<IllegalArgumentException> { array.extend(9) }
        assertFailsWith<IllegalArgumentException> { array.extend(-1) }
        // The failed calls must not have changed anything.
        assertEquals(0, array.length)
        assertEquals(0, array.capacity)
    }

    @Test
    fun testAccessBeyondLengthThrowsEvenWhenCapacityAllows() {
        val array = SegmentedFloatArray(segmentSize = 8)
        array.extend(3)
        // Capacity is 8, but only 3 values are valid.
        assertTrue(array.capacity > array.length)
        assertFailsWith<IndexOutOfBoundsException> { array[3] }
        assertFailsWith<IndexOutOfBoundsException> { array[3] = 1f }
        assertFailsWith<IndexOutOfBoundsException> { array[-1] }
        assertFailsWith<IndexOutOfBoundsException> { array[-1] = 1f }
    }

    @Test
    fun testBulkWriteAndReadSpanningSegments() {
        val array = SegmentedFloatArray(segmentSize = 4)
        repeat(4) { array.extend(4) } // 16 values
        val source = FloatArray(16) { it.toFloat() }
        array.write(0, source)

        val dest = FloatArray(16)
        array.read(0, dest)
        assertTrue(source.contentEquals(dest))
    }

    @Test
    fun testBulkWriteAndReadWithOffsets() {
        val array = SegmentedFloatArray(segmentSize = 4)
        repeat(3) { array.extend(4) } // 12 values
        val source = FloatArray(12) { 100f + it }

        // Write 6 values from the middle of the source into the middle of the array,
        // which straddles a segment boundary in both cases.
        array.write(3, source, 2, 6)
        for (i in 0 until 6) {
            assertEquals(source[2 + i], array[3 + i], "index ${3 + i}")
        }
        // Untouched values stay zero.
        assertEquals(0f, array[2])
        assertEquals(0f, array[9])

        val dest = FloatArray(10)
        array.read(3, dest, 4, 6)
        for (i in 0 until 6) {
            assertEquals(source[2 + i], dest[4 + i], "dest index ${4 + i}")
        }
        assertEquals(0f, dest[3])
    }

    @Test
    fun testBulkAccessOutOfRangeThrows() {
        val array = SegmentedFloatArray(segmentSize = 4)
        array.extend(4)
        val buffer = FloatArray(8)

        assertFailsWith<IndexOutOfBoundsException> { array.read(0, buffer, 0, 5) }
        assertFailsWith<IndexOutOfBoundsException> { array.write(0, buffer, 0, 5) }
        assertFailsWith<IndexOutOfBoundsException> { array.read(2, buffer, 0, 3) }
        // Valid array range, but the external buffer is too small.
        assertFailsWith<IndexOutOfBoundsException> { array.read(0, buffer, 6, 4) }
        assertFailsWith<IllegalArgumentException> { array.read(0, buffer, 0, -1) }
    }

    @Test
    fun testReplenishFillsPoolAndExtendDoesNotAllocate() {
        val array = SegmentedFloatArray(segmentSize = 4, poolCapacity = 8)
        assertEquals(0, array.pooledSegmentCount)

        array.replenish()
        assertEquals(8, array.pooledSegmentCount)

        // Draining the whole pool must not allocate.
        repeat(8) { array.extend(4) }
        assertEquals(0, array.pooledSegmentCount)
        assertEquals(32, array.length)
        assertEquals(0, array.unpooledAllocations)
    }

    @Test
    fun testExtendStillWorksWhenPoolIsEmptyButIsCounted() {
        val array = SegmentedFloatArray(segmentSize = 4, poolCapacity = 0)
        array.extend(4)
        array.extend(4)
        // Recording continues rather than failing, but the shortfall is visible.
        assertEquals(8, array.length)
        assertTrue(array.unpooledAllocations >= 2, "expected inline allocations to be counted")
    }

    @Test
    fun testReplenishAfterUseRefillsPool() {
        val array = SegmentedFloatArray(segmentSize = 4, poolCapacity = 4)
        array.replenish()
        repeat(4) { array.extend(4) }
        assertEquals(0, array.pooledSegmentCount)

        array.replenish()
        assertEquals(4, array.pooledSegmentCount)
        // Data written before replenishing survives it.
        array[0] = 42f
        array.replenish()
        assertEquals(42f, array[0])
    }

    @Test
    fun testClearRecyclesSegmentsIntoPool() {
        val array = SegmentedFloatArray(segmentSize = 4, poolCapacity = 4)
        array.replenish()
        repeat(3) { array.extend(4) }
        array[0] = 7f
        val allocationsBefore = array.unpooledAllocations

        array.clear()
        assertEquals(0, array.length)
        assertEquals(0, array.capacity)
        // One segment was left in the pool and the three in use come back, refilling it.
        assertEquals(4, array.pooledSegmentCount, "cleared segments should return to the pool")

        // Reusing after clear should not need any new allocation.
        repeat(3) { array.extend(4) }
        assertEquals(allocationsBefore, array.unpooledAllocations)
        assertEquals(0f, array[0], "recycled segments should be zeroed")
    }

    @Test
    fun testNonPowerOfTwoSegmentSize() {
        // Exercises the divide/remainder path rather than the shift/mask fast path.
        val array = SegmentedFloatArray(segmentSize = 5)
        repeat(4) { array.extend(5) } // 20 values over 4 segments
        for (i in 0 until 20) {
            array[i] = i.toFloat()
        }
        for (i in 0 until 20) {
            assertEquals(i.toFloat(), array[i], "index $i")
        }

        val dest = FloatArray(20)
        array.read(0, dest)
        for (i in 0 until 20) {
            assertEquals(i.toFloat(), dest[i], "dest index $i")
        }
    }

    @Test
    fun testUnevenExtendsKeepIndexingConsistent() {
        // Extend by amounts that do not divide the segment size, so values land at
        // varying offsets within their segments.
        val array = SegmentedFloatArray(segmentSize = 8)
        var written = 0
        repeat(20) {
            array.extend(3)
            while (written < array.length) {
                array[written] = written.toFloat()
                written++
            }
        }
        assertEquals(60, array.length)
        for (i in 0 until 60) {
            assertEquals(i.toFloat(), array[i], "index $i")
        }
    }

    @Test
    fun testGrowsBeyondInitialIndexTable() {
        // More segments than the initial index table holds, to cover table growth.
        val array = SegmentedFloatArray(segmentSize = 2, poolCapacity = 4)
        repeat(200) {
            array.replenish()
            array.extend(2)
        }
        assertEquals(400, array.length)
        assertEquals(400, array.capacity)

        for (i in 0 until 400) {
            array[i] = i.toFloat()
        }
        for (i in 0 until 400) {
            assertEquals(i.toFloat(), array[i], "index $i")
        }
    }

    @Test
    fun testReplenishKeepsIndexTableAheadOfPool() {
        // After replenishing, draining the entire pool should never need to grow the table.
        val array = SegmentedFloatArray(segmentSize = 2, poolCapacity = 128)
        array.replenish()
        assertEquals(0, array.unpooledAllocations)

        repeat(128) { array.extend(2) }
        assertEquals(0, array.unpooledAllocations, "draining the pool should not have allocated")
    }

    @Test
    fun testPoolCountTracksTakesAndRefillsAcrossWrap() {
        // Cycle through the ring many times so the read and write counters pass its capacity
        // repeatedly and the modulo indexing wraps.
        val array = SegmentedFloatArray(segmentSize = 2, poolCapacity = 3, initialSegmentCapacity = 64)
        repeat(20) {
            array.replenish()
            assertEquals(3, array.pooledSegmentCount)
            array.extend(2)
            assertEquals(2, array.pooledSegmentCount)
            array.extend(2)
            assertEquals(1, array.pooledSegmentCount)
        }
        assertEquals(80, array.length)
        assertEquals(0, array.unpooledAllocations, "pool should have covered every extend")
    }

    @Test
    fun testPoolIsFifoSoSegmentsAreReusedInOrder() {
        // Fill, drain, and refill repeatedly while writing distinct data, to check that ring
        // slots are never handed out twice or skipped.
        val array = SegmentedFloatArray(segmentSize = 4, poolCapacity = 2, initialSegmentCapacity = 64)
        var expected = 0
        repeat(30) {
            array.replenish()
            array.extend(4)
            while (expected < array.length) {
                array[expected] = expected.toFloat()
                expected++
            }
        }
        assertEquals(120, array.length)
        for (i in 0 until 120) {
            assertEquals(i.toFloat(), array[i], "index $i")
        }
    }

    @Test
    fun testPoolCapacityZeroAlwaysAllocatesInline() {
        // Guards the ring arithmetic against a division by zero when pooling is disabled.
        val array = SegmentedFloatArray(segmentSize = 4, poolCapacity = 0)
        array.replenish()
        assertEquals(0, array.pooledSegmentCount)
        array.extend(4)
        array.extend(4)
        assertEquals(8, array.length)
        for (i in 0 until 8) assertEquals(0f, array[i])
    }

    @Test
    fun testInitialSegmentCapacityAvoidsTableGrowth() {
        // Pre-sizing the index table for the expected length should mean no allocation
        // beyond the pool for the whole recording.
        val array = SegmentedFloatArray(
            segmentSize = 2,
            poolCapacity = 4,
            initialSegmentCapacity = 300
        )
        repeat(300) {
            array.replenish()
            array.extend(2)
        }
        assertEquals(600, array.length)
        assertEquals(0, array.unpooledAllocations, "pre-sized table should not have grown")
    }

    @Test
    fun testRejectsInvalidConstructorArguments() {
        assertFailsWith<IllegalArgumentException> { SegmentedFloatArray(segmentSize = 0) }
        assertFailsWith<IllegalArgumentException> { SegmentedFloatArray(segmentSize = -1) }
        assertFailsWith<IllegalArgumentException> {
            SegmentedFloatArray(segmentSize = 8, poolCapacity = -1)
        }
    }

    @Test
    fun testDefaultsAreAudioFriendly() {
        val array = SegmentedFloatArray()
        assertEquals(4096, array.segmentSize)
        array.replenish()
        assertEquals(16, array.pooledSegmentCount)
        array.extend(4096)
        assertEquals(0, array.unpooledAllocations)
    }
}

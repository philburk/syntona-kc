package com.syntona.util

import kotlin.concurrent.Volatile

/**
 * A growable, randomly accessible array of floats stored as a series of fixed-size segments.
 *
 * Intended for recording audio of indefinite length without pre-allocating one large array.
 * Growth allocates a single new segment rather than reallocating and copying, so the cost of
 * extending is bounded and independent of how much data has already been recorded. Segments are
 * never moved, so data already written stays at a stable location.
 *
 * ### Real-time use
 *
 * [get], [set], [read] and [write] never allocate. [extend] takes a segment from a pre-allocated
 * pool, so it does not allocate either, as long as the pool has been kept filled by calls to
 * [replenish] from a non-real-time context. If the pool is empty, [extend] still succeeds by
 * allocating inline — recording continues rather than failing — and [unpooledAllocations] counts
 * how often that happened so a caller can detect that it is not replenishing fast enough.
 *
 * ### Sizing
 *
 * [segmentSize] is in floats, not bytes. A segment of 4096 floats is 16 KB and holds about 85 ms
 * of audio at 48 kHz, so at audio rates a new segment is needed only a few times per second.
 * A power-of-two [segmentSize] is slightly faster to index, but any positive size is allowed.
 *
 * ### Threading
 *
 * The segment pool is a single-producer, single-consumer FIFO. One thread may call [extend],
 * [get], [set], [read] and [write] (the consumer, typically the audio thread) while another calls
 * [replenish] (the producer). No locking is involved: the two threads advance separate counters,
 * so neither performs a read-modify-write on state the other also writes.
 *
 * [clear] acts as a producer, so it must not run concurrently with [extend] or [replenish];
 * call it when recording is stopped.
 *
 * Note that publishing a valid extent to a *reader* on a third thread is still the caller's job.
 * [length] grows when space is reserved, which is before the samples are written into it, so a
 * reader that trusted [length] could see values that have not been written yet. The recording
 * classes that will wrap this should publish their own count after writing.
 */
class SegmentedFloatArray(
    val segmentSize: Int = DEFAULT_SEGMENT_SIZE,
    val poolCapacity: Int = DEFAULT_POOL_CAPACITY,
    initialSegmentCapacity: Int = 0
) {
    init {
        require(segmentSize > 0) { "segmentSize must be positive, was $segmentSize" }
        require(poolCapacity >= 0) { "poolCapacity must not be negative, was $poolCapacity" }
        require(initialSegmentCapacity >= 0) {
            "initialSegmentCapacity must not be negative, was $initialSegmentCapacity"
        }
    }

    // Index table of allocated segments. Only the consumer touches this, so it needs no
    // synchronization, but growing it does allocate. It is sized so that draining a full pool
    // never forces a growth, and any growth that does happen is counted like an empty pool.
    private var segments = arrayOfNulls<FloatArray>(
        maxOf(MIN_TABLE_CAPACITY, poolCapacity + initialSegmentCapacity)
    )
    private var segmentCount = 0

    // Segment pool, used as a ring buffer. Only replenish() writes a slot and only takeSegment()
    // reads one, and a slot is reused only after the consumer has released it.
    private val pool = arrayOfNulls<FloatArray>(poolCapacity)

    // Advanced only by takeSegment() on the consumer thread.
    @Volatile
    private var poolReadCount = 0L

    // Advanced only by replenish() (or clear()) on the producer thread. Written after the slot
    // it refers to has been filled, so a consumer that sees the new count also sees the segment.
    @Volatile
    private var poolWriteCount = 0L

    // Fast path for power-of-two segment sizes: shift/mask instead of divide/remainder.
    // -1 means segmentSize is not a power of two.
    private val indexShift =
        if (segmentSize and (segmentSize - 1) == 0) segmentSize.countTrailingZeroBits() else -1

    /**
     * Number of valid float values. Indices `0 until length` may be read and written.
     *
     * Volatile so that a reader on another thread sees a current value rather than a cached one,
     * but see the threading notes above: this counts space that has been reserved, not space that
     * has necessarily been written.
     */
    @Volatile
    var length: Int = 0
        private set

    /**
     * Number of float slots currently allocated. Always at least [length]. Extending beyond this
     * requires another segment.
     */
    val capacity: Int
        get() = segmentCount * segmentSize

    /** How much [length] can grow before another segment is needed. */
    val spareCapacity: Int
        get() = capacity - length

    /** Number of segments currently held in the pool, ready for [extend] to take. */
    val pooledSegmentCount: Int
        get() = (poolWriteCount - poolReadCount).toInt()

    /**
     * How many times a segment had to be allocated on the spot, either because the pool was empty
     * or because the segment index table had to grow. Should stay at zero in a correctly
     * replenished real-time setup.
     */
    @Volatile
    var unpooledAllocations: Int = 0
        private set

    private fun segmentOf(index: Int): Int =
        if (indexShift >= 0) index shr indexShift else index / segmentSize

    private fun offsetOf(index: Int): Int =
        if (indexShift >= 0) index and (segmentSize - 1) else index % segmentSize

    /**
     * Grows the valid range by [num] values, which read as `0f` until written.
     *
     * [num] may not exceed [segmentSize], which guarantees that a single call needs at most one
     * new segment and so cannot cause an unbounded amount of work.
     */
    fun extend(num: Int) {
        require(num >= 0) { "num must not be negative, was $num" }
        require(num <= segmentSize) { "num ($num) must not exceed segmentSize ($segmentSize)" }
        val newLength = length + num
        if (newLength > capacity) {
            addSegment()
        }
        // Clear the new range so it reads as zero even when it is reusing capacity that held
        // data before a truncate(). At most segmentSize values, so the cost stays bounded.
        fillRange(length, newLength, 0f)
        length = newLength
    }

    /**
     * Shrinks the valid range to [newLength], discarding everything after it. Capacity is kept,
     * so growing back into the discarded range needs no new segments, and [extend] clears it so
     * the old values are not visible again.
     *
     * Cheap regardless of how much is discarded: no memory is touched or released here.
     */
    fun truncate(newLength: Int) {
        require(newLength >= 0) { "newLength must not be negative, was $newLength" }
        require(newLength <= length) {
            "newLength ($newLength) must not exceed length ($length); use extend() to grow"
        }
        length = newLength
    }

    /** Fills `[from, to)` with [value], walking segment by segment. */
    private fun fillRange(from: Int, to: Int, value: Float) {
        var index = from
        while (index < to) {
            val offset = offsetOf(index)
            val n = minOf(to - index, segmentSize - offset)
            segments[segmentOf(index)]!!.fill(value, offset, offset + n)
            index += n
        }
    }

    private fun addSegment() {
        if (segmentCount == segments.size) {
            // The table can only be grown here, on the consumer thread, because extend() writes
            // into it; letting replenish() reallocate it concurrently would lose those writes.
            // Growth doubles, so it is rare, and it is counted since it does allocate.
            unpooledAllocations++
            segments = segments.copyOf(segments.size * 2)
        }
        segments[segmentCount++] = takeSegment()
    }

    /** Consumer side of the pool: takes the oldest pooled segment, or allocates if none. */
    private fun takeSegment(): FloatArray {
        val read = poolReadCount
        if (poolWriteCount - read > 0) {
            val slot = (read % poolCapacity).toInt()
            val segment = pool[slot]!!
            pool[slot] = null
            // Publish the take only after the slot has been consumed, so the producer cannot
            // overwrite a segment we are still reading.
            poolReadCount = read + 1
            return segment
        }
        unpooledAllocations++
        return FloatArray(segmentSize)
    }

    /** Producer side of the pool: publishes one segment into the ring. Returns false if full. */
    private fun offerSegment(segment: FloatArray): Boolean {
        val write = poolWriteCount
        if (write - poolReadCount >= poolCapacity) return false
        pool[(write % poolCapacity).toInt()] = segment
        // Publish the slot contents before the count that makes them visible to the consumer.
        poolWriteCount = write + 1
        return true
    }

    /**
     * Fills the segment pool, allocating as needed. Call this from a background thread or
     * coroutine, never from the audio thread.
     */
    fun replenish() {
        // Check before allocating so a full pool does not allocate an array just to discard it.
        // Only this thread raises poolWriteCount and the consumer only lowers occupancy, so an
        // offer that passes this guard always succeeds.
        while (poolWriteCount - poolReadCount < poolCapacity) {
            offerSegment(FloatArray(segmentSize))
        }
    }

    /** Value at [index], which must be within `0 until length`. */
    operator fun get(index: Int): Float {
        checkIndex(index)
        return segments[segmentOf(index)]!![offsetOf(index)]
    }

    /** Sets the value at [index], which must be within `0 until length`. */
    operator fun set(index: Int, value: Float) {
        checkIndex(index)
        segments[segmentOf(index)]!![offsetOf(index)] = value
    }

    /**
     * Copies [count] values starting at [startIndex] into [dest] at [destOffset].
     * The whole source range must lie within `0 until length`.
     */
    fun read(startIndex: Int, dest: FloatArray, destOffset: Int, count: Int) {
        checkRange(startIndex, count)
        checkExternal(dest.size, destOffset, count, "dest")
        var index = startIndex
        var target = destOffset
        var remaining = count
        while (remaining > 0) {
            val offset = offsetOf(index)
            val n = minOf(remaining, segmentSize - offset)
            segments[segmentOf(index)]!!.copyInto(dest, target, offset, offset + n)
            index += n
            target += n
            remaining -= n
        }
    }

    /** Copies the whole of [dest] out of this array, starting at [startIndex]. */
    fun read(startIndex: Int, dest: FloatArray) = read(startIndex, dest, 0, dest.size)

    /**
     * Copies [count] values from [src] at [srcOffset] into this array starting at [startIndex].
     * The whole destination range must lie within `0 until length`, so [extend] first.
     */
    fun write(startIndex: Int, src: FloatArray, srcOffset: Int, count: Int) {
        checkRange(startIndex, count)
        checkExternal(src.size, srcOffset, count, "src")
        var index = startIndex
        var source = srcOffset
        var remaining = count
        while (remaining > 0) {
            val offset = offsetOf(index)
            val n = minOf(remaining, segmentSize - offset)
            src.copyInto(segments[segmentOf(index)]!!, offset, source, source + n)
            index += n
            source += n
            remaining -= n
        }
    }

    /** Copies the whole of [src] into this array, starting at [startIndex]. */
    fun write(startIndex: Int, src: FloatArray) = write(startIndex, src, 0, src.size)

    /**
     * Discards all data, returning segments to the pool for reuse so that starting a new
     * recording does not have to allocate again. Does not shrink the pool below its capacity.
     */
    fun clear() {
        for (i in 0 until segmentCount) {
            val segment = segments[i]!!
            segments[i] = null
            segment.fill(0f)
            // Segments beyond the pool's capacity are simply dropped for the collector.
            offerSegment(segment)
        }
        segmentCount = 0
        length = 0
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= length) {
            throw IndexOutOfBoundsException("index $index out of bounds for length $length")
        }
    }

    private fun checkRange(startIndex: Int, count: Int) {
        if (count < 0) throw IllegalArgumentException("count must not be negative, was $count")
        if (startIndex < 0 || startIndex + count > length) {
            throw IndexOutOfBoundsException(
                "range [$startIndex, ${startIndex + count}) out of bounds for length $length"
            )
        }
    }

    private fun checkExternal(size: Int, offset: Int, count: Int, name: String) {
        if (offset < 0 || offset + count > size) {
            throw IndexOutOfBoundsException(
                "range [$offset, ${offset + count}) out of bounds for $name of size $size"
            )
        }
    }

    companion object {
        /** 4096 floats is 16 KB, about 85 ms of audio at 48 kHz. */
        const val DEFAULT_SEGMENT_SIZE = 4096

        /** At the default segment size this is roughly 1.4 seconds of lookahead at 48 kHz. */
        const val DEFAULT_POOL_CAPACITY = 16

        private const val MIN_TABLE_CAPACITY = 64
    }
}

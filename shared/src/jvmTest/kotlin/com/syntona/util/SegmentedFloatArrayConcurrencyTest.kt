package com.syntona.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the single-producer/single-consumer segment pool with a real second thread.
 * These live in jvmTest because commonTest has no portable threading.
 */
class SegmentedFloatArrayConcurrencyTest {

    @Test
    fun testConsumerAndProducerDoNotCorruptThePool() {
        val segmentSize = 64
        val totalSegments = 5000
        val array = SegmentedFloatArray(
            segmentSize = segmentSize,
            poolCapacity = 8,
            initialSegmentCapacity = totalSegments
        )

        val failure = AtomicReference<Throwable?>(null)
        val done = CountDownLatch(1)

        // Producer: keep the pool topped up until the consumer says it is finished.
        val producer = Thread {
            try {
                while (done.count > 0L) {
                    array.replenish()
                    Thread.yield()
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }
        producer.isDaemon = true
        producer.start()

        // Consumer: extend and immediately stamp every new slot with a known value.
        try {
            var written = 0
            repeat(totalSegments) {
                array.extend(segmentSize)
                while (written < array.length) {
                    array[written] = written.toFloat()
                    written++
                }
            }
        } catch (t: Throwable) {
            failure.compareAndSet(null, t)
        } finally {
            done.countDown()
        }
        producer.join(TimeUnit.SECONDS.toMillis(10))

        assertNull(failure.get(), "producer or consumer threw: ${failure.get()}")
        assertEquals(segmentSize * totalSegments, array.length)

        // Every value must be intact. A pool slot handed out twice, or a segment published
        // before it was filled, would show up here as a wrong or lost value.
        for (i in 0 until array.length) {
            assertEquals(i.toFloat(), array[i], "index $i was corrupted")
        }
    }

    @Test
    fun testReplenishingKeepsPoolAheadOfDemand() {
        // With a producer running, a consumer extending at a leisurely rate should rarely, if
        // ever, have to allocate for itself. This is a sanity check on the pool actually being
        // used, not a hard real-time guarantee.
        val array = SegmentedFloatArray(
            segmentSize = 64,
            poolCapacity = 16,
            initialSegmentCapacity = 512
        )
        val done = CountDownLatch(1)
        val producer = Thread {
            while (done.count > 0L) {
                array.replenish()
                Thread.sleep(1)
            }
        }
        producer.isDaemon = true
        array.replenish() // start with a full pool, as a recorder would
        producer.start()

        try {
            repeat(500) {
                array.extend(64)
                Thread.sleep(0, 200_000) // ~0.2 ms between segments
            }
        } finally {
            done.countDown()
        }
        producer.join(TimeUnit.SECONDS.toMillis(10))

        assertTrue(
            array.unpooledAllocations < 50,
            "expected the pool to cover most demand, but fell back ${array.unpooledAllocations} times"
        )
    }
}

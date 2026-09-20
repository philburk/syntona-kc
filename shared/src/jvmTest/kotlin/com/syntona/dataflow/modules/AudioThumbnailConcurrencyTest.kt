package com.syntona.dataflow.modules

import com.syntona.util.SegmentedFloatArray
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * The engine thread rebuilds a thumbnail while the UI thread draws it. A draw must never see a
 * block count without the values behind it, which is what crashed when recording finished.
 */
class AudioThumbnailConcurrencyTest {

    @Test
    fun drawingWhileTheThumbnailIsRebuiltNeverReadsPastTheValues() {
        val recording = SegmentedFloatArray()
        val frames = 8192
        var remaining = frames
        while (remaining > 0) {
            val chunk = minOf(remaining, recording.segmentSize)
            recording.extend(chunk)
            remaining -= chunk
        }
        recording.write(0, FloatArray(frames) { (it % 100) / 100f })

        val thumbnail = AudioThumbnail()
        thumbnail.updateFrom(recording)

        val failure = AtomicReference<Throwable?>(null)
        val running = AtomicBoolean(true)

        val writer = thread {
            try {
                while (running.get()) {
                    thumbnail.clear() // As finishRecording does, then refills
                    thumbnail.updateFrom(recording)
                }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
        val reader = thread {
            try {
                while (running.get()) {
                    // Exactly what a waveform draw does
                    val blocks = thumbnail.blocks
                    for (b in 0 until blocks.count) {
                        blocks.minValues[b]
                        blocks.maxValues[b]
                    }
                }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }

        Thread.sleep(500)
        running.set(false)
        writer.join(5_000)
        reader.join(5_000)
        assertNull(failure.get(), "Reading a thumbnail while it is rebuilt must not fail")
    }
}

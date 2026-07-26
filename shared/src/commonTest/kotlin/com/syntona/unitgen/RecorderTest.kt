package com.syntona.unitgen

import com.softsynth.ksyn.Synthesizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The port value buffers are allocated when the ports are constructed, so [Recorder.generate] can
 * be driven directly here without a running synthesizer. With no engine attached, the transport
 * methods apply immediately rather than being queued.
 */
class RecorderTest {

    private val block = Synthesizer.FRAMES_PER_BLOCK

    /** Feeds one block of [values] into the input and generates. Returns the output block. */
    private fun Recorder.pump(values: FloatArray = FloatArray(block)): FloatArray {
        val inputs = input.getValues()
        for (i in 0 until block) inputs[i] = values[i]
        generate()
        return output.getValues().copyOf(block)
    }

    private fun ramp(start: Int) = FloatArray(block) { (start + it).toFloat() }

    @Test
    fun testStartsPausedAndEmpty() {
        val recorder = Recorder()
        assertEquals(Recorder.Mode.PAUSED, recorder.mode)
        assertEquals(0, recorder.cursor)
        assertEquals(0, recorder.length)
        assertFalse(recorder.isRecording)
        assertFalse(recorder.isPlaying)
    }

    @Test
    fun testPausedOutputsSilenceAndRecordsNothing() {
        val recorder = Recorder()
        val outputs = recorder.pump(ramp(1))
        for (i in 0 until block) assertEquals(0f, outputs[i])
        assertEquals(0, recorder.length)
        assertEquals(0, recorder.cursor)
    }

    @Test
    fun testRecordCapturesInputAndGrowsTheRecording() {
        val recorder = Recorder()
        recorder.record()
        assertTrue(recorder.isRecording)

        recorder.pump(ramp(0))
        assertEquals(block, recorder.length)
        assertEquals(block, recorder.cursor)

        recorder.pump(ramp(block))
        assertEquals(2 * block, recorder.length)
        assertEquals(2 * block, recorder.cursor)

        for (i in 0 until 2 * block) {
            assertEquals(i.toFloat(), recorder.recording[i], "frame $i")
        }
    }

    @Test
    fun testOutputIsSilentWhileRecording() {
        val recorder = Recorder()
        recorder.record()
        val outputs = recorder.pump(ramp(1))
        for (i in 0 until block) assertEquals(0f, outputs[i], "frame $i should be silent")
    }

    @Test
    fun testPauseStopsRecordingAndKeepsCursor() {
        val recorder = Recorder()
        recorder.record()
        recorder.pump(ramp(0))
        recorder.pause()

        recorder.pump(ramp(100))
        assertEquals(Recorder.Mode.PAUSED, recorder.mode)
        assertEquals(block, recorder.length, "paused recording should not grow")
        assertEquals(block, recorder.cursor, "cursor should stay where recording stopped")
    }

    @Test
    fun testRewindThenPlayReproducesTheRecording() {
        val recorder = Recorder()
        recorder.record()
        recorder.pump(ramp(0))
        recorder.pump(ramp(block))
        recorder.pause()
        recorder.rewind()
        assertEquals(0, recorder.cursor)

        recorder.play()
        val first = recorder.pump()
        val second = recorder.pump()
        for (i in 0 until block) {
            assertEquals(i.toFloat(), first[i], "first block frame $i")
            assertEquals((block + i).toFloat(), second[i], "second block frame $i")
        }
    }

    @Test
    fun testAmplitudeScalesPlayback() {
        val recorder = Recorder()
        recorder.record()
        recorder.pump(FloatArray(block) { 2f })
        recorder.pause()
        recorder.rewind()

        recorder.amplitude.setValueInternal(0.25f)
        recorder.play()
        val outputs = recorder.pump()
        for (i in 0 until block) assertEquals(0.5f, outputs[i], "frame $i")
    }

    @Test
    fun testPlaybackStopsAtTheEndOfTheRecording() {
        val recorder = Recorder()
        recorder.record()
        recorder.pump(FloatArray(block) { 1f })
        recorder.pause()
        recorder.rewind()

        recorder.play()
        recorder.pump()
        // The single recorded block is used up, so playback should have stopped on its own.
        assertEquals(Recorder.Mode.PAUSED, recorder.mode)
        assertEquals(block, recorder.cursor)

        val afterEnd = recorder.pump()
        for (i in 0 until block) assertEquals(0f, afterEnd[i], "frame $i past the end")
    }

    @Test
    fun testPlaybackPastAPartialEndOutputsSilence() {
        // Record less than a full block so playback runs off the end mid-block.
        val recorder = Recorder()
        recorder.record()
        recorder.pump(FloatArray(block) { 3f })
        recorder.pause()
        recorder.seek(block - 3)

        recorder.play()
        val outputs = recorder.pump()
        for (i in 0 until 3) assertEquals(3f, outputs[i], "frame $i should still be recorded audio")
        for (i in 3 until block) assertEquals(0f, outputs[i], "frame $i should be silent")
    }

    @Test
    fun testRewindAndRecordDiscardsTheOldTake() {
        // The scenario that motivated this: record a long take, rewind, record a short one, and
        // the recording should be as short as the new take rather than keeping the old tail.
        val recorder = Recorder()
        recorder.record()
        repeat(4) { b -> recorder.pump(ramp(b * block)) }
        recorder.pause()
        assertEquals(4 * block, recorder.length)

        recorder.rewind()
        recorder.record()
        recorder.pump(FloatArray(block) { -1f })
        recorder.pause()

        assertEquals(block, recorder.length, "the old take should have been discarded")
        for (i in 0 until block) {
            assertEquals(-1f, recorder.recording[i], "frame $i should be from the new take")
        }
    }

    @Test
    fun testPlaybackAfterAShorterRetakeStopsAtTheNewEnd() {
        val recorder = Recorder()
        recorder.record()
        repeat(4) { b -> recorder.pump(ramp(b * block)) }
        recorder.pause()

        recorder.rewind()
        recorder.record()
        recorder.pump(FloatArray(block) { 7f })
        recorder.pause()
        recorder.rewind()

        recorder.play()
        val first = recorder.pump()
        for (i in 0 until block) assertEquals(7f, first[i], "frame $i of the retake")
        assertEquals(Recorder.Mode.PAUSED, recorder.mode, "should stop at the end of the retake")

        val afterEnd = recorder.pump()
        for (i in 0 until block) assertEquals(0f, afterEnd[i], "frame $i should be silent")
    }

    @Test
    fun testRecordingFromTheMiddleTruncatesTheTail() {
        val recorder = Recorder()
        recorder.record()
        repeat(3) { b -> recorder.pump(ramp(b * block)) }
        recorder.pause()

        // Punch in one block from the start; everything after that point is discarded.
        recorder.seek(block)
        recorder.record()
        recorder.pump(FloatArray(block) { 9f })
        recorder.pause()

        assertEquals(2 * block, recorder.length)
        for (i in 0 until block) {
            assertEquals(i.toFloat(), recorder.recording[i], "frame $i before the punch-in")
        }
        for (i in block until 2 * block) {
            assertEquals(9f, recorder.recording[i], "frame $i of the new material")
        }
    }

    @Test
    fun testReRecordingReusesCapacityWithoutAllocating() {
        val recorder = Recorder(segmentSize = 64, poolCapacity = 8)
        recorder.replenish()
        recorder.record()
        repeat(40) { b ->
            recorder.replenish()
            recorder.pump(ramp(b * block))
        }
        recorder.pause()
        val allocationsAfterFirstTake = recorder.recording.unpooledAllocations

        // Recording the same span again should reuse the segments already held.
        recorder.rewind()
        recorder.record()
        repeat(40) { recorder.pump(FloatArray(block) { 1f }) }
        recorder.pause()

        assertEquals(
            allocationsAfterFirstTake,
            recorder.recording.unpooledAllocations,
            "re-recording should not need new segments"
        )
    }

    @Test
    fun testSeekClampsToTheRecordedRange() {
        val recorder = Recorder()
        recorder.record()
        recorder.pump(ramp(0))
        recorder.pause()

        recorder.seek(-5)
        assertEquals(0, recorder.cursor)
        recorder.seek(1000)
        assertEquals(block, recorder.cursor, "should clamp to the recorded length")
        recorder.seek(3)
        assertEquals(3, recorder.cursor)
    }

    @Test
    fun testLongRecordingWithReplenishNeverAllocatesOnTheAudioPath() {
        // A small segment size makes the recording cross many segment boundaries quickly.
        val recorder = Recorder(segmentSize = 64, poolCapacity = 8)
        recorder.replenish()
        recorder.record()

        // 400 blocks is 50 segments at this size. Replenishing between blocks stands in for the
        // background task that RecorderModule schedules while recording.
        repeat(400) { b ->
            recorder.replenish()
            recorder.pump(ramp(b * block))
        }

        assertEquals(400 * block, recorder.length)
        assertEquals(
            0,
            recorder.recording.unpooledAllocations,
            "replenishing between blocks should have covered every extend"
        )
        for (i in 0 until recorder.length) {
            assertEquals(i.toFloat(), recorder.recording[i], "frame $i")
        }
    }

    @Test
    fun testStartIsRequiredSoRecordingWorksWithOutputUnconnected() {
        // Without this the unit would only generate when something downstream pulled from it,
        // and recording with nothing patched to "out" would silently do nothing.
        assertTrue(Recorder().isStartRequired)
    }
}

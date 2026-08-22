package com.syntona.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.unitgen.UnitFilter
import com.syntona.util.SegmentedFloatArray
import kotlin.concurrent.Volatile

/**
 * Records its input into a [SegmentedFloatArray] and plays it back to its output, one sample per
 * frame at the engine's sample rate.
 *
 * The recording grows as needed rather than being pre-allocated, so its length is limited by
 * memory rather than by a fixed buffer. New space is taken from the recording's segment pool,
 * which [replenish] refills; call that periodically from a non-audio context while recording.
 *
 * A single [cursor] is shared by recording and playback and always stays within
 * `0..recording.length`. Recording is destructive: [record] discards anything after the cursor,
 * so rewinding and recording again replaces the previous take rather than leaving its tail
 * behind. Playback stops on its own when the cursor reaches the end.
 *
 * The output is silent while recording — patch the source through separately if you want to
 * monitor it — and amplitude scales playback only.
 */
class Recorder(
    val segmentSize: Int = SegmentedFloatArray.DEFAULT_SEGMENT_SIZE,
    val poolCapacity: Int = SegmentedFloatArray.DEFAULT_POOL_CAPACITY
) : UnitFilter() {

    enum class Mode { PAUSED, RECORDING, PLAYING }

    val amplitude = UnitInputPort(PORT_NAME_AMPLITUDE)

    /** The recorded audio. Grows while [Mode.RECORDING] is active. */
    @Volatile
    var recording = SegmentedFloatArray(segmentSize, poolCapacity)

    /** Read and write position in frames, always within `0..recording.length`. */
    @Volatile
    var cursor: Int = 0
        private set

    @Volatile
    var mode: Mode = Mode.PAUSED
        private set

    val isRecording: Boolean get() = mode == Mode.RECORDING
    val isPlaying: Boolean get() = mode == Mode.PLAYING

    /** Number of frames recorded so far. */
    val length: Int get() = recording.length

    /** Position of the [cursor] in seconds, using the engine's frame rate. */
    val cursorSeconds: Double get() = cursor / frameRate

    /** Length of the recording in seconds, using the engine's frame rate. */
    val lengthSeconds: Double get() = recording.length / frameRate

    init {
        addPort(amplitude)
        amplitude.setup(0.0, 1.0, 2.0)
        recording.replenish()
    }

    // Generate even when nothing downstream is pulling, so that recording works with the
    // output unconnected.
    override val isStartRequired: Boolean
        get() = true

    /**
     * Starts recording the input into a new [SegmentedFloatArray], ensuring any readers referencing
     * a previous recording are not disturbed.
     */
    fun record() {
        val oldRecording = recording
        val newRecording = SegmentedFloatArray(segmentSize, poolCapacity)
        newRecording.replenish()
        val copyCount = cursor.coerceIn(0, oldRecording.length)
        if (copyCount > 0) {
            var remaining = copyCount
            while (remaining > 0) {
                val chunk = minOf(remaining, newRecording.segmentSize)
                newRecording.extend(chunk)
                remaining -= chunk
            }
            val buf = FloatArray(copyCount)
            oldRecording.read(0, buf, 0, copyCount)
            newRecording.write(0, buf)
            newRecording.replenish()
        }
        applyToAudioThread {
            recording = newRecording
            mode = Mode.RECORDING
        }
    }

    /** Starts playing to the output from the current [cursor]. */
    fun play() = applyToAudioThread { mode = Mode.PLAYING }

    /** Stops recording or playback, leaving [cursor] where it is. */
    fun pause() = applyToAudioThread { mode = Mode.PAUSED }

    /** Moves the cursor back to the start of the recording. */
    fun rewind() = applyToAudioThread { cursor = 0 }

    /** Moves the cursor to [position], which is clamped to the recorded range. */
    fun seek(position: Int) = applyToAudioThread {
        cursor = position.coerceIn(0, recording.length)
    }

    /**
     * Refills the recording's segment pool. Call periodically from a background coroutine while
     * recording so that [generate] never has to allocate. Cheap when the pool is already full.
     */
    fun replenish() = recording.replenish()

    /**
     * Applies a transport change on the audio thread when one is running, so that it cannot
     * interleave with a block that is midway through advancing the cursor.
     *
     * When the engine is absent or not yet running, the change is applied directly instead.
     * Nothing is generating in that case, so there is nothing to race with, and queueing would
     * mean a command that never runs — which matters on the web, where audio cannot start until
     * the user has interacted with the page.
     */
    private fun applyToAudioThread(action: () -> Unit) {
        val engine = synthesisEngine
        if (engine != null && engine.isRunning) engine.queueCommand(action) else action()
    }

    override fun generate() {
        val outputs = output.getValues()
        when (mode) {
            Mode.RECORDING -> generateRecording(outputs)
            Mode.PLAYING -> generatePlayback(outputs)
            Mode.PAUSED -> outputs.fill(0f, 0, Synthesizer.FRAMES_PER_BLOCK)
        }
    }

    private fun generateRecording(outputs: FloatArray) {
        val inputs = input.getValues()
        // Only extend past what has already been recorded, so recording from an earlier cursor
        // overwrites existing material instead of growing the array.
        val shortfall = cursor + Synthesizer.FRAMES_PER_BLOCK - recording.length
        if (shortfall > 0) recording.extend(shortfall)

        var index = cursor
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            recording[index++] = inputs[i]
            outputs[i] = 0f
        }
        cursor = index
    }

    private fun generatePlayback(outputs: FloatArray) {
        val amplitudes = amplitude.getValues()
        val end = recording.length
        var index = cursor
        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            outputs[i] = if (index < end) recording[index++] * amplitudes[i] else 0f
        }
        cursor = index
        if (index >= end) mode = Mode.PAUSED
    }
}

package com.syntona.core.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.audiobridge.AudioInputBridge
import com.syntona.util.LatencyAnalyzer
import com.syntona.util.LatencyResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

/**
 * Drives a round-trip latency measurement and remembers the answer.
 *
 * The analyzer itself runs on the audio thread, one frame at a time. This object installs it,
 * waits for the recording to fill, then does the correlation work off the audio thread and stores
 * the result against the pair of devices that produced it.
 */
object LatencyMeasurement {

    enum class Status { IDLE, RUNNING, SUCCEEDED, FAILED }

    var status by mutableStateOf(Status.IDLE)
        private set

    /** How far the current measurement has got, from 0 to 1. */
    var progress by mutableStateOf(0f)
        private set

    var result by mutableStateOf<LatencyResult?>(null)
        private set

    /** Explanation shown when [status] is [Status.FAILED]. */
    var failureMessage by mutableStateOf("")
        private set

    /** Devices the current [result] was measured on. */
    var measuredInputName by mutableStateOf("")
        private set

    var measuredOutputName by mutableStateOf("")
        private set

    val isRunning: Boolean get() = status == Status.RUNNING

    /** True when audio input exists, without which no measurement is possible. */
    fun isSupported(): Boolean = AudioInputBridge.isSupported()

    /**
     * Runs one measurement. Suspends for roughly two seconds while the pulse plays, then a moment
     * longer for the correlation. Safe to cancel: the analyzer is always removed again.
     */
    suspend fun measure() {
        if (status == Status.RUNNING) return
        val bridge = SynthKSyn.ksynAudioBridge

        if (!isSupported()) {
            fail("This platform has no audio input, so latency cannot be measured.")
            return
        }
        if (bridge.inputBridge == null) {
            fail("No audio input is open. Check the input device and microphone permission.")
            return
        }

        status = Status.RUNNING
        failureMessage = ""
        progress = 0f
        result = null

        val sampleRate = bridge.audioBridge.getSampleRate().let { if (it > 0) it else 48000 }
        val analyzer = LatencyAnalyzer(sampleRate)
        val inputName = bridge.activeInputDeviceName
        val outputName = bridge.activeOutputDeviceName

        try {
            bridge.latencyAnalyzer = analyzer

            // Wait for the audio thread to fill the recording. The whole run is bounded so a
            // stalled or silent stream cannot hang here forever.
            val timeoutMillis = (analyzer.totalFrames * 1000L / sampleRate) + TIMEOUT_MARGIN_MILLIS
            var elapsed = 0L
            while (!analyzer.hasEnoughData() && elapsed < timeoutMillis) {
                if (!currentCoroutineContext().isActive) return
                delay(POLL_INTERVAL_MILLIS)
                elapsed += POLL_INTERVAL_MILLIS
                progress = analyzer.progress
            }
            if (!analyzer.hasEnoughData()) {
                fail("Timed out waiting for audio. Is the audio stream running?")
                return
            }
            progress = 1f
        } finally {
            // Always hand the signal path back to the synth.
            bridge.latencyAnalyzer = null
        }

        // Correlation is slow, which is exactly why it does not belong on the audio thread.
        val measured = analyzer.analyze()
        if (measured == null) {
            fail("Could not find the test signal in the recording.")
            return
        }
        if (!measured.isConfident) {
            result = measured
            measuredInputName = inputName
            measuredOutputName = outputName
            fail(
                "Signal too weak to be sure (confidence ${formatConfidence(measured.confidence)}). " +
                    "Turn the volume up, or move the microphone closer to the speaker."
            )
            return
        }

        result = measured
        measuredInputName = inputName
        measuredOutputName = outputName
        AudioSettings.setMeasuredLatencyFrames(inputName, outputName, measured.latencyFrames)
        status = Status.SUCCEEDED
    }

    /** Forgets the current result, without touching what is stored in settings. */
    fun clear() {
        status = Status.IDLE
        result = null
        failureMessage = ""
        progress = 0f
    }

    private fun fail(message: String) {
        failureMessage = message
        status = Status.FAILED
        progress = 0f
    }

    private fun formatConfidence(value: Double): String {
        val hundredths = (value * 100).toInt()
        return "0.${hundredths.toString().padStart(2, '0')}"
    }

    private const val POLL_INTERVAL_MILLIS = 50L
    private const val TIMEOUT_MARGIN_MILLIS = 3000L
}

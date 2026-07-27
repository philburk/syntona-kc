package com.syntona.util

import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Measures round-trip audio latency by playing a burst pattern of white noise and finding where
 * that pattern reappears in the recorded input.
 *
 * This is a Kotlin port of the `WhiteNoiseLatencyAnalyzer` from OboeTester's `LatencyAnalyzer.h`.
 *
 * Usage is split between two threads:
 *
 *  - The audio thread calls [processFrame] once per frame, handing in the input sample and
 *    writing the returned sample to the output. It allocates nothing.
 *  - When [hasEnoughData] becomes true, some other thread calls [analyze], which does the
 *    correlation work. That is far too slow for the audio callback.
 *
 * The measurement needs a physical or virtual loopback: the output has to reach the input, either
 * through a cable, or through speaker and microphone.
 */
class LatencyAnalyzer(
    val sampleRate: Int,
    private val pulseAmplitude: Float = DEFAULT_PULSE_AMPLITUDE,
    randomSeed: Int = DEFAULT_RANDOM_SEED,
    /** Exposed so the trade-off between peak sharpness and noise rejection can be measured. */
    private val fineEnvelopeDecay: Float = FINE_ENVELOPE_DECAY
) {

    enum class State {
        /** Listening to the room so we know what the noise floor is. */
        MEASURING_BACKGROUND,

        /** Emitting the burst pattern and recording everything that comes back. */
        IN_PULSE,

        /** The recording buffer is full and is ready for [analyze]. */
        GOT_DATA,

        /** [analyze] has run. */
        DONE
    }

    private val random = Random(randomSeed)

    /** The signal we emit, and the reference we later search the recording for. */
    private val pulse: FloatArray = generatePulse(pulseLengthFrames(sampleRate))

    /** Long enough for the whole pulse plus the greatest latency we are willing to report. */
    private val recorded = FloatArray(pulse.size + maxLatencyFrames(sampleRate))

    @Volatile
    var state: State = State.MEASURING_BACKGROUND
        private set

    @Volatile
    private var recordedCount = 0

    private var pulseCursor = 0
    private var backgroundCountdown = (sampleRate * BACKGROUND_MEASUREMENT_SECONDS).toInt()
    private var backgroundSumSquares = 0.0
    private var backgroundSumCount = 0L

    /** Noise floor measured before the pulse, as an RMS amplitude. */
    @Volatile
    var backgroundRms: Double = 0.0
        private set

    /** How far the recording has got, from 0 to 1, for a progress indicator. */
    val progress: Float
        get() = recordedCount.toFloat() / recorded.size

    /** True once the recording buffer is full, so [analyze] can run. */
    fun hasEnoughData(): Boolean = recordedCount >= recorded.size

    /** Frames the analyzer needs in total, i.e. how long a measurement takes. */
    val totalFrames: Int
        get() = backgroundCountdownStart + recorded.size

    private val backgroundCountdownStart = (sampleRate * BACKGROUND_MEASUREMENT_SECONDS).toInt()

    /**
     * Processes one frame on the audio thread: takes the sample arriving at the input and returns
     * the sample to send to the output. Allocation free.
     */
    fun processFrame(input: Float): Float {
        return when (state) {
            State.MEASURING_BACKGROUND -> {
                backgroundSumSquares += input.toDouble() * input
                backgroundSumCount++
                if (--backgroundCountdown <= 0) {
                    backgroundRms = sqrt(backgroundSumSquares / max(1L, backgroundSumCount))
                    pulseCursor = 0
                    state = State.IN_PULSE
                }
                0f // stay silent while listening to the room
            }

            State.IN_PULSE -> {
                if (recordedCount < recorded.size) {
                    recorded[recordedCount++] = input
                }
                // Recording continues after the pulse has finished, which is what leaves room for
                // the echo of the pulse to arrive.
                val output = if (pulseCursor < pulse.size) pulse[pulseCursor++] else 0f
                if (recordedCount >= recorded.size) {
                    state = State.GOT_DATA
                }
                output
            }

            State.GOT_DATA, State.DONE -> 0f
        }
    }

    /**
     * Correlates the recorded audio against the pulse to find the round-trip latency.
     *
     * Slow: call it from a background coroutine, never from the audio thread. Returns null if the
     * recording is not complete or holds no usable signal.
     */
    fun analyze(): LatencyResult? {
        if (recordedCount < recorded.size) return null

        // Timed so the cost of the correlation is visible; it is the reason this cannot run on
        // the audio thread. Monotonic so a clock adjustment cannot produce a negative duration.
        val startMark = TimeSource.Monotonic.markNow()

        // Work on copies so the raw recording survives for a second look.
        val coarseSignal = recorded.copyOf()
        val coarseReference = pulse.copyOf()

        val gain = normalizeInPlace(coarseSignal, 1.0f)

        // The two passes want opposite things from the envelope follower, so each gets its own.
        //
        // The coarse pass samples every COARSE_STRIDE frames, so its correlation has to stay
        // smooth over at least that many frames or the search would step straight over the peak.
        // A slow decay turns the noise bursts into broad rectangular blocks, which is exactly
        // that, and it also removes any sensitivity to phase inversion or distortion.
        //
        // The fine pass then has to pin down a single frame, and those broad blocks barely change
        // as it shifts, leaving an almost flat curve with no clear maximum. A much faster decay
        // keeps the detail of the noise itself, which correlates to a sharp peak, while still
        // rectifying the signal so inverted polarity is no problem.
        detectPeaksInPlace(coarseSignal, COARSE_ENVELOPE_DECAY)
        detectPeaksInPlace(coarseReference, COARSE_ENVELOPE_DECAY)

        val fineSignal = recorded.copyOf()
        val fineReference = pulse.copyOf()
        normalizeInPlace(fineSignal, 1.0f)
        detectPeaksInPlace(fineSignal, fineEnvelopeDecay)
        detectPeaksInPlace(fineReference, fineEnvelopeDecay)

        val measurement = measureLatencyFromPulse(
            coarseRecorded = coarseSignal,
            coarsePulse = coarseReference,
            fineRecorded = fineSignal,
            finePulse = fineReference
        ) ?: run {
            state = State.DONE
            return null
        }

        val signalRms = rootMeanSquare(
            recorded,
            measurement.latencyFrames,
            min(pulse.size, recorded.size - measurement.latencyFrames)
        ) / gain

        state = State.DONE
        return LatencyResult(
            latencyFrames = measurement.latencyFrames,
            sampleRate = sampleRate,
            confidence = measurement.peakCorrelation,
            backgroundRms = backgroundRms,
            signalRms = signalRms,
            coarse = measurement.coarse,
            fine = measurement.fine,
            analysisMillis = startMark.elapsedNow().inWholeMicroseconds / 1000.0
        )
    }

    /** Starts over, so the same analyzer can take another measurement. */
    fun reset() {
        recorded.fill(0f)
        recordedCount = 0
        pulseCursor = 0
        backgroundCountdown = backgroundCountdownStart
        backgroundSumSquares = 0.0
        backgroundSumCount = 0
        backgroundRms = 0.0
        state = State.MEASURING_BACKGROUND
    }

    /**
     * Builds the burst pattern. The noise is switched on and off to sharpen the correlation peak,
     * and there are deliberately more silent sections than loud ones so that steady background
     * noise cannot push the correlation above 0.5 on its own.
     */
    private fun generatePulse(lengthFrames: Int): FloatArray {
        val output = FloatArray(lengthFrames)
        val framesPerSection = lengthFrames / PULSE_PATTERN.size
        var index = 0
        for (section in PULSE_PATTERN) {
            repeat(framesPerSection) {
                output[index++] = if (section) {
                    // nextDouble() is in 0..1; centre it so the burst has no DC offset.
                    ((random.nextDouble() * 2.0 - 1.0) * pulseAmplitude).toFloat()
                } else {
                    0f
                }
            }
        }
        // Any frames left over from the integer division stay silent.
        return output
    }

    companion object {
        /** Amplitude of the noise bursts. */
        const val DEFAULT_PULSE_AMPLITUDE = 0.5f

        /** Fixed so a measurement is reproducible; the pattern only has to be noise-like. */
        const val DEFAULT_RANDOM_SEED = 1234567

        /** Total length of the burst pattern. */
        const val PULSE_LENGTH_MILLIS = 500

        /** The largest latency we can report; the recording is this much longer than the pulse. */
        const val MAX_LATENCY_MILLIS = 1000

        /** How long we listen to the room before emitting anything. */
        const val BACKGROUND_MEASUREMENT_SECONDS = 0.5

        /**
         * Envelope decay for the coarse pass. Just under 1.0, so the bursts become broad blocks
         * that a strided search cannot step over.
         */
        const val COARSE_ENVELOPE_DECAY = 0.99f

        /**
         * Envelope decay for the fine pass. Faster than the coarse one, so more of the detail of
         * the noise survives and the correlation peak is better defined.
         *
         * Chosen by measurement rather than intuition. Sweeping the value against simulated
         * loopbacks showed a trade-off: making it much faster does sharpen the peak, but it also
         * keeps the noise, and with a weak echo buried in noise the argmax then wanders by tens
         * of frames. At 0.5 barely two thirds of the hard cases were exact. 0.95 was the only
         * value that stayed frame exact in every case tried while still roughly doubling the
         * peak's prominence compared with the coarse decay of 0.99.
         */
        const val FINE_ENVELOPE_DECAY = 0.95f

        /** Below this, the peak is not convincing enough to report a latency. */
        const val MINIMUM_CONFIDENCE = 0.5

        /**
         * On/off pattern of the noise bursts, with runs that grow in length. The asymmetry gives
         * the correlation a single unambiguous peak rather than a periodic one.
         */
        private val PULSE_PATTERN = booleanArrayOf(
            true, false, false,
            true, true, false, false, false,
            true, true, true, false, false, false, false,
            true, true, true, true, false, false, false, false, false
        )

        fun pulseLengthFrames(sampleRate: Int): Int = sampleRate * PULSE_LENGTH_MILLIS / 1000

        fun maxLatencyFrames(sampleRate: Int): Int = sampleRate * MAX_LATENCY_MILLIS / 1000
    }
}

/** What [LatencyAnalyzer.analyze] found. */
class LatencyResult(
    /** Round-trip latency, in frames. */
    val latencyFrames: Int,
    val sampleRate: Int,
    /** Height of the correlation peak, from 0 to 1. Below 0.5 the result is not trustworthy. */
    val confidence: Double,
    val backgroundRms: Double,
    val signalRms: Double,
    /** Coarse sweep across the whole search range, which shows where the peak was found. */
    val coarse: CorrelationCurve,
    /**
     * Full resolution sweep of a small window around the peak, which shows how precisely the
     * exact frame could be located. Empty if the fine pass could not run.
     */
    val fine: CorrelationCurve,
    /** Wall time the correlation took, once the recording was complete. */
    val analysisMillis: Double = 0.0
) {
    val latencyMillis: Double
        get() = latencyFrames * 1000.0 / sampleRate

    /** True when the correlation peak is strong enough to believe. */
    val isConfident: Boolean
        get() = confidence >= LatencyAnalyzer.MINIMUM_CONFIDENCE

    override fun toString(): String =
        "LatencyResult(frames=$latencyFrames, millis=$latencyMillis, confidence=$confidence)"
}

// ---------------------------------------------------------------------------------------------
// The DSP below is deliberately free of any object state so it can be tested on its own.
// ---------------------------------------------------------------------------------------------

/** Correlation values sampled at regular frame offsets, ready to be charted. */
class CorrelationCurve(
    val values: FloatArray,
    /** Frame offset of `values[0]`. */
    val firstFrame: Int,
    /** Frames between successive values. */
    val stride: Int
) {
    val lastFrame: Int
        get() = firstFrame + (values.size - 1).coerceAtLeast(0) * stride

    /** Index into [values] nearest to [frame], or -1 if it falls outside the curve. */
    fun indexOfFrame(frame: Int): Int {
        if (values.isEmpty() || stride <= 0) return -1
        val index = (frame - firstFrame) / stride
        return if (index in values.indices) index else -1
    }
}

internal class LatencyMeasurement(
    val latencyFrames: Int,
    val peakCorrelation: Double,
    val coarse: CorrelationCurve,
    val fine: CorrelationCurve
)

/**
 * Normalized cross correlation of [a] starting at [aOffset] against [b], over [windowSize] frames
 * and sampling every [stride]th frame.
 *
 * @return a value between -1.0 and 1.0
 */
internal fun calculateNormalizedCorrelation(
    a: FloatArray,
    aOffset: Int,
    b: FloatArray,
    windowSize: Int,
    stride: Int
): Float {
    var sumProducts = 0.0f
    var sumSquares = 0.0f
    var i = 0
    while (i < windowSize) {
        val s1 = a[aOffset + i]
        val s2 = b[i]
        sumProducts += s1 * s2
        sumSquares += (s1 * s1) + (s2 * s2)
        i += stride
    }
    // Guard against a silent window, where the ratio would be meaningless.
    return if (sumSquares >= 1.0e-9f) 2.0f * sumProducts / sumSquares else 0.0f
}

/** Envelope follower that rides over the peaks, rewriting [data] in place. */
internal fun detectPeaksInPlace(data: FloatArray, decay: Float) {
    var level = 0.0f
    for (i in data.indices) {
        level *= decay
        val input = abs(data[i])
        if (input > level) level = input
        data[i] = level
    }
}

/** Scales [data] so its peak equals [target], returning the gain that was applied. */
internal fun normalizeInPlace(data: FloatArray, target: Float): Float {
    var maxValue = 1.0e-9f
    for (sample in data) {
        val magnitude = abs(sample)
        if (magnitude > maxValue) maxValue = magnitude
    }
    val gain = target / maxValue
    for (i in data.indices) {
        data[i] *= gain
    }
    return gain
}

internal fun rootMeanSquare(data: FloatArray, offset: Int, count: Int): Double {
    if (count <= 0) return 0.0
    var sum = 0.0
    for (i in offset until offset + count) {
        val sample = data[i].toDouble()
        sum += sample * sample
    }
    return sqrt(sum / count)
}

/**
 * Searches a window of [recorded] for [pulse], returning the offset of the best match.
 *
 * @param recordedOffset first frame of [recorded] to test
 * @param windowSize how many frames of offsets to test
 * @param stride test every [stride]th offset, and every [stride]th sample within each comparison
 */
internal fun measureLatencyFromPulsePartial(
    recorded: FloatArray,
    recordedOffset: Int,
    windowSize: Int,
    pulse: FloatArray,
    stride: Int
): PartialMeasurement? {
    if (recordedOffset < 0 || recordedOffset + windowSize + pulse.size > recorded.size) return null

    val numCorrelations = windowSize / stride
    if (numCorrelations < MIN_CORRELATIONS) return null

    val correlations = FloatArray(numCorrelations)
    for (i in 0 until numCorrelations) {
        correlations[i] = calculateNormalizedCorrelation(
            a = recorded,
            aOffset = (i * stride) + recordedOffset,
            b = pulse,
            windowSize = pulse.size,
            stride = stride
        )
    }

    var peakCorrelation = 0.0f
    var peakIndex = -1
    for (i in 0 until numCorrelations) {
        val magnitude = abs(correlations[i])
        if (magnitude > peakCorrelation) {
            peakCorrelation = magnitude
            peakIndex = i
        }
    }
    if (peakIndex < 0) return null

    return PartialMeasurement(
        latencyFrames = recordedOffset + (peakIndex * stride),
        peakCorrelation = peakCorrelation.toDouble(),
        curve = CorrelationCurve(correlations, recordedOffset, stride)
    )
}

internal class PartialMeasurement(
    val latencyFrames: Int,
    val peakCorrelation: Double,
    val curve: CorrelationCurve
)

/**
 * Finds the latency in two passes: a coarse sweep of the whole recording that skips most samples,
 * then a full resolution sweep of a small window around whatever the coarse pass found. Searching
 * every offset at full resolution would be about [COARSE_STRIDE] squared times more work.
 *
 * The returned measurement takes its latency from the fine pass but carries the *coarse*
 * correlations for display. The fine window covers only [FINE_WINDOW_SIZE] frames out of a pulse
 * tens of thousands of frames long, so after the envelope follower has smoothed the bursts into
 * blocks the correlation barely changes across it — a nearly flat line with no visible peak. The
 * coarse sweep spans the whole search range and shows the peak that was actually found.
 */
internal fun measureLatencyFromPulse(
    coarseRecorded: FloatArray,
    coarsePulse: FloatArray,
    fineRecorded: FloatArray,
    finePulse: FloatArray
): LatencyMeasurement? {
    val coarse = measureLatencyFromPulsePartial(
        recorded = coarseRecorded,
        recordedOffset = 0,
        windowSize = coarseRecorded.size - coarsePulse.size,
        pulse = coarsePulse,
        stride = COARSE_STRIDE
    ) ?: return null

    val coarseOnly = LatencyMeasurement(
        latencyFrames = coarse.latencyFrames,
        peakCorrelation = coarse.peakCorrelation,
        coarse = coarse.curve,
        fine = CorrelationCurve(FloatArray(0), 0, FINE_STRIDE)
    )

    // Clamp so the fine window cannot run off either end of the recording. The C original does
    // not clamp the upper end, which can read past the buffer when the peak is near the end.
    val highestValidOffset = fineRecorded.size - finePulse.size - FINE_WINDOW_SIZE
    if (highestValidOffset < 0) return coarseOnly
    val fineOffset = (coarse.latencyFrames - (FINE_WINDOW_SIZE / 2))
        .coerceIn(0, highestValidOffset)

    val fine = measureLatencyFromPulsePartial(
        recorded = fineRecorded,
        recordedOffset = fineOffset,
        windowSize = FINE_WINDOW_SIZE,
        pulse = finePulse,
        stride = FINE_STRIDE
    ) ?: return coarseOnly

    return LatencyMeasurement(
        latencyFrames = fine.latencyFrames,
        peakCorrelation = fine.peakCorrelation,
        coarse = coarse.curve,
        fine = fine.curve
    )
}

/** Skip this many frames in the first pass. */
internal const val COARSE_STRIDE = 16

/** Width of the full resolution search around the coarse result. */
internal const val FINE_WINDOW_SIZE = COARSE_STRIDE * 8

internal const val FINE_STRIDE = 1

/** Fewer offsets than this and there is not enough of a curve to find a peak in. */
private const val MIN_CORRELATIONS = 10

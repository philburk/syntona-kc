package com.syntona.util

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LatencyAnalyzerTest {

    private val sampleRate = 48000

    /**
     * A loopback that returns each output sample [delayFrames] later, optionally attenuated and
     * with background noise added, standing in for a real cable or a speaker and microphone.
     */
    private class DelayLine(
        val delayFrames: Int,
        val gain: Float = 1.0f,
        val noiseAmplitude: Float = 0.0f,
        val invert: Boolean = false,
        val seed: Int = 99
    )

    /**
     * Runs a whole measurement through [loopback] and returns the result.
     *
     * The delay is applied by keeping every emitted sample and feeding back the one from exactly
     * [DelayLine.delayFrames] frames earlier. An earlier version put a circular buffer inside the
     * feedback loop, which contributed two frames of its own and so biased every measurement by
     * +2; driving it from a full history keeps the ground truth exact.
     */
    private fun measure(analyzer: LatencyAnalyzer, loopback: DelayLine): LatencyResult? {
        val limit = analyzer.totalFrames + loopback.delayFrames + sampleRate
        val emitted = FloatArray(limit)
        val random = Random(loopback.seed)
        val polarity = if (loopback.invert) -1f else 1f
        var frame = 0
        while (!analyzer.hasEnoughData() && frame < limit) {
            val echoed = if (frame >= loopback.delayFrames) {
                emitted[frame - loopback.delayFrames]
            } else {
                0f
            }
            val noise = if (loopback.noiseAmplitude > 0f) {
                ((random.nextDouble() * 2.0 - 1.0) * loopback.noiseAmplitude).toFloat()
            } else {
                0f
            }
            emitted[frame] = analyzer.processFrame(echoed * loopback.gain * polarity + noise)
            frame++
        }
        assertTrue(analyzer.hasEnoughData(), "analyzer never filled its recording buffer")
        return analyzer.analyze()
    }

    @Test
    fun testMeasuresAKnownDelay() {
        val delayFrames = sampleRate * 100 / 1000 // 100 ms
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(analyzer, DelayLine(delayFrames))

        assertNotNull(result)
        assertTrue(result.isConfident, "confidence too low: ${result.confidence}")
        assertEquals(
            delayFrames.toDouble(),
            result.latencyFrames.toDouble(),
            1.0,
            "measured ${result.latencyFrames} frames, expected $delayFrames"
        )
    }

    @Test
    fun testMeasuresRandomDelaysAcrossTheSupportedRange() {
        // The range suggested for validating the DSP: 20 ms to 500 ms.
        val random = Random(20250726)
        repeat(12) { trial ->
            val delayMillis = 20 + random.nextInt(481) // 20..500
            val delayFrames = sampleRate * delayMillis / 1000
            val analyzer = LatencyAnalyzer(sampleRate)
            val result = measure(analyzer, DelayLine(delayFrames, seed = trial))

            assertNotNull(result, "trial $trial at $delayMillis ms returned no result")
            assertTrue(
                result.isConfident,
                "trial $trial at $delayMillis ms: confidence ${result.confidence}"
            )
            assertEquals(
                delayFrames.toDouble(),
                result.latencyFrames.toDouble(),
                1.0,
                "trial $trial at $delayMillis ms: measured ${result.latencyFrames}, " +
                    "expected $delayFrames"
            )
        }
    }

    @Test
    fun testMillisecondsAgreeWithFrames() {
        val delayFrames = sampleRate * 250 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(analyzer, DelayLine(delayFrames))

        assertNotNull(result)
        assertEquals(250.0, result.latencyMillis, 1.0)
        assertEquals(
            result.latencyFrames * 1000.0 / sampleRate,
            result.latencyMillis,
            1.0e-9
        )
    }

    @Test
    fun testWorksWithAQuietLoopback() {
        // A microphone picking up a speaker across the room is much quieter than a cable.
        val delayFrames = sampleRate * 60 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(analyzer, DelayLine(delayFrames, gain = 0.05f))

        assertNotNull(result)
        assertTrue(result.isConfident, "confidence too low: ${result.confidence}")
        assertEquals(delayFrames.toDouble(), result.latencyFrames.toDouble(), 1.0)
    }

    @Test
    fun testWorksWithBackgroundNoise() {
        val delayFrames = sampleRate * 80 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(
            analyzer,
            DelayLine(delayFrames, gain = 0.5f, noiseAmplitude = 0.02f)
        )

        assertNotNull(result)
        assertTrue(result.isConfident, "confidence too low: ${result.confidence}")
        assertEquals(delayFrames.toDouble(), result.latencyFrames.toDouble(), 1.0)
        assertTrue(result.backgroundRms > 0.0, "should have measured a noise floor")
    }

    @Test
    fun testWorksWhenTheLoopbackInvertsPhase() {
        // Some interfaces invert polarity. The envelope follower is what makes this survivable.
        val delayFrames = sampleRate * 120 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(analyzer, DelayLine(delayFrames, invert = true))

        assertNotNull(result)
        assertTrue(result.isConfident, "confidence too low: ${result.confidence}")
        assertEquals(delayFrames.toDouble(), result.latencyFrames.toDouble(), 1.0)
    }

    @Test
    fun testSilentInputIsNotReportedAsConfident() {
        // Nothing connected: the analyzer must not invent a latency.
        val analyzer = LatencyAnalyzer(sampleRate)
        var guard = 0
        while (!analyzer.hasEnoughData() && guard++ < analyzer.totalFrames + sampleRate) {
            analyzer.processFrame(0f)
        }
        val result = analyzer.analyze()
        if (result != null) {
            assertTrue(
                !result.isConfident,
                "silence should not produce a confident result, got ${result.confidence}"
            )
        }
    }

    @Test
    fun testStateMachineProgressesInOrder() {
        val analyzer = LatencyAnalyzer(sampleRate)
        assertEquals(LatencyAnalyzer.State.MEASURING_BACKGROUND, analyzer.state)

        // Stays silent and in the background state until it has heard enough of the room.
        repeat(100) {
            assertEquals(0f, analyzer.processFrame(0.01f), "should be silent while listening")
        }
        assertEquals(LatencyAnalyzer.State.MEASURING_BACKGROUND, analyzer.state)

        val delayFrames = sampleRate / 100
        val limit = analyzer.totalFrames + sampleRate
        val emitted = FloatArray(limit)
        var sawPulseState = false
        var frame = 0
        while (!analyzer.hasEnoughData() && frame < limit) {
            val echoed = if (frame >= delayFrames) emitted[frame - delayFrames] else 0f
            emitted[frame] = analyzer.processFrame(echoed)
            frame++
            if (analyzer.state == LatencyAnalyzer.State.IN_PULSE) sawPulseState = true
        }
        assertTrue(sawPulseState, "should have passed through IN_PULSE")
        assertEquals(LatencyAnalyzer.State.GOT_DATA, analyzer.state)

        analyzer.analyze()
        assertEquals(LatencyAnalyzer.State.DONE, analyzer.state)
    }

    @Test
    fun testAnalyzeReturnsNullBeforeTheRecordingIsFull() {
        val analyzer = LatencyAnalyzer(sampleRate)
        assertNull(analyzer.analyze(), "should refuse to analyze an incomplete recording")
    }

    @Test
    fun testResetAllowsASecondMeasurement() {
        val firstDelay = sampleRate * 50 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val first = measure(analyzer, DelayLine(firstDelay))
        assertNotNull(first)
        assertEquals(firstDelay.toDouble(), first.latencyFrames.toDouble(), 1.0)

        analyzer.reset()
        assertEquals(LatencyAnalyzer.State.MEASURING_BACKGROUND, analyzer.state)

        val secondDelay = sampleRate * 300 / 1000
        val second = measure(analyzer, DelayLine(secondDelay))
        assertNotNull(second)
        assertEquals(secondDelay.toDouble(), second.latencyFrames.toDouble(), 1.0)
    }

    @Test
    fun testProgressAdvancesFromZeroToOne() {
        val analyzer = LatencyAnalyzer(sampleRate)
        assertEquals(0f, analyzer.progress)
        measure(analyzer, DelayLine(sampleRate / 50))
        assertEquals(1f, analyzer.progress, 0.001f)
    }

    @Test
    fun testCorrelationsAreReturnedForCharting() {
        val delayFrames = sampleRate * 150 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(analyzer, DelayLine(delayFrames))

        assertNotNull(result)
        assertTrue(result.coarse.values.isNotEmpty(), "should return correlation data")
        // The chart data comes from the coarse sweep, which spans the whole search range.
        assertEquals(COARSE_STRIDE, result.coarse.stride)
        assertEquals(0, result.coarse.firstFrame)

        // The peak of the returned curve should land on the reported latency, to within the
        // resolution of the coarse sweep.
        var peakIndex = 0
        for (i in result.coarse.values.indices) {
            if (abs(result.coarse.values[i]) > abs(result.coarse.values[peakIndex])) peakIndex = i
        }
        val peakFrame = result.coarse.firstFrame + peakIndex * result.coarse.stride
        assertEquals(
            result.latencyFrames.toDouble(),
            peakFrame.toDouble(),
            COARSE_STRIDE.toDouble(),
            "chart peak should agree with the reported latency"
        )
    }

    @Test
    fun testAnalysisDurationIsReported() {
        val delayFrames = sampleRate * 100 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(analyzer, DelayLine(delayFrames))

        assertNotNull(result)
        assertTrue(
            result.analysisMillis > 0.0,
            "analysis time should be measured, was ${result.analysisMillis}"
        )
        // The correlation is heavy but nowhere near this slow; a larger value would mean the
        // timer is measuring something other than the analysis.
        assertTrue(
            result.analysisMillis < 60_000.0,
            "analysis time looks wrong: ${result.analysisMillis} ms"
        )
    }

    @Test
    fun testFinePassUsesASharperEnvelopeThanTheCoarsePass() {
        // The fine pass gets its own, faster, envelope follower. With the coarse decay the fine
        // correlations were nearly identical, so the exact frame was barely determined at all.
        val delayFrames = sampleRate * 100 / 1000

        fun peakMargin(decay: Float): Float {
            val analyzer = LatencyAnalyzer(sampleRate, fineEnvelopeDecay = decay)
            val result = measure(analyzer, DelayLine(delayFrames))
            assertNotNull(result)
            val magnitudes = result.fine.values.map { abs(it) }
            // How far the curve dips below its peak: larger means a better defined maximum.
            return 1f - (magnitudes.min() / magnitudes.max())
        }

        val withCoarseDecay = peakMargin(LatencyAnalyzer.COARSE_ENVELOPE_DECAY)
        val withFineDecay = peakMargin(LatencyAnalyzer.FINE_ENVELOPE_DECAY)
        assertTrue(
            withFineDecay > withCoarseDecay * 1.5f,
            "fine decay should give a markedly better defined peak, " +
                "got $withFineDecay vs $withCoarseDecay"
        )
    }

    @Test
    fun testBothCurvesAreReturned() {
        val delayFrames = sampleRate * 100 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(analyzer, DelayLine(delayFrames))

        assertNotNull(result)
        // The UI offers a choice of either, so both have to be populated and self describing.
        assertTrue(result.coarse.values.isNotEmpty())
        assertTrue(result.fine.values.isNotEmpty())
        assertEquals(COARSE_STRIDE, result.coarse.stride)
        assertEquals(FINE_STRIDE, result.fine.stride)
        assertTrue(
            result.fine.values.size < result.coarse.values.size,
            "the fine curve covers a small window, the coarse one the whole range"
        )
        // The reported latency must fall inside the fine window, which is centred on it.
        assertTrue(
            result.latencyFrames in result.fine.firstFrame..result.fine.lastFrame,
            "latency ${result.latencyFrames} should lie within the fine window " +
                "${result.fine.firstFrame}..${result.fine.lastFrame}"
        )
        assertEquals(result.latencyFrames, result.fine.firstFrame + result.fine.indexOfFrame(result.latencyFrames))
    }

    @Test
    fun testChartDataHasRealStructureNotAFlatLine() {
        // Regression test. The fine window covers so little of the pulse that its correlations
        // are almost identical, which drew as a flat line. The charted data must instead show
        // the peak rising well above the surrounding values.
        val delayFrames = sampleRate * 100 / 1000
        val analyzer = LatencyAnalyzer(sampleRate)
        val result = measure(analyzer, DelayLine(delayFrames))

        assertNotNull(result)
        val magnitudes = result.coarse.values.map { abs(it) }
        val peak = magnitudes.max()
        val median = magnitudes.sorted()[magnitudes.size / 2]

        assertTrue(peak > 0.5f, "peak should be strong, was $peak")
        assertTrue(
            median < peak / 4f,
            "typical value ($median) should be far below the peak ($peak), " +
                "otherwise the chart is a flat line"
        )
    }
}

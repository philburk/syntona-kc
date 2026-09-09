package com.syntona.unitgen

import com.softsynth.ksyn.Synthesizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZeroCrossingDetectorTest {

    private val block = Synthesizer.FRAMES_PER_BLOCK

    /** Feeds one block of [values] into the input and generates. Returns the output block. */
    private fun ZeroCrossingDetector.pump(values: FloatArray): FloatArray {
        val inSamples = input.getValues()
        for (i in 0 until block) inSamples[i] = values[i]
        generate()
        return output.getValues().copyOf(block)
    }

    @Test
    fun testBasicPositiveZeroCrossingDetection() {
        val detector = ZeroCrossingDetector()
        detector.hysteresis.set(0.01)
        detector.holdOffMs.set(0.0)

        // Step 1: Negative signal to arm (-0.5)
        val negBlock = FloatArray(block) { -0.5f }
        val out1 = detector.pump(negBlock)
        assertEquals(0, detector.count)
        for (sample in out1) assertEquals(0.0f, sample)

        // Step 2: Signal crosses from negative to positive at index 3
        val crossBlock = floatArrayOf(-0.2f, -0.1f, -0.05f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f)
        val out2 = detector.pump(crossBlock)

        assertEquals(1, detector.count)
        assertEquals(0.0f, out2[0])
        assertEquals(0.0f, out2[1])
        assertEquals(0.0f, out2[2])
        assertEquals(1.0f, out2[3], "Should output 1.0f impulse on crossing sample")
        assertEquals(0.0f, out2[4])
        assertEquals(0.0f, out2[5])
        assertEquals(0.0f, out2[6])
        assertEquals(0.0f, out2[7])
    }

    @Test
    fun testNegativeCrossingDoesNotTrigger() {
        val detector = ZeroCrossingDetector()
        detector.hysteresis.set(0.01)

        // Signal going from positive to negative
        val fallingBlock = floatArrayOf(0.5f, 0.3f, 0.1f, -0.1f, -0.3f, -0.5f, -0.6f, -0.7f)
        val out = detector.pump(fallingBlock)

        assertEquals(0, detector.count)
        for (sample in out) assertEquals(0.0f, sample)
    }

    @Test
    fun testHysteresisRejectsChatterAroundZero() {
        val detector = ZeroCrossingDetector()
        detector.hysteresis.set(0.1) // Hysteresis threshold = 0.1
        detector.holdOffMs.set(0.0)

        // 1. Arm by going below -0.1
        detector.pump(FloatArray(block) { -0.2f })
        assertEquals(0, detector.count)

        // 2. Cross positive above +0.1 at index 2
        val triggerBlock = floatArrayOf(-0.05f, 0.05f, 0.15f, 0.12f, 0.08f, 0.05f, 0.02f, 0.05f)
        val out = detector.pump(triggerBlock)
        assertEquals(1, detector.count)
        assertEquals(1.0f, out[2])

        // 3. Oscillate around zero within [-0.08, +0.08] without falling below -0.1
        val noiseBlock = floatArrayOf(-0.05f, 0.08f, -0.02f, 0.05f, -0.08f, 0.07f, -0.04f, 0.06f)
        val outNoise = detector.pump(noiseBlock)
        assertEquals(1, detector.count, "Should NOT trigger again because it did not drop below -hysteresis")
        for (sample in outNoise) assertEquals(0.0f, sample)

        // 4. Now drop below -0.1 to re-arm
        detector.pump(FloatArray(block) { -0.15f })
        assertEquals(1, detector.count)

        // 5. Cross positive again
        val outReArmed = detector.pump(FloatArray(block) { 0.2f })
        assertEquals(2, detector.count, "Should trigger after re-arming")
        assertEquals(1.0f, outReArmed[0])
    }

    @Test
    fun testHoldOffRefractoryPeriod() {
        val detector = ZeroCrossingDetector()
        detector.hysteresis.set(0.01)
        // Set holdOff to 16 frames: (16.0 / frameRate) * 1000.0 ms
        val holdOffMs = (16.0 / detector.frameRate) * 1000.0
        detector.holdOffMs.set(holdOffMs)

        // Arm and trigger first crossing at block 1
        detector.pump(FloatArray(block) { -0.5f })
        val out1 = detector.pump(FloatArray(block) { 0.5f })
        assertEquals(1, detector.count)
        assertEquals(1.0f, out1[0])

        // Drop below -0.01 to re-arm within block 2 (< 16 frames since trigger)
        detector.pump(FloatArray(block) { -0.5f })

        // Attempt second crossing at block 3 (elapsed frames ~ 16)
        // At frame index 0 of block 3, elapsed frames since trigger = 8 + 8 = 16.
        // Let's test earlier: within block 2 at index 4 (only 12 frames since trigger)
        detector.reset()
        detector.holdOffMs.set(holdOffMs)

        // Block 1: arm at sample 0..3 (-0.5f), trigger at sample 4 (+0.5f)
        val b1 = floatArrayOf(-0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val outB1 = detector.pump(b1)
        assertEquals(1, detector.count)
        assertEquals(1.0f, outB1[4])

        // Block 2: sample 0..1 goes negative (-0.5f, arms), sample 2 goes positive (+0.5f).
        // Elapsed frames at sample 2 = (8 - 4) + 2 = 6 frames (< 16 frames holdOff).
        val b2 = floatArrayOf(-0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val outB2 = detector.pump(b2)
        assertEquals(1, detector.count, "Should not fire within hold-off window")
        for (s in outB2) assertEquals(0.0f, s)
    }

    @Test
    fun testCallbackInvokedOnCrossing() {
        val detector = ZeroCrossingDetector()
        detector.hysteresis.set(0.01)
        detector.holdOffMs.set(0.0)

        var callbackTime = -1.0
        var callbackCount = -1L
        detector.onZeroCrossing = { time, count ->
            callbackTime = time
            callbackCount = count
        }

        detector.pump(FloatArray(block) { -0.5f })
        val blockWithCrossing = floatArrayOf(-0.2f, -0.1f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)
        detector.pump(blockWithCrossing)

        val expectedTime = 2 * detector.framePeriod
        assertEquals(expectedTime, callbackTime, 1e-9)
        assertEquals(1L, callbackCount)
    }

    @Test
    fun testReset() {
        val detector = ZeroCrossingDetector()
        detector.pump(FloatArray(block) { -0.5f })
        detector.pump(FloatArray(block) { 0.5f })
        assertEquals(1, detector.count)

        detector.reset()
        assertEquals(0, detector.count)
    }
}

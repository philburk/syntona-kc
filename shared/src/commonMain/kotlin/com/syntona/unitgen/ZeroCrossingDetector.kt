package com.syntona.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.unitgen.UnitGenerator
import kotlin.math.abs

/**
 * An audio-rate unit generator that detects positive zero crossings with hysteresis.
 *
 * When a positive zero crossing is detected:
 * - Emits an impulse of 1.0f on [output] for that sample (0.0f otherwise).
 * - Increments [count].
 * - Invokes [onZeroCrossing] callback if set.
 *
 * Hysteresis logic:
 * - Arms when input falls below `-hysteresis`.
 * - Fires when armed and input rises above `+hysteresis`.
 * - Disarms immediately upon firing so noise between `-hysteresis` and `+hysteresis`
 *   cannot cause chatter or multiple triggers.
 *
 * An optional [holdOff] duration in seconds enforces a minimum refractory period
 * between triggers.
 */
class ZeroCrossingDetector : UnitGenerator() {

    val input = UnitInputPort("Input")
    val output = UnitOutputPort("Output")
    val hysteresis = UnitInputPort("Hysteresis", 0.01)
    val holdOffMs = UnitInputPort("HoldOffMs", 40.0)

    /** Total number of detected zero crossings since start or last reset. */
    var count: Long = 0L
        private set

    /**
     * Callback invoked synchronously on the audio thread/coroutine when a crossing occurs.
     * Arguments:
     * - `sampleIndex`: frame index of the zero crossing frame
     * - `count`: cumulative crossing count
     */
    var onZeroCrossing: ((synthTime: Double, count: Long) -> Unit)? = null

    private var armed = false
    private var framesSinceTrigger = Int.MAX_VALUE / 2

    init {
        addPort(input)
        addPort(output)
        addPort(hysteresis)
        addPort(holdOffMs)
    }

    override val isStartRequired: Boolean
        get() = true

    fun reset() {
        count = 0L
        armed = false
        framesSinceTrigger = Int.MAX_VALUE / 2
    }

    override fun generate() {
        val inSamples = input.getValues()
        val outSamples = output.getValues()
        val hystSamples = hysteresis.getValues()
        val holdOffMsSamples = holdOffMs.getValues()

        val holdOffMsValue = holdOffMsSamples[0].toDouble().coerceAtLeast(0.0)
        val holdOffFrames = ((holdOffMsValue * 0.001) * frameRate).toInt()

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val sample = inSamples[i]
            val h = abs(hystSamples[i])
            var impulse = 0.0f

            if (framesSinceTrigger < Int.MAX_VALUE - 1) {
                framesSinceTrigger++
            }

            if (sample < -h) {
                armed = true
            } else if (armed && sample > h) {
                if (framesSinceTrigger >= holdOffFrames) {
                    impulse = 1.0f
                    count++
                    framesSinceTrigger = 0
                    val frame = (synthesisEngine?.frameCount ?: 0L) + i
                    val time = frame * framePeriod
                    onZeroCrossing?.invoke(time, count)
                }
                armed = false
            }

            outSamples[i] = impulse
        }
    }
}

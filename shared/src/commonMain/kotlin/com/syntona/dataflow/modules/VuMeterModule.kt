package com.syntona.dataflow.modules

import com.softsynth.ksyn.unitgen.PeakFollower
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * VU meter module for monitoring audio signal levels.
 *
 * Uses a KSyn [PeakFollower] per channel to track audio amplitude.
 * - "vu1": Mono (1 channel, input jack "in")
 * - "vu2": Stereo (2 channels, input jacks "in0", "in1")
 *
 * Input jacks default to min 0.0 and max 1.0. The maximum can be edited via "Edit Jacks...".
 */
open class VuMeterModule(
    val numChannels: Int = 1
) : Module(if (numChannels == 2) "vu2" else "vu1") {

    override val isAllowedInPolyPatch: Boolean
        get() = false

    val followers = Array(numChannels) { PeakFollower() }

    init {
        for (i in 0 until numChannels) {
            val follower = followers[i]
            SynthKSyn.synth.add(follower)
            val jackName = if (numChannels == 1) "in" else "in$i"
            val jack = addAudioInput(jackName, follower.input, 0)
            jack.min = 0.0
            jack.max = 1.0
            follower.start()
        }
    }

    override fun execute(triggerMessage: Message) {}

    override fun teardown() {
        for (follower in followers) {
            follower.stop()
            SynthKSyn.synth.remove(follower)
        }
        super.teardown()
    }
}

class Vu1Module : VuMeterModule(1)
class Vu2Module : VuMeterModule(2)

const val VU_METER_FLOOR_DB = -60f
const val VU_METER_CEILING_DB = 12f
const val VU_METER_RANGE_DB = VU_METER_CEILING_DB - VU_METER_FLOOR_DB // 72 dB

/** 80% mark (-12 dB) where green transitions to orange. */
const val VU_METER_ORANGE_THRESHOLD_FRACTION = (-12f - VU_METER_FLOOR_DB) / VU_METER_RANGE_DB // 48/72 = 2/3 ≈ 0.6667f

/** 0 dB / max threshold where orange transitions to red (over-range). */
const val VU_METER_RED_THRESHOLD_FRACTION = (0f - VU_METER_FLOOR_DB) / VU_METER_RANGE_DB // 60/72 = 5/6 ≈ 0.8333f

/** Number of 30 ms ticks (50 * 30 ms = 1.5 s) to hold the over-range clip indication. */
const val VU_METER_CLIP_HOLD_TICKS = 50

/**
 * Converts a linear amplitude to a 0.0..1.0 graphic display fraction on a logarithmic dB scale
 * with +12 dB headroom over max.
 *
 * @param amplitude The peak amplitude measured from the signal.
 * @param max The maximum level (corresponds to 0 dBFS / full scale).
 * @param floorDb The minimum dynamic range floor in decibels (default -60 dB).
 * @param ceilingDb The maximum dynamic range ceiling in decibels (default +12 dB).
 * @return Graphic level in range 0.0..1.0.
 */
fun amplitudeToGraphicFraction(
    amplitude: Float,
    max: Float,
    floorDb: Float = VU_METER_FLOOR_DB,
    ceilingDb: Float = VU_METER_CEILING_DB
): Float {
    val effectiveMax = if (max > 0f) max else 1f
    val ratio = (amplitude / effectiveMax).coerceAtLeast(0f)
    if (ratio <= 1.0e-5f) return 0f
    val db = 20f * kotlin.math.log10(ratio)
    val totalRange = ceilingDb - floorDb
    return ((db - floorDb) / totalRange).coerceIn(0f, 1f)
}

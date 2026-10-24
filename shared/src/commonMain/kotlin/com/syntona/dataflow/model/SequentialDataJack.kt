package com.syntona.dataflow.model

import com.softsynth.ksyn.data.AudioSampleData
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.data.SequentialData
import com.syntona.core.audio.SynthKSyn.synth
import com.syntona.core.model.ScoreClock

/**
 * Returns this [SequentialData] as a mono [FloatSample].
 * If this is already a mono [FloatSample], returns `this` without copying.
 */
fun SequentialData.toMonoFloatSample(): FloatSample {
    if (this is FloatSample) return toMono()
    val frames = numFrames.coerceAtLeast(0)
    val channels = channelsPerFrame.coerceAtLeast(1)
    val out = FloatSample(frames)
    val rate = (this as? AudioSampleData)?.frameRate?.takeIf { it > 0.0 }
        ?: (this as? com.syntona.util.SegmentedFloatArray)?.frameRate?.takeIf { it > 0.0 }
        ?: synth.frameRate.toDouble()
    out.frameRate = rate
    for (i in 0 until frames) {
        var sum = 0.0f
        for (c in 0 until channels) {
            sum += readSample(i * channels + c)
        }
        out.writeSample(i, sum / channels)
    }
    return out
}

/**
 * Receives a whole [SequentialData] object (FloatSample, recording, SegmentedEnvelope…) and
 * remembers it as [currentData]. It does not queue anything; the module decides what to do
 * with the data.
 */
class SequentialDataInputJack(
    name: String,
    override val module: Module
) : InputJack(name, module) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false

    var currentData: SequentialData? = null
        private set

    override fun receive(msg: Message) {
        if (msg is Message.SequentialDataMessage) {
            currentData = msg.data
        }
        super.receive(msg)
    }

    override fun getValue(): Double = 0.0
}

/** Sends a whole [SequentialData] object, and resends the latest one to each new connection. */
class SequentialDataOutputJack(
    name: String,
    override val module: Module
) : OutputJack(name, module) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false

    var currentData: SequentialData? = null

    override fun send(msg: Message) {
        if (msg is Message.SequentialDataMessage) {
            currentData = msg.data
        }
        super.send(msg)
    }

    override fun connect(inputJack: InputJack) {
        super.connect(inputJack)
        val data = currentData
        if (data != null && inputJack is SequentialDataInputJack) {
            inputJack.receive(Message.SequentialDataMessage(ScoreClock.now(), data))
        }
    }
}

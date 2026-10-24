package com.syntona.dataflow.model

import com.softsynth.ksyn.data.AudioSampleData
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.ports.UnitDataQueuePort
import com.softsynth.ksyn.data.SequentialData
import com.softsynth.ksyn.shared.time.TimeStamp
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

class DataQueueInputJack(
    name: String,
    override val module: Module,
    val dataQueuePort: UnitDataQueuePort? = null,
    val autoQueue: Boolean = dataQueuePort != null
) : InputJack(name, module) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false

    var currentData: SequentialData? = null
        private set

    override fun receive(msg: Message) {
        val data = (msg as? Message.DataQueueMessage)?.data as? SequentialData
        if (data != null) {
            currentData = data
        }
        super.receive(msg)
        if (msg is Message.DataQueueMessage && data != null) {
            // Accept any SequentialData (SegmentedEnvelope, FloatSample, …) since that is
            // what UnitDataQueuePort itself takes.
            val port = dataQueuePort
            if (autoQueue && port != null) {
                when (msg.opcode) {
                    // TODO add simple call to KSyn and use it.
                    Message.DataQueueOpcode.QUEUE -> port.queueImmediate(data, 0, data.numFrames, synth.createTimeStamp())
                    Message.DataQueueOpcode.QUEUE_ON -> port.queueOn(data)
                    Message.DataQueueOpcode.QUEUE_OFF -> port.queueOff(data)
                    Message.DataQueueOpcode.QUEUE_LOOP -> port.queueLoop(data)
                }
            }
        }
    }

    override fun getValue(): Double = 0.0
}

class DataQueueOutputJack(
    name: String,
    override val module: Module
) : OutputJack(name, module) {
    override val isDisplayableValue: Boolean get() = false
    override val isValueDraggable: Boolean get() = false
    override val isModuleEditorVisible: Boolean get() = false

    var currentData: SequentialData? = null

    override fun send(msg: Message) {
        if (msg is Message.DataQueueMessage) {
            currentData = msg.data as? SequentialData
        }
        super.send(msg)
    }

    override fun connect(inputJack: InputJack) {
        super.connect(inputJack)
        val data = currentData
        if (data != null && inputJack is DataQueueInputJack) {
            inputJack.receive(Message.DataQueueMessage(ScoreClock.now(), data, Message.DataQueueOpcode.QUEUE))
        }
    }
}

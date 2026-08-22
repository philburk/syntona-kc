package com.syntona.dataflow.model

import com.softsynth.ksyn.ports.UnitDataQueuePort
import com.softsynth.ksyn.data.SequentialData
import com.softsynth.ksyn.shared.time.TimeStamp
import com.syntona.core.audio.SynthKSyn.synth
import com.syntona.core.model.ScoreClock

class DataQueueInputJack(
    name: String,
    override val module: Module,
    val dataQueuePort: UnitDataQueuePort,
    val autoQueue: Boolean = true
) : InputJack(name, module) {

    var currentData: SequentialData? = null
        private set

    override fun receive(msg: Message) {
        super.receive(msg)
        if (msg is Message.DataQueueMessage) {
            // Accept any SequentialData (SegmentedEnvelope, FloatSample, …) since that is
            // what UnitDataQueuePort itself takes.
            val data = msg.data as? SequentialData ?: return
            currentData = data
            if (autoQueue) {
                when (msg.opcode) {
                    // TODO add simple call to KSyn and use it.
                    Message.DataQueueOpcode.QUEUE -> dataQueuePort.queueImmediate(data, 0, data.numFrames, synth.createTimeStamp())
                    Message.DataQueueOpcode.QUEUE_ON -> dataQueuePort.queueOn(data)
                    Message.DataQueueOpcode.QUEUE_OFF -> dataQueuePort.queueOff(data)
                    Message.DataQueueOpcode.QUEUE_LOOP -> dataQueuePort.queueLoop(data)
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

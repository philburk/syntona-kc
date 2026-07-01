package com.syntona.dataflow.model

import com.softsynth.ksyn.ports.UnitDataQueuePort
import com.softsynth.ksyn.data.SegmentedEnvelope

class DataQueueInputJack(
    name: String,
    override val module: Module,
    val dataQueuePort: UnitDataQueuePort
) : InputJack(name, module) {

    override fun receive(msg: Message) {
        super.receive(msg)
        if (msg is Message.DataQueueMessage) {
            val envelope = msg.data as? SegmentedEnvelope ?: return
            when (msg.opcode) {
                Message.DataQueueOpcode.QUEUE -> dataQueuePort.queue(envelope)
                Message.DataQueueOpcode.QUEUE_ON -> dataQueuePort.queueOn(envelope)
                Message.DataQueueOpcode.QUEUE_OFF -> dataQueuePort.queueOff(envelope)
            }
        }
    }

    override fun getValue(): Double = 0.0
}

class DataQueueOutputJack(
    name: String,
    override val module: Module
) : OutputJack(name, module)

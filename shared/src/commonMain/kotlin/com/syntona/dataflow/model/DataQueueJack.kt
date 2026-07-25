package com.syntona.dataflow.model

import com.softsynth.ksyn.ports.UnitDataQueuePort
import com.softsynth.ksyn.data.SequentialData

class DataQueueInputJack(
    name: String,
    override val module: Module,
    val dataQueuePort: UnitDataQueuePort
) : InputJack(name, module) {

    override fun receive(msg: Message) {
        super.receive(msg)
        if (msg is Message.DataQueueMessage) {
            // Accept any SequentialData (SegmentedEnvelope, FloatSample, …) since that is
            // what UnitDataQueuePort itself takes.
            val data = msg.data as? SequentialData ?: return
            when (msg.opcode) {
                Message.DataQueueOpcode.QUEUE -> dataQueuePort.queue(data)
                Message.DataQueueOpcode.QUEUE_ON -> dataQueuePort.queueOn(data)
                Message.DataQueueOpcode.QUEUE_OFF -> dataQueuePort.queueOff(data)
            }
        }
    }

    override fun getValue(): Double = 0.0
}

class DataQueueOutputJack(
    name: String,
    override val module: Module
) : OutputJack(name, module)

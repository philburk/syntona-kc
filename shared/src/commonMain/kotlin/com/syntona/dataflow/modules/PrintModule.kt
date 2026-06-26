package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Logs the received message to the console.
 */
class PrintModule : Module("print") {
    val input = addInput("in", isKickable = true)

    override fun execute(triggerMessage: Message) {
        val value = when (triggerMessage) {
            is Message.FloatValue -> triggerMessage.value.toString()
            is Message.TextValue -> triggerMessage.value
            is Message.ObjectValue -> triggerMessage.value.toString()
            is Message.DataQueueMessage -> "${triggerMessage.opcode}: ${triggerMessage.data}"
        }
        println("[PrintModule] ${triggerMessage.time}: $value")
    }
}

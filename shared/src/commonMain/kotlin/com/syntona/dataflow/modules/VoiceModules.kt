package com.syntona.dataflow.modules

import androidx.compose.runtime.mutableStateListOf
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Composes a [Message.VoiceMessage] from four inputs: opcode, tag, value, extra.
 * Receiving a message on [tagIn] (kickable) sends the composed message from [output].
 */
class CompoundModule : Module("compound") {
    val opcodeIn = addInput("opcode").also {
        it.min = 0.0
        it.max = (Message.VoiceOpcode.entries.size - 1).toDouble()
    }
    val tagIn = addInput("tag", isKickable = true)
    val valueIn = addInput("value")
    val extraIn = addInput("extra")
    val output = addOutput("out")

    init {
        opcodeIn.setValue(Message.VoiceOpcode.NOTE_ON.ordinal.toDouble())
    }

    var opcode: Message.VoiceOpcode
        get() = Message.VoiceOpcode.entries.getOrElse(opcodeIn.getDoubleValue().toInt()) {
            Message.VoiceOpcode.NOTE_ON
        }
        set(newOpcode) { opcodeIn.setValue(newOpcode.ordinal.toDouble()) }

    override fun execute(triggerMessage: Message) {
        output.send(
            Message.VoiceMessage(
                time = triggerMessage.time,
                opcode = opcode,
                tag = tagIn.getDoubleValue().toInt(),
                value = valueIn.getDoubleValue(),
                extra = extraIn.getDoubleValue()
            )
        )
    }
}

/**
 * Displays the last [capacity] [Message.VoiceMessage]s received, most recent first.
 */
class ShowVoiceModule : Module("showVoice") {
    val input = addInput("in", isKickable = true)

    val capacity = 8
    val messages = mutableStateListOf<Message.VoiceMessage>()

    override fun execute(triggerMessage: Message) {
        if (triggerMessage is Message.VoiceMessage) {
            messages.add(0, triggerMessage)
            while (messages.size > capacity) messages.removeAt(messages.size - 1)
        }
    }
}

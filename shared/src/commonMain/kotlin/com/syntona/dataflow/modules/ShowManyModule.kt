package com.syntona.dataflow.modules

import androidx.compose.runtime.mutableStateListOf
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that keeps a scrolling list of the last [capacity] messages received.
 */
class ShowManyModule : Module("showMany") {
    val input = addInput("in", isKickable = true)

    val capacity = 30
    val messages = mutableStateListOf<String>()

    override fun execute(triggerMessage: Message) {
        while (messages.size >= capacity) {
            messages.removeAt(0)
        }
        messages.add(triggerMessage.toString())
    }
}

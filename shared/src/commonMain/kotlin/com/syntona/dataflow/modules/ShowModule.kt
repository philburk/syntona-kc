package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that displays its received FloatValue on the screen.
 */
class ShowModule : Module("show") {
    val input = addUniversalInput("in", isKickable = true)

    // We use a mutable state to update the UI when a message arrives
    var displayText by mutableStateOf("0")
        private set

    override fun execute(triggerMessage: Message) {
        displayText = triggerMessage.toString()
    }
}

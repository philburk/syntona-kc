package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class KeyMatchModule : Module("keyMatch") {
    val on = addOutput("on")
    val off = addOutput("off")

    var keyChar by mutableStateOf('X')

    override fun execute(triggerMessage: Message) {
        // Triggered by global key events or button interaction, no execution on graph kicks
    }
}

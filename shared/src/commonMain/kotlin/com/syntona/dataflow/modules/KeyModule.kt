package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class KeyModule : Module("key") {
    val on = addOutput("on")
    val off = addOutput("off")

    override fun execute(triggerMessage: Message) {
        // Triggered by global key events, no execution needed on graph kicks
    }
}

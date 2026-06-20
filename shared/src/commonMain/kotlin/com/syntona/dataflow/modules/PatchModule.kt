package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class PatchModule(name: String = "patch") : Module(name) {
    val graphState = GraphState()

    override fun execute(triggerMessage: Message) {}
}

package com.syntona.dataflow.modules

import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class InputModule : Module("input") {
    val output = addOutput("output")

    // Set by the parent PatchModule when this module is added to its graphState
    internal var patch: PatchModule? = null
    internal var patchJack: InputJack? = null

    fun receive(msg: Message) {
        output.send(msg)
    }

    override fun execute(triggerMessage: Message) {}
}

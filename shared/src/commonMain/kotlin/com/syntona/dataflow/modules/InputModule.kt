package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class InputModule : Module("input") {
    val output = addOutput("output")

    var displayName by mutableStateOf("input")

    // Set by the parent PatchModule when this module is added to its graphState
    internal var patch: PatchModule? = null
    internal var patchJack: InputJack? = null

    fun receive(msg: Message) {
        output.send(msg)
    }

    override fun execute(triggerMessage: Message) {}
}

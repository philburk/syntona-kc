package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class InputModule : Module("input") {
    val output = addOutput("output")

    private var _name by mutableStateOf("input")
    override var name: String
        get() = _name
        set(value) {
            _name = value
            patchJack?.name = value
        }

    // Set by the parent PatchModule when this module is added to its graphState
    internal var patch: PatchModule? = null
    internal var patchJack: InputJack? = null

    fun receive(msg: Message) {
        output.send(msg)
    }

    override fun execute(triggerMessage: Message) {}
}

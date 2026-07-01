package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack

class OutputModule : Module("output") {
    val input = addInput("input")

    private var _name by mutableStateOf("output")
    override var name: String
        get() = _name
        set(value) {
            _name = value
            patchJack?.name = value
        }

    // Set by the parent PatchModule when this module is added to its graphState
    internal var patch: PatchModule? = null
    internal var patchJack: OutputJack? = null

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === input) {
            patch?.receiveFromOutputModule(this, msg)
        }
        super.onMessageReceived(jack, msg)
    }

    override fun execute(triggerMessage: Message) {}
}

package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

open class CommandModule : Module("command") {
    val output = addOutput("out")

    private var _name by mutableStateOf("command")
    override var name: String
        get() = _name
        set(value) {
            _name = value
            patchJack?.name = value
        }

    // Set by the parent PatchModule when this module is added to its graphState
    internal var patch: PatchModule? = null
    var patchJack: CommandJack? = null

    fun receive(msg: Message) {
        output.send(msg)
    }

    override fun execute(triggerMessage: Message) {}
}

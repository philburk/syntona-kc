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
    var patchJack: InputJack? = null
        set(value) {
            field = value
            if (value != null) {
                value.min = defaultMin
                value.max = defaultMax
                if (hasValue) {
                    value.setValue(defaultValue)
                }
                value.isKickable = defaultIsKickable
                value.isInteger = defaultIsInteger
            }
        }

    var defaultMin: Double = 0.0
        set(value) {
            field = value
            patchJack?.min = value
        }
    var defaultMax: Double = 100.0
        set(value) {
            field = value
            patchJack?.max = value
        }
    var defaultValue: Double = 0.0
        set(value) {
            field = value
            hasValue = true
            patchJack?.setValue(value)
        }
    var hasValue: Boolean = false
    var defaultIsKickable: Boolean = false
        set(value) {
            field = value
            patchJack?.isKickable = value
        }
    var defaultIsInteger: Boolean = false
        set(value) {
            field = value
            patchJack?.isInteger = value
        }

    fun receive(msg: Message) {
        output.send(msg)
    }

    override fun execute(triggerMessage: Message) {}
}

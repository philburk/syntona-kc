package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.isDefaultExponentialName

open class InputModule : Module("input") {
    val output = addOutput("out")

    private var _name by mutableStateOf("input")
    override var name: String
        get() = _name
        set(value) {
            _name = value
            hasAutoName = false
            patchJack?.name = value
            if (!isExponentialExplicitlySet) {
                val exp = isDefaultExponentialName(value)
                defaultIsExponential = exp
                isExponentialExplicitlySet = false
            }
        }

    // True while the name is the one the parent patch made up (in1, in2...), so the first
    // connection may replace it with the name of the connected jack. Any rename clears it.
    internal var hasAutoName = false

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
                if (isExponentialExplicitlySet) {
                    value.isExponential = defaultIsExponential
                } else {
                    value.isExponential = isDefaultExponentialName(name)
                    defaultIsExponential = value.isExponential
                    isExponentialExplicitlySet = false
                }
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
    private var isExponentialExplicitlySet = false
    var defaultIsExponential: Boolean = isDefaultExponentialName(name)
        set(value) {
            field = value
            isExponentialExplicitlySet = true
            patchJack?.isExponential = value
        }

    fun receive(msg: Message) {
        output.send(msg)
    }

    override fun execute(triggerMessage: Message) {}
}

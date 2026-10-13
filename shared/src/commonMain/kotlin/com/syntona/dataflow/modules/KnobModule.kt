package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class KnobModule : Module("knob") {
    val kick = addInput("kick", isKickable = true)
    val valueIn = addInput("in").also {
        it.min = 0.0
        it.max = 1.0
    }
    val output = addOutput("out")

    var isExponential: Boolean
        get() = valueIn.isExponential
        set(value) { valueIn.isExponential = value }

    var isInteger: Boolean
        get() = valueIn.isInteger
        set(value) { valueIn.isInteger = value }

    var value by mutableStateOf(0.5)

    fun setValue(newValue: Double, time: ScoreTime = ScoreTime.Zero) {
        val rounded = if (valueIn.isInteger) kotlin.math.round(newValue) else newValue
        value = rounded.coerceIn(valueIn.min, valueIn.max)
        output.send(Message.FloatValue(time, value))
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === valueIn && msg is Message.FloatValue) setValue(msg.value, msg.time)
        super.onMessageReceived(jack, msg)
    }

    override fun execute(triggerMessage: Message) {
        output.send(Message.FloatValue(triggerMessage.time, value))
    }
}

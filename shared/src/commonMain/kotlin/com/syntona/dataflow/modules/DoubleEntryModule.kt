package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that holds a single Double value.
 *
 * - [kick] (kickable): sends the current [value] to [output].
 * - [valueIn]: sets [value] from incoming FloatValue messages.
 * - [output]: emits the [value] when kicked.
 */
class DoubleEntryModule : Module("double") {
    val kick = addInput("kick", isKickable = true)
    val valueIn = addInput("in")
    val output = addOutput("out")

    var value by mutableStateOf(1.0)

    fun setValue(newValue: Double, time: ScoreTime = ScoreTime.Zero) {
        value = newValue
        output.send(Message.FloatValue(time, newValue))
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === valueIn && msg is Message.FloatValue) {
            setValue(msg.value, msg.time)
        }
        super.onMessageReceived(jack, msg)
    }

    override fun execute(triggerMessage: Message) {
        output.send(Message.FloatValue(triggerMessage.time, value))
    }
}

package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class FaderModule : Module("fader") {
    val kick = addInput("kick", isKickable = true)
    val valueIn = addInput("in").also {
        it.min = 0.0
        it.max = 100.0
    }
    val output = addOutput("out")

    var value by mutableStateOf(0.0)

    fun setValue(newValue: Double, time: ScoreTime = ScoreTime.Zero) {
        value = newValue.coerceIn(valueIn.min, valueIn.max)
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

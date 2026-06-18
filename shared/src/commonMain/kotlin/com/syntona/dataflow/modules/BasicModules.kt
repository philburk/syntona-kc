package com.syntona.dataflow.modules

import kotlin.math.sin
import kotlin.math.cos
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import kotlin.math.floor
import kotlin.math.round

class CounterModule() : Module("counter") {
    val reset = addInput("reset", isKickable = true)
    val increment = addInput("inc", isKickable = true)
    val output = addOutput("out")
    var count = 0

    override fun execute(triggerMessage: Message) {
        if (reset.kicked) {
            count = 0
        } else if (increment.kicked) {
            count++
        }
        output.send(Message.FloatValue(triggerMessage.time, count.toDouble()))
    }
}

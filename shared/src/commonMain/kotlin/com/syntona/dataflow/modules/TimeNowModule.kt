package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that outputs the current time in seconds as a [Double] scalar value when kicked.
 */
class TimeNowModule : Module("timeNow") {
    val kick = addCommandInput("kick")
    val output = addOutput("out")

    override fun execute(triggerMessage: Message) {
        val now = ScoreClock.now()
        output.send(Message.FloatValue(now, now.seconds))
    }
}

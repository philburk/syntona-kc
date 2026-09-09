package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that receives any incoming message, extracts its [ScoreTime] timestamp,
 * and sends it out as a [Double] scalar value in seconds.
 */
class GrabTimeModule : Module("grabTime") {
    val input = addUniversalInput("in", isKickable = true)
    val output = addOutput("out")

    override fun execute(triggerMessage: Message) {
        output.send(Message.FloatValue(triggerMessage.time, triggerMessage.time.seconds))
    }
}

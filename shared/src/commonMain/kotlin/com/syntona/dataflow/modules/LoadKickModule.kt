package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that sends a kick after a file has been completely loaded.
 */
class LoadKickModule : Module("loadKick") {
    val output = addOutput("kick")

    /**
     * Sends the kick message.
     */
    fun kick(time: ScoreTime = ScoreTime.Zero) {
        output.send(Message.FloatValue(time, 0.0))
    }

    override fun execute(triggerMessage: Message) {
        output.send(triggerMessage)
    }
}

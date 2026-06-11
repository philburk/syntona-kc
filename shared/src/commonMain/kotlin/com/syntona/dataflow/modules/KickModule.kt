package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that sends a message when triggered by the UI.
 * This will be connected to a Button in the Compose UI.
 */
class KickModule : Module("!") {
    val output = addOutput("Kick")

    /**
     * Called by the UI when the user clicks the button.
     */
    fun kick(time: ScoreTime = ScoreTime.Zero) {
        output.send(Message.FloatValue(time, 0.0))
    }

    override fun execute(triggerMessage: Message) {
        // Not typically kicked by another module, but could be.
        output.send(triggerMessage)
    }
}

package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import kotlin.random.Random

/**
 * Generates a random value between 0.0 and 1.0 when kicked.
 */
class RandomModule : Module("random") {
    val kick = addCommandInput("kick")
    val output = addOutput("out")

    override fun execute(triggerMessage: Message) {
        val value = Random.nextDouble()
        output.send(Message.FloatValue(triggerMessage.time, value))
    }
}

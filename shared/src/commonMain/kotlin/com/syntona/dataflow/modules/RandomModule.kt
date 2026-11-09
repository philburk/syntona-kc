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

/**
 * Generates a random integer evenly distributed in 0..(range - 1) when kicked.
 */
class ChooseModule : Module("choose") {
    val kick = addCommandInput("kick")
    val range = addIntegerInput("range").also {
        it.min = 1.0
        it.max = 128.0
        it.setValue(8.0)
    }
    val output = addIntegerOutput("out")

    override fun execute(triggerMessage: Message) {
        val n = range.getDoubleValue().toInt()
        val value = if (n > 0) Random.nextInt(n).toDouble() else 0.0
        output.send(Message.FloatValue(triggerMessage.time, value))
    }
}


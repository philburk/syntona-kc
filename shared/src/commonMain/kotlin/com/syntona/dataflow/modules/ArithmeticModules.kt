package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Adds two inputs. Triggered when either input receives a message (since both are kickable).
 */
class AddModule : Module("+") {
    val inputA = addInput("A", isKickable = true)
    val inputB = addInput("B", isKickable = true)
    val output = addOutput("Sum")

    override fun execute(triggerMessage: Message) {
        val sum = inputA.getDoubleValue() + inputB.getDoubleValue()
        output.send(Message.FloatValue(triggerMessage.time, sum))
    }
}

/**
 * Multiplies two inputs.
 */
class MultiplyModule : Module("*") {
    val inputA = addInput("A", isKickable = true)
    val inputB = addInput("B", isKickable = true)
    val output = addOutput("Product")

    override fun execute(triggerMessage: Message) {
        val product = inputA.getDoubleValue() * inputB.getDoubleValue()
        output.send(Message.FloatValue(triggerMessage.time, product))
    }
}

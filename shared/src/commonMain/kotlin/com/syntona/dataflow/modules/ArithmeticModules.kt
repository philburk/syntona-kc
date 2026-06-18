package com.syntona.dataflow.modules

import kotlin.math.sin
import kotlin.math.cos
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

abstract class UnaryOperatorModule(name: String) : Module(name) {
    val input = addInput("in", isKickable = true)
    val output = addOutput("out")
}

abstract class BinaryOperatorModule(name: String) : Module(name) {
    val inputA = addInput("A", isKickable = true)
    val inputB = addInput("B", isKickable = false)
    val output = addOutput("out")
}

/**
 * Adds two inputs. Triggered when first input receives a message (since both are kickable).
 */
class AddModule : BinaryOperatorModule("+") {
    override fun execute(triggerMessage: Message) {
        val sum = inputA.getDoubleValue() + inputB.getDoubleValue()
        output.send(Message.FloatValue(triggerMessage.time, sum))
    }
}

/**
 * Multiplies two inputs.
 */
class MultiplyModule : BinaryOperatorModule("*") {
    override fun execute(triggerMessage: Message) {
        val product = inputA.getDoubleValue() * inputB.getDoubleValue()
        output.send(Message.FloatValue(triggerMessage.time, product))
    }
}

class SubtractModule : BinaryOperatorModule("-") {
    override fun execute(triggerMessage: Message) {
        val product = inputA.getDoubleValue() - inputB.getDoubleValue()
        output.send(Message.FloatValue(triggerMessage.time, product))
    }
}
class DivideModule : BinaryOperatorModule("/") {
    override fun execute(triggerMessage: Message) {
        val denominator = inputB.getDoubleValue()
        if (denominator != 0.0) {
            val result = inputA.getDoubleValue() / denominator
            output.send(Message.FloatValue(triggerMessage.time, result))
        } // TODO else throw an error?
    }
}

class SineModule : UnaryOperatorModule("sin") {
    override fun execute(triggerMessage: Message) {
        val result = sin(input.getDoubleValue())
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

class CosineModule : UnaryOperatorModule("cos") {
    override fun execute(triggerMessage: Message) {
        val result = cos(input.getDoubleValue())
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

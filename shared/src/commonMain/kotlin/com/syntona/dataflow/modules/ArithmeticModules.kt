package com.syntona.dataflow.modules

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import kotlin.math.floor
import kotlin.math.round

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

/**
 * Multiplies two inputs and adds a third: (A * B) + C.
 */
class MultiplyAddModule : Module("*+") {
    val inputA = addInput("A", isKickable = true)
    val inputB = addInput("B", isKickable = false)
    val inputC = addInput("C", isKickable = false)
    val output = addOutput("out")

    override fun execute(triggerMessage: Message) {
        val result = (inputA.getDoubleValue() * inputB.getDoubleValue()) + inputC.getDoubleValue()
        output.send(Message.FloatValue(triggerMessage.time, result))
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

class CompareModule() : Module("compare") {
    val inputA = addInput("A", isKickable = true)
    val inputB = addInput("B", isKickable = false)
    val outputLT = addOutput("outLT")
    val outputEQ = addOutput("outEQ")
    val outputGT = addOutput("outGT")

    override fun execute(triggerMessage: Message) {
        val a = inputA.getDoubleValue()
        val b = inputB.getDoubleValue()
        if (a < b) {
            outputLT.send(Message.FloatValue(triggerMessage.time, a))
        } else if (a == b) {
            outputEQ.send(Message.FloatValue(triggerMessage.time, a))
        } else if (a > b) {
            outputGT.send(Message.FloatValue(triggerMessage.time, a))
        }
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

class FloorModule : UnaryOperatorModule("floor") {
    override fun execute(triggerMessage: Message) {
        val result = floor(input.getDoubleValue())
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

class RoundModule : UnaryOperatorModule("round") {
    override fun execute(triggerMessage: Message) {
        val result = round(input.getDoubleValue())
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

class ModModule : BinaryOperatorModule("mod") {
    init {
        inputB.setValue(4.0)
    }
    override fun execute(triggerMessage: Message) {
        val denominator = inputB.getDoubleValue()
        if (denominator != 0.0) {
            val result = inputA.getDoubleValue() % denominator
            output.send(Message.FloatValue(triggerMessage.time, result))
        }
    }
}

class PitchToFrequencyModule : UnaryOperatorModule("pitchToHz") {
    init {
        input.setValue(60.0)
    }
    override fun execute(triggerMessage: Message) {
        val result = com.softsynth.math.AudioMath.pitchToFrequency(input.getDoubleValue())
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

class MinModule : BinaryOperatorModule("min") {
    override fun execute(triggerMessage: Message) {
        val result = min(inputA.getDoubleValue(), inputB.getDoubleValue())
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

class MaxModule : BinaryOperatorModule("max") {
    override fun execute(triggerMessage: Message) {
        val result = max(inputA.getDoubleValue(), inputB.getDoubleValue())
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

class AbsModule : UnaryOperatorModule("abs") {
    override fun execute(triggerMessage: Message) {
        val result = abs(input.getDoubleValue())
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}


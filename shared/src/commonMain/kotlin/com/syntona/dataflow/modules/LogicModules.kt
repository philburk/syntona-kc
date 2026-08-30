package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Evaluates whether a numerical or message payload represents boolean TRUE.
 * Any payload <= 0 is evaluated as FALSE. Any payload > 0 is evaluated as TRUE.
 */
internal fun isTruthy(value: Double): Boolean = value > 0.0

internal fun isTruthy(msg: Message): Boolean = when (msg) {
    is Message.FloatValue -> msg.value > 0.0
    is Message.TextValue -> (msg.value.toDoubleOrNull() ?: 0.0) > 0.0
    else -> true
}

/**
 * Base class for two-input boolean logic modules.
 * Both inputs are kickable, re-evaluating and outputting 1.0 or 0.0 whenever either changes.
 */
abstract class BinaryLogicModule(name: String) : Module(name) {
    val inputA = addIntegerInput("A", isKickable = true)
    val inputB = addIntegerInput("B", isKickable = true)
    val output = addIntegerOutput("out")
}

/**
 * Logical AND: outputs 1 if both A and B are > 0, otherwise 0.
 */
class AndModule : BinaryLogicModule("and") {
    override fun execute(triggerMessage: Message) {
        val a = isTruthy(inputA.getDoubleValue())
        val b = isTruthy(inputB.getDoubleValue())
        val result = if (a && b) 1.0 else 0.0
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

/**
 * Logical OR: outputs 1 if either A or B is > 0, otherwise 0.
 */
class OrModule : BinaryLogicModule("or") {
    override fun execute(triggerMessage: Message) {
        val a = isTruthy(inputA.getDoubleValue())
        val b = isTruthy(inputB.getDoubleValue())
        val result = if (a || b) 1.0 else 0.0
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

/**
 * Logical XOR: outputs 1 if exactly one of A or B is > 0, otherwise 0.
 */
class XorModule : BinaryLogicModule("xor") {
    override fun execute(triggerMessage: Message) {
        val a = isTruthy(inputA.getDoubleValue())
        val b = isTruthy(inputB.getDoubleValue())
        val result = if (a xor b) 1.0 else 0.0
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

/**
 * Logical NOT: inverts the boolean state of the incoming message.
 * Outputs 1 if input <= 0, outputs 0 if input > 0.
 */
class NotModule : Module("not") {
    val input = addIntegerInput("in", isKickable = true)
    val output = addIntegerOutput("out")

    override fun execute(triggerMessage: Message) {
        val a = isTruthy(input.getDoubleValue())
        val result = if (!a) 1.0 else 0.0
        output.send(Message.FloatValue(triggerMessage.time, result))
    }
}

/**
 * 1-to-N Demultiplexer for event/message domain.
 * Routes an incoming message from [input] to [stepOutputs]`[index]`.
 * Updating [indexIn] changes the active route but does not trigger an output.
 * If index is out of bounds (< 0 or >= N), the message is dropped.
 */
open class BranchModule(val numOutputs: Int) : Module("branch$numOutputs") {
    val indexIn = addIntegerInput("index", isKickable = false).also {
        it.min = 0.0
        it.max = maxOf(0.0, (numOutputs - 1).toDouble())
    }
    val input = addInput("in", isKickable = true)
    val stepOutputs = List(numOutputs) { addOutput("out$it") }

    override fun execute(triggerMessage: Message) {
        if (input.isKicked) {
            val idx = indexIn.getDoubleValue().toInt()
            if (idx in 0 until numOutputs) {
                stepOutputs[idx].send(triggerMessage)
            }
        }
    }
}

class Branch4Module : BranchModule(4)
class Branch8Module : BranchModule(8)
class Branch16Module : BranchModule(16)

/**
 * N-to-1 Multiplexer for event/message domain.
 * Listens to one of several incoming streams and forwards it to [output] based on [indexIn].
 * Updating [indexIn] changes the active input but does not trigger an output.
 * Messages arriving on unselected inputs are dropped.
 */
open class SelectModule(val numInputs: Int) : Module("select$numInputs") {
    val indexIn = addIntegerInput("index", isKickable = false).also {
        it.min = 0.0
        it.max = maxOf(0.0, (numInputs - 1).toDouble())
    }
    val stepInputs = List(numInputs) { addInput("in$it", isKickable = true) }
    val output = addOutput("out")

    override fun execute(triggerMessage: Message) {
        val idx = indexIn.getDoubleValue().toInt()
        if (idx in 0 until numInputs && stepInputs[idx].isKicked) {
            output.send(triggerMessage)
        }
    }
}

class Select4Module : SelectModule(4)
class Select8Module : SelectModule(8)
class Select16Module : SelectModule(16)

/**
 * Boolean pass-through gate / mute switch for event messages.
 * When [enable] > 0 (Open), incoming messages on [input] are forwarded to [output].
 * When [enable] <= 0 (Closed), incoming messages are dropped.
 */
class PassModule : Module("pass") {
    val enable = addIntegerInput("enable", isKickable = false).also {
        it.min = 0.0
        it.max = 1.0
    }
    val input = addInput("in", isKickable = true)
    val output = addOutput("out")

    init {
        enable.setValue(1.0)
    }

    override fun execute(triggerMessage: Message) {
        if (input.isKicked && isTruthy(enable.getDoubleValue())) {
            output.send(triggerMessage)
        }
    }
}

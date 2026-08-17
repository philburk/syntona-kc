package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import androidx.compose.runtime.mutableStateListOf
import com.syntona.dataflow.model.InputJack

class CounterModule() : Module("counter") {
    val reset = addInput("reset", isKickable = true)
    val increment = addInput("inc", isKickable = true)
    val output = addOutput("out")
    var count = 0

    override fun execute(triggerMessage: Message) {
        if (reset.isKicked) {
            count = 0
        } else if (increment.isKicked) {
            count++
        }
        output.send(Message.FloatValue(triggerMessage.time, count.toDouble()))
    }
}

/**
 * Divides an incoming clock value by N and outputs the result if the remainder is zero.
 */
class ClockDividerModule : Module("ckDiv") {
    val input = addInput("in", isKickable = true)
    val divisor = addInput("N").also {
        it.min = 1.0
        it.max = 128.0
        it.setValue(2.0)
    }
    val output = addOutput("out")

    override fun execute(triggerMessage: Message) {
        val clockVal = input.getDoubleValue()
        val n = divisor.getDoubleValue(2.0).toLong().coerceAtLeast(1L)
        val c = kotlin.math.round(clockVal).toLong()
        if (kotlin.math.abs(clockVal - c) < 1e-6 && c % n == 0L) {
            val result = (c / n).toDouble()
            output.send(Message.FloatValue(triggerMessage.time, result))
        }
    }
}

open class SequenceModule(val numOutputs: Int) : Module("seq" + numOutputs) {
    val reset = addInput("reset", isKickable = true)
    val increment = addInput("in", isKickable = true)

    val stepInputs = List(numOutputs) { index ->
        addInput("in$index")
    }

    val stepOutputs = List(numOutputs) { index ->
        addOutput("out$index")
    }

    val allOutput = addOutput("all")

    val values = mutableStateListOf<String>().apply {
        repeat(numOutputs) { add("1.0") }
    }

    var count = 0

    init {
        stepOutputs[0].isHighlighted = true
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        val index = stepInputs.indexOf(jack)
        if (index != -1) {
            when (msg) {
                is Message.FloatValue -> values[index] = msg.value.toString()
                is Message.TextValue -> values[index] = msg.value
                else -> {}
            }
        }
        super.onMessageReceived(jack, msg)
    }

    override fun execute(triggerMessage: Message) {
        if (reset.isKicked) {
            count = 0
            for (index in 0 until numOutputs) {
                stepOutputs[index].isHighlighted = (index == 0)
            }
            return
        } else if (increment.isKicked) {
            count++
        }
        for (index in 0 until numOutputs) {
            stepOutputs[index].isHighlighted = false
        }
        val activeIndex = count % numOutputs
        val output = stepOutputs[activeIndex]
        val activeText = values[activeIndex]
        val valList = parseSequenceValueList(activeText)
        for (valItem in valList) {
            val msg = Message.FloatValue(triggerMessage.time, valItem)
            output.send(msg)
            allOutput.send(msg)
        }
        output.isHighlighted = true
    }
}

internal fun parseSequenceValueList(text: String): List<Double> {
    val items = text.split(',')
        .mapNotNull { it.trim().toDoubleOrNull() }
    return if (items.isNotEmpty()) items else listOf(1.0)
}

open class ModuloSelectModule(val numOutputs: Int) : Module("mod" + numOutputs) {
    val reset = addInput("reset", isKickable = true)
    val increment = addInput("in", isKickable = true)

    val stepOutputs = List(numOutputs) { index ->
        addOutput("out$index")
    }

    init {
        stepOutputs[0].isHighlighted = true
    }

    override fun execute(triggerMessage: Message) {
        val valToUse = if (reset.isKicked) {
            0.0
        } else {
            increment.getDoubleValue()
        }
        val moduloValue = ((valToUse % numOutputs) + numOutputs) % numOutputs
        val activeIndex = kotlin.math.floor(moduloValue).toInt().coerceIn(0, numOutputs - 1)

        for (i in 0 until numOutputs) {
            stepOutputs[i].isHighlighted = false
        }
        stepOutputs[activeIndex].send(Message.FloatValue(triggerMessage.time, moduloValue))
        stepOutputs[activeIndex].isHighlighted = true
    }
}

class Mod2Module : ModuloSelectModule(2)
class Mod4Module : ModuloSelectModule(4)
class Mod8Module : ModuloSelectModule(8)
class Mod16Module : ModuloSelectModule(16)

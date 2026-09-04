package com.syntona.dataflow.modules

import androidx.compose.runtime.mutableStateListOf
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack

class CounterModule() : Module("counter") {
    val reset = addCommandInput("reset")
    val increment = addCommandInput("kick")
    val kick get() = increment
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
class ClockDividerModule : Module("clockDiv") {
    val input = addInput("in", isKickable = true)
    val divisor = addIntegerInput("divisor").also {
        it.min = 1.0
        it.max = 128.0
        it.setValue(2.0)
    }
    val n: com.syntona.dataflow.model.IntegerInputJack get() = divisor
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

/**
 * Common base class for modules that hold an indexed array of text/numeric values,
 * with per-step inputs (in0..inN-1), per-step outputs (out0..outN-1), and a main output.
 */
abstract class StepValueModule(
    typeName: String,
    val numOutputs: Int,
    mainOutputName: String = "out"
) : Module(typeName) {

    val stepInputs: List<InputJack> = List(numOutputs) { index ->
        addInput("in$index")
    }

    val stepOutputs: List<OutputJack> = List(numOutputs) { index ->
        addOutput("out$index")
    }

    val mainOutput: OutputJack = addOutput(mainOutputName)

    val values = mutableStateListOf<String>().apply {
        repeat(numOutputs) { add("1.0") }
    }

    init {
        if (numOutputs > 0) {
            stepOutputs[0].isHighlighted = true
        }
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

    /**
     * Highlights the active output jack and sends the parsed values for [activeIndex]
     * to both the corresponding stepOutput and the main output.
     */
    protected fun fireStep(activeIndex: Int, triggerTime: ScoreTime) {
        for (index in 0 until numOutputs) {
            stepOutputs[index].isHighlighted = false
        }
        val safeIndex = ((activeIndex % numOutputs) + numOutputs) % numOutputs
        val output = stepOutputs[safeIndex]
        val activeText = values[safeIndex]
        val valList = parseSequenceValueList(activeText)
        for (valItem in valList) {
            val msg = Message.FloatValue(triggerTime, valItem)
            output.send(msg)
            mainOutput.send(msg)
        }
        output.isHighlighted = true
    }
}

open class SequenceModule(numOutputs: Int) : StepValueModule("seq$numOutputs", numOutputs, mainOutputName = "out") {
    val reset = addCommandInput("reset")
    val kick = addCommandInput("kick")

    /** Alias for backward compatibility */
    val increment get() = kick

    /** Alias for backward compatibility */
    val allOutput get() = mainOutput

    var count = 0

    override fun execute(triggerMessage: Message) {
        if (reset.isKicked) {
            count = 0
            for (index in 0 until numOutputs) {
                stepOutputs[index].isHighlighted = (index == 0)
            }
            return
        }

        fireStep(count, triggerMessage.time)

        // Post-increment
        if (kick.isKicked) {
            count++
        }
    }
}

internal fun parseSequenceValueList(text: String): List<Double> {
    val items = text.split(',')
        .mapNotNull { it.trim().toDoubleOrNull() }
    return if (items.isNotEmpty()) items else listOf(1.0)
}

open class ModuloSelectModule(val numOutputs: Int) : Module("mod" + numOutputs) {
    val input = addIntegerInput("in", isKickable = true)

    /** Alias for backward compatibility */
    val increment get() = input

    val stepOutputs = List(numOutputs) { index ->
        addOutput("out$index")
    }

    init {
        stepOutputs[0].isHighlighted = true
    }

    override fun execute(triggerMessage: Message) {
        val valToUse = input.getDoubleValue()
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

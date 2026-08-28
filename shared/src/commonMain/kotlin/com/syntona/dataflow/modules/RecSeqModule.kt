package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.util.RecursiveSequence

class RecSeqModule : Module("recSeq") {
    val step = addIntegerInput("step", isKickable = true)
    val stepsPerCycle = addIntegerInput("stepsPerCycle").also {
        it.min = 2.0
        it.max = 32.0
        it.setValue(8.0)
    }
    val density = addInput("density").also {
        it.min = 0.0
        it.max = 1.0
        it.setValue(0.5)
    }
    val limit = addIntegerInput("limit").also {
        it.min = 2.0
        it.max = 35.0
        it.setValue(11.0)
    }
    val maxJump = addIntegerInput("maxJump").also {
        it.min = 2.0
        it.max = 35.0
        it.setValue(5.0)
    }
    val output = addOutput("output")

    private val sequence = RecursiveSequence()

    override fun execute(triggerMessage: Message) {
        sequence.stepsPerCycle = stepsPerCycle.getClippedIntValue()
        sequence.density = density.getClippedDoubleValue()
        sequence.maxValue = limit.getClippedIntValue()
        sequence.maxInterval = maxJump.getClippedIntValue()

        val stepValue = step.getDoubleValue(0.0).toInt()
        val nextVal = sequence.next(stepValue)
        if (nextVal >= 0) {
            output.send(Message.FloatValue(triggerMessage.time, nextVal.toDouble()))
        }
    }
}

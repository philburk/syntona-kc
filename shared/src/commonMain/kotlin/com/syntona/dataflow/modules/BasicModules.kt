package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

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

open class SequenceModule(val numOutputs: Int) : Module("seq" + numOutputs) {
    val reset = addInput("reset", isKickable = true)
    val increment = addInput("in", isKickable = true)
    var count = 0

    init {
        for (index in 0..< numOutputs) {
            addOutput("out" + index)
        }
        outputs[0].isHighlighted = true
    }

    override fun execute(triggerMessage: Message) {
        if (reset.isKicked) {
            count = 0
        } else if (increment.isKicked){
            count++
        }
        val outs = outputs
        for (index in 0 ..< numOutputs) {
            outs[index].isHighlighted = false
        }
        val output = outs[count % numOutputs]
        output.send(Message.FloatValue(triggerMessage.time, count.toDouble()))
        output.isHighlighted = true
    }
}

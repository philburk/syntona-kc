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

open class SequenceModule(val numOutputs: Int) : Module("seq" + numOutputs) {
    val reset = addInput("reset", isKickable = true)
    val increment = addInput("in", isKickable = true)

    val stepInputs = List(numOutputs) { index ->
        addInput("in$index")
    }

    val stepOutputs = List(numOutputs) { index ->
        addOutput("out$index")
    }

    val values = mutableStateListOf<Double>().apply {
        repeat(numOutputs) { add(1.0) }
    }

    var count = 0

    init {
        stepOutputs[0].isHighlighted = true
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        val index = stepInputs.indexOf(jack)
        if (index != -1 && msg is Message.FloatValue) {
            values[index] = msg.value
        }
        super.onMessageReceived(jack, msg)
    }

    override fun execute(triggerMessage: Message) {
        if (reset.isKicked) {
            count = 0
        } else if (increment.isKicked){
            count++
        }
        for (index in 0 until numOutputs) {
            stepOutputs[index].isHighlighted = false
        }
        val activeIndex = count % numOutputs
        val output = stepOutputs[activeIndex]
        val activeValue = values[activeIndex]
        output.send(Message.FloatValue(triggerMessage.time, activeValue))
        output.isHighlighted = true
    }
}

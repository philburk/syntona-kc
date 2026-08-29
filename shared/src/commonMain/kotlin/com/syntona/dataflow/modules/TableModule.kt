package com.syntona.dataflow.modules

import com.syntona.dataflow.model.Message

/**
 * An indexed lookup table module with editable numeric fields.
 * Unlike [SequenceModule], it has no internal counter or reset; it is triggered
 * directly by an incoming [indexIn] jack, emitting the value(s) stored at that index.
 */
open class TableModule(numOutputs: Int) : StepValueModule("table$numOutputs", numOutputs, mainOutputName = "out") {
    val indexIn = addIntegerInput("index", isKickable = true).also {
        it.min = 0.0
        it.max = maxOf(0.0, (numOutputs - 1).toDouble())
    }

    /** Alias to [mainOutput] */
    val out get() = mainOutput

    override fun execute(triggerMessage: Message) {
        if (indexIn.isKicked) {
            val idx = indexIn.getClippedIntValue()
            fireStep(idx, triggerMessage.time)
        }
    }
}

class Table4Module : TableModule(4)
class Table8Module : TableModule(8)
class Table16Module : TableModule(16)

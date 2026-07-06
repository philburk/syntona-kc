package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class AddTimeModule : Module("addTime"), EngineAware {
    val input = addInput("in", isKickable = true)
    val delayMs = addInput("delay(ms)").also {
        it.min = 0.0
        it.max = 60_000.0
    }
    val output = addOutput("out")

    private var engine: DataFlowEngine? = null

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun execute(triggerMessage: Message) {
        val delayNs = (delayMs.getDoubleValue(0.0) * 1_000_000.0).toLong().coerceAtLeast(0L)
        if (delayNs == 0L) {
            output.send(triggerMessage)
            return
        }
        val e = engine ?: run { output.send(triggerMessage); return }
        val fireTime = triggerMessage.time + ScoreTime(delayNs)
        val delayedMsg = triggerMessage.withTime(fireTime)
        e.schedule(fireTime) { output.send(delayedMsg) }
    }
}

private fun Message.withTime(newTime: ScoreTime): Message = when (this) {
    is Message.FloatValue -> copy(time = newTime)
    is Message.TextValue -> copy(time = newTime)
    is Message.ObjectValue -> copy(time = newTime)
    is Message.DataQueueMessage -> copy(time = newTime)
    is Message.VoiceMessage -> copy(time = newTime)
}

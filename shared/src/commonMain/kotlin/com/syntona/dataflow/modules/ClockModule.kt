package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class ClockModule : Module("clock"), EngineAware {
    val start = addCommandInput("start")
    val stop = addCommandInput("stop")
    val reset = addCommandInput("reset")
    val periodMs = addInput("period(ms)").also {
        it.min = 1.0
        it.max = 10_000.0
    }
    val countOut = addOutput("output")

    private var engine: DataFlowEngine? = null
    private var pendingEvent: ScheduledEvent? = null
    private var running = false
    private var count = 0L

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun execute(triggerMessage: Message) {
        when {
            start.isKicked -> {
                if (!running) {
                    running = true
                    val delayNs = periodToNanos()
                    scheduleNext(ScoreClock.now() + ScoreTime(delayNs))
                }
            }
            stop.isKicked -> {
                running = false
                pendingEvent?.cancel()
                pendingEvent = null
            }
            reset.isKicked -> {
                count = 0L
                countOut.send(Message.FloatValue(triggerMessage.time, 0.0))
            }
        }
    }

    private fun periodToNanos(): Long =
        (periodMs.getDoubleValue(500.0) * 1_000_000.0).toLong().coerceAtLeast(1_000_000L)

    private fun scheduleNext(fireTime: ScoreTime) {
        val e = engine ?: return
        pendingEvent = e.schedule(fireTime) { onClockFire(fireTime) }
    }

    private fun onClockFire(fireTime: ScoreTime) {
        if (!running) return
        count++
        countOut.send(Message.FloatValue(fireTime, count.toDouble()))
        scheduleNext(fireTime + ScoreTime(periodToNanos()))
    }
}

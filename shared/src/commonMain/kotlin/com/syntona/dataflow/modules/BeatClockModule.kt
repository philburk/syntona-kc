package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import kotlin.math.roundToLong

class BeatClockModule : Module("beatClock"), EngineAware {
    val start = addCommandInput("start")
    val stop = addCommandInput("stop")
    val reset = addCommandInput("reset")
    val tempo = addInput("tempo").also {
        it.min = 20.0
        it.max = 300.0
        it.setValue(120.0)
    }
    val countOut = addOutput("out")

    var division by mutableStateOf("1/1")

    private var engine: DataFlowEngine? = null
    private var pendingEvent: ScheduledEvent? = null
    private var running = false
    private var count = 0L
    private var remainderNs = 0.0

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun teardown() {
        running = false
        pendingEvent?.cancel()
        pendingEvent = null
    }

    override fun execute(triggerMessage: Message) {
        when {
            start.isKicked -> {
                if (!running) {
                    running = true
                    remainderNs = 0.0
                    val interval = getIntervalNanos()
                    val stepNs = interval
                    val delayNs = stepNs.roundToLong()
                    remainderNs = stepNs - delayNs
                    scheduleNext(triggerMessage.time + ScoreTime(delayNs))
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

    private fun getIntervalNanos(): Double {
        val t = tempo.getDoubleValue(120.0).coerceAtLeast(1.0)
        val divVal = getDivisionValue(division)
        return (divVal * 60_000_000_000.0 / t).coerceAtLeast(1_000_000.0)
    }

    fun getDivisionValue(div: String): Double {
        return when (div) {
            "1/4" -> 4.0
            "1/3" -> 3.0
            "1/2" -> 2.0
            "1/1" -> 1.0
            "2/1" -> 0.5
            "8/3" -> 3.0 / 8.0
            "4/1" -> 0.25
            "16/3" -> 3.0 / 16.0
            "8/1" -> 0.125
            "16/1" -> 1.0 / 16.0
            "32/1" -> 1.0 / 32.0
            "3/1" -> 1.0 / 3.0
            "6/1" -> 1.0 / 6.0
            "12/1" -> 1.0 / 12.0
            "5/1" -> 0.2
            "10/1" -> 0.1
            else -> {
                val parts = div.split('/')
                if (parts.size == 2) {
                    val num = parts[0].toDoubleOrNull() ?: 1.0
                    val den = parts[1].toDoubleOrNull() ?: 1.0
                    if (num != 0.0) den / num else 1.0
                } else 1.0
            }
        }
    }

    companion object {
        fun reverseDivision(div: String): String {
            val parts = div.split('/')
            return if (parts.size == 2) {
                "${parts[1]}/${parts[0]}"
            } else {
                div
            }
        }
    }

    private fun scheduleNext(fireTime: ScoreTime) {
        val e = engine ?: return
        pendingEvent = e.schedule(fireTime) { onClockFire(fireTime) }
    }

    private fun onClockFire(fireTime: ScoreTime) {
        if (!running) return
        count++
        countOut.send(Message.FloatValue(fireTime, count.toDouble()))
        val interval = getIntervalNanos()
        val stepNs = interval + remainderNs
        val delayNs = stepNs.roundToLong()
        remainderNs = stepNs - delayNs
        scheduleNext(fireTime + ScoreTime(delayNs))
    }
}

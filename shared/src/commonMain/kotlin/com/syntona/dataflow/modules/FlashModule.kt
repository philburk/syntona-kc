package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A module that flashes briefly when kicked.
 * It has only one input called "flash".
 */
class FlashModule : Module("flash"), EngineAware {
    val flashInput = addInput("flash", isKickable = true)

    var isOn by mutableStateOf(false)
    var triggerCount by mutableStateOf(0)
        private set

    private var engine: DataFlowEngine? = null
    private var pendingEvent: ScheduledEvent? = null

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun teardown() {
        pendingEvent?.cancel()
        pendingEvent = null
    }

    override fun execute(triggerMessage: Message) {
        if (flashInput.isKicked) {
            isOn = true
            triggerCount++
            pendingEvent?.cancel()
            engine?.let { e ->
                val delayNs = 150_000_000L // 150ms
                val turnOffTime = triggerMessage.time + ScoreTime(delayNs)
                pendingEvent = e.schedule(turnOffTime) {
                    isOn = false
                    pendingEvent = null
                }
            }
        }
    }
}

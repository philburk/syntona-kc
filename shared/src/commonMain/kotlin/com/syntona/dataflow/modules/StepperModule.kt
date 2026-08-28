package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.CommandJackBadge
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A 0-based, bounded iterator designed for sequencing and logic routing.
 * Utilizes an internal "Run/Dormant" state machine to eliminate race conditions
 * and double-triggering when chaining modules or sharing master clocks.
 */
class StepperModule : Module("stepper"), CommandJackBadge {
    val start = addCommandInput("start")
    val stop = addCommandInput("stop")
    val kick = addInput("kick", isKickable = true)
    val limit = addIntegerInput("N").also {
        it.min = 1.0
        it.max = 128.0
        it.setValue(4.0)
    }
    val loop = addIntegerInput("loop").also {
        it.min = 0.0
        it.max = 1.0
        it.setValue(0.0)
    }

    val indexOut = addIntegerOutput("index")
    val doneOut = addOutput("done")

    var isRunning: Boolean by mutableStateOf(false)
        private set

    var currentIndex: Int by mutableStateOf(0)
        private set

    private var lastActiveTime: ScoreTime? = null

    private var _isLooping by mutableStateOf(false)
    var isLooping: Boolean
        get() = _isLooping
        set(value) {
            _isLooping = value
            val doubleVal = if (value) 1.0 else 0.0
            if (loop.getDoubleValue() != doubleVal) {
                loop.setValue(doubleVal)
            }
        }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === loop && msg is Message.FloatValue) {
            _isLooping = msg.value > 0.5
        }
        super.onMessageReceived(jack, msg)
    }

    override fun badgeFor(jack: CommandJack): String? =
        if (jack === start && isRunning) "RUN" else null

    override fun teardown() {
        isRunning = false
        start.isHighlighted = false
        lastActiveTime = null
    }

    override fun execute(triggerMessage: Message) {
        val nVal = maxOf(1, limit.getClippedIntValue())

        if (start.isKicked) {
            isRunning = true
            start.isHighlighted = true
            currentIndex = 0
            lastActiveTime = triggerMessage.time
            indexOut.send(Message.FloatValue(triggerMessage.time, 0.0))
            return
        }

        if (stop.isKicked) {
            isRunning = false
            start.isHighlighted = false
            lastActiveTime = null
            return
        }

        if (kick.isKicked) {
            if (!isRunning) {
                // Dormant state: kick is ignored
                return
            }

            if (triggerMessage.time == lastActiveTime) {
                // Activated on this same clock tick: Start acts as the first beat
                return
            }

            if (currentIndex < nVal - 1) {
                currentIndex++
                indexOut.send(Message.FloatValue(triggerMessage.time, currentIndex.toDouble()))
            } else {
                // Currently outputting N - 1; this kick completes the final step's rhythmic duration.
                doneOut.send(Message.FloatValue(triggerMessage.time, 1.0))
                if (isLooping) {
                    currentIndex = 0
                    indexOut.send(Message.FloatValue(triggerMessage.time, 0.0))
                } else {
                    isRunning = false
                    start.isHighlighted = false
                    lastActiveTime = null
                }
            }
        }
    }
}

package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.dataflow.model.CommandOutputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class TimelineTrigger(
    val id: Int,
    initialTime: Double,
    val outputJack: CommandOutputJack
) {
    var time by mutableStateOf(initialTime)
}

open class TimelineModule(name: String = "timeline") : Module(name), EngineAware {
    val start = addCommandInput("start")
    val stop = addCommandInput("stop")
    val rewind = addCommandInput("rewind")

    val triggers = mutableStateListOf<TimelineTrigger>()

    var width by mutableStateOf(320.0)
    var maxTime by mutableStateOf(60.0)
    var loopStart by mutableStateOf(0.0)
    var loopEnd by mutableStateOf(5.0)
    var loopMode by mutableStateOf(true)
    var zoom by mutableStateOf(1.0f)
    var seekTime by mutableStateOf(0.0)

    var isPlaying by mutableStateOf(false)
        private set
    var playbackStartPos by mutableStateOf(0.0)
        private set
    var playbackStartTimeNs by mutableStateOf(0L)
        private set

    open fun unitsToSeconds(units: Double): Double = units
    open fun secondsToUnits(seconds: Double): Double = seconds

    private var engine: DataFlowEngine? = null
    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private var nextTriggerId = 0

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun teardown() {
        stopPlayback(ScoreClock.now())
        super.teardown()
    }

    override fun execute(triggerMessage: Message) {
        when {
            start.isKicked -> {
                val e = engine ?: return
                e.post(object : SyntonaCommand {
                    override val isUndoable = false
                    override fun execute() {
                        startPlayback(triggerMessage.time, seekTime)
                    }
                    override fun undo() {}
                })
            }
            stop.isKicked -> {
                val e = engine ?: return
                e.post(object : SyntonaCommand {
                    override val isUndoable = false
                    override fun execute() {
                        stopPlayback(triggerMessage.time)
                    }
                    override fun undo() {}
                })
            }
            rewind.isKicked -> {
                val e = engine ?: return
                e.post(object : SyntonaCommand {
                    override val isUndoable = false
                    override fun execute() {
                        seekTo(0.0)
                    }
                    override fun undo() {}
                })
            }
        }
    }

    fun startPlayback(now: ScoreTime, startPosUnits: Double) {
        if (isPlaying) stopPlayback(now)
        val e = engine ?: return

        val startPos = startPosUnits.coerceIn(0.0, maxTime)
        val cycleEnd = if (loopMode) {
            if (startPos < loopEnd) loopEnd.coerceAtMost(maxTime) else maxTime
        } else {
            maxTime
        }
        val cycleDurationUnits = cycleEnd - startPos

        if (cycleDurationUnits <= 0.0) {
            isPlaying = false
            return
        }

        isPlaying = true
        playbackStartPos = startPos
        playbackStartTimeNs = now.nanoseconds

        // Schedule all triggers in the range [startPos, cycleEnd)
        triggers.forEach { trigger ->
            if (trigger.time >= startPos && trigger.time < cycleEnd) {
                val delayUnits = trigger.time - startPos
                val delaySeconds = unitsToSeconds(delayUnits)
                val fireTime = now + ScoreTime((delaySeconds * 1_000_000_000.0).toLong())
                val event = e.schedule(fireTime) {
                    trigger.outputJack.send(Message.FloatValue(fireTime, 0.0))
                }
                scheduledEvents.add(event)
            }
        }

        // Schedule cycle end event
        val cycleDurationSeconds = unitsToSeconds(cycleDurationUnits)
        val cycleEndTime = now + ScoreTime((cycleDurationSeconds * 1_000_000_000.0).toLong())
        val cycleEndEvent = e.schedule(cycleEndTime) {
            if (loopMode) {
                startPlayback(cycleEndTime, loopStart)
            } else {
                isPlaying = false
                scheduledEvents.clear()
            }
        }
        scheduledEvents.add(cycleEndEvent)
    }

    fun stopPlayback(now: ScoreTime) {
        if (isPlaying) {
            val elapsedSeconds = (now.nanoseconds - playbackStartTimeNs) / 1_000_000_000.0
            val elapsedUnits = secondsToUnits(elapsedSeconds)
            seekTime = (playbackStartPos + elapsedUnits).coerceIn(0.0, maxTime)
        }
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        isPlaying = false
    }

    fun requestPlayToggle() {
        val e = engine ?: return
        val jack = if (isPlaying) stop else start
        e.post(com.syntona.dataflow.engine.KickJackCommand(jack, ScoreClock.now()))
    }

    fun seekTo(timeSeconds: Double) {
        val e = engine ?: return
        val coerced = timeSeconds.coerceIn(0.0, maxTime)
        e.post(object : SyntonaCommand {
            override val isUndoable = false
            override fun execute() {
                seekTime = coerced
                if (isPlaying) {
                    startPlayback(ScoreClock.now(), coerced)
                }
            }
            override fun undo() {}
        })
    }

    fun addTrigger(time: Double) {
        val id = nextTriggerId++
        val jackName = "t$id"
        addTriggerDirect(id, time, jackName)
    }

    fun addTriggerDirect(id: Int, time: Double, jackName: String) {
        val jack = addCommandOutput(jackName)
        val trigger = TimelineTrigger(id, time, jack)
        triggers.add(trigger)
        if (id >= nextTriggerId) {
            nextTriggerId = id + 1
        }
    }

    fun removeTrigger(trigger: TimelineTrigger) {
        triggers.remove(trigger)
        val jack = trigger.outputJack
        removeOutput(jack)
        parentPatch?.graphState?.let { gs ->
            val toDisconnect = gs.connections.filter { it.output == jack }
            toDisconnect.forEach { gs.disconnect(it.output, it.input) }
        }
    }

    fun clearTriggers() {
        triggers.toList().forEach { removeTrigger(it) }
        triggers.clear()
        nextTriggerId = 0
    }
}

/**
 * A timeline sequencer operating in musical beats with tempo control.
 */
class BeatLineModule : TimelineModule("beatLine") {
    val tempo = addInput("tempo").also {
        it.min = 20.0
        it.max = 300.0
        it.setValue(120.0)
    }

    init {
        maxTime = 16.0
        loopEnd = 4.0
    }

    fun getTempoBpm(): Double = tempo.getDoubleValue(120.0).coerceAtLeast(1.0)

    override fun unitsToSeconds(units: Double): Double {
        return units * (60.0 / getTempoBpm())
    }

    override fun secondsToUnits(seconds: Double): Double {
        return seconds * (getTempoBpm() / 60.0)
    }
}


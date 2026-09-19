package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.CommandOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.model.VoiceInputJack
import com.syntona.dataflow.model.VoiceOutputJack

import kotlin.math.roundToInt

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
    val tracks = mutableStateListOf<TimelineTrack>()

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

    var isRecording by mutableStateOf(false)
        private set

    open fun unitsToSeconds(units: Double): Double = units
    open fun secondsToUnits(seconds: Double): Double = seconds
    open fun getTempoBpm(): Double = 120.0

    fun unitsToTicks(units: Double): Int {
        val ticksPerUnit = if (this is BeatLineModule) {
            PianoRollTiming.TPB.toDouble()
        } else {
            (getTempoBpm() / 60.0) * PianoRollTiming.TPB
        }
        return (units * ticksPerUnit).roundToInt()
    }

    fun ticksToUnits(ticks: Int): Double {
        val ticksPerUnit = if (this is BeatLineModule) {
            PianoRollTiming.TPB.toDouble()
        } else {
            (getTempoBpm() / 60.0) * PianoRollTiming.TPB
        }
        return ticks.toDouble() / ticksPerUnit
    }

    private var engine: DataFlowEngine? = null
    fun getEngine(): DataFlowEngine? = engine

    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private var nextTriggerId = 0
    private var nextTrackId = 1

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
        tracks.forEach { it.setEngine(engine) }
    }

    override fun teardown() {
        stopRecording(ScoreClock.now())
        stopPlayback(ScoreClock.now())
        tracks.toList().forEach { it.teardown() }
        super.teardown()
    }

    fun addTrack(type: TrackType, customName: String? = null): TimelineTrack {
        val id = nextTrackId++
        val trackName = customName?.takeIf { it.isNotBlank() } ?: "${type.displayName} $id"
        val track = when (type) {
            TrackType.PATCH -> PatchTrack(id, trackName, this)
            TrackType.MESSAGE -> MessageTrack(id, trackName, this)
            TrackType.MIDI -> MidiTrack(id, trackName, this)
            TrackType.AUDIO -> AudioTrack(id, trackName, this)
        }
        engine?.let { track.setEngine(it) }
        tracks.add(track)
        return track
    }

    fun addTrackDirect(
        id: Int,
        type: TrackType,
        name: String,
        inJackName: String? = null,
        outJackName: String? = null
    ): TimelineTrack {
        val track = when (type) {
            TrackType.PATCH -> PatchTrack(id, name, this)
            TrackType.MESSAGE -> MessageTrack(id, name, this, inJackName, outJackName)
            TrackType.MIDI -> MidiTrack(id, name, this, inJackName, outJackName)
            TrackType.AUDIO -> AudioTrack(id, name, this, inJackName, outJackName)
        }
        engine?.let { track.setEngine(it) }
        tracks.add(track)
        if (id >= nextTrackId) {
            nextTrackId = id + 1
        }
        return track
    }

    fun removeTrack(track: TimelineTrack) {
        track.dispose()
        tracks.remove(track)
    }

    fun clearTracks() {
        tracks.toList().forEach { it.dispose() }
        tracks.clear()
        nextTrackId = 1
    }

    internal fun createTrackInput(name: String): InputJack = addInput(name)
    internal fun createTrackOutput(name: String): OutputJack = addOutput(name)
    internal fun createTrackVoiceInput(name: String): VoiceInputJack = addVoiceInput(name)
    internal fun createTrackVoiceOutput(name: String): VoiceOutputJack = addVoiceOutput(name)
    internal fun createTrackAudioInput(name: String, port: com.softsynth.ksyn.ports.UnitInputPort): AudioInputJack = addAudioInput(name, port)
    internal fun createTrackAudioOutput(name: String, port: com.softsynth.ksyn.ports.UnitOutputPort, partIndex: Int = 0): AudioOutputJack = addAudioOutput(name, port, partIndex)

    internal fun deleteTrackInput(jack: InputJack) {
        removeInput(jack)
        parentPatch?.graphState?.let { gs ->
            val toDisconnect = gs.connections.filter { conn -> conn.input == jack }
            toDisconnect.forEach { conn -> gs.disconnect(conn.output, conn.input) }
            gs.jackOffsets.remove(jack)
            gs.jackBounds.remove(jack)
        }
    }

    internal fun deleteTrackOutput(jack: OutputJack) {
        removeOutput(jack)
        parentPatch?.graphState?.let { gs ->
            val toDisconnect = gs.connections.filter { conn -> conn.output == jack }
            toDisconnect.forEach { conn -> gs.disconnect(conn.output, conn.input) }
            gs.jackOffsets.remove(jack)
            gs.jackBounds.remove(jack)
        }
    }

    fun getCurrentTime(now: ScoreTime = ScoreClock.now()): Double {
        if (!isPlaying) return seekTime
        val nowNs = if (now.nanoseconds <= 0L && playbackStartTimeNs > 1_000_000_000L) {
            ScoreClock.now().nanoseconds
        } else {
            now.nanoseconds
        }
        val elapsedNs = nowNs - playbackStartTimeNs
        val elapsedSec = if (elapsedNs > 0) elapsedNs / 1_000_000_000.0 else 0.0
        val elapsedUnits = secondsToUnits(elapsedSec)
        val rawPos = playbackStartPos + elapsedUnits
        return if (loopMode && loopEnd > loopStart) {
            val loopLen = loopEnd - loopStart
            if (rawPos >= loopStart) {
                loopStart + ((rawPos - loopStart) % loopLen)
            } else {
                rawPos.coerceIn(0.0, maxTime)
            }
        } else {
            rawPos.coerceIn(0.0, maxTime)
        }
    }

    fun startRecording(now: ScoreTime) {
        if (!isPlaying) {
            startPlayback(now, seekTime)
        }
        isRecording = true
        val pos = getCurrentTime(now)
        tracks.filter { it.isRecordArmed }.forEach { it.onRecordStart(now, pos) }
    }

    fun stopRecording(now: ScoreTime) {
        if (isRecording) {
            isRecording = false
            val pos = getCurrentTime(now)
            tracks.filter { it.isRecordArmed }.forEach { it.onRecordStop(now, pos) }
        }
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        tracks.forEach { track ->
            if (track.inputJack === jack) {
                track.onMessageReceived(jack, msg)
            }
        }
        super.onMessageReceived(jack, msg)
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
                        if (isRecording) {
                            stopRecording(triggerMessage.time)
                        }
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

        // Schedule track playback events
        tracks.forEach { track ->
            track.onPlaybackStart(now, startPos, cycleEnd, e)
        }

        // Schedule cycle end event
        val cycleDurationSeconds = unitsToSeconds(cycleDurationUnits)
        val cycleEndTime = now + ScoreTime((cycleDurationSeconds * 1_000_000_000.0).toLong())
        val cycleEndEvent = e.schedule(cycleEndTime) {
            if (loopMode) {
                startPlayback(cycleEndTime, loopStart)
            } else {
                stopPlayback(cycleEndTime)
            }
        }
        scheduledEvents.add(cycleEndEvent)
    }

    fun stopPlayback(now: ScoreTime) {
        if (isRecording) {
            stopRecording(now)
        }
        if (isPlaying) {
            seekTime = getCurrentTime(now)
        }
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        isPlaying = false
        tracks.forEach { it.onPlaybackStop() }
    }

    fun requestPlayToggle() {
        val e = engine ?: return
        val jack = if (isPlaying) stop else start
        e.post(com.syntona.dataflow.engine.KickJackCommand(jack, ScoreClock.now()))
    }

    fun requestRecordToggle() {
        val e = engine
        if (e != null) {
            e.post(object : SyntonaCommand {
                override val isUndoable = false
                override fun execute() {
                    val now = ScoreClock.now()
                    if (isRecording) {
                        stopRecording(now)
                    } else {
                        startRecording(now)
                    }
                }
                override fun undo() {}
            })
        } else {
            val now = ScoreClock.now()
            if (isRecording) {
                stopRecording(now)
            } else {
                startRecording(now)
            }
        }
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

    fun addTrigger(time: Double): TimelineTrigger {
        val id = nextTriggerId++
        val jackName = "t$id"
        return addTriggerDirect(id, time, jackName)
    }

    fun addTriggerDirect(id: Int, time: Double, jackName: String): TimelineTrigger {
        val jack = addCommandOutput(jackName)
        val trigger = TimelineTrigger(id, time, jack)
        triggers.add(trigger)
        if (id >= nextTriggerId) {
            nextTriggerId = id + 1
        }
        return trigger
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
    val tempo: InputJack = addCustomInput(object : InputJack("tempo", this) {
        override fun receive(msg: Message) {
            if (isTempoLocked) {
                return
            }
            super.receive(msg)
        }
    }).also {
        it.min = 20.0
        it.max = 300.0
        it.setValue(120.0)
    }

    val isTempoLocked: Boolean
        get() = tracks.any { it is AudioTrack && it.sections.isNotEmpty() }

    var lockedTempoBpm: Double? = null
        get() {
            if (isTempoLocked) {
                if (field == null) {
                    field = tempo.getDoubleValue(120.0).coerceAtLeast(1.0)
                }
                return field
            } else {
                field = null
                return null
            }
        }
        private set

    init {
        maxTime = 16.0
        loopEnd = 4.0
    }

    override fun getTempoBpm(): Double {
        return lockedTempoBpm ?: tempo.getDoubleValue(120.0).coerceAtLeast(1.0)
    }

    override fun unitsToSeconds(units: Double): Double {
        return units * (60.0 / getTempoBpm())
    }

    override fun secondsToUnits(seconds: Double): Double {
        return seconds * (getTempoBpm() / 60.0)
    }
}


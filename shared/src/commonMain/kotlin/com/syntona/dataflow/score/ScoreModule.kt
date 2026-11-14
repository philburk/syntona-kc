package com.syntona.dataflow.score

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.KickJackCommand
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.VoiceOutputJack
import com.syntona.dataflow.modules.PianoRollTiming
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Staff notation score module under `music.compose`.
 * Displays a stave with key signature, time signature, notes, and rests rendered via SMuFL (Leland),
 * accepts MIDI Voice input on [input] for step recording, and outputs timed [Message.VoiceMessage] events on [output].
 */
class ScoreModule : Module("score"), EngineAware {
    val start = addCommandInput("start")
    val stop = addCommandInput("stop")
    val tempo = addInput("tempo").also {
        it.min = PianoRollTiming.MIN_TEMPO
        it.max = PianoRollTiming.MAX_TEMPO
        it.setValue(PianoRollTiming.DEFAULT_TEMPO)
    }
    val input = addVoiceInput("in")
    val output = addVoiceOutput("out")

    val scoreData = ScoreData()

    var windowX: Float? = null
    var windowY: Float? = null
    var windowWidth: Float? = null
    var windowHeight: Float? = null

    var isPlaying by mutableStateOf(false)
        private set

    var activeItemIndex by mutableStateOf<Int?>(null)
        private set

    var isStepRecording by mutableStateOf(false)
        private set

    var cursorPosition by mutableStateOf<ScoreCursorPosition?>(null)
        internal set

    var stepRecordDuration by mutableStateOf(NoteDuration.QUARTER)
    var stepRecordStaccato by mutableStateOf(false)

    var lastStepRecordedText by mutableStateOf<String?>(null)
        internal set

    var stepRecordRevision by mutableStateOf(0)
        internal set

    private var engine: DataFlowEngine? = null
    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private val soundingNotes = mutableMapOf<Int, Double>() // tag -> pitch

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    fun getEngine(): DataFlowEngine? = engine

    fun setStepRecordingEnabled(enabled: Boolean) {
        if (enabled && cursorPosition == null) {
            cursorPosition = ScoreEditing.defaultStepCursor(scoreData.measures)
        }
        isStepRecording = enabled
        if (!enabled) {
            lastStepRecordedText = null
        }
    }

    fun exitStepRecording() {
        setStepRecordingEnabled(false)
    }

    fun placeCursor(position: ScoreCursorPosition?) {
        cursorPosition = position
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === input && msg is Message.VoiceMessage && isStepRecording) {
            handleIncomingVoiceMessage(msg)
        }
        super.onMessageReceived(jack, msg)
    }

    private fun handleIncomingVoiceMessage(msg: Message.VoiceMessage) {
        if (!isPlaying) {
            output.send(msg)
        }
        val isNoteOn = (msg.opcode == Message.VoiceOpcode.NOTE_ON || msg.opcode == Message.VoiceOpcode.NOTE) &&
            msg.extra > 0.0
        if (!isNoteOn) return

        val midiPitch = msg.value.roundToInt().coerceIn(0, 127)
        val cmd = StepRecordScoreNoteCommand(
            module = this,
            midiPitch = midiPitch,
            amplitude = msg.extra,
        )
        val e = engine
        if (e != null) {
            e.post(cmd)
        } else {
            cmd.execute()
        }
    }

    override fun execute(triggerMessage: Message) {
        val now = triggerMessage.time
        when {
            start.isKicked -> startPlayback(now)
            stop.isKicked -> stopPlayback(now)
        }
    }

    fun requestPlayToggle() {
        val e = engine ?: return
        val jack = if (isPlaying) stop else start
        e.post(KickJackCommand(jack, ScoreClock.now()))
    }

    /**
     * Sounds a short preview of [pitch] through [output]. Safe to call from the UI thread.
     */
    fun previewPitch(pitch: Double, isStaccato: Boolean = false) {
        val e = engine ?: return
        val durationMs = if (isStaccato) STACCATO_PREVIEW_DURATION_MS else PREVIEW_DURATION_MS
        e.post(PreviewScoreNoteCommand(output, pitch, durationMs))
    }

    private fun startPlayback(now: ScoreTime) {
        if (isPlaying) stopPlayback(now)
        val e = engine ?: return

        val tempoVal = tempo.getDoubleValue(PianoRollTiming.DEFAULT_TEMPO).coerceAtLeast(1.0)
        val nsPerTick = PianoRollTiming.nanosPerTick(tempoVal)
        val items = scoreData.measures.flatMap { it.items }
        if (items.isEmpty()) return

        isPlaying = true
        var tickCursor = 0L
        var globalIdx = 0
        var nextVoiceTag = 0

        for (item in items) {
            val itemIdx = globalIdx++
            val itemTicks = item.totalTicks.toLong()
            val onTime = now + ScoreTime((tickCursor * nsPerTick).roundToLong())
            val offTime = now + ScoreTime(((tickCursor + itemTicks) * nsPerTick).roundToLong())
            val fullDurSec = (offTime.nanoseconds - onTime.nanoseconds).coerceAtLeast(0L) / 1_000_000_000.0
            val soundingPitches = item.soundingPitches()
            val soundingDurSec = if (soundingPitches.isNotEmpty() && item.noteStaccato()) {
                minOf(fullDurSec * STACCATO_DURATION_FRACTION, MAX_STACCATO_SECONDS)
                    .coerceAtLeast(minOf(MIN_STACCATO_SECONDS, fullDurSec))
            } else {
                fullDurSec
            }
            val soundingOffTime = onTime + ScoreTime((soundingDurSec * 1_000_000_000.0).roundToLong())
            val voiceTags = List(soundingPitches.size) { nextVoiceTag++ }

            scheduledEvents.add(e.schedule(onTime) {
                activeItemIndex = itemIdx
                if (soundingPitches.isNotEmpty()) {
                    val amp = item.noteAmplitude()
                    soundingPitches.forEachIndexed { idx, spelled ->
                        val pitch = spelled.midiPitch.toDouble()
                        val tag = voiceTags[idx]
                        output.send(
                            Message.VoiceMessage(
                                time = onTime,
                                opcode = Message.VoiceOpcode.NOTE,
                                tag = tag,
                                value = pitch,
                                extra = amp,
                                duration = soundingDurSec,
                            )
                        )
                        soundingNotes[tag] = pitch
                    }
                }
            })
            if (soundingPitches.isNotEmpty()) {
                scheduledEvents.add(e.schedule(soundingOffTime) {
                    voiceTags.forEach { soundingNotes.remove(it) }
                })
            }
            tickCursor += itemTicks
        }

        val endTime = now + ScoreTime((tickCursor * nsPerTick).roundToLong()) + ScoreTime(1L)
        scheduledEvents.add(e.schedule(endTime) {
            activeItemIndex = null
            isPlaying = false
            scheduledEvents.clear()
        })
    }

    private fun stopPlayback(now: ScoreTime) {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        for ((tag, pitch) in soundingNotes) {
            output.send(
                Message.VoiceMessage(
                    time = now,
                    opcode = Message.VoiceOpcode.NOTE_OFF,
                    tag = tag,
                    value = pitch,
                    extra = 0.0,
                )
            )
        }
        soundingNotes.clear()
        activeItemIndex = null
        isPlaying = false
    }

    override fun teardown() {
        stopPlayback(ScoreClock.now())
        super.teardown()
    }

    companion object {
        const val PREVIEW_TAG = -2
        const val PREVIEW_DURATION_MS = 250L
        const val STACCATO_PREVIEW_DURATION_MS = 60L
        const val PREVIEW_AMPLITUDE = 0.25
        const val STACCATO_DURATION_FRACTION = 0.35
        const val MAX_STACCATO_SECONDS = 0.08
        const val MIN_STACCATO_SECONDS = 0.015
    }
}

private class PreviewScoreNoteCommand(
    private val output: VoiceOutputJack,
    private val pitch: Double,
    private val durationMs: Long,
) : SyntonaCommand {
    override val isUndoable = false

    override fun execute() {
        val now = ScoreClock.now()
        output.send(
            Message.VoiceMessage(
                time = now,
                opcode = Message.VoiceOpcode.NOTE_OFF,
                tag = ScoreModule.PREVIEW_TAG,
                value = pitch,
                extra = ScoreModule.PREVIEW_AMPLITUDE,
            )
        )
        output.send(
            Message.VoiceMessage(
                time = now,
                opcode = Message.VoiceOpcode.NOTE,
                tag = ScoreModule.PREVIEW_TAG,
                value = pitch,
                extra = ScoreModule.PREVIEW_AMPLITUDE,
                duration = durationMs * 0.001,
            )
        )
    }

    override fun undo() {}
}

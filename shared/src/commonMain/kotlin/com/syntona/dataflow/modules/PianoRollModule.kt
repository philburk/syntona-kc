package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.KickJackCommand
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.VoiceOutputJack
import kotlin.math.log2

/**
 * A piano-roll sequencer supporting relative just intonation. Notes may be chained to a
 * parent note at a whole-number frequency ratio; chained notes follow their parent's pitch.
 * Playback emits NOTE_ON/NOTE_OFF [Message.VoiceMessage]s on [output] with pitch in
 * fractional semitones (60.0 = middle C).
 */
class PianoRollModule : Module("pianoRoll"), EngineAware {
    val start = addCommandInput("start")
    val stop = addCommandInput("stop")
    val output = addVoiceOutput("out")

    val notes = mutableStateListOf<PianoRollNote>()

    var bpm by mutableStateOf(PianoRollTiming.DEFAULT_BPM)

    /** Playback start marker position, set by clicking the timeline ruler. */
    var playStartTicks by mutableStateOf(0)

    // Persisted editor window geometry (in desktop pixels / dp, PatchModule pattern).
    var windowX: Float? = null
    var windowY: Float? = null
    var windowWidth: Float? = null
    var windowHeight: Float? = null

    var isPlaying by mutableStateOf(false)
        private set

    /** ScoreClock time when playback started; valid while [isPlaying]. */
    var playbackStartTime: ScoreTime = ScoreTime.Zero
        private set

    private var nextNoteId = 0
    private var engine: DataFlowEngine? = null
    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private val soundingNotes = mutableMapOf<Int, Double>() // tag -> pitch

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    fun getEngine(): DataFlowEngine? = engine

    override fun execute(triggerMessage: Message) {
        when {
            start.isKicked -> startPlayback(triggerMessage.time)
            stop.isKicked -> stopPlayback(triggerMessage.time)
        }
    }

    override fun teardown() {
        stopPlayback(ScoreClock.now())
        super.teardown()
    }

    // ------------------------------------------------------------------ notes

    /** Absolute pitch of [note] in fractional semitones (60.0 = middle C). */
    fun pitchOf(note: PianoRollNote): Double {
        var pitch = 0.0
        var current: PianoRollNote? = note
        var hops = 0
        while (current != null && hops <= notes.size) {
            if (current.isRoot) {
                return pitch + current.rootPitch
            }
            pitch += current.ratio.semitones
            current = findNote(current.parentId)
            hops++
        }
        // Orphaned or cyclic chain: treat accumulated ratio as relative to middle C.
        return pitch + 60.0
    }

    fun findNote(id: Int): PianoRollNote? = notes.find { it.id == id }

    /** All notes whose pitch depends (directly or transitively) on note [id]. */
    fun descendantsOf(id: Int): List<PianoRollNote> {
        val result = mutableListOf<PianoRollNote>()
        val queue = ArrayDeque<Int>()
        queue.add(id)
        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            for (n in notes) {
                if (n.parentId == currentId && result.none { it.id == n.id }) {
                    result.add(n)
                    queue.add(n.id)
                }
            }
        }
        return result
    }

    /** True if [candidateId] is a descendant of note [ofId] (used to prevent chain cycles). */
    fun isDescendant(candidateId: Int, ofId: Int): Boolean =
        descendantsOf(ofId).any { it.id == candidateId }

    fun allocateNoteId(): Int = nextNoteId++

    /** Called after deserialization so future ids don't collide. */
    fun resetNextNoteId() {
        nextNoteId = (notes.maxOfOrNull { it.id } ?: -1) + 1
    }

    // --------------------------------------------------------------- playback

    /** Runs on the engine thread (from [execute] or a scheduled event). */
    private fun startPlayback(now: ScoreTime) {
        if (isPlaying) stopPlayback(now)
        val e = engine ?: return

        val nsPerTick = PianoRollTiming.nanosPerTick(bpm)
        val startTicks = playStartTicks
        val playable = notes.filter { it.endTicks > startTicks }
        if (playable.isEmpty()) return

        isPlaying = true
        playbackStartTime = now
        var lastOffTime = now

        for (note in playable) {
            val pitch = pitchOf(note)
            val onDelayTicks = (note.startTicks - startTicks).coerceAtLeast(0)
            val onTime = now + ScoreTime(onDelayTicks * nsPerTick)
            val offTime = now + ScoreTime((note.endTicks - startTicks) * nsPerTick)
            if (offTime > lastOffTime) lastOffTime = offTime

            scheduledEvents.add(e.schedule(onTime) {
                output.send(
                    Message.VoiceMessage(
                        onTime, Message.VoiceOpcode.NOTE_ON,
                        tag = note.id, value = pitch, extra = NOTE_AMPLITUDE
                    )
                )
                soundingNotes[note.id] = pitch
            })
            scheduledEvents.add(e.schedule(offTime) {
                output.send(
                    Message.VoiceMessage(
                        offTime, Message.VoiceOpcode.NOTE_OFF,
                        tag = note.id, value = pitch, extra = NOTE_AMPLITUDE
                    )
                )
                soundingNotes.remove(note.id)
            })
        }

        // Mark playback finished just after the final NOTE_OFF fires.
        val endTime = lastOffTime + ScoreTime(1L)
        scheduledEvents.add(e.schedule(endTime) {
            isPlaying = false
            scheduledEvents.clear()
        })
    }

    /** Runs on the engine thread. Cancels pending events and silences sounding notes. */
    private fun stopPlayback(now: ScoreTime) {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        for ((tag, pitch) in soundingNotes) {
            output.send(
                Message.VoiceMessage(
                    now, Message.VoiceOpcode.NOTE_OFF,
                    tag = tag, value = pitch, extra = NOTE_AMPLITUDE
                )
            )
        }
        soundingNotes.clear()
        isPlaying = false
    }

    /** Toggle playback from the UI thread (Space key or toolbar button). */
    fun requestPlayToggle() {
        val e = engine ?: return
        val jack = if (isPlaying) stop else start
        e.post(KickJackCommand(jack, ScoreClock.now()))
    }

    /** Current playhead position in ticks, or null when not playing. */
    fun playheadTicks(now: ScoreTime): Double? {
        if (!isPlaying) return null
        val elapsedNs = (now - playbackStartTime).nanoseconds
        return playStartTicks + elapsedNs.toDouble() / PianoRollTiming.nanosPerTick(bpm)
    }

    // ---------------------------------------------------------------- preview

    /**
     * Sound a short preview of [pitch] through the output jack. Safe to call from the
     * UI thread; the NOTE_OFF is scheduled on the engine thread.
     */
    fun previewPitch(pitch: Double, durationMs: Long = PREVIEW_DURATION_MS) {
        val e = engine ?: return
        e.post(PreviewNoteCommand(e, output, pitch, durationMs))
    }

    companion object {
        const val NOTE_AMPLITUDE = 0.2

        /** Tag used for edit-preview notes; outside the note-id space. */
        const val PREVIEW_TAG = -2

        const val PREVIEW_DURATION_MS = 250L

        /** MIDI pitch (60.0 = middle C) for a frequency in Hz. */
        fun frequencyToPitch(frequencyHz: Double): Double = 69.0 + 12.0 * log2(frequencyHz / 440.0)
    }
}

/**
 * Sends a short NOTE_ON/NOTE_OFF pair for auditioning a pitch while editing.
 * The command executes on the engine thread, where scheduling the OFF is legal.
 */
private class PreviewNoteCommand(
    private val engine: DataFlowEngine,
    private val output: VoiceOutputJack,
    private val pitch: Double,
    private val durationMs: Long,
) : SyntonaCommand {
    override val isUndoable = false

    override fun execute() {
        val now = ScoreClock.now()
        // Silence any previous preview before starting the next one.
        output.send(
            Message.VoiceMessage(
                now, Message.VoiceOpcode.NOTE_OFF,
                tag = PianoRollModule.PREVIEW_TAG, value = pitch, extra = PianoRollModule.NOTE_AMPLITUDE
            )
        )
        output.send(
            Message.VoiceMessage(
                now, Message.VoiceOpcode.NOTE_ON,
                tag = PianoRollModule.PREVIEW_TAG, value = pitch, extra = PianoRollModule.NOTE_AMPLITUDE
            )
        )
        val offTime = now + ScoreTime(durationMs * 1_000_000L)
        engine.schedule(offTime) {
            output.send(
                Message.VoiceMessage(
                    offTime, Message.VoiceOpcode.NOTE_OFF,
                    tag = PianoRollModule.PREVIEW_TAG, value = pitch, extra = PianoRollModule.NOTE_AMPLITUDE
                )
            )
        }
    }

    override fun undo() {}
}

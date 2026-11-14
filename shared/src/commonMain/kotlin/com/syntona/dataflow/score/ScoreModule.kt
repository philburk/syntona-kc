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
    var stepRecordDotted by mutableStateOf(false)

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
            start.isKicked -> startPlayback(now, startCursor = null)
            stop.isKicked -> stopPlayback(now)
        }
    }

    /**
     * Toggles playback from the UI Play button or Spacebar.
     * Starts from [cursorPosition] (or from the beginning when `cursorPosition == null`).
     */
    fun requestPlayToggle() {
        val e = engine ?: return
        if (isPlaying) {
            e.post(KickJackCommand(stop, ScoreClock.now()))
        } else {
            e.post(PlayScoreFromCursorCommand(this, ScoreClock.now()))
        }
    }

    /**
     * Stops playback if running and moves the cursor to the beginning of the score.
     */
    fun requestRewind() {
        if (isPlaying) {
            val e = engine
            if (e != null) {
                e.post(KickJackCommand(stop, ScoreClock.now()))
            } else {
                stopPlayback(ScoreClock.now())
            }
        }
        cursorPosition = ScoreCursorPosition(0, 0)
    }

    internal fun startPlaybackFromCursor(now: ScoreTime = ScoreClock.now()) {
        startPlayback(now, startCursor = cursorPosition)
    }

    /**
     * Sounds a short preview of [pitch] through [output]. Safe to call from the UI thread.
     */
    fun previewPitch(pitch: Double, isStaccato: Boolean = false) {
        val e = engine ?: return
        val durationMs = if (isStaccato) STACCATO_PREVIEW_DURATION_MS else PREVIEW_DURATION_MS
        e.post(PreviewScoreNoteCommand(output, pitch, durationMs))
    }

    private fun startPlayback(
        now: ScoreTime,
        startCursor: ScoreCursorPosition? = null,
    ) {
        if (isPlaying) stopPlayback(now)
        val e = engine ?: return

        val tempoVal = tempo.getDoubleValue(PianoRollTiming.DEFAULT_TEMPO).coerceAtLeast(1.0)
        val nsPerTick = PianoRollTiming.nanosPerTick(tempoVal)
        val measures = scoreData.measures
        if (measures.none { it.items.isNotEmpty() }) return

        val targetCursor = startCursor?.let { raw ->
            val clampedM = raw.measureIdx.coerceIn(0, measures.lastIndex)
            val clampedI = raw.itemIdx.coerceIn(0, measures[clampedM].items.size)
            ScoreCursorPosition(clampedM, clampedI)
        }
        if (targetCursor != null) {
            val hasRemainingItem = (targetCursor.measureIdx..measures.lastIndex).any { mIdx ->
                val startSlot = if (mIdx == targetCursor.measureIdx) targetCursor.itemIdx else 0
                startSlot < measures[mIdx].items.size
            }
            if (!hasRemainingItem) return
        }

        var effectiveTime = measures.firstOrNull()?.timeSignature ?: scoreData.timeSignature
        isPlaying = true
        var tickCursor = 0L
        var startTick: Long? = if (targetCursor == null) 0L else null
        var globalIdx = 0
        var nextVoiceTag = 0

        data class PlaybackSlot(
            val loc: ScoreItemLocation,
            val itemIdx: Int,
            val item: ScoreItem,
            val onsetTick: Long,
            val endTick: Long,
            val attackDyn: DynamicMark?,
            val startsHighlight: Boolean,
        )

        val slots = ArrayList<PlaybackSlot>()
        val slotByLoc = HashMap<ScoreItemLocation, PlaybackSlot>()
        val trailingRestTicks = ArrayList<Long>()

        var sustainedDynamic: DynamicMark? = null
        var hasActiveHighlight = false
        for ((mIdx, measure) in measures.withIndex()) {
            if (mIdx > 0) {
                measure.timeSignature?.let { effectiveTime = it }
            }
            val measureCapacityTicks = effectiveTime.measureCapacityTicks.toLong().coerceAtLeast(0L)
            val measureStartTick = tickCursor
            for ((slotIdx, item) in measure.items.withIndex()) {
                if (startTick == null && targetCursor != null && mIdx == targetCursor.measureIdx && slotIdx >= targetCursor.itemIdx) {
                    startTick = tickCursor
                }
                val (attackDyn, nextSustained) = ScoreEditing.resolvePlaybackDynamicStep(
                    measure.dynamics[slotIdx],
                    sustainedDynamic,
                )
                sustainedDynamic = nextSustained
                val itemIdx = globalIdx++
                val itemTicks = item.totalTicks.toLong()
                val inPlaybackRange = startTick != null
                if (inPlaybackRange) {
                    hasActiveHighlight = true
                }
                val slot = PlaybackSlot(
                    loc = ScoreItemLocation(mIdx, slotIdx),
                    itemIdx = itemIdx,
                    item = item,
                    onsetTick = tickCursor,
                    endTick = tickCursor + itemTicks,
                    attackDyn = attackDyn,
                    startsHighlight = inPlaybackRange,
                )
                slots.add(slot)
                slotByLoc[slot.loc] = slot
                tickCursor += itemTicks
            }
            if (startTick == null && targetCursor != null && mIdx == targetCursor.measureIdx && targetCursor.itemIdx >= measure.items.size) {
                startTick = tickCursor
            }

            // Honor the time signature by treating missing space in empty/incomplete measures as rests.
            for ((k, dyn) in measure.dynamics.entries.sortedBy { it.key }) {
                if (k >= measure.items.size) {
                    sustainedDynamic = ScoreEditing.resolvePlaybackDynamicStep(dyn, sustainedDynamic).second
                }
            }
            val measureTicks = tickCursor - measureStartTick
            if (measureTicks < measureCapacityTicks) {
                if (hasActiveHighlight && startTick != null) {
                    trailingRestTicks.add(tickCursor)
                    hasActiveHighlight = false
                }
                tickCursor = measureStartTick + measureCapacityTicks
            }
        }

        val resolvedStartTick = startTick ?: 0L
        val continuedPitchesByLoc = HashMap<ScoreItemLocation, MutableSet<Int>>()

        for (slot in slots) {
            if (!slot.startsHighlight) continue
            val item = slot.item
            val relStartTick = slot.onsetTick - resolvedStartTick
            val onTime = now + ScoreTime((relStartTick * nsPerTick).roundToLong())
            val continuedMidis = continuedPitchesByLoc[slot.loc].orEmpty()
            val attackPitches = item.soundingPitches().filter { it.midiPitch !in continuedMidis }

            val effectiveAmp = if (slot.attackDyn != null) {
                ((item.noteAmplitude() / 0.25) * slot.attackDyn.amplitude).coerceIn(0.0, 1.0)
            } else {
                item.noteAmplitude()
            }

            data class PitchAttackSpec(
                val midiPitch: Double,
                val tag: Int,
                val soundingDurSec: Double,
                val soundingOffTime: ScoreTime,
            )

            val attackSpecs = attackPitches.map { spelled ->
                val targetMidi = spelled.midiPitch
                var endSlot = slot
                var curLoc = slot.loc
                while (ScoreEditing.hasActiveTieForPitch(measures, curLoc.measureIdx, curLoc.itemIdx, targetMidi)) {
                    val nextLoc = ScoreEditing.nextItemLocation(measures, curLoc.measureIdx, curLoc.itemIdx) ?: break
                    val nextSlot = slotByLoc[nextLoc] ?: break
                    continuedPitchesByLoc.getOrPut(nextLoc) { mutableSetOf() }.add(targetMidi)
                    endSlot = nextSlot
                    curLoc = nextLoc
                }
                val sustainDurSec = ((endSlot.endTick - slot.onsetTick).coerceAtLeast(0L) * nsPerTick) / 1_000_000_000.0
                val soundingDurSec = if (endSlot.item.noteStaccato()) {
                    val leadSec = ((endSlot.onsetTick - slot.onsetTick).coerceAtLeast(0L) * nsPerTick) / 1_000_000_000.0
                    val lastNoteFullSec = ((endSlot.endTick - endSlot.onsetTick).coerceAtLeast(0L) * nsPerTick) / 1_000_000_000.0
                    val staccatoTailSec = minOf(lastNoteFullSec * STACCATO_DURATION_FRACTION, MAX_STACCATO_SECONDS)
                        .coerceAtLeast(minOf(MIN_STACCATO_SECONDS, lastNoteFullSec))
                    leadSec + staccatoTailSec
                } else {
                    sustainDurSec
                }
                val soundingOffTime = onTime + ScoreTime((soundingDurSec * 1_000_000_000.0).roundToLong())
                PitchAttackSpec(
                    midiPitch = targetMidi.toDouble(),
                    tag = nextVoiceTag++,
                    soundingDurSec = soundingDurSec,
                    soundingOffTime = soundingOffTime,
                )
            }

            scheduledEvents.add(e.schedule(onTime) {
                activeItemIndex = slot.itemIdx
                for (spec in attackSpecs) {
                    output.send(
                        Message.VoiceMessage(
                            time = onTime,
                            opcode = Message.VoiceOpcode.NOTE,
                            tag = spec.tag,
                            value = spec.midiPitch,
                            extra = effectiveAmp,
                            duration = spec.soundingDurSec,
                        )
                    )
                    soundingNotes[spec.tag] = spec.midiPitch
                }
            })
            for (spec in attackSpecs) {
                scheduledEvents.add(e.schedule(spec.soundingOffTime) {
                    soundingNotes.remove(spec.tag)
                })
            }
        }

        for (restTick in trailingRestTicks) {
            val restStartTime = now + ScoreTime(((restTick - resolvedStartTick) * nsPerTick).roundToLong())
            scheduledEvents.add(e.schedule(restStartTime) {
                activeItemIndex = null
            })
        }

        val endTime = now + ScoreTime(((tickCursor - resolvedStartTick).coerceAtLeast(0L) * nsPerTick).roundToLong()) + ScoreTime(1L)
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

private class PlayScoreFromCursorCommand(
    private val module: ScoreModule,
    private val now: ScoreTime,
) : SyntonaCommand {
    override val isUndoable = false

    override fun execute() {
        module.startPlaybackFromCursor(now)
    }

    override fun undo() {}
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

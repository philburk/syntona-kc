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
    private val primaryOutput = addVoiceOutput("out")
    val output: VoiceOutputJack get() = outputForInstrument(0)

    val scoreData = ScoreData()

    private val instrumentOutputs = linkedMapOf<String, VoiceOutputJack>().apply {
        scoreData.instruments.firstOrNull()?.let { put(it.id, primaryOutput) }
    }

    var windowX: Float? = null
    var windowY: Float? = null
    var windowWidth: Float? = null
    var windowHeight: Float? = null

    var isPlaying by mutableStateOf(false)
        private set

    var activeItemIndex by mutableStateOf<Int?>(null)
        private set

    var activeItemsByStaff by mutableStateOf<Map<ScoreStaffId, Int>>(emptyMap())
        private set

    var isStepRecording by mutableStateOf(false)
        private set

    var cursorPosition by mutableStateOf<ScoreCursorPosition?>(null)
        internal set

    var stepRecordDuration by mutableStateOf(NoteDuration.QUARTER)
    var stepRecordStaccato by mutableStateOf(false)
    var stepRecordDotted by mutableStateOf(false)
    var stepRecordTupletRatio by mutableStateOf<Pair<Int, Int>?>(null)
    var autoFillRests by mutableStateOf(true)
    var loopMode by mutableStateOf(false)
    var loopStartMeasure by mutableStateOf(0)
        private set
    var loopEndMeasure by mutableStateOf(DEFAULT_LOOP_END_MEASURE)
        private set

    fun toggleLoopMode() {
        loopMode = !loopMode
    }

    /** Returns the clamped contiguous 0-based measure range currently targeted for looping. */
    fun effectiveLoopRange(measureCount: Int = scoreData.maxMeasureCount()): IntRange {
        val lastIdx = (measureCount - 1).coerceAtLeast(0)
        val start = loopStartMeasure.coerceIn(0, lastIdx)
        val end = loopEndMeasure.coerceIn(start, lastIdx)
        return start..end
    }

    /** Sets the contiguous 0-based inclusive measure loop range, clamped to existing measures. */
    fun setLoopRange(startMeasureIdx: Int, endMeasureIdx: Int) {
        val lastIdx = (scoreData.maxMeasureCount() - 1).coerceAtLeast(0)
        val clampedStart = startMeasureIdx.coerceIn(0, lastIdx)
        val clampedEnd = endMeasureIdx.coerceIn(clampedStart, lastIdx)
        loopStartMeasure = clampedStart
        loopEndMeasure = clampedEnd
    }

    init {
        if (autoFillRests) {
            fillEmptyMeasuresWithRests()
        }
    }

    fun fillEmptyMeasuresWithRests() {
        for (staff in scoreData.allStaffIds()) {
            val ms = scoreData.staffMeasures(staff)
            val filled = ScoreEditing.fillMeasuresWithRests(ms, scoreData.timeSignature)
            if (filled !== ms) {
                scoreData.replaceStaffMeasures(staff, filled)
            }
        }
    }

    /**
     * Resolves the [ScoreTuplet] to attach to a newly inserted item at [measureIdx]/[insertIdx].
     * Continues an immediately preceding incomplete tuplet group when [stepRecordTupletRatio]
     * matches, or when [stepRecordTupletRatio] is `null` and either [joinPrecedingIncomplete] is
     * true or the insertion falls before subsequent items in the same measure. Otherwise begins a
     * new group keyed off [baseTicks] when [stepRecordTupletRatio] is active.
     */
    fun resolveTupletForInsertion(
        measures: List<ScoreMeasure>,
        measureIdx: Int,
        insertIdx: Int,
        baseTicks: Int,
        joinPrecedingIncomplete: Boolean = false,
    ): ScoreTuplet? {
        val ratio = stepRecordTupletRatio
        val measure = measures.getOrNull(measureIdx)
        val prevIdx = insertIdx - 1
        if (measure != null && prevIdx in measure.items.indices) {
            val prevTuplet = measure.items[prevIdx].tuplet
            if (prevTuplet != null) {
                val canJoinPreceding = if (ratio != null) {
                    prevTuplet.actualCount == ratio.first && prevTuplet.normalCount == ratio.second
                } else {
                    joinPrecedingIncomplete || insertIdx < measure.items.size
                }
                if (canJoinPreceding) {
                    var groupStart = prevIdx
                    while (groupStart > 0 && measure.items[groupStart - 1].tuplet?.groupId == prevTuplet.groupId) {
                        groupStart--
                    }
                    var groupEnd = prevIdx
                    while (groupEnd + 1 < measure.items.size &&
                        measure.items[groupEnd + 1].tuplet?.groupId == prevTuplet.groupId
                    ) {
                        groupEnd++
                    }
                    // When auto-fill rests populated trailing rests inside this tuplet, check whether
                    // the prefix up to `prevIdx` still has room in the tuplet group.
                    val prefixEnd = if (ratio != null && (prevIdx + 1..groupEnd).all { measure.items[it] is ScoreItem.Rest }) {
                        prevIdx
                    } else {
                        groupEnd
                    }
                    val unscaledSum = (groupStart..prefixEnd).sumOf { measure.items[it].totalTicks }
                    val targetSum = if (prevTuplet.baseTicks > 0) {
                        prevTuplet.actualCount * prevTuplet.baseTicks
                    } else {
                        Int.MAX_VALUE
                    }
                    val isGroupFull = if (prevTuplet.baseTicks > 0) {
                        unscaledSum >= targetSum
                    } else {
                        (prefixEnd - groupStart + 1) >= prevTuplet.actualCount
                    }
                    if (!isGroupFull) {
                        return prevTuplet
                    }
                }
            }
        }
        val activeRatio = ratio ?: return null
        val usedIds = ScoreEditing.collectTupletGroupIds(measures)
        return ScoreTuplet(
            groupId = ScoreEditing.nextTupletGroupId(usedIds),
            actualCount = activeRatio.first,
            normalCount = activeRatio.second,
            baseTicks = baseTicks.coerceAtLeast(1),
        )
    }

    var lastStepRecordedText by mutableStateOf<String?>(null)
        internal set

    var stepRecordRevision by mutableStateOf(0)
        internal set

    private var engine: DataFlowEngine? = null
    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private val soundingNotesByOutput = linkedMapOf<VoiceOutputJack, MutableMap<Int, Double>>() // jack -> (tag -> pitch)

    fun outputForInstrument(instrumentIdx: Int): VoiceOutputJack {
        val part = scoreData.instruments.getOrNull(instrumentIdx) ?: return primaryOutput
        return instrumentOutputs[part.id] ?: primaryOutput
    }

    fun syncInstrumentOutputs() {
        val parts = scoreData.instruments.toList()
        if (parts.isEmpty()) return

        val activeIds = parts.map { it.id }.toSet()
        val staleIds = instrumentOutputs.keys.filter { it !in activeIds }
        for (staleId in staleIds) {
            val staleJack = instrumentOutputs.remove(staleId)
            if (staleJack != null) {
                detachAndRemoveOutput(staleJack)
            }
        }

        // Preserve each instrument's VoiceOutputJack across reorders and edits so wires
        // remain attached to their intended instrument; reuse primaryOutput only if it is
        // currently unassigned in outputs.
        for (part in parts) {
            if (part.id !in instrumentOutputs) {
                val canReusePrimary = primaryOutput in outputs && primaryOutput !in instrumentOutputs.values
                if (canReusePrimary) {
                    instrumentOutputs[part.id] = primaryOutput
                } else {
                    val existingNames = outputs.map { it.name }
                    val jackName = ScoreInstrumentPart.uniqueJackName(part.jackName, existingNames)
                    instrumentOutputs[part.id] = addVoiceOutput(jackName)
                }
            }
        }

        // Synchronize jack names (including renames of existing instruments).
        val assignedNames = outputs.filter { it !in instrumentOutputs.values }.map { it.name }.toMutableSet()
        for (iIdx in scoreData.instruments.indices) {
            val part = scoreData.instruments[iIdx]
            val jack = instrumentOutputs[part.id] ?: continue
            val uniqueName = ScoreInstrumentPart.uniqueJackName(part.jackName, assignedNames)
            assignedNames.add(uniqueName)
            if (uniqueName != part.jackName) {
                scoreData.instruments[iIdx] = part.copy(jackName = uniqueName)
            }
            if (jack.name != uniqueName) {
                jack.name = uniqueName
            }
        }

        // Match module output jack vertical order to the score instrument order.
        reorderOutputs(scoreData.instruments.mapNotNull { instrumentOutputs[it.id] })
    }

    private fun detachAndRemoveOutput(jack: VoiceOutputJack) {
        soundingNotesByOutput.remove(jack)
        removeOutput(jack)
        parentPatch?.graphState?.let { gs ->
            val toDisconnect = gs.connections.filter { conn -> conn.output == jack }
            toDisconnect.forEach { conn -> gs.disconnect(conn.output, conn.input) }
            gs.jackOffsets.remove(jack)
            gs.jackBounds.remove(jack)
            gs.jackMarkerCenters.remove(jack)
        }
    }

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
        val targetStaff = cursorPosition?.staff ?: ScoreStaffId(0, 0)
        val targetOutput = outputForInstrument(targetStaff.instrumentIdx)
        if (!isPlaying) {
            targetOutput.send(msg)
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
     * Sounds a short preview of [pitch] through [instrumentIdx]'s output jack. Safe to call from the UI thread.
     */
    fun previewPitch(pitch: Double, isStaccato: Boolean = false, instrumentIdx: Int = 0) {
        val e = engine ?: return
        val durationMs = if (isStaccato) STACCATO_PREVIEW_DURATION_MS else PREVIEW_DURATION_MS
        e.post(PreviewScoreNoteCommand(outputForInstrument(instrumentIdx), pitch, durationMs))
    }

    private fun startPlayback(
        now: ScoreTime,
        startCursor: ScoreCursorPosition? = null,
        explicitStartTick: RationalTicks? = null,
    ) {
        if (isPlaying) stopPlayback(now)
        val e = engine ?: return

        val tempoVal = tempo.getDoubleValue(PianoRollTiming.DEFAULT_TEMPO).coerceAtLeast(1.0)
        val nsPerTick = PianoRollTiming.nanosPerTick(tempoVal)
        val staffIds = scoreData.allStaffIds()
        if (staffIds.none { staff -> scoreData.staffMeasures(staff).any { it.items.isNotEmpty() } }) return

        val maxMeasureCount = scoreData.maxMeasureCount()
        val targetCursor = if (explicitStartTick == null) {
            startCursor?.let { raw ->
                val targetStaff = raw.staff.takeIf { it in staffIds } ?: ScoreStaffId(0, 0)
                val staffMeasures = scoreData.staffMeasures(targetStaff)
                val clampedM = raw.measureIdx.coerceIn(0, staffMeasures.lastIndex)
                val clampedI = raw.itemIdx.coerceIn(0, staffMeasures[clampedM].items.size)
                ScoreCursorPosition(clampedM, clampedI, targetStaff)
            }
        } else {
            null
        }
        if (targetCursor != null && !loopMode) {
            val cursorStaffMeasures = scoreData.staffMeasures(targetCursor.staff)
            val hasRemainingOnCursorStaff = (targetCursor.measureIdx..cursorStaffMeasures.lastIndex).any { mIdx ->
                val startSlot = if (mIdx == targetCursor.measureIdx) targetCursor.itemIdx else 0
                startSlot < cursorStaffMeasures[mIdx].items.size
            }
            val hasLaterMeasureOnOtherStaff = (targetCursor.measureIdx + 1 until maxMeasureCount).any { mIdx ->
                staffIds.any { s -> scoreData.staffMeasures(s).getOrNull(mIdx)?.items?.isNotEmpty() == true }
            }
            if (!hasRemainingOnCursorStaff && !hasLaterMeasureOnOtherStaff) return
        }

        data class PlaybackSlot(
            val staff: ScoreStaffId,
            val targetOutput: VoiceOutputJack,
            val loc: ScoreItemLocation,
            val itemIdx: Int,
            val item: ScoreItem,
            val onsetTick: RationalTicks,
            val endTick: RationalTicks,
            val attackDyn: DynamicMark?,
        )

        isPlaying = true
        var effectiveTime = scoreData.instruments.firstOrNull()?.measures?.firstOrNull()?.timeSignature
            ?: scoreData.timeSignature
        var tickCursor = RationalTicks.ZERO
        var cursorStartTick: RationalTicks? = if (targetCursor == null) RationalTicks.ZERO else null
        var nextVoiceTag = 0

        val slotsByStaff = linkedMapOf<ScoreStaffId, MutableList<PlaybackSlot>>()
        val slotLookupByStaff = linkedMapOf<ScoreStaffId, MutableMap<ScoreItemLocation, PlaybackSlot>>()
        val trailingRestTicksByStaff = linkedMapOf<ScoreStaffId, MutableList<Pair<RationalTicks, ScoreItemLocation?>>>()
        val sustainedByStaff = staffIds.associateWith<ScoreStaffId, DynamicMark?> { null }.toMutableMap()
        val globalIdxByStaff = staffIds.associateWith { 0 }.toMutableMap()
        val hadSlotInBarBefore = staffIds.associateWith { false }.toMutableMap()
        val measureStartTicks = ArrayList<RationalTicks>(maxMeasureCount)
        val measureEndTicks = ArrayList<RationalTicks>(maxMeasureCount)

        for (mIdx in 0 until maxMeasureCount) {
            if (mIdx > 0) {
                scoreData.instruments.firstNotNullOfOrNull { it.measures.getOrNull(mIdx)?.timeSignature }
                    ?.let { effectiveTime = it }
            }
            val measureCapacityTicks = RationalTicks.of(effectiveTime.measureCapacityTicks.coerceAtLeast(0))
            val measureStartTick = tickCursor
            var maxStaffEndTick = measureStartTick

            for (staff in staffIds) {
                val staffMeasures = scoreData.staffMeasures(staff)
                val measure = staffMeasures.getOrNull(mIdx)
                val staffSlots = slotsByStaff.getOrPut(staff) { mutableListOf() }
                val staffLookup = slotLookupByStaff.getOrPut(staff) { mutableMapOf() }
                val staffOut = outputForInstrument(staff.instrumentIdx)
                var staffCursor = measureStartTick
                var sustainedDyn = sustainedByStaff[staff]

                val items = measure?.items.orEmpty()
                for ((slotIdx, item) in items.withIndex()) {
                    if (cursorStartTick == null &&
                        targetCursor != null &&
                        staff == targetCursor.staff &&
                        mIdx == targetCursor.measureIdx &&
                        slotIdx >= targetCursor.itemIdx
                    ) {
                        cursorStartTick = staffCursor
                    }
                    val (attackDyn, nextSustained) = ScoreEditing.resolvePlaybackDynamicStep(
                        measure?.dynamics?.get(slotIdx),
                        sustainedDyn,
                    )
                    sustainedDyn = nextSustained
                    val gIdx = (globalIdxByStaff[staff] ?: 0)
                    globalIdxByStaff[staff] = gIdx + 1
                    val itemTicks = item.exactTicks()
                    val loc = ScoreItemLocation(mIdx, slotIdx, staff = staff)
                    val slot = PlaybackSlot(
                        staff = staff,
                        targetOutput = staffOut,
                        loc = loc,
                        itemIdx = gIdx,
                        item = item,
                        onsetTick = staffCursor,
                        endTick = staffCursor + itemTicks,
                        attackDyn = attackDyn,
                    )
                    staffSlots.add(slot)
                    staffLookup[loc] = slot
                    staffCursor += itemTicks
                }

                if (cursorStartTick == null &&
                    targetCursor != null &&
                    staff == targetCursor.staff &&
                    mIdx == targetCursor.measureIdx &&
                    targetCursor.itemIdx >= items.size
                ) {
                    cursorStartTick = staffCursor
                }

                if (measure != null) {
                    for ((k, dyn) in measure.dynamics.entries.sortedBy { it.key }) {
                        if (k >= items.size) {
                            sustainedDyn = ScoreEditing.resolvePlaybackDynamicStep(dyn, sustainedDyn).second
                        }
                    }
                }
                sustainedByStaff[staff] = sustainedDyn

                val staffTicks = staffCursor - measureStartTick
                if (staffTicks < measureCapacityTicks && items.isNotEmpty()) {
                    // Schedule highlight clear when the last item of an incomplete bar finishes.
                    trailingRestTicksByStaff.getOrPut(staff) { mutableListOf() }
                        .add(staffCursor to null)
                } else if (items.isEmpty()) {
                    trailingRestTicksByStaff.getOrPut(staff) { mutableListOf() }
                        .add(measureStartTick to null)
                }
                if (staffCursor > maxStaffEndTick) {
                    maxStaffEndTick = staffCursor
                }
            }
            measureStartTicks.add(measureStartTick)
            tickCursor = maxOf(measureStartTick + measureCapacityTicks, maxStaffEndTick)
            measureEndTicks.add(tickCursor)
        }

        val totalScoreEndTick = tickCursor
        val loopRange = effectiveLoopRange(maxMeasureCount)
        val loopStartTick = measureStartTicks.getOrElse(loopRange.first) { RationalTicks.ZERO }
        val loopEndTick = measureEndTicks.getOrElse(loopRange.last) { totalScoreEndTick }

        var resolvedStartTick = explicitStartTick ?: cursorStartTick ?: RationalTicks.ZERO
        if (loopMode && explicitStartTick == null && resolvedStartTick >= totalScoreEndTick) {
            resolvedStartTick = loopStartTick
        }
        val cycleEndTick = if (loopMode && resolvedStartTick < loopEndTick) {
            loopEndTick.coerceAtMost(totalScoreEndTick)
        } else {
            totalScoreEndTick
        }
        val cycleDurationTicks = cycleEndTick - resolvedStartTick
        if (cycleDurationTicks <= RationalTicks.ZERO) {
            isPlaying = false
            return
        }
        val cycleDurationNs = (cycleDurationTicks.toDoubleTicks() * nsPerTick).roundToLong()
        val cycleEndTime = if (loopMode) {
            now + ScoreTime(cycleDurationNs)
        } else {
            now + ScoreTime(cycleDurationNs) + ScoreTime(1L)
        }

        var anyItemScheduled = false

        for (staff in staffIds) {
            val staffMeasures = scoreData.staffMeasures(staff)
            val staffSlots = slotsByStaff[staff].orEmpty()
            val slotByLoc = slotLookupByStaff[staff].orEmpty()
            val continuedPitchesByLoc = HashMap<ScoreItemLocation, MutableSet<Int>>()
            var hasActiveHighlightForStaff = false

            for (slot in staffSlots) {
                if (slot.onsetTick < resolvedStartTick || slot.onsetTick >= cycleEndTick) continue
                anyItemScheduled = true
                hasActiveHighlightForStaff = true
                val item = slot.item
                val relStartTicks = (slot.onsetTick - resolvedStartTick).toDoubleTicks()
                val onTime = now + ScoreTime((relStartTicks * nsPerTick).roundToLong())
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
                    while (ScoreEditing.hasActiveTieForPitch(staffMeasures, curLoc.measureIdx, curLoc.itemIdx, targetMidi)) {
                        val nextBase = ScoreEditing.nextItemLocation(staffMeasures, curLoc.measureIdx, curLoc.itemIdx) ?: break
                        val nextLoc = nextBase.copy(staff = staff)
                        val nextSlot = slotByLoc[nextLoc] ?: break
                        if (nextSlot.onsetTick >= cycleEndTick) break
                        continuedPitchesByLoc.getOrPut(nextLoc) { mutableSetOf() }.add(targetMidi)
                        endSlot = nextSlot
                        curLoc = nextLoc
                    }
                    val clampedEndTick = minOf(endSlot.endTick, cycleEndTick)
                    val sustainTicks = maxOf(clampedEndTick - slot.onsetTick, RationalTicks.ZERO).toDoubleTicks()
                    val sustainDurSec = (sustainTicks * nsPerTick) / 1_000_000_000.0
                    val soundingDurSec = if (endSlot.item.noteStaccato()) {
                        val leadTicks = maxOf(endSlot.onsetTick - slot.onsetTick, RationalTicks.ZERO).toDoubleTicks()
                        val lastNoteFullTicks = maxOf(clampedEndTick - endSlot.onsetTick, RationalTicks.ZERO).toDoubleTicks()
                        val leadSec = (leadTicks * nsPerTick) / 1_000_000_000.0
                        val lastNoteFullSec = (lastNoteFullTicks * nsPerTick) / 1_000_000_000.0
                        val staccatoTailSec = minOf(lastNoteFullSec * STACCATO_DURATION_FRACTION, MAX_STACCATO_SECONDS)
                            .coerceAtLeast(minOf(MIN_STACCATO_SECONDS, lastNoteFullSec))
                        minOf(sustainDurSec, leadSec + staccatoTailSec)
                    } else {
                        sustainDurSec
                    }
                    val rawOffTime = onTime + ScoreTime((soundingDurSec * 1_000_000_000.0).roundToLong())
                    val soundingOffTime = if (rawOffTime > cycleEndTime) cycleEndTime else rawOffTime
                    PitchAttackSpec(
                        midiPitch = targetMidi.toDouble(),
                        tag = nextVoiceTag++,
                        soundingDurSec = soundingDurSec,
                        soundingOffTime = soundingOffTime,
                    )
                }

                val targetOutput = slot.targetOutput
                scheduledEvents.add(e.schedule(onTime) {
                    activeItemsByStaff = activeItemsByStaff + (staff to slot.itemIdx)
                    if (staff == ScoreStaffId(0, 0)) {
                        activeItemIndex = slot.itemIdx
                    }
                    val activeMap = soundingNotesByOutput.getOrPut(targetOutput) { mutableMapOf() }
                    for (spec in attackSpecs) {
                        targetOutput.send(
                            Message.VoiceMessage(
                                time = onTime,
                                opcode = Message.VoiceOpcode.NOTE,
                                tag = spec.tag,
                                value = spec.midiPitch,
                                extra = effectiveAmp,
                                duration = spec.soundingDurSec,
                            )
                        )
                        activeMap[spec.tag] = spec.midiPitch
                    }
                })
                for (spec in attackSpecs) {
                    scheduledEvents.add(e.schedule(spec.soundingOffTime) {
                        soundingNotesByOutput[targetOutput]?.remove(spec.tag)
                    })
                }
            }

            // Emit highlight clear whenever playback enters an empty/incomplete region after active notes.
            for (slot in staffSlots) {
                if (slot.onsetTick < resolvedStartTick || slot.onsetTick >= cycleEndTick) continue
                hadSlotInBarBefore[staff] = true
            }
            if (hasActiveHighlightForStaff) {
                for ((restTick, _) in trailingRestTicksByStaff[staff].orEmpty()) {
                    if (restTick >= resolvedStartTick && restTick < cycleEndTick) {
                        val hasPreviousPlayedNote = staffSlots.any {
                            it.onsetTick >= resolvedStartTick && it.onsetTick < cycleEndTick && it.endTick <= restTick
                        }
                        if (hasPreviousPlayedNote) {
                            val relRestTicks = (restTick - resolvedStartTick).toDoubleTicks()
                            val restStartTime = now + ScoreTime((relRestTicks * nsPerTick).roundToLong())
                            scheduledEvents.add(e.schedule(restStartTime) {
                                activeItemsByStaff = activeItemsByStaff - staff
                                if (staff == ScoreStaffId(0, 0)) {
                                    activeItemIndex = null
                                }
                            })
                        }
                    }
                }
            }
        }

        if (!anyItemScheduled && targetCursor != null && !loopMode) {
            isPlaying = false
            return
        }

        scheduledEvents.add(e.schedule(cycleEndTime) {
            if (loopMode) {
                val nextRange = effectiveLoopRange(scoreData.maxMeasureCount())
                val nextStartTick = measureStartTickForIndex(nextRange.first)
                startPlayback(cycleEndTime, explicitStartTick = nextStartTick)
            } else if (cycleEndTick < totalScoreEndTick) {
                startPlayback(cycleEndTime, explicitStartTick = cycleEndTick)
            } else {
                activeItemsByStaff = emptyMap()
                activeItemIndex = null
                isPlaying = false
                scheduledEvents.clear()
            }
        })
    }

    private fun measureStartTickForIndex(measureIdx: Int): RationalTicks {
        val count = scoreData.maxMeasureCount()
        if (measureIdx <= 0 || count <= 0) return RationalTicks.ZERO
        var effectiveTime = scoreData.instruments.firstOrNull()?.measures?.firstOrNull()?.timeSignature
            ?: scoreData.timeSignature
        val staffIds = scoreData.allStaffIds()
        var tickCursor = RationalTicks.ZERO
        val targetIdx = measureIdx.coerceAtMost(count - 1)
        for (mIdx in 0 until targetIdx) {
            if (mIdx > 0) {
                scoreData.instruments.firstNotNullOfOrNull { it.measures.getOrNull(mIdx)?.timeSignature }
                    ?.let { effectiveTime = it }
            }
            val measureCapacityTicks = RationalTicks.of(effectiveTime.measureCapacityTicks.coerceAtLeast(0))
            val measureStartTick = tickCursor
            var maxStaffEndTick = measureStartTick
            for (staff in staffIds) {
                val measure = scoreData.staffMeasures(staff).getOrNull(mIdx)
                var staffCursor = measureStartTick
                measure?.items.orEmpty().forEach { staffCursor += it.exactTicks() }
                if (staffCursor > maxStaffEndTick) maxStaffEndTick = staffCursor
            }
            tickCursor = maxOf(measureStartTick + measureCapacityTicks, maxStaffEndTick)
        }
        return tickCursor
    }

    private fun stopPlayback(now: ScoreTime) {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        for ((outJack, activeNotes) in soundingNotesByOutput) {
            for ((tag, pitch) in activeNotes) {
                outJack.send(
                    Message.VoiceMessage(
                        time = now,
                        opcode = Message.VoiceOpcode.NOTE_OFF,
                        tag = tag,
                        value = pitch,
                        extra = 0.0,
                    )
                )
            }
            activeNotes.clear()
        }
        soundingNotesByOutput.clear()
        activeItemsByStaff = emptyMap()
        activeItemIndex = null
        isPlaying = false
    }

    override fun teardown() {
        stopPlayback(ScoreClock.now())
        super.teardown()
    }

    companion object {
        const val DEFAULT_LOOP_END_MEASURE = 3
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

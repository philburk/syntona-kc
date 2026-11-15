package com.syntona.dataflow.score

import com.syntona.dataflow.editor.FileState
import com.syntona.dataflow.engine.SyntonaCommand

/**
 * Undoable command replacing the measures list in a [ScoreData] document.
 * Because [ScoreMeasure] and [ScoreItem] are immutable data classes, snapshotting
 * before/after measure lists supports every Phase 2 edit symmetrically.
 */
class ReplaceScoreMeasuresCommand(
    private val scoreData: ScoreData,
    private val beforeMeasures: List<ScoreMeasure>,
    private val afterMeasures: List<ScoreMeasure>,
    private val staff: ScoreStaffId = ScoreStaffId(0, 0),
    private val onSelectionSync: ((List<ScoreMeasure>) -> Unit)? = null,
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        scoreData.replaceStaffMeasures(staff, afterMeasures)
        onSelectionSync?.invoke(afterMeasures)
        FileState.markDirty()
    }

    override fun undo() {
        scoreData.replaceStaffMeasures(staff, beforeMeasures)
        onSelectionSync?.invoke(beforeMeasures)
        FileState.markDirty()
    }
}

/**
 * Undoable command for step-recording an incoming MIDI note into [ScoreModule] at its
 * current [ScoreModule.cursorPosition]. Computing the edit inside [execute] preserves order
 * when multiple notes arrive in the engine queue, while [undo] restores measures and cursor.
 */
class StepRecordScoreNoteCommand(
    private val module: ScoreModule,
    private val midiPitch: Int,
    private val amplitude: Double,
) : SyntonaCommand {
    override val isUndoable = true

    private var beforeMeasures: List<ScoreMeasure>? = null
    private var beforeCursor: ScoreCursorPosition? = null
    private var afterMeasures: List<ScoreMeasure>? = null
    private var afterCursor: ScoreCursorPosition? = null
    private var pitchDisplayText: String? = null
    private var targetStaff: ScoreStaffId = ScoreStaffId(0, 0)

    override fun execute() {
        val prevCursor = beforeCursor ?: module.cursorPosition
            ?: ScoreEditing.defaultStepCursor(module.scoreData.staffMeasures(ScoreStaffId(0, 0)))
        targetStaff = prevCursor.staff
        val prevMeasures = beforeMeasures ?: module.scoreData.staffMeasures(targetStaff).toList()
        val cachedAfter = afterMeasures
        val cachedCursor = afterCursor

        if (cachedAfter != null && cachedCursor != null) {
            module.scoreData.replaceStaffMeasures(targetStaff, cachedAfter)
            module.cursorPosition = cachedCursor
        } else {
            val activeKey = ScoreEditing.effectiveKeyAtMeasure(
                measures = prevMeasures,
                initialKey = module.scoreData.keySignature,
                measureIdx = prevCursor.measureIdx,
            )
            val spelled = SpelledPitch.fromMidiPitch(midiPitch, activeKey)
            val insertMIdx = prevCursor.measureIdx.coerceIn(0, prevMeasures.lastIndex.coerceAtLeast(0))
            val insertIIdx = prevMeasures.getOrNull(insertMIdx)?.let {
                prevCursor.itemIdx.coerceIn(0, it.items.size)
            } ?: 0
            val activeTuplet = module.resolveTupletForInsertion(
                measures = prevMeasures,
                measureIdx = insertMIdx,
                insertIdx = insertIIdx,
                baseTicks = module.stepRecordDuration.totalTicks(module.stepRecordDotted),
            )
            val note = ScoreItem.Note(
                pitch = spelled,
                duration = module.stepRecordDuration,
                isDotted = module.stepRecordDotted,
                amplitude = amplitude.coerceIn(0.05, 1.0),
                isStaccato = module.stepRecordStaccato,
                tuplet = activeTuplet,
            )
            val activeTime = ScoreEditing.effectiveTimeAtMeasure(
                measures = prevMeasures,
                initialTime = module.scoreData.timeSignature,
                measureIdx = prevCursor.measureIdx,
            )
            val result = ScoreEditing.stepInsertItem(
                measures = prevMeasures,
                cursor = prevCursor,
                item = note,
                measureCapacityTicks = activeTime.measureCapacityTicks,
            )
            beforeMeasures = prevMeasures
            beforeCursor = prevCursor
            afterMeasures = result.measures
            afterCursor = result.nextCursor
            pitchDisplayText = spelled.pitchDisplay()

            module.scoreData.replaceStaffMeasures(targetStaff, result.measures)
            module.cursorPosition = result.nextCursor
        }
        module.lastStepRecordedText = pitchDisplayText
        module.stepRecordRevision++
        FileState.markDirty()
    }

    override fun undo() {
        val prevMeasures = beforeMeasures ?: return
        module.scoreData.replaceStaffMeasures(targetStaff, prevMeasures)
        module.cursorPosition = beforeCursor
        module.lastStepRecordedText = null
        module.stepRecordRevision++
        FileState.markDirty()
    }
}

/**
 * Undoable command updating the score's opening and/or per-measure key signatures while leaving
 * all note pitch values unchanged. Propagates measure-level key changes across all staves.
 */
class SetScoreKeySignatureCommand(
    private val scoreData: ScoreData,
    private val beforeInitialKey: KeySignature,
    private val beforeMeasures: List<ScoreMeasure>,
    private val afterInitialKey: KeySignature,
    private val afterMeasures: List<ScoreMeasure>,
    private val staff: ScoreStaffId = ScoreStaffId(0, 0),
    private val onSync: (() -> Unit)? = null,
) : SyntonaCommand {
    override val isUndoable = true
    private var beforeInstrumentsSnapshot: List<ScoreInstrumentPart>? = null

    override fun execute() {
        if (beforeInstrumentsSnapshot == null) {
            beforeInstrumentsSnapshot = scoreData.instruments.toList()
        }
        scoreData.keySignature = afterInitialKey
        scoreData.replaceStaffMeasures(staff, afterMeasures)
        val syncKeys = afterMeasures.map { it.keySignature }
        for (iIdx in scoreData.instruments.indices) {
            val part = scoreData.instruments[iIdx]
            var updatedPart = part
            if (ScoreStaffId(iIdx, 0) != staff) {
                val synced = part.measures.mapIndexed { mIdx, m ->
                    val k = syncKeys.getOrNull(mIdx)
                    if (m.keySignature != k) m.copy(keySignature = k) else m
                }
                updatedPart = updatedPart.withStaffMeasures(0, synced)
            }
            if (part.staffCount >= 2 && ScoreStaffId(iIdx, 1) != staff) {
                val syncedLower = part.lowerMeasures.mapIndexed { mIdx, m ->
                    val k = syncKeys.getOrNull(mIdx)
                    if (m.keySignature != k) m.copy(keySignature = k) else m
                }
                updatedPart = updatedPart.withStaffMeasures(1, syncedLower)
            }
            if (updatedPart !== part) {
                scoreData.instruments[iIdx] = updatedPart
            }
        }
        onSync?.invoke()
        FileState.markDirty()
    }

    override fun undo() {
        scoreData.keySignature = beforeInitialKey
        val snapshot = beforeInstrumentsSnapshot
        if (snapshot != null) {
            scoreData.replaceAllInstruments(snapshot)
        } else {
            scoreData.replaceStaffMeasures(staff, beforeMeasures)
        }
        onSync?.invoke()
        FileState.markDirty()
    }
}

/**
 * Undoable command updating the score's opening and/or per-measure time signatures across all staves.
 */
class SetScoreTimeSignatureCommand(
    private val scoreData: ScoreData,
    private val beforeInitialTime: TimeSignature,
    private val beforeMeasures: List<ScoreMeasure>,
    private val afterInitialTime: TimeSignature,
    private val afterMeasures: List<ScoreMeasure>,
    private val staff: ScoreStaffId = ScoreStaffId(0, 0),
    private val onSync: (() -> Unit)? = null,
) : SyntonaCommand {
    override val isUndoable = true
    private var beforeInstrumentsSnapshot: List<ScoreInstrumentPart>? = null

    override fun execute() {
        if (beforeInstrumentsSnapshot == null) {
            beforeInstrumentsSnapshot = scoreData.instruments.toList()
        }
        scoreData.timeSignature = afterInitialTime
        scoreData.replaceStaffMeasures(staff, afterMeasures)
        val syncTimes = afterMeasures.map { it.timeSignature }
        for (iIdx in scoreData.instruments.indices) {
            val part = scoreData.instruments[iIdx]
            var updatedPart = part
            if (ScoreStaffId(iIdx, 0) != staff) {
                val synced = part.measures.mapIndexed { mIdx, m ->
                    val t = syncTimes.getOrNull(mIdx)
                    if (m.timeSignature != t) m.copy(timeSignature = t) else m
                }
                updatedPart = updatedPart.withStaffMeasures(0, synced)
            }
            if (part.staffCount >= 2 && ScoreStaffId(iIdx, 1) != staff) {
                val syncedLower = part.lowerMeasures.mapIndexed { mIdx, m ->
                    val t = syncTimes.getOrNull(mIdx)
                    if (m.timeSignature != t) m.copy(timeSignature = t) else m
                }
                updatedPart = updatedPart.withStaffMeasures(1, syncedLower)
            }
            if (updatedPart !== part) {
                scoreData.instruments[iIdx] = updatedPart
            }
        }
        onSync?.invoke()
        FileState.markDirty()
    }

    override fun undo() {
        scoreData.timeSignature = beforeInitialTime
        val snapshot = beforeInstrumentsSnapshot
        if (snapshot != null) {
            scoreData.replaceAllInstruments(snapshot)
        } else {
            scoreData.replaceStaffMeasures(staff, beforeMeasures)
        }
        onSync?.invoke()
        FileState.markDirty()
    }
}

/**
 * Undoable command updating a staff's opening clef and/or per-measure / mid-measure clefs
 * while leaving all note pitch values unchanged.
 */
class SetScoreClefCommand(
    private val scoreData: ScoreData,
    private val beforeInitialClef: Clef,
    private val beforeMeasures: List<ScoreMeasure>,
    private val afterInitialClef: Clef,
    private val afterMeasures: List<ScoreMeasure>,
    private val staff: ScoreStaffId = ScoreStaffId(0, 0),
    private val onSync: (() -> Unit)? = null,
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        scoreData.setStaffClef(staff, afterInitialClef)
        scoreData.replaceStaffMeasures(staff, afterMeasures)
        onSync?.invoke()
        FileState.markDirty()
    }

    override fun undo() {
        scoreData.setStaffClef(staff, beforeInitialClef)
        scoreData.replaceStaffMeasures(staff, beforeMeasures)
        onSync?.invoke()
        FileState.markDirty()
    }
}

/** Atomically replaces opening clef/key/time signatures and all measures on [staff]. */
class ReplaceScoreDocumentCommand(
    private val scoreData: ScoreData,
    private val beforeInitialClef: Clef,
    private val beforeInitialKey: KeySignature,
    private val beforeInitialTime: TimeSignature,
    private val beforeMeasures: List<ScoreMeasure>,
    private val afterInitialClef: Clef,
    private val afterInitialKey: KeySignature,
    private val afterInitialTime: TimeSignature,
    private val afterMeasures: List<ScoreMeasure>,
    private val staff: ScoreStaffId = ScoreStaffId(0, 0),
    private val onSync: ((List<ScoreMeasure>, Boolean) -> Unit)? = null,
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        scoreData.setStaffClef(staff, afterInitialClef)
        scoreData.keySignature = afterInitialKey
        scoreData.timeSignature = afterInitialTime
        scoreData.replaceStaffMeasures(staff, afterMeasures)
        onSync?.invoke(afterMeasures, false)
        FileState.markDirty()
    }

    override fun undo() {
        scoreData.setStaffClef(staff, beforeInitialClef)
        scoreData.keySignature = beforeInitialKey
        scoreData.timeSignature = beforeInitialTime
        scoreData.replaceStaffMeasures(staff, beforeMeasures)
        onSync?.invoke(beforeMeasures, true)
        FileState.markDirty()
    }
}

/**
 * Undoable command replacing the instrument roster of [ScoreModule] and synchronizing its per-instrument
 * [com.syntona.dataflow.model.VoiceOutputJack] instances.
 */
class ReplaceScoreInstrumentsCommand(
    private val module: ScoreModule,
    private val beforeInstruments: List<ScoreInstrumentPart>,
    private val afterInstruments: List<ScoreInstrumentPart>,
    private val onSync: (() -> Unit)? = null,
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        module.scoreData.replaceAllInstruments(afterInstruments)
        module.syncInstrumentOutputs()
        onSync?.invoke()
        FileState.markDirty()
    }

    override fun undo() {
        module.scoreData.replaceAllInstruments(beforeInstruments)
        module.syncInstrumentOutputs()
        onSync?.invoke()
        FileState.markDirty()
    }
}

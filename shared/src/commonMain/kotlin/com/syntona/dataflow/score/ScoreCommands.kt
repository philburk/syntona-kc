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
    private val onSelectionSync: ((List<ScoreMeasure>) -> Unit)? = null,
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        scoreData.replaceMeasures(afterMeasures)
        onSelectionSync?.invoke(afterMeasures)
        FileState.markDirty()
    }

    override fun undo() {
        scoreData.replaceMeasures(beforeMeasures)
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

    override fun execute() {
        val prevMeasures = beforeMeasures ?: module.scoreData.measures.toList()
        val prevCursor = beforeCursor ?: module.cursorPosition ?: ScoreEditing.defaultStepCursor(prevMeasures)
        val cachedAfter = afterMeasures
        val cachedCursor = afterCursor

        if (cachedAfter != null && cachedCursor != null) {
            module.scoreData.replaceMeasures(cachedAfter)
            module.cursorPosition = cachedCursor
        } else {
            val spelled = SpelledPitch.fromMidiPitch(midiPitch, module.scoreData.keySignature)
            val note = ScoreItem.Note(
                pitch = spelled,
                duration = module.stepRecordDuration,
                amplitude = amplitude.coerceIn(0.05, 1.0),
                isStaccato = module.stepRecordStaccato,
            )
            val result = ScoreEditing.stepInsertItem(
                measures = prevMeasures,
                cursor = prevCursor,
                item = note,
                measureCapacityTicks = module.scoreData.timeSignature.measureCapacityTicks,
            )
            beforeMeasures = prevMeasures
            beforeCursor = prevCursor
            afterMeasures = result.measures
            afterCursor = result.nextCursor
            pitchDisplayText = spelled.pitchDisplay()

            module.scoreData.replaceMeasures(result.measures)
            module.cursorPosition = result.nextCursor
        }
        module.lastStepRecordedText = pitchDisplayText
        module.stepRecordRevision++
        FileState.markDirty()
    }

    override fun undo() {
        val prevMeasures = beforeMeasures ?: return
        module.scoreData.replaceMeasures(prevMeasures)
        module.cursorPosition = beforeCursor
        module.lastStepRecordedText = null
        module.stepRecordRevision++
        FileState.markDirty()
    }
}

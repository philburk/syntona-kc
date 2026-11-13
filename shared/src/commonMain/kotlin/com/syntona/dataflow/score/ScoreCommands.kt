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

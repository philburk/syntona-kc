package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.FileState
import com.syntona.dataflow.engine.SyntonaCommand

/**
 * Undoable commands for note-level edits in a [PianoRollModule]. Posted to the
 * [com.syntona.dataflow.engine.DataFlowEngine] from the editor so they participate
 * in the global undo/redo stack.
 */

/** Snapshot of the mutable fields of a [PianoRollNote]. */
data class NoteFields(
    val startTicks: Int,
    val durationTicks: Int,
    val parentId: Int,
    val ratio: Ratio,
    val rootPitch: Double,
    val amplitude: Double = PianoRollModule.NOTE_AMPLITUDE,
)

fun PianoRollNote.captureFields() = NoteFields(startTicks, durationTicks, parentId, ratio, rootPitch, amplitude)

fun PianoRollNote.applyFields(fields: NoteFields) {
    startTicks = fields.startTicks
    durationTicks = fields.durationTicks
    parentId = fields.parentId
    ratio = fields.ratio
    rootPitch = fields.rootPitch
    amplitude = fields.amplitude
}

/** Adds [newNotes] to the module. Also used by Paste. */
class AddNotesCommand(
    private val module: PianoRollModule,
    private val newNotes: List<PianoRollNote>,
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        module.notes.addAll(newNotes)
        FileState.markDirty()
    }

    override fun undo() {
        val ids = newNotes.map { it.id }.toSet()
        module.notes.removeAll { it.id in ids }
        FileState.markDirty()
    }
}

/**
 * Deletes the given notes and all of their dependents. The full set is computed at
 * construction time; undo restores the same note objects with their fields intact.
 */
class DeleteNotesCommand(
    module: PianoRollModule,
    noteIds: Collection<Int>,
) : SyntonaCommand {
    private val module = module
    private val toDelete: List<PianoRollNote> = buildList {
        val ids = mutableSetOf<Int>()
        for (id in noteIds) {
            ids.add(id)
            module.descendantsOf(id).forEach { ids.add(it.id) }
        }
        addAll(module.notes.filter { it.id in ids })
    }

    override val isUndoable = true

    val deletedIds: Set<Int> get() = toDelete.map { it.id }.toSet()

    override fun execute() {
        val ids = deletedIds
        module.notes.removeAll { it.id in ids }
        FileState.markDirty()
    }

    override fun undo() {
        module.notes.addAll(toDelete)
        FileState.markDirty()
    }
}

/**
 * Applies field changes to existing notes — covers move, resize, ratio re-derivation,
 * and Chain-tool re-parenting in one symmetric command.
 */
class ChangeNotesCommand(
    private val module: PianoRollModule,
    private val edits: List<Edit>,
) : SyntonaCommand {
    data class Edit(val id: Int, val before: NoteFields, val after: NoteFields)

    override val isUndoable = true

    override fun execute() = apply { edit -> edit.after }

    override fun undo() = apply { edit -> edit.before }

    private inline fun apply(fields: (Edit) -> NoteFields) {
        for (edit in edits) {
            module.findNote(edit.id)?.applyFields(fields(edit))
        }
        FileState.markDirty()
    }
}

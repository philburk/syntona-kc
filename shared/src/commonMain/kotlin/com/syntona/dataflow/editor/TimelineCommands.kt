package com.syntona.dataflow.editor

import com.syntona.dataflow.engine.SyntonaCommand
import com.syntona.dataflow.modules.TimelineTrack
import com.syntona.dataflow.modules.TrackSection

class DeleteSectionCommand(
    val track: TimelineTrack,
    val section: TrackSection
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        track.removeSection(section)
    }

    override fun undo() {
        track.insertSection(section)
    }
}

/**
 * Creates a section with [create] on the engine thread, so the track's section list is only
 * changed there. [create] returns null if there is no room, and then nothing is added to the undo history.
 */
class AddSectionCommand(
    val track: TimelineTrack,
    private val create: () -> TrackSection?,
    private val onAdded: (TrackSection) -> Unit = {}
) : SyntonaCommand {
    var section: TrackSection? = null
        private set

    override val isUndoable: Boolean get() = section != null

    override fun execute() {
        val existing = section
        if (existing == null) {
            section = create()?.also(onAdded)
        } else {
            track.insertSection(existing) // Redo
        }
    }

    override fun undo() {
        section?.let { track.removeSection(it) }
    }
}

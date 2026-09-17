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
        if (!track.sections.contains(section)) {
            val insertIndex = track.sections.indexOfFirst { it.start > section.start }
            if (insertIndex >= 0) {
                track.sections.add(insertIndex, section)
            } else {
                track.sections.add(section)
            }
        }
    }
}

class AddSectionCommand(
    val track: TimelineTrack,
    val section: TrackSection
) : SyntonaCommand {
    override val isUndoable = true

    override fun execute() {
        if (!track.sections.contains(section)) {
            val insertIndex = track.sections.indexOfFirst { it.start > section.start }
            if (insertIndex >= 0) {
                track.sections.add(insertIndex, section)
            } else {
                track.sections.add(section)
            }
        }
    }

    override fun undo() {
        track.removeSection(section)
    }
}

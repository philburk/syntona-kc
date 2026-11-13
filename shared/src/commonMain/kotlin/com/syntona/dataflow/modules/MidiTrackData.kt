package com.syntona.dataflow.modules

import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message

/**
 * A discrete voice message event recorded with its relative arrival time (in timeline units)
 * from the start of a TrackSection.
 */
data class TrackVoiceEvent(
    var time: Double,
    val opcode: Message.VoiceOpcode,
    val tag: Int,
    val value: Double,
    val extra: Double
)

/**
 * Section data payload for a [MidiTrack]. Wraps a [PianoRollModule] which holds the
 * musical note collection, intonation chains, and editing state, and stores arbitrary
 * voice message events (e.g. PITCH_BEND, CONTROL_CHANGE, PRESSURE).
 */
class MidiSectionData(
    val pianoRoll: PianoRollModule = PianoRollModule()
) {
    var bridgeJack: InputJack? = null
    val voiceEvents = mutableListOf<TrackVoiceEvent>()

    /**
     * Creates a deep copy of this section's note data, intonation settings, and voice events.
     */
    fun copy(): MidiSectionData {
        val newModule = PianoRollModule()
        newModule.name = pianoRoll.name
        newModule.tempo.setValue(pianoRoll.tempo.getDoubleValue(PianoRollTiming.DEFAULT_TEMPO))
        pianoRoll.notes.forEach { note ->
            newModule.notes.add(note.copyData())
        }
        newModule.resetNextNoteId()
        val copyData = MidiSectionData(newModule)
        copyData.voiceEvents.addAll(voiceEvents.map { it.copy() })
        return copyData
    }

    /**
     * Convenience helper to add a root note to the backing [PianoRollModule].
     */
    fun addNote(
        startTicks: Int,
        durationTicks: Int,
        pitch: Double,
        amplitude: Double = PianoRollModule.NOTE_AMPLITUDE,
    ): PianoRollNote {
        val nid = pianoRoll.allocateNoteId()
        val note = PianoRollNote(
            id = nid,
            startTicks = startTicks,
            durationTicks = durationTicks,
            parentId = -1,
            ratio = Ratio.UNISON,
            rootPitch = pitch,
            amplitude = amplitude,
        )
        pianoRoll.notes.add(note)
        return note
    }

    /**
     * Adds a voice event in chronological order.
     */
    fun addVoiceEvent(time: Double, opcode: Message.VoiceOpcode, tag: Int, value: Double, extra: Double): TrackVoiceEvent {
        val event = TrackVoiceEvent(time, opcode, tag, value, extra)
        if (voiceEvents.isEmpty() || voiceEvents.last().time <= time) {
            voiceEvents.add(event)
        } else {
            val insertIndex = voiceEvents.indexOfFirst { it.time > time }
            if (insertIndex >= 0) {
                voiceEvents.add(insertIndex, event)
            } else {
                voiceEvents.add(event)
            }
        }
        return event
    }
}

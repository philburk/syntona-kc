package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.unitgen.PassThrough
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.model.VoiceInputJack
import com.syntona.dataflow.model.VoiceOutputJack

enum class TrackType {
    PATCH, MESSAGE, MIDI, AUDIO;

    val displayName: String
        get() = when (this) {
            PATCH -> "Patch"
            MESSAGE -> "Message"
            MIDI -> "MIDI"
            AUDIO -> "Audio"
        }

    val isRecordable: Boolean
        get() = when (this) {
            PATCH -> false
            MESSAGE -> true
            MIDI -> true
            AUDIO -> true
        }
}

class TrackSection(
    val id: Int,
    initialStart: Double,
    initialDuration: Double,
    initialRepeats: Int = 1,
    initialName: String = ""
) {
    var start by mutableStateOf(initialStart.coerceAtLeast(0.0))
    var duration by mutableStateOf(initialDuration.coerceAtLeast(0.01))
    var repeats by mutableStateOf(initialRepeats.coerceAtLeast(1))
    var name by mutableStateOf(initialName)

    /** Optional track-specific data payload (e.g. note list, column values, audio buffer). */
    var data: Any? = null

    val totalDuration: Double
        get() = duration * repeats

    val end: Double
        get() = start + totalDuration
}

abstract class TimelineTrack(
    val id: Int,
    val type: TrackType,
    initialName: String,
    val timeline: TimelineModule
) {
    var name by mutableStateOf(initialName)
    var isMuted by mutableStateOf(false)
    var isSoloed by mutableStateOf(false)
    var isRecordArmed by mutableStateOf(false)
    var isInputMonitored by mutableStateOf(false)

    val sections = mutableStateListOf<TrackSection>()
    private var nextSectionId = 0

    open val inputJack: InputJack? = null
    open val outputJack: OutputJack? = null

    fun addSection(start: Double, duration: Double, repeats: Int = 1, name: String = ""): TrackSection {
        val sId = nextSectionId++
        return addSectionDirect(sId, start, duration, repeats, name)
    }

    fun addSectionDirect(id: Int, start: Double, duration: Double, repeats: Int = 1, name: String = ""): TrackSection {
        val section = TrackSection(id, start, duration, repeats, name)
        sections.add(section)
        if (id >= nextSectionId) {
            nextSectionId = id + 1
        }
        return section
    }

    fun removeSection(section: TrackSection) {
        sections.remove(section)
    }

    fun clearSections() {
        sections.clear()
        nextSectionId = 0
    }

    open fun teardown() {
        inputJack?.let { timeline.deleteTrackInput(it) }
        outputJack?.let { timeline.deleteTrackOutput(it) }
        clearSections()
    }

    open fun onPlaybackStart(startUnits: Double, isPlaying: Boolean) {}
    open fun onPlaybackStop() {}
    open fun onRecordStart(startUnits: Double) {}
    open fun onRecordStop() {}
}

class PatchTrack(
    id: Int,
    name: String,
    timeline: TimelineModule
) : TimelineTrack(id, TrackType.PATCH, name, timeline) {
    // Sub-patch embedding and dynamic child output syncing will be hooked up in Phase 2
}

class MessageTrack(
    id: Int,
    name: String,
    timeline: TimelineModule,
    inJackName: String? = null,
    outJackName: String? = null
) : TimelineTrack(id, TrackType.MESSAGE, name, timeline) {
    override val inputJack: InputJack = timeline.createTrackInput(inJackName ?: "in$id")
    override val outputJack: OutputJack = timeline.createTrackOutput(outJackName ?: "out$id")
}

class MidiTrack(
    id: Int,
    name: String,
    timeline: TimelineModule,
    inJackName: String? = null,
    outJackName: String? = null
) : TimelineTrack(id, TrackType.MIDI, name, timeline) {
    override val inputJack: VoiceInputJack = timeline.createTrackVoiceInput(inJackName ?: "in$id")
    override val outputJack: VoiceOutputJack = timeline.createTrackVoiceOutput(outJackName ?: "out$id")
}

class AudioTrack(
    id: Int,
    name: String,
    timeline: TimelineModule,
    inJackName: String? = null,
    outJackName: String? = null
) : TimelineTrack(id, TrackType.AUDIO, name, timeline) {
    val passThrough = PassThrough()
    override val inputJack: AudioInputJack = timeline.createTrackAudioInput(inJackName ?: "in$id", passThrough.input)
    override val outputJack: AudioOutputJack = timeline.createTrackAudioOutput(outJackName ?: "out$id", passThrough.output)
}

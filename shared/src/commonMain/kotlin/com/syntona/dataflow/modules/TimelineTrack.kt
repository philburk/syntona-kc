package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.UnitFilter
import com.syntona.core.audio.AudioSettings
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.util.SegmentedFloatArray
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
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

    fun copy(newId: Int = id, newStart: Double = start): TrackSection {
        val sec = TrackSection(newId, newStart, duration, repeats, name)
        sec.data = when (val d = data) {
            is MessageSectionData -> d.copy()
            is MidiSectionData -> d.copy()
            is AudioSectionData -> d.copy()
            else -> d
        }
        return sec
    }
}

abstract class TimelineTrack(
    val id: Int,
    val type: TrackType,
    initialName: String,
    val timeline: TimelineModule
) {
    open var name by mutableStateOf(initialName)

    private var _isMuted by mutableStateOf(false)
    var isMuted: Boolean
        get() = _isMuted
        set(value) {
            if (_isMuted != value) {
                _isMuted = value
                timeline.onTrackAudibilityChanged()
            }
        }

    private var _isSoloed by mutableStateOf(false)
    var isSoloed: Boolean
        get() = _isSoloed
        set(value) {
            if (_isSoloed != value) {
                _isSoloed = value
                timeline.onTrackAudibilityChanged()
            }
        }

    open var isRecordArmed by mutableStateOf(false)
    open var isInputMonitored by mutableStateOf(false)

    val isSoloActive: Boolean
        get() {
            val anySoloed = timeline.tracks.any { it.isSoloed }
            return if (anySoloed) isSoloed else true
        }

    val isAudible: Boolean
        get() = !isMuted && isSoloActive

    val sections = mutableStateListOf<TrackSection>()
    private var nextSectionId = 0

    open val inputJack: InputJack? = null
    open val outputJack: OutputJack? get() = outputJacks.firstOrNull()
    open val outputJacks: List<OutputJack> get() = listOfNotNull(outputJack)

    companion object {
        /** Shortest a section can be, matching the floor that [getMaxDuration] reports. */
        const val MIN_SECTION_DURATION = 0.01
    }

    fun canAddSectionAt(start: Double, duration: Double = 0.05): Boolean {
        if (start < 0.0) return false
        // A section starting at the end of the timeline has no room to grow and cannot play
        if (start >= timeline.maxTime) return false
        if (sections.any { start >= it.start && start < it.end }) return false
        val nextSec = sections.filter { it.start > start }.minByOrNull { it.start }
        if (nextSec != null && (nextSec.start - start) < 0.05) return false
        return true
    }

    fun getAvailableDurationAt(start: Double, desiredDuration: Double): Double {
        val nextSec = sections.filter { it.start > start }.minByOrNull { it.start }
        return if (nextSec != null) minOf(desiredDuration, nextSec.start - start) else desiredDuration
    }

    fun getMoveBounds(section: TrackSection, maxTime: Double): ClosedFloatingPointRange<Double> {
        val prevEnd = sections.filter { it !== section && it.start < section.start }.maxOfOrNull { it.end } ?: 0.0
        val nextStart = sections.filter { it !== section && it.start > section.start }.minOfOrNull { it.start } ?: maxTime
        val minStart = prevEnd
        val maxStart = (nextStart - section.totalDuration).coerceAtLeast(minStart)
        return minStart..maxStart
    }

    fun getMaxDuration(section: TrackSection, maxTime: Double): Double {
        val nextStart = sections.filter { it !== section && it.start > section.start }.minOfOrNull { it.start } ?: maxTime
        val space = (nextStart - section.start).coerceAtLeast(0.0)
        return (space / section.repeats).coerceAtLeast(0.01)
    }

    fun getMaxRepeats(section: TrackSection, maxTime: Double): Int {
        val nextStart = sections.filter { it !== section && it.start > section.start }.minOfOrNull { it.start } ?: maxTime
        val space = (nextStart - section.start).coerceAtLeast(0.0)
        return if (section.duration > 0.0) (space / section.duration).toInt().coerceAtLeast(1) else 1
    }

    fun addSection(start: Double, duration: Double, repeats: Int = 1, name: String = ""): TrackSection? {
        if (!canAddSectionAt(start)) return null
        val available = getAvailableDurationAt(start, duration * repeats)
        val actualDuration = available / repeats
        if (actualDuration < 0.05) return null
        val sId = nextSectionId++
        return addSectionDirect(sId, start, actualDuration, repeats, name).also { sectionsChanged() }
    }

    open fun addSectionDirect(id: Int, start: Double, duration: Double, repeats: Int = 1, name: String = ""): TrackSection {
        val section = TrackSection(id, start, duration, repeats, name)
        insertSorted(section)
        if (id >= nextSectionId) {
            nextSectionId = id + 1
        }
        return section
    }

    /** Inserts an existing [section] in start order, e.g. to restore it on undo. Does nothing if already present. */
    fun insertSection(section: TrackSection) {
        if (sections.contains(section)) return
        insertSorted(section)
        sectionsChanged()
    }

    private fun insertSorted(section: TrackSection) {
        val insertIndex = sections.indexOfFirst { it.start > section.start }
        if (insertIndex >= 0) {
            sections.add(insertIndex, section)
        } else {
            sections.add(section)
        }
    }

    fun duplicateSection(section: TrackSection, newStart: Double): TrackSection? {
        if (!canAddSectionAt(newStart)) return null
        val available = getAvailableDurationAt(newStart, section.totalDuration)
        if (available < section.totalDuration - 0.001) return null
        val sId = nextSectionId++
        val newSec = addSectionDirect(sId, newStart, section.duration, section.repeats, section.name)
        newSec.data = when (val d = section.data) {
            is MessageSectionData -> d.copy()
            is MidiSectionData -> d.copy()
            else -> d
        }
        sectionsChanged()
        return newSec
    }

    fun removeSection(section: TrackSection) {
        if (sections.remove(section)) sectionsChanged()
    }

    fun moveSection(section: TrackSection, newStart: Double) {
        if (section.start == newStart) return
        section.start = newStart
        sectionsChanged()
    }

    fun resizeSection(section: TrackSection, newDuration: Double) {
        if (section.duration == newDuration) return
        section.duration = newDuration
        sectionsChanged()
    }

    fun setSectionRepeats(section: TrackSection, newRepeats: Int) {
        if (section.repeats == newRepeats) return
        section.repeats = newRepeats
        sectionsChanged()
    }

    /** Lets playback in progress hear an edit to this track's sections. */
    private fun sectionsChanged() {
        timeline.onTrackSectionsChanged(this)
    }

    fun clearSections() {
        sections.clear()
        nextSectionId = 0
    }

    open fun setEngine(engine: DataFlowEngine) {}

    /**
     * Called on every track when any track's mute or solo changes, because soloing one track
     * changes whether all the others are audible. Check [isAudible] for the new state.
     */
    open fun onAudibilityChanged() {}

    open fun teardown() {
        onPlaybackStop()
        onRecordStop()
    }

    open fun dispose() {
        teardown()
        inputJack?.let { timeline.deleteTrackInput(it) }
        outputJacks.forEach { timeline.deleteTrackOutput(it) }
        clearSections()
    }

    open fun onPlaybackStart(now: ScoreTime, startPosUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {}

    /**
     * Called during playback when this track's sections were added, removed, moved or resized.
     * Replaces the events scheduled for the rest of the cycle, starting at [posUnits], the current playhead.
     * Overrides must not interrupt notes, patches or audio that should keep sounding.
     */
    open fun onSectionsChanged(now: ScoreTime, posUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {
        onPlaybackStart(now, posUnits, cycleEndUnits, engine)
    }

    open fun onPlaybackStop() {}
    open fun onRecordStart(now: ScoreTime, startUnits: Double) {
        onRecordStart(startUnits)
    }
    open fun onRecordStart(startUnits: Double) {}
    open fun onRecordStop(now: ScoreTime, stopUnits: Double) {
        onRecordStop()
    }
    open fun onRecordStop() {}
    open fun onMessageReceived(jack: InputJack, msg: Message) {}
}

/**
 * Patch Module customized for PatchTrack to hook into output additions/removals and message dispatch.
 */
class IntrinsicCommandModule(initialName: String) : CommandModule() {
    override val isDeletable: Boolean get() = false
    init {
        name = initialName
    }
}

class TrackPatchModule(name: String, val track: PatchTrack) : PatchModule(name, "patch") {
    override fun canDeleteChildModule(module: Module): Boolean {
        if (module === track.startCommandModule || module === track.stopCommandModule) return false
        if (module is CommandModule && (module.name == "start" || module.name == "stop")) return false
        return super.canDeleteChildModule(module)
    }

    override fun onChildModuleAdded(module: Module) {
        super.onChildModuleAdded(module)
        track.syncOutputForChild(module)
    }

    override fun onChildModuleRemoved(module: Module) {
        track.removeOutputForChild(module)
        super.onChildModuleRemoved(module)
    }

    override fun receiveFromOutputModule(outputModule: OutputModule, msg: Message) {
        super.receiveFromOutputModule(outputModule, msg)
        track.onReceiveFromOutputModule(outputModule, msg)
    }
}

class PatchTrack(
    id: Int,
    name: String,
    timeline: TimelineModule,
    patchInstance: PatchModule? = null
) : TimelineTrack(id, TrackType.PATCH, name, timeline) {

    val patch: PatchModule = patchInstance ?: TrackPatchModule(name, this)

    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    var isPatchRunning by mutableStateOf(false)
        private set

    private val fallbackStartMod by lazy { IntrinsicCommandModule("start") }
    private val fallbackStopMod by lazy { IntrinsicCommandModule("stop") }

    val startCommandModule: CommandModule
        get() = patch.graphState.modules.filterIsInstance<CommandModule>().find { it.name == "start" }
            ?: fallbackStartMod

    val stopCommandModule: CommandModule
        get() = patch.graphState.modules.filterIsInstance<CommandModule>().find { it.name == "stop" }
            ?: fallbackStopMod

    private val _mirroredOutputs = mutableStateListOf<OutputJack>()
    override val outputJacks: List<OutputJack> get() = _mirroredOutputs
    override val outputJack: OutputJack? get() = _mirroredOutputs.firstOrNull()

    private val childOutputLinks = mutableMapOf<Module, List<OutputJack>>()

    init {
        patch.parentPatch = timeline.parentPatch as? PatchModule
        ensureDefaultCommandModules()
        // Sync any modules already in the patch
        patch.graphState.modules.toList().forEach { syncOutputForChild(it) }
    }

    fun ensureDefaultCommandModules() {
        var startMod = patch.graphState.modules.filterIsInstance<CommandModule>().find { it.name == "start" }
        if (startMod == null) {
            startMod = IntrinsicCommandModule("start")
            patch.graphState.addModule(startMod, Offset(20f, 20f))
        } else {
            startMod.isIntrinsic = true
        }

        var stopMod = patch.graphState.modules.filterIsInstance<CommandModule>().find { it.name == "stop" }
        if (stopMod == null) {
            stopMod = IntrinsicCommandModule("stop")
            patch.graphState.addModule(stopMod, Offset(20f, 80f))
        } else {
            stopMod.isIntrinsic = true
        }
    }

    internal fun syncOutputForChild(module: Module) {
        if (childOutputLinks.containsKey(module)) return
        when (module) {
            is AudioOutputModule -> {
                val base = if (_mirroredOutputs.isEmpty()) "out$id" else "out${id}_${module.name}"
                val jack = timeline.createTrackAudioOutput(base, module.passThrough.output, 0)
                childOutputLinks[module] = listOf(jack)
                _mirroredOutputs.add(jack)
            }
            is AudioOutput2Module -> {
                val base = if (_mirroredOutputs.isEmpty()) "out$id" else "out${id}_${module.name}"
                val j0 = timeline.createTrackAudioOutput("${base}0", module.twoInDualOut.output, 0)
                val j1 = timeline.createTrackAudioOutput("${base}1", module.twoInDualOut.output, 1)
                childOutputLinks[module] = listOf(j0, j1)
                _mirroredOutputs.add(j0)
                _mirroredOutputs.add(j1)
            }
            is OutputModule -> {
                val base = if (_mirroredOutputs.isEmpty()) "out$id" else "out${id}_${module.name}"
                val jack = timeline.createTrackOutput(base)
                childOutputLinks[module] = listOf(jack)
                _mirroredOutputs.add(jack)
            }
        }
    }

    internal fun removeOutputForChild(module: Module) {
        val jacks = childOutputLinks.remove(module) ?: return
        jacks.forEach { jack ->
            _mirroredOutputs.remove(jack)
            timeline.deleteTrackOutput(jack)
        }
    }

    internal fun onReceiveFromOutputModule(outputModule: OutputModule, msg: Message) {
        if (isAudible) {
            childOutputLinks[outputModule]?.firstOrNull()?.send(msg)
        }
    }

    override fun onAudibilityChanged() {
        val shouldRun = isAudible && timeline.isPlaying && timeline.getCurrentTime().let { pos ->
            sections.any { pos >= it.start && pos < it.end }
        }
        if (shouldRun) {
            // Do not restart a patch that is already running, e.g. when another track is soloed.
            if (!isPatchRunning) startPatch()
        } else {
            stopPatch()
        }
    }

    fun startPatch() {
        isPatchRunning = true
        startCommandModule.receive(Message.FloatValue(ScoreClock.now(), 1.0))
    }

    fun stopPatch() {
        if (isPatchRunning) {
            isPatchRunning = false
            stopCommandModule.receive(Message.FloatValue(ScoreClock.now(), 0.0))
        }
    }

    override fun onPlaybackStart(now: ScoreTime, startPosUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()

        val inSectionAtStart = isAudible && sections.any { section ->
            (0 until section.repeats).any { r ->
                val repStart = section.start + r * section.duration
                val repEnd = repStart + section.duration
                startPosUnits >= repStart && startPosUnits < repEnd
            }
        }
        if (!inSectionAtStart) {
            stopPatch()
        }

        sections.forEach { section ->
            for (r in 0 until section.repeats) {
                val repStart = section.start + r * section.duration
                val repEnd = repStart + section.duration
                if (repEnd <= startPosUnits || repStart >= cycleEndUnits) continue

                if (startPosUnits >= repStart && startPosUnits < repEnd) {
                    // Already running when rescheduled after a section edit, so leave it alone.
                    if (isAudible && !isPatchRunning) {
                        startPatch()
                    }
                } else if (repStart > startPosUnits && repStart < cycleEndUnits) {
                    val delayUnits = repStart - startPosUnits
                    val delaySec = timeline.unitsToSeconds(delayUnits)
                    val fireTime = now + ScoreTime((delaySec * 1_000_000_000.0).toLong())
                    val event = engine.schedule(fireTime) {
                        if (isAudible) startPatch()
                    }
                    scheduledEvents.add(event)
                }

                val continuesAtEnd = sections.any { other ->
                    (0 until other.repeats).any { rOther ->
                        val otherStart = other.start + rOther * other.duration
                        otherStart < cycleEndUnits && kotlin.math.abs(otherStart - repEnd) < 0.0001
                    }
                }

                if (!continuesAtEnd && repEnd > startPosUnits && repEnd <= cycleEndUnits) {
                    val delayUnits = repEnd - startPosUnits
                    val delaySec = timeline.unitsToSeconds(delayUnits)
                    val fireTime = now + ScoreTime((delaySec * 1_000_000_000.0).toLong())
                    val event = engine.schedule(fireTime) {
                        stopPatch()
                    }
                    scheduledEvents.add(event)
                }
            }
        }
    }

    override fun onPlaybackStop() {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        stopPatch()
    }

    override fun teardown() {
        stopPatch()
        patch.teardown()
        super.teardown()
    }

    override fun dispose() {
        _mirroredOutputs.toList().forEach { timeline.deleteTrackOutput(it) }
        _mirroredOutputs.clear()
        childOutputLinks.clear()
        super.dispose()
    }
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
    override val outputJacks: List<OutputJack> get() = listOf(outputJack)

    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private var activeRecordingSection: TrackSection? = null

    override fun addSectionDirect(id: Int, start: Double, duration: Double, repeats: Int, name: String): TrackSection {
        val section = super.addSectionDirect(id, start, duration, repeats, name)
        if (section.data == null) {
            section.data = MessageSectionData()
        }
        return section
    }

    override fun onRecordStart(now: ScoreTime, startUnits: Double) {
        val existing = sections.find { startUnits >= it.start && startUnits < it.end }
        if (existing != null) {
            activeRecordingSection = existing
            if (existing.data == null) existing.data = MessageSectionData()
        } else if (canAddSectionAt(startUnits)) {
            val avail = getAvailableDurationAt(startUnits, 4.0)
            if (avail >= 0.05) {
                activeRecordingSection = addSection(startUnits, avail)?.also {
                    it.data = MessageSectionData()
                }
            } else {
                activeRecordingSection = null
            }
        } else {
            activeRecordingSection = null
        }
    }

    override fun onRecordStop(now: ScoreTime, stopUnits: Double) {
        activeRecordingSection?.let { sec ->
            if (stopUnits > sec.start && sec.repeats == 1) {
                val maxDur = getMaxDuration(sec, timeline.maxTime)
                val newDur = (stopUnits - sec.start).coerceAtLeast(0.5).coerceAtMost(maxDur)
                if (newDur > sec.duration) {
                    sec.duration = newDur
                }
            }
        }
        activeRecordingSection = null
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === inputJack && msg is Message.FloatValue) {
            if (isInputMonitored && isAudible) {
                outputJack.send(msg)
            }
            if (isRecordArmed && timeline.isRecording) {
                recordValue(msg.value, msg.time)
            }
        }
    }

    private fun recordValue(value: Double, msgTime: ScoreTime) {
        val pos = timeline.getCurrentTime(msgTime)

        var sec = activeRecordingSection ?: sections.find { pos >= it.start && pos < it.end }
        if (sec == null && canAddSectionAt(pos)) {
            val avail = getAvailableDurationAt(pos, 4.0)
            if (avail >= 0.05) {
                sec = addSection(pos, avail)?.also { it.data = MessageSectionData() }
                activeRecordingSection = sec
            }
        }
        if (sec == null) return

        val data = sec.data as? MessageSectionData ?: MessageSectionData().also { sec.data = it }

        // If recording continues past section.end and repeats == 1, dynamically expand section duration
        if (pos >= sec.end && sec.repeats == 1) {
            val maxDur = getMaxDuration(sec, timeline.maxTime)
            val neededDur = (pos - sec.start + 0.5).coerceAtMost(maxDur)
            if (neededDur > sec.duration) {
                sec.duration = neededDur
            }
        }

        val relTime = if (sec.repeats > 1) {
            (pos - sec.start) % sec.duration
        } else {
            pos - sec.start
        }

        if (relTime >= 0.0) {
            data.addEvent(relTime, value)
        }
    }

    override fun onPlaybackStart(now: ScoreTime, startPosUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()

        sections.forEach { section ->
            val data = section.data as? MessageSectionData ?: return@forEach
            for (r in 0 until section.repeats) {
                val repStart = section.start + r * section.duration
                val repEnd = repStart + section.duration
                if (repEnd <= startPosUnits || repStart >= cycleEndUnits) continue

                for (event in data.events) {
                    // Events beyond the section duration are hidden, e.g. after the section was shortened.
                    if (event.time >= section.duration) continue
                    val eventTime = repStart + event.time
                    if (eventTime >= startPosUnits && eventTime < cycleEndUnits) {
                        val delayUnits = eventTime - startPosUnits
                        val delaySec = timeline.unitsToSeconds(delayUnits)
                        val fireTime = now + ScoreTime((delaySec * 1_000_000_000.0).toLong())
                        val valToSend = event.value
                        val sched = engine.schedule(fireTime) {
                            if (isAudible) {
                                outputJack.send(Message.FloatValue(fireTime, valToSend))
                            }
                        }
                        scheduledEvents.add(sched)
                    }
                }
            }
        }
    }

    override fun onPlaybackStop() {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
    }
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
    override val outputJacks: List<OutputJack> get() = listOf(outputJack)

    private var engine: DataFlowEngine? = null
    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private val activeSoundingNotes = mutableMapOf<Int, Double>() // tag -> pitch

    /** Identifies one playback of a note, so a reschedule can tell whether a sounding note is still wanted. */
    private data class NoteKey(val section: TrackSection, val repStart: Double, val noteId: Int)
    private val soundingNoteKeys = mutableMapOf<NoteKey, Int>() // key -> tag
    private var activeRecordingSection: TrackSection? = null

    private data class OpenRecordingNote(
        val tag: Int,
        val pitch: Double,
        val velocity: Double,
        val startUnits: Double,
        val section: TrackSection
    )
    private val openNotes = mutableMapOf<Int, OpenRecordingNote>()

    /** Tags of notes that input monitoring has sent a NOTE_ON for and not yet ended. */
    private val monitoredNoteTags = mutableSetOf<Int>()

    companion object {
        /** Above MIDI note numbers and piano roll note ids, so playback tags never collide with them. */
        private const val FIRST_PLAYBACK_TAG = 1_000_000
        private var nextPlaybackTag = FIRST_PLAYBACK_TAG

        /**
         * Returns a voice tag for one scheduled note. Shared by all MIDI tracks so that tracks
         * feeding the same synth cannot turn off each other's notes. Called from the engine thread.
         */
        internal fun allocatePlaybackTag(): Int {
            val tag = nextPlaybackTag
            nextPlaybackTag = if (tag == Int.MAX_VALUE) FIRST_PLAYBACK_TAG else tag + 1
            return tag
        }
    }

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
        sections.forEach { sec ->
            (sec.data as? MidiSectionData)?.pianoRoll?.setEngine(engine)
        }
    }

    override fun onAudibilityChanged() {
        if (!isAudible) silenceSoundingNotes()
    }

    var lastSentPitchBend: Double? = null

    fun silenceSoundingNotes(timestamp: ScoreTime = ScoreClock.now()) {
        if (activeSoundingNotes.isNotEmpty()) {
            for ((tag, pitch) in activeSoundingNotes) {
                outputJack.send(
                    Message.VoiceMessage(
                        timestamp,
                        Message.VoiceOpcode.NOTE_OFF,
                        tag = tag,
                        value = pitch,
                        extra = PianoRollModule.NOTE_AMPLITUDE
                    )
                )
            }
            activeSoundingNotes.clear()
        }
        soundingNoteKeys.clear()
        if (lastSentPitchBend != null && lastSentPitchBend != 0.0) {
            outputJack.send(
                Message.VoiceMessage(
                    timestamp,
                    Message.VoiceOpcode.PITCH_BEND,
                    tag = -1,
                    value = 0.0,
                    extra = 0.0
                )
            )
            lastSentPitchBend = 0.0
        }
    }

    override fun addSectionDirect(id: Int, start: Double, duration: Double, repeats: Int, name: String): TrackSection {
        val section = super.addSectionDirect(id, start, duration, repeats, name)
        if (section.data == null) {
            val data = MidiSectionData()
            bindSectionData(data)
            section.data = data
        } else if (section.data is MidiSectionData) {
            bindSectionData(section.data as MidiSectionData)
        }
        return section
    }

    fun bindSectionData(data: MidiSectionData) {
        val pr = data.pianoRoll
        engine?.let { pr.setEngine(it) }
        pr.tempo.setValue(timeline.getTempoBpm())
        if (data.bridgeJack == null) {
            val bridge = object : InputJack("bridge_${id}_${pr.name}", timeline) {
                override fun receive(msg: Message) {
                    if (passesMute(msg)) {
                        outputJack.send(msg)
                    }
                }
            }
            data.bridgeJack = bridge
            pr.output.connect(bridge)
        }
    }

    override fun onRecordStart(now: ScoreTime, startUnits: Double) {
        openNotes.clear()
        val existing = sections.find { startUnits >= it.start && startUnits < it.end }
        if (existing != null) {
            activeRecordingSection = existing
            if (existing.data !is MidiSectionData) {
                val data = MidiSectionData()
                bindSectionData(data)
                existing.data = data
            }
        } else if (canAddSectionAt(startUnits)) {
            val defaultDur = if (timeline is BeatLineModule) 4.0 else 2.0
            val avail = getAvailableDurationAt(startUnits, defaultDur)
            if (avail >= 0.05) {
                activeRecordingSection = addSection(startUnits, avail)
            } else {
                activeRecordingSection = null
            }
        } else {
            activeRecordingSection = null
        }
    }

    override fun onRecordStop(now: ScoreTime, stopUnits: Double) {
        if (openNotes.isNotEmpty()) {
            openNotes.values.toList().forEach { openNote ->
                closeRecordingNote(openNote, stopUnits)
            }
            openNotes.clear()
        }

        activeRecordingSection?.let { sec ->
            if (stopUnits > sec.start && sec.repeats == 1) {
                val maxDur = getMaxDuration(sec, timeline.maxTime)
                val newDur = (stopUnits - sec.start).coerceAtLeast(0.5).coerceAtMost(maxDur)
                if (newDur > sec.duration) {
                    sec.duration = newDur
                }
            }
        }
        activeRecordingSection = null
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === inputJack && msg is Message.VoiceMessage) {
            val noteOff = isNoteOff(msg)
            // A note-off always follows its monitored note-on, even if monitoring was turned off in between.
            val shouldSend = if (noteOff) {
                isInputMonitored || monitoredNoteTags.contains(msg.tag)
            } else {
                isInputMonitored && isAudible
            }
            if (shouldSend) {
                if (msg.opcode == Message.VoiceOpcode.PITCH_BEND) {
                    lastSentPitchBend = msg.value
                }
                outputJack.send(msg)
                if (noteOff) {
                    monitoredNoteTags.remove(msg.tag)
                } else if (msg.opcode == Message.VoiceOpcode.NOTE_ON) {
                    monitoredNoteTags.add(msg.tag)
                }
            }
            if (isRecordArmed && timeline.isRecording) {
                recordVoiceMessage(msg)
            }
        }
    }

    private fun getOrCreateRecordingSection(pos: Double): TrackSection? {
        var sec = activeRecordingSection ?: sections.find { pos >= it.start && pos < it.end }
        if (sec == null && canAddSectionAt(pos)) {
            val defaultDur = if (timeline is BeatLineModule) 4.0 else 2.0
            val avail = getAvailableDurationAt(pos, defaultDur)
            if (avail >= 0.05) {
                sec = addSection(pos, avail)
                activeRecordingSection = sec
            }
        }
        return sec
    }

    /** NOTE_OFF, or NOTE_ON with zero amplitude, which MIDI also uses to end a note. */
    private fun isNoteOff(msg: Message): Boolean =
        msg is Message.VoiceMessage && (msg.opcode == Message.VoiceOpcode.NOTE_OFF ||
                (msg.opcode == Message.VoiceOpcode.NOTE_ON && msg.extra <= 0.0))

    /** Mute blocks outgoing messages except note-offs, so notes that are already sounding still end. */
    private fun passesMute(msg: Message): Boolean = isAudible || isNoteOff(msg)

    private fun recordVoiceMessage(msg: Message.VoiceMessage) {
        if (isNoteOff(msg)) {
            val openNote = openNotes.remove(msg.tag)
                ?: openNotes.entries.firstOrNull { it.value.pitch == msg.value }?.let { openNotes.remove(it.key) }
            if (openNote != null) {
                val endUnits = timeline.getCurrentTime(msg.time)
                closeRecordingNote(openNote, endUnits)
            }
        } else if (msg.opcode == Message.VoiceOpcode.NOTE_ON) {
            val pos = timeline.getCurrentTime(msg.time)
            val sec = getOrCreateRecordingSection(pos) ?: return

            // If note with this tag was already open, close it first
            openNotes.remove(msg.tag)?.let { closeRecordingNote(it, pos) }

            openNotes[msg.tag] = OpenRecordingNote(
                tag = msg.tag,
                pitch = msg.value,
                velocity = msg.extra,
                startUnits = pos,
                section = sec
            )
        } else {
            // Non-note VoiceMessage (PITCH_BEND, CONTROL_CHANGE, PRESSURE, PROGRAM_CHANGE, etc.)
            val pos = timeline.getCurrentTime(msg.time)
            val sec = getOrCreateRecordingSection(pos) ?: return

            if (pos > sec.end && sec.repeats == 1) {
                val maxDur = getMaxDuration(sec, timeline.maxTime)
                val neededDur = (pos - sec.start + 0.25).coerceAtMost(maxDur)
                if (neededDur > sec.duration) {
                    sec.duration = neededDur
                }
            }

            val data = sec.data as? MidiSectionData ?: MidiSectionData().also {
                bindSectionData(it)
                sec.data = it
            }

            val relStartUnits = if (sec.repeats > 1) {
                (pos - sec.start) % sec.duration
            } else {
                pos - sec.start
            }.coerceAtLeast(0.0)

            data.addVoiceEvent(relStartUnits, msg.opcode, msg.tag, msg.value, msg.extra)
        }
    }

    private fun closeRecordingNote(openNote: OpenRecordingNote, endUnits: Double) {
        val sec = openNote.section
        val startUnits = openNote.startUnits
        val safeEndUnits = maxOf(endUnits, startUnits + 0.01)

        if (safeEndUnits > sec.end && sec.repeats == 1) {
            val maxDur = getMaxDuration(sec, timeline.maxTime)
            val neededDur = (safeEndUnits - sec.start + 0.25).coerceAtMost(maxDur)
            if (neededDur > sec.duration) {
                sec.duration = neededDur
            }
        }

        val data = sec.data as? MidiSectionData ?: MidiSectionData().also {
            bindSectionData(it)
            sec.data = it
        }

        val relStartUnits = if (sec.repeats > 1) {
            (startUnits - sec.start) % sec.duration
        } else {
            startUnits - sec.start
        }.coerceAtLeast(0.0)

        val durUnits = (safeEndUnits - startUnits).coerceAtLeast(0.01)

        val startTicks = timeline.unitsToTicks(relStartUnits)
        val durTicks = timeline.unitsToTicks(durUnits).coerceAtLeast(PianoRollTiming.GRID_TICKS / 4)

        data.addNote(startTicks, durTicks, openNote.pitch)
    }

    override fun onPlaybackStart(now: ScoreTime, startPosUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {
        silenceSoundingNotes(now)
        scheduleSections(now, startPosUnits, cycleEndUnits, engine)
    }

    override fun onSectionsChanged(now: ScoreTime, posUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {
        val keptNotes = scheduleSections(now, posUnits, cycleEndUnits, engine)
        // End notes whose section was deleted or moved away from the playhead
        soundingNoteKeys.filterKeys { it !in keptNotes }.forEach { (key, tag) ->
            val pitch = activeSoundingNotes.remove(tag) ?: return@forEach
            outputJack.send(
                Message.VoiceMessage(
                    now,
                    Message.VoiceOpcode.NOTE_OFF,
                    tag = tag,
                    value = pitch,
                    extra = PianoRollModule.NOTE_AMPLITUDE
                )
            )
            soundingNoteKeys.remove(key)
        }
    }

    /**
     * Schedules notes and voice events from [startPosUnits] to [cycleEndUnits], replacing any already scheduled.
     * A note that should be sounding at [startPosUnits] and already is keeps sounding with its tag,
     * otherwise it is chased with a new NOTE_ON. Returns the keys of the notes that kept sounding.
     */
    private fun scheduleSections(
        now: ScoreTime,
        startPosUnits: Double,
        cycleEndUnits: Double,
        engine: DataFlowEngine
    ): Set<NoteKey> {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        val keptNotes = mutableSetOf<NoteKey>()

        sections.forEach { section ->
            val data = section.data as? MidiSectionData ?: return@forEach
            val pianoRoll = data.pianoRoll
            val notes = pianoRoll.notes

            for (r in 0 until section.repeats) {
                val repStart = section.start + r * section.duration
                val repEnd = repStart + section.duration
                if (repEnd <= startPosUnits || repStart >= cycleEndUnits) continue

                for (note in notes) {
                    val pitch = pianoRoll.pitchOf(note)
                    val noteStartUnits = timeline.ticksToUnits(note.startTicks)
                    val noteDurUnits = timeline.ticksToUnits(note.durationTicks)
                    val onTimelineUnits = repStart + noteStartUnits
                    // Notes starting beyond the section duration are hidden, e.g. after the section was shortened.
                    if (onTimelineUnits >= repEnd) continue
                    val offTimelineUnits = onTimelineUnits + noteDurUnits
                    val effectiveOffUnits = minOf(offTimelineUnits, repEnd)

                    if (effectiveOffUnits <= startPosUnits || onTimelineUnits >= cycleEndUnits) continue

                    val key = NoteKey(section, repStart, note.id)
                    val isUnderPlayhead = startPosUnits >= onTimelineUnits && startPosUnits < effectiveOffUnits
                    val soundingTag = if (isUnderPlayhead) soundingNoteKeys[key] else null
                    if (soundingTag != null) keptNotes.add(key)
                    val tag = soundingTag ?: allocatePlaybackTag()

                    // NOTE_ON
                    if (onTimelineUnits >= startPosUnits && onTimelineUnits < cycleEndUnits) {
                        val delayUnits = onTimelineUnits - startPosUnits
                        val delaySec = timeline.unitsToSeconds(delayUnits)
                        val fireOnTime = now + ScoreTime((delaySec * 1_000_000_000.0).toLong())
                        val sched = engine.schedule(fireOnTime) {
                            if (isAudible) {
                                outputJack.send(
                                    Message.VoiceMessage(
                                        fireOnTime,
                                        Message.VoiceOpcode.NOTE_ON,
                                        tag = tag,
                                        value = pitch,
                                        extra = PianoRollModule.NOTE_AMPLITUDE
                                    )
                                )
                                activeSoundingNotes[tag] = pitch
                                soundingNoteKeys[key] = tag
                            }
                        }
                        scheduledEvents.add(sched)
                    } else if (isUnderPlayhead && soundingTag == null) {
                        // Chase note
                        if (isAudible) {
                            outputJack.send(
                                Message.VoiceMessage(
                                    now,
                                    Message.VoiceOpcode.NOTE_ON,
                                    tag = tag,
                                    value = pitch,
                                    extra = PianoRollModule.NOTE_AMPLITUDE
                                )
                            )
                            activeSoundingNotes[tag] = pitch
                            soundingNoteKeys[key] = tag
                        }
                    }

                    // NOTE_OFF
                    if (effectiveOffUnits > startPosUnits && effectiveOffUnits <= cycleEndUnits) {
                        val delayUnits = effectiveOffUnits - startPosUnits
                        val delaySec = timeline.unitsToSeconds(delayUnits)
                        val fireOffTime = now + ScoreTime((delaySec * 1_000_000_000.0).toLong())
                        val sched = engine.schedule(fireOffTime) {
                            outputJack.send(
                                Message.VoiceMessage(
                                    fireOffTime,
                                    Message.VoiceOpcode.NOTE_OFF,
                                    tag = tag,
                                    value = pitch,
                                    extra = PianoRollModule.NOTE_AMPLITUDE
                                )
                            )
                            activeSoundingNotes.remove(tag)
                            soundingNoteKeys.remove(key)
                        }
                        scheduledEvents.add(sched)
                    }
                }

                // Schedule non-note voice events (PITCH_BEND, CONTROL_CHANGE, PRESSURE, etc.)
                for (event in data.voiceEvents) {
                    if (event.time >= section.duration) continue
                    val eventTimelineUnits = repStart + event.time
                    if (eventTimelineUnits >= startPosUnits && eventTimelineUnits < minOf(cycleEndUnits, repEnd)) {
                        val delayUnits = eventTimelineUnits - startPosUnits
                        val delaySec = timeline.unitsToSeconds(delayUnits)
                        val fireTime = now + ScoreTime((delaySec * 1_000_000_000.0).toLong())
                        val sched = engine.schedule(fireTime) {
                            if (isAudible) {
                                if (event.opcode == Message.VoiceOpcode.PITCH_BEND) {
                                    lastSentPitchBend = event.value
                                }
                                outputJack.send(
                                    Message.VoiceMessage(
                                        fireTime,
                                        event.opcode,
                                        tag = event.tag,
                                        value = event.value,
                                        extra = event.extra
                                    )
                                )
                            }
                        }
                        scheduledEvents.add(sched)
                    }
                }

                // Chase controllers/pitch bend if playback starts mid-repeat
                if (startPosUnits > repStart && startPosUnits < repEnd && isAudible) {
                    val pastEvents = data.voiceEvents.filter { (repStart + it.time) <= startPosUnits && it.time < section.duration }
                    // Chase latest pitch bend if any
                    pastEvents.lastOrNull { it.opcode == Message.VoiceOpcode.PITCH_BEND }?.let { bendEvent ->
                        lastSentPitchBend = bendEvent.value
                        outputJack.send(
                            Message.VoiceMessage(
                                now,
                                Message.VoiceOpcode.PITCH_BEND,
                                tag = bendEvent.tag,
                                value = bendEvent.value,
                                extra = bendEvent.extra
                            )
                        )
                    }
                    // Chase latest of each CC
                    val pastCCs = pastEvents.filter { it.opcode == Message.VoiceOpcode.CONTROL_CHANGE }
                    val ccsSeen = mutableSetOf<Int>()
                    for (i in pastCCs.indices.reversed()) {
                        val ccEvent = pastCCs[i]
                        val ccNum = ccEvent.extra.toInt()
                        if (ccsSeen.add(ccNum)) {
                            outputJack.send(
                                Message.VoiceMessage(
                                    now,
                                    Message.VoiceOpcode.CONTROL_CHANGE,
                                    tag = ccEvent.tag,
                                    value = ccEvent.value,
                                    extra = ccEvent.extra
                                )
                            )
                        }
                    }
                }
            }
        }
        return keptNotes
    }

    override fun onPlaybackStop() {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        silenceSoundingNotes()
    }

    override fun teardown() {
        silenceSoundingNotes()
        super.teardown()
    }
}

class AudioTrackUnit(
    val segmentSize: Int = SegmentedFloatArray.DEFAULT_SEGMENT_SIZE,
    val poolCapacity: Int = SegmentedFloatArray.DEFAULT_POOL_CAPACITY
) : UnitFilter() {

    val amplitude = UnitInputPort(PORT_NAME_AMPLITUDE)

    @Volatile
    var isInputMonitored: Boolean = false

    @Volatile
    var isAudible: Boolean = true

    @Volatile
    var recordingArray: SegmentedFloatArray? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    var recordCursor: Int = 0
        private set

    class PlaybackVoice(
        val array: SegmentedFloatArray,
        var cursor: Int,
        var endFrame: Int,
        val key: Any? = null
    )

    /** A voice that should be playing now, for [syncVoices]. */
    class VoiceRequest(
        val array: SegmentedFloatArray,
        val startFrame: Int,
        val endFrame: Int,
        val key: Any
    )

    private val playbackVoices = mutableListOf<PlaybackVoice>()

    init {
        addPort(amplitude)
        amplitude.setup(0.0, 1.0, 2.0)
    }

    override val isStartRequired: Boolean get() = true

    fun startRecording(array: SegmentedFloatArray) {
        applyToAudioThread {
            recordingArray = array
            recordCursor = 0
            isRecording = true
        }
    }

    fun stopRecording() {
        applyToAudioThread {
            isRecording = false
            recordingArray = null
        }
    }

    fun playAudio(array: SegmentedFloatArray, startFrame: Int, countFrames: Int, key: Any? = null) {
        if (countFrames <= 0) return
        val end = minOf(array.length, startFrame + countFrames)
        if (startFrame >= end) return
        applyToAudioThread {
            playbackVoices.add(PlaybackVoice(array, startFrame, end, key))
        }
    }

    /**
     * Makes [requests] the voices that are playing. A voice already playing with the same key and array
     * keeps its position, so it does not click, and only takes the new end frame. Other voices stop.
     */
    fun syncVoices(requests: List<VoiceRequest>) {
        applyToAudioThread {
            playbackVoices.removeAll { voice ->
                requests.none { it.key == voice.key && it.array === voice.array }
            }
            for (req in requests) {
                val existing = playbackVoices.find { it.key == req.key }
                if (existing != null) {
                    existing.endFrame = minOf(req.endFrame, req.array.length)
                } else if (req.startFrame < minOf(req.endFrame, req.array.length)) {
                    playbackVoices.add(PlaybackVoice(req.array, req.startFrame, minOf(req.endFrame, req.array.length), req.key))
                }
            }
        }
    }

    fun stopAllPlayback() {
        applyToAudioThread {
            playbackVoices.clear()
        }
    }

    private fun applyToAudioThread(action: () -> Unit) {
        val engine = synthesisEngine
        if (engine != null && engine.isRunning) engine.queueCommand(action) else action()
    }

    override fun generate() {
        val outputs = output.getValues()
        val inputs = input.getValues()
        val blockSize = Synthesizer.FRAMES_PER_BLOCK
        val audible = isAudible

        if (isRecording) {
            val rec = recordingArray
            if (rec != null) {
                val shortfall = recordCursor + blockSize - rec.length
                if (shortfall > 0) {
                    rec.extend(shortfall)
                }
                var idx = recordCursor
                for (i in 0 until blockSize) {
                    rec[idx++] = inputs[i]
                }
                recordCursor = idx
            }
        }

        if (isInputMonitored && audible) {
            for (i in 0 until blockSize) {
                outputs[i] = inputs[i]
            }
        } else {
            outputs.fill(0f, 0, blockSize)
        }

        if (playbackVoices.isNotEmpty()) {
            val amps = amplitude.getValues()
            val it = playbackVoices.iterator()
            while (it.hasNext()) {
                val voice = it.next()
                val arr = voice.array
                val end = voice.endFrame
                var cur = voice.cursor
                if (audible) {
                    for (i in 0 until blockSize) {
                        if (cur < end && cur < arr.length) {
                            outputs[i] += arr[cur++] * amps[i]
                        } else {
                            break
                        }
                    }
                } else {
                    // Keep advancing while muted so playback stays in sync with the timeline.
                    cur = minOf(cur + blockSize, end, arr.length)
                }
                voice.cursor = cur
                if (cur >= end || cur >= arr.length) {
                    it.remove()
                }
            }
        }
    }
}

class AudioTrack(
    id: Int,
    name: String,
    timeline: TimelineModule,
    inJackName: String? = null,
    outJackName: String? = null
) : TimelineTrack(id, TrackType.AUDIO, name, timeline) {
    val unit = AudioTrackUnit()
    override val inputJack: AudioInputJack = timeline.createTrackAudioInput(inJackName ?: "in$id", unit.input)
    override val outputJack: AudioOutputJack = timeline.createTrackAudioOutput(outJackName ?: "out$id", unit.output)
    override val outputJacks: List<OutputJack> get() = listOf(outputJack)

    private var engine: DataFlowEngine? = null
    private val scheduledEvents = mutableListOf<ScheduledEvent>()
    private var replenishEvent: ScheduledEvent? = null
    private var pendingStopEvent: ScheduledEvent? = null
    private var pendingStopSection: TrackSection? = null
    private var pendingStopUnits: Double = 0.0
    private var activeRecordingSection: TrackSection? = null

    /** The take being recorded. It is combined with the section's audio when recording stops. */
    private var rawRecording: SegmentedFloatArray? = null
    /** Section whose audio is silenced because a take is being recorded into it. */
    private var recordingIntoSection: TrackSection? = null
    /** Where the take starts within the section's audio, in frames. */
    private var punchInFrames = 0
    /** The section's audio before recording, which the take is written over. Null if it had none. */
    private var punchInBase: SegmentedFloatArray? = null
    private var recordingIntoNewSection = true

    fun flushPendingRecordStop() {
        val sec = pendingStopSection ?: return
        val stopUnits = pendingStopUnits
        pendingStopEvent?.cancel()
        pendingStopEvent = null
        pendingStopSection = null
        finishRecording(sec, effectiveLatencyFrames, stopUnits)
    }

    /** Latency override in frames. If null, queries [AudioSettings.measuredLatencyForActiveDevices]. */
    var latencyFramesOverride: Int? = null

    val effectiveLatencyFrames: Int
        get() {
            val override = latencyFramesOverride
            if (override != null) return override.coerceAtLeast(0)
            val measured = AudioSettings.measuredLatencyForActiveDevices()
            return if (measured >= 0) measured else 0
        }

    override var isInputMonitored: Boolean
        get() = super.isInputMonitored
        set(value) {
            super.isInputMonitored = value
            unit.isInputMonitored = value
        }

    override fun onAudibilityChanged() {
        unit.isAudible = isAudible
    }

    init {
        SynthKSyn.synth.add(unit)
        if (unit.isStartRequired) {
            unit.start()
        }
        onAudibilityChanged()
        unit.isInputMonitored = isInputMonitored
    }

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun addSectionDirect(id: Int, start: Double, duration: Double, repeats: Int, name: String): TrackSection {
        val section = super.addSectionDirect(id, start, duration, repeats, name)
        if (section.data == null) {
            section.data = AudioSectionData()
        }
        return section
    }

    override fun onRecordStart(now: ScoreTime, startUnits: Double) {
        flushPendingRecordStop()

        val existing = sections.find { startUnits >= it.start && startUnits < it.end }
        val sec = if (existing != null) {
            existing
        } else if (canAddSectionAt(startUnits)) {
            val avail = getAvailableDurationAt(startUnits, 4.0)
            if (avail >= 0.05) {
                addSection(startUnits, avail)
            } else {
                null
            }
        } else {
            null
        }

        if (sec == null) {
            activeRecordingSection = null
            return
        }

        activeRecordingSection = sec
        val audioData = sec.data as? AudioSectionData ?: AudioSectionData().also { sec.data = it }
        val newRec = SegmentedFloatArray(unit.segmentSize, unit.poolCapacity).apply {
            frameRate = if (unit.frameRate > 0.0) unit.frameRate else SynthKSyn.synth.frameRate.toDouble()
        }
        newRec.replenish()
        rawRecording = newRec

        // Recording that starts inside an existing section is punched in where it starts.
        // The section keeps its audio until recording stops, then the take is written over it.
        recordingIntoNewSection = existing == null
        val offsetUnits = if (existing == null) {
            0.0
        } else if (sec.repeats > 1) {
            (startUnits - sec.start) % sec.duration
        } else {
            startUnits - sec.start
        }
        punchInFrames = (timeline.unitsToSeconds(offsetUnits) * newRec.frameRate).roundToInt().coerceAtLeast(0)
        punchInBase = audioData.recording?.takeIf { existing != null && it.length > 0 }

        if (punchInFrames == 0 && punchInBase == null) {
            // Nothing to keep, so show the take's waveform as it is recorded
            audioData.recording = newRec
            audioData.isLoadedFromFile = false
            audioData.thumbnail.clear()
        }

        unit.startRecording(newRec)
        startReplenishing()
        // Stop this section's old audio, which the take is replacing
        recordingIntoSection = sec
        timeline.onTrackSectionsChanged(this)
    }

    private fun startReplenishing() {
        if (replenishEvent != null) return
        scheduleReplenish()
    }

    private fun scheduleReplenish() {
        val e = engine ?: timeline.getEngine() ?: return
        replenishEvent = e.schedule(ScoreClock.now() + ScoreTime(20_000_000L)) {
            replenishEvent = null
            if (unit.isRecording) {
                unit.recordingArray?.replenish()
                activeRecordingSection?.let { sec ->
                    val data = sec.data as? AudioSectionData
                    data?.updateThumbnail(unit.recordCursor)

                    val pos = timeline.getCurrentTime()
                    if (pos > sec.end && sec.repeats == 1) {
                        val maxDur = getMaxDuration(sec, timeline.maxTime)
                        val neededDur = (pos - sec.start + 0.5).coerceAtMost(maxDur)
                        if (neededDur > sec.duration) {
                            sec.duration = neededDur
                        }
                    }
                }
                scheduleReplenish()
            }
        }
    }

    override fun onRecordStop(now: ScoreTime, stopUnits: Double) {
        val sec = activeRecordingSection ?: return
        activeRecordingSection = null

        val latencyFrames = effectiveLatencyFrames
        val frameRate = if (unit.frameRate > 0.0) unit.frameRate else SynthKSyn.synth.frameRate.toDouble()
        val latencyNs = if (frameRate > 0.0) ((latencyFrames / frameRate) * 1_000_000_000.0).toLong() else 0L
        val e = engine ?: timeline.getEngine()

        if (unit.isRecording && latencyNs > 0L && e != null) {
            pendingStopSection = sec
            pendingStopUnits = stopUnits
            val fireTime = now + ScoreTime(latencyNs)
            pendingStopEvent = e.schedule(fireTime) {
                pendingStopEvent = null
                pendingStopSection = null
                finishRecording(sec, latencyFrames, stopUnits)
            }
        } else {
            finishRecording(sec, latencyFrames, stopUnits)
        }
    }

    fun finishRecording(sec: TrackSection, latencyFrames: Int, stopUnits: Double = timeline.getCurrentTime()) {
        unit.stopRecording()
        replenishEvent?.cancel()
        replenishEvent = null

        val rawRec = rawRecording ?: run {
            recordingIntoSection = null
            return
        }
        val base = punchInBase
        val offset = punchInFrames
        rawRecording = null
        punchInBase = null
        punchInFrames = 0
        val data = sec.data as? AudioSectionData ?: run {
            recordingIntoSection = null
            return
        }

        // Drop the input latency from the start of the take, then write it at the punch-in point
        val usableFrames = (unit.recordCursor - latencyFrames).coerceAtLeast(0)
        val baseLength = base?.length ?: 0
        val totalFrames = maxOf(baseLength, offset + usableFrames)
        val result = SegmentedFloatArray(unit.segmentSize, unit.poolCapacity).apply {
            frameRate = rawRec.frameRate
        }
        var rem = totalFrames
        while (rem > 0) {
            val chunk = minOf(rem, result.segmentSize)
            result.extend(chunk) // Zeroed, so a punch-in past the end of the old audio is padded with silence
            rem -= chunk
        }
        if (base != null && baseLength > 0) {
            val buf = FloatArray(baseLength)
            base.read(0, buf, 0, baseLength)
            result.write(0, buf)
        }
        if (usableFrames > 0) {
            val buf = FloatArray(usableFrames)
            rawRec.read(latencyFrames, buf, 0, usableFrames)
            result.write(offset, buf)
        }
        data.recording = result
        data.isLoadedFromFile = false
        data.needsWavWrite = true
        data.savedWavPath = null
        data.thumbnail.clear()
        data.thumbnail.updateFrom(result)
        // Play the section again, now with the take in it
        recordingIntoSection = null
        timeline.onTrackSectionsChanged(this)

        if (sec.repeats == 1) {
            val frameRate = if (result.frameRate > 0.0) result.frameRate else 44100.0
            val audioDurationUnits = timeline.secondsToUnits(totalFrames / frameRate)
            val maxDur = getMaxDuration(sec, timeline.maxTime)
            sec.duration = if (recordingIntoNewSection) {
                audioDurationUnits.coerceAtMost(maxDur).coerceAtLeast(MIN_SECTION_DURATION)
            } else {
                // Punching in never shortens a section, it only grows to fit a take that ran past its end
                maxOf(sec.duration, audioDurationUnits).coerceAtMost(maxDur)
            }
        }
    }

    /** Identifies one repeat of a section, so a reschedule can keep its voice playing. */
    private data class VoiceKey(val section: TrackSection, val repStart: Double)

    override fun onPlaybackStart(now: ScoreTime, startPosUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {
        // A new cycle restarts every voice, even one whose key matches, e.g. when looping.
        unit.stopAllPlayback()
        onAudibilityChanged()
        scheduleSections(now, startPosUnits, cycleEndUnits, engine)
    }

    override fun onSectionsChanged(now: ScoreTime, posUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {
        scheduleSections(now, posUnits, cycleEndUnits, engine)
    }

    /**
     * Starts the audio that should be playing at [startPosUnits], keeping voices that already are,
     * and schedules later sections up to [cycleEndUnits], replacing anything already scheduled.
     */
    private fun scheduleSections(now: ScoreTime, startPosUnits: Double, cycleEndUnits: Double, engine: DataFlowEngine) {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        val currentVoices = mutableListOf<AudioTrackUnit.VoiceRequest>()
        val frameRate = if (unit.frameRate > 0.0) unit.frameRate else SynthKSyn.synth.frameRate.toDouble()

        sections.forEach { section ->
            // The old audio of the section being recorded into stays silent under the take
            if (section === recordingIntoSection) return@forEach
            val audioData = section.data as? AudioSectionData ?: return@forEach
            val rec = audioData.recording ?: return@forEach
            if (rec.length == 0) return@forEach

            for (r in 0 until section.repeats) {
                val repStart = section.start + r * section.duration
                val repEnd = repStart + section.duration
                if (repEnd <= startPosUnits || repStart >= cycleEndUnits) continue

                val playDurationUnits = minOf(repEnd, cycleEndUnits) - maxOf(repStart, startPosUnits)
                val playDurationSec = timeline.unitsToSeconds(playDurationUnits)
                val countFrames = (playDurationSec * frameRate).toInt()

                val key = VoiceKey(section, repStart)
                if (startPosUnits >= repStart && startPosUnits < repEnd) {
                    val offsetUnits = startPosUnits - repStart
                    val offsetSec = timeline.unitsToSeconds(offsetUnits)
                    val startFrame = (offsetSec * frameRate).toInt().coerceIn(0, rec.length)
                    currentVoices.add(AudioTrackUnit.VoiceRequest(rec, startFrame, startFrame + countFrames, key))
                } else if (repStart > startPosUnits && repStart < cycleEndUnits) {
                    val delayUnits = repStart - startPosUnits
                    val delaySec = timeline.unitsToSeconds(delayUnits)
                    val fireTime = now + ScoreTime((delaySec * 1_000_000_000.0).toLong())
                    val sched = engine.schedule(fireTime) {
                        unit.playAudio(rec, 0, countFrames, key)
                    }
                    scheduledEvents.add(sched)
                }
            }
        }
        unit.syncVoices(currentVoices)
    }

    override fun onPlaybackStop() {
        scheduledEvents.forEach { it.cancel() }
        scheduledEvents.clear()
        unit.stopAllPlayback()
    }

    override fun teardown() {
        onPlaybackStop()
        flushPendingRecordStop()
        recordingIntoSection = null
        unit.stopRecording()
        replenishEvent?.cancel()
        replenishEvent = null
        SynthKSyn.synth.remove(unit)
        super.teardown()
    }
}

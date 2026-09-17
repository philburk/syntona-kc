package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.unitgen.PassThrough
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
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
    open var isMuted by mutableStateOf(false)
    open var isSoloed by mutableStateOf(false)
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

    fun canAddSectionAt(start: Double, duration: Double = 0.05): Boolean {
        if (start < 0.0) return false
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
        return addSectionDirect(sId, start, actualDuration, repeats, name)
    }

    open fun addSectionDirect(id: Int, start: Double, duration: Double, repeats: Int = 1, name: String = ""): TrackSection {
        val section = TrackSection(id, start, duration, repeats, name)
        val insertIndex = sections.indexOfFirst { it.start > start }
        if (insertIndex >= 0) {
            sections.add(insertIndex, section)
        } else {
            sections.add(section)
        }
        if (id >= nextSectionId) {
            nextSectionId = id + 1
        }
        return section
    }

    fun duplicateSection(section: TrackSection, newStart: Double): TrackSection? {
        if (!canAddSectionAt(newStart)) return null
        val available = getAvailableDurationAt(newStart, section.totalDuration)
        if (available < section.totalDuration - 0.001) return null
        val sId = nextSectionId++
        val newSec = addSectionDirect(sId, newStart, section.duration, section.repeats, section.name)
        newSec.data = when (val d = section.data) {
            is MessageSectionData -> d.copy()
            else -> d
        }
        return newSec
    }

    fun removeSection(section: TrackSection) {
        sections.remove(section)
    }

    fun clearSections() {
        sections.clear()
        nextSectionId = 0
    }

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

    override var isMuted: Boolean
        get() = super.isMuted
        set(value) {
            val changed = super.isMuted != value
            super.isMuted = value
            if (changed) handleAudibleChanged()
        }

    override var isSoloed: Boolean
        get() = super.isSoloed
        set(value) {
            val changed = super.isSoloed != value
            super.isSoloed = value
            if (changed) {
                timeline.tracks.forEach { (it as? PatchTrack)?.handleAudibleChanged() }
            }
        }

    fun handleAudibleChanged() {
        if (timeline.isPlaying) {
            val pos = timeline.getCurrentTime()
            val inSection = sections.any { pos >= it.start && pos < it.end }
            if (isAudible && inSection) {
                startPatch()
            } else {
                stopPatch()
            }
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
                    if (isAudible) {
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
                val newDur = (stopUnits - sec.start).coerceIn(0.5, maxDur)
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
    override val outputJacks: List<OutputJack> get() = listOf(outputJack)
}

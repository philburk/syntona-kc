package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.dataflow.model.AutoDisableVoiceMode
import com.syntona.dataflow.model.AutoDisableVoiceModule
import com.syntona.dataflow.model.defaultAutoDisableVoiceMode
import com.softsynth.ksyn.unitgen.SupportsAutoDisable
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.ports.UnitDataQueuePort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.UnitGenerator
import com.softsynth.ksyn.voices.PolyphonicInstrument
import com.softsynth.ksyn.voices.VoiceDescription
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.EngineAware
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A Syntona [Module] that wraps a KSyn [UnitGenerator].
 *
 * For each port on the unit:
 * - [UnitInputPort] becomes an [com.syntona.dataflow.model.AudioInputJack]
 * - [UnitOutputPort] becomes an [com.syntona.dataflow.model.AudioOutputJack]
 * - [UnitDataQueuePort] becomes a [com.syntona.dataflow.model.SequentialDataInputJack]
 *   whose data is queued on the port
 * Other port types are ignored.
 *
 * The underlying [unit] must still be added to a KSyn synthesizer and started separately.
 */
open class UnitGeneratorModule(name: String, val unit: UnitGenerator) : Module(name), AutoDisableVoiceModule {
    override val isAllowedInPolyPatch: Boolean
        get() = unit !is com.softsynth.ksyn.unitgen.LineIn && unit !is com.softsynth.ksyn.unitgen.LineOut

    override val autoDisableUnit: SupportsAutoDisable?
        get() = unit as? SupportsAutoDisable

    // Auto for units that cannot switch a voice off, so that no preference is saved in the JSON.
    override var autoDisableVoice: AutoDisableVoiceMode = defaultAutoDisableVoiceMode(unit)

    val onJack: com.syntona.dataflow.model.CommandJack?
    val offJack: com.syntona.dataflow.model.CommandJack?
    val triggerJack: com.syntona.dataflow.model.CommandJack?

    // Data that arrives on one of these jacks is queued on the unit's matching port.
    private val dataQueuePorts = mutableMapOf<InputJack, UnitDataQueuePort>()

    init {
        SynthKSyn.synth.add(unit)
        if (unit is com.softsynth.ksyn.unitgen.EnvelopeDAHDSR) {
            onJack = addCommandInput("on")
            offJack = addCommandInput("off")
            triggerJack = null
        } else if (unit is com.softsynth.ksyn.unitgen.EnvelopeAttackDecay) {
            triggerJack = addCommandInput("trigger")
            onJack = null
            offJack = null
        } else if (unit is com.softsynth.ksyn.unitgen.UnitGate) {
            onJack = addCommandInput("on")
            offJack = addCommandInput("off")
            triggerJack = null
        } else {
            onJack = null
            offJack = null
            triggerJack = null
        }
        // Place data-queue and primary "Input" ports at the top of the module, even when a
        // subclass (such as DelayGrains) registers "Input" after its superclass ports.
        val orderedPorts = unit.getPorts().sortedBy { port ->
            when {
                port is UnitDataQueuePort -> 0
                port is UnitInputPort && port.name.equals("Input", ignoreCase = true) -> 1
                else -> 2
            }
        }
        for (port in orderedPorts) {
            val rawName = port.name
            val baseName = when {
                rawName.startsWith("input", ignoreCase = true) -> "in" + rawName.substring(5)
                rawName.startsWith("output", ignoreCase = true) -> "out" + rawName.substring(6)
                else -> rawName.replaceFirstChar { it.lowercase() }
            }
            when (port) {
                is UnitInputPort -> {
                    for (i in 0 until port.numParts) {
                        val jackName = if (port.numParts == 1) baseName else "$baseName$i"
                        addAudioInput(jackName, port, i)
                    }
                }
                is UnitOutputPort -> {
                    for (i in 0 until port.numParts) {
                        val jackName = if (port.numParts == 1) baseName else "$baseName$i"
                        addAudioOutput(jackName, port, i)
                    }
                }
                is UnitDataQueuePort -> {
                    dataQueuePorts[addSequentialDataInput(baseName)] = port
                }
                else -> {}
            }
        }
        if (unit.isStartRequired) {
            unit.start()
        }
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        val port = dataQueuePorts[jack]
        if (port != null && msg is Message.SequentialDataMessage) {
            val data = msg.data
            port.queueImmediate(data, 0, data.numFrames, SynthKSyn.synth.createTimeStamp())
        }
        super.onMessageReceived(jack, msg)
    }

    override fun execute(triggerMessage: Message) {
        if (offJack != null && offJack.isKicked) {
            (unit as? com.softsynth.ksyn.unitgen.UnitGate)?.input?.off()
        }
        if (onJack != null && onJack.isKicked) {
            (unit as? com.softsynth.ksyn.unitgen.UnitGate)?.input?.on()
        }
        if (triggerJack != null && triggerJack.isKicked) {
            (unit as? com.softsynth.ksyn.unitgen.UnitGate)?.input?.trigger()
        }
    }

    override fun teardown() {
        if (unit.isStartRequired) {
            unit.stop()
        }
        for (port in unit.getPorts()) {
            if (port is com.softsynth.ksyn.ports.UnitBlockPort) {
                for (i in 0 until port.numParts) {
                    port.disconnectAll(i)
                }
            }
        }
        SynthKSyn.synth.remove(unit)
        super.teardown()
    }
}

/**
 * A Syntona [Module] that wraps a KSyn [PolyphonicInstrument] created from a [VoiceDescription].
 *
 * Exposes:
 * - A MIDI [com.syntona.dataflow.model.VoiceInputJack] named "input"
 * - An [AudioInputJack] for each exported [UnitInputPort] on the instrument
 * - An [com.syntona.dataflow.model.AudioOutputJack] named "output" (mono) or "out0"/"out1" (stereo)
 * - Preset selection from [VoiceDescription.presetNames]
 */
class KSynInstrumentModule(
    name: String,
    val voiceDescription: VoiceDescription,
    val numVoices: Int = VoiceDescription.DEFAULT_NUM_VOICES,
    defaultPresetIndex: Int = 0
) : Module(name), EngineAware {

    override val isAllowedInPolyPatch: Boolean
        get() = false

    val instrument: PolyphonicInstrument =
        voiceDescription.createPolyphonicInstrument(SynthKSyn.synth, numVoices)

    val voiceInputJack = addVoiceInput("input", isKickable = true)

    val presetNames: List<String> = voiceDescription.presetNames.toList()

    private var _presetIndex by mutableStateOf(0)

    var presetIndex: Int
        get() = _presetIndex
        set(value) {
            val validIndex = if (presetNames.isNotEmpty()) {
                value.coerceIn(0, presetNames.lastIndex)
            } else {
                0
            }
            _presetIndex = validIndex
            instrument.usePreset(validIndex)
            for (jack in inputs) {
                if (jack is AudioInputJack) {
                    jack.notifyPortValueChanged()
                }
            }
        }

    var presetName: String
        get() = presetNames.getOrElse(_presetIndex) { "" }
        set(value) {
            val idx = presetNames.indexOfFirst { it.equals(value, ignoreCase = true) }
            if (idx >= 0) {
                presetIndex = idx
            }
        }

    private var engine: DataFlowEngine? = null
    private val pendingNoteOffs = mutableMapOf<Int, ScheduledEvent>()

    init {
        SynthKSyn.synth.add(instrument)
        for (port in instrument.getPorts()) {
            val rawName = port.name
            val baseName = rawName.replaceFirstChar { it.lowercase() }
            if (port is UnitInputPort) {
                for (i in 0 until port.numParts) {
                    val jackName = if (port.numParts == 1) baseName else "$baseName$i"
                    addAudioInput(jackName, port, i)
                }
            }
        }
        val outputPort = instrument.getOutputPort()
        if (outputPort.numParts == 1) {
            addAudioOutput("output", outputPort, 0)
        } else {
            for (i in 0 until outputPort.numParts) {
                addAudioOutput("out$i", outputPort, i)
            }
        }
        presetIndex = defaultPresetIndex
        if (instrument.isStartRequired) {
            instrument.start()
        }
    }

    override fun setEngine(engine: DataFlowEngine) {
        this.engine = engine
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === voiceInputJack && msg is Message.VoiceMessage) {
            val timeStamp = SynthKSyn.synth.createTimeStamp()
            when (msg.opcode) {
                Message.VoiceOpcode.NOTE_ON -> {
                    pendingNoteOffs.remove(msg.tag)?.cancel()
                    instrument.noteOn(msg.tag, msg.value, msg.extra, timeStamp)
                }
                Message.VoiceOpcode.NOTE_OFF -> {
                    pendingNoteOffs.remove(msg.tag)?.cancel()
                    instrument.noteOff(msg.tag, timeStamp)
                }
                Message.VoiceOpcode.NOTE -> {
                    pendingNoteOffs.remove(msg.tag)?.cancel()
                    val durSec = msg.duration.coerceAtLeast(0.0)
                    instrument.noteOn(msg.tag, msg.value, msg.extra, timeStamp)
                    val offTime = msg.time + ScoreTime.fromSeconds(durSec)
                    val e = engine
                    if (e != null) {
                        var scheduled: ScheduledEvent? = null
                        scheduled = e.schedule(offTime) {
                            if (pendingNoteOffs[msg.tag] === scheduled) {
                                pendingNoteOffs.remove(msg.tag)
                            }
                            val offStamp = SynthKSyn.synth.createTimeStamp()
                            instrument.noteOff(msg.tag, offStamp)
                        }
                        pendingNoteOffs[msg.tag] = scheduled
                    } else {
                        instrument.noteOff(msg.tag, timeStamp)
                    }
                }
                Message.VoiceOpcode.CONTROL_CHANGE -> {
                    setVoicePort(msg.tag, "control${msg.extra.toInt()}", msg.value, timeStamp)
                }
                Message.VoiceOpcode.PRESSURE -> {
                    setVoicePort(msg.tag, "pressure", msg.value, timeStamp)
                }
                Message.VoiceOpcode.PITCH_BEND -> {
                    setVoicePitchBend(msg.tag, msg.value, timeStamp)
                }
                Message.VoiceOpcode.PROGRAM_CHANGE -> {
                    if (presetNames.isNotEmpty()) {
                        presetIndex = msg.value.toInt().coerceIn(0, presetNames.lastIndex)
                    } else {
                        setVoicePort(msg.tag, "program", msg.value, timeStamp)
                    }
                }
            }
        }
        super.onMessageReceived(jack, msg)
    }

    private fun setVoicePort(tag: Int, portName: String, value: Double, timeStamp: TimeStamp) {
        if (tag == -1) {
            instrument.setPort(portName, value, timeStamp)
        } else {
            instrument.setPort(tag, portName, value, timeStamp)
        }
    }

    private fun setVoicePitchBend(tag: Int, value: Double, timeStamp: TimeStamp) {
        if (tag == -1) {
            instrument.setPitchBend(value, timeStamp)
        } else {
            instrument.setPitchBend(tag, value, timeStamp)
        }
    }

    override fun execute(triggerMessage: Message) {}

    override fun teardown() {
        pendingNoteOffs.values.forEach { it.cancel() }
        pendingNoteOffs.clear()
        if (instrument.isStartRequired) {
            instrument.stop()
        }
        val outPort = instrument.getOutputPort()
        for (i in 0 until outPort.numParts) {
            outPort.disconnectAll(i)
        }
        for (port in instrument.getPorts()) {
            if (port is com.softsynth.ksyn.ports.UnitBlockPort) {
                for (i in 0 until port.numParts) {
                    port.disconnectAll(i)
                }
            }
        }
        SynthKSyn.synth.remove(instrument)
        super.teardown()
    }
}

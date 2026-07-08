package com.syntona.dataflow.modules

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.UnitGenerator
import com.softsynth.ksyn.voices.PitchedVoice
import com.softsynth.ksyn.voices.PolyphonicInstrument
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.*
import com.syntona.dataflow.registry.ModuleRegistry

class PolyPatchModule(name: String = "polyPatch") : PatchModule(name) {
    var maxVoices: Int = 8
        private set

    var polyphonicInstrument: PolyphonicInstrument? = null
        private set

    private var voices: Array<DynamicPitchedVoice> = emptyArray()

    var isInitialized = false
        private set

    val voiceInputJack = addInput("input", isKickable = true)

    init {
        // Setup graphState lifecycle callbacks to sync edits to background voices
        graphState.onModuleAddedCallback = ::syncModuleAdded
        graphState.onModuleRemovedCallback = ::syncModuleRemoved
        graphState.onConnectionAddedCallback = ::syncConnectionAdded
        graphState.onConnectionRemovedCallback = ::syncConnectionRemoved

        // Add default AudioInputModules
        val pitchIn = AudioInputModule()
        pitchIn.name = "pitch"
        graphState.addModule(pitchIn, androidx.compose.ui.geometry.Offset(50f, 100f))

        val ampIn = AudioInputModule()
        ampIn.name = "amplitude"
        graphState.addModule(ampIn, androidx.compose.ui.geometry.Offset(50f, 180f))

        val pressIn = AudioInputModule()
        pressIn.name = "pressure"
        graphState.addModule(pressIn, androidx.compose.ui.geometry.Offset(50f, 260f))

        val pbIn = AudioInputModule()
        pbIn.name = "pitchBend"
        graphState.addModule(pbIn, androidx.compose.ui.geometry.Offset(50f, 340f))

        // Add a default AudioOutputModule named "output"
        val defaultOutput = AudioOutputModule()
        defaultOutput.name = "output"
        graphState.addModule(defaultOutput, androidx.compose.ui.geometry.Offset(400f, 250f))
    }

    private fun isSpecialVoiceInput(name: String): Boolean {
        val n = name.lowercase()
        if (n == "pitch" || n == "amplitude" || n == "pressure" || n == "pitchbend") return true
        if (n.startsWith("control") && n.length > 7) {
            val numPart = n.substring(7)
            return numPart.toIntOrNull() != null
        }
        return false
    }

    override fun onChildModuleAdded(module: Module) {
        module.parentPatch = this
        if (module is AudioOutputModule) {
            module.patch = this
            if (module.name == "output") {
                return
            }
            val proposedName = if (module.name == "aOutput") "aOut${outputs.size}" else module.name
            module.name = proposedName
            return
        }
        if (module is AudioInputModule) {
            module.patch = this
            if (isSpecialVoiceInput(module.name)) {
                return
            }
        }
        super.onChildModuleAdded(module)
    }

    fun initializeVoices(maxVoices: Int) {
        if (isInitialized) return
        this.maxVoices = maxVoices
        this.voices = Array(maxVoices) { DynamicPitchedVoice() }

        val instrument = PolyphonicInstrument(SynthKSyn.synth, this.voices.map { it as PitchedVoice }.toTypedArray())
        this.polyphonicInstrument = instrument

        SynthKSyn.synth.add(instrument)
        if (instrument.isStartRequired) {
            instrument.start()
        }

        // Expose instrument output mixed port
        addAudioOutput("output", instrument.getOutputPort(), 0)

        isInitialized = true

        // Sync existing modules and connections if they were loaded before initialization
        for (module in graphState.modules) {
            syncModuleAdded(module)
        }
        for (conn in graphState.connections) {
            syncConnectionAdded(conn.output, conn.input)
        }
    }

    fun ensureDefaultModulesExist() {
        val hasPitch = graphState.modules.any { it is AudioInputModule && it.name == "pitch" }
        if (!hasPitch) {
            val pitchIn = AudioInputModule()
            pitchIn.name = "pitch"
            graphState.addModule(pitchIn, androidx.compose.ui.geometry.Offset(50f, 100f))
        }
        val hasAmp = graphState.modules.any { it is AudioInputModule && it.name == "amplitude" }
        if (!hasAmp) {
            val ampIn = AudioInputModule()
            ampIn.name = "amplitude"
            graphState.addModule(ampIn, androidx.compose.ui.geometry.Offset(50f, 180f))
        }
        val hasPress = graphState.modules.any { it is AudioInputModule && it.name == "pressure" }
        if (!hasPress) {
            val pressIn = AudioInputModule()
            pressIn.name = "pressure"
            graphState.addModule(pressIn, androidx.compose.ui.geometry.Offset(50f, 260f))
        }
        val hasPb = graphState.modules.any { it is AudioInputModule && it.name == "pitchBend" }
        if (!hasPb) {
            val pbIn = AudioInputModule()
            pbIn.name = "pitchBend"
            graphState.addModule(pbIn, androidx.compose.ui.geometry.Offset(50f, 340f))
        }
        val hasOutput = graphState.modules.any { it is AudioOutputModule && it.name == "output" }
        if (!hasOutput) {
            val defaultOutput = AudioOutputModule()
            defaultOutput.name = "output"
            graphState.addModule(defaultOutput, androidx.compose.ui.geometry.Offset(400f, 250f))
        }
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === voiceInputJack) {
            if (msg is Message.VoiceMessage) {
                val instrument = polyphonicInstrument ?: return
                // NOTE: msg.time (ScoreTime) is not yet applied here; it measures elapsed time
                // since an app-level ScoreClock mark, while TimeStamp is the Synthesizer's own
                // transport time, and there is no shared origin to convert between the two yet.
                // Until that clock mapping exists, voice messages always fire at "now".
                val timeStamp = SynthKSyn.synth.createTimeStamp()
                when (msg.opcode) {
                    Message.VoiceOpcode.NOTE_ON -> {
                        instrument.noteOn(msg.tag, msg.value, msg.extra, timeStamp)
                    }
                    Message.VoiceOpcode.NOTE_OFF -> {
                        instrument.noteOff(msg.tag, timeStamp)
                    }
                    Message.VoiceOpcode.CONTROL_CHANGE -> {
                        setVoicePort(msg.tag, "control${msg.extra.toInt()}", msg.value, timeStamp)
                    }
                    Message.VoiceOpcode.PRESSURE -> {
                        setVoicePort(msg.tag, "pressure", msg.value, timeStamp)
                    }
                    Message.VoiceOpcode.PITCH_BEND -> {
                        setVoicePort(msg.tag, "pitchBend", msg.value, timeStamp)
                    }
                }
            }
        }
        super.onMessageReceived(jack, msg)
    }

    /**
     * Routes a controller value to the voice allocated to [tag], or — per the CONTROL_CHANGE /
     * PRESSURE / PITCH_BEND contract in DESIGN.md — broadcasts it to every voice when [tag] is
     * -1. [PolyphonicInstrument.setPort] only targets a single allocated voice, so the -1 case
     * is handled directly here via the public voice list, wrapped in the same queueCommand
     * used internally by PolyphonicInstrument to stay on the audio thread.
     */
    private fun setVoicePort(tag: Int, portName: String, value: Double, timeStamp: TimeStamp) {
        val instrument = polyphonicInstrument ?: return
        if (tag == -1) {
            SynthKSyn.synth.queueCommand {
                for (voice in instrument.voices) {
                    voice.setPort(portName, value.toFloat(), timeStamp)
                }
            }
        } else {
            instrument.setPort(tag, portName, value, timeStamp)
        }
    }

    fun syncInputJackValue(jack: AudioInputJack, value: Double) {
        if (!isInitialized) return
        val visibleModule = jack.module
        val portName = jack.unitInputPort.name
        val partIndex = jack.partIndex
        val ts = TimeStamp(SynthKSyn.synth.currentTime)
        for (voice in voices) {
            val bgUnit = voice.moduleToUnitMap[visibleModule] ?: continue
            val bgPort = bgUnit.getPortByName(portName) as? UnitInputPort ?: continue
            bgPort.set(partIndex, value.toFloat(), ts)
        }
    }

    private fun syncModuleAdded(module: Module) {
        if (!isInitialized) return
        for (voice in voices) {
            if (voice.moduleToUnitMap.containsKey(module)) continue

            val bgUnit: UnitGenerator = when (module) {
                is AudioInputModule -> {
                    val passThrough = PassThrough()
                    voice.add(passThrough)
                    voice.addPort(passThrough.input, module.name)
                    passThrough
                }
                is AudioOutputModule -> {
                    if (module.name == "output") {
                        // For the main output module, we use the voice's pre-created outputPassThrough
                        val passThrough = voice.outputPassThrough
                        voice.addPort(passThrough.output, module.name)
                        passThrough
                    } else {
                        // For auxiliary outputs, we create a new PassThrough for this module in the voice
                        val passThrough = PassThrough()
                        voice.add(passThrough)
                        voice.addPort(passThrough.output, module.name)
                        passThrough
                    }
                }
                is UnitGeneratorModule -> {
                    val factory = ModuleRegistry.ksynFactories[module.type]
                    if (factory != null) {
                        val newUnit = factory()
                        voice.add(newUnit)
                        newUnit
                    } else {
                        continue
                    }
                }
                is SamplerModule -> {
                    val factory = ModuleRegistry.ksynFactories[module.type]
                    if (factory != null) {
                        val newUnit = factory()
                        voice.add(newUnit)
                        newUnit
                    } else {
                        continue
                    }
                }
                else -> continue
            }
            voice.moduleToUnitMap[module] = bgUnit
        }

        // Export input ports on PolyphonicInstrument if AudioInputModule was added
        if (module is AudioInputModule) {
            if (!isSpecialVoiceInput(module.name)) {
                polyphonicInstrument?.exportNamedInputPort(module.name)
            }
        }

        if (module is AudioOutputModule && module.name != "output") {
            syncAuxOutputAdded(module)
        }

        // Connect pitchBend to pitch if both exist in voices
        for (voice in voices) {
            wirePitchBendIfBothExist(voice)
        }
    }

    private fun wirePitchBendIfBothExist(voice: DynamicPitchedVoice) {
        val pitchModule = graphState.modules.find { it is AudioInputModule && it.name == "pitch" }
        val pitchBendModule = graphState.modules.find { it is AudioInputModule && it.name == "pitchBend" }
        if (pitchModule != null && pitchBendModule != null) {
            val pitchUnit = voice.moduleToUnitMap[pitchModule] as? PassThrough
            val pitchBendUnit = voice.moduleToUnitMap[pitchBendModule] as? PassThrough
            if (pitchUnit != null && pitchBendUnit != null) {
                pitchBendUnit.output.connect(pitchUnit.input)
                pitchUnit.input.isValueAdded = true
            }
        }
    }

    private val auxMixers = mutableMapOf<String, PassThrough>()

    private fun syncAuxOutputAdded(module: AudioOutputModule) {
        val instrument = polyphonicInstrument ?: return
        val portName = module.name
        if (auxMixers.containsKey(portName)) return

        // 1. Create a PassThrough mixer unit on PolyphonicInstrument
        val mixer = PassThrough()
        instrument.add(mixer)
        instrument.addPort(mixer.output, portName)
        auxMixers[portName] = mixer

        // 2. Connect each voice's auxiliary output to the mixer
        for (voice in voices) {
            val bgUnit = voice.moduleToUnitMap[module] as? PassThrough ?: continue
            bgUnit.output.connect(mixer.input)
        }

        // 3. Expose the mixed port as a parent audio output jack
        if (outputs.none { it.name == portName }) {
            val jack = addAudioOutput(portName, mixer.output, 0)
            audioOutputLinks[jack] = module
            module.patchJack = jack
        }
    }

    private fun syncAuxOutputRemoved(module: AudioOutputModule) {
        val instrument = polyphonicInstrument ?: return
        val portName = module.name
        val mixer = auxMixers.remove(portName) ?: return

        // Disconnect voices and remove mixer from instrument
        for (voice in voices) {
            val bgUnit = voice.moduleToUnitMap[module] as? PassThrough ?: continue
            bgUnit.output.disconnect(mixer.input)
        }
        instrument.remove(mixer)
        instrument.removePortAlias(portName)

        // Remove jack from parent
        val jack = outputs.find { it.name == portName } as? AudioOutputJack
        if (jack != null) {
            removeOutput(jack)
            audioOutputLinks.remove(jack)
        }
    }

    private fun syncModuleRemoved(module: Module) {
        if (!isInitialized) return
        if (module is AudioOutputModule && module.name != "output") {
            syncAuxOutputRemoved(module)
        }
        for (voice in voices) {
            val bgUnit = voice.moduleToUnitMap.remove(module) ?: continue
            if (module is AudioInputModule) {
                val port = voice.getPortByName(module.name)
                if (port != null) {
                    voice.removePort(port)
                    voice.removePortAlias(module.name)
                }
                // Remove port from PolyphonicInstrument
                val instPort = polyphonicInstrument?.getPortByName(module.name)
                if (instPort != null) {
                    polyphonicInstrument?.removePort(instPort)
                    polyphonicInstrument?.removePortAlias(module.name)
                }
                voice.remove(bgUnit)
            } else if (module is AudioOutputModule) {
                val port = voice.getPortByName(module.name)
                if (port != null) {
                    voice.removePort(port)
                    voice.removePortAlias(module.name)
                }
                if (module.name != "output") {
                    voice.remove(bgUnit)
                }
            } else {
                voice.remove(bgUnit)
            }
        }
    }

    private fun syncConnectionAdded(outputJack: OutputJack, inputJack: InputJack) {
        if (!isInitialized) return
        if (outputJack !is AudioOutputJack || inputJack !is AudioInputJack) return

        val outMod = outputJack.module
        val inMod = inputJack.module
        val outPortName = outputJack.unitOutputPort.name
        val inPortName = inputJack.unitInputPort.name

        for (voice in voices) {
            val bgUnitA = voice.moduleToUnitMap[outMod] ?: continue
            val bgUnitB = voice.moduleToUnitMap[inMod] ?: continue
            val portA = bgUnitA.getPortByName(outPortName) as? UnitOutputPort ?: continue
            val portB = bgUnitB.getPortByName(inPortName) as? UnitInputPort ?: continue
            portA.connect(outputJack.partIndex, portB, inputJack.partIndex)
        }
    }

    private fun syncConnectionRemoved(outputJack: OutputJack, inputJack: InputJack) {
        if (!isInitialized) return
        if (outputJack !is AudioOutputJack || inputJack !is AudioInputJack) return

        val outMod = outputJack.module
        val inMod = inputJack.module
        val outPortName = outputJack.unitOutputPort.name
        val inPortName = inputJack.unitInputPort.name

        for (voice in voices) {
            val bgUnitA = voice.moduleToUnitMap[outMod] ?: continue
            val bgUnitB = voice.moduleToUnitMap[inMod] ?: continue
            val portA = bgUnitA.getPortByName(outPortName) as? UnitOutputPort ?: continue
            val portB = bgUnitB.getPortByName(inPortName) as? UnitInputPort ?: continue
            portA.disconnect(outputJack.partIndex, portB, inputJack.partIndex)
        }
    }

    fun onAudioInputNameChanged(module: AudioInputModule, newName: String) {
        if (!isInitialized) return
        for (voice in voices) {
            val bgUnit = voice.moduleToUnitMap[module] as? PassThrough ?: continue
            val oldName = voice.getPorts().find { it === bgUnit.input }?.name ?: continue
            if (oldName.lowercase() == newName.lowercase()) continue

            // Remove old alias/port from voice
            voice.removePort(bgUnit.input)
            voice.removePortAlias(oldName)

            // Add new alias/port to voice
            voice.addPort(bgUnit.input, newName)

            // Sync on PolyphonicInstrument
                        val oldInstPort = polyphonicInstrument?.getPortByName(oldName)
            if (oldInstPort != null) {
                polyphonicInstrument?.removePort(oldInstPort)
                polyphonicInstrument?.removePortAlias(oldName)
            }
            if (!isSpecialVoiceInput(newName)) {
                polyphonicInstrument?.exportNamedInputPort(newName)
            }
        }
        for (voice in voices) {
            wirePitchBendIfBothExist(voice)
        }
    }

    fun onAudioOutputNameChanged(module: AudioOutputModule, newName: String) {
        if (!isInitialized) return
        val voice = voices.firstOrNull() ?: return
        val bgUnit = voice.moduleToUnitMap[module] as? PassThrough ?: return
        val oldName = voice.getPorts().find { it === bgUnit.output }?.name ?: return
        if (oldName == newName) return

        // Update voice port aliases
        for (v in voices) {
            val unit = v.moduleToUnitMap[module] as? PassThrough ?: continue
            val oldPortName = v.getPorts().find { it === unit.output }?.name ?: continue
            v.removePort(unit.output)
            v.removePortAlias(oldPortName)
            v.addPort(unit.output, newName)
        }

        // Update PolyphonicInstrument mixer port alias
        val instrument = polyphonicInstrument ?: return
        val mixer = auxMixers.remove(oldName)
        if (mixer != null) {
            instrument.removePortAlias(oldName)
            instrument.addPort(mixer.output, newName)
            auxMixers[newName] = mixer
        }

        // Update parent jack name
        module.patchJack?.name = newName
    }

    override fun teardown() {
        val instrument = polyphonicInstrument
        if (instrument != null) {
            if (instrument.isStartRequired) {
                instrument.stop()
            }
            SynthKSyn.synth.remove(instrument)
        }
        for (voice in voices) {
            voice.teardown()
        }
        super.teardown()
    }
}

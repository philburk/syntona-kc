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

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === voiceInputJack) {
            if (msg is Message.VoiceMessage) {
                val instrument = polyphonicInstrument ?: return
                val timeStamp = SynthKSyn.synth.createTimeStamp() // TODO // TimeStamp(msg.time.nanoseconds / 1_000_000_000.0)
                when (msg.opcode) {
                    Message.VoiceOpcode.NOTE_ON -> {
                        instrument.noteOn(msg.tag, msg.value, msg.extra, timeStamp)
                    }
                    Message.VoiceOpcode.NOTE_OFF -> {
                        instrument.noteOff(msg.tag, timeStamp)
                    }
                    Message.VoiceOpcode.CONTROL_CHANGE -> {
                        instrument.setPort(msg.tag, "Ctrl${msg.extra.toInt()}", msg.value, timeStamp)
                    }
                    Message.VoiceOpcode.PRESSURE -> {
                        instrument.setPort(msg.tag, "Pressure", msg.value, timeStamp)
                    }
                    Message.VoiceOpcode.PITCH_BEND -> {
                        instrument.setPort(msg.tag, "PitchBend", msg.value, timeStamp)
                    }
                }
            }
        }
        super.onMessageReceived(jack, msg)
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
                    // For the aOutput module, we use the voice's pre-created outputPassThrough
                    val passThrough = voice.outputPassThrough
                    voice.addPort(passThrough.output, module.name)
                    passThrough
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
            if (module.name.lowercase() != "pitch" && module.name.lowercase() != "amplitude") {
                polyphonicInstrument?.exportNamedInputPort(module.name)
            }
        }
    }

    private fun syncModuleRemoved(module: Module) {
        if (!isInitialized) return
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
            } else if (module is AudioOutputModule) {
                val port = voice.getPortByName(module.name)
                if (port != null) {
                    voice.removePort(port)
                    voice.removePortAlias(module.name)
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
            if (newName.lowercase() != "pitch" && newName.lowercase() != "amplitude") {
                polyphonicInstrument?.exportNamedInputPort(newName)
            }
        }
    }

    fun onAudioOutputNameChanged(module: AudioOutputModule, newName: String) {
        if (!isInitialized) return
        for (voice in voices) {
            val bgUnit = voice.moduleToUnitMap[module] as? PassThrough ?: continue
            val oldName = voice.getPorts().find { it === bgUnit.output }?.name ?: continue
            if (oldName.lowercase() == newName.lowercase()) continue

            // Remove old alias/port from voice
            voice.removePort(bgUnit.output)
            voice.removePortAlias(oldName)

            // Add new alias/port to voice
            voice.addPort(bgUnit.output, newName)
        }
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

package com.syntona.dataflow.modules

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.UnitGenerator
import com.softsynth.ksyn.voices.PitchedVoice
import com.softsynth.ksyn.voices.PolyphonicInstrument
import com.syntona.core.audio.SynthKSyn
import com.syntona.export.SourceExporter
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.model.*
import com.syntona.dataflow.registry.ModuleRegistry

class PolyPatchModule(name: String = "polyPatch") : PatchModule(name, "polyPatch") {
    override val isAllowedInPolyPatch: Boolean
        get() = false

    var maxVoices: Int = 8
        private set

    var isStereo: Boolean = false

    var polyphonicInstrument: PolyphonicInstrument? = null
        private set

    var voices: Array<PolyPatchVoice> = emptyArray()
        private set

    var isInitialized = false
        private set

    val voiceInputJack = addVoiceInput("input", isKickable = true)

    internal val auxMixers = mutableMapOf<String, PassThrough>()

    init {
        // Setup graphState lifecycle callbacks to sync edits to background voices
        graphState.onModuleAddedCallback = ::syncModuleAdded
        graphState.onModuleRemovedCallback = ::syncModuleRemoved
        graphState.onConnectionAddedCallback = ::syncConnectionAdded
        graphState.onConnectionRemovedCallback = ::syncConnectionRemoved

        // 1. "on" (event kick for triggering envelopes, sample queues, sequencers)
        val onIn = InputModule().apply { isIntrinsic = true }
        onIn.name = "on"
        graphState.addModule(onIn, androidx.compose.ui.geometry.Offset(50f, 60f))

        // 2. "off" (event kick for releasing envelopes, sequencers)
        val offIn = InputModule().apply { isIntrinsic = true }
        offIn.name = "off"
        graphState.addModule(offIn, androidx.compose.ui.geometry.Offset(50f, 130f))

        // 3. "pitch" (event input)
        val pitchIn = InputModule().apply { isIntrinsic = true }
        pitchIn.name = "pitch"
        graphState.addModule(pitchIn, androidx.compose.ui.geometry.Offset(50f, 200f))

        // 4. "amplitude" (event input)
        val ampIn = InputModule().apply { isIntrinsic = true }
        ampIn.name = "amplitude"
        graphState.addModule(ampIn, androidx.compose.ui.geometry.Offset(50f, 270f))

        // 5. "pressure" (event input)
        val pressIn = InputModule().apply { isIntrinsic = true }
        pressIn.name = "pressure"
        graphState.addModule(pressIn, androidx.compose.ui.geometry.Offset(50f, 340f))

        // 6. "output" (audio output)
        val defaultOutput = AudioOutputModule().apply { isIntrinsic = true }
        defaultOutput.name = "output"
        graphState.addModule(defaultOutput, androidx.compose.ui.geometry.Offset(450f, 200f))
    }

    override fun setEngine(engine: DataFlowEngine) {
        super.setEngine(engine)
        for (voice in voices) {
            voice.setEngine(engine)
        }
    }

    override fun isReservedInputName(name: String): Boolean = isSpecialVoiceInput(name)

    private fun isSpecialVoiceInput(name: String): Boolean {
        val n = name.lowercase()
        if (n == "pitch" || n == "amplitude" || n == "pressure" || n == "pitchbend" || n == "gate" || n == "on" || n == "off") return true
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
            val proposedName = if (module.name == "output~" || module.name == "aOutput") "aOut${outputs.size}" else module.name
            module.name = proposedName
            return
        }
        if (module is AudioOutput2Module) {
            module.patch = this
            if (module.name == "output" || module.name == "output2~") {
                return
            }
            val proposedName = "aOut${outputs.size}"
            module.name = proposedName
            return
        }
        if (module is AudioInputModule) {
            module.patch = this
            if (isSpecialVoiceInput(module.name)) {
                return
            }
        }
        if (module is InputModule) {
            module.patch = this
            if (isSpecialVoiceInput(module.name)) {
                return
            }
        }
        if (module is CommandModule) {
            module.patch = this
            if (isSpecialVoiceInput(module.name)) {
                return
            }
        }
        super.onChildModuleAdded(module)
    }

    fun initializeVoices(maxVoices: Int, isStereo: Boolean = this.isStereo) {
        if (isInitialized) return
        this.isStereo = isStereo
        this.maxVoices = maxVoices
        this.voices = Array(maxVoices) { i -> PolyPatchVoice(i, this) }

        val currentEngine = graphState.engine
        if (currentEngine != null) {
            for (voice in voices) {
                voice.setEngine(currentEngine)
            }
        }

        val instrument = PolyphonicInstrument(SynthKSyn.synth, this.voices.map { it as PitchedVoice }.toTypedArray())
        this.polyphonicInstrument = instrument

        SynthKSyn.synth.add(instrument)
        if (instrument.isStartRequired) {
            instrument.start()
        }

        if (isStereo) {
            // Replace initial mono output module with stereo output module
            val existingMonoOutput = graphState.modules.find { it is AudioOutputModule && it.name == "output" }
            val existingStereoOutput = graphState.modules.find { it is AudioOutput2Module }
            if (existingStereoOutput == null) {
                val pos = existingMonoOutput?.let { graphState.modulePositions[it] }
                    ?: androidx.compose.ui.geometry.Offset(450f, 200f)
                if (existingMonoOutput != null) {
                    graphState.removeModulesDirectly(listOf(existingMonoOutput))
                }
                val defaultOutput = AudioOutput2Module().apply { isIntrinsic = true }
                defaultOutput.name = "output"
                graphState.addModule(defaultOutput, pos)
            } else {
                existingStereoOutput.isIntrinsic = true
            }

            // Expose stereo output jacks "out0" and "out1" on the outside of the polyPatch
            val mixer0 = getOrCreateAuxMixer("out0")
            val mixer1 = getOrCreateAuxMixer("out1")
            if (outputs.none { it.name == "out0" }) {
                addAudioOutput("out0", mixer0.output, 0)
            }
            if (outputs.none { it.name == "out1" }) {
                addAudioOutput("out1", mixer1.output, 0)
            }
        } else {
            // Expose instrument output mixed port
            if (outputs.none { it.name == "output" }) {
                addAudioOutput("output", instrument.getOutputPort(), 0)
            }
        }

        isInitialized = true

        // Sync existing modules and connections if they were loaded before initialization
        for (module in graphState.modules) {
            syncModuleAdded(module)
        }
        for (conn in graphState.connections) {
            syncConnectionAdded(conn.output, conn.input)
        }
        updateAutoDisableBindings()
    }

    fun ensureDefaultModulesExist() {
        val onIn = graphState.modules.find { it is InputModule && it.name == "on" }
        if (onIn == null) {
            val newOn = InputModule().apply { isIntrinsic = true }
            newOn.name = "on"
            graphState.addModule(newOn, androidx.compose.ui.geometry.Offset(50f, 60f))
        } else {
            onIn.isIntrinsic = true
        }

        val offIn = graphState.modules.find { it is InputModule && it.name == "off" }
        if (offIn == null) {
            val newOff = InputModule().apply { isIntrinsic = true }
            newOff.name = "off"
            graphState.addModule(newOff, androidx.compose.ui.geometry.Offset(50f, 130f))
        } else {
            offIn.isIntrinsic = true
        }

        val pitchIn = graphState.modules.find { (it is InputModule || it is AudioInputModule) && it.name == "pitch" }
        if (pitchIn == null) {
            val newPitch = InputModule().apply { isIntrinsic = true }
            newPitch.name = "pitch"
            graphState.addModule(newPitch, androidx.compose.ui.geometry.Offset(50f, 200f))
        } else {
            pitchIn.isIntrinsic = true
        }

        val ampIn = graphState.modules.find { (it is InputModule || it is AudioInputModule) && it.name == "amplitude" }
        if (ampIn == null) {
            val newAmp = InputModule().apply { isIntrinsic = true }
            newAmp.name = "amplitude"
            graphState.addModule(newAmp, androidx.compose.ui.geometry.Offset(50f, 270f))
        } else {
            ampIn.isIntrinsic = true
        }

        val pressIn = graphState.modules.find { (it is InputModule || it is AudioInputModule) && it.name == "pressure" }
        if (pressIn == null) {
            val newPress = InputModule().apply { isIntrinsic = true }
            newPress.name = "pressure"
            graphState.addModule(newPress, androidx.compose.ui.geometry.Offset(50f, 340f))
        } else {
            pressIn.isIntrinsic = true
        }

        if (isStereo) {
            val output2 = graphState.modules.find { it is AudioOutput2Module && (it.name == "output" || it.name == "output2~") }
            if (output2 == null) {
                val defaultOutput = AudioOutput2Module().apply { isIntrinsic = true }
                defaultOutput.name = "output"
                graphState.addModule(defaultOutput, androidx.compose.ui.geometry.Offset(450f, 200f))
            } else {
                output2.isIntrinsic = true
            }
        } else {
            val output = graphState.modules.find { it is AudioOutputModule && it.name == "output" }
            if (output == null) {
                val defaultOutput = AudioOutputModule().apply { isIntrinsic = true }
                defaultOutput.name = "output"
                graphState.addModule(defaultOutput, androidx.compose.ui.geometry.Offset(450f, 200f))
            } else {
                output.isIntrinsic = true
            }
        }
    }

    /**
     * Checks if [module] is intrinsic (built-in) to this [PolyPatchModule].
     */
    fun isModuleIntrinsic(module: Module): Boolean {
        if (module.isIntrinsic) return true
        if (module is AudioOutputModule && module.name == "output") return true
        if (module is AudioOutput2Module && (module.name == "output" || module.name == "output2~")) return true
        if ((module is InputModule || module is AudioInputModule) &&
            (module.name.equals("on", ignoreCase = true) ||
             module.name.equals("off", ignoreCase = true) ||
             module.name.equals("pitch", ignoreCase = true) ||
             module.name.equals("amplitude", ignoreCase = true) ||
             module.name.equals("pressure", ignoreCase = true) ||
             module.name.equals("in", ignoreCase = true) ||
             module.name.equals("input", ignoreCase = true))) {
            return true
        }
        return false
    }

    override fun canDeleteChildModule(module: Module): Boolean {
        if (!super.canDeleteChildModule(module)) return false
        if (isModuleIntrinsic(module)) return false
        return true
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        if (jack === voiceInputJack) {
            if (msg is Message.VoiceMessage) {
                val instrument = polyphonicInstrument ?: return
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
                        setVoicePitchBend(msg.tag, msg.value, timeStamp)
                    }
                    Message.VoiceOpcode.PROGRAM_CHANGE -> {
                        setVoicePort(msg.tag, "program", msg.value, timeStamp)
                    }
                }
            }
        } else {
            // Forward external input messages to the corresponding InputModule inside all voice patches
            val inputMod = inputLinks[jack]
            if (inputMod != null) {
                inputMod.receive(msg)
                for (voice in voices) {
                    val voiceInMod = voice.templateToVoiceMap[inputMod] as? InputModule
                    voiceInMod?.receive(msg)
                }
            }
            val cmdMod = commandLinks[jack]
            if (cmdMod != null) {
                cmdMod.receive(msg)
                for (voice in voices) {
                    val voiceCmdMod = voice.templateToVoiceMap[cmdMod] as? CommandModule
                    voiceCmdMod?.receive(msg)
                }
            }
        }
        super.onMessageReceived(jack, msg)
    }

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

    private fun setVoicePitchBend(tag: Int, value: Double, timeStamp: TimeStamp) {
        val instrument = polyphonicInstrument ?: return
        if (tag == -1) {
            SynthKSyn.synth.queueCommand {
                for (voice in voices) {
                    voice.setPitchBend(value, timeStamp)
                }
            }
        } else {
            instrument.setPitchBend(tag, value, timeStamp)
        }
    }

    /**
     * The KSyn units a voice module runs. They must live in the voice's circuit, which is what
     * the instrument generates; a unit left outside it never runs, so its output stays silent.
     * Listed in signal order, since a circuit runs its units in the order they were added.
     */
    private fun voiceUnitsOf(module: Module): List<UnitGenerator> = when (module) {
        is UnitGeneratorModule -> listOf(module.unit)
        is EnvSegModule -> listOf(module.unit)
        // The rate multiplier feeds the reader's rate port, so it runs first.
        is SamplePlayerModule -> listOf(module.rateMultiplier, module.unit)
        is SampleReaderModule -> listOf(module.rateMultiplier, module.unit)
        is SampleWriterModule -> listOf(module.unit)
        is AudioOutputModule -> listOf(module.passThrough)
        is AudioOutput2Module -> listOf(module.twoInDualOut)
        is AudioInputModule -> listOf(module.passThrough)
        is GrabAudioModule -> listOf(module.unit)
        else -> emptyList()
    }

    fun clearVoices() {
        for (voice in voices) {
            voice.templateToVoiceMap.clear()
            voice.voiceToTemplateMap.clear()
            for (m in voice.graphState.modules) {
                voiceUnitsOf(m).forEach { voice.removeVoiceUnit(it) }
            }
            voice.graphState.removeModulesDirectly(voice.graphState.modules.toList())
            voice.graphState.connections.clear()
        }
        val toRemove = auxMixers.filterKeys { it != "out0" && it != "out1" }
        for ((name, mixer) in toRemove) {
            if (mixer.isStartRequired) mixer.stop()
            SynthKSyn.synth.remove(mixer)
            auxMixers.remove(name)
        }
        auxMixers["out0"]?.input?.disconnectAll()
        auxMixers["out1"]?.input?.disconnectAll()
    }

    fun syncInputJackValue(jack: InputJack, value: Double) {
        if (!isInitialized) return
        val visibleModule = jack.module
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[visibleModule] ?: continue
            val voiceJack = findMatchingInputJack(voiceMod, jack) ?: continue
            voiceJack.setValue(value)
        }
    }

    fun syncInputJackValueAdded(jack: AudioInputJack, value: Boolean) {
        if (!isInitialized) return
        val visibleModule = jack.module
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[visibleModule] ?: continue
            val voiceJack = findMatchingInputJack(voiceMod, jack) as? AudioInputJack ?: continue
            voiceJack.isValueAdded = value
        }
    }

    fun syncInputJackMin(jack: InputJack, value: Double) {
        if (!isInitialized) return
        val visibleModule = jack.module
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[visibleModule] ?: continue
            val voiceJack = findMatchingInputJack(voiceMod, jack) ?: continue
            voiceJack.min = value
        }
    }

    fun syncInputJackMax(jack: InputJack, value: Double) {
        if (!isInitialized) return
        val visibleModule = jack.module
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[visibleModule] ?: continue
            val voiceJack = findMatchingInputJack(voiceMod, jack) ?: continue
            voiceJack.max = value
        }
    }

    fun syncInputJackKickable(jack: InputJack, value: Boolean) {
        if (!isInitialized) return
        val visibleModule = jack.module
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[visibleModule] ?: continue
            val voiceJack = findMatchingInputJack(voiceMod, jack) ?: continue
            voiceJack.isKickable = value
        }
    }

    fun syncInputJackInteger(jack: InputJack, value: Boolean) {
        if (!isInitialized) return
        val visibleModule = jack.module
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[visibleModule] ?: continue
            val voiceJack = findMatchingInputJack(voiceMod, jack) ?: continue
            voiceJack.isInteger = value
        }
    }

    fun syncInputJackExponential(jack: InputJack, value: Boolean) {
        if (!isInitialized) return
        val visibleModule = jack.module
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[visibleModule] ?: continue
            val voiceJack = findMatchingInputJack(voiceMod, jack) ?: continue
            voiceJack.isExponential = value
        }
    }

    private fun syncModuleAdded(module: Module) {
        if (!isInitialized) return
        for (voice in voices) {
            if (voice.templateToVoiceMap.containsKey(module)) continue

            val voiceModule = createVoiceModule(module)
            voice.templateToVoiceMap[module] = voiceModule
            voice.voiceToTemplateMap[voiceModule] = module

            voice.graphState.addModuleDirectly(voiceModule, androidx.compose.ui.geometry.Offset.Zero)

            if (module is AudioOutputModule) {
                val voiceOutMod = voiceModule as AudioOutputModule
                if (module.name == "output") {
                    voiceOutMod.passThrough.output.connect(voice.outputPassThrough.input)
                } else {
                    val auxMixer = getOrCreateAuxMixer(module.name)
                    voiceOutMod.passThrough.output.connect(auxMixer.input)
                }
            }

            if (module is AudioOutput2Module) {
                val voiceOutMod = voiceModule as AudioOutput2Module
                if (module.name == "output" || module.name == "output2~") {
                    val mixer0 = getOrCreateAuxMixer("out0")
                    val mixer1 = getOrCreateAuxMixer("out1")
                    voiceOutMod.twoInDualOut.output.connect(0, mixer0.input, 0)
                    voiceOutMod.twoInDualOut.output.connect(1, mixer1.input, 0)
                } else {
                    val mixer0 = getOrCreateAuxMixer("${module.name}0")
                    val mixer1 = getOrCreateAuxMixer("${module.name}1")
                    voiceOutMod.twoInDualOut.output.connect(0, mixer0.input, 0)
                    voiceOutMod.twoInDualOut.output.connect(1, mixer1.input, 0)
                }
            }

            if (module is AudioInputModule) {
                if (!isSpecialVoiceInput(module.name)) {
                    val voiceInMod = voiceModule as AudioInputModule
                    module.passThrough.output.connect(voiceInMod.passThrough.input)
                }
            }

            // Register voice units in voice.voiceCircuit
            voiceUnitsOf(voiceModule).forEach { voice.addVoiceUnit(it) }
        }

        if (module is AudioOutputModule && module.name != "output") {
            syncAuxOutputAdded(module)
        }
        if (module is AudioOutput2Module && module.name != "output" && module.name != "output2~") {
            syncAuxOutput2Added(module)
        }
        updateAutoDisableBindings()
    }

    private fun getOrCreateAuxMixer(portName: String): PassThrough {
        return auxMixers.getOrPut(portName) {
            val mixer = PassThrough()
            SynthKSyn.synth.add(mixer)
            if (mixer.isStartRequired) mixer.start()
            mixer
        }
    }

    private fun syncAuxOutputAdded(module: AudioOutputModule) {
        val portName = module.name
        val mixer = getOrCreateAuxMixer(portName)

        // Connect each voice's auxiliary output to the mixer
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[module] as? AudioOutputModule ?: continue
            voiceMod.passThrough.output.connect(mixer.input)
        }

        // Expose the mixed port as a parent audio output jack
        if (outputs.none { it.name == portName }) {
            val jack = addAudioOutput(portName, mixer.output, 0)
            audioOutputLinks[jack] = module
            module.patchJack = jack
        }
    }

    private fun syncAuxOutputRemoved(module: AudioOutputModule) {
        val portName = module.name
        val mixer = auxMixers.remove(portName) ?: return

        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[module] as? AudioOutputModule ?: continue
            voiceMod.passThrough.output.disconnect(mixer.input)
        }
        if (mixer.isStartRequired) mixer.stop()
        SynthKSyn.synth.remove(mixer)

        val jack = outputs.find { it.name == portName } as? AudioOutputJack
        if (jack != null) {
            removeOutput(jack)
            audioOutputLinks.remove(jack)
        }
    }

    private fun syncAuxOutput2Added(module: AudioOutput2Module) {
        val baseName = module.name
        val name0 = "${baseName}0"
        val name1 = "${baseName}1"
        val mixer0 = getOrCreateAuxMixer(name0)
        val mixer1 = getOrCreateAuxMixer(name1)

        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[module] as? AudioOutput2Module ?: continue
            voiceMod.twoInDualOut.output.connect(0, mixer0.input, 0)
            voiceMod.twoInDualOut.output.connect(1, mixer1.input, 0)
        }

        if (outputs.none { it.name == name0 }) {
            val jack0 = addAudioOutput(name0, mixer0.output, 0)
            audioOutput2Links[jack0] = module
            module.patchJack0 = jack0
        }
        if (outputs.none { it.name == name1 }) {
            val jack1 = addAudioOutput(name1, mixer1.output, 0)
            audioOutput2Links[jack1] = module
            module.patchJack1 = jack1
        }
    }

    private fun syncAuxOutput2Removed(module: AudioOutput2Module) {
        val baseName = module.name
        val name0 = "${baseName}0"
        val name1 = "${baseName}1"
        val mixer0 = auxMixers.remove(name0)
        val mixer1 = auxMixers.remove(name1)

        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[module] as? AudioOutput2Module ?: continue
            if (mixer0 != null) voiceMod.twoInDualOut.output.disconnect(0, mixer0.input, 0)
            if (mixer1 != null) voiceMod.twoInDualOut.output.disconnect(1, mixer1.input, 0)
        }
        mixer0?.let {
            if (it.isStartRequired) it.stop()
            SynthKSyn.synth.remove(it)
        }
        mixer1?.let {
            if (it.isStartRequired) it.stop()
            SynthKSyn.synth.remove(it)
        }

        (outputs.find { it.name == name0 } as? AudioOutputJack)?.let {
            removeOutput(it)
            audioOutput2Links.remove(it)
        }
        (outputs.find { it.name == name1 } as? AudioOutputJack)?.let {
            removeOutput(it)
            audioOutput2Links.remove(it)
        }
    }

    private fun syncModuleRemoved(module: Module) {
        super.onChildModuleRemoved(module)
        if (!isInitialized) return
        if (module is AudioOutputModule && module.name != "output") {
            syncAuxOutputRemoved(module)
        }
        if (module is AudioOutput2Module && module.name != "output" && module.name != "output2~") {
            syncAuxOutput2Removed(module)
        }

        for (voice in voices) {
            val voiceModule = voice.templateToVoiceMap.remove(module) ?: continue
            voice.voiceToTemplateMap.remove(voiceModule)

            if (module is AudioOutputModule) {
                val voiceOutMod = voiceModule as AudioOutputModule
                if (module.name == "output") {
                    voiceOutMod.passThrough.output.disconnect(voice.outputPassThrough.input)
                } else {
                    auxMixers[module.name]?.let { mixer ->
                        voiceOutMod.passThrough.output.disconnect(mixer.input)
                    }
                }
            }

            if (module is AudioOutput2Module) {
                val voiceOutMod = voiceModule as AudioOutput2Module
                if (module.name == "output" || module.name == "output2~") {
                    auxMixers["out0"]?.let { mixer0 ->
                        voiceOutMod.twoInDualOut.output.disconnect(0, mixer0.input, 0)
                    }
                    auxMixers["out1"]?.let { mixer1 ->
                        voiceOutMod.twoInDualOut.output.disconnect(1, mixer1.input, 0)
                    }
                } else {
                    auxMixers["${module.name}0"]?.let { mixer0 ->
                        voiceOutMod.twoInDualOut.output.disconnect(0, mixer0.input, 0)
                    }
                    auxMixers["${module.name}1"]?.let { mixer1 ->
                        voiceOutMod.twoInDualOut.output.disconnect(1, mixer1.input, 0)
                    }
                }
            }

            if (module is AudioInputModule) {
                if (!isSpecialVoiceInput(module.name)) {
                    val voiceInMod = voiceModule as AudioInputModule
                    module.passThrough.output.disconnect(voiceInMod.passThrough.input)
                }
            }

            voiceUnitsOf(voiceModule).forEach { voice.removeVoiceUnit(it) }

            voice.graphState.removeModulesDirectly(listOf(voiceModule))
        }
        updateAutoDisableBindings()
    }

    private fun syncConnectionAdded(outputJack: OutputJack, inputJack: InputJack) {
        if (!isInitialized) return

        val outMod = outputJack.module
        val inMod = inputJack.module

        for (voice in voices) {
            val voiceOutMod = voice.templateToVoiceMap[outMod] ?: continue
            val voiceInMod = voice.templateToVoiceMap[inMod] ?: continue
            val voiceOutJack = findMatchingOutputJack(voiceOutMod, outputJack) ?: continue
            val voiceInJack = findMatchingInputJack(voiceInMod, inputJack) ?: continue
            voice.graphState.isDeserializing = graphState.isDeserializing
            voice.graphState.connectDirectly(voiceOutJack, voiceInJack)
        }
        updateAutoDisableBindings()
    }

    private fun syncConnectionRemoved(outputJack: OutputJack, inputJack: InputJack) {
        if (!isInitialized) return

        val outMod = outputJack.module
        val inMod = inputJack.module

        for (voice in voices) {
            val voiceOutMod = voice.templateToVoiceMap[outMod] ?: continue
            val voiceInMod = voice.templateToVoiceMap[inMod] ?: continue
            val voiceOutJack = findMatchingOutputJack(voiceOutMod, outputJack) ?: continue
            val voiceInJack = findMatchingInputJack(voiceInMod, inputJack) ?: continue
            voice.graphState.disconnect(voiceOutJack, voiceInJack)
        }
        updateAutoDisableBindings()
    }

    fun findMatchingInputJack(targetModule: Module, templateJack: InputJack): InputJack? {
        if (templateJack is AudioInputJack) {
            return targetModule.inputs.filterIsInstance<AudioInputJack>().find {
                it.name == templateJack.name && it.partIndex == templateJack.partIndex
            } ?: targetModule.inputs.find { it.name == templateJack.name }
        }
        return targetModule.inputs.find { it.name == templateJack.name && it::class == templateJack::class }
            ?: targetModule.inputs.find { it.name == templateJack.name }
    }

    fun findMatchingOutputJack(targetModule: Module, templateJack: OutputJack): OutputJack? {
        if (templateJack is AudioOutputJack) {
            return targetModule.outputs.filterIsInstance<AudioOutputJack>().find {
                it.name == templateJack.name && it.partIndex == templateJack.partIndex
            } ?: targetModule.outputs.find { it.name == templateJack.name }
        }
        return targetModule.outputs.find { it.name == templateJack.name && it::class == templateJack::class }
            ?: targetModule.outputs.find { it.name == templateJack.name }
    }

    private fun createVoiceModule(templateModule: Module): Module {
        val voiceModule = when (templateModule) {
            is InputModule -> {
                InputModule().apply {
                    name = templateModule.name
                    defaultMin = templateModule.defaultMin
                    defaultMax = templateModule.defaultMax
                    defaultValue = templateModule.defaultValue
                    hasValue = templateModule.hasValue
                    defaultIsKickable = templateModule.defaultIsKickable
                }
            }
            is CommandModule -> {
                CommandModule().apply {
                    name = templateModule.name
                }
            }
            is KnobModule -> {
                KnobModule().apply {
                    name = templateModule.name
                    value = templateModule.value
                }
            }
            is FaderModule -> {
                FaderModule().apply {
                    name = templateModule.name
                    value = templateModule.value
                }
            }
            is XyPadModule -> {
                XyPadModule().apply {
                    name = templateModule.name
                    x = templateModule.x
                    y = templateModule.y
                    width = templateModule.width
                    height = templateModule.height
                }
            }
            is NumberModule -> {
                NumberModule().apply {
                    name = templateModule.name
                    value = templateModule.value
                }
            }
            is AudioInputModule -> {
                AudioInputModule().apply { name = templateModule.name }
            }
            is AudioOutputModule -> {
                AudioOutputModule().apply { name = templateModule.name }
            }
            is AudioOutput2Module -> {
                AudioOutput2Module().apply { name = templateModule.name }
            }
            is OutputModule -> {
                OutputModule().apply { name = templateModule.name }
            }
            is EnvSegModule -> {
                EnvSegModule().apply {
                    name = templateModule.name
                    envelope = templateModule.envelope // Share by reference!
                    isLooping = templateModule.isLooping
                }
            }
            is FloatSampleModule -> {
                FloatSampleModule().apply {
                    name = templateModule.name
                    sample = templateModule.sample // Share by reference!
                    filePath = templateModule.filePath
                    // The constructor already sent its empty default sample, and the data
                    // output hands its last sample to each reader it connects to. Send the
                    // real one so the voice's readers get it instead of silence.
                    sendSample()
                }
            }
            is SamplePlayerModule -> {
                SamplePlayerModule().apply {
                    name = templateModule.name
                    sample = templateModule.sample // Share by reference!
                    filePath = templateModule.filePath
                    isLooping = templateModule.isLooping
                }
            }
            is SampleReaderModule -> {
                SampleReaderModule().apply {
                    name = templateModule.name
                    isLooping = templateModule.isLooping
                }
            }
            is SampleWriterModule -> {
                SampleWriterModule().apply {
                    name = templateModule.name
                    isLooping = templateModule.isLooping
                }
            }
            is ToggleModule -> {
                ToggleModule().apply {
                    name = templateModule.name
                }
            }
            is EquationModule -> {
                EquationModule(templateModule.name).apply {
                    equation = templateModule.equation
                    width = templateModule.width
                    height = templateModule.height
                }
            }
            is SequenceModule -> {
                SequenceModule(templateModule.numOutputs).apply {
                    name = templateModule.name
                    for (i in 0 until templateModule.numOutputs) {
                        values[i] = templateModule.values[i]
                    }
                }
            }
            is ScaleModule -> {
                ScaleModule(templateModule.name, templateModule.isEditable, templateModule.scaleDegrees.copyOf())
            }
            // Must come before UnitGeneratorModule, whose branch rebuilds a plain module from the
            // unit factory and would silently drop the order chosen in the menu or the data jack.
            is ChebyshevOscModule -> {
                ChebyshevOscModule().apply {
                    name = templateModule.name
                    order = templateModule.order
                }
            }
            is SampleScrubberModule -> {
                SampleScrubberModule().apply {
                    name = templateModule.name
                    sample = templateModule.sample
                }
            }
            is SampleGrainsModule -> {
                SampleGrainsModule().apply {
                    name = templateModule.name
                    sample = templateModule.sample
                }
            }
            is UnitGeneratorModule -> {
                val resolvedType = ModuleRegistry.resolveLegacyTypeName(templateModule.type)
                val factory = ModuleRegistry.ksynFactories[templateModule.type]
                    ?: ModuleRegistry.ksynFactories[resolvedType]
                    ?: ModuleRegistry.ksynClassFactories[templateModule.unit::class]
                if (factory != null) {
                    UnitGeneratorModule(templateModule.type, factory()).apply {
                        name = templateModule.name
                    }
                } else {
                    ModuleRegistry.findCreator(templateModule.type)?.invoke() ?: templateModule
                }
            }
            is GrabAudioModule -> {
                GrabAudioModule().apply { name = templateModule.name }
            }
            else -> {
                ModuleRegistry.findCreator(templateModule.type)?.invoke() ?: templateModule
            }
        }

        // Every module that can switch its voice off keeps the template's choice.
        voiceModule.autoDisableVoiceMode = templateModule.autoDisableVoiceMode

        // Copy input jack properties and values
        for (templateJack in templateModule.inputs) {
            val voiceJack = findMatchingInputJack(voiceModule, templateJack)
            if (voiceJack != null) {
                voiceJack.isKickable = templateJack.isKickable
                if (templateJack is AudioInputJack && voiceJack is AudioInputJack) {
                    voiceJack.isValueAdded = templateJack.isValueAdded
                }
                voiceJack.min = templateJack.min
                voiceJack.max = templateJack.max
                if (voiceJack !is CommandJack && !voiceJack.isKickable) {
                    voiceJack.setValue(templateJack.getValue())
                }
            }
        }
        return voiceModule
    }

    fun syncEnvSegEnvelope(templateMod: EnvSegModule, newEnvelope: com.softsynth.ksyn.data.SegmentedEnvelope) {
        if (!isInitialized) return
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[templateMod] as? EnvSegModule ?: continue
            voiceMod.envelope = newEnvelope
        }
    }

    /** Gives every voice a sample newly loaded into the template's sample-based module. */
    fun syncSample(templateMod: Module, sample: com.softsynth.ksyn.data.FloatSample?) {
        if (!isInitialized) return
        for (voice in voices) {
            when (val voiceMod = voice.templateToVoiceMap[templateMod]) {
                is FloatSampleModule -> if (sample != null) {
                    voiceMod.sample = sample
                    voiceMod.sendSample()
                }
                is SamplePlayerModule -> if (sample != null) voiceMod.sample = sample
                is SampleScrubberModule -> voiceMod.sample = sample
                is SampleGrainsModule -> voiceMod.sample = sample
            }
        }
    }

    fun syncChebyshevOrder(templateMod: ChebyshevOscModule, order: Int) {
        if (!isInitialized) return
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[templateMod] as? ChebyshevOscModule ?: continue
            voiceMod.order = order
        }
    }

    fun onAudioInputNameChanged(module: AudioInputModule, newName: String) {
        if (!isInitialized) return
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[module] ?: continue
            voiceMod.name = newName
        }
    }

    fun onAudioOutputNameChanged(module: AudioOutputModule, newName: String) {
        if (!isInitialized) return
        val oldName = module.name
        if (oldName == newName) return

        val mixer = auxMixers.remove(oldName)
        if (mixer != null) {
            auxMixers[newName] = mixer
        }
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[module] ?: continue
            voiceMod.name = newName
        }
        module.patchJack?.name = newName
    }

    fun onAudioOutput2NameChanged(module: AudioOutput2Module, newName: String) {
        if (!isInitialized) return
        val oldName = module.name
        if (oldName == newName) return

        val baseOld = if (oldName == "output2~" || oldName == "output") "out" else oldName
        val baseNew = if (newName == "output2~" || newName == "output") "out" else newName

        val mixer0 = auxMixers.remove("${baseOld}0")
        if (mixer0 != null) {
            auxMixers["${baseNew}0"] = mixer0
        }
        val mixer1 = auxMixers.remove("${baseOld}1")
        if (mixer1 != null) {
            auxMixers["${baseNew}1"] = mixer1
        }
        for (voice in voices) {
            val voiceMod = voice.templateToVoiceMap[module] ?: continue
            voiceMod.name = newName
        }
        module.patchJack0?.name = "${baseNew}0"
        module.patchJack1?.name = "${baseNew}1"
    }

    fun syncAutoDisableVoice(templateModule: Module, mode: com.syntona.dataflow.model.AutoDisableVoiceMode) {
        templateModule.autoDisableVoiceMode = mode
        if (!isInitialized) return
        for (voice in voices) {
            voice.templateToVoiceMap[templateModule]?.autoDisableVoiceMode = mode
        }
        updateAutoDisableBindings()
    }

    /**
     * Chooses which module, if any, switches each voice off when it finishes, and binds its unit
     * to the voice circuit. Any module whose unit implements SupportsAutoDisable can be chosen.
     */
    fun updateAutoDisableBindings() {
        if (!isInitialized) return

        val candidates: Map<Module, Module> =
            graphState.modules.filter { it.supportsAutoDisableVoice }.associateWith { it }
        val mainModule = SourceExporter.findMainAmpEnvelope(
            patch = this,
            candidates = candidates,
            getVarName = { it.name }
        )

        for (voice in voices) {
            // First turn it off everywhere, so that a module that is no longer chosen lets go.
            for (m in voice.graphState.modules) {
                m.autoDisableUnit?.setAutoDisableEnabled(false)
            }

            val voiceUnit = mainModule?.let { voice.templateToVoiceMap[it] }?.autoDisableUnit
            if (voiceUnit != null) {
                voiceUnit.setupAutoDisable(voice.voiceCircuit)
            } else {
                voice.voiceCircuit.isEnabled = true
            }
        }
    }

    override fun teardown() {
        val instrument = polyphonicInstrument
        if (instrument != null) {
            if (instrument.isStartRequired) {
                instrument.stop()
            }
            instrument.getOutputPort().disconnectAll()
            instrument.amplitude.disconnectAll()
            instrument.pitchOffset.disconnectAll()
            for (voice in voices) {
                instrument.remove(voice.getUnitGenerator())
            }
            SynthKSyn.synth.remove(instrument)
        }
        polyphonicInstrument = null
        for (mixer in auxMixers.values) {
            if (mixer.isStartRequired) mixer.stop()
            mixer.output.disconnectAll()
            mixer.input.disconnectAll()
            SynthKSyn.synth.remove(mixer)
        }
        auxMixers.clear()
        for (voice in voices) {
            voice.teardown()
        }
        super.teardown()
    }
}

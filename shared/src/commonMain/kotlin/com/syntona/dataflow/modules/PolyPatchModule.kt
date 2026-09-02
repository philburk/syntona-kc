package com.syntona.dataflow.modules

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.voices.PitchedVoice
import com.softsynth.ksyn.voices.PolyphonicInstrument
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.model.*
import com.syntona.dataflow.registry.ModuleRegistry

class PolyPatchModule(name: String = "polyPatch") : PatchModule(name, "polyPatch") {
    override val isAllowedInPolyPatch: Boolean
        get() = false

    var maxVoices: Int = 8
        private set

    var polyphonicInstrument: PolyphonicInstrument? = null
        private set

    var voices: Array<PolyPatchVoice> = emptyArray()
        private set

    var isInitialized = false
        private set

    val voiceInputJack = addVoiceInput("input", isKickable = true)

    private val auxMixers = mutableMapOf<String, PassThrough>()

    init {
        // Setup graphState lifecycle callbacks to sync edits to background voices
        graphState.onModuleAddedCallback = ::syncModuleAdded
        graphState.onModuleRemovedCallback = ::syncModuleRemoved
        graphState.onConnectionAddedCallback = ::syncConnectionAdded
        graphState.onConnectionRemovedCallback = ::syncConnectionRemoved

        // 1. "on" (event kick for triggering envelopes, sample queues, sequencers)
        val onIn = InputModule()
        onIn.name = "on"
        graphState.addModule(onIn, androidx.compose.ui.geometry.Offset(50f, 60f))

        // 2. "off" (event kick for releasing envelopes, sequencers)
        val offIn = InputModule()
        offIn.name = "off"
        graphState.addModule(offIn, androidx.compose.ui.geometry.Offset(50f, 130f))

        // 3. "pitch" (event input)
        val pitchIn = InputModule()
        pitchIn.name = "pitch"
        graphState.addModule(pitchIn, androidx.compose.ui.geometry.Offset(50f, 200f))

        // 4. "amplitude" (event input)
        val ampIn = InputModule()
        ampIn.name = "amplitude"
        graphState.addModule(ampIn, androidx.compose.ui.geometry.Offset(50f, 270f))

        // 5. "pressure" (event input)
        val pressIn = InputModule()
        pressIn.name = "pressure"
        graphState.addModule(pressIn, androidx.compose.ui.geometry.Offset(50f, 340f))

        // 6. "output" (audio output)
        val defaultOutput = AudioOutputModule()
        defaultOutput.name = "output"
        graphState.addModule(defaultOutput, androidx.compose.ui.geometry.Offset(450f, 200f))
    }

    override fun setEngine(engine: DataFlowEngine) {
        super.setEngine(engine)
        for (voice in voices) {
            voice.setEngine(engine)
        }
    }

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

    fun initializeVoices(maxVoices: Int) {
        if (isInitialized) return
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
        val hasOn = graphState.modules.any { it is InputModule && it.name == "on" }
        if (!hasOn) {
            val onIn = InputModule()
            onIn.name = "on"
            graphState.addModule(onIn, androidx.compose.ui.geometry.Offset(50f, 60f))
        }
        val hasOff = graphState.modules.any { it is InputModule && it.name == "off" }
        if (!hasOff) {
            val offIn = InputModule()
            offIn.name = "off"
            graphState.addModule(offIn, androidx.compose.ui.geometry.Offset(50f, 130f))
        }
        val hasPitch = graphState.modules.any { (it is InputModule || it is AudioInputModule) && it.name == "pitch" }
        if (!hasPitch) {
            val pitchIn = InputModule()
            pitchIn.name = "pitch"
            graphState.addModule(pitchIn, androidx.compose.ui.geometry.Offset(50f, 200f))
        }
        val hasAmp = graphState.modules.any { (it is InputModule || it is AudioInputModule) && it.name == "amplitude" }
        if (!hasAmp) {
            val ampIn = InputModule()
            ampIn.name = "amplitude"
            graphState.addModule(ampIn, androidx.compose.ui.geometry.Offset(50f, 270f))
        }
        val hasPress = graphState.modules.any { (it is InputModule || it is AudioInputModule) && it.name == "pressure" }
        if (!hasPress) {
            val pressIn = InputModule()
            pressIn.name = "pressure"
            graphState.addModule(pressIn, androidx.compose.ui.geometry.Offset(50f, 340f))
        }
        val hasOutput = graphState.modules.any { it is AudioOutputModule && it.name == "output" }
        if (!hasOutput) {
            val defaultOutput = AudioOutputModule()
            defaultOutput.name = "output"
            graphState.addModule(defaultOutput, androidx.compose.ui.geometry.Offset(450f, 200f))
        }
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

    fun clearVoices() {
        for (voice in voices) {
            voice.templateToVoiceMap.clear()
            voice.voiceToTemplateMap.clear()
            voice.graphState.removeModulesDirectly(voice.graphState.modules.toList())
            voice.graphState.connections.clear()
        }
        for (mixer in auxMixers.values) {
            if (mixer.isStartRequired) mixer.stop()
            SynthKSyn.synth.remove(mixer)
        }
        auxMixers.clear()
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

            if (module is AudioInputModule) {
                if (!isSpecialVoiceInput(module.name)) {
                    val voiceInMod = voiceModule as AudioInputModule
                    module.passThrough.output.connect(voiceInMod.passThrough.input)
                }
            }
        }

        if (module is AudioOutputModule && module.name != "output") {
            syncAuxOutputAdded(module)
        }
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

    private fun syncModuleRemoved(module: Module) {
        super.onChildModuleRemoved(module)
        if (!isInitialized) return
        if (module is AudioOutputModule && module.name != "output") {
            syncAuxOutputRemoved(module)
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

            if (module is AudioInputModule) {
                if (!isSpecialVoiceInput(module.name)) {
                    val voiceInMod = voiceModule as AudioInputModule
                    module.passThrough.output.disconnect(voiceInMod.passThrough.input)
                }
            }

            voice.graphState.removeModulesDirectly(listOf(voiceModule))
        }
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
            voice.graphState.connectDirectly(voiceOutJack, voiceInJack)
        }
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
            is OutputModule -> {
                OutputModule().apply { name = templateModule.name }
            }
            is SegmentedEnvelopeModule -> {
                SegmentedEnvelopeModule().apply {
                    name = templateModule.name
                    envelope = templateModule.envelope // Share by reference!
                }
            }
            is FloatSampleModule -> {
                FloatSampleModule().apply {
                    name = templateModule.name
                    sample = templateModule.sample // Share by reference!
                    filePath = templateModule.filePath
                }
            }
            is SamplePlayerModule -> {
                SamplePlayerModule().apply {
                    name = templateModule.name
                    sample = templateModule.sample // Share by reference!
                    filePath = templateModule.filePath
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
            is UnitGeneratorModule -> {
                val resolvedType = ModuleRegistry.resolveLegacyTypeName(templateModule.type)
                val factory = ModuleRegistry.ksynFactories[templateModule.type] ?: ModuleRegistry.ksynFactories[resolvedType]
                if (factory != null) {
                    UnitGeneratorModule(templateModule.name, factory())
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
                voiceJack.setValue(templateJack.getValue())
            }
        }
        return voiceModule
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

    override fun teardown() {
        val instrument = polyphonicInstrument
        if (instrument != null) {
            if (instrument.isStartRequired) {
                instrument.stop()
            }
            SynthKSyn.synth.remove(instrument)
        }
        for (mixer in auxMixers.values) {
            if (mixer.isStartRequired) mixer.stop()
            SynthKSyn.synth.remove(mixer)
        }
        auxMixers.clear()
        for (voice in voices) {
            voice.teardown()
        }
        super.teardown()
    }
}

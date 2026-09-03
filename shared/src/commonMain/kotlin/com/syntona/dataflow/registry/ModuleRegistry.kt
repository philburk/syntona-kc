package com.syntona.dataflow.registry

import com.softsynth.ksyn.unitgen.*
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.*

/**
 * Registry of available modules organized by bundles.
 */
object ModuleRegistry {

    val ksynFactories = mutableMapOf<String, () -> UnitGenerator>()

    data class ModuleEntry(
        val name: String,
        var helpResourcePath: String? = null,
        val creator: () -> Module
    ) {
        constructor(name: String, creator: () -> Module) : this(name, null, creator)
    }

    data class Bundle(
        val name: String,
        val subBundles: MutableList<Bundle> = mutableListOf(),
        val modules: MutableList<ModuleEntry> = mutableListOf()
    )

    val root = Bundle("Modules")

    init {
        val core = getOrCreateBundle(root, "core")
        core.modules.add(ModuleEntry("patch") { PatchModule() })
        core.modules.add(ModuleEntry("polyPatch") { PolyPatchModule() })
        core.modules.add(ModuleEntry("input") { InputModule() })
        core.modules.add(ModuleEntry("command") { CommandModule() })
        core.modules.add(ModuleEntry("output") { OutputModule() })

        val basic = getOrCreateBundle(core, "basic")
        basic.modules.add(ModuleEntry("clock") { ClockModule() })
        basic.modules.add(ModuleEntry("clockDiv") { ClockDividerModule() })
        basic.modules.add(ModuleEntry("timeline") { TimelineModule() })
        basic.modules.add(ModuleEntry("addTime") { AddTimeModule() })
        basic.modules.add(ModuleEntry("counter") { CounterModule() })
        basic.modules.add(ModuleEntry("seq2") { SequenceModule(2) })
        basic.modules.add(ModuleEntry("seq8") { SequenceModule(8) })
        basic.modules.add(ModuleEntry("seq16") { SequenceModule(16) })
        basic.modules.add(ModuleEntry("mod2") { Mod2Module() })
        basic.modules.add(ModuleEntry("mod4") { Mod4Module() })
        basic.modules.add(ModuleEntry("mod8") { Mod8Module() })
        basic.modules.add(ModuleEntry("mod16") { Mod16Module() })
        basic.modules.add(ModuleEntry("link") { LinkModule() })
        basic.modules.add(ModuleEntry("oneShot") { OneShotModule() })
        basic.modules.add(ModuleEntry("send") { SendModule() })
        basic.modules.add(ModuleEntry("receive") { ReceiveModule() })

        val math = getOrCreateBundle(core, "math")
        math.modules.add(ModuleEntry("+") { AddModule() })
        math.modules.add(ModuleEntry("-") { SubtractModule() })
        math.modules.add(ModuleEntry("*") { MultiplyModule() })
        math.modules.add(ModuleEntry("/") { DivideModule() })
        math.modules.add(ModuleEntry("compare") { CompareModule() })
        math.modules.add(ModuleEntry("cos") { CosineModule() })
        math.modules.add(ModuleEntry("floor") { FloorModule() })
        math.modules.add(ModuleEntry("mod") { ModModule() })
        math.modules.add(ModuleEntry("random") { RandomModule() })
        math.modules.add(ModuleEntry("round") { RoundModule() })
        math.modules.add(ModuleEntry("sin") { SineModule() })
        math.modules.add(ModuleEntry("f(x)") { EquationModule("f(x)") })
        math.modules.add(ModuleEntry("f(x,y)") { EquationModule("f(x,y)") })
        math.modules.add(ModuleEntry("f(x,y,z)") { EquationModule("f(x,y,z)") })
        math.modules.add(ModuleEntry("pitchToHz", "tutorials/04_sequencer/03_pitch_to_freq.json") { PitchToFrequencyModule() })

        val logic = getOrCreateBundle(core, "logic")
        logic.modules.add(ModuleEntry("and") { AndModule() })
        logic.modules.add(ModuleEntry("or") { OrModule() })
        logic.modules.add(ModuleEntry("xor") { XorModule() })
        logic.modules.add(ModuleEntry("not") { NotModule() })
        logic.modules.add(ModuleEntry("toggle") { ToggleModule() })
        logic.modules.add(ModuleEntry("pass") { PassModule() })
        logic.modules.add(ModuleEntry("branch4") { Branch4Module() })
        logic.modules.add(ModuleEntry("branch8") { Branch8Module() })
        logic.modules.add(ModuleEntry("branch16") { Branch16Module() })
        logic.modules.add(ModuleEntry("select4") { Select4Module() })
        logic.modules.add(ModuleEntry("select8") { Select8Module() })
        logic.modules.add(ModuleEntry("select16") { Select16Module() })

        val input = getOrCreateBundle(core, "input")
        input.modules.add(ModuleEntry("number") { NumberModule() })
        input.modules.add(ModuleEntry("fader") { FaderModule() })
        input.modules.add(ModuleEntry("knob") { KnobModule() })
        input.modules.add(ModuleEntry("kick") { KickModule() })
        input.modules.add(ModuleEntry("onLoad") { OnLoadModule() })
        input.modules.add(ModuleEntry("comment") { CommentModule() })
        input.modules.add(ModuleEntry("key") { KeyModule() })
        input.modules.add(ModuleEntry("keyMatch", "tutorials/03_core/05_key_match.json") { KeyMatchModule() })

        val view = getOrCreateBundle(core, "view")
        view.modules.add(ModuleEntry("show") { ShowModule() })
        view.modules.add(ModuleEntry("showMany") { ShowManyModule() })
        view.modules.add(ModuleEntry("print") { PrintModule() })
        view.modules.add(ModuleEntry("glow") { GlowModule() })
        view.modules.add(ModuleEntry("flash") { FlashModule() })

        val music = getOrCreateBundle(root, "music")
        val musicInput = getOrCreateBundle(music, "input")
        musicInput.modules.add(ModuleEntry("keyboard13") { Keyboard13Module() })
        musicInput.modules.add(ModuleEntry("keyboard25") { Keyboard25Module() })
        musicInput.modules.add(ModuleEntry("keyNotes") { KeyNotesModule() })
        musicInput.modules.add(ModuleEntry("pianoRoll", "examples/pianoRoll.json") { PianoRollModule() })
        val midi = getOrCreateBundle(music, "midi")
        midi.modules.add(ModuleEntry("packVoice") { PackVoiceModule() })
        midi.modules.add(ModuleEntry("packBend") { PackBendModule() })
        midi.modules.add(ModuleEntry("packControl") { PackControlModule() })
        midi.modules.add(ModuleEntry("packNote") { PackNoteModule() })
        midi.modules.add(ModuleEntry("packProgram") { PackProgramModule() })
        midi.modules.add(ModuleEntry("unpackNoteOn") { UnpackNoteOnModule() })
        midi.modules.add(ModuleEntry("unpackNoteOff") { UnpackNoteOffModule() })
        midi.modules.add(ModuleEntry("unpackControl") { UnpackControlModule() })
        midi.modules.add(ModuleEntry("matchControl", "examples/matchControl.json") { MatchControlModule() })
        midi.modules.add(ModuleEntry("matchNoteOn") { MatchNoteOnModule() })
        midi.modules.add(ModuleEntry("unpackBend") { UnpackBendModule() })
        midi.modules.add(ModuleEntry("unpackProgram") { UnpackProgramModule() })
        midi.modules.add(ModuleEntry("midiIn", "tutorials/06_midi/01_midi_input.json") { MidiInModule() })
        midi.modules.add(ModuleEntry("midiOut", "tutorials/06_midi/04_midiOut.json") { MidiOutModule() })

        val compose = getOrCreateBundle(music, "compose")
        compose.modules.add(ModuleEntry("beatClock") { BeatClockModule() })
        compose.modules.add(ModuleEntry("pentatonic") {
            ScaleModule("pentatonic", isEditable = false, doubleArrayOf(0.0, 2.0, 4.0, 7.0, 9.0)) })
        compose.modules.add(ModuleEntry("diatonic") {
            ScaleModule("diatonic", isEditable = false, doubleArrayOf(0.0, 2.0, 4.0, 5.0, 7.0, 9.0, 11.0)) })
        compose.modules.add(ModuleEntry("scale") {
            ScaleModule("scale", isEditable = true, doubleArrayOf(0.0, 2.0, 4.0, 7.0, 9.0)) })
        compose.modules.add(ModuleEntry("recursiveSeq", "examples/recSeq.json") { RecursiveSeqModule() })
        compose.modules.add(ModuleEntry("stepper", "examples/stepper.json") { StepperModule() })
        compose.modules.add(ModuleEntry("table4") { Table4Module() })
        compose.modules.add(ModuleEntry("table8") { Table8Module() })
        compose.modules.add(ModuleEntry("table16") { Table16Module() })

        val instruments = getOrCreateBundle(music, "instruments")
        val sounds = getOrCreateBundle(music, "sounds")

        val ksyn = getOrCreateBundle(root, "ksyn")
        addKSynModule(ksyn, "lineIn", ::LineIn)
        addKSynModule(ksyn, "lineOut", ::LineOut)
        ksyn.modules.add(ModuleEntry("input~") { AudioInputModule() })
        ksyn.modules.add(ModuleEntry("output~") { AudioOutputModule() })

        val sources = getOrCreateBundle(ksyn, "sources")
        addKSynModule(sources, "sineOsc", ::SineOscillator)
        addKSynModule(sources, "sineOscPM", ::SineOscillatorPhaseModulated)
        addKSynModule(sources, "sawOsc", ::SawtoothOscillator)
        addKSynModule(sources, "sawOscBL", ::SawtoothOscillatorBL)
        addKSynModule(sources, "sawOscDPW", ::SawtoothOscillatorDPW)
        addKSynModule(sources, "squareOsc", ::SquareOscillator)
        addKSynModule(sources, "squareOscBL", ::SquareOscillatorBL)
        addKSynModule(sources, "triOsc", ::TriangleOscillator)
        addKSynModule(sources, "impulse", ::ImpulseOscillator)
        addKSynModule(sources, "impulseBL", ::ImpulseOscillatorBL)
        addKSynModule(sources, "pulseOsc", ::PulseOscillator)
        addKSynModule(sources, "pulseOscBL", ::PulseOscillatorBL)
        addKSynModule(sources, "whiteNoise", ::WhiteNoise)
        addKSynModule(sources, "redNoise", ::RedNoise)
        addKSynModule(sources, "pinkNoise", ::PinkNoise)

        val analysis = getOrCreateBundle(ksyn, "analysis")
        addKSynModule(analysis, "edgeDetector", ::EdgeDetector)
        addKSynModule(analysis, "peakFollower", ::PeakFollower)
        addKSynModule(analysis, "pitchDetector", ::PitchDetector)
        addKSynModule(analysis, "schmittTrigger", ::SchmittTrigger)

        val control = getOrCreateBundle(ksyn, "control")
        addKSynModule(control, "envDAHDSR", ::EnvelopeDAHDSR)
        addKSynModule(control, "envAD", ::EnvelopeAttackDecay)
        addKSynModule(control, "latch", ::Latch)
        addKSynModule(control, "latchZeroX", ::LatchZeroCrossing)
        addKSynModule(control, "rampLin", ::LinearRamp)
        addKSynModule(control, "rampCon", ::ContinuousRamp)
        addKSynModule(control, "rampAsym", ::AsymptoticRamp)
        addKSynModule(control, "rampExp", ::ExponentialRamp)
        addKSynModule(control, "select~", ::Select)
        control.modules.add(ModuleEntry("grabAudio") { GrabAudioModule() })

        val amath = getOrCreateBundle(ksyn, "math")
        addKSynModule(amath, "+~", ::Add)
        addKSynModule(amath, "-~", ::Subtract)
        addKSynModule(amath, "*~", ::Multiply)
        addKSynModule(amath, "*+~", ::MultiplyAdd)
        addKSynModule(amath, "/~", ::Divide)
        addKSynModule(amath, ">~", ::Compare)
        addKSynModule(amath, "pitchToHz~", ::PitchToFrequency)
        addKSynModule(amath, "power2~", ::PowerOfTwo)
        addKSynModule(amath, "min~", ::Minimum)
        addKSynModule(amath, "max~", ::Maximum)
        addKSynModule(amath, "range~", ::RangeConverter)
        ksynFactories["mixer8x2"] = { MixerStereoRamped(8) }
        ksynFactories["mixer4x2"] = { MixerStereoRamped(4) }
        amath.modules.add(ModuleEntry("mixer8x2") { Mixer8x2Module() })
        amath.modules.add(ModuleEntry("mixer4x2") { Mixer4x2Module() })

        val filters = getOrCreateBundle(ksyn, "filters")
        addKSynModule(filters, "lowPass", ::FilterLowPass)
        addKSynModule(filters, "bandPass", ::FilterBandPass)
        addKSynModule(filters, "bandStop", ::FilterBandStop)
        addKSynModule(filters, "highPass", ::FilterHighPass)
        addKSynModule(filters, "allPass", ::FilterAllPass)
        addKSynModule(filters, "lowShelf", ::FilterLowShelf)
        addKSynModule(filters, "highShelf", ::FilterHighShelf)
        addKSynModule(filters, "peakingEQ", ::FilterPeakingEQ)
        addKSynModule(filters, "fourPole", ::FilterFourPoles)
        addKSynModule(filters, "stateVar", ::FilterStateVariable)
        addKSynModule(filters, "waveFolder", ::WaveFolder)

        val effects = getOrCreateBundle(ksyn, "effects")
        addKSynModule(effects, "phaseShifter", ::PhaseShifter)
        ksynFactories["delay~"] = ::InterpolatingDelay
        effects.modules.add(ModuleEntry("delay~") {
            val delay = InterpolatingDelay()
            delay.allocate(48000 * 2)
            UnitGeneratorModule("delay~", delay)
        })
        addKSynModule(effects, "pan", ::Pan)
        addKSynModule(effects, "plateReverb", ::PlateReverb)
        addKSynModule(effects, "roomReverb", ::RoomReverb)

        val ksynView = getOrCreateBundle(ksyn, "view")
        ksynView.modules.add(ModuleEntry("scope") { ScopeModule() })

        val data = getOrCreateBundle(ksyn, "data")
        addKSynModule(data, "monoRdr", ::VariableRateMonoReader)
        addKSynModule(data, "monoWtr", ::FixedRateMonoWriter)
        data.modules.add(ModuleEntry("segEnv") { SegmentedEnvelopeModule() })
        data.modules.add(ModuleEntry("envSeg") { EnvSegModule() })
        data.modules.add(ModuleEntry("samplePlayer") { SamplePlayerModule() })
        data.modules.add(ModuleEntry("sample") { FloatSampleModule() })
        data.modules.add(ModuleEntry("sampleReader", "tutorials/07_audio_input/05_sampleReader.json") { SampleReaderModule() })
        data.modules.add(ModuleEntry("sampleWriter") { SampleWriterModule() })
        data.modules.add(ModuleEntry("recorder") { RecorderModule() })
    }

    private data class Candidate(val path: String, val entry: ModuleEntry)

    private fun findAllCandidates(name: String): List<Candidate> {
        val list = mutableListOf<Candidate>()
        fun collect(bundle: Bundle, currentPath: List<String>) {
            val nextPath = if (bundle.name == "Modules") currentPath else currentPath + bundle.name
            bundle.modules.forEach { mod ->
                if (mod.name == name) {
                    val fullPath = (nextPath + mod.name).joinToString(".")
                    list.add(Candidate(fullPath, mod))
                }
            }
            bundle.subBundles.forEach { collect(it, nextPath) }
        }
        collect(root, emptyList())
        return list
    }

    private fun getMatchScore(targetParts: List<String>, candParts: List<String>): Int {
        var score = 0
        val tSize = targetParts.size
        val cSize = candParts.size
        val minSize = minOf(tSize, cSize)
        for (i in 1..minSize) {
            if (targetParts[tSize - i] == candParts[cSize - i]) {
                score++
            } else {
                break
            }
        }
        return score
    }

    private fun findEntryByPath(path: String): ModuleEntry? {
        val parts = path.split('.')
        if (parts.size <= 1) return null
        var current = root
        for (i in 0..<parts.size - 1) {
            val part = parts[i]
            if (i == 0 && part == "Modules") continue
            current = current.subBundles.find { it.name == part } ?: return null
        }
        return current.modules.find { it.name == parts.last() }
    }

    private fun findEntryByShortName(name: String): ModuleEntry? {
        fun searchBundle(bundle: Bundle): ModuleEntry? {
            bundle.modules.firstOrNull { it.name == name }?.let { return it }
            bundle.subBundles.forEach { searchBundle(it)?.let { e -> return e } }
            return null
        }
        return searchBundle(root)
    }

    fun resolveLegacyModuleName(name: String): String = when (name) {
        "showVoice" -> "showMany"
        "double" -> "number"
        "ckDiv" -> "clockDiv"
        "arm" -> "oneShot"
        "gate" -> "pass"
        "choose4" -> "select4"
        "choose8" -> "select8"
        "choose16" -> "select16"
        "loadKick" -> "onLoad"
        "recSeq" -> "recursiveSeq"
        "p2f" -> "pitchToHz"
        "DAHDSR" -> "envDAHDSR"
        "latchZC" -> "latchZeroX"
        "schmidtTrigger" -> "schmittTrigger"
        "aInput" -> "input~"
        "aOutput" -> "output~"
        "a+" -> "+~"
        "a-" -> "-~"
        "a*" -> "*~"
        "a*+" -> "*+~"
        "a/" -> "/~"
        "a>" -> ">~"
        "ap2f" -> "pitchToHz~"
        "aPower2" -> "power2~"
        "aMin" -> "min~"
        "aMax" -> "max~"
        "aRanger" -> "range~"
        "delay" -> "delay~"
        "select" -> "select~"
        else -> name
    }

    fun resolveLegacyTypeName(rawTypeName: String): String {
        val mappedVoices = if (rawTypeName.startsWith("music.voices.")) {
            rawTypeName.replace("music.voices.", "music.midi.")
        } else {
            rawTypeName
        }

        if (mappedVoices.contains('.')) {
            val parts = mappedVoices.split('.').toMutableList()
            parts[parts.lastIndex] = resolveLegacyModuleName(parts.last())
            return parts.joinToString(".")
        }

        return resolveLegacyModuleName(mappedVoices)
    }

    fun findEntry(typeName: String): ModuleEntry? {
        val resolvedTypeName = resolveLegacyTypeName(typeName)

        // 1. Try to find by exact full path
        if (resolvedTypeName.contains('.')) {
            findEntryByPath(resolvedTypeName)?.let { return it }
        } else {
            findEntryByShortName(resolvedTypeName)?.let { return it }
        }

        // 2. Try to find by final part and get the closest match
        val parts = resolvedTypeName.split('.')
        val finalPart = parts.last()

        val candidates = findAllCandidates(finalPart)
        if (candidates.isEmpty()) return null

        val bestCandidate = candidates.maxByOrNull { cand ->
            getMatchScore(parts, cand.path.split('.'))
        }

        return bestCandidate?.entry
    }

    fun findCreator(typeName: String): (() -> Module)? {
        return findEntry(typeName)?.creator
    }

    fun getHelpResourcePath(typeName: String): String? {
        return findEntry(typeName)?.helpResourcePath
    }

    fun setHelpResourcePath(typeName: String, helpResourcePath: String) {
        findEntry(typeName)?.helpResourcePath = helpResourcePath
    }

    fun getBundlePath(typeName: String): String? {
        val resolvedTypeName = if (typeName == "double") "number" else typeName
        fun searchBundle(bundle: Bundle, currentPath: List<String>): String? {
            val nextPath = if (bundle.name == "Modules") currentPath else currentPath + bundle.name
            if (bundle.modules.any { it.name == resolvedTypeName }) {
                return (nextPath + resolvedTypeName).joinToString(".")
            }
            for (sub in bundle.subBundles) {
                val found = searchBundle(sub, nextPath)
                if (found != null) return found
            }
            return null
        }
        return searchBundle(root, emptyList())
    }

    fun getOrCreateBundle(parent: Bundle, name: String): Bundle {
        return parent.subBundles.find { it.name == name }
            ?: Bundle(name).also { parent.subBundles.add(it) }
    }

    private fun addKSynModule(
        bundle: Bundle,
        name: String,
        unitFactory: () -> UnitGenerator,
        helpResourcePath: String? = null
    ) {
        ksynFactories[name] = unitFactory
        bundle.modules.add(ModuleEntry(name, helpResourcePath) { UnitGeneratorModule(name, unitFactory()) })
    }
}

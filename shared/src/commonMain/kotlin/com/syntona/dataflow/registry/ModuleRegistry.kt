package com.syntona.dataflow.registry

import com.softsynth.ksyn.unitgen.*
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.*

/**
 * Registry of available modules organized by bundles.
 */
object ModuleRegistry {

    val ksynFactories = mutableMapOf<String, () -> UnitGenerator>()
    val ksynClassFactories = mutableMapOf<kotlin.reflect.KClass<out UnitGenerator>, () -> UnitGenerator>()

    data class ModuleEntry(
        val name: String,
        var description: String? = null,
        var helpResourcePath: String? = null,
        val creator: () -> Module
    ) {
        constructor(name: String, creator: () -> Module) : this(name, null, null, creator)
        constructor(name: String, helpResourcePath: String?, creator: () -> Module) : this(name, null, helpResourcePath, creator)
    }

    /**
     * One-line help descriptions for each module explaining its purpose and behavior.
     * Edit these entries directly if any descriptions need clarification.
     */
    val moduleDescriptions: MutableMap<String, String> = mutableMapOf(
        // ==========================================
        // Core
        // ==========================================
        "patch" to "container for a nested sub-patch",
        "polyPatch" to "polyphonic voice allocator and patch container",
        "input" to "add an input jack to a patch",
        "command" to "add a command jack to a patch",
        "output" to "add an output jack to a patch",

        // ==========================================
        // Core -> Basic
        // ==========================================
        "clock" to "periodic incrementing clock at a rate in Hz",
        "clockDiv" to "divide clock frequency by an integer ratio",
        "timeline" to "schedule events and automation triggers along a timeline",
        "addTime" to "delay an event by adding time in milliseconds",
        "grabTime" to "output the timestamp of an incoming message in seconds",
        "timeNow" to "output the current time in seconds when kicked",
        "valueList" to "store and manipulate a dynamic list of values",
        "counter" to "increment count on each kick with reset",
        "seq2" to "2-step event and value sequencer",
        "seq8" to "8-step event and value sequencer",
        "seq16" to "16-step event and value sequencer",
        "mod2" to "route incoming values modulo 2 to alternating outputs",
        "mod4" to "route incoming values modulo 4 to 4 outputs",
        "mod8" to "route incoming values modulo 8 to 8 outputs",
        "mod16" to "route incoming values modulo 16 to 16 outputs",
        "link" to "open a linked patch file when triggered",
        "oneShot" to "pass a single message when armed and triggered",
        "send" to "send messages wirelessly to named receive modules",
        "receive" to "receive messages wirelessly from matching send modules",

        // ==========================================
        // Core -> Math
        // ==========================================
        "+" to "add two numbers (A + B)",
        "-" to "subtract two numbers (A - B)",
        "*" to "multiply two numbers (A * B)",
        "/" to "divide two numbers (A / B)",
        "abs" to "calculate the absolute value of a number",
        "compare" to "compare two numbers and output to less-than, equal, or greater-than jacks",
        "cos" to "calculate cosine of an angle in radians",
        "floor" to "round number down to the nearest integer",
        "max" to "output the greater of two numbers",
        "min" to "output the lesser of two numbers",
        "mod" to "calculate remainder of division (A % B)",
        "random" to "generate a pseudo-random number between 0.0 and 1.0 when kicked",
        "round" to "round number to the nearest integer",
        "sin" to "calculate sine of an angle in radians",
        "f(x)" to "evaluate a math expression with variable x",
        "f(x,y)" to "evaluate a math expression with variables x and y",
        "f(x,y,z)" to "evaluate a math expression with variables x, y, and z",
        "pitchToHz" to "convert MIDI pitch to frequency in Hertz",

        // ==========================================
        // Core -> Logic
        // ==========================================
        "and" to "logical AND: outputs 1 if both inputs are true (> 0), otherwise 0",
        "or" to "logical OR: outputs 1 if either input is true (> 0), otherwise 0",
        "xor" to "logical XOR: outputs 1 if exactly one input is true (> 0), otherwise 0",
        "not" to "logical NOT: inverts boolean value of input",
        "toggle" to "alternate trigger between two outputs on each kick",
        "pass" to "pass or block messages based on an enable gate",
        "branch4" to "route input message to 1 of 4 outputs selected by index",
        "branch8" to "route input message to 1 of 8 outputs selected by index",
        "branch16" to "route input message to 1 of 16 outputs selected by index",
        "select4" to "select 1 of 4 inputs to pass to output by index",
        "select8" to "select 1 of 8 inputs to pass to output by index",
        "select16" to "select 1 of 16 inputs to pass to output by index",

        // ==========================================
        // Core -> Input
        // ==========================================
        "number" to "constant or editable numeric value",
        "fader" to "interactive vertical slider control",
        "knob" to "interactive rotary dial control",
        "kick" to "interactive pushbutton to send a trigger message",
        "onLoad" to "send a trigger message when patch is loaded",
        "comment" to "text label or note on the patch canvas",
        "key" to "capture computer keyboard keystrokes as messages",
        "keyMatch" to "match specific computer keyboard keys to trigger an output",

        // ==========================================
        // Core -> View
        // ==========================================
        "show" to "display the most recent numeric value on the canvas",
        "showMany" to "display multiple incoming values or voice states",
        "print" to "log incoming values or messages to the console",
        "glow" to "indicator light that varies brightness with input level",
        "flash" to "indicator light that flashes briefly when triggered",

        // ==========================================
        // Music -> Input
        // ==========================================
        "keyboard13" to "13-key interactive musical keyboard (one octave)",
        "keyboard25" to "25-key interactive musical keyboard (two octaves)",
        "keyNotes" to "play musical notes using the computer keyboard",
        "pianoRoll" to "graphical piano roll note sequencer and editor",

        // ==========================================
        // Music -> MIDI
        // ==========================================
        "packVoice" to "compose a voice message from opcode, tag, value, and extra",
        "packBend" to "compose a pitch bend voice message",
        "packControl" to "compose a MIDI control change voice message",
        "packNote" to "compose a timed note-on and note-off voice message pair",
        "packProgram" to "compose a MIDI program change voice message",
        "unpackNoteOn" to "unpack pitch and velocity from note-on voice messages",
        "unpackNoteOff" to "unpack pitch and release velocity from note-off voice messages",
        "unpackControl" to "unpack controller value from control change messages",
        "matchControl" to "match a specific MIDI controller number and output its value",
        "matchControl4" to "match 4 sequential MIDI controllers and output their values",
        "matchControl8" to "match 8 sequential MIDI controllers and output their values",
        "matchNoteOn" to "match a specific MIDI note number and output its velocity",
        "unpackBend" to "unpack pitch bend amount from voice messages",
        "unpackProgram" to "unpack program change number from voice messages",
        "midiIn" to "receive MIDI messages from an external hardware device",
        "midiOut" to "send voice messages to an external MIDI hardware device",

        // ==========================================
        // Music -> Compose
        // ==========================================
        "beatClock" to "periodic incrementing clock at subdivisions of a beat",
        "pentatonic" to "convert scale degrees into a major pentatonic scale",
        "diatonic" to "convert scale degrees into a diatonic scale",
        "scale" to "quantize pitch or scale degrees into a custom scale",
        "recursiveSeq" to "generate algorithmic fractal sequences based on recursive math",
        "stepper" to "bounded step counter and iterator with loop control",
        "table4" to "use an index to look up a value in a table",
        "table8" to "use an index to look up a value in an 8-element table",
        "table16" to "use an index to look up a value in a 16-element table",

        // ==========================================
        // KSyn (Top-level)
        // ==========================================
        "lineIn" to "read audio input from sound card hardware",
        "lineOut" to "write audio output to sound card hardware",
        "input~" to "audio input jack for a patch or sub-patch",
        "output~" to "audio output jack for a patch or sub-patch",

        // ==========================================
        // KSyn -> Sources
        // ==========================================
        "sineOsc" to "sine wave audio oscillator",
        "sineOscPM" to "phase-modulated sine wave oscillator",
        "sawOsc" to "sawtooth wave oscillator",
        "sawOscBL" to "band-limited sawtooth wave oscillator",
        "sawOscDPW" to "differentiated polynomial waveform (DPW) sawtooth oscillator",
        "squareOsc" to "square wave oscillator",
        "squareOscBL" to "band-limited square wave oscillator",
        "triOsc" to "triangle wave oscillator",
        "impulse" to "single-sample impulse click generator",
        "impulseBL" to "band-limited impulse train generator",
        "pulseOsc" to "variable pulse width oscillator",
        "pulseOscBL" to "band-limited variable pulse width oscillator",
        "whiteNoise" to "white noise random audio generator",
        "redNoise" to "red / Brownian noise audio generator",
        "pinkNoise" to "pink noise 1/f audio generator",

        // ==========================================
        // KSyn -> Analysis
        // ==========================================
        "edgeDetector" to "detect rising or falling transitions in an audio signal",
        "peakFollower" to "track peak amplitude envelope of an audio signal",
        "pitchDetector" to "detect fundamental pitch and period of an audio signal",
        "schmittTrigger" to "convert signal to true/false with hysteresis",
        "zeroCrossing" to "detect positive zero crossings of an audio signal",

        // ==========================================
        // KSyn -> Control
        // ==========================================
        "envDAHDSR" to "delay-attack-hold-decay-sustain-release envelope generator",
        "envAD" to "attack-decay envelope generator",
        "envSeg" to "multi-segment breakpoint envelope generator",
        "latch" to "sample and hold audio input when triggered",
        "latchZeroX" to "sample and hold audio input on zero crossings",
        "rampLin" to "linear ramp generator towards target value",
        "rampCon" to "continuous ramp generator with constant slope",
        "rampAsym" to "asymptotic exponential smoothing ramp towards target",
        "rampExp" to "exponential ramp generator towards target value",
        "select~" to "switch or select between audio input signals",
        "grabAudio" to "capture audio signal sample value as a control message when kicked",

        // ==========================================
        // KSyn -> Math
        // ==========================================
        "+~" to "add two audio signals",
        "-~" to "subtract two audio signals",
        "*~" to "multiply two audio signals",
        "*+~" to "multiply two audio signals and add a third (A * B + C)",
        "/~" to "divide audio signal A by signal B",
        ">~" to "compare audio signals: 1.0 if A > B, else 0.0",
        "pitchToHz~" to "convert pitch signal to frequency in Hertz at audio rate",
        "power2~" to "calculate 2 raised to the power of the input signal",
        "min~" to "output the minimum of two audio signals",
        "max~" to "output the maximum of two audio signals",
        "range~" to "rescale audio signal from input range to output range",
        "mixer4x2" to "4-input stereo audio mixer with gain and pan controls",
        "mixer8x2" to "8-input stereo audio mixer with gain and pan controls",

        // ==========================================
        // KSyn -> Filters
        // ==========================================
        "lowPass" to "second-order resonant low-pass filter",
        "bandPass" to "second-order resonant band-pass filter",
        "bandStop" to "second-order notch / band-stop filter",
        "highPass" to "second-order resonant high-pass filter",
        "allPass" to "all-pass filter for phase shifting and dispersion",
        "lowShelf" to "low-shelf equalizer filter",
        "highShelf" to "high-shelf equalizer filter",
        "peakingEQ" to "parametric peaking equalizer filter",
        "fourPole" to "four-pole 24dB/octave resonant low-pass filter",
        "stateVar" to "state-variable filter with simultaneous low, high, and band outputs",
        "waveFolder" to "wave folder for rich harmonic distortion and folding",

        // ==========================================
        // KSyn -> Effects
        // ==========================================
        "phaseShifter" to "multi-stage phase shifter / phaser effect",
        "delay~" to "interpolating audio delay line",
        "pan" to "stereo panning and balance control",
        "plateReverb" to "plate reverberation effect",
        "roomReverb" to "room reverberation effect",

        // ==========================================
        // KSyn -> View
        // ==========================================
        "scope" to "oscilloscope for visual waveform display",
        "vu1" to "single-channel mono VU meter",
        "vu2" to "two-channel stereo VU meter",

        // ==========================================
        // KSyn -> Sampling
        // ==========================================
        "samplePlayer" to "audio sample player with looping and rate control",
        "sample" to "hold a loaded audio sample buffer in memory",
        "sampleReader" to "play a slice or loop from a shared sample buffer",
        "sampleWriter" to "write incoming audio into a shared sample buffer",
        "recorder" to "record incoming audio to a buffer and play it back"
    )

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
        basic.modules.add(ModuleEntry("grabTime") { GrabTimeModule() })
        basic.modules.add(ModuleEntry("timeNow") { TimeNowModule() })
        basic.modules.add(ModuleEntry("valueList") { ValueListModule() })
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
        math.modules.add(ModuleEntry("abs") { AbsModule() })
        math.modules.add(ModuleEntry("compare") { CompareModule() })
        math.modules.add(ModuleEntry("cos") { CosineModule() })
        math.modules.add(ModuleEntry("floor") { FloorModule() })
        math.modules.add(ModuleEntry("max") { MaxModule() })
        math.modules.add(ModuleEntry("min") { MinModule() })
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
        midi.modules.add(ModuleEntry("matchControl4") { MatchControl4Module() })
        midi.modules.add(ModuleEntry("matchControl8") { MatchControl8Module() })
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
        analysis.modules.add(ModuleEntry("zeroCrossing") { ZeroCrossingModule() })

        val control = getOrCreateBundle(ksyn, "control")
        addKSynModule(control, "envDAHDSR", ::EnvelopeDAHDSR)
        addKSynModule(control, "envAD", ::EnvelopeAttackDecay)
        control.modules.add(ModuleEntry("envSeg") { EnvSegModule() })
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
        ksynView.modules.add(ModuleEntry("vu1") { VuMeterModule(1) })
        ksynView.modules.add(ModuleEntry("vu2") { VuMeterModule(2) })

        val sampling = getOrCreateBundle(ksyn, "sampling")
        sampling.modules.add(ModuleEntry("samplePlayer") { SamplePlayerModule() })
        sampling.modules.add(ModuleEntry("sample") { FloatSampleModule() })
        sampling.modules.add(ModuleEntry("sampleReader", "tutorials/07_audio_input/05_sampleReader.json") { SampleReaderModule() })
        sampling.modules.add(ModuleEntry("sampleWriter") { SampleWriterModule() })
        sampling.modules.add(ModuleEntry("recorder") { RecorderModule() })

        fun applyDescriptions(bundle: Bundle) {
            bundle.modules.forEach { mod ->
                if (mod.description == null) {
                    mod.description = moduleDescriptions[mod.name]
                }
            }
            bundle.subBundles.forEach { applyDescriptions(it) }
        }
        applyDescriptions(root)
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
        "segEnv" -> "envSeg"
        "monoRdr" -> "envSeg"
        else -> name
    }

    fun resolveLegacyTypeName(rawTypeName: String): String {
        val mappedPrefix = when {
            rawTypeName.startsWith("basic.math.") -> rawTypeName.replace("basic.math.", "core.math.")
            rawTypeName.startsWith("music.voices.") -> rawTypeName.replace("music.voices.", "music.midi.")
            rawTypeName == "ksyn.data.envSeg" || rawTypeName == "ksyn.data.segEnv" -> "ksyn.control.envSeg"
            rawTypeName.startsWith("ksyn.data.") -> rawTypeName.replace("ksyn.data.", "ksyn.sampling.")
            else -> rawTypeName
        }

        if (mappedPrefix.contains('.')) {
            val parts = mappedPrefix.split('.').toMutableList()
            parts[parts.lastIndex] = resolveLegacyModuleName(parts.last())
            return parts.joinToString(".")
        }

        return resolveLegacyModuleName(mappedPrefix)
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

    fun getDescription(typeName: String): String? {
        val resolved = resolveLegacyModuleName(typeName.substringAfterLast('.'))
        return findEntry(typeName)?.description
            ?: moduleDescriptions[typeName]
            ?: moduleDescriptions[resolved]
    }

    fun setDescription(typeName: String, description: String) {
        findEntry(typeName)?.description = description
        moduleDescriptions[typeName] = description
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

    private inline fun <reified T : UnitGenerator> addKSynModule(
        bundle: Bundle,
        name: String,
        noinline unitFactory: () -> T,
        helpResourcePath: String? = null
    ) {
        ksynFactories[name] = unitFactory
        ksynClassFactories[T::class] = unitFactory
        bundle.modules.add(ModuleEntry(name, helpResourcePath) { UnitGeneratorModule(name, unitFactory()) })
    }
}

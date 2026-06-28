package com.syntona.dataflow.registry

import com.softsynth.ksyn.unitgen.*
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.*

/**
 * Registry of available modules organized by bundles.
 */
object ModuleRegistry {

    data class ModuleEntry(
        val name: String,
        val creator: () -> Module
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
        core.modules.add(ModuleEntry("input") { InputModule() })
        core.modules.add(ModuleEntry("output") { OutputModule() })

        val basic = getOrCreateBundle(core, "basic")
        basic.modules.add(ModuleEntry("clock") { ClockModule() })
        basic.modules.add(ModuleEntry("addTime") { AddTimeModule() })
        basic.modules.add(ModuleEntry("counter") { CounterModule() })
        basic.modules.add(ModuleEntry("seq2") { SequenceModule(2) })
        basic.modules.add(ModuleEntry("seq8") { SequenceModule(8) })

        val math = getOrCreateBundle(core, "math")
        math.modules.add(ModuleEntry("+") { AddModule() })
        math.modules.add(ModuleEntry("-") { SubtractModule() })
        math.modules.add(ModuleEntry("*") { MultiplyModule() })
        math.modules.add(ModuleEntry("/") { DivideModule() })
        math.modules.add(ModuleEntry("compare") { CompareModule() })
        math.modules.add(ModuleEntry("cos") { CosineModule() })
        math.modules.add(ModuleEntry("floor") { FloorModule() })
        math.modules.add(ModuleEntry("random") { RandomModule() })
        math.modules.add(ModuleEntry("round") { RoundModule() })
        math.modules.add(ModuleEntry("sin") { SineModule() })

        val input = getOrCreateBundle(core, "input")
        input.modules.add(ModuleEntry("double") { DoubleEntryModule() })
        input.modules.add(ModuleEntry("fader") { FaderModule() })
        input.modules.add(ModuleEntry("knob") { KnobModule() })
        input.modules.add(ModuleEntry("kick") { KickModule() })
        input.modules.add(ModuleEntry("loadKick") { LoadKickModule() })
        input.modules.add(ModuleEntry("comment") { CommentModule() })

        val view = getOrCreateBundle(core, "view")
        view.modules.add(ModuleEntry("show") { ShowModule() })
        view.modules.add(ModuleEntry("print") { PrintModule() })

        val ksyn = getOrCreateBundle(root, "ksyn")
        addKSynModule(ksyn, "lineOut", ::LineOut)
        ksyn.modules.add(ModuleEntry("aInput") { AudioInputModule() })
        ksyn.modules.add(ModuleEntry("aOutput") { AudioOutputModule() })

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
        addKSynModule(analysis, "schmidtTrigger", ::SchmidtTrigger)

        val control = getOrCreateBundle(ksyn, "control")
        addKSynModule(control, "DAHDSR", ::EnvelopeDAHDSR)
        addKSynModule(control, "envAD", ::EnvelopeAttackDecay)
        addKSynModule(control, "latch", ::Latch)
        addKSynModule(control, "latchZC", ::LatchZeroCrossing)
        addKSynModule(control, "rampLin", ::LinearRamp)
        addKSynModule(control, "rampCon", ::ContinuousRamp)
        addKSynModule(control, "rampAsym", ::AsymptoticRamp)
        addKSynModule(control, "rampExp", ::ExponentialRamp)
        addKSynModule(control, "select", ::Select)

        val amath = getOrCreateBundle(ksyn, "math")
        addKSynModule(amath, "a+", ::Add)
        addKSynModule(amath, "a-", ::Subtract)
        addKSynModule(amath, "a*", ::Multiply)
        addKSynModule(amath, "a*+", ::MultiplyAdd)
        addKSynModule(amath, "a/", ::Divide)
        addKSynModule(amath, "a>", ::Compare)
        addKSynModule(amath, "aPower2", ::PowerOfTwo)
        addKSynModule(amath, "aMin", ::Minimum)
        addKSynModule(amath, "aMax", ::Maximum)
        addKSynModule(amath, "aRanger", ::RangeConverter)

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
        addKSynModule(effects, "delay", ::InterpolatingDelay)
        addKSynModule(effects, "pan", ::Pan)
        addKSynModule(effects, "plateReverb", ::PlateReverb)
        addKSynModule(effects, "roomReverb", ::RoomReverb)

        val ksynView = getOrCreateBundle(ksyn, "view")
        ksynView.modules.add(ModuleEntry("scope") { ScopeModule() })

        val data = getOrCreateBundle(ksyn, "data")
        addKSynModule(data, "monoRdr", ::VariableRateMonoReader)
        addKSynModule(data, "monoWtr", ::FixedRateMonoWriter)
        data.modules.add(ModuleEntry("segEnv") { SegmentedEnvelopeModule() })
    }

    fun findCreator(typeName: String): (() -> Module)? {
        fun searchBundle(bundle: Bundle): (() -> Module)? {
            bundle.modules.firstOrNull { it.name == typeName }?.let { return it.creator }
            bundle.subBundles.forEach { searchBundle(it)?.let { c -> return c } }
            return null
        }
        return searchBundle(root)
    }

    private fun getOrCreateBundle(parent: Bundle, name: String): Bundle {
        return parent.subBundles.find { it.name == name }
            ?: Bundle(name).also { parent.subBundles.add(it) }
    }

    private fun addKSynModule(
        bundle: Bundle,
        name: String,
        unitFactory: () -> UnitGenerator
    ) {
        bundle.modules.add(ModuleEntry(name) { UnitGeneratorModule(name, unitFactory()) })
    }
}

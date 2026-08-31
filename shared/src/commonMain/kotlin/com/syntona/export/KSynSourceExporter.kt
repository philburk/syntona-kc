package com.syntona.export

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.unitgen.EnvelopeAttackDecay
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PitchToFrequencyModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.UnitGeneratorModule
import com.syntona.dataflow.registry.ModuleRegistry

/**
 * Generates compile-ready Kotlin source code for KSyn from a Syntona PatchModule or PolyPatchModule.
 */
object KSynSourceExporter {

    private data class UnitInfo(
        val varName: String,
        val className: String,
        val constructorArgs: String = "",
        val module: UnitGeneratorModule
    )

    fun export(patch: PatchModule, rawClassName: String, packageName: String = "com.softsynth.ksyn.instruments"): String {
        val className = SourceExporter.sanitizeClassName(rawClassName)
        val isPoly = patch is PolyPatchModule

        // Filter and map unit generator modules
        val unitMap = mutableMapOf<UnitGeneratorModule, UnitInfo>()
        val usedVarNames = mutableSetOf<String>()

        // Find pitchToHz if it was PitchToFrequencyModule or a UnitGeneratorModule
        // In minimalSaw.json, pitchToHz can be a PitchToFrequencyModule or UnitGeneratorModule
        val p2fModules = patch.graphState.modules.filter {
            it is PitchToFrequencyModule || (it is UnitGeneratorModule && it.unit is com.softsynth.ksyn.unitgen.PitchToFrequency)
        }

        for (module in patch.graphState.modules) {
            if (module is UnitGeneratorModule) {
                val baseVar = chooseVarNameForUnit(module)
                val uniqueVar = makeUniqueVar(baseVar, usedVarNames)
                usedVarNames.add(uniqueVar)

                val (unitClassName, constructorArgs) = resolveKSynClassAndArgs(module)
                unitMap[module] = UnitInfo(uniqueVar, unitClassName, constructorArgs, module)
            }
        }

        // Also if any PitchToFrequencyModule is present, we represent it as KSyn's PitchToFrequency() unit
        val p2fVarMap = mutableMapOf<PitchToFrequencyModule, String>()
        for (module in patch.graphState.modules) {
            if (module is PitchToFrequencyModule) {
                val varName = makeUniqueVar("p2f", usedVarNames)
                usedVarNames.add(varName)
                p2fVarMap[module] = varName
            }
        }

        // Voice inputs for PolyPatch
        val voiceInputs = if (isPoly) {
            patch.graphState.modules.filterIsInstance<InputModule>().associateBy { it.name.lowercase() }
        } else {
            emptyMap()
        }

        // Audio inputs & outputs
        val audioInputs = patch.graphState.modules.filterIsInstance<AudioInputModule>()
        val audioOutputs = patch.graphState.modules.filterIsInstance<AudioOutputModule>()

        val sb = StringBuilder()

        // Package and imports
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import com.softsynth.ksyn.ports.UnitInputPort")
        sb.appendLine("import com.softsynth.ksyn.ports.UnitOutputPort")
        sb.appendLine("import com.softsynth.ksyn.shared.time.TimeStamp")
        sb.appendLine("import com.softsynth.ksyn.unitgen.*")
        if (isPoly) {
            sb.appendLine("import com.softsynth.ksyn.voices.PitchedVoice")
            sb.appendLine("import com.softsynth.ksyn.voices.VoiceDescription")
        }
        sb.appendLine()

        // Class documentation
        sb.appendLine("/**")
        sb.appendLine(" * Exported from Syntona patch: ${patch.name}")
        sb.appendLine(" */")

        val inheritance = if (isPoly) ": Circuit(), PitchedVoice" else ": Circuit()"
        sb.appendLine("class $className $inheritance {")

        // Member declarations
        for ((_, info) in unitMap) {
            val args = if (info.constructorArgs.isNotEmpty()) "(${info.constructorArgs})" else "()"
            sb.appendLine("    private val ${info.varName} = ${info.className}$args")
        }
        for ((_, varName) in p2fVarMap) {
            sb.appendLine("    private val $varName = PitchToFrequency()")
        }
        sb.appendLine()

        // Ports
        val exportedPortNames = mutableSetOf<String>()
        if (isPoly) {
            sb.appendLine("    val pitch: UnitInputPort")
            sb.appendLine("    val amplitude: UnitInputPort")
            exportedPortNames.add("pitch")
            exportedPortNames.add("amplitude")
        } else {
            for (inputMod in audioInputs) {
                val propName = SourceExporter.sanitizeIdentifier(inputMod.name)
                sb.appendLine("    val $propName: UnitInputPort")
            }
            for (outputMod in audioOutputs) {
                val propName = SourceExporter.sanitizeIdentifier(outputMod.name)
                sb.appendLine("    val $propName: UnitOutputPort")
            }
        }
        sb.appendLine()

        // init block
        sb.appendLine("    init {")

        // Add units to circuit
        for ((_, info) in unitMap) {
            sb.appendLine("        add(${info.varName})")
        }
        for ((_, varName) in p2fVarMap) {
            sb.appendLine("        add($varName)")
        }
        sb.appendLine()

        // Envelope auto disable
        val envelopes = unitMap.values.filter {
            it.className == "EnvelopeDAHDSR" || it.className == "EnvelopeAttackDecay"
        }
        val mainAmpEnv = envelopes.firstOrNull { it.varName.contains("amp", ignoreCase = true) }
            ?: envelopes.firstOrNull()

        // Wiring connections
        sb.appendLine("        // Wire connections")
        val connections = patch.graphState.connections
        for (conn in connections) {
            val outMod = conn.output.module
            val inMod = conn.input.module

            // Skip ignored modules
            if (outMod is KnobModule || outMod is FaderModule || inMod is KnobModule || inMod is FaderModule) {
                continue
            }

            val source = resolveOutputEndpoint(outMod, conn.output, unitMap, p2fVarMap)
            val dest = resolveInputEndpoint(inMod, conn.input, unitMap, p2fVarMap)

            if (source != null && dest != null) {
                if (source.partIndex == 0 && dest.partIndex == 0) {
                    sb.appendLine("        ${source.varName}.${source.portName}.connect(${dest.varName}.${dest.portName})")
                } else {
                    sb.appendLine("        ${source.varName}.${source.portName}.connect(${source.partIndex}, ${dest.varName}.${dest.portName}, ${dest.partIndex})")
                }
            }
        }
        sb.appendLine()

        // Setup pitch port
        if (isPoly) {
            val p2fUnit = p2fVarMap.values.firstOrNull()
                ?: unitMap.values.firstOrNull { it.className == "PitchToFrequency" }?.varName

            if (p2fUnit != null) {
                sb.appendLine("        pitch = $p2fUnit.input")
            } else {
                // Fallback to first oscillator frequency
                val firstOsc = unitMap.values.firstOrNull { it.className.contains("Oscillator") }
                if (firstOsc != null) {
                    sb.appendLine("        pitch = ${firstOsc.varName}.frequency")
                } else {
                    sb.appendLine("        pitch = UnitInputPort(\"Pitch\")")
                }
            }
            sb.appendLine("        pitch.isValueAdded = true")
            sb.appendLine("        addPort(pitch, \"Pitch\")")
            sb.appendLine()

            // Setup amplitude port
            val ampInputMod = voiceInputs["amplitude"]
            val outgoingFromAmp = if (ampInputMod != null) connections.filter { it.output.module === ampInputMod } else emptyList()
            val ampTarget = outgoingFromAmp.firstOrNull()?.let { resolveInputEndpoint(it.input.module, it.input, unitMap, p2fVarMap) }

            if (ampTarget != null) {
                val accessor = if (ampTarget.partIndex == 0) "${ampTarget.varName}.${ampTarget.portName}" else "${ampTarget.varName}.${ampTarget.portName}[${ampTarget.partIndex}]"
                sb.appendLine("        amplitude = $accessor")
            } else {
                val firstOsc = unitMap.values.firstOrNull { it.className.contains("Oscillator") }
                if (firstOsc != null) {
                    sb.appendLine("        amplitude = ${firstOsc.varName}.amplitude")
                } else if (mainAmpEnv != null) {
                    sb.appendLine("        amplitude = ${mainAmpEnv.varName}.amplitude")
                } else {
                    sb.appendLine("        amplitude = UnitInputPort(\"Amplitude\")")
                }
            }
            sb.appendLine("        addPort(amplitude, \"Amplitude\")")
            sb.appendLine()
        } else {
            // Standard patch audio input/output ports
            for (inputMod in audioInputs) {
                val propName = SourceExporter.sanitizeIdentifier(inputMod.name)
                // Find what connects to inputMod inside the patch
                // inputMod.output connects to something
                val outgoing = connections.filter { it.output.module === inputMod }
                val target = outgoing.firstOrNull()?.let { resolveInputEndpoint(it.input.module, it.input, unitMap, p2fVarMap) }
                if (target != null) {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}[${target.partIndex}]"
                    sb.appendLine("        $propName = $accessor")
                } else {
                    sb.appendLine("        $propName = UnitInputPort(\"${inputMod.name}\")")
                }
                sb.appendLine("        addPort($propName, \"${inputMod.name}\")")
            }
            for (outputMod in audioOutputs) {
                val propName = SourceExporter.sanitizeIdentifier(outputMod.name)
                val incoming = connections.filter { it.input.module === outputMod }
                val source = incoming.firstOrNull()?.let { resolveOutputEndpoint(it.output.module, it.output, unitMap, p2fVarMap) }
                if (source != null) {
                    val accessor = if (source.partIndex == 0) "${source.varName}.${source.portName}" else "${source.varName}.${source.portName}[${source.partIndex}]"
                    sb.appendLine("        $propName = $accessor")
                } else {
                    sb.appendLine("        $propName = UnitOutputPort(\"${outputMod.name}\")")
                }
                sb.appendLine("        addPort($propName, \"${outputMod.name}\")")
            }
            sb.appendLine()
        }

        // Export envelopes
        for (env in envelopes) {
            val prefix = if (env.varName.contains("amp", ignoreCase = true)) "Amp" else "Env"
            sb.appendLine("        ${env.varName}.export(this, \"$prefix\")")
        }
        sb.appendLine()

        // Configure input port settings (setup min/value/max and isValueAdded)
        sb.appendLine("        // Port limits and default values")
        for ((module, info) in unitMap) {
            for (jack in module.inputs) {
                if (jack is AudioInputJack) {
                    val portName = SourceExporter.resolveJackPortName(jack).first
                    val partIndex = jack.partIndex

                    val minStr = SourceExporter.formatDouble(jack.min)
                    val valStr = SourceExporter.formatDouble(jack.getValue())
                    val maxStr = SourceExporter.formatDouble(jack.max)

                    if (partIndex == 0) {
                        sb.appendLine("        ${info.varName}.$portName.setup($minStr, $valStr, $maxStr)")
                        if (jack.isValueAdded) {
                            sb.appendLine("        ${info.varName}.$portName.isValueAdded = true")
                        }
                    } else {
                        sb.appendLine("        ${info.varName}.$portName.set($partIndex, $valStr.toFloat())")
                    }
                }
            }
        }
        sb.appendLine()

        // Auto disable
        if (mainAmpEnv != null) {
            sb.appendLine("        // Turn off circuit when envelope completes to save CPU")
            sb.appendLine("        ${mainAmpEnv.varName}.setupAutoDisable(this)")
            sb.appendLine()
        }

        if (isPoly) {
            sb.appendLine("        usePreset(0)")
        }
        sb.appendLine("    }")
        sb.appendLine()

        // PitchedVoice methods if poly
        if (isPoly) {
            val offInputMod = voiceInputs["off"]
            val offTargets = if (offInputMod != null) {
                connections.filter { it.output.module === offInputMod }
                    .mapNotNull { resolveInputEndpoint(it.input.module, it.input, unitMap, p2fVarMap) }
                    .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }
            } else emptyList()

            sb.appendLine("    override fun noteOff(timeStamp: TimeStamp) {")
            if (offTargets.isNotEmpty()) {
                for (target in offTargets) {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}[${target.partIndex}]"
                    sb.appendLine("        $accessor.off(timeStamp)")
                }
            } else if (mainAmpEnv != null) {
                sb.appendLine("        ${mainAmpEnv.varName}.input.off(timeStamp)")
            }
            sb.appendLine("    }")
            sb.appendLine()

            val onInputMod = voiceInputs["on"]
            val onTargets = if (onInputMod != null) {
                connections.filter { it.output.module === onInputMod }
                    .mapNotNull { resolveInputEndpoint(it.input.module, it.input, unitMap, p2fVarMap) }
                    .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }
            } else emptyList()

            sb.appendLine("    override fun noteOn(pitch: Double, amplitude: Double, timeStamp: TimeStamp) {")
            val p2fUnit = p2fVarMap.values.firstOrNull()
                ?: unitMap.values.firstOrNull { it.className == "PitchToFrequency" }?.varName
            if (p2fUnit != null) {
                sb.appendLine("        $p2fUnit.input.set(pitch, timeStamp)")
            } else {
                sb.appendLine("        this.pitch.set(pitch, timeStamp)")
            }
            sb.appendLine("        this.amplitude.set(amplitude, timeStamp)")
            if (onTargets.isNotEmpty()) {
                for (target in onTargets) {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}[${target.partIndex}]"
                    sb.appendLine("        $accessor.on(timeStamp)")
                }
            } else if (mainAmpEnv != null) {
                sb.appendLine("        ${mainAmpEnv.varName}.input.on(timeStamp)")
            }
            sb.appendLine("    }")
            sb.appendLine()

            // getOutputPort()
            sb.appendLine("    override fun getOutputPort(): UnitOutputPort {")
            val lastOutputMod = audioOutputs.firstOrNull()
            val connToOutput = lastOutputMod?.let { outMod ->
                connections.firstOrNull { it.input.module === outMod }
            }
            val outEndpoint = if (connToOutput != null) {
                resolveOutputEndpoint(connToOutput.output.module, connToOutput.output, unitMap, p2fVarMap)
            } else null

            if (outEndpoint != null) {
                val accessor = if (outEndpoint.partIndex == 0) "${outEndpoint.varName}.${outEndpoint.portName}" else "${outEndpoint.varName}.${outEndpoint.portName}[${outEndpoint.partIndex}]"
                sb.appendLine("        return $accessor")
            } else if (mainAmpEnv != null) {
                sb.appendLine("        return ${mainAmpEnv.varName}.output")
            } else {
                val firstOsc = unitMap.values.firstOrNull { it.className.contains("Oscillator") }
                if (firstOsc != null) {
                    sb.appendLine("        return ${firstOsc.varName}.output")
                } else {
                    sb.appendLine("        return UnitOutputPort(\"output\")")
                }
            }
            sb.appendLine("    }")
            sb.appendLine()

            // getPitchPort()
            sb.appendLine("    override fun getPitchPort(): UnitInputPort? {")
            sb.appendLine("        return pitch")
            sb.appendLine("    }")
            sb.appendLine()

            // usePreset()
            sb.appendLine("    override fun usePreset(presetIndex: Int) {")
            sb.appendLine("        val n = presetIndex % presetNames.size")
            sb.appendLine("        when (n) {")
            sb.appendLine("            0 -> {")
            if (mainAmpEnv != null) {
                sb.appendLine("                ${mainAmpEnv.varName}.attack.set(0.01)")
                sb.appendLine("                ${mainAmpEnv.varName}.decay.set(0.2)")
                sb.appendLine("                ${mainAmpEnv.varName}.release.set(1.0)")
            }
            sb.appendLine("            }")
            sb.appendLine("            1 -> {")
            if (mainAmpEnv != null) {
                sb.appendLine("                ${mainAmpEnv.varName}.attack.set(0.5)")
                sb.appendLine("                ${mainAmpEnv.varName}.decay.set(0.3)")
                sb.appendLine("                ${mainAmpEnv.varName}.release.set(0.2)")
            }
            sb.appendLine("            }")
            sb.appendLine("            else -> {")
            if (mainAmpEnv != null) {
                sb.appendLine("                ${mainAmpEnv.varName}.attack.set(0.1)")
                sb.appendLine("                ${mainAmpEnv.varName}.decay.set(0.3)")
                sb.appendLine("                ${mainAmpEnv.varName}.release.set(0.5)")
            }
            sb.appendLine("            }")
            sb.appendLine("        }")
            sb.appendLine("    }")
            sb.appendLine()

            // Companion object & VoiceDescription
            sb.appendLine("    companion object {")
            sb.appendLine("        val presetNames = arrayOf(\"Default\", \"SlowAttack\", \"FastRelease\")")
            sb.appendLine()
            sb.appendLine("        fun getVoiceDescription(): VoiceDescription {")
            sb.appendLine("            return MyVoiceDescription")
            sb.appendLine("        }")
            sb.appendLine("    }")
            sb.appendLine()
            sb.appendLine("    private object MyVoiceDescription : VoiceDescription(\"${className}Synth\", presetNames) {")
            sb.appendLine("        override val voiceClassName: String")
            sb.appendLine("            get() = \"$packageName.$className\"")
            sb.appendLine()
            sb.appendLine("        private val tags = arrayOf(\"electronic\", \"syntona\")")
            sb.appendLine()
            sb.appendLine("        override fun createPitchedVoice(): PitchedVoice {")
            sb.appendLine("            return $className()")
            sb.appendLine("        }")
            sb.appendLine()
            sb.appendLine("        override fun getTags(presetIndex: Int): Array<String> {")
            sb.appendLine("            return tags")
            sb.appendLine("        }")
            sb.appendLine("    }")
        }

        sb.appendLine("}")
        return sb.toString()
    }

    private fun resolveOutputEndpoint(
        module: com.syntona.dataflow.model.Module,
        outputJack: com.syntona.dataflow.model.OutputJack,
        unitMap: Map<UnitGeneratorModule, UnitInfo>,
        p2fVarMap: Map<PitchToFrequencyModule, String>
    ): SourceExporter.PortEndpoint? {
        if (module is UnitGeneratorModule) {
            val info = unitMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveOutputJackPortName(outputJack)
            return SourceExporter.PortEndpoint(info.varName, portName, partIndex)
        }
        if (module is PitchToFrequencyModule) {
            val varName = p2fVarMap[module] ?: return null
            return SourceExporter.PortEndpoint(varName, "output", 0)
        }
        return null
    }

    private fun resolveInputEndpoint(
        module: com.syntona.dataflow.model.Module,
        inputJack: com.syntona.dataflow.model.InputJack,
        unitMap: Map<UnitGeneratorModule, UnitInfo>,
        p2fVarMap: Map<PitchToFrequencyModule, String>
    ): SourceExporter.PortEndpoint? {
        if (module is UnitGeneratorModule) {
            val info = unitMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveJackPortName(inputJack)
            return SourceExporter.PortEndpoint(info.varName, portName, partIndex)
        }
        if (module is PitchToFrequencyModule) {
            val varName = p2fVarMap[module] ?: return null
            return SourceExporter.PortEndpoint(varName, "input", 0)
        }
        return null
    }

    private fun chooseVarNameForUnit(module: UnitGeneratorModule): String {
        val unit = module.unit
        val className = unit::class.simpleName ?: "unit"
        return when {
            unit is com.softsynth.ksyn.unitgen.PitchToFrequency -> "p2f"
            className.contains("Sawtooth", ignoreCase = true) -> "osc"
            className.contains("Sine", ignoreCase = true) -> "osc"
            className.contains("Square", ignoreCase = true) -> "osc"
            className.contains("Triangle", ignoreCase = true) -> "osc"
            className.contains("Pulse", ignoreCase = true) -> "osc"
            className.contains("Noise", ignoreCase = true) -> "noise"
            unit is EnvelopeDAHDSR -> "ampEnv"
            unit is EnvelopeAttackDecay -> "ampEnv"
            className.contains("Filter", ignoreCase = true) -> "filter"
            unit is com.softsynth.ksyn.unitgen.MixerStereoRamped -> "mixer"
            unit is com.softsynth.ksyn.unitgen.MixerMonoRamped -> "mixer"
            unit is com.softsynth.ksyn.unitgen.Add -> "add"
            unit is com.softsynth.ksyn.unitgen.Subtract -> "sub"
            unit is com.softsynth.ksyn.unitgen.Multiply -> "multiply"
            unit is com.softsynth.ksyn.unitgen.Divide -> "divide"
            else -> SourceExporter.sanitizeIdentifier(module.name)
        }
    }

    private fun makeUniqueVar(base: String, used: Set<String>): String {
        if (!used.contains(base)) return base
        var i = 2
        while (used.contains("$base$i")) {
            i++
        }
        return "$base$i"
    }

    private fun resolveKSynClassAndArgs(module: UnitGeneratorModule): Pair<String, String> {
        val unit = module.unit
        val simpleName = unit::class.simpleName ?: "UnitGenerator"
        return when (unit) {
            is com.softsynth.ksyn.unitgen.MixerStereoRamped -> {
                val numInputs = unit.input.numParts
                Pair("MixerStereoRamped", "$numInputs")
            }
            is com.softsynth.ksyn.unitgen.MixerMonoRamped -> {
                val numInputs = unit.input.numParts
                Pair("MixerMonoRamped", "$numInputs")
            }
            else -> Pair(simpleName, "")
        }
    }
}

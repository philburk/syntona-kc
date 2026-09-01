package com.syntona.export

import com.softsynth.ksyn.unitgen.EnvelopeAttackDecay
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PitchToFrequencyModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.UnitGeneratorModule

/**
 * Generates compile-ready Java source code for JSyn from a Syntona PatchModule or PolyPatchModule.
 */
object JSynSourceExporter {

    private data class UnitInfo(
        val varName: String,
        val className: String,
        val constructorArgs: String = "",
        val module: UnitGeneratorModule
    )

    fun export(patch: PatchModule, rawClassName: String, packageName: String = "com.jsyn.instruments"): String {
        val className = SourceExporter.sanitizeClassName(rawClassName)
        val isPoly = patch is PolyPatchModule

        val unitMap = mutableMapOf<UnitGeneratorModule, UnitInfo>()
        val usedVarNames = mutableSetOf<String>()

        // Check if PitchToFrequency is present (either as PitchToFrequencyModule or UnitGeneratorModule)
        val p2fModules = patch.graphState.modules.filter {
            it is PitchToFrequencyModule || (it is UnitGeneratorModule && it.unit is com.softsynth.ksyn.unitgen.PitchToFrequency)
        }

        for (module in patch.graphState.modules) {
            if (module is UnitGeneratorModule) {
                // In JSyn UnitVoice, we BYPASS PitchToFrequency because JSyn instruments use frequency directly!
                if (isPoly && module.unit is com.softsynth.ksyn.unitgen.PitchToFrequency) {
                    continue
                }
                val baseVar = chooseVarNameForUnit(module)
                val uniqueVar = makeUniqueVar(baseVar, usedVarNames)
                usedVarNames.add(uniqueVar)

                val (unitClassName, constructorArgs) = resolveJSynClassAndArgs(module)
                unitMap[module] = UnitInfo(uniqueVar, unitClassName, constructorArgs, module)
            }
        }

        val voiceInputs = if (isPoly) {
            patch.graphState.modules.filterIsInstance<InputModule>().associateBy { it.name.lowercase() }
        } else {
            emptyMap()
        }

        val audioInputs = patch.graphState.modules.filterIsInstance<AudioInputModule>()
        val audioOutputs = patch.graphState.modules.filterIsInstance<AudioOutputModule>()

        val sb = StringBuilder()

        // Package and imports
        sb.appendLine("package $packageName;")
        sb.appendLine()
        sb.appendLine("import com.jsyn.ports.UnitInputPort;")
        sb.appendLine("import com.jsyn.ports.UnitOutputPort;")
        sb.appendLine("import com.jsyn.unitgen.*;")
        if (isPoly) {
            sb.appendLine("import com.jsyn.util.VoiceDescription;")
            sb.appendLine("import com.softsynth.shared.time.TimeStamp;")
        }
        sb.appendLine()

        sb.appendLine("/**")
        sb.appendLine(" * Exported from Syntona patch: ${patch.name}")
        sb.appendLine(" *")
        sb.appendLine(" * Instead of editing this file, you may want to")
        sb.appendLine(" * modify the Syntona patch and then re-export this source code.")
        sb.appendLine(" */")

        val inheritance = if (isPoly) "extends Circuit implements UnitVoice" else "extends Circuit"
        sb.appendLine("public class $className $inheritance {")

        // Fields
        for ((_, info) in unitMap) {
            sb.appendLine("    private ${info.className} ${info.varName};")
        }
        sb.appendLine()

        // Ports
        if (isPoly) {
            sb.appendLine("    public UnitInputPort frequency;")
            sb.appendLine("    public UnitInputPort amplitude;")
        } else {
            for (inputMod in audioInputs) {
                val propName = SourceExporter.sanitizeIdentifier(inputMod.name)
                sb.appendLine("    public UnitInputPort $propName;")
            }
            for (outputMod in audioOutputs) {
                val propName = SourceExporter.sanitizeIdentifier(outputMod.name)
                sb.appendLine("    public UnitOutputPort $propName;")
            }
        }
        sb.appendLine()

        // Constructor
        sb.appendLine("    public $className() {")

        // Instantiate and add subunits
        for ((_, info) in unitMap) {
            val args = if (info.constructorArgs.isNotEmpty()) info.constructorArgs else ""
            sb.appendLine("        add(${info.varName} = new ${info.className}($args));")
        }
        sb.appendLine()

        val envelopes = unitMap.values.filter {
            it.className == "EnvelopeDAHDSR" || it.className == "EnvelopeAttackDecay"
        }
        val mainAmpEnv = envelopes.firstOrNull { it.varName.contains("amp", ignoreCase = true) }
            ?: envelopes.firstOrNull()

        // Find which oscillator frequency was fed by pitch/p2f in Syntona
        val connections = patch.graphState.connections
        var frequencyTarget: String? = null
        if (isPoly) {
            // Check connections from pitch input or from p2f to an oscillator
            for (p2fMod in p2fModules) {
                // Find connection from p2f to destination
                val fromP2f = connections.filter { it.output.module === p2fMod }
                for (c in fromP2f) {
                    val destMod = c.input.module
                    if (destMod is UnitGeneratorModule && unitMap.containsKey(destMod)) {
                        val destInfo = unitMap[destMod]!!
                        val (portName, partIndex) = SourceExporter.resolveJackPortName(c.input)
                        frequencyTarget = if (partIndex == 0) "${destInfo.varName}.$portName" else "${destInfo.varName}.${portName}.getPart($partIndex)"
                        break
                    }
                }
                if (frequencyTarget != null) break
            }
            // If not found via p2f, check if pitch input directly connected to something
            if (frequencyTarget == null) {
                val pitchInputMod = voiceInputs["pitch"]
                if (pitchInputMod != null) {
                    val fromPitch = connections.filter { it.output.module === pitchInputMod }
                    for (c in fromPitch) {
                        val destMod = c.input.module
                        if (destMod is UnitGeneratorModule && unitMap.containsKey(destMod)) {
                            val destInfo = unitMap[destMod]!!
                            val (portName, partIndex) = SourceExporter.resolveJackPortName(c.input)
                            frequencyTarget = if (partIndex == 0) "${destInfo.varName}.$portName" else "${destInfo.varName}.${portName}.getPart($partIndex)"
                            break
                        }
                    }
                }
            }
            // Fallback: first oscillator's frequency
            if (frequencyTarget == null) {
                val firstOsc = unitMap.values.firstOrNull { it.className.contains("Oscillator") }
                if (firstOsc != null) {
                    frequencyTarget = "${firstOsc.varName}.frequency"
                }
            }
        }

        // Wiring connections
        sb.appendLine("        // Wire connections")
        for (conn in connections) {
            val outMod = conn.output.module
            val inMod = conn.input.module

            // Skip ignored modules
            if (outMod is KnobModule || outMod is FaderModule || inMod is KnobModule || inMod is FaderModule) {
                continue
            }

            // In JSyn voice export, skip connections to/from PitchToFrequency
            if (isPoly && (outMod in p2fModules || inMod in p2fModules)) {
                continue
            }

            val source = resolveOutputEndpoint(outMod, conn.output, unitMap)
            val dest = resolveInputEndpoint(inMod, conn.input, unitMap)

            if (source != null && dest != null) {
                if (source.partIndex == 0 && dest.partIndex == 0) {
                    sb.appendLine("        ${source.varName}.${source.portName}.connect(${dest.varName}.${dest.portName});")
                } else {
                    sb.appendLine("        ${source.varName}.${source.portName}.connect(${source.partIndex}, ${dest.varName}.${dest.portName}, ${dest.partIndex});")
                }
            }
        }
        sb.appendLine()

        // Expose ports
        if (isPoly) {
            val ampInputMod = voiceInputs["amplitude"]
            val outgoingFromAmp = if (ampInputMod != null) connections.filter { it.output.module === ampInputMod } else emptyList()
            val ampTarget = outgoingFromAmp.firstOrNull()?.let { resolveInputEndpoint(it.input.module, it.input, unitMap) }

            val amplTarget = if (ampTarget != null) {
                if (ampTarget.partIndex == 0) "${ampTarget.varName}.${ampTarget.portName}" else "${ampTarget.varName}.${ampTarget.portName}.getPart(${ampTarget.partIndex})"
            } else {
                val firstOsc = unitMap.values.firstOrNull { it.className.contains("Oscillator") }
                if (firstOsc != null) "${firstOsc.varName}.amplitude" else if (mainAmpEnv != null) "${mainAmpEnv.varName}.amplitude" else null
            }

            if (amplTarget != null) {
                sb.appendLine("        addPort(amplitude = $amplTarget, \"Amplitude\");")
            } else {
                sb.appendLine("        addPort(amplitude = new UnitInputPort(\"Amplitude\"));")
            }

            if (frequencyTarget != null) {
                sb.appendLine("        addPort(frequency = $frequencyTarget, \"Frequency\");")
            } else {
                sb.appendLine("        addPort(frequency = new UnitInputPort(\"Frequency\"));")
            }
            sb.appendLine()
        } else {
            for (inputMod in audioInputs) {
                val propName = SourceExporter.sanitizeIdentifier(inputMod.name)
                val outgoing = connections.filter { it.output.module === inputMod }
                val target = outgoing.firstOrNull()?.let { resolveInputEndpoint(it.input.module, it.input, unitMap) }
                if (target != null) {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}.getPart(${target.partIndex})"
                    sb.appendLine("        $propName = $accessor;")
                } else {
                    sb.appendLine("        $propName = new UnitInputPort(\"${inputMod.name}\");")
                }
                sb.appendLine("        addPort($propName, \"${inputMod.name}\");")
            }
            for (outputMod in audioOutputs) {
                val propName = SourceExporter.sanitizeIdentifier(outputMod.name)
                val incoming = connections.filter { it.input.module === outputMod }
                val source = incoming.firstOrNull()?.let { resolveOutputEndpoint(it.output.module, it.output, unitMap) }
                if (source != null) {
                    val accessor = if (source.partIndex == 0) "${source.varName}.${source.portName}" else "${source.varName}.${source.portName}.getPart(${source.partIndex})"
                    sb.appendLine("        $propName = $accessor;")
                } else {
                    sb.appendLine("        $propName = new UnitOutputPort(\"${outputMod.name}\");")
                }
                sb.appendLine("        addPort($propName, \"${outputMod.name}\");")
            }
            sb.appendLine()
        }

        // Export envelopes
        for (env in envelopes) {
            val prefix = if (env.varName.contains("amp", ignoreCase = true)) "Amp" else "Env"
            sb.appendLine("        ${env.varName}.export(this, \"$prefix\");")
        }
        sb.appendLine()

        // Port setup
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
                        sb.appendLine("        ${info.varName}.$portName.setup($minStr, $valStr, $maxStr);")
                        if (jack.isValueAdded) {
                            sb.appendLine("        ${info.varName}.$portName.setValueAdded(true);")
                        }
                    } else {
                        sb.appendLine("        ${info.varName}.$portName.set($partIndex, $valStr);")
                    }
                }
            }
        }
        sb.appendLine()

        // Auto disable
        if (mainAmpEnv != null) {
            sb.appendLine("        // Turn off circuit when envelope completes to save CPU")
            sb.appendLine("        ${mainAmpEnv.varName}.setupAutoDisable(this);")
            sb.appendLine()
        }

        if (isPoly) {
            sb.appendLine("        usePreset(0);")
        }
        sb.appendLine("    }")
        sb.appendLine()

        // UnitVoice methods
        if (isPoly) {
            val offInputMod = voiceInputs["off"]
            val offTargets = if (offInputMod != null) {
                connections.filter { it.output.module === offInputMod }
                    .mapNotNull { resolveInputEndpoint(it.input.module, it.input, unitMap) }
                    .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }
            } else emptyList()

            sb.appendLine("    @Override")
            sb.appendLine("    public void noteOff(TimeStamp timeStamp) {")
            if (offTargets.isNotEmpty()) {
                for (target in offTargets) {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}.getPart(${target.partIndex})"
                    sb.appendLine("        $accessor.off(timeStamp);")
                }
            } else if (mainAmpEnv != null) {
                sb.appendLine("        ${mainAmpEnv.varName}.input.off(timeStamp);")
            }
            sb.appendLine("    }")
            sb.appendLine()

            val onInputMod = voiceInputs["on"]
            val onTargets = if (onInputMod != null) {
                connections.filter { it.output.module === onInputMod }
                    .mapNotNull { resolveInputEndpoint(it.input.module, it.input, unitMap) }
                    .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }
            } else emptyList()

            sb.appendLine("    @Override")
            sb.appendLine("    public void noteOn(double freq, double ampl, TimeStamp timeStamp) {")
            sb.appendLine("        frequency.set(freq, timeStamp);")
            sb.appendLine("        amplitude.set(ampl, timeStamp);")
            if (onTargets.isNotEmpty()) {
                for (target in onTargets) {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}.getPart(${target.partIndex})"
                    sb.appendLine("        $accessor.on(timeStamp);")
                }
            } else if (mainAmpEnv != null) {
                sb.appendLine("        ${mainAmpEnv.varName}.input.on(timeStamp);")
            }
            sb.appendLine("    }")
            sb.appendLine()

            sb.appendLine("    @Override")
            sb.appendLine("    public UnitOutputPort getOutput() {")
            val lastOutputMod = audioOutputs.firstOrNull()
            val connToOutput = lastOutputMod?.let { outMod ->
                connections.firstOrNull { it.input.module === outMod }
            }
            val outEndpoint = if (connToOutput != null) {
                resolveOutputEndpoint(connToOutput.output.module, connToOutput.output, unitMap)
            } else null

            if (outEndpoint != null) {
                val accessor = if (outEndpoint.partIndex == 0) "${outEndpoint.varName}.${outEndpoint.portName}" else "${outEndpoint.varName}.${outEndpoint.portName}.getPart(${outEndpoint.partIndex})"
                sb.appendLine("        return $accessor;")
            } else if (mainAmpEnv != null) {
                sb.appendLine("        return ${mainAmpEnv.varName}.output;")
            } else {
                val firstOsc = unitMap.values.firstOrNull { it.className.contains("Oscillator") }
                if (firstOsc != null) {
                    sb.appendLine("        return ${firstOsc.varName}.output;")
                } else {
                    sb.appendLine("        return new UnitOutputPort(\"output\");")
                }
            }
            sb.appendLine("    }")
            sb.appendLine()

            sb.appendLine("    @Override")
            sb.appendLine("    public void usePreset(int presetIndex) {")
            sb.appendLine("    }")
            sb.appendLine()

            // Preset names and VoiceDescription
            sb.appendLine("    public static final String[] presetNames = {")
            sb.appendLine("        \"Default\"")
            sb.appendLine("    };")
            sb.appendLine()
            sb.appendLine("    public static class MyVoiceDescription extends VoiceDescription {")
            sb.appendLine("        String[] tags = { \"electronic\", \"syntona\" };")
            sb.appendLine()
            sb.appendLine("        public MyVoiceDescription() {")
            sb.appendLine("            super(\"$className\", presetNames);")
            sb.appendLine("        }")
            sb.appendLine()
            sb.appendLine("        @Override")
            sb.appendLine("        public UnitVoice createUnitVoice() {")
            sb.appendLine("            return new $className();")
            sb.appendLine("        }")
            sb.appendLine()
            sb.appendLine("        @Override")
            sb.appendLine("        public String[] getTags(int presetIndex) {")
            sb.appendLine("            return tags;")
            sb.appendLine("        }")
            sb.appendLine()
            sb.appendLine("        @Override")
            sb.appendLine("        public String getVoiceClassName() {")
            sb.appendLine("            return $className.class.getName();")
            sb.appendLine("        }")
            sb.appendLine("    }")
            sb.appendLine()
            sb.appendLine("    public static VoiceDescription getVoiceDescription() {")
            sb.appendLine("        return new MyVoiceDescription();")
            sb.appendLine("    }")
        }

        sb.appendLine("}")
        return sb.toString()
    }

    private fun resolveOutputEndpoint(
        module: com.syntona.dataflow.model.Module,
        outputJack: com.syntona.dataflow.model.OutputJack,
        unitMap: Map<UnitGeneratorModule, UnitInfo>
    ): SourceExporter.PortEndpoint? {
        if (module is UnitGeneratorModule) {
            val info = unitMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveOutputJackPortName(outputJack)
            return SourceExporter.PortEndpoint(info.varName, portName, partIndex)
        }
        return null
    }

    private fun resolveInputEndpoint(
        module: com.syntona.dataflow.model.Module,
        inputJack: com.syntona.dataflow.model.InputJack,
        unitMap: Map<UnitGeneratorModule, UnitInfo>
    ): SourceExporter.PortEndpoint? {
        if (module is UnitGeneratorModule) {
            val info = unitMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveJackPortName(inputJack)
            return SourceExporter.PortEndpoint(info.varName, portName, partIndex)
        }
        return null
    }

    private fun chooseVarNameForUnit(module: UnitGeneratorModule): String {
        val unit = module.unit
        val className = unit::class.simpleName ?: "unit"
        return when {
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

    private fun resolveJSynClassAndArgs(module: UnitGeneratorModule): Pair<String, String> {
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

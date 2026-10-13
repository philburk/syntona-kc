package com.syntona.export

import com.softsynth.ksyn.data.SegmentedEnvelope
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.*
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.AutoDisableVoiceMode
import com.syntona.dataflow.model.supportsAutoDisableVoice
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.AudioOutput2Module
import com.syntona.dataflow.modules.ChebyshevOscModule
import com.syntona.dataflow.modules.EnvSegModule
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
        val module: Module
    )

    fun export(patch: PatchModule, rawClassName: String, packageName: String = "com.softsynth.ksyn.instruments"): String {
        val className = SourceExporter.sanitizeClassName(rawClassName)
        val isPoly = patch is PolyPatchModule

        // Filter and map unit generator modules
        val unitMap = mutableMapOf<UnitGeneratorModule, UnitInfo>()
        val usedVarNames = if (isPoly) mutableSetOf("pitch", "amplitude") else mutableSetOf()
        val isSink = SourceExporter.findSinkInput(patch) != null
        if (isSink) usedVarNames.add(SourceExporter.SINK_INPUT_VAR)

        // Find pitchToHz if it was PitchToFrequencyModule or a UnitGeneratorModule
        val p2fModules = patch.graphState.modules.filter {
            it is PitchToFrequencyModule || (it is UnitGeneratorModule && it.unit is com.softsynth.ksyn.unitgen.PitchToFrequency)
        }

        for (module in patch.graphState.modules) {
            if (module is UnitGeneratorModule) {
                val baseVar = SourceExporter.sanitizeIdentifier(module.name)
                val uniqueVar = SourceExporter.makeUniqueVar(baseVar, usedVarNames)
                usedVarNames.add(uniqueVar)

                val (unitClassName, constructorArgs) = resolveKSynClassAndArgs(module)
                unitMap[module] = UnitInfo(uniqueVar, unitClassName, constructorArgs, module)
            }
        }

        // EnvSegModules
        val envSegModules = patch.graphState.modules.filterIsInstance<EnvSegModule>()
        val envSegMap = mutableMapOf<EnvSegModule, SourceExporter.EnvSegInfo>()
        val envSegUnitInfos = mutableMapOf<EnvSegModule, UnitInfo>()
        for (module in envSegModules) {
            val baseVar = SourceExporter.sanitizeIdentifier(module.name)
            val readerVar = SourceExporter.makeUniqueVar(baseVar, usedVarNames)
            usedVarNames.add(readerVar)

            val baseEnvVar = "${readerVar}Envelope"
            val envVar = SourceExporter.makeUniqueVar(baseEnvVar, usedVarNames)
            usedVarNames.add(envVar)

            val envInfo = SourceExporter.EnvSegInfo(readerVar, envVar, module)
            envSegMap[module] = envInfo
            envSegUnitInfos[module] = UnitInfo(readerVar, "VariableRateMonoReader", "", module)
        }

        // Also if any PitchToFrequencyModule is present, we represent it as KSyn's PitchToFrequency() unit
        val p2fVarMap = mutableMapOf<PitchToFrequencyModule, String>()
        for (module in patch.graphState.modules) {
            if (module is PitchToFrequencyModule) {
                val baseVar = SourceExporter.sanitizeIdentifier(module.name)
                val varName = SourceExporter.makeUniqueVar(baseVar, usedVarNames)
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

        // Audio outputs & custom inputs
        val audioOutputs = patch.graphState.modules.filterIsInstance<AudioOutputModule>()
        val audioOutputs2 = patch.graphState.modules.filterIsInstance<AudioOutput2Module>()
        val isStereoPatch = audioOutputs2.isNotEmpty()

        val customInputs = SourceExporter.resolveCustomAudioInputs(
            patch = patch,
            isPoly = isPoly,
            usedVarNames = usedVarNames,
            resolveTarget = { mod, jack -> resolveInputEndpoint(mod, jack, unitMap, p2fVarMap, envSegMap) }
        )

        // The amplitude port may fan out to several units through a PassThrough.
        val ampInputMod = voiceInputs["amplitude"]
        val amplitudeFanOut = if (isPoly) {
            val ampConns = patch.graphState.connections.filter { it.output.module === ampInputMod }
            val ampRange = ampInputMod?.takeIf { it.hasValue }?.let { Triple(it.defaultMin, it.defaultValue, it.defaultMax) }
            SourceExporter.resolveVoicePortFanOut(ampConns, "amplitude", usedVarNames, ampRange) { mod, jack ->
                resolveInputEndpoint(mod, jack, unitMap, p2fVarMap, envSegMap)
            }
        } else null
        val ampPassThrough = amplitudeFanOut?.passThroughVarName

        val sb = StringBuilder()

        // Package and imports
        sb.appendLine("package $packageName")
        sb.appendLine()
        if (envSegMap.isNotEmpty()) {
            sb.appendLine("import com.softsynth.ksyn.data.SegmentedEnvelope")
        }
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
        sb.appendLine(" *")
        val commentLines = SourceExporter.formatPatchComments(patch)
        if (commentLines.isNotEmpty()) {
            commentLines.forEach { sb.appendLine(it) }
            sb.appendLine(" *")
        }
        sb.appendLine(" * This code was exported from Syntona. Rather then editing this file, you may want to")
        sb.appendLine(" * modify the Syntona patch and then re-export this source code.")
        sb.appendLine(" */")

        val inheritance = when {
            isSink -> ": Circuit(), PitchedVoice, UnitSink"
            isPoly -> ": Circuit(), PitchedVoice"
            else -> ": Circuit()"
        }
        sb.appendLine("class $className $inheritance {")

        // Member declarations
        for ((_, info) in unitMap) {
            val args = if (info.constructorArgs.isNotEmpty()) "(${info.constructorArgs})" else "()"
            sb.appendLine("    private val ${info.varName} = ${info.className}$args")
        }
        for ((_, varName) in p2fVarMap) {
            sb.appendLine("    private val $varName = PitchToFrequency()")
        }
        for ((_, info) in envSegMap) {
            sb.appendLine("    private val ${info.readerVarName} = VariableRateMonoReader()")
        }
        for (info in customInputs) {
            if (info.passThroughVarName != null) {
                sb.appendLine("    private val ${info.passThroughVarName} = PassThrough()")
            }
        }
        if (ampPassThrough != null) {
            sb.appendLine("    private val $ampPassThrough = PassThrough()")
        }
        if (isStereoPatch) {
            sb.appendLine("    private val twoInDualOut = TwoInDualOut()")
        }
        sb.appendLine()

        // Ports
        val exportedPortNames = mutableSetOf<String>()
        if (isPoly) {
            sb.appendLine("    val pitch: UnitInputPort")
            sb.appendLine("    val amplitude: UnitInputPort")
            exportedPortNames.add("pitch")
            exportedPortNames.add("amplitude")
        }
        for (info in customInputs) {
            val declaration = if (info.isSinkInput) "override var" else "val"
            sb.appendLine("    $declaration ${info.varName}: UnitInputPort")
            exportedPortNames.add(info.varName)
        }
        if (!isPoly) {
            for (outputMod in audioOutputs) {
                val propName = SourceExporter.sanitizeIdentifier(outputMod.name)
                sb.appendLine("    val $propName: UnitOutputPort")
            }
            if (isStereoPatch) {
                sb.appendLine("    val output: UnitOutputPort")
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
        for ((_, info) in envSegMap) {
            sb.appendLine("        add(${info.readerVarName})")
        }
        for (info in customInputs) {
            if (info.passThroughVarName != null) {
                sb.appendLine("        add(${info.passThroughVarName})")
            }
        }
        if (ampPassThrough != null) {
            sb.appendLine("        add($ampPassThrough)")
        }
        if (isStereoPatch) {
            sb.appendLine("        add(twoInDualOut)")
        }
        sb.appendLine()

        // Envelope auto disable & main amp env
        val envelopes = unitMap.values.filter {
            it.className == "EnvelopeDAHDSR" || it.className == "EnvelopeAttackDecay"
        }
        val exportedModules = buildMap<Module, UnitInfo> { putAll(unitMap); putAll(envSegUnitInfos) }
        val mainAmpEnv = SourceExporter.findMainAmpEnvelope(
            patch, exportedModules.filterKeys { SourceExporter.isEnvelopeModule(it) }, { it.varName }
        )

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

            val source = resolveOutputEndpoint(outMod, conn.output, unitMap, p2fVarMap, envSegMap)
            val dest = resolveInputEndpoint(inMod, conn.input, unitMap, p2fVarMap, envSegMap)

            if (source != null && dest != null) {
                if (source.partIndex == 0 && dest.partIndex == 0) {
                    sb.appendLine("        ${source.varName}.${source.portName}.connect(${dest.varName}.${dest.portName})")
                } else {
                    sb.appendLine("        ${source.varName}.${source.portName}.connect(${source.partIndex}, ${dest.varName}.${dest.portName}, ${dest.partIndex})")
                }
            }
        }
        val passThroughWiring = customInputs.map { it.passThroughVarName to it.targets } +
            listOfNotNull(amplitudeFanOut?.let { it.passThroughVarName to it.targets })
        for ((passThroughVarName, targets) in passThroughWiring) {
            if (passThroughVarName != null) {
                for (target in targets) {
                    if (target.partIndex == 0) {
                        sb.appendLine("        $passThroughVarName.output.connect(${target.varName}.${target.portName})")
                    } else {
                        sb.appendLine("        $passThroughVarName.output.connect(0, ${target.varName}.${target.portName}, ${target.partIndex})")
                    }
                }
            }
        }
        if (isStereoPatch) {
            val output2Mod = audioOutputs2.first()
            val in0Conns = connections.filter { it.input.module === output2Mod && it.input.name == "in0" }
            for (conn in in0Conns) {
                val source = resolveOutputEndpoint(conn.output.module, conn.output, unitMap, p2fVarMap, envSegMap)
                if (source != null) {
                    if (source.partIndex == 0) {
                        sb.appendLine("        ${source.varName}.${source.portName}.connect(twoInDualOut.inputA)")
                    } else {
                        sb.appendLine("        ${source.varName}.${source.portName}.connect(${source.partIndex}, twoInDualOut.inputA, 0)")
                    }
                }
            }
            val in1Conns = connections.filter { it.input.module === output2Mod && it.input.name == "in1" }
            for (conn in in1Conns) {
                val source = resolveOutputEndpoint(conn.output.module, conn.output, unitMap, p2fVarMap, envSegMap)
                if (source != null) {
                    if (source.partIndex == 0) {
                        sb.appendLine("        ${source.varName}.${source.portName}.connect(twoInDualOut.inputB)")
                    } else {
                        sb.appendLine("        ${source.varName}.${source.portName}.connect(${source.partIndex}, twoInDualOut.inputB, 0)")
                    }
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
            val ampTarget = amplitudeFanOut?.targets?.firstOrNull()

            if (ampPassThrough != null) {
                sb.appendLine("        amplitude = $ampPassThrough.input")
            } else if (ampTarget != null) {
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

            for (info in customInputs) {
                if (info.passThroughVarName != null) {
                    sb.appendLine("        ${info.varName} = ${info.passThroughVarName}.input")
                } else if (info.targets.size == 1) {
                    val target = info.targets[0]
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}[${target.partIndex}]"
                    sb.appendLine("        ${info.varName} = $accessor")
                } else {
                    sb.appendLine("        ${info.varName} = UnitInputPort(\"${info.portName}\")")
                }
                sb.appendLine("        addPort(${info.varName}, \"${info.portName}\")")
            }
            if (customInputs.isNotEmpty()) {
                sb.appendLine()
            }
        } else {
            // Standard patch audio input/output ports
            for (info in customInputs) {
                if (info.passThroughVarName != null) {
                    sb.appendLine("        ${info.varName} = ${info.passThroughVarName}.input")
                } else if (info.targets.size == 1) {
                    val target = info.targets[0]
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}[${target.partIndex}]"
                    sb.appendLine("        ${info.varName} = $accessor")
                } else {
                    sb.appendLine("        ${info.varName} = UnitInputPort(\"${info.portName}\")")
                }
                sb.appendLine("        addPort(${info.varName}, \"${info.portName}\")")
            }
            for (outputMod in audioOutputs) {
                val propName = SourceExporter.sanitizeIdentifier(outputMod.name)
                val incoming = connections.filter { it.input.module === outputMod }
                val source = incoming.firstOrNull()?.let { resolveOutputEndpoint(it.output.module, it.output, unitMap, p2fVarMap, envSegMap) }
                if (source != null) {
                    val accessor = if (source.partIndex == 0) "${source.varName}.${source.portName}" else "${source.varName}.${source.portName}[${source.partIndex}]"
                    sb.appendLine("        $propName = $accessor")
                } else {
                    sb.appendLine("        $propName = UnitOutputPort(\"${outputMod.name}\")")
                }
                sb.appendLine("        addPort($propName, \"${outputMod.name}\")")
            }
            if (isStereoPatch) {
                sb.appendLine("        output = twoInDualOut.output")
                sb.appendLine("        addPort(output, \"output\")")
            }
            sb.appendLine()
        }

        // Export envelopes. A voice only exposes the ports defined by its input modules.
        if (!isPoly) {
            for (env in envelopes) {
                val prefix = if (env.varName.contains("amp", ignoreCase = true)) "Amp" else "Env"
                sb.appendLine("        ${env.varName}.export(this, \"$prefix\")")
            }
            sb.appendLine()
        }

        // Delay lines and grain farms output silence until allocated. A delay's allocate() also
        // resets its delay port's maximum, so this has to come before the port limits below.
        val allocations = unitMap.mapNotNull { (module, info) -> SourceExporter.allocationFor(module)?.let { info to it } }
        if (allocations.isNotEmpty()) {
            sb.appendLine("        // Allocate memory")
            for ((info, amount) in allocations) {
                sb.appendLine("        ${info.varName}.allocate($amount)")
            }
            sb.appendLine()
        }

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
        for ((module, info) in envSegMap) {
            val minRate = SourceExporter.formatDouble(module.speed.min)
            val valRate = SourceExporter.formatDouble(module.speed.getValue())
            val maxRate = SourceExporter.formatDouble(module.speed.max)
            sb.appendLine("        ${info.readerVarName}.rate.setup($minRate, $valRate, $maxRate)")

            val minAmp = SourceExporter.formatDouble(module.amplitude.min)
            val valAmp = SourceExporter.formatDouble(module.amplitude.getValue())
            val maxAmp = SourceExporter.formatDouble(module.amplitude.max)
            sb.appendLine("        ${info.readerVarName}.amplitude.setup($minAmp, $valAmp, $maxAmp)")
        }
        // Settings chosen from a menu rather than a jack, so the port loop above misses them.
        for ((module, info) in unitMap) {
            if (module is ChebyshevOscModule) {
                sb.appendLine("        ${info.varName}.order = ${module.order}")
            }
        }
        // Custom input ports use the range of their input module.
        for (info in customInputs) {
            val (min, value, max) = info.defaultRange ?: continue
            sb.appendLine("        ${info.varName}.setup(${SourceExporter.formatDouble(min)}, ${SourceExporter.formatDouble(value)}, ${SourceExporter.formatDouble(max)})")
        }
        // A PassThrough input has no range of its own, so give it one.
        amplitudeFanOut?.range?.let { (min, value, max) ->
            sb.appendLine("        amplitude.setup(${SourceExporter.formatDouble(min)}, ${SourceExporter.formatDouble(value)}, ${SourceExporter.formatDouble(max)})")
        }
        sb.appendLine()

        // Auto disable
        // Chosen the same way a polyPatch chooses at run time: from every exported module whose
        // unit can switch the voice off.
        val autoDisableEnv = SourceExporter.findMainAmpEnvelope(
            patch, exportedModules.filterKeys { it.supportsAutoDisableVoice }, { it.varName }
        )

        if (autoDisableEnv != null) {
            sb.appendLine("        // Turn off circuit when envelope completes to save CPU")
            sb.appendLine("        ${autoDisableEnv.varName}.setupAutoDisable(this)")
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
            val offUnitTargets = if (offInputMod != null) {
                connections.filter { it.output.module === offInputMod }
                    .mapNotNull { resolveInputEndpoint(it.input.module, it.input, unitMap, p2fVarMap, envSegMap) }
                    .filter { it.portName != "rate" && it.portName != "amplitude" }
                    .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }
            } else emptyList()

            val offEnvSegTargets = if (offInputMod != null) {
                connections.filter { it.output.module === offInputMod && it.input.module is EnvSegModule }
                    .mapNotNull { envSegMap[it.input.module] }
                    .distinctBy { it.readerVarName }
            } else emptyList()

            sb.appendLine("    override fun noteOff(timeStamp: TimeStamp) {")
            var hasOff = false
            for (target in offUnitTargets) {
                val envInfo = envSegMap.values.firstOrNull { it.readerVarName == target.varName }
                if (envInfo != null) {
                    sb.appendLine("        ${envInfo.readerVarName}.dataQueue.queueOff(${envInfo.envelopeVarName}, false, timeStamp)")
                    hasOff = true
                } else {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}[${target.partIndex}]"
                    sb.appendLine("        $accessor.off(timeStamp)")
                    hasOff = true
                }
            }
            for (target in offEnvSegTargets) {
                if (offUnitTargets.none { it.varName == target.readerVarName }) {
                    sb.appendLine("        ${target.readerVarName}.dataQueue.queueOff(${target.envelopeVarName}, false, timeStamp)")
                    hasOff = true
                }
            }
            if (!hasOff) {
                if (mainAmpEnv != null) {
                    val envInfo = envSegMap.values.firstOrNull { it.readerVarName == mainAmpEnv.varName }
                    if (envInfo != null) {
                        sb.appendLine("        ${envInfo.readerVarName}.dataQueue.queueOff(${envInfo.envelopeVarName}, false, timeStamp)")
                    } else {
                        sb.appendLine("        ${mainAmpEnv.varName}.input.off(timeStamp)")
                    }
                } else if (envSegMap.isNotEmpty()) {
                    for (target in envSegMap.values) {
                        sb.appendLine("        ${target.readerVarName}.dataQueue.queueOff(${target.envelopeVarName}, false, timeStamp)")
                    }
                }
            }
            sb.appendLine("    }")
            sb.appendLine()

            val onInputMod = voiceInputs["on"]
            val onUnitTargets = if (onInputMod != null) {
                connections.filter { it.output.module === onInputMod }
                    .mapNotNull { resolveInputEndpoint(it.input.module, it.input, unitMap, p2fVarMap, envSegMap) }
                    .filter { it.portName != "rate" && it.portName != "amplitude" }
                    .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }
            } else emptyList()

            val onEnvSegTargets = if (onInputMod != null) {
                connections.filter { it.output.module === onInputMod && it.input.module is EnvSegModule }
                    .mapNotNull { envSegMap[it.input.module] }
                    .distinctBy { it.readerVarName }
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

            var hasOn = false
            for (target in onUnitTargets) {
                val envInfo = envSegMap.values.firstOrNull { it.readerVarName == target.varName }
                if (envInfo != null) {
                    sb.appendLine("        ${envInfo.readerVarName}.dataQueue.queueOn(${envInfo.envelopeVarName}, timeStamp)")
                    hasOn = true
                } else {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}[${target.partIndex}]"
                    sb.appendLine("        $accessor.on(timeStamp)")
                    hasOn = true
                }
            }
            for (target in onEnvSegTargets) {
                if (onUnitTargets.none { it.varName == target.readerVarName }) {
                    sb.appendLine("        ${target.readerVarName}.dataQueue.queueOn(${target.envelopeVarName}, timeStamp)")
                    hasOn = true
                }
            }
            if (!hasOn) {
                if (mainAmpEnv != null) {
                    val envInfo = envSegMap.values.firstOrNull { it.readerVarName == mainAmpEnv.varName }
                    if (envInfo != null) {
                        sb.appendLine("        ${envInfo.readerVarName}.dataQueue.queueOn(${envInfo.envelopeVarName}, timeStamp)")
                    } else {
                        sb.appendLine("        ${mainAmpEnv.varName}.input.on(timeStamp)")
                    }
                } else if (envSegMap.isNotEmpty()) {
                    for (target in envSegMap.values) {
                        sb.appendLine("        ${target.readerVarName}.dataQueue.queueOn(${target.envelopeVarName}, timeStamp)")
                    }
                }
            }
            sb.appendLine("    }")
            sb.appendLine()

            // getOutputPort()
            sb.appendLine("    override fun getOutputPort(): UnitOutputPort {")
            if (isStereoPatch) {
                sb.appendLine("        return twoInDualOut.output")
            } else {
                val lastOutputMod = audioOutputs.firstOrNull()
                val connToOutput = lastOutputMod?.let { outMod ->
                    connections.firstOrNull { it.input.module === outMod }
                }
                val outEndpoint = if (connToOutput != null) {
                    resolveOutputEndpoint(connToOutput.output.module, connToOutput.output, unitMap, p2fVarMap, envSegMap)
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
            sb.appendLine("    }")
            sb.appendLine()

            // Companion object & VoiceDescription
            sb.appendLine("    companion object {")
            for (info in envSegMap.values) {
                val env = info.module.envelope
                sb.appendLine("        val ${info.envelopeVarName} = SegmentedEnvelope(doubleArrayOf(")
                sb.appendLine(SourceExporter.formatEnvelopeData(env, indent = "            "))
                sb.appendLine("        )).apply {")
                if (env.sustainBegin >= 0) {
                    sb.appendLine("            sustainBegin = ${env.sustainBegin}")
                    sb.appendLine("            sustainEnd = ${env.sustainEnd}")
                }
                if (env.releaseBegin >= 0) {
                    sb.appendLine("            releaseBegin = ${env.releaseBegin}")
                    sb.appendLine("            releaseEnd = ${env.releaseEnd}")
                }
                sb.appendLine("        }")
                sb.appendLine()
            }
            sb.appendLine("        val presetNames = arrayOf(\"Default\")")
            sb.appendLine()
            sb.appendLine("        fun getVoiceDescription(): VoiceDescription {")
            sb.appendLine("            return MyVoiceDescription")
            sb.appendLine("        }")
            sb.appendLine("    }")
            sb.appendLine()
            sb.appendLine("    private object MyVoiceDescription : VoiceDescription(\"$className\", presetNames) {")
            sb.appendLine("        override val voiceClassName: String")
            sb.appendLine("            get() = \"$packageName.$className\"")
            sb.appendLine()
            sb.appendLine("        private val tags = arrayOf(\"electronic\")")
            sb.appendLine()
            sb.appendLine("        override fun createPitchedVoice(): PitchedVoice {")
            sb.appendLine("            return $className()")
            sb.appendLine("        }")
            sb.appendLine()
            sb.appendLine("        override fun getTags(presetIndex: Int): Array<String> {")
            sb.appendLine("            return tags")
            sb.appendLine("        }")
            sb.appendLine("    }")
        } else {
            if (envSegMap.isNotEmpty()) {
                sb.appendLine("    companion object {")
                for (info in envSegMap.values) {
                    val env = info.module.envelope
                    sb.appendLine("        val ${info.envelopeVarName} = SegmentedEnvelope(doubleArrayOf(")
                    sb.appendLine(SourceExporter.formatEnvelopeData(env, indent = "            "))
                    sb.appendLine("        )).apply {")
                    if (env.sustainBegin >= 0) {
                        sb.appendLine("            sustainBegin = ${env.sustainBegin}")
                        sb.appendLine("            sustainEnd = ${env.sustainEnd}")
                    }
                    if (env.releaseBegin >= 0) {
                        sb.appendLine("            releaseBegin = ${env.releaseBegin}")
                        sb.appendLine("            releaseEnd = ${env.releaseEnd}")
                    }
                    sb.appendLine("        }")
                    sb.appendLine()
                }
                sb.appendLine("    }")
            }
        }

        sb.appendLine("}")
        return sb.toString()
    }

    private fun resolveOutputEndpoint(
        module: Module,
        outputJack: OutputJack,
        unitMap: Map<UnitGeneratorModule, UnitInfo>,
        p2fVarMap: Map<PitchToFrequencyModule, String>,
        envSegMap: Map<EnvSegModule, SourceExporter.EnvSegInfo> = emptyMap()
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
        if (module is EnvSegModule) {
            val info = envSegMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveOutputJackPortName(outputJack)
            return SourceExporter.PortEndpoint(info.readerVarName, portName, partIndex)
        }
        return null
    }

    private fun resolveInputEndpoint(
        module: Module,
        inputJack: InputJack,
        unitMap: Map<UnitGeneratorModule, UnitInfo>,
        p2fVarMap: Map<PitchToFrequencyModule, String>,
        envSegMap: Map<EnvSegModule, SourceExporter.EnvSegInfo> = emptyMap()
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
        if (module is EnvSegModule) {
            val info = envSegMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveJackPortName(inputJack)
            return SourceExporter.PortEndpoint(info.readerVarName, portName, partIndex)
        }
        return null
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

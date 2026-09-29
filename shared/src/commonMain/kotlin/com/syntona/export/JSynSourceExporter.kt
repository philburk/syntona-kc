package com.syntona.export

import com.softsynth.ksyn.unitgen.EnvelopeAttackDecay
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.AutoDisableVoiceMode
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.AudioOutput2Module
import com.syntona.dataflow.modules.EnvSegModule
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
        val module: Module
    )

    fun export(patch: PatchModule, rawClassName: String, packageName: String = "com.jsyn.instruments"): String {
        val className = SourceExporter.sanitizeClassName(rawClassName)
        val isPoly = patch is PolyPatchModule

        val unitMap = mutableMapOf<UnitGeneratorModule, UnitInfo>()
        val usedVarNames = if (isPoly) mutableSetOf("frequency", "amplitude") else mutableSetOf()

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
                val baseVar = SourceExporter.sanitizeIdentifier(module.name)
                val uniqueVar = SourceExporter.makeUniqueVar(baseVar, usedVarNames)
                usedVarNames.add(uniqueVar)

                val (unitClassName, constructorArgs) = resolveJSynClassAndArgs(module)
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

        val voiceInputs = if (isPoly) {
            patch.graphState.modules.filterIsInstance<InputModule>().associateBy { it.name.lowercase() }
        } else {
            emptyMap()
        }

        val audioOutputs = patch.graphState.modules.filterIsInstance<AudioOutputModule>()
        val audioOutputs2 = patch.graphState.modules.filterIsInstance<AudioOutput2Module>()
        val isStereoPatch = audioOutputs2.isNotEmpty()

        val customInputs = SourceExporter.resolveCustomAudioInputs(
            patch = patch,
            isPoly = isPoly,
            usedVarNames = usedVarNames,
            resolveTarget = { mod, jack -> resolveInputEndpoint(mod, jack, unitMap, envSegMap) }
        )

        val sb = StringBuilder()

        // Package and imports
        sb.appendLine("package $packageName;")
        sb.appendLine()
        if (envSegMap.isNotEmpty()) {
            sb.appendLine("import com.jsyn.data.SegmentedEnvelope;")
        }
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
        sb.appendLine(" * This code was exported from Syntona. Rather then editing this file, you may want to")
        sb.appendLine(" * modify the Syntona patch and then re-export this source code.")
        sb.appendLine(" */")

        val inheritance = if (isPoly) "extends Circuit implements UnitVoice" else "extends Circuit"
        sb.appendLine("public class $className $inheritance {")

        // Static envelope data definitions
        for (info in envSegMap.values) {
            val env = info.module.envelope
            sb.appendLine("    private static final SegmentedEnvelope ${info.envelopeVarName};")
            sb.appendLine()
            sb.appendLine("    static {")
            sb.appendLine("        double[] ${info.envelopeVarName}Data = {")
            sb.appendLine(SourceExporter.formatEnvelopeData(env, indent = "            "))
            sb.appendLine("        };")
            sb.appendLine("        ${info.envelopeVarName} = new SegmentedEnvelope(${info.envelopeVarName}Data);")
            sb.appendLine("        ${info.envelopeVarName}.setSustainBegin(${env.sustainBegin});")
            sb.appendLine("        ${info.envelopeVarName}.setSustainEnd(${env.sustainEnd});")
            sb.appendLine("        ${info.envelopeVarName}.setReleaseBegin(${env.releaseBegin});")
            sb.appendLine("        ${info.envelopeVarName}.setReleaseEnd(${env.releaseEnd});")
            sb.appendLine("    }")
            sb.appendLine()
        }

        // Fields
        for ((_, info) in unitMap) {
            sb.appendLine("    private ${info.className} ${info.varName};")
        }
        for ((_, info) in envSegMap) {
            sb.appendLine("    private VariableRateMonoReader ${info.readerVarName};")
        }
        for (info in customInputs) {
            if (info.passThroughVarName != null) {
                sb.appendLine("    private PassThrough ${info.passThroughVarName};")
            }
        }
        if (isStereoPatch) {
            sb.appendLine("    private TwoInDualOut twoInDualOut;")
        }
        sb.appendLine()

        // Ports
        if (isPoly) {
            sb.appendLine("    public UnitInputPort frequency;")
            sb.appendLine("    public UnitInputPort amplitude;")
        }
        for (info in customInputs) {
            sb.appendLine("    public UnitInputPort ${info.varName};")
        }
        if (!isPoly) {
            for (outputMod in audioOutputs) {
                val propName = SourceExporter.sanitizeIdentifier(outputMod.name)
                sb.appendLine("    public UnitOutputPort $propName;")
            }
            if (isStereoPatch) {
                sb.appendLine("    public UnitOutputPort output;")
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
        for ((_, info) in envSegMap) {
            sb.appendLine("        add(${info.readerVarName} = new VariableRateMonoReader());")
        }
        for (info in customInputs) {
            if (info.passThroughVarName != null) {
                sb.appendLine("        add(${info.passThroughVarName} = new PassThrough());")
            }
        }
        if (isStereoPatch) {
            sb.appendLine("        add(twoInDualOut = new TwoInDualOut());")
        }
        sb.appendLine()

        val envelopes = unitMap.values.filter {
            it.className == "EnvelopeDAHDSR" || it.className == "EnvelopeAttackDecay"
        }
        val mainAmpEnv = SourceExporter.findMainAmpEnvelope(patch, unitMap, { it.className }, { it.varName }, envSegUnitInfos)

        // Find which oscillator frequency was fed by pitch/p2f in Syntona
        val connections = patch.graphState.connections
        var frequencyTarget: String? = null
        if (isPoly) {
            // Check connections from pitch input or from p2f to an oscillator
            for (p2fMod in p2fModules) {
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

            val source = resolveOutputEndpoint(outMod, conn.output, unitMap, envSegMap)
            val dest = resolveInputEndpoint(inMod, conn.input, unitMap, envSegMap)

            if (source != null && dest != null) {
                if (source.partIndex == 0 && dest.partIndex == 0) {
                    sb.appendLine("        ${source.varName}.${source.portName}.connect(${dest.varName}.${dest.portName});")
                } else {
                    sb.appendLine("        ${source.varName}.${source.portName}.connect(${source.partIndex}, ${dest.varName}.${dest.portName}, ${dest.partIndex});")
                }
            }
        }
        for (info in customInputs) {
            if (info.passThroughVarName != null) {
                for (target in info.targets) {
                    if (target.partIndex == 0) {
                        sb.appendLine("        ${info.passThroughVarName}.output.connect(${target.varName}.${target.portName});")
                    } else {
                        sb.appendLine("        ${info.passThroughVarName}.output.connect(0, ${target.varName}.${target.portName}, ${target.partIndex});")
                    }
                }
            }
        }
        if (isStereoPatch) {
            val output2Mod = audioOutputs2.first()
            val in0Conns = connections.filter { it.input.module === output2Mod && it.input.name == "in0" }
            for (conn in in0Conns) {
                val source = resolveOutputEndpoint(conn.output.module, conn.output, unitMap, envSegMap)
                if (source != null) {
                    if (source.partIndex == 0) {
                        sb.appendLine("        ${source.varName}.${source.portName}.connect(twoInDualOut.inputA);")
                    } else {
                        sb.appendLine("        ${source.varName}.${source.portName}.connect(${source.partIndex}, twoInDualOut.inputA, 0);")
                    }
                }
            }
            val in1Conns = connections.filter { it.input.module === output2Mod && it.input.name == "in1" }
            for (conn in in1Conns) {
                val source = resolveOutputEndpoint(conn.output.module, conn.output, unitMap, envSegMap)
                if (source != null) {
                    if (source.partIndex == 0) {
                        sb.appendLine("        ${source.varName}.${source.portName}.connect(twoInDualOut.inputB);")
                    } else {
                        sb.appendLine("        ${source.varName}.${source.portName}.connect(${source.partIndex}, twoInDualOut.inputB, 0);")
                    }
                }
            }
        }
        sb.appendLine()

        // Expose ports
        if (isPoly) {
            val ampInputMod = voiceInputs["amplitude"]
            val outgoingFromAmp = if (ampInputMod != null) connections.filter { it.output.module === ampInputMod } else emptyList()
            val ampTarget = outgoingFromAmp.firstOrNull()?.let { resolveInputEndpoint(it.input.module, it.input, unitMap, envSegMap) }

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

            for (info in customInputs) {
                if (info.passThroughVarName != null) {
                    sb.appendLine("        addPort(${info.varName} = ${info.passThroughVarName}.input, \"${info.portName}\");")
                } else if (info.targets.size == 1) {
                    val target = info.targets[0]
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}.getPart(${target.partIndex})"
                    sb.appendLine("        addPort(${info.varName} = $accessor, \"${info.portName}\");")
                } else {
                    sb.appendLine("        addPort(${info.varName} = new UnitInputPort(\"${info.portName}\"), \"${info.portName}\");")
                }
            }
            if (customInputs.isNotEmpty()) {
                sb.appendLine()
            }
        } else {
            for (info in customInputs) {
                if (info.passThroughVarName != null) {
                    sb.appendLine("        addPort(${info.varName} = ${info.passThroughVarName}.input, \"${info.portName}\");")
                } else if (info.targets.size == 1) {
                    val target = info.targets[0]
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}.getPart(${target.partIndex})"
                    sb.appendLine("        addPort(${info.varName} = $accessor, \"${info.portName}\");")
                } else {
                    sb.appendLine("        addPort(${info.varName} = new UnitInputPort(\"${info.portName}\"), \"${info.portName}\");")
                }
            }
            for (outputMod in audioOutputs) {
                val propName = SourceExporter.sanitizeIdentifier(outputMod.name)
                val incoming = connections.filter { it.input.module === outputMod }
                val source = incoming.firstOrNull()?.let { resolveOutputEndpoint(it.output.module, it.output, unitMap, envSegMap) }
                if (source != null) {
                    val accessor = if (source.partIndex == 0) "${source.varName}.${source.portName}" else "${source.varName}.${source.portName}.getPart(${source.partIndex})"
                    sb.appendLine("        $propName = $accessor;")
                } else {
                    sb.appendLine("        $propName = new UnitOutputPort(\"${outputMod.name}\");")
                }
                sb.appendLine("        addPort($propName, \"${outputMod.name}\");")
            }
            if (isStereoPatch) {
                sb.appendLine("        output = twoInDualOut.output;")
                sb.appendLine("        addPort(output, \"output\");")
            }
            sb.appendLine()
        }

        // Export envelopes. A voice only exposes the ports defined by its input modules.
        if (!isPoly) {
            for (env in envelopes) {
                val prefix = if (env.varName.contains("amp", ignoreCase = true)) "Amp" else "Env"
                sb.appendLine("        ${env.varName}.export(this, \"$prefix\");")
            }
            sb.appendLine()
        }

        // A delay line outputs silence until it has a buffer. allocate() also resets the delay
        // port's maximum, so it has to come before the port limits below.
        val delayUnits = unitMap.filterKeys { it.unit is com.softsynth.ksyn.unitgen.InterpolatingDelay }.values
        if (delayUnits.isNotEmpty()) {
            sb.appendLine("        // Delay buffers")
            for (info in delayUnits) {
                sb.appendLine("        ${info.varName}.allocate(${com.syntona.dataflow.registry.ModuleRegistry.DELAY_BUFFER_FRAMES});")
            }
            sb.appendLine()
        }

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
        for ((module, info) in envSegMap) {
            val minRate = SourceExporter.formatDouble(module.speed.min)
            val valRate = SourceExporter.formatDouble(module.speed.getValue())
            val maxRate = SourceExporter.formatDouble(module.speed.max)
            sb.appendLine("        ${info.readerVarName}.rate.setup($minRate, $valRate, $maxRate);")

            val minAmp = SourceExporter.formatDouble(module.amplitude.min)
            val valAmp = SourceExporter.formatDouble(module.amplitude.getValue())
            val maxAmp = SourceExporter.formatDouble(module.amplitude.max)
            sb.appendLine("        ${info.readerVarName}.amplitude.setup($minAmp, $valAmp, $maxAmp);")
        }
        // Custom input ports use the range of their input module.
        for (info in customInputs) {
            val (min, value, max) = info.defaultRange ?: continue
            sb.appendLine("        ${info.varName}.setup(${SourceExporter.formatDouble(min)}, ${SourceExporter.formatDouble(value)}, ${SourceExporter.formatDouble(max)});")
        }
        sb.appendLine()

        // Auto disable
        val autoDisableEnv = if (mainAmpEnv != null && (mainAmpEnv.className == "EnvelopeDAHDSR" || mainAmpEnv.className == "EnvelopeAttackDecay")) {
            mainAmpEnv
        } else {
            val explicitYesExists = patch.graphState.modules.any {
                (it is EnvSegModule && it.autoDisableVoice == AutoDisableVoiceMode.YES) ||
                (it is UnitGeneratorModule && it.autoDisableVoice == AutoDisableVoiceMode.YES)
            }
            if (explicitYesExists) {
                null
            } else {
                SourceExporter.findMainAmpEnvelope(
                    patch = patch,
                    unitMap = unitMap,
                    getClassName = { it.className },
                    getVarName = { it.varName }
                )
            }
        }

        if (autoDisableEnv != null && (autoDisableEnv.className == "EnvelopeDAHDSR" || autoDisableEnv.className == "EnvelopeAttackDecay")) {
            sb.appendLine("        // Turn off circuit when envelope completes to save CPU")
            sb.appendLine("        ${autoDisableEnv.varName}.setupAutoDisable(this);")
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
            val offUnitTargets = if (offInputMod != null) {
                connections.filter { it.output.module === offInputMod }
                    .mapNotNull { resolveInputEndpoint(it.input.module, it.input, unitMap, envSegMap) }
                    .filter { it.portName != "rate" && it.portName != "amplitude" }
                    .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }
            } else emptyList()

            val offEnvSegTargets = if (offInputMod != null) {
                connections.filter { it.output.module === offInputMod && it.input.module is EnvSegModule }
                    .mapNotNull { envSegMap[it.input.module] }
                    .distinctBy { it.readerVarName }
            } else emptyList()

            sb.appendLine("    @Override")
            sb.appendLine("    public void noteOff(TimeStamp timeStamp) {")
            var hasOff = false
            for (target in offUnitTargets) {
                val envInfo = envSegMap.values.firstOrNull { it.readerVarName == target.varName }
                if (envInfo != null) {
                    sb.appendLine("        ${envInfo.readerVarName}.dataQueue.queueOff(${envInfo.envelopeVarName}, false, timeStamp);")
                    hasOff = true
                } else {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}.getPart(${target.partIndex})"
                    sb.appendLine("        $accessor.off(timeStamp);")
                    hasOff = true
                }
            }
            for (target in offEnvSegTargets) {
                if (offUnitTargets.none { it.varName == target.readerVarName }) {
                    sb.appendLine("        ${target.readerVarName}.dataQueue.queueOff(${target.envelopeVarName}, false, timeStamp);")
                    hasOff = true
                }
            }
            if (!hasOff) {
                if (mainAmpEnv != null) {
                    val envInfo = envSegMap.values.firstOrNull { it.readerVarName == mainAmpEnv.varName }
                    if (envInfo != null) {
                        sb.appendLine("        ${envInfo.readerVarName}.dataQueue.queueOff(${envInfo.envelopeVarName}, false, timeStamp);")
                    } else {
                        sb.appendLine("        ${mainAmpEnv.varName}.input.off(timeStamp);")
                    }
                } else if (envSegMap.isNotEmpty()) {
                    for (target in envSegMap.values) {
                        sb.appendLine("        ${target.readerVarName}.dataQueue.queueOff(${target.envelopeVarName}, false, timeStamp);")
                    }
                }
            }
            sb.appendLine("    }")
            sb.appendLine()

            val onInputMod = voiceInputs["on"]
            val onUnitTargets = if (onInputMod != null) {
                connections.filter { it.output.module === onInputMod }
                    .mapNotNull { resolveInputEndpoint(it.input.module, it.input, unitMap, envSegMap) }
                    .filter { it.portName != "rate" && it.portName != "amplitude" }
                    .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }
            } else emptyList()

            val onEnvSegTargets = if (onInputMod != null) {
                connections.filter { it.output.module === onInputMod && it.input.module is EnvSegModule }
                    .mapNotNull { envSegMap[it.input.module] }
                    .distinctBy { it.readerVarName }
            } else emptyList()

            sb.appendLine("    @Override")
            sb.appendLine("    public void noteOn(double freq, double ampl, TimeStamp timeStamp) {")
            sb.appendLine("        frequency.set(freq, timeStamp);")
            sb.appendLine("        amplitude.set(ampl, timeStamp);")

            var hasOn = false
            for (target in onUnitTargets) {
                val envInfo = envSegMap.values.firstOrNull { it.readerVarName == target.varName }
                if (envInfo != null) {
                    sb.appendLine("        ${envInfo.readerVarName}.dataQueue.queueOn(${envInfo.envelopeVarName}, timeStamp);")
                    hasOn = true
                } else {
                    val accessor = if (target.partIndex == 0) "${target.varName}.${target.portName}" else "${target.varName}.${target.portName}.getPart(${target.partIndex})"
                    sb.appendLine("        $accessor.on(timeStamp);")
                    hasOn = true
                }
            }
            for (target in onEnvSegTargets) {
                if (onUnitTargets.none { it.varName == target.readerVarName }) {
                    sb.appendLine("        ${target.readerVarName}.dataQueue.queueOn(${target.envelopeVarName}, timeStamp);")
                    hasOn = true
                }
            }
            if (!hasOn) {
                if (mainAmpEnv != null) {
                    val envInfo = envSegMap.values.firstOrNull { it.readerVarName == mainAmpEnv.varName }
                    if (envInfo != null) {
                        sb.appendLine("        ${envInfo.readerVarName}.dataQueue.queueOn(${envInfo.envelopeVarName}, timeStamp);")
                    } else {
                        sb.appendLine("        ${mainAmpEnv.varName}.input.on(timeStamp);")
                    }
                } else if (envSegMap.isNotEmpty()) {
                    for (target in envSegMap.values) {
                        sb.appendLine("        ${target.readerVarName}.dataQueue.queueOn(${target.envelopeVarName}, timeStamp);")
                    }
                }
            }
            sb.appendLine("    }")
            sb.appendLine()

            sb.appendLine("    @Override")
            sb.appendLine("    public UnitOutputPort getOutput() {")
            if (isStereoPatch) {
                sb.appendLine("        return twoInDualOut.output;")
            } else {
                val lastOutputMod = audioOutputs.firstOrNull()
                val connToOutput = lastOutputMod?.let { outMod ->
                    connections.firstOrNull { it.input.module === outMod }
                }
                val outEndpoint = if (connToOutput != null) {
                    resolveOutputEndpoint(connToOutput.output.module, connToOutput.output, unitMap, envSegMap)
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
        module: Module,
        outputJack: OutputJack,
        unitMap: Map<UnitGeneratorModule, UnitInfo>,
        envSegMap: Map<EnvSegModule, SourceExporter.EnvSegInfo> = emptyMap()
    ): SourceExporter.PortEndpoint? {
        if (module is UnitGeneratorModule) {
            val info = unitMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveOutputJackPortName(outputJack)
            return SourceExporter.PortEndpoint(info.varName, portName, partIndex)
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
        envSegMap: Map<EnvSegModule, SourceExporter.EnvSegInfo> = emptyMap()
    ): SourceExporter.PortEndpoint? {
        if (module is UnitGeneratorModule) {
            val info = unitMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveJackPortName(inputJack)
            return SourceExporter.PortEndpoint(info.varName, portName, partIndex)
        }
        if (module is EnvSegModule) {
            val info = envSegMap[module] ?: return null
            val (portName, partIndex) = SourceExporter.resolveJackPortName(inputJack)
            return SourceExporter.PortEndpoint(info.readerVarName, portName, partIndex)
        }
        return null
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

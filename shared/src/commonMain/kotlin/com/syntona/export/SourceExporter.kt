package com.syntona.export

import com.softsynth.ksyn.data.SegmentedEnvelope
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.AutoDisableVoiceMode
import com.syntona.dataflow.model.autoDisableVoiceMode
import com.syntona.dataflow.editor.GraphState.UIConnection
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.AudioOutput2Module
import com.syntona.dataflow.modules.CommentModule
import com.syntona.dataflow.modules.EnvSegModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PitchToFrequencyModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.UnitGeneratorModule

/**
 * Common data structures and helpers for exporting Syntona patches to source code.
 */
object SourceExporter {

    data class PortEndpoint(
        val varName: String,
        val portName: String,
        val partIndex: Int = 0
    )

    data class EnvSegInfo(
        val readerVarName: String,
        val envelopeVarName: String,
        val module: EnvSegModule
    )

    /**
     * An input port on the exported circuit, created from an input~ module, or from a
     * non-standard input module in a PolyPatch. [defaultRange] is (min, value, max),
     * or null if the input module has no default value.
     */
    data class CustomInputInfo(
        val module: Module,
        val varName: String,
        val portName: String,
        val targets: List<PortEndpoint>,
        val passThroughVarName: String?,
        val defaultRange: Triple<Double, Double, Double>? = null,
        /** True if this is the voice's main audio input, which implements UnitSink. */
        val isSinkInput: Boolean = false
    )

    /**
     * A standard voice port, like frequency or amplitude, and the unit ports it drives.
     * When it drives more than one, [passThroughVarName] names a PassThrough that fans it out,
     * and [range] is the (min, value, max) to give the PassThrough input.
     */
    data class VoicePortFanOut(
        val targets: List<PortEndpoint>,
        val passThroughVarName: String?,
        val range: Triple<Double, Double, Double>?
    )

    /**
     * Resolves the unit ports driven by [connections], and allocates a PassThrough variable
     * named after [baseVarName] if there is more than one. The range comes from [defaultRange]
     * or else from the first target jack.
     */
    fun resolveVoicePortFanOut(
        connections: List<UIConnection>,
        baseVarName: String,
        usedVarNames: MutableSet<String>,
        defaultRange: Triple<Double, Double, Double>? = null,
        resolveTarget: (Module, InputJack) -> PortEndpoint?
    ): VoicePortFanOut {
        val resolved = connections.mapNotNull { conn ->
            resolveTarget(conn.input.module, conn.input)?.let { it to conn.input }
        }.distinctBy { (target, _) -> "${target.varName}.${target.portName}[${target.partIndex}]" }
        if (resolved.size <= 1) {
            return VoicePortFanOut(resolved.map { it.first }, null, null)
        }
        val passThroughVar = makeUniqueVar("${baseVarName}PassThrough", usedVarNames)
        usedVarNames.add(passThroughVar)
        val firstJack = resolved.first().second
        val range = defaultRange ?: Triple(firstJack.min, firstJack.getValue(), firstJack.max)
        return VoicePortFanOut(resolved.map { it.first }, passThroughVar, range)
    }

    /** Variable name used for the UnitSink input port of an exported voice. */
    const val SINK_INPUT_VAR = "input"

    private val SINK_INPUT_NAMES = setOf("in", "input")

    /**
     * Returns the input~ module named "in" or "input" in a PolyPatch, or null.
     * The exported voice implements UnitSink using that input.
     */
    fun findSinkInput(patch: PatchModule): AudioInputModule? {
        if (patch !is PolyPatchModule) return null
        return patch.graphState.modules.filterIsInstance<AudioInputModule>()
            .firstOrNull { it.name.trim().lowercase() in SINK_INPUT_NAMES }
    }

    private val STANDARD_VOICE_INPUT_NAMES = setOf("on", "off", "pitch", "amplitude", "pressure")

    fun isStandardVoiceInput(name: String): Boolean =
        name.trim().lowercase() in STANDARD_VOICE_INPUT_NAMES

    fun resolveCustomAudioInputs(
        patch: PatchModule,
        isPoly: Boolean,
        usedVarNames: MutableSet<String>,
        resolveTarget: (Module, InputJack) -> PortEndpoint?
    ): List<CustomInputInfo> {
        val filtered = patch.graphState.modules.filter {
            when (it) {
                is AudioInputModule -> !isPoly || !isStandardVoiceInput(it.name)
                // Control inputs other than on/off/pitch/etc. become extra ports on a voice.
                is InputModule -> isPoly && !isStandardVoiceInput(it.name)
                else -> false
            }
        }

        // The exporters reserve SINK_INPUT_VAR in usedVarNames up front when there is a sink input.
        val sinkInput = findSinkInput(patch)
        val result = mutableListOf<CustomInputInfo>()
        for (module in filtered) {
            val isSink = module === sinkInput
            val varName = if (isSink) {
                SINK_INPUT_VAR
            } else {
                makeUniqueVar(sanitizeIdentifier(module.name), usedVarNames)
            }
            usedVarNames.add(varName)

            val outgoing = patch.graphState.connections.filter { it.output.module === module }
            val targets = outgoing.mapNotNull { resolveTarget(it.input.module, it.input) }
                .distinctBy { "${it.varName}.${it.portName}[${it.partIndex}]" }

            val passThroughVar = if (targets.size > 1) {
                val ptVar = makeUniqueVar("${varName}PassThrough", usedVarNames)
                usedVarNames.add(ptVar)
                ptVar
            } else {
                null
            }

            result.add(
                CustomInputInfo(
                    module = module,
                    varName = varName,
                    // JSyn/KSyn convention: port names are capitalized, e.g. "Pitch".
                    portName = if (isSink) "Input" else module.name.replaceFirstChar { it.uppercaseChar() },
                    targets = targets,
                    passThroughVarName = passThroughVar,
                    defaultRange = when (module) {
                        is AudioInputModule -> if (module.hasValue) Triple(module.defaultMin, module.defaultValue, module.defaultMax) else null
                        is InputModule -> if (module.hasValue) Triple(module.defaultMin, module.defaultValue, module.defaultMax) else null
                        else -> null
                    },
                    isSinkInput = isSink
                )
            )
        }
        return result
    }

    data class ExportValidationResult(
        val isExportable: Boolean,
        val unsupportedModules: List<Module>,
        val ignoredModules: List<Module>,
        val audioModules: List<Module>
    )

    /**
     * Returns the text of the patch's comment modules as lines for the class doc comment,
     * each starting with " * ". Comments are in reading order, top to bottom then left to right,
     * separated by a blank line. Returns an empty list if there are no comments.
     */
    fun formatPatchComments(patch: PatchModule): List<String> {
        val positions = patch.graphState.modulePositions
        val comments = patch.graphState.modules.filterIsInstance<CommentModule>()
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ positions[it]?.y ?: 0f }, { positions[it]?.x ?: 0f }))
        val lines = mutableListOf<String>()
        for (comment in comments) {
            if (lines.isNotEmpty()) lines.add(" *")
            for (line in comment.text.trim().lines()) {
                // A "*/" in the text would end the doc comment early.
                val safe = line.trimEnd().replace("*/", "* /")
                lines.add(if (safe.isEmpty()) " *" else " * $safe")
            }
        }
        return lines
    }

    /**
     * Builds the warning shown before exporting [patch], or null if there is nothing to warn about.
     * Used by both the in-window menu and the desktop menu bar.
     */
    fun exportWarningMessage(patch: PatchModule, forJSyn: Boolean): String? {
        val warnings = mutableListOf<String>()
        if (forJSyn) {
            for (unitName in JSynSourceExporter.findKSynOnlyUnits(patch)) {
                warnings.add("$unitName is not available in JSyn. It is only in KSyn.")
            }
        }
        val unsupported = validatePatch(patch).unsupportedModules
        if (unsupported.isNotEmpty()) {
            val names = unsupported.map { it.name }.distinct().joinToString(", ")
            warnings.add("This patch contains non-audio modules ($names) that cannot be exported as audio units. They will be omitted.")
        }
        if (warnings.isEmpty()) return null
        return "Warning:\n" + warnings.joinToString("\n") + "\nDo you want to proceed?"
    }

    /**
     * Sanitizes a file stem or name into a valid Kotlin/Java class identifier (PascalCase).
     * e.g., "HeavenlyBell.kt" -> "HeavenlyBell", "my-synth_1" -> "MySynth1".
     */
    fun sanitizeClassName(nameOrFilename: String): String {
        val base = nameOrFilename.substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast(".kt")
            .substringBeforeLast(".java")
            .trim()

        if (base.isEmpty()) return "SyntonaVoice"

        val sb = StringBuilder()
        var capitalizeNext = true
        for (ch in base) {
            if (ch.isLetterOrDigit()) {
                if (sb.isEmpty() && ch.isDigit()) {
                    sb.append("Voice")
                }
                sb.append(if (capitalizeNext) ch.uppercaseChar() else ch)
                capitalizeNext = false
            } else {
                capitalizeNext = true
            }
        }
        val result = sb.toString()
        return if (result.isEmpty()) "SyntonaVoice" else result
    }

    private val SYMBOLIC_NAME_PREFIXES = listOf(
        "*+~" to "timesPlus",
        "*+" to "timesPlus",
        "+~" to "plus",
        "*~" to "times",
        "-~" to "minus",
        "/~" to "divide",
        ">~" to "compare",
        "+" to "plus",
        "*" to "times",
        "-" to "minus",
        "/" to "divide",
        ">" to "compare",
    )

    private val RESERVED_KEYWORDS = setOf(
        "abstract", "as", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally",
        "float", "for", "fun", "goto", "if", "implements", "import", "in", "instanceof", "int",
        "interface", "is", "long", "native", "new", "null", "object", "package", "private", "protected",
        "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "true", "try", "typeof", "val", "var", "void", "volatile",
        "when", "while", "yield"
    )

    /**
     * Converts a module or port name into a valid camelCase variable name.
     */
    fun sanitizeIdentifier(name: String): String {
        var trimmed = name.trim()
        if (trimmed.isEmpty()) return "u"

        for ((symbol, word) in SYMBOLIC_NAME_PREFIXES) {
            if (trimmed.startsWith(symbol)) {
                trimmed = word + trimmed.substring(symbol.length)
                break
            }
        }

        val sb = StringBuilder()
        var capitalizeNext = false
        for (ch in trimmed) {
            if (ch.isLetterOrDigit()) {
                if (sb.isEmpty()) {
                    if (ch.isDigit()) sb.append("v")
                    sb.append(ch.lowercaseChar())
                } else {
                    sb.append(if (capitalizeNext) ch.uppercaseChar() else ch)
                }
                capitalizeNext = false
            } else {
                capitalizeNext = true
            }
        }
        val res = sb.toString()
        val finalRes = if (res.isEmpty()) "u" else res
        return if (finalRes in RESERVED_KEYWORDS) "${finalRes}Unit" else finalRes
    }

    /**
     * Ensures an identifier is unique within [used] set.
     * If [base] already exists in [used], appends or increments a numerical suffix (e.g. '2', '3').
     */
    fun makeUniqueVar(base: String, used: Set<String>): String {
        if (!used.contains(base)) return base
        val match = Regex("""^(.*?)(\d+)$""").matchEntire(base)
        val (prefix, startNum) = if (match != null) {
            Pair(match.groupValues[1], match.groupValues[2].toIntOrNull() ?: 2)
        } else {
            Pair(base, 2)
        }
        var i = if (match != null) startNum + 1 else 2
        while (used.contains("$prefix$i")) {
            i++
        }
        return "$prefix$i"
    }

    /**
     * Formats a Double into a clean Kotlin/Java double literal string.
     */
    fun formatDouble(v: Double): String {
        if (v.isNaN()) return "0.0"
        if (v.isInfinite()) return if (v > 0) "Double.MAX_VALUE" else "Double.MIN_VALUE"
        val s = v.toString()
        return if (!s.contains('.') && !s.contains('e') && !s.contains('E')) {
            "$s.0"
        } else {
            val formatted = if (s.contains('.')) {
                val parts = s.split('.')
                if (parts[1].length > 6 && !parts[1].contains('e') && !parts[1].contains('E')) {
                    val rounded = kotlin.math.round(v * 1000000.0) / 1000000.0
                    rounded.toString()
                } else s
            } else s
            if (!formatted.contains('.') && !formatted.contains('e') && !formatted.contains('E')) {
                "$formatted.0"
            } else {
                formatted
            }
        }
    }

    /**
     * Inspects a patch and separates modules into:
     * - audioModules (unit generators and audio I/O)
     * - ignoredModules (knobs and faders)
     * - unsupportedModules (modules that cannot be translated to audio units)
     */
    fun validatePatch(patch: PatchModule): ExportValidationResult {
        val unsupported = mutableListOf<Module>()
        val ignored = mutableListOf<Module>()
        val audio = mutableListOf<Module>()

        val isPoly = patch is PolyPatchModule

        for (module in patch.graphState.modules) {
            when (module) {
                is KnobModule, is FaderModule, is CommentModule -> {
                    ignored.add(module)
                }
                is UnitGeneratorModule, is AudioInputModule, is AudioOutputModule, is AudioOutput2Module, is PitchToFrequencyModule, is EnvSegModule -> {
                    audio.add(module)
                }
                is InputModule -> {
                    if (isPoly) {
                        // In a PolyPatch, InputModules like on, off, pitch, amplitude, pressure
                        // represent standard voice control inputs.
                        audio.add(module)
                    } else {
                        unsupported.add(module)
                    }
                }
                is OutputModule -> {
                    if (isPoly) {
                        audio.add(module)
                    } else {
                        unsupported.add(module)
                    }
                }
                else -> {
                    if (module.name.equals("pitchToHz", ignoreCase = true) ||
                        module.name.equals("p2f", ignoreCase = true) ||
                        module::class.simpleName?.contains("PitchToFrequency", ignoreCase = true) == true
                    ) {
                        audio.add(module)
                    } else {
                        unsupported.add(module)
                    }
                }
            }
        }

        return ExportValidationResult(
            isExportable = audio.isNotEmpty(),
            unsupportedModules = unsupported,
            ignoredModules = ignored,
            audioModules = audio
        )
    }

    /**
     * Reverses Syntona's port name shortening back to standard KSyn/JSyn port names:
     * - "q" -> "Q"
     * - "inA", "inputa", "ina" -> "inputA"
     * - "inB", "inputb", "inb" -> "inputB"
     * - "inC", "inputc", "inc" -> "inputC"
     * - "outA", "outputa" -> "outputA"
     * - "in", "input" -> "input"
     * - "out", "output" -> "output"
     */
    fun normalizeJackPortName(rawName: String): String {
        if (rawName.equals("q", ignoreCase = true)) return "Q"
        if (rawName.equals("speed", ignoreCase = true)) return "rate"

        val inMatch = Regex("^(?i)(?:in|input)([a-zA-Z])$").find(rawName)
        if (inMatch != null) {
            return "input${inMatch.groupValues[1].uppercase()}"
        }

        val outMatch = Regex("^(?i)(?:out|output)([a-zA-Z])$").find(rawName)
        if (outMatch != null) {
            return "output${outMatch.groupValues[1].uppercase()}"
        }

        if (rawName.equals("in", ignoreCase = true) || rawName.equals("input", ignoreCase = true)
            || rawName.equals("on", ignoreCase = true) || rawName.equals("off", ignoreCase = true)) {
            return "input"
        }
        if (rawName.equals("out", ignoreCase = true) || rawName.equals("output", ignoreCase = true)) {
            return "output"
        }

        return rawName.replaceFirstChar { it.lowercase() }
    }

    /**
     * Resolves the variable name and port name for a given [UnitGeneratorModule] jack.
     */
    fun resolveJackPortName(jack: InputJack): Pair<String, Int> {
        val jackName = jack.name
        val cleanName: String
        val partIndex: Int

        // Handle numbered ports e.g. in0, in1, gain0, gain1, out0, out1
        val lastDigits = jackName.takeLastWhile { it.isDigit() }
        if (lastDigits.isNotEmpty()) {
            val baseName = jackName.dropLast(lastDigits.length)
            val index = lastDigits.toIntOrNull() ?: 0
            cleanName = normalizeJackPortName(baseName)
            partIndex = index
        } else {
            cleanName = normalizeJackPortName(jackName)
            partIndex = if (jack is AudioInputJack) jack.partIndex else 0
        }
        return Pair(cleanName, partIndex)
    }

    fun resolveOutputJackPortName(jack: OutputJack): Pair<String, Int> {
        val jackName = jack.name
        val cleanName: String
        val partIndex: Int

        val lastDigits = jackName.takeLastWhile { it.isDigit() }
        if (lastDigits.isNotEmpty()) {
            val baseName = jackName.dropLast(lastDigits.length)
            val index = lastDigits.toIntOrNull() ?: 0
            cleanName = normalizeJackPortName(baseName)
            partIndex = index
        } else {
            cleanName = normalizeJackPortName(jackName)
            partIndex = if (jack is AudioOutputJack) jack.partIndex else 0
        }
        return Pair(cleanName, partIndex)
    }

    /**
     * Formats the duration/value pairs of a [SegmentedEnvelope] as source code frames.
     */
    fun formatEnvelopeData(envelope: SegmentedEnvelope, indent: String = "        "): String {
        val data = DoubleArray(envelope.numFrames * 2)
        envelope.read(data)
        val sb = StringBuilder()
        for (i in 0 until envelope.numFrames) {
            val dur = formatDouble(data[i * 2])
            val value = formatDouble(data[i * 2 + 1])
            if (i > 0) sb.append("\n")
            sb.append("$indent$dur, $value,")
        }
        return sb.toString()
    }

    data class EnvelopeCandidate<T>(
        val module: Module,
        val info: T,
        val distanceToOutput: Int,
        val isConnectedToAmplitudePort: Boolean,
        val nameContainsAmp: Boolean,
        val autoDisableMode: AutoDisableVoiceMode = AutoDisableVoiceMode.AUTO
    )

    /**
     * The argument to allocate() for a unit that makes no sound until it has been allocated,
     * matching what the Syntona module does, or null if the unit needs no allocation.
     */
    fun allocationFor(module: UnitGeneratorModule): Int? = when (module.unit) {
        is com.softsynth.ksyn.unitgen.InterpolatingDelay -> com.syntona.dataflow.registry.ModuleRegistry.DELAY_BUFFER_FRAMES
        is com.softsynth.ksyn.unitgen.GrainFarm -> com.syntona.dataflow.registry.ModuleRegistry.GRAIN_FARM_GRAINS
        else -> null
    }

    /** The modules that can be a patch's main amplitude envelope: envelopes and envSeg. */
    fun isEnvelopeModule(module: Module): Boolean = module is EnvSegModule ||
        (module is UnitGeneratorModule && (
            module.unit is com.softsynth.ksyn.unitgen.EnvelopeDAHDSR ||
                module.unit is com.softsynth.ksyn.unitgen.EnvelopeAttackDecay ||
                module.unit is com.softsynth.ksyn.unitgen.VariableRateMonoReader
            ))

    /**
     * Identifies the primary amplitude envelope in a patch, or the module that should switch a
     * voice off, depending on which [candidates] are passed.
     * Traces backwards from AudioOutputModule to find the candidate most closely connected
     * to the audio output path (explicit YES setting, amplitude port connection, "amp" naming, or closest distance).
     */
    fun <T> findMainAmpEnvelope(
        patch: PatchModule,
        candidates: Map<out Module, T>,
        getVarName: (T) -> String
    ): T? {
        val envelopeEntries: Map<Module, T> = candidates.toMap()

        if (envelopeEntries.isEmpty()) return null

        val audioOutputs = patch.graphState.modules.filter { it is AudioOutputModule || it is AudioOutput2Module }
        val envelopeDistances = mutableMapOf<Module, Int>()
        val connectsToAmpPort = mutableSetOf<Module>()

        if (audioOutputs.isNotEmpty()) {
            val queue = ArrayDeque<Pair<Module, Int>>()
            val visited = mutableSetOf<Module>()
            for (outMod in audioOutputs) {
                queue.add(Pair(outMod, 0))
                visited.add(outMod)
            }

            while (queue.isNotEmpty()) {
                val (currMod, dist) = queue.removeFirst()

                if (currMod in envelopeEntries) {
                    if (!envelopeDistances.containsKey(currMod)) {
                        envelopeDistances[currMod] = dist
                    }
                }

                // Check incoming connections to currMod
                val incoming = patch.graphState.connections.filter { it.input.module === currMod }
                for (conn in incoming) {
                    val upstreamMod = conn.output.module
                    // If upstream is an envelope, check if it connects to an amplitude-related port, multiplier, or output
                    if (upstreamMod in envelopeEntries) {
                        val inputPortName = conn.input.name.lowercase()
                        if (conn.input.module is AudioOutputModule || conn.input.module is AudioOutput2Module ||
                            inputPortName.contains("amp") ||
                            inputPortName == "ina" || inputPortName == "inb" ||
                            inputPortName == "inputa" || inputPortName == "inputb"
                        ) {
                            connectsToAmpPort.add(upstreamMod)
                        }
                    }
                    if (upstreamMod !in visited) {
                        visited.add(upstreamMod)
                        queue.add(Pair(upstreamMod, dist + 1))
                    }
                }
            }
        }

        val candidates = envelopeEntries.map { (mod, info) ->
            val dist = envelopeDistances[mod] ?: Int.MAX_VALUE
            val toAmpPort = connectsToAmpPort.contains(mod)
            val nameHasAmp = getVarName(info).contains("amp", ignoreCase = true)
            val mode = mod.autoDisableVoiceMode ?: AutoDisableVoiceMode.AUTO
            EnvelopeCandidate(mod, info, dist, toAmpPort, nameHasAmp, mode)
        }

        // 1. Any candidate explicitly marked YES takes highest precedence
        val yesCandidates = candidates.filter { it.autoDisableMode == AutoDisableVoiceMode.YES }
        if (yesCandidates.isNotEmpty()) {
            val bestYes = yesCandidates.minWithOrNull(
                compareByDescending<EnvelopeCandidate<T>> { it.isConnectedToAmplitudePort }
                    .thenByDescending { it.nameContainsAmp }
                    .thenBy { it.distanceToOutput }
            )
            return bestYes?.info
        }

        // 2. Filter out any candidate explicitly marked NO
        val eligibleCandidates = candidates.filter { it.autoDisableMode != AutoDisableVoiceMode.NO }
        if (eligibleCandidates.isEmpty()) {
            return null
        }

        // 3. Select best among remaining candidates
        val bestCandidate = eligibleCandidates.minWithOrNull(
            compareByDescending<EnvelopeCandidate<T>> { it.isConnectedToAmplitudePort }
                .thenByDescending { it.nameContainsAmp }
                .thenBy { it.distanceToOutput }
        )

        return bestCandidate?.info ?: eligibleCandidates.first().info
    }
}

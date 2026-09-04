package com.syntona.export

import com.softsynth.ksyn.data.SegmentedEnvelope
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
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

    data class ExportValidationResult(
        val isExportable: Boolean,
        val unsupportedModules: List<Module>,
        val ignoredModules: List<Module>,
        val audioModules: List<Module>
    )

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
                is KnobModule, is FaderModule -> {
                    ignored.add(module)
                }
                is UnitGeneratorModule, is AudioInputModule, is AudioOutputModule, is PitchToFrequencyModule, is EnvSegModule -> {
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
        val nameContainsAmp: Boolean
    )

    /**
     * Identifies the primary amplitude envelope in a patch.
     * Traces backwards from AudioOutputModule to find the envelope most closely connected
     * to the audio output path (closest distance, amplitude port connection, or "amp" naming).
     */
    fun <T> findMainAmpEnvelope(
        patch: PatchModule,
        unitMap: Map<UnitGeneratorModule, T>,
        getClassName: (T) -> String,
        getVarName: (T) -> String,
        envSegMap: Map<EnvSegModule, T> = emptyMap()
    ): T? {
        val envelopeEntries = mutableMapOf<Module, T>()
        for ((mod, info) in unitMap) {
            val cls = getClassName(info)
            if (cls == "EnvelopeDAHDSR" || cls == "EnvelopeAttackDecay" || cls == "VariableRateMonoReader") {
                envelopeEntries[mod] = info
            }
        }
        for ((mod, info) in envSegMap) {
            envelopeEntries[mod] = info
        }

        if (envelopeEntries.isEmpty()) return null
        if (envelopeEntries.size == 1) return envelopeEntries.values.first()

        val audioOutputs = patch.graphState.modules.filterIsInstance<AudioOutputModule>()
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
                    // If upstream is an envelope, check if it connects to an amplitude-related port or multiplier
                    if (upstreamMod in envelopeEntries) {
                        val inputPortName = conn.input.name.lowercase()
                        if (inputPortName.contains("amp") || inputPortName == "ina" || inputPortName == "inb" || inputPortName == "inputa" || inputPortName == "inputb") {
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
            EnvelopeCandidate(mod, info, dist, toAmpPort, nameHasAmp)
        }

        val bestCandidate = candidates.minWithOrNull(
            compareBy<EnvelopeCandidate<T>> { it.distanceToOutput }
                .thenByDescending { it.isConnectedToAmplitudePort }
                .thenByDescending { it.nameContainsAmp }
        )

        return bestCandidate?.info ?: envelopeEntries.values.first()
    }
}

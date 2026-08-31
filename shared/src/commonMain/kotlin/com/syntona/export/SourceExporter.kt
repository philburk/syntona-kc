package com.syntona.export

import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack
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

/**
 * Common data structures and helpers for exporting Syntona patches to source code.
 */
object SourceExporter {

    data class PortEndpoint(
        val varName: String,
        val portName: String,
        val partIndex: Int = 0
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

    /**
     * Converts a module or port name into a valid camelCase variable name.
     */
    fun sanitizeIdentifier(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "u"
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
        return if (res.isEmpty()) "u" else res
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
                is UnitGeneratorModule, is AudioInputModule, is AudioOutputModule, is PitchToFrequencyModule -> {
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

        val inMatch = Regex("^(?i)(?:in|input)([a-zA-Z])$").find(rawName)
        if (inMatch != null) {
            return "input${inMatch.groupValues[1].uppercase()}"
        }

        val outMatch = Regex("^(?i)(?:out|output)([a-zA-Z])$").find(rawName)
        if (outMatch != null) {
            return "output${outMatch.groupValues[1].uppercase()}"
        }

        if (rawName.equals("in", ignoreCase = true) || rawName.equals("input", ignoreCase = true)) {
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
}

package com.syntona.dataflow.editor

import com.syntona.dataflow.editor.FileState
import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.CommentModule
import com.syntona.dataflow.modules.KeyboardModule
import com.syntona.dataflow.modules.ScopeModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.SegmentedEnvelopeModule
import com.syntona.dataflow.modules.LinkModule
import com.syntona.dataflow.modules.KeyMatchModule
import com.syntona.dataflow.modules.KeyNotesModule
import com.syntona.dataflow.modules.PianoRollModule
import com.syntona.dataflow.modules.PianoRollNote
import com.syntona.dataflow.modules.Ratio
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.SequenceModule
import com.syntona.dataflow.modules.EquationModule
import com.syntona.dataflow.modules.ScaleModule
import com.softsynth.ksyn.data.SegmentedEnvelope
import com.syntona.dataflow.registry.ModuleRegistry

/** Position/size (in dp) of the root patch's own MDI window, which has no module entry of its own. */
data class RootWindowGeometry(val x: Float?, val y: Float?, val width: Float?, val height: Float?)

object GraphSerializer {

    fun serialize(
        graphState: GraphState,
        modules: List<Module>,
        density: Float = 1f,
        rootWindowX: Float? = null,
        rootWindowY: Float? = null,
        rootWindowWidth: Float? = null,
        rootWindowHeight: Float? = null
    ): String {
        val raw = buildRawJson(graphState, modules, density)
        if (rootWindowX == null && rootWindowY == null && rootWindowWidth == null && rootWindowHeight == null) {
            return prettyPrintJson(raw)
        }
        val parsedRoot = try { JsonParser(raw).parse() as? JsonValue.Obj } catch (_: Exception) { null }
            ?: return prettyPrintJson(raw)
        val merged = LinkedHashMap(parsedRoot.map)
        rootWindowX?.let { merged["windowX"] = JsonValue.Num(it.toDouble()) }
        rootWindowY?.let { merged["windowY"] = JsonValue.Num(it.toDouble()) }
        rootWindowWidth?.let { merged["windowWidth"] = JsonValue.Num(it.toDouble()) }
        rootWindowHeight?.let { merged["windowHeight"] = JsonValue.Num(it.toDouble()) }
        return JsonValue.Obj(merged).stringify(0)
    }

    /** Reads the root patch window's saved geometry, if any, without touching module data. */
    fun readRootWindowGeometry(json: String): RootWindowGeometry? {
        val root = try { JsonParser(json).parse() as? JsonValue.Obj } catch (_: Exception) { null } ?: return null
        val x = (root.map["windowX"] as? JsonValue.Num)?.value?.toFloat()
        val y = (root.map["windowY"] as? JsonValue.Num)?.value?.toFloat()
        val w = (root.map["windowWidth"] as? JsonValue.Num)?.value?.toFloat()
        val h = (root.map["windowHeight"] as? JsonValue.Num)?.value?.toFloat()
        if (x == null && y == null && w == null && h == null) return null
        return RootWindowGeometry(x, y, w, h)
    }



    fun serializePatch(patch: PatchModule, density: Float = 1f): String {
        val raw = buildRawJson(patch.graphState, patch.graphState.modules.toList(), density)
        val parsed = try { JsonParser(raw).parse() as? JsonValue.Obj } catch (_: Exception) { null }
            ?: return raw
        val merged = LinkedHashMap<String, JsonValue>()
        val resolvedType = ModuleRegistry.getBundlePath(patch.type) ?: patch.type
        merged["type"] = JsonValue.Str(resolvedType)
        if (patch is PolyPatchModule) {
            merged["maxVoices"] = JsonValue.Num(patch.maxVoices.toDouble())
        }
        merged["patch"] = parsed
        return JsonValue.Obj(merged).stringify(0)
    }

    // Builds compact JSON. Recursive PatchModule calls stay on this raw path so only the
    // outermost serialize() call pays for the parse+pretty-print pass.
    private fun buildRawJson(graphState: GraphState, modules: List<Module>, density: Float): String = buildString {
        val internalConns = graphState.connections.filter { conn ->
            modules.any { it === conn.output.module } &&
            modules.any { it === conn.input.module }
        }
        append("{\"modules\":[")
        modules.forEachIndexed { i, module ->
            if (i > 0) append(",")
            val pos = graphState.modulePositions[module] ?: Offset.Zero
            val xDp = pos.x
            val yDp = pos.y
            val resolvedType = ModuleRegistry.getBundlePath(module.type) ?: module.type
            append("{\"id\":$i,\"type\":\"${resolvedType.jsonEscape}\",\"name\":\"${module.name.jsonEscape}\"")
            append(",\"x\":$xDp,\"y\":$yDp,\"jacks\":{")
            val valueJacks = module.inputs.filter { jack ->
                jack !is CommandJack && !jack.isKickable &&
                (jack.lastMessage is Message.FloatValue || jack is AudioInputJack ||
                 ((module is KnobModule || module is FaderModule) && jack.name == "in") ||
                 (module is SequenceModule && jack.name.startsWith("in") && jack.name.substring(2).toIntOrNull() != null))
            }
            valueJacks.forEachIndexed { j, jack ->
                if (j > 0) append(",")
                append("\"${jack.name.jsonEscape}\":{\"v\":${jack.getValue()},\"min\":${jack.min},\"max\":${jack.max}")
                if (jack is AudioInputJack) {
                    append(",\"add\":${jack.isValueAdded}")
                }
                append("}")
            }
            append("}")
            if (module is CommentModule) {
                append(",\"text\":\"${module.text.jsonEscape}\"")
                append(",\"w\":${module.width},\"h\":${module.height}")
            }
            if (module is EquationModule) {
                append(",\"equation\":\"${module.equation.jsonEscape}\"")
            }
            if (module is ScaleModule) {
                append(",\"scale\":\"${module.scaleString.jsonEscape}\"")
            }
            if (module is KeyboardModule) {
                append(",\"w\":${module.width},\"h\":${module.height}")
            }
            if (module is ScopeModule) {
                append(",\"w\":${module.width},\"h\":${module.height}")
            }
            if (module is LinkModule) {
                append(",\"filePath\":\"${module.filePath.jsonEscape}\"")
                append(",\"buttonText\":\"${module.buttonText.jsonEscape}\"")
            }
            if (module is KeyMatchModule) {
                append(",\"keyChar\":\"${module.keyChar.toString().jsonEscape}\"")
            }
            if (module is KeyNotesModule) {
                append(",\"bottomOctaveKeys\":\"${module.bottomOctaveKeys.jsonEscape}\"")
                append(",\"topOctaveKeys\":\"${module.topOctaveKeys.jsonEscape}\"")
                append(",\"startingNote\":${module.startingNote}")
            }
            if (module is PianoRollModule) {
                append(",\"bpm\":${module.bpm}")
                append(",\"playStart\":${module.playStartTicks}")
                module.windowX?.let { append(",\"windowX\":$it") }
                module.windowY?.let { append(",\"windowY\":$it") }
                module.windowWidth?.let { append(",\"windowWidth\":$it") }
                module.windowHeight?.let { append(",\"windowHeight\":$it") }
                append(",\"notes\":[")
                module.notes.forEachIndexed { j, n ->
                    if (j > 0) append(",")
                    append("[${n.id},${n.startTicks},${n.durationTicks},${n.parentId},${n.ratio.n},${n.ratio.d},${n.rootPitch}]")
                }
                append("]")
            }
            if (module is SegmentedEnvelopeModule) {
                val env = module.envelope
                val data = DoubleArray(env.numFrames * 2)
                env.read(data)
                val dataJson = data.joinToString(prefix = "[", postfix = "]") { it.toString() }
                append(",\"envData\":$dataJson")
                append(",\"sustainBegin\":${env.sustainBegin}")
                append(",\"sustainEnd\":${env.sustainEnd}")
                append(",\"releaseBegin\":${env.releaseBegin}")
                append(",\"releaseEnd\":${env.releaseEnd}")
            }
            if (module is PolyPatchModule) {
                append(",\"maxVoices\":${module.maxVoices}")
            }
            // Recursively embed patch contents so jacks are restored on paste/load
            if (module is PatchModule) {
                module.windowX?.let { append(",\"windowX\":$it") }
                module.windowY?.let { append(",\"windowY\":$it") }
                module.windowWidth?.let { append(",\"windowWidth\":$it") }
                module.windowHeight?.let { append(",\"windowHeight\":$it") }
                append(",\"windowOpen\":${module.isWindowOpen}")
                val path = module.filePath
                if (path != null && !module.isModified) {
                    append(",\"filePath\":\"${path.jsonEscape}\"")
                } else {
                    if (path != null) {
                        append(",\"filePath\":\"${path.jsonEscape}\"")
                    }
                    append(",\"patch\":")
                    append(buildRawJson(module.graphState, module.graphState.modules.toList(), density))
                }
            }
            append("}")
        }
        append("],\"connections\":[")
        internalConns.forEachIndexed { i, conn ->
            if (i > 0) append(",")
            val fromIdx = modules.indexOfFirst { it === conn.output.module }
            val toIdx = modules.indexOfFirst { it === conn.input.module }
            append("{\"from\":$fromIdx,\"fromJack\":\"${conn.output.name.jsonEscape}\"")
            append(",\"to\":$toIdx,\"toJack\":\"${conn.input.name.jsonEscape}\"}")
        }
        append("]}")
    }

    private fun prettyPrintJson(json: String): String {
        val root = try { JsonParser(json).parse() } catch (_: Exception) { return json }
        return root.stringify(0)
    }

    fun deserialize(graphState: GraphState, json: String, offset: Offset = Offset.Zero, density: Float = 1f): List<Module> {
        val problems = mutableListOf<String>()
        val rootVal = try {
            JsonParser(json).parse() as? JsonValue.Obj
        } catch (e: Exception) {
            problems.add("Failed to parse JSON: ${e.message}")
            null
        } ?: return emptyList()
        val type = (rootVal.map["type"] as? JsonValue.Str)?.value
        val finalType = type?.substringAfterLast('.')
        val result = if (finalType == "patch" || finalType == "polyPatch") {
            val innerPatchObj = rootVal.map["patch"] as? JsonValue.Obj
            if (innerPatchObj != null) {
                deserializeObj(graphState, innerPatchObj, offset, density, problems)
            } else {
                deserializeObj(graphState, rootVal, offset, density, problems)
            }
        } else {
            deserializeObj(graphState, rootVal, offset, density, problems)
        }
        if (problems.isNotEmpty()) {
            FileState.loadingProblemsReport = problems.joinToString("\n")
        }
        return result
    }

    private fun deserializeObj(
        graphState: GraphState,
        rootObj: JsonValue.Obj,
        offset: Offset,
        density: Float = 1f,
        problems: MutableList<String>
    ): List<Module> {
        graphState.isDeserializing = true
        try {
            val modulesArr = (rootObj.map["modules"] as? JsonValue.Arr)?.items ?: return emptyList()
            val connectionsArr = (rootObj.map["connections"] as? JsonValue.Arr)?.items ?: return emptyList()

            val created = mutableListOf<Module?>()
            val succeeded = mutableListOf<Module>()

        for (item in modulesArr) {
            val obj = item as? JsonValue.Obj
            val rawTypeName = (obj?.map?.get("type") as? JsonValue.Str)?.value
            val typeName = when (rawTypeName) {
                "showVoice" -> "showMany"
                "double" -> "number"
                else -> rawTypeName
            }
            val x = (obj?.map?.get("x") as? JsonValue.Num)?.value?.toFloat() ?: 100f
            val y = (obj?.map?.get("y") as? JsonValue.Num)?.value?.toFloat() ?: 100f
            val jacks = obj?.map?.get("jacks") as? JsonValue.Obj

            val creator = typeName?.let { ModuleRegistry.findCreator(it) }
            if (creator == null) {
                problems.add("Module type '$typeName' not found in registry (name: '${obj?.map?.get("name") ?: "unknown"}').")
                created.add(null)
                continue
            }

            val module = creator()
            (obj?.map?.get("name") as? JsonValue.Str)?.value?.let { module.name = it }
            if (module is CommentModule) {
                (obj?.map?.get("text") as? JsonValue.Str)?.value?.let { module.text = it }
                (obj?.map?.get("w") as? JsonValue.Num)?.value?.let { module.width = it.toFloat() }
                (obj?.map?.get("h") as? JsonValue.Num)?.value?.let { module.height = it.toFloat() }
            }
            if (module is EquationModule) {
                (obj?.map?.get("equation") as? JsonValue.Str)?.value?.let { module.compile(it) }
            }
            if (module is ScaleModule) {
                (obj?.map?.get("scale") as? JsonValue.Str)?.value?.let { module.updateScale(it) }
            }
            if (module is KeyboardModule) {
                (obj?.map?.get("w") as? JsonValue.Num)?.value?.let { module.width = it.toFloat() }
                (obj?.map?.get("h") as? JsonValue.Num)?.value?.let { module.height = it.toFloat() }
            }
            if (module is ScopeModule) {
                (obj?.map?.get("w") as? JsonValue.Num)?.value?.let { module.width = it.toFloat() }
                (obj?.map?.get("h") as? JsonValue.Num)?.value?.let { module.height = it.toFloat() }
            }
            if (module is LinkModule) {
                (obj?.map?.get("filePath") as? JsonValue.Str)?.value?.let { module.filePath = it }
                (obj?.map?.get("buttonText") as? JsonValue.Str)?.value?.let { module.buttonText = it }
            }
            if (module is KeyMatchModule) {
                (obj?.map?.get("keyChar") as? JsonValue.Str)?.value?.firstOrNull()?.let { module.keyChar = it }
            }
            if (module is KeyNotesModule) {
                (obj?.map?.get("bottomOctaveKeys") as? JsonValue.Str)?.value?.let { module.bottomOctaveKeys = it }
                (obj?.map?.get("topOctaveKeys") as? JsonValue.Str)?.value?.let { module.topOctaveKeys = it }
                (obj?.map?.get("mappingString") as? JsonValue.Str)?.value?.let { oldMap ->
                    val commaIdx = oldMap.indexOf(',')
                    if (commaIdx >= 0) {
                        module.bottomOctaveKeys = oldMap.substring(0, commaIdx + 1)
                        module.topOctaveKeys = oldMap.substring(commaIdx + 1)
                    } else {
                        module.bottomOctaveKeys = oldMap.take(13)
                        module.topOctaveKeys = oldMap.drop(13)
                    }
                }
                (obj?.map?.get("startingNote") as? JsonValue.Num)?.value?.toInt()?.let { module.startingNote = it }
            }

            if (module is PolyPatchModule) {
                val maxV = (obj?.map?.get("maxVoices") as? JsonValue.Num)?.value?.toInt()
                if (maxV != null) {
                    module.initializeVoices(maxV)
                }
            }

            // x/y are stored as dp; store directly in GraphState
            graphState.addModule(module, Offset(x + offset.x, y + offset.y))
            jacks?.map?.forEach { (jackName, jackValue) ->
                val inputJack = module.inputs.find { it.name == jackName } as? InputJack ?: return@forEach
                when (jackValue) {
                    is JsonValue.Num -> inputJack.setValue(jackValue.value)
                    is JsonValue.Obj -> {
                        (jackValue.map["min"] as? JsonValue.Num)?.value?.let { inputJack.min = it }
                        (jackValue.map["max"] as? JsonValue.Num)?.value?.let { inputJack.max = it }
                        (jackValue.map["v"] as? JsonValue.Num)?.value?.let { inputJack.setValue(it) }
                        if (inputJack is AudioInputJack) {
                            (jackValue.map["add"] as? JsonValue.Bool)?.value?.let { inputJack.isValueAdded = it }
                        }
                    }
                    else -> {}
                }
            }
            // Restore patch internals before parent connections so jacks (in1, out1, …) exist
            if (module is PatchModule) {
                (obj?.map?.get("windowX") as? JsonValue.Num)?.value?.let { module.windowX = it.toFloat() }
                (obj?.map?.get("windowY") as? JsonValue.Num)?.value?.let { module.windowY = it.toFloat() }
                (obj?.map?.get("windowWidth") as? JsonValue.Num)?.value?.let { module.windowWidth = it.toFloat() }
                (obj?.map?.get("windowHeight") as? JsonValue.Num)?.value?.let { module.windowHeight = it.toFloat() }
                (obj?.map?.get("windowOpen") as? JsonValue.Bool)?.value?.let { module.isWindowOpen = it }
                val filePath = (obj?.map?.get("filePath") as? JsonValue.Str)?.value
                if (filePath != null) {
                    module.filePath = filePath
                    val fileContent = readFileDirect(filePath)
                    if (fileContent != null) {
                        val childRootVal = try { JsonParser(fileContent).parse() as? JsonValue.Obj } catch (_: Exception) { null }
                        if (childRootVal != null) {
                            module.graphState.isDeserializing = true
                            try {
                                module.graphState.removeModulesDirectly(module.graphState.modules.toList())
                                val innerPatchObj = childRootVal.map["patch"] as? JsonValue.Obj
                                if (innerPatchObj != null) {
                                    deserializeObj(module.graphState, innerPatchObj, Offset.Zero, density, problems)
                                } else {
                                    deserializeObj(module.graphState, childRootVal, Offset.Zero, density, problems)
                                }
                                if (module is PolyPatchModule) {
                                    module.ensureDefaultModulesExist()
                                }
                            } finally {
                                module.graphState.isDeserializing = false
                            }
                        }
                    } else {
                        val patchObj = obj?.map?.get("patch") as? JsonValue.Obj
                        if (patchObj != null) {
                            module.graphState.isDeserializing = true
                            try {
                                module.graphState.removeModulesDirectly(module.graphState.modules.toList())
                                deserializeObj(module.graphState, patchObj, Offset.Zero, density, problems)
                                if (module is PolyPatchModule) {
                                    module.ensureDefaultModulesExist()
                                }
                            } finally {
                                module.graphState.isDeserializing = false
                            }
                        }
                    }
                    module.isModified = false
                } else {
                    val patchObj = obj?.map?.get("patch") as? JsonValue.Obj
                    if (patchObj != null) {
                        module.graphState.isDeserializing = true
                        try {
                            module.graphState.removeModulesDirectly(module.graphState.modules.toList())
                            deserializeObj(module.graphState, patchObj, Offset.Zero, density, problems)
                            if (module is PolyPatchModule) {
                                module.ensureDefaultModulesExist()
                            }
                        } finally {
                            module.graphState.isDeserializing = false
                        }
                    }
                }
            }
            if (module is PianoRollModule) {
                (obj?.map?.get("bpm") as? JsonValue.Num)?.value?.toInt()?.let { module.bpm = it }
                (obj?.map?.get("playStart") as? JsonValue.Num)?.value?.toInt()?.let { module.playStartTicks = it }
                (obj?.map?.get("windowX") as? JsonValue.Num)?.value?.let { module.windowX = it.toFloat() }
                (obj?.map?.get("windowY") as? JsonValue.Num)?.value?.let { module.windowY = it.toFloat() }
                (obj?.map?.get("windowWidth") as? JsonValue.Num)?.value?.let { module.windowWidth = it.toFloat() }
                (obj?.map?.get("windowHeight") as? JsonValue.Num)?.value?.let { module.windowHeight = it.toFloat() }
                val notesArr = (obj?.map?.get("notes") as? JsonValue.Arr)?.items
                if (notesArr != null) {
                    for (row in notesArr) {
                        val fields = (row as? JsonValue.Arr)?.items
                        if (fields == null || fields.size < 7) {
                            problems.add("pianoRoll '${module.name}': skipping malformed note entry.")
                            continue
                        }
                        val nums = fields.map { (it as? JsonValue.Num)?.value }
                        if (nums.any { it == null }) {
                            problems.add("pianoRoll '${module.name}': skipping non-numeric note entry.")
                            continue
                        }
                        module.notes.add(
                            PianoRollNote(
                                id = nums[0]!!.toInt(),
                                startTicks = nums[1]!!.toInt(),
                                durationTicks = nums[2]!!.toInt(),
                                parentId = nums[3]!!.toInt(),
                                ratio = Ratio(nums[4]!!.toInt(), nums[5]!!.toInt()),
                                rootPitch = nums[6]!!,
                            )
                        )
                    }
                    // Defensive: orphaned parent references become root notes at their current pitch.
                    for (note in module.notes) {
                        if (!note.isRoot && module.findNote(note.parentId) == null) {
                            problems.add("pianoRoll '${module.name}': note ${note.id} references missing parent ${note.parentId}; converting to root.")
                            note.rootPitch = 60.0 + note.ratio.semitones
                            note.parentId = -1
                            note.ratio = Ratio.UNISON
                        }
                    }
                    module.resetNextNoteId()
                }
            }
            if (module is SegmentedEnvelopeModule) {
                val envDataArr = (obj?.map?.get("envData") as? JsonValue.Arr)?.items
                if (envDataArr != null) {
                    val doubles = DoubleArray(envDataArr.size) { idx ->
                        (envDataArr[idx] as? JsonValue.Num)?.value ?: 0.0
                    }
                    val env = SegmentedEnvelope(doubles)
                    (obj.map["sustainBegin"] as? JsonValue.Num)?.value?.let { env.sustainBegin = it.toInt() }
                    (obj.map["sustainEnd"] as? JsonValue.Num)?.value?.let { env.sustainEnd = it.toInt() }
                    (obj.map["releaseBegin"] as? JsonValue.Num)?.value?.let { env.releaseBegin = it.toInt() }
                    (obj.map["releaseEnd"] as? JsonValue.Num)?.value?.let { env.releaseEnd = it.toInt() }
                    module.envelope = env
                }
            }
            created.add(module)
            succeeded.add(module)
        }

        for (item in connectionsArr) {
            val obj = item as? JsonValue.Obj ?: continue
            val fromIdx = (obj.map["from"] as? JsonValue.Num)?.value?.toInt()
            val fromJack = (obj.map["fromJack"] as? JsonValue.Str)?.value
            val toIdx = (obj.map["to"] as? JsonValue.Num)?.value?.toInt()
            val toJack = (obj.map["toJack"] as? JsonValue.Str)?.value

            if (fromIdx == null || fromJack == null || toIdx == null || toJack == null) {
                problems.add("Connection entry is missing required fields (from: $fromIdx, fromJack: $fromJack, to: $toIdx, toJack: $toJack).")
                continue
            }

            val fromModule = created.getOrNull(fromIdx)
            if (fromModule == null) {
                problems.add("Connection source module ID $fromIdx not found.")
                continue
            }
            val toModule = created.getOrNull(toIdx)
            if (toModule == null) {
                problems.add("Connection target module ID $toIdx not found.")
                continue
            }
            val output = fromModule.outputs.find { it.name == fromJack }
            if (output == null) {
                problems.add("Output jack '$fromJack' not found on module '${fromModule.name}' (type: ${fromModule.type}).")
                continue
            }
            val input = toModule.inputs.find { it.name == toJack }
            if (input == null) {
                problems.add("Input jack '$toJack' not found on module '${toModule.name}' (type: ${toModule.type}).")
                continue
            }
            graphState.connect(output, input)
        }
        return succeeded
        } finally {
            graphState.isDeserializing = false
        }
    }
}

internal val String.jsonEscape: String
    get() = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

sealed class JsonValue {
    abstract fun stringify(indentLevel: Int): String

    object Null : JsonValue() {
        override fun stringify(indentLevel: Int): String = "null"
    }
    data class Bool(val value: Boolean) : JsonValue() {
        override fun stringify(indentLevel: Int): String = value.toString()
    }
    data class Num(val value: Double) : JsonValue() {
        override fun stringify(indentLevel: Int): String {
            return if (value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                value.toString()
            }
        }
    }
    data class Str(val value: String) : JsonValue() {
        override fun stringify(indentLevel: Int): String = "\"${value.jsonEscape}\""
    }
    data class Arr(val items: List<JsonValue>) : JsonValue() {
        override fun stringify(indentLevel: Int): String {
            if (items.isEmpty()) return "[]"
            val allPrimitives = items.all { it is Num || it is Str || it is Bool || it is Null }
            if (allPrimitives && items.size <= 32) {
                return items.joinToString(prefix = "[", postfix = "]") { it.stringify(0) }
            }
            val indent = "  ".repeat(indentLevel)
            val innerIndent = "  ".repeat(indentLevel + 1)
            return buildString {
                append("[\n")
                items.forEachIndexed { i, item ->
                    if (i > 0) append(",\n")
                    append(innerIndent)
                    append(item.stringify(indentLevel + 1))
                }
                append("\n")
                append(indent)
                append("]")
            }
        }
    }
    data class Obj(val map: Map<String, JsonValue>) : JsonValue() {
        override fun stringify(indentLevel: Int): String {
            if (map.isEmpty()) return "{}"
            val indent = "  ".repeat(indentLevel)
            val innerIndent = "  ".repeat(indentLevel + 1)
            return buildString {
                append("{\n")
                var first = true
                for ((key, value) in map) {
                    if (!first) append(",\n")
                    first = false
                    append(innerIndent)
                    append("\"${key.jsonEscape}\": ")
                    append(value.stringify(indentLevel + 1))
                }
                append("\n")
                append(indent)
                append("}")
            }
        }
    }
}

class JsonParser(private val input: String) {
    private var pos = 0

    fun parse(): JsonValue {
        skipWs()
        return parseValue()
    }

    private fun parseValue(): JsonValue {
        skipWs()
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBool()
            'n' -> parseNull()
            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonValue.Obj {
        expect('{')
        val map = mutableMapOf<String, JsonValue>()
        skipWs()
        if (peek() == '}') { advance(); return JsonValue.Obj(map) }
        while (true) {
            skipWs()
            val key = parseString().value
            skipWs()
            expect(':')
            map[key] = parseValue()
            skipWs()
            if (peek() == '}') { advance(); break }
            expect(',')
        }
        return JsonValue.Obj(map)
    }

    private fun parseArray(): JsonValue.Arr {
        expect('[')
        val items = mutableListOf<JsonValue>()
        skipWs()
        if (peek() == ']') { advance(); return JsonValue.Arr(items) }
        while (true) {
            items.add(parseValue())
            skipWs()
            if (peek() == ']') { advance(); break }
            expect(',')
        }
        return JsonValue.Arr(items)
    }

    private fun parseString(): JsonValue.Str {
        expect('"')
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != '"') {
            if (input[pos] == '\\') {
                pos++
                when (input.getOrNull(pos)) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> input.getOrNull(pos)?.let { sb.append(it) }
                }
            } else {
                sb.append(input[pos])
            }
            pos++
        }
        expect('"')
        return JsonValue.Str(sb.toString())
    }

    private fun parseBool(): JsonValue.Bool {
        return if (input.startsWith("true", pos)) {
            pos += 4; JsonValue.Bool(true)
        } else {
            pos += 5; JsonValue.Bool(false)
        }
    }

    private fun parseNull(): JsonValue.Null {
        pos += 4; return JsonValue.Null
    }

    private fun parseNumber(): JsonValue.Num {
        val start = pos
        if (pos < input.length && input[pos] == '-') pos++
        while (pos < input.length && input[pos].isDigit()) pos++
        if (pos < input.length && input[pos] == '.') {
            pos++
            while (pos < input.length && input[pos].isDigit()) pos++
        }
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
            while (pos < input.length && input[pos].isDigit()) pos++
        }
        return JsonValue.Num(input.substring(start, pos).toDouble())
    }

    private fun skipWs() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
    private fun peek(): Char = input.getOrElse(pos) { ' ' }
    private fun advance() { pos++ }
    private fun expect(c: Char) {
        if (pos >= input.length || input[pos] != c)
            throw IllegalArgumentException("Expected '$c' at pos $pos, got '${input.getOrNull(pos)}'")
        pos++
    }
}

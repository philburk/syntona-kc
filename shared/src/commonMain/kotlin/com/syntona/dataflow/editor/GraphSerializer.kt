package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.CommentModule
import com.syntona.dataflow.modules.ScopeModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.SegmentedEnvelopeModule
import com.syntona.dataflow.modules.LinkModule
import com.softsynth.ksyn.data.SegmentedEnvelope
import com.syntona.dataflow.registry.ModuleRegistry

object GraphSerializer {

    fun serialize(graphState: GraphState, modules: List<Module>): String = buildString {
        val internalConns = graphState.connections.filter { conn ->
            modules.any { it === conn.output.module } &&
            modules.any { it === conn.input.module }
        }
        append("{\"modules\":[")
        modules.forEachIndexed { i, module ->
            if (i > 0) append(",")
            val pos = graphState.modulePositions[module] ?: Offset.Zero
            append("{\"id\":$i,\"type\":\"${module.name.jsonEscape}\"")
            append(",\"x\":${pos.x},\"y\":${pos.y},\"jacks\":{")
            val valueJacks = module.inputs.filter { jack ->
                jack !is CommandJack && !jack.isKickable &&
                (jack.lastMessage is Message.FloatValue || jack is AudioInputJack)
            }
            valueJacks.forEachIndexed { j, jack ->
                if (j > 0) append(",")
                append("\"${jack.name.jsonEscape}\":{\"v\":${jack.getValue()},\"min\":${jack.min},\"max\":${jack.max}}")
            }
            append("}")
            if (module is CommentModule) {
                append(",\"text\":\"${module.text.jsonEscape}\"")
                append(",\"w\":${module.width},\"h\":${module.height}")
            }
            if (module is ScopeModule) {
                append(",\"w\":${module.width},\"h\":${module.height}")
            }
            if (module is LinkModule) {
                append(",\"filePath\":\"${module.filePath.jsonEscape}\"")
                append(",\"buttonText\":\"${module.buttonText.jsonEscape}\"")
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
            // Recursively embed patch contents so jacks are restored on paste/load
            if (module is PatchModule) {
                append(",\"patch\":")
                append(serialize(module.graphState, module.graphState.modules.toList()))
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

    fun deserialize(graphState: GraphState, json: String, offset: Offset = Offset.Zero): List<Module> {
        val rootVal = try { JsonParser(json).parse() } catch (_: Exception) { return emptyList() }
        return deserializeObj(graphState, rootVal as? JsonValue.Obj ?: return emptyList(), offset)
    }

    private fun deserializeObj(graphState: GraphState, rootObj: JsonValue.Obj, offset: Offset): List<Module> {
        val modulesArr = (rootObj.map["modules"] as? JsonValue.Arr)?.items ?: return emptyList()
        val connectionsArr = (rootObj.map["connections"] as? JsonValue.Arr)?.items ?: return emptyList()

        val created = mutableListOf<Module?>()
        val succeeded = mutableListOf<Module>()

        for (item in modulesArr) {
            val obj = item as? JsonValue.Obj
            val typeName = (obj?.map?.get("type") as? JsonValue.Str)?.value
            val x = (obj?.map?.get("x") as? JsonValue.Num)?.value?.toFloat() ?: 100f
            val y = (obj?.map?.get("y") as? JsonValue.Num)?.value?.toFloat() ?: 100f
            val jacks = obj?.map?.get("jacks") as? JsonValue.Obj

            val creator = typeName?.let { ModuleRegistry.findCreator(it) }
            if (creator == null) { created.add(null); continue }

            val module = creator()
            graphState.addModule(module, Offset(x + offset.x, y + offset.y))
            jacks?.map?.forEach { (jackName, jackValue) ->
                val inputJack = module.inputs.find { it.name == jackName } as? InputJack ?: return@forEach
                when (jackValue) {
                    is JsonValue.Num -> inputJack.setValue(jackValue.value)
                    is JsonValue.Obj -> {
                        (jackValue.map["min"] as? JsonValue.Num)?.value?.let { inputJack.min = it }
                        (jackValue.map["max"] as? JsonValue.Num)?.value?.let { inputJack.max = it }
                        (jackValue.map["v"] as? JsonValue.Num)?.value?.let { inputJack.setValue(it) }
                    }
                    else -> {}
                }
            }
            // Restore patch internals before parent connections so jacks (in1, out1, …) exist
            if (module is PatchModule) {
                val patchObj = obj?.map?.get("patch") as? JsonValue.Obj
                if (patchObj != null) {
                    deserializeObj(module.graphState, patchObj, Offset.Zero)
                }
            }
            if (module is CommentModule) {
                (obj?.map?.get("text") as? JsonValue.Str)?.value?.let { module.text = it }
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
            val fromIdx = (obj.map["from"] as? JsonValue.Num)?.value?.toInt() ?: continue
            val fromJack = (obj.map["fromJack"] as? JsonValue.Str)?.value ?: continue
            val toIdx = (obj.map["to"] as? JsonValue.Num)?.value?.toInt() ?: continue
            val toJack = (obj.map["toJack"] as? JsonValue.Str)?.value ?: continue

            val fromModule = created.getOrNull(fromIdx) ?: continue
            val toModule = created.getOrNull(toIdx) ?: continue
            val output = fromModule.outputs.find { it.name == fromJack } ?: continue
            val input = toModule.inputs.find { it.name == toJack } ?: continue
            graphState.connect(output, input)
        }
        return succeeded
    }
}

internal val String.jsonEscape: String
    get() = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

internal sealed class JsonValue {
    object Null : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val map: Map<String, JsonValue>) : JsonValue()
}

internal class JsonParser(private val input: String) {
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

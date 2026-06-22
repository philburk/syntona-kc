package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry

object GraphSerializer {

    fun serialize(graphState: GraphState, modules: List<Module>): String {
        val internalConns = graphState.connections.filter { conn ->
            modules.any { it === conn.output.module } &&
            modules.any { it === conn.input.module }
        }
        return buildString {
            append("{\"modules\":[")
            modules.forEachIndexed { i, module ->
                if (i > 0) append(",")
                val pos = graphState.modulePositions[module] ?: Offset.Zero
                append("{\"id\":$i,\"type\":\"${module.name.jsonEscape}\"")
                append(",\"x\":${pos.x},\"y\":${pos.y},\"jacks\":{")
                val valueJacks = module.inputs.filter { jack ->
                    jack !is CommandJack && !jack.isKickable &&
                    jack !is AudioInputJack &&
                    jack.lastMessage is Message.FloatValue
                }
                valueJacks.forEachIndexed { j, jack ->
                    if (j > 0) append(",")
                    append("\"${jack.name.jsonEscape}\":${jack.getDoubleValue()}")
                }
                append("}}")
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
    }

    fun deserialize(graphState: GraphState, json: String, offset: Offset = Offset.Zero): List<Module> {
        val rootVal = try { JsonParser(json).parse() } catch (_: Exception) { return emptyList() }
        val rootObj = rootVal as? JsonValue.Obj ?: return emptyList()
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
                if (jackValue is JsonValue.Num) {
                    module.inputs.find { it.name == jackName }?.setValue(jackValue.value)
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

    private fun skipWs() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    private fun peek(): Char = input.getOrElse(pos) { ' ' }
    private fun advance() { pos++ }
    private fun expect(c: Char) {
        if (pos >= input.length || input[pos] != c)
            throw IllegalArgumentException("Expected '$c' at pos $pos, got '${input.getOrNull(pos)}'")
        pos++
    }
}

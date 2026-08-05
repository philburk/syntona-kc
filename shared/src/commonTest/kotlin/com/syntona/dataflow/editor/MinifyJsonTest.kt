package com.syntona.dataflow.editor

import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.SineModule
import com.syntona.util.LzString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MinifyJsonTest {

    @Test
    fun testMinifyJsonStripsWhitespaceOutsideStrings() {
        val prettyJson = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "sine",
                  "name": "hello world",
                  "x": 100.0,
                  "y": 200.0
                }
              ]
            }
        """.trimIndent()

        val minified = GraphSerializer.minifyJson(prettyJson)
        val expected = "{\"modules\":[{\"id\":0,\"type\":\"sine\",\"name\":\"hello world\",\"x\":100.0,\"y\":200.0}]}"
        assertEquals(expected, minified)
    }

    @Test
    fun testMinifyJsonPreservesSpacesInsideStrings() {
        val json = "{\"text\": \"  hello   world  \\\" quoted \\\"  \"}"
        val minified = GraphSerializer.minifyJson(json)
        val expected = "{\"text\":\"  hello   world  \\\" quoted \\\"  \"}"
        assertEquals(expected, minified)
    }

    @Test
    fun testDrumRhythmExactByteSizes() {
        val drumFile = File("src/commonMain/composeResources/files/examples/drum_rhythm.json")
        if (!drumFile.exists()) return

        val rawFileJson = drumFile.readText()
        val minifiedFileJson = GraphSerializer.minifyJson(rawFileJson)

        val unminifiedLzPayload = LzString.compressToEncodedURIComponent(rawFileJson)
        val minifiedLzPayload = LzString.compressToEncodedURIComponent(minifiedFileJson)

        println("DRUM RHYTHM RAW UNMINIFIED FILE BYTES: ${rawFileJson.length}")
        println("DRUM RHYTHM MINIFIED FILE BYTES: ${minifiedFileJson.length}")
        println("UNMINIFIED LZ COMPRESSED PAYLOAD SIZE: ${unminifiedLzPayload.length}")
        println("MINIFIED LZ COMPRESSED PAYLOAD SIZE: ${minifiedLzPayload.length}")
    }
}

package com.syntona.dataflow.editor

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
}

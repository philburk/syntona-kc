package com.syntona.dataflow.editor

import com.syntona.dataflow.model.Message
import com.syntona.dataflow.modules.KeyModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import com.syntona.dataflow.model.cleanKotlinIdentifier

class ModuleRenameTest {

    @Test
    fun testIdentifierCleaning() {
        assertEquals("valid_name", cleanKotlinIdentifier("valid_name"))
        assertEquals("validName123", cleanKotlinIdentifier("validName123"))
        assertEquals("validname", cleanKotlinIdentifier("valid-name"))
        assertEquals("namewithspaces", cleanKotlinIdentifier("name with spaces"))
        assertEquals("namewithdots", cleanKotlinIdentifier("name.with.dots"))
        assertEquals("a1", cleanKotlinIdentifier("1a1")) // leading '1' should be removed, leaving 'a1'
        assertEquals("_123", cleanKotlinIdentifier("1_123")) // leading '1' removed, leaving '_123'
        assertEquals("", cleanKotlinIdentifier("123")) // all leading digits removed since first char must be letter/underscore
    }

    @Test
    fun testModuleDefaultNameAndType() {
        val module = KeyModule()
        assertEquals("key", module.type, "Default type should be 'key'")
        assertEquals("key", module.name, "Default name should match type")
    }

    @Test
    fun testModuleRename() {
        val module = KeyModule()
        module.name = "myCustomKey"
        assertEquals("key", module.type, "Type should remain 'key' after rename")
        assertEquals("myCustomKey", module.name, "Name should be updated to nickname")
    }

    @Test
    fun testRenameSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = KeyModule()
        module.name = "ampEnv" // rename 'key' module to 'ampEnv'
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize
        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"type\": \"core.input.key\"") || jsonStr.contains("\"type\": \"key\""), "JSON should contain type 'core.input.key' or 'key'")
        assertTrue(jsonStr.contains("\"name\": \"ampEnv\""), "JSON should contain the customized name 'ampEnv'")

        // Deserialize
        val newGraphState = GraphState()
        val deserializedModules = GraphSerializer.deserialize(newGraphState, jsonStr)

        assertEquals(1, deserializedModules.size)
        val deserializedModule = deserializedModules[0]
        assertTrue(deserializedModule is KeyModule, "Deserialized module should be reconstructed as the proper creator class KeyModule")
        assertEquals("key", deserializedModule.type)
        assertEquals("ampEnv", deserializedModule.name, "Deserialized module should have its custom nickname restored")
    }

    @Test
    fun testSegmentedEnvelopeRename() {
        val module = com.syntona.dataflow.modules.SegmentedEnvelopeModule()
        assertEquals("segEnv", module.type)
        assertEquals("segEnv", module.name)
        module.name = "myAmpEnv"
        assertEquals("myAmpEnv", module.name)
    }

    @Test
    fun testLegacyModuleNameResolutionOnDeserialization() {
        val legacyJson = """
            {
              "modules": [
                {"id": 0, "type": "core.input.loadKick", "name": "loadKick", "x": 0.0, "y": 0.0},
                {"id": 1, "type": "ksyn.math.ap2f", "name": "ap2f", "x": 0.0, "y": 0.0},
                {"id": 2, "type": "ksyn.math.a*", "name": "a*", "x": 0.0, "y": 0.0},
                {"id": 3, "type": "core.logic.ckDiv", "name": "ckDiv", "x": 0.0, "y": 0.0},
                {"id": 4, "type": "core.control.arm", "name": "arm", "x": 0.0, "y": 0.0},
                {"id": 5, "type": "core.logic.gate", "name": "gate", "x": 0.0, "y": 0.0},
                {"id": 6, "type": "core.logic.choose4", "name": "choose4", "x": 0.0, "y": 0.0}
              ],
              "connections": []
            }
        """.trimIndent()

        val graphState = GraphState()
        val deserialized = GraphSerializer.deserialize(graphState, legacyJson)
        assertEquals(7, deserialized.size)

        assertEquals("onLoad", deserialized[0].name)
        assertEquals("pitchToHz~", deserialized[1].name)
        assertEquals("*~", deserialized[2].name)
        assertEquals("clockDiv", deserialized[3].name)
        assertEquals("oneShot", deserialized[4].name)
        assertEquals("pass", deserialized[5].name)
        assertEquals("select4", deserialized[6].name)
    }

    @Test
    fun testCustomNicknamesPreservedOnDeserialization() {
        val customJson = """
            {
              "modules": [
                {"id": 0, "type": "core.input.loadKick", "name": "kickTrigger", "x": 0.0, "y": 0.0},
                {"id": 1, "type": "ksyn.math.ap2f", "name": "myPitchConverter", "x": 0.0, "y": 0.0}
              ],
              "connections": []
            }
        """.trimIndent()

        val graphState = GraphState()
        val deserialized = GraphSerializer.deserialize(graphState, customJson)
        assertEquals(2, deserialized.size)

        assertEquals("kickTrigger", deserialized[0].name)
        assertEquals("myPitchConverter", deserialized[1].name)
    }
}


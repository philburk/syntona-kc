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
}

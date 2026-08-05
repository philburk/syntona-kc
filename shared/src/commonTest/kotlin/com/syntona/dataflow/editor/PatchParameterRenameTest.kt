package com.syntona.dataflow.editor

import com.syntona.dataflow.modules.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PatchParameterRenameTest {

    @Test
    fun testDefaultParameterNaming() {
        val patchModule = PatchModule()
        val inputModule = InputModule()
        val outputModule = OutputModule()

        patchModule.graphState.addModule(inputModule, androidx.compose.ui.geometry.Offset.Zero)
        patchModule.graphState.addModule(outputModule, androidx.compose.ui.geometry.Offset.Zero)

        // Verifying default name assignment
        assertEquals("in1", inputModule.name)
        assertEquals("out1", outputModule.name)

        // Verifying parent jacks match
        assertEquals(1, patchModule.inputs.size)
        assertEquals("in1", patchModule.inputs[0].name)

        assertEquals(1, patchModule.outputs.size)
        assertEquals("out1", patchModule.outputs[0].name)
    }

    @Test
    fun testParameterRenamingPropagation() {
        val patchModule = PatchModule()
        val inputModule = InputModule()
        val audioInputModule = AudioInputModule()

        patchModule.graphState.addModule(inputModule, androidx.compose.ui.geometry.Offset.Zero)
        patchModule.graphState.addModule(audioInputModule, androidx.compose.ui.geometry.Offset.Zero)

        // Rename the modules
        inputModule.name = "cutoff"
        audioInputModule.name = "modulator"

        // Verify the parent patch's corresponding jacks are renamed
        assertEquals("cutoff", inputModule.name)
        assertEquals("modulator", audioInputModule.name)

        val patchInputJack = patchModule.inputs.find { it === inputModule.patchJack }
        assertNotNull(patchInputJack)
        assertEquals("cutoff", patchInputJack.name)

        val patchAudioInputJack = patchModule.inputs.find { it === audioInputModule.patchJack }
        assertNotNull(patchAudioInputJack)
        assertEquals("modulator", patchAudioInputJack.name)
    }

    @Test
    fun testInputModulePatchJackMinMaxEditing() {
        val patchModule = PatchModule()
        val inputModule = InputModule()
        patchModule.graphState.addModule(inputModule, androidx.compose.ui.geometry.Offset.Zero)
        inputModule.name = "freq"

        val jack = inputModule.patchJack
        assertNotNull(jack)
        assertEquals("freq", jack.name)

        jack.min = 20.0
        jack.max = 20000.0

        assertEquals(20.0, jack.min)
        assertEquals(20000.0, jack.max)
        assertEquals(20.0, patchModule.inputs[0].min)
        assertEquals(20000.0, patchModule.inputs[0].max)
    }

    @Test
    fun testPatchRenamingSerializationAndDeserialization() {
        val rootGraph = GraphState()
        val patchModule = PatchModule()
        rootGraph.addModule(patchModule, androidx.compose.ui.geometry.Offset.Zero)

        // Add child module and rename it
        val inputModule = InputModule()
        patchModule.graphState.addModule(inputModule, androidx.compose.ui.geometry.Offset.Zero)
        inputModule.name = "amplitude"

        // Verify pre-serialize state
        assertEquals("amplitude", inputModule.name)
        assertEquals("amplitude", patchModule.inputs[0].name)

        // Serialize
        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"type\": \"core.input\"") || json.contains("\"type\": \"input\""))
        assertTrue(json.contains("\"name\": \"amplitude\""))

        // Deserialize
        val newRootGraph = GraphState()
        val deserializedModules = GraphSerializer.deserialize(newRootGraph, json)
        assertEquals(1, deserializedModules.size)

        val newPatchModule = deserializedModules[0] as PatchModule
        assertEquals(1, newPatchModule.graphState.modules.size)

        val deserializedInputModule = newPatchModule.graphState.modules.first() as InputModule
        assertEquals("amplitude", deserializedInputModule.name)
        assertEquals(1, newPatchModule.inputs.size)
        assertEquals("amplitude", newPatchModule.inputs[0].name)
    }

    @Test
    fun testPatchWindowGeometrySerializationAndDeserialization() {
        val rootGraph = GraphState()
        val patchModule = PatchModule()
        rootGraph.addModule(patchModule, androidx.compose.ui.geometry.Offset.Zero)

        // Simulate a user having moved/resized the patch's editing window
        patchModule.windowX = 123f
        patchModule.windowY = 45f
        patchModule.windowWidth = 640f
        patchModule.windowHeight = 480f

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"windowX\": 123"))
        assertTrue(json.contains("\"windowY\": 45"))
        assertTrue(json.contains("\"windowWidth\": 640"))
        assertTrue(json.contains("\"windowHeight\": 480"))

        val newRootGraph = GraphState()
        val deserializedModules = GraphSerializer.deserialize(newRootGraph, json)
        val newPatchModule = deserializedModules[0] as PatchModule

        assertEquals(123f, newPatchModule.windowX)
        assertEquals(45f, newPatchModule.windowY)
        assertEquals(640f, newPatchModule.windowWidth)
        assertEquals(480f, newPatchModule.windowHeight)
    }

    @Test
    fun testPatchWindowGeometryDefaultsToNullWhenNeverSet() {
        val rootGraph = GraphState()
        val patchModule = PatchModule()
        rootGraph.addModule(patchModule, androidx.compose.ui.geometry.Offset.Zero)

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(!json.contains("windowX"))

        val newRootGraph = GraphState()
        val deserializedModules = GraphSerializer.deserialize(newRootGraph, json)
        val newPatchModule = deserializedModules[0] as PatchModule

        assertEquals(null, newPatchModule.windowX)
        assertEquals(null, newPatchModule.windowWidth)
    }

    @Test
    fun testRootWindowGeometrySerializationAndDeserialization() {
        val rootGraph = GraphState()
        val childModule = PatchModule()
        rootGraph.addModule(childModule, androidx.compose.ui.geometry.Offset.Zero)

        // The root patch has no module entry of its own, so its geometry is passed in
        // separately and lives as top-level fields, not nested under any module.
        val json = GraphSerializer.serialize(
            rootGraph, rootGraph.modules,
            rootWindowX = 15f,
            rootWindowY = 25f,
            rootWindowWidth = 900f,
            rootWindowHeight = 700f
        )
        assertTrue(json.contains("\"windowX\": 15"))
        assertTrue(json.contains("\"windowY\": 25"))
        assertTrue(json.contains("\"windowWidth\": 900"))
        assertTrue(json.contains("\"windowHeight\": 700"))

        val geometry = GraphSerializer.readRootWindowGeometry(json)
        assertNotNull(geometry)
        assertEquals(15f, geometry.x)
        assertEquals(25f, geometry.y)
        assertEquals(900f, geometry.width)
        assertEquals(700f, geometry.height)

        // Deserializing the modules must still work normally alongside the extra root fields
        val newRootGraph = GraphState()
        val deserializedModules = GraphSerializer.deserialize(newRootGraph, json)
        assertEquals(1, deserializedModules.size)
    }

    @Test
    fun testRootWindowGeometryAbsentWhenNotProvided() {
        val rootGraph = GraphState()
        rootGraph.addModule(PatchModule(), androidx.compose.ui.geometry.Offset.Zero)

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertEquals(null, GraphSerializer.readRootWindowGeometry(json))
    }
}

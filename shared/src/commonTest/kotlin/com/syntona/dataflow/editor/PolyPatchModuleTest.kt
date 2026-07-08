package com.syntona.dataflow.editor

import com.softsynth.ksyn.unitgen.SineOscillator
import com.softsynth.ksyn.unitgen.PassThrough
import com.syntona.dataflow.modules.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PolyPatchModuleTest {

    @Test
    fun testPolyPatchInitializationAndVoiceSync() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(4)
        assertEquals(4, polyPatch.maxVoices)
        assertNotNull(polyPatch.polyphonicInstrument)

        // Add a unit generator module
        val sineOscModule = UnitGeneratorModule("sineOsc", SineOscillator())
        polyPatch.graphState.addModule(sineOscModule, androidx.compose.ui.geometry.Offset.Zero)

        // Add aOutput module
        val audioOutputModule = AudioOutputModule()
        polyPatch.graphState.addModule(audioOutputModule, androidx.compose.ui.geometry.Offset.Zero)

        // Connect them
        val outJack = sineOscModule.outputs.find { it.name == "output" }
        val inJack = audioOutputModule.inputs.find { it.name == "input" }
        assertNotNull(outJack)
        assertNotNull(inJack)
        polyPatch.graphState.connect(outJack, inJack)

        // Remove module to verify no throw/errors
        polyPatch.graphState.removeModulesDirectly(listOf(sineOscModule))
    }

    @Test
    fun testPolyPatchSerialization() {
        val rootGraph = GraphState()
        val polyPatch = PolyPatchModule()
        rootGraph.addModule(polyPatch, androidx.compose.ui.geometry.Offset.Zero)
        polyPatch.initializeVoices(3)

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        assertTrue(json.contains("\"maxVoices\": 3"))

        val newRootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newRootGraph, json)
        assertEquals(1, deserialized.size)

        val newPolyPatch = deserialized[0] as PolyPatchModule
        assertEquals(3, newPolyPatch.maxVoices)
        assertTrue(newPolyPatch.isInitialized)
    }

    @Test
    fun testPolyPatchDeserializationWithoutMaxVoices() {
        val json = """
            {"modules":[{"id":1,"type":"polyPatch","name":"poly1","x":0.0,"y":0.0}],"connections":[]}
        """.trimIndent()
        val rootGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(rootGraph, json)
        assertEquals(1, deserialized.size)
        val polyPatch = deserialized[0] as PolyPatchModule
        assertTrue(!polyPatch.isInitialized)
    }

    @Test
    fun testJackValuePropagationToVoices() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)

        val sineOscModule = UnitGeneratorModule("sineOsc", SineOscillator())
        polyPatch.graphState.addModule(sineOscModule, androidx.compose.ui.geometry.Offset.Zero)

        val freqJack = sineOscModule.inputs.find { it.name == "frequency" }
        assertNotNull(freqJack)

        // Set value on the editor module's jack
        freqJack.setValue(880.0)
    }
}

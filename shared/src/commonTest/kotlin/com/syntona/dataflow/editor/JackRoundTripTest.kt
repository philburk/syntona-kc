package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.AddTimeModule
import com.syntona.dataflow.modules.KnobModule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A jack's min, max, kickable flag and value all have to survive save and load.
 *
 * These fields are easy to drop by "optimizing" the JSON, because a field missing on load means
 * "keep the module's own default" - and modules set their own defaults, which are not the ones
 * [com.syntona.dataflow.model.InputJack] starts with.
 */
class JackRoundTripTest {

    private fun roundTrip(module: Module): Module {
        val before = GraphState()
        before.addModule(module, Offset(10f, 20f))
        val json = GraphSerializer.serialize(before, before.modules.toList(), 1f, 0f, 0f, 800f, 600f)
        val after = GraphState()
        GraphSerializer.deserialize(after, json)
        return after.modules.first()
    }

    @Test
    fun jackMaxSurvivesEvenWhenItMatchesTheBaseClassDefault() {
        // A knob's own default max is 1.0, so 100.0 is a real edit, not a default to be skipped.
        val knob = KnobModule()
        knob.inputs.first { it.name == "in" }.max = 100.0
        val back = roundTrip(knob)
        assertEquals(100.0, back.inputs.first { it.name == "in" }.max)
    }

    @Test
    fun jackMinSurvivesEvenWhenItMatchesTheBaseClassDefault() {
        val addTime = AddTimeModule()
        val delay = addTime.inputs.first { it.name != "in" }
        delay.min = 0.0
        delay.max = 100.0
        val back = roundTrip(addTime)
        val backDelay = back.inputs.first { it.name == delay.name }
        assertEquals(0.0, backDelay.min)
        assertEquals(100.0, backDelay.max)
    }

    @Test
    fun turningOffKickableSurvives() {
        // addTime's "in" is kickable by default, so false is the value that needs writing down.
        val addTime = AddTimeModule()
        addTime.inputs.first { it.name == "in" }.isKickable = false
        val back = roundTrip(addTime)
        assertEquals(false, back.inputs.first { it.name == "in" }.isKickable)
    }

    @Test
    fun jackValueKeepsItsPrecision() {
        val knob = KnobModule()
        knob.inputs.first { it.name == "in" }.setValue(0.123456)
        val back = roundTrip(knob)
        assertEquals(0.123456, back.inputs.first { it.name == "in" }.getValue())
    }

    @Test
    fun anUntouchedKickableJackIsNotGivenAValue() {
        // Writing "v" for a kickable jack makes loading the patch fire that module.
        val addTime = AddTimeModule()
        val state = GraphState()
        state.addModule(addTime, Offset.Zero)
        val json = GraphSerializer.serialize(state, state.modules.toList(), 1f, 0f, 0f, 800f, 600f)
        val inJack = GraphSerializer.minifyJson(json).substringAfter("\"in\":{").substringBefore("}")
        assertEquals(false, inJack.contains("\"v\""), "unexpected value in: $inJack")
    }

    @Test
    fun inputModuleJackPropertiesSurvivePatchSerialization() {
        val patch = com.syntona.dataflow.modules.PatchModule("myPatch")
        val inMod = com.syntona.dataflow.modules.InputModule()
        inMod.name = "mcRatio"
        patch.graphState.addModule(inMod, Offset(10f, 20f))

        val patchJack = inMod.patchJack
        kotlin.test.assertNotNull(patchJack)
        patchJack.setValue(2.0)
        patchJack.min = 0.0
        patchJack.max = 10.0

        val serialized = GraphSerializer.serializePatch(patch)
        val loadedPatch = com.syntona.dataflow.modules.PatchModule("loaded")
        GraphSerializer.deserialize(loadedPatch.graphState, serialized)

        val loadedInMod = loadedPatch.graphState.modules.filterIsInstance<com.syntona.dataflow.modules.InputModule>().firstOrNull()
        kotlin.test.assertNotNull(loadedInMod)
        assertEquals("mcRatio", loadedInMod.name)
        val loadedJack = loadedInMod.patchJack
        kotlin.test.assertNotNull(loadedJack)
        assertEquals(2.0, loadedJack.getValue())
        assertEquals(0.0, loadedJack.min)
        assertEquals(10.0, loadedJack.max)
    }

    @Test
    fun audioInputModuleJackPropertiesSurvivePatchSerialization() {
        val patch = com.syntona.dataflow.modules.PatchModule("myPatch")
        val aInMod = com.syntona.dataflow.modules.AudioInputModule()
        aInMod.name = "audioMod"
        patch.graphState.addModule(aInMod, Offset(10f, 20f))

        val patchJack = aInMod.patchJack
        kotlin.test.assertNotNull(patchJack)
        patchJack.setValue(0.75)
        patchJack.min = 0.1
        patchJack.max = 0.9
        patchJack.isValueAdded = true

        val serialized = GraphSerializer.serializePatch(patch)
        val loadedPatch = com.syntona.dataflow.modules.PatchModule("loaded")
        GraphSerializer.deserialize(loadedPatch.graphState, serialized)

        val loadedAInMod = loadedPatch.graphState.modules.filterIsInstance<com.syntona.dataflow.modules.AudioInputModule>().firstOrNull()
        kotlin.test.assertNotNull(loadedAInMod)
        assertEquals("audioMod", loadedAInMod.name)
        val loadedJack = loadedAInMod.patchJack
        kotlin.test.assertNotNull(loadedJack)
        assertEquals(0.75, loadedJack.getValue(), 1e-4)
        assertEquals(0.1, loadedJack.min, 1e-4)
        assertEquals(0.9, loadedJack.max, 1e-4)
        assertEquals(true, loadedJack.isValueAdded)
    }
}

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
}

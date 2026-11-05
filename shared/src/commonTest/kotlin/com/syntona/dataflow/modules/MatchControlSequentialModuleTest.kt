/*
 * Copyright (C) 2026 Phil Burk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatchControlSequentialModuleTest {

    private val testTime = ScoreTime(100L)

    private class Recorder : Module("recorder") {
        val input = addInput("in", isKickable = true)
        val values = mutableListOf<Double>()

        override fun execute(triggerMessage: Message) {
            if (triggerMessage is Message.FloatValue) values.add(triggerMessage.value)
        }
    }

    private class VoiceRecorder : Module("voiceRecorder") {
        val input = addVoiceInput("in", isKickable = true)
        val messages = mutableListOf<Message.VoiceMessage>()

        override fun execute(triggerMessage: Message) {
            if (triggerMessage is Message.VoiceMessage) messages.add(triggerMessage)
        }
    }

    private fun controlChange(index: Double, value: Double) =
        Message.VoiceMessage(testTime, Message.VoiceOpcode.CONTROL_CHANGE, -1, value, index)

    @Test
    fun isRegisteredInTheMidiBundle() {
        val creator4 = ModuleRegistry.findCreator("matchControl4")
        assertNotNull(creator4, "matchControl4 should be registered")
        assertTrue(creator4() is MatchControl4Module)
        assertEquals("music.midi.matchControl4", ModuleRegistry.getBundlePath("matchControl4"))

        val creator8 = ModuleRegistry.findCreator("matchControl8")
        assertNotNull(creator8, "matchControl8 should be registered")
        assertTrue(creator8() is MatchControl8Module)
        assertEquals("music.midi.matchControl8", ModuleRegistry.getBundlePath("matchControl8"))
    }

    @Test
    fun startsArmedSoItCanBeTaught() {
        val module = MatchControl4Module()
        assertTrue(module.isArmed)
        assertEquals(-1, module.matchNumber)
        assertTrue(module.learn.isHighlighted, "the learn button should be lit while armed")
        assertEquals("?", module.badgeFor(module.learn))
        assertEquals(4, module.numOutputs)
        assertEquals(4, module.stepOutputs.size)
        assertEquals("value0", module.stepOutputs[0].name)
        assertEquals("value3", module.stepOutputs[3].name)
        assertEquals(-1, module.indexFor(0))
        assertEquals(-1, module.indexFor(3))
    }

    @Test
    fun learnsTheFirstControllerAndAssignsIndicesSequentially() {
        val module = MatchControl4Module()
        val recorders = List(4) { Recorder() }
        for (i in 0 until 4) {
            module.stepOutputs[i].connect(recorders[i].input)
        }

        // Send CC 20 with value 0.75
        module.input.receive(controlChange(index = 20.0, value = 0.75))

        assertEquals(20, module.matchNumber)
        assertFalse(module.isArmed)
        assertFalse(module.learn.isHighlighted, "the light goes out once it has learned")
        assertEquals("20", module.badgeFor(module.learn))

        assertEquals(20, module.indexFor(0))
        assertEquals(21, module.indexFor(1))
        assertEquals(22, module.indexFor(2))
        assertEquals(23, module.indexFor(3))

        // First value output jack immediately emits 0.75
        assertEquals(listOf(0.75), recorders[0].values)
        assertEquals(0.75, module.currentValues[0])
        assertEquals(0.75, module.getValue(0))
        for (i in 1 until 4) {
            assertTrue(recorders[i].values.isEmpty())
            assertEquals(0.0, module.currentValues[i])
        }
    }

    @Test
    fun followsOnlySequentialControllersAcrossOutputs() {
        val module = MatchControl4Module()
        val recorders = List(4) { Recorder() }
        for (i in 0 until 4) {
            module.stepOutputs[i].connect(recorders[i].input)
        }

        // Teach base index 16
        module.input.receive(controlChange(index = 16.0, value = 0.1))
        recorders[0].values.clear()

        // Message to jack 1 (CC 17)
        module.input.receive(controlChange(index = 17.0, value = 0.4))
        assertEquals(listOf(0.4), recorders[1].values)
        assertEquals(0.4, module.currentValues[1])

        // Message to jack 2 (CC 18)
        module.input.receive(controlChange(index = 18.0, value = 0.6))
        assertEquals(listOf(0.6), recorders[2].values)
        assertEquals(0.6, module.currentValues[2])

        // Message to jack 3 (CC 19)
        module.input.receive(controlChange(index = 19.0, value = 0.8))
        assertEquals(listOf(0.8), recorders[3].values)
        assertEquals(0.8, module.currentValues[3])

        // Message to jack 0 (CC 16)
        module.input.receive(controlChange(index = 16.0, value = 0.95))
        assertEquals(listOf(0.95), recorders[0].values)
        assertEquals(0.95, module.currentValues[0])
    }

    @Test
    fun unhandledMessagesPassToUnmatched() {
        val module = MatchControl4Module()
        val unmatchedRec = VoiceRecorder()
        module.unmatched.connect(unmatchedRec.input)

        // Teach base index 16
        module.input.receive(controlChange(index = 16.0, value = 0.1))

        // CC 15 is before sequential range [16..19]
        val cc15 = controlChange(index = 15.0, value = 0.2)
        module.input.receive(cc15)

        // CC 20 is after sequential range [16..19]
        val cc20 = controlChange(index = 20.0, value = 0.3)
        module.input.receive(cc20)

        // Note-on message
        val noteOn = Message.VoiceMessage(testTime, Message.VoiceOpcode.NOTE_ON, 60, 60.0, 0.8)
        module.input.receive(noteOn)

        assertEquals(listOf(cc15, cc20, noteOn), unmatchedRec.messages)
    }

    @Test
    fun theLearnButtonRearmsIt() {
        val module = MatchControl4Module()
        val recorder0 = Recorder()
        module.stepOutputs[0].connect(recorder0.input)

        module.input.receive(controlChange(index = 16.0, value = 0.1))
        assertEquals(16, module.matchNumber)

        // Rearm
        module.learn.receive(Message.FloatValue(testTime, 0.0))
        assertTrue(module.isArmed)
        assertTrue(module.learn.isHighlighted)
        assertEquals("?", module.badgeFor(module.learn))

        recorder0.values.clear()
        // Teach new base index 40
        module.input.receive(controlChange(index = 40.0, value = 0.7))
        assertEquals(40, module.matchNumber)
        assertFalse(module.isArmed)
        assertEquals("40", module.badgeFor(module.learn))
        assertEquals(listOf(0.7), recorder0.values)

        assertEquals(40, module.indexFor(0))
        assertEquals(41, module.indexFor(1))
        assertEquals(42, module.indexFor(2))
        assertEquals(43, module.indexFor(3))
    }

    @Test
    fun settingIndexJackDirectlyUpdatesIndicesOrArms() {
        val module = MatchControl4Module()
        module.matchIn.setValue(64.0)

        assertFalse(module.isArmed)
        assertFalse(module.learn.isHighlighted)
        assertEquals(64, module.matchNumber)
        assertEquals("64", module.badgeFor(module.learn))
        assertEquals(64, module.indexFor(0))
        assertEquals(67, module.indexFor(3))

        // Set to armed
        module.matchIn.setValue(MATCH_ARMED)
        assertTrue(module.isArmed)
        assertTrue(module.learn.isHighlighted)
        assertEquals("?", module.badgeFor(module.learn))
    }

    @Test
    fun matchControl8HasEightOutputsAndMatchesSequentially() {
        val module = MatchControl8Module()
        assertEquals(8, module.numOutputs)
        assertEquals(8, module.stepOutputs.size)
        assertEquals("value0", module.stepOutputs[0].name)
        assertEquals("value7", module.stepOutputs[7].name)

        val recorder7 = Recorder()
        module.stepOutputs[7].connect(recorder7.input)

        // Teach CC 30
        module.input.receive(controlChange(index = 30.0, value = 0.1))
        assertEquals(30, module.matchNumber)
        assertEquals(30, module.indexFor(0))
        assertEquals(37, module.indexFor(7))

        // Send CC 37 -> should arrive at output 7
        module.input.receive(controlChange(index = 37.0, value = 0.88))
        assertEquals(listOf(0.88), recorder7.values)
        assertEquals(0.88, module.currentValues[7])
        assertEquals(0.88, module.getValue(7))
    }

    @Test
    fun survivesSaveAndReloadRoundTrip() {
        val state = GraphState()
        val module4 = MatchControl4Module()
        module4.input.receive(controlChange(index = 24.0, value = 0.5))
        state.addModule(module4, androidx.compose.ui.geometry.Offset.Zero)

        val module8 = MatchControl8Module()
        module8.input.receive(controlChange(index = 60.0, value = 0.3))
        state.addModule(module8, androidx.compose.ui.geometry.Offset.Zero)

        val json = GraphSerializer.serialize(state, state.modules)
        val reloaded = GraphState()
        GraphSerializer.deserialize(reloaded, json)

        val loaded4 = reloaded.modules.first { it is MatchControl4Module } as MatchControl4Module
        assertEquals(24, loaded4.matchNumber)
        assertFalse(loaded4.isArmed)
        assertEquals(24, loaded4.indexFor(0))
        assertEquals(27, loaded4.indexFor(3))

        val loaded8 = reloaded.modules.first { it is MatchControl8Module } as MatchControl8Module
        assertEquals(60, loaded8.matchNumber)
        assertFalse(loaded8.isArmed)
        assertEquals(60, loaded8.indexFor(0))
        assertEquals(67, loaded8.indexFor(7))
    }

    @Test
    fun conveniencePropertiesAndAliases() {
        val module = MatchControl4Module()
        assertEquals(module.stepOutputs[0], module.value0)
        assertEquals(module.stepOutputs[0], module.out0)
        assertEquals(module.stepOutputs[0], module.getValueJack(0))
        assertEquals(module.stepOutputs[0], module.getOutputJack(0))
        assertEquals(module.stepOutputs, module.valueOutputs)

        assertEquals(module.stepOutputs[1], module.value1)
        assertEquals(module.stepOutputs[1], module.out1)
        assertEquals(module.stepOutputs[2], module.value2)
        assertEquals(module.stepOutputs[2], module.out2)
        assertEquals(module.stepOutputs[3], module.value3)
        assertEquals(module.stepOutputs[3], module.out3)
    }

    @Test
    fun connectionByOutOrValueNameDeserializesSuccessfully() {
        val module = MatchControl4Module()
        val rec = Recorder()
        val state = GraphState()
        state.addModule(module, androidx.compose.ui.geometry.Offset.Zero)
        state.addModule(rec, androidx.compose.ui.geometry.Offset.Zero)

        // Connect using value0
        state.connect(module.value0, rec.input)
        val json = GraphSerializer.serialize(state, state.modules)

        // Replace value0 with out0 in json to simulate alternative connection
        val jsonWithOut0 = json.replace("\"fromJack\":\"value0\"", "\"fromJack\":\"out0\"")
        val reloaded = GraphState()
        val created = GraphSerializer.deserialize(reloaded, jsonWithOut0)
        assertTrue(created.isNotEmpty(), "Deserialization should succeed when connecting to out0")
        assertEquals(1, reloaded.connections.size)
        assertEquals("value0", reloaded.connections[0].output.name)
    }

    @Test
    fun stepInputJacksScaleIncomingControlChangesIndependently() {
        val module = MatchControl4Module()
        assertEquals(4, module.stepInputs.size)
        assertEquals("value0", module.stepInputs[0].name)
        assertEquals("value3", module.stepInputs[3].name)
        assertTrue(module.stepInputs.all { !it.isNodeVisible && it.isModuleEditorVisible })

        // Configure output 0 as linear 10..20, output 1 as exponential 100..400
        module.stepInputs[0].min = 10.0
        module.stepInputs[0].max = 20.0
        module.stepInputs[1].min = 100.0
        module.stepInputs[1].max = 400.0
        module.stepInputs[1].isExponential = true

        val rec0 = Recorder()
        val rec1 = Recorder()
        module.stepOutputs[0].connect(rec0.input)
        module.stepOutputs[1].connect(rec1.input)

        // Teach CC 16 at 0.5 -> output 0 gets 15.0
        module.input.receive(controlChange(index = 16.0, value = 0.5))
        assertEquals(listOf(15.0), rec0.values)
        assertEquals(15.0, module.currentValues[0])
        assertEquals(15.0, module.stepInputs[0].getValue())

        // CC 17 at 0.5 -> output 1 gets geometric midpoint 200.0
        module.input.receive(controlChange(index = 17.0, value = 0.5))
        assertEquals(listOf(200.0), rec1.values)
        assertEquals(200.0, module.currentValues[1])
        assertEquals(200.0, module.stepInputs[1].getValue())
    }
}

package com.syntona.dataflow.editor

import androidx.compose.ui.geometry.Offset
import com.syntona.dataflow.modules.AddModule
import com.syntona.dataflow.modules.AddTimeModule
import com.syntona.dataflow.modules.Keyboard13Module
import com.syntona.dataflow.modules.MatchNoteOnModule
import com.syntona.dataflow.modules.OneShotModule
import com.syntona.dataflow.modules.PassModule
import com.syntona.dataflow.modules.PrintModule
import com.syntona.dataflow.modules.SequenceModule
import com.syntona.dataflow.modules.ShowManyModule
import com.syntona.dataflow.modules.ShowModule
import com.syntona.dataflow.modules.PolyPatchModule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionCompatibilityTest {

    @Test
    fun testBlueEventCannotConnectToOrangeVoice() {
        val state = GraphState()
        val seq8 = SequenceModule(8)
        val polyPatch = PolyPatchModule("testPoly")
        val matchNote = MatchNoteOnModule()

        state.addModule(seq8, Offset.Zero)
        state.addModule(polyPatch, Offset.Zero)
        state.addModule(matchNote, Offset.Zero)

        // seq8.mainOutput is blue (OutputJack), polyPatch.input is orange (VoiceInputJack)
        assertFalse(state.canConnect(seq8.mainOutput, polyPatch.voiceInputJack))
        state.connect(seq8.mainOutput, polyPatch.voiceInputJack)
        assertTrue(state.connections.isEmpty())

        // seq8.mainOutput to matchNote.input
        assertFalse(state.canConnect(seq8.mainOutput, matchNote.input))
        state.connect(seq8.mainOutput, matchNote.input)
        assertTrue(state.connections.isEmpty())
    }

    @Test
    fun testOrangeVoiceCannotConnectToBlueEvent() {
        val state = GraphState()
        val keyboard = Keyboard13Module()
        val add = AddModule()

        state.addModule(keyboard, Offset.Zero)
        state.addModule(add, Offset.Zero)

        // keyboard.output is orange (VoiceOutputJack), add.inputA is blue (InputJack)
        assertFalse(state.canConnect(keyboard.output, add.inputA))
        state.connect(keyboard.output, add.inputA)
        assertTrue(state.connections.isEmpty())

        assertFalse(state.canConnect(keyboard.output, add.inputB))
        state.connect(keyboard.output, add.inputB)
        assertTrue(state.connections.isEmpty())
    }

    @Test
    fun testSameTypeConnectionsAllowed() {
        val state = GraphState()
        val seq8 = SequenceModule(8)
        val add = AddModule()
        val keyboard = Keyboard13Module()
        val polyPatch = PolyPatchModule("testPoly")

        state.addModule(seq8, Offset.Zero)
        state.addModule(add, Offset.Zero)
        state.addModule(keyboard, Offset.Zero)
        state.addModule(polyPatch, Offset.Zero)

        // Blue to blue
        assertTrue(state.canConnect(seq8.mainOutput, add.inputA))
        state.connect(seq8.mainOutput, add.inputA)
        assertTrue(state.connections.any { it.output === seq8.mainOutput && it.input === add.inputA })

        // Orange to orange
        assertTrue(state.canConnect(keyboard.output, polyPatch.voiceInputJack))
        state.connect(keyboard.output, polyPatch.voiceInputJack)
        assertTrue(state.connections.any { it.output === keyboard.output && it.input === polyPatch.voiceInputJack })
    }

    @Test
    fun testUniversalInputsAcceptBothBlueAndOrange() {
        val state = GraphState()
        val seq8 = SequenceModule(8)
        val keyboard = Keyboard13Module()
        val showMany = ShowManyModule()
        val addTime = AddTimeModule()
        val print = PrintModule()
        val show = ShowModule()
        val oneShot = OneShotModule()
        val pass = PassModule()

        state.addModule(seq8, Offset.Zero)
        state.addModule(keyboard, Offset.Zero)
        state.addModule(showMany, Offset.Zero)
        state.addModule(addTime, Offset.Zero)
        state.addModule(print, Offset.Zero)
        state.addModule(show, Offset.Zero)
        state.addModule(oneShot, Offset.Zero)
        state.addModule(pass, Offset.Zero)

        // Blue output to universal inputs
        assertTrue(state.canConnect(seq8.mainOutput, showMany.input))
        assertTrue(state.canConnect(seq8.mainOutput, addTime.input))
        assertTrue(state.canConnect(seq8.mainOutput, print.input))
        assertTrue(state.canConnect(seq8.mainOutput, show.input))
        assertTrue(state.canConnect(seq8.mainOutput, oneShot.kickInput))
        assertTrue(state.canConnect(seq8.mainOutput, pass.input))

        // Orange output to universal inputs
        assertTrue(state.canConnect(keyboard.output, showMany.input))
        assertTrue(state.canConnect(keyboard.output, addTime.input))
        assertTrue(state.canConnect(keyboard.output, print.input))
        assertTrue(state.canConnect(keyboard.output, show.input))
        assertTrue(state.canConnect(keyboard.output, oneShot.kickInput))
        assertTrue(state.canConnect(keyboard.output, pass.input))

        // Ensure connections can actually be created
        state.connect(seq8.mainOutput, showMany.input)
        assertTrue(state.connections.any { it.output === seq8.mainOutput && it.input === showMany.input })

        state.connect(keyboard.output, addTime.input)
        assertTrue(state.connections.any { it.output === keyboard.output && it.input === addTime.input })
    }

    @Test
    fun testUniversalOutputsCanConnectToBlueOrangeAndUniversal() {
        val state = GraphState()
        val addTime = AddTimeModule()
        val add = AddModule()
        val polyPatch = PolyPatchModule("testPoly")
        val showMany = ShowManyModule()

        state.addModule(addTime, Offset.Zero)
        state.addModule(add, Offset.Zero)
        state.addModule(polyPatch, Offset.Zero)
        state.addModule(showMany, Offset.Zero)

        // Universal output to blue event input
        assertTrue(state.canConnect(addTime.output, add.inputA))
        state.connect(addTime.output, add.inputA)
        assertTrue(state.connections.any { it.output === addTime.output && it.input === add.inputA })

        // Universal output to orange voice input
        assertTrue(state.canConnect(addTime.output, polyPatch.voiceInputJack))
        state.connect(addTime.output, polyPatch.voiceInputJack)
        assertTrue(state.connections.any { it.output === addTime.output && it.input === polyPatch.voiceInputJack })

        // Universal output to universal input
        assertTrue(state.canConnect(addTime.output, showMany.input))
        state.connect(addTime.output, showMany.input)
        assertTrue(state.connections.any { it.output === addTime.output && it.input === showMany.input })
    }
}

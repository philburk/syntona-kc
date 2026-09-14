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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchNoteOnSequentialModuleTest {

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

    private fun noteOn(tag: Int, amplitude: Double) =
        Message.VoiceMessage(testTime, Message.VoiceOpcode.NOTE_ON, tag, tag.toDouble(), amplitude)

    private fun noteOff(tag: Int) =
        Message.VoiceMessage(testTime, Message.VoiceOpcode.NOTE_OFF, tag, tag.toDouble(), 0.0)

    @Test
    fun isRegisteredInTheMidiBundle() {
        val creator4 = ModuleRegistry.findCreator("matchNoteOn4")
        assertNotNull(creator4, "matchNoteOn4 should be registered")
        assertTrue(creator4() is MatchNoteOn4Module)
        assertEquals("music.midi.matchNoteOn4", ModuleRegistry.getBundlePath("matchNoteOn4"))

        val creator8 = ModuleRegistry.findCreator("matchNoteOn8")
        assertNotNull(creator8, "matchNoteOn8 should be registered")
        assertTrue(creator8() is MatchNoteOn8Module)
        assertEquals("music.midi.matchNoteOn8", ModuleRegistry.getBundlePath("matchNoteOn8"))
    }

    @Test
    fun legacyNameResolutionWorks() {
        val creator4 = ModuleRegistry.findCreator("matchNote4")
        assertNotNull(creator4, "matchNote4 should resolve to MatchNoteOn4Module")
        assertTrue(creator4() is MatchNoteOn4Module)

        val creator8 = ModuleRegistry.findCreator("matchNote8")
        assertNotNull(creator8, "matchNote8 should resolve to MatchNoteOn8Module")
        assertTrue(creator8() is MatchNoteOn8Module)
    }

    @Test
    fun initialStateIsArmedWithQuestionMarkBadge() {
        val module = MatchNoteOn4Module()
        assertTrue(module.isArmed)
        assertTrue(module.learn.isHighlighted)
        assertEquals("?", module.badgeFor(module.learn))
        assertEquals(-1, module.matchNumber)
        assertEquals(4, module.numOutputs)
        assertEquals(4, module.stepOutputs.size)
        assertEquals("amplitude0", module.stepOutputs[0].name)
        assertEquals("amplitude3", module.stepOutputs[3].name)
        assertEquals("tag", module.matchIn.name)
        assertEquals("in", module.input.name)
        assertEquals("unmatched", module.unmatched.name)

        assertEquals(-1, module.indexFor(0))
        assertEquals(-1, module.indexFor(1))
    }

    @Test
    fun learningFirstNoteSetsBaseAndDisarmsAndEmitsVelocityOnAmplitude0() {
        val module = MatchNoteOn4Module()
        val recorder0 = Recorder()
        val voiceRecorder = VoiceRecorder()
        module.amplitude0.connect(recorder0.input)
        module.unmatched.connect(voiceRecorder.input)

        // Hit pad 36 (Bass drum) with velocity 0.85
        module.input.receive(noteOn(tag = 36, amplitude = 0.85))

        assertEquals(36, module.matchNumber)
        assertFalse(module.isArmed)
        assertFalse(module.learn.isHighlighted)
        assertEquals("36", module.badgeFor(module.learn))
        assertEquals(36.0, module.matchIn.getDoubleValue())

        assertEquals(listOf(0.85), recorder0.values, "First learned note should output amplitude on amplitude0")
        assertEquals(0.85, module.currentAmplitudes[0])
        assertEquals(0.85, module.getAmplitude(0))
        assertEquals(1, module.triggerCounts[0])
        assertEquals(1, module.getTriggerCount(0))
        assertEquals(0, module.triggerCounts[1])
        assertTrue(voiceRecorder.messages.isEmpty(), "Learned note should NOT pass to unmatched")

        assertEquals(36, module.indexFor(0))
        assertEquals(37, module.indexFor(1))
        assertEquals(38, module.indexFor(2))
        assertEquals(39, module.indexFor(3))
        assertEquals(-1, module.indexFor(4))
    }

    @Test
    fun subsequentNotesRouteSequentiallyToAmplitudeJacks() {
        val module = MatchNoteOn4Module()
        val r0 = Recorder()
        val r1 = Recorder()
        val r2 = Recorder()
        val r3 = Recorder()
        module.stepOutputs[0].connect(r0.input)
        module.stepOutputs[1].connect(r1.input)
        module.stepOutputs[2].connect(r2.input)
        module.stepOutputs[3].connect(r3.input)

        // Learn base note 40
        module.input.receive(noteOn(tag = 40, amplitude = 0.5))
        assertEquals(listOf(0.5), r0.values)

        // Hit notes 41, 42, 43
        module.input.receive(noteOn(tag = 41, amplitude = 0.6))
        module.input.receive(noteOn(tag = 42, amplitude = 0.7))
        module.input.receive(noteOn(tag = 43, amplitude = 0.8))

        assertEquals(listOf(0.6), r1.values)
        assertEquals(listOf(0.7), r2.values)
        assertEquals(listOf(0.8), r3.values)

        assertEquals(0.5, module.currentAmplitudes[0])
        assertEquals(0.6, module.currentAmplitudes[1])
        assertEquals(0.7, module.currentAmplitudes[2])
        assertEquals(0.8, module.currentAmplitudes[3])

        assertEquals(1, module.triggerCounts[0])
        assertEquals(1, module.triggerCounts[1])
        assertEquals(1, module.triggerCounts[2])
        assertEquals(1, module.triggerCounts[3])
    }

    @Test
    fun noteOffForMatchedNotesIsFilteredFromUnmatchedAndDoesNotEmitAmplitude() {
        val module = MatchNoteOn4Module()
        val r0 = Recorder()
        val r1 = Recorder()
        val voiceRecorder = VoiceRecorder()
        module.stepOutputs[0].connect(r0.input)
        module.stepOutputs[1].connect(r1.input)
        module.unmatched.connect(voiceRecorder.input)

        // Learn note 40
        module.input.receive(noteOn(tag = 40, amplitude = 0.5))
        r0.values.clear()
        assertEquals(1, module.triggerCounts[0])

        // Send NOTE_OFF for note 40 and note 41
        module.input.receive(noteOff(tag = 40))
        module.input.receive(noteOff(tag = 41))

        assertTrue(r0.values.isEmpty(), "NOTE_OFF must not emit on amplitude outputs")
        assertTrue(r1.values.isEmpty(), "NOTE_OFF must not emit on amplitude outputs")
        assertTrue(voiceRecorder.messages.isEmpty(), "NOTE_OFF for matched notes must be filtered from unmatched")
        assertEquals(1, module.triggerCounts[0], "NOTE_OFF should not increment triggerCounts")
        assertEquals(0, module.triggerCounts[1], "NOTE_OFF should not increment triggerCounts")
    }

    @Test
    fun nonMatchedNotesAndOtherVoiceMessagesPassThroughUnmatched() {
        val module = MatchNoteOn4Module()
        val r0 = Recorder()
        val voiceRecorder = VoiceRecorder()
        module.stepOutputs[0].connect(r0.input)
        module.unmatched.connect(voiceRecorder.input)

        // Set base to 40 directly
        module.matchIn.setValue(40.0)

        val outOfRangeBelowOn = noteOn(tag = 39, amplitude = 0.5)
        val outOfRangeBelowOff = noteOff(tag = 39)
        val outOfRangeAboveOn = noteOn(tag = 44, amplitude = 0.9)
        val outOfRangeAboveOff = noteOff(tag = 44)
        val cc = Message.VoiceMessage(testTime, Message.VoiceOpcode.CONTROL_CHANGE, -1, 0.5, 7.0)
        val bend = Message.VoiceMessage(testTime, Message.VoiceOpcode.PITCH_BEND, -1, 0.25, 0.0)

        module.input.receive(outOfRangeBelowOn)
        module.input.receive(outOfRangeBelowOff)
        module.input.receive(outOfRangeAboveOn)
        module.input.receive(outOfRangeAboveOff)
        module.input.receive(cc)
        module.input.receive(bend)

        assertTrue(r0.values.isEmpty(), "Unmatched messages must not emit on amplitude outputs")
        assertEquals(
            listOf(outOfRangeBelowOn, outOfRangeBelowOff, outOfRangeAboveOn, outOfRangeAboveOff, cc, bend),
            voiceRecorder.messages,
            "Unmatched messages must pass through to unmatched output"
        )
    }

    @Test
    fun whenArmedNonNoteOnMessagesPassToUnmatchedWithoutDisarming() {
        val module = MatchNoteOn4Module()
        val voiceRecorder = VoiceRecorder()
        module.unmatched.connect(voiceRecorder.input)

        assertTrue(module.isArmed)

        val off = noteOff(tag = 36)
        val cc = Message.VoiceMessage(testTime, Message.VoiceOpcode.CONTROL_CHANGE, -1, 0.5, 10.0)

        module.input.receive(off)
        module.input.receive(cc)

        assertTrue(module.isArmed, "Module should remain armed")
        assertEquals(listOf(off, cc), voiceRecorder.messages)
    }

    @Test
    fun theLearnButtonRearmsIt() {
        val module = MatchNoteOn4Module()
        val recorder0 = Recorder()
        module.stepOutputs[0].connect(recorder0.input)

        module.input.receive(noteOn(tag = 36, amplitude = 0.7))
        assertEquals(36, module.matchNumber)

        // Rearm
        module.learn.receive(Message.FloatValue(testTime, 0.0))
        assertTrue(module.isArmed)
        assertTrue(module.learn.isHighlighted)
        assertEquals("?", module.badgeFor(module.learn))

        recorder0.values.clear()
        // Teach new base note 48
        module.input.receive(noteOn(tag = 48, amplitude = 0.95))
        assertEquals(48, module.matchNumber)
        assertFalse(module.isArmed)
        assertEquals("48", module.badgeFor(module.learn))
        assertEquals(listOf(0.95), recorder0.values)

        assertEquals(48, module.indexFor(0))
        assertEquals(49, module.indexFor(1))
        assertEquals(50, module.indexFor(2))
        assertEquals(51, module.indexFor(3))
    }

    @Test
    fun settingTagJackDirectlyUpdatesIndicesOrArms() {
        val module = MatchNoteOn4Module()
        module.matchIn.setValue(60.0)

        assertFalse(module.isArmed)
        assertFalse(module.learn.isHighlighted)
        assertEquals(60, module.matchNumber)
        assertEquals("60", module.badgeFor(module.learn))
        assertEquals(60, module.indexFor(0))
        assertEquals(63, module.indexFor(3))

        // Set to armed
        module.matchIn.setValue(MATCH_ARMED)
        assertTrue(module.isArmed)
        assertTrue(module.learn.isHighlighted)
        assertEquals("?", module.badgeFor(module.learn))
    }

    @Test
    fun matchNoteOn8HasEightOutputsAndMatchesSequentially() {
        val module = MatchNoteOn8Module()
        assertEquals(8, module.numOutputs)
        assertEquals(8, module.stepOutputs.size)
        assertEquals("amplitude0", module.stepOutputs[0].name)
        assertEquals("amplitude7", module.stepOutputs[7].name)

        val recorder7 = Recorder()
        module.stepOutputs[7].connect(recorder7.input)

        // Teach note 36
        module.input.receive(noteOn(tag = 36, amplitude = 0.5))
        assertEquals(36, module.matchNumber)
        assertEquals(36, module.indexFor(0))
        assertEquals(43, module.indexFor(7))

        // Send note 43 -> arrives on output 7
        module.input.receive(noteOn(tag = 43, amplitude = 0.88))
        assertEquals(listOf(0.88), recorder7.values)
        assertEquals(0.88, module.currentAmplitudes[7])
        assertEquals(0.88, module.getAmplitude(7))
    }

    @Test
    fun survivesSaveAndReloadRoundTrip() {
        val state = GraphState()
        val module4 = MatchNoteOn4Module()
        module4.input.receive(noteOn(tag = 36, amplitude = 0.5))
        state.addModule(module4, androidx.compose.ui.geometry.Offset.Zero)

        val module8 = MatchNoteOn8Module()
        module8.input.receive(noteOn(tag = 48, amplitude = 0.7))
        state.addModule(module8, androidx.compose.ui.geometry.Offset.Zero)

        val json = GraphSerializer.serialize(state, state.modules)
        val reloaded = GraphState()
        GraphSerializer.deserialize(reloaded, json)

        val loaded4 = reloaded.modules.first { it is MatchNoteOn4Module } as MatchNoteOn4Module
        assertEquals(36, loaded4.matchNumber)
        assertFalse(loaded4.isArmed)
        assertEquals(36, loaded4.indexFor(0))
        assertEquals(39, loaded4.indexFor(3))

        val loaded8 = reloaded.modules.first { it is MatchNoteOn8Module } as MatchNoteOn8Module
        assertEquals(48, loaded8.matchNumber)
        assertFalse(loaded8.isArmed)
        assertEquals(48, loaded8.indexFor(0))
        assertEquals(55, loaded8.indexFor(7))
    }

    @Test
    fun conveniencePropertiesAndHelpers() {
        val module = MatchNoteOn4Module()
        assertEquals(module.stepOutputs[0], module.amplitude0)
        assertEquals(module.stepOutputs[0], module.getAmplitudeJack(0))
        assertEquals(module.stepOutputs, module.amplitudeOutputs)

        assertEquals(module.stepOutputs[1], module.amplitude1)
        assertEquals(module.stepOutputs[2], module.amplitude2)
        assertEquals(module.stepOutputs[3], module.amplitude3)
        assertNull(module.amplitude4)

        val module8 = MatchNoteOn8Module()
        assertNotNull(module8.amplitude7)
        assertEquals(module8.stepOutputs[7], module8.amplitude7)
    }
}

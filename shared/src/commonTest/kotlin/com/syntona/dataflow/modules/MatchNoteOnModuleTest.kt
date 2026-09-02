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

/**
 * matchNoteOn: follows one note and emits its amplitude on every strike, for triggering from a
 * drum pad or a single key.
 */
class MatchNoteOnModuleTest {

    private val testTime = ScoreTime(100L)

    /** Records values arriving on the module's output. */
    private class Recorder : Module("recorder") {
        val input = addInput("in", isKickable = true)
        val values = mutableListOf<Double>()

        override fun execute(triggerMessage: Message) {
            if (triggerMessage is Message.FloatValue) values.add(triggerMessage.value)
        }
    }

    private fun noteOn(tag: Int, amplitude: Double) =
        Message.VoiceMessage(
            testTime, Message.VoiceOpcode.NOTE_ON, tag, tag.toDouble(), amplitude
        )

    private fun matcherWithRecorder(): Pair<MatchNoteOnModule, Recorder> {
        val module = MatchNoteOnModule()
        val recorder = Recorder()
        module.output.connect(recorder.input)
        return module to recorder
    }

    @Test
    fun isRegisteredInTheMidiBundle() {
        val creator = ModuleRegistry.findCreator("matchNoteOn")
        assertNotNull(creator, "matchNoteOn should be registered")
        assertTrue(creator() is MatchNoteOnModule)
        assertEquals("music.midi.matchNoteOn", ModuleRegistry.getBundlePath("matchNoteOn"))
    }

    @Test
    fun itsJacksAreNamedForNotes() {
        val module = MatchNoteOnModule()
        assertNotNull(module.inputs.find { it.name == "tag" }, "matches on the note's tag")
        assertNotNull(module.outputs.find { it.name == "amplitude" }, "emits how hard it was hit")
        assertNotNull(module.outputs.find { it.name == "unmatched" }, "emits unmatched voice messages")
    }

    @Test
    fun passesUnmatchedNotesThroughUnmatchedOutput() {
        val module = MatchNoteOnModule()
        val ampRecorder = Recorder()
        module.output.connect(ampRecorder.input)

        val unmatchedVoiceMsgs = mutableListOf<Message.VoiceMessage>()
        val voiceRecorder = object : Module("voiceRecorder") {
            val input = addVoiceInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.VoiceMessage) unmatchedVoiceMsgs.add(triggerMessage)
            }
        }
        module.unmatched.connect(voiceRecorder.input)

        // Set module to match note 61 (e.g. C#4 black key)
        module.matchIn.setValue(61.0)

        // 1. Play matched note 61 (ON then OFF)
        val on61 = noteOn(tag = 61, amplitude = 0.8)
        val off61 = Message.VoiceMessage(testTime, Message.VoiceOpcode.NOTE_OFF, 61, 61.0, 0.0)
        module.input.receive(on61)
        module.input.receive(off61)

        assertEquals(listOf(0.8), ampRecorder.values, "Matched note should trigger amplitude output")
        assertTrue(unmatchedVoiceMsgs.isEmpty(), "Matched note (both NOTE_ON and NOTE_OFF) must NOT appear on unmatched")

        // 2. Play other notes (e.g. note 60 C4, note 62 D4)
        val on60 = noteOn(tag = 60, amplitude = 0.7)
        val off60 = Message.VoiceMessage(testTime, Message.VoiceOpcode.NOTE_OFF, 60, 60.0, 0.0)
        val on62 = noteOn(tag = 62, amplitude = 0.9)
        val off62 = Message.VoiceMessage(testTime, Message.VoiceOpcode.NOTE_OFF, 62, 62.0, 0.0)

        module.input.receive(on60)
        module.input.receive(off60)
        module.input.receive(on62)
        module.input.receive(off62)

        assertEquals(listOf(0.8), ampRecorder.values, "Other notes must not trigger amplitude output")
        assertEquals(listOf(on60, off60, on62, off62), unmatchedVoiceMsgs, "Unmatched notes must pass through cleanly")

        // 3. Pitch bend and control change pass through to unmatched
        val bend = Message.VoiceMessage(testTime, Message.VoiceOpcode.PITCH_BEND, -1, 0.5, 0.0)
        module.input.receive(bend)
        assertEquals(listOf(on60, off60, on62, off62, bend), unmatchedVoiceMsgs)
    }

    @Test
    fun canChainMultipleMatchModulesToFilterMultipleTriggerKeys() {
        // Chain: in -> matcher1 (filters note 61) -> unmatched -> matcher2 (filters note 63) -> unmatched -> downstream
        val match61 = MatchNoteOnModule().apply { matchIn.setValue(61.0) }
        val match63 = MatchNoteOnModule().apply { matchIn.setValue(63.0) }
        match61.unmatched.connect(match63.input)

        val trigger61Recorder = Recorder()
        val trigger63Recorder = Recorder()
        match61.output.connect(trigger61Recorder.input)
        match63.output.connect(trigger63Recorder.input)

        val remainingMsgs = mutableListOf<Message.VoiceMessage>()
        val polyPatchRecorder = object : Module("polyPatchRecorder") {
            val input = addVoiceInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.VoiceMessage) remainingMsgs.add(triggerMessage)
            }
        }
        match63.unmatched.connect(polyPatchRecorder.input)

        val on61 = noteOn(tag = 61, amplitude = 0.6)
        val on63 = noteOn(tag = 63, amplitude = 0.9)
        val on60 = noteOn(tag = 60, amplitude = 0.7)

        match61.input.receive(on61)
        match61.input.receive(on63)
        match61.input.receive(on60)

        assertEquals(listOf(0.6), trigger61Recorder.values)
        assertEquals(listOf(0.9), trigger63Recorder.values)
        assertEquals(listOf(on60), remainingMsgs, "Only un-filtered notes reach downstream")
    }

    @Test
    fun startsArmedSoItCanBeTaught() {
        val module = MatchNoteOnModule()
        assertTrue(module.isArmed)
        assertEquals(-1, module.matchNumber)
        assertTrue(module.learn.isHighlighted, "the learn button should be lit while armed")
    }

    @Test
    fun learnsTheFirstPadStruckAndPassesItsAmplitudeOn() {
        val (module, recorder) = matcherWithRecorder()

        module.input.receive(noteOn(tag = 38, amplitude = 0.8))

        assertEquals(38, module.matchNumber)
        assertFalse(module.isArmed)
        // The pad that taught it already triggers, without needing a second hit.
        assertEquals(listOf(0.8), recorder.values)
    }

    @Test
    fun followsOnlyTheNoteItLearned() {
        val (module, recorder) = matcherWithRecorder()
        module.input.receive(noteOn(tag = 38, amplitude = 0.1))
        recorder.values.clear()

        module.input.receive(noteOn(tag = 38, amplitude = 0.9))
        module.input.receive(noteOn(tag = 36, amplitude = 0.5))
        module.input.receive(noteOn(tag = 38, amplitude = 0.3))

        assertEquals(listOf(0.9, 0.3), recorder.values, "another pad must not get through")
        assertEquals(38, module.matchNumber, "a stray note must not steal the assignment")
    }

    @Test
    fun eachStrikeCarriesItsOwnDynamics() {
        // The point of emitting amplitude rather than a bare trigger: a hard hit and a soft one
        // are distinguishable downstream.
        val (module, recorder) = matcherWithRecorder()
        module.matchIn.setValue(38.0)

        module.input.receive(noteOn(tag = 38, amplitude = 0.2))
        module.input.receive(noteOn(tag = 38, amplitude = 1.0))

        assertEquals(listOf(0.2, 1.0), recorder.values)
    }

    @Test
    fun aNoteOffDoesNotTrigger() {
        val (module, recorder) = matcherWithRecorder()
        module.matchIn.setValue(38.0)

        module.input.receive(
            Message.VoiceMessage(testTime, Message.VoiceOpcode.NOTE_OFF, 38, 38.0, 0.0)
        )

        assertTrue(recorder.values.isEmpty(), "a pad fires when struck, not when released")
    }

    @Test
    fun aControllerIsNotMistakenForANote() {
        val (module, recorder) = matcherWithRecorder()

        module.input.receive(
            Message.VoiceMessage(testTime, Message.VoiceOpcode.CONTROL_CHANGE, -1, 0.5, 74.0)
        )

        assertTrue(module.isArmed, "a knob must not claim a module waiting for a pad")
        assertTrue(recorder.values.isEmpty())
    }

    @Test
    fun theLearnButtonRearmsIt() {
        val (module, recorder) = matcherWithRecorder()
        module.input.receive(noteOn(tag = 38, amplitude = 0.5))
        assertEquals(38, module.matchNumber)

        module.learn.receive(Message.FloatValue(testTime, 0.0))
        assertTrue(module.isArmed)
        assertEquals("?", module.badgeFor(module.learn))

        recorder.values.clear()
        module.input.receive(noteOn(tag = 36, amplitude = 0.4))
        assertEquals(36, module.matchNumber, "rearming lets a different pad take over")
        assertEquals(listOf(0.4), recorder.values)
        assertEquals("36", module.badgeFor(module.learn))
    }

    @Test
    fun theLearnedNoteSurvivesSaveAndReload() {
        val state = GraphState()
        val module = MatchNoteOnModule()
        module.input.receive(noteOn(tag = 38, amplitude = 0.5))
        state.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        val reloaded = GraphState()
        GraphSerializer.deserialize(reloaded, GraphSerializer.serialize(state, state.modules))
        val loaded = reloaded.modules.first { it is MatchNoteOnModule } as MatchNoteOnModule

        assertEquals(38, loaded.matchNumber)
        assertFalse(loaded.isArmed, "a saved patch should not re-learn on load")
    }
}

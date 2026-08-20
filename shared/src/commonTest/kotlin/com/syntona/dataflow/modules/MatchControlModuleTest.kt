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
 * matchControl: follows one MIDI controller and ignores the rest, learning which one it is from
 * the next controller to move while it is armed.
 */
class MatchControlModuleTest {

    private val testTime = ScoreTime(100L)

    /** Records values arriving on the module's output. */
    private class Recorder : Module("recorder") {
        val input = addInput("in", isKickable = true)
        val values = mutableListOf<Double>()

        override fun execute(triggerMessage: Message) {
            if (triggerMessage is Message.FloatValue) values.add(triggerMessage.value)
        }
    }

    private fun controlChange(index: Double, value: Double) =
        Message.VoiceMessage(testTime, Message.VoiceOpcode.CONTROL_CHANGE, -1, value, index)

    private fun matcherWithRecorder(): Pair<MatchControlModule, Recorder> {
        val module = MatchControlModule()
        val recorder = Recorder()
        module.output.connect(recorder.input)
        return module to recorder
    }

    @Test
    fun isRegisteredInTheVoicesBundle() {
        val creator = ModuleRegistry.findCreator("matchControl")
        assertNotNull(creator, "matchControl should be registered")
        assertTrue(creator() is MatchControlModule)
        assertEquals("music.midi.matchControl", ModuleRegistry.getBundlePath("matchControl"))
    }

    @Test
    fun startsArmedSoItCanBeTaught() {
        val module = MatchControlModule()
        assertTrue(module.isArmed)
        assertEquals(-1, module.matchNumber)
        assertTrue(module.learn.isHighlighted, "the learn button should be lit while armed")
    }

    @Test
    fun learnsTheFirstControllerAndPassesItsValueOn() {
        val (module, recorder) = matcherWithRecorder()

        module.input.receive(controlChange(index = 74.0, value = 0.5))

        assertEquals(74, module.matchNumber)
        assertFalse(module.isArmed)
        assertFalse(module.learn.isHighlighted, "the light goes out once it has learned")
        // The knob that taught it is already in control, without needing a second nudge.
        assertEquals(listOf(0.5), recorder.values)
    }

    @Test
    fun followsOnlyTheControllerItLearned() {
        val (module, recorder) = matcherWithRecorder()
        module.input.receive(controlChange(index = 74.0, value = 0.1))
        recorder.values.clear()

        module.input.receive(controlChange(index = 74.0, value = 0.25))
        module.input.receive(controlChange(index = 7.0, value = 0.99))
        module.input.receive(controlChange(index = 74.0, value = 0.75))

        assertEquals(listOf(0.25, 0.75), recorder.values, "another knob must not get through")
        assertEquals(74, module.matchNumber, "a stray controller must not steal the assignment")
    }

    @Test
    fun theLearnButtonRearmsIt() {
        val (module, recorder) = matcherWithRecorder()
        module.input.receive(controlChange(index = 74.0, value = 0.1))
        assertEquals(74, module.matchNumber)

        module.learn.receive(Message.FloatValue(testTime, 0.0))

        assertTrue(module.isArmed)
        assertTrue(module.learn.isHighlighted)

        recorder.values.clear()
        module.input.receive(controlChange(index = 7.0, value = 0.6))
        assertEquals(7, module.matchNumber, "rearming lets a different knob take over")
        assertEquals(listOf(0.6), recorder.values)
    }

    @Test
    fun settingTheIndexJackToMinusOneAlsoArmsIt() {
        val module = MatchControlModule()
        module.matchIn.setValue(20.0)
        assertFalse(module.isArmed)
        assertFalse(module.learn.isHighlighted)

        // The jack is the state; the button is one way of setting it, a wire is another.
        module.matchIn.setValue(MATCH_ARMED)

        assertTrue(module.isArmed)
        assertTrue(module.learn.isHighlighted, "the button follows the jack, however it was set")
    }

    @Test
    fun theBadgeAsksWhileArmedAndAnswersOnceLearned() {
        val module = MatchControlModule()
        assertEquals("?", module.badgeFor(module.learn), "an armed module should ask")

        module.input.receive(controlChange(index = 74.0, value = 0.5))
        assertEquals("74", module.badgeFor(module.learn), "the caught controller should show")

        module.learn.receive(Message.FloatValue(testTime, 0.0))
        assertEquals("?", module.badgeFor(module.learn), "rearming asks again")
    }

    @Test
    fun onlyTheLearnButtonCarriesABadge() {
        val module = MatchControlModule()
        val otherCommandJack = ArmModule().armInput
        assertEquals(null, module.badgeFor(otherCommandJack))
    }

    @Test
    fun theIndexMirrorTracksTheJack() {
        // The badge reads this mirror rather than the jack, because jack values are not Compose
        // state and a node reading one would never repaint when a controller was learned.
        val module = MatchControlModule()
        assertEquals(-1, module.matchNumber)

        module.matchIn.setValue(64.0)
        assertEquals(64, module.matchNumber)
        assertEquals("64", module.badgeFor(module.learn))

        module.matchIn.setValue(MATCH_ARMED)
        assertEquals(-1, module.matchNumber)
    }

    @Test
    fun ignoresMessagesThatAreNotControlChanges() {
        val (module, recorder) = matcherWithRecorder()

        module.input.receive(
            Message.VoiceMessage(testTime, Message.VoiceOpcode.NOTE_ON, 60, 60.0, 0.8)
        )
        module.input.receive(Message.FloatValue(testTime, 42.0))

        assertTrue(module.isArmed, "a note must not be mistaken for a controller assignment")
        assertTrue(recorder.values.isEmpty())
    }

    @Test
    fun theLearnedControllerSurvivesSaveAndReload() {
        val state = GraphState()
        val module = MatchControlModule()
        module.input.receive(controlChange(index = 74.0, value = 0.5))
        state.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        val reloaded = GraphState()
        GraphSerializer.deserialize(reloaded, GraphSerializer.serialize(state, state.modules))
        val loaded = reloaded.modules.first { it is MatchControlModule } as MatchControlModule

        assertEquals(74, loaded.matchNumber)
        assertFalse(loaded.isArmed, "a saved patch should not re-learn on load")
        assertFalse(loaded.learn.isHighlighted)
    }

    @Test
    fun currentValueTracksIncomingControlValues() {
        val module = MatchControlModule()
        assertEquals(0.0, module.currentValue)

        // Learn CC 1 (ModWheel) at value 0.3
        module.input.receive(controlChange(index = 1.0, value = 0.3))
        assertEquals(0.3, module.currentValue)
        assertEquals(0.3, module.output.getValue())

        // Move to 0.8
        module.input.receive(controlChange(index = 1.0, value = 0.8))
        assertEquals(0.8, module.currentValue)
        assertEquals(0.8, module.output.getValue())

        // Different controller ignored
        module.input.receive(controlChange(index = 7.0, value = 0.1))
        assertEquals(0.8, module.currentValue)
    }
}

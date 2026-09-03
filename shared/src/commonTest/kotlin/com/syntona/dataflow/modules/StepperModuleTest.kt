package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
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

class StepperModuleTest {

    private class TestReceiver : Module("receiver") {
        val inJack = addInput("in", isKickable = true)
        val values = mutableListOf<Double>()

        override fun execute(triggerMessage: Message) {
            if (triggerMessage is Message.FloatValue) {
                values.add(triggerMessage.value)
            }
        }
    }

    @Test
    fun testModuleRegistrationAndJackTypes() {
        val creator = ModuleRegistry.findCreator("stepper")
        assertNotNull(creator, "stepper should be registered in ModuleRegistry")
        val module = creator()
        assertTrue(module is StepperModule, "Creator should return StepperModule")
        assertEquals("music.compose.stepper", ModuleRegistry.getBundlePath("stepper"))

        val stepper = module as StepperModule
        assertEquals("start", stepper.start.name)
        assertTrue(stepper.start.isKickable)

        assertEquals("stop", stepper.stop.name)
        assertTrue(stepper.stop.isKickable)

        assertEquals("kick", stepper.kick.name)
        assertTrue(stepper.kick.isKickable)
        assertFalse(stepper.kick.isInteger)

        assertEquals("N", stepper.limit.name)
        assertTrue(stepper.limit.isInteger)
        assertEquals(1.0, stepper.limit.min)
        assertEquals(128.0, stepper.limit.max)
        assertEquals(4.0, stepper.limit.getValue())

        assertEquals("looping", stepper.loop.name)
        assertEquals("looping", stepper.looping.name)
        assertTrue(stepper.loop is com.syntona.dataflow.model.BooleanInputJack)
        assertTrue(stepper.loop.isInteger)
        assertFalse(stepper.loop.isValueDraggable)
        assertFalse(stepper.loop.isDisplayableValue)
        assertFalse(stepper.loop.isModuleEditorVisible)
        assertEquals(0.0, stepper.loop.min)
        assertEquals(1.0, stepper.loop.max)
        assertEquals(0.0, stepper.loop.getValue())
        assertFalse(stepper.loop.isChecked)

        assertEquals("index", stepper.indexOut.name)
        assertTrue(stepper.indexOut.isInteger)

        assertEquals("done", stepper.doneOut.name)
        assertFalse(stepper.doneOut.isInteger)
    }

    @Test
    fun testDormantIgnoresKick() {
        val stepper = StepperModule()
        val indexReceiver = TestReceiver()
        val doneReceiver = TestReceiver()
        stepper.indexOut.connect(indexReceiver.inJack)
        stepper.doneOut.connect(doneReceiver.inJack)

        assertFalse(stepper.isRunning)
        assertNull(stepper.badgeFor(stepper.start))

        // Pulses arriving at kick while dormant should be completely ignored
        for (i in 0..5) {
            stepper.kick.receive(Message.FloatValue(ScoreTime(i.toLong()), 1.0))
        }

        assertTrue(indexReceiver.values.isEmpty())
        assertTrue(doneReceiver.values.isEmpty())
        assertFalse(stepper.isRunning)
    }

    @Test
    fun testStartActivation() {
        val stepper = StepperModule()
        val indexReceiver = TestReceiver()
        stepper.indexOut.connect(indexReceiver.inJack)

        stepper.start.receive(Message.FloatValue(ScoreTime.Zero, 1.0))

        assertTrue(stepper.isRunning)
        assertEquals(0, stepper.currentIndex)
        assertEquals(listOf(0.0), indexReceiver.values)
        assertEquals("RUN", stepper.badgeFor(stepper.start))
        assertTrue(stepper.start.isHighlighted)
    }

    @Test
    fun testIterationAndCompletionWithoutLoop() {
        val stepper = StepperModule()
        stepper.limit.setValue(3.0) // 3 steps: 0, 1, 2
        val indexReceiver = TestReceiver()
        val doneReceiver = TestReceiver()
        stepper.indexOut.connect(indexReceiver.inJack)
        stepper.doneOut.connect(doneReceiver.inJack)

        // Wake module: beat 0
        stepper.start.receive(Message.FloatValue(ScoreTime(0), 1.0))
        assertEquals(listOf(0.0), indexReceiver.values)
        assertTrue(doneReceiver.values.isEmpty())
        assertTrue(stepper.isRunning)

        // 1st kick: advances to 1
        stepper.kick.receive(Message.FloatValue(ScoreTime(1), 1.0))
        assertEquals(listOf(0.0, 1.0), indexReceiver.values)
        assertTrue(doneReceiver.values.isEmpty())
        assertTrue(stepper.isRunning)

        // 2nd kick: advances to 2 (N - 1)
        stepper.kick.receive(Message.FloatValue(ScoreTime(2), 1.0))
        assertEquals(listOf(0.0, 1.0, 2.0), indexReceiver.values)
        assertTrue(doneReceiver.values.isEmpty())
        assertTrue(stepper.isRunning)

        // 3rd kick (Nth kick): finishes step 2's rhythmic duration, fires done, reverts to dormant
        stepper.kick.receive(Message.FloatValue(ScoreTime(3), 1.0))
        assertEquals(listOf(0.0, 1.0, 2.0), indexReceiver.values, "No new index should be emitted on completion")
        assertEquals(listOf(1.0), doneReceiver.values, "Done jack should fire on completion")
        assertFalse(stepper.isRunning, "Module should revert to dormant")
        assertNull(stepper.badgeFor(stepper.start))
        assertFalse(stepper.start.isHighlighted)

        // Further kicks should be ignored
        stepper.kick.receive(Message.FloatValue(ScoreTime(4), 1.0))
        stepper.kick.receive(Message.FloatValue(ScoreTime(5), 1.0))
        assertEquals(listOf(0.0, 1.0, 2.0), indexReceiver.values)
        assertEquals(listOf(1.0), doneReceiver.values)
    }

    @Test
    fun testIterationWithLoop() {
        val stepper = StepperModule()
        stepper.limit.setValue(3.0)
        stepper.isLooping = true
        val indexReceiver = TestReceiver()
        val doneReceiver = TestReceiver()
        stepper.indexOut.connect(indexReceiver.inJack)
        stepper.doneOut.connect(doneReceiver.inJack)

        // Start
        stepper.start.receive(Message.FloatValue(ScoreTime(0), 1.0))
        assertEquals(listOf(0.0), indexReceiver.values)

        // Kick 1 -> 1.0
        stepper.kick.receive(Message.FloatValue(ScoreTime(1), 1.0))
        // Kick 2 -> 2.0 (N - 1)
        stepper.kick.receive(Message.FloatValue(ScoreTime(2), 1.0))
        assertEquals(listOf(0.0, 1.0, 2.0), indexReceiver.values)
        assertTrue(doneReceiver.values.isEmpty())

        // Kick 3 (Nth kick): fires done and rolls back to 0.0
        stepper.kick.receive(Message.FloatValue(ScoreTime(3), 1.0))
        assertEquals(listOf(1.0), doneReceiver.values)
        assertEquals(listOf(0.0, 1.0, 2.0, 0.0), indexReceiver.values)
        assertTrue(stepper.isRunning)
        assertEquals(0, stepper.currentIndex)

        // Kick 4 -> 1.0
        stepper.kick.receive(Message.FloatValue(ScoreTime(4), 1.0))
        // Kick 5 -> 2.0
        stepper.kick.receive(Message.FloatValue(ScoreTime(5), 1.0))
        // Kick 6 -> done + 0.0
        stepper.kick.receive(Message.FloatValue(ScoreTime(6), 1.0))
        assertEquals(listOf(0.0, 1.0, 2.0, 0.0, 1.0, 2.0, 0.0), indexReceiver.values)
        assertEquals(listOf(1.0, 1.0), doneReceiver.values)
        assertTrue(stepper.isRunning)
    }

    @Test
    fun testChainedSteppersWithSharedClock() {
        // Chain: s1 (N=2) done -> s2 (N=2) start. Both s1 and s2 receive the master clock on kick.
        val s1 = StepperModule()
        s1.limit.setValue(2.0)
        val s2 = StepperModule()
        s2.limit.setValue(2.0)

        s1.doneOut.connect(s2.start)

        val s1Receiver = TestReceiver()
        val s2Receiver = TestReceiver()
        s1.indexOut.connect(s1Receiver.inJack)
        s2.indexOut.connect(s2Receiver.inJack)

        // Start s1 at time 0
        s1.start.receive(Message.FloatValue(ScoreTime(0), 1.0))
        assertEquals(listOf(0.0), s1Receiver.values)
        assertTrue(s2Receiver.values.isEmpty())
        assertTrue(s1.isRunning)
        assertFalse(s2.isRunning)

        // Master Clock Tick 1:
        // s1 advances to 1. s2 is dormant and ignores.
        s1.kick.receive(Message.FloatValue(ScoreTime(1), 1.0))
        s2.kick.receive(Message.FloatValue(ScoreTime(1), 1.0))
        assertEquals(listOf(0.0, 1.0), s1Receiver.values)
        assertTrue(s2Receiver.values.isEmpty())

        // Master Clock Tick 2:
        // s1 was at N-1 (1). This kick finishes s1, fires done (which triggers s2.start), and s1 goes dormant.
        // s2 receives s1's done on start AND receives the master clock tick 2 on kick.
        // Because start has precedence, s2 wakes up at index 0 and does NOT double-step to 1!
        val tick2Msg = Message.FloatValue(ScoreTime(2), 1.0)
        s1.kick.receive(tick2Msg)
        s2.kick.receive(tick2Msg)

        assertFalse(s1.isRunning, "s1 should be dormant after Nth kick")
        assertTrue(s2.isRunning, "s2 should be activated by s1.done")
        assertEquals(listOf(0.0), s2Receiver.values, "s2 should be at index 0 on activation, no double-trigger")

        // Master Clock Tick 3:
        // s1 is dormant, s2 advances to index 1
        val tick3Msg = Message.FloatValue(ScoreTime(3), 1.0)
        s1.kick.receive(tick3Msg)
        s2.kick.receive(tick3Msg)

        assertEquals(listOf(0.0, 1.0), s2Receiver.values)
        assertTrue(s2.isRunning)

        // Master Clock Tick 4:
        // s2 finishes and goes dormant
        val tick4Msg = Message.FloatValue(ScoreTime(4), 1.0)
        s1.kick.receive(tick4Msg)
        s2.kick.receive(tick4Msg)

        assertFalse(s2.isRunning)
        assertEquals(listOf(0.0, 1.0), s2Receiver.values)
    }

    @Test
    fun testRestartWhileRunning() {
        val stepper = StepperModule()
        stepper.limit.setValue(5.0)
        val receiver = TestReceiver()
        stepper.indexOut.connect(receiver.inJack)

        stepper.start.receive(Message.FloatValue(ScoreTime(0), 1.0))
        stepper.kick.receive(Message.FloatValue(ScoreTime(1), 1.0))
        stepper.kick.receive(Message.FloatValue(ScoreTime(2), 1.0))
        assertEquals(listOf(0.0, 1.0, 2.0), receiver.values)

        // Restart trigger arrives while at index 2
        stepper.start.receive(Message.FloatValue(ScoreTime(3), 1.0))
        assertEquals(listOf(0.0, 1.0, 2.0, 0.0), receiver.values)
        assertEquals(0, stepper.currentIndex)
        assertTrue(stepper.isRunning)

        stepper.kick.receive(Message.FloatValue(ScoreTime(4), 1.0))
        assertEquals(listOf(0.0, 1.0, 2.0, 0.0, 1.0), receiver.values)
    }

    @Test
    fun testSingleStepN1() {
        val stepper = StepperModule()
        stepper.limit.setValue(1.0)
        val indexReceiver = TestReceiver()
        val doneReceiver = TestReceiver()
        stepper.indexOut.connect(indexReceiver.inJack)
        stepper.doneOut.connect(doneReceiver.inJack)

        stepper.start.receive(Message.FloatValue(ScoreTime(0), 1.0))
        assertEquals(listOf(0.0), indexReceiver.values)
        assertTrue(doneReceiver.values.isEmpty())

        // 1st kick finishes step 0's duration and fires done
        stepper.kick.receive(Message.FloatValue(ScoreTime(1), 1.0))
        assertEquals(listOf(1.0), doneReceiver.values)
        assertFalse(stepper.isRunning)
    }

    @Test
    fun testSerializationRoundTrip() {
        val state = GraphState()
        val stepper = StepperModule()
        stepper.limit.setValue(16.0)
        stepper.loop.setValue(1.0)
        state.addModule(stepper, Offset.Zero)

        val json = GraphSerializer.serialize(state, state.modules.toList())
        assertTrue(json.contains("music.compose.stepper"))

        val restoredState = GraphState()
        GraphSerializer.deserialize(restoredState, json)

        val restoredStepper = restoredState.modules.firstOrNull { it is StepperModule } as? StepperModule
        assertNotNull(restoredStepper)
        assertEquals(16.0, restoredStepper.limit.getValue())
        assertEquals(1.0, restoredStepper.loop.getValue())
        assertTrue(restoredStepper.isLooping)
        assertTrue(restoredStepper.limit.isInteger)
        assertTrue(restoredStepper.loop.isInteger)
        assertTrue(restoredStepper.indexOut.isInteger)

        // Verify backward compatibility when loading legacy type core.basic.stepper
        val legacyJson = """{"modules":[{"id":0,"type":"core.basic.stepper","name":"stepper"}],"connections":[]}"""
        val legacyState = GraphState()
        GraphSerializer.deserialize(legacyState, legacyJson)
        val legacyStepper = legacyState.modules.firstOrNull { it is StepperModule }
        assertNotNull(legacyStepper, "Legacy core.basic.stepper should deserialize to StepperModule")
    }

    @Test
    fun testLoopPropertyAndJackSynchronization() {
        val stepper = StepperModule()
        assertFalse(stepper.isLooping)
        assertEquals(0.0, stepper.loop.getValue())

        // Toggling isLooping property updates the jack value
        stepper.isLooping = true
        assertTrue(stepper.isLooping)
        assertEquals(1.0, stepper.loop.getValue())

        stepper.isLooping = false
        assertFalse(stepper.isLooping)
        assertEquals(0.0, stepper.loop.getValue())

        // Delivering a message to the loop jack updates the isLooping property
        stepper.loop.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertTrue(stepper.isLooping)

        stepper.loop.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertFalse(stepper.isLooping)
    }

    @Test
    fun testStopTakesOutOfRunMode() {
        val stepper = StepperModule()
        val indexReceiver = TestReceiver()
        stepper.indexOut.connect(indexReceiver.inJack)

        // Start running
        stepper.start.receive(Message.FloatValue(ScoreTime(0), 1.0))
        assertTrue(stepper.isRunning)
        assertTrue(stepper.start.isHighlighted)
        assertEquals("RUN", stepper.badgeFor(stepper.start))
        assertEquals(listOf(0.0), indexReceiver.values)

        // Advance one step
        stepper.kick.receive(Message.FloatValue(ScoreTime(1), 1.0))
        assertEquals(listOf(0.0, 1.0), indexReceiver.values)
        assertTrue(stepper.isRunning)

        // Kick stop
        stepper.stop.receive(Message.FloatValue(ScoreTime(2), 1.0))
        assertFalse(stepper.isRunning)
        assertFalse(stepper.start.isHighlighted)
        assertNull(stepper.badgeFor(stepper.start))

        // Subsequent kicks are ignored while stopped / dormant
        stepper.kick.receive(Message.FloatValue(ScoreTime(3), 1.0))
        assertEquals(listOf(0.0, 1.0), indexReceiver.values)
        assertFalse(stepper.isRunning)

        // Triggering start again resets and resumes from step 0
        stepper.start.receive(Message.FloatValue(ScoreTime(4), 1.0))
        assertTrue(stepper.isRunning)
        assertTrue(stepper.start.isHighlighted)
        assertEquals("RUN", stepper.badgeFor(stepper.start))
        assertEquals(listOf(0.0, 1.0, 0.0), indexReceiver.values)
    }

    @Test
    fun testLegacyLoopJackDeserialization() {
        val legacyJson = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "stepper",
                  "name": "stepper",
                  "jacks": {
                    "loop": {
                      "v": 1.0,
                      "min": 0.0,
                      "max": 1.0
                    }
                  }
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val graphState = com.syntona.dataflow.editor.GraphState()
        val deserialized = com.syntona.dataflow.editor.GraphSerializer.deserialize(graphState, legacyJson)
        assertEquals(1, deserialized.size)
        val stepper = deserialized[0] as StepperModule
        assertEquals("looping", stepper.loop.name)
        assertEquals(1.0, stepper.loop.getValue())
        assertTrue(stepper.isLooping)
    }
}


package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BeatClockModuleTest {

    // A test engine that allows us to intercept scheduling and step time manually
    private class TestEngine : DataFlowEngine(CoroutineScope(EmptyCoroutineContext)) {
        val scheduled = mutableListOf<Pair<ScoreTime, () -> Unit>>()

        override fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
            scheduled.add(time to action)
            return ScheduledEvent(action)
        }
    }

    @Test
    fun testModuleCreationAndPorts() {
        val module = BeatClockModule()
        assertEquals("beatClock", module.name)
        assertEquals("1/1", module.division)

        val startInput = module.inputs.find { it.name == "start" }
        assertNotNull(startInput)
        assertTrue(startInput.isKickable)

        val stopInput = module.inputs.find { it.name == "stop" }
        assertNotNull(stopInput)
        assertTrue(stopInput.isKickable)

        val resetInput = module.inputs.find { it.name == "reset" }
        assertNotNull(resetInput)
        assertTrue(resetInput.isKickable)

        val tempoInput = module.inputs.find { it.name == "tempo" }
        assertNotNull(tempoInput)
        assertEquals(120.0, tempoInput.getValue())
        assertEquals(20.0, tempoInput.min)
        assertEquals(300.0, tempoInput.max)

        val countOutput = module.outputs.find { it.name == "output" }
        assertNotNull(countOutput)
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("beatClock")
        assertNotNull(creator)
        val module = creator()
        assertTrue(module is BeatClockModule)
    }

    @Test
    fun testSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = BeatClockModule()
        module.division = "3/8"
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"division\": \"3/8\"") || jsonStr.contains("\"division\":\"3/8\""))

        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserialized = newGraphState.modules.find { it.name == "beatClock" } as? BeatClockModule
        assertNotNull(deserialized)
        assertEquals("3/8", deserialized.division)
    }

    @Test
    fun testDriftPreventionAndRemainders() {
        val module = BeatClockModule()
        val engine = TestEngine()
        module.setEngine(engine)

        // Capture output sent from the output jack
        val receivedValues = mutableListOf<Double>()
        val receiver = object : com.syntona.dataflow.model.Module("testReceiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedValues.add(triggerMessage.value)
                }
            }
        }
        module.countOut.connect(receiver.input)

        // Set tempo to 120 bpm -> 1 beat = 0.5s = 500,000,000 ns.
        // Set division to 1/3 -> interval = 166,666,666.666... ns.
        module.tempo.setValue(120.0)
        module.division = "1/3"

        // 1. Kick start
        val startTime = ScoreClock.now()
        module.start.receive(Message.FloatValue(startTime, 1.0))

        // First schedule must be after start
        assertEquals(1, engine.scheduled.size)
        val firstEvent = engine.scheduled.first()
        val firstFireTime = firstEvent.first
        
        // Duration of first delay = 166666667 ns (rounded from 166666666.666...)
        val firstDelay = firstFireTime.nanoseconds - startTime.nanoseconds
        assertEquals(166666667L, firstDelay)

        // 2. Fire the first scheduled event
        engine.scheduled.clear()
        firstEvent.second.invoke()

        // Verify count sent is 1.0
        assertEquals(1, receivedValues.size)
        assertEquals(1.0, receivedValues.first())

        // Next schedule must be present
        assertEquals(1, engine.scheduled.size)
        val secondEvent = engine.scheduled.first()
        val secondFireTime = secondEvent.first
        
        // Second step includes negative remainder -0.333...
        // step = 166666666.666... - 0.333... = 166666666.333...
        // delay = 166666666
        val secondDelay = secondFireTime.nanoseconds - firstFireTime.nanoseconds
        assertEquals(166666666L, secondDelay)

        // 3. Fire the second event
        engine.scheduled.clear()
        secondEvent.second.invoke()
        assertEquals(2, receivedValues.size)
        assertEquals(2.0, receivedValues.last())

        // Third step includes remainder 0.333...
        // step = 166666666.666... + 0.333... = 166666667.0
        // delay = 166666667
        assertEquals(1, engine.scheduled.size)
        val thirdEvent = engine.scheduled.first()
        val thirdFireTime = thirdEvent.first
        val thirdDelay = thirdFireTime.nanoseconds - secondFireTime.nanoseconds
        assertEquals(166666667L, thirdDelay)

        // Total delay over 3 ticks = 166666666 + 166666667 + 166666667 = 500,000,000 ns.
        // This is exactly 1 beat (no drift!).
        val totalDelay = thirdFireTime.nanoseconds - startTime.nanoseconds
        assertEquals(500000000L, totalDelay)
    }
}

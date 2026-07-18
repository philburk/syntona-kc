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

class TimelineModuleTest {

    private class TestEngine : DataFlowEngine(CoroutineScope(EmptyCoroutineContext)) {
        val scheduled = mutableListOf<Pair<ScoreTime, () -> Unit>>()

        override fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
            scheduled.add(time to action)
            return ScheduledEvent(action)
        }
    }

    @Test
    fun testModuleCreationAndPorts() {
        val module = TimelineModule()
        assertEquals("timeline", module.name)
        assertTrue(module.loopMode)
        assertEquals(60.0, module.maxTime)

        val startInput = module.inputs.find { it.name == "start" }
        assertNotNull(startInput)
        assertTrue(startInput.isKickable)

        val stopInput = module.inputs.find { it.name == "stop" }
        assertNotNull(stopInput)
        assertTrue(stopInput.isKickable)

        val rewindInput = module.inputs.find { it.name == "rewind" }
        assertNotNull(rewindInput)
        assertTrue(rewindInput.isKickable)
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("timeline")
        assertNotNull(creator)
        val module = creator()
        assertTrue(module is TimelineModule)
    }

    @Test
    fun testAddAndRemoveTriggers() {
        val module = TimelineModule()
        assertEquals(0, module.triggers.size)
        assertEquals(0, module.outputs.size)

        module.addTrigger(2.5)
        assertEquals(1, module.triggers.size)
        assertEquals(1, module.outputs.size)
        assertEquals(2.5, module.triggers[0].time)
        assertEquals("t0", module.outputs[0].name)

        val trigger = module.triggers[0]
        module.removeTrigger(trigger)
        assertEquals(0, module.triggers.size)
        assertEquals(0, module.outputs.size)
    }

    @Test
    fun testSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = TimelineModule()
        module.width = 400.0
        module.maxTime = 20.0
        module.loopStart = 2.0
        module.loopEnd = 8.0
        module.loopMode = false
        module.zoom = 1.5f
        module.addTrigger(4.5)
        module.addTrigger(7.0)

        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"width\":400") || jsonStr.contains("\"width\": 400"))
        assertTrue(jsonStr.contains("\"maxTime\":20") || jsonStr.contains("\"maxTime\": 20"))
        assertTrue(jsonStr.contains("\"loopStart\":2") || jsonStr.contains("\"loopStart\": 2"))
        assertTrue(jsonStr.contains("\"loopEnd\":8") || jsonStr.contains("\"loopEnd\": 8"))
        assertTrue(jsonStr.contains("\"loopMode\":false") || jsonStr.contains("\"loopMode\": false"))
        assertTrue(jsonStr.contains("\"zoom\":1.5") || jsonStr.contains("\"zoom\": 1.5"))

        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserialized = newGraphState.modules.find { it.name == "timeline" } as? TimelineModule
        assertNotNull(deserialized)
        assertEquals(400.0, deserialized.width)
        assertEquals(20.0, deserialized.maxTime)
        assertEquals(2.0, deserialized.loopStart)
        assertEquals(8.0, deserialized.loopEnd)
        assertEquals(false, deserialized.loopMode)
        assertEquals(1.5f, deserialized.zoom)
        assertEquals(2, deserialized.triggers.size)
        assertEquals(4.5, deserialized.triggers[0].time)
        assertEquals(7.0, deserialized.triggers[1].time)
    }

    @Test
    fun testPlaybackAndTriggersFiring() {
        val module = TimelineModule()
        val engine = TestEngine()
        module.setEngine(engine)

        // Add 2 triggers
        module.addTrigger(2.0)
        module.addTrigger(4.0)
        module.loopStart = 1.0
        module.loopEnd = 5.0
        module.loopMode = true

        val trig1Values = mutableListOf<Double>()
        val trig2Values = mutableListOf<Double>()

        val receiver1 = object : com.syntona.dataflow.model.Module("trig1Rec") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    trig1Values.add(triggerMessage.value)
                }
            }
        }
        val receiver2 = object : com.syntona.dataflow.model.Module("trig2Rec") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    trig2Values.add(triggerMessage.value)
                }
            }
        }

        module.triggers[0].outputJack.connect(receiver1.input)
        module.triggers[1].outputJack.connect(receiver2.input)

        // Start playback from 0.0
        val startTime = ScoreClock.now()
        module.startPlayback(startTime, 0.0)

        // We expect 3 scheduled events: Trigger 1 (at 2.0s), Trigger 2 (at 4.0s), and Loop End (at 5.0s)
        assertEquals(3, engine.scheduled.size)

        // Sort by scheduled fire time to verify order
        val sortedEvents = engine.scheduled.sortedBy { it.first.nanoseconds }

        // 1. First trigger event at 2.0s
        val t1Event = sortedEvents[0]
        assertEquals(2000000000L, t1Event.first.nanoseconds - startTime.nanoseconds)

        // 2. Second trigger event at 4.0s
        val t2Event = sortedEvents[1]
        assertEquals(4000000000L, t2Event.first.nanoseconds - startTime.nanoseconds)

        // 3. Loop end cycle event at 5.0s
        val endEvent = sortedEvents[2]
        assertEquals(5000000000L, endEvent.first.nanoseconds - startTime.nanoseconds)

        // Fire Trigger 1
        t1Event.second.invoke()
        assertEquals(1, trig1Values.size)
        assertEquals(0, trig2Values.size)

        // Fire Trigger 2
        t2Event.second.invoke()
        assertEquals(1, trig1Values.size)
        assertEquals(1, trig2Values.size)

        // Fire loop cycle end event — it should loop and schedule the next cycle starting from loopStart (1.0s)
        engine.scheduled.clear()
        val cycleEndTime = endEvent.first
        endEvent.second.invoke()

        // In loop cycle 2:
        // Current playhead = 1.0s, loopEnd = 5.0s.
        // Trig 1 is at 2.0s (delay = 1.0s)
        // Trig 2 is at 4.0s (delay = 3.0s)
        // Loop End is at 5.0s (delay = 4.0s)
        assertEquals(3, engine.scheduled.size)
        val sortedEvents2 = engine.scheduled.sortedBy { it.first.nanoseconds }

        val t1Event2 = sortedEvents2[0]
        assertEquals(1000000000L, t1Event2.first.nanoseconds - cycleEndTime.nanoseconds)

        val t2Event2 = sortedEvents2[1]
        assertEquals(3000000000L, t2Event2.first.nanoseconds - cycleEndTime.nanoseconds)

        val endEvent2 = sortedEvents2[2]
        assertEquals(4000000000L, endEvent2.first.nanoseconds - cycleEndTime.nanoseconds)
    }
}

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

class BeatLineModuleTest {

    private class TestEngine : DataFlowEngine(CoroutineScope(EmptyCoroutineContext)) {
        val scheduled = mutableListOf<Pair<ScoreTime, () -> Unit>>()

        override fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
            scheduled.add(time to action)
            return ScheduledEvent(action)
        }
    }

    @Test
    fun testModuleCreationAndPorts() {
        val module = BeatLineModule()
        assertEquals("beatLine", module.name)
        assertTrue(module.loopMode)
        assertEquals(16.0, module.maxTime)
        assertEquals(4.0, module.loopEnd)

        val startInput = module.inputs.find { it.name == "start" }
        assertNotNull(startInput)
        assertTrue(startInput.isKickable)

        val stopInput = module.inputs.find { it.name == "stop" }
        assertNotNull(stopInput)
        assertTrue(stopInput.isKickable)

        val rewindInput = module.inputs.find { it.name == "rewind" }
        assertNotNull(rewindInput)
        assertTrue(rewindInput.isKickable)

        val tempoInput = module.inputs.find { it.name == "tempo" }
        assertNotNull(tempoInput)
        assertEquals(120.0, tempoInput.getValue())
        assertEquals(20.0, tempoInput.min)
        assertEquals(300.0, tempoInput.max)
    }

    @Test
    fun testModuleRegistryCreator() {
        val creator = ModuleRegistry.findCreator("beatLine")
        assertNotNull(creator)
        val module = creator()
        assertTrue(module is BeatLineModule)
        assertNotNull(ModuleRegistry.moduleDescriptions["beatLine"])
    }

    @Test
    fun testTempoConversions() {
        val module = BeatLineModule()
        module.tempo.setValue(120.0) // 120 bpm: 1 beat = 0.5s = 500,000,000 ns

        assertEquals(0.5, module.unitsToSeconds(1.0))
        assertEquals(2.0, module.unitsToSeconds(4.0))
        assertEquals(4.0, module.secondsToUnits(2.0))
        assertEquals(1.0, module.secondsToUnits(0.5))

        // Change tempo to 60 bpm: 1 beat = 1.0s
        module.tempo.setValue(60.0)
        assertEquals(1.0, module.unitsToSeconds(1.0))
        assertEquals(4.0, module.unitsToSeconds(4.0))
        assertEquals(4.0, module.secondsToUnits(4.0))
    }

    @Test
    fun testAddAndRemoveTriggers() {
        val module = BeatLineModule()
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
        val module = BeatLineModule()
        module.width = 400.0
        module.maxTime = 32.0
        module.loopStart = 0.0
        module.loopEnd = 8.0
        module.loopMode = true
        module.zoom = 1.5f
        module.tempo.setValue(140.0)
        module.addTrigger(2.0)
        module.addTrigger(6.5)

        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("beatLine"))
        assertTrue(jsonStr.contains("\"maxTime\":32") || jsonStr.contains("\"maxTime\": 32"))
        assertTrue(jsonStr.contains("\"loopEnd\":8") || jsonStr.contains("\"loopEnd\": 8"))

        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserialized = newGraphState.modules.find { it.name == "beatLine" } as? BeatLineModule
        assertNotNull(deserialized)
        assertEquals(400.0, deserialized.width)
        assertEquals(32.0, deserialized.maxTime)
        assertEquals(0.0, deserialized.loopStart)
        assertEquals(8.0, deserialized.loopEnd)
        assertEquals(true, deserialized.loopMode)
        assertEquals(1.5f, deserialized.zoom)
        assertEquals(140.0, deserialized.tempo.getValue())
        assertEquals(2, deserialized.triggers.size)
        assertEquals(2.0, deserialized.triggers[0].time)
        assertEquals(6.5, deserialized.triggers[1].time)
    }

    @Test
    fun testPlaybackWithTempoScaling() {
        val module = BeatLineModule()
        val engine = TestEngine()
        module.setEngine(engine)

        // At 120 bpm, 1 beat = 0.5s = 500,000,000 ns
        module.tempo.setValue(120.0)
        module.addTrigger(2.0) // Beat 2.0 -> 1.0s into cycle
        module.addTrigger(4.0) // Beat 4.0 -> 2.0s into cycle
        module.loopStart = 0.0
        module.loopEnd = 4.0   // Cycle duration = 4.0 beats = 2.0s
        module.loopMode = true

        val startTime = ScoreClock.now()
        module.startPlayback(startTime, 0.0)

        // Events: Trigger 1 (at beat 2.0 = 1.0s), Trigger 2 (at beat 4.0 - wait, trigger is at 4.0, cycleEnd is 4.0)
        // Note: range is [startPos, cycleEnd), so trigger at 4.0 is at cycle boundary.
        // Cycle end is at 2.0s.
        val sortedEvents = engine.scheduled.sortedBy { it.first.nanoseconds }
        assertTrue(sortedEvents.isNotEmpty())

        // Trigger at beat 2.0 (delay = 2 beats = 1.0s = 1,000,000,000 ns)
        val trigEvent = sortedEvents[0]
        assertEquals(1_000_000_000L, trigEvent.first.nanoseconds - startTime.nanoseconds)

        // Cycle end at beat 4.0 = 2.0s = 2,000,000,000 ns
        val cycleEndEvent = sortedEvents.last()
        assertEquals(2_000_000_000L, cycleEndEvent.first.nanoseconds - startTime.nanoseconds)
    }
}

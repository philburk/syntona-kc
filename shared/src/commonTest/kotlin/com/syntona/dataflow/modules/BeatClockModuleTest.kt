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
        assertEquals("1:1", module.division)

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

        val countOutput = module.outputs.find { it.name == "out" }
        assertNotNull(countOutput)
    }

    @Test
    fun testWholeNoteMultipliers() {
        val module = BeatClockModule()
        module.division = "1:4"
        assertEquals("1:4", module.division)

        module.division = "1:3"
        assertEquals("1:3", module.division)

        module.division = "1:2"
        assertEquals("1:2", module.division)
    }

    @Test
    fun testGetDivisionValue() {
        val module = BeatClockModule()
        // Colon format
        assertEquals(4.0, module.getDivisionValue("1:4"))
        assertEquals(3.0, module.getDivisionValue("1:3"))
        assertEquals(2.0, module.getDivisionValue("1:2"))
        assertEquals(1.0, module.getDivisionValue("1:1"))
        assertEquals(0.5, module.getDivisionValue("2:1"))
        assertEquals(3.0 / 8.0, module.getDivisionValue("8:3"))
        assertEquals(0.25, module.getDivisionValue("4:1"))
        assertEquals(3.0 / 16.0, module.getDivisionValue("16:3"))
        assertEquals(0.125, module.getDivisionValue("8:1"))
        assertEquals(1.0 / 16.0, module.getDivisionValue("16:1"))
        assertEquals(1.0 / 32.0, module.getDivisionValue("32:1"))
        assertEquals(1.0 / 3.0, module.getDivisionValue("3:1"))
        assertEquals(1.0 / 6.0, module.getDivisionValue("6:1"))
        assertEquals(1.0 / 12.0, module.getDivisionValue("12:1"))
        assertEquals(0.2, module.getDivisionValue("5:1"))
        assertEquals(0.1, module.getDivisionValue("10:1"))

        // Slash format backwards compatibility
        assertEquals(3.0 / 8.0, module.getDivisionValue("8/3"))
        assertEquals(3.0 / 16.0, module.getDivisionValue("16/3"))
        assertEquals(4.0, module.getDivisionValue("1/4"))
        assertEquals(0.25, module.getDivisionValue("4/1"))
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
        module.division = "8:3"
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"division\": \"8:3\"") || jsonStr.contains("\"division\":\"8:3\""))

        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserialized = newGraphState.modules.find { it.name == "beatClock" } as? BeatClockModule
        assertNotNull(deserialized)
        assertEquals("8:3", deserialized.division)
    }

    @Test
    fun testLegacyDeserializationReversesDivision() {
        // Version 0.7.2 legacy JSON with slash: division should be reversed and normalized to colon
        val legacyJson072Slash = """
            {
              "version": "0.7.2 beta",
              "versionCode": 458754,
              "modules": [
                {
                  "type": "beatClock",
                  "name": "beatClock",
                  "division": "3/16"
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val state1 = GraphState()
        GraphSerializer.deserialize(state1, legacyJson072Slash)
        val clock1 = state1.modules.first() as BeatClockModule
        assertEquals("16:3", clock1.division)

        // Version 0.7.2 legacy JSON with colon: division should also be reversed
        val legacyJson072Colon = """
            {
              "version": "0.7.2 beta",
              "versionCode": 458754,
              "modules": [
                {
                  "type": "beatClock",
                  "name": "beatClock",
                  "division": "3:16"
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val state2 = GraphState()
        GraphSerializer.deserialize(state2, legacyJson072Colon)
        val clock2 = state2.modules.first() as BeatClockModule
        assertEquals("16:3", clock2.division)

        // Version 0.6.1 legacy JSON
        val legacyJson061 = """
            {
              "version": "0.6.1 beta",
              "versionCode": 393217,
              "modules": [
                {
                  "type": "beatClock",
                  "name": "beatClock",
                  "division": "4/1"
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val state3 = GraphState()
        GraphSerializer.deserialize(state3, legacyJson061)
        val clock3 = state3.modules.first() as BeatClockModule
        assertEquals("1:4", clock3.division)

        // Unversioned legacy JSON
        val unversionedJson = """
            {
              "modules": [
                {
                  "type": "beatClock",
                  "name": "beatClock",
                  "division": "3/8"
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val state4 = GraphState()
        GraphSerializer.deserialize(state4, unversionedJson)
        val clock4 = state4.modules.first() as BeatClockModule
        assertEquals("8:3", clock4.division)

        // Version 0.7.3 JSON (already reversed, but had slash): division should NOT be reversed, normalized to colon
        val version073Json = """
            {
              "version": "0.7.3 beta",
              "versionCode": 458755,
              "modules": [
                {
                  "type": "beatClock",
                  "name": "beatClock",
                  "division": "16/3"
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val state5 = GraphState()
        GraphSerializer.deserialize(state5, version073Json)
        val clock5 = state5.modules.first() as BeatClockModule
        assertEquals("16:3", clock5.division)

        // Version 0.7.4+ current JSON: division should NOT be reversed
        val modernJson = """
            {
              "version": "0.7.4 beta",
              "versionCode": 458756,
              "modules": [
                {
                  "type": "beatClock",
                  "name": "beatClock",
                  "division": "16:3"
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val state6 = GraphState()
        GraphSerializer.deserialize(state6, modernJson)
        val clock6 = state6.modules.first() as BeatClockModule
        assertEquals("16:3", clock6.division)
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
        // Set division to 3:1 (3 kicks/beat) -> interval = 166,666,666.666... ns.
        module.tempo.setValue(120.0)
        module.division = "3:1"

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

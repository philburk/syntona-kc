package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
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

class ClockModuleTest {

    private class TestEngine : DataFlowEngine(CoroutineScope(EmptyCoroutineContext)) {
        val scheduled = mutableListOf<Pair<ScoreTime, () -> Unit>>()

        override fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
            scheduled.add(time to action)
            return ScheduledEvent(action)
        }
    }

    @Test
    fun testClockModuleJacks() {
        val clock = ClockModule()
        assertNotNull(clock.inputs.find { it.name == "start" })
        assertNotNull(clock.inputs.find { it.name == "stop" })
        assertNotNull(clock.inputs.find { it.name == "reset" })
        val rateJack = clock.inputs.find { it.name == "rate(hz)" }
        assertNotNull(rateJack, "rate(hz) jack should exist")
        assertEquals(0.01, rateJack.min)
        assertEquals(1000.0, rateJack.max)
        assertEquals(2.0, rateJack.getDoubleValue())
        assertNotNull(clock.outputs.find { it.name == "output" })
    }

    @Test
    fun testClockExecution() {
        val clock = ClockModule()
        val fakeEngine = TestEngine()
        clock.setEngine(fakeEngine)

        clock.rateHz.setValue(4.0) // 4 Hz -> 250 ms = 250_000_000 ns
        val startTime = ScoreClock.now()
        clock.start.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        clock.execute(Message.FloatValue(ScoreTime.Zero, 1.0))

        assertEquals(1, fakeEngine.scheduled.size)
        val scheduledTime = fakeEngine.scheduled.first().first
        // Delay should be 250,000,000 ns (250 ms)
        val expectedDelay = (1_000_000_000.0 / 4.0).toLong()
        val actualDelay = (scheduledTime - startTime).nanoseconds
        assertTrue(kotlin.math.abs(actualDelay - expectedDelay) < 10_000_000L)
    }

    @Test
    fun testClockRegistry() {
        val creator = ModuleRegistry.findCreator("clock")
        assertNotNull(creator)
        val mod = creator()
        assertTrue(mod is ClockModule)
    }
}

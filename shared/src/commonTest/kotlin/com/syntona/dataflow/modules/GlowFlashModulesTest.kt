package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.DataFlowEngine
import com.syntona.dataflow.engine.ScheduledEvent
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GlowFlashModulesTest {

    private class TestEngine : DataFlowEngine(CoroutineScope(EmptyCoroutineContext)) {
        val scheduled = mutableListOf<Pair<ScoreTime, ScheduledEvent>>()

        override fun schedule(time: ScoreTime, action: () -> Unit): ScheduledEvent {
            val event = ScheduledEvent(action)
            scheduled.add(time to event)
            return event
        }
    }

    @Test
    fun testGlowModuleCreationAndPorts() {
        val module = GlowModule()
        assertEquals("glow", module.type)
        assertFalse(module.isOn)

        val onInput = module.inputs.find { it.name == "on" }
        assertNotNull(onInput, "GlowModule should have an input named 'on'")
        assertTrue(onInput.isKickable, "'on' input should be kickable")

        val offInput = module.inputs.find { it.name == "off" }
        assertNotNull(offInput, "GlowModule should have an input named 'off'")
        assertTrue(offInput.isKickable, "'off' input should be kickable")
    }

    @Test
    fun testGlowModuleBehavior() {
        val module = GlowModule()
        val onInput = module.inputs.find { it.name == "on" }!!
        val offInput = module.inputs.find { it.name == "off" }!!

        // Kick onInput
        onInput.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertTrue(module.isOn, "GlowModule should turn ON when 'on' is kicked")

        // Kick offInput
        offInput.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertFalse(module.isOn, "GlowModule should turn OFF when 'off' is kicked")
    }

    @Test
    fun testFlashModuleCreationAndPorts() {
        val module = FlashModule()
        assertEquals("flash", module.type)
        assertFalse(module.isOn)
        assertEquals(0, module.triggerCount)

        val flashInput = module.inputs.find { it.name == "flash" }
        assertNotNull(flashInput, "FlashModule should have an input named 'flash'")
        assertTrue(flashInput.isKickable, "'flash' input should be kickable")
    }

    @Test
    fun testFlashModuleBehavior() {
        val module = FlashModule()
        val flashInput = module.inputs.find { it.name == "flash" }!!

        // Kick flashInput
        flashInput.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertTrue(module.isOn, "FlashModule should turn ON when 'flash' is kicked")
        assertEquals(1, module.triggerCount, "triggerCount should be incremented")

        // Kick flashInput again
        flashInput.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
        assertTrue(module.isOn, "FlashModule should stay ON when 'flash' is kicked again")
        assertEquals(2, module.triggerCount, "triggerCount should be incremented to 2")
    }

    @Test
    fun testModuleRegistryCreators() {
        val glowCreator = ModuleRegistry.findCreator("glow")
        assertNotNull(glowCreator, "GlowModule creator should be registered in ModuleRegistry under 'glow'")
        val glowModule = glowCreator()
        assertTrue(glowModule is GlowModule, "Created module should be an instance of GlowModule")

        val flashCreator = ModuleRegistry.findCreator("flash")
        assertNotNull(flashCreator, "FlashModule creator should be registered in ModuleRegistry under 'flash'")
        val flashModule = flashCreator()
        assertTrue(flashModule is FlashModule, "Created module should be an instance of FlashModule")
    }

    @Test
    fun testFlashModuleAutomaticTurnOff() {
        val engine = TestEngine()
        val module = FlashModule()
        module.setEngine(engine)
        val flashInput = module.inputs.find { it.name == "flash" }!!

        // Kick flashInput
        val triggerTime = ScoreTime(1000)
        flashInput.receive(Message.FloatValue(triggerTime, 1.0))
        assertTrue(module.isOn, "FlashModule should turn ON when 'flash' is kicked")
        assertEquals(1, engine.scheduled.size, "Should have scheduled one turn-off event")

        val (scheduledTime, event) = engine.scheduled[0]
        assertEquals(triggerTime + ScoreTime(150_000_000L), scheduledTime, "Scheduled time should be 150ms after trigger")

        // Fire the scheduled event
        event.fire()
        assertFalse(module.isOn, "FlashModule should turn OFF after the scheduled event fires")
    }

    @Test
    fun testFlashModuleRescheduleCancellation() {
        val engine = TestEngine()
        val module = FlashModule()
        module.setEngine(engine)
        val flashInput = module.inputs.find { it.name == "flash" }!!

        // Kick flashInput once
        flashInput.receive(Message.FloatValue(ScoreTime(1000), 1.0))
        assertEquals(1, engine.scheduled.size)
        val event1 = engine.scheduled[0].second
        assertFalse(event1.isCancelled)

        // Kick flashInput again before the first one fires
        flashInput.receive(Message.FloatValue(ScoreTime(1100), 1.0))
        assertEquals(2, engine.scheduled.size)
        val event2 = engine.scheduled[1].second
        assertTrue(event1.isCancelled, "First event should be cancelled")
        assertFalse(event2.isCancelled, "Second event should not be cancelled")
    }
}

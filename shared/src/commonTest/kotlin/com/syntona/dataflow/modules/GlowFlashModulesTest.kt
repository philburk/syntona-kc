package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GlowFlashModulesTest {

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
}

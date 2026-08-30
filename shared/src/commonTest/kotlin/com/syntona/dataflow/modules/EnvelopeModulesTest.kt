package com.syntona.dataflow.modules

import com.softsynth.ksyn.unitgen.EnvelopeAttackDecay
import com.softsynth.ksyn.unitgen.EnvelopeDAHDSR
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvelopeModulesTest {

    @Test
    fun testDAHDSRTriggerCommandJack() {
        val dahdsrCreator = ModuleRegistry.findCreator("envDAHDSR")
        assertNotNull(dahdsrCreator, "envDAHDSR should be registered")
        val dahdsrMod = dahdsrCreator() as UnitGeneratorModule
        val dahdsrUnit = dahdsrMod.unit as EnvelopeDAHDSR

        // Legacy DAHDSR alias
        val legacyCreator = ModuleRegistry.findCreator("DAHDSR")
        assertNotNull(legacyCreator, "Legacy DAHDSR should resolve")

        // First input must be "trigger" of type CommandJack at the top
        assertTrue(dahdsrMod.inputs.isNotEmpty())
        val firstInput = dahdsrMod.inputs[0]
        assertEquals("trigger", firstInput.name)
        assertTrue(firstInput is CommandJack)
        assertTrue(firstInput.isKickable)

        // Verify "in" is present as an AudioInputJack (coerced from input)
        val inPort = dahdsrMod.inputs.find { it.name == "in" }
        assertNotNull(inPort, "Input port should be coerced to 'in'")

        // Triggering the command jack triggers the envelope
        firstInput.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
    }

    @Test
    fun testEnvADTriggerCommandJack() {
        val envADCreator = ModuleRegistry.findCreator("envAD")
        assertNotNull(envADCreator, "envAD should be registered")
        val envADMod = envADCreator() as UnitGeneratorModule
        val envADUnit = envADMod.unit as EnvelopeAttackDecay

        // First input must be "trigger" of type CommandJack at the top
        assertTrue(envADMod.inputs.isNotEmpty())
        val firstInput = envADMod.inputs[0]
        assertEquals("trigger", firstInput.name)
        assertTrue(firstInput is CommandJack)
        assertTrue(firstInput.isKickable)

        // Verify "in" is present (coerced from input)
        val inPort = envADMod.inputs.find { it.name == "in" }
        assertNotNull(inPort, "Input port should be coerced to 'in'")

        // Triggering the command jack triggers the envelope
        firstInput.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
    }
}

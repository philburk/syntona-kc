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
        val dahdsrCreator = ModuleRegistry.findCreator("DAHDSR")
        assertNotNull(dahdsrCreator, "DAHDSR should be registered")
        val dahdsrMod = dahdsrCreator() as UnitGeneratorModule
        val dahdsrUnit = dahdsrMod.unit as EnvelopeDAHDSR

        // First input must be "trigger" of type CommandJack at the top
        assertTrue(dahdsrMod.inputs.isNotEmpty())
        val firstInput = dahdsrMod.inputs[0]
        assertEquals("trigger", firstInput.name)
        assertTrue(firstInput is CommandJack)
        assertTrue(firstInput.isKickable)

        // Verify "input" is also present as an AudioInputJack
        val inputPort = dahdsrMod.inputs.find { it.name == "input" }
        assertNotNull(inputPort)

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

        // Verify "input" is also present
        val inputPort = envADMod.inputs.find { it.name == "input" }
        assertNotNull(inputPort)

        // Triggering the command jack triggers the envelope
        firstInput.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
    }
}

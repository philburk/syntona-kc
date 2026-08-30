package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneShotModuleTest {

    @Test
    fun testModuleCreationAndJacks() {
        val module = OneShotModule()
        assertEquals("oneShot", module.name)

        val armInput = module.inputs.find { it.name == "arm" }
        assertNotNull(armInput)

        val kickInput = module.inputs.find { it.name == "in" }
        assertNotNull(kickInput)
        assertTrue(kickInput.isKickable)

        val output = module.outputs.find { it.name == "out" }
        assertNotNull(output)

        assertFalse(module.isArmed)
    }

    @Test
    fun testRegistryLookup() {
        val shortNameCreator = ModuleRegistry.findCreator("oneShot")
        assertNotNull(shortNameCreator)
        assertTrue(shortNameCreator() is OneShotModule)

        val pathCreator = ModuleRegistry.findCreator("core.basic.oneShot")
        assertNotNull(pathCreator)
        assertTrue(pathCreator() is OneShotModule)

        // Legacy arm alias
        val legacyCreator = ModuleRegistry.findCreator("arm")
        assertNotNull(legacyCreator)
        assertTrue(legacyCreator() is OneShotModule)
    }

    @Test
    fun testArmingAndKickPassthrough() {
        val module = OneShotModule()
        val armInput = module.inputs.first { it.name == "arm" }
        val kickInput = module.inputs.first { it.name == "in" }
        val output = module.outputs.first { it.name == "out" }

        var receivedMessage: Message? = null
        val receiver = object : InputJack("testRecv", module) {
            override fun receive(msg: Message) {
                receivedMessage = msg
                super.receive(msg)
            }
        }
        output.connect(receiver)

        // 1. Kick when disarmed -> output should NOT receive anything
        kickInput.receive(Message.FloatValue(ScoreTime(100), 42.0))
        assertNull(receivedMessage, "Kick should be ignored when disarmed")

        // 2. Arm the module
        armInput.receive(Message.FloatValue(ScoreTime(105), 0.0))
        assertTrue(module.isArmed, "Module should be armed after arm input is kicked")

        // 3. Kick when armed -> output SHOULD receive the message and module becomes disarmed
        val testMessage = Message.FloatValue(ScoreTime(200), 99.0)
        kickInput.receive(testMessage)

        assertNotNull(receivedMessage, "Message should be passed through when armed")
        assertEquals(testMessage, receivedMessage)
        assertFalse(module.isArmed, "Module should be disarmed after kick passes through")

        // 4. Next kick when disarmed -> ignored
        receivedMessage = null
        kickInput.receive(Message.FloatValue(ScoreTime(300), 100.0))
        assertNull(receivedMessage, "Subsequent kick should be ignored because module disarmed")
    }

    @Test
    fun testArmToggle() {
        val module = OneShotModule()
        val armInput = module.inputs.first { it.name == "arm" }

        assertFalse(module.isArmed)

        // Arming
        armInput.receive(Message.FloatValue(ScoreTime(10), 0.0))
        assertTrue(module.isArmed)

        // Disarming manually via arm button
        armInput.receive(Message.FloatValue(ScoreTime(20), 0.0))
        assertFalse(module.isArmed)
    }
}

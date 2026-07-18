package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.MailboxManager
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MailboxModulesTest {

    @Test
    fun testDefaultCreationAndRegistration() {
        MailboxManager.clear()
        val sendModule = SendModule()
        val receiveModule = ReceiveModule()

        assertEquals("global", sendModule.name)
        assertEquals("global", receiveModule.name)

        val mailbox = MailboxManager.getMailbox("global")
        assertNotNull(mailbox, "Mailbox 'global' should be created automatically")
        assertTrue(mailbox.senders.contains(sendModule), "SendModule should be registered in 'global' mailbox")
        assertTrue(mailbox.receivers.contains(receiveModule), "ReceiveModule should be registered in 'global' mailbox")

        sendModule.teardown()
        receiveModule.teardown()
        assertTrue(MailboxManager.getAllMailboxes().isEmpty(), "Mailbox should be removed after both modules are torn down")
    }

    @Test
    fun testMessageTransmission() {
        MailboxManager.clear()
        val sendModule = SendModule()
        val receiveModule1 = ReceiveModule()
        val receiveModule2 = ReceiveModule()

        val inJack = sendModule.inputs.find { it.name == "in" }
        assertNotNull(inJack, "SendModule should have an 'in' jack")

        val outJack1 = receiveModule1.outputs.find { it.name == "out" }
        val outJack2 = receiveModule2.outputs.find { it.name == "out" }
        assertNotNull(outJack1, "ReceiveModule1 should have an 'out' jack")
        assertNotNull(outJack2, "ReceiveModule2 should have an 'out' jack")

        val dummyModule = GlowModule()
        val dummyInput1 = dummyModule.inputs.find { it.name == "on" }!!
        val dummyInput2 = dummyModule.inputs.find { it.name == "off" }!!
        outJack1.connect(dummyInput1)
        outJack2.connect(dummyInput2)

        val testMessage = Message.FloatValue(ScoreTime.Zero, 42.0)
        inJack.receive(testMessage)

        assertEquals(testMessage, dummyInput1.lastMessage, "dummyInput1 should receive the sent message")
        assertEquals(testMessage, dummyInput2.lastMessage, "dummyInput2 should receive the sent message")

        sendModule.teardown()
        receiveModule1.teardown()
        receiveModule2.teardown()
        assertTrue(MailboxManager.getAllMailboxes().isEmpty(), "Mailboxes should be completely clean")
    }

    @Test
    fun testRenameDynamics() {
        MailboxManager.clear()
        val sendModule = SendModule()
        val receiveModule = ReceiveModule()

        sendModule.name = "bus1"
        receiveModule.name = "bus1"

        assertFalse(MailboxManager.getAllMailboxes().containsKey("global"), "Mailbox 'global' should be deleted")

        val mailbox = MailboxManager.getMailbox("bus1")
        assertNotNull(mailbox, "Mailbox 'bus1' should be created")
        assertTrue(mailbox.senders.contains(sendModule))
        assertTrue(mailbox.receivers.contains(receiveModule))

        receiveModule.name = "bus2"
        assertNotNull(MailboxManager.getMailbox("bus1"))
        assertEquals(1, MailboxManager.getMailbox("bus1")?.senders?.size)
        assertEquals(0, MailboxManager.getMailbox("bus1")?.receivers?.size)

        val mailbox2 = MailboxManager.getMailbox("bus2")
        assertNotNull(mailbox2)
        assertEquals(0, mailbox2.senders.size)
        assertEquals(1, mailbox2.receivers.size)

        sendModule.teardown()
        receiveModule.teardown()
        assertTrue(MailboxManager.getAllMailboxes().isEmpty(), "Mailboxes should be completely clean")
    }

    @Test
    fun testModuleRegistry() {
        val sendCreator = ModuleRegistry.findCreator("send")
        assertNotNull(sendCreator, "SendModule should be registered in ModuleRegistry")
        val sendModule = sendCreator()
        assertTrue(sendModule is SendModule)

        val receiveCreator = ModuleRegistry.findCreator("receive")
        assertNotNull(receiveCreator, "ReceiveModule should be registered in ModuleRegistry")
        val receiveModule = receiveCreator()
        assertTrue(receiveModule is ReceiveModule)

        sendModule.teardown()
        receiveModule.teardown()
    }
}

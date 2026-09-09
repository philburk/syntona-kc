package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.IntegerInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValueListModuleTest {

    @Test
    fun testJacksAndDefaults() {
        val module = ValueListModule()
        assertEquals("valueList", module.name)

        assertEquals(6, module.inputs.size)
        val getJack = module.inputs.find { it.name == "get" } as IntegerInputJack
        assertTrue(getJack.isKickable)
        assertTrue(getJack.isInteger)

        val setJack = module.inputs.find { it.name == "set" }!!
        assertTrue(setJack.isKickable)
        assertTrue(!setJack.isInteger)

        val indexJack = module.inputs.find { it.name == "index" } as IntegerInputJack
        assertTrue(!indexJack.isKickable)
        assertTrue(indexJack.isInteger)

        val addJack = module.inputs.find { it.name == "add" }!!
        assertTrue(addJack.isKickable)

        val clearJack = module.inputs.find { it.name == "clear" } as CommandJack
        assertTrue(clearJack.isKickable)

        val capacityJack = module.inputs.find { it.name == "capacity" } as IntegerInputJack
        assertTrue(!capacityJack.isKickable)
        assertEquals(1024, capacityJack.getClippedIntValue())

        assertEquals(2, module.outputs.size)
        val outJack = module.outputs.find { it.name == "out" }!!
        assertTrue(!outJack.isInteger)

        val sizeJack = module.outputs.find { it.name == "size" }!!
        assertTrue(sizeJack.isInteger)

        assertEquals(0, module.size)
        assertEquals(1024, module.currentStorageCapacity)
    }

    @Test
    fun testAddAndSize() {
        val module = ValueListModule()
        var lastSizeMessage: Message? = null
        val receiver = object : InputJack("recv", module) {
            override fun receive(msg: Message) {
                lastSizeMessage = msg
            }
        }
        module.sizeOut.connect(receiver)

        val t1 = ScoreTime(1000)
        module.addIn.receive(Message.FloatValue(t1, 10.5))
        assertEquals(1, module.size)
        assertEquals(10.5, module[0])
        assertNotNull(lastSizeMessage)
        val size1 = lastSizeMessage as Message.FloatValue
        assertEquals(t1, size1.time)
        assertEquals(1.0, size1.value)

        val t2 = ScoreTime(2000)
        module.addIn.receive(Message.FloatValue(t2, 20.5))
        assertEquals(2, module.size)
        assertEquals(20.5, module[1])
        val size2 = lastSizeMessage as Message.FloatValue
        assertEquals(t2, size2.time)
        assertEquals(2.0, size2.value)
    }

    @Test
    fun testGetWithIndexAsValue() {
        val module = ValueListModule()
        module.addIn.receive(Message.FloatValue(ScoreTime(1), 100.0))
        module.addIn.receive(Message.FloatValue(ScoreTime(2), 200.0))
        module.addIn.receive(Message.FloatValue(ScoreTime(3), 300.0))

        var lastOutMessage: Message? = null
        val receiver = object : InputJack("recv", module) {
            override fun receive(msg: Message) {
                lastOutMessage = msg
            }
        }
        module.out.connect(receiver)

        val tGet0 = ScoreTime(10)
        module.getIn.receive(Message.FloatValue(tGet0, 0.0))
        assertNotNull(lastOutMessage)
        assertEquals(100.0, (lastOutMessage as Message.FloatValue).value)
        assertEquals(tGet0, (lastOutMessage as Message.FloatValue).time)

        val tGet1 = ScoreTime(20)
        module.getIn.receive(Message.FloatValue(tGet1, 1.0))
        assertEquals(200.0, (lastOutMessage as Message.FloatValue).value)
        assertEquals(tGet1, (lastOutMessage as Message.FloatValue).time)

        val tGet2 = ScoreTime(30)
        module.getIn.receive(Message.FloatValue(tGet2, 2.0))
        assertEquals(300.0, (lastOutMessage as Message.FloatValue).value)
        assertEquals(tGet2, (lastOutMessage as Message.FloatValue).time)
    }

    @Test
    fun testGetOutOfRangeDoesNothing() {
        val module = ValueListModule()
        module.addIn.receive(Message.FloatValue(ScoreTime(1), 42.0))

        var lastOutMessage: Message? = null
        val receiver = object : InputJack("recv", module) {
            override fun receive(msg: Message) {
                lastOutMessage = msg
            }
        }
        module.out.connect(receiver)

        // Query index 1 (size is 1, so index 1 is out of range)
        module.getIn.receive(Message.FloatValue(ScoreTime(10), 1.0))
        assertNull(lastOutMessage, "get out of range should do nothing")

        // Query negative index
        module.getIn.receive(Message.FloatValue(ScoreTime(20), -1.0))
        assertNull(lastOutMessage, "negative index should do nothing")

        // Query large index
        module.getIn.receive(Message.FloatValue(ScoreTime(30), 100.0))
        assertNull(lastOutMessage, "large index should do nothing")
    }

    @Test
    fun testSetWithIndex() {
        val module = ValueListModule()
        module.addIn.receive(Message.FloatValue(ScoreTime(1), 10.0))
        module.addIn.receive(Message.FloatValue(ScoreTime(2), 20.0))
        module.addIn.receive(Message.FloatValue(ScoreTime(3), 30.0))

        module.indexIn.setValue(1.0)
        module.setIn.receive(Message.FloatValue(ScoreTime(10), 99.0))

        assertEquals(10.0, module[0])
        assertEquals(99.0, module[1])
        assertEquals(30.0, module[2])
    }

    @Test
    fun testSetOutOfRangeDoesNothing() {
        val module = ValueListModule()
        module.addIn.receive(Message.FloatValue(ScoreTime(1), 10.0))
        module.addIn.receive(Message.FloatValue(ScoreTime(2), 20.0))

        // Index 2 is out of range (size is 2)
        module.indexIn.setValue(2.0)
        module.setIn.receive(Message.FloatValue(ScoreTime(10), 99.0))
        assertEquals(2, module.size)
        assertEquals(10.0, module[0])
        assertEquals(20.0, module[1])
        assertNull(module[2])

        // Negative index
        module.indexIn.setValue(-1.0)
        module.setIn.receive(Message.FloatValue(ScoreTime(20), 88.0))
        assertEquals(2, module.size)
        assertEquals(10.0, module[0])
        assertEquals(20.0, module[1])
    }

    @Test
    fun testClearResetsSizeAndFiresZero() {
        val module = ValueListModule()
        module.addIn.receive(Message.FloatValue(ScoreTime(1), 10.0))
        module.addIn.receive(Message.FloatValue(ScoreTime(2), 20.0))
        assertEquals(2, module.size)

        var lastSizeMessage: Message? = null
        val receiver = object : InputJack("recv", module) {
            override fun receive(msg: Message) {
                lastSizeMessage = msg
            }
        }
        module.sizeOut.connect(receiver)

        val tClear = ScoreTime(50)
        module.clearIn.receive(Message.FloatValue(tClear, 0.0))
        assertEquals(0, module.size)
        assertNotNull(lastSizeMessage)
        val sizeMsg = lastSizeMessage as Message.FloatValue
        assertEquals(tClear, sizeMsg.time)
        assertEquals(0.0, sizeMsg.value)

        // get at 0 should now do nothing
        var lastOutMessage: Message? = null
        val outReceiver = object : InputJack("recvOut", module) {
            override fun receive(msg: Message) {
                lastOutMessage = msg
            }
        }
        module.out.connect(outReceiver)
        module.getIn.receive(Message.FloatValue(ScoreTime(60), 0.0))
        assertNull(lastOutMessage)
    }

    @Test
    fun testCapacityEnforcementAndReallocationOnClear() {
        val module = ValueListModule()
        assertEquals(1024, module.currentStorageCapacity)

        // Set capacity to 2 without clearing
        module.capacityIn.setValue(2.0)
        // Storage should not have reallocated yet
        assertEquals(1024, module.currentStorageCapacity)

        module.addIn.receive(Message.FloatValue(ScoreTime(1), 1.0))
        module.addIn.receive(Message.FloatValue(ScoreTime(2), 2.0))
        assertEquals(2, module.size)

        // 3rd element should be ignored because capacity is 2
        module.addIn.receive(Message.FloatValue(ScoreTime(3), 3.0))
        assertEquals(2, module.size)
        assertEquals(1024, module.currentStorageCapacity)

        // Change capacity to 512 and kick clear
        module.capacityIn.setValue(512.0)
        module.clearIn.receive(Message.FloatValue(ScoreTime(10), 0.0))
        assertEquals(0, module.size)
        assertEquals(512, module.currentStorageCapacity, "Storage should reallocate on clear")
    }

    @Test
    fun testRegistryLookup() {
        val creator = ModuleRegistry.findCreator("valueList")
        assertNotNull(creator)
        val module = creator()
        assertTrue(module is ValueListModule)
        assertEquals("valueList", module.name)
    }
}

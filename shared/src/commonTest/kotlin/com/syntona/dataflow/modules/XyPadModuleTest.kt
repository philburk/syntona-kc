package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.unitgen.SineOscillator
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XyPadModuleTest {

    @Test
    fun testDefaultProperties() {
        val pad = XyPadModule()
        assertEquals("xyPad", pad.type)
        assertEquals("xyPad", pad.name)

        assertEquals(3, pad.inputs.size)
        assertEquals("kick", pad.inputs[0].name)
        assertTrue(pad.inputs[0].isKickable)
        assertEquals("x", pad.inputs[1].name)
        assertEquals(0.0, pad.inX.min)
        assertEquals(1.0, pad.inX.max)
        assertEquals("y", pad.inputs[2].name)
        assertEquals(0.0, pad.inY.min)
        assertEquals(1.0, pad.inY.max)

        assertEquals(4, pad.outputs.size)
        assertEquals("x", pad.outputs[0].name)
        assertEquals("y", pad.outputs[1].name)
        assertEquals("on", pad.outputs[2].name)
        assertEquals("off", pad.outputs[3].name)

        assertEquals(0.5, pad.x)
        assertEquals(0.5, pad.y)

        assertEquals(225f, pad.width)
        assertEquals(180f, pad.height)
    }

    @Test
    fun testPressAndRelease() {
        val pad = XyPadModule()
        var receivedOn: Double? = null
        var receivedOff: Double? = null

        val dummyOn = object : InputJack("dummyOn", pad) {
            override fun receive(msg: Message) {
                if (msg is Message.FloatValue) receivedOn = msg.value
            }
        }
        val dummyOff = object : InputJack("dummyOff", pad) {
            override fun receive(msg: Message) {
                if (msg is Message.FloatValue) receivedOff = msg.value
            }
        }

        pad.on.connect(dummyOn)
        pad.off.connect(dummyOff)

        pad.press()
        assertEquals(1.0, receivedOn)
        assertEquals(null, receivedOff)

        pad.release()
        assertEquals(0.0, receivedOff)
    }

    @Test
    fun testSetValuesAndMessageOrder() {
        val pad = XyPadModule()
        val receivedOutX = mutableListOf<Double>()
        val receivedOutY = mutableListOf<Double>()
        val sendOrder = mutableListOf<String>()

        val dummyTargetX = object : InputJack("targetX", pad) {
            override fun receive(msg: Message) {
                if (msg is Message.FloatValue) {
                    receivedOutX.add(msg.value)
                    sendOrder.add("x")
                }
            }
        }
        val dummyTargetY = object : InputJack("targetY", pad) {
            override fun receive(msg: Message) {
                if (msg is Message.FloatValue) {
                    receivedOutY.add(msg.value)
                    sendOrder.add("y")
                }
            }
        }

        pad.outX.connect(dummyTargetX)
        pad.outY.connect(dummyTargetY)

        // setX
        pad.setX(0.75)
        assertEquals(0.75, pad.x)
        assertEquals(listOf(0.75), receivedOutX)

        // setY
        pad.setY(0.25)
        assertEquals(0.25, pad.y)
        assertEquals(listOf(0.25), receivedOutY)

        // setXY: sends bottom jack (y) first, then top jack (x)
        sendOrder.clear()
        pad.setXY(0.9, 0.1)
        assertEquals(0.9, pad.x)
        assertEquals(0.1, pad.y)
        assertEquals(listOf("y", "x"), sendOrder)

        // kick execution: sends bottom jack (y) first, then top jack (x)
        sendOrder.clear()
        pad.execute(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(listOf("y", "x"), sendOrder)
    }

    @Test
    fun testReceivingInputMessages() {
        val pad = XyPadModule()
        pad.inX.receive(Message.FloatValue(ScoreTime.Zero, 0.8))
        assertEquals(0.8, pad.x)
        assertEquals(0.8, pad.outX.getValue())

        pad.inY.receive(Message.FloatValue(ScoreTime.Zero, 0.3))
        assertEquals(0.3, pad.y)
        assertEquals(0.3, pad.outY.getValue())
    }

    @Test
    fun testInheritanceOnFirstAndSecondConnection() {
        val rootGraph = GraphState()
        val pad = XyPadModule()
        rootGraph.addModule(pad, Offset.Zero)

        val osc = UnitGeneratorModule("sineOsc", SineOscillator())
        rootGraph.addModule(osc, Offset.Zero)

        val clock = ClockModule()
        rootGraph.addModule(clock, Offset.Zero)

        val freqJack = osc.inputs.find { it.name == "frequency" }!!
        val rateJack = clock.inputs.find { it.name == "rateHz" }!!

        // Connect outX to frequency
        rootGraph.connect(pad.outX, freqJack)
        assertEquals(40.0, pad.inX.min)
        assertEquals(8000.0, pad.inX.max)
        assertEquals(440.0, pad.x)
        assertEquals(440.0, freqJack.getValue())

        // Connect outY to rateHz
        rootGraph.connect(pad.outY, rateJack)
        assertEquals(0.1, pad.inY.min)
        assertEquals(100.0, pad.inY.max)
        assertEquals(2.0, pad.y)
        assertEquals(2.0, rateJack.getValue())

        // Modify X, connect to second target with different range
        pad.setX(1000.0)
        val osc2 = UnitGeneratorModule("sineOsc2", SineOscillator())
        rootGraph.addModule(osc2, Offset.Zero)
        val freqJack2 = osc2.inputs.find { it.name == "frequency" }!!
        freqJack2.min = 20.0
        freqJack2.max = 10000.0

        rootGraph.connect(pad.outX, freqJack2)
        // Range expands
        assertEquals(20.0, pad.inX.min)
        assertEquals(10000.0, pad.inX.max)
        // Value preserved on second connection
        assertEquals(1000.0, pad.x)

        // Changing value now sends to both connected targets
        pad.setX(800.0)
        assertEquals(800.0, freqJack.getValue())
        assertEquals(800.0, freqJack2.getValue())
    }

    @Test
    fun testSerializationRoundTrip() {
        val rootGraph = GraphState()
        val pad = XyPadModule()
        pad.inX.min = 10.0
        pad.inX.max = 500.0
        pad.inY.min = -1.0
        pad.inY.max = 1.0
        pad.setXY(123.45, -0.67)
        pad.width = 320f
        pad.height = 250f
        rootGraph.addModule(pad, Offset(50f, 60f))

        val json = GraphSerializer.serialize(rootGraph, rootGraph.modules)
        val restoredGraph = GraphState()
        GraphSerializer.deserialize(restoredGraph, json)

        val restoredPad = restoredGraph.modules.find { it is XyPadModule } as? XyPadModule
        assertNotNull(restoredPad)
        assertEquals(10.0, restoredPad.inX.min)
        assertEquals(500.0, restoredPad.inX.max)
        assertEquals(123.45, restoredPad.x, 0.001)
        assertEquals(-1.0, restoredPad.inY.min)
        assertEquals(1.0, restoredPad.inY.max)
        assertEquals(-0.67, restoredPad.y, 0.001)
        assertEquals(320f, restoredPad.width)
        assertEquals(250f, restoredPad.height)
    }

    @Test
    fun testModuleRegistryEntryAndDescription() {
        val creator = ModuleRegistry.findCreator("xyPad")
        assertNotNull(creator)
        val module = creator()
        assertTrue(module is XyPadModule)
        assertNotNull(ModuleRegistry.moduleDescriptions["xyPad"])
    }
}

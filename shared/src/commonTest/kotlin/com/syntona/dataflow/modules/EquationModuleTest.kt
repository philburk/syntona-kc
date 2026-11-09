package com.syntona.dataflow.modules

import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.core.model.ScoreTime
import com.syntona.core.model.ScoreClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EquationModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        // f(x)
        val fx = EquationModule("f(x)")
        assertEquals("f(x)", fx.name)
        assertNotNull(fx.inputX)
        assertTrue(fx.inputX.isKickable)
        assertEquals(null, fx.inputY)
        assertEquals(null, fx.inputZ)
        assertNotNull(fx.output)

        // f(x,y)
        val fxy = EquationModule("f(x,y)")
        assertEquals("f(x,y)", fxy.name)
        assertNotNull(fxy.inputX)
        assertTrue(fxy.inputX.isKickable)
        assertNotNull(fxy.inputY)
        assertEquals(false, fxy.inputY.isKickable)
        assertEquals(null, fxy.inputZ)
        assertNotNull(fxy.output)

        // f(x,y,z)
        val fxyz = EquationModule("f(x,y,z)")
        assertEquals("f(x,y,z)", fxyz.name)
        assertNotNull(fxyz.inputX)
        assertTrue(fxyz.inputX.isKickable)
        assertNotNull(fxyz.inputY)
        assertEquals(false, fxyz.inputY.isKickable)
        assertNotNull(fxyz.inputZ)
        assertEquals(false, fxyz.inputZ.isKickable)
        assertNotNull(fxyz.output)
    }

    @Test
    fun testModuleRegistryCreator() {
        for (name in listOf("f(x)", "f(x,y)", "f(x,y,z)")) {
            val creator = ModuleRegistry.findCreator(name)
            assertNotNull(creator, "EquationModule creator should be registered in ModuleRegistry under '$name'")
            val module = creator()
            assertTrue(module is EquationModule, "Created module should be an instance of EquationModule")
            assertEquals(name, module.name)
        }
    }

    @Test
    fun testEvaluation() {
        val graphState = GraphState()
        val module = EquationModule("f(x,y,z)")
        module.compile("x * y + z")
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        var receivedVal: Double? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    receivedVal = triggerMessage.value
                }
            }
        }
        graphState.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(module.output, receiver.input)

        // Set inputs
        module.inputX.setValue(4.0)
        module.inputY!!.setValue(3.0)
        module.inputZ!!.setValue(5.0)

        // Kick the module (inputX is kickable, which triggers execute)
        module.onMessageReceived(module.inputX, Message.FloatValue(ScoreClock.now(), 4.0))

        assertEquals(17.0, receivedVal, "4.0 * 3.0 + 5.0 should be 17.0")
    }

    @Test
    fun testSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = EquationModule("f(x,y)")
        module.compile("sin(x) * y")
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize
        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"equation\": \"sin(x) * y\"") || jsonStr.contains("\"equation\":\"sin(x) * y\""), "Serialized string should contain equation value")

        // Deserialize
        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserializedModule = newGraphState.modules.find { it.name == "f(x,y)" } as? EquationModule
        assertNotNull(deserializedModule)
        assertEquals("sin(x) * y", deserializedModule.equation, "Deserialized equation should match original")
    }

    @Test
    fun testCompileError() {
        val module = EquationModule("f(x)")
        assertEquals(null, module.errorMessage)

        val success = module.compile("sizzzn(x)")
        kotlin.test.assertFalse(success, "Compilation of invalid equation should fail")
        assertNotNull(module.errorMessage, "errorMessage should be populated when compilation fails")
        assertTrue(module.errorMessage!!.contains("Unexpected character") || module.errorMessage!!.contains("Unexpected characters") || module.errorMessage!!.isNotEmpty())
    }

    private fun evaluateFormula(formula: String, x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): Double {
        val module = EquationModule("f(x,y,z)")
        val compiled = module.compile(formula)
        assertTrue(compiled, "Failed to compile formula '$formula': ${module.errorMessage}")

        var result: Double? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    result = triggerMessage.value
                }
            }
        }
        val graph = GraphState()
        graph.addModule(module, androidx.compose.ui.geometry.Offset.Zero)
        graph.addModule(receiver, androidx.compose.ui.geometry.Offset.Zero)
        graph.connect(module.output, receiver.input)

        module.inputX.setValue(x)
        module.inputY?.setValue(y)
        module.inputZ?.setValue(z)
        module.onMessageReceived(module.inputX, Message.FloatValue(ScoreClock.now(), x))

        assertNotNull(result, "Result should not be null for formula '$formula'")
        return result
    }

    @Test
    fun testUnaryOperatorsFloorAndRound() {
        assertEquals(3.0, evaluateFormula("floor(x)", x = 3.8))
        assertEquals(3.0, evaluateFormula("floor(x)", x = 3.0))
        assertEquals(-3.0, evaluateFormula("floor(x)", x = -2.3))

        assertEquals(3.0, evaluateFormula("round(x)", x = 3.2))
        assertEquals(4.0, evaluateFormula("round(x)", x = 3.7))
        assertEquals(-3.0, evaluateFormula("round(x)", x = -2.8))
    }

    @Test
    fun testPitchFrequencyConversionOperators() {
        // Pitch to frequency: Concert A (69.0) -> 440.0 Hz
        assertEquals(440.0, evaluateFormula("p2f(x)", x = 69.0), 1e-4)

        // Frequency to pitch: 440.0 Hz -> Concert A (69.0)
        assertEquals(69.0, evaluateFormula("f2p(x)", x = 440.0), 1e-4)

        // Roundtrip: f2p(p2f(x)) == x
        assertEquals(60.0, evaluateFormula("f2p(p2f(x))", x = 60.0), 1e-4)
        assertEquals(72.5, evaluateFormula("f2p(p2f(x))", x = 72.5), 1e-4)

        // Compound expression with floor and p2f
        assertEquals(440.0, evaluateFormula("p2f(floor(x))", x = 69.8), 1e-4)
    }

    @Test
    fun testRandVoidFunction() {
        // Test multiple evaluations of rand()
        for (i in 0 until 20) {
            val v = evaluateFormula("rand()")
            assertTrue(v >= 0.0 && v < 1.0, "rand() value $v should be in [0.0, 1.0)")
        }

        // Test rand() in compound expression: round(rand() * 10)
        for (i in 0 until 20) {
            val v = evaluateFormula("round(rand() * 10)")
            assertTrue(v >= 0.0 && v <= 10.0, "round(rand() * 10) value $v should be in [0.0, 10.0]")
            assertEquals(kotlin.math.round(v), v, "Value should be an integer")
        }
    }

    @Test
    fun testResizableSizeSerializationAndDeserialization() {
        val graphState = GraphState()
        val module = EquationModule("f(x,y)")
        module.compile("p2f(x) + rand()")
        module.width = 280f
        module.height = 75f
        graphState.addModule(module, androidx.compose.ui.geometry.Offset.Zero)

        // Serialize
        val jsonStr = GraphSerializer.serialize(graphState, graphState.modules)
        assertTrue(jsonStr.contains("\"w\":280") || jsonStr.contains("\"w\": 280"), "Serialized JSON should contain width")
        assertTrue(jsonStr.contains("\"h\":75") || jsonStr.contains("\"h\": 75"), "Serialized JSON should contain height")

        // Deserialize
        val newGraphState = GraphState()
        GraphSerializer.deserialize(newGraphState, jsonStr)
        val deserialized = newGraphState.modules.find { it.name == "f(x,y)" } as? EquationModule
        assertNotNull(deserialized)
        assertEquals("p2f(x) + rand()", deserialized.equation)
        assertEquals(280f, deserialized.width)
        assertEquals(75f, deserialized.height)
    }

    @Test
    fun testMinimumDimensionsEnforced() {
        val fx = EquationModule("f(x)")
        assertEquals(140f, fx.minWidth)
        assertEquals(40f, fx.minHeight)
        fx.width = 50f
        fx.height = 20f
        assertEquals(140f, fx.width, "Width should be clamped to minWidth")
        assertEquals(40f, fx.height, "Height should be clamped to minHeight")

        val fxy = EquationModule("f(x,y)")
        assertEquals(140f, fxy.minWidth)
        assertEquals(60f, fxy.minHeight)
        fxy.width = 50f
        fxy.height = 20f
        assertEquals(140f, fxy.width, "Width should be clamped to minWidth")
        assertEquals(60f, fxy.height, "Height should be clamped to minHeight so Y jack is visible")

        val fxyz = EquationModule("f(x,y,z)")
        assertEquals(140f, fxyz.minWidth)
        assertEquals(88f, fxyz.minHeight)
        fxyz.width = 80f
        fxyz.height = 30f
        assertEquals(140f, fxyz.width, "Width should be clamped to minWidth")
        assertEquals(88f, fxyz.height, "Height should be clamped to minHeight so Z jack is visible")
    }

    @Test
    fun testDeserializationClampsSmallDimensions() {
        val json = """
            {
              "modules": [
                {
                  "id": 0,
                  "type": "core.math.f(x,y)",
                  "name": "f(x,y)",
                  "w": 50,
                  "h": 20
                }
              ],
              "connections": []
            }
        """.trimIndent()

        val graphState = GraphState()
        val deserialized = GraphSerializer.deserialize(graphState, json)
        assertEquals(1, deserialized.size)
        val mod = deserialized[0] as EquationModule
        assertTrue(mod.width >= mod.minWidth, "Width should be clamped to minWidth")
        assertTrue(mod.height >= mod.minHeight, "Height should be clamped to minHeight")
        assertEquals(60f, mod.height, "f(x,y) height should be clamped to 60f to keep Y jack visible")
    }

    @Test
    fun testAbsFunction() {
        assertEquals(5.0, evaluateFormula("abs(x)", x = -5.0))
        assertEquals(5.0, evaluateFormula("abs(x)", x = 5.0))
        assertEquals(0.0, evaluateFormula("abs(x)", x = 0.0))
        assertEquals(12.3, evaluateFormula("abs(x)", x = -12.3), 1e-6)
    }

    @Test
    fun testMinAndMaxFunctions() {
        // min binary
        assertEquals(3.0, evaluateFormula("min(x, y)", x = 3.0, y = 7.0))
        assertEquals(3.0, evaluateFormula("min(x, y)", x = 7.0, y = 3.0))
        assertEquals(-5.0, evaluateFormula("min(x, y)", x = -5.0, y = 2.0))
        assertEquals(4.0, evaluateFormula("min(x, 4)", x = 9.0))
        assertEquals(0.0, evaluateFormula("min(x, 4)", x = 0.0))

        // max binary
        assertEquals(7.0, evaluateFormula("max(x, y)", x = 3.0, y = 7.0))
        assertEquals(7.0, evaluateFormula("max(x, y)", x = 7.0, y = 3.0))
        assertEquals(2.0, evaluateFormula("max(x, y)", x = -5.0, y = 2.0))
        assertEquals(10.0, evaluateFormula("max(x, 10)", x = 2.0))
        assertEquals(15.0, evaluateFormula("max(x, 10)", x = 15.0))
    }

    @Test
    fun testMinAndMaxMultiArgAndCompound() {
        // 3 args
        assertEquals(1.0, evaluateFormula("min(x, y, z)", x = 5.0, y = 1.0, z = 8.0))
        assertEquals(8.0, evaluateFormula("max(x, y, z)", x = 5.0, y = 1.0, z = 8.0))

        // 4 args
        assertEquals(2.0, evaluateFormula("min(10, 4, 2, 7)"))
        assertEquals(10.0, evaluateFormula("max(10, 4, 2, 7)"))

        // Compound: min with abs and max
        assertEquals(3.0, evaluateFormula("min(abs(x), max(y, z))", x = -3.0, y = 10.0, z = 5.0))
        assertEquals(5.0, evaluateFormula("max(abs(x), min(y, z))", x = -5.0, y = 1.0, z = 2.0))
    }

    @Test
    fun testMinMaxArgErrors() {
        val mod = EquationModule("f(x)")
        val compileMinSingleArg = mod.compile("min(x)")
        kotlin.test.assertFalse(compileMinSingleArg, "min with single arg should fail compilation")
        assertTrue(mod.errorMessage?.contains("requires at least 2 arguments") == true)

        val compileMaxSingleArg = mod.compile("max(x)")
        kotlin.test.assertFalse(compileMaxSingleArg, "max with single arg should fail compilation")
        assertTrue(mod.errorMessage?.contains("requires at least 2 arguments") == true)
    }

    @Test
    fun testChooseFunction() {
        val seen = mutableSetOf<Int>()
        for (i in 0 until 200) {
            val v = evaluateFormula("choose(6)")
            assertEquals(kotlin.math.floor(v), v, "choose(6) should return an integer")
            val intVal = v.toInt()
            assertTrue(intVal in 0..5, "choose(6) value $intVal should be in 0..5")
            seen.add(intVal)
        }
        assertEquals((0..5).toSet(), seen, "choose(6) should cover 0..5 over 200 samples")

        // Variable argument in f(x), f(x,y), f(x,y,z)
        for (i in 0 until 50) {
            val v = evaluateFormula("choose(x) + y + z", x = 4.0, y = 10.0, z = 100.0)
            val offset = (v - 110.0).toInt()
            assertTrue(offset in 0..3, "choose(4) offset $offset should be in 0..3")
        }

        // Edge case: non-positive N returns 0.0
        assertEquals(0.0, evaluateFormula("choose(0)"))
        assertEquals(0.0, evaluateFormula("choose(-5)"))
    }

    @Test
    fun testChooseModule() {
        val creator = ModuleRegistry.findCreator("choose")
        assertNotNull(creator, "choose should be registered in ModuleRegistry")
        assertEquals("core.math.choose", ModuleRegistry.getBundlePath("choose"))

        val choose = creator() as ChooseModule
        assertEquals("choose", choose.name)
        assertEquals("kick", choose.kick.name)
        assertTrue(choose.range.isInteger)
        assertEquals(false, choose.range.isKickable)
        assertTrue(choose.output.isInteger)

        var lastValue: Double? = null
        val receiver = object : com.syntona.dataflow.model.Module("receiver") {
            val input = addInput("in", isKickable = true)
            override fun execute(triggerMessage: Message) {
                if (triggerMessage is Message.FloatValue) {
                    lastValue = triggerMessage.value
                }
            }
        }
        choose.output.connect(receiver.input)

        // Setting range should not trigger output
        choose.range.setValue(6.0)
        assertEquals(null, lastValue, "Setting range should not trigger output")

        val seen = mutableSetOf<Int>()
        for (i in 0 until 200) {
            choose.kick.receive(Message.FloatValue(ScoreTime.Zero, 1.0))
            val v = lastValue
            assertNotNull(v)
            assertEquals(kotlin.math.floor(v), v, "Output should be an integer")
            val intVal = v.toInt()
            assertTrue(intVal in 0..5, "Output $intVal should be in 0..5 when range is 6")
            seen.add(intVal)
        }
        assertEquals((0..5).toSet(), seen, "choose module should produce all values 0..5")
    }
}



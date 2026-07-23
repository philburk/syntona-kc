package com.syntona.dataflow.modules

import com.softsynth.ksyn.unitgen.LineIn
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LineInModuleTest {

    @Test
    fun testLineInModuleRegistry() {
        val creator = ModuleRegistry.findCreator("lineIn")
        assertNotNull(creator, "LineIn creator should be registered in ModuleRegistry under 'lineIn'")
        
        val module = creator()
        assertTrue(module is UnitGeneratorModule, "Created module should be an instance of UnitGeneratorModule")
        assertTrue(module.unit is LineIn, "Underlying unit should be LineIn")

        assertEquals(0, module.inputs.size, "LineIn should have 0 input jacks")
        assertEquals(2, module.outputs.size, "LineIn should have 2 audio output jacks")

        val output0 = module.outputs.find { it.name == "output0" }
        val output1 = module.outputs.find { it.name == "output1" }
        assertNotNull(output0, "LineIn should have output0 jack")
        assertNotNull(output1, "LineIn should have output1 jack")

        assertTrue(output0 is AudioOutputJack, "output0 should be AudioOutputJack")
        assertTrue(output1 is AudioOutputJack, "output1 should be AudioOutputJack")

        module.teardown()
    }
}

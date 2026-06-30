package com.syntona.dataflow.editor

import com.softsynth.ksyn.unitgen.SineOscillator
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.DataQueueInputJack
import com.syntona.dataflow.model.DataQueueOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.SegmentedEnvelopeModule
import com.syntona.dataflow.modules.UnitGeneratorModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionTest {

    @Test
    fun testDataQueueConnectionRules() {
        val graphState = GraphState()

        val segEnv = SegmentedEnvelopeModule()
        val monoRdr = UnitGeneratorModule("monoRdr", VariableRateMonoReader())
        val sineOsc = UnitGeneratorModule("sineOsc", SineOscillator())
        val fader = FaderModule()

        // Locate jacks
        val dataQueueOut = segEnv.outputs.first { it is DataQueueOutputJack }
        val dataQueueIn = monoRdr.inputs.first { it is DataQueueInputJack }
        
        val audioOut = sineOsc.outputs.first { it is AudioOutputJack }
        val audioIn = monoRdr.inputs.first { it.name == "amplitude" && it is AudioInputJack }

        val standardOut = fader.outputs.first { it.name == "out" }
        val standardIn = fader.inputs.first { it.name == "in" }

        // 1. DataQueueOutputJack -> DataQueueInputJack should succeed
        graphState.connect(dataQueueOut, dataQueueIn)
        assertEquals(1, graphState.connections.size)
        assertTrue(graphState.connections.any { it.output === dataQueueOut && it.input === dataQueueIn })

        // Clear connections
        graphState.connections.clear()

        // 2. DataQueueOutputJack -> AudioInputJack should fail
        graphState.connect(dataQueueOut, audioIn)
        assertEquals(0, graphState.connections.size)

        // 3. DataQueueOutputJack -> StandardInputJack should fail
        graphState.connect(dataQueueOut, standardIn)
        assertEquals(0, graphState.connections.size)

        // 4. StandardOutputJack -> DataQueueInputJack should fail
        graphState.connect(standardOut, dataQueueIn)
        assertEquals(0, graphState.connections.size)

        // 5. AudioOutputJack -> DataQueueInputJack should fail
        graphState.connect(audioOut, dataQueueIn)
        assertEquals(0, graphState.connections.size)

        // 6. AudioOutputJack -> AudioInputJack should succeed
        graphState.connect(audioOut, audioIn)
        assertEquals(1, graphState.connections.size)
        assertTrue(graphState.connections.any { it.output === audioOut && it.input === audioIn })

        graphState.connections.clear()

        // 7. StandardOutputJack -> AudioInputJack should succeed (acts as scalar control parameter)
        graphState.connect(standardOut, audioIn)
        assertEquals(1, graphState.connections.size)
        assertTrue(graphState.connections.any { it.output === standardOut && it.input === audioIn })
    }
}

package com.syntona.dataflow.editor

import com.softsynth.ksyn.unitgen.SineOscillator
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.SequentialDataInputJack
import com.syntona.dataflow.model.SequentialDataOutputJack
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.OutputJack
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.FloatSampleModule
import com.syntona.dataflow.modules.SampleReaderModule
import com.syntona.dataflow.modules.UnitGeneratorModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionTest {

    @Test
    fun testSequentialDataConnectionRules() {
        val graphState = GraphState()

        val sample = FloatSampleModule()
        val sampleReader = SampleReaderModule()
        val sineOsc = UnitGeneratorModule("sineOsc", SineOscillator())
        val fader = FaderModule()

        // Locate jacks
        val dataOut = sample.dataOutput
        val dataIn = sampleReader.dataInput

        val audioOut = sineOsc.outputs.first { it is AudioOutputJack }
        val audioIn = sampleReader.amplitude

        val standardOut = fader.outputs.first { it.name == "out" }
        val standardIn = fader.inputs.first { it.name == "in" }

        // 1. SequentialDataOutputJack -> SequentialDataInputJack should succeed
        graphState.connect(dataOut, dataIn)
        assertEquals(1, graphState.connections.size)
        assertTrue(graphState.connections.any { it.output === dataOut && it.input === dataIn })

        // Clear connections
        graphState.connections.clear()

        // 2. SequentialDataOutputJack -> AudioInputJack should fail
        graphState.connect(dataOut, audioIn)
        assertEquals(0, graphState.connections.size)

        // 3. SequentialDataOutputJack -> StandardInputJack should fail
        graphState.connect(dataOut, standardIn)
        assertEquals(0, graphState.connections.size)

        // 4. StandardOutputJack -> SequentialDataInputJack should fail
        graphState.connect(standardOut, dataIn)
        assertEquals(0, graphState.connections.size)

        // 5. AudioOutputJack -> SequentialDataInputJack should fail
        graphState.connect(audioOut, dataIn)
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

    @Test
    fun testWrappedUnitQueuesSequentialDataOnItsPort() = kotlinx.coroutines.runBlocking {
        val reader = VariableRateMonoReader()
        val module = UnitGeneratorModule("monoReader", reader)
        val dataIn = module.inputs.first { it is SequentialDataInputJack }

        val sample = FloatSampleModule()
        sample.dataOutput.connect(dataIn)
        sample.dataOutput.send(
            Message.SequentialDataMessage(ScoreClock.now(), sample.sample)
        )
        (SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine).generateNextBuffer()
        assertTrue(reader.dataQueue.hasMore(), "data should be queued on the unit's port")
        module.teardown()
    }
}

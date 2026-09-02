package com.syntona.dataflow.modules

import com.softsynth.ksyn.data.FloatSample
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.DataQueueInputJack
import com.syntona.dataflow.model.IntegerInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SampleWriterModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = SampleWriterModule()
        assertEquals("sampleWriter", module.name)

        assertEquals(5, module.inputs.size)
        assertEquals("data", module.inputs[0].name)
        assertTrue(module.inputs[0] is DataQueueInputJack)
        assertFalse(module.inputs[0].isValueDraggable)
        assertFalse(module.inputs[0].isDisplayableValue)
        assertFalse(module.inputs[0].isModuleEditorVisible)
        assertEquals("start", module.inputs[1].name)
        assertTrue(module.inputs[1] is CommandJack)
        assertEquals("stop", module.inputs[2].name)
        assertTrue(module.inputs[2] is CommandJack)
        assertEquals("looping", module.inputs[3].name)
        assertTrue(module.inputs[3] is IntegerInputJack)
        assertEquals("in", module.inputs[4].name)
        assertTrue(module.inputs[4] is AudioInputJack)

        assertEquals(0, module.outputs.size)

        module.teardown()
    }

    @Test
    fun testRegisteredInRegistry() {
        val creator = ModuleRegistry.findCreator("sampleWriter")
        assertNotNull(creator, "sampleWriter should be registered")
        val module = creator()
        assertTrue(module is SampleWriterModule)
        module.teardown()
    }

    @Test
    fun testSampleWriterWritesAudioToSampleBuffer() = kotlinx.coroutines.runBlocking {
        val sampleModule = FloatSampleModule()
        val writerModule = SampleWriterModule()

        // Initialize sample with zeros
        for (i in 0 until sampleModule.sample.numFrames) {
            sampleModule.sample.buffer[i] = 0.0f
        }

        // Connect sample.data -> writer.data
        writerModule.dataInput.receive(
            Message.DataQueueMessage(ScoreClock.now(), sampleModule.sample, Message.DataQueueOpcode.QUEUE)
        )
        assertEquals(sampleModule.sample, writerModule.dataInput.currentData)

        // Set input value to a known test signal level
        writerModule.input.setValue(0.75)

        // Trigger write
        writerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Process audio blocks through synthesis engine
        val engine = SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        // Check that frames were written with non-zero values
        val hasWrittenData = sampleModule.sample.buffer.take(com.softsynth.ksyn.Synthesizer.FRAMES_PER_BLOCK).any { it != 0.0f }
        assertTrue(hasWrittenData, "SampleWriter should write audio data into the sample buffer")

        writerModule.stopIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        sampleModule.teardown()
        writerModule.teardown()
    }

    @Test
    fun testSampleWriterLoopingAndStop() = kotlinx.coroutines.runBlocking {
        val sampleModule = FloatSampleModule()
        val writerModule = SampleWriterModule()

        writerModule.dataInput.receive(
            Message.DataQueueMessage(ScoreClock.now(), sampleModule.sample, Message.DataQueueOpcode.QUEUE)
        )

        // Enable looping
        writerModule.loopInput.setValue(1.0)
        writerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        val engine = SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()
        assertTrue(writerModule.unit.dataQueue.hasMore())

        // Stop should clear the data queue
        writerModule.stopIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        engine.generateNextBuffer()
        assertFalse(writerModule.unit.dataQueue.hasMore(), "Stop should clear data queue")

        sampleModule.teardown()
        writerModule.teardown()
    }

    @Test
    fun testSampleWriterWithRecorderData() = kotlinx.coroutines.runBlocking {
        val graphState = GraphState()
        val recorderModule = RecorderModule()
        val writerModule = SampleWriterModule()

        graphState.addModule(recorderModule, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(writerModule, androidx.compose.ui.geometry.Offset.Zero)

        graphState.connect(recorderModule.dataOutput, writerModule.dataInput)

        // Allocate recording memory
        recorderModule.unit.recording.extend(512)
        recorderModule.sendData()

        assertEquals(recorderModule.unit.recording, writerModule.dataInput.currentData)

        writerModule.input.setValue(0.5)
        writerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        val engine = SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        writerModule.stopIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        recorderModule.teardown()
        writerModule.teardown()
    }

    @Test
    fun testDisplayCursorSecondsAndIsActive() = kotlinx.coroutines.runBlocking {
        val sampleModule = FloatSampleModule()
        val writerModule = SampleWriterModule()

        writerModule.dataInput.receive(
            Message.DataQueueMessage(ScoreClock.now(), sampleModule.sample, Message.DataQueueOpcode.QUEUE)
        )

        assertFalse(writerModule.isActive, "Should be inactive initially")
        assertEquals(0.0, writerModule.displayCursorSeconds, 0.001)

        writerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        val engine = SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        assertTrue(writerModule.isActive, "Should be active after start")
        assertTrue(writerModule.displayCursorSeconds >= 0.0)

        writerModule.stopIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        engine.generateNextBuffer()
        assertFalse(writerModule.isActive, "Should be inactive after stop")

        sampleModule.teardown()
        writerModule.teardown()
    }
}

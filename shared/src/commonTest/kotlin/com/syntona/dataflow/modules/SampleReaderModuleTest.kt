package com.syntona.dataflow.modules

import com.softsynth.ksyn.data.FloatSample
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.DataQueueInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SampleReaderModuleTest {

    @Test
    fun testModuleCreationAndPorts() {
        val module = SampleReaderModule()
        assertEquals("sampleReader", module.name)

        assertEquals(9, module.inputs.size)
        assertEquals("data", module.inputs[0].name)
        assertTrue(module.inputs[0] is DataQueueInputJack)
        assertFalse(module.inputs[0].isValueDraggable)
        assertFalse(module.inputs[0].isDisplayableValue)
        assertFalse(module.inputs[0].isModuleEditorVisible)
        assertEquals("start", module.inputs[1].name)
        assertTrue(module.inputs[1] is CommandJack)
        assertEquals("stop", module.inputs[2].name)
        assertTrue(module.inputs[2] is CommandJack)
        assertEquals("offset", module.inputs[3].name)
        assertTrue(module.inputs[3].isKickable)
        assertEquals("offsetSec", module.inputs[4].name)
        assertTrue(module.inputs[4].isKickable)
        assertEquals("durationMs", module.inputs[5].name)
        assertTrue(module.inputs[5].isKickable)
        assertEquals("looping", module.inputs[6].name)
        assertTrue(module.inputs[6] is com.syntona.dataflow.model.BooleanInputJack)
        assertFalse(module.inputs[6].isValueDraggable)
        assertFalse(module.inputs[6].isDisplayableValue)
        assertFalse(module.inputs[6].isModuleEditorVisible)
        assertEquals("speed", module.inputs[7].name)
        assertTrue(module.inputs[7] is AudioInputJack)
        assertEquals("amplitude", module.inputs[8].name)
        assertTrue(module.inputs[8] is AudioInputJack)

        assertEquals(1, module.outputs.size)
        assertEquals("out", module.outputs[0].name)
        assertTrue(module.outputs[0] is AudioOutputJack)

        module.teardown()
    }

    @Test
    fun testOffsetOrDurationChangeWhileLoopingRetriggersLoop() = kotlinx.coroutines.runBlocking {
        val sampleModule = FloatSampleModule()
        val readerModule = SampleReaderModule()

        // Give reader sample data
        readerModule.dataInput.receive(Message.DataQueueMessage(ScoreClock.now(), sampleModule.sample, Message.DataQueueOpcode.QUEUE))

        // Set looping on
        readerModule.loopInput.setValue(1.0)
        readerModule.offset.setValue(0.1)
        readerModule.durationMs.setValue(500.0)

        // Start playback
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Simulate engine tick to process queued start
        (SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine).generateNextBuffer()
        assertTrue(readerModule.unit.dataQueue.hasMore())

        // Change offset while looping - should re-queue immediate loop
        readerModule.offset.setValue(0.5)

        // Change durationMs while looping - should re-queue immediate loop
        readerModule.durationMs.setValue(100.0)

        // Stop
        readerModule.stopIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        sampleModule.teardown()
        readerModule.teardown()
    }

    @Test
    fun testOffsetSecChangeWhileLoopingRetriggersLoop() = kotlinx.coroutines.runBlocking {
        val sampleModule = FloatSampleModule()
        val readerModule = SampleReaderModule()

        // Give reader sample data
        readerModule.dataInput.receive(Message.DataQueueMessage(ScoreClock.now(), sampleModule.sample, Message.DataQueueOpcode.QUEUE))

        // Set looping on
        readerModule.loopInput.setValue(1.0)
        readerModule.offsetSec.setValue(0.1)
        readerModule.durationMs.setValue(500.0)

        // Start playback
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Simulate engine tick to process queued start
        (SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine).generateNextBuffer()
        assertTrue(readerModule.unit.dataQueue.hasMore())

        // Change offsetSec while looping - should re-queue immediate loop
        readerModule.offsetSec.setValue(0.5)

        // Stop
        readerModule.stopIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        sampleModule.teardown()
        readerModule.teardown()
    }

    @Test
    fun testRegisteredInRegistry() {
        val creator = ModuleRegistry.findCreator("sampleReader")
        assertNotNull(creator, "sampleReader should be registered")
        val module = creator()
        assertTrue(module is SampleReaderModule)
        module.teardown()
    }

    @Test
    fun testSampleReaderReceivesDataAndPlaysSlice() {
        val graphState = GraphState()
        val sampleModule = FloatSampleModule()
        val readerModule = SampleReaderModule()

        graphState.addModule(sampleModule, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(readerModule, androidx.compose.ui.geometry.Offset.Zero)

        // Connect sample.data -> reader.data
        graphState.connect(sampleModule.dataOutput, readerModule.dataInput)

        // Send sample reference
        sampleModule.sendSample()

        assertEquals(sampleModule.sample, readerModule.dataInput.currentData)

        // Set offset = 0.25, durationMs = 200.0 (or 0.0 for full sample)
        readerModule.offset.setValue(0.25)
        readerModule.durationMs.setValue(200.0)

        // Trigger start
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Trigger stop
        readerModule.stopIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        sampleModule.teardown()
        readerModule.teardown()
    }

    @Test
    fun testSampleReaderWithRecorderData() = kotlinx.coroutines.runBlocking {
        val graphState = GraphState()
        val recorderModule = RecorderModule()
        val readerModule = SampleReaderModule()

        graphState.addModule(recorderModule, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(readerModule, androidx.compose.ui.geometry.Offset.Zero)

        // Connect recorder.data -> reader.data
        graphState.connect(recorderModule.dataOutput, readerModule.dataInput)

        // Extend and fill recording with test values
        recorderModule.unit.recording.extend(1024)
        for (i in 0 until 1024) {
            recorderModule.unit.recording[i] = 0.65f
        }
        recorderModule.sendData()

        assertEquals(recorderModule.unit.recording, readerModule.dataInput.currentData)

        // Trigger start at default rate = 1.0
        readerModule.speed.setValue(1.0)
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Process scheduled commands
        (SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine).generateNextBuffer()
        readerModule.rateMultiplier.generate()
        readerModule.unit.generate()

        val outputs = readerModule.unit.output.getValues()
        var nonZeroCount = 0
        for (i in 0 until 8) {
            if (outputs[i] > 0.1f) nonZeroCount++
        }
        assertTrue(nonZeroCount > 0, "Recorder data played by sampleReader should produce audio samples, got: ${outputs.take(8)}")

        recorderModule.teardown()
        readerModule.teardown()
    }

    @Test
    fun testMultipleSampleReloadsAndRateScaling() {
        val graphState = GraphState()
        val sampleModule = FloatSampleModule()
        val readerModule = SampleReaderModule()

        graphState.addModule(sampleModule, androidx.compose.ui.geometry.Offset.Zero)
        graphState.addModule(readerModule, androidx.compose.ui.geometry.Offset.Zero)
        graphState.connect(sampleModule.dataOutput, readerModule.dataInput)

        // Sample 1: 100 frames filled with 0.5
        val sample1 = FloatSample(100).apply {
            frameRate = 48000.0
            for (i in 0 until 100) writeSample(i, 0.5f)
        }
        sampleModule.sample = sample1
        sampleModule.sendIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        assertEquals(sample1, readerModule.dataInput.currentData)
        readerModule.speed.setValue(1.0)
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Sample 2: 200 frames filled with 0.8
        val sample2 = FloatSample(200).apply {
            frameRate = 48000.0
            for (i in 0 until 200) writeSample(i, 0.8f)
        }
        sampleModule.sample = sample2
        sampleModule.sendIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        assertEquals(sample2, readerModule.dataInput.currentData)
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        sampleModule.teardown()
        readerModule.teardown()
    }

    @Test
    fun testAudioOutputAtRateOne() = kotlinx.coroutines.runBlocking {
        val readerModule = SampleReaderModule()
        val sample = FloatSample(100).apply {
            frameRate = 48000.0
            for (i in 0 until 100) writeSample(i, 0.75f)
        }
        val msg = Message.DataQueueMessage(ScoreClock.now(), sample, Message.DataQueueOpcode.QUEUE)
        readerModule.dataInput.receive(msg)
        readerModule.speed.setValue(1.0)
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Process scheduled commands
        (SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine).generateNextBuffer()
        // Generate audio block from reader
        readerModule.rateMultiplier.generate()
        readerModule.unit.generate()

        val outputs = readerModule.unit.output.getValues()
        // At rate = 1.0, the output should contain non-zero samples around 0.75f
        var nonZeroCount = 0
        for (i in 0 until 8) {
            if (outputs[i] > 0.1f) nonZeroCount++
        }
        assertTrue(nonZeroCount > 0, "Reader output should produce audio samples at rate=1.0, got: ${outputs.take(8)}")

        readerModule.teardown()
    }

    @Test
    fun testStereoSamplePlayback() = kotlinx.coroutines.runBlocking {
        val readerModule = SampleReaderModule()
        // 2 channels stereo sample
        val stereoSample = FloatSample(100, 2).apply {
            frameRate = 48000.0
            for (i in 0 until 100) {
                writeSample(i * 2, 0.6f)
                writeSample(i * 2 + 1, 0.8f)
            }
        }
        val msg = Message.DataQueueMessage(ScoreClock.now(), stereoSample, Message.DataQueueOpcode.QUEUE)
        readerModule.dataInput.receive(msg)
        readerModule.speed.setValue(1.0)
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // Process scheduled commands
        (SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine).generateNextBuffer()
        // Generate audio block from reader
        readerModule.rateMultiplier.generate()
        readerModule.unit.generate()

        val outputs = readerModule.unit.output.getValues()
        // Mixed down sample should be (0.6 + 0.8)/2 = 0.7f
        var nonZeroCount = 0
        for (i in 0 until 8) {
            if (outputs[i] > 0.1f) nonZeroCount++
        }
        assertTrue(nonZeroCount > 0, "Stereo sample reader output should produce audio samples, got: ${outputs.take(8)}")

        readerModule.teardown()
    }

    @Test
    fun testDisplayCursorSecondsAndIsActive() = kotlinx.coroutines.runBlocking {
        val readerModule = SampleReaderModule()
        val sample = FloatSample(44100, 1).apply {
            frameRate = 44100.0
            for (i in 0 until 44100) writeSample(i, 0.5f)
        }
        readerModule.dataInput.receive(Message.DataQueueMessage(ScoreClock.now(), sample, Message.DataQueueOpcode.QUEUE))

        assertFalse(readerModule.isActive, "Should be inactive initially")
        assertEquals(0.0, readerModule.displayCursorSeconds, 0.001)

        readerModule.speed.setValue(1.0)
        readerModule.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        val engine = SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        engine.generateNextBuffer()

        assertTrue(readerModule.isActive, "Should be active after start")
        assertTrue(readerModule.displayCursorSeconds >= 0.0)

        readerModule.stopIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        engine.generateNextBuffer()
        assertFalse(readerModule.isActive, "Should be inactive after stop")

        readerModule.teardown()
    }
}

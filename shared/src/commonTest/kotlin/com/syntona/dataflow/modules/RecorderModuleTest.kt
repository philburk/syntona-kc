package com.syntona.dataflow.modules

import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.SequentialDataOutputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.unitgen.Recorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecorderModuleTest {

    private fun kick(module: RecorderModule, name: String) {
        val jack = module.inputs.first { it.name == name }
        jack.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
    }

    @Test
    fun testModuleCreationAndPorts() {
        val module = RecorderModule()
        assertEquals("recorder", module.name)

        assertEquals(7, module.inputs.size)
        assertEquals("in", module.inputs[0].name)
        assertEquals("amplitude", module.inputs[1].name)
        assertEquals("latencyMs", module.inputs[2].name)
        assertEquals("record", module.inputs[3].name)
        assertEquals("play", module.inputs[4].name)
        assertEquals("pause", module.inputs[5].name)
        assertEquals("rewind", module.inputs[6].name)
        assertTrue(module.inputs[0] is AudioInputJack, "in should carry audio")
        assertTrue(module.inputs[1] is AudioInputJack, "amplitude should carry audio")
        assertTrue(module.inputs[3] is CommandJack, "record should be CommandJack")
        assertTrue(module.inputs[4] is CommandJack, "play should be CommandJack")
        assertTrue(module.inputs[5] is CommandJack, "pause should be CommandJack")
        assertTrue(module.inputs[6] is CommandJack, "rewind should be CommandJack")

        val latencyJack = module.latencyMillis
        assertEquals(0.0, latencyJack.min)
        assertEquals(1000.0, latencyJack.max)

        assertEquals(2, module.outputs.size)
        assertEquals("out", module.outputs[0].name)
        assertTrue(module.outputs[0] is AudioOutputJack, "out should carry audio")
        assertEquals("data", module.outputs[1].name)
        assertTrue(module.outputs[1] is SequentialDataOutputJack, "data should carry SequentialData")
    }

    @Test
    fun testNonCommandInputsExcludeCommandJacks() {
        val module = RecorderModule()
        val nonCommandInputs = module.inputs.filter { it !is CommandJack }
        assertEquals(3, nonCommandInputs.size)
        assertEquals(listOf("in", "amplitude", "latencyMs"), nonCommandInputs.map { it.name })
    }

    @Test
    fun testCommandJacksDriveTheTransport() {
        val module = RecorderModule()
        assertEquals(Recorder.Mode.PAUSED, module.unit.mode)

        kick(module, "record")
        assertEquals(Recorder.Mode.RECORDING, module.unit.mode)

        kick(module, "pause")
        assertEquals(Recorder.Mode.PAUSED, module.unit.mode)

        kick(module, "play")
        assertEquals(Recorder.Mode.PLAYING, module.unit.mode)

        kick(module, "pause")
        assertEquals(Recorder.Mode.PAUSED, module.unit.mode)
    }

    @Test
    fun testPlaybackAndRewindWithLatencyMs() {
        val module = RecorderModule()
        // Set latencyMs to 100 ms
        module.latencyMillis.receive(Message.FloatValue(ScoreTime.Zero, 100.0))

        // Simulate recording audio frames
        kick(module, "record")
        repeat(600) {
            module.unit.replenish()
            module.unit.generate()
        }
        kick(module, "pause")

        // Cursor is now at the end of recording (>= length)
        assertTrue(module.unit.cursor >= module.unit.recording.length)

        // 1. Kicking play when cursor is at the end of recording should seek to latencyFrames
        kick(module, "play")
        val expectedOffset = (0.1 * module.unit.frameRate).toInt()
        assertEquals(expectedOffset, module.unit.cursor, "Play should seek to latency offset when cursor is at end of recording")

        // 2. Kicking rewind should seek to latencyFrames and display 0.00 seconds
        kick(module, "pause")
        kick(module, "rewind")
        assertEquals(expectedOffset, module.unit.cursor, "Rewind should seek to latency offset")
        assertEquals(0.0, module.displayCursorSeconds, 1e-6, "Display cursor seconds should show 0.00 on rewind")
    }

    @Test
    fun testRewindReturnsCursorToStart() {
        val module = RecorderModule()
        kick(module, "record")
        module.unit.generate()
        assertTrue(module.unit.cursor > 0, "recording a block should advance the cursor")

        kick(module, "pause")
        kick(module, "rewind")
        assertEquals(0, module.unit.cursor)
    }

    @Test
    fun testPoolIsFilledAtConstructionSoTheFirstRecordDoesNotAllocate() {
        val module = RecorderModule()
        assertTrue(
            module.unit.recording.pooledSegmentCount > 0,
            "constructor should have primed the segment pool"
        )
        kick(module, "record")
        module.unit.generate()
        assertEquals(0, module.unit.recording.unpooledAllocations)
    }

    @Test
    fun testAmplitudeJackIsWiredToTheUnitsAmplitudePort() {
        val module = RecorderModule()
        val jack = module.inputs[1] as AudioInputJack
        assertTrue(
            jack.unitInputPort === module.unit.amplitude,
            "the amplitude jack should drive the recorder's amplitude port"
        )

        // Record a constant, then play it back through a reduced amplitude set via the jack.
        kick(module, "record")
        module.unit.generate()
        kick(module, "pause")
        module.unit.rewind()

        jack.receive(Message.FloatValue(ScoreTime.Zero, 0.5))
        assertEquals(0.5f, module.unit.amplitude.get(0))
    }

    @Test
    fun testAutoSetMeasuredLatencyOnModuleCreation() {
        val inputDevice = "TestMic"
        val outputDevice = "TestSpeaker"
        com.syntona.core.audio.AudioSettings.setMeasuredLatencyFrames(inputDevice, outputDevice, 480)

        val origInput = com.syntona.core.audio.AudioSettings.activeInputName
        val origOutput = com.syntona.core.audio.AudioSettings.activeOutputName
        try {
            com.syntona.core.audio.AudioSettings.activeInputName = inputDevice
            com.syntona.core.audio.AudioSettings.activeOutputName = outputDevice

            val module = RecorderModule()
            val expectedMs = 480 * 1000.0 / module.unit.frameRate
            assertEquals(expectedMs, module.latencyMillis.getDoubleValue(), 1e-5, "latencyMs should automatically load stored round-trip latency")
        } finally {
            com.syntona.core.audio.AudioSettings.activeInputName = origInput
            com.syntona.core.audio.AudioSettings.activeOutputName = origOutput
        }
    }

    @Test
    fun testDeserializationPrefersMeasuredLatencyOverJsonFile() {
        val testModule = RecorderModule()
        testModule.latencyMillis.setValue(15.0)
        val tempState = GraphState()
        tempState.addModule(testModule, androidx.compose.ui.geometry.Offset.Zero)
        val json = GraphSerializer.serialize(tempState, tempState.modules)

        val inputDevice = "TestMic2"
        val outputDevice = "TestSpeaker2"
        com.syntona.core.audio.AudioSettings.setMeasuredLatencyFrames(inputDevice, outputDevice, 480)

        val origInput = com.syntona.core.audio.AudioSettings.activeInputName
        val origOutput = com.syntona.core.audio.AudioSettings.activeOutputName
        try {
            // Case A: Measurement IS available -> prefer measured latency over file value (15.0)
            com.syntona.core.audio.AudioSettings.activeInputName = inputDevice
            com.syntona.core.audio.AudioSettings.activeOutputName = outputDevice

            val graphStateA = GraphState()
            GraphSerializer.deserialize(graphStateA, json)
            val recA = graphStateA.modules.first { it is RecorderModule } as RecorderModule
            val expectedMs = 480 * 1000.0 / recA.unit.frameRate
            assertEquals(expectedMs, recA.latencyMillis.getDoubleValue(), 1e-5, "Should keep measured latency when available")

            // Case B: NO measurement is available -> fall back to file value (15.0)
            com.syntona.core.audio.AudioSettings.activeInputName = "UnmeasuredMic"
            com.syntona.core.audio.AudioSettings.activeOutputName = "UnmeasuredSpeaker"

            val graphStateB = GraphState()
            GraphSerializer.deserialize(graphStateB, json)
            val recB = graphStateB.modules.first { it is RecorderModule } as RecorderModule
            assertEquals(15.0, recB.latencyMillis.getDoubleValue(), 1e-5, "Should fall back to JSON file value when no measurement is available")
        } finally {
            com.syntona.core.audio.AudioSettings.activeInputName = origInput
            com.syntona.core.audio.AudioSettings.activeOutputName = origOutput
        }
    }

    @Test
    fun testRegisteredInRegistry() {
        val creator = ModuleRegistry.findCreator("recorder")
        assertNotNull(creator, "recorder should be registered")
        assertTrue(creator() is RecorderModule)
    }

    @Test
    fun testRecordingWhileReaderIsLoopingDoesNotCrash() {
        val recorder = RecorderModule()
        val reader = SampleReaderModule()
        val graph = GraphState()
        graph.addModule(recorder, androidx.compose.ui.geometry.Offset.Zero)
        graph.addModule(reader, androidx.compose.ui.geometry.Offset.Zero)
        graph.connect(recorder.dataOutput, reader.dataInput)

        // Record some audio into recorder
        kick(recorder, "record")
        repeat(50) {
            recorder.unit.replenish()
            recorder.unit.generate()
        }
        kick(recorder, "pause")

        val firstRecording = recorder.unit.recording
        val initialLength = firstRecording.length
        assertEquals(firstRecording, reader.dataInput.currentData)
        assertTrue(initialLength > 0)

        // Start looping on the reader
        reader.loopInput.setValue(1.0)
        reader.startIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))

        // While reader is looping, start a new recording take on recorder
        kick(recorder, "record")

        // New recording is sent on record command; old recording is not truncated or mutated
        val secondRecording = recorder.unit.recording
        assertTrue(secondRecording !== firstRecording)
        assertEquals(secondRecording, reader.dataInput.currentData)
        assertEquals(initialLength, firstRecording.length)

        // Reader can still safely read samples from the old recording without IndexOutOfBoundsException
        reader.unit.generate()

        // Generate some new recording frames
        repeat(20) {
            recorder.unit.replenish()
            recorder.unit.generate()
        }

        // Stop new recording
        kick(recorder, "pause")

        // New recording should now contain the newly recorded frames
        assertEquals(secondRecording, reader.dataInput.currentData)
        assertTrue(secondRecording.length > 0)

        recorder.teardown()
        reader.teardown()
    }

    @Test
    fun testStoppingRecordingByPlayingSendsData() {
        val recorder = RecorderModule()
        val reader = SampleReaderModule()
        val graph = GraphState()
        graph.addModule(recorder, androidx.compose.ui.geometry.Offset.Zero)
        graph.addModule(reader, androidx.compose.ui.geometry.Offset.Zero)
        graph.connect(recorder.dataOutput, reader.dataInput)

        val initialRecording = recorder.unit.recording
        assertEquals(initialRecording, reader.dataInput.currentData)

        // Start recording
        kick(recorder, "record")
        repeat(30) {
            recorder.unit.replenish()
            recorder.unit.generate()
        }

        // Stop recording by kicking "play"
        kick(recorder, "play")
        assertEquals(Recorder.Mode.PLAYING, recorder.unit.mode)

        // The new recording take must have been sent to reader
        val recordedData = recorder.unit.recording
        assertTrue(recordedData !== initialRecording)
        assertEquals(recordedData, reader.dataInput.currentData)
        assertTrue(recordedData.length > 0)

        recorder.teardown()
        reader.teardown()
    }
}

package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.util.SampleLoader
import com.syntona.core.audio.writeWavFile
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.readBinaryFileDirect
import com.syntona.dataflow.model.Message
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SampleAndRecorderWavTest {

    private fun generateTestWavBytes(numFrames: Int, sampleRate: Int = 44100): ByteArray {
        val samples = FloatArray(numFrames) { i ->
            sin(2.0 * kotlin.math.PI * 440.0 * i / sampleRate).toFloat()
        }
        val tempPath = "build/tmp_test_sine.wav"
        writeWavFile(tempPath, samples, sampleRate, numChannels = 1)
        val bytes = readBinaryFileDirect(tempPath)
        assertNotNull(bytes, "WAV bytes should be readable from $tempPath")
        return bytes
    }

    @Test
    fun testFloatSampleModuleLoadWav() {
        val module = FloatSampleModule()
        val numFrames = 4410
        val wavBytes = generateTestWavBytes(numFrames, 44100)

        val success = module.loadWav(wavBytes, "test_sample.wav")
        assertTrue(success)
        assertEquals("test_sample.wav", module.filePath)
        assertEquals(numFrames, module.sample.numFrames)
        assertEquals(44100.0, module.sample.frameRate)
        assertEquals(1, module.sample.channelsPerFrame)
    }

    @Test
    fun testFloatSampleModuleSerialization() {
        val graph = GraphState()
        val module = FloatSampleModule()
        module.filePath = "samples/kick.wav"
        graph.addModule(module, Offset.Zero)

        val json = GraphSerializer.serialize(graph, graph.modules)
        assertTrue(json.contains("\"filePath\":\"samples/kick.wav\"") || json.contains("\"filePath\": \"samples/kick.wav\""))

        val newGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newGraph, json)
        assertEquals(1, deserialized.size)
        val newMod = deserialized[0] as FloatSampleModule
        assertEquals("samples/kick.wav", newMod.filePath)
    }

    @Test
    fun testRecorderModuleLoadWavAndPlaybackWithoutLatencyCompensation() {
        val module = RecorderModule()
        module.latencyMillis.setValue(50.0) // 50 ms latency
        val latencyFrames = (module.latencySeconds * module.unit.frameRate).toInt()
        assertTrue(latencyFrames > 0, "Latency frames should be > 0")

        val numFrames = 4800
        val wavBytes = generateTestWavBytes(numFrames, module.unit.frameRate.toInt())

        val success = module.loadWav(wavBytes, "loaded_take.wav")
        assertTrue(success)
        assertTrue(module.isLoadedFromFile)
        assertEquals("loaded_take.wav", module.filePath)
        assertEquals(numFrames, module.unit.length)
        assertEquals(0, module.unit.cursor)

        // When loaded from file, playing starts from cursor 0 (no latency offset)
        module.playIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(0, module.unit.cursor)
        assertTrue(module.unit.isPlaying)

        // Rewinding also rewinds to 0 (no latency offset)
        module.rewindIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(0, module.unit.cursor)
    }

    @Test
    fun testRecorderModuleRecordingAppliesLatencyCompensationOnPlayAndSave() {
        val module = RecorderModule()
        module.latencyMillis.setValue(50.0) // 50 ms latency
        val latencyFrames = (module.latencySeconds * module.unit.frameRate).toInt()
        assertTrue(latencyFrames > 0)

        // Simulate recording
        module.recordIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertFalse(module.isLoadedFromFile)
        assertTrue(module.unit.isRecording)

        // Simulate recorded audio in unit
        val totalRecorded = 4800
        var rem = totalRecorded
        while (rem > 0) {
            val chunk = minOf(rem, module.unit.recording.segmentSize)
            module.unit.recording.extend(chunk)
            rem -= chunk
        }
        for (i in 0 until totalRecorded) {
            module.unit.recording[i] = (i.toFloat() / totalRecorded)
        }
        module.unit.pause()

        // When playing recorded audio, it skips latencyFrames
        module.playIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(latencyFrames, module.unit.cursor)

        // When rewinding recorded audio, it seeks to latencyFrames
        module.rewindIn.receive(Message.FloatValue(ScoreTime.Zero, 0.0))
        assertEquals(latencyFrames, module.unit.cursor)

        // When saving recorded audio, it skips the first latencyFrames
        val savePath = "build/tmp_recorded_saved.wav"
        val saveSuccess = module.saveWavFile(savePath)
        assertTrue(saveSuccess)
        assertEquals(savePath, module.filePath)

        val savedBytes = readBinaryFileDirect(savePath)
        assertNotNull(savedBytes)
        val loaded = SampleLoader.loadFloatSample(savedBytes)
        assertEquals(totalRecorded - latencyFrames, loaded.numFrames)
        // First saved frame corresponds to frame at index latencyFrames
        assertEquals(latencyFrames.toFloat() / totalRecorded, loaded.readSample(0), 1e-4f)
    }

    @Test
    fun testRecorderModuleSerialization() {
        val graph = GraphState()
        val module = RecorderModule()
        module.filePath = "captures/drum_take.wav"
        graph.addModule(module, Offset.Zero)

        val json = GraphSerializer.serialize(graph, graph.modules)
        assertTrue(json.contains("\"filePath\":\"captures/drum_take.wav\"") || json.contains("\"filePath\": \"captures/drum_take.wav\""))

        val newGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newGraph, json)
        assertEquals(1, deserialized.size)
        val newMod = deserialized[0] as RecorderModule
        assertEquals("captures/drum_take.wav", newMod.filePath)
    }
}

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

    private fun generateTestStereoWavBytes(numFrames: Int, sampleRate: Int = 44100): ByteArray {
        val samples = FloatArray(numFrames * 2) { i ->
            val frame = i / 2
            val channel = i % 2
            if (channel == 0) {
                sin(2.0 * kotlin.math.PI * 440.0 * frame / sampleRate).toFloat()
            } else {
                sin(2.0 * kotlin.math.PI * 880.0 * frame / sampleRate).toFloat()
            }
        }
        val tempPath = "build/tmp_test_stereo.wav"
        writeWavFile(tempPath, samples, sampleRate, numChannels = 2)
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
        assertEquals("test_sample.wav", module.fileName)
        assertEquals(numFrames, module.sample.numFrames)
        assertEquals(44100.0, module.sample.frameRate)
        assertEquals(1, module.sample.channelsPerFrame)
    }

    @Test
    fun testFloatSampleModuleLoadStereoWav() {
        val module = FloatSampleModule()
        val numFrames = 4410
        val wavBytes = generateTestStereoWavBytes(numFrames, 44100)

        val success = module.loadWav(wavBytes, "test_stereo_sample.wav")
        assertTrue(success)
        assertEquals("test_stereo_sample.wav", module.filePath)
        assertEquals(numFrames, module.sample.numFrames)
        assertEquals(44100.0, module.sample.frameRate)
        assertEquals(1, module.sample.channelsPerFrame)
    }

    @Test
    fun testSamplePlayerModuleLoadWav() {
        val module = SamplePlayerModule()
        val numFrames = 4410
        val wavBytes = generateTestWavBytes(numFrames, 44100)

        val success = module.loadWav(wavBytes, "test_player.wav")
        assertTrue(success)
        assertEquals("test_player.wav", module.filePath)
        assertEquals("test_player.wav", module.fileName)
        assertEquals(numFrames, module.sample.numFrames)
        assertEquals(44100.0, module.sample.frameRate)
        assertEquals(1, module.sample.channelsPerFrame)
        module.teardown()
    }

    @Test
    fun testSamplePlayerModuleLoadStereoWav() {
        val module = SamplePlayerModule()
        val numFrames = 4410
        val wavBytes = generateTestStereoWavBytes(numFrames, 44100)

        val success = module.loadWav(wavBytes, "test_stereo_player.wav")
        assertTrue(success)
        assertEquals("test_stereo_player.wav", module.filePath)
        assertEquals(numFrames, module.sample.numFrames)
        assertEquals(44100.0, module.sample.frameRate)
        assertEquals(1, module.sample.channelsPerFrame)
        module.teardown()
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
    fun testSamplePlayerModuleSerialization() {
        val graph = GraphState()
        val module = SamplePlayerModule()
        module.filePath = "samples/snare.wav"
        graph.addModule(module, Offset.Zero)

        val json = GraphSerializer.serialize(graph, graph.modules)
        assertTrue(json.contains("\"filePath\":\"samples/snare.wav\"") || json.contains("\"filePath\": \"samples/snare.wav\""))

        val newGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(newGraph, json)
        assertEquals(1, deserialized.size)
        val newMod = deserialized[0] as SamplePlayerModule
        assertEquals("samples/snare.wav", newMod.filePath)
        newMod.teardown()
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
    fun testRecorderModuleLoadStereoWav() {
        val module = RecorderModule()
        val numFrames = 4800
        val wavBytes = generateTestStereoWavBytes(numFrames, module.unit.frameRate.toInt())

        val success = module.loadWav(wavBytes, "loaded_stereo_take.wav")
        assertTrue(success)
        assertTrue(module.isLoadedFromFile)
        assertEquals("loaded_stereo_take.wav", module.filePath)
        assertEquals(numFrames, module.unit.length)
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

    @Test
    fun testPatchDeserializationWithSampleAndReadersAutomaticallySendsData() {
        val tempPath = "build/tmp_sample_readers_test.wav"
        val samples = FloatArray(4410) { 0.5f }
        writeWavFile(tempPath, samples, 44100, numChannels = 1)

        val json = """
        {
          "modules": [
            {
              "id": 0,
              "type": "ksyn.data.sample",
              "name": "sample",
              "x": 100.0,
              "y": 100.0,
              "filePath": "$tempPath"
            },
            {
              "id": 1,
              "type": "ksyn.data.sampleReader",
              "name": "sampleReader1",
              "x": 300.0,
              "y": 100.0
            },
            {
              "id": 2,
              "type": "ksyn.data.sampleReader",
              "name": "sampleReader2",
              "x": 300.0,
              "y": 300.0
            }
          ],
          "connections": [
            {
              "from": 0,
              "fromJack": "data",
              "to": 1,
              "toJack": "data"
            },
            {
              "from": 0,
              "fromJack": "data",
              "to": 2,
              "toJack": "data"
            }
          ]
        }
        """.trimIndent()

        val graph = GraphState()
        val deserialized = GraphSerializer.deserialize(graph, json)
        assertEquals(3, deserialized.size)

        val sampleMod = deserialized[0] as FloatSampleModule
        val reader1 = deserialized[1] as SampleReaderModule
        val reader2 = deserialized[2] as SampleReaderModule

        assertNotNull(reader1.dataInput.currentData, "Reader 1 should automatically have currentData")
        assertNotNull(reader2.dataInput.currentData, "Reader 2 should automatically have currentData")
        assertEquals(sampleMod.sample, reader1.dataInput.currentData)
        assertEquals(sampleMod.sample, reader2.dataInput.currentData)
        assertEquals(4410, reader1.dataInput.currentData?.numFrames)

        sampleMod.teardown()
        reader1.teardown()
        reader2.teardown()
    }

    @Test
    fun testRelativeWavPathSerializationAndDeserialization() {
        val testDir = java.io.File("build/test_relative_patch_dir")
        val samplesDir = java.io.File(testDir, "samples")
        samplesDir.mkdirs()

        val wavFile = java.io.File(samplesDir, "snare.wav")
        val samples = FloatArray(4410) { 0.7f }
        writeWavFile(wavFile.absolutePath, samples, 44100, numChannels = 1)

        val sntnFile = java.io.File(testDir, "drumkit.sntn")

        val graph = GraphState()
        val sampleModule = FloatSampleModule()
        sampleModule.loadWavFile(wavFile.absolutePath)
        assertEquals("snare.wav", sampleModule.fileName)
        graph.addModule(sampleModule, Offset.Zero)

        val playerModule = SamplePlayerModule()
        playerModule.loadWavFile(wavFile.absolutePath)
        assertEquals("snare.wav", playerModule.fileName)
        graph.addModule(playerModule, Offset.Zero)

        // Serialize relative to sntnFile
        val json = GraphSerializer.serialize(graph, graph.modules, basePath = sntnFile.absolutePath)
        assertTrue(json.contains("\"filePath\": \"samples/snare.wav\"") || json.contains("\"filePath\":\"samples/snare.wav\""), "filePath should be relative in JSON: $json")

        // Simulate moving the entire folder to movedDir
        val movedDir = java.io.File("build/moved_relative_patch_dir")
        if (movedDir.exists()) movedDir.deleteRecursively()
        testDir.copyRecursively(movedDir)

        val movedSntnFile = java.io.File(movedDir, "drumkit.sntn")

        val loadedGraph = GraphState()
        val deserialized = GraphSerializer.deserialize(loadedGraph, json, basePath = movedSntnFile.absolutePath)
        assertEquals(2, deserialized.size)

        val loadedSample = deserialized[0] as FloatSampleModule
        val loadedPlayer = deserialized[1] as SamplePlayerModule

        assertEquals(4410, loadedSample.sample.numFrames, "Sample should load audio from moved directory")
        assertEquals(4410, loadedPlayer.sample.numFrames, "Player should load audio from moved directory")

        sampleModule.teardown()
        playerModule.teardown()
        loadedSample.teardown()
        loadedPlayer.teardown()
        testDir.deleteRecursively()
        movedDir.deleteRecursively()
    }

    @Test
    fun testWavOutsidePatchDirectoryRemainsAbsolute() {
        val outsideDir = java.io.File("build/test_outside_dir")
        outsideDir.mkdirs()
        val outsideWav = java.io.File(outsideDir, "global_kick.wav")
        val samples = FloatArray(4410) { 0.3f }
        writeWavFile(outsideWav.absolutePath, samples, 44100, numChannels = 1)

        val patchDir = java.io.File("build/test_patch_inside_dir")
        patchDir.mkdirs()
        val sntnFile = java.io.File(patchDir, "main.sntn")

        val graph = GraphState()
        val sampleModule = FloatSampleModule()
        sampleModule.loadWavFile(outsideWav.absolutePath)
        graph.addModule(sampleModule, Offset.Zero)

        val json = GraphSerializer.serialize(graph, graph.modules, basePath = sntnFile.absolutePath)
        val expectedPath = outsideWav.absolutePath.replace('\\', '/')
        assertTrue(json.contains(expectedPath), "Outside WAV should remain absolute: $json")
        assertFalse(json.contains(".."), "Should not use ../ to relativize outside files")

        sampleModule.teardown()
        outsideDir.deleteRecursively()
        patchDir.deleteRecursively()
    }
}


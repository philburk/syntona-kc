package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.softsynth.ksyn.util.SampleLoader
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.audio.writeWavFile
import com.syntona.dataflow.editor.readBinaryFileDirect
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A self-contained, 1-module WAV sample player.
 * Supports on/off/loop/stop triggering and variable-rate playback.
 * In a polyPatch, the underlying [FloatSample] is shared across all voice instances.
 */
class SamplePlayerModule : Module("samplePlayer") {
    val unit = VariableRateMonoReader()

    var sample: FloatSample by mutableStateOf(createSample())
    var filePath: String? by mutableStateOf(null)

    val onIn: CommandJack
    val offIn: CommandJack
    val loopIn: CommandJack
    val stopIn: CommandJack
    val rate: AudioInputJack
    val amplitude: AudioInputJack
    val output: AudioOutputJack

    init {
        SynthKSyn.synth.add(unit)
        if (unit.isStartRequired) {
            unit.start()
        }

        onIn = addCommandInput("on")
        offIn = addCommandInput("off")
        loopIn = addCommandInput("loop")
        stopIn = addCommandInput("stop")

        rate = addAudioInput("rate", unit.rate, 0).apply {
            min = 0.0
            max = 8.0
            setValue(1.0)
        }
        amplitude = addAudioInput("amplitude", unit.amplitude, 0).apply {
            min = 0.0
            max = 1.0
            setValue(1.0)
        }
        output = addAudioOutput("output", unit.output!!, 0)
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        val timeStamp = SynthKSyn.synth.createTimeStamp()
        when (jack) {
            onIn -> {
                unit.dataQueue.queueOn(sample, timeStamp)
            }
            offIn -> {
                unit.dataQueue.queueOff(sample, false, timeStamp)
            }
            loopIn -> {
                unit.dataQueue.queueLoop(sample)
            }
            stopIn -> {
                unit.dataQueue.clear()
            }
        }
    }

    override fun execute(triggerMessage: Message) {}

    fun loadWav(bytes: ByteArray, path: String? = null): Boolean {
        return try {
            val loaded = SampleLoader.loadFloatSample(bytes).toMono()
            sample = loaded
            if (path != null) filePath = path
            true
        } catch (e: Exception) {
            println("Failed to load WAV into SamplePlayerModule: ${e.message}")
            false
        }
    }

    fun loadWavFile(path: String): Boolean {
        filePath = path
        val bytes = readBinaryFileDirect(path)
        if (bytes != null) {
            return loadWav(bytes, path)
        }
        return false
    }

    fun saveWavFile(path: String): Boolean {
        return try {
            val s = sample
            val numSamples = s.numFrames * s.channelsPerFrame
            val data = FloatArray(numSamples)
            s.read(0, data, 0, s.numFrames)
            val sampleRate = if (s.frameRate > 0.0) s.frameRate.toInt() else SynthKSyn.synth.frameRate
            writeWavFile(path, data, sampleRate, s.channelsPerFrame)
            filePath = path
            true
        } catch (e: Exception) {
            println("Failed to save WAV from SamplePlayerModule: ${e.message}")
            false
        }
    }

    override fun teardown() {
        if (unit.isStartRequired) {
            unit.stop()
        }
        SynthKSyn.synth.remove(unit)
        super.teardown()
    }

    companion object {
        const val DURATION_SECONDS = 8

        private fun createSample(): FloatSample {
            val frameRate = SynthKSyn.synth.frameRate
            return FloatSample(frameRate * DURATION_SECONDS).apply {
                this.frameRate = frameRate.toDouble()
            }
        }
    }
}

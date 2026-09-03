package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.unitgen.Multiply
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.softsynth.ksyn.util.SampleLoader
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.audio.writeWavFile
import com.syntona.dataflow.editor.readBinaryFileDirect
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.BooleanInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A self-contained, 1-module WAV sample player.
 * Supports on/off/stop triggering, looping toggle, and variable-rate playback.
 * In a polyPatch, the underlying [FloatSample] is shared across all voice instances.
 */
class SamplePlayerModule : Module("samplePlayer") {
    val unit = VariableRateMonoReader()
    val rateMultiplier = Multiply()

    var sample: FloatSample by mutableStateOf(createSample())
    var filePath: String? by mutableStateOf(null)

    var isLooping: Boolean
        get() = loopInput.isChecked
        set(value) {
            loopInput.isChecked = value
        }

    val onIn: CommandJack
    val offIn: CommandJack
    val stopIn: CommandJack
    val loopInput: BooleanInputJack
    val looping: BooleanInputJack get() = loopInput
    val speed: AudioInputJack
    val amplitude: AudioInputJack
    val output: AudioOutputJack

    init {
        SynthKSyn.synth.add(rateMultiplier)
        SynthKSyn.synth.add(unit)
        if (rateMultiplier.isStartRequired) {
            rateMultiplier.start()
        }
        if (unit.isStartRequired) {
            unit.start()
        }

        rateMultiplier.inputB.set(sample.frameRate.toFloat())
        rateMultiplier.output.connect(unit.rate)

        onIn = addCommandInput("on")
        offIn = addCommandInput("off")
        stopIn = addCommandInput("stop")
        loopInput = addBooleanInput("looping")

        speed = addAudioInput("speed", rateMultiplier.inputA, 0).apply {
            min = 0.0
            max = 8.0
            setValue(1.0)
        }
        amplitude = addAudioInput("amplitude", unit.amplitude, 0).apply {
            min = 0.0
            max = 1.0
            setValue(1.0)
        }
        output = addAudioOutput("out", unit.output!!, 0)
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        val timeStamp = SynthKSyn.synth.createTimeStamp()
        when (jack) {
            onIn -> {
                if (isLooping || loopInput.isChecked) {
                    unit.dataQueue.queueLoop(sample)
                } else {
                    unit.dataQueue.queueOn(sample, timeStamp)
                }
            }
            offIn -> {
                unit.dataQueue.queueOff(sample, false, timeStamp)
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
            rateMultiplier.inputB.set(loaded.frameRate.toFloat())
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
        if (rateMultiplier.isStartRequired) {
            rateMultiplier.stop()
        }
        rateMultiplier.output.disconnect(unit.rate)
        SynthKSyn.synth.remove(unit)
        SynthKSyn.synth.remove(rateMultiplier)
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

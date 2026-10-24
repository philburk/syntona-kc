package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.util.SampleLoader
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.audio.writeWavFile
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.editor.readBinaryFileDirect
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.SequentialDataOutputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.OutputJack

/**
 * Holds a mono [FloatSample] in memory that can be shared across readers (like [SampleReaderModule]).
 * In a polyPatch, the underlying sample is shared among all voices.
 */
class FloatSampleModule : Module("sample") {
    private var _sample by mutableStateOf(createSample())

    /** In a polyPatch, setting this on the template also gives the new sample to every voice. */
    var sample: FloatSample
        get() = _sample
        set(value) {
            _sample = value
            (parentPatch as? PolyPatchModule)?.syncSample(this, value)
        }
    var filePath: String? by mutableStateOf(null)
    val fileName: String?
        get() = filePath?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }

    val sendIn: CommandJack = addCommandInput("send")
    val dataOutput: SequentialDataOutputJack = addSequentialDataOutput("data")
    val durationOutput: OutputJack = addOutput("durationMs")

    init {
        sendSample()
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        if (jack === sendIn) {
            sendSample()
        }
    }

    fun sendSample() {
        dataOutput.send(Message.SequentialDataMessage(ScoreClock.now(), sample))
        val rate = if (sample.frameRate > 0.0) sample.frameRate else SynthKSyn.synth.frameRate.toDouble()
        val durationMs = if (rate > 0.0) (sample.numFrames * 1000.0 / rate) else 0.0
        durationOutput.send(Message.FloatValue(ScoreTime.Zero, durationMs))
    }

    fun loadWav(bytes: ByteArray, path: String? = null): Boolean {
        return try {
            val loaded = SampleLoader.loadFloatSample(bytes).toMono()
            sample = loaded
            if (path != null) filePath = path
            sendSample()
            true
        } catch (e: Exception) {
            println("Failed to load WAV into FloatSampleModule: ${e.message}")
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
            println("Failed to save WAV from FloatSampleModule: ${e.message}")
            false
        }
    }

    override fun execute(triggerMessage: Message) {}

    companion object {
        /** Frames are allocated for this many seconds of mono audio. */
        const val DURATION_SECONDS = 8

        /**
         * Allocates a silent mono sample holding [DURATION_SECONDS] of audio at the synth's
         * frame rate.
         */
        private fun createSample(): FloatSample {
            val frameRate = SynthKSyn.synth.frameRate
            return FloatSample(frameRate * DURATION_SECONDS).apply {
                this.frameRate = frameRate.toDouble()
            }
        }
    }
}

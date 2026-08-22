package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.DataQueueInputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Plays a slice or loop of a connected sample buffer ([FloatSampleModule] or [RecorderModule]).
 * Supports normalized offset and length for granular synthesis or beat chopping.
 */
class SampleReaderModule : Module("sampleReader") {
    val unit = VariableRateMonoReader()

    var isLooping: Boolean by mutableStateOf(false)
    private var isPlayingLoop = false

    val dataInput: DataQueueInputJack
    val startIn: CommandJack
    val stopIn: CommandJack
    val offset: InputJack
    val durationMs: InputJack
    val loopInput: InputJack
    val rate: AudioInputJack
    val amplitude: AudioInputJack
    val output: AudioOutputJack

    init {
        SynthKSyn.synth.add(unit)
        if (unit.isStartRequired) {
            unit.start()
        }

        dataInput = addDataQueueInput("data", unit.dataQueue, autoQueue = false)
        startIn = addCommandInput("start")
        stopIn = addCommandInput("stop")

        offset = addInput("offset", isKickable = true).apply {
            min = 0.0
            max = 1.0
            setValue(0.0)
        }
        durationMs = addInput("durationMs", isKickable = true).apply {
            min = 0.0
            max = 10000.0
            setValue(0.0)
        }
        loopInput = addInput("loop").apply {
            min = 0.0
            max = 1.0
            setValue(0.0)
        }
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
        when (jack) {
            startIn -> triggerSlice(immediate = true)
            stopIn -> {
                isPlayingLoop = false
                unit.dataQueue.clear(SynthKSyn.synth.createTimeStamp())
            }
            offset, durationMs -> {
                val shouldLoop = isLooping || loopInput.getClippedDoubleValue() >= 0.5
                if (shouldLoop && (isPlayingLoop || unit.dataQueue.hasMore())) {
                    triggerSlice(immediate = false, skipIfOthers = true)
                }
            }
        }
    }

    fun triggerSlice(immediate: Boolean = true, skipIfOthers: Boolean = false) {
        val data = dataInput.currentData ?: return
        val totalFrames = data.numFrames
        if (totalFrames <= 0) return

        val offsetNorm = offset.getClippedDoubleValue().coerceIn(0.0, 1.0)
        val durationMsVal = durationMs.getClippedDoubleValue()

        val startFrame = (offsetNorm * totalFrames).toInt().coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
        val maxAvailable = totalFrames - startFrame

        val numFrames = if (durationMsVal <= 0.0) {
            maxAvailable
        } else {
            val frameRate = data.getRateScaler(0, 1.0 / SynthKSyn.synth.frameRate)
            val requestedFrames = (durationMsVal * 0.001 * frameRate).toInt()
            requestedFrames.coerceIn(1, maxAvailable.coerceAtLeast(1))
        }

        val shouldLoop = isLooping || loopInput.getClippedDoubleValue() >= 0.5
        val timeStamp = SynthKSyn.synth.createTimeStamp()

        if (shouldLoop) {
            isPlayingLoop = true
            val frameRate = data.getRateScaler(0, 1.0 / SynthKSyn.synth.frameRate)
            val crossFadeFrames = (CROSS_FADE_SECONDS * frameRate).toInt().coerceAtMost(numFrames / 2).coerceAtLeast(0)
            if (immediate) {
                unit.dataQueue.queueLoopImmediate(data, startFrame, numFrames, timeStamp, crossFadeFrames)
            } else {
                unit.dataQueue.queueLoop(data, startFrame, numFrames, timeStamp, crossFadeFrames, skipIfOthers)
            }
        } else {
            isPlayingLoop = false
            unit.dataQueue.queueImmediate(data, startFrame, numFrames, timeStamp)
        }
    }

    override fun execute(triggerMessage: Message) {}

    override fun teardown() {
        if (unit.isStartRequired) {
            unit.stop()
        }
        SynthKSyn.synth.remove(unit)
        super.teardown()
    }

    companion object {
        const val CROSS_FADE_SECONDS = 0.030
    }
}

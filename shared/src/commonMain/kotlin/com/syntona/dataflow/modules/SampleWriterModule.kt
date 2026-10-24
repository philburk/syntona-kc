package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.data.AudioSampleData
import com.softsynth.ksyn.unitgen.FixedRateMonoWriter
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.BooleanInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.SequentialDataInputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.IntegerInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Writes incoming audio into a connected sample buffer ([FloatSampleModule] or [RecorderModule])
 * using a [FixedRateMonoWriter].
 */
class SampleWriterModule : Module("sampleWriter") {
    val unit = FixedRateMonoWriter()

    var isLooping: Boolean
        get() = loopInput.isChecked
        set(value) {
            loopInput.isChecked = value
        }
    private var isWritingLoop = false

    val displayCursorSeconds: Double
        get() {
            val data = dataInput.currentData ?: return 0.0
            val sampleFrameRate = (data as? AudioSampleData)?.frameRate?.takeIf { it > 0.0 }
                ?: (data as? com.syntona.util.SegmentedFloatArray)?.frameRate?.takeIf { it > 0.0 }
                ?: SynthKSyn.synth.frameRate.toDouble()
            return if (sampleFrameRate > 0.0) unit.dataQueue.frameIndex / sampleFrameRate else 0.0
        }

    val isActive: Boolean
        get() = unit.dataQueue.hasMore()

    val dataInput: SequentialDataInputJack
    val startIn: CommandJack
    val stopIn: CommandJack
    val loopInput: BooleanInputJack
    val looping: BooleanInputJack get() = loopInput
    val input: AudioInputJack

    init {
        SynthKSyn.synth.add(unit)
        if (unit.isStartRequired) {
            unit.start()
        }

        dataInput = addSequentialDataInput("data")
        startIn = addCommandInput("start")
        stopIn = addCommandInput("stop")
        loopInput = addBooleanInput("looping")
        input = addAudioInput("in", unit.input, 0)
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        when (jack) {
            startIn -> triggerWrite(immediate = true)
            stopIn -> {
                isWritingLoop = false
                unit.dataQueue.clear(SynthKSyn.synth.createTimeStamp())
            }
        }
    }

    fun triggerWrite(immediate: Boolean = true) {
        val data = dataInput.currentData ?: return
        val totalFrames = data.numFrames
        if (totalFrames <= 0) return

        val shouldLoop = isLooping || loopInput.isChecked
        val timeStamp = SynthKSyn.synth.createTimeStamp()

        if (shouldLoop) {
            isWritingLoop = true
            if (immediate) {
                unit.dataQueue.queueLoopImmediate(data, 0, totalFrames, timeStamp)
            } else {
                unit.dataQueue.queueLoop(data, 0, totalFrames, timeStamp)
            }
        } else {
            isWritingLoop = false
            if (immediate) {
                unit.dataQueue.queueImmediate(data, 0, totalFrames, timeStamp)
            } else {
                unit.dataQueue.queue(data, 0, totalFrames, timeStamp)
            }
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
}

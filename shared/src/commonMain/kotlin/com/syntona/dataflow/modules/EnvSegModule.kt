package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.data.SegmentedEnvelope
import com.softsynth.ksyn.ports.QueueDataEvent
import com.softsynth.ksyn.ports.UnitDataQueueCallback
import com.softsynth.ksyn.unitgen.UnitGenerator
import com.softsynth.ksyn.unitgen.VariableRateMonoReader
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.BooleanInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * An integrated Segmented Envelope generator and player module.
 * Combines a KSyn [SegmentedEnvelope] with a [VariableRateMonoReader].
 *
 * Supports triggering via [onIn] / [offIn], looping control via [loopInput],
 * rate scaling via [speed], and level control via [amplitude].
 */
class EnvSegModule : Module("envSeg") {
    val unit = VariableRateMonoReader()

    var autoDisableVoice: com.syntona.dataflow.model.AutoDisableVoiceMode = com.syntona.dataflow.model.AutoDisableVoiceMode.AUTO

    fun setupAutoDisable(target: UnitGenerator) {
        unit.setupAutoDisable(target)
    }

    fun setAutoDisableEnabled(enabled: Boolean) {
        unit.setAutoDisableEnabled(enabled)
    }

    private var _envelope by mutableStateOf(
        SegmentedEnvelope(
            doubleArrayOf(
                0.02, 0.9, // duration,value pair 0, "attack"
                0.10, 0.5, // pair 1, "decay"
                0.50, 0.0  // pair 2, "release"
            )
        ).apply {
            sustainBegin = 2
            sustainEnd = 2
        }
    )
    var envelope: SegmentedEnvelope
        get() = _envelope
        set(value) {
            _envelope = value
            val poly = parentPatch as? PolyPatchModule
            poly?.syncEnvSegEnvelope(this, value)
        }

    var isLooping: Boolean
        get() = loopInput.isChecked
        set(value) {
            loopInput.isChecked = value
        }

    val onIn: CommandJack
    val offIn: CommandJack
    val loopInput: BooleanInputJack
    val looping: BooleanInputJack get() = loopInput
    val speed: AudioInputJack
    val amplitude: AudioInputJack
    val output: AudioOutputJack

    init {
        SynthKSyn.synth.add(unit)
        if (unit.isStartRequired) {
            unit.start()
        }

        onIn = addCommandInput("on")
        offIn = addCommandInput("off")
        loopInput = addBooleanInput("looping")

        speed = addAudioInput("speed", unit.rate, 0).apply {
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
                    unit.dataQueue.queueLoop(envelope)
                } else {
                    unit.dataQueue.queueOn(envelope, timeStamp)
                }
            }
            offIn -> {
                unit.dataQueue.queueOff(envelope, false, timeStamp)
            }
        }
    }

    override fun execute(triggerMessage: Message) {}

    override fun teardown() {
        unit.setAutoDisableEnabled(false)
        if (unit.isStartRequired) {
            unit.stop()
        }
        SynthKSyn.synth.remove(unit)
        super.teardown()
    }
}

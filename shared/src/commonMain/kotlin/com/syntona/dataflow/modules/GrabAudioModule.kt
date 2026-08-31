package com.syntona.dataflow.modules

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.unitgen.UnitGenerator
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.CommandJack
import com.syntona.dataflow.model.Message

class GrabAudioUnit : UnitGenerator() {
    val input = UnitInputPort("input")

    init {
        addPort(input)
    }

    override val isStartRequired: Boolean
        get() = true

    override fun generate() {
        input.getValues()
    }
}

class GrabAudioModule : com.syntona.dataflow.model.Module("grabAudio") {
    val unit = GrabAudioUnit()

    val kick: CommandJack
    val input: AudioInputJack
    val output = addOutput("out")

    init {
        // Add unit to Synth
        SynthKSyn.synth.add(unit)
        if (unit.isStartRequired) {
            unit.start()
        }

        // Add inputs in the desired order: kick first, then input
        kick = addCommandInput("kick")
        input = addAudioInput("in", unit.input, 0)
    }

    override fun execute(triggerMessage: Message) {
        if (kick.isKicked) {
            val value = if (unit.input.isConnected(0)) {
                unit.input.getValue(0)
            } else {
                unit.input.get(0)
            }
            output.send(Message.FloatValue(triggerMessage.time, value.toDouble()))
        }
    }

    override fun teardown() {
        if (unit.isStartRequired) {
            unit.stop()
        }
        SynthKSyn.synth.remove(unit)
        super.teardown()
    }
}

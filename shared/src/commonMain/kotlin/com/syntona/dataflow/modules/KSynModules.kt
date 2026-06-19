package com.syntona.dataflow.modules

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.unitgen.UnitGenerator
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A Syntona [Module] that wraps a KSyn [UnitGenerator].
 *
 * For each port on the unit:
 * - [UnitInputPort] becomes an [com.syntona.dataflow.model.AudioInputJack]
 * - [UnitOutputPort] becomes an [com.syntona.dataflow.model.AudioOutputJack]
 * Other port types are ignored.
 *
 * The underlying [unit] must still be added to a KSyn synthesizer and started separately.
 */
open class UnitGeneratorModule(name: String, val unit: UnitGenerator) : Module(name) {
    init {
        SynthKSyn.synth.add(unit)
        for (port in unit.getPorts()) {
            when (port) {
                is UnitInputPort -> addAudioInput(port.name, port)
                is UnitOutputPort -> addAudioOutput(port.name, port)
                else -> {}
            }
        }
        if (unit.isStartRequired) {
            unit.start()
        }
    }

    override fun execute(triggerMessage: Message) {
        // Audio is driven by the KSyn engine, nothing to do here.
    }
}

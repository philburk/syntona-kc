package com.syntona.dataflow.modules

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.ports.UnitDataQueuePort
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
 * - [UnitDataQueuePort] becomes an [com.syntona.dataflow.model.DataQueueInputJack]
 * Other port types are ignored.
 *
 * The underlying [unit] must still be added to a KSyn synthesizer and started separately.
 */
open class UnitGeneratorModule(name: String, val unit: UnitGenerator) : Module(name) {
    override val isAllowedInPolyPatch: Boolean
        get() = unit !is com.softsynth.ksyn.unitgen.LineIn && unit !is com.softsynth.ksyn.unitgen.LineOut

    init {
        SynthKSyn.synth.add(unit)
        for (port in unit.getPorts()) {
            val baseName = port.name.lowercase()
            when (port) {
                is UnitInputPort -> {
                    for (i in 0 until port.numParts) {
                        val jackName = if (port.numParts == 1) baseName else "$baseName$i"
                        addAudioInput(jackName, port, i)
                    }
                }
                is UnitOutputPort -> {
                    for (i in 0 until port.numParts) {
                        val jackName = if (port.numParts == 1) baseName else "$baseName$i"
                        addAudioOutput(jackName, port, i)
                    }
                }
                is UnitDataQueuePort -> {
                    addDataQueueInput(baseName, port)
                }
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

    override fun teardown() {
        if (unit.isStartRequired) {
            unit.stop()
        }
        SynthKSyn.synth.remove(unit)
        super.teardown()
    }
}

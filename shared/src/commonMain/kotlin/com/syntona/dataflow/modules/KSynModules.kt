package com.syntona.dataflow.modules

import com.syntona.dataflow.model.AutoDisableVoiceMode
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

    val isEnvelope: Boolean
        get() = unit is com.softsynth.ksyn.unitgen.UnitGate

    // This is set to Auto for non-envelopes so that no preference is saved in the JSON.
    var autoDisableVoice: AutoDisableVoiceMode = if (isEnvelope) AutoDisableVoiceMode.NO else AutoDisableVoiceMode.AUTO

    val onJack: com.syntona.dataflow.model.CommandJack?
    val offJack: com.syntona.dataflow.model.CommandJack?
    val triggerJack: com.syntona.dataflow.model.CommandJack?

    init {
        SynthKSyn.synth.add(unit)
        if (unit is com.softsynth.ksyn.unitgen.EnvelopeDAHDSR) {
            onJack = addCommandInput("on")
            offJack = addCommandInput("off")
            triggerJack = null
        } else if (unit is com.softsynth.ksyn.unitgen.EnvelopeAttackDecay) {
            triggerJack = addCommandInput("trigger")
            onJack = null
            offJack = null
        } else if (unit is com.softsynth.ksyn.unitgen.UnitGate) {
            onJack = addCommandInput("on")
            offJack = addCommandInput("off")
            triggerJack = null
        } else {
            onJack = null
            offJack = null
            triggerJack = null
        }
        for (port in unit.getPorts()) {
            val rawName = port.name
            val baseName = when {
                rawName.startsWith("input", ignoreCase = true) -> "in" + rawName.substring(5)
                rawName.startsWith("output", ignoreCase = true) -> "out" + rawName.substring(6)
                else -> rawName.replaceFirstChar { it.lowercase() }
            }
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
        if (onJack != null && onJack.isKicked) {
            (unit as? com.softsynth.ksyn.unitgen.UnitGate)?.input?.on()
        }
        if (offJack != null && offJack.isKicked) {
            (unit as? com.softsynth.ksyn.unitgen.UnitGate)?.input?.off()
        }
        if (triggerJack != null && triggerJack.isKicked) {
            (unit as? com.softsynth.ksyn.unitgen.UnitGate)?.input?.trigger()
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

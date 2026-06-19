package com.syntona.dataflow.model

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * An [InputJack] that wraps a KSyn [UnitInputPort].
 *
 * When a float message arrives from a regular Syntona output, it calls [UnitInputPort.set]
 * so the value flows into the KSyn audio graph as a parameter.
 *
 * When an [AudioOutputJack] connects to this jack, the KSyn ports are wired for audio-rate
 * signal flow instead (see [AudioOutputJack.connect]).
 */
class AudioInputJack(
    name: String,
    module: Module,
    val unitInputPort: UnitInputPort
) : InputJack(name, module) {

    override fun receive(msg: Message) {
        super.receive(msg)
        if (msg is Message.FloatValue) {
            unitInputPort.set(msg.value)
        }
    }
}

/**
 * An [OutputJack] that wraps a KSyn [UnitOutputPort].
 *
 * When connected to an [AudioInputJack], wires the underlying KSyn ports so audio signals
 * flow at audio rate. When connected to a regular [InputJack], the connection is tracked
 * but no audio-rate signal is sent (the KSyn output is not sampled for messages).
 */
class AudioOutputJack(
    name: String,
    module: Module,
    val unitOutputPort: UnitOutputPort
) : OutputJack(name, module) {

    override fun connect(inputJack: InputJack) {
        super.connect(inputJack)
        if (inputJack is AudioInputJack) {
            unitOutputPort.connect(inputJack.unitInputPort)
        }
    }

    override fun disconnect(inputJack: InputJack) {
        super.disconnect(inputJack)
        if (inputJack is AudioInputJack) {
            unitOutputPort.disconnect(inputJack.unitInputPort)
        }
    }
}

package com.syntona.dataflow.model

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort

/**
 * An [InputJack] that wraps one part of a KSyn [UnitInputPort].
 *
 * When a float message arrives from a regular Syntona output, it calls [UnitInputPort.set]
 * on the specific [partIndex] so the value flows into the KSyn audio graph as a parameter.
 *
 * When an [AudioOutputJack] connects to this jack, the KSyn ports are wired for audio-rate
 * signal flow instead (see [AudioOutputJack.connect]).
 */
class AudioInputJack(
    name: String,
    module: Module,
    val unitInputPort: UnitInputPort,
    val partIndex: Int = 0
) : InputJack(name, module) {

    override var min: Double
        get() = unitInputPort.minimum.toDouble()
        set(value) { unitInputPort.minimum = value.toFloat() }

    override var max: Double
        get() = unitInputPort.maximum.toDouble()
        set(value) { unitInputPort.maximum = value.toFloat() }

    override fun getValue(): Double = unitInputPort.get(partIndex).toDouble()

    override fun setValue(value: Double) {
        unitInputPort.set(partIndex, value.toFloat())
    }

    override fun receive(msg: Message) {
        super.receive(msg)
        if (msg is Message.FloatValue) {
            unitInputPort.set(partIndex, msg.value.toFloat())
        }
    }
}

/**
 * An [OutputJack] that wraps one part of a KSyn [UnitOutputPort].
 *
 * When connected to an [AudioInputJack], wires the specific parts of the underlying KSyn ports
 * so audio signals flow at audio rate. When connected to a regular [InputJack], the connection
 * is tracked but no audio-rate signal is sent.
 */
class AudioOutputJack(
    name: String,
    module: Module,
    val unitOutputPort: UnitOutputPort,
    val partIndex: Int = 0
) : OutputJack(name, module) {

    override fun getValue(): Double = unitOutputPort.value.toDouble()

    override fun connect(inputJack: InputJack) {
        super.connect(inputJack)
        if (inputJack is AudioInputJack) {
            unitOutputPort.connect(partIndex, inputJack.unitInputPort, inputJack.partIndex)
        }
    }

    override fun disconnect(inputJack: InputJack) {
        super.disconnect(inputJack)
        if (inputJack is AudioInputJack) {
            unitOutputPort.disconnect(partIndex, inputJack.unitInputPort, inputJack.partIndex)
        }
    }
}

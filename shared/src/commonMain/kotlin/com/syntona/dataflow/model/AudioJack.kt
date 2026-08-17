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
        set(value) {
            unitInputPort.minimum = value.toFloat()
            val polyPatch = module.parentPatch as? com.syntona.dataflow.modules.PolyPatchModule
            polyPatch?.syncInputJackMin(this, value)
        }

    override var max: Double
        get() = unitInputPort.maximum.toDouble()
        set(value) {
            unitInputPort.maximum = value.toFloat()
            val polyPatch = module.parentPatch as? com.syntona.dataflow.modules.PolyPatchModule
            polyPatch?.syncInputJackMax(this, value)
        }

    /** When true, the value set() on this jack is added to the audio values of connected ports
     * instead of being ignored while an audio-rate connection is present. */
    var isValueAdded: Boolean
        get() = unitInputPort.isValueAdded
        set(value) {
            unitInputPort.isValueAdded = value
            val polyPatch = module.parentPatch as? com.syntona.dataflow.modules.PolyPatchModule
            polyPatch?.syncInputJackValueAdded(this, value)
        }

    override fun getValue(): Double = unitInputPort.get(partIndex).toDouble()

    // setValue() is inherited from InputJack: it calls receive(), which both applies the
    // value to the KSyn port and syncs it to any polyPatch background voices below.

    override fun receive(msg: Message) {
        if (msg is Message.FloatValue) {
            unitInputPort.set(partIndex, msg.value.toFloat())
        }
        super.receive(msg)
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

package com.syntona.dataflow.modules

import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.Circuit
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.unitgen.UnitGate
import com.softsynth.ksyn.unitgen.UnitGenerator
import com.softsynth.ksyn.voices.PitchedVoice
import com.syntona.dataflow.model.Module

class DynamicPitchedVoice : Circuit(), PitchedVoice {
    val outputPassThrough = PassThrough()
    val moduleToUnitMap = mutableMapOf<Module, UnitGenerator>()

    var pitch: Double = 0.0
    var pitchBend: Double = 0.0

    init {
        add(outputPassThrough)
    }

    override fun getOutputPort(): UnitOutputPort {
        return outputPassThrough.output
    }

    private fun updatePitchPort(timeStamp: TimeStamp) {
        val pitchPort = getPitchPort()
        pitchPort?.set((pitch + pitchBend).toFloat(), timeStamp)
    }

    override fun noteOn(pitch: Double, amplitude: Double, timeStamp: TimeStamp) {
        this.pitch = pitch
        updatePitchPort(timeStamp)

        val amplitudePort = getPortByName("Amplitude") as? UnitInputPort
        amplitudePort?.set(amplitude.toFloat(), timeStamp)

        for (unit in getUnits()) {
            if (unit is UnitGate) {
                unit.input.on(timeStamp)
            }
        }
    }

    override fun setPitchBend(pitchBend: Double, timeStamp: TimeStamp) {
        this.pitchBend = pitchBend
        updatePitchPort(timeStamp)
    }

    override fun noteOff(timeStamp: TimeStamp) {
        for (unit in getUnits()) {
            if (unit is UnitGate) {
                unit.input.off(timeStamp)
            }
        }
    }

    override fun getPitchPort(): UnitInputPort? {
        return getPortByName("Pitch") as? UnitInputPort
    }

    override fun usePreset(presetIndex: Int) {
        // Preset handling is a no-op by default
    }

    fun teardown() {
        flattenOutputs()
    }
}

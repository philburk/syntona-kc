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

    /** Pitch of the sounding note in semitones, before any bend. */
    var notePitch = 0.0
        private set

    /** Current bend in semitones, added to [notePitch] whenever the pitch port is written. */
    var pitchBend = 0.0
        private set

    /**
     * The tag of the note this voice is playing, so a per-note message can find it.
     *
     * PolyphonicInstrument allocates the voice and keeps its allocator private, so the tag never
     * reaches [noteOn]. It is supplied instead by whoever owns the voices, read at the moment
     * the note starts: both this and the allocation run as queued commands on the audio thread,
     * in the order they were queued, so the value read here is the one that came with this note.
     */
    var tagSource: (() -> Int)? = null

    /** The tag this voice was started with, or -1 when it has never played. */
    var noteTag = -1
        private set

    init {
        add(outputPassThrough)
    }

    override fun getOutputPort(): UnitOutputPort {
        return outputPassThrough.output
    }

    override fun noteOn(pitch: Double, amplitude: Double, timeStamp: TimeStamp) {
        tagSource?.let { noteTag = it() }
        notePitch = pitch
        applyPitch(timeStamp)

        val amplitudePort = getPortByName("Amplitude") as? UnitInputPort
        amplitudePort?.set(amplitude.toFloat(), timeStamp)

        for (unit in getUnits()) {
            if (unit is UnitGate) {
                unit.input.on(timeStamp)
            }
        }
    }

    override fun noteOff(timeStamp: TimeStamp) {
        for (unit in getUnits()) {
            if (unit is UnitGate) {
                unit.input.off(timeStamp)
            }
        }
    }

    /**
     * Sets the bend and re-applies it to the note that is sounding.
     *
     * Bend is summed here in Kotlin rather than in the audio graph. The earlier attempt fed a
     * second signal into the pitch port and never reached the ear; adding the two numbers before
     * writing the port is one line to read and one place to look when it misbehaves.
     */
    fun setPitchBend(semitones: Double, timeStamp: TimeStamp) {
        pitchBend = semitones
        applyPitch(timeStamp)
    }

    /** Writes note plus bend to the pitch port. */
    private fun applyPitch(timeStamp: TimeStamp) {
        val pitchPort = getPortByName("Pitch") as? UnitInputPort
        pitchPort?.set((notePitch + pitchBend).toFloat(), timeStamp)
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

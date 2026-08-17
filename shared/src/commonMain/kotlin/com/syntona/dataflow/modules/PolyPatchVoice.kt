package com.syntona.dataflow.modules

import com.softsynth.ksyn.AudioSample
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.PassThrough
import com.softsynth.ksyn.voices.PitchedVoice
import com.syntona.core.audio.SynthKSyn
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * A hidden [PatchModule] representing one voice inside a [PolyPatchModule].
 *
 * It contains its own [com.syntona.dataflow.editor.GraphState] where per-voice modules and connections run.
 * Voice audio is routed through [outputPassThrough] to the parent [PolyPatchModule]'s master mixer.
 */
class PolyPatchVoice(
    val voiceIndex: Int,
    val parentPolyPatch: PolyPatchModule
) : PatchModule("voice$voiceIndex"), PitchedVoice {

    val templateToVoiceMap = mutableMapOf<Module, Module>()
    val voiceToTemplateMap = mutableMapOf<Module, Module>()

    val outputPassThrough = PassThrough()

    var currentPitch: Double = 60.0
    var currentAmplitude: Double = 1.0
    var pitchBend: Double = 0.0

    init {
        SynthKSyn.synth.add(outputPassThrough)
        if (outputPassThrough.isStartRequired) outputPassThrough.start()
    }

    override fun getOutputPort(): UnitOutputPort {
        return outputPassThrough.output
    }

    override fun getUnitGenerator(): com.softsynth.ksyn.unitgen.UnitGenerator {
        return outputPassThrough
    }

    override fun getPitchPort(): UnitInputPort? {
        val pitchMod = findVoiceSpecialInput("pitch")
        return (pitchMod as? AudioInputModule)?.passThrough?.input
    }

    override fun noteOn(pitch: Double, amplitude: Double, timeStamp: TimeStamp) {
        this.currentPitch = pitch
        this.currentAmplitude = amplitude
        val now = ScoreClock.now()

        // 1. Dispatch "on" event
        val onMod = findVoiceSpecialInput("on")
        when (onMod) {
            is InputModule -> onMod.receive(Message.FloatValue(now, 1.0))
        }

        // 2. Dispatch pitch
        val totalPitch = pitch + pitchBend
        val pitchMod = findVoiceSpecialInput("pitch")
        when (pitchMod) {
            is InputModule -> pitchMod.receive(Message.FloatValue(now, totalPitch))
            is AudioInputModule -> pitchMod.passThrough.input.setValueInternal(0, totalPitch.toFloat())
        }

        // 3. Dispatch amplitude
        val ampMod = findVoiceSpecialInput("amplitude")
        when (ampMod) {
            is InputModule -> ampMod.receive(Message.FloatValue(now, amplitude))
            is AudioInputModule -> ampMod.passThrough.input.setValueInternal(0, amplitude.toFloat())
        }

        // 4. Dispatch gate (high) if legacy/custom gate module exists
        val gateMod = findVoiceSpecialInput("gate")
        when (gateMod) {
            is AudioInputModule -> gateMod.passThrough.input.setValueInternal(0, 1.0f)
            is InputModule -> gateMod.receive(Message.FloatValue(now, 1.0))
        }
    }

    override fun noteOff(timeStamp: TimeStamp) {
        val now = ScoreClock.now()

        // 1. Dispatch "off" event
        val offMod = findVoiceSpecialInput("off")
        when (offMod) {
            is InputModule -> offMod.receive(Message.FloatValue(now, 0.0))
        }

        // 2. Dispatch gate (low) if legacy/custom gate module exists
        val gateMod = findVoiceSpecialInput("gate")
        when (gateMod) {
            is AudioInputModule -> gateMod.passThrough.input.setValueInternal(0, 0.0f)
            is InputModule -> gateMod.receive(Message.FloatValue(now, 0.0))
        }
    }

    override fun setPitchBend(pitchBend: Double, timeStamp: TimeStamp) {
        this.pitchBend = pitchBend
        val now = ScoreClock.now()
        val totalPitch = currentPitch + pitchBend
        val pitchMod = findVoiceSpecialInput("pitch")
        when (pitchMod) {
            is InputModule -> pitchMod.receive(Message.FloatValue(now, totalPitch))
            is AudioInputModule -> pitchMod.passThrough.input.setValueInternal(0, totalPitch.toFloat())
        }
    }

    override fun setPort(portName: String, value: AudioSample, timeStamp: TimeStamp) {
        val now = ScoreClock.now()
        val mod = findVoiceSpecialInput(portName)
        when (mod) {
            is InputModule -> mod.receive(Message.FloatValue(now, value.toDouble()))
            is AudioInputModule -> mod.passThrough.input.setValueInternal(0, value)
        }
    }

    fun setPressure(value: Double, timeStamp: TimeStamp) {
        setPort("pressure", value.toFloat(), timeStamp)
    }

    fun setProgram(value: Double, timeStamp: TimeStamp) {
        setPort("program", value.toFloat(), timeStamp)
    }

    fun findVoiceSpecialInput(name: String): Module? {
        val lower = name.lowercase()
        return graphState.modules.find { it.name.lowercase() == lower }
    }

    override fun usePreset(presetIndex: Int) {
        // Presets not used in custom patch voices
    }

    override fun teardown() {
        if (outputPassThrough.isStartRequired) {
            outputPassThrough.stop()
        }
        SynthKSyn.synth.remove(outputPassThrough)
        super.teardown()
    }
}

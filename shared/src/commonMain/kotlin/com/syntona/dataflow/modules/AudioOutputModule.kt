package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.unitgen.PassThrough
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Places an audio output port on the parent [PatchModule].
 *
 * Inside the patch, [input] collects audio into [passThrough].input. The parent gains an
 * [AudioOutputJack] that wraps [passThrough].output, so external modules receive the audio.
 */
class AudioOutputModule : Module("output~") {
    val passThrough = PassThrough()

    val input: AudioInputJack

    private var _name by mutableStateOf("output~")
    override var name: String
        get() = _name
        set(value) {
            _name = value
            patchJack?.name = value
            (patch as? PolyPatchModule)?.onAudioOutputNameChanged(this, value)
            (patch as? TrackPatchModule)?.track?.syncOutputNamesForChild(this)
        }

    internal var patch: PatchModule? = null
    internal var patchJack: AudioOutputJack? = null

    init {
        SynthKSyn.synth.add(passThrough)
        if (passThrough.isStartRequired) passThrough.start()
        input = addAudioInput("in", passThrough.input, 0)
    }

    override fun execute(triggerMessage: Message) {}

    override fun teardown() {
        if (passThrough.isStartRequired) {
            passThrough.stop()
        }
        SynthKSyn.synth.remove(passThrough)
        super.teardown()
    }
}

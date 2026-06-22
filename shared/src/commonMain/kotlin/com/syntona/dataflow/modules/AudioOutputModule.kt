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
class AudioOutputModule : Module("aOutput") {
    val passThrough = PassThrough()

    val input: AudioInputJack

    var displayName by mutableStateOf("aOutput")

    internal var patch: PatchModule? = null
    internal var patchJack: AudioOutputJack? = null

    init {
        SynthKSyn.synth.add(passThrough)
        if (passThrough.isStartRequired) passThrough.start()
        input = addAudioInput("input", passThrough.input, 0)
    }

    override fun execute(triggerMessage: Message) {}
}

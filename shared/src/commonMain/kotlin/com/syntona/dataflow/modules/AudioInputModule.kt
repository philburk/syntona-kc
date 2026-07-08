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
 * Places an audio input port on the parent [PatchModule].
 *
 * The parent gains an [AudioInputJack] that wraps [passThrough].input, so external audio
 * sources connect into the PassThrough at the KSyn level. Inside the patch, [output]
 * exposes [passThrough].output so the signal can be routed to other audio modules.
 */
class AudioInputModule : Module("aInput") {
    val passThrough = PassThrough()

    val output: AudioOutputJack

    private var _name by mutableStateOf("aInput")
    override var name: String
        get() = _name
        set(value) {
            _name = value
            patchJack?.name = value
            (patch as? PolyPatchModule)?.onAudioInputNameChanged(this, value)
        }

    internal var patch: PatchModule? = null
    internal var patchJack: AudioInputJack? = null

    init {
        SynthKSyn.synth.add(passThrough)
        if (passThrough.isStartRequired) passThrough.start()
        output = addAudioOutput("output", passThrough.output, 0)
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

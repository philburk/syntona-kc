package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.unitgen.TwoInDualOut
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.AudioOutputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

/**
 * Places stereo audio output ports on the parent [PatchModule].
 *
 * Inside the patch, [in0] and [in1] collect audio into [twoInDualOut].inputA and inputB.
 * The parent gains two [AudioOutputJack]s that wrap [twoInDualOut].output parts 0 and 1,
 * so external modules receive stereo audio.
 */
class AudioOutput2Module : Module("output2~") {
    val twoInDualOut = TwoInDualOut()

    val in0: AudioInputJack
    val in1: AudioInputJack

    private var _name by mutableStateOf("output2~")
    override var name: String
        get() = _name
        set(value) {
            _name = value
            val base = if (value == "output2~" || value == "output") "out" else value
            patchJack0?.name = "${base}0"
            patchJack1?.name = "${base}1"
            (patch as? PolyPatchModule)?.onAudioOutput2NameChanged(this, value)
            (patch as? TrackPatchModule)?.track?.syncOutputNamesForChild(this)
        }

    internal var patch: PatchModule? = null
    internal var patchJack0: AudioOutputJack? = null
    internal var patchJack1: AudioOutputJack? = null

    init {
        SynthKSyn.synth.add(twoInDualOut)
        if (twoInDualOut.isStartRequired) twoInDualOut.start()
        in0 = addAudioInput("in0", twoInDualOut.inputA, 0)
        in1 = addAudioInput("in1", twoInDualOut.inputB, 0)
    }

    override fun execute(triggerMessage: Message) {}

    override fun teardown() {
        if (twoInDualOut.isStartRequired) {
            twoInDualOut.stop()
        }
        SynthKSyn.synth.remove(twoInDualOut)
        super.teardown()
    }
}

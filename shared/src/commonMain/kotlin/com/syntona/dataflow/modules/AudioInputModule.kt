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
import com.syntona.dataflow.model.isDefaultExponentialName

/**
 * Places an audio input port on the parent [PatchModule].
 *
 * The parent gains an [AudioInputJack] that wraps [passThrough].input, so external audio
 * sources connect into the PassThrough at the KSyn level. Inside the patch, [output]
 * exposes [passThrough].output so the signal can be routed to other audio modules.
 */
class AudioInputModule : Module("input~") {
    val passThrough = PassThrough()

    val output: AudioOutputJack

    private var _name by mutableStateOf("input~")
    override var name: String
        get() = _name
        set(value) {
            _name = value
            hasAutoName = false
            patchJack?.name = value
            if (!isExponentialExplicitlySet) {
                val exp = isDefaultExponentialName(value)
                defaultIsExponential = exp
                isExponentialExplicitlySet = false
            }
            (patch as? PolyPatchModule)?.onAudioInputNameChanged(this, value)
        }

    // True while the name is the one the parent patch made up (aIn1, aIn2...), so the first
    // connection may replace it with the name of the connected jack. Any rename clears it.
    internal var hasAutoName = false

    internal var patch: PatchModule? = null
    var patchJack: AudioInputJack? = null
        set(value) {
            field = value
            if (value != null) {
                value.min = defaultMin
                value.max = defaultMax
                if (hasValue) {
                    value.setValue(defaultValue)
                }
                value.isKickable = defaultIsKickable
                value.isValueAdded = defaultAdd
                value.isInteger = defaultIsInteger
                if (isExponentialExplicitlySet) {
                    value.isExponential = defaultIsExponential
                } else {
                    value.isExponential = isDefaultExponentialName(name)
                    defaultIsExponential = value.isExponential
                    isExponentialExplicitlySet = false
                }
            }
        }

    var defaultMin: Double = 0.0
        set(value) {
            field = value
            patchJack?.min = value
        }
    var defaultMax: Double = 1.0
        set(value) {
            field = value
            patchJack?.max = value
        }
    var defaultValue: Double = 0.0
        set(value) {
            field = value
            hasValue = true
            patchJack?.setValue(value)
        }
    var hasValue: Boolean = false
    var defaultIsKickable: Boolean = false
        set(value) {
            field = value
            patchJack?.isKickable = value
        }
    var defaultAdd: Boolean = false
        set(value) {
            field = value
            patchJack?.isValueAdded = value
        }
    var defaultIsInteger: Boolean = false
        set(value) {
            field = value
            patchJack?.isInteger = value
        }
    private var isExponentialExplicitlySet = false
    var defaultIsExponential: Boolean = isDefaultExponentialName(name)
        set(value) {
            field = value
            isExponentialExplicitlySet = true
            patchJack?.isExponential = value
        }

    init {
        SynthKSyn.synth.add(passThrough)
        if (passThrough.isStartRequired) passThrough.start()
        output = addAudioOutput("out", passThrough.output, 0)
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

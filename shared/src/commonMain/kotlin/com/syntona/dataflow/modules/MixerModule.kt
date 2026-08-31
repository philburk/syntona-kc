package com.syntona.dataflow.modules

import com.softsynth.ksyn.unitgen.MixerStereoRamped
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Jack
import com.syntona.dataflow.model.Message
import androidx.compose.runtime.mutableStateOf
import kotlin.math.pow
import kotlin.math.ln

open class MixerModule(name: String, val numInputs: Int) : UnitGeneratorModule(name, MixerStereoRamped(numInputs)) {

    val volumeStates = Array(numInputs) { mutableStateOf(1.0f) }
    val panStates = Array(numInputs) { mutableStateOf(0.0f) }

    fun getGainJack(index: Int): AudioInputJack? {
        return inputs.find { it.name == "gain$index" } as? AudioInputJack
    }

    fun getPanJack(index: Int): AudioInputJack? {
        return inputs.find { it.name == "pan$index" } as? AudioInputJack
    }

    fun getInputJack(index: Int): AudioInputJack? {
        return inputs.find { it.name == "in$index" } as? AudioInputJack
    }

    fun getOutputJack(index: Int): com.syntona.dataflow.model.AudioOutputJack? {
        return outputs.find { it.name == "out$index" || it.name == "output$index" } as? com.syntona.dataflow.model.AudioOutputJack
    }

    fun getVolumeKnobValue(index: Int): Float {
        return volumeStates[index].value
    }

    fun getPanKnobValue(index: Int): Float {
        return panStates[index].value
    }

    override fun onMessageReceived(jack: InputJack, msg: Message) {
        super.onMessageReceived(jack, msg)
        if (msg is Message.FloatValue) {
            val name = jack.name
            if (name.startsWith("gain")) {
                val index = name.substring(4).toIntOrNull()
                if (index != null && index in 0 until numInputs) {
                    val gainVal = msg.value.coerceIn(0.0, 1.0)
                    volumeStates[index].value = (ln(gainVal * 49.0 + 1.0) / ln(50.0)).toFloat()
                }
            } else if (name.startsWith("pan")) {
                val index = name.substring(3).toIntOrNull()
                if (index != null && index in 0 until numInputs) {
                    panStates[index].value = msg.value.toFloat().coerceIn(-1.0f, 1.0f)
                }
            }
        }
    }
}

class Mixer8x2Module : MixerModule("mixer8x2", 8)
class Mixer4x2Module : MixerModule("mixer4x2", 4)

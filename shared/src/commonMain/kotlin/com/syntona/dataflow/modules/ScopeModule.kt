package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.unitgen.ScopeProbe
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class ScopeModule : Module("scope") {
    val scope = ScopeProbe(numChannels = 2)
    var width by mutableStateOf(300f)
    var height by mutableStateOf(200f)

    init {
        SynthKSyn.synth.add(scope)
        addAudioInput("in0", scope.input, 0)
        addAudioInput("in1", scope.input, 1)
        scope.start()
    }

    override fun execute(triggerMessage: Message) {}

    override fun teardown() {
        scope.stop()
        SynthKSyn.synth.remove(scope)
        super.teardown()
    }
}

package com.syntona.dataflow.modules

import com.softsynth.ksyn.unitgen.ScopeProbe
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module

class ScopeModule : Module("scope") {
    val scope = ScopeProbe(numChannels = 2)

    init {
        SynthKSyn.synth.add(scope)
        addAudioInput("in0", scope.input, 0)
        addAudioInput("in1", scope.input, 1)
        scope.start()
    }

    override fun execute(triggerMessage: Message) {}
}

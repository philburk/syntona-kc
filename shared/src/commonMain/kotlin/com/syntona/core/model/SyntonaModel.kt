package com.syntona.core.model

import com.syntona.core.audio.SynthKSyn

class SyntonaModel {
    // Synth engine MUST be initialized once per instance
    private val synthKSyn = SynthKSyn()

    fun start() {
        synthKSyn.start()
    }
}

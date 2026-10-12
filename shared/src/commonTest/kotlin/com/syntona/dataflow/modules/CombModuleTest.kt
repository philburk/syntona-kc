package com.syntona.dataflow.modules

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.unitgen.FilterComb
import com.syntona.dataflow.registry.ModuleRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CombModuleTest {

    private fun createComb(): UnitGeneratorModule =
        assertIs<UnitGeneratorModule>(assertNotNull(ModuleRegistry.findCreator("comb")).invoke())

    @Test
    fun combIsAFilterWithFrequencyAndDecayJacks() {
        assertEquals("ksyn.filters.comb", ModuleRegistry.getBundlePath("comb"))
        val comb = createComb()
        assertIs<FilterComb>(comb.unit)
        assertEquals(listOf("in", "frequency", "decay"), comb.inputs.map { it.name })
        assertEquals(listOf("out"), comb.outputs.map { it.name })
    }

    @Test
    fun anImpulseRingsAtTheTunedPeriod() = kotlinx.coroutines.runBlocking {
        // Tuned so the loop is exactly 100 frames long at whatever rate the synth runs, so a
        // single impulse comes back around every 100 frames.
        val engine = com.syntona.core.audio.SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine
        if (!engine.isRunning) engine.start()
        val filter = createComb().unit as FilterComb
        try {
            filter.frequency.set(filter.frameRate / 100.0)
            filter.decay.set(2.0)
            // Port values set while the engine runs are applied on the audio thread.
            engine.generateNextBuffer()
        } finally {
            engine.stop()
        }

        val output = FloatArray(400)
        for (block in 0 until output.size / Synthesizer.FRAMES_PER_BLOCK) {
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                filter.input.getValues()[i] = if (block == 0 && i == 0) 1.0f else 0.0f
            }
            filter.generate()
            filter.output.getValues().copyInto(output, block * Synthesizer.FRAMES_PER_BLOCK, 0, Synthesizer.FRAMES_PER_BLOCK)
        }

        for (echo in listOf(100, 200, 300)) {
            val peakIndex = (echo - 3..echo + 3).maxBy { abs(output[it]) }
            assertTrue(abs(peakIndex - echo) <= 1, "echo near frame $echo was at $peakIndex")
            assertTrue(abs(output[peakIndex]) > 0.5f, "echo at $echo is too quiet: ${output[peakIndex]}")
        }
    }
}

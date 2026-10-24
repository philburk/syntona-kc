package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.unitgen.DelayGrains
import com.softsynth.ksyn.unitgen.GrainFarm
import com.softsynth.ksyn.unitgen.SampleGrains
import com.softsynth.ksyn.unitgen.SampleScrubber
import com.softsynth.ksyn.unitgen.SineGrains
import com.syntona.core.model.ScoreClock
import com.syntona.dataflow.model.DataQueueInputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Message.DataQueueMessage
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.export.JSynSourceExporter
import com.syntona.export.KSynSourceExporter
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests sineGrains, sampleGrains, delayGrains and sampleScrubber. */
class GrainModulesTest {

    private fun createSineGrains(): UnitGeneratorModule =
        assertIs<UnitGeneratorModule>(assertNotNull(ModuleRegistry.findCreator("sineGrains")).invoke())

    private fun createSampleGrains(): SampleGrainsModule =
        assertIs<SampleGrainsModule>(assertNotNull(ModuleRegistry.findCreator("sampleGrains")).invoke())

    private fun createDelayGrains(): UnitGeneratorModule =
        assertIs<UnitGeneratorModule>(assertNotNull(ModuleRegistry.findCreator("delayGrains")).invoke())

    private fun createSampleScrubber(): SampleScrubberModule =
        assertIs<SampleScrubberModule>(assertNotNull(ModuleRegistry.findCreator("sampleScrubber")).invoke())

    /** Peak output over about a second. A GrainFarm with no grains allocated stays at zero. */
    private fun peakOutput(farm: GrainFarm): Float {
        var peak = 0.0f
        repeat(6000) {
            farm.generate()
            for (value in farm.output.getValues()) peak = maxOf(peak, abs(value))
        }
        return peak
    }

    private fun queueData(sample: FloatSample): DataQueueMessage =
        DataQueueMessage(ScoreClock.now(), sample, Message.DataQueueOpcode.QUEUE)

    private fun makeSineSample(numFrames: Int = 4096): FloatSample {
        val data = FloatArray(numFrames) { i ->
            sin(2.0 * kotlin.math.PI * i / 64.0).toFloat()
        }
        return FloatSample(data, 1)
    }

    @Test
    fun sineGrainsIsASourceWithItsGrainControls() {
        assertEquals("ksyn.sources.sineGrains", ModuleRegistry.getBundlePath("sineGrains"))
        assertNotNull(ModuleRegistry.getDescription("sineGrains"))
        val module = createSineGrains()
        assertIs<SineGrains>(module.unit)
        assertEquals(
            listOf("density", "rate", "rateRange", "duration", "durationRange", "amplitude", "amplitudeRange"),
            module.inputs.map { it.name }
        )
        assertEquals(listOf("out"), module.outputs.map { it.name })
    }

    @Test
    fun grainFarmIsStillLoadableAsSineGrains() {
        for (legacyName in listOf("grainFarm", "ksyn.sources.grainFarm")) {
            val module = assertIs<UnitGeneratorModule>(assertNotNull(ModuleRegistry.findCreator(legacyName)).invoke())
            assertIs<SineGrains>(module.unit)
            assertEquals("sineGrains", module.type)
        }
        assertEquals("sineGrains", ModuleRegistry.resolveLegacyTypeName("grainFarm"))
    }

    @Test
    fun oldGrainSampleAndGrainDelayNamesAreNotSupported() {
        assertNull(ModuleRegistry.findCreator("grainSample"))
        assertNull(ModuleRegistry.findCreator("grainDelay"))
    }

    @Test
    fun aNewSineGrainsMakesSound() {
        val farm = createSineGrains().unit as GrainFarm
        assertTrue(peakOutput(farm) > 0.01f, "sineGrains is silent, so its grains were not allocated")
    }

    @Test
    fun sineGrainsInAPolyPatchVoiceMakesSound() {
        // Voices are rebuilt from ModuleRegistry.ksynFactories, so the factory must allocate too.
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val template = createSineGrains()
        polyPatch.graphState.addModule(template, Offset.Zero)

        for (voice in polyPatch.voices) {
            val voiceFarm = assertIs<UnitGeneratorModule>(voice.templateToVoiceMap[template]).unit as GrainFarm
            assertTrue(peakOutput(voiceFarm) > 0.01f, "sineGrains in a voice is silent")
        }
    }

    @Test
    fun exportedCodeAllocatesTheGrains() {
        val patch = PatchModule("cloud")
        val farm = createSineGrains()
        val output = AudioOutputModule().apply { name = "audioOut" }
        patch.graphState.addModule(farm, Offset.Zero)
        patch.graphState.addModule(output, Offset.Zero)
        patch.graphState.connect(farm.outputs.first { it.name == "out" }, output.input)

        val grains = ModuleRegistry.GRAIN_FARM_GRAINS
        val ksyn = KSynSourceExporter.export(patch, "Cloud")
        assertTrue(ksyn.contains("sineGrains.allocate($grains)"), ksyn)
        val jsyn = JSynSourceExporter.export(patch, "Cloud")
        assertTrue(jsyn.contains("sineGrains.allocate($grains);"), jsyn)
    }

    @Test
    fun sampleGrainsIsInSamplingWithDataJackAndMakesSoundWithSample() {
        assertEquals("ksyn.sampling.sampleGrains", ModuleRegistry.getBundlePath("sampleGrains"))
        assertNotNull(ModuleRegistry.getDescription("sampleGrains"))
        val module = createSampleGrains()
        assertIs<SampleGrains>(module.unit)
        assertIs<DataQueueInputJack>(module.inputs.first())
        assertEquals(
            listOf(
                "data", "density", "rate", "rateRange", "duration", "durationRange",
                "amplitude", "amplitudeRange", "position", "positionRange"
            ),
            module.inputs.map { it.name }
        )
        assertEquals(listOf("out"), module.outputs.map { it.name })

        val sample = makeSineSample()
        module.dataInput.receive(queueData(sample))
        assertTrue(peakOutput(module.grains) > 0.01f, "sampleGrains should produce sound after receiving sample")
    }

    @Test
    fun sampleGrainsInPolyPatchSyncsSampleAndMakesSound() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val template = createSampleGrains()
        polyPatch.graphState.addModule(template, Offset.Zero)

        val sample = makeSineSample()
        template.dataInput.receive(queueData(sample))

        for (voice in polyPatch.voices) {
            val voiceMod = assertIs<SampleGrainsModule>(voice.templateToVoiceMap[template])
            assertIs<DataQueueInputJack>(voiceMod.inputs.first())
            assertEquals(sample, voiceMod.sample)
            assertTrue(peakOutput(voiceMod.grains) > 0.01f, "sampleGrains in a voice is silent")
        }
    }

    @Test
    fun delayGrainsIsInEffectsWithInFirstAndMakesSound() {
        assertEquals("ksyn.effects.delayGrains", ModuleRegistry.getBundlePath("delayGrains"))
        assertNotNull(ModuleRegistry.getDescription("delayGrains"))
        val module = createDelayGrains()
        val delayGrains = assertIs<DelayGrains>(module.unit)
        assertEquals(
            listOf(
                "in", "density", "rate", "rateRange", "duration", "durationRange",
                "amplitude", "amplitudeRange", "delay", "delayRange"
            ),
            module.inputs.map { it.name }
        )
        assertEquals(listOf("out"), module.outputs.map { it.name })

        // Feed a constant non-zero signal into the delay line and verify grains produce output
        delayGrains.input.getValues().fill(0.5f)
        assertTrue(peakOutput(delayGrains) > 0.01f, "delayGrains should produce sound when fed input")
    }

    @Test
    fun sampleScrubberAndWaveFolderAreRegisteredAndSampleScrubberScrubsAudio() {
        assertEquals("ksyn.effects.waveFolder", ModuleRegistry.getBundlePath("waveFolder"))
        assertNotNull(ModuleRegistry.findCreator("ksyn.filters.waveFolder"))
        assertEquals("ksyn.sampling.sampleScrubber", ModuleRegistry.getBundlePath("sampleScrubber"))
        assertNotNull(ModuleRegistry.getDescription("sampleScrubber"))

        val module = createSampleScrubber()
        val scrubber = assertIs<SampleScrubber>(module.unit)
        assertIs<DataQueueInputJack>(module.inputs.first())
        assertEquals(
            listOf("data", "in", "position", "time", "amplitude"),
            module.inputs.map { it.name }
        )
        assertEquals(listOf("out"), module.outputs.map { it.name })

        // Load a lookup table [-1.0, 0.5, 1.0] and verify input 0.0 maps to 0.5
        val table = FloatSample(floatArrayOf(-1.0f, 0.5f, 1.0f), 1)
        module.dataInput.receive(queueData(table))
        scrubber.input.getValues().fill(0.0f)
        scrubber.amplitude.getValues().fill(1.0f)
        scrubber.generate()
        assertEquals(0.5f, scrubber.output.getValues()[0], 1e-4f)
    }

    @Test
    fun sampleScrubberInPolyPatchSyncsSample() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val template = createSampleScrubber()
        polyPatch.graphState.addModule(template, Offset.Zero)

        val table = FloatSample(floatArrayOf(-1.0f, 0.5f, 1.0f), 1)
        template.dataInput.receive(queueData(table))

        for (voice in polyPatch.voices) {
            val voiceMod = assertIs<SampleScrubberModule>(voice.templateToVoiceMap[template])
            assertEquals(table, voiceMod.sample)
        }
    }

    @Test
    fun jSynExportFlagsKSynOnlyUnitsAndAllocatesGrains() {
        val patch = PatchModule("effects")
        val sampleScrubber = createSampleScrubber()
        val delayGrains = createDelayGrains()
        val sampleGrains = createSampleGrains()
        val output = AudioOutputModule().apply { name = "audioOut" }
        patch.graphState.addModule(sampleScrubber, Offset.Zero)
        patch.graphState.addModule(delayGrains, Offset.Zero)
        patch.graphState.addModule(sampleGrains, Offset.Zero)
        patch.graphState.addModule(output, Offset.Zero)
        patch.graphState.connect(delayGrains.outputs.first { it.name == "out" }, output.input)

        val ksynOnly = JSynSourceExporter.findKSynOnlyUnits(patch)
        for (unit in listOf("SampleScrubber", "DelayGrains", "SampleGrains")) {
            assertTrue(unit in ksynOnly, "Expected $unit in $ksynOnly")
        }

        val grains = ModuleRegistry.GRAIN_FARM_GRAINS
        val ksyn = KSynSourceExporter.export(patch, "Effects")
        assertTrue(ksyn.contains("delayGrains.allocate($grains)"), ksyn)
        assertTrue(ksyn.contains("sampleGrains.allocate($grains)"), ksyn)
    }
}

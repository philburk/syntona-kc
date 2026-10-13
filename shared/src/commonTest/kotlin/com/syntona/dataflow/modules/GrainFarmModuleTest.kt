package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.unitgen.GrainFarm
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.export.JSynSourceExporter
import com.syntona.export.KSynSourceExporter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GrainFarmModuleTest {

    private fun createGrainFarm(): UnitGeneratorModule =
        assertIs<UnitGeneratorModule>(assertNotNull(ModuleRegistry.findCreator("grainFarm")).invoke())

    /** Peak output over about a second. A GrainFarm with no grains allocated stays at zero. */
    private fun peakOutput(farm: GrainFarm): Float {
        var peak = 0.0f
        repeat(6000) {
            farm.generate()
            for (value in farm.output.getValues()) peak = maxOf(peak, abs(value))
        }
        return peak
    }

    @Test
    fun grainFarmIsASourceWithItsGrainControls() {
        assertEquals("ksyn.sources.grainFarm", ModuleRegistry.getBundlePath("grainFarm"))
        val module = createGrainFarm()
        assertIs<GrainFarm>(module.unit)
        assertEquals(
            listOf("density", "rate", "rateRange", "duration", "durationRange", "amplitude", "amplitudeRange"),
            module.inputs.map { it.name }
        )
        assertEquals(listOf("out"), module.outputs.map { it.name })
    }

    @Test
    fun aNewGrainFarmMakesSound() {
        val farm = createGrainFarm().unit as GrainFarm
        assertTrue(peakOutput(farm) > 0.01f, "grainFarm is silent, so its grains were not allocated")
    }

    @Test
    fun aGrainFarmInAPolyPatchVoiceMakesSound() {
        // Voices are rebuilt from ModuleRegistry.ksynFactories, so the factory must allocate too.
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val template = createGrainFarm()
        polyPatch.graphState.addModule(template, Offset.Zero)

        for (voice in polyPatch.voices) {
            val voiceFarm = assertIs<UnitGeneratorModule>(voice.templateToVoiceMap[template]).unit as GrainFarm
            assertTrue(peakOutput(voiceFarm) > 0.01f, "grainFarm in a voice is silent")
        }
    }

    @Test
    fun exportedCodeAllocatesTheGrains() {
        val patch = PatchModule("cloud")
        val farm = createGrainFarm()
        val output = AudioOutputModule().apply { name = "audioOut" }
        patch.graphState.addModule(farm, Offset.Zero)
        patch.graphState.addModule(output, Offset.Zero)
        patch.graphState.connect(farm.outputs.first { it.name == "out" }, output.input)

        val grains = ModuleRegistry.GRAIN_FARM_GRAINS
        val ksyn = KSynSourceExporter.export(patch, "Cloud")
        assertTrue(ksyn.contains("grainFarm.allocate($grains)"), ksyn)
        val jsyn = JSynSourceExporter.export(patch, "Cloud")
        assertTrue(jsyn.contains("grainFarm.allocate($grains);"), jsyn)
    }
}

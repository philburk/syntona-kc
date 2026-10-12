package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AutoDisableVoiceMode
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.model.autoDisableUnit
import com.syntona.dataflow.model.autoDisableVoiceMode
import com.syntona.dataflow.model.supportsAutoDisableVoice
import com.syntona.dataflow.registry.ModuleRegistry
import com.syntona.export.KSynSourceExporter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The auto-disable choice is offered, saved and applied for any module whose KSyn unit implements
 * SupportsAutoDisable, rather than for a fixed list of module classes.
 */
class AutoDisableVoiceTest {

    private val engine get() = SynthKSyn.synth as com.softsynth.ksyn.engine.SynthesisEngine

    @BeforeTest
    fun setUp() {
        if (!engine.isRunning) engine.start()
    }

    @AfterTest
    fun tearDown() {
        if (engine.isRunning) engine.stop()
    }

    private fun create(type: String): Module = assertNotNull(ModuleRegistry.findCreator(type), type).invoke()

    private val supporting = listOf("envDAHDSR", "envAD", "envSeg", "stringRes", "samplePlayer", "sampleReader")
    private val notSupporting = listOf("sineOsc", "lowPass", "sample", "sampleWriter", "clock")

    @Test
    fun theChoiceIsOfferedExactlyForModulesWhoseUnitSupportsIt() {
        for (type in supporting) assertTrue(create(type).supportsAutoDisableVoice, "$type should offer it")
        for (type in notSupporting) assertFalse(create(type).supportsAutoDisableVoice, "$type should not offer it")
    }

    @Test
    fun everySupportingModuleDefaultsToNo() {
        // So a voice is only switched off where the user asked, and existing patches are unchanged.
        for (type in supporting) {
            assertEquals(AutoDisableVoiceMode.NO, create(type).autoDisableVoiceMode, type)
        }
    }

    @Test
    fun theChoiceSurvivesSaveAndLoad() {
        for (type in supporting) {
            val state = GraphState()
            val module = create(type)
            module.autoDisableVoiceMode = AutoDisableVoiceMode.YES
            state.addModule(module, Offset.Zero)
            val json = GraphSerializer.serialize(state, state.modules.toList())

            val loaded = GraphState()
            GraphSerializer.deserialize(loaded, json)
            assertEquals(AutoDisableVoiceMode.YES, loaded.modules.single().autoDisableVoiceMode, type)
        }
    }

    @Test
    fun aSamplePlayerChosenInAPolyPatchSwitchesItsVoiceOff() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(2)
        val player = create("samplePlayer")
        val env = create("envDAHDSR")
        polyPatch.graphState.addModule(player, Offset.Zero)
        polyPatch.graphState.addModule(env, Offset.Zero)

        polyPatch.syncAutoDisableVoice(player, AutoDisableVoiceMode.YES)

        for (voice in polyPatch.voices) {
            val voicePlayer = voice.templateToVoiceMap[player]!!
            val voiceEnv = voice.templateToVoiceMap[env]!!
            assertEquals(AutoDisableVoiceMode.YES, voicePlayer.autoDisableVoiceMode, "voice copy keeps the choice")
            assertTrue(voicePlayer.autoDisableUnit!!.isAutoDisableEnabled, "the sample player should switch the voice off")
            assertFalse(voiceEnv.autoDisableUnit!!.isAutoDisableEnabled, "the envelope was not chosen")
        }

        // Turning it back off lets the voice run continuously.
        polyPatch.syncAutoDisableVoice(player, AutoDisableVoiceMode.NO)
        for (voice in polyPatch.voices) {
            assertFalse(voice.templateToVoiceMap[player]!!.autoDisableUnit!!.isAutoDisableEnabled)
            assertTrue(voice.voiceCircuit.isEnabled)
        }
    }

    @Test
    fun aStringResonatorChosenForExportSwitchesTheVoiceOff() {
        val polyPatch = PolyPatchModule("Pluck")
        val string = create("stringRes")
        polyPatch.graphState.addModule(string, Offset.Zero)
        val output = polyPatch.graphState.modules.filterIsInstance<AudioOutputModule>().first()
        polyPatch.graphState.connect(string.outputs.first { it.name == "out" }, output.inputs.first { it.name == "in" })
        string.autoDisableVoiceMode = AutoDisableVoiceMode.YES

        val code = KSynSourceExporter.export(polyPatch, "Pluck")
        assertTrue(code.contains("stringRes.setupAutoDisable(this)"), code)
    }
}

package com.syntona.dataflow.modules

import androidx.compose.ui.geometry.Offset
import com.softsynth.ksyn.engine.SynthesisEngine
import com.softsynth.ksyn.unitgen.ChebyshevOscillator
import com.softsynth.ksyn.unitgen.FunctionEvaluator
import com.syntona.core.audio.SynthKSyn
import com.syntona.dataflow.editor.GraphSerializer
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.model.AudioInputJack
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ChebyshevOscModuleTest {

    private val engine get() = SynthKSyn.synth as SynthesisEngine

    @BeforeTest
    fun setUp() {
        if (!engine.isRunning) engine.start()
    }

    @AfterTest
    fun tearDown() {
        if (engine.isRunning) engine.stop()
    }

    /** The table the waveshaper is actually reading from on the audio thread. */
    private fun ChebyshevOscModule.activeFunction() =
        (unit as ChebyshevOscillator).getUnits().filterIsInstance<FunctionEvaluator>().single().function.get()

    private fun roundTrip(module: ChebyshevOscModule): ChebyshevOscModule {
        val state = GraphState()
        state.addModule(module, Offset.Zero)
        val json = GraphSerializer.serialize(state, state.modules.toList())
        val loaded = GraphState()
        GraphSerializer.deserialize(loaded, json)
        return assertIs<ChebyshevOscModule>(loaded.modules.single())
    }

    @Test
    fun jacksAreFrequencyDriveAmplitudeAndOut() {
        val module = ChebyshevOscModule()
        assertEquals(listOf("frequency", "drive", "amplitude"), module.inputs.map { it.name })
        assertEquals(listOf("out"), module.outputs.map { it.name })

        val drive = assertIs<AudioInputJack>(module.inputs.first { it.name == "drive" })
        assertEquals(0.1, drive.min, 1e-6)
        assertEquals(0.8, drive.getValue(), 1e-6)
        assertEquals(1.0, drive.max, 1e-6)
    }

    @Test
    fun orderDefaultsToTheChebyshevSongVoice() {
        assertEquals(ChebyshevOscillator.DEFAULT_ORDER, ChebyshevOscModule().order)
    }

    @Test
    fun orderSurvivesSaveAndLoad() {
        val module = ChebyshevOscModule()
        module.order = 5
        assertEquals(5, roundTrip(module).order)
    }

    @Test
    fun anInvalidOrderInAFileSnapsToAnOddOne() {
        fun load(order: Int): Int {
            val json = """{"modules":[{"id":0,"type":"ksyn.sources.chebyshevOsc","name":"c",""" +
                """"x":0,"y":0,"order":$order}],"connections":[]}"""
            val state = GraphState()
            GraphSerializer.deserialize(state, json)
            return assertIs<ChebyshevOscModule>(state.modules.single()).order
        }
        assertEquals(3, load(4))
        assertEquals(1, load(0))
        assertEquals(15, load(40))
        assertEquals(9, load(9))
    }

    @Test
    fun anOrderChangeReachesTheAudioThread() = kotlinx.coroutines.runBlocking {
        val a = ChebyshevOscModule()
        val b = ChebyshevOscModule()
        val stillEleven = ChebyshevOscModule()
        a.order = 3
        b.order = 3
        // With the engine running, the new table is swapped in at the start of the next block.
        engine.generateNextBuffer()

        // Oscillators of the same order read one shared table; different orders do not.
        assertSame(a.activeFunction(), b.activeFunction())
        assertNotSame(a.activeFunction(), stillEleven.activeFunction())
    }

    @Test
    fun polyPatchVoicesFollowTheTemplateOrder() {
        val polyPatch = PolyPatchModule()
        polyPatch.initializeVoices(3)

        val template = ChebyshevOscModule()
        template.order = 7
        polyPatch.graphState.addModule(template, Offset.Zero)

        fun voiceOrders() = polyPatch.voices.map {
            assertIs<ChebyshevOscModule>(it.templateToVoiceMap[template]).order
        }
        assertEquals(listOf(7, 7, 7), voiceOrders(), "voices are cloned with the template's order")

        template.order = 13
        assertEquals(listOf(13, 13, 13), voiceOrders(), "changing the menu updates every voice")
    }
}

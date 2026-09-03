package com.syntona.dataflow.registry

import com.syntona.dataflow.modules.MidiInModule
import com.syntona.dataflow.modules.MidiOutModule
import com.syntona.dataflow.modules.PackBendModule
import com.syntona.dataflow.modules.PackNoteModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleRegistryTest {

    @Test
    fun testMidiBundlePathResolution() {
        assertEquals("music.midi.midiIn", ModuleRegistry.getBundlePath("midiIn"))
        assertEquals("music.midi.midiOut", ModuleRegistry.getBundlePath("midiOut"))
        assertEquals("music.midi.packBend", ModuleRegistry.getBundlePath("packBend"))
        assertEquals("music.midi.packNote", ModuleRegistry.getBundlePath("packNote"))
        assertEquals("music.midi.matchControl", ModuleRegistry.getBundlePath("matchControl"))
        assertEquals("music.midi.matchNoteOn", ModuleRegistry.getBundlePath("matchNoteOn"))
    }

    @Test
    fun testFindCreatorWithNewMidiBundlePath() {
        val midiInCreator = ModuleRegistry.findCreator("music.midi.midiIn")
        assertNotNull(midiInCreator)
        assertTrue(midiInCreator() is MidiInModule)

        val midiOutCreator = ModuleRegistry.findCreator("music.midi.midiOut")
        assertNotNull(midiOutCreator)
        assertTrue(midiOutCreator() is MidiOutModule)
    }

    @Test
    fun testLegacyMusicVoicesBackwardsCompatibility() {
        val legacyMidiIn = ModuleRegistry.findCreator("music.voices.midiIn")
        assertNotNull(legacyMidiIn, "Legacy music.voices.midiIn should resolve to MidiInModule")
        assertTrue(legacyMidiIn() is MidiInModule)

        val legacyPackBend = ModuleRegistry.findCreator("music.voices.packBend")
        assertNotNull(legacyPackBend, "Legacy music.voices.packBend should resolve to PackBendModule")
        assertTrue(legacyPackBend() is PackBendModule)

        val legacyPackNote = ModuleRegistry.findCreator("music.voices.packNote")
        assertNotNull(legacyPackNote, "Legacy music.voices.packNote should resolve to PackNoteModule")
        assertTrue(legacyPackNote() is PackNoteModule)
    }

    @Test
    fun testHelpResourcePaths() {
        assertEquals("examples/matchControl.json", ModuleRegistry.getHelpResourcePath("matchControl"))
        assertEquals("examples/matchControl.json", ModuleRegistry.getHelpResourcePath("music.midi.matchControl"))

        assertEquals("examples/pianoRoll.json", ModuleRegistry.getHelpResourcePath("pianoRoll"))
        assertEquals("examples/pianoRoll.json", ModuleRegistry.getHelpResourcePath("music.compose.pianoRoll"))

        assertEquals("examples/recSeq.json", ModuleRegistry.getHelpResourcePath("recursiveSeq"))
        assertEquals("examples/recSeq.json", ModuleRegistry.getHelpResourcePath("music.compose.recursiveSeq"))

        assertEquals("tutorials/03_core/05_key_match.json", ModuleRegistry.getHelpResourcePath("keyMatch"))
        assertEquals("tutorials/03_core/05_key_match.json", ModuleRegistry.getHelpResourcePath("core.input.keyMatch"))

        assertEquals("tutorials/04_sequencer/03_pitch_to_freq.json", ModuleRegistry.getHelpResourcePath("pitchToHz"))
        assertEquals("tutorials/04_sequencer/03_pitch_to_freq.json", ModuleRegistry.getHelpResourcePath("core.math.pitchToHz"))

        assertEquals("tutorials/06_midi/04_midiOut.json", ModuleRegistry.getHelpResourcePath("midiOut"))
        assertEquals("tutorials/06_midi/04_midiOut.json", ModuleRegistry.getHelpResourcePath("music.midi.midiOut"))

        assertEquals("tutorials/06_midi/01_midi_input.json", ModuleRegistry.getHelpResourcePath("midiIn"))
        assertEquals("tutorials/06_midi/01_midi_input.json", ModuleRegistry.getHelpResourcePath("music.midi.midiIn"))

        assertEquals("tutorials/07_audio_input/05_sampleReader.json", ModuleRegistry.getHelpResourcePath("sampleReader"))
        assertEquals("tutorials/07_audio_input/05_sampleReader.json", ModuleRegistry.getHelpResourcePath("ksyn.data.sampleReader"))

        assertEquals(null, ModuleRegistry.getHelpResourcePath("keyboard13"))
    }

    @Test
    fun testResolveLegacyModuleName() {
        assertEquals("onLoad", ModuleRegistry.resolveLegacyModuleName("loadKick"))
        assertEquals("pitchToHz~", ModuleRegistry.resolveLegacyModuleName("ap2f"))
        assertEquals("*~", ModuleRegistry.resolveLegacyModuleName("a*"))
        assertEquals("+~", ModuleRegistry.resolveLegacyModuleName("a+"))
        assertEquals("-~", ModuleRegistry.resolveLegacyModuleName("a-"))
        assertEquals("*+~", ModuleRegistry.resolveLegacyModuleName("a*+"))
        assertEquals("/~", ModuleRegistry.resolveLegacyModuleName("a/"))
        assertEquals(">~", ModuleRegistry.resolveLegacyModuleName("a>"))
        assertEquals("min~", ModuleRegistry.resolveLegacyModuleName("aMin"))
        assertEquals("max~", ModuleRegistry.resolveLegacyModuleName("aMax"))
        assertEquals("range~", ModuleRegistry.resolveLegacyModuleName("aRanger"))
        assertEquals("power2~", ModuleRegistry.resolveLegacyModuleName("aPower2"))
        assertEquals("delay~", ModuleRegistry.resolveLegacyModuleName("delay"))
        assertEquals("select~", ModuleRegistry.resolveLegacyModuleName("select"))
        assertEquals("clockDiv", ModuleRegistry.resolveLegacyModuleName("ckDiv"))
        assertEquals("oneShot", ModuleRegistry.resolveLegacyModuleName("arm"))
        assertEquals("pass", ModuleRegistry.resolveLegacyModuleName("gate"))
        assertEquals("select4", ModuleRegistry.resolveLegacyModuleName("choose4"))
        assertEquals("select8", ModuleRegistry.resolveLegacyModuleName("choose8"))
        assertEquals("select16", ModuleRegistry.resolveLegacyModuleName("choose16"))
        assertEquals("recursiveSeq", ModuleRegistry.resolveLegacyModuleName("recSeq"))
        assertEquals("pitchToHz", ModuleRegistry.resolveLegacyModuleName("p2f"))
        assertEquals("envDAHDSR", ModuleRegistry.resolveLegacyModuleName("DAHDSR"))
        assertEquals("latchZeroX", ModuleRegistry.resolveLegacyModuleName("latchZC"))
        assertEquals("schmittTrigger", ModuleRegistry.resolveLegacyModuleName("schmidtTrigger"))
        assertEquals("input~", ModuleRegistry.resolveLegacyModuleName("aInput"))
        assertEquals("output~", ModuleRegistry.resolveLegacyModuleName("aOutput"))
        assertEquals("showMany", ModuleRegistry.resolveLegacyModuleName("showVoice"))
        assertEquals("number", ModuleRegistry.resolveLegacyModuleName("double"))

        // Unmodified custom names
        assertEquals("customKick", ModuleRegistry.resolveLegacyModuleName("customKick"))
        assertEquals("in1", ModuleRegistry.resolveLegacyModuleName("in1"))
        assertEquals("aIn1", ModuleRegistry.resolveLegacyModuleName("aIn1"))
        assertEquals("aOut1", ModuleRegistry.resolveLegacyModuleName("aOut1"))
    }

    @Test
    fun testControlAndSamplingBundles() {
        assertEquals("ksyn.control.envSeg", ModuleRegistry.getBundlePath("envSeg"))
        assertEquals("ksyn.sampling.samplePlayer", ModuleRegistry.getBundlePath("samplePlayer"))
        assertEquals("ksyn.sampling.sample", ModuleRegistry.getBundlePath("sample"))
        assertEquals("ksyn.sampling.sampleReader", ModuleRegistry.getBundlePath("sampleReader"))
        assertEquals("ksyn.sampling.sampleWriter", ModuleRegistry.getBundlePath("sampleWriter"))
        assertEquals("ksyn.sampling.recorder", ModuleRegistry.getBundlePath("recorder"))

        assertNotNull(ModuleRegistry.findCreator("ksyn.data.envSeg"))
        assertNotNull(ModuleRegistry.findCreator("ksyn.data.segEnv"))
        assertNotNull(ModuleRegistry.findCreator("ksyn.data.samplePlayer"))
    }
}


package com.syntona.dataflow.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpRegistryTest {

    @Test
    fun testHelpFilePathResolution() {
        HelpRegistry.setHelpFiles(
            setOf(
                "help/music/input/keyboard13.sntn",
                "help/music/compose/scale.sntn",
                "help/music/input/pianoRoll.sntn"
            )
        )

        assertTrue(HelpRegistry.hasHelpFile("keyboard13"))
        assertEquals("help/music/input/keyboard13.sntn", HelpRegistry.getHelpFilePath("keyboard13"))

        assertTrue(HelpRegistry.hasHelpFile("scale"))
        assertEquals("help/music/compose/scale.sntn", HelpRegistry.getHelpFilePath("scale"))

        assertTrue(HelpRegistry.hasHelpFile("pianoRoll"))
        assertEquals("help/music/input/pianoRoll.sntn", HelpRegistry.getHelpFilePath("pianoRoll"))

        assertFalse(HelpRegistry.hasHelpFile("nonExistentModule"))
        assertEquals(null, HelpRegistry.getHelpFilePath("nonExistentModule"))
    }

    @Test
    fun testCustomTutorialAndExampleHelpResourcePaths() {
        HelpRegistry.setHelpFiles(
            setOf(
                "examples/matchControl.json",
                "examples/pianoRoll.json",
                "examples/recSeq.json",
                "tutorials/03_core/05_key_match.json",
                "tutorials/04_sequencer/03_pitch_to_freq.json",
                "tutorials/06_midi/04_midiOut.json",
                "tutorials/06_midi/01_midi_input.json",
                "tutorials/07_audio_input/05_sampleReader.json"
            )
        )

        assertEquals("examples/matchControl.json", HelpRegistry.getHelpFilePath("matchControl"))
        assertEquals("examples/matchControl.json", HelpRegistry.getHelpFilePath("music.midi.matchControl"))

        assertEquals("examples/pianoRoll.json", HelpRegistry.getHelpFilePath("pianoRoll"))
        assertEquals("examples/pianoRoll.json", HelpRegistry.getHelpFilePath("music.compose.pianoRoll"))

        assertEquals("examples/recSeq.json", HelpRegistry.getHelpFilePath("recursiveSeq"))
        assertEquals("examples/recSeq.json", HelpRegistry.getHelpFilePath("music.compose.recursiveSeq"))

        assertEquals("tutorials/03_core/05_key_match.json", HelpRegistry.getHelpFilePath("keyMatch"))
        assertEquals("tutorials/03_core/05_key_match.json", HelpRegistry.getHelpFilePath("core.input.keyMatch"))

        assertEquals("tutorials/04_sequencer/03_pitch_to_freq.json", HelpRegistry.getHelpFilePath("pitchToHz"))
        assertEquals("tutorials/04_sequencer/03_pitch_to_freq.json", HelpRegistry.getHelpFilePath("core.math.pitchToHz"))

        assertEquals("tutorials/06_midi/04_midiOut.json", HelpRegistry.getHelpFilePath("midiOut"))
        assertEquals("tutorials/06_midi/04_midiOut.json", HelpRegistry.getHelpFilePath("music.midi.midiOut"))

        assertEquals("tutorials/06_midi/01_midi_input.json", HelpRegistry.getHelpFilePath("midiIn"))
        assertEquals("tutorials/06_midi/01_midi_input.json", HelpRegistry.getHelpFilePath("music.midi.midiIn"))

        assertEquals("tutorials/07_audio_input/05_sampleReader.json", HelpRegistry.getHelpFilePath("sampleReader"))
        assertEquals("tutorials/07_audio_input/05_sampleReader.json", HelpRegistry.getHelpFilePath("ksyn.data.sampleReader"))
    }
}


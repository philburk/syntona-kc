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
}

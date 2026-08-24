package com.syntona.storage

import com.syntona.dataflow.editor.DocumentState
import com.syntona.dataflow.modules.PatchModule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentPatchesStoreTest {

    private lateinit var backend: InMemorySettingsBackend
    private lateinit var settings: SettingsStore

    @BeforeTest
    fun setUp() {
        backend = InMemorySettingsBackend()
        settings = SettingsStore("testSettings", backend)
        RecentPatchesStore.clear(settings)
    }

    @Test
    fun testAddRecentPatchLimitsTo8() {
        for (i in 1..12) {
            RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_$i.sntn", settings)
        }

        assertEquals(8, RecentPatchesStore.recentPatches.size)
        assertEquals("/Users/phil/Documents/patch_12.sntn", RecentPatchesStore.recentPatches[0])
        assertEquals("/Users/phil/Documents/patch_11.sntn", RecentPatchesStore.recentPatches[1])
        assertEquals("/Users/phil/Documents/patch_5.sntn", RecentPatchesStore.recentPatches[7])
    }

    @Test
    fun testAddRecentPatchDeduplicatesAndMovesToTop() {
        RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_a.sntn", settings)
        RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_b.sntn", settings)
        RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_c.sntn", settings)

        assertEquals(
            listOf(
                "/Users/phil/Documents/patch_c.sntn",
                "/Users/phil/Documents/patch_b.sntn",
                "/Users/phil/Documents/patch_a.sntn"
            ),
            RecentPatchesStore.recentPatches.toList()
        )

        // Add patch_a again: should move to front
        RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_a.sntn", settings)
        assertEquals(
            listOf(
                "/Users/phil/Documents/patch_a.sntn",
                "/Users/phil/Documents/patch_c.sntn",
                "/Users/phil/Documents/patch_b.sntn"
            ),
            RecentPatchesStore.recentPatches.toList()
        )
    }

    @Test
    fun testIgnoresResourcePatches() {
        RecentPatchesStore.addRecentPatch("examples/pianoRoll.json", settings)
        RecentPatchesStore.addRecentPatch("tutorials/01_tour/01_sawtooth_modulation.json", settings)
        RecentPatchesStore.addRecentPatch("help/music/input/keyboard13.json", settings)
        RecentPatchesStore.addRecentPatch("/app/composeResources/files/examples/basic.json", settings)
        RecentPatchesStore.addRecentPatch("instruments/sawBasic.json", settings)
        RecentPatchesStore.addRecentPatch("sounds/chuff.json", settings)

        assertTrue(RecentPatchesStore.recentPatches.isEmpty(), "Resource paths must not be recorded")
    }

    @Test
    fun testIgnoresBlankAndSharedPiece() {
        RecentPatchesStore.addRecentPatch("", settings)
        RecentPatchesStore.addRecentPatch("   ", settings)
        RecentPatchesStore.addRecentPatch("shared_piece.sntn", settings)

        assertTrue(RecentPatchesStore.recentPatches.isEmpty(), "Blank or shared piece paths must not be recorded")
    }

    @Test
    fun testRemoveRecentPatch() {
        RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_1.sntn", settings)
        RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_2.sntn", settings)

        RecentPatchesStore.removeRecentPatch("/Users/phil/Documents/patch_1.sntn", settings)
        assertEquals(listOf("/Users/phil/Documents/patch_2.sntn"), RecentPatchesStore.recentPatches.toList())
    }

    @Test
    fun testClear() {
        RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_1.sntn", settings)
        RecentPatchesStore.addRecentPatch("/Users/phil/Documents/patch_2.sntn", settings)

        RecentPatchesStore.clear(settings)
        assertTrue(RecentPatchesStore.recentPatches.isEmpty())
        assertFalse(settings.contains(RecentPatchesStore.KEY_RECENT_PATCHES))
    }

    @Test
    fun testRoundTripPersistence() {
        RecentPatchesStore.addRecentPatch("/path/one.sntn", settings)
        RecentPatchesStore.addRecentPatch("/path/two.sntn", settings)

        // Reload from settings
        val newStore = SettingsStore("testSettings", backend)
        RecentPatchesStore.reload(newStore)

        assertEquals(listOf("/path/two.sntn", "/path/one.sntn"), RecentPatchesStore.recentPatches.toList())
    }

    @Test
    fun testDocumentStateMarkSavedAddsRecentPatch() {
        RecentPatchesStore.clear()
        val doc = DocumentState(PatchModule("root"))

        doc.markSaved("/Users/phil/Documents/user_patch.sntn")
        assertTrue(RecentPatchesStore.recentPatches.contains("/Users/phil/Documents/user_patch.sntn"))

        val resourceDoc = DocumentState(PatchModule("root"))
        resourceDoc.markSaved("examples/pianoRoll.json")
        assertFalse(RecentPatchesStore.recentPatches.contains("examples/pianoRoll.json"))

        RecentPatchesStore.clear()
    }
}

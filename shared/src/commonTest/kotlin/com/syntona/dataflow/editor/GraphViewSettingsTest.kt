package com.syntona.dataflow.editor

import com.syntona.storage.InMemorySettingsBackend
import com.syntona.storage.SettingsStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphViewSettingsTest {

    @AfterTest
    fun resetSettings() {
        GraphViewSettings.resetForTest(false)
    }

    @Test
    fun testKeyMatchesSettingsStore() {
        assertEquals(SettingsStore.KEY_WIRES_ON_TOP, GraphViewSettings.KEY_WIRES_ON_TOP)
    }

    @Test
    fun testToggleWiresOnTop() {
        val backend = InMemorySettingsBackend()
        val store = SettingsStore(backend = backend)

        GraphViewSettings.resetForTest(false)
        assertFalse(GraphViewSettings.wiresOnTop)

        GraphViewSettings.toggleWiresOnTop(store)
        assertTrue(GraphViewSettings.wiresOnTop)
        assertTrue(store.getBoolean(SettingsStore.KEY_WIRES_ON_TOP))

        GraphViewSettings.toggleWiresOnTop(store)
        assertFalse(GraphViewSettings.wiresOnTop)
        assertFalse(store.getBoolean(SettingsStore.KEY_WIRES_ON_TOP))
    }

    @Test
    fun testSetWiresOnTop() {
        val backend = InMemorySettingsBackend()
        val store = SettingsStore(backend = backend)

        GraphViewSettings.setWiresOnTop(true, store)
        assertTrue(GraphViewSettings.wiresOnTop)
        assertTrue(store.getBoolean(SettingsStore.KEY_WIRES_ON_TOP))

        GraphViewSettings.setWiresOnTop(false, store)
        assertFalse(GraphViewSettings.wiresOnTop)
        assertFalse(store.getBoolean(SettingsStore.KEY_WIRES_ON_TOP))
    }
}

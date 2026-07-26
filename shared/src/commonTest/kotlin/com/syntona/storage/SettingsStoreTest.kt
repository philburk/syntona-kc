package com.syntona.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsStoreTest {

    private fun store(backend: SettingsBackend = InMemorySettingsBackend()) =
        SettingsStore("test", backend)

    @Test
    fun testMissingKeysReturnDefaults() {
        val settings = store()
        assertEquals("", settings.getString("nope"))
        assertEquals("fallback", settings.getString("nope", "fallback"))
        assertEquals(7, settings.getInt("nope", 7))
        assertEquals(7L, settings.getLong("nope", 7L))
        assertEquals(1.5, settings.getDouble("nope", 1.5))
        assertEquals(1.5f, settings.getFloat("nope", 1.5f))
        assertTrue(settings.getBoolean("nope", true))
        assertFalse(settings.contains("nope"))
    }

    @Test
    fun testRoundTripsEveryType() {
        val backend = InMemorySettingsBackend()
        store(backend).edit {
            putString("dir", "/Users/phil/Music")
            putInt("count", -42)
            putLong("frames", 9_000_000_000L)
            putDouble("gain", 0.375)
            putFloat("pan", -0.25f)
            putBoolean("looping", true)
        }

        // A fresh store over the same backend must see everything that was written.
        val reloaded = store(backend)
        assertEquals("/Users/phil/Music", reloaded.getString("dir"))
        assertEquals(-42, reloaded.getInt("count"))
        assertEquals(9_000_000_000L, reloaded.getLong("frames"))
        assertEquals(0.375, reloaded.getDouble("gain"))
        assertEquals(-0.25f, reloaded.getFloat("pan"))
        assertTrue(reloaded.getBoolean("looping"))
    }

    @Test
    fun testPersistsWithoutAnExplicitSave() {
        val backend = InMemorySettingsBackend()
        store(backend).putString(SettingsStore.KEY_LAST_DIRECTORY, "/tmp/patches")
        assertEquals("/tmp/patches", store(backend).getString(SettingsStore.KEY_LAST_DIRECTORY))
    }

    @Test
    fun testEditWritesOnceAtTheEnd() {
        var writeCount = 0
        val counting = object : SettingsBackend {
            private val inner = InMemorySettingsBackend()
            override fun read(name: String) = inner.read(name)
            override fun write(name: String, text: String) {
                writeCount++
                inner.write(name, text)
            }
            override fun delete(name: String) = inner.delete(name)
        }

        val settings = store(counting)
        settings.edit {
            putString("a", "1")
            putString("b", "2")
            putString("c", "3")
        }
        assertEquals(1, writeCount, "batched changes should write once")
        assertEquals("2", settings.getString("b"))

        settings.putString("d", "4")
        assertEquals(2, writeCount, "an unbatched change should write immediately")
    }

    @Test
    fun testWritingTheSameValueDoesNotRewrite() {
        var writeCount = 0
        val counting = object : SettingsBackend {
            private val inner = InMemorySettingsBackend()
            override fun read(name: String) = inner.read(name)
            override fun write(name: String, text: String) {
                writeCount++
                inner.write(name, text)
            }
            override fun delete(name: String) = inner.delete(name)
        }
        val settings = store(counting)
        settings.putString("k", "v")
        assertEquals(1, writeCount)
        settings.putString("k", "v")
        assertEquals(1, writeCount, "an unchanged value should not cause a write")
        settings.putString("k", "other")
        assertEquals(2, writeCount)
    }

    @Test
    fun testRemoveAndClear() {
        val backend = InMemorySettingsBackend()
        val settings = store(backend)
        settings.putString("a", "1")
        settings.putString("b", "2")

        settings.remove("a")
        assertFalse(settings.contains("a"))
        assertTrue(settings.contains("b"))
        assertEquals(setOf("b"), settings.keys())

        settings.clear()
        assertEquals(emptySet(), settings.keys())
        assertNull(backend.read("test"), "clear should remove the stored document")
    }

    @Test
    fun testReloadDiscardsInMemoryChanges() {
        val backend = InMemorySettingsBackend()
        val settings = store(backend)
        settings.putString("keep", "yes")

        // Simulate the document changing underneath, then pick the change up.
        backend.write("test", encodeSettings(mapOf("keep" to "no", "extra" to "added")))
        settings.reload()
        assertEquals("no", settings.getString("keep"))
        assertEquals("added", settings.getString("extra"))
    }

    @Test
    fun testMalformedValuesFallBackToDefaults() {
        val backend = InMemorySettingsBackend()
        backend.write("test", encodeSettings(mapOf("count" to "not-a-number", "flag" to "maybe")))
        val settings = store(backend)
        // A hand-edited or outdated file should not break the app.
        assertEquals(5, settings.getInt("count", 5))
        assertTrue(settings.getBoolean("flag", true))
        assertFalse(settings.getBoolean("flag", false))
    }

    @Test
    fun testKeysKeepInsertionOrder() {
        val settings = store()
        settings.edit {
            putString("zebra", "1")
            putString("apple", "2")
            putString("mango", "3")
        }
        assertEquals(listOf("zebra", "apple", "mango"), settings.keys().toList())
    }

    @Test
    fun testUnreadableBackendLeavesAnEmptyUsableStore() {
        val failing = object : SettingsBackend {
            override fun read(name: String): String? = null
            override fun write(name: String, text: String) {}
            override fun delete(name: String) {}
        }
        val settings = store(failing)
        assertEquals("default", settings.getString("anything", "default"))
        settings.putString("anything", "value")
        assertEquals("value", settings.getString("anything"), "should still work in memory")
    }
}

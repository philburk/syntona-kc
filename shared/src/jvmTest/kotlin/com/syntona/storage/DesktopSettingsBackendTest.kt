package com.syntona.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real file-backed implementation against a temporary directory, so the developer's
 * own settings are never touched.
 */
class DesktopSettingsBackendTest {

    private val directory: File = Files.createTempDirectory("syntona-settings-test").toFile()
    private val backend = DesktopSettingsBackend(directory)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun testReadingSomethingNeverWrittenReturnsNull() {
        assertNull(backend.read("settings"))
    }

    @Test
    fun testWriteThenReadRoundTrips() {
        backend.write("settings", "# Syntona settings\na=1\n")
        assertEquals("# Syntona settings\na=1\n", backend.read("settings"))
    }

    @Test
    fun testWriteCreatesTheDirectoryIfMissing() {
        val nested = File(directory, "deeper/still")
        val nestedBackend = DesktopSettingsBackend(nested)
        nestedBackend.write("settings", "a=1\n")
        assertTrue(nested.isDirectory, "the backend should create its own directory")
        assertEquals("a=1\n", nestedBackend.read("settings"))
    }

    @Test
    fun testOverwritingReplacesTheWholeDocument() {
        backend.write("settings", "a=1\nb=2\nc=3\n")
        backend.write("settings", "a=9\n")
        assertEquals("a=9\n", backend.read("settings"), "stale content must not survive")
    }

    @Test
    fun testWriteLeavesNoTemporaryFileBehind() {
        backend.write("settings", "a=1\n")
        val leftovers = directory.listFiles { f -> f.name.endsWith(".tmp") }
        assertEquals(0, leftovers?.size ?: 0, "the temp file should have been renamed away")
    }

    @Test
    fun testSeparateNamesAreIndependent() {
        backend.write("settings", "a=1\n")
        backend.write("recent", "b=2\n")
        assertEquals("a=1\n", backend.read("settings"))
        assertEquals("b=2\n", backend.read("recent"))
    }

    @Test
    fun testDelete() {
        backend.write("settings", "a=1\n")
        backend.delete("settings")
        assertNull(backend.read("settings"))
        assertFalse(File(directory, "settings.properties").exists())
        // Deleting something absent is not an error.
        backend.delete("settings")
    }

    @Test
    fun testStoreOverTheFileBackendSurvivesRestart() {
        SettingsStore("settings", backend).edit {
            putString(SettingsStore.KEY_LAST_DIRECTORY, "/Users/phil/Music/Patches")
            putInt("windowWidth", 1024)
        }

        // A brand new store, as if the app had been restarted.
        val reopened = SettingsStore("settings", DesktopSettingsBackend(directory))
        assertEquals("/Users/phil/Music/Patches", reopened.getString(SettingsStore.KEY_LAST_DIRECTORY))
        assertEquals(1024, reopened.getInt("windowWidth"))
    }

    @Test
    fun testDefaultDirectoryIsUnderTheUsersHomeAndNamedForSyntona() {
        val defaultDirectory = DesktopSettingsBackend.defaultSettingsDirectory()
        val path = defaultDirectory.absolutePath
        assertTrue(path.contains("Syntona") || path.contains("syntona"), "unexpected path: $path")
        assertTrue(path.endsWith("settings"), "unexpected path: $path")
        // It must be an absolute location, not relative to the working directory.
        assertTrue(defaultDirectory.isAbsolute, "unexpected path: $path")
    }
}

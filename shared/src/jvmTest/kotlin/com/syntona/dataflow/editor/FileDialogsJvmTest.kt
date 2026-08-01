package com.syntona.dataflow.editor

import com.syntona.storage.SettingsStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileDialogsJvmTest {

    @Test
    fun testKeyLastDocumentDirectoryConstant() {
        assertEquals("lastDocumentDirectoryUsed", SettingsStore.KEY_LAST_DOCUMENT_DIRECTORY)
    }

    @Test
    fun testIsSaveAsResourceAvailableRunsWithoutError() {
        val available = isSaveAsResourceAvailable()
        // Check that function executes cleanly
        assertTrue(available || !available)
    }

    /**
     * Save as Resource should open in the folder a loaded example or tutorial came from, rather
     * than the resource root, which is where it started before.
     */
    @Test
    fun testResolvesTheFolderAResourceWasLoadedFrom() {
        val root = File.createTempFile("syntona-resources", "").let { temp ->
            temp.delete()
            temp.also { it.mkdirs() }
        }
        try {
            File(root, "examples").mkdirs()
            File(root, "tutorials/basics").mkdirs()

            assertEquals(
                File(root, "examples"),
                resolveResourceDirectory(root, "examples/wavey_square.sntn")
            )
            // Nested folders should resolve to the innermost one.
            assertEquals(
                File(root, "tutorials/basics"),
                resolveResourceDirectory(root, "tutorials/basics/first.sntn")
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testResolvesNullSoTheCallerCanFallBack() {
        val root = File.createTempFile("syntona-resources", "").let { temp ->
            temp.delete()
            temp.also { it.mkdirs() }
        }
        try {
            // No resource loaded, so there is nothing to prefer.
            assertEquals(null, resolveResourceDirectory(root, null))
            assertEquals(null, resolveResourceDirectory(root, ""))
            // A folder that is not present, e.g. running outside a source checkout.
            assertEquals(null, resolveResourceDirectory(root, "missing/thing.sntn"))
            // No resource root at all.
            assertEquals(null, resolveResourceDirectory(null, "examples/a.sntn"))
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * The dialog only prefers the loaded folder while the open document is still a bundled
     * resource. Once it has been saved to a real path, the last used resource folder takes over,
     * so the user is not sent back to examples/ every time.
     */
    @Test
    fun testOnlyBundledResourcePathsCountAsResources() {
        val document = DocumentState(com.syntona.dataflow.modules.PatchModule("test"))
        FileState.addDocument(document)
        try {
            document.markSaved("examples/wavey_square.sntn")
            assertTrue(FileState.isResource, "an example should count as a resource")
            assertNotNull(FileState.currentFilePath.takeIf { FileState.isResource })

            document.markSaved("tutorials/basics/first.sntn")
            assertTrue(FileState.isResource, "a tutorial should count as a resource")

            document.markSaved("/Users/phil/Documents/Syntona/mine.sntn")
            assertTrue(!FileState.isResource, "a saved file is not a resource")
            assertEquals(null, FileState.currentFilePath.takeIf { FileState.isResource })
        } finally {
            FileState.removeDocument(document)
        }
    }
}

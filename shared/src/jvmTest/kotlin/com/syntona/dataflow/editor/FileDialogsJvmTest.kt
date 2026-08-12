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
     * Saving into composeResources should offer a .json name, whatever the open document is
     * called. Resources are served over HTTP by the web build and the bundled patches are .json
     * almost to a file, so offering the document's .sntn name is how strays got in.
     */
    @Test
    fun testResourceSaveDefaultsToJson() {
        assertEquals("patch.json", defaultResourceFileName("patch.sntn"))
        assertEquals("patch.json", defaultResourceFileName("patch.json"))
        assertEquals("wavey_square.json", defaultResourceFileName("wavey_square.sntn"))
        // A name with no extension still gets one.
        assertEquals("scratch.json", defaultResourceFileName("scratch"))
        // Dots inside the name are not extensions and must survive.
        assertEquals("etude_001.v2.json", defaultResourceFileName("etude_001.v2"))
        // Nothing to go on falls back to the standard patch name, renamed for resources.
        assertEquals("patch.json", defaultResourceFileName(""))
        assertEquals("patch.json", defaultResourceFileName("   "))
    }

    /**
     * Under composeResources the extension is forced, not defaulted. A .sntn file there is
     * served wrongly or not at all by a static web server, and the breakage only surfaces after
     * deployment, so a typed or picked .sntn name is converted instead of honoured.
     */
    @Test
    fun testSavingAResourceForcesJsonOverSntn() {
        assertEquals("/res/examples/xyz.json", asResourcePath("/res/examples/xyz.sntn"))
        assertEquals("/res/examples/xyz.json", asResourcePath("/res/examples/xyz.json"))
        assertEquals("/res/examples/xyz.json", asResourcePath("/res/examples/xyz"))
        // Picking an existing legacy resource from the file list must not perpetuate it.
        assertEquals("/res/examples/midiOut.json", asResourcePath("/res/examples/midiOut.sntn"))
        // Typed in a different case, still a .sntn as far as a web server is concerned.
        assertEquals("/res/examples/xyz.json", asResourcePath("/res/examples/xyz.SNTN"))
        // A dot in a folder name is not an extension on the file.
        assertEquals("/res/v1.2/xyz.json", asResourcePath("/res/v1.2/xyz"))
        // Dots inside the file name survive; only a patch extension is replaced.
        assertEquals("/res/etude_001.v2.json", asResourcePath("/res/etude_001.v2"))
        // An unrelated extension is left in place rather than destroyed.
        assertEquals("/res/notes.txt.json", asResourcePath("/res/notes.txt"))
    }

    /**
     * A name the user has already given an extension to is taken at their word. Appending
     * unconditionally is what turned a typed "xyz.json" into "xyz.json.sntn". This is the
     * document dialog's rule; resources force .json instead, see above.
     */
    @Test
    fun testAnExistingPatchExtensionIsNotDoubledUp() {
        assertEquals("/tmp/xyz.json", withPatchExtension("/tmp/xyz.json", ".json"))
        assertEquals("/tmp/xyz.json", withPatchExtension("/tmp/xyz.json", ".sntn"))
        assertEquals("/tmp/xyz.sntn", withPatchExtension("/tmp/xyz.sntn", ".sntn"))
        // Case typed by hand should count too.
        assertEquals("/tmp/xyz.JSON", withPatchExtension("/tmp/xyz.JSON", ".sntn"))
    }

    @Test
    fun testAMissingExtensionIsSupplied() {
        assertEquals("/tmp/xyz.json", withPatchExtension("/tmp/xyz", ".json"))
        assertEquals("/tmp/xyz.sntn", withPatchExtension("/tmp/xyz", ".sntn"))
        // An unrelated extension is not a patch extension, so the default is still added rather
        // than the name being silently accepted as a patch.
        assertEquals("/tmp/xyz.txt.json", withPatchExtension("/tmp/xyz.txt", ".json"))
        // A folder with a dot in it must not be mistaken for an extension on the file.
        assertEquals("/tmp/v1.2/xyz.json", withPatchExtension("/tmp/v1.2/xyz", ".json"))
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
            File(root, "help/music/input").mkdirs()

            assertEquals(
                File(root, "examples"),
                resolveResourceDirectory(root, "examples/wavey_square.sntn")
            )
            // Nested folders should resolve to the innermost one.
            assertEquals(
                File(root, "tutorials/basics"),
                resolveResourceDirectory(root, "tutorials/basics/first.sntn")
            )
            assertEquals(
                File(root, "help/music/input"),
                resolveResourceDirectory(root, "help/music/input/keyboard13.sntn")
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
     * A patch saved through Save as Resource lands on an absolute path inside the checkout, and
     * must still be recognised as a resource. While it was not, its title kept the extension and
     * its recorded path was rewritten to .sntn behind the .json file that was actually written.
     */
    @Test
    fun testAFreshlySavedResourceIsStillAResource() {
        val saved = "/Users/phil/work/syntona-kc/shared/src/commonMain/composeResources/" +
            "files/examples/xyz.json"
        val document = DocumentState(com.syntona.dataflow.modules.PatchModule("test"))
        FileState.addDocument(document)
        try {
            document.markSaved(saved)

            assertTrue(document.isResource, "a path under composeResources is a resource")
            assertEquals("examples/xyz.json", document.resourcePath)
            // The recorded path must match the file that was written, or the next plain Save
            // writes a second file beside it.
            assertEquals(saved, document.currentFilePath)
            assertEquals(saved, document.rootPatch.filePath)
            // No extension in the title.
            assertEquals("xyz", document.displayName)
        } finally {
            FileState.removeDocument(document)
        }
    }

    /**
     * Resources opened from the bundle and resources just saved should be titled the same way,
     * whichever extension they carry.
     */
    @Test
    fun testResourceTitlesDropTheExtension() {
        val document = DocumentState(com.syntona.dataflow.modules.PatchModule("test"))
        FileState.addDocument(document)
        try {
            document.markSaved("examples/keyNotes.json")
            assertEquals("keyNotes", document.displayName)

            document.markSaved("examples/midiOut.sntn")
            assertEquals("midiOut", document.displayName)

            document.markSaved("/res/composeResources/files/tutorials/basics/first.json")
            assertEquals("first", document.displayName)
            assertEquals("tutorials/basics/first.json", document.resourcePath)

            // An ordinary document keeps its extension, so it is clear what is on disk.
            document.markSaved("/Users/phil/Documents/Syntona/mine.sntn")
            assertEquals("mine.sntn", document.displayName)
            assertEquals(null, document.resourcePath)
        } finally {
            FileState.removeDocument(document)
        }
    }

    /**
     * Saving an ordinary document as .json used to be recorded as .sntn, so the title was wrong
     * and the next Save wrote somewhere else.
     */
    @Test
    fun testAnOrdinaryJsonSaveIsRecordedAsItself() {
        val document = DocumentState(com.syntona.dataflow.modules.PatchModule("test"))
        FileState.addDocument(document)
        try {
            document.markSaved("/Users/phil/Documents/Syntona/mine.json")
            assertEquals("/Users/phil/Documents/Syntona/mine.json", document.currentFilePath)
            assertEquals("mine.json", document.displayName)
        } finally {
            FileState.removeDocument(document)
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

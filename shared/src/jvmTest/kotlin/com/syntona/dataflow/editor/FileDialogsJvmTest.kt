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
}

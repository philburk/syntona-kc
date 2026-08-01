package com.syntona

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlNavigationTest {

    private val mockFilesIndex = setOf(
        "examples/drum_rhythm.sntn",
        "tutorials/01_tour/01_sawtooth_modulation.sntn",
        "help/music/input/keyboard13.sntn",
        "lineIn.sntn"
    )

    @Test
    fun testValidPathsAccepted() {
        assertEquals(
            "examples/drum_rhythm.sntn",
            sanitizeAndValidateResourcePath("examples/drum_rhythm.sntn", mockFilesIndex)
        )
        assertEquals(
            "tutorials/01_tour/01_sawtooth_modulation.sntn",
            sanitizeAndValidateResourcePath("tutorials/01_tour/01_sawtooth_modulation.sntn", mockFilesIndex)
        )
        assertEquals(
            "help/music/input/keyboard13.sntn",
            sanitizeAndValidateResourcePath("help/music/input/keyboard13.sntn", mockFilesIndex)
        )
    }

    @Test
    fun testDirectoryTraversalRejected() {
        assertEquals(
            null,
            sanitizeAndValidateResourcePath("../../etc/passwd", mockFilesIndex)
        )
        assertEquals(
            null,
            sanitizeAndValidateResourcePath("examples/../etc/passwd", mockFilesIndex)
        )
    }

    @Test
    fun testProtocolSchemesAndAbsolutePathsRejected() {
        assertEquals(
            null,
            sanitizeAndValidateResourcePath("http://evil.com/drum.sntn", mockFilesIndex)
        )
        assertEquals(
            null,
            sanitizeAndValidateResourcePath("/examples/drum_rhythm.sntn", mockFilesIndex)
        )
        assertEquals(
            null,
            sanitizeAndValidateResourcePath("javascript:alert(1)", mockFilesIndex)
        )
    }

    @Test
    fun testUnindexedFilesRejected() {
        assertEquals(
            null,
            sanitizeAndValidateResourcePath("examples/unknown.sntn", mockFilesIndex)
        )
    }
}

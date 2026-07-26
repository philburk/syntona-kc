package com.syntona.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsFormatTest {

    private fun roundTrip(values: Map<String, String>) {
        assertEquals(values, decodeSettings(encodeSettings(values)))
    }

    @Test
    fun testEmpty() {
        assertEquals(emptyMap(), decodeSettings(encodeSettings(emptyMap())))
        assertEquals(emptyMap(), decodeSettings(""))
    }

    @Test
    fun testSimpleValues() {
        roundTrip(mapOf("lastDirectoryUsed" to "/Users/phil/Documents", "windowWidth" to "800"))
    }

    @Test
    fun testValuesMayContainEquals() {
        // Only the key needs escaping, since everything after the first separator is the value.
        roundTrip(mapOf("query" to "a=b=c", "url" to "https://x/y?p=1&q=2"))
    }

    @Test
    fun testKeysMayContainEquals() {
        roundTrip(mapOf("odd=key" to "value", "=leading" to "v2", "trailing=" to "v3"))
    }

    @Test
    fun testNewlinesAndCarriageReturnsSurvive() {
        // A multi-line value must not be able to forge extra entries.
        roundTrip(mapOf("multi" to "line one\nline two\r\nline three", "after" to "still here"))
    }

    @Test
    fun testBackslashesSurvive() {
        roundTrip(
            mapOf(
                "windowsPath" to """C:\Users\phil\Documents""",
                "trailingSlash" to """ends with \""",
                "escapes" to """\n \r \= \\"""
            )
        )
    }

    @Test
    fun testInjectionThroughAValueIsNotPossible() {
        // Encoding a value that looks like another entry must not produce a second entry.
        val decoded = decodeSettings(encodeSettings(mapOf("a" to "1\nb=2")))
        assertEquals(mapOf("a" to "1\nb=2"), decoded)
        assertEquals(1, decoded.size)
    }

    @Test
    fun testUnicodeAndEmptyStrings() {
        roundTrip(mapOf("empty" to "", "unicode" to "ドラム — café 🎹"))
    }

    @Test
    fun testCommentsAndBlankLinesAreIgnored() {
        val text = """
            # Syntona settings
            # hand written comment

            a=1
            b=2
        """.trimIndent()
        assertEquals(mapOf("a" to "1", "b" to "2"), decodeSettings(text))
    }

    @Test
    fun testLinesWithoutASeparatorAreSkipped() {
        assertEquals(mapOf("good" to "1"), decodeSettings("garbage\ngood=1\n"))
    }

    @Test
    fun testOutputIsHumanReadable() {
        val text = encodeSettings(mapOf("lastDirectoryUsed" to "/tmp"))
        assertTrue(text.startsWith("# Syntona settings"), "should carry a header comment")
        assertTrue(text.contains("lastDirectoryUsed=/tmp"), "plain values should stay plain")
    }
}

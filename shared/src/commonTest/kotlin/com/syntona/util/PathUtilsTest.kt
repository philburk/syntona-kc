package com.syntona.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathUtilsTest {

    @Test
    fun testIsAbsolutePath() {
        assertTrue(isAbsolutePath("/Users/phil/Music/sample.wav"))
        assertTrue(isAbsolutePath("/sample.wav"))
        assertTrue(isAbsolutePath("C:\\Music\\sample.wav"))
        assertTrue(isAbsolutePath("c:/Music/sample.wav"))
        assertTrue(isAbsolutePath("D:\\sample.wav"))
        assertTrue(isAbsolutePath("\\\\server\\share\\sample.wav"))
        assertTrue(isAbsolutePath("//server/share/sample.wav"))

        assertFalse(isAbsolutePath("sample.wav"))
        assertFalse(isAbsolutePath("samples/snare.wav"))
        assertFalse(isAbsolutePath("./sample.wav"))
        assertFalse(isAbsolutePath("../sample.wav"))
        assertFalse(isAbsolutePath(""))
    }

    @Test
    fun testGetDirectoryOf() {
        assertEquals("/Users/phil/Music", getDirectoryOf("/Users/phil/Music/Patch.sntn"))
        assertEquals("/Users/phil/Music", getDirectoryOf("/Users/phil/Music/"))
        assertEquals("/Users/phil", getDirectoryOf("/Users/phil/Music"))
        assertEquals("C:/Music", getDirectoryOf("C:\\Music\\Patch.sntn"))
        assertEquals("", getDirectoryOf("Patch.sntn"))
        assertEquals("", getDirectoryOf(""))
    }

    @Test
    fun testRelativizePathAtOrBelow() {
        val sntnPath = "/Users/phil/Music/MyPatches/CoolPatch.sntn"

        // Same directory
        assertEquals("kick.wav", relativizePath("/Users/phil/Music/MyPatches/kick.wav", sntnPath))

        // Subdirectory
        assertEquals("samples/snare.wav", relativizePath("/Users/phil/Music/MyPatches/samples/snare.wav", sntnPath))
        assertEquals("drums/acoustic/hihat.wav", relativizePath("/Users/phil/Music/MyPatches/drums/acoustic/hihat.wav", sntnPath))

        // Same directory with backslashes
        assertEquals("samples/snare.wav", relativizePath("/Users/phil/Music/MyPatches\\samples\\snare.wav", sntnPath))
    }

    @Test
    fun testRelativizePathOutsideOrAbove() {
        val sntnPath = "/Users/phil/Music/MyPatches/CoolPatch.sntn"

        // Above / outside
        assertEquals("/Users/phil/Music/OtherFolder/snare.wav", relativizePath("/Users/phil/Music/OtherFolder/snare.wav", sntnPath))
        assertEquals("/Users/phil/Music/kick.wav", relativizePath("/Users/phil/Music/kick.wav", sntnPath))
        assertEquals("/Library/Audio/Sounds/cymbal.wav", relativizePath("/Library/Audio/Sounds/cymbal.wav", sntnPath))

        // Similar name prefix but not child directory
        assertEquals("/Users/phil/Music/MyPatchesExtra/kick.wav", relativizePath("/Users/phil/Music/MyPatchesExtra/kick.wav", sntnPath))

        // Null or blank base path
        assertEquals("/Users/phil/Music/kick.wav", relativizePath("/Users/phil/Music/kick.wav", null))
        assertEquals("/Users/phil/Music/kick.wav", relativizePath("/Users/phil/Music/kick.wav", ""))

        // Already relative path
        assertEquals("samples/kick.wav", relativizePath("samples/kick.wav", sntnPath))
    }

    @Test
    fun testRelativizePathWindows() {
        val sntnPath = "C:\\Users\\phil\\Music\\MyPatches\\CoolPatch.sntn"

        // Child
        assertEquals("kick.wav", relativizePath("C:\\Users\\phil\\Music\\MyPatches\\kick.wav", sntnPath))
        assertEquals("samples/snare.wav", relativizePath("C:\\Users\\phil\\Music\\MyPatches\\samples\\snare.wav", sntnPath))

        // Different drive
        assertEquals("D:\\Samples\\kick.wav", relativizePath("D:\\Samples\\kick.wav", sntnPath))

        // Outside folder
        assertEquals("C:\\Users\\phil\\Other\\kick.wav", relativizePath("C:\\Users\\phil\\Other\\kick.wav", sntnPath))
    }

    @Test
    fun testResolvePath() {
        val sntnPath = "/Volumes/USB/MyPatches/CoolPatch.sntn"

        // Relative paths
        assertEquals("/Volumes/USB/MyPatches/kick.wav", resolvePath("kick.wav", sntnPath))
        assertEquals("/Volumes/USB/MyPatches/samples/snare.wav", resolvePath("samples/snare.wav", sntnPath))
        assertEquals("/Volumes/USB/MyPatches/samples/snare.wav", resolvePath("samples\\snare.wav", sntnPath))

        // Already absolute paths
        assertEquals("/Users/phil/Global/kick.wav", resolvePath("/Users/phil/Global/kick.wav", sntnPath))
        assertEquals("C:\\Global\\kick.wav", resolvePath("C:\\Global\\kick.wav", sntnPath))

        // Null / blank base
        assertEquals("kick.wav", resolvePath("kick.wav", null))
        assertEquals("kick.wav", resolvePath("kick.wav", ""))
    }
}

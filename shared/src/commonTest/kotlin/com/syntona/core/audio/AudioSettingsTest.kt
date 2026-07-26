package com.syntona.core.audio

import com.softsynth.audiobridge.AudioDeviceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioSettingsTest {

    private val devices = listOf(
        AudioDeviceInfo(id = 0, name = "MacBook Pro Speakers", maxChannels = 2, isDefault = true),
        AudioDeviceInfo(id = 7, name = "Scarlett 2i2 USB", maxChannels = 2, isDefault = false),
        AudioDeviceInfo(id = 9, name = "External Headphones", maxChannels = 2, isDefault = false)
    )

    @Test
    fun testNoStoredNameUsesTheDefaultDevice() {
        assertEquals(AudioSettings.DEFAULT_DEVICE_ID, AudioSettings.resolveDeviceId(null, devices))
        assertEquals(AudioSettings.DEFAULT_DEVICE_ID, AudioSettings.resolveDeviceId("", devices))
    }

    @Test
    fun testStoredNameResolvesToItsCurrentId() {
        assertEquals(7, AudioSettings.resolveDeviceId("Scarlett 2i2 USB", devices))
        assertEquals(9, AudioSettings.resolveDeviceId("External Headphones", devices))
    }

    @Test
    fun testResolvingByNameSurvivesRenumbering() {
        // The whole reason names are stored rather than ids: the OS can renumber devices
        // between runs, so the same name must still find the right device.
        val renumbered = listOf(
            AudioDeviceInfo(id = 3, name = "Scarlett 2i2 USB", maxChannels = 2, isDefault = false),
            AudioDeviceInfo(id = 4, name = "MacBook Pro Speakers", maxChannels = 2, isDefault = true)
        )
        assertEquals(7, AudioSettings.resolveDeviceId("Scarlett 2i2 USB", devices))
        assertEquals(3, AudioSettings.resolveDeviceId("Scarlett 2i2 USB", renumbered))
    }

    @Test
    fun testMissingDeviceFallsBackToDefault() {
        // An interface that has been unplugged should not stop audio from starting.
        assertEquals(
            AudioSettings.DEFAULT_DEVICE_ID,
            AudioSettings.resolveDeviceId("Some Unplugged Interface", devices)
        )
        assertEquals(
            AudioSettings.DEFAULT_DEVICE_ID,
            AudioSettings.resolveDeviceId("Scarlett 2i2 USB", emptyList())
        )
    }

    @Test
    fun testNameMatchingIsCaseInsensitiveAsAFallback() {
        assertEquals(7, AudioSettings.resolveDeviceId("scarlett 2i2 usb", devices))
    }

    @Test
    fun testExactMatchWinsOverCaseInsensitiveMatch() {
        val ambiguous = listOf(
            AudioDeviceInfo(id = 1, name = "audio device", maxChannels = 2, isDefault = false),
            AudioDeviceInfo(id = 2, name = "Audio Device", maxChannels = 2, isDefault = false)
        )
        assertEquals(2, AudioSettings.resolveDeviceId("Audio Device", ambiguous))
        assertEquals(1, AudioSettings.resolveDeviceId("audio device", ambiguous))
    }

    @Test
    fun testIsDeviceMissingReportsWhetherTheUsersChoiceIsPresent() {
        assertFalse(AudioSettings.isDeviceMissing(null, devices), "default is never missing")
        assertFalse(AudioSettings.isDeviceMissing("", devices))
        assertFalse(AudioSettings.isDeviceMissing("Scarlett 2i2 USB", devices))
        assertTrue(AudioSettings.isDeviceMissing("Scarlett 2i2 USB", emptyList()))
        assertTrue(AudioSettings.isDeviceMissing("Nonexistent", devices))
    }

    @Test
    fun testDefaultDeviceIdMatchesTheBridgeConvention() {
        // AudioConfig.Builder uses -1 to mean "let the platform choose".
        assertEquals(-1, AudioSettings.DEFAULT_DEVICE_ID)
    }
}

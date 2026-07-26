package com.syntona.core.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.audiobridge.AudioDeviceInfo
import com.syntona.storage.SettingsStore

/**
 * Remembers which audio devices the user chose, and which ones are actually open.
 *
 * Preferences are stored by device *name* rather than by id, because ids are assigned by the
 * operating system and are not stable: unplugging a USB interface, rebooting, or plugging things
 * in a different order can renumber them, so a saved id could silently select a different device.
 * A name is resolved back to an id each time audio starts, and if the device is gone we fall back
 * to the system default rather than failing to start.
 */
object AudioSettings {

    const val KEY_OUTPUT_DEVICE = "audio.outputDeviceName"
    const val KEY_INPUT_DEVICE = "audio.inputDeviceName"

    /** Id meaning "let the platform pick", which is what the audio bridge uses by default. */
    const val DEFAULT_DEVICE_ID = -1

    private val settings: SettingsStore by lazy { SettingsStore.default }

    /** Device the user asked for, or null to follow the system default. */
    var preferredOutputName by mutableStateOf<String?>(null)
        private set

    var preferredInputName by mutableStateOf<String?>(null)
        private set

    /** Device actually in use, as reported by the stream after it opened. */
    var activeOutputName by mutableStateOf("")
        internal set

    var activeInputName by mutableStateOf("")
        internal set

    /** True when a preferred device was named but could not be found when audio last started. */
    var outputFellBackToDefault by mutableStateOf(false)
        internal set

    var inputFellBackToDefault by mutableStateOf(false)
        internal set

    private var loaded = false

    /** Reads stored preferences. Safe to call repeatedly; only the first call reads. */
    fun load() {
        if (loaded) return
        loaded = true
        preferredOutputName = settings.getString(KEY_OUTPUT_DEVICE).ifEmpty { null }
        preferredInputName = settings.getString(KEY_INPUT_DEVICE).ifEmpty { null }
    }

    /** Chooses the output device by name, or the system default when [name] is null. */
    fun selectOutputDevice(name: String?) {
        preferredOutputName = name
        settings.putString(KEY_OUTPUT_DEVICE, name ?: "")
    }

    /** Chooses the input device by name, or the system default when [name] is null. */
    fun selectInputDevice(name: String?) {
        preferredInputName = name
        settings.putString(KEY_INPUT_DEVICE, name ?: "")
    }

    /**
     * Finds the id for a stored device [name] in [devices].
     *
     * Returns [DEFAULT_DEVICE_ID] when no name is stored or the named device is not currently
     * present, so that a missing interface degrades to the default instead of failing to open.
     * An exact name match is preferred; a case-insensitive match is accepted as a fallback, since
     * some platforms vary the capitalisation they report between runs.
     */
    fun resolveDeviceId(name: String?, devices: List<AudioDeviceInfo>): Int {
        if (name.isNullOrEmpty()) return DEFAULT_DEVICE_ID
        devices.firstOrNull { it.name == name }?.let { return it.id }
        devices.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it.id }
        return DEFAULT_DEVICE_ID
    }

    /** True if [name] names a device that is not in [devices], so the default will be used. */
    fun isDeviceMissing(name: String?, devices: List<AudioDeviceInfo>): Boolean {
        if (name.isNullOrEmpty()) return false
        return resolveDeviceId(name, devices) == DEFAULT_DEVICE_ID
    }
}

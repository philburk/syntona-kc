package com.syntona.storage

/**
 * A small persistent map of strings, for things like window preferences or the last directory
 * the user opened a file from.
 *
 * Values are held in memory and written through to the [backend] as they change, so a caller
 * never has to remember to save. Use [edit] to group several changes into a single write.
 *
 * Loading is lazy: nothing is read until the first access. Reads and writes are synchronous, and
 * the documents are tiny, but on Android that still means touching the disk, so prefer not to
 * call this from a scroll or animation frame.
 *
 * Not thread safe. Use it from one thread, normally the UI thread.
 */
class SettingsStore(
    private val name: String = DEFAULT_NAME,
    private val backend: SettingsBackend = platformSettingsBackend()
) {
    private val values = LinkedHashMap<String, String>()
    private var loaded = false
    private var batching = false
    private var dirtyWhileBatching = false

    private fun ensureLoaded() {
        if (loaded) return
        // Set before reading so that a backend failure does not retry on every single access.
        loaded = true
        val text = backend.read(name) ?: return
        values.putAll(decodeSettings(text))
    }

    /** Re-reads from the backend, discarding anything held in memory. */
    fun reload() {
        values.clear()
        loaded = false
        ensureLoaded()
    }

    /** Writes the current contents out. Called automatically unless inside [edit]. */
    fun save() {
        ensureLoaded()
        backend.write(name, encodeSettings(values))
    }

    private fun persist() {
        if (batching) dirtyWhileBatching = true else save()
    }

    /**
     * Applies several changes and writes once at the end, rather than after each one.
     *
     * ```
     * settings.edit {
     *     putString("lastDirectoryUsed", path)
     *     putInt("windowWidth", width)
     * }
     * ```
     */
    fun edit(block: SettingsStore.() -> Unit) {
        val alreadyBatching = batching
        batching = true
        try {
            block(this)
        } finally {
            if (!alreadyBatching) {
                batching = false
                if (dirtyWhileBatching) {
                    dirtyWhileBatching = false
                    save()
                }
            }
        }
    }

    /** All keys currently stored, in the order they were first written. */
    fun keys(): Set<String> {
        ensureLoaded()
        return values.keys.toSet()
    }

    fun contains(key: String): Boolean {
        ensureLoaded()
        return values.containsKey(key)
    }

    fun remove(key: String) {
        ensureLoaded()
        if (values.remove(key) != null) persist()
    }

    /** Removes everything, including the stored document. */
    fun clear() {
        ensureLoaded()
        values.clear()
        if (batching) dirtyWhileBatching = true else backend.delete(name)
    }

    fun getString(key: String, default: String = ""): String {
        ensureLoaded()
        return values[key] ?: default
    }

    fun putString(key: String, value: String) {
        ensureLoaded()
        if (values.put(key, value) != value) persist()
    }

    // Typed accessors fall back to the default when the key is missing or the stored text does
    // not parse, so a hand-edited or outdated file cannot break the app.

    fun getInt(key: String, default: Int = 0): Int {
        ensureLoaded()
        return values[key]?.toIntOrNull() ?: default
    }

    fun putInt(key: String, value: Int) = putString(key, value.toString())

    fun getLong(key: String, default: Long = 0L): Long {
        ensureLoaded()
        return values[key]?.toLongOrNull() ?: default
    }

    fun putLong(key: String, value: Long) = putString(key, value.toString())

    fun getDouble(key: String, default: Double = 0.0): Double {
        ensureLoaded()
        return values[key]?.toDoubleOrNull() ?: default
    }

    fun putDouble(key: String, value: Double) = putString(key, value.toString())

    fun getFloat(key: String, default: Float = 0f): Float {
        ensureLoaded()
        return values[key]?.toFloatOrNull() ?: default
    }

    fun putFloat(key: String, value: Float) = putString(key, value.toString())

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        ensureLoaded()
        return when (values[key]?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }
    }

    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    companion object {
        const val DEFAULT_NAME = "settings"

        /** Shared store for general application settings. */
        val default: SettingsStore by lazy { SettingsStore() }

        /** Key for the directory the user last opened or saved a patch in. */
        const val KEY_LAST_DIRECTORY = "lastDirectoryUsed"
        const val KEY_LAST_DOCUMENT_DIRECTORY = "lastDocumentDirectoryUsed"

        /** Keys for the MIDI devices last chosen, so a patch with none can pick them up. */
        const val KEY_LAST_MIDI_SOURCE = "lastMidiSourceUsed"
        const val KEY_LAST_MIDI_DESTINATION = "lastMidiDestinationUsed"

        /** Key for the most recently saved or opened patch files. */
        const val KEY_RECENT_PATCHES = "recentPatches"
    }
}

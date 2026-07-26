package com.syntona.storage

/**
 * Platform storage for small named documents of text, such as an application's settings.
 *
 * This is deliberately the smallest surface that every platform can support: the browser has no
 * file system, so anything richer than "read and write a named blob" would not map onto it. All
 * of the key/value handling lives above this, in [SettingsStore], so it is written and tested
 * once rather than per platform.
 *
 * Implementations are expected to be small and synchronous. Documents are on the order of a few
 * hundred bytes, so this is intended for settings and similar, never for patch or audio data.
 */
interface SettingsBackend {

    /** Returns the contents of [name], or null if it has never been written. */
    fun read(name: String): String?

    /** Replaces the contents of [name], creating it if needed. */
    fun write(name: String, text: String)

    /** Removes [name]. Does nothing if it does not exist. */
    fun delete(name: String)
}

/** The [SettingsBackend] for the platform the app is running on. */
expect fun platformSettingsBackend(): SettingsBackend

/**
 * A [SettingsBackend] that keeps everything in memory. Useful in tests, and as a fallback on a
 * platform where persistent storage is unavailable, so that settings still work for the run.
 */
class InMemorySettingsBackend : SettingsBackend {
    private val documents = mutableMapOf<String, String>()

    override fun read(name: String): String? = documents[name]

    override fun write(name: String, text: String) {
        documents[name] = text
    }

    override fun delete(name: String) {
        documents.remove(name)
    }
}

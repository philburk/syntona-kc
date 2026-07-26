@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona.storage

@JsFun("""(key) => {
    try {
        var value = localStorage.getItem(key);
        return value === null ? null : value;
    } catch (e) {
        return null;
    }
}""")
private external fun jsLocalStorageGet(key: String): String?

@JsFun("""(key, value) => {
    try {
        localStorage.setItem(key, value);
        return true;
    } catch (e) {
        return false;
    }
}""")
private external fun jsLocalStorageSet(key: String, value: String): Boolean

@JsFun("""(key) => {
    try {
        localStorage.removeItem(key);
    } catch (e) {
    }
}""")
private external fun jsLocalStorageRemove(key: String)

/**
 * Stores settings in the browser's `localStorage`, keyed by [KEY_PREFIX] plus the document name.
 *
 * The browser has no file system, so there is nothing to write documents into; localStorage is
 * the equivalent, and it persists across sessions for the same origin.
 *
 * Every call is guarded because localStorage is not always usable: it throws when a browser is in
 * a private or blocked-cookies mode, and `setItem` throws once the origin's quota is full. In
 * those cases settings fall back to memory for the session instead of the app failing.
 */
class BrowserSettingsBackend : SettingsBackend {

    private val fallback = InMemorySettingsBackend()

    private fun keyFor(name: String) = "$KEY_PREFIX$name"

    override fun read(name: String): String? =
        jsLocalStorageGet(keyFor(name)) ?: fallback.read(name)

    override fun write(name: String, text: String) {
        if (!jsLocalStorageSet(keyFor(name), text)) {
            println("localStorage unavailable, keeping settings in memory for this session")
            fallback.write(name, text)
        }
    }

    override fun delete(name: String) {
        jsLocalStorageRemove(keyFor(name))
        fallback.delete(name)
    }

    companion object {
        /** Namespaced so Syntona cannot collide with anything else served from the same origin. */
        const val KEY_PREFIX = "syntona.settings."
    }
}

actual fun platformSettingsBackend(): SettingsBackend = BrowserSettingsBackend()

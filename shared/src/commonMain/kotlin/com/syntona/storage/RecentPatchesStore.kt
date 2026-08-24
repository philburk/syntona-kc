package com.syntona.storage

import androidx.compose.runtime.mutableStateListOf
import com.syntona.dataflow.editor.resourceRelativePath

/**
 * Tracks the list of up to [MAX_RECENT_PATCHES] most recently opened or saved user patch files.
 * Persisted in [SettingsStore.default].
 */
object RecentPatchesStore {
    const val MAX_RECENT_PATCHES = 8
    const val KEY_RECENT_PATCHES = "recentPatches"

    val recentPatches = mutableStateListOf<String>()

    private var loaded = false

    init {
        ensureLoaded()
    }

    fun ensureLoaded(settings: SettingsStore = SettingsStore.default) {
        if (loaded) return
        loaded = true
        val text = settings.getString(KEY_RECENT_PATCHES, "")
        val items = decodeRecentPatches(text)
        recentPatches.clear()
        recentPatches.addAll(items)
    }

    fun reload(settings: SettingsStore = SettingsStore.default) {
        loaded = false
        ensureLoaded(settings)
    }

    fun addRecentPatch(path: String, settings: SettingsStore = SettingsStore.default) {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("shared_piece")) return
        if (resourceRelativePath(trimmed) != null) return

        ensureLoaded(settings)

        // Remove if already present, then insert at the top
        recentPatches.removeAll { it == trimmed }
        recentPatches.add(0, trimmed)

        while (recentPatches.size > MAX_RECENT_PATCHES) {
            recentPatches.removeAt(recentPatches.size - 1)
        }

        save(settings)
    }

    fun removeRecentPatch(path: String, settings: SettingsStore = SettingsStore.default) {
        ensureLoaded(settings)
        if (recentPatches.removeAll { it == path }) {
            save(settings)
        }
    }

    fun clear(settings: SettingsStore = SettingsStore.default) {
        ensureLoaded(settings)
        recentPatches.clear()
        settings.remove(KEY_RECENT_PATCHES)
    }

    fun save(settings: SettingsStore = SettingsStore.default) {
        settings.putString(KEY_RECENT_PATCHES, encodeRecentPatches(recentPatches))
    }

    internal fun encodeRecentPatches(paths: List<String>): String {
        return paths.joinToString("\n")
    }

    internal fun decodeRecentPatches(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && resourceRelativePath(it) == null && !it.startsWith("shared_piece") }
            .distinct()
            .take(MAX_RECENT_PATCHES)
            .toList()
    }
}

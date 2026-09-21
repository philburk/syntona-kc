package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.storage.SettingsStore

/**
 * Global view and rendering settings for [GraphEditor].
 */
object GraphViewSettings {
    const val KEY_WIRES_ON_TOP = SettingsStore.KEY_WIRES_ON_TOP

    var wiresOnTop by mutableStateOf(SettingsStore.default.getBoolean(KEY_WIRES_ON_TOP, false))
        private set

    fun setWiresOnTop(value: Boolean, settings: SettingsStore = SettingsStore.default) {
        wiresOnTop = value
        settings.putBoolean(KEY_WIRES_ON_TOP, value)
    }

    fun toggleWiresOnTop(settings: SettingsStore = SettingsStore.default) {
        setWiresOnTop(!wiresOnTop, settings)
    }

    fun resetForTest(value: Boolean = false) {
        wiresOnTop = value
    }
}

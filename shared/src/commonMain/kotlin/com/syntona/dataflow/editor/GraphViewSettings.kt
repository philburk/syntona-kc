package com.syntona.dataflow.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.storage.SettingsStore

enum class ColorTheme(val label: String) {
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromString(value: String): ColorTheme =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: LIGHT
    }
}

/**
 * Global view and rendering settings for [GraphEditor].
 */
object GraphViewSettings {
    const val KEY_WIRES_ON_TOP = SettingsStore.KEY_WIRES_ON_TOP
    const val KEY_COLOR_THEME = SettingsStore.KEY_COLOR_THEME

    var wiresOnTop by mutableStateOf(SettingsStore.default.getBoolean(KEY_WIRES_ON_TOP, false))
        private set

    var colorTheme by mutableStateOf(
        ColorTheme.fromString(SettingsStore.default.getString(KEY_COLOR_THEME, ColorTheme.LIGHT.name))
    )
        private set

    val isDarkMode: Boolean
        get() = colorTheme == ColorTheme.DARK

    val graphColors: GraphColors
        get() = when (colorTheme) {
            ColorTheme.DARK -> DarkGraphColors
            ColorTheme.LIGHT -> LightGraphColors
        }

    fun setWiresOnTop(value: Boolean, settings: SettingsStore = SettingsStore.default) {
        wiresOnTop = value
        settings.putBoolean(KEY_WIRES_ON_TOP, value)
    }

    fun toggleWiresOnTop(settings: SettingsStore = SettingsStore.default) {
        setWiresOnTop(!wiresOnTop, settings)
    }

    fun setColorTheme(theme: ColorTheme, settings: SettingsStore = SettingsStore.default) {
        colorTheme = theme
        settings.putString(KEY_COLOR_THEME, theme.name)
    }

    fun resetForTest(
        wiresOnTopValue: Boolean = false,
        colorThemeValue: ColorTheme = ColorTheme.LIGHT
    ) {
        wiresOnTop = wiresOnTopValue
        colorTheme = colorThemeValue
    }
}

package com.syntona.storage

import java.io.File

/**
 * Stores settings in the location each desktop OS reserves for application configuration:
 *
 *  - macOS: `~/Library/Application Support/Syntona/settings`
 *  - Windows: `%APPDATA%\Syntona\settings`
 *  - Linux and others: `$XDG_CONFIG_HOME/syntona/settings`, or `~/.config/syntona/settings`
 *
 * These are used in preference to a folder under `~/Documents` because settings are not user
 * documents: putting them there clutters a folder the user browses, invites hand-editing of files
 * that the app rewrites, and — most importantly — `~/Documents` is commonly synced by iCloud,
 * OneDrive or Dropbox. Syncing machine-specific values such as `lastDirectoryUsed` between
 * computers would produce paths that do not exist on the other machine.
 */
class DesktopSettingsBackend(
    private val directory: File = defaultSettingsDirectory()
) : SettingsBackend {

    private fun fileFor(name: String) = File(directory, "$name.properties")

    override fun read(name: String): String? {
        val file = fileFor(name)
        return try {
            if (file.isFile) file.readText() else null
        } catch (e: Exception) {
            println("Could not read settings from $file: ${e.message}")
            null
        }
    }

    override fun write(name: String, text: String) {
        val file = fileFor(name)
        try {
            directory.mkdirs()
            // Write to a temporary file and swap it in, so that being interrupted partway cannot
            // leave a truncated settings file behind.
            val temp = File(directory, "$name.properties.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                // Windows will not rename onto an existing file.
                file.delete()
                if (!temp.renameTo(file)) {
                    file.writeText(text)
                    temp.delete()
                }
            }
        } catch (e: Exception) {
            println("Could not write settings to $file: ${e.message}")
        }
    }

    override fun delete(name: String) {
        try {
            fileFor(name).delete()
        } catch (e: Exception) {
            println("Could not delete settings file for $name: ${e.message}")
        }
    }

    companion object {
        fun defaultSettingsDirectory(): File {
            val home = System.getProperty("user.home") ?: "."
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            val base = when {
                os.contains("mac") || os.contains("darwin") ->
                    File(home, "Library/Application Support/Syntona")

                os.contains("win") -> {
                    val appData = System.getenv("APPDATA")
                    if (!appData.isNullOrBlank()) File(appData, "Syntona")
                    else File(home, "AppData/Roaming/Syntona")
                }

                else -> {
                    val configHome = System.getenv("XDG_CONFIG_HOME")
                    if (!configHome.isNullOrBlank()) File(configHome, "syntona")
                    else File(home, ".config/syntona")
                }
            }
            return File(base, "settings")
        }
    }
}

actual fun platformSettingsBackend(): SettingsBackend = DesktopSettingsBackend()

package com.syntona.storage

import android.content.Context
import java.io.File

/**
 * Holds the application [Context] that the Android settings backend needs.
 *
 * Android cannot resolve its own private storage without a Context, and common code has no way to
 * supply one, so the app must hand it over once at startup. [com.syntona.MainActivity] does this
 * in `onCreate`.
 */
object AndroidAppContext {
    @Volatile
    var applicationContext: Context? = null
        private set

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }
}

/**
 * Stores settings under the app's private files directory, which needs no permissions and is
 * removed when the app is uninstalled.
 *
 * If [AndroidAppContext] was never initialized there is nowhere to write, so this falls back to
 * keeping settings in memory: they work for the session and are lost on exit, rather than the app
 * crashing at startup.
 */
class AndroidSettingsBackend : SettingsBackend {

    private val fallback = InMemorySettingsBackend()

    private fun directory(): File? {
        val context = AndroidAppContext.applicationContext ?: return null
        return File(context.filesDir, "settings")
    }

    private fun fileFor(name: String): File? = directory()?.let { File(it, "$name.properties") }

    override fun read(name: String): String? {
        val file = fileFor(name) ?: return fallback.read(name)
        return try {
            if (file.isFile) file.readText() else null
        } catch (e: Exception) {
            println("Could not read settings from $file: ${e.message}")
            null
        }
    }

    override fun write(name: String, text: String) {
        val file = fileFor(name)
        if (file == null) {
            fallback.write(name, text)
            return
        }
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$name.properties.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
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
        val file = fileFor(name)
        if (file == null) {
            fallback.delete(name)
            return
        }
        try {
            file.delete()
        } catch (e: Exception) {
            println("Could not delete settings file for $name: ${e.message}")
        }
    }
}

actual fun platformSettingsBackend(): SettingsBackend = AndroidSettingsBackend()

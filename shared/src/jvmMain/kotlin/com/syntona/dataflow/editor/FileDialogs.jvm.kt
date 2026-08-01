package com.syntona.dataflow.editor

import com.syntona.SYNTONA_FILE_EXTENSION
import com.syntona.SYNTONA_FILE_SUFFIX
import com.syntona.storage.SettingsStore
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Root of the bundled resource files in the source tree, when running from a dev checkout. */
private val resourceRootDirectory: File? = File(
    System.getProperty("user.home"),
    "work/syntona-kc/shared/src/commonMain/composeResources/files/"
).takeIf { it.exists() }

private var lastResourceDirectory: File? = resourceRootDirectory

/**
 * Turns a resource path such as `examples/wavey_square.sntn` into the directory it lives in,
 * so that saving a loaded example starts in the folder it came from rather than the resource
 * root. Returns null when the path is not a resource or the folder is not there.
 */
internal fun resolveResourceDirectory(root: File?, relativePath: String?): File? {
    if (root == null || relativePath.isNullOrEmpty()) return null
    return File(root, relativePath).parentFile?.takeIf { it.isDirectory }
}

private fun getInitialDocumentDirectory(): File {
    val savedPath = try {
        val docPath = SettingsStore.default.getString(SettingsStore.KEY_LAST_DOCUMENT_DIRECTORY)
        if (docPath.isNotEmpty()) docPath
        else SettingsStore.default.getString(SettingsStore.KEY_LAST_DIRECTORY)
    } catch (_: Exception) { "" }

    if (savedPath.isNotEmpty()) {
        val dir = File(savedPath)
        if (dir.exists() && dir.isDirectory) {
            return dir
        }
    }

    val docsDir = File(System.getProperty("user.home"), "Documents/Syntona")
    if (!docsDir.exists()) {
        docsDir.mkdirs()
    }
    return docsDir
}

private var lastDocumentDirectory: File = getInitialDocumentDirectory()

private fun updateLastDocumentDirectory(dir: File) {
    if (!dir.exists()) {
        dir.mkdirs()
    }
    lastDocumentDirectory = dir
    try {
        SettingsStore.default.edit {
            putString(SettingsStore.KEY_LAST_DOCUMENT_DIRECTORY, dir.absolutePath)
            putString(SettingsStore.KEY_LAST_DIRECTORY, dir.absolutePath)
        }
    } catch (_: Exception) {}
}

actual fun isSaveAsResourceAvailable(): Boolean {
    return lastResourceDirectory?.exists() == true
}

actual fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Open Patch"
        fileFilter = FileNameExtensionFilter("Syntona files (*$SYNTONA_FILE_SUFFIX)", SYNTONA_FILE_EXTENSION)
        if (!lastDocumentDirectory.exists()) lastDocumentDirectory.mkdirs()
        currentDirectory = lastDocumentDirectory
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile
        updateLastDocumentDirectory(file.parentFile)
        onResult(file.absolutePath, file.readText())
    }
}

actual fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save Patch"
        fileFilter = FileNameExtensionFilter("Syntona files (*$SYNTONA_FILE_SUFFIX)", SYNTONA_FILE_EXTENSION)
        if (!lastDocumentDirectory.exists()) lastDocumentDirectory.mkdirs()
        currentDirectory = lastDocumentDirectory
        selectedFile = File(lastDocumentDirectory, defaultName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(SYNTONA_FILE_SUFFIX)) file = File(file.path + SYNTONA_FILE_SUFFIX)
        updateLastDocumentDirectory(file.parentFile)
        file.writeText(content)
        onSaved(file.absolutePath)
    }
}

actual fun showSaveAsResourceFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    // Prefer the folder the currently loaded resource came from, so that saving an example
    // opened from the Help menu starts in examples/ rather than the resource root. Once the
    // patch has been saved somewhere concrete it is no longer a resource, and the directory
    // last used for saving takes over again.
    val loadedFrom = resolveResourceDirectory(
        resourceRootDirectory,
        FileState.currentFilePath.takeIf { FileState.isResource }
    )
    val dir = loadedFrom ?: lastResourceDirectory ?: return
    val chooser = JFileChooser().apply {
        dialogTitle = "Save Patch as Resource"
        fileFilter = FileNameExtensionFilter("Syntona files (*$SYNTONA_FILE_SUFFIX)", SYNTONA_FILE_EXTENSION)
        currentDirectory = dir
        selectedFile = File(dir, defaultName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(SYNTONA_FILE_SUFFIX)) file = File(file.path + SYNTONA_FILE_SUFFIX)
        lastResourceDirectory = file.parentFile
        file.writeText(content)
        onSaved(file.absolutePath)
    }
}

actual fun saveFileDirect(path: String, content: String) {
    val file = File(path)
    file.writeText(content)
    val parent = file.parentFile
    if (parent != null) {
        val resourceDir = lastResourceDirectory
        if (resourceDir != null && file.absolutePath.startsWith(resourceDir.absolutePath)) {
            lastResourceDirectory = parent
        } else {
            updateLastDocumentDirectory(parent)
        }
    }
}

actual fun chooseWavRecordFile(defaultName: String, onSelected: (path: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Record Audio"
        fileFilter = FileNameExtensionFilter("WAV files (*.wav)", "wav")
        if (!lastDocumentDirectory.exists()) lastDocumentDirectory.mkdirs()
        currentDirectory = lastDocumentDirectory
        selectedFile = File(lastDocumentDirectory, defaultName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(".wav")) file = File(file.path + ".wav")
        updateLastDocumentDirectory(file.parentFile)

        if (file.exists()) {
            val response = javax.swing.JOptionPane.showConfirmDialog(
                null,
                "${file.name} already exists. Do you want to replace it?",
                "Confirm Save As",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE
            )
            if (response != javax.swing.JOptionPane.YES_OPTION) {
                return
            }
        }

        onSelected(file.absolutePath)
    }
}

actual fun readFileDirect(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        null
    }
}

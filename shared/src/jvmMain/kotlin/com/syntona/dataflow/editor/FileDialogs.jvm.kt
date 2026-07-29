package com.syntona.dataflow.editor

import com.syntona.storage.SettingsStore
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private var lastResourceDirectory: File? = File(
    System.getProperty("user.home"),
    "work/syntona-kc/shared/src/commonMain/composeResources/files/"
).takeIf { it.exists() }

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
        fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
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
        fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
        if (!lastDocumentDirectory.exists()) lastDocumentDirectory.mkdirs()
        currentDirectory = lastDocumentDirectory
        selectedFile = File(lastDocumentDirectory, defaultName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(".json")) file = File(file.path + ".json")
        updateLastDocumentDirectory(file.parentFile)
        file.writeText(content)
        onSaved(file.absolutePath)
    }
}

actual fun showSaveAsResourceFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    val dir = lastResourceDirectory ?: return
    val chooser = JFileChooser().apply {
        dialogTitle = "Save Patch as Resource"
        fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
        currentDirectory = dir
        selectedFile = File(dir, defaultName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(".json")) file = File(file.path + ".json")
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

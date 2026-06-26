package com.syntona.dataflow.editor

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private var lastDirectory: File? = null

actual fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Open Patch"
        fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
        lastDirectory?.let { currentDirectory = it }
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile
        lastDirectory = file.parentFile
        onResult(file.absolutePath, file.readText())
    }
}

actual fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save Patch"
        fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
        val dir = lastDirectory
        if (dir != null) {
            currentDirectory = dir
            selectedFile = File(dir, defaultName)
        } else {
            selectedFile = File(defaultName)
        }
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(".json")) file = File(file.path + ".json")
        lastDirectory = file.parentFile
        file.writeText(content)
        onSaved(file.absolutePath)
    }
}

actual fun saveFileDirect(path: String, content: String) {
    val file = File(path)
    file.writeText(content)
    lastDirectory = file.parentFile
}

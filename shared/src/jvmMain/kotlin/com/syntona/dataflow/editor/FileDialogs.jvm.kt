package com.syntona.dataflow.editor

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Open Patch"
        fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile
        onResult(file.absolutePath, file.readText())
    }
}

actual fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save Patch"
        fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
        selectedFile = File(defaultName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var file = chooser.selectedFile
        if (!file.name.endsWith(".json")) file = File(file.path + ".json")
        file.writeText(content)
        onSaved(file.absolutePath)
    }
}

actual fun saveFileDirect(path: String, content: String) {
    File(path).writeText(content)
}

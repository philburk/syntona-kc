package com.syntona.dataflow.editor

actual fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit) {}

actual fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {}

actual fun saveFileDirect(path: String, content: String) {}

actual fun chooseWavRecordFile(defaultName: String, onSelected: (path: String) -> Unit) {}

actual fun readFileDirect(path: String): String? = null


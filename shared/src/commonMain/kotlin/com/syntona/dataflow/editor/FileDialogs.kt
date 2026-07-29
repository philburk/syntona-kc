package com.syntona.dataflow.editor

expect fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit)

expect fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit)

expect fun isSaveAsResourceAvailable(): Boolean

expect fun showSaveAsResourceFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit)

/** Write [content] to [path] without showing a dialog. On WASM, [path] is just a filename. */
expect fun saveFileDirect(path: String, content: String)

expect fun chooseWavRecordFile(defaultName: String, onSelected: (path: String) -> Unit)

/** Read content from [path] without showing a dialog. Returns null if error or unsupported. */
expect fun readFileDirect(path: String): String?



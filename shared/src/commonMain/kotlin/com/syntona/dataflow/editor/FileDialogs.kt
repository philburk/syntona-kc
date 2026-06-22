package com.syntona.dataflow.editor

expect fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit)

expect fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit)

/** Write [content] to [path] without showing a dialog. On WASM, [path] is just a filename. */
expect fun saveFileDirect(path: String, content: String)

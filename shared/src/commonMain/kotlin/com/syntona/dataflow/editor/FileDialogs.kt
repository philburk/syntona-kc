package com.syntona.dataflow.editor

expect fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit)

expect fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit)

expect fun showExportSourceFileDialog(
    defaultName: String,
    extension: String,
    dialogTitle: String,
    onChosen: (path: String) -> Unit
)

expect fun isSaveAsResourceAvailable(): Boolean

expect fun showSaveAsResourceFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit)

/** Write [content] to [path] without showing a dialog. On WASM, [path] is just a filename. */
expect fun saveFileDirect(path: String, content: String)

/** Write [content] to bundled resource [relativePath] when SaveAsResource privilege is available. */
expect fun saveResourceFileDirect(relativePath: String, content: String)

expect fun chooseWavRecordFile(defaultName: String, onSelected: (path: String) -> Unit)

expect fun chooseWavOpenFile(onSelected: (path: String) -> Unit)

/** Read content from [path] without showing a dialog. Returns null if error or unsupported. */
expect fun readFileDirect(path: String): String?

/** Read recent patch file from disk asynchronously or synchronously. Returns null if error or unsupported. */
expect fun readRecentPatchFile(path: String, onResult: (content: String?) -> Unit)

/** Read binary content from [path] without showing a dialog. Returns null if error or unsupported. */
expect fun readBinaryFileDirect(path: String): ByteArray?

/** Read content from bundled resource [relativePath]. Returns null if error or unsupported. */
expect fun readResourceFileDirect(relativePath: String): String?

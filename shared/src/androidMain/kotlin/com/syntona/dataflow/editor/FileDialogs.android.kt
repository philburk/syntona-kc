package com.syntona.dataflow.editor

import com.syntona.SYNTONA_FILE_SUFFIX

actual fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit) {}

actual fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {}

actual fun isSaveAsResourceAvailable(): Boolean = false

actual fun showSaveAsResourceFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {}

actual fun saveFileDirect(path: String, content: String) {}

actual fun saveResourceFileDirect(relativePath: String, content: String) {}

actual fun chooseWavRecordFile(defaultName: String, onSelected: (path: String) -> Unit) {}

actual fun chooseWavOpenFile(onSelected: (path: String) -> Unit) {}

actual fun readFileDirect(path: String): String? = null

actual fun readRecentPatchFile(path: String, onResult: (content: String?) -> Unit) {
    onResult(readFileDirect(path))
}

actual fun readBinaryFileDirect(path: String): ByteArray? = null

actual fun readResourceFileDirect(relativePath: String): String? {
    val stem = relativePath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
    return com.syntona.dataflow.registry.InstrumentRegistry.instrumentJsonCache[stem]
        ?: com.syntona.dataflow.registry.SoundRegistry.soundJsonCache[stem]
}

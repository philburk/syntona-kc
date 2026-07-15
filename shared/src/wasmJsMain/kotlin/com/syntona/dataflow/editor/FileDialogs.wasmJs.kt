@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona.dataflow.editor

@JsFun("""(callback) => {
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = function(e) {
        var file = e.target.files[0];
        if (!file) return;
        var reader = new FileReader();
        reader.onload = function(ev) { callback(file.name, ev.target.result); };
        reader.readAsText(file);
    };
    input.click();
}""")
private external fun jsOpenFilePicker(callback: (String, String) -> Unit)

@JsFun("""(content, filename) => {
    var blob = new Blob([content], {type: 'application/json'});
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}""")
private external fun jsSaveFile(content: String, filename: String)

actual fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit) {
    jsOpenFilePicker(onResult)
}

actual fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    jsSaveFile(content, defaultName)
    onSaved(defaultName)
}

actual fun saveFileDirect(path: String, content: String) {
    // On WASM, "path" is just the filename; re-trigger the download.
    jsSaveFile(content, path)
}

@JsFun("() => { return prompt('Enter filename for recording:', 'recording.wav'); }")
private external fun jsPrompt(): String?

actual fun chooseWavRecordFile(defaultName: String, onSelected: (path: String) -> Unit) {
    val name = jsPrompt()
    if (name != null && name.isNotBlank()) {
        val cleanName = if (name.endsWith(".wav")) name else "$name.wav"
        onSelected(cleanName)
    }
}

actual fun readFileDirect(path: String): String? = null


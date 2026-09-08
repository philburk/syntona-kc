@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona.dataflow.editor

import com.syntona.SYNTONA_FILE_SUFFIX
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

@JsFun("""(callback) => {
    function fallbackInput() {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = '.sntn,.json';
        input.onchange = function(e) {
            var file = e.target.files[0];
            if (!file) return;
            var reader = new FileReader();
            reader.onload = function(ev) { callback(file.name, ev.target.result); };
            reader.readAsText(file);
        };
        input.click();
    }

    if (typeof window !== 'undefined' && window.showOpenFilePicker) {
        window.showOpenFilePicker({
            types: [{
                description: 'Syntona files (*.sntn, *.json)',
                accept: {
                    'application/json': ['.sntn', '.json']
                }
            }],
            multiple: false
        }).then(async function(handles) {
            if (!handles || handles.length === 0) return;
            var handle = handles[0];
            try {
                var req = indexedDB.open("syntona_fs", 1);
                req.onupgradeneeded = function(e) {
                    var db = e.target.result;
                    if (!db.objectStoreNames.contains("file_handles")) {
                        db.createObjectStore("file_handles");
                    }
                };
                req.onsuccess = function(e) {
                    var db = e.target.result;
                    var tx = db.transaction("file_handles", "readwrite");
                    tx.objectStore("file_handles").put(handle, handle.name);
                };
            } catch (e) {
                console.warn("Failed to persist file handle in IndexedDB:", e);
            }
            var file = await handle.getFile();
            var text = await file.text();
            callback(handle.name, text);
        }).catch(function(err) {
            if (err.name !== 'AbortError') {
                console.error("Open file picker failed, trying fallback:", err);
                fallbackInput();
            }
        });
    } else {
        fallbackInput();
    }
}""")
private external fun jsOpenFilePicker(callback: (String, String) -> Unit)

@JsFun("""(content, defaultName, callback) => {
    function fallbackDownload() {
        var blob = new Blob([content], {type: 'application/json'});
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = defaultName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        callback(defaultName);
    }

    if (typeof window !== 'undefined' && window.showSaveFilePicker) {
        window.showSaveFilePicker({
            suggestedName: defaultName,
            types: [{
                description: 'Syntona files (*.sntn, *.json)',
                accept: {
                    'application/json': ['.sntn', '.json']
                }
            }]
        }).then(async function(handle) {
            if (!handle) return;
            try {
                var req = indexedDB.open("syntona_fs", 1);
                req.onupgradeneeded = function(e) {
                    var db = e.target.result;
                    if (!db.objectStoreNames.contains("file_handles")) {
                        db.createObjectStore("file_handles");
                    }
                };
                req.onsuccess = function(e) {
                    var db = e.target.result;
                    var tx = db.transaction("file_handles", "readwrite");
                    tx.objectStore("file_handles").put(handle, handle.name);
                };
            } catch (e) {
                console.warn("Failed to persist save file handle in IndexedDB:", e);
            }
            var writable = await handle.createWritable();
            await writable.write(content);
            await writable.close();
            callback(handle.name);
        }).catch(function(err) {
            if (err.name !== 'AbortError') {
                console.error("Save file picker failed, downloading instead:", err);
                fallbackDownload();
            }
        });
    } else {
        fallbackDownload();
    }
}""")
private external fun jsSaveFileWithPicker(content: String, defaultName: String, callback: (String) -> Unit)

@JsFun("""(content, filename) => {
    function fallbackDownload() {
        var blob = new Blob([content], {type: 'text/plain'});
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    try {
        var req = indexedDB.open("syntona_fs", 1);
        req.onupgradeneeded = function(e) {
            var db = e.target.result;
            if (!db.objectStoreNames.contains("file_handles")) {
                db.createObjectStore("file_handles");
            }
        };
        req.onsuccess = function(e) {
            var db = e.target.result;
            if (!db.objectStoreNames.contains("file_handles")) {
                fallbackDownload();
                return;
            }
            var tx = db.transaction("file_handles", "readonly");
            var key = filename.split('/').pop().split('\\').pop();
            var getReq = tx.objectStore("file_handles").get(key);
            getReq.onsuccess = async function() {
                var handle = getReq.result;
                if (!handle) {
                    fallbackDownload();
                    return;
                }
                try {
                    var perm = await handle.queryPermission({ mode: 'readwrite' });
                    if (perm !== 'granted') {
                        perm = await handle.requestPermission({ mode: 'readwrite' });
                    }
                    if (perm === 'granted') {
                        var writable = await handle.createWritable();
                        await writable.write(content);
                        await writable.close();
                        return;
                    }
                } catch (err) {
                    console.warn("Direct write to disk handle failed, downloading instead:", err);
                }
                fallbackDownload();
            };
            getReq.onerror = function() { fallbackDownload(); };
        };
        req.onerror = function() { fallbackDownload(); };
    } catch (e) {
        fallbackDownload();
    }
}""")
private external fun jsSaveFileDirect(content: String, filename: String)

@JsFun("""(path, callback) => {
    var key = path.split('/').pop().split('\\').pop();
    try {
        var req = indexedDB.open("syntona_fs", 1);
        req.onupgradeneeded = function(e) {
            var db = e.target.result;
            if (!db.objectStoreNames.contains("file_handles")) {
                db.createObjectStore("file_handles");
            }
        };
        req.onsuccess = function(e) {
            var db = e.target.result;
            if (!db.objectStoreNames.contains("file_handles")) {
                console.warn("No file_handles store found in IndexedDB");
                callback(null);
                return;
            }
            var tx = db.transaction("file_handles", "readonly");
            var getReq = tx.objectStore("file_handles").get(key);
            getReq.onsuccess = async function() {
                var handle = getReq.result;
                if (!handle) {
                    console.warn("No FileSystemFileHandle found in IndexedDB for:", key);
                    callback(null);
                    return;
                }
                try {
                    var perm = await handle.queryPermission({ mode: 'read' });
                    if (perm !== 'granted') {
                        perm = await handle.requestPermission({ mode: 'read' });
                    }
                    if (perm !== 'granted') {
                        console.warn("Permission denied by user to read file from disk:", key);
                        callback(null);
                        return;
                    }
                    var file = await handle.getFile();
                    var text = await file.text();
                    callback(text);
                } catch (err) {
                    console.error("Error reading file directly from disk:", key, err);
                    callback(null);
                }
            };
            getReq.onerror = function(err) {
                console.error("IndexedDB get error for key:", key, err);
                callback(null);
            };
        };
        req.onerror = function(err) {
            console.error("IndexedDB open error:", err);
            callback(null);
        };
    } catch (err) {
        console.error("Failed to access IndexedDB:", err);
        callback(null);
    }
}""")
private external fun jsReadRecentFileFromDisk(path: String, callback: (String?) -> Unit)

actual fun showOpenFileDialog(onResult: (path: String, content: String) -> Unit) {
    jsOpenFilePicker(onResult)
}

actual fun showSaveFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {
    jsSaveFileWithPicker(content, defaultName, onSaved)
}

@JsFun("""(defaultName, extension, dialogTitle, callback) => {
    var cleanExt = extension.replace(/^\./, '');
    var initialName = defaultName.endsWith('.' + cleanExt) ? defaultName : defaultName + '.' + cleanExt;

    function fallback() {
        callback(initialName);
    }

    if (typeof window !== 'undefined' && window.showSaveFilePicker) {
        var desc = 'Source files (*.' + cleanExt + ')';
        if (cleanExt === 'kt') {
            desc = 'Kotlin source files (*.kt)';
        } else if (cleanExt === 'java') {
            desc = 'Java source files (*.java)';
        }
        var acceptTypes = {};
        acceptTypes['text/plain'] = ['.' + cleanExt];

        window.showSaveFilePicker({
            suggestedName: initialName,
            types: [{
                description: desc,
                accept: acceptTypes
            }]
        }).then(async function(handle) {
            if (!handle) return;
            try {
                var req = indexedDB.open("syntona_fs", 1);
                req.onupgradeneeded = function(e) {
                    var db = e.target.result;
                    if (!db.objectStoreNames.contains("file_handles")) {
                        db.createObjectStore("file_handles");
                    }
                };
                req.onsuccess = function(e) {
                    var db = e.target.result;
                    var tx = db.transaction("file_handles", "readwrite");
                    tx.objectStore("file_handles").put(handle, handle.name);
                    tx.oncomplete = function() {
                        callback(handle.name);
                    };
                    tx.onerror = function(err) {
                        console.warn("Failed to store export handle in IndexedDB:", err);
                        callback(handle.name);
                    };
                };
                req.onerror = function(err) {
                    console.warn("Failed to open IndexedDB for export:", err);
                    callback(handle.name);
                };
            } catch (e) {
                console.warn("Failed to persist export file handle in IndexedDB:", e);
                callback(handle.name);
            }
        }).catch(function(err) {
            if (err.name !== 'AbortError') {
                console.error("Export save file picker failed, falling back:", err);
                fallback();
            }
        });
    } else {
        fallback();
    }
}""")
private external fun jsExportSourceFilePicker(
    defaultName: String,
    extension: String,
    dialogTitle: String,
    callback: (String) -> Unit
)

actual fun showExportSourceFileDialog(
    defaultName: String,
    extension: String,
    dialogTitle: String,
    onChosen: (path: String) -> Unit
) {
    jsExportSourceFilePicker(defaultName, extension, dialogTitle, onChosen)
}

actual fun isSaveAsResourceAvailable(): Boolean = false

actual fun showSaveAsResourceFileDialog(content: String, defaultName: String, onSaved: (path: String) -> Unit) {}

actual fun saveFileDirect(path: String, content: String) {
    jsSaveFileDirect(content, path)
}

actual fun saveResourceFileDirect(relativePath: String, content: String) {}

actual fun readRecentPatchFile(path: String, onResult: (content: String?) -> Unit) {
    jsReadRecentFileFromDisk(path, onResult)
}

private val binaryFileCache = mutableMapOf<String, ByteArray>()

fun cacheBinaryFile(path: String, bytes: ByteArray) {
    binaryFileCache[path] = bytes
    val key = path.substringAfterLast('/').substringAfterLast('\\')
    binaryFileCache[key] = bytes
}

@JsFun("""(defaultName, callback) => {
    var cleanName = defaultName.endsWith('.wav') ? defaultName : defaultName + '.wav';

    function fallback() {
        callback(cleanName);
    }

    if (typeof window !== 'undefined' && window.showSaveFilePicker) {
        window.showSaveFilePicker({
            suggestedName: cleanName,
            types: [{
                description: 'WAV audio files (*.wav)',
                accept: {
                    'audio/wav': ['.wav']
                }
            }]
        }).then(async function(handle) {
            if (!handle) return;
            try {
                window._syntona_file_handles = window._syntona_file_handles || {};
                window._syntona_file_handles[handle.name] = handle;

                var req = indexedDB.open("syntona_fs", 1);
                req.onupgradeneeded = function(e) {
                    var db = e.target.result;
                    if (!db.objectStoreNames.contains("file_handles")) {
                        db.createObjectStore("file_handles");
                    }
                };
                req.onsuccess = function(e) {
                    var db = e.target.result;
                    var tx = db.transaction("file_handles", "readwrite");
                    tx.objectStore("file_handles").put(handle, handle.name);
                    tx.oncomplete = function() {
                        callback(handle.name);
                    };
                    tx.onerror = function(err) {
                        console.warn("Failed to store wav handle in IndexedDB:", err);
                        callback(handle.name);
                    };
                };
                req.onerror = function(err) {
                    console.warn("Failed to open IndexedDB for wav save:", err);
                    callback(handle.name);
                };
            } catch (e) {
                console.warn("Failed to persist wav file handle:", e);
                callback(handle.name);
            }
        }).catch(function(err) {
            if (err.name !== 'AbortError') {
                console.error("WAV save file picker failed, falling back:", err);
                fallback();
            }
        });
    } else {
        fallback();
    }
}""")
private external fun jsChooseWavRecordFile(defaultName: String, callback: (String) -> Unit)

actual fun chooseWavRecordFile(defaultName: String, onSelected: (path: String) -> Unit) {
    jsChooseWavRecordFile(defaultName, onSelected)
}

@JsFun("""(callback) => {
    function handleFile(file) {
        var reader = new FileReader();
        reader.onload = function(ev) {
            var array = new Int8Array(ev.target.result);
            callback(file.name, array);
        };
        reader.readAsArrayBuffer(file);
    }

    function fallbackInput() {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = '.wav,audio/wav';
        input.onchange = function(e) {
            var file = e.target.files[0];
            if (!file) return;
            handleFile(file);
        };
        input.click();
    }

    if (typeof window !== 'undefined' && window.showOpenFilePicker) {
        window.showOpenFilePicker({
            types: [{
                description: 'WAV audio files (*.wav)',
                accept: {
                    'audio/wav': ['.wav']
                }
            }],
            multiple: false
        }).then(async function(handles) {
            if (!handles || handles.length === 0) return;
            var handle = handles[0];
            try {
                window._syntona_file_handles = window._syntona_file_handles || {};
                window._syntona_file_handles[handle.name] = handle;

                var req = indexedDB.open("syntona_fs", 1);
                req.onupgradeneeded = function(e) {
                    var db = e.target.result;
                    if (!db.objectStoreNames.contains("file_handles")) {
                        db.createObjectStore("file_handles");
                    }
                };
                req.onsuccess = function(e) {
                    var db = e.target.result;
                    var tx = db.transaction("file_handles", "readwrite");
                    tx.objectStore("file_handles").put(handle, handle.name);
                };
            } catch (e) {
                console.warn("Failed to persist opened wav handle in IndexedDB:", e);
            }
            var file = await handle.getFile();
            var buffer = await file.arrayBuffer();
            var array = new Int8Array(buffer);
            callback(handle.name, array);
        }).catch(function(err) {
            if (err.name !== 'AbortError') {
                console.error("Open WAV file picker failed, trying fallback:", err);
                fallbackInput();
            }
        });
    } else {
        fallbackInput();
    }
}""")
private external fun jsOpenWavFilePicker(callback: (String, Int8Array) -> Unit)

actual fun chooseWavOpenFile(onSelected: (path: String) -> Unit) {
    jsOpenWavFilePicker { fileName, jsArray ->
        val bytes = ByteArray(jsArray.length)
        for (i in 0 until jsArray.length) {
            bytes[i] = jsArray[i]
        }
        cacheBinaryFile(fileName, bytes)
        onSelected(fileName)
    }
}

actual fun readFileDirect(path: String): String? = null

actual fun readBinaryFileDirect(path: String): ByteArray? {
    val key = path.substringAfterLast('/').substringAfterLast('\\')
    return binaryFileCache[path] ?: binaryFileCache[key]
}

actual fun readResourceFileDirect(relativePath: String): String? {
    val stem = relativePath.substringAfterLast('/').substringBeforeLast(".json").substringBeforeLast(SYNTONA_FILE_SUFFIX)
    return com.syntona.dataflow.registry.InstrumentRegistry.instrumentJsonCache[stem]
        ?: com.syntona.dataflow.registry.SoundRegistry.soundJsonCache[stem]
}


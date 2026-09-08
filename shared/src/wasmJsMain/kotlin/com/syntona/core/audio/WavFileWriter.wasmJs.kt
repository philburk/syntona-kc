@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona.core.audio

import org.khronos.webgl.Int8Array
import org.khronos.webgl.set

@JsFun("""(array, filename) => {
    function fallbackDownload() {
        var blob = new Blob([array], {type: 'audio/wav'});
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    var key = filename.split('/').pop().split('\\').pop();
    var handle = (typeof window !== 'undefined' && window._syntona_file_handles && window._syntona_file_handles[key]) || null;
    if (handle) {
        (async function() {
            try {
                var perm = await handle.queryPermission({ mode: 'readwrite' });
                if (perm !== 'granted') {
                    perm = await handle.requestPermission({ mode: 'readwrite' });
                }
                if (perm === 'granted') {
                    var writable = await handle.createWritable();
                    await writable.write(array);
                    await writable.close();
                    return;
                }
            } catch (err) {
                console.warn("Direct write to wav file handle failed, downloading instead:", err);
            }
            fallbackDownload();
        })();
        return;
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
            var getReq = tx.objectStore("file_handles").get(key);
            getReq.onsuccess = async function() {
                var idbHandle = getReq.result;
                if (!idbHandle) {
                    fallbackDownload();
                    return;
                }
                try {
                    var perm = await idbHandle.queryPermission({ mode: 'readwrite' });
                    if (perm !== 'granted') {
                        perm = await idbHandle.requestPermission({ mode: 'readwrite' });
                    }
                    if (perm === 'granted') {
                        var writable = await idbHandle.createWritable();
                        await writable.write(array);
                        await writable.close();
                        return;
                    }
                } catch (err) {
                    console.warn("Direct write to IndexedDB wav handle failed, downloading instead:", err);
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
private external fun jsSaveOrDownloadWav(array: Int8Array, filename: String)

actual class WavFileWriter actual constructor(
    private val filePath: String,
    private val sampleRate: Int,
    private val numChannels: Int
) {
    private val chunks = mutableListOf<ByteArray>()
    private var totalBytesWritten = 0

    actual fun writeSamples(samples: FloatArray, count: Int) {
        val bytes = ByteArray(count * 3)
        var byteIdx = 0
        for (i in 0 until count) {
            val sample = samples[i]
            val intVal = (sample * 8388607.0).toInt().coerceIn(-8388608, 8388607)
            bytes[byteIdx++] = (intVal and 0xFF).toByte()
            bytes[byteIdx++] = ((intVal shr 8) and 0xFF).toByte()
            bytes[byteIdx++] = ((intVal shr 16) and 0xFF).toByte()
        }
        chunks.add(bytes)
        totalBytesWritten += bytes.size
    }

    actual fun close() {
        // Build the WAV header
        val header = ByteArray(44)

        // Helper to write ASCII
        fun writeAscii(offset: Int, str: String) {
            for (i in 0..3) {
                header[offset + i] = str[i].code.toByte()
            }
        }

        // Helper to write Int (Little-Endian)
        fun writeInt(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            header[offset + 2] = ((value shr 16) and 0xFF).toByte()
            header[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        // Helper to write Short (Little-Endian)
        fun writeShort(offset: Int, value: Short) {
            header[offset] = (value.toInt() and 0xFF).toByte()
            header[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }

        val bytesPerSample = 3
        val blockAlign = numChannels * bytesPerSample
        val byteRate = sampleRate * blockAlign
        val isOdd = (totalBytesWritten and 1) == 1
        val paddedSize = totalBytesWritten + if (isOdd) 1 else 0

        writeAscii(0, "RIFF")
        writeInt(4, paddedSize + 36)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeInt(16, 16) // Subchunk1Size
        writeShort(20, 1.toShort()) // AudioFormat = 1 (PCM)
        writeShort(22, numChannels.toShort()) // NumChannels
        writeInt(24, sampleRate)
        writeInt(28, byteRate) // ByteRate
        writeShort(32, blockAlign.toShort()) // BlockAlign
        writeShort(34, 24.toShort()) // BitsPerSample = 24
        writeAscii(36, "data")
        writeInt(40, totalBytesWritten)

        // Concatenate header and all chunks into one large JS Int8Array
        val totalSize = 44 + paddedSize
        val jsArray = Int8Array(totalSize)

        // Copy header
        for (i in 0 until 44) {
            jsArray[i] = header[i]
        }

        // Copy chunks
        var writeIdx = 44
        for (chunk in chunks) {
            for (b in chunk) {
                jsArray[writeIdx++] = b
            }
        }
        if (isOdd) {
            jsArray[writeIdx] = 0
        }

        val fullWavBytes = ByteArray(totalSize)
        for (i in 0 until 44) {
            fullWavBytes[i] = header[i]
        }
        var writeIdx2 = 44
        for (chunk in chunks) {
            for (b in chunk) {
                fullWavBytes[writeIdx2++] = b
            }
        }
        if (isOdd) {
            fullWavBytes[writeIdx2] = 0
        }
        com.syntona.dataflow.editor.cacheBinaryFile(filePath, fullWavBytes)

        // Save to file handle or download the file!
        jsSaveOrDownloadWav(jsArray, filePath)
    }
}

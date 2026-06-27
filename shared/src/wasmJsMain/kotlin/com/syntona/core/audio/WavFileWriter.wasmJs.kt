@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.syntona.core.audio

import org.khronos.webgl.Int8Array
import org.khronos.webgl.set

@JsFun("""(array, filename) => {
    var blob = new Blob([array], {type: 'audio/wav'});
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}""")
private external fun jsDownloadWav(array: Int8Array, filename: String)

actual class WavFileWriter actual constructor(private val filePath: String, private val sampleRate: Int) {
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

        writeAscii(0, "RIFF")
        writeInt(4, totalBytesWritten + 36)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeInt(16, 16) // Subchunk1Size
        writeShort(20, 1.toShort()) // AudioFormat = 1 (PCM)
        writeShort(22, 2.toShort()) // NumChannels = 2
        writeInt(24, sampleRate)
        writeInt(28, sampleRate * 6) // ByteRate
        writeShort(32, 6.toShort()) // BlockAlign
        writeShort(34, 24.toShort()) // BitsPerSample = 24
        writeAscii(36, "data")
        writeInt(40, totalBytesWritten)

        // Concatenate header and all chunks into one large JS Int8Array
        val totalSize = 44 + totalBytesWritten
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

        // Download the file!
        jsDownloadWav(jsArray, filePath)
    }
}

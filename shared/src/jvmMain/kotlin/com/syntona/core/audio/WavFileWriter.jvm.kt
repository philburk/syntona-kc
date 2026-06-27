package com.syntona.core.audio

import java.io.RandomAccessFile
import java.io.File

actual class WavFileWriter actual constructor(filePath: String, sampleRate: Int) {
    private val file = RandomAccessFile(filePath, "rw")
    private var totalBytesWritten = 0

    init {
        // Write standard 44-byte WAV header placeholders
        writeAscii("RIFF")
        file.writeInt(0) // ChunkSize placeholder (will backpatch)
        writeAscii("WAVE")
        writeAscii("fmt ")
        file.writeInt(Integer.reverseBytes(16)) // Subchunk1Size
        file.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // AudioFormat = 1 (PCM)
        file.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt()) // NumChannels = 2
        file.writeInt(Integer.reverseBytes(sampleRate))
        file.writeInt(Integer.reverseBytes(sampleRate * 6)) // ByteRate
        file.writeShort(java.lang.Short.reverseBytes(6.toShort()).toInt()) // BlockAlign
        file.writeShort(java.lang.Short.reverseBytes(24.toShort()).toInt()) // BitsPerSample = 24
        writeAscii("data")
        file.writeInt(0) // Subchunk2Size placeholder (will backpatch)
    }

    private fun writeAscii(str: String) {
        val bytes = ByteArray(4)
        for (i in 0..3) {
            bytes[i] = str[i].code.toByte()
        }
        file.write(bytes)
    }

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
        file.write(bytes)
        totalBytesWritten += bytes.size
    }

    actual fun close() {
        try {
            // Backpatch header
            // 1. ChunkSize at offset 4: totalBytesWritten + 36
            file.seek(4)
            file.writeInt(Integer.reverseBytes(totalBytesWritten + 36))
            // 2. Subchunk2Size at offset 40: totalBytesWritten
            file.seek(40)
            file.writeInt(Integer.reverseBytes(totalBytesWritten))
        } finally {
            file.close()
        }
    }
}

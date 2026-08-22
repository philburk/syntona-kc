package com.syntona.core.audio

import java.io.File
import java.io.RandomAccessFile

actual class WavFileWriter actual constructor(
    filePath: String,
    sampleRate: Int,
    private val numChannels: Int
) {
    private val file: RandomAccessFile
    private var totalBytesWritten = 0

    init {
        val target = File(filePath)
        target.parentFile?.mkdirs()
        file = RandomAccessFile(target, "rw")
        file.setLength(0)

        val bytesPerSample = 3 // 24-bit PCM
        val blockAlign = numChannels * bytesPerSample
        val byteRate = sampleRate * blockAlign

        // Write standard 44-byte WAV header placeholders
        writeAscii("RIFF")
        file.writeInt(0) // ChunkSize placeholder (will backpatch)
        writeAscii("WAVE")
        writeAscii("fmt ")
        file.writeInt(Integer.reverseBytes(16)) // Subchunk1Size
        file.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // AudioFormat = 1 (PCM)
        file.writeShort(java.lang.Short.reverseBytes(numChannels.toShort()).toInt()) // NumChannels
        file.writeInt(Integer.reverseBytes(sampleRate))
        file.writeInt(Integer.reverseBytes(byteRate)) // ByteRate
        file.writeShort(java.lang.Short.reverseBytes(blockAlign.toShort()).toInt()) // BlockAlign
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
            // RIFF chunks must be padded to an even number of bytes
            val isOdd = (totalBytesWritten and 1) == 1
            if (isOdd) {
                file.writeByte(0)
            }
            // Backpatch header
            // 1. ChunkSize at offset 4: (totalBytesWritten + pad) + 36
            file.seek(4)
            val paddedSize = totalBytesWritten + if (isOdd) 1 else 0
            file.writeInt(Integer.reverseBytes(paddedSize + 36))
            // 2. Subchunk2Size at offset 40: totalBytesWritten
            file.seek(40)
            file.writeInt(Integer.reverseBytes(totalBytesWritten))
        } finally {
            file.close()
        }
    }
}

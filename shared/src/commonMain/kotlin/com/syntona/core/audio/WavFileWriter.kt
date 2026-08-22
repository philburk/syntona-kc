package com.syntona.core.audio

expect class WavFileWriter(filePath: String, sampleRate: Int, numChannels: Int = 2) {
    fun writeSamples(samples: FloatArray, count: Int)
    fun close()
}

fun writeWavFile(filePath: String, samples: FloatArray, sampleRate: Int, numChannels: Int = 1) {
    val writer = WavFileWriter(filePath, sampleRate, numChannels)
    try {
        writer.writeSamples(samples, samples.size)
    } finally {
        writer.close()
    }
}

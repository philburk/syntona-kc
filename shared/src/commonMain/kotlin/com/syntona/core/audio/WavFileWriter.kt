package com.syntona.core.audio

expect class WavFileWriter(filePath: String, sampleRate: Int) {
    fun writeSamples(samples: FloatArray, count: Int)
    fun close()
}

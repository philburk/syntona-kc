package com.syntona.core.audio

actual class WavFileWriter actual constructor(
    filePath: String,
    sampleRate: Int,
    numChannels: Int
) {
    actual fun writeSamples(samples: FloatArray, count: Int) {}
    actual fun close() {}
}

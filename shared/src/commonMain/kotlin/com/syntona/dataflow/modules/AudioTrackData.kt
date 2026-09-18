package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.util.SegmentedFloatArray

/**
 * Caches minimum and maximum audio sample values for blocks of frames to optimize waveform rendering.
 */
class AudioThumbnail(val blockSize: Int = 256) {
    var minValues: FloatArray = FloatArray(0)
        private set
    var maxValues: FloatArray = FloatArray(0)
        private set
    var blockCount: Int by mutableStateOf(0)
        private set

    private var lastProcessedFrame: Int = 0

    fun clear() {
        minValues = FloatArray(0)
        maxValues = FloatArray(0)
        lastProcessedFrame = 0
        blockCount = 0
    }

    fun copyFrom(other: AudioThumbnail) {
        minValues = other.minValues.copyOf()
        maxValues = other.maxValues.copyOf()
        lastProcessedFrame = other.lastProcessedFrame
        blockCount = other.blockCount
    }

    /**
     * Updates thumbnail cache from [recording] up to [maxFrames].
     * Computes and caches min/max sample values for each [blockSize] chunk.
     */
    fun updateFrom(recording: SegmentedFloatArray?, maxFrames: Int = recording?.length ?: 0) {
        if (recording == null) {
            if (blockCount > 0) clear()
            return
        }
        val limit = minOf(recording.length, maxFrames)
        if (limit <= lastProcessedFrame) return

        val newFrames = limit - lastProcessedFrame
        val additionalBlocks = (newFrames + blockSize - 1) / blockSize
        val reqCap = blockCount + additionalBlocks
        var minArr = minValues
        var maxArr = maxValues
        if (reqCap > minArr.size) {
            val newCap = maxOf(reqCap, minArr.size * 2, 64)
            minArr = minArr.copyOf(newCap)
            maxArr = maxArr.copyOf(newCap)
        }

        val chunkBuf = FloatArray(blockSize)
        var frameIdx = lastProcessedFrame
        var bIdx = blockCount
        while (frameIdx < limit) {
            val chunkSize = minOf(blockSize, limit - frameIdx)
            recording.read(frameIdx, chunkBuf, 0, chunkSize)
            var minVal = chunkBuf[0]
            var maxVal = chunkBuf[0]
            for (i in 1 until chunkSize) {
                val v = chunkBuf[i]
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }
            minArr[bIdx] = minVal
            maxArr[bIdx] = maxVal
            bIdx++
            frameIdx += chunkSize
        }

        minValues = minArr
        maxValues = maxArr
        lastProcessedFrame = frameIdx
        blockCount = bIdx
    }
}

/**
 * Section data payload for an [AudioTrack].
 * Holds the recorded or loaded [SegmentedFloatArray], relative WAV file path, and cached [AudioThumbnail].
 */
class AudioSectionData(
    var recording: SegmentedFloatArray? = null,
    var wavFileName: String? = null
) {
    val thumbnail = AudioThumbnail()
    var isLoadedFromFile: Boolean = false

    init {
        recording?.let { thumbnail.updateFrom(it) }
    }

    fun updateThumbnail(writtenFrames: Int = recording?.length ?: 0) {
        thumbnail.updateFrom(recording, writtenFrames)
    }

    fun copy(): AudioSectionData {
        val copyData = AudioSectionData(recording, wavFileName)
        copyData.isLoadedFromFile = isLoadedFromFile
        copyData.thumbnail.copyFrom(thumbnail)
        return copyData
    }
}

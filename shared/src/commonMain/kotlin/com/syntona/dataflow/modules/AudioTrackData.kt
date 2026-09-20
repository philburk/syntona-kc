package com.syntona.dataflow.modules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syntona.util.SegmentedFloatArray

/**
 * Caches minimum and maximum audio sample values for blocks of frames to optimize waveform rendering.
 */
class AudioThumbnail(val blockSize: Int = 256) {

    /**
     * One consistent view of the cache. The arrays and the count are published together, because a
     * view drawing on another thread must never see a count that its arrays are too short for.
     * Slots past [count] are scratch space that readers ignore, so appending needs no copy.
     */
    class Blocks(
        val minValues: FloatArray,
        val maxValues: FloatArray,
        val count: Int,
        val processedFrames: Int
    )

    /** Read this once per draw and use it alone; it is replaced, never modified, on every update. */
    var blocks: Blocks by mutableStateOf(EMPTY)
        private set

    val blockCount: Int get() = blocks.count
    val minValues: FloatArray get() = blocks.minValues
    val maxValues: FloatArray get() = blocks.maxValues

    fun clear() {
        blocks = EMPTY
    }

    fun copyFrom(other: AudioThumbnail) {
        val source = other.blocks
        blocks = Blocks(
            source.minValues.copyOf(),
            source.maxValues.copyOf(),
            source.count,
            source.processedFrames
        )
    }

    /**
     * Updates thumbnail cache from [recording] up to [maxFrames].
     * Computes and caches min/max sample values for each [blockSize] chunk.
     */
    fun updateFrom(recording: SegmentedFloatArray?, maxFrames: Int = recording?.length ?: 0) {
        val current = blocks
        if (recording == null) {
            if (current.count > 0) clear()
            return
        }
        val limit = minOf(recording.length, maxFrames)
        if (limit <= current.processedFrames) return

        val newFrames = limit - current.processedFrames
        val additionalBlocks = (newFrames + blockSize - 1) / blockSize
        val reqCap = current.count + additionalBlocks
        var minArr = current.minValues
        var maxArr = current.maxValues
        if (reqCap > minArr.size) {
            val newCap = maxOf(reqCap, minArr.size * 2, 64)
            minArr = minArr.copyOf(newCap)
            maxArr = maxArr.copyOf(newCap)
        }

        val chunkBuf = FloatArray(blockSize)
        var frameIdx = current.processedFrames
        var bIdx = current.count
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

        // Published last, so the new count is never visible before the values it counts
        blocks = Blocks(minArr, maxArr, bIdx, frameIdx)
    }

    private companion object {
        val EMPTY = Blocks(FloatArray(0), FloatArray(0), 0, 0)
    }
}

/**
 * Audio shared by one or more sections: the recorded or loaded [SegmentedFloatArray], the WAV file
 * it is saved in, and the cached [AudioThumbnail]. Copying a section with Option-drag shares this
 * rather than duplicating the samples, so the copies play, save and load as one clip.
 */
class AudioSectionData(
    var recording: SegmentedFloatArray? = null,
    var wavFileName: String? = null
) {
    val thumbnail = AudioThumbnail()
    var isLoadedFromFile: Boolean = false

    /**
     * The section this audio belongs to. Other sections sharing it are aliases, drawn dimmed and
     * not recorded into. Null until the audio is shared, and moved on when the owner is deleted.
     */
    var ownerSection: TrackSection? by mutableStateOf(null)

    /** True when [section] is an alias, i.e. it shares audio that belongs to another section. */
    fun isAliasFor(section: TrackSection): Boolean {
        val owner = ownerSection
        return owner != null && owner !== section
    }

    /** Absolute path where this audio is known to exist, so a save can skip writing it again. */
    var savedWavPath: String? = null

    /** True when the audio changed since it was last written, e.g. after recording. */
    var needsWavWrite: Boolean = false

    init {
        recording?.let { thumbnail.updateFrom(it) }
    }

    fun updateThumbnail(writtenFrames: Int = recording?.length ?: 0) {
        thumbnail.updateFrom(recording, writtenFrames)
    }

    fun copy(): AudioSectionData {
        val copyData = AudioSectionData(recording, wavFileName)
        copyData.isLoadedFromFile = isLoadedFromFile
        // A deep copy is independent audio, not an alias, and needs a file of its own
        copyData.savedWavPath = null
        copyData.needsWavWrite = recording != null
        copyData.thumbnail.copyFrom(thumbnail)
        return copyData
    }
}

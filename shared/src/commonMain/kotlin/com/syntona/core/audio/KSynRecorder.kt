package com.syntona.core.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.softsynth.ksyn.io.AudioFifo
import kotlinx.coroutines.*

object KSynRecorder {
    var isRecording by mutableStateOf(false)
        private set
    var durationText by mutableStateOf("00:00:000")
        private set
    var maxDurationMinutes by mutableStateOf(3)
    var hasSkippedSamples by mutableStateOf(false)
    var isRecordingActive by mutableStateOf(false)

    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startRecording(filePath: String) {
        if (isRecording) return

        hasSkippedSamples = false
        isRecordingActive = false

        val sampleRate = SynthKSyn.ksynAudioBridge.audioBridge.getSampleRate()
        val fifo = AudioFifo().apply {
            allocate(131072) // 128k samples
            isReadWaitEnabled = false
            isWriteWaitEnabled = false
        }

        SynthKSyn.ksynAudioBridge.recordingFifo = fifo
        isRecording = true

        durationText = "00:00:000"

        recordingJob = scope.launch {
            val writer = WavFileWriter(filePath, sampleRate)
            val readBuffer = FloatArray(4096)
            var totalSamplesWritten = 0L
            val maxSamples = maxDurationMinutes * 60L * sampleRate * 2L // 2 channels (stereo)

            try {
                while (isActive && isRecording) {
                    val available = fifo.available()
                    if (available > 0) {
                        val countToRead = minOf(available, readBuffer.size)
                        val readCount = fifo.read(readBuffer, 0, countToRead)
                        if (readCount > 0) {
                            writer.writeSamples(readBuffer, readCount)
                            totalSamplesWritten += readCount
                            isRecordingActive = true

                            val durationMillis = ((totalSamplesWritten / 2.0) / sampleRate * 1000).toLong()
                            durationText = formatDuration(durationMillis)

                            if (totalSamplesWritten >= maxSamples) {
                                break
                            }
                        }
                    } else {

                        delay(20)
                    }
                    yield()
                }
            } catch (e: Exception) {
                println("Error during recording: ${e.message}")
            } finally {
                SynthKSyn.ksynAudioBridge.recordingFifo = null

                // Drain the remaining samples
                var remaining = fifo.available()
                while (remaining > 0) {
                    val countToRead = minOf(remaining, readBuffer.size)
                    val readCount = fifo.read(readBuffer, 0, countToRead)
                    if (readCount <= 0) break
                    writer.writeSamples(readBuffer, readCount)
                    remaining = fifo.available()
                    yield()
                }

                writer.close()
                isRecording = false
            }
        }
    }


    fun stopRecording() {
        isRecording = false
    }

    private fun formatDuration(millis: Long): String {
        val min = millis / 60000
        val sec = (millis % 60000) / 1000
        val ms = millis % 1000
        return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}:${ms.toString().padStart(3, '0')}"
    }
}

/*
 * Copyright 2025 Phil Burk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.syntona.core.audio

import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import com.softsynth.audiobridge.writeSuspending
import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.io.AudioFifo
import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

enum class DuplexMode {
    IDLE,
    PREPARING,
    STABLE
}

class KSynAudioBridge(val synth: Synthesizer): AudioStreamManager() {
    val STEREO_CHANNELS = 2

    @Volatile
    var recordingFifo: AudioFifo? = null

    @Volatile
    var duplexMode: DuplexMode = DuplexMode.IDLE
        private set

    override suspend fun onAudioTask(scope: CoroutineScope) {
        println("KSynAudioBridge onAudioTask started.")

        val activeInputBridge = inputBridge
        val isInputActive = (activeInputBridge != null)

        duplexMode = if (isInputActive) DuplexMode.PREPARING else DuplexMode.IDLE
        val numInputChannels = if (isInputActive) 1 else 0
        synth.start(audioBridge.getSampleRate(), numInputChannels, STEREO_CHANNELS)

        val bufferSizeFrames = audioBridge.getFramesPerBurst().let { if (it > 0) it else 64 }
        val tempInputBuffer = FloatArray(bufferSizeFrames * 4)
        val monoInputBuffer = FloatArray(bufferSizeFrames)
        val silenceInputBuffer = FloatArray(bufferSizeFrames)
        val STABLE_LOOPS_REQUIRED = 8
        var stableLoopsConsecutive = 0

        try {
            while (isActive()) { // Check isActive for cooperative cancellation
                val stereoBuffer = if (isInputActive && activeInputBridge != null) {
                    if (duplexMode == DuplexMode.PREPARING) {
                        var totalReadThisLoop = 0
                        while (isActive()) {
                            val remainingSpace = tempInputBuffer.size - totalReadThisLoop
                            if (remainingSpace <= 0) break
                            val read = activeInputBridge.read(
                                tempInputBuffer,
                                totalReadThisLoop,
                                min(bufferSizeFrames, remainingSpace)
                            )
                            if (read <= 0) break
                            totalReadThisLoop += read
                        }

                        val limit = bufferSizeFrames * 5 / 4
                        if (totalReadThisLoop <= limit) {
                            stableLoopsConsecutive++
                            if (stableLoopsConsecutive >= STABLE_LOOPS_REQUIRED) {
                                duplexMode = DuplexMode.STABLE
                            }
                        } else {
                            stableLoopsConsecutive = 0
                        }

                        synth.renderBuffer(silenceInputBuffer)
                    } else {
                        val readCount = activeInputBridge.read(monoInputBuffer, 0, bufferSizeFrames)
                        if (readCount < bufferSizeFrames && readCount >= 0) {
                            monoInputBuffer.fill(0.0f, readCount, bufferSizeFrames)
                        }
                        synth.renderBuffer(monoInputBuffer)
                    }
                } else {
                    synth.renderBuffer()
                }

                recordingFifo?.let { fifo ->
                    if (KSynRecorder.isRecordingActive && fifo.available() + stereoBuffer.size > fifo.size()) {
                        KSynRecorder.hasSkippedSamples = true
                    }
                    fifo.write(stereoBuffer)
                }

                val framesToWrite = stereoBuffer.size / STEREO_CHANNELS

                val framesWritten = audioBridge.writeSuspending(
                    stereoBuffer,
                    0,
                    framesToWrite,
                    timeoutMillis = 1000L
                )
                if (framesWritten < 0) {
                    // Handle error from audioBridge.write, e.g., stream closed
                    println("AudioBridge write error: $framesWritten")
                    scope.cancel("AudioBridge write error") // Cancel the coroutine
                    break
                } else if (framesWritten < framesToWrite) {
                    println("AudioBridge write timeout")
                    scope.cancel("AudioBridge write timeout") // Cancel the coroutine
                    break
                }
            }
        } catch (e: CancellationException) {
            println("KSynAudioBridge task cancelled.")
            // Perform any cleanup specific to this coroutine if needed
        } finally {
            println("KSynAudioBridge task exiting.")
            duplexMode = DuplexMode.IDLE
            synth.stop()
        }
    }
}

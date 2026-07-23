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

import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.audiobridge.AudioInputBridge
import com.softsynth.audiobridge.AudioOutputBridge
import com.softsynth.audiobridge.AudioPermissionState
import com.softsynth.audiobridge.AudioResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class AudioStreamManager {

    abstract suspend fun onAudioTask(scope: CoroutineScope)

    public fun isActive(): Boolean {
        return audioStreamJob?.isActive ?: false
    }

    // Keep a reference to the Job of the audio stream
    private var audioStreamJob: Job? = null
    private var isBridgeOpen = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    var context: Any? = null

    private fun startAudioStreamJob(): Job {
        audioStreamJob?.cancel()

        val job = scope.launch {
            onAudioTask(this)
        }
        audioStreamJob = job
        return job
    }

    private fun stopAudioStreamJob() {
        audioStreamJob?.cancel()
        audioStreamJob = null
        println("Requested to stop audio stream job.")
    }

    suspend fun start(context: Any? = null): AudioResult {
        this.context = context
        if (isBridgeOpen) {
            stop()
        }

        val optimalSampleRate = AudioDeviceManager.getOptimalSampleRate()
        val bufferSizeFrames = 64

        var activeInputBridge: AudioInputBridge? = null
        if (AudioInputBridge.isSupported()) {
            var permState = AudioInputBridge.getPermissionState(context)
            if (permState != AudioPermissionState.GRANTED) {
                permState = AudioInputBridge.requestPermission(context)
            }
            if (permState == AudioPermissionState.GRANTED) {
                val inputCandidate = AudioInputBridge.create {
                    sampleRate = optimalSampleRate
                    framesPerBuffer = bufferSizeFrames
                    channels = 1
                }
                val openRes = inputCandidate.open()
                if (openRes == AudioResult.OK) {
                    val startRes = inputCandidate.start()
                    if (startRes == AudioResult.OK) {
                        activeInputBridge = inputCandidate
                        println("AudioInputBridge opened and started successfully.")
                    } else {
                        inputCandidate.close()
                    }
                } else {
                    println("AudioInputBridge failed to open: $openRes")
                }
            } else {
                println("Audio permission not granted: $permState")
            }
        }
        this.inputBridge = activeInputBridge

        val outputCandidate = AudioOutputBridge.create {
            sampleRate = optimalSampleRate
            framesPerBuffer = bufferSizeFrames
        }
        val openResult = outputCandidate.open()
        if (openResult != AudioResult.OK) {
            println("Failed to open audio output bridge: $openResult")
            inputBridge?.stop()
            inputBridge?.close()
            inputBridge = null
            return openResult
        }
        println("AudioBridge opened. sampleRate = ${outputCandidate.getSampleRate()}")
        val startResult = outputCandidate.start()
        if (startResult != AudioResult.OK) {
            println("Failed to start audio output bridge: $startResult")
            outputCandidate.close()
            inputBridge?.stop()
            inputBridge?.close()
            inputBridge = null
            return startResult
        }
        this.audioBridge = outputCandidate
        isBridgeOpen = true
        println("AudioBridge opened and started.")

        startAudioStreamJob()
        return AudioResult.OK
    }

    fun stop() {
        stopAudioStreamJob()
        if (isBridgeOpen) {
            inputBridge?.stop()
            inputBridge?.close()
            inputBridge = null

            println("Audio stream coroutine finishing. Call audioBridge.stop()")
            audioBridge.stop()
            println("Audio stream coroutine finishing. Call audioBridge.close()")
            audioBridge.close()
            isBridgeOpen = false
            println("Audio stream stopped and closed.")
        }
    }

    var audioBridge = AudioOutputBridge.create()
    var inputBridge: AudioInputBridge? = null
}

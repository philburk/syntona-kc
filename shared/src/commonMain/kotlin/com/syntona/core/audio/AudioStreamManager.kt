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

    /**
     * Device ids to open, or -1 to let the platform choose. Set these before calling [start];
     * they are resolved from stored device names by the caller, since ids are not stable.
     */
    var requestedOutputDeviceId: Int = -1
    var requestedInputDeviceId: Int = -1

    /** Name of the device each stream actually opened, valid once [start] has succeeded. */
    var activeOutputDeviceName: String = ""
        private set
    var activeInputDeviceName: String = ""
        private set

    /** True if the requested device could not be opened, so the default device was opened instead. */
    var outputFellBackToDefault: Boolean = false
        private set
    var inputFellBackToDefault: Boolean = false
        private set

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
        inputFellBackToDefault = false
        outputFellBackToDefault = false

        val optimalSampleRate = AudioDeviceManager.getOptimalSampleRate()
        val bufferSizeFrames = 64

        var activeInputBridge: AudioInputBridge? = null
        if (AudioInputBridge.isSupported()) {
            var permState = AudioInputBridge.getPermissionState(context)
            if (permState != AudioPermissionState.GRANTED) {
                permState = AudioInputBridge.requestPermission(context)
            }
            if (permState == AudioPermissionState.GRANTED) {
                activeInputBridge = openInput(requestedInputDeviceId, optimalSampleRate, bufferSizeFrames)
                if (activeInputBridge == null && requestedInputDeviceId != AudioSettings.DEFAULT_DEVICE_ID) {
                    println("AudioInputBridge could not open the chosen device, trying the default.")
                    activeInputBridge = openInput(AudioSettings.DEFAULT_DEVICE_ID, optimalSampleRate, bufferSizeFrames)
                    inputFellBackToDefault = activeInputBridge != null
                }
                if (activeInputBridge != null) {
                    activeInputDeviceName = activeInputBridge.getCurrentDeviceName()
                    println("AudioInputBridge opened and started on '$activeInputDeviceName' with ${activeInputBridge.getChannelCount()} channel(s).")
                }
            } else {
                println("Audio permission not granted: $permState")
            }
        }
        this.inputBridge = activeInputBridge

        var outputResult = AudioResult.OK
        var outputCandidate = openOutput(requestedOutputDeviceId, optimalSampleRate, bufferSizeFrames) { outputResult = it }
        if (outputCandidate == null && requestedOutputDeviceId != AudioSettings.DEFAULT_DEVICE_ID) {
            println("AudioOutputBridge could not open the chosen device, trying the default.")
            outputCandidate = openOutput(AudioSettings.DEFAULT_DEVICE_ID, optimalSampleRate, bufferSizeFrames) { outputResult = it }
            outputFellBackToDefault = outputCandidate != null
        }
        if (outputCandidate == null) {
            inputBridge?.let { closeQuietly(it) }
            inputBridge = null
            activeInputDeviceName = ""
            return outputResult
        }
        this.audioBridge = outputCandidate
        activeOutputDeviceName = outputCandidate.getCurrentDeviceName()
        isBridgeOpen = true
        println("AudioBridge opened and started on '$activeOutputDeviceName'.")

        startAudioStreamJob()
        return AudioResult.OK
    }

    /** Opens and starts an input on [deviceId], in stereo if possible, else mono. Returns null if both fail. */
    private fun openInput(deviceId: Int, sampleRate: Int, framesPerBuffer: Int): AudioInputBridge? {
        for (channelCount in listOf(2, 1)) {
            val candidate = AudioInputBridge.create {
                this.sampleRate = sampleRate
                this.framesPerBuffer = framesPerBuffer
                channels = channelCount
                this.deviceId = deviceId
            }
            val openRes = guarded("AudioInputBridge open, $channelCount channel(s)") { candidate.open() }
            if (openRes != AudioResult.OK) {
                println("AudioInputBridge $channelCount channel open failed: $openRes")
                closeQuietly(candidate)
                continue
            }
            val startRes = guarded("AudioInputBridge start") { candidate.start() }
            if (startRes == AudioResult.OK) return candidate
            println("AudioInputBridge $channelCount channel start failed: $startRes")
            closeQuietly(candidate)
        }
        return null
    }

    /** Opens and starts an output on [deviceId]. Returns null on failure and reports why to [onFailure]. */
    private fun openOutput(
        deviceId: Int,
        sampleRate: Int,
        framesPerBuffer: Int,
        onFailure: (AudioResult) -> Unit
    ): AudioOutputBridge? {
        val candidate = AudioOutputBridge.create {
            this.sampleRate = sampleRate
            this.framesPerBuffer = framesPerBuffer
            this.deviceId = deviceId
        }
        val openResult = guarded("AudioOutputBridge open") { candidate.open() }
        if (openResult != AudioResult.OK) {
            println("Failed to open audio output bridge: $openResult")
            onFailure(openResult)
            closeQuietly(candidate)
            return null
        }
        println("AudioBridge opened. sampleRate = ${candidate.getSampleRate()}")
        val startResult = guarded("AudioOutputBridge start") { candidate.start() }
        if (startResult != AudioResult.OK) {
            println("Failed to start audio output bridge: $startResult")
            onFailure(startResult)
            closeQuietly(candidate)
            return null
        }
        return candidate
    }

    /**
     * Some bridges throw instead of returning an error, e.g. Java Sound throws
     * IllegalArgumentException for a format the device does not support. Treat that as a failure
     * so the caller can try another format or device instead of the exception escaping.
     */
    private inline fun guarded(what: String, action: () -> AudioResult): AudioResult =
        try {
            action()
        } catch (e: Exception) {
            println("$what threw $e")
            AudioResult.ERROR_INTERNAL
        }

    private fun closeQuietly(bridge: AudioInputBridge) {
        try {
            bridge.stop()
            bridge.close()
        } catch (e: Exception) {
            println("AudioInputBridge close threw $e")
        }
    }

    private fun closeQuietly(bridge: AudioOutputBridge) {
        try {
            bridge.close()
        } catch (e: Exception) {
            println("AudioOutputBridge close threw $e")
        }
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
            activeOutputDeviceName = ""
            activeInputDeviceName = ""
            println("Audio stream stopped and closed.")
        }
    }

    var audioBridge = AudioOutputBridge.create()
    var inputBridge: AudioInputBridge? = null
}

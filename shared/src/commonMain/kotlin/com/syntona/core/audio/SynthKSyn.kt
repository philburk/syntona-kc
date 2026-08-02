/*
 * Copyright 2023 Phil Burk, Mobileer Inc
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

import com.softsynth.audiobridge.AudioDeviceInfo
import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.instruments.SubtractiveSynthVoice
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.util.VoiceAllocator
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.WhiteNoise
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

object SynthKSyn {

    val synth = KSyn.createSynthesizer()
    val ksynAudioBridge: KSynAudioBridge = KSynAudioBridge(synth)


    /**
     * Opens audio using the devices remembered in settings, falling back to the system default
     * for any device that is no longer present.
     */
    suspend fun start(context: Any? = null) {
        AudioSettings.load()
        applyPreferredDevices()
        ksynAudioBridge.start(context)
        publishActiveDevices()
    }

    /**
     * Closes the current audio devices and opens the ones now selected in [AudioSettings].
     * Used when the user picks a different device in Audio Settings.
     */
    suspend fun restart(context: Any? = null) {
        ksynAudioBridge.stop()
        applyPreferredDevices()
        ksynAudioBridge.start(context ?: ksynAudioBridge.context)
        publishActiveDevices()
    }

    /**
     * Turns the remembered device names into ids for the streams to open. Names are resolved
     * against the device list as it is right now, so a device that has been unplugged since the
     * preference was saved resolves to the default instead.
     */
    private suspend fun applyPreferredDevices() {
        // Nothing to resolve unless the user actually picked a device, and asking for the device
        // list is not free: in a browser it can block until microphone permission is granted.
        // Skipping it here keeps the common case - no preference stored - starting immediately.
        if (AudioSettings.preferredOutputName.isNullOrEmpty() &&
            AudioSettings.preferredInputName.isNullOrEmpty()
        ) {
            ksynAudioBridge.requestedOutputDeviceId = AudioSettings.DEFAULT_DEVICE_ID
            ksynAudioBridge.requestedInputDeviceId = AudioSettings.DEFAULT_DEVICE_ID
            AudioSettings.outputFellBackToDefault = false
            AudioSettings.inputFellBackToDefault = false
            return
        }
        val outputs = currentOutputDevices()
        val inputs = currentInputDevices()
        ksynAudioBridge.requestedOutputDeviceId =
            AudioSettings.resolveDeviceId(AudioSettings.preferredOutputName, outputs)
        ksynAudioBridge.requestedInputDeviceId =
            AudioSettings.resolveDeviceId(AudioSettings.preferredInputName, inputs)
        AudioSettings.outputFellBackToDefault =
            AudioSettings.isDeviceMissing(AudioSettings.preferredOutputName, outputs)
        AudioSettings.inputFellBackToDefault =
            AudioSettings.isDeviceMissing(AudioSettings.preferredInputName, inputs)
    }

    private fun publishActiveDevices() {
        AudioSettings.activeOutputName = ksynAudioBridge.activeOutputDeviceName
        AudioSettings.activeInputName = ksynAudioBridge.activeInputDeviceName
    }

    /**
     * A snapshot of the device list, which is normally exposed only as a Flow.
     *
     * Bounded by a timeout because that Flow is not guaranteed to emit. In a browser the device
     * list is empty until microphone permission is granted, and the Flow only emits when the list
     * changes - so on Safari, which reports no devices at all beforehand, it emits nothing and
     * waiting on it would hang audio startup completely. Falling back to an empty list simply
     * means the default device is used, which is the right answer when nothing is enumerable.
     */
    suspend fun currentOutputDevices(): List<AudioDeviceInfo> =
        withTimeoutOrNull(DEVICE_QUERY_TIMEOUT_MILLIS) {
            AudioDeviceManager.outputDevices.firstOrNull()
        } ?: emptyList()

    suspend fun currentInputDevices(): List<AudioDeviceInfo> =
        withTimeoutOrNull(DEVICE_QUERY_TIMEOUT_MILLIS) {
            AudioDeviceManager.inputDevices.firstOrNull()
        } ?: emptyList()

    /** Long enough for a real enumeration, short enough not to stall the first sound. */
    private const val DEVICE_QUERY_TIMEOUT_MILLIS = 1500L

    fun stop() {
        ksynAudioBridge.stop()
        synth.stop()
    }

    val currentTime: Double
        get() = synth.currentTime

    suspend fun sleepUntilTime(time: Double) {
        var timeToSleep = time - synth.currentTime
        while (timeToSleep > 0.0) {
            val msecToSleep = (1000 * timeToSleep).toLong()
            if (msecToSleep > 0) {
                delay(msecToSleep)
            } else {
                yield()
            }
            timeToSleep = time - synth.currentTime
        }
    }

}

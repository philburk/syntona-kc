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

    /** A snapshot of the device list, which is normally exposed only as a Flow. */
    suspend fun currentOutputDevices(): List<AudioDeviceInfo> =
        AudioDeviceManager.outputDevices.firstOrNull() ?: emptyList()

    suspend fun currentInputDevices(): List<AudioDeviceInfo> =
        AudioDeviceManager.inputDevices.firstOrNull() ?: emptyList()

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

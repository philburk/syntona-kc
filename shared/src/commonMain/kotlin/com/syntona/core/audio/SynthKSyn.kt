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

import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.instruments.SubtractiveSynthVoice
import com.softsynth.ksyn.unitgen.LineOut
import com.softsynth.ksyn.unitgen.UnitVoice
import com.softsynth.ksyn.util.VoiceAllocator
import com.softsynth.ksyn.shared.time.TimeStamp
import com.softsynth.ksyn.unitgen.WhiteNoise
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

object SynthKSyn {

    val synth = KSyn.createSynthesizer()
    private val ksynAudioBridge: KSynAudioBridge = KSynAudioBridge(synth)


    fun start() {
        ksynAudioBridge.start()
    }

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

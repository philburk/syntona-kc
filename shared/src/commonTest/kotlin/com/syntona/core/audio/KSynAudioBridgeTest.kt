package com.syntona.core.audio

import com.softsynth.ksyn.KSyn
import com.softsynth.ksyn.unitgen.LineIn
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class KSynAudioBridgeTest {

    @Test
    fun testStereoInputSeparationInKSyn() = runBlocking {
        val synth = KSyn.createSynthesizer()
        val lineIn = LineIn()
        synth.add(lineIn)
        lineIn.start()

        // Start with 2 input channels (stereo)
        synth.start(44100, numInputChannels = 2, numOutputChannels = 2)

        val frames = 64
        val stereoInput = FloatArray(frames * 2)
        val guitar1Level = 0.8f
        val guitar2Level = 0.3f

        for (i in 0 until frames) {
            stereoInput[i * 2] = guitar1Level       // Channel 0: Guitar 1
            stereoInput[i * 2 + 1] = guitar2Level   // Channel 1: Guitar 2
        }

        synth.renderBuffer(stereoInput)

        val out0 = lineIn.output.getValues(0)
        val out1 = lineIn.output.getValues(1)

        // Verify guitar 1 is on channel 0 and guitar 2 is on channel 1
        assertEquals(guitar1Level, out0[0], 1e-4f, "Channel 0 should contain guitar 1")
        assertEquals(guitar2Level, out1[0], 1e-4f, "Channel 1 should contain guitar 2")
        assertNotEquals(out0[0], out1[0], "Channel 0 should not be duplicated to channel 1 in stereo")

        synth.stop()
    }

    @Test
    fun testMonoInputDuplicatesToBothOutputsInKSyn() = runBlocking {
        val synth = KSyn.createSynthesizer()
        val lineIn = LineIn()
        synth.add(lineIn)
        lineIn.start()

        // Start with 1 input channel (mono)
        synth.start(44100, numInputChannels = 1, numOutputChannels = 2)

        val frames = 64
        val monoInput = FloatArray(frames)
        val guitarLevel = 0.7f

        for (i in 0 until frames) {
            monoInput[i] = guitarLevel
        }

        synth.renderBuffer(monoInput)

        val out0 = lineIn.output.getValues(0)
        val out1 = lineIn.output.getValues(1)

        // Verify mono input is copied to both channels as a fallback
        assertEquals(guitarLevel, out0[0], 1e-4f, "Channel 0 should contain mono input")
        assertEquals(guitarLevel, out1[0], 1e-4f, "Channel 1 should contain duplicated mono input")

        synth.stop()
    }
}

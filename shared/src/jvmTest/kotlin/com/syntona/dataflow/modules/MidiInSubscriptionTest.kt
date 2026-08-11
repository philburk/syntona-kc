package com.syntona.dataflow.modules

import com.softsynth.midibridge.loopback.LoopbackMidiBridge
import com.syntona.core.midi.MidiDeviceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How midiIn hands its source subscription back.
 *
 * Subscribing suspends, so a device change can land while an earlier subscription is still in
 * flight. If the later one overwrites the handle for the earlier, nothing can ever cancel that
 * first listener: it keeps feeding this module from a device the user has switched away from,
 * and the only symptom is notes arriving from the wrong keyboard.
 */
class MidiInSubscriptionTest {

    private val loopbackSource = "Loopback Out"

    private fun withService(block: suspend () -> Unit) = runBlocking {
        MidiDeviceService.resetForTest(LoopbackMidiBridge())
        MidiDeviceService.open()
        block()
    }

    @AfterTest
    fun cleanUp() = runBlocking {
        MidiDeviceService.resetForTest(null)
    }

    /** Subscribing and releasing both hop through coroutines, so let them land. */
    private suspend fun settle() = delay(300)

    @Test
    fun selectingADeviceSubscribesExactlyOnce() = withService {
        val midiIn = MidiInModule()
        midiIn.selectDevice(loopbackSource)
        settle()

        assertEquals(1, MidiDeviceService.sourceListenerCount(loopbackSource))
    }

    @Test
    fun switchingAwayLeavesNoListenerBehind() = withService {
        val midiIn = MidiInModule()
        midiIn.selectDevice(loopbackSource)
        settle()
        midiIn.selectDevice("")
        settle()

        assertEquals(0, MidiDeviceService.sourceListenerCount(loopbackSource))
    }

    @Test
    fun rapidDeviceChangesLeaveOneListener() = withService {
        val midiIn = MidiInModule()
        // No settling in between: each change starts while the previous subscribe is in flight,
        // which is exactly the window where a handle used to be lost.
        midiIn.selectDevice(loopbackSource)
        midiIn.selectDevice("device that is not attached")
        midiIn.selectDevice(loopbackSource)
        settle()

        assertEquals(1, MidiDeviceService.sourceListenerCount(loopbackSource))
    }

    @Test
    fun rapidChangesEndingElsewhereLeaveNoListener() = withService {
        val midiIn = MidiInModule()
        midiIn.selectDevice(loopbackSource)
        midiIn.selectDevice("")
        settle()

        assertEquals(0, MidiDeviceService.sourceListenerCount(loopbackSource))
    }

    @Test
    fun teardownDuringSubscribeLeavesNoListener() = withService {
        val midiIn = MidiInModule()
        midiIn.selectDevice(loopbackSource)
        // Tearing down immediately cancels the subscribe coroutine mid-flight.
        midiIn.teardown()
        settle()

        assertEquals(0, MidiDeviceService.sourceListenerCount(loopbackSource))
    }

    @Test
    fun twoModulesOnOneDeviceEachKeepTheirOwnListener() = withService {
        val first = MidiInModule()
        val second = MidiInModule()
        first.selectDevice(loopbackSource)
        second.selectDevice(loopbackSource)
        settle()

        assertEquals(2, MidiDeviceService.sourceListenerCount(loopbackSource))

        first.teardown()
        settle()

        assertEquals(1, MidiDeviceService.sourceListenerCount(loopbackSource))
    }
}

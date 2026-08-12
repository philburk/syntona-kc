package com.syntona.core.midi

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Guards the lock discipline in [MidiDeviceService].
 *
 * Both entry points need the bridge, and opening it takes a lock of its own. Taking that same
 * lock around the call - as the first version of the shared-port change did - deadlocks the
 * caller outright, because kotlinx's Mutex is not reentrant. The symptom is total: no MIDI in
 * or out, and no error anywhere, since the coroutine simply never resumes.
 *
 * A timeout is the assertion. Neither call needs a real device to return.
 */
class MidiDeviceServiceTest {

    private val timeoutMillis = 3000L

    // The service is a process-wide singleton, so start from a clean one rather than inheriting
    // whatever bridge and coroutines the previous test class left behind.
    @BeforeTest
    fun startClean() = runBlocking { MidiDeviceService.resetForTest(null) }

    @AfterTest
    fun cleanUp() = runBlocking { MidiDeviceService.resetForTest(null) }

    @Test
    fun subscribeSourceReturnsRatherThanDeadlocking() {
        runBlocking {
            val finished = withTimeoutOrNull(timeoutMillis) {
                MidiDeviceService.subscribeSource("device that is not attached") { }
                true
            }
            assertNotNull(finished, "subscribeSource never returned")
        }
    }

    @Test
    fun acquireDestinationReturnsRatherThanDeadlocking() {
        runBlocking {
            val finished = withTimeoutOrNull(timeoutMillis) {
                MidiDeviceService.acquireDestination("device that is not attached")
                true
            }
            assertNotNull(finished, "acquireDestination never returned")
        }
    }
}

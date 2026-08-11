package com.syntona.core.midi

import com.softsynth.midibridge.loopback.LoopbackMidiBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Reference counting of shared destination ports, driven through a loopback bridge so the real
 * open and close calls happen.
 *
 * The port is shared, so no single holder may close it, and nothing else will: without counting
 * the leases, a destination stays open for the life of the app even after every module using it
 * is gone.
 */
class MidiDestinationSharingTest {

    private val deviceName = "Loopback In"

    private fun withService(block: suspend () -> Unit) = runBlocking {
        MidiDeviceService.resetForTest(LoopbackMidiBridge())
        MidiDeviceService.open()
        block()
    }

    @AfterTest
    fun cleanUp() = runBlocking {
        MidiDeviceService.resetForTest(null)
    }

    /** Releasing hands the work to a coroutine, so give it a moment to land. */
    private suspend fun settle() = delay(100)

    @Test
    fun holdersOfOneDeviceShareOnePort() = withService {
        val first = MidiDeviceService.acquireDestination(deviceName)
        val second = MidiDeviceService.acquireDestination(deviceName)
        assertNotNull(first)
        assertNotNull(second)

        assertSame(first.destination, second.destination, "both holders should share one port")
        assertEquals(2, MidiDeviceService.destinationRefCount(deviceName))
    }

    @Test
    fun portStaysOpenWhileAnyHolderRemains() = withService {
        val first = MidiDeviceService.acquireDestination(deviceName)!!
        val second = MidiDeviceService.acquireDestination(deviceName)!!

        first.release()
        settle()

        assertEquals(1, MidiDeviceService.destinationRefCount(deviceName))
        assertTrue(second.destination.isOpen, "the surviving holder must keep its port open")
    }

    @Test
    fun portIsClosedWhenTheLastHolderReleases() = withService {
        val first = MidiDeviceService.acquireDestination(deviceName)!!
        val second = MidiDeviceService.acquireDestination(deviceName)!!
        val port = first.destination

        first.release()
        second.release()
        settle()

        assertFalse(port.isOpen, "the port leaked: nothing else will ever close it")
        assertEquals(0, MidiDeviceService.destinationRefCount(deviceName))
    }

    @Test
    fun releasingTwiceCountsOnce() = withService {
        val first = MidiDeviceService.acquireDestination(deviceName)!!
        val second = MidiDeviceService.acquireDestination(deviceName)!!

        first.release()
        first.release()
        settle()

        assertEquals(1, MidiDeviceService.destinationRefCount(deviceName))
        assertTrue(second.destination.isOpen, "a double release must not close another holder's port")
    }

    @Test
    fun acquiringAgainAfterEveryoneLeftOpensAFreshPort() = withService {
        val first = MidiDeviceService.acquireDestination(deviceName)!!
        val oldPort = first.destination
        first.release()
        settle()

        val second = MidiDeviceService.acquireDestination(deviceName)
        assertNotNull(second)
        assertTrue(second.destination.isOpen)
        assertFalse(oldPort.isOpen, "the closed port must not be handed out again")
        assertEquals(1, MidiDeviceService.destinationRefCount(deviceName))
    }

    @Test
    fun aPortClosedBehindOurBackIsReplacedRatherThanReused() = withService {
        val first = MidiDeviceService.acquireDestination(deviceName)!!
        // Stands in for the device being unplugged while a module still holds its port.
        first.destination.close()

        val second = MidiDeviceService.acquireDestination(deviceName)
        assertNotNull(second)
        assertTrue(second.destination.isOpen, "a dead port must not be handed out")
        assertEquals(1, MidiDeviceService.destinationRefCount(deviceName))
    }

    @Test
    fun unknownDeviceLeavesNothingBehind() = withService {
        assertEquals(null, MidiDeviceService.acquireDestination("device that is not attached"))
        assertEquals(0, MidiDeviceService.destinationRefCount("device that is not attached"))
    }
}

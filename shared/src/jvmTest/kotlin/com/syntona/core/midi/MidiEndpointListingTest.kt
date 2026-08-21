package com.syntona.core.midi

import com.softsynth.midibridge.MidiDirection
import com.softsynth.midibridge.loopback.LoopbackMidiBridge
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the device menus read: endpoints with their manufacturer attached.
 *
 * A device name on its own is often too vague to pick from - an Arturia keyboard reports itself
 * as plain "MIDI" - so the menu shows the maker too. Only the name is stored in a patch, so the
 * maker for a saved selection has to be looked up from the attached devices.
 */
class MidiEndpointListingTest {

    private fun withService(block: suspend () -> Unit) = runBlocking {
        MidiDeviceService.resetForTest(LoopbackMidiBridge())
        MidiDeviceService.open()
        block()
    }

    @AfterTest
    fun cleanUp() = runBlocking {
        MidiDeviceService.resetForTest(null)
    }

    @Test
    fun endpointsCarryTheirManufacturer() = withService {
        val sources = MidiDeviceService.endpointsFor(MidiDirection.SOURCE)
        assertEquals(1, sources.size)
        assertEquals("Loopback Out", sources.single().name)
        assertEquals("kc-midi-bridge", sources.single().manufacturer)
    }

    @Test
    fun eachDirectionListsOnlyItsOwnEndpoints() = withService {
        assertEquals(
            listOf("Loopback Out"),
            MidiDeviceService.endpointsFor(MidiDirection.SOURCE).map { it.name }
        )
        assertEquals(
            listOf("Loopback In"),
            MidiDeviceService.endpointsFor(MidiDirection.DESTINATION).map { it.name }
        )
    }

    @Test
    fun compoundNameCombinesNameAndManufacturer() = withService {
        val source = MidiDeviceService.endpointsFor(MidiDirection.SOURCE).single()
        assertEquals("Loopback Out, kc-midi-bridge", source.compoundName())
    }

    @Test
    fun manufacturerIsResolvedFromASavedName() = withService {
        assertEquals(
            "kc-midi-bridge",
            MidiDeviceService.manufacturerOf("Loopback Out", MidiDirection.SOURCE)
        )
        assertEquals(
            "kc-midi-bridge",
            MidiDeviceService.manufacturerOf("Loopback Out, kc-midi-bridge", MidiDirection.SOURCE)
        )
    }

    @Test
    fun resolveLoadedDeviceReturnsCompoundName() = withService {
        // Plain name resolves to compound name
        assertEquals(
            "Loopback Out, kc-midi-bridge",
            MidiDeviceService.resolveLoadedDevice(MidiDirection.SOURCE, "Loopback Out")
        )
        // Compound name resolves to compound name
        assertEquals(
            "Loopback Out, kc-midi-bridge",
            MidiDeviceService.resolveLoadedDevice(MidiDirection.SOURCE, "Loopback Out, kc-midi-bridge")
        )
    }

    @Test
    fun manufacturerIsEmptyWhenTheDeviceIsNotThere() = withService {
        // A patch saved against a device that is currently unplugged: name shows, maker does not.
        assertEquals(
            "",
            MidiDeviceService.manufacturerOf("device that is not attached", MidiDirection.SOURCE)
        )
        // And a source name must not pick up a destination's maker.
        assertEquals(
            "",
            MidiDeviceService.manufacturerOf("Loopback Out", MidiDirection.DESTINATION)
        )
    }
}

package com.syntona.dataflow.modules

import com.softsynth.midibridge.MidiDirection
import androidx.compose.ui.geometry.Offset
import com.syntona.core.midi.MidiDeviceService
import com.syntona.dataflow.editor.GraphState
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Remembering the MIDI devices last chosen, so a patch that arrives without one picks them up.
 *
 * The usual case is a patch shared by someone else, or one saved before anything was plugged in:
 * it names no device, and having to pick the same keyboard again every time is the annoyance
 * this removes.
 */
class MidiDeviceMemoryTest {

    // resetForTest installs a scratch settings store, so choosing a device here cannot rewrite
    // the one the user picked in the app: these values live in their real application settings.
    @BeforeTest
    fun startClean() = runBlocking { MidiDeviceService.resetForTest(null) }

    @Test
    fun choosingASourceRemembersItForNextTime() {
        MidiInModule().selectDevice("Arturia KeyStep")
        assertEquals("Arturia KeyStep", MidiDeviceService.lastUsedDevice(MidiDirection.SOURCE))
    }

    @Test
    fun choosingADestinationRemembersItForNextTime() {
        MidiOutModule().selectDevice("Logic Pro Virtual In")
        assertEquals(
            "Logic Pro Virtual In",
            MidiDeviceService.lastUsedDevice(MidiDirection.DESTINATION)
        )
    }

    @Test
    fun theTwoDirectionsAreRememberedSeparately() {
        // One rig, one keyboard and one synth: mixing them up would point a midiIn at an output.
        MidiInModule().selectDevice("Arturia KeyStep")
        MidiOutModule().selectDevice("Logic Pro Virtual In")

        assertEquals("Arturia KeyStep", MidiDeviceService.lastUsedDevice(MidiDirection.SOURCE))
        assertEquals(
            "Logic Pro Virtual In",
            MidiDeviceService.lastUsedDevice(MidiDirection.DESTINATION)
        )
    }

    @Test
    fun aPatchWithNoDeviceTakesTheRememberedOne() {
        MidiInModule().selectDevice("Arturia KeyStep")
        MidiOutModule().selectDevice("Logic Pro Virtual In")

        val loadedIn = MidiInModule().also { it.loadDevice("") }
        val loadedOut = MidiOutModule().also { it.loadDevice("") }

        assertEquals("Arturia KeyStep", loadedIn.deviceName)
        assertEquals("Logic Pro Virtual In", loadedOut.deviceName)
    }

    @Test
    fun aPatchThatNamesADeviceKeepsIt() {
        MidiInModule().selectDevice("Arturia KeyStep")

        // Even though something else is remembered, the patch's own choice wins. Overriding it
        // would silently move a piece onto a different instrument.
        val loaded = MidiInModule().also { it.loadDevice("Bus 1") }
        assertEquals("Bus 1", loaded.deviceName)
    }

    @Test
    fun withNothingRememberedAnEmptyPatchStaysOnNone() {
        val loadedIn = MidiInModule().also { it.loadDevice("") }
        val loadedOut = MidiOutModule().also { it.loadDevice("") }

        assertEquals("", loadedIn.deviceName)
        assertEquals("", loadedOut.deviceName)
    }

    @Test
    fun choosingNoneDoesNotEraseTheMemory() {
        val module = MidiInModule()
        module.selectDevice("Arturia KeyStep")

        // Detaching one module is not a statement about what the rig is; the next patch that
        // arrives without a device should still find the keyboard.
        module.selectDevice("")

        assertEquals("", module.deviceName)
        assertEquals("Arturia KeyStep", MidiDeviceService.lastUsedDevice(MidiDirection.SOURCE))
    }

    @Test
    fun aFreshModuleDroppedIntoAPatchTakesTheRememberedDevice() {
        MidiInModule().selectDevice("Arturia KeyStep")
        MidiOutModule().selectDevice("Logic Pro Virtual In")

        val graphState = GraphState()
        val freshIn = MidiInModule()
        val freshOut = MidiOutModule()
        graphState.addModule(freshIn, Offset.Zero)
        graphState.addModule(freshOut, Offset.Zero)

        assertEquals("Arturia KeyStep", freshIn.deviceName)
        assertEquals("Logic Pro Virtual In", freshOut.deviceName)
    }

    @Test
    fun aLoadedPatchKeepsItsOwnDeviceWhenItIsAdded() {
        MidiInModule().selectDevice("Arturia KeyStep")

        // Loading applies the patch's values before the module is added, and being added must
        // not then overwrite them with whatever this machine happened to use last.
        val graphState = GraphState()
        val loaded = MidiInModule().also { it.loadDevice("Bus 1") }
        graphState.addModule(loaded, Offset.Zero)

        assertEquals("Bus 1", loaded.deviceName)
    }

    @Test
    fun withNothingRememberedAFreshModuleStaysOnNone() {
        val graphState = GraphState()
        val freshIn = MidiInModule()
        graphState.addModule(freshIn, Offset.Zero)

        assertEquals("", freshIn.deviceName)
    }

    @Test
    fun loadingAPatchDoesNotChangeWhatIsRemembered() {
        MidiInModule().selectDevice("Arturia KeyStep")
        MidiOutModule().selectDevice("Logic Pro Virtual In")

        // Opening someone else's patch is not the user saying "this is my rig now". If it were
        // remembered, every patch opened afterwards would quietly follow that patch's devices.
        MidiInModule().loadDevice("Bus 1")
        MidiOutModule().loadDevice("Bus 2")

        assertEquals("Arturia KeyStep", MidiDeviceService.lastUsedDevice(MidiDirection.SOURCE))
        assertEquals(
            "Logic Pro Virtual In",
            MidiDeviceService.lastUsedDevice(MidiDirection.DESTINATION)
        )
    }

    @Test
    fun addingAModuleToAPatchDoesNotChangeWhatIsRemembered() {
        MidiInModule().selectDevice("Arturia KeyStep")

        val graphState = GraphState()
        graphState.addModule(MidiInModule().also { it.loadDevice("Bus 1") }, Offset.Zero)
        graphState.addModule(MidiInModule(), Offset.Zero)

        assertEquals("Arturia KeyStep", MidiDeviceService.lastUsedDevice(MidiDirection.SOURCE))
    }

    @Test
    fun theMostRecentChoiceIsTheOneRemembered() {
        MidiInModule().selectDevice("Arturia KeyStep")
        MidiInModule().selectDevice("Bus 1")

        assertEquals("Bus 1", MidiDeviceService.lastUsedDevice(MidiDirection.SOURCE))
        assertEquals("Bus 1", MidiInModule().also { it.loadDevice("") }.deviceName)
    }

    @Test
    fun loadingAPatchWithUnavailableDeviceOverwritesBadName() = runBlocking {
        MidiDeviceService.resetForTest(com.softsynth.midibridge.loopback.LoopbackMidiBridge())
        MidiInModule().selectDevice("Loopback Out")

        val module = MidiInModule()
        module.loadDevice("MIDI")
        kotlinx.coroutines.delay(150)

        assertEquals("Loopback Out", module.deviceName)
    }
}

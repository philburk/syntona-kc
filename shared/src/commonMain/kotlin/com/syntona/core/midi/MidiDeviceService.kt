/*
 * Copyright 2026 Phil Burk
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

package com.syntona.core.midi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.softsynth.midibridge.MidiBridge
import com.softsynth.midibridge.MidiDestination
import com.softsynth.midibridge.MidiDirection
import com.softsynth.midibridge.MidiEndpoint
import com.softsynth.midibridge.MidiEvent
import com.softsynth.midibridge.MidiSource
import com.syntona.storage.InMemorySettingsBackend
import com.syntona.storage.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long the shared input poller idles when no MIDI is waiting. */
private const val POLL_INTERVAL_MS = 2L

/**
 * The application's single connection to kc-midi-bridge.
 *
 * One [MidiBridge] is shared by every midiIn and midiOut module, because opening it can prompt
 * the user for permission in the browser and doing that once per module would be unbearable.
 * The bridge is opened lazily, the first time a module or a device menu asks for it, so a patch
 * with no MIDI in it never triggers the prompt.
 *
 * Devices are named rather than numbered here: [MidiEndpoint.id] is assigned by the platform and
 * is not stable across sessions, so a saved patch stores the device name and resolves it again
 * on load, the same way [com.syntona.core.audio.AudioStreamManager] handles audio devices.
 */
object MidiDeviceService {

    // A var only so that resetForTest can replace it. Every port poller, retry loop and hotplug
    // collector lives in here, and a test cannot be isolated from the one before it unless all
    // of them can be stopped at once. Production opens one scope and keeps it.
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Guards opening the bridge itself. Held only by [open]. */
    private val openMutex = Mutex()

    /** Guards the shared port registries. Never held while calling [open]. */
    private val portMutex = Mutex()

    /** Serializes writes to [endpoints]. A leaf lock: nothing is called while holding it. */
    private val publishMutex = Mutex()

    private var bridge: MidiBridge? = null

    /** Collects hotplug updates from the open bridge. Cancelled when the bridge is replaced. */
    private var endpointCollectorJob: Job? = null

    private class SourceSubscription(
        val deviceName: String,
        val midi: MidiBridge
    ) {
        val listeners = mutableListOf<(MidiEvent) -> Unit>()
        var job: Job? = null

        fun subscribe(listener: (MidiEvent) -> Unit, scope: CoroutineScope) {
            listeners.add(listener)
            if (job == null || job?.isActive != true) {
                job = scope.launch {
                    while (isActive) {
                        val endpoint = findEndpoint(deviceName, MidiDirection.SOURCE)
                        if (endpoint == null) {
                            delay(200)
                            continue
                        }
                        val source = try {
                            midi.openSource(endpoint)
                        } catch (e: Exception) {
                            delay(200)
                            null
                        } ?: continue

                        try {
                            val event = MidiEvent()
                            while (isActive && source.isOpen) {
                                var delivered = false
                                while (source.next(event)) {
                                    delivered = true
                                    val targets = ArrayList(listeners)
                                    for (target in targets) {
                                        target(event)
                                    }
                                }
                                if (!delivered) delay(POLL_INTERVAL_MS)
                            }
                        } finally {
                            source.close()
                        }
                    }
                }
            }
        }

        fun unsubscribe(listener: (MidiEvent) -> Unit): Boolean {
            listeners.remove(listener)
            if (listeners.isEmpty()) {
                cancel()
                return true
            }
            return false
        }

        fun cancel() {
            job?.cancel()
            job = null
        }
    }

    /** One open destination port and the number of leases currently holding it. */
    private class SharedDestination(val destination: MidiDestination) {
        var refCount = 0
    }

    private val sourceSubscriptions = mutableMapOf<String, SourceSubscription>()
    private val openDestinations = mutableMapOf<String, SharedDestination>()

    /** Endpoints currently visible, updated as devices are plugged in and unplugged. */
    val endpoints = mutableStateListOf<MidiEndpoint>()

    /**
     * The same endpoints as an ordinary immutable list, for code that is not in a composition.
     * The menus read [endpoints] so they follow hotplug; everything else reads this, because a
     * snapshot state list cannot safely be iterated while another coroutine is replacing it.
     */
    private var plainEndpoints: List<MidiEndpoint> = emptyList()

    /** Why MIDI is unavailable, for the device menus to show. Null while all is well. */
    var errorMessage: String? by mutableStateOf(null)
        private set

    /** Whether this platform has a MIDI implementation at all, regardless of devices attached. */
    val isSupported: Boolean get() = MidiBridge.isSupported()

    /**
     * Bridge to use instead of the platform's, so tests can drive a loopback bridge through the
     * real sharing and reference counting. Null everywhere outside tests.
     */
    internal var testBridge: MidiBridge? = null

    /** Opens the bridge if it is not open yet, returning null if MIDI is unavailable. */
    suspend fun open(): MidiBridge? = openMutex.withLock {
        bridge?.let { return@withLock it }
        val override = testBridge
        if (override == null && !MidiBridge.isSupported()) {
            errorMessage = "MIDI is not supported on this platform."
            return@withLock null
        }
        try {
            val opened = override ?: MidiBridge.open()
            bridge = opened
            errorMessage = null
            publish(opened.endpointsNow())
            // Held so it can be cancelled when the bridge is replaced. Two collectors writing
            // the same snapshot state list conflict, and the survivor would be publishing the
            // endpoints of a bridge nobody is using any more.
            endpointCollectorJob?.cancel()
            endpointCollectorJob = scope.launch {
                opened.endpoints.collect { list -> publish(list) }
            }
            opened
        } catch (e: Exception) {
            errorMessage = e.message ?: "MIDI could not be opened."
            null
        }
    }

    /** Opens the bridge in the background, for callers that are not in a coroutine. */
    fun openInBackground() {
        scope.launch { open() }
    }

    fun endpointNames(direction: MidiDirection): List<String> =
        endpoints.filter { it.direction == direction }.map { it.name }

    /**
     * Where the remembered devices are kept. Replaceable so that tests can use an in-memory
     * store: these values live in the user's real application settings, and a test has no
     * business rewriting the device someone picked in the app.
     */
    internal var settings: SettingsStore = SettingsStore.default

    private fun lastDeviceKey(direction: MidiDirection): String = when (direction) {
        MidiDirection.SOURCE -> SettingsStore.KEY_LAST_MIDI_SOURCE
        MidiDirection.DESTINATION -> SettingsStore.KEY_LAST_MIDI_DESTINATION
    }

    /**
     * Records the device just chosen, so a patch that arrives without one can pick it up.
     *
     * Kept per direction: a rig has one keyboard and one synth, and remembering them separately
     * is what lets both a midiIn and a midiOut land on the right thing.
     */
    fun rememberDevice(direction: MidiDirection, deviceName: String) {
        if (deviceName.isEmpty()) return
        try {
            settings.putString(lastDeviceKey(direction), deviceName)
        } catch (_: Exception) {
            // A settings write that fails costs the convenience, not the session.
        }
    }

    /** The device last chosen for this direction, or empty if there has never been one. */
    fun lastUsedDevice(direction: MidiDirection): String =
        try {
            settings.getString(lastDeviceKey(direction))
        } catch (_: Exception) {
            ""
        }

    /**
     * Checks whether a device with the given name is currently available for [direction].
     */
    fun isDeviceAvailable(direction: MidiDirection, deviceName: String): Boolean {
        if (deviceName.isEmpty()) return false
        return findEndpoint(deviceName, direction) != null
    }

    /**
     * The device a module loading with [savedName] should end up on.
     *
     * A patch saved with no device takes the one last chosen from Settings Storage.
     * If a saved device name is specified but is no longer available among attached devices,
     * it falls back to the device last chosen from Settings Storage for [direction].
     */
    fun resolveLoadedDevice(direction: MidiDirection, savedName: String): String {
        if (bridge == null) {
            openInBackground()
        }
        if (savedName.isEmpty()) {
            return lastUsedDevice(direction)
        }
        val available = endpointsFor(direction)
        if (available.isNotEmpty() && !isDeviceAvailable(direction, savedName)) {
            return lastUsedDevice(direction)
        }
        return savedName
    }

    /**
     * Endpoints of one direction, for a device menu that wants more than the bare name.
     *
     * Device names alone are often too vague to choose between - an Arturia keyboard reports
     * itself as plain "MIDI" - so the menu shows the manufacturer as well.
     */
    fun endpointsFor(direction: MidiDirection): List<MidiEndpoint> =
        endpoints.filter { it.direction == direction }

    /**
     * Manufacturer of the named device, or empty if it is unknown or the device is not attached.
     * Lets a menu label a saved selection without the module having to store the manufacturer.
     */
    fun manufacturerOf(deviceName: String, direction: MidiDirection): String =
        findEndpoint(deviceName, direction)?.manufacturer ?: ""

    /**
     * Subscribes to events from the named MIDI source. Multiple subscribers to the same device
     * share a single underlying polling loop and all receive every event.
     * Returns an unsubscribe function to cancel the subscription.
     */
    suspend fun subscribeSource(
        deviceName: String,
        listener: (MidiEvent) -> Unit
    ): (() -> Unit)? {
        // open() takes openMutex itself, so it must be called before portMutex is held and never
        // from inside a lock: kotlinx Mutex is not reentrant and re-locking deadlocks the caller.
        val midi = open() ?: return null
        return portMutex.withLock {
            val sub = sourceSubscriptions.getOrPut(deviceName) {
                SourceSubscription(deviceName, midi)
            }
            sub.subscribe(listener, scope);
            {
                scope.launch {
                    portMutex.withLock {
                        if (sub.unsubscribe(listener)) {
                            sourceSubscriptions.remove(deviceName)
                        }
                    }
                }
            }
        }
    }

    /**
     * Acquires the named destination for writing, or returns null if no such device is attached.
     *
     * Callers requesting the same device share one port, so the lease must be released when the
     * holder is done with it. The port is closed once the last lease is released; nothing else
     * closes it, which is why a holder that simply drops its reference leaks the port until the
     * app exits.
     */
    suspend fun acquireDestination(deviceName: String): DestinationLease? {
        // Opened before taking portMutex, for the same non-reentrancy reason as subscribeSource.
        val midi = open() ?: return null
        return portMutex.withLock {
            val existing = openDestinations[deviceName]
            val shared = if (existing != null && existing.destination.isOpen) {
                existing
            } else {
                // The device went away while we held its port. Close the dead one rather than
                // stranding it in the map, then open a replacement for the device that is back.
                if (existing != null) {
                    existing.destination.close()
                    openDestinations.remove(deviceName)
                }
                val endpoint = findEndpoint(deviceName, MidiDirection.DESTINATION)
                    ?: return@withLock null
                val opened = try {
                    midi.openDestination(endpoint)
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Could not open MIDI destination $deviceName."
                    return@withLock null
                }
                SharedDestination(opened).also { openDestinations[deviceName] = it }
            }
            shared.refCount++
            DestinationLease(shared.destination) { releaseDestination(deviceName, shared) }
        }
    }

    /** Number of leases held on a device's port. Zero when the port is closed. For tests. */
    internal fun destinationRefCount(deviceName: String): Int =
        openDestinations[deviceName]?.refCount ?: 0

    /** Number of listeners subscribed to a device's source. Zero when nothing is polling it. */
    internal fun sourceListenerCount(deviceName: String): Int =
        sourceSubscriptions[deviceName]?.listeners?.size ?: 0

    /** Drops every port and the bridge itself, so each test starts from a clean service. */
    internal suspend fun resetForTest(replacement: MidiBridge?) {
        // A scratch store, so that a test choosing a device cannot rewrite the one the user
        // picked in the app: these values live in their real application settings.
        settings = SettingsStore(backend = InMemorySettingsBackend())
        // The whole scope goes, not just the jobs this object still has handles for. A poll loop
        // or a retry loop left running from an earlier test would otherwise keep writing to the
        // maps and the endpoint list, and the next test would inherit its mess.
        scope.cancel()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        endpointCollectorJob = null
        portMutex.withLock {
            sourceSubscriptions.values.forEach { it.cancel() }
            sourceSubscriptions.clear()
            openDestinations.values.forEach { it.destination.close() }
            openDestinations.clear()
        }
        openMutex.withLock {
            bridge = null
            errorMessage = null
        }
        testBridge = replacement
        publish(emptyList())
    }

    /**
     * One holder's claim on a shared destination port.
     *
     * Handed out rather than the port itself so that giving it back is a thing the holder can
     * do: [MidiDestination] has no notion of being shared, and closing it directly would cut
     * off every other module using the same device.
     */
    class DestinationLease internal constructor(
        val destination: MidiDestination,
        private val onRelease: () -> Unit
    ) {
        private var isReleased = false

        /** Drops this claim. Releasing twice is harmless and counts once. */
        fun release() {
            if (isReleased) return
            isReleased = true
            onRelease()
        }
    }

    private fun releaseDestination(deviceName: String, shared: SharedDestination) {
        scope.launch {
            portMutex.withLock {
                shared.refCount--
                if (shared.refCount <= 0) {
                    shared.destination.close()
                    if (openDestinations[deviceName] === shared) {
                        openDestinations.remove(deviceName)
                    }
                }
            }
        }
    }

    private fun findEndpoint(deviceName: String, direction: MidiDirection): MidiEndpoint? {
        plainEndpoints.firstOrNull { it.name == deviceName && it.direction == direction }
            ?.let { return it }
        endpoints.firstOrNull { it.name == deviceName && it.direction == direction }
            ?.let { return it }
        return bridge?.endpointsNow()?.firstOrNull { it.name == deviceName && it.direction == direction }
    }

    /**
     * Replaces the visible endpoint list.
     *
     * Serialized, because opening the bridge and the hotplug collector both publish and they run
     * on different coroutines: two mutable snapshots touching the same state list at once throw
     * SnapshotApplyConflictException, which inside the collector would silently end hotplug
     * updates for the rest of the session. The snapshot is still needed because the collector
     * runs off the UI thread.
     */
    private suspend fun publish(list: List<MidiEndpoint>) {
        publishMutex.withLock {
            // The plain copy is swapped as one reference, so a reader outside a composition
            // always sees a whole list rather than one being rebuilt underneath it.
            plainEndpoints = list
            Snapshot.withMutableSnapshot {
                endpoints.clear()
                endpoints.addAll(list)
            }
        }
    }
}

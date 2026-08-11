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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Guards opening the bridge itself. Held only by [open]. */
    private val openMutex = Mutex()

    /** Guards the shared port registries. Never held while calling [open]. */
    private val portMutex = Mutex()

    private var bridge: MidiBridge? = null

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
            scope.launch {
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
        endpoints.firstOrNull { it.name == deviceName && it.direction == direction }?.let { return it }
        return bridge?.endpointsNow()?.firstOrNull { it.name == deviceName && it.direction == direction }
    }

    private fun publish(list: List<MidiEndpoint>) {
        // The collector runs off the UI thread, so the state list is updated inside a snapshot.
        Snapshot.withMutableSnapshot {
            endpoints.clear()
            endpoints.addAll(list)
        }
    }
}

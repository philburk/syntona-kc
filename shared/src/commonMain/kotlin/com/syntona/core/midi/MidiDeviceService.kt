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
import com.softsynth.midibridge.MidiSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val openMutex = Mutex()
    private var bridge: MidiBridge? = null

    /** Endpoints currently visible, updated as devices are plugged in and unplugged. */
    val endpoints = mutableStateListOf<MidiEndpoint>()

    /** Why MIDI is unavailable, for the device menus to show. Null while all is well. */
    var errorMessage: String? by mutableStateOf(null)
        private set

    /** Whether this platform has a MIDI implementation at all, regardless of devices attached. */
    val isSupported: Boolean get() = MidiBridge.isSupported()

    /** Opens the bridge if it is not open yet, returning null if MIDI is unavailable. */
    suspend fun open(): MidiBridge? = openMutex.withLock {
        bridge?.let { return@withLock it }
        if (!MidiBridge.isSupported()) {
            errorMessage = "MIDI is not supported on this platform."
            return@withLock null
        }
        try {
            val opened = MidiBridge.open()
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
     * Opens the named source for reading, or returns null if no such device is attached.
     * The caller owns the returned port and must close it.
     */
    suspend fun openSource(deviceName: String): MidiSource? {
        val midi = open() ?: return null
        val endpoint = findEndpoint(deviceName, MidiDirection.SOURCE) ?: return null
        return try {
            midi.openSource(endpoint)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Could not open MIDI source $deviceName."
            null
        }
    }

    /**
     * Opens the named destination for writing, or returns null if no such device is attached.
     * The caller owns the returned port and must close it.
     */
    suspend fun openDestination(deviceName: String): MidiDestination? {
        val midi = open() ?: return null
        val endpoint = findEndpoint(deviceName, MidiDirection.DESTINATION) ?: return null
        return try {
            midi.openDestination(endpoint)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Could not open MIDI destination $deviceName."
            null
        }
    }

    private fun findEndpoint(deviceName: String, direction: MidiDirection): MidiEndpoint? =
        endpoints.firstOrNull { it.name == deviceName && it.direction == direction }

    private fun publish(list: List<MidiEndpoint>) {
        // The collector runs off the UI thread, so the state list is updated inside a snapshot.
        Snapshot.withMutableSnapshot {
            endpoints.clear()
            endpoints.addAll(list)
        }
    }
}

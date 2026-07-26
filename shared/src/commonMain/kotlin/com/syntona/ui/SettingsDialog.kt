package com.syntona.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softsynth.audiobridge.AudioDeviceInfo
import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.audiobridge.AudioInputBridge
import com.syntona.core.audio.AudioSettings
import com.syntona.core.audio.SynthKSyn
import kotlinx.coroutines.launch

private enum class SettingsTab(val title: String) {
    AUDIO("Audio")
}

/**
 * Whether the settings dialog is open. Hoisted out of the composable so that the desktop OS menu
 * bar, which is built outside the App composition, can open it too.
 */
object SettingsDialogState {
    var isVisible by mutableStateOf(false)

    fun open() {
        isVisible = true
    }

    fun close() {
        isVisible = false
    }
}

/**
 * Application settings, shown as a dialog with a tab per topic. Only Audio exists so far; the
 * tab row is there so adding another does not mean restructuring the dialog.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(SettingsTab.AUDIO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.width(460.dp)) {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    SettingsTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.title) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.heightIn(min = 220.dp)) {
                    when (selectedTab) {
                        SettingsTab.AUDIO -> AudioSettingsTab()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun AudioSettingsTab() {
    val scope = rememberCoroutineScope()
    val outputDevices by AudioDeviceManager.outputDevices.collectAsState(initial = emptyList())
    val inputDevices by AudioDeviceManager.inputDevices.collectAsState(initial = emptyList())
    val inputSupported = remember { AudioInputBridge.isSupported() }
    var isRestarting by remember { mutableStateOf(false) }

    /** Applies a change by closing the current devices and opening the newly chosen ones. */
    fun applyChange(change: () -> Unit) {
        change()
        isRestarting = true
        scope.launch {
            try {
                SynthKSyn.restart()
            } finally {
                isRestarting = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InUseSummary(isRestarting)

        HorizontalDivider()

        DeviceChooser(
            label = "Output Device",
            devices = outputDevices,
            preferredName = AudioSettings.preferredOutputName,
            enabled = !isRestarting,
            onSelect = { name -> applyChange { AudioSettings.selectOutputDevice(name) } }
        )

        if (inputSupported) {
            DeviceChooser(
                label = "Input Device",
                devices = inputDevices,
                preferredName = AudioSettings.preferredInputName,
                enabled = !isRestarting,
                onSelect = { name -> applyChange { AudioSettings.selectInputDevice(name) } }
            )
        } else {
            Text(
                "Audio input is not available on this platform.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        HorizontalDivider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { applyChange {} },
                enabled = !isRestarting
            ) {
                Text("Restart Audio")
            }
            Button(
                onClick = {
                    applyChange {
                        AudioSettings.selectOutputDevice(null)
                        AudioSettings.selectInputDevice(null)
                    }
                },
                enabled = !isRestarting
            ) {
                Text("Use Defaults")
            }
        }

        Text(
            "Devices are remembered by name, so they are found again even if the system " +
                "renumbers them. If a device is missing when Syntona starts, the system " +
                "default is used instead.",
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

/** Shows which devices are actually open right now, which is what the user cares about. */
@Composable
private fun InUseSummary(isRestarting: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Currently in use", fontSize = 11.sp, color = Color.Gray)
        if (isRestarting) {
            Text("Restarting audio…", fontSize = 13.sp, color = Color(0xFFFFC107))
        } else {
            InUseLine("Output", AudioSettings.activeOutputName, AudioSettings.outputFellBackToDefault)
            InUseLine("Input", AudioSettings.activeInputName, AudioSettings.inputFellBackToDefault)
        }
    }
}

@Composable
private fun InUseLine(label: String, deviceName: String, fellBack: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            fontSize = 12.sp,
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = deviceName.ifEmpty { "not open" },
            fontSize = 13.sp,
            color = if (deviceName.isEmpty()) Color.Gray else Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
        if (fellBack) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "(saved device unavailable, using default)",
                fontSize = 11.sp,
                color = Color(0xFFFFC107)
            )
        }
    }
}

@Composable
private fun DeviceChooser(
    label: String,
    devices: List<AudioDeviceInfo>,
    preferredName: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val missing = AudioSettings.isDeviceMissing(preferredName, devices)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box {
            Button(onClick = { expanded = true }, enabled = enabled) {
                Text(preferredName ?: DEFAULT_LABEL)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(DEFAULT_LABEL) },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    }
                )
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                buildString {
                                    append(device.name)
                                    if (device.isDefault) append("  (system default)")
                                    append("  •  ${device.maxChannels} ch")
                                }
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(device.name)
                        }
                    )
                }
            }
        }
        if (missing) {
            Text(
                "\"$preferredName\" is not connected.",
                fontSize = 11.sp,
                color = Color(0xFFFFC107)
            )
        }
    }
}

private const val DEFAULT_LABEL = "System Default"

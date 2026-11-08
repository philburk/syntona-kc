package com.syntona.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softsynth.audiobridge.AudioDeviceInfo
import com.softsynth.audiobridge.AudioDeviceManager
import com.softsynth.audiobridge.AudioInputBridge
import com.syntona.core.audio.AudioSettings
import com.syntona.dataflow.editor.ColorTheme
import com.syntona.dataflow.editor.CompactDropdownMenuItem
import com.syntona.dataflow.editor.GraphViewSettings
import com.syntona.dataflow.editor.LocalGraphColors
import com.syntona.core.audio.LatencyMeasurement
import com.syntona.core.audio.SynthKSyn
import kotlin.math.abs
import kotlin.math.log10
import kotlinx.coroutines.launch

private enum class SettingsTab(val title: String) {
    GENERAL("General"),
    AUDIO("Audio"),
    LATENCY("Latency")
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
    var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }

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
                        SettingsTab.GENERAL -> GeneralSettingsTab()
                        SettingsTab.AUDIO -> AudioSettingsTab()
                        SettingsTab.LATENCY -> LatencySettingsTab()
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
private fun GeneralSettingsTab() {
    var expanded by remember { mutableStateOf(false) }
    val currentTheme = GraphViewSettings.colorTheme

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Color Theme", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box {
                Button(onClick = { expanded = true }) {
                    Text(currentTheme.label)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ColorTheme.entries.forEach { theme ->
                        CompactDropdownMenuItem(
                            text = { Text(theme.label, fontSize = 12.sp) },
                            onClick = {
                                expanded = false
                                GraphViewSettings.setColorTheme(theme)
                            }
                        )
                    }
                }
            }
        }
    }
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

@Composable
private fun LatencySettingsTab() {
    val scope = rememberCoroutineScope()
    val supported = remember { LatencyMeasurement.isSupported() }
    val running = LatencyMeasurement.isRunning
    val result = LatencyMeasurement.result
    var logScale by remember { mutableStateOf(false) }
    var showFineCurve by remember { mutableStateOf(false) }

    // Whatever was measured previously for the devices currently open.
    val storedFrames = remember(
        AudioSettings.activeInputName,
        AudioSettings.activeOutputName,
        LatencyMeasurement.status
    ) { AudioSettings.measuredLatencyForActiveDevices() }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Round-trip latency is the delay from Syntona sending a sample to hearing it come " +
                "back. Measuring it needs a loopback: either a cable from output to input, or " +
                "the speaker playing loudly enough for the microphone to hear.",
            fontSize = 11.sp,
            color = Color.Gray
        )

        if (!supported) {
            Text(
                "This platform has no audio input, so latency cannot be measured here.",
                fontSize = 12.sp,
                color = Color(0xFFFFC107)
            )
            return@Column
        }

        val colors = LocalGraphColors.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(4.dp))
                .border(1.dp, colors.moduleBorder, RoundedCornerShape(4.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Measured latency", fontSize = 11.sp, color = Color.Gray)
            when {
                running -> {
                    Text(
                        "Measuring… ${(LatencyMeasurement.progress * 100).toInt()}%",
                        fontSize = 14.sp,
                        color = Color(0xFFFFC107)
                    )
                    Text(
                        "Keep the room quiet and do not change the volume.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                result != null && result.isConfident -> {
                    Text(
                        text = "${result.latencyFrames} frames  •  " +
                            "${formatMillis(result.latencyMillis)} ms",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        "confidence ${formatMillis(result.confidence * 100)}%  at " +
                            "${result.sampleRate} Hz",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        "analysis took ${formatMillis(result.analysisMillis)} ms",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                storedFrames >= 0 -> {
                    val millis = storedFrames * 1000.0 /
                        (SynthKSyn.synth.frameRate.takeIf { it > 0 } ?: 48000)
                    Text(
                        text = "$storedFrames frames  •  ${formatMillis(millis)} ms",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF4CAF50)
                    )
                    Text("saved from an earlier measurement", fontSize = 11.sp, color = Color.Gray)
                }

                else -> Text("not measured yet", fontSize = 14.sp, color = Color.Gray)
            }

            if (LatencyMeasurement.status == LatencyMeasurement.Status.FAILED) {
                Text(
                    LatencyMeasurement.failureMessage,
                    fontSize = 11.sp,
                    color = Color(0xFFFF7043)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { scope.launch { LatencyMeasurement.measure() } },
                enabled = !running
            ) {
                Text(if (result == null && storedFrames < 0) "Measure" else "Measure Again")
            }
            Button(onClick = { LatencyMeasurement.clear() }, enabled = !running) {
                Text("Clear")
            }
        }

        // The correlation curve makes it obvious whether the peak is sharp and isolated, or
        // whether the measurement merely picked the least bad of several similar offsets.
        val curve = result?.let { if (showFineCurve) it.fine else it.coarse }
        if (result != null && curve != null && curve.values.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Correlation vs. offset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showFineCurve = !showFineCurve }) {
                        Text(if (showFineCurve) "Fine" else "Coarse", fontSize = 11.sp)
                    }
                    TextButton(onClick = { logScale = !logScale }) {
                        Text(if (logScale) "Log" else "Linear", fontSize = 11.sp)
                    }
                }
            }
            CorrelationChart(
                correlations = curve.values,
                firstFrame = curve.firstFrame,
                stride = curve.stride,
                peakFrame = result.latencyFrames,
                logScale = logScale,
                modifier = Modifier.fillMaxWidth().height(110.dp)
            )
            Text(
                text = if (showFineCurve) {
                    "Fine pass: every frame from ${curve.firstFrame} to ${curve.lastFrame}, which " +
                        "is how precisely the exact frame could be pinned down."
                } else {
                    "Coarse pass: every ${curve.stride} frames from ${curve.firstFrame} to " +
                        "${curve.lastFrame}, which is where the pulse was found. One clear peak " +
                        "means a reliable result."
                },
                fontSize = 10.sp,
                color = Color.Gray
            )
        }

        if (LatencyMeasurement.measuredInputName.isNotEmpty()) {
            Text(
                "Saved for: ${LatencyMeasurement.measuredInputName} → " +
                    LatencyMeasurement.measuredOutputName,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * Plots correlation magnitude against offset, with the chosen peak marked.
 *
 * There are far more correlation values than there are pixels, so each column shows the largest
 * magnitude falling within it. Taking the maximum rather than sampling matters: a single-pixel
 * peak would otherwise be skipped over entirely.
 */
@Composable
private fun CorrelationChart(
    correlations: FloatArray,
    firstFrame: Int,
    stride: Int,
    peakFrame: Int,
    logScale: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(Color(0xFF141414), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
    ) {
        if (correlations.isEmpty()) return@Canvas

        val columns = size.width.toInt().coerceIn(1, correlations.size)
        val perColumn = correlations.size.toFloat() / columns

        // Reduce to one magnitude per pixel column.
        val peaks = FloatArray(columns)
        for (column in 0 until columns) {
            val start = (column * perColumn).toInt()
            val end = (((column + 1) * perColumn).toInt()).coerceAtMost(correlations.size)
            var highest = 0f
            for (i in start until end.coerceAtLeast(start + 1)) {
                if (i < correlations.size) {
                    val magnitude = abs(correlations[i])
                    if (magnitude > highest) highest = magnitude
                }
            }
            peaks[column] = highest
        }

        val maxMagnitude = peaks.max().coerceAtLeast(1.0e-6f)

        // Correlation magnitude is always positive, so the baseline is the bottom of the plot
        // rather than the middle. On a log axis everything below the floor is clamped to zero.
        fun heightFraction(magnitude: Float): Float = if (logScale) {
            val floorDb = LOG_FLOOR_DB
            val db = 20f * log10((magnitude / maxMagnitude).coerceAtLeast(1.0e-9f))
            ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
        } else {
            (magnitude / maxMagnitude).coerceIn(0f, 1f)
        }

        // Horizontal guides at quarters of full scale.
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            val y = size.height * (1f - fraction)
            drawLine(
                color = Color(0xFF2E2E2E),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        val stepX = size.width / (columns - 1).coerceAtLeast(1)
        val path = Path()
        for (column in 0 until columns) {
            val x = column * stepX
            val y = size.height * (1f - heightFraction(peaks[column]))
            if (column == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFF4CAF50), style = Stroke(width = 1.5f))

        // Mark the offset that was reported as the latency.
        val peakIndex = (peakFrame - firstFrame) / stride.coerceAtLeast(1)
        if (peakIndex in correlations.indices) {
            val x = (peakIndex / perColumn) * stepX
            drawLine(
                color = Color(0xFFFFC107),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
    }
}

/** Lowest level shown on the log axis, relative to the peak. */
private const val LOG_FLOOR_DB = -60f

private fun formatMillis(value: Double): String {
    val scaled = (value * 100).toLong()
    val whole = scaled / 100
    val fraction = (if (scaled < 0) -scaled else scaled) % 100
    return "$whole.${fraction.toString().padStart(2, '0')}"
}

/** Shows which devices are actually open right now, which is what the user cares about. */
@Composable
private fun InUseSummary(isRestarting: Boolean) {
    val colors = LocalGraphColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background, RoundedCornerShape(4.dp))
            .border(1.dp, colors.moduleBorder, RoundedCornerShape(4.dp))
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
            Button(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.widthIn(max = DEVICE_MENU_WIDTH)
            ) {
                Text(
                    text = preferredName ?: DEFAULT_LABEL,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Device names run long - a USB id, "(system default)" and the channel count on top of
            // the name itself - so the menu is given the full width of the dialog and the rows are
            // held to a single line. Wrapping would be cut off by the fixed row height.
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(DEVICE_MENU_WIDTH)
            ) {
                CompactDropdownMenuItem(
                    text = { DeviceMenuText(DEFAULT_LABEL) },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    }
                )
                devices.forEach { device ->
                    CompactDropdownMenuItem(
                        text = {
                            DeviceMenuText(
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

/** One row of a device menu: never wraps, ellipsized if even the wide menu cannot hold it. */
@Composable
private fun DeviceMenuText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

private const val DEFAULT_LABEL = "System Default"

/** Matches the dialog body width so a device name has as much room as the dialog can give it. */
private val DEVICE_MENU_WIDTH = 460.dp

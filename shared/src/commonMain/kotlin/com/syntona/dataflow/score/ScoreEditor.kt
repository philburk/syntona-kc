package com.syntona.dataflow.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.dataflow.editor.CompactDropdownMenuItem
import com.syntona.dataflow.editor.FileState
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.GraphViewSettings
import com.syntona.ui.PlayIcon
import com.syntona.ui.StopIcon
import org.jetbrains.compose.resources.Font
import syntonakc.shared.generated.resources.Res
import syntonakc.shared.generated.resources.leland

@Composable
fun ScoreEditor(module: ScoreModule, state: GraphState) {
    val isDark = GraphViewSettings.isDarkMode
    val bgColor = if (isDark) Color(0xFF1A1A2E) else Color(0xFFFFFDF8)
    val toolbarBg = if (isDark) Color(0xFF12122A) else Color(0xFFE2E8F0)
    val toolbarText = if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)
    val borderMuted = if (isDark) Color(0x33FFFFFF) else Color(0x33000000)

    val lelandFont = Font(Res.font.leland)
    val musicFontFamily = remember(lelandFont) { FontFamily(lelandFont) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    var staffSpaceDp by remember { mutableStateOf(10f) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Minimal Phase 1 toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(toolbarBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(
                onClick = { module.requestPlayToggle() },
                modifier = Modifier
                    .height(30.dp)
                    .focusProperties { canFocus = false },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (module.isPlaying) {
                        StopIcon(color = Color(0xFFEF4444), size = 10.dp)
                        Text("Stop", fontSize = 12.sp, color = Color(0xFFEF4444))
                    } else {
                        val playColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                        PlayIcon(color = playColor, size = 10.dp)
                        Text("Play", fontSize = 12.sp, color = playColor)
                    }
                }
            }

            Text("|", color = borderMuted)

            ScoreKeySelector(
                selected = module.scoreData.keySignature,
                textColor = toolbarText,
                onSelect = {
                    module.scoreData.keySignature = it
                    FileState.markDirty()
                    state.markChanged()
                },
            )

            ScoreTimeSigSelector(
                selected = module.scoreData.timeSignature,
                textColor = toolbarText,
                onSelect = {
                    module.scoreData.timeSignature = it
                    FileState.markDirty()
                    state.markChanged()
                },
            )

            Text("|", color = borderMuted)

            Text("Size", fontSize = 10.sp, color = toolbarText)
            Slider(
                value = staffSpaceDp,
                onValueChange = { staffSpaceDp = it },
                valueRange = 6f..18f,
                modifier = Modifier.width(100.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (isDark) Color.White else Color.DarkGray,
                    activeTrackColor = if (isDark) Color.Yellow else Color(0xFFF59E0B),
                    inactiveTrackColor = if (isDark) Color.LightGray else Color(0xFF94A3B8),
                ),
            )
        }

        // Scrollable single-stave score canvas
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .horizontalScroll(scrollState)
        ) {
            val staffSpacePx = with(density) { staffSpaceDp.dp.toPx() }
            val minWidthPx = constraints.minWidth.toFloat().coerceAtLeast(600f)
            val canvasWidthDp = maxOf(
                maxWidth,
                with(density) { (staffSpacePx * 110f).toDp() }
            )
            val renderColors = ScoreRenderColors.forTheme(isDark)

            Canvas(
                modifier = Modifier
                    .width(canvasWidthDp)
                    .height(maxHeight)
            ) {
                val target = ComposeScoreDrawTarget(
                    drawScope = this,
                    textMeasurer = textMeasurer,
                    density = density,
                    musicFontFamily = musicFontFamily,
                )
                ScoreRenderer.render(
                    score = module.scoreData,
                    target = target,
                    staffSpacePx = staffSpacePx,
                    minWidthPx = minWidthPx,
                    colors = renderColors,
                    activeItemGlobalIndex = module.activeItemIndex,
                )
            }
        }
    }
}

@Composable
private fun ScoreKeySelector(
    selected: KeySignature,
    textColor: Color,
    onSelect: (KeySignature) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .height(30.dp)
                .focusProperties { canFocus = false },
        ) {
            Text("Key: ${selected.label}", fontSize = 11.sp, color = textColor)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            KeySignature.COMMON_PHASE1_PRESETS.forEach { preset ->
                CompactDropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ScoreTimeSigSelector(
    selected: TimeSignature,
    textColor: Color,
    onSelect: (TimeSignature) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .height(30.dp)
                .focusProperties { canFocus = false },
        ) {
            Text("Time: ${selected.label}", fontSize = 11.sp, color = textColor)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeSignature.PRESETS.forEach { preset ->
                CompactDropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

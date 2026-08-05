package com.syntona.dataflow.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.onKeyEvent
import com.syntona.dataflow.model.AudioInputJack
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.LinkModule
import com.syntona.dataflow.modules.KeyMatchModule
import com.syntona.dataflow.modules.KeyNotesModule
import com.syntona.dataflow.modules.PackVoiceModule
import com.syntona.dataflow.editor.FileState

private val HEADER_COLOR = Color(0xFFCC6666)
private val PANEL_COLOR = Color(0xFFF0F0F0)
private val BORDER_COLOR = Color(0xFFAAAAAA)
private val LABEL_COLOR = Color(0xFF444444)

@Composable
fun ModuleNameEditor(
    module: Module,
    modifier: Modifier = Modifier,
    textColor: Color = Color.LightGray,
    borderColor: Color = Color.Gray,
    onNameChanged: ((String) -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text("Name:", fontSize = 12.sp, color = textColor)
        var nicknameText by remember(module) { mutableStateOf(module.name) }
        Box(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = nicknameText,
                onValueChange = { input ->
                    val filtered = com.syntona.dataflow.model.cleanKotlinIdentifier(input)
                    nicknameText = filtered
                    if (filtered.isNotEmpty()) {
                        module.name = filtered
                        FileState.markDirty()
                        onNameChanged?.invoke(filtered)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )
        }
    }
}

/**
 * Floating editor panel for a [Module]. Shows an editable row for each [InputJack]:
 * Min / Value / Max number fields and a fader that sweeps the full Min–Max range.
 *
 * Rendered as an overlay inside [GraphEditor]; clicking outside (the backdrop) dismisses it.
 */
@Composable
fun ModuleEditor(module: Module, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(480.dp)
            .background(PANEL_COLOR, RoundedCornerShape(8.dp))
            .border(1.dp, BORDER_COLOR, RoundedCornerShape(8.dp))
            .onKeyEvent { true }
    ) {
        Column {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HEADER_COLOR, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(module.name, color = Color.White, fontSize = 14.sp)
            }

            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModuleNameEditor(
                    module = module,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    textColor = LABEL_COLOR,
                    borderColor = BORDER_COLOR
                )

                androidx.compose.material3.HorizontalDivider(color = BORDER_COLOR, thickness = 1.dp)

                if (module is LinkModule) {
                    LinkModuleEditor(module)
                } else if (module is KeyMatchModule) {
                    KeyMatchModuleEditor(module)
                } else if (module is KeyNotesModule) {
                    KeyNotesModuleEditor(module)
                } else if (module is PackVoiceModule) {
                    PackVoiceModuleEditor(module)
                } else if (module is InputModule) {
                    module.patchJack?.let { jack ->
                        JackEditorRow(jack)
                    }
                } else if (module is AudioInputModule) {
                    module.patchJack?.let { jack ->
                        JackEditorRow(jack)
                    }
                } else {
                    module.inputs.forEach { jack ->
                        JackEditorRow(jack)
                    }
                }
            }
        }
    }
}

@Composable
private fun JackEditorRow(jack: InputJack) {
    var currentValue by remember(jack) { mutableStateOf(jack.getValue()) }

    // FieldSet-style box: border with a legend overlapping the top edge.
    // The outer Box has no top padding so the legend Text sits at y=0 and
    // straddles the border, which is offset 8 dp down by the Column's own padding.
    Box(modifier = Modifier.fillMaxWidth()) {
        // Border and content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .border(1.dp, BORDER_COLOR, RoundedCornerShape(4.dp))
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Min / Value / Max row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Min:", fontSize = 11.sp, color = LABEL_COLOR, modifier = Modifier.width(28.dp))
                NumberField(
                    value = jack.min,
                    onCommit = { jack.min = it },
                    modifier = Modifier.width(80.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Value:", fontSize = 11.sp, color = LABEL_COLOR, modifier = Modifier.width(36.dp))
                NumberField(
                    value = currentValue,
                    onCommit = { newValue ->
                        val clamped = newValue.coerceIn(jack.min, jack.max)
                        jack.setValue(clamped)
                        currentValue = clamped
                    },
                    modifier = Modifier.width(80.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Max:", fontSize = 11.sp, color = LABEL_COLOR, modifier = Modifier.width(28.dp))
                NumberField(
                    value = jack.max,
                    onCommit = { jack.max = it },
                    modifier = Modifier.width(80.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))
                Text("Kick?", fontSize = 11.sp, color = LABEL_COLOR)
                var isKickable by remember(jack) { mutableStateOf(jack.isKickable) }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Checkbox(
                        checked = isKickable,
                        onCheckedChange = {
                            isKickable = it
                            jack.isKickable = it
                            FileState.markDirty()
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (jack is AudioInputJack) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add?", fontSize = 11.sp, color = LABEL_COLOR)
                    var isValueAdded by remember(jack) { mutableStateOf(jack.isValueAdded) }
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        Checkbox(
                            checked = isValueAdded,
                            onCheckedChange = {
                                isValueAdded = it
                                jack.isValueAdded = it
                                FileState.markDirty()
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Fader
            val range = jack.max - jack.min
            val interactionSource = remember { MutableInteractionSource() }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Slider(
                    value = if (range > 0.0) {
                        ((currentValue - jack.min) / range).toFloat().coerceIn(0f, 1f)
                    } else 0f,
                    onValueChange = { fraction ->
                        val newValue = jack.min + fraction * range
                        currentValue = newValue
                        jack.setValue(newValue)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = interactionSource,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            thumbSize = DpSize(10.dp, 10.dp)
                        )
                    }
                )
            }
        }

        // Legend overlapping top border
        Text(
            text = jack.name,
            fontSize = 11.sp,
            color = LABEL_COLOR,
            modifier = Modifier
                .offset(y = -2.dp)
                .padding(start = 6.dp)
                .background(PANEL_COLOR)
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun LinkModuleEditor(module: LinkModule) {
    var filePathText by remember(module) { mutableStateOf(module.filePath) }
    var buttonTextVal by remember(module) { mutableStateOf(module.buttonText) }
    var expanded by remember { mutableStateOf(false) }
    val files by produceState<List<String>>(emptyList()) {
        value = com.syntona.SharedResources.getFilesIndex().filter { it.endsWith(com.syntona.SYNTONA_FILE_SUFFIX) }
    }

    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Button Text
        Column {
            Text("Button Text:", fontSize = 12.sp, color = LABEL_COLOR)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BORDER_COLOR, RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = buttonTextVal,
                    onValueChange = {
                        buttonTextVal = it
                        module.buttonText = it
                        FileState.markDirty()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }
        }

        // File Path
        Column {
            Text("File Path:", fontSize = 12.sp, color = LABEL_COLOR)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, BORDER_COLOR, RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = filePathText,
                        onValueChange = {
                            filePathText = it
                            module.filePath = it
                            FileState.markDirty()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                }

                // Dropdown trigger
                Box {
                    androidx.compose.material3.IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("▼", fontSize = 12.sp)
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        files.forEach { path ->
                            CompactDropdownMenuItem(
                                text = { Text(path, fontSize = 12.sp) },
                                onClick = {
                                    filePathText = path
                                    module.filePath = path
                                    FileState.markDirty()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackVoiceModuleEditor(module: PackVoiceModule) {
    var expanded by remember { mutableStateOf(false) }
    var opcode by remember(module) { mutableStateOf(module.opcode) }

    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Opcode:", fontSize = 12.sp, color = LABEL_COLOR)
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .border(1.dp, BORDER_COLOR, RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clickable { expanded = true }
                ) {
                    Text(opcode.name, fontSize = 13.sp)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    Message.VoiceOpcode.entries.forEach { candidate ->
                        CompactDropdownMenuItem(
                            text = { Text(candidate.name, fontSize = 12.sp) },
                            onClick = {
                                module.opcode = candidate
                                opcode = candidate
                                FileState.markDirty()
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        androidx.compose.material3.HorizontalDivider(color = BORDER_COLOR, thickness = 1.dp)

        module.inputs.filter { it !== module.opcodeIn }.forEach { jack ->
            JackEditorRow(jack)
        }
    }
}

@Composable
private fun KeyMatchModuleEditor(module: KeyMatchModule) {
    var keyCharText by remember(module) { mutableStateOf(module.keyChar.toString()) }

    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Character to Match:", fontSize = 12.sp, color = LABEL_COLOR)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .border(1.dp, BORDER_COLOR, RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = keyCharText,
                    onValueChange = {
                        val newText = it.take(1).uppercase()
                        keyCharText = newText
                        module.keyChar = if (newText.isNotEmpty()) newText[0] else ' '
                        FileState.markDirty()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }
        }
    }
}

@Composable
private fun KeyNotesModuleEditor(module: KeyNotesModule) {
    var bottomKeysText by remember(module) { mutableStateOf(module.bottomOctaveKeys) }
    var topKeysText by remember(module) { mutableStateOf(module.topOctaveKeys) }
    var startingNoteText by remember(module) { mutableStateOf(module.startingNote.toString()) }

    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Top Octave Keys:", fontSize = 12.sp, color = LABEL_COLOR)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BORDER_COLOR, RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = topKeysText,
                    onValueChange = {
                        topKeysText = it
                        module.topOctaveKeys = it
                        FileState.markDirty()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }
        }

        Column {
            Text("Bottom Octave Keys:", fontSize = 12.sp, color = LABEL_COLOR)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BORDER_COLOR, RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = bottomKeysText,
                    onValueChange = {
                        bottomKeysText = it
                        module.bottomOctaveKeys = it
                        FileState.markDirty()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }
        }

        Column {
            Text("Starting Midi Note:", fontSize = 12.sp, color = LABEL_COLOR)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .border(1.dp, BORDER_COLOR, RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = startingNoteText,
                    onValueChange = {
                        startingNoteText = it
                        it.toIntOrNull()?.let { note ->
                            module.startingNote = note
                            FileState.markDirty()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }
        }
    }
}

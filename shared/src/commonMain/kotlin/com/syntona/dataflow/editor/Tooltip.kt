package com.syntona.dataflow.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.syntona.dataflow.model.Module
import com.syntona.dataflow.modules.UnitGeneratorModule
import com.syntona.dataflow.registry.ModuleRegistry

@Composable
fun TooltipArea(
    text: String,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset? = null,
    content: @Composable () -> Unit
) {
    if (text.isEmpty()) {
        content()
        return
    }
    var isHovered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var hoverJob by remember { mutableStateOf<Job?>(null) }
    var width by remember { mutableStateOf(0) }
    var height by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .onSizeChanged {
                width = it.width
                height = it.height
            }
            .pointerInput(text) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> {
                                hoverJob?.cancel()
                                hoverJob = scope.launch {
                                    delay(400)
                                    isHovered = true
                                }
                            }
                            PointerEventType.Exit -> {
                                hoverJob?.cancel()
                                isHovered = false
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        content()
        if (isHovered) {
            val resolvedOffset = offset ?: when (alignment) {
                Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd -> IntOffset(0, height + 4)
                else -> IntOffset(width + 4, 0)
            }
            androidx.compose.ui.window.Popup(
                alignment = alignment,
                offset = resolvedOffset,
                properties = androidx.compose.ui.window.PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = {
                    hoverJob?.cancel()
                    isHovered = false
                }
            ) {
                Surface(
                    color = Color(0xFF333333),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color(0xFF555555)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

fun getModuleTooltipText(module: Module): String {
    val type = when (module) {
        is com.syntona.dataflow.modules.PolyPatchModule -> "polyPatch"
        is com.syntona.dataflow.modules.PatchModule -> "patch"
        else -> module.type
    }
    val bundlePath = ModuleRegistry.getBundlePath(type) ?: type
    val description = ModuleRegistry.getDescription(type)
    val lines = mutableListOf<String>()
    lines.add(bundlePath)
    if (!description.isNullOrBlank()) {
        lines.add(description)
    }
    if (module is UnitGeneratorModule) {
        val qName = module.unit::class.qualifiedName ?: module.unit::class.simpleName ?: ""
        if (qName.isNotEmpty()) lines.add(qName)
    }
    if (com.syntona.dataflow.registry.HelpRegistry.hasHelpFile(type)) {
        lines.add("(Help available)")
    }
    return lines.joinToString("\n")
}

fun getModuleEntryTooltipText(entry: ModuleRegistry.ModuleEntry): String {
    val bundlePath = ModuleRegistry.getBundlePath(entry.name) ?: entry.name
    val description = entry.description ?: ModuleRegistry.getDescription(entry.name)
    val lines = mutableListOf<String>()
    lines.add(bundlePath)
    if (!description.isNullOrBlank()) {
        lines.add(description)
    }
    val factory = ModuleRegistry.ksynFactories[entry.name]
    if (factory != null) {
        val unit = try { factory() } catch (_: Exception) { null }
        val qName = unit?.let { it::class.qualifiedName ?: it::class.simpleName } ?: ""
        if (qName.isNotEmpty()) lines.add(qName)
    }
    if (com.syntona.dataflow.registry.HelpRegistry.hasHelpFile(entry.name)) {
        lines.add("(Help available)")
    }
    return lines.joinToString("\n")
}

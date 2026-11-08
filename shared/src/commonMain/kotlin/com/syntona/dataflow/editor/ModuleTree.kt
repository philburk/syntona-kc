package com.syntona.dataflow.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.SyntonaVersion
import com.syntona.dataflow.registry.ModuleRegistry

import com.syntona.ui.DisclosureTriangle
import com.syntona.ui.SearchIcon

@Composable
fun ModuleTree(state: GraphState) {
    val colors = LocalGraphColors.current
    var filterText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxHeight().width(188.dp).padding(8.dp).onKeyEvent { true }) {
        Text("Syntona v" + SyntonaVersion, fontSize = 16.sp, color = colors.sidebarText)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                SearchIcon(color = colors.sidebarText, size = 12.dp, modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "Filter:",
                    fontSize = 12.sp,
                    color = colors.sidebarText
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.sidebarFilterBackground, RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                BasicTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.sidebarFilterText, fontSize = 12.sp),
                    cursorBrush = SolidColor(colors.sidebarModuleText),
                    modifier = Modifier.fillMaxWidth().onKeyEvent { true }
                )
            }
        }

        LazyColumn {
            item {
                BundleView(
                    bundle = ModuleRegistry.root,
                    level = 0,
                    state = state,
                    filterText = filterText.trim(),
                    onClearFilter = { filterText = "" }
                )
            }
        }
    }
}

private fun ModuleRegistry.Bundle.hasMatchingModules(filter: String): Boolean {
    if (filter.isBlank()) return true
    if (modules.any { it.name.contains(filter, ignoreCase = true) }) return true
    return subBundles.any { it.hasMatchingModules(filter) }
}

@Composable
fun BundleView(
    bundle: ModuleRegistry.Bundle,
    level: Int,
    state: GraphState,
    filterText: String = "",
    onClearFilter: () -> Unit = {}
) {
    if (!bundle.hasMatchingModules(filterText)) return

    val colors = LocalGraphColors.current
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var expandedState by remember { mutableStateOf(level < 2) }
    val isFiltering = filterText.isNotBlank()
    val isExpanded = expandedState || isFiltering

    Column(modifier = Modifier.padding(start = if (level == 0) 0.dp else 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedState = !isExpanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                DisclosureTriangle(
                    isExpanded = isExpanded,
                    color = Color.Gray,
                    size = 8.dp
                )
            }
            Text(bundle.name, fontSize = 14.sp, color = colors.sidebarText)
        }

        if (isExpanded) {
            bundle.subBundles.forEach { subBundle ->
                BundleView(
                    bundle = subBundle,
                    level = level + 1,
                    state = state,
                    filterText = filterText,
                    onClearFilter = onClearFilter
                )
            }
            val matchingModules = if (isFiltering) {
                bundle.modules.filter { it.name.contains(filterText, ignoreCase = true) }
            } else {
                bundle.modules
            }
            matchingModules.forEach { module ->
                var localOffset by remember { mutableStateOf(Offset.Zero) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { localOffset = it.positionInRoot() }
                        .pointerInput(module) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    mdiState.startTreeDrag(module.creator, localOffset + offset)
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    mdiState.updateTreeDragPosition(delta)
                                },
                                onDragEnd = {
                                    val creator = mdiState.treeDragCreator
                                    val pos = mdiState.treeDragPosition
                                    mdiState.cancelTreeDrag()
                                    if (creator != null) {
                                        val target = mdiState.graphStateAtPosition(pos, density)
                                        if (target != null) {
                                            target.addModule(creator(), (pos - target.rootOffset) / density)
                                            onClearFilter()
                                        }
                                    }
                                },
                                onDragCancel = {
                                    mdiState.cancelTreeDrag()
                                }
                            )
                        }
                        .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TooltipArea(text = getModuleEntryTooltipText(module)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = module.name,
                                fontSize = 13.sp,
                                color = colors.sidebarModuleText,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (com.syntona.dataflow.registry.HelpRegistry.hasHelpFile(module.name)) {
                                Text(
                                    text = " (?)",
                                    fontSize = 11.sp,
                                    color = if (colors.isDark) Color(0xFFFFCC00) else Color(0xFFD97706),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            openHelpForType(
                                                module.name,
                                                mdiState,
                                                state.engine,
                                                density,
                                                scope
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

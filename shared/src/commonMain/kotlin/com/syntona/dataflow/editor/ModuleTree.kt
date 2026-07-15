package com.syntona.dataflow.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.SyntonaVersion
import com.syntona.dataflow.registry.ModuleRegistry

@Composable
fun ModuleTree(state: GraphState) {
    Column(modifier = Modifier.fillMaxHeight().width(180.dp).padding(8.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn {
            item {
                Text("Syntona v" + SyntonaVersion, fontSize = 16.sp, color = Color.LightGray)
                BundleView(ModuleRegistry.root, 0, state)
            }
        }
    }
}

@Composable
fun BundleView(
    bundle: ModuleRegistry.Bundle,
    level: Int,
    state: GraphState
) {
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current.density
    var expanded by remember { mutableStateOf(level < 2) }

    Column(modifier = Modifier.padding(start = (level * 8).dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "▼" else "▶",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.width(12.dp)
            )
            Text(bundle.name, fontSize = 14.sp, color = Color.LightGray)
        }

        if (expanded) {
            bundle.subBundles.forEach { subBundle ->
                BundleView(subBundle, level + 1, state)
            }
            bundle.modules.forEach { module ->
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
                                        target?.addModule(creator(), (pos - target.rootOffset) / density)
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
                        Text(
                            text = module.name,
                            fontSize = 13.sp,
                            color = Color.Cyan
                        )
                    }
                }
            }
        }
    }
}

package com.syntona

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.SyntonaModel
import com.syntona.dataflow.editor.*
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.Message
import com.syntona.dataflow.modules.*

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        val syntonaModel = remember { SyntonaModel() }

        val rootPatch = remember {
            PatchModule("root").also { it.graphState.engine = syntonaModel.engine }.apply {
                graphState.apply {
                    /*
                    val kick = KickModule()
                    val random = RandomModule()
                    val add = AddModule()
                    val show = ShowModule()

                    addModule(kick, Offset(50f, 50f))
                    addModule(random, Offset(250f, 50f))
                    addModule(add, Offset(450f, 150f))
                    addModule(show, Offset(650f, 150f))

                    connect(kick.output, random.kick)
                    connect(random.output, add.inputA)
                    connect(add.output, show.input)
                     */
                }
            }
        }

        val mdiState = remember {
            MdiState().also { mdi ->
                mdi.activeGraphState = rootPatch.graphState
                mdi.open(
                    MdiWindowInfo(
                        id = rootPatch,
                        title = rootPatch.name,
                        position = Offset(10f, 10f),
                        closable = false,
                        graphState = rootPatch.graphState,
                        width = 800.dp,
                        height = 500.dp,
                        content = { GraphEditor(rootPatch.graphState) }
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!showContent) {
                Text("Choose top level editor:")
                Button(onClick = {
                    showContent = !showContent
                    syntonaModel.start()
                }) {
                    Text("DataFlow")
                }
                Button(onClick = {
                    showContent = !showContent
                    syntonaModel.start()
                }) {
                    Text("Hierarchy")
                }
            } else {
                CompositionLocalProvider(LocalMdiState provides mdiState) {
                    val appFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { appFocusRequester.requestFocus() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(appFocusRequester)
                            .focusable()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete)) {
                                    mdiState.activeGraphState?.deleteSelectedModules()
                                    true
                                } else false
                            }
                            .onGloballyPositioned { mdiState.appRootOffset = it.positionInRoot() }
                    ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Panel: Module Tree
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(200.dp)
                                .background(Color(0xFF252525))
                        ) {
                            ModuleTree(state = rootPatch.graphState)
                        }

                        // Right Panel: Toolbar + MDI Desktop
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // Toolbar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(Color(0xFF1A1A1A))
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val engine = syntonaModel.engine
                                        val time = ScoreClock.now()
                                        rootPatch.graphState.modules
                                            .filterIsInstance<KickModule>()
                                            .forEach { module ->
                                                engine.post(SendOutputCommand(
                                                    module.output,
                                                    Message.FloatValue(time, 0.0)
                                                ))
                                            }
                                    }
                                ) {
                                    Text("Kick Graph")
                                }
                            }

                            // MDI Desktop
                            MdiDesktop(
                                mdiState = mdiState,
                                modifier = Modifier.weight(1f).background(Color(0xFF181818))
                            )
                        }
                    }

                    // Ghost rectangle shown while dragging from ModuleTree
                    mdiState.treeDragCreator?.let {
                        Box(
                            modifier = Modifier
                                .offset {
                                    val pos = mdiState.treeDragPosition - mdiState.appRootOffset
                                    IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                }
                                .width(140.dp)
                                .height(60.dp)
                                .background(Color.Cyan.copy(alpha = 0.3f))
                                .border(1.dp, Color.Cyan, RoundedCornerShape(4.dp))
                        )
                    }
                    } // end outer Box
                }
            }
        }
    }
}

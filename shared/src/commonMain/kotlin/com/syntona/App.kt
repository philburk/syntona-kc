package com.syntona

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.syntona.core.model.SyntonaModel
import com.syntona.dataflow.editor.GraphEditor
import com.syntona.dataflow.editor.GraphState
import com.syntona.dataflow.editor.ModuleTree
import com.syntona.dataflow.modules.AddModule
import com.syntona.dataflow.modules.KickModule
import com.syntona.dataflow.modules.PrintModule
import com.syntona.dataflow.modules.RandomModule
import com.syntona.dataflow.modules.ShowModule

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        val syntonaModel = remember { SyntonaModel() }

        val modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .safeContentPadding()
            .fillMaxSize()

        val graphState = remember {
            GraphState().apply {
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
            }
        }

        Column(
            modifier = modifier,
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
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Panel: Module Tree
                    Box(modifier = Modifier.fillMaxHeight().width(200.dp).background(Color(0xFF252525))) {
                        ModuleTree(state = graphState)
                    }

                    // Main Panel: Graph Editor
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        GraphEditor(graphState)

                        // Simple "Kick" button to trigger the graph
                        Button(
                            onClick = {
                                graphState.modules.filterIsInstance<KickModule>().forEach { it.kick() }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                        ) {
                            Text("Kick Graph")
                        }
                    }
                }
            }
        }
    }
}

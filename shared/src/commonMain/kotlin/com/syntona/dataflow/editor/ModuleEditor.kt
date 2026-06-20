package com.syntona.dataflow.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.dataflow.model.InputJack
import com.syntona.dataflow.model.Module

private val HEADER_COLOR = Color(0xFFCC6666)
private val PANEL_COLOR = Color(0xFFF0F0F0)
private val BORDER_COLOR = Color(0xFFAAAAAA)
private val LABEL_COLOR = Color(0xFF444444)

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
                module.inputs.forEach { jack ->
                    JackEditorRow(jack)
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
            }

            // Fader
            val range = jack.max - jack.min
            Slider(
                value = if (range > 0.0) {
                    ((currentValue - jack.min) / range).toFloat().coerceIn(0f, 1f)
                } else 0f,
                onValueChange = { fraction ->
                    val newValue = jack.min + fraction * range
                    currentValue = newValue
                    jack.setValue(newValue)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Legend overlapping top border
        Text(
            text = jack.name,
            fontSize = 11.sp,
            color = LABEL_COLOR,
            modifier = Modifier
                .padding(start = 6.dp)
                .background(PANEL_COLOR)
                .padding(horizontal = 4.dp)
        )
    }
}

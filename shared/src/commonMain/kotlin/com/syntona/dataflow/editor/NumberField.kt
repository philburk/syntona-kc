package com.syntona.dataflow.editor

import kotlin.math.pow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DIRTY_BACKGROUND = Color(0xFFFFB0B0)
private val CLEAN_BACKGROUND = Color.White

/**
 * A single-line numeric text field.
 *
 * Displays [value] as formatted text. When the user edits the content the background
 * turns pink (dirty). Pressing Enter commits the parsed value via [onCommit] and
 * resets the background. If the text cannot be parsed as a Double, Enter is ignored.
 *
 * External changes to [value] are reflected in the display only while the field is clean.
 */
@Composable
fun NumberField(
    value: Double,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(formatNumber(value)) }
    var isDirty by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDirty) text = formatNumber(value)
    }

    fun commit() {
        text.toDoubleOrNull()?.let { parsed ->
            onCommit(parsed)
            isDirty = false
        }
    }

    BasicTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            isDirty = true
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = 12.sp, color = Color.Black),
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier
            .background(if (isDirty) DIRTY_BACKGROUND else CLEAN_BACKGROUND)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                    commit()
                    true
                } else false
            }
            .onKeyEvent { true }
    )
}

fun formatNumber(value: Double): String {
    if (!value.isFinite()) return value.toString()
    if (value == 0.0) return "0"
    val absVal = kotlin.math.abs(value)
    val exp = kotlin.math.floor(kotlin.math.log10(absVal)).toInt()
    return if (exp in -4..3) {
        val places = maxOf(0, 3 - exp)
        val factor = 10.0.pow(places)
        val rounded = kotlin.math.round(value * factor) / factor
        if (places == 0) rounded.toLong().toString()
        else rounded.toString().let { if ('.' in it) it.trimEnd('0').trimEnd('.') else it }
    } else {
        val mantissa = value / 10.0.pow(exp)
        val rm = kotlin.math.round(mantissa * 1000.0) / 1000.0
        rm.toString().let { if ('.' in it) it.trimEnd('0').trimEnd('.') else it } + "e$exp"
    }
}

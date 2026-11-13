package com.syntona.dataflow.editor

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword
import com.syntona.dataflow.score.NoteDuration

@OptIn(ExperimentalComposeUiApi::class)
actual fun resizeCornerCursor(): PointerIcon = PointerIcon.fromKeyword("se-resize")

@OptIn(ExperimentalComposeUiApi::class)
actual fun resizeHorizontalCursor(): PointerIcon = PointerIcon.fromKeyword("ew-resize")

@OptIn(ExperimentalComposeUiApi::class)
actual fun moveCursor(): PointerIcon = PointerIcon.fromKeyword("move")

@OptIn(ExperimentalComposeUiApi::class)
actual fun crosshairCursor(): PointerIcon = PointerIcon.fromKeyword("crosshair")

private data class WasmCursorKey(
    val duration: NoteDuration,
    val isRest: Boolean,
    val isDarkMode: Boolean,
)

private val wasmScoreCursorCache = mutableMapOf<WasmCursorKey, PointerIcon>()

@OptIn(ExperimentalComposeUiApi::class)
actual fun scoreSymbolCursor(
    duration: NoteDuration,
    isRest: Boolean,
    isDarkMode: Boolean,
): PointerIcon {
    val key = WasmCursorKey(duration, isRest, isDarkMode)
    wasmScoreCursorCache[key]?.let { return it }
    val fg = if (isDarkMode) "%23F8FAFC" else "%23111827"
    val halo = if (isDarkMode) "%230F172A" else "%23FFFFFF"
    val body = svgSymbolBody(duration, isRest, fg, halo)
    val svg = "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'>$body</svg>"
    val encoded = svg
        .replace("<", "%3C")
        .replace(">", "%3E")
        .replace(" ", "%20")
    val (hotX, hotY) = if (isRest) 16 to 16 else 10 to 23
    val icon = PointerIcon.fromKeyword("url(\"data:image/svg+xml,$encoded\") $hotX $hotY, crosshair")
    wasmScoreCursorCache[key] = icon
    return icon
}

private fun svgSymbolBody(
    duration: NoteDuration,
    isRest: Boolean,
    fg: String,
    halo: String,
): String {
    if (!isRest) {
        val sb = StringBuilder()
        val head = if (duration == NoteDuration.WHOLE) {
            "<ellipse cx='10' cy='23' rx='5.5' ry='3.5' fill='none' stroke-width='2.2'/>"
        } else if (duration == NoteDuration.HALF) {
            "<ellipse cx='10' cy='23' rx='4.8' ry='3.3' transform='rotate(-20 10 23)' fill='none' stroke-width='2'/>"
        } else {
            "<ellipse cx='10' cy='23' rx='4.8' ry='3.3' transform='rotate(-20 10 23)'/>"
        }
        val stemTop = if (duration.flagCount >= 3) 4 else 5
        val stem = if (duration.hasStem) {
            "<line x1='14' y1='22' x2='14' y2='$stemTop' stroke-width='1.8' stroke-linecap='round'/>"
        } else {
            ""
        }
        val flags = buildString {
            for (i in 0 until duration.flagCount) {
                val fy = stemTop + i * 4
                append("<path d='M14,$fy C19,${fy + 1.5} 20.5,${fy + 5.5} 17.5,${fy + 8.5}' fill='none' stroke-width='1.8' stroke-linecap='round'/>")
            }
        }
        sb.append("<g stroke='$halo' fill='$halo' stroke-width='3.8'>$head$stem$flags</g>")
        sb.append("<g stroke='$fg' fill='$fg'>$head$stem$flags</g>")
        return sb.toString()
    } else {
        val inner = when (duration) {
            NoteDuration.WHOLE ->
                "<line x1='7' y1='13' x2='25' y2='13' stroke-width='1.5'/><rect x='11' y='13' width='10' height='4'/>"
            NoteDuration.HALF ->
                "<line x1='7' y1='18' x2='25' y2='18' stroke-width='1.5'/><rect x='11' y='14' width='10' height='4'/>"
            NoteDuration.QUARTER ->
                "<path d='M13,6 L18,12 L13.5,16.5 L18.5,22 C14.5,21.5 12.5,24.5 16.5,27' fill='none' stroke-width='2.3' stroke-linecap='round' stroke-linejoin='round'/>"
            NoteDuration.EIGHTH, NoteDuration.SIXTEENTH, NoteDuration.THIRTY_SECOND -> buildString {
                val count = duration.flagCount
                val startY = if (count >= 3) 6 else 8
                append("<line x1='18.5' y1='$startY' x2='13.5' y2='25' stroke-width='1.8' stroke-linecap='round'/>")
                for (i in 0 until count) {
                    val by = startY + i * 4.5
                    val bx = 18.5 - i * 1.1
                    append("<path d='M$bx,$by C${bx - 4},${by + 2} ${bx - 6},${by + 1} ${bx - 6.5},${by - 0.5}' fill='none' stroke-width='1.8' stroke-linecap='round'/>")
                    append("<circle cx='${bx - 7.5}' cy='$by' r='2.1'/>")
                }
            }
        }
        return "<g stroke='$halo' fill='$halo' stroke-width='3.8'>$inner</g><g stroke='$fg' fill='$fg'>$inner</g>"
    }
}

package com.syntona.dataflow.editor

import androidx.compose.ui.input.pointer.PointerIcon
import com.syntona.dataflow.score.NoteDuration

expect fun resizeCornerCursor(): PointerIcon

expect fun resizeHorizontalCursor(): PointerIcon

expect fun moveCursor(): PointerIcon

expect fun crosshairCursor(): PointerIcon

expect fun scoreSymbolCursor(
    duration: NoteDuration,
    isRest: Boolean,
    isDarkMode: Boolean,
): PointerIcon

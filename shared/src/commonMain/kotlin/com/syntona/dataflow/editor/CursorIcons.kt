package com.syntona.dataflow.editor

import androidx.compose.ui.input.pointer.PointerIcon

expect fun resizeCornerCursor(): PointerIcon

expect fun resizeHorizontalCursor(): PointerIcon

expect fun moveCursor(): PointerIcon

expect fun crosshairCursor(): PointerIcon

package com.syntona.dataflow.editor

import androidx.compose.ui.input.pointer.PointerIcon
import android.view.PointerIcon as AndroidPointerIcon

actual fun resizeCornerCursor(): PointerIcon = PointerIcon(AndroidPointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW)

actual fun resizeHorizontalCursor(): PointerIcon = PointerIcon(AndroidPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)

actual fun moveCursor(): PointerIcon = PointerIcon(AndroidPointerIcon.TYPE_ALL_SCROLL)

actual fun crosshairCursor(): PointerIcon = PointerIcon.Crosshair

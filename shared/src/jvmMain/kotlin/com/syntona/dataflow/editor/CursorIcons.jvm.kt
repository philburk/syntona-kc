package com.syntona.dataflow.editor

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun resizeCornerCursor(): PointerIcon = PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR))

actual fun resizeHorizontalCursor(): PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

actual fun moveCursor(): PointerIcon = PointerIcon(Cursor(Cursor.MOVE_CURSOR))

actual fun crosshairCursor(): PointerIcon = PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR))

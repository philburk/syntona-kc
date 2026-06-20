package com.syntona.dataflow.editor

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun resizeCornerCursor(): PointerIcon = PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR))

package com.syntona.dataflow.editor

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword

@OptIn(ExperimentalComposeUiApi::class)
actual fun resizeCornerCursor(): PointerIcon = PointerIcon.fromKeyword("se-resize")

@OptIn(ExperimentalComposeUiApi::class)
actual fun resizeHorizontalCursor(): PointerIcon = PointerIcon.fromKeyword("ew-resize")

@OptIn(ExperimentalComposeUiApi::class)
actual fun moveCursor(): PointerIcon = PointerIcon.fromKeyword("move")

@OptIn(ExperimentalComposeUiApi::class)
actual fun crosshairCursor(): PointerIcon = PointerIcon.fromKeyword("crosshair")

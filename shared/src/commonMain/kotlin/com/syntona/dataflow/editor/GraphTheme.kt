package com.syntona.dataflow.editor

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class GraphColors(
    val background: Color,
    val gridLines: Color,
    val selectionRect: Color,

    // Module container colors
    val moduleBorder: Color,
    val moduleBorderSelected: Color,
    val moduleBackground: Color,
    val moduleBackgroundSelected: Color,
    val modulePatchBackground: Color,
    val moduleScopeBackground: Color,
    
    // Header backgrounds
    val headerDefault: Color,
    val headerPatch: Color,
    val headerAudio: Color,
    val headerInputOutput: Color,
    val headerSequence: Color,
    val headerKnob: Color,
    val headerComment: Color,

    // Button backgrounds (kick/load etc.)
    val buttonBg: Color,
    val buttonBgPressed: Color,
    
    // Jack connector color indicators
    val jackDefault: Color,
    val jackKickable: Color,
    val jackAudio: Color,
    val jackVoice: Color,
    val jackDataQueue: Color,
    
    // Jack background pads
    val jackBgDefault: Color,
    val jackBgAudio: Color,
    val jackBgVoice: Color,
    val jackBgDataQueue: Color,
    val jackBgHighlighted: Color,

    // Wire lines
    val wireDefault: Color,
    val wireAudio: Color,
    val wireVoice: Color,
    val wireDataQueue: Color,
    val wireDragging: Color,

    // Fader specifics
    val faderGrip: Color
)

val DarkGraphColors = GraphColors(
    background = Color(0xFF1E1E1E),
    gridLines = Color(0xFF2D2D2D),
    selectionRect = Color.Cyan,

    moduleBorder = Color.Black,
    moduleBorderSelected = Color(0xFFFFA500),
    moduleBackground = Color.DarkGray,
    moduleBackgroundSelected = Color.DarkGray,
    modulePatchBackground = Color(0xFF1A3A2A),
    moduleScopeBackground = Color(0xFF1E1E1E),

    headerDefault = Color.Blue,
    headerPatch = Color(0xFF2E7D52),
    headerAudio = Color(0xFF1A5C1A),
    headerInputOutput = Color(0xFF6A3D00),
    headerSequence = Color(0xFF2B506E),
    headerKnob = Color(0xFF1565C0),
    headerComment = Color(0xFF4A4A4A),

    buttonBg = Color(0xFF3E3E3E),
    buttonBgPressed = Color.Gray,

    jackDefault = Color.Cyan,
    jackKickable = Color.Red,
    jackAudio = Color(0xFF44DD44),
    jackVoice = Color(0xFFFFA500),
    jackDataQueue = Color.Yellow,

    jackBgDefault = Color.DarkGray,
    jackBgAudio = Color(0xFF1A3A1A),
    jackBgVoice = Color(0xFF4E3610),
    jackBgDataQueue = Color(0xFF3A3A1A),
    jackBgHighlighted = Color(180, 170, 130),

    wireDefault = Color.Gray,
    wireAudio = Color(0xFF44DD44),
    wireVoice = Color(0xFFD0A500),
    wireDataQueue = Color.Yellow,
    wireDragging = Color.Yellow,

    faderGrip = Color(0xFF888888)
)

val LightGraphColors = GraphColors(
    background = Color(0xFFF5F5F5),
    gridLines = Color(0xFFE0E0E0),
    selectionRect = Color(0xFF00ACC1),

    moduleBorder = Color(0xFFB0BEC5),
    moduleBorderSelected = Color(0xFFE65100),
    moduleBackground = Color.White,
    moduleBackgroundSelected = Color(0xFFECEFF1),
    modulePatchBackground = Color(0xFFE8F5E9),
    moduleScopeBackground = Color(0xFFF5F5F5),

    headerDefault = Color(0xFF1976D2),
    headerPatch = Color(0xFF4CAF50),
    headerAudio = Color(0xFF2E7D32),
    headerInputOutput = Color(0xFFEF6C00),
    headerSequence = Color(0xFF0288D1),
    headerKnob = Color(0xFF2196F3),
    headerComment = Color(0xFF78909C),

    buttonBg = Color(0xFFE0E0E0),
    buttonBgPressed = Color(0xFFBDBDBD),

    jackDefault = Color(0xFF00ACC1),
    jackKickable = Color(0xFFD32F2F),
    jackAudio = Color(0xFF388E3C),
    jackVoice = Color(0xFFF57C00),
    jackDataQueue = Color(0xFFFBC02D),

    jackBgDefault = Color(0xFFECEFF1),
    jackBgAudio = Color(0xFFC8E6C9),
    jackBgVoice = Color(0xFFFFE0B2),
    jackBgDataQueue = Color(0xFFFFF9C4),
    jackBgHighlighted = Color(220, 210, 160),

    wireDefault = Color(0xFF90A4AE),
    wireAudio = Color(0xFF388E3C),
    wireVoice = Color(0xFFF57C00),
    wireDataQueue = Color(0xFFFBC02D),
    wireDragging = Color(0xFFFBC02D),

    faderGrip = Color(0xFF78909C)
)

val LocalGraphColors = staticCompositionLocalOf { DarkGraphColors }

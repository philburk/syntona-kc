package com.syntona.dataflow.editor

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class GraphColors(
    val isDark: Boolean,
    val background: Color,
    val gridLines: Color,
    val selectionRect: Color,

    // App shell (toolbar, sidebar, MDI desktop & window chrome)
    val toolbarBackground: Color,
    val toolbarText: Color,
    val sidebarBackground: Color,
    val sidebarText: Color,
    val sidebarModuleText: Color,
    val sidebarFilterBackground: Color,
    val sidebarFilterText: Color,
    val desktopBackground: Color,
    val windowSurface: Color,
    val windowBorder: Color,
    val windowTitleBar: Color,
    val windowTitleText: Color,

    // Module container colors
    val moduleBorder: Color,
    val moduleBorderSelected: Color,
    val moduleBackground: Color,
    val moduleBackgroundSelected: Color,
    val modulePatchBackground: Color,
    val moduleScopeBackground: Color,
    val moduleText: Color,
    val moduleSecondaryText: Color,
    val moduleDivider: Color,
    val commentText: Color,

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
    val jackSequentialData: Color,
    val jackUniversal: Color,
    val jackUniversalBorder: Color,

    // Jack background pads
    val jackBgDefault: Color,
    val jackBgAudio: Color,
    val jackBgVoice: Color,
    val jackBgSequentialData: Color,
    val jackBgUniversal: Color,
    val jackBgHighlighted: Color,
    val jackText: Color,
    /** Fill for a command button whose module is armed and waiting for input. */
    val buttonBgArmed: Color,

    // Wire lines
    val wireDefault: Color,
    val wireAudio: Color,
    val wireVoice: Color,
    val wireSequentialData: Color,
    val wireUniversal: Color,
    val wireDragging: Color,

    // Fader specifics
    val faderGrip: Color
)

val DarkGraphColors = GraphColors(
    isDark = true,
    background = Color(0xFF1E1E1E),
    gridLines = Color(0xFF2D2D2D),
    selectionRect = Color.Cyan,

    toolbarBackground = Color(0xFF1A1A1A),
    toolbarText = Color.White,
    sidebarBackground = Color(0xFF252525),
    sidebarText = Color.LightGray,
    sidebarModuleText = Color.Cyan,
    sidebarFilterBackground = Color(0xFF2A2A2A),
    sidebarFilterText = Color.White,
    desktopBackground = Color(0xFF181818),
    windowSurface = Color(0xFF2D2D2D),
    windowBorder = Color(0xFF555555),
    windowTitleBar = Color(0xFF3C3C3C),
    windowTitleText = Color(0xFFCCCCCC),

    moduleBorder = Color.Black,
    moduleBorderSelected = Color(0xFFFFA500),
    moduleBackground = Color.DarkGray,
    moduleBackgroundSelected = Color.DarkGray,
    modulePatchBackground = Color(0xFF1A3A2A),
    moduleScopeBackground = Color(0xFF1E1E1E),
    moduleText = Color.LightGray,
    moduleSecondaryText = Color.Gray,
    moduleDivider = Color.Black,
    commentText = Color.White,

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
    jackKickable = Color(0xFFE55353),
    jackAudio = Color(0xFF44DD44),
    jackVoice = Color(0xFFFFA500),
    jackSequentialData = Color.Yellow,
    jackUniversal = Color.White,
    jackUniversalBorder = Color(0xFFAAAAAA),

    jackBgDefault = Color.DarkGray,
    jackBgAudio = Color(0xFF1A3A1A),
    jackBgVoice = Color(0xFF4E3610),
    jackBgSequentialData = Color(0xFF3A3A1A),
    jackBgUniversal = Color(0xFF333333),
    jackBgHighlighted = Color(180, 170, 130),
    jackText = Color.LightGray,
    buttonBgArmed = Color(0xFFFF8C00),

    wireDefault = Color.Gray,
    wireAudio = Color(0xFF44DD44),
    wireVoice = Color(0xFFD0A500),
    wireSequentialData = Color.Yellow,
    wireUniversal = Color.White,
    wireDragging = Color.Yellow,

    faderGrip = Color(0xFF888888)
)

val LightGraphColors = GraphColors(
    isDark = false,
    background = Color(0xFFF5F5F5),
    gridLines = Color(0xFFE0E0E0),
    selectionRect = Color(0xFF00ACC1),

    toolbarBackground = Color(0xFFE0E0E0),
    toolbarText = Color(0xFF212121),
    sidebarBackground = Color(0xFFEEEEEE),
    sidebarText = Color(0xFF333333),
    sidebarModuleText = Color(0xFF007799),
    sidebarFilterBackground = Color.White,
    sidebarFilterText = Color(0xFF212121),
    desktopBackground = Color(0xFFD6D6D6),
    windowSurface = Color(0xFFFAFAFA),
    windowBorder = Color(0xFF9E9E9E),
    windowTitleBar = Color(0xFFDCDCDC),
    windowTitleText = Color(0xFF212121),

    moduleBorder = Color(0xFF90A4AE),
    moduleBorderSelected = Color(0xFFE65100),
    moduleBackground = Color.White,
    moduleBackgroundSelected = Color(0xFFECEFF1),
    modulePatchBackground = Color(0xFFF3EFE6),
    moduleScopeBackground = Color(0xFFF5F5F5),
    moduleText = Color(0xFF263238),
    moduleSecondaryText = Color(0xFF607D8B),
    moduleDivider = Color(0xFFB0BEC5),
    commentText = Color(0xFF212121),

    headerDefault = Color(0xFF1976D2),
    headerPatch = Color(0xFF9A8B6E),
    headerAudio = Color(0xFF2E7D32),
    headerInputOutput = Color(0xFFA1785C),
    headerSequence = Color(0xFF0288D1),
    headerKnob = Color(0xFF2196F3),
    headerComment = Color(0xFF78909C),

    buttonBg = Color(0xFFE0E0E0),
    buttonBgPressed = Color(0xFFBDBDBD),

    jackDefault = Color(0xFF00ACC1),
    jackKickable = Color(0xFFD32F2F),
    jackAudio = Color(0xFF22AA22),
    jackVoice = Color(0xFFC28538),
    jackSequentialData = Color(0xFFFBC02D),
    jackUniversal = Color.White,
    jackUniversalBorder = Color(0xFF606060),

    jackBgDefault = Color(0xFFECEFF1),
    jackBgAudio = Color(0xFFC8E6C9),
    jackBgVoice = Color(0xFFEDE0D4),
    jackBgSequentialData = Color(0xFFFFF9C4),
    jackBgUniversal = Color(0xFFE0E0E0),
    jackBgHighlighted = Color(220, 210, 160),
    jackText = Color(0xFF263238),
    buttonBgArmed = Color(0xFFF57C00),

    wireDefault = Color(0xFF3B74A8),
    wireAudio = Color(0xFF22AA22),
    wireVoice = Color(0xFFC28538),
    wireSequentialData = Color(0xFFFBC02D),
    wireUniversal = Color(0xFF546E7A),
    wireDragging = Color(0xFFFBC02D),

    faderGrip = Color(0xFF78909C)
)

val LocalGraphColors = staticCompositionLocalOf { LightGraphColors }

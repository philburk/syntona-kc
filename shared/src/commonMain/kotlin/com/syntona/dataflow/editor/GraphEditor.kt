package com.syntona.dataflow.editor

import com.syntona.ui.ArrowForwardIcon
import com.syntona.ui.DropdownArrow
import com.syntona.ui.LightbulbIcon
import com.syntona.ui.LoopIcon
import com.syntona.ui.RecordIcon
import com.syntona.ui.VerticalEllipsisIcon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.syntona.dataflow.modules.OneShotModule
import com.syntona.dataflow.modules.RecorderModule
import com.syntona.dataflow.modules.SampleReaderModule
import com.syntona.dataflow.modules.SampleWriterModule
import com.syntona.dataflow.modules.StepperModule
import com.syntona.dataflow.modules.StepValueModule
import com.syntona.dataflow.modules.TableModule
import com.syntona.util.formatMinutesSecondsMillis
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.syntona.dataflow.modules.EquationModule
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntona.core.model.ScoreClock
import com.syntona.core.model.ScoreTime
import com.syntona.dataflow.engine.KickJackCommand
import com.syntona.dataflow.engine.SetValueCommand
import com.syntona.dataflow.engine.SendOutputCommand
import com.syntona.dataflow.model.*
import com.softsynth.ksyn.compose.Oscilloscope
import com.syntona.dataflow.modules.NumberModule
import com.syntona.dataflow.modules.ScaleModule
import com.syntona.dataflow.modules.FaderModule
import com.syntona.dataflow.modules.MixerModule
import com.syntona.dataflow.modules.AudioInputModule
import com.syntona.dataflow.modules.AudioOutputModule
import com.syntona.dataflow.modules.AudioOutput2Module
import com.syntona.dataflow.modules.CommandModule
import com.syntona.dataflow.modules.FloatSampleModule
import com.syntona.dataflow.modules.SamplePlayerModule
import com.syntona.dataflow.modules.InputModule
import com.syntona.dataflow.modules.KnobModule
import com.syntona.dataflow.modules.OutputModule
import com.syntona.dataflow.modules.PatchModule
import com.syntona.dataflow.modules.PolyPatchModule
import com.syntona.dataflow.modules.ScopeModule
import com.syntona.dataflow.modules.VuMeterModule
import com.syntona.dataflow.modules.amplitudeToGraphicFraction
import com.syntona.dataflow.modules.VU_METER_CLIP_HOLD_TICKS
import com.syntona.dataflow.modules.VU_METER_ORANGE_THRESHOLD_FRACTION
import com.syntona.dataflow.modules.VU_METER_RED_THRESHOLD_FRACTION
import com.syntona.dataflow.modules.ShowModule
import com.syntona.dataflow.modules.GlowModule
import com.syntona.dataflow.modules.FlashModule
import com.syntona.dataflow.modules.SendModule
import com.syntona.dataflow.modules.ReceiveModule
import com.syntona.dataflow.modules.CommentModule
import com.syntona.dataflow.modules.KeyboardModule
import com.syntona.dataflow.modules.SEMITONES_PER_OCTAVE
import com.syntona.dataflow.modules.currentAmplitude
import com.softsynth.ksyn.compose.BlackWhiteKeyboard
import com.syntona.dataflow.modules.SequenceModule
import com.syntona.dataflow.modules.ModuloSelectModule
import com.syntona.dataflow.modules.EnvSegModule
import com.syntona.dataflow.modules.LinkModule
import com.syntona.dataflow.modules.KeyMatchModule
import com.syntona.dataflow.modules.MatchControlModule
import com.syntona.dataflow.modules.MatchControlSequentialModule
import com.syntona.dataflow.modules.MatchNoteOnModule
import com.syntona.dataflow.modules.MatchNoteOnSequentialModule
import com.syntona.dataflow.modules.BeatClockModule
import com.syntona.dataflow.modules.BeatLineModule
import com.syntona.dataflow.modules.TimelineBeatUtils
import com.syntona.dataflow.modules.TimelineModule
import com.syntona.dataflow.modules.TimelineTrigger
import com.syntona.dataflow.modules.TimelineTrack
import com.syntona.dataflow.modules.TrackType
import com.syntona.dataflow.modules.TrackSection
import com.syntona.dataflow.modules.PatchTrack
import com.syntona.dataflow.modules.MessageTrack
import com.syntona.dataflow.modules.MidiTrack
import com.syntona.dataflow.modules.AudioTrack
import com.syntona.dataflow.modules.MessageSectionData
import com.syntona.dataflow.modules.MidiSectionData
import com.syntona.dataflow.modules.AudioSectionData
import com.syntona.dataflow.modules.PianoRollTiming
import com.syntona.core.audio.SynthKSyn
import com.softsynth.audiobridge.AudioInputBridge
import com.syntona.dataflow.modules.KeyNotesModule
import com.syntona.dataflow.modules.PianoRollModule
import com.syntona.dataflow.modules.KickModule
import com.syntona.dataflow.modules.MidiInModule
import com.syntona.dataflow.modules.MidiOutModule
import com.syntona.dataflow.modules.OnLoadModule
import com.syntona.dataflow.modules.ShowManyModule
import com.softsynth.ksyn.compose.SegmentedEnvelopeEditor
import com.syntona.dataflow.model.cleanKotlinIdentifier
import com.syntona.dataflow.editor.FileState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun GraphEditor(state: GraphState, modifier: Modifier = Modifier) {
    val colors = LocalGraphColors.current
    val mdiState = LocalMdiState.current
    val focusRequester = remember { FocusRequester() }
    state.requestFocus = { focusRequester.requestFocus() }
    val editingModule = state.editingModule
    LaunchedEffect(editingModule) {
        if (editingModule == null) {
            focusRequester.requestFocus()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        val parentPatch = state.parentPatch
        if (parentPatch != null && !parentPatch.isRoot) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.moduleBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Patch Name:",
                    color = if (colors.background == Color(0xFF1E1E1E)) Color.LightGray else Color.DarkGray,
                    fontSize = 12.sp
                )
                var tempName by remember(parentPatch) { mutableStateOf(parentPatch.name) }
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .background(colors.background, RoundedCornerShape(4.dp))
                        .border(1.dp, colors.moduleBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .onKeyEvent { true }
                ) {
                    BasicTextField(
                        value = tempName,
                        onValueChange = { input ->
                            val filtered = cleanKotlinIdentifier(input)
                            tempName = filtered
                            if (filtered.isNotEmpty()) {
                                parentPatch.name = filtered
                                mdiState.windows.find { it.id === parentPatch }?.let { win ->
                                    win.title = filtered
                                }
                                FileState.markDirty()
                            }
                        },
                        textStyle = TextStyle(
                            color = if (colors.background == Color(0xFF1E1E1E)) Color.White else Color.Black,
                            fontSize = 12.sp
                        ),
                        cursorBrush = SolidColor(if (colors.background == Color(0xFF1E1E1E)) Color.White else Color.Black),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            HorizontalDivider(color = colors.gridLines, thickness = 1.dp)
        }

        // Viewport dimensions
        var viewportWidth by remember { mutableStateOf(0f) }
        var viewportHeight by remember { mutableStateOf(0f) }

        val localDensity = LocalDensity.current
        val density = localDensity.density
        val extent = state.computeContentExtent(density)
        val totalContentWidth = maxOf(viewportWidth, extent.width)
        val totalContentHeight = maxOf(viewportHeight, extent.height)

        val maxScrollX = maxOf(0f, totalContentWidth - viewportWidth)
        val maxScrollY = maxOf(0f, totalContentHeight - viewportHeight)

        // Clamp scroll positions if viewport size changes or modules are moved
        LaunchedEffect(maxScrollX) {
            if (state.scrollX > maxScrollX) state.scrollX = maxScrollX
        }
        LaunchedEffect(maxScrollY) {
            if (state.scrollY > maxScrollY) state.scrollY = maxScrollY
        }

        val contentWidthDp = with(localDensity) { totalContentWidth.toDp() }
        val contentHeightDp = with(localDensity) { totalContentHeight.toDp() }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.background)
                .clipToBounds()
                .onSizeChanged { size ->
                    viewportWidth = size.width.toFloat()
                    viewportHeight = size.height.toFloat()
                }
                .pointerInput(maxScrollX, maxScrollY) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                                if (delta != Offset.Zero) {
                                    val isShift = event.keyboardModifiers.isShiftPressed
                                    val dx = if (isShift) delta.y else delta.x
                                    val dy = if (isShift) 0f else delta.y

                                    var scrolled = false
                                    if (maxScrollX > 0f && dx != 0f) {
                                        state.scrollX = (state.scrollX + dx * 24f).coerceIn(0f, maxScrollX)
                                        scrolled = true
                                    }
                                    if (maxScrollY > 0f && dy != 0f) {
                                        state.scrollY = (state.scrollY + dy * 24f).coerceIn(0f, maxScrollY)
                                        scrolled = true
                                    }
                                    if (!scrolled && maxScrollX > 0f && maxScrollY <= 0f && delta.y != 0f) {
                                        state.scrollX = (state.scrollX + delta.y * 24f).coerceIn(0f, maxScrollX)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Scrollable canvas content Box
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(contentWidthDp, contentHeightDp)
                    .offset { IntOffset(-state.scrollX.roundToInt(), -state.scrollY.roundToInt()) }
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (state.editingModule != null) return@onKeyEvent false
                        var handled = state.onKeyEvent(keyEvent)
                        if (!handled) {
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete)) {
                                state.deleteSelected()
                                handled = true
                            } else if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.Z &&
                                (keyEvent.isMetaPressed || keyEvent.isCtrlPressed)) {
                                if (keyEvent.isShiftPressed) state.engine?.redo()
                                else state.engine?.undo()
                                handled = true
                            }
                        }
                        handled
                    }
                    .onGloballyPositioned { state.updateRootOffset(it.positionInRoot()) }
                    .pointerInput(state) {
                        detectTapGestures {
                            focusRequester.requestFocus()
                            mdiState.activeGraphState = state
                            // Tap on background clears selection
                            state.clearSelection()
                        }
                    }
                    .pointerInput(state) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var wasDragged = false
                            drag(down.id) { change ->
                                change.consume()
                                if (!wasDragged) {
                                    state.selectionRectStart = down.position
                                    wasDragged = true
                                }
                                state.selectionRectEnd = change.position
                            }
                            val rectStart = state.selectionRectStart
                            val rectEnd = state.selectionRectEnd
                            if (wasDragged && rectStart != null && rectEnd != null && (rectEnd - rectStart).getDistance() > 5f) {
                                state.selectModulesInRect(rectStart, rectEnd, density)
                            }
                            state.selectionRectStart = null
                            state.selectionRectEnd = null
                        }
                    }
            ) {
                val isRootGraph = parentPatch == null || parentPatch.isRoot
                if (isRootGraph && !FileState.isDirty && FileState.currentFilePath == null && state.modules.isEmpty()) {
                    Box(
                        modifier = Modifier.size(
                            with(localDensity) { viewportWidth.toDp() },
                            with(localDensity) { viewportHeight.toDp() }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .background(Color(0x11FFFFFF), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            LightbulbIcon(color = Color(0xFFFFD54F), size = 18.dp)
                            Text(
                                text = "Look in the Help menu for Examples and Tutorials!",
                                color = Color(0xAAFFFFFF),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Draw Wires behind modules
                WireRenderer(state)

                // Draw Modules — add cases here for modules with custom layouts
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    state.modules.forEach { module ->
                        when (module) {
                            is NumberModule -> NumberModuleNode(module, state)
                            is EquationModule -> EquationModuleNode(module, state)
                            is ScaleModule -> ScaleModuleNode(module, state)
                            is FaderModule -> FaderModuleNode(module, state)
                            is KnobModule -> KnobModuleNode(module, state)
                            is PatchModule -> PatchModuleNode(module, state)
                            is StepValueModule -> StepValueModuleNode(module, state)
                            is MixerModule -> MixerModuleNode(module, state)
                            is ModuloSelectModule -> ModuleNode(module, state, headerColor = colors.headerSequence)
                            is StepperModule -> ModuleNode(module, state, headerColor = colors.headerSequence)
                            is InputModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                            is CommandModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                            is OutputModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                            is AudioInputModule -> ModuleNode(module, state, headerColor = colors.headerAudio, label = module.name)
                            is AudioOutputModule -> ModuleNode(module, state, headerColor = colors.headerAudio, label = module.name)
                            is AudioOutput2Module -> ModuleNode(module, state, headerColor = colors.headerAudio, label = module.name)
                            is ScopeModule -> ScopeModuleNode(module, state)
                            is VuMeterModule -> VuMeterModuleNode(module, state)
                            is KeyboardModule -> KeyboardModuleNode(module, state)
                            is CommentModule -> CommentModuleNode(module, state)
                            is EnvSegModule -> EnvSegModuleNode(module, state)
                            is PianoRollModule -> PianoRollModuleNode(module, state)
                            is KeyMatchModule -> KeyMatchModuleNode(module, state)
                            is MatchControlModule -> MatchControlModuleNode(module, state)
                            is MatchControlSequentialModule -> MatchControlSequentialModuleNode(module, state)
                            is MatchNoteOnModule -> MatchNoteOnModuleNode(module, state)
                            is MatchNoteOnSequentialModule -> MatchNoteOnSequentialModuleNode(module, state)
                            is GlowModule -> GlowModuleNode(module, state)
                            is FlashModule -> FlashModuleNode(module, state)
                            is SendModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                            is ReceiveModule -> ModuleNode(module, state, headerColor = colors.headerInputOutput, label = module.name)
                            is TimelineModule -> TimelineModuleNode(module, state)
                            is MidiInModule -> MidiInModuleNode(module, state)
                            is MidiOutModule -> MidiOutModuleNode(module, state)
                            else -> ModuleNode(module, state)
                        }
                    }
                }

                // Draw selection rectangle (in screen space, account for rootOffset)
                val selRectStart = state.selectionRectStart
                val selRectEnd = state.selectionRectEnd
                if (selRectStart != null && selRectEnd != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val left = minOf(selRectStart.x, selRectEnd.x)
                        val top = minOf(selRectStart.y, selRectEnd.y)
                        val right = maxOf(selRectStart.x, selRectEnd.x)
                        val bottom = maxOf(selRectStart.y, selRectEnd.y)
                        drawRect(
                            color = colors.selectionRect,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawRect(
                            color = colors.selectionRect.copy(alpha = 0.1f),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top)
                        )
                    }
                }
            }

            // Scrollbars (only appear when needed)
            val scrollbarThickness = 15.dp
            if (maxScrollY > 0f) {
                GraphVerticalScrollbar(
                    scrollOffset = state.scrollY,
                    maxScroll = maxScrollY,
                    viewportLength = viewportHeight,
                    contentLength = totalContentHeight,
                    onScrollChanged = { state.scrollY = it },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(scrollbarThickness)
                        .fillMaxHeight()
                        .padding(
                            top = 2.dp,
                            end = 2.dp,
                            bottom = if (maxScrollX > 0f) scrollbarThickness + 4.dp else 2.dp
                        )
                )
            }

            if (maxScrollX > 0f) {
                GraphHorizontalScrollbar(
                    scrollOffset = state.scrollX,
                    maxScroll = maxScrollX,
                    viewportLength = viewportWidth,
                    contentLength = totalContentWidth,
                    onScrollChanged = { state.scrollX = it },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .height(scrollbarThickness)
                        .fillMaxWidth()
                        .padding(
                            start = 2.dp,
                            bottom = 2.dp,
                            end = if (maxScrollY > 0f) scrollbarThickness + 4.dp else 2.dp
                        )
                )
            }

            // Editor overlay — backdrop + panel
            state.editingModule?.let { module ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .pointerInput(Unit) { detectTapGestures { state.editingModule = null } }
                )
                ModuleEditor(
                    module = module,
                    onDismiss = { state.editingModule = null },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun GraphVerticalScrollbar(
    scrollOffset: Float,
    maxScroll: Float,
    viewportLength: Float,
    contentLength: Float,
    onScrollChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    thickness: Dp = 15.dp
) {
    if (maxScroll <= 0f || contentLength <= viewportLength) return

    val density = LocalDensity.current
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Default)
            .pointerInput("hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .pointerInput(maxScroll, viewportLength, contentLength) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val trackHeight = size.height.toFloat()
                    if (trackHeight <= 0f) return@awaitEachGesture

                    val visibleRatio = (viewportLength / contentLength).coerceIn(0f, 1f)
                    val minThumbHeight = with(density) { 24.dp.toPx() }
                    val thumbHeight = maxOf(minThumbHeight, trackHeight * visibleRatio).coerceAtMost(trackHeight)
                    val scrollRange = trackHeight - thumbHeight
                    if (scrollRange <= 0f) return@awaitEachGesture

                    val currentRatio = (scrollOffset / maxScroll).coerceIn(0f, 1f)
                    val thumbY = scrollRange * currentRatio

                    val clickY = down.position.y
                    val isInsideThumb = clickY >= thumbY && clickY <= (thumbY + thumbHeight)

                    val startScroll = scrollOffset
                    val startClickY = clickY

                    fun update(currentY: Float) {
                        if (isInsideThumb) {
                            val dy = currentY - startClickY
                            val dScroll = (dy / scrollRange) * maxScroll
                            onScrollChanged((startScroll + dScroll).coerceIn(0f, maxScroll))
                        } else {
                            val targetThumbY = currentY - thumbHeight / 2f
                            val targetScroll = (targetThumbY / scrollRange) * maxScroll
                            onScrollChanged(targetScroll.coerceIn(0f, maxScroll))
                        }
                    }

                    isDragging = true
                    if (!isInsideThumb) {
                        update(clickY)
                    }
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position.y)
                        change.consume()
                    }
                    isDragging = false
                }
            }
    ) {
        val trackWidth = size.width
        val trackHeight = size.height
        if (trackHeight <= 0f) return@Canvas

        val cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)

        // Draw track
        drawRoundRect(
            color = Color(0x22000000),
            topLeft = Offset.Zero,
            size = Size(trackWidth, trackHeight),
            cornerRadius = cornerRadius
        )

        // Calculate thumb dimensions
        val visibleRatio = (viewportLength / contentLength).coerceIn(0f, 1f)
        val minThumbHeight = 24.dp.toPx()
        val thumbHeight = maxOf(minThumbHeight, trackHeight * visibleRatio).coerceAtMost(trackHeight)
        val scrollRange = trackHeight - thumbHeight
        val scrollRatio = if (maxScroll > 0f) (scrollOffset / maxScroll).coerceIn(0f, 1f) else 0f
        val thumbY = scrollRange * scrollRatio

        val thumbColor = if (isDragging || isHovered) Color(0x99FFFFFF) else Color(0x55FFFFFF)
        val inset = 1.dp.toPx()

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(inset, thumbY),
            size = Size(trackWidth - inset * 2f, thumbHeight),
            cornerRadius = CornerRadius((trackWidth - inset * 2f) / 2f)
        )
    }
}

@Composable
fun GraphHorizontalScrollbar(
    scrollOffset: Float,
    maxScroll: Float,
    viewportLength: Float,
    contentLength: Float,
    onScrollChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    thickness: Dp = 15.dp
) {
    if (maxScroll <= 0f || contentLength <= viewportLength) return

    val density = LocalDensity.current
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Default)
            .pointerInput("hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .pointerInput(maxScroll, viewportLength, contentLength) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val trackWidth = size.width.toFloat()
                    if (trackWidth <= 0f) return@awaitEachGesture

                    val visibleRatio = (viewportLength / contentLength).coerceIn(0f, 1f)
                    val minThumbWidth = with(density) { 24.dp.toPx() }
                    val thumbWidth = maxOf(minThumbWidth, trackWidth * visibleRatio).coerceAtMost(trackWidth)
                    val scrollRange = trackWidth - thumbWidth
                    if (scrollRange <= 0f) return@awaitEachGesture

                    val currentRatio = (scrollOffset / maxScroll).coerceIn(0f, 1f)
                    val thumbX = scrollRange * currentRatio

                    val clickX = down.position.x
                    val isInsideThumb = clickX >= thumbX && clickX <= (thumbX + thumbWidth)

                    val startScroll = scrollOffset
                    val startClickX = clickX

                    fun update(currentX: Float) {
                        if (isInsideThumb) {
                            val dx = currentX - startClickX
                            val dScroll = (dx / scrollRange) * maxScroll
                            onScrollChanged((startScroll + dScroll).coerceIn(0f, maxScroll))
                        } else {
                            val targetThumbX = currentX - thumbWidth / 2f
                            val targetScroll = (targetThumbX / scrollRange) * maxScroll
                            onScrollChanged(targetScroll.coerceIn(0f, maxScroll))
                        }
                    }

                    isDragging = true
                    if (!isInsideThumb) {
                        update(clickX)
                    }
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position.x)
                        change.consume()
                    }
                    isDragging = false
                }
            }
    ) {
        val trackWidth = size.width
        val trackHeight = size.height
        if (trackWidth <= 0f) return@Canvas

        val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        // Draw track
        drawRoundRect(
            color = Color(0x22000000),
            topLeft = Offset.Zero,
            size = Size(trackWidth, trackHeight),
            cornerRadius = cornerRadius
        )

        // Calculate thumb dimensions
        val visibleRatio = (viewportLength / contentLength).coerceIn(0f, 1f)
        val minThumbWidth = 24.dp.toPx()
        val thumbWidth = maxOf(minThumbWidth, trackWidth * visibleRatio).coerceAtMost(trackWidth)
        val scrollRange = trackWidth - thumbWidth
        val scrollRatio = if (maxScroll > 0f) (scrollOffset / maxScroll).coerceIn(0f, 1f) else 0f
        val thumbX = scrollRange * scrollRatio

        val thumbColor = if (isDragging || isHovered) Color(0x99FFFFFF) else Color(0x55FFFFFF)
        val inset = 1.dp.toPx()

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbX, inset),
            size = Size(thumbWidth, trackHeight - inset * 2f),
            cornerRadius = CornerRadius((trackHeight - inset * 2f) / 2f)
        )
    }
}

@Composable
fun WireRenderer(state: GraphState) {
    val colors = LocalGraphColors.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        state.connections.forEach { conn ->
            val start = state.jackOffsets[conn.output] ?: Offset.Zero
            val end = state.jackOffsets[conn.input] ?: Offset.Zero

            if (start != Offset.Zero && end != Offset.Zero) {
                val wireColor = when (conn.output) {
                    is AudioOutputJack -> colors.wireAudio
                    is VoiceOutputJack -> colors.wireVoice
                    is DataQueueOutputJack -> colors.wireDataQueue
                    is UniversalOutputJack -> colors.wireUniversal
                    else -> colors.wireDefault
                }
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    val isBottomTrigger = conn.output.module is TimelineModule &&
                        (conn.output.module as TimelineModule).triggers.any { it.outputJack === conn.output }
                    if (isBottomTrigger) {
                        val dy = 40f
                        val dx = ((end.x - start.x).coerceAtLeast(60f) / 2f).coerceAtMost(100f)
                        cubicTo(start.x, start.y + dy, end.x - dx, end.y, end.x, end.y)
                    } else {
                        val controlX = (start.x + end.x) / 2
                        cubicTo(controlX, start.y, controlX, end.y, end.x, end.y)
                    }
                }
                drawPath(path, color = wireColor, style = Stroke(width = 3f))
            }
        }

        // Draw rubber-band wire as a simple straight line
        val draggingOutput = state.draggingWireByOutput
        if (draggingOutput != null) {
            val start = state.jackOffsets[draggingOutput] ?: Offset.Zero
            val end = state.dragPointerPosition
            if (start != Offset.Zero) {
                val wireColor = when (draggingOutput) {
                    is AudioOutputJack -> colors.wireAudio
                    is VoiceOutputJack -> colors.wireVoice
                    is DataQueueOutputJack -> colors.wireDataQueue
                    is UniversalOutputJack -> colors.wireUniversal
                    else -> colors.wireDragging
                }
                drawLine(
                    color = wireColor,
                    start = start,
                    end = end,
                    strokeWidth = 3f
                )
            }
        }
    }
}

@Composable
fun ModuleNode(module: Module, state: GraphState, headerColor: Color? = null, label: String = module.name) {
    val colors = LocalGraphColors.current
    val resolvedHeaderColor = headerColor ?: colors.headerDefault
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    // A show module's width follows the text it is displaying, so a value losing a digit would
    // narrow the whole node. With values streaming in that reads as a jitter, so the node may
    // grow to fit but never shrink back. Reset when the composable is reused for another module.
    val nodeDensity = LocalDensity.current
    var widestSoFar by remember(module) { mutableStateOf(0.dp) }
    val growOnlyWidth = module is ShowModule

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .then(if (growOnlyWidth) Modifier.widthIn(min = widestSoFar) else Modifier)
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
                if (growOnlyWidth) {
                    with(nodeDensity) {
                        val measured = size.width.toDp()
                        if (measured > widestSoFar) widestSoFar = measured
                    }
                }
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        // fillMaxWidth rather than IntrinsicSize.Max, so the header and body still span the node
        // when the grow-only minimum holds it wider than its content needs.
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            ModuleHeader(module = module, state = state, headerColor = resolvedHeaderColor, label = label)

            // Display loaded/saved WAV file name for sample and samplePlayer modules
            val sampleFileName = when (module) {
                is FloatSampleModule -> module.fileName
                is SamplePlayerModule -> module.fileName
                else -> null
            }
            if (sampleFileName != null) {
                val fullPath = when (module) {
                    is FloatSampleModule -> module.filePath ?: ""
                    is SamplePlayerModule -> module.filePath ?: ""
                    else -> ""
                }
                TooltipArea(text = fullPath) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 200.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sampleFileName,
                            color = if (colors.moduleBackground == Color.White) Color.DarkGray else Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Show custom content for ShowModule
            if (module is ShowModule) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.Black)) {
                    Text(
                        text = module.displayText,
                        color = Color.Green,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Show the transport position for RecorderModule. The cursor is advanced on the audio
            // thread and is not Compose state, so poll it rather than expecting a recomposition.
            if (module is RecorderModule) {
                var cursorText by remember { mutableStateOf(formatMinutesSecondsMillis(0.0)) }
                LaunchedEffect(module) {
                    while (true) {
                        cursorText = formatMinutesSecondsMillis(module.displayCursorSeconds)
                        delay(50)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .background(Color.Black),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cursorText,
                        color = if (module.unit.isRecording) Color.Red else Color.Green,
                        fontSize = 12.sp,
                        // Monospace keeps every digit the same width, so the module does not
                        // change size as the time advances.
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Show time display for SampleReaderModule. Changes color when active (playing).
            if (module is SampleReaderModule) {
                var cursorText by remember { mutableStateOf(formatMinutesSecondsMillis(0.0)) }
                var isActive by remember { mutableStateOf(false) }
                LaunchedEffect(module) {
                    while (true) {
                        cursorText = formatMinutesSecondsMillis(module.displayCursorSeconds)
                        isActive = module.isActive
                        delay(50)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .background(Color.Black),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cursorText,
                        color = if (isActive) Color.Green else Color.DarkGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Show time display for SampleWriterModule. Changes color when active (writing).
            if (module is SampleWriterModule) {
                var cursorText by remember { mutableStateOf(formatMinutesSecondsMillis(0.0)) }
                var isActive by remember { mutableStateOf(false) }
                LaunchedEffect(module) {
                    while (true) {
                        cursorText = formatMinutesSecondsMillis(module.displayCursorSeconds)
                        isActive = module.isActive
                        delay(50)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .background(Color.Black),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cursorText,
                        color = if (isActive) Color.Red else Color.DarkGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Show custom content for ShowManyModule: scrolling list of messages
            if (module is ShowManyModule) {
                val scrollState = rememberLazyListState()
                LaunchedEffect(scrollState) {
                    snapshotFlow { scrollState.layoutInfo.totalItemsCount }
                        .collect { totalItems ->
                            if (totalItems > 0) {
                                try {
                                    scrollState.scrollToItem(totalItems - 1)
                                } catch (_: Exception) {
                                    // Ignore index race conditions during rapid state updates
                                }
                            }
                        }
                }
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier
                        .width(220.dp)
                        .height(120.dp)
                        .background(Color.Black)
                        .padding(4.dp)
                ) {
                    items(module.messages) { msgText ->
                        Text(
                            text = msgText,
                            color = Color.Green,
                            fontSize = 10.sp
                        )
                    }
                }
            }




            if (module is OneShotModule) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (module.isArmed) "● ARMED" else "○ DISARMED",
                        color = if (module.isArmed) colors.jackKickable else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Jacks: inputs at top, outputs at bottom, single column
            module.inputs.forEach { JackView(it, state, isLeft = true) }
            if (module is BeatClockModule) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "kicks/beat",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .background(Color(0xFFE0E0E0))
                                .clickable { expanded = true }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = module.division,
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                DropdownArrow(
                                    color = Color.Gray,
                                    size = 7.dp
                                )
                            }
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            val divisions = listOf(
                                "1:4", "1:2", "1:1", "2:1", "4:1", "8:1", "16:1", "32:1",
                                "1:3", "8:3", "16:3",
                                "3:1", "6:1", "12:1",
                                "5:1", "10:1"
                            )
                            divisions.forEach { div ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(div, fontSize = 12.sp) },
                                    onClick = {
                                        module.division = div
                                        FileState.markDirty()
                                        expanded = false
                                    },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (module !is TimelineModule) {
                if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                    HorizontalDivider(color = Color.Black, thickness = 1.dp)
                }
                module.outputs.forEach { JackView(it, state, isLeft = false) }
            }
        }
    }
}

@Composable
fun KeyMatchModuleNode(module: KeyMatchModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .size(width = 80.dp, height = 80.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            // Body containing the button and labeled jacks
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Button
                var isPressed by remember { mutableStateOf(false) }
                val buttonBg = if (isPressed) colors.buttonBgPressed else colors.buttonBg

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(buttonBg, RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                        .pointerInput(module) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val e = state.engine
                                    val time = ScoreClock.now()
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            isPressed = true
                                            val msg = Message.FloatValue(time, module.keyChar.code.toDouble())
                                            if (e != null) {
                                                e.post(SendOutputCommand(module.on, msg))
                                            } else {
                                                module.on.send(msg)
                                            }
                                        }
                                        PointerEventType.Release -> {
                                            isPressed = false
                                            val msg = Message.FloatValue(time, module.keyChar.code.toDouble())
                                            if (e != null) {
                                                e.post(SendOutputCommand(module.off, msg))
                                            } else {
                                                module.off.send(msg)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Text(
                        text = module.keyChar.toString().uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                // Labeled Jacks stacked vertically
                Column(
                    modifier = Modifier.fillMaxHeight().weight(1f),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.End
                ) {
                    JackView(module.on, state, isLeft = false)
                    JackView(module.off, state, isLeft = false)
                }
            }
        }
    }
}

/**
 * A compact horizontal value indicator showing a value between 0.0 and 1.0
 * as a simple horizontal line with a vertical thumb moving from left to right.
 */
@Composable
fun HorizontalValueIndicator(
    value: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Gray,
    thumbColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val strokePx = 1.5.dp.toPx()
        val thumbWidthPx = 2.dp.toPx()
        val thumbHeightPx = 10.dp.toPx()
        val halfThumbWidth = thumbWidthPx / 2f
        val centerY = size.height / 2f

        val startX = halfThumbWidth
        val endX = size.width - halfThumbWidth
        val usableWidth = (endX - startX).coerceAtLeast(0f)

        // Draw horizontal line
        drawLine(
            color = lineColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokePx
        )

        // Calculate thumb X position (0.0 = startX, 1.0 = endX)
        val fraction = value.coerceIn(0f, 1f)
        val thumbX = startX + fraction * usableWidth

        // Draw vertical thumb
        drawLine(
            color = thumbColor,
            start = Offset(thumbX, centerY - thumbHeightPx / 2f),
            end = Offset(thumbX, centerY + thumbHeightPx / 2f),
            strokeWidth = thumbWidthPx
        )
    }
}

/**
 * A compact circular flash indicator (LED style) that illuminates briefly when triggered.
 */
@Composable
fun FlashIndicator(
    isFlashing: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.Yellow,
    inactiveColor: Color = Color(0xFF333333),
    haloColor: Color = Color.Yellow.copy(alpha = 0.3f),
    size: Dp = 14.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        if (isFlashing) {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(haloColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(size * 0.7f)
                    .background(activeColor, CircleShape)
                    .border(0.75.dp, Color.White, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.7f)
                    .background(inactiveColor, CircleShape)
                    .border(0.75.dp, Color(0xFF555555), CircleShape)
            )
        }
    }
}

/**
 * Convenience overload of [FlashIndicator] that flashes for [flashDurationMs] whenever [triggerCount] increments.
 */
@Composable
fun FlashIndicator(
    triggerCount: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.Yellow,
    inactiveColor: Color = Color(0xFF333333),
    haloColor: Color = Color.Yellow.copy(alpha = 0.3f),
    size: Dp = 14.dp,
    flashDurationMs: Long = 150L
) {
    var isFlashing by remember { mutableStateOf(false) }

    LaunchedEffect(triggerCount) {
        if (triggerCount > 0) {
            isFlashing = true
            delay(flashDurationMs)
            isFlashing = false
        }
    }

    FlashIndicator(
        isFlashing = isFlashing,
        modifier = modifier,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        haloColor = haloColor,
        size = size
    )
}

@Composable
fun MatchControlModuleNode(module: MatchControlModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            // Inputs: "in", "learn", "index"
            module.inputs.forEach { JackView(it, state, isLeft = true) }

            HorizontalDivider(color = Color.Black, thickness = 1.dp)

            // Output row with graphic display to the left of the "value" output jack
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalValueIndicator(
                    value = module.currentValue.toFloat(),
                    modifier = Modifier
                        .padding(start = 6.dp, end = 2.dp)
                        .width(40.dp)
                        .height(16.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    JackView(module.output, state, isLeft = false)
                }
            }
        }
    }
}

@Composable
fun MatchControlSequentialModuleNode(module: MatchControlSequentialModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            // Inputs: "in", "learn", "index"
            module.inputs.forEach { JackView(it, state, isLeft = true) }

            HorizontalDivider(color = Color.Black, thickness = 1.dp)

            // Output rows with graphic display to the left of each "value" output jack
            module.stepOutputs.forEachIndexed { i, jack ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalValueIndicator(
                        value = module.currentValues[i].toFloat(),
                        modifier = Modifier
                            .padding(start = 6.dp, end = 2.dp)
                            .width(40.dp)
                            .height(16.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        JackView(jack, state, isLeft = false)
                    }
                }
            }

            // Other outputs (e.g. unmatched)
            val otherOutputs = module.outputs.filter { it !in module.stepOutputs }
            otherOutputs.forEach { jack ->
                JackView(jack, state, isLeft = false)
            }
        }
    }
}

@Composable
fun MatchNoteOnModuleNode(module: MatchNoteOnModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            // Inputs: "in", "learn", "tag"
            module.inputs.forEach { JackView(it, state, isLeft = true) }

            HorizontalDivider(color = Color.Black, thickness = 1.dp)

            // Output row with flash indicator to the left of the "amplitude" output jack
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlashIndicator(
                    triggerCount = module.triggerCount,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    JackView(module.output, state, isLeft = false)
                }
            }

            // Other outputs (e.g. unmatched)
            val otherOutputs = module.outputs.filter { it !== module.output }
            otherOutputs.forEach { jack ->
                JackView(jack, state, isLeft = false)
            }
        }
    }
}

@Composable
fun MatchNoteOnSequentialModuleNode(module: MatchNoteOnSequentialModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            // Inputs: "in", "learn", "tag"
            module.inputs.forEach { JackView(it, state, isLeft = true) }

            HorizontalDivider(color = Color.Black, thickness = 1.dp)

            // Output rows with flash indicator to the left of each "amplitude" output jack
            module.stepOutputs.forEachIndexed { i, jack ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlashIndicator(
                        triggerCount = module.triggerCounts[i],
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        JackView(jack, state, isLeft = false)
                    }
                }
            }

            // Other outputs (e.g. unmatched)
            val otherOutputs = module.outputs.filter { it !in module.stepOutputs }
            otherOutputs.forEach { jack ->
                JackView(jack, state, isLeft = false)
            }
        }
    }
}

@Composable
fun MidiInModuleNode(module: MidiInModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerInputOutput, label = module.name)

            // On-module channel selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                var expanded by remember { mutableStateOf(false) }
                val chText = if (module.channelFilter == -1) "Ch: ALL" else "Ch: ${module.channelFilter}"
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0E0E0))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = chText,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        DropdownArrow(
                            color = Color.Gray,
                            size = 7.dp
                        )
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    val channels = listOf(-1) + (1..16).toList()
                    channels.forEach { ch ->
                        val label = if (ch == -1) "ALL" else "Channel $ch"
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(label, fontSize = 12.sp) },
                            onClick = {
                                module.selectChannelFilter(ch)
                                FileState.markDirty()
                                expanded = false
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        )
                    }
                }
            }

            if (module.outputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
                module.outputs.forEach { JackView(it, state, isLeft = false) }
            }
        }
    }
}

@Composable
fun MidiOutModuleNode(module: MidiOutModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerInputOutput, label = module.name)

            // On-module channel selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                var expanded by remember { mutableStateOf(false) }
                val chText = "Ch: ${module.channel}"
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0E0E0))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = chText,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        DropdownArrow(
                            color = Color.Gray,
                            size = 7.dp
                        )
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    (1..16).forEach { ch ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Channel $ch", fontSize = 12.sp) },
                            onClick = {
                                module.selectChannel(ch)
                                FileState.markDirty()
                                expanded = false
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        )
                    }
                }
            }

            if (module.inputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
                module.inputs.forEach { JackView(it, state, isLeft = true) }
            }
        }
    }
}

private fun formatValue(value: Double): String = formatNumber(value)

@Composable
fun JackView(
    jack: Jack,
    state: GraphState,
    isLeft: Boolean,
    trailingContent: (@Composable () -> Unit)? = null
) {
    if (jack is CommandJack) {
        CommandJackView(jack, state, isLeft)
        return
    }
    if (jack is CommandOutputJack) {
        CommandOutputJackView(jack, state, isLeft)
        return
    }
    val colors = LocalGraphColors.current
    val textColor = if (jack.isHighlighted) Color.Black else Color.LightGray
    val backgroundColor = when {
        jack.isHighlighted -> colors.jackBgHighlighted
        jack is AudioInputJack || jack is AudioOutputJack -> colors.jackBgAudio
        jack is VoiceInputJack || jack is VoiceOutputJack -> colors.jackBgVoice
        jack is DataQueueInputJack || jack is DataQueueOutputJack -> colors.jackBgDataQueue
        jack is UniversalInputJack || jack is UniversalOutputJack -> colors.jackBgUniversal
        else -> colors.jackBgDefault
    }

    var isHovered by remember { mutableStateOf(false) }
    var isValueDragging by remember { mutableStateOf(false) }
    var valueDragCurrentValue by remember { mutableStateOf(0.0) }
    var valueDragRawValue by remember { mutableStateOf(0.0) }
    var valueDragAccumX by remember { mutableStateOf(0f) }
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }

    val shouldShowValueOnHover = jack.isDisplayableValue && (jack !is InputJack || jack.isValueDraggable)
    val displayText = when {
        isValueDragging -> formatValue(valueDragCurrentValue)
        isHovered && shouldShowValueOnHover -> formatValue(jack.getValue())
        else -> jack.name
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(14.dp)
            .onGloballyPositioned { layoutCoordinates ->
                jackRootPosition = layoutCoordinates.positionInRoot()
                val size = layoutCoordinates.size
                val pos = jackRootPosition - state.rootOffset
                val edgeOffset = if (isLeft) {
                    Offset(0f, size.height / 2f)
                } else {
                    Offset(size.width.toFloat(), size.height / 2f)
                }
                state.updateJackOffset(jack, pos + edgeOffset, Size(size.width.toFloat(), size.height.toFloat()))
            }
            .background(backgroundColor)
            // Hover detection
            .pointerInput("hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                            else -> {}
                        }
                    }
                }
            }
            .pointerInput(jack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed) return@awaitEachGesture
                    val isShiftPressed = currentEvent.keyboardModifiers.isShiftPressed
                    val startOffset = down.position
                    val unconnectedInput = jack.isValueDraggable &&
                        state.connections.none { it.input == jack }
                    if (unconnectedInput) {
                        val initialVal = jack.getValue()
                        valueDragCurrentValue = initialVal
                        valueDragRawValue = initialVal
                        valueDragAccumX = 0f
                        isValueDragging = true
                    } else {
                        if (jack is OutputJack) state.startDraggingFromOutput(jack, isShiftPressed)
                        else if (jack is InputJack) state.startDraggingFromInput(jack)
                        state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (isValueDragging) {
                                isValueDragging = false
                            } else {
                                state.onPointerUp(null)
                            }
                            break
                        }
                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            if (isValueDragging) {
                                val inputJack = jack as InputJack
                                valueDragAccumX += dragAmount.x
                                val range = inputJack.max - inputJack.min
                                val baseSensitivity = if (inputJack.isInteger) {
                                    maxOf(range / 400.0, 1.0 / 20.0)
                                } else {
                                    range / 400.0
                                }
                                val sensitivity = baseSensitivity * 10.0.pow(-valueDragAccumX / 200.0)
                                val low = minOf(inputJack.min, inputJack.max)
                                val high = maxOf(inputJack.min, inputJack.max)
                                valueDragRawValue = (valueDragRawValue - dragAmount.y * sensitivity)
                                    .coerceIn(low, high)
                                val newValue = if (inputJack.isInteger) {
                                    kotlin.math.round(valueDragRawValue).coerceIn(low, high)
                                } else {
                                    valueDragRawValue
                                }
                                if (newValue != valueDragCurrentValue) {
                                    valueDragCurrentValue = newValue
                                    val e = state.engine
                                    if (e != null) e.post(SetValueCommand(inputJack, newValue, ScoreClock.now()))
                                    else inputJack.setValue(newValue)
                                    state.markChanged()
                                    FileState.markDirty()
                                }
                            } else {
                                state.onPointerMove(state.dragPointerPosition + dragAmount)
                            }
                        }
                    }
                }
            }
    ) {
        if (isLeft) {
            JackMarker(jack)
            Spacer(modifier = Modifier.width(4.dp))
            Text(displayText, color = textColor, fontSize = 10.sp, lineHeight = 10.sp, maxLines = 1, softWrap = false)
            trailingContent?.invoke()
            if (jack.module is SendModule) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(">>", color = colors.jackDefault, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            if (jack is BooleanInputJack) {
                Spacer(modifier = Modifier.widthIn(min = 8.dp).weight(1f))
                val isConnected = state.connections.any { it.input == jack }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(14.dp)
                        .pointerInput(jack) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                down.consume()
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    up.consume()
                                    if (!isConnected) {
                                        val newChecked = !jack.isChecked
                                        val newValue = if (newChecked) 1.0 else 0.0
                                        val e = state.engine
                                        if (e != null) e.post(SetValueCommand(jack, newValue, ScoreClock.now()))
                                        else jack.setValue(newValue)
                                        jack.isChecked = newChecked
                                        state.markChanged()
                                        FileState.markDirty()
                                    }
                                }
                            }
                        }
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        Checkbox(
                            checked = jack.isChecked,
                            onCheckedChange = null,
                            modifier = Modifier.size(14.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.headerSequence,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.White
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(4.dp))
            if (jack.module is ReceiveModule) {
                Text(">>", color = colors.jackDefault, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = jack.name,
                    color = Color.Transparent,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = displayText,
                    color = textColor,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.matchParentSize()
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Outputs are never kickable, so this is the plain type-coloured square.
            JackMarker(jack)
        }
    }
}

@Composable
fun CommandJackView(jack: CommandJack, state: GraphState, isLeft: Boolean) {
    val colors = LocalGraphColors.current
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val edgeOffset = if (isLeft) Offset(0f, size.height / 2f)
                                 else Offset(size.width.toFloat(), size.height / 2f)
                state.updateJackOffset(jack, jackRootPosition - state.rootOffset + edgeOffset, Size(size.width.toFloat(), size.height.toFloat()))
            }
            .pointerInput(jack) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        state.startDraggingFromInput(jack)
                        state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.onPointerMove(state.dragPointerPosition + dragAmount)
                    },
                    onDragEnd = { state.onPointerUp(null) },
                    onDragCancel = { state.onPointerUp(null) }
                )
            }
    ) {
        if (isLeft) {
            Box(modifier = Modifier.size(8.dp).background(colors.jackKickable))
            Spacer(modifier = Modifier.width(4.dp))
        }
        // A highlighted command jack is one whose module is waiting for something - armed, or
        // learning a controller. Tinting the button is what makes that state visible; without
        // it, setting isHighlighted on a CommandJack has no effect at all.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Button(
                onClick = {},
                modifier = Modifier
                    .height(16.dp)
                    .pointerInput(jack) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val e = state.engine
                            if (e != null) {
                                e.post(KickJackCommand(jack, ScoreClock.now()))
                            } else {
                                jack.receive(com.syntona.dataflow.model.Message.FloatValue(ScoreTime.Zero, 0.0))
                            }
                        }
                    },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                colors = if (jack.isHighlighted) {
                    ButtonDefaults.buttonColors(
                        containerColor = colors.buttonBgArmed,
                        contentColor = Color.Black
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    jack.name,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        (jack.module as? CommandJackBadge)?.badgeFor(jack)?.let { badge ->
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                badge,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                color = if (jack.isHighlighted) colors.buttonBgArmed else Color.Gray,
                maxLines = 1,
                softWrap = false
            )
        }
        if (!isLeft) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).background(colors.jackKickable))
        }
    }
}

@Composable
fun CommandOutputJackView(jack: CommandOutputJack, state: GraphState, isLeft: Boolean) {
    val colors = LocalGraphColors.current
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val edgeOffset = if (isLeft) Offset(0f, size.height / 2f)
                                 else Offset(size.width.toFloat(), size.height / 2f)
                state.updateJackOffset(jack, jackRootPosition - state.rootOffset + edgeOffset, Size(size.width.toFloat(), size.height.toFloat()))
            }
            .pointerInput(jack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isShiftPressed = currentEvent.keyboardModifiers.isShiftPressed
                    val startOffset = down.position
                    state.startDraggingFromOutput(jack, isShiftPressed)
                    state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            state.onPointerUp(null)
                            break
                        }
                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            state.onPointerMove(state.dragPointerPosition + dragAmount)
                        }
                    }
                }
            }
    ) {
        if (isLeft) {
            Box(modifier = Modifier.size(8.dp).background(colors.jackKickable))
            Spacer(modifier = Modifier.width(4.dp))
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Button(
                onClick = {},
                modifier = Modifier
                    .height(16.dp)
                    .pointerInput(jack) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val e = state.engine
                            val time = ScoreClock.now()
                            if (e != null) {
                                e.post(SendOutputCommand(jack, Message.FloatValue(time, 0.0)))
                            } else {
                                jack.send(Message.FloatValue(time, 0.0))
                            }
                        }
                    },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("Kick", fontSize = 10.sp, lineHeight = 10.sp)
            }
        }
        if (!isLeft) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).background(colors.jackKickable))
        }
    }
}

/**
 * Connector dot with wire-drag support but no name label.
 * Used by compact module layouts such as [NumberModuleNode].
 */
/**
 * The square that marks a jack, coloured by the kind of message it carries, with a red triangle
 * butting against its right edge when arriving there runs the module.
 *
 * Two facts needed one square. Colour used to mean the message type except when the jack was
 * kickable, when it meant that instead - so a kickable voice jack could advertise only one of
 * the two, and always the less useful one. Colour now always means the type; the triangle alone
 * means kickable.
 */
@Composable
private fun JackMarker(jack: Jack, squareSize: Dp = 8.dp) {
    val colors = LocalGraphColors.current
    val isUniversal = jack is UniversalInputJack || jack is UniversalOutputJack
    val typeColor = when {
        jack is AudioInputJack || jack is AudioOutputJack -> colors.jackAudio
        jack is VoiceInputJack || jack is VoiceOutputJack -> colors.jackVoice
        jack is DataQueueInputJack || jack is DataQueueOutputJack -> colors.jackDataQueue
        isUniversal -> colors.jackUniversal
        else -> colors.jackDefault
    }
    val kickColor = colors.jackKickable
    val markerShape = if (jack.isInteger) RectangleShape else RoundedCornerShape(2.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val boxModifier = Modifier
            .size(squareSize)
            .background(typeColor, shape = markerShape)
            .then(
                if (isUniversal) Modifier.border(1.dp, colors.jackUniversalBorder, shape = markerShape)
                else Modifier
            )
        Box(modifier = boxModifier)
        if (jack is InputJack && jack.isKickable) {
            Canvas(modifier = Modifier.size(width = squareSize / 2, height = squareSize)) {
                drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    },
                    kickColor
                )
            }
        }
    }
}

@Composable
fun CompactJackConnector(jack: Jack, state: GraphState, isLeft: Boolean) {
    val colors = LocalGraphColors.current
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(12.dp)
            .then(if (jack.isHighlighted) Modifier.background(colors.jackBgHighlighted, shape = RoundedCornerShape(3.dp)) else Modifier)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val edgeX = if (isLeft) 0f else size.width.toFloat()
                state.updateJackOffset(
                    jack,
                    jackRootPosition - state.rootOffset + Offset(edgeX, size.height / 2f),
                    Size(size.width.toFloat(), size.height.toFloat())
                )
            }
            .pointerInput(jack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isShiftPressed = currentEvent.keyboardModifiers.isShiftPressed
                    val startOffset = down.position
                    if (jack is OutputJack) state.startDraggingFromOutput(jack, isShiftPressed)
                    else if (jack is InputJack) state.startDraggingFromInput(jack)
                    state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            state.onPointerUp(null)
                            break
                        }
                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            state.onPointerMove(state.dragPointerPosition + dragAmount)
                        }
                    }
                }
            }
    ) {
        // Square plus triangle is exactly the 12.dp box, so a kickable jack keeps the same
        // footprint in the compact nodes and nothing around it shifts.
        JackMarker(jack)
    }
}

@Composable
fun FaderModuleNode(module: FaderModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(70.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp).padding(4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    module.inputs.forEach { CompactJackConnector(it, state, isLeft = true) }
                }
                VerticalSlider(
                    value = module.value.toFloat(),
                    onValueChange = { v ->
                        val e = state.engine
                        if (e != null) e.post(SetValueCommand(module.valueIn, v.toDouble(), ScoreClock.now()))
                        else module.setValue(v.toDouble())
                        state.markChanged()
                    },
                    valueRange = module.valueIn.min.toFloat()..module.valueIn.max.toFloat(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    module.outputs.forEach { CompactJackConnector(it, state, isLeft = false) }
                }
            }

            NumberField(
                value = module.value,
                onCommit = { v ->
                    val e = state.engine
                    if (e != null) e.post(SetValueCommand(module.valueIn, v, ScoreClock.now()))
                    else module.setValue(v)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun PatchModuleNode(module: PatchModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .widthIn(min = 100.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.modulePatchBackground
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            val suffix = if (module.isModified) "*" else ""
            val displayName = if (module is PolyPatchModule) "${module.name}$suffix[${module.maxVoices}]" else "${module.name}$suffix"
            ModuleHeader(
                module = module,
                state = state,
                headerColor = colors.headerPatch,
                label = displayName,
                onDoubleTap = {
                    val savedX = module.windowX
                    val savedY = module.windowY
                    val restoredPosition = if (savedX != null && savedY != null) {
                        Offset(savedX * density.density, savedY * density.density)
                    } else {
                        val parentWin = mdiState.windows.find { it.graphState === state }
                        if (parentWin != null) {
                            Offset(parentWin.position.x + 28f * density.density, parentWin.position.y + 28f * density.density)
                        } else {
                            val pos = state.modulePositions[module] ?: Offset(100f, 100f)
                            Offset(
                                (pos.x + 20f) * density.density,
                                (pos.y + 30f) * density.density
                            )
                        }
                    }
                    val window = MdiWindowInfo(
                        id = module,
                        title = module.name,
                        position = restoredPosition,
                        width = module.windowWidth?.dp ?: 600.dp,
                        height = module.windowHeight?.dp ?: 450.dp,
                        graphState = module.graphState,
                        content = { GraphEditor(state = module.graphState) }
                    )
                    window.onGeometryChanged = {
                        val newX = window.position.x / density.density
                        val newY = window.position.y / density.density
                        val newW = window.width.value
                        val newH = window.height.value
                        if (module.windowWidth != null && (module.windowWidth != newW || module.windowHeight != newH)) {
                            module.isModified = true
                            module.parentPatch?.graphState?.markChanged()
                        }
                        module.windowX = newX
                        module.windowY = newY
                        module.windowWidth = newW
                        module.windowHeight = newH
                    }
                    window.onGeometryChanged?.invoke()
                    mdiState.open(window)
                },
                trailingContent = {
                    val activeGlowModules = module.graphState.modules.filter { it is GlowModule || it is FlashModule }
                    if (activeGlowModules.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            activeGlowModules.forEach { subModule ->
                                val isOn = when (subModule) {
                                    is GlowModule -> subModule.isOn
                                    is FlashModule -> subModule.isOn
                                    else -> false
                                }
                                TooltipArea(text = subModule.name) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                if (isOn) Color.Yellow else Color(0xFF444444),
                                                CircleShape
                                            )
                                            .border(0.5.dp, Color.Gray, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                    Arrangement.SpaceBetween
                } else if (module.inputs.isNotEmpty()) {
                    Arrangement.Start
                } else {
                    Arrangement.End
                }
            ) {
                if (module.inputs.isNotEmpty()) {
                    Column(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        horizontalAlignment = Alignment.Start
                    ) {
                        module.inputs.forEach { JackView(it, state, isLeft = true) }
                    }
                }
                if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (module.outputs.isNotEmpty()) {
                    Column(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        horizontalAlignment = Alignment.End
                    ) {
                        module.outputs.forEach { JackView(it, state, isLeft = false) }
                    }
                }
            }
        }
    }
}

@Composable
fun NumberModuleNode(module: NumberModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(140.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(4.dp)
            ) {
                Column {
                    module.inputs.forEach { CompactJackConnector(it, state, isLeft = true) }
                }
                NumberField(
                    value = module.value,
                    onCommit = { v ->
                        val e = state.engine
                        if (e != null) e.post(SetValueCommand(module.valueIn, v, ScoreClock.now()))
                        else module.setValue(v)
                    },
                    modifier = Modifier.weight(1f)
                )
                Column {
                    module.outputs.forEach { CompactJackConnector(it, state, isLeft = false) }
                }
            }
        }
    }
}

@Composable
fun StepValueModuleNode(module: StepValueModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(110.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerSequence, height = 28.dp)

            val controlInputs = module.inputs.filter { !module.stepInputs.contains(it) }
            val controlOutputs = module.outputs.filter { !module.stepOutputs.contains(it) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    controlInputs.forEach { jack ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompactJackConnector(jack, state, isLeft = true)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(jack.name, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    controlOutputs.forEach { jack ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(jack.name, fontSize = 10.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactJackConnector(jack, state, isLeft = false)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray, thickness = 1.dp)

            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                for (i in 0 until module.numOutputs) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
                            CompactJackConnector(module.stepInputs[i], state, isLeft = true)
                        }
                        SequenceStepTextField(
                            value = module.values[i],
                            onCommit = { text ->
                                module.values[i] = text
                                val numVal = text.toDoubleOrNull()
                                if (numVal != null) {
                                    val e = state.engine
                                    if (e != null) e.post(SetValueCommand(module.stepInputs[i], numVal, ScoreClock.now()))
                                    else module.stepInputs[i].setValue(numVal)
                                }
                                FileState.markDirty()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.CenterEnd) {
                            CompactJackConnector(module.stepOutputs[i], state, isLeft = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SequenceStepTextField(
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(value) }
    var isDirty by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDirty) text = value
    }

    fun commit() {
        onCommit(text)
        isDirty = false
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
            .background(if (isDirty) Color(0xFFFFB0B0) else Color.White)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    commit()
                    true
                } else false
            }
    )
}

@Composable
fun KnobModuleNode(module: KnobModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(90.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerKnob)

            // Knob area with jacks on either side
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    module.inputs.forEach { CompactJackConnector(it, state, isLeft = true) }
                }
                RotaryKnob(
                    value = module.value.toFloat(),
                    onValueChange = { v ->
                        val e = state.engine
                        if (e != null) e.post(SetValueCommand(module.valueIn, v.toDouble(), ScoreClock.now()))
                        else module.setValue(v.toDouble())
                        state.markChanged()
                    },
                    valueRange = module.valueIn.min.toFloat()..module.valueIn.max.toFloat(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    module.outputs.forEach { CompactJackConnector(it, state, isLeft = false) }
                }
            }

            // Numeric readout / direct entry
            NumberField(
                value = module.value,
                onCommit = { v ->
                    val e = state.engine
                    if (e != null) e.post(SetValueCommand(module.valueIn, v, ScoreClock.now()))
                    else module.setValue(v)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ResizableModuleBox(
    module: Module,
    state: GraphState,
    headerColor: Color,
    initialWidth: Dp,
    initialHeight: Dp,
    minWidth: Dp = 100.dp,
    minHeight: Dp = 50.dp,
    onResize: (Dp, Dp) -> Unit = { _, _ -> },
    content: @Composable BoxScope.(Dp, Dp) -> Unit
) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val density = LocalDensity.current

    // Safety floor based on jacks so they are never hidden or clipped
    val maxJacks = maxOf(module.inputs.size, module.outputs.size)
    val minJackHeight = if (maxJacks > 0) (8 + maxJacks * 16).dp else 0.dp
    val effectiveMinHeight = maxOf(minHeight, minJackHeight)
    val effectiveMinWidth = minWidth

    // Local state for smooth dragging, initialized from module's state
    var currentWidth by remember(module, initialWidth, effectiveMinWidth) {
        mutableStateOf(initialWidth.coerceAtLeast(effectiveMinWidth))
    }
    var currentHeight by remember(module, initialHeight, effectiveMinHeight) {
        mutableStateOf(initialHeight.coerceAtLeast(effectiveMinHeight))
    }

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(currentWidth)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = headerColor)

            Box(modifier = Modifier.width(currentWidth).height(currentHeight)) {
                content(currentWidth, currentHeight)

                // Resize grip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .pointerInput(module) {
                            detectDragGestures(
                                onDragEnd = {
                                    onResize(currentWidth, currentHeight)
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val dw: Dp = (dragAmount.x / density.density).dp
                                val dh: Dp = (dragAmount.y / density.density).dp
                                currentWidth = (currentWidth + dw).coerceAtLeast(effectiveMinWidth)
                                currentHeight = (currentHeight + dh).coerceAtLeast(effectiveMinHeight)
                             }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val grip = colors.faderGrip
                        for (i in 1..3) {
                            val d = size.width * i / 4f
                            drawLine(grip, Offset(d, size.height), Offset(size.width, d), strokeWidth = 1.5f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentModuleNode(module: CommentModule, state: GraphState) {
    val colors = LocalGraphColors.current
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = colors.headerComment,
        initialWidth = module.width.dp,
        initialHeight = module.height.dp,
        onResize = { w, h ->
            module.width = w.value
            module.height = h.value
        }
    ) { _, _ ->
        BasicTextField(
            value = module.text,
            onValueChange = { module.text = it },
            modifier = Modifier.fillMaxSize().padding(8.dp).onKeyEvent { true },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White)
        )
    }
}

/** Width of one jack in the keyboard's top row, wide enough for "amplitude". */
private val KEYBOARD_JACK_WIDTH = 76.dp

@Composable
fun KeyboardModuleNode(module: KeyboardModule, state: GraphState) {
    val colors = LocalGraphColors.current
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = colors.headerInputOutput,
        initialWidth = module.width.dp,
        initialHeight = module.height.dp,
        minWidth = 260.dp,
        minHeight = 80.dp,
        onResize = { w, h ->
            module.width = w.value
            module.height = h.value
        }
    ) { _, _ ->
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            // Jacks sit above the keys rather than beside them, so the keyboard gets the whole
            // width of the module. JackView, not CompactJackConnector, because these carry values
            // worth seeing: it swaps the name for the value on hover and drags to set it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column() {
                    module.inputs.forEach { jack ->
                        Box(modifier = Modifier.width(KEYBOARD_JACK_WIDTH)) {
                            JackView(jack, state, isLeft = true)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                module.outputs.forEach { jack ->
                    Box(modifier = Modifier.width(KEYBOARD_JACK_WIDTH)) {
                        JackView(jack, state, isLeft = false)
                    }
                }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                // Reading module.octave, not the jack, is what makes the keys redraw and start
                // sending their new pitches when the octave input changes.
                val sendNote = { noteNumber: Int, opcode: Message.VoiceOpcode ->
                    val e = state.engine
                    val msg = Message.VoiceMessage(
                        time = ScoreClock.now(),
                        opcode = opcode,
                        tag = noteNumber,
                        value = noteNumber.toDouble(),
                        extra = module.currentAmplitude()
                    )
                    if (e != null) {
                        e.post(SendOutputCommand(module.output, msg))
                    } else {
                        module.output.send(msg)
                    }
                }
                BlackWhiteKeyboard(
                    modifier = Modifier.fillMaxSize(),
                    onKeyDown = { noteNumber -> sendNote(noteNumber, Message.VoiceOpcode.NOTE_ON) },
                    onKeyUp = { noteNumber -> sendNote(noteNumber, Message.VoiceOpcode.NOTE_OFF) },
                    numNotes = module.numNotes,
                    startingNote = module.octave * SEMITONES_PER_OCTAVE
                )
            }
        }
    }
}

@Composable
fun ScopeModuleNode(module: ScopeModule, state: GraphState) {
    val colors = LocalGraphColors.current
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = colors.headerAudio,
        initialWidth = module.width.dp,
        initialHeight = module.height.dp,
        onResize = { w, h ->
            module.width = w.value
            module.height = h.value
        }
    ) { _, _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp)
            ) {
                module.inputs.forEach { CompactJackConnector(it, state, isLeft = true) }
            }
            Oscilloscope(
                probe = module.scope,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun VuMeterModuleNode(module: VuMeterModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    var levels by remember(module) { mutableStateOf(FloatArray(module.numChannels)) }
    var clipHoldTicks by remember(module) { mutableStateOf(IntArray(module.numChannels)) }
    var clipPeakLevels by remember(module) { mutableStateOf(FloatArray(module.numChannels)) }

    LaunchedEffect(module) {
        val ticks = IntArray(module.numChannels)
        val peaks = FloatArray(module.numChannels)
        while (true) {
            delay(30)
            val snapshot = FloatArray(module.numChannels) { ch ->
                if (ch < module.followers.size) module.followers[ch].current.value else 0f
            }
            for (ch in 0 until module.numChannels) {
                val jack = module.inputs.getOrNull(ch)
                val max = jack?.max?.toFloat()?.takeIf { it > 0f } ?: 1.0f
                val currentVal = snapshot[ch]
                if (currentVal > max) {
                    ticks[ch] = VU_METER_CLIP_HOLD_TICKS // 50 ticks * 30 ms = 1.5 s hold
                    peaks[ch] = maxOf(peaks[ch], currentVal)
                } else if (ticks[ch] > 0) {
                    ticks[ch]--
                    if (ticks[ch] == 0) {
                        peaks[ch] = 0f
                    }
                }
            }
            levels = snapshot
            clipHoldTicks = ticks.copyOf()
            clipPeakLevels = peaks.copyOf()
        }
    }

    val width = if (module.numChannels == 1) 56.dp else 76.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(width)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerAudio)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // One unlabelled audio "in" jack per channel on the side of the display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 4.dp)
                ) {
                    module.inputs.forEach { jack ->
                        CompactJackConnector(jack, state, isLeft = true)
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (ch in 0 until module.numChannels) {
                        val jack = module.inputs.getOrNull(ch)
                        val max = jack?.max?.toFloat()?.takeIf { it > 0f } ?: 1.0f
                        val level = levels.getOrElse(ch) { 0f }
                        VuMeterBar(
                            level = level,
                            max = max,
                            isOverRange = clipHoldTicks.getOrElse(ch) { 0 } > 0,
                            overRangePeak = clipPeakLevels.getOrElse(ch) { 0f },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VuMeterBar(
    level: Float,
    max: Float,
    isOverRange: Boolean = false,
    overRangePeak: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val fraction = amplitudeToGraphicFraction(level, max)

        val orangeThreshold = VU_METER_ORANGE_THRESHOLD_FRACTION
        val redThreshold = VU_METER_RED_THRESHOLD_FRACTION

        // Background track
        drawRoundRect(
            color = Color(0xFF141414),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // Dimmed unlit region indicators
        // Top region (> 0 dB up to +12 dB, over-range): y from 0 to (1 - redThreshold) * h
        drawRect(
            color = Color(0x33FF1744),
            topLeft = Offset(0f, 0f),
            size = Size(w, (1f - redThreshold) * h)
        )
        // Middle region (-12 dB to 0 dB / max): y from (1 - redThreshold) * h to (1 - orangeThreshold) * h
        drawRect(
            color = Color(0x33FFA000),
            topLeft = Offset(0f, (1f - redThreshold) * h),
            size = Size(w, (redThreshold - orangeThreshold) * h)
        )
        // Bottom region (< -12 dB): y from (1 - orangeThreshold) * h to h
        drawRect(
            color = Color(0x3300E676),
            topLeft = Offset(0f, (1f - orangeThreshold) * h),
            size = Size(w, orangeThreshold * h)
        )

        // Helper to draw a lit vertical slice [fLow, fHigh]
        fun drawLitFractionRange(fLow: Float, fHigh: Float, color: Color) {
            if (fHigh > fLow) {
                val yBottom = h * (1f - fLow)
                val yTop = h * (1f - fHigh)
                drawRect(
                    color = color,
                    topLeft = Offset(0f, yTop),
                    size = Size(w, yBottom - yTop)
                )
            }
        }

        // 1. Bottom region (green) up to min(fraction, orangeThreshold)
        if (fraction > 0f) {
            drawLitFractionRange(0f, minOf(fraction, orangeThreshold), Color(0xFF00E676))
        }

        // 2. Middle region (orange) from orangeThreshold up to min(fraction, redThreshold)
        if (fraction > orangeThreshold) {
            drawLitFractionRange(orangeThreshold, minOf(fraction, redThreshold), Color(0xFFFF9100))
        }

        // 3. Top region (red / over-range) from redThreshold up to min(fraction, 1.0)
        if (fraction > redThreshold) {
            drawLitFractionRange(redThreshold, minOf(fraction, 1f), Color(0xFFFF1744))
        }

        // Over-range persistence (1.5 s hold on clip condition)
        if (isOverRange) {
            // Clip cap at top of meter stays solidly lit red
            drawRect(
                color = Color(0xFFFF1744),
                topLeft = Offset(0f, 0f),
                size = Size(w, 4.dp.toPx())
            )
            // Held red peak line at the highest over-range level reached
            val heldPeakF = amplitudeToGraphicFraction(overRangePeak, max)
            if (heldPeakF > redThreshold) {
                val yPeak = h * (1f - heldPeakF)
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(0f, yPeak),
                    end = Offset(w, yPeak),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Horizontal segment divider lines for classic LED VU meter look
        val segmentHeightPx = 5.dp.toPx()
        var yTick = segmentHeightPx
        while (yTick < h) {
            drawLine(
                color = Color(0x44000000),
                start = Offset(0f, yTick),
                end = Offset(w, yTick),
                strokeWidth = 1.dp.toPx()
            )
            yTick += segmentHeightPx
        }

        // Clear regional divider line at -12 dB (green to orange)
        drawLine(
            color = Color(0x88000000),
            start = Offset(0f, (1f - orangeThreshold) * h),
            end = Offset(w, (1f - orangeThreshold) * h),
            strokeWidth = 1.5.dp.toPx()
        )

        // Highlighted 0 dB / MAX divider line (orange to red over-range)
        drawLine(
            color = Color(0xCCFFFFFF),
            start = Offset(0f, (1f - redThreshold) * h),
            end = Offset(w, (1f - redThreshold) * h),
            strokeWidth = 1.5.dp.toPx()
        )

        // Subtle outer border
        drawRoundRect(
            color = Color(0xFF333333),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
fun EnvSegModuleNode(module: EnvSegModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(
                module = module,
                state = state,
                headerColor = colors.headerAudio,
                onDoubleTap = {
                    mdiState.open(
                        MdiWindowInfo(
                            id = module,
                            title = module.name,
                            position = Offset(
                                ((state.modulePositions[module]?.x ?: 100f) + 20f) * density.density,
                                ((state.modulePositions[module]?.y ?: 100f) + 30f) * density.density
                            ),
                            graphState = state,
                            content = {
                                val localMdi = LocalMdiState.current
                                val windowInfo = remember { localMdi.windows.find { it.id == module } }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.background)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ModuleNameEditor(
                                        module = module,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        textColor = Color.LightGray,
                                        borderColor = Color.Gray,
                                        onNameChanged = { windowInfo?.title = it }
                                    )

                                    EnvelopeAutoDisableVoiceEditor(module, textColor = Color.LightGray)

                                    androidx.compose.material3.HorizontalDivider(color = Color.Gray, thickness = 1.dp)

                                    Box(modifier = Modifier.weight(1f)) {
                                        SegmentedEnvelopeEditor(
                                            envelope = module.envelope,
                                            onChanged = {
                                                state.markChanged()
                                                FileState.markDirty()
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    )
                }
            )
            // Draw jacks.
            module.inputs.forEach { JackView(it, state, isLeft = true) }
            if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
            }
            module.outputs.forEach { JackView(it, state, isLeft = false) }
        }
    }
}

@Composable
fun PianoRollModuleNode(module: PianoRollModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(IntrinsicSize.Max)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(
                module = module,
                state = state,
                headerColor = colors.headerSequence,
                onDoubleTap = {
                    val savedX = module.windowX
                    val savedY = module.windowY
                    val restoredPosition = if (savedX != null && savedY != null) {
                        Offset(savedX * density.density, savedY * density.density)
                    } else {
                        val pos = state.modulePositions[module] ?: Offset(100f, 100f)
                        Offset(
                            (pos.x + 20f) * density.density,
                            (pos.y + 30f) * density.density
                        )
                    }
                    val window = MdiWindowInfo(
                        id = module,
                        title = module.name,
                        position = restoredPosition,
                        width = module.windowWidth?.dp ?: 800.dp,
                        height = module.windowHeight?.dp ?: 500.dp,
                        graphState = state,
                        content = { PianoRollEditor(module, state) }
                    )
                    window.onGeometryChanged = {
                        module.windowX = window.position.x / density.density
                        module.windowY = window.position.y / density.density
                        module.windowWidth = window.width.value
                        module.windowHeight = window.height.value
                    }
                    mdiState.open(window)
                }
            )
            // Draw jacks.
            module.inputs.forEach { JackView(it, state, isLeft = true) }
            if (module.inputs.isNotEmpty() && module.outputs.isNotEmpty()) {
                HorizontalDivider(color = Color.Black, thickness = 1.dp)
            }
            module.outputs.forEach { JackView(it, state, isLeft = false) }
        }
    }
}


fun openHelpForModule(
    module: Module,
    mdiState: MdiState,
    engine: com.syntona.dataflow.engine.DataFlowEngine?,
    density: Float,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val type = when (module) {
        is com.syntona.dataflow.modules.PolyPatchModule -> "polyPatch"
        is com.syntona.dataflow.modules.PatchModule -> "patch"
        else -> module.type
    }
    val helpPath = com.syntona.dataflow.registry.HelpRegistry.getHelpFilePath(type) ?: return
    val currentEngine = engine ?: return
    scope.launch {
        try {
            val bytes = com.syntona.SharedResources.readBytes("files/$helpPath")
            val content = bytes.decodeToString()
            val newDoc = com.syntona.openNewDocument(mdiState, currentEngine, density)
            com.syntona.loadIntoDocument(mdiState, newDoc, helpPath, content, density)
        } catch (e: Exception) {
            println("Failed to load help file $helpPath: ${e.message}")
        }
    }
}

@Composable
fun ModuleHeader(
    module: Module,
    state: GraphState,
    headerColor: Color = LocalGraphColors.current.headerDefault,
    label: String = module.name,
    height: Dp = 24.dp,
    onDoubleTap: () -> Unit = { state.editingModule = module },
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val mdiState = LocalMdiState.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val type = when (module) {
        is com.syntona.dataflow.modules.PolyPatchModule -> "polyPatch"
        is com.syntona.dataflow.modules.PatchModule -> "patch"
        else -> module.type
    }
    val hasHelp = com.syntona.dataflow.registry.HelpRegistry.hasHelpFile(type)

    TooltipArea(text = getModuleTooltipText(module)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(headerColor)
                .headerGestures(
                    state = state,
                    module = module,
                    mdiState = mdiState,
                    onRightClick = { showMenu = true },
                    onDoubleTap = onDoubleTap
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    if (trailingContent != null) {
                        trailingContent()
                    }
                }

                Box {
                    Box(
                        modifier = Modifier
                            .clickable { showMenu = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        VerticalEllipsisIcon(color = Color.White, size = 12.dp)
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Edit Jacks...", fontSize = 12.sp) },
                            onClick = {
                                showMenu = false
                                state.editingModule = module
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        )
                        if (module is FloatSampleModule || module is SamplePlayerModule || module is RecorderModule) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Load from WAV File...", fontSize = 12.sp) },
                                onClick = {
                                    showMenu = false
                                    chooseWavOpenFile { path ->
                                        when (module) {
                                            is FloatSampleModule -> module.loadWavFile(path)
                                            is SamplePlayerModule -> module.loadWavFile(path)
                                            is RecorderModule -> module.loadWavFile(path)
                                        }
                                        state.markChanged()
                                        FileState.markDirty()
                                    }
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Save to WAV File...", fontSize = 12.sp) },
                                onClick = {
                                    showMenu = false
                                    val defaultName = when (module) {
                                        is FloatSampleModule -> module.fileName ?: "${module.name}.wav"
                                        is SamplePlayerModule -> module.fileName ?: "${module.name}.wav"
                                        else -> "${module.name}.wav"
                                    }
                                    chooseWavRecordFile(defaultName = defaultName) { path ->
                                        when (module) {
                                            is FloatSampleModule -> module.saveWavFile(path)
                                            is SamplePlayerModule -> module.saveWavFile(path)
                                            is RecorderModule -> module.saveWavFile(path)
                                        }
                                        state.markChanged()
                                        FileState.markDirty()
                                    }
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            )
                        }
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Help...", fontSize = 12.sp) },
                            enabled = hasHelp,
                            onClick = {
                                showMenu = false
                                openHelpForModule(module, mdiState, state.engine, density, scope)
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.headerGestures(
    state: GraphState,
    module: Module,
    mdiState: MdiState? = null,
    onRightClick: (() -> Unit)? = null,
    onDoubleTap: () -> Unit
): Modifier = this.pointerInput(module, state) {
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown()
        if (currentEvent.buttons.isSecondaryPressed || currentEvent.keyboardModifiers.isCtrlPressed) {
            down.consume()
            onRightClick?.invoke()
            return@awaitEachGesture
        }
        down.consume()
        state.requestFocus?.invoke()
        if (mdiState != null) {
            mdiState.activeGraphState = state
        }
        if (!state.selectedModules.contains(module)) {
            state.clearSelection()
            state.selectModule(module)
        }
        var wasDragged = false
        drag(down.id) { change ->
            change.consume()
            wasDragged = true
            val dragAmount = (change.position - change.previousPosition) / density
            if (state.selectedModules.contains(module)) {
                state.selectedModules.forEach { m ->
                    state.updateModulePosition(m, dragAmount)
                }
            } else {
                state.updateModulePosition(module, dragAmount)
            }
        }
        if (!wasDragged) {
            val secondDown = withTimeoutOrNull(doubleTapTimeout) { awaitFirstDown() }
            if (secondDown != null) {
                secondDown.consume()
                onDoubleTap()
                drag(secondDown.id) { it.consume() }
            }
        }
    }
}

@Composable
fun MixerModuleNode(module: MixerModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(150.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerAudio)

            // Channel strips
            for (i in 0 until module.numInputs) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 6.dp)
                ) {
                    val inputJack = module.getInputJack(i)
                    if (inputJack != null) {
                        CompactJackConnector(inputJack, state, isLeft = true)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${i + 1}",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Left knob: Volume (exponential taper sets gain)
                    RotaryKnob(
                        value = module.getVolumeKnobValue(i),
                        onValueChange = { v ->
                            val gainJack = module.getGainJack(i)
                            if (gainJack != null) {
                                val gainVal = (50.0.pow(v.toDouble()) - 1.0) / 49.0
                                val e = state.engine
                                if (e != null) e.post(SetValueCommand(gainJack, gainVal, ScoreClock.now()))
                                else gainJack.setValue(gainVal)
                            }
                            state.markChanged()
                        },
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Right knob: Pan (-1 to +1)
                    RotaryKnob(
                        value = module.getPanKnobValue(i),
                        onValueChange = { v ->
                            val panJack = module.getPanJack(i)
                            if (panJack != null) {
                                val e = state.engine
                                if (e != null) e.post(SetValueCommand(panJack, v.toDouble(), ScoreClock.now()))
                                else panJack.setValue(v.toDouble())
                            }
                            state.markChanged()
                        },
                        valueRange = -1f..1f,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(28.dp))
                }
            }

            // Master / Output Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                val ampJack = module.inputs.find { it.name == "amplitude" }
                if (ampJack != null) {
                    CompactJackConnector(ampJack, state, isLeft = true)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("amp", color = Color.LightGray, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.weight(1f))
                Text("Master", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    val out0 = module.getOutputJack(0) ?: module.outputs.find { it.name == "out0" || it.name == "output0" }
                    val out1 = module.getOutputJack(1) ?: module.outputs.find { it.name == "out1" || it.name == "output1" }
                    if (out0 != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("L", color = Color.LightGray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactJackConnector(out0, state, isLeft = false)
                        }
                    }
                    if (out1 != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R", color = Color.LightGray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactJackConnector(out1, state, isLeft = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EquationModuleNode(module: EquationModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val minWidth = module.minWidth.dp
    val minHeight = module.minHeight.dp
    ResizableModuleBox(
        module = module,
        state = state,
        headerColor = colors.headerDefault,
        initialWidth = module.width.dp.coerceAtLeast(minWidth),
        initialHeight = module.height.dp.coerceAtLeast(minHeight),
        minWidth = minWidth,
        minHeight = minHeight,
        onResize = { w, h ->
            module.width = w.value
            module.height = h.value
        }
    ) { _, _ ->
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Inputs on the left
            Column(
                modifier = Modifier.width(26.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                module.inputs.forEach { JackView(it, state, isLeft = true) }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Text field and Output on the right
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                EquationField(
                    value = module.equation,
                    errorMessage = module.errorMessage,
                    onCommit = { eq ->
                        val success = module.compile(eq)
                        if (success) {
                            state.markChanged()
                            FileState.markDirty()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                CompactJackConnector(module.output, state, isLeft = false)
            }
        }
    }
}

@Composable
fun EquationField(
    value: String,
    errorMessage: String?,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(value) }
    var isDirty by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDirty) text = value
    }

    fun commit() {
        onCommit(text)
        isDirty = false
    }

    Box(
        modifier = modifier
    ) {
        TooltipArea(
            text = errorMessage ?: ""
        ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            isDirty -> Color(0xFFFFB0B0)
                            errorMessage != null -> Color(0xFFFFC266)
                            else -> Color.White
                        }
                    )
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
    }
}

@Composable
fun ScaleModuleNode(module: ScaleModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    val moduleWidth = if (module.isEditable) 240.dp else 135.dp
    val inputsWidth = if (module.isEditable) 54.dp else 40.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(moduleWidth)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Inputs on the left
                Column(
                    modifier = Modifier.width(inputsWidth),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    module.inputs.forEach { JackView(it, state, isLeft = true) }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Scale Display/Field and Output on the right
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (module.isEditable) {
                        ScaleField(
                            value = module.scaleString,
                            errorMessage = module.errorMessage,
                            onCommit = { scaleStr ->
                                val success = module.updateScale(scaleStr)
                                if (success) {
                                    state.markChanged()
                                    FileState.markDirty()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = "[${module.scaleDegrees.joinToString(",") { formatNumber(it) }}]",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    CompactJackConnector(module.output, state, isLeft = false)
                }
            }
        }
    }
}

@Composable
fun ScaleField(
    value: String,
    errorMessage: String?,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(value) }
    var isDirty by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDirty) text = value
    }

    fun commit() {
        onCommit(text)
        isDirty = false
    }

    Box(
        modifier = modifier
    ) {
        TooltipArea(
            text = errorMessage ?: ""
        ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            isDirty -> Color(0xFFFF8080) // Turns red while editing
                            errorMessage != null -> Color(0xFFFFC266) // Orange if error
                            else -> Color.White
                        }
                    )
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
    }
}

@Composable
fun GlowModuleNode(module: GlowModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(80.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    module.inputs.forEach { JackView(it, state, isLeft = true) }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp)
                ) {
                    if (module.isOn) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Yellow.copy(alpha = 0.3f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.Yellow, CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(0xFF333333), CircleShape)
                                .border(1.dp, Color(0xFF555555), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlashModuleNode(module: FlashModule, state: GraphState) {
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density).roundToInt(), (position.y * density).roundToInt()) }
            .width(80.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(module = module, state = state, headerColor = colors.headerDefault)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    module.inputs.forEach { JackView(it, state, isLeft = true) }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp)
                ) {
                    if (module.isOn) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Yellow.copy(alpha = 0.3f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.Yellow, CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(0xFF333333), CircleShape)
                                .border(1.dp, Color(0xFF555555), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineTriggerJackView(
    jack: OutputJack,
    state: GraphState,
    viewportLeftGraphX: Float,
    viewportRightGraphX: Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalGraphColors.current
    var jackRootPosition by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .size(8.dp)
            .background(colors.jackDefault)
            .onGloballyPositioned { coords ->
                jackRootPosition = coords.positionInRoot()
                val size = coords.size
                val pos = jackRootPosition - state.rootOffset
                val bottomCenterOffset = Offset(size.width / 2f, size.height.toFloat())
                val unclippedX = pos.x + bottomCenterOffset.x
                val clippedX = unclippedX.coerceIn(viewportLeftGraphX, viewportRightGraphX)
                val connectionOffset = Offset(clippedX, pos.y + bottomCenterOffset.y)
                state.updateJackOffset(jack, connectionOffset, Size(size.width.toFloat(), size.height.toFloat()))
            }
            .pointerInput(jack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isShiftPressed = currentEvent.keyboardModifiers.isShiftPressed
                    val startOffset = down.position
                    state.startDraggingFromOutput(jack, isShiftPressed)
                    state.onPointerMove(jackRootPosition - state.rootOffset + startOffset)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            state.onPointerUp(null)
                            break
                        }
                        val dragAmount = change.positionChange()
                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            state.onPointerMove(state.dragPointerPosition + dragAmount)
                        }
                    }
                }
            }
    )
}

@Composable
private fun TrackButton(
    text: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(
                color = if (isActive) activeColor else Color(0xFF333333),
                shape = RoundedCornerShape(2.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Black else Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TrackHeaderView(
    track: TimelineTrack,
    module: TimelineModule,
    state: GraphState,
    width: Dp,
    height: Dp,
    onEditTrack: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(Color(0xFF2B2D30), RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicTextField(
                value = track.name,
                onValueChange = {
                    track.name = it
                    FileState.markDirty()
                },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 2.dp)
            )
            Text(
                text = "✕",
                color = Color.Gray,
                fontSize = 10.sp,
                modifier = Modifier.clickable {
                    module.removeTrack(track)
                    FileState.markDirty()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TrackButton(
                text = "M",
                isActive = track.isMuted,
                activeColor = Color(0xFFFFA000),
                onClick = {
                    track.isMuted = !track.isMuted
                    FileState.markDirty()
                }
            )
            TrackButton(
                text = "S",
                isActive = track.isSoloed,
                activeColor = Color(0xFFFFD54F),
                onClick = {
                    track.isSoloed = !track.isSoloed
                    FileState.markDirty()
                }
            )
            if (track.type.isRecordable) {
                TrackButton(
                    text = "R",
                    isActive = track.isRecordArmed,
                    activeColor = Color(0xFFE53935),
                    onClick = {
                        track.isRecordArmed = !track.isRecordArmed
                        FileState.markDirty()
                    }
                )
                TrackButton(
                    text = "I",
                    isActive = track.isInputMonitored,
                    activeColor = Color(0xFFFF7043),
                    onClick = {
                        track.isInputMonitored = !track.isInputMonitored
                        FileState.markDirty()
                    }
                )
            } else if (onEditTrack != null) {
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .background(Color(0xFF3C3F41), RoundedCornerShape(2.dp))
                        .clickable { onEditTrack() }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Edit", fontSize = 9.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun TrackSectionView(
    section: TrackSection,
    track: TimelineTrack,
    module: TimelineModule,
    state: GraphState,
    isBeats: Boolean,
    sectionColor: Color,
    pxPerUnit: Float,
    smallestSub: Double,
    height: Dp,
    density: Float,
    onEditSection: (() -> Unit)? = null
) {
    val sectionH = height - 6.dp
    val isSelected = state.selectedSectionInfo?.third === section

    if (section.repeats > 1) {
        for (r in 1 until section.repeats) {
            val isLastRepeat = (r == section.repeats - 1)
            Box(
                modifier = Modifier
                    .offset { IntOffset(((section.start + section.duration * r) * pxPerUnit).roundToInt(), (3f * density).roundToInt()) }
                    .width(((section.duration * pxPerUnit) / density).dp)
                    .height(sectionH)
                    .background(sectionColor.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) Color.White else sectionColor.copy(alpha = 0.6f),
                        RoundedCornerShape(2.dp)
                    )
            ) {
                Text(
                    text = "⟳ ${section.name.ifEmpty { "Sec ${section.id + 1}" }}",
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 2.dp, top = 1.dp)
                )

                val ghostMidiData = section.data as? MidiSectionData
                if (ghostMidiData != null) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)) {
                        val w = size.width
                        val h = size.height
                        val notes = ghostMidiData.pianoRoll.notes
                        if (notes.isNotEmpty()) {
                            val totalTicks = module.unitsToTicks(section.duration).coerceAtLeast(PianoRollTiming.GRID_TICKS)
                            val pitches = notes.map { ghostMidiData.pianoRoll.pitchOf(it) }
                            val minPitch = (pitches.minOrNull() ?: 60.0) - 2.0
                            val maxPitch = (pitches.maxOrNull() ?: 72.0) + 2.0
                            val pitchRange = (maxPitch - minPitch).coerceAtLeast(12.0)

                            for (note in notes) {
                                val p = ghostMidiData.pianoRoll.pitchOf(note)
                                val nx = ((note.startTicks.toFloat() / totalTicks) * w).coerceIn(0f, w)
                                val nw = ((note.durationTicks.toFloat() / totalTicks) * w).coerceAtLeast(2f)
                                val ny = ((1f - ((p - minPitch) / pitchRange).toFloat()) * (h - 4f)).coerceIn(0f, h - 3f)
                                drawRect(
                                    color = Color.White.copy(alpha = 0.4f),
                                    topLeft = Offset(nx, ny),
                                    size = Size(minOf(nw, w - nx), 2.5f.dp.toPx())
                                )
                            }
                        }
                    }
                }

                val ghostAudioData = section.data as? AudioSectionData
                if (ghostAudioData != null) {
                    val thumb = ghostAudioData.thumbnail
                    val count = thumb.blockCount
                    if (count > 0) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)) {
                            val w = size.width
                            val h = size.height
                            val midY = h / 2f
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(0f, midY),
                                end = Offset(w, midY),
                                strokeWidth = 1f
                            )
                            val sRate = ghostAudioData.recording?.frameRate?.takeIf { it > 0.0 } ?: SynthKSyn.synth.frameRate.toDouble()
                            val secDurSec = module.unitsToSeconds(section.duration).coerceAtLeast(0.001)
                            val blockWidthPx = ((thumb.blockSize.toDouble() / (sRate * secDurSec)) * w).toFloat()
                            val strokeW = maxOf(1f, blockWidthPx + 0.5f)
                            for (b in 0 until count) {
                                val blockSec = (b * thumb.blockSize).toDouble() / sRate
                                val bx = ((blockSec / secDurSec) * w).toFloat()
                                if (bx > w) break
                                val minV = thumb.minValues[b]
                                val maxV = thumb.maxValues[b]
                                val topY = (midY - maxV * (midY - 2f)).coerceIn(1f, h - 1f)
                                val bottomY = (midY - minV * (midY - 2f)).coerceIn(1f, h - 1f)
                                drawLine(
                                    color = Color.White.copy(alpha = 0.45f),
                                    start = Offset(bx, minOf(topY, bottomY)),
                                    end = Offset(bx, maxOf(topY, bottomY).coerceAtLeast(minOf(topY, bottomY) + 1f)),
                                    strokeWidth = strokeW
                                )
                            }
                        }
                    }
                }

                // Repeat drag handle on the last repeated ghost box
                if (isLastRepeat) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp, 14.dp)
                            .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(topEnd = 2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⟳", fontSize = 8.sp, lineHeight = 9.sp, color = Color.White)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset((section.start * pxPerUnit).roundToInt(), (3f * density).roundToInt()) }
            .width(((section.duration * pxPerUnit) / density).dp)
            .height(sectionH)
            .background(sectionColor.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) Color.White else sectionColor,
                RoundedCornerShape(2.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(15.dp)
                    .background(if (isSelected) sectionColor.copy(alpha = 1.0f) else sectionColor)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = section.name.ifEmpty { "Sec ${section.id + 1}" },
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = Color.White,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold
                    )
                    Text(
                        text = "✕",
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.clickable {
                            val e = state.engine
                            if (e != null) {
                                e.post(DeleteSectionCommand(track, section))
                            } else {
                                track.removeSection(section)
                            }
                            if (state.selectedSectionInfo?.third === section) {
                                state.clearSectionSelection()
                            }
                            FileState.markDirty()
                        }
                    )
                }
            }

            // Section body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val msgData = section.data as? MessageSectionData
                if (msgData != null) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)) {
                        val w = size.width
                        val h = size.height
                        val dur = section.duration.coerceAtLeast(0.001)
                        val range = (msgData.max - msgData.min).takeIf { it > 0.0 } ?: 1.0
                        msgData.events.forEach { ev ->
                            val evX = ((ev.time / dur).toFloat() * w).coerceIn(0f, w)
                            val normV = ((ev.value - msgData.min) / range).coerceIn(0.0, 1.0).toFloat()
                            val evY = ((1f - normV) * h).coerceIn(0f, h)

                            // Skinny stem
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(evX, h),
                                end = Offset(evX, evY),
                                strokeWidth = 1f
                            )
                            // Circle at value
                            drawCircle(
                                color = Color.White.copy(alpha = 0.85f),
                                radius = 2f.dp.toPx(),
                                center = Offset(evX, evY)
                            )
                        }
                    }
                }

                val midiData = section.data as? MidiSectionData
                if (midiData != null) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)) {
                        val w = size.width
                        val h = size.height
                        val notes = midiData.pianoRoll.notes
                        if (notes.isNotEmpty()) {
                            val totalTicks = module.unitsToTicks(section.duration).coerceAtLeast(PianoRollTiming.GRID_TICKS)
                            val pitches = notes.map { midiData.pianoRoll.pitchOf(it) }
                            val minPitch = (pitches.minOrNull() ?: 60.0) - 2.0
                            val maxPitch = (pitches.maxOrNull() ?: 72.0) + 2.0
                            val pitchRange = (maxPitch - minPitch).coerceAtLeast(12.0)

                            for (note in notes) {
                                val p = midiData.pianoRoll.pitchOf(note)
                                val nx = ((note.startTicks.toFloat() / totalTicks) * w).coerceIn(0f, w)
                                val nw = ((note.durationTicks.toFloat() / totalTicks) * w).coerceAtLeast(2f)
                                val ny = ((1f - ((p - minPitch) / pitchRange).toFloat()) * (h - 4f)).coerceIn(0f, h - 3f)
                                drawRect(
                                    color = Color.White.copy(alpha = 0.8f),
                                    topLeft = Offset(nx, ny),
                                    size = Size(minOf(nw, w - nx), 2.5f.dp.toPx())
                                )
                            }
                        }
                    }
                }

                val audioData = section.data as? AudioSectionData
                if (audioData != null) {
                    val thumb = audioData.thumbnail
                    val count = thumb.blockCount
                    if (count > 0) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)) {
                            val w = size.width
                            val h = size.height
                            val midY = h / 2f
                            drawLine(
                                color = Color.White.copy(alpha = 0.25f),
                                start = Offset(0f, midY),
                                end = Offset(w, midY),
                                strokeWidth = 1f
                            )
                            val sRate = audioData.recording?.frameRate?.takeIf { it > 0.0 } ?: SynthKSyn.synth.frameRate.toDouble()
                            val secDurSec = module.unitsToSeconds(section.duration).coerceAtLeast(0.001)
                            val blockWidthPx = ((thumb.blockSize.toDouble() / (sRate * secDurSec)) * w).toFloat()
                            val strokeW = maxOf(1f, blockWidthPx + 0.5f)
                            for (b in 0 until count) {
                                val blockSec = (b * thumb.blockSize).toDouble() / sRate
                                val bx = ((blockSec / secDurSec) * w).toFloat()
                                if (bx > w) break
                                val minV = thumb.minValues[b]
                                val maxV = thumb.maxValues[b]
                                val topY = (midY - maxV * (midY - 2f)).coerceIn(1f, h - 1f)
                                val bottomY = (midY - minV * (midY - 2f)).coerceIn(1f, h - 1f)
                                drawLine(
                                    color = Color.White.copy(alpha = 0.85f),
                                    start = Offset(bx, minOf(topY, bottomY)),
                                    end = Offset(bx, maxOf(topY, bottomY).coerceAtLeast(minOf(topY, bottomY) + 1f)),
                                    strokeWidth = strokeW
                                )
                            }
                        }
                    }
                }

                // Repeat Handle (Top-Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp, 14.dp)
                        .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(topEnd = 2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⟳", fontSize = 8.sp, lineHeight = 9.sp, color = Color.White)
                }

                // Resize Handle (Bottom-Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp, 14.dp)
                        .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(bottomEnd = 2.dp))
                        .pointerInput(section, module, pxPerUnit, smallestSub) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    down.consume()
                                    state.selectTrackSection(module, track, section)
                                    val initialDur = section.duration
                                    var accumDx = 0f
                                    val maxDur = track.getMaxDuration(section, module.maxTime)
                                    val minDur = if (isBeats) smallestSub else 0.1
                                    val clampedMaxDur = maxDur.coerceAtLeast(minDur)
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.pressed) {
                                            change.consume()
                                            val deltaX = change.position.x - change.previousPosition.x
                                            accumDx += deltaX
                                            val dUnits = (accumDx / pxPerUnit).toDouble()
                                            val rawDur = (initialDur + dUnits).coerceIn(minDur, clampedMaxDur)
                                            val isOptionHeld = event.keyboardModifiers.isAltPressed
                                            val newDur = if (isBeats && !isOptionHeld) {
                                                TimelineBeatUtils.snapToSubdivision(rawDur, smallestSub).coerceIn(minDur, clampedMaxDur)
                                            } else {
                                                rawDur
                                            }
                                            if (section.duration != newDur) {
                                                section.duration = newDur
                                                FileState.markDirty()
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⇥", fontSize = 8.sp, lineHeight = 9.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun TrackLaneView(
    track: TimelineTrack,
    module: TimelineModule,
    state: GraphState,
    isBeats: Boolean,
    coercedZoom: Float,
    timelineWidth: Dp,
    height: Dp,
    density: Float,
    currentPlayheadTime: Double,
    extraTime: Double,
    onEditSection: ((TrackSection) -> Unit)? = null
) {
    val pxPerUnit = density * 24f * coercedZoom
    val smallestSub = if (isBeats) {
        TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerUnit.toDouble(), density)
    } else 1.0

    val sectionColor = when (track.type) {
        TrackType.PATCH -> MaterialTheme.colorScheme.primary
        TrackType.MESSAGE -> Color(0xFF1E88E5)
        TrackType.MIDI -> Color(0xFFFB8C00)
        TrackType.AUDIO -> Color(0xFF43A047)
    }

    val maxTimeX = 24.dp * (module.maxTime.toFloat() * coercedZoom)

    var duplicateGhostStart by remember { mutableStateOf<Double?>(null) }
    var draggingSection by remember { mutableStateOf<TrackSection?>(null) }
    var lastTapMark by remember { mutableStateOf<kotlin.time.TimeMark?>(null) }
    var lastTapSection by remember { mutableStateOf<TrackSection?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(2.dp))
            .border(0.5.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
            .pointerInput(module, track, pxPerUnit, smallestSub) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()
                        val clickUnits = (down.position.x / pxPerUnit).toDouble()
                        val hitSection = track.sections.find { clickUnits >= it.start && clickUnits < (it.start + it.totalDuration) }

                        if (hitSection != null) {
                            val endPx = ((hitSection.start + hitSection.totalDuration) * pxPerUnit).toFloat()
                            val rep0EndPx = ((hitSection.start + hitSection.duration) * pxPerUnit).toFloat()
                            val handleW = 18f * density
                            val isNearTopRepeat = down.position.y <= 32f * density
                            val isNearEndRight = down.position.x >= (endPx - handleW) && down.position.x <= (endPx + 4f * density)
                            val isNearRep0Right = hitSection.repeats > 1 && down.position.x >= (rep0EndPx - handleW) && down.position.x <= (rep0EndPx + 4f * density) && down.position.y >= (15f * density)

                            if (isNearTopRepeat && (isNearEndRight || isNearRep0Right)) {
                                state.selectTrackSection(module, track, hitSection)
                                val initialRepeats = hitSection.repeats
                                val maxReps = track.getMaxRepeats(hitSection, module.maxTime)
                                val unitPx = (hitSection.duration * pxPerUnit).toFloat().coerceAtLeast(10f)
                                val threshold = 12f * density
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        change.consume()
                                        val accumDx = change.position.x - down.position.x
                                        val targetReps = when {
                                            accumDx >= threshold -> initialRepeats + 1 + ((accumDx - threshold) / unitPx).toInt()
                                            accumDx <= -threshold -> initialRepeats - (1 + ((-accumDx - threshold) / unitPx).toInt())
                                            else -> initialRepeats
                                        }.coerceIn(1, maxReps)
                                        if (hitSection.repeats != targetReps) {
                                            hitSection.repeats = targetReps
                                            FileState.markDirty()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                continue
                            }

                            val initialStart = hitSection.start
                            val grabOffsetUnits = clickUnits - initialStart
                            val bounds = track.getMoveBounds(hitSection, module.maxTime)
                            var isDragging = false
                            var isDuplicating = false

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    val dist = kotlin.math.abs(change.position.x - down.position.x)
                                    if (!isDragging && dist > 4f) {
                                        isDragging = true
                                        draggingSection = hitSection
                                        state.selectTrackSection(module, track, hitSection)
                                    }
                                    if (isDragging) {
                                        change.consume()
                                        val isOptionHeld = event.keyboardModifiers.isAltPressed
                                        val currentMouseUnits = (change.position.x / pxPerUnit).toDouble()
                                        val targetStart = currentMouseUnits - grabOffsetUnits

                                        if (isOptionHeld) {
                                            isDuplicating = true
                                            if (hitSection.start != initialStart) {
                                                hitSection.start = initialStart
                                            }
                                            val rawGhost = targetStart.coerceIn(0.0, module.maxTime - hitSection.totalDuration)
                                            val snappedGhost = if (isBeats) {
                                                TimelineBeatUtils.snapToSubdivision(rawGhost, smallestSub).coerceIn(0.0, module.maxTime - hitSection.totalDuration)
                                            } else {
                                                rawGhost
                                            }
                                            duplicateGhostStart = snappedGhost
                                        } else {
                                            if (isDuplicating) {
                                                isDuplicating = false
                                                duplicateGhostStart = null
                                            }
                                            val rawStart = targetStart.coerceIn(bounds)
                                            val newStart = if (isBeats) {
                                                TimelineBeatUtils.snapToSubdivision(rawStart, smallestSub).coerceIn(bounds)
                                            } else {
                                                rawStart
                                            }
                                            if (hitSection.start != newStart) {
                                                hitSection.start = newStart
                                                FileState.markDirty()
                                            }
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            val wasDragging = isDragging
                            val wasDuplicating = isDuplicating
                            val dropGhost = duplicateGhostStart
                            draggingSection = null
                            duplicateGhostStart = null

                            if (wasDragging) {
                                if (wasDuplicating && dropGhost != null) {
                                    val newSec = track.duplicateSection(hitSection, dropGhost)
                                    if (newSec != null) {
                                        val e = state.engine
                                        if (e != null) {
                                            e.post(AddSectionCommand(track, newSec))
                                        }
                                        state.selectTrackSection(module, track, newSec)
                                        FileState.markDirty()
                                    }
                                }
                            } else {
                                state.selectTrackSection(module, track, hitSection)
                                val mark = lastTapMark
                                val lastSec = lastTapSection
                                val now = kotlin.time.TimeSource.Monotonic.markNow()
                                if (lastSec === hitSection && mark != null && mark.elapsedNow().inWholeMilliseconds < 350) {
                                    onEditSection?.invoke(hitSection)
                                    lastTapMark = null
                                    lastTapSection = null
                                } else {
                                    lastTapMark = now
                                    lastTapSection = hitSection
                                }
                            }
                        } else {
                            var isDragging = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    val dist = kotlin.math.abs(change.position.x - down.position.x)
                                    if (dist > 4f) {
                                        isDragging = true
                                    }
                                    if (isDragging) {
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (!isDragging) {
                                state.clearSectionSelection()
                                val mark = lastTapMark
                                val lastSec = lastTapSection
                                val now = kotlin.time.TimeSource.Monotonic.markNow()
                                if (lastSec == null && mark != null && mark.elapsedNow().inWholeMilliseconds < 350) {
                                    if (clickUnits < module.maxTime - 0.05) {
                                        val start = if (isBeats) {
                                            TimelineBeatUtils.snapToSubdivision(clickUnits, smallestSub).coerceIn(0.0, module.maxTime)
                                        } else {
                                            clickUnits.coerceIn(0.0, module.maxTime)
                                        }
                                        if (track.canAddSectionAt(start)) {
                                            val defaultDuration = if (isBeats) 4.0 else 2.0
                                            val newSec = track.addSection(start, defaultDuration)
                                            if (newSec != null) {
                                                val e = state.engine
                                                if (e != null) {
                                                    e.post(AddSectionCommand(track, newSec))
                                                }
                                                state.selectTrackSection(module, track, newSec)
                                                FileState.markDirty()
                                            }
                                        }
                                    }
                                    lastTapMark = null
                                    lastTapSection = null
                                } else {
                                    lastTapMark = now
                                    lastTapSection = null
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (isBeats) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val totalTime = module.maxTime + extraTime
                val numSubTicks = (module.maxTime / smallestSub).roundToInt()
                for (i in 0..numSubTicks) {
                    val beat = if (smallestSub < 1.0) {
                        val d = (1.0 / smallestSub).roundToLong()
                        i.toDouble() / d.toDouble()
                    } else {
                        i.toDouble() * smallestSub
                    }
                    if (beat > module.maxTime + 0.0001) break

                    val pct = (beat / totalTime).toFloat()
                    val x = size.width * pct

                    val isWholeBeat = (beat % 1.0 < 0.0001 || (1.0 - (beat % 1.0)) < 0.0001)
                    val isHalfBeat = (beat % 0.5 < 0.0001 || (0.5 - (beat % 0.5)) < 0.0001)

                    val (gridColor, strokeW) = when {
                        isWholeBeat -> Color(0x22FFFFFF) to 1f
                        isHalfBeat -> Color(0x14FFFFFF) to 0.8f
                        else -> Color(0x0AFFFFFF) to 0.5f
                    }

                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = strokeW
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(timelineWidth - maxTimeX)
                .offset(x = maxTimeX)
                .background(Color(0x1F000000))
        )

        val ghostStart = duplicateGhostStart
        val ghostSec = draggingSection
        if (ghostStart != null && ghostSec != null) {
            val ghostX = (ghostStart * pxPerUnit).toFloat()
            val secDurPx = (ghostSec.duration * pxPerUnit).toFloat()
            val canDrop = track.canAddSectionAt(ghostStart) &&
                    track.getAvailableDurationAt(ghostStart, ghostSec.totalDuration) >= (ghostSec.totalDuration - 0.001)
            Box(
                modifier = Modifier
                    .offset { IntOffset(ghostX.roundToInt(), (3f * density).roundToInt()) }
                    .width(((secDurPx * ghostSec.repeats) / density).dp)
                    .height(height - 6.dp)
                    .background(
                        if (canDrop) sectionColor.copy(alpha = 0.5f) else Color(0x66E53935),
                        RoundedCornerShape(2.dp)
                    )
                    .border(
                        2.dp,
                        if (canDrop) Color(0xFF64B5F6) else Color(0xFFFF5252),
                        RoundedCornerShape(2.dp)
                    )
            ) {
                Text(
                    text = "+ ${ghostSec.name.ifEmpty { "Sec ${ghostSec.id + 1}" }} (Copy)",
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }

        track.sections.forEach { section ->
            TrackSectionView(
                section = section,
                track = track,
                module = module,
                state = state,
                isBeats = isBeats,
                sectionColor = sectionColor,
                pxPerUnit = pxPerUnit,
                smallestSub = smallestSub,
                height = height,
                density = density,
                onEditSection = { onEditSection?.invoke(section) }
            )
        }

        val cursorPct = (currentPlayheadTime / (module.maxTime + extraTime)).toFloat().coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.5.dp)
                .offset(x = timelineWidth * cursorPct)
                .background(Color.Red)
        )
    }
}

@Composable
fun TimelineModuleNode(module: TimelineModule, state: GraphState) {
    val isBeats = module is BeatLineModule
    val colors = LocalGraphColors.current
    val position = state.modulePositions[module] ?: Offset.Zero
    val isSelected = state.selectedModules.contains(module)
    val borderColor = if (isSelected) colors.moduleBorderSelected else colors.moduleBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val density = LocalDensity.current
    val mdiState = LocalMdiState.current

    val hasTrackInputs = module.tracks.any { it.inputJack != null }
    val jackColWidth = if (hasTrackInputs) 38.dp else 0.dp
    val headerCardWidth = 86.dp
    val leftWidth = jackColWidth + headerCardWidth
    val hasOutputs = module.tracks.any { it.outputJacks.isNotEmpty() }
    val rightWidth = if (hasOutputs) 52.dp else 0.dp
    val spacing = 6.dp
    val viewportWidth = (module.width.dp - leftWidth - (if (hasOutputs) rightWidth + spacing else 0.dp) - spacing - 2.dp).coerceAtLeast(100.dp)

    val minZoom = (viewportWidth.value / (24f * (module.maxTime.toFloat() + 5.0f))).coerceIn(0.01f, 1f)
    val maxZoom = (viewportWidth.value / (24f * 2.0f)).coerceIn(4f, 20f)
    val coercedZoom = module.zoom.coerceIn(minZoom, maxZoom)

    val scrollState = rememberScrollState()
    var pendingScrollTarget by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(coercedZoom, scrollState.maxValue) {
        pendingScrollTarget?.let { target ->
            val clamped = target.coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(clamped)
            val maxPossible = with(density) {
                ((24.dp * ((module.maxTime + 5.0).toFloat() * coercedZoom)).toPx() - viewportWidth.toPx()).coerceAtLeast(0f)
            }
            if (target <= scrollState.maxValue || scrollState.maxValue >= maxPossible - 2f) {
                pendingScrollTarget = null
            }
        }
    }

    val viewportLeftGraphX = position.x + with(density) { (leftWidth + spacing).toPx() }
    val viewportRightGraphX = position.x + with(density) { (leftWidth + spacing + viewportWidth).toPx() }

    val openPatchTrackEditor = { track: PatchTrack ->
        state.clearSelection()
        val window = MdiWindowInfo(
            id = track.patch,
            title = "Track: ${track.name}",
            position = Offset(
                ((state.modulePositions[module]?.x ?: 100f) + 30f) * density.density,
                ((state.modulePositions[module]?.y ?: 100f) + 40f) * density.density
            ),
            width = 650.dp,
            height = 480.dp,
            graphState = track.patch.graphState,
            onClose = { state.clearSelection() },
            content = { GraphEditor(state = track.patch.graphState) }
        )
        mdiState.open(window)
    }

    val openMessageTrackEditor = { track: MessageTrack, sec: TrackSection ->
        state.clearSelection()
        val window = MdiWindowInfo(
            id = sec,
            title = "${track.name} : ${sec.name.ifEmpty { "Sec ${sec.id + 1}" }}",
            position = Offset(
                ((state.modulePositions[module]?.x ?: 100f) + 30f) * density.density,
                ((state.modulePositions[module]?.y ?: 100f) + 40f) * density.density
            ),
            width = 480.dp,
            height = 280.dp,
            graphState = null,
            onClose = { state.clearSelection() },
            content = { MessageColumnEditor(section = sec, track = track) }
        )
        mdiState.open(window)
    }

    val openMidiTrackEditor = { track: MidiTrack, sec: TrackSection ->
        state.clearSelection()
        val midiData = sec.data as? MidiSectionData ?: MidiSectionData().also {
            track.bindSectionData(it)
            sec.data = it
        }
        val prModule = midiData.pianoRoll
        prModule.name = "${track.name} : ${sec.name.ifEmpty { "Sec ${sec.id + 1}" }}"
        (state.engine ?: module.getEngine())?.let { prModule.setEngine(it) }
        prModule.tempo.setValue(module.getTempoBpm())

        val window = MdiWindowInfo(
            id = sec,
            title = prModule.name,
            position = Offset(
                ((state.modulePositions[module]?.x ?: 100f) + 30f) * density.density,
                ((state.modulePositions[module]?.y ?: 100f) + 40f) * density.density
            ),
            width = prModule.windowWidth?.dp ?: 800.dp,
            height = prModule.windowHeight?.dp ?: 500.dp,
            graphState = state,
            onClose = { state.clearSelection() },
            content = { PianoRollEditor(prModule, state) }
        )
        window.onGeometryChanged = {
            prModule.windowX = window.position.x / density.density
            prModule.windowY = window.position.y / density.density
            prModule.windowWidth = window.width.value
            prModule.windowHeight = window.height.value
        }
        mdiState.open(window)
    }

    val transportInputs = module.inputs.filter { jack -> module.tracks.none { it.inputJack === jack } }
    val topSectionHeight = maxOf(60.dp, if (transportInputs.isNotEmpty()) (transportInputs.size * 18 + 4).dp else 0.dp)
    val trackHeight = 48.dp
    val triggerHeight = 44.dp

    var isTrackMenuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .offset { IntOffset((position.x * density.density).roundToInt(), (position.y * density.density).roundToInt()) }
            .width(module.width.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                state.updateModuleSize(module, size.width.toFloat(), size.height.toFloat())
            },
        shape = RoundedCornerShape(4.dp),
        color = colors.moduleBackground
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ModuleHeader(
                module = module,
                state = state,
                headerColor = colors.headerDefault,
                label = if (isBeats) "BeatLine" else "Timeline",
                height = 28.dp,
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Box {
                            Button(
                                onClick = { isTrackMenuOpen = true },
                                modifier = Modifier.height(20.dp),
                                shape = RoundedCornerShape(3.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("+Track", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = isTrackMenuOpen,
                                onDismissRequest = { isTrackMenuOpen = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Patch Track", fontSize = 12.sp) },
                                    onClick = {
                                        isTrackMenuOpen = false
                                        module.addTrack(TrackType.PATCH)
                                        FileState.markDirty()
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Message Track", fontSize = 12.sp) },
                                    onClick = {
                                        isTrackMenuOpen = false
                                        module.addTrack(TrackType.MESSAGE)
                                        FileState.markDirty()
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("MIDI Track", fontSize = 12.sp) },
                                    onClick = {
                                        isTrackMenuOpen = false
                                        module.addTrack(TrackType.MIDI)
                                        FileState.markDirty()
                                    }
                                )
                                if (AudioInputBridge.isSupported()) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Audio Track", fontSize = 12.sp) },
                                        onClick = {
                                            isTrackMenuOpen = false
                                            module.addTrack(TrackType.AUDIO)
                                            FileState.markDirty()
                                        }
                                    )
                                }
                            }
                        }

                        if (module.tracks.any { it.type.isRecordable }) {
                            TooltipArea(text = if (module.isRecording) "Stop Recording" else "Record") {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                    Button(
                                        onClick = {
                                            module.requestRecordToggle()
                                            FileState.markDirty()
                                        },
                                        modifier = Modifier.size(width = 24.dp, height = 20.dp),
                                        shape = RoundedCornerShape(3.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (module.isRecording) Color(0xFFFF1744) else Color(0xFFC62828),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        RecordIcon(
                                            color = Color.White,
                                            size = 10.dp
                                        )
                                    }
                                }
                            }
                        }

                        TooltipArea(text = if (module.loopMode) "Loop: On" else "Loop: Off") {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                Button(
                                    onClick = {
                                        module.loopMode = !module.loopMode
                                        FileState.markDirty()
                                    },
                                    modifier = Modifier.size(width = 24.dp, height = 20.dp),
                                    shape = RoundedCornerShape(3.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (module.loopMode) Color(0xFF1B5E20) else Color(0xFF374151),
                                        contentColor = if (module.loopMode) Color(0xFF81C784) else Color(0xFFB0BEC5)
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    if (module.loopMode) {
                                        LoopIcon(
                                            color = Color(0xFF81C784),
                                            size = 13.dp
                                        )
                                    } else {
                                        ArrowForwardIcon(
                                            color = Color(0xFFB0BEC5),
                                            size = 13.dp
                                        )
                                    }
                                }
                            }
                        }
                        Text("Zoom", fontSize = 10.sp, color = Color.White)
                        Slider(
                            value = coercedZoom,
                            onValueChange = { newZoom ->
                                val oldZoom = coercedZoom
                                val currentPos = if (module.isPlaying) module.getCurrentTime() else module.seekTime
                                val oldPxPerUnit = density.density * 24f * oldZoom
                                val newPxPerUnit = density.density * 24f * newZoom
                                val viewportWidthPx = with(density) { viewportWidth.toPx() }
                                val oldScrollX = scrollState.value.toFloat()
                                val cursorXPx = (currentPos * oldPxPerUnit).toFloat()
                                val screenX = cursorXPx - oldScrollX

                                val targetScrollX = if (screenX in 0f..viewportWidthPx) {
                                    (currentPos * newPxPerUnit).toFloat() - screenX
                                } else {
                                    (oldScrollX + viewportWidthPx / 2f) * (newZoom / oldZoom) - viewportWidthPx / 2f
                                }
                                val targetInt = targetScrollX.roundToInt().coerceAtLeast(0)
                                pendingScrollTarget = targetInt
                                module.zoom = newZoom
                                FileState.markDirty()
                                coroutineScope.launch {
                                    scrollState.scrollTo(targetInt.coerceIn(0, scrollState.maxValue))
                                }
                            },
                            valueRange = minZoom..maxZoom,
                            modifier = Modifier.width(70.dp).height(18.dp)
                        )
                    }
                }
            )

            Box(
                modifier = Modifier.fillMaxWidth().padding(start = 0.dp, top = 6.dp, end = 0.dp, bottom = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.width(leftWidth)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(topSectionHeight),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            transportInputs.forEach { jack ->
                                JackView(
                                    jack = jack,
                                    state = state,
                                    isLeft = true,
                                    trailingContent = {
                                        if (jack.name == "tempo" && module is BeatLineModule && module.isTempoLocked) {
                                            Text(" 🔒", fontSize = 10.sp, lineHeight = 10.sp)
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        module.tracks.forEach { track ->
                            Row(
                                modifier = Modifier.width(leftWidth).height(trackHeight),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (jackColWidth > 0.dp) {
                                    Box(
                                        modifier = Modifier.width(jackColWidth).fillMaxHeight(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        track.inputJack?.let { JackView(it, state, isLeft = true) }
                                    }
                                }
                                TrackHeaderView(
                                    track = track,
                                    module = module,
                                    state = state,
                                    width = if (jackColWidth > 0.dp) headerCardWidth else leftWidth,
                                    height = trackHeight,
                                    onEditTrack = if (track is PatchTrack) {
                                        { openPatchTrackEditor(track) }
                                    } else null
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Box(
                            modifier = Modifier
                                .width(leftWidth)
                                .height(triggerHeight)
                                .padding(start = 4.dp, top = 6.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text("Triggers", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(spacing))

                    val extraTime = 5.0
                    val timelineWidth = 24.dp * ((module.maxTime + extraTime).toFloat() * coercedZoom)
                    val maxTimeX = 24.dp * (module.maxTime.toFloat() * coercedZoom)

                    Column(
                        modifier = Modifier.width(viewportWidth)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .background(Color(0xFFE5E7EB), RoundedCornerShape(3.dp))
                                .pointerInput(scrollState.maxValue, timelineWidth) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            val trackWidthPx = size.width.toFloat()
                                            val viewportWidthPx = with(density) { viewportWidth.toPx() }
                                            val timelineWidthPx = with(density) { timelineWidth.toPx() }

                                            if (timelineWidthPx > viewportWidthPx && scrollState.maxValue > 0) {
                                                val ratio = viewportWidthPx / timelineWidthPx
                                                val thumbWidthPx = trackWidthPx * ratio

                                                var clickX = down.position.x
                                                val targetScrollPx = ((clickX - thumbWidthPx / 2f) / (trackWidthPx - thumbWidthPx)) * scrollState.maxValue
                                                coroutineScope.launch {
                                                    scrollState.scrollTo(targetScrollPx.roundToInt().coerceIn(0, scrollState.maxValue))
                                                }

                                                do {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull()
                                                    if (change != null && change.pressed) {
                                                        change.consume()
                                                        clickX = change.position.x
                                                        val dragScrollPx = ((clickX - thumbWidthPx / 2f) / (trackWidthPx - thumbWidthPx)) * scrollState.maxValue
                                                        coroutineScope.launch {
                                                            scrollState.scrollTo(dragScrollPx.roundToInt().coerceIn(0, scrollState.maxValue))
                                                        }
                                                    }
                                                } while (event.changes.any { it.pressed })
                                            }
                                        }
                                    }
                                }
                        ) {
                            val viewportWidthPx = with(density) { viewportWidth.toPx() }
                            val timelineWidthPx = with(density) { timelineWidth.toPx() }
                            if (timelineWidthPx > viewportWidthPx && scrollState.maxValue > 0) {
                                val ratio = viewportWidthPx / timelineWidthPx
                                val scrollPct = scrollState.value.toFloat() / scrollState.maxValue
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(ratio)
                                        .offset(x = (viewportWidth * (1f - ratio) * scrollPct))
                                        .background(Color(0xFF888888), RoundedCornerShape(3.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState)
                        ) {
                            Column(
                                modifier = Modifier.width(timelineWidth)
                            ) {
                                var currentPlayheadTime by remember { mutableStateOf(0.0) }
                                if (module.isPlaying) {
                                    LaunchedEffect(Unit) {
                                        while (true) {
                                            currentPlayheadTime = module.getCurrentTime()
                                            kotlinx.coroutines.delay(16)
                                        }
                                    }
                                } else {
                                    currentPlayheadTime = module.seekTime
                                }

                                val centerTopRemaining = (topSectionHeight - 20.dp).coerceAtLeast(40.dp)
                                Column(modifier = Modifier.fillMaxWidth().height(centerTopRemaining)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(16.dp)
                                            .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                                            .pointerInput(module) {
                                                val getPxPerSecond = {
                                                    val d = density.density
                                                    val inputsWidthPx = leftWidth.value * d
                                                    val spacingPx = spacing.value * d
                                                    val viewportWidthPx = (module.width.toFloat() * d - inputsWidthPx - spacingPx).coerceAtLeast(100f * d)
                                                    val minZoomVal = (viewportWidthPx / (d * 24f * (module.maxTime.toFloat() + 5.0f))).coerceIn(0.01f, 1f)
                                                    val maxZoomVal = (viewportWidthPx / (d * 24f * 2.0f)).coerceIn(4f, 20f)
                                                    val currentZoom = module.zoom.coerceIn(minZoomVal, maxZoomVal)
                                                    d * 24f * currentZoom
                                                }

                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                                        val pxPerSecond = getPxPerSecond()
                                                        val clickX = down.position.x

                                                        val startX = (module.loopStart * pxPerSecond).toFloat()
                                                        val endX = (module.loopEnd * pxPerSecond).toFloat()
                                                        val maxTimeXPx = (module.maxTime * pxPerSecond).toFloat()
                                                        val tolerance = 10f * density.density

                                                        val isDraggingStart = kotlin.math.abs(clickX - startX) <= tolerance
                                                        val isDraggingEnd = kotlin.math.abs(clickX - endX) <= tolerance
                                                        val isDraggingMaxTime = kotlin.math.abs(clickX - maxTimeXPx) <= tolerance

                                                        if (isDraggingMaxTime) {
                                                            down.consume()
                                                            do {
                                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                                val change = event.changes.firstOrNull()
                                                                if (change != null && change.pressed) {
                                                                    change.consume()
                                                                    val currentX = change.position.x
                                                                    val rawMax = (currentX / getPxPerSecond()).toDouble().coerceAtLeast(module.loopEnd)
                                                                    val isOptionHeld = event.keyboardModifiers.isAltPressed
                                                                    val newMax = if (isBeats && !isOptionHeld) {
                                                                        val smallestSub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(getPxPerSecond().toDouble(), density.density)
                                                                        TimelineBeatUtils.snapToSubdivision(rawMax, smallestSub).coerceAtLeast(module.loopEnd)
                                                                    } else {
                                                                        rawMax
                                                                    }
                                                                    module.maxTime = newMax
                                                                    FileState.markDirty()
                                                                }
                                                            } while (event.changes.any { it.pressed })
                                                        } else if (isDraggingStart) {
                                                            down.consume()
                                                            do {
                                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                                val change = event.changes.firstOrNull()
                                                                if (change != null && change.pressed) {
                                                                    change.consume()
                                                                    val currentX = change.position.x
                                                                    val smallestSub = if (isBeats) TimelineBeatUtils.calculateSmallestVisibleSubdivision(getPxPerSecond().toDouble(), density.density) else 0.1
                                                                    val minGap = if (isBeats) smallestSub else 0.1
                                                                    val rawStart = (currentX / getPxPerSecond()).toDouble().coerceIn(0.0, module.loopEnd - minGap)
                                                                    val isOptionHeld = event.keyboardModifiers.isAltPressed
                                                                    val newStart = if (isBeats && !isOptionHeld) {
                                                                        TimelineBeatUtils.snapToSubdivision(rawStart, smallestSub).coerceIn(0.0, module.loopEnd - minGap)
                                                                    } else {
                                                                        rawStart
                                                                    }
                                                                    module.loopStart = newStart
                                                                    FileState.markDirty()
                                                                }
                                                            } while (event.changes.any { it.pressed })
                                                        } else if (isDraggingEnd) {
                                                            down.consume()
                                                            do {
                                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                                val change = event.changes.firstOrNull()
                                                                if (change != null && change.pressed) {
                                                                    change.consume()
                                                                    val currentX = change.position.x
                                                                    val smallestSub = if (isBeats) TimelineBeatUtils.calculateSmallestVisibleSubdivision(getPxPerSecond().toDouble(), density.density) else 0.1
                                                                    val minGap = if (isBeats) smallestSub else 0.1
                                                                    val rawEnd = (currentX / getPxPerSecond()).toDouble().coerceIn(module.loopStart + minGap, module.maxTime)
                                                                    val isOptionHeld = event.keyboardModifiers.isAltPressed
                                                                    val newEnd = if (isBeats && !isOptionHeld) {
                                                                        TimelineBeatUtils.snapToSubdivision(rawEnd, smallestSub).coerceIn(module.loopStart + minGap, module.maxTime)
                                                                    } else {
                                                                        rawEnd
                                                                    }
                                                                    module.loopEnd = newEnd
                                                                    FileState.markDirty()
                                                                }
                                                            } while (event.changes.any { it.pressed })
                                                        } else {
                                                            val smallestSub = if (isBeats) TimelineBeatUtils.calculateSmallestVisibleSubdivision(getPxPerSecond().toDouble(), density.density) else 0.1
                                                            val isOptionHeldDown = currentEvent.keyboardModifiers.isAltPressed
                                                            val clickedTime = if (isBeats && !isOptionHeldDown) {
                                                                val rawTime = (clickX / pxPerSecond).toDouble().coerceIn(0.0, module.maxTime)
                                                                TimelineBeatUtils.snapToSubdivision(rawTime, smallestSub).coerceIn(0.0, module.maxTime)
                                                            } else {
                                                                (clickX / pxPerSecond).toDouble().coerceIn(0.0, module.maxTime)
                                                            }
                                                            val loopLen = module.loopEnd - module.loopStart
                                                            val newStart = (clickedTime - loopLen / 2.0).coerceIn(0.0, module.maxTime - loopLen)
                                                            val finalStart = if (isBeats && !isOptionHeldDown) {
                                                                TimelineBeatUtils.snapToSubdivision(newStart, smallestSub).coerceIn(0.0, module.maxTime - loopLen)
                                                            } else {
                                                                newStart
                                                            }
                                                            module.loopStart = finalStart
                                                            module.loopEnd = finalStart + loopLen
                                                            FileState.markDirty()

                                                            down.consume()
                                                            do {
                                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                                val change = event.changes.firstOrNull()
                                                                if (change != null && change.pressed) {
                                                                    change.consume()
                                                                    val currentX = change.position.x
                                                                    val isOptionHeldDrag = event.keyboardModifiers.isAltPressed
                                                                    val dragTime = if (isBeats && !isOptionHeldDrag) {
                                                                        val rawDrag = (currentX / getPxPerSecond()).toDouble().coerceIn(0.0, module.maxTime)
                                                                        TimelineBeatUtils.snapToSubdivision(rawDrag, smallestSub).coerceIn(0.0, module.maxTime)
                                                                    } else {
                                                                        (currentX / getPxPerSecond()).toDouble().coerceIn(0.0, module.maxTime)
                                                                    }
                                                                    val dragStart = (dragTime - loopLen / 2.0).coerceIn(0.0, module.maxTime - loopLen)
                                                                    val finalDragStart = if (isBeats && !isOptionHeldDrag) {
                                                                        TimelineBeatUtils.snapToSubdivision(dragStart, smallestSub).coerceIn(0.0, module.maxTime - loopLen)
                                                                    } else {
                                                                        dragStart
                                                                    }
                                                                    module.loopStart = finalDragStart
                                                                    module.loopEnd = finalDragStart + loopLen
                                                                    FileState.markDirty()
                                                                }
                                                            } while (event.changes.any { it.pressed })
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        if (isBeats) {
                                            val pxPerUnit = density.density * 24f * coercedZoom
                                            val smallestSub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerUnit.toDouble(), density.density)
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val totalTime = module.maxTime + extraTime
                                                val numSubTicks = (module.maxTime / smallestSub).roundToInt()
                                                for (i in 0..numSubTicks) {
                                                    val beat = if (smallestSub < 1.0) {
                                                        val d = (1.0 / smallestSub).roundToLong()
                                                        i.toDouble() / d.toDouble()
                                                    } else {
                                                        i.toDouble() * smallestSub
                                                    }
                                                    if (beat > module.maxTime + 0.0001) break

                                                    val pct = (beat / totalTime).toFloat()
                                                    val x = size.width * pct

                                                    val isWholeBeat = (beat % 1.0 < 0.0001 || (1.0 - (beat % 1.0)) < 0.0001)
                                                    val isHalfBeat = (beat % 0.5 < 0.0001 || (0.5 - (beat % 0.5)) < 0.0001)

                                                    val (gridColor, strokeW) = when {
                                                        isWholeBeat -> Color(0x33000000) to 1f
                                                        isHalfBeat -> Color(0x20000000) to 0.8f
                                                        else -> Color(0x10000000) to 0.5f
                                                    }

                                                    drawLine(
                                                        color = gridColor,
                                                        start = Offset(x, 0f),
                                                        end = Offset(x, size.height),
                                                        strokeWidth = strokeW
                                                    )
                                                }
                                            }
                                        }

                                        val startPct = (module.loopStart / (module.maxTime + extraTime)).toFloat()
                                        val endPct = (module.loopEnd / (module.maxTime + extraTime)).toFloat()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(endPct - startPct)
                                                .offset(x = timelineWidth * startPct)
                                                .background(Color(0xFF90CAF9).copy(alpha = 0.6f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp, 16.dp)
                                                .offset(x = timelineWidth * startPct)
                                                .background(Color.Blue)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp, 16.dp)
                                                .offset(x = timelineWidth * endPct - 6.dp)
                                                .background(Color.Blue)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp, 16.dp)
                                                .offset(x = maxTimeX - 7.dp)
                                                .background(Color(0xFFD32F2F), RoundedCornerShape(2.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("⇥", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(20.dp)
                                            .background(Color(0xFFCCCCCC), RoundedCornerShape(2.dp))
                                            .pointerInput(module) {
                                                val getPxPerSecond = {
                                                    val d = density.density
                                                    val inputsWidthPx = leftWidth.value * d
                                                    val spacingPx = spacing.value * d
                                                    val viewportWidthPx = (module.width.toFloat() * d - inputsWidthPx - spacingPx).coerceAtLeast(100f * d)
                                                    val minZoomVal = (viewportWidthPx / (d * 24f * (module.maxTime.toFloat() + 5.0f))).coerceIn(0.01f, 1f)
                                                    val maxZoomVal = (viewportWidthPx / (d * 24f * 2.0f)).coerceIn(4f, 20f)
                                                    val currentZoom = module.zoom.coerceIn(minZoomVal, maxZoomVal)
                                                    d * 24f * currentZoom
                                                }

                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                                        var currentX = down.position.x
                                                        val pxPerSecond = getPxPerSecond()
                                                        val rawSeek = (currentX / pxPerSecond).toDouble().coerceIn(0.0, module.maxTime)
                                                        val isOptionHeldDown = currentEvent.keyboardModifiers.isAltPressed
                                                        val newSeek = if (isBeats && !isOptionHeldDown) {
                                                            val smallestSub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerSecond.toDouble(), density.density)
                                                            TimelineBeatUtils.snapToSubdivision(rawSeek, smallestSub).coerceIn(0.0, module.maxTime)
                                                        } else {
                                                            rawSeek
                                                        }
                                                        module.seekTo(newSeek)
                                                        FileState.markDirty()

                                                        down.consume()
                                                        do {
                                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                                            val change = event.changes.firstOrNull()
                                                            if (change != null && change.pressed) {
                                                                change.consume()
                                                                currentX = change.position.x
                                                                val isOptionHeldDrag = event.keyboardModifiers.isAltPressed
                                                                val dragRawSeek = (currentX / getPxPerSecond()).toDouble().coerceIn(0.0, module.maxTime)
                                                                val dragSeek = if (isBeats && !isOptionHeldDrag) {
                                                                    val smallestSub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerSecond.toDouble(), density.density)
                                                                    TimelineBeatUtils.snapToSubdivision(dragRawSeek, smallestSub).coerceIn(0.0, module.maxTime)
                                                                } else {
                                                                    dragRawSeek
                                                                }
                                                                module.seekTo(dragSeek)
                                                                FileState.markDirty()
                                                            }
                                                        } while (event.changes.any { it.pressed })
                                                    }
                                                }
                                            }
                                    ) {
                                        val totalTime = module.maxTime + extraTime
                                        if (isBeats) {
                                            val pxPerUnit = density.density * 24f * coercedZoom
                                            val smallestSub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerUnit.toDouble(), density.density)
                                            val labelInterval = TimelineBeatUtils.calculateLabelInterval(pxPerUnit.toDouble(), density.density)
                                            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val numSubTicks = (module.maxTime / smallestSub).roundToInt()
                                                for (i in 0..numSubTicks) {
                                                    val beat = if (smallestSub < 1.0) {
                                                        val d = (1.0 / smallestSub).roundToLong()
                                                        i.toDouble() / d.toDouble()
                                                    } else {
                                                        i.toDouble() * smallestSub
                                                    }
                                                    if (beat > module.maxTime + 0.0001) break

                                                    val pct = (beat / totalTime).toFloat()
                                                    val x = size.width * pct

                                                    val isWholeBeat = (beat % 1.0 < 0.0001 || (1.0 - (beat % 1.0)) < 0.0001)
                                                    val isHalfBeat = (beat % 0.5 < 0.0001 || (0.5 - (beat % 0.5)) < 0.0001)
                                                    val isQuarterBeat = (beat % 0.25 < 0.0001 || (0.25 - (beat % 0.25)) < 0.0001)

                                                    val (tickHeight, tickColor, strokeW) = when {
                                                        isWholeBeat -> Triple(size.height * 0.7f, Color(0xFF333333), 1.2f)
                                                        isHalfBeat -> Triple(size.height * 0.5f, Color(0xFF555555), 1.0f)
                                                        isQuarterBeat -> Triple(size.height * 0.35f, Color(0xFF777777), 0.8f)
                                                        else -> Triple(size.height * 0.25f, Color(0xFF999999), 0.6f)
                                                    }

                                                    drawLine(
                                                        color = tickColor,
                                                        start = Offset(x, size.height - tickHeight),
                                                        end = Offset(x, size.height),
                                                        strokeWidth = strokeW
                                                    )

                                                    if (isWholeBeat) {
                                                        val wholeBeat = (beat + 0.5).toInt()
                                                        if (wholeBeat % labelInterval.toInt() == 0) {
                                                            val labelText = wholeBeat.toString()
                                                            val measured = textMeasurer.measure(
                                                                text = labelText,
                                                                style = TextStyle(color = Color(0xFF333333), fontSize = 9.sp)
                                                            )
                                                            drawText(
                                                                textMeasurer = textMeasurer,
                                                                text = labelText,
                                                                topLeft = Offset(x + 2f, 1f),
                                                                style = TextStyle(color = Color(0xFF333333), fontSize = 9.sp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val step = when {
                                                    coercedZoom >= 5f -> 0.2
                                                    coercedZoom >= 2f -> 0.5
                                                    coercedZoom >= 1f -> 1.0
                                                    coercedZoom >= 0.5f -> 2.0
                                                    coercedZoom >= 0.2f -> 5.0
                                                    else -> 10.0
                                                }
                                                val count = (totalTime / step).toInt()
                                                for (i in 0..count) {
                                                    val t = i * step
                                                    if (t > totalTime) break
                                                    val pct = (t / totalTime).toFloat()
                                                    val x = size.width * pct
                                                    val isMajor = (t % (step * 5).coerceAtLeast(1.0) == 0.0)
                                                    val tickH = if (isMajor) size.height * 0.6f else size.height * 0.3f
                                                    drawLine(
                                                        color = if (isMajor) Color.DarkGray else Color.Gray,
                                                        start = Offset(x, size.height - tickH),
                                                        end = Offset(x, size.height),
                                                        strokeWidth = if (isMajor) 1.5f else 1f
                                                    )
                                                }
                                            }
                                        }

                                        val thumbPct = (module.seekTime / (module.maxTime + extraTime)).toFloat()
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp, 20.dp)
                                                .offset(x = timelineWidth * thumbPct - 4.dp)
                                                .background(Color.DarkGray.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                module.tracks.forEach { track ->
                                    TrackLaneView(
                                        track = track,
                                        module = module,
                                        state = state,
                                        isBeats = isBeats,
                                        coercedZoom = coercedZoom,
                                        timelineWidth = timelineWidth,
                                        height = trackHeight,
                                        density = density.density,
                                        currentPlayheadTime = currentPlayheadTime,
                                        extraTime = extraTime,
                                        onEditSection = { sec ->
                                            when (track) {
                                                is PatchTrack -> openPatchTrackEditor(track)
                                                is MessageTrack -> openMessageTrackEditor(track, sec)
                                                is MidiTrack -> openMidiTrackEditor(track, sec)
                                                else -> {}
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(triggerHeight)
                                        .background(Color(0xFFE5E7EB), RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                        .pointerInput(module, isBeats) {
                                            val getPxPerSecond = {
                                                val d = density.density
                                                val inputsWidthPx = leftWidth.value * d
                                                val spacingPx = spacing.value * d
                                                val viewportWidthPx = (module.width.toFloat() * d - inputsWidthPx - spacingPx).coerceAtLeast(100f * d)
                                                val minZoomVal = (viewportWidthPx / (d * 24f * (module.maxTime.toFloat() + 5.0f))).coerceIn(0.01f, 1f)
                                                val maxZoomVal = (viewportWidthPx / (d * 24f * 2.0f)).coerceIn(4f, 20f)
                                                val currentZoom = module.zoom.coerceIn(minZoomVal, maxZoomVal)
                                                d * 24f * currentZoom
                                            }

                                            awaitPointerEventScope {
                                                var lastTapMark: kotlin.time.TimeMark? = null
                                                var lastTapPos: Offset? = null

                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val down = event.changes.firstOrNull { !it.previousPressed && it.pressed } ?: continue
                                                    val isShiftHeld = event.keyboardModifiers.isShiftPressed
                                                    val pxPerSecond = getPxPerSecond()
                                                    val clickX = down.position.x
                                                    val clickY = down.position.y

                                                    val smallestSub = if (isBeats) TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerSecond.toDouble(), density.density) else 1.0
                                                    val subPx = (smallestSub * pxPerSecond).toFloat()
                                                    val maxAllowedTolerance = if (isBeats) (subPx * 0.4f) else (5f * density.density)
                                                    val tolerancePx = minOf(5f * density.density, maxAllowedTolerance).coerceAtLeast(2.5f * density.density)
                                                    val jackTopBound = (triggerHeight.value - 14f) * density.density

                                                    val nearestTrigger = module.triggers.minByOrNull {
                                                        val trigXPx = (it.time * pxPerSecond.toDouble()).toFloat()
                                                        kotlin.math.abs(trigXPx - clickX)
                                                    }?.takeIf {
                                                        val trigXPx = (it.time * pxPerSecond.toDouble()).toFloat()
                                                        kotlin.math.abs(trigXPx - clickX) <= tolerancePx
                                                    }

                                                    val isOverJack = nearestTrigger != null && clickY >= jackTopBound
                                                    if (isOverJack) {
                                                        continue
                                                    }

                                                    val activeTrigger = if (clickY < jackTopBound) nearestTrigger else null

                                                    if (activeTrigger != null) {
                                                        down.consume()
                                                        lastTapMark = null
                                                        lastTapPos = null
                                                        if (isShiftHeld) {
                                                            module.removeTrigger(activeTrigger)
                                                            FileState.markDirty()
                                                        } else {
                                                            var isDragging = false
                                                            do {
                                                                val dragEvent = awaitPointerEvent(PointerEventPass.Initial)
                                                                val change = dragEvent.changes.firstOrNull()
                                                                if (change != null && change.pressed) {
                                                                    val dist = kotlin.math.abs(change.position.x - down.position.x)
                                                                    if (dist > 3f * density.density) {
                                                                        isDragging = true
                                                                    }
                                                                    if (isDragging) {
                                                                        change.consume()
                                                                        val currentX = change.position.x
                                                                        val pxPerSecondDrag = getPxPerSecond()
                                                                        val rawTime = (currentX / pxPerSecondDrag).toDouble().coerceIn(0.0, module.maxTime)
                                                                        val isOptionHeld = dragEvent.keyboardModifiers.isAltPressed
                                                                        val finalTime = if (isBeats && !isOptionHeld) {
                                                                            val sub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerSecondDrag.toDouble(), density.density)
                                                                            TimelineBeatUtils.snapToSubdivision(rawTime, sub)
                                                                        } else {
                                                                            rawTime
                                                                        }
                                                                        val clampedTime = finalTime.coerceIn(0.0, module.maxTime)
                                                                        if (activeTrigger.time != clampedTime) {
                                                                            activeTrigger.time = clampedTime
                                                                            FileState.markDirty()
                                                                        }
                                                                    }
                                                                }
                                                            } while (dragEvent.changes.any { it.pressed })
                                                        }
                                                    } else {
                                                        val mark = lastTapMark
                                                        val lastPos = lastTapPos
                                                        val now = kotlin.time.TimeSource.Monotonic.markNow()
                                                        val maxTapDistance = if (isBeats) minOf(8f * density.density, subPx * 0.8f) else (8f * density.density)
                                                        val isDoubleTap = mark != null &&
                                                            mark.elapsedNow().inWholeMilliseconds < 450 &&
                                                            lastPos != null &&
                                                            (down.position - lastPos).getDistance() < maxTapDistance

                                                        if (isDoubleTap) {
                                                            lastTapMark = null
                                                            lastTapPos = null
                                                            down.consume()

                                                            val rawTime = (clickX / pxPerSecond).toDouble().coerceIn(0.0, module.maxTime)
                                                            val isOptionHeld = event.keyboardModifiers.isAltPressed
                                                            val finalTime = if (isBeats && !isOptionHeld) {
                                                                TimelineBeatUtils.snapToSubdivision(rawTime, smallestSub)
                                                            } else {
                                                                rawTime
                                                            }.coerceIn(0.0, module.maxTime)

                                                            val newTrigger = if (module.triggers.none { kotlin.math.abs(it.time - finalTime) < 0.001 }) {
                                                                val trig = module.addTrigger(finalTime)
                                                                FileState.markDirty()
                                                                trig
                                                            } else {
                                                                null
                                                            }

                                                            if (newTrigger != null) {
                                                                var isDraggingNew = false
                                                                do {
                                                                    val dragEvent = awaitPointerEvent(PointerEventPass.Initial)
                                                                    val change = dragEvent.changes.firstOrNull()
                                                                    if (change != null && change.pressed) {
                                                                        val dist = kotlin.math.abs(change.position.x - down.position.x)
                                                                        if (dist > 3f * density.density) {
                                                                            isDraggingNew = true
                                                                        }
                                                                        if (isDraggingNew) {
                                                                            change.consume()
                                                                            val currentX = change.position.x
                                                                            val pxPerSecondDrag = getPxPerSecond()
                                                                            val rTime = (currentX / pxPerSecondDrag).toDouble().coerceIn(0.0, module.maxTime)
                                                                            val optHeld = dragEvent.keyboardModifiers.isAltPressed
                                                                            val fTime = if (isBeats && !optHeld) {
                                                                                val sub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerSecondDrag.toDouble(), density.density)
                                                                                TimelineBeatUtils.snapToSubdivision(rTime, sub)
                                                                            } else {
                                                                                rTime
                                                                            }
                                                                            val clampedTime = fTime.coerceIn(0.0, module.maxTime)
                                                                            if (newTrigger.time != clampedTime) {
                                                                                newTrigger.time = clampedTime
                                                                                FileState.markDirty()
                                                                            }
                                                                        }
                                                                    }
                                                                } while (dragEvent.changes.any { it.pressed })
                                                            }
                                                        } else {
                                                            lastTapMark = now
                                                            lastTapPos = down.position
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    if (isBeats) {
                                        val pxPerUnit = density.density * 24f * coercedZoom
                                        val smallestSub = TimelineBeatUtils.calculateSmallestVisibleSubdivision(pxPerUnit.toDouble(), density.density)
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val totalTime = module.maxTime + extraTime
                                            val numSubTicks = (module.maxTime / smallestSub).roundToInt()
                                            for (i in 0..numSubTicks) {
                                                val beat = if (smallestSub < 1.0) {
                                                    val d = (1.0 / smallestSub).roundToLong()
                                                    i.toDouble() / d.toDouble()
                                                } else {
                                                    i.toDouble() * smallestSub
                                                }
                                                if (beat > module.maxTime + 0.0001) break

                                                val pct = (beat / totalTime).toFloat()
                                                val x = size.width * pct

                                                val isWholeBeat = (beat % 1.0 < 0.0001 || (1.0 - (beat % 1.0)) < 0.0001)
                                                val isHalfBeat = (beat % 0.5 < 0.0001 || (0.5 - (beat % 0.5)) < 0.0001)
                                                val isQuarterBeat = (beat % 0.25 < 0.0001 || (0.25 - (beat % 0.25)) < 0.0001)

                                                val (gridColor, strokeW) = when {
                                                    isWholeBeat -> Color(0x33000000) to 1f
                                                    isHalfBeat -> Color(0x22000000) to 0.8f
                                                    isQuarterBeat -> Color(0x15000000) to 0.6f
                                                    else -> Color(0x0C000000) to 0.5f
                                                }

                                                drawLine(
                                                    color = gridColor,
                                                    start = Offset(x, 0f),
                                                    end = Offset(x, size.height),
                                                    strokeWidth = strokeW
                                                )
                                            }
                                        }
                                    }

                                    val cursorPct = (currentPlayheadTime / (module.maxTime + extraTime)).toFloat().coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(2.dp)
                                            .offset(x = timelineWidth * cursorPct)
                                            .background(Color.Red)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(timelineWidth - maxTimeX)
                                            .offset(x = maxTimeX)
                                            .background(Color(0x1F000000))
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .offset(x = maxTimeX)
                                            .background(Color(0xFFD32F2F).copy(alpha = 0.5f))
                                    )

                                    for (trigger in module.triggers) {
                                        val trigPct = (trigger.time / (module.maxTime + extraTime)).toFloat()
                                        val triggerX = timelineWidth * trigPct

                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(24.dp)
                                                .offset(x = triggerX - 12.dp),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.fillMaxHeight().padding(top = 2.dp, bottom = 0.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .graphicsLayer(rotationZ = 45f)
                                                        .background(Color.Yellow)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .weight(1f)
                                                        .background(Color(0xFF4B5563))
                                                )
                                                TimelineTriggerJackView(trigger.outputJack, state, viewportLeftGraphX, viewportRightGraphX)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (hasOutputs) {
                        Spacer(modifier = Modifier.width(spacing))
                        Column(
                            modifier = Modifier.width(rightWidth).padding(end = 2.dp)
                        ) {
                            Spacer(modifier = Modifier.height(topSectionHeight + 4.dp))
                            module.tracks.forEach { track ->
                                Column(
                                    modifier = Modifier.width(rightWidth).height(trackHeight),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    track.outputJacks.forEach { jack ->
                                        JackView(jack, state, isLeft = false)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Spacer(modifier = Modifier.height(triggerHeight))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .pointerInput(module) {
                            detectDragGestures(
                                onDragEnd = { FileState.markDirty() }
                            ) { change, dragAmount ->
                                change.consume()
                                val dw = dragAmount.x / density.density
                                module.width = (module.width + dw).coerceAtLeast(300.0)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val grip = colors.faderGrip
                        val w = size.width
                        val h = size.height
                        drawLine(grip, Offset(w - 4f, h - 12f), Offset(w - 4f, h - 2f), strokeWidth = 1.5f)
                        drawLine(grip, Offset(w - 8f, h - 12f), Offset(w - 8f, h - 2f), strokeWidth = 1.5f)
                    }
                }
            }
        }
    }
}





package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Approved Trackside activity interaction:
 * - real, independently interactive GPS map behind the draggable sheet;
 * - initial preview contains only title, date, and three unboxed metrics;
 * - expanded state reveals a sticky lime section pill and one continuous list;
 * - scrolling, not page replacement, advances Overview -> Splits -> Charts.
 *
 * The persisted activity and Garmin/Intervals source APIs are unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TracksideFigmaActivityDetailSurface(
    activity: ActivityRecord,
    recorded: ActivityDetail?,
    detailSource: ActivityRecord,
    matchedSourceIds: Set<String>,
    history: List<ActivityRecord>,
    state: TrainingState,
    actions: NativeActions,
    online: Boolean,
    selectedTab: Int,
    onTab: (Int) -> Unit,
    matte: Boolean,
    onToggleMatte: () -> Unit,
    onBack: () -> Unit,
    onLoadGarminGps: (() -> Unit)? = null
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .testTag("screen")
            .semantics { paneTitle = activity.title }
    ) {
        // Collapsed peek tracks the ACTUAL summary height, not a fixed
        // viewport fraction. Otherwise the sheet has a large empty tail.
        // This also grows correctly for a two-line title or large fonts.
        val density = LocalDensity.current
        var previewHeightPx by remember(activity.id) { mutableIntStateOf(0) }
        val peekHeight = if (previewHeightPx > 0) {
            with(density) { previewHeightPx.toDp() } + 37.dp
        } else 220.dp // Only used before the preview's first measurement.
        val effectivePeekHeight = peekHeight.coerceAtMost(
            (maxHeight - 80.dp).coerceAtLeast(120.dp)
        )
        val sheetHeight = (maxHeight - 24.dp).coerceAtLeast(400.dp).coerceAtMost(maxHeight)
        val sheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = false // Enable a real third, map-only anchor.
        )
        val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
        val scope = rememberCoroutineScope()
        val expanded = sheetState.currentValue == SheetValue.Expanded
        val mapOnly = sheetState.currentValue == SheetValue.Hidden &&
            sheetState.targetValue == SheetValue.Hidden
        // FIT sample sets can be large. Cache this once for both attribution
        // positions instead of rescanning on every drag or state change.
        val hasRecordedGps = remember(recorded) {
            recorded?.samples.orEmpty().count {
                it.latitude != null && it.longitude != null
            } >= 2
        }

        // Speed up programmatic sheet settling without relying on internal
        // Material 3 motion APIs. Preserve Android's animator accessibility
        // setting by scaling (rather than replacing) the inherited value.
        suspend fun settleQuickly(target: SheetValue) {
            val inherited = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
            val accelerated = object : MotionDurationScale {
                override val scaleFactor: Float = inherited * 0.72f
            }
            withContext(accelerated) {
                when (target) {
                    SheetValue.Expanded -> sheetState.expand()
                    SheetValue.PartiallyExpanded -> sheetState.partialExpand()
                    SheetValue.Hidden -> sheetState.hide()
                }
            }
        }

        // Track the real draggable offset in draw, not in composition. The
        // summary-to-details dissolve follows the finger and reverses without
        // waiting for the sheet to finish its anchored animation.
        val collapsedOffsetPx = with(density) { (maxHeight - effectivePeekHeight).toPx() }
        val expandedOffsetPx = with(density) { (maxHeight - sheetHeight).toPx() }
        fun sheetFraction(): Float {
            val offset = try {
                sheetState.requireOffset()
            } catch (_: IllegalStateException) {
                collapsedOffsetPx
            }
            return ((collapsedOffsetPx - offset) /
                (collapsedOffsetPx - expandedOffsetPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
        fun detailsAlpha(): Float {
            val t = ((sheetFraction() - 0.35f) / 0.45f).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        val prepareDetails by remember(sheetState, collapsedOffsetPx, expandedOffsetPx) {
            derivedStateOf {
                sheetFraction() > 0.27f ||
                    sheetState.currentValue == SheetValue.Expanded ||
                    sheetState.targetValue == SheetValue.Expanded
            }
        }
        val collapseOrBack: () -> Unit = {
            if (expanded || sheetState.targetValue == SheetValue.Expanded) {
                scope.launch { settleQuickly(SheetValue.PartiallyExpanded) }
            } else onBack()
        }
        BackHandler(enabled = expanded) {
            scope.launch { settleQuickly(SheetValue.PartiallyExpanded) }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = effectivePeekHeight,
            sheetShape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp),
            sheetContainerColor = if (matte) Color(0xFF0A110F) else Color(0xFF080D0C),
            sheetDragHandle = null,
            sheetContent = {
                Column(
                    Modifier.fillMaxWidth().height(sheetHeight)
                        .testTag("activity-sheet-scroll")
                        .semantics {
                            stateDescription = when {
                                expanded -> "Expanded activity details"
                                mapOnly -> "Map only"
                                else -> "Activity summary"
                            }
                            if (expanded) {
                                collapse {
                                    scope.launch { settleQuickly(SheetValue.PartiallyExpanded) }
                                    true
                                }
                            } else {
                                expand {
                                    scope.launch {
                                        settleQuickly(
                                            if (mapOnly) SheetValue.PartiallyExpanded
                                            else SheetValue.Expanded
                                        )
                                    }
                                    true
                                }
                            }
                            if (!expanded && !mapOnly) {
                                dismiss {
                                    scope.launch { settleQuickly(SheetValue.Hidden) }
                                    true
                                }
                            }
                        }
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(37.dp)
                            .clickable {
                                scope.launch {
                                    // A second tap reverses an in-flight
                                    // transition rather than starting the same
                                    // animation again while currentValue lags.
                                    if (expanded || sheetState.targetValue == SheetValue.Expanded)
                                        settleQuickly(SheetValue.PartiallyExpanded)
                                    else settleQuickly(SheetValue.Expanded)
                                }
                            }.testTag("activity-sheet-handle")
                            .semantics {
                                contentDescription = if (expanded)
                                    "Collapse activity details" else "Expand activity details"
                            }
                    ) {
                        Box(
                            Modifier.align(Alignment.TopCenter).padding(top = 11.dp)
                                .size(width = 44.dp, height = 5.dp)
                                .background(Color(0xFF899790), RoundedCornerShape(4.dp))
                        )
                        if (online && hasRecordedGps) {
                            // Attribution is anchored to the moving sheet, rather
                            // than floating over independently pannable map tiles.
                            Text(
                                TracksideMapTiles.ATTRIBUTION,
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                    .testTag("activity-map-attribution"),
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                color = Color(0xFF9CABAB)
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        // Always keep the preview measured at its approved
                        // compact height. Swapping this whole subtree at the
                        // Expanded anchor caused the visibly abrupt cut.
                        Box(
                            Modifier.fillMaxWidth().wrapContentHeight()
                                .graphicsLayer { alpha = 1f - detailsAlpha() }
                                .onSizeChanged { size ->
                                    if (previewHeightPx != size.height)
                                        previewHeightPx = size.height
                                }
                        ) {
                            TracksideActivityInitialPreview(activity)
                        }
                        if (prepareDetails) {
                            // Compose the section list shortly before it fades
                            // in. No page replacement or jump when settling.
                            Column(
                                Modifier.fillMaxSize()
                                    .graphicsLayer { alpha = detailsAlpha() }
                            ) {
                                TracksideActivityExpandedHeader(activity, collapseOrBack)
                                TracksideActivityContinuousSections(
                                    activity = activity,
                                    recorded = recorded,
                                    detailSource = detailSource,
                                    matchedSourceIds = matchedSourceIds,
                                    history = history,
                                    state = state,
                                    actions = actions,
                                    online = online,
                                    selectedTab = selectedTab,
                                    onTab = onTab,
                                    onLoadGarminGps = onLoadGarminGps
                                )
                            }
                        }
                    }
                }
            }
        ) {
            TracksideParityActivityMapHero(
                detail = recorded,
                gpsSourceLabel = detailSource.sourceLabel,
                onBack = collapseOrBack,
                heightDp = maxHeight,
                onChangeStyle = onToggleMatte,
                online = online
            )
        }

        // Hidden positions the full details sheet below the viewport. Keep
        // just one small reachable grip above the system navigation area;
        // it does not cover or intercept gestures on the GPS map itself.
        if (mapOnly) {
            // When the sheet is fully hidden, map attribution must remain
            // visible. Otherwise it would disappear offscreen with the sheet.
            if (online && hasRecordedGps) {
                Text(
                    TracksideMapTiles.ATTRIBUTION,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 43.dp)
                        .background(
                            Color(0xDD08100D), RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                        .testTag("activity-map-only-attribution"),
                    color = Color(0xFFC3D2C7),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    maxLines = 1
                )
            }
            val openSummary: () -> Unit = {
                scope.launch { settleQuickly(SheetValue.PartiallyExpanded) }
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .width(96.dp).height(36.dp)
                    .background(
                        Color(0xE608100D),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    )
                    .testTag("activity-map-only-handle")
                    .clickable(onClick = openSummary)
                    .pointerInput(sheetState) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onVerticalDrag = { change, delta ->
                                dragged += delta
                                change.consume()
                            },
                            onDragEnd = {
                                if (dragged <= -22.dp.toPx()) openSummary()
                                dragged = 0f
                            },
                            onDragCancel = { dragged = 0f }
                        )
                    }
                    .semantics {
                        contentDescription = "Show activity summary"
                        stateDescription = "Map only"
                        expand { openSummary(); true }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    Modifier.padding(top = 12.dp)
                        .size(width = 42.dp, height = 5.dp)
                        .background(Color(0xFFA4B3A9), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

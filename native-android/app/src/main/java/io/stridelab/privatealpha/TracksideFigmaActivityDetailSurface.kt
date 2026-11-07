package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
            skipHiddenState = true
        )
        val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
        val scope = rememberCoroutineScope()
        val expanded = sheetState.currentValue == SheetValue.Expanded

        // Speed up programmatic sheet settling without relying on internal
        // Material 3 motion APIs. Preserve Android's animator accessibility
        // setting by scaling (rather than replacing) the inherited value.
        suspend fun settleQuickly(target: SheetValue) {
            val inherited = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
            val accelerated = object : MotionDurationScale {
                override val scaleFactor: Float = inherited * 0.72f
            }
            withContext(accelerated) {
                if (target == SheetValue.Expanded) sheetState.expand()
                else sheetState.partialExpand()
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
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                            if (expanded) collapse {
                                scope.launch { settleQuickly(SheetValue.PartiallyExpanded) }
                                true
                            } else expand {
                                scope.launch { settleQuickly(SheetValue.Expanded) }
                                true
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
                        val route = recorded?.samples.orEmpty()
                        if (online && route.count { it.latitude != null && it.longitude != null } >= 2) {
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
    }
}

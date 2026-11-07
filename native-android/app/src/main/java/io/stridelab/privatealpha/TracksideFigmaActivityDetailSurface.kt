package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
        val uriHandler = LocalUriHandler.current
        var showMapCredits by remember(activity.id) { androidx.compose.runtime.mutableStateOf(false) }
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
            } >= 1
        }

        // Use Material's native anchored motion instead of locally accelerating
        // it. This keeps velocity/easing continuous for taps, flicks and drags,
        // and automatically respects the system animator-duration setting.
        suspend fun settleSheet(target: SheetValue) {
            when (target) {
                SheetValue.Expanded -> sheetState.expand()
                SheetValue.PartiallyExpanded -> sheetState.partialExpand()
                SheetValue.Hidden -> sheetState.hide()
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
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val mapLipHeightPx = with(density) { 24.dp.toPx() }
        val mapCreditMarginPx = with(density) { 30.dp.toPx() }
        val mapCreditThresholdPx = with(density) { 110.dp.toPx() }
        val mapCreditVisible by remember(sheetState, online, hasRecordedGps,
            collapsedOffsetPx, mapCreditThresholdPx) {
            derivedStateOf {
                online && hasRecordedGps &&
                    (runCatching { sheetState.requireOffset() }.getOrNull()
                        ?: collapsedOffsetPx) > mapCreditThresholdPx
            }
        }
        val showMapLip by remember(sheetState, maxHeightPx, mapLipHeightPx) {
            derivedStateOf {
                (runCatching { sheetState.requireOffset() }.getOrNull()
                    ?: 0f) >= maxHeightPx - mapLipHeightPx
            }
        }

        fun detailsAlpha(): Float {
            val t = ((sheetFraction() - 0.35f) / 0.45f).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        val collapseOrBack: () -> Unit = {
            if (expanded || sheetState.targetValue == SheetValue.Expanded) {
                scope.launch { settleSheet(SheetValue.PartiallyExpanded) }
            } else onBack()
        }
        BackHandler(enabled = expanded) {
            scope.launch { settleSheet(SheetValue.PartiallyExpanded) }
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
                                    scope.launch { settleSheet(SheetValue.PartiallyExpanded) }
                                    true
                                }
                            } else {
                                expand {
                                    scope.launch {
                                        settleSheet(
                                            if (mapOnly) SheetValue.PartiallyExpanded
                                            else SheetValue.Expanded
                                        )
                                    }
                                    true
                                }
                            }
                            if (!expanded && !mapOnly) {
                                dismiss {
                                    scope.launch { settleSheet(SheetValue.Hidden) }
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
                                        settleSheet(SheetValue.PartiallyExpanded)
                                    else settleSheet(SheetValue.Expanded)
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
                        // Keep details precomposed while the compact summary is
                        // visible. Previously this subtree was inserted midway
                        // through a drag, which could cause a perceptible hitch.
                        // While fully transparent it is translated below the
                        // sheet so it cannot steal compact-summary gestures.
                        Column(
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    val details = detailsAlpha()
                                    alpha = details
                                    translationY = if (details <= .001f) {
                                        with(density) { sheetHeight.toPx() }
                                    } else 0f
                                }
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

        // Single compact, clickable credit. Its Y position follows the sheet
        // anchor in the DRAW layer (no per-frame Compose remeasurement).
        // When fully hidden, it settles just above the narrow full-width grip.
        // The MapLibre-native watermark/attribution is disabled only because
        // this visible source-owned attribution remains accessible.
        if (mapCreditVisible) {
            Text(
                TracksideMapTiles.ATTRIBUTION,
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(start = 9.dp)
                    .graphicsLayer {
                        val offset = runCatching { sheetState.requireOffset() }
                            .getOrDefault(collapsedOffsetPx)
                        val edge = minOf(offset, maxHeightPx - mapLipHeightPx)
                        translationY = (edge - mapCreditMarginPx).coerceAtLeast(0f)
                    }
                    .background(Color(0xC908100D), RoundedCornerShape(7.dp))
                    .clickable { showMapCredits = true }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("activity-map-attribution")
                    .semantics { contentDescription = "Map data credits. View attribution links." },
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                color = Color(0xFFDAE8DC)
            )
        }

        // A thin, full-width sheet lip makes the hidden anchor look like the
        // SAME sheet at the bottom, rather than a floating black bubble.
        // The map remains freely pannable everywhere outside this 24dp strip.
        if (showMapLip) {
            val openSummary: () -> Unit = {
                scope.launch { settleSheet(SheetValue.PartiallyExpanded) }
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().height(24.dp)
                    .background(
                        if (matte) Color(0xFF0A110F) else Color(0xFF080D0C),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    )
                    .testTag("activity-map-only-handle")
                    .clickable(onClick = openSummary)
                    .pointerInput(sheetState) {
                        var dragged = 0f
                        var opened = false
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragged = 0f
                                opened = false
                            },
                            onVerticalDrag = { change, delta ->
                                dragged += delta
                                // Start lifting during the swipe, not after the
                                // finger is released. A single swipe restores
                                // the approved summary (not full details).
                                if (!opened && dragged < -10.dp.toPx()) {
                                    opened = true
                                    openSummary()
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                if (!opened && dragged < -8.dp.toPx()) openSummary()
                                dragged = 0f
                                opened = false
                            },
                            onDragCancel = {
                                dragged = 0f
                                opened = false
                            }
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
                    Modifier.padding(top = 7.dp)
                        .size(width = 46.dp, height = 4.dp)
                        .background(Color(0xFF9BAB9F), RoundedCornerShape(4.dp))
                )
            }
        }

        // Keep detailed license links available without permanently displaying
        // extra badges, logos or an attribution panel over the map.
        if (showMapCredits) {
            AlertDialog(
                onDismissRequest = { showMapCredits = false },
                title = { Text("Map credits") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(TracksideMapTiles.ATTRIBUTION)
                        TextButton(onClick = {
                            uriHandler.openUri("https://www.openstreetmap.org/copyright")
                        }) { Text("OpenStreetMap copyright") }
                        TextButton(onClick = {
                            uriHandler.openUri("https://openmaptiles.org/")
                        }) { Text("OpenMapTiles") }
                        TextButton(onClick = {
                            uriHandler.openUri("https://openfreemap.org/")
                        }) { Text("OpenFreeMap") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMapCredits = false }) {
                        Text("Close")
                    }
                },
                containerColor = Color(0xFF101A15),
                titleContentColor = Color(0xFFF0F7F1),
                textContentColor = Color(0xFFBFCFC4)
            )
        }
    }
}

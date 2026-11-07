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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        val sheetTop = if (maxHeight < 720.dp) 355.dp else 425.dp
        val sheetHeight = (maxHeight - 24.dp).coerceAtLeast(400.dp).coerceAtMost(maxHeight)
        val sheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
        val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
        val scope = rememberCoroutineScope()
        // Do not replace the preview mid-drag. Expanded content is installed
        // only when the sheet reaches the expanded anchor.
        val expanded = sheetState.currentValue == SheetValue.Expanded
        val collapseOrBack: () -> Unit = {
            if (expanded || sheetState.targetValue == SheetValue.Expanded) {
                scope.launch { sheetState.partialExpand() }
            } else onBack()
        }
        BackHandler(enabled = expanded) {
            scope.launch { sheetState.partialExpand() }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = maxHeight - sheetTop,
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
                                scope.launch { sheetState.partialExpand() }
                                true
                            } else expand {
                                scope.launch { sheetState.expand() }
                                true
                            }
                        }
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(37.dp)
                            .clickable {
                                scope.launch {
                                    if (expanded) sheetState.partialExpand()
                                    else sheetState.expand()
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
                    if (!expanded) {
                        // No pill and no body below the approved initial preview.
                        TracksideActivityInitialPreview(activity)
                    } else {
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

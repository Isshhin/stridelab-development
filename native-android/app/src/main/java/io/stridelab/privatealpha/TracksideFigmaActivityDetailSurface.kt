package io.stridelab.privatealpha

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Figma approved 551:235 / 551:365 require the map to remain behind an
 * independently scrolling, overlapping activity sheet. The old implementation
 * incorrectly placed the map in a LazyColumn, so scrolling erased it entirely.
 *
 * MapLibre shows geographically correct OSM-derived streets when actual
 * recorded GPS and network access exist; offline retains source coordinates.
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
    onLoadGarminGps:(()->Unit)?=null
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF000000))
            .testTag("screen")
            .semantics { paneTitle = activity.title }
    ) {
        val sheetTop = if (maxHeight < 720.dp) 355.dp else 425.dp
        val sheetHeight = (maxHeight - 96.dp).coerceAtLeast(520.dp)
        val sheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
        val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
        val scope = rememberCoroutineScope()
        val expanded = sheetState.currentValue == SheetValue.Expanded ||
            sheetState.targetValue == SheetValue.Expanded
        val collapseOrBack: () -> Unit = {
            if (expanded) scope.launch { sheetState.partialExpand() }
            else onBack()
        }

        BackHandler(enabled = expanded) {
            scope.launch { sheetState.partialExpand() }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = maxHeight - sheetTop,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContainerColor = if (matte) Color(0xFF091B26)
                else Color(0xFF122A38),
            sheetDragHandle = null,
            sheetContent = {
                ActivitySheetContent(
                    modifier = Modifier.fillMaxWidth().height(sheetHeight)
                        .semantics {
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                            if (expanded) collapse {
                                scope.launch { sheetState.partialExpand() }
                                true
                            } else expand {
                                scope.launch { sheetState.expand() }
                                true
                            }
                        },
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
                    matte = matte,
                    onLoadGarminGps=onLoadGarminGps
                )
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

@Composable
private fun ActivitySheetContent(
    modifier: Modifier,
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
    onLoadGarminGps:(()->Unit)?
) {
    Column(
        modifier.verticalScroll(rememberScrollState())
            .testTag("activity-sheet-scroll")
    ) {
        if (matte) TracksideParityMatteActivityOverview(activity)
        else TracksideParityActivityOverview(activity)

        if (matte) TracksideParityMatteActivityTabs(selectedTab, onTab)
        else TracksideParityActivityTabs(selectedTab, onTab)

        when (selectedTab) {
            0 -> {
                Spacer(Modifier.height(20.dp))
                if (matte) TracksideParityMatteActivityHighlights(
                    activity, history, recorded
                ) else Column(Modifier.padding(horizontal = 17.dp)) {
                    TracksideParityActivityHighlights(activity, history)
                }
                Spacer(Modifier.height(13.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp)) {
                    Text("ADDITIONAL RUN DETAILS", fontSize = 14.sp,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    MetricPair(
                        "Elapsed time" to activity.durationLabel,
                        "Average heart rate" to (activity.averageHr?.let {
                            it.roundToInt().toString() + " bpm"
                        } ?: "Not recorded")
                    )
                    activity.elevationM?.let {
                        Spacer(Modifier.height(8.dp))
                        Metric("Elevation gain", it.roundToInt().toString() + " m")
                    }
                    if (DashboardSummary.isRun(activity)) {
                        Spacer(Modifier.height(12.dp))
                        ActivityPlanLink(state, activity, actions.planning, matchedSourceIds)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("RECORDING DETAILS", fontSize = 14.sp,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    Text(activity.sourceLabel + " · " + activity.dateLabel,
                        color = TracksidePalette.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(13.dp))
                    if(detailSource.id!=activity.id)
                        Text("Supplemental recorded streams: " + detailSource.sourceLabel,
                            color = TracksidePalette.textMuted, fontSize = 11.sp)
                    if(activity.garminMovingPaceSPerKm!=null)
                        Text("Pace calculated from Garmin moving time and distance.",
                            color = TracksidePalette.textMuted, fontSize = 11.sp)
                    Text("Source-derived values only. Missing GPS, splits, heart rate and training effect are not estimated.",
                        color = TracksidePalette.textMuted, fontSize = 11.sp)
                }
            }
            1 -> {
                Spacer(Modifier.height(17.dp))
                Column(Modifier.padding(horizontal = 17.dp)) {
                    TracksideParityActivityLaps(recorded)
                }
            }
            else -> {
                Spacer(Modifier.height(17.dp))
                Column(Modifier.padding(horizontal = 17.dp)) {
                    if (recorded != null) RecordedActivityDetail(
                        recorded, activity.supportsPace
                    ) else Text(
                        "Recorded charts aren't cached for this activity.",
                        color = TracksidePalette.textMuted
                    )
                }
            }
        }

        if(activity.source=="garmin" && detailSource.id!=activity.id) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Garmin is the primary activity source. GPS, splits and charts come from matching ${detailSource.sourceLabel} records when available; Garmin summary metrics are not replaced.",
                modifier=Modifier.fillMaxWidth().padding(horizontal=17.dp),
                color=TracksidePalette.textMuted,fontSize=11.sp
            )
        }
        if(activity.source=="garmin" && recorded==null && detailSource.id==activity.id) {
            Spacer(Modifier.height(18.dp))
            Text(
                "No Garmin GPS track is cached for this activity. StrideLab can request the original FIT recording when available; no route is invented.",
                modifier=Modifier.fillMaxWidth().padding(horizontal=17.dp),
                color=TracksidePalette.textMuted,fontSize=11.sp
            )
        }
        if(activity.source=="garmin" && detailSource.id==activity.id && recorded==null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick={ onLoadGarminGps?.invoke() },
                enabled=online && !state.busy && !state.needsSignIn && onLoadGarminGps!=null,
                modifier=Modifier.fillMaxWidth().padding(horizontal=17.dp)
                    .testTag("garmin-load-gps")
            ) { Text(if(state.busy && state.task?.contains("Garmin")==true)
                "Loading Garmin GPS…" else "Load Garmin GPS") }
            if(state.message?.contains("Garmin",ignoreCase=true)==true) {
                Text(state.message.orEmpty(),
                    modifier=Modifier.fillMaxWidth().padding(horizontal=17.dp),
                    color=TracksidePalette.textMuted,fontSize=11.sp)
            }
        }
        if (detailSource.source!="garmin" && online && !state.busy && !state.needsSignIn) {
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = { actions.loadDetail(detailSource.id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp)
                    .testTag("activity-refresh-streams")
            ) {
                Text(if(recorded != null)
                    "Refresh source data" else "Load recorded streams")
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

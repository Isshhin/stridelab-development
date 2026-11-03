package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Figma approved 551:235 / 551:365 require the map to remain behind an
 * independently scrolling, overlapping activity sheet. The old implementation
 * incorrectly placed the map in a LazyColumn, so scrolling erased it entirely.
 *
 * Map texture is decorative, not a geographic tile. Route is a schematic
 * visualization of actual source coordinates, or absent when not recorded.
 */
@Composable
internal fun TracksideFigmaActivityDetailSurface(
    activity: ActivityRecord,
    recorded: ActivityDetail?,
    history: List<ActivityRecord>,
    state: TrainingState,
    actions: NativeActions,
    online: Boolean,
    selectedTab: Int,
    onTab: (Int) -> Unit,
    matte: Boolean,
    onToggleMatte: () -> Unit,
    onBack: () -> Unit
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF000000))
            .testTag("screen")
            .semantics { paneTitle = activity.title }
    ) {
        val sheetTop = if (maxHeight < 720.dp) 355.dp else 425.dp
        TracksideParityActivityMapHero(
            detail = recorded,
            onBack = onBack,
            heightDp = sheetTop,
            onChangeStyle = onToggleMatte
        )

        Column(
            Modifier.fillMaxWidth().fillMaxHeight().padding(top = sheetTop)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(if (matte) Color(0xFF091B26) else Color(0xFF122A38))
                .verticalScroll(rememberScrollState())
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
                            ActivityPlanLink(state, activity, actions.planning)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("RECORDING DETAILS", fontSize = 14.sp,
                            color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(9.dp))
                        Text(activity.sourceLabel + " · " + activity.dateLabel,
                            color = TracksidePalette.textMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(13.dp))
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

            if (online && !state.busy && !state.needsSignIn) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = { actions.loadDetail(activity.id) },
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
}

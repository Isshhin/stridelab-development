package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Master-gallery Figma 551:235: atmospheric Activity Overview.
 * Original street-grid texture is decorative only; no sample GPS is displayed.
 */
@Composable
internal fun TracksideFigmaActivityScreen(
    activity: ActivityRecord?, state: TrainingState, online: Boolean,
    actions: NativeActions, onBack: () -> Unit
) {
    var tab by rememberSaveable(activity?.id) { mutableStateOf("Overview") }
    Box(Modifier.fillMaxSize().background(Color(0xFF09131C))) {
        Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.trackside_activity_street_grid),
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xB509131C), Color(0xED09131C), Color(0xFF09131C)
        ))))
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen"),
        contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.testTag("activity-back")) {
                Text("Back to history", color = TracksidePalette.text)
            }
        }
        if (activity == null) {
            item { Text("Activity unavailable", color = TracksidePalette.text,
                style = MaterialTheme.typography.headlineMedium) }
            item { Text("No record is cached for the current account.",
                color = TracksidePalette.textMuted) }
        } else {
            item { TracksideFigmaActivityHero(activity) }
            item { TracksideActivityTabStrip(tab) { tab = it } }
            val data = state.details[activity.id]
            when (tab) {
                "Overview" -> {
                    if (data != null) item {
                        // This is the actual source GPS trace. It appears BELOW primary metrics.
                        RecordedActivityDetail(data, activity.supportsPace, mode = "overview")
                    }
                    item {
                        Text("Activity Highlights", style = MaterialTheme.typography.titleLarge,
                            color = TracksidePalette.text)
                    }
                    item { TracksideGlassCard {
                        Text(if (activity.supportsPace) "RUNNING INTELLIGENCE" else "SESSION CONTEXT",
                            color = TracksidePalette.red, style = MaterialTheme.typography.labelSmall)
                        Text(ActivityIntelligence.narrative(activity, state.activities),
                            color = TracksidePalette.text, style = MaterialTheme.typography.bodyLarge)
                        ActivityIntelligence.comparison(activity, state.activities)?.let { compare ->
                            Text("Compared with ${compare.count} comparable cached activities; not a prediction.",
                                color = TracksidePalette.textMuted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } }
                    item { TracksideGlassCard {
                        Text("TRAINING IMPACT", color = TracksidePalette.textMuted,
                            style = MaterialTheme.typography.labelSmall)
                        Text("Source-dependent", color = TracksidePalette.text,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Aerobic and anaerobic training-effect scores are not supplied in this record. No scores are inferred.",
                            color = TracksidePalette.textMuted,
                            style = MaterialTheme.typography.bodySmall)
                    } }
                    if (DashboardSummary.isRun(activity)) item {
                        ActivityPlanLink(state, activity, actions.planning)
                    }
                    item { TracksideGlassCard {
                        Text("ADDITIONAL RUN DETAILS", color = TracksidePalette.textMuted,
                            style = MaterialTheme.typography.labelSmall)
                        MetricPair("Elapsed time" to activity.durationLabel,
                            "Moving time" to (activity.movingTimeS?.let { ActivityRecord.duration(it) } ?: "Not recorded"))
                        MetricPair(
                            "Average heart rate" to (activity.averageHr?.let { "${it.roundToInt()} bpm" } ?: "Not recorded"),
                            "Maximum heart rate" to (activity.maximumHr?.let { "${it.roundToInt()} bpm" } ?: "Not recorded")
                        )
                        Metric("Elevation gain", activity.elevationM?.let { "${it.roundToInt()} m" } ?: "Not recorded")
                    } }
                    item { TracksideGlassCard {
                        Text("RECORDING DETAILS", color = TracksidePalette.textMuted,
                            style = MaterialTheme.typography.labelSmall)
                        Text(activity.sourceLabel + " · " + activity.dateLabel,
                            color = TracksidePalette.text, style = MaterialTheme.typography.bodyMedium)
                        Text("Metrics come from the original source; absent streams and coordinates remain absent.",
                            color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
                    } }
                }
                "Splits" -> {
                    if (data != null) item { RecordedActivityDetail(data, activity.supportsPace, mode = "splits") }
                    else item { TracksideGlassCard {
                        Text("No source laps cached", color = TracksidePalette.text)
                        Text("Source splits require a recorded activity detail response; no kilometer splits are invented.",
                            color = TracksidePalette.textMuted)
                    } }
                }
                "Charts" -> {
                    if (data != null) item { RecordedActivityDetail(data, activity.supportsPace, mode = "charts") }
                    else item { TracksideGlassCard {
                        Text("Recorded streams unavailable", color = TracksidePalette.text)
                        Text("No HR, pace or elevation series can be inferred from summary metrics.",
                            color = TracksidePalette.textMuted)
                    } }
                }
            }
            item {
                OutlinedButton(
                    onClick = { actions.loadDetail(activity.id) },
                    enabled = online && !state.busy && !state.needsSignIn,
                    modifier = Modifier.fillMaxWidth().testTag("activity-refresh-streams")
                ) { Text(if (data == null) "Load recorded streams & laps" else "Refresh recorded streams") }
            }
        }
    }
    }
}

@Composable
private fun TracksideFigmaActivityHero(activity: ActivityRecord) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
        .background(Color(0xFF0B1D2A))
        .testTag("trackside-activity-overview-hero")) {
        Box(Modifier.fillMaxWidth().height(177.dp)) {
            Image(painter = androidx.compose.ui.res.painterResource(R.drawable.trackside_activity_street_grid),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color(0x440B1D2A), Color(0xED09131C)
            ))))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 13.dp)) {
                Text("RECORDED ACTIVITY", fontSize = 11.sp, color = TracksidePalette.textMuted,
                    fontWeight = FontWeight.Bold)
                Text(activity.title, fontSize = 29.sp, lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold, color = TracksidePalette.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(activity.dateLabel + " · " + activity.type + " · " + activity.sourceLabel,
                    color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 118.dp)
            .background(Brush.horizontalGradient(listOf(
                Color(0xFF351C29), Color(0xFF1C2B36), Color(0xFF0B1D2A)
            ))).padding(horizontal = 9.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TracksideActivityHeroMetric("DISTANCE", activity.distanceLabel, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(67.dp)
                .background(TracksidePalette.outline.copy(alpha = .5f)))
            TracksideActivityHeroMetric("MOVING TIME",
                activity.movingTimeS?.let { ActivityRecord.duration(it) } ?: "Not recorded",
                Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(67.dp)
                .background(TracksidePalette.outline.copy(alpha = .5f)))
            TracksideActivityHeroMetric("AVG PACE", activity.paceLabel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TracksideActivityHeroMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 10.sp, color = TracksidePalette.textMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 20.sp, lineHeight = 23.sp,
            fontWeight = FontWeight.Bold, color = TracksidePalette.text,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The three Figma tabs are real stateful controls, not decorative labels. */
@Composable
private fun TracksideActivityTabStrip(selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp)
        .background(TracksidePalette.surface, RoundedCornerShape(14.dp))
        .testTag("trackside-activity-tabs")) {
        listOf("Overview", "Splits", "Charts").forEach { label ->
            val active = selected == label
            Column(Modifier.weight(1f).fillMaxHeight()
                .clickable { onSelected(label) }
                .testTag("activity-tab-${label.lowercase()}")
                .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = if (active) TracksidePalette.text else TracksidePalette.textMuted,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(62.dp).height(2.dp)
                    .background(if (active) TracksidePalette.red else Color.Transparent))
            }
        }
    }
}

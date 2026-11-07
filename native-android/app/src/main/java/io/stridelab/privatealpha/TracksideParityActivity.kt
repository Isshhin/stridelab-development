package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp

/**
 * Activity hero. The street map uses real source coordinates through MapLibre.
 * No recorded GPS means no map location or illustrative/fake route is shown.
 */
@Composable
internal fun TracksideParityActivityMapHero(
    detail: ActivityDetail?,
    onBack: () -> Unit,
    heightDp: Dp = 425.dp,
    onChangeStyle: (() -> Unit)? = null,
    online:Boolean = true,
    gpsSourceLabel:String? = null,
    selectedSeconds:Double? = null,
    selectedRange:Pair<Double,Double>? = null,
    onSelectRange:(Pair<Double,Double>) -> Unit = {}
) {
    val route=remember(detail) {
        RecordedGpsRoute.from(
            detail?.samples.orEmpty(),
            detail?.pauseIntervals.orEmpty()
        )
    }
    Box(Modifier.fillMaxWidth().height(heightDp).testTag("activity-map-hero")) {
        if(route.pointCount > 0) {
            TracksideGeographicMap(
                route, online, Modifier.fillMaxSize(),
                selectedSeconds = selectedSeconds,
                laps = detail?.laps.orEmpty(),
                selectedRange = selectedRange,
                onSelectRange = onSelectRange
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0xFF131C17),Color(0xFF080D0C)))))
        }
        // Keep actual vector streets crisp. The old full-screen tint was
        // darkening even the lighter road/label palette; only shade the empty
        // no-GPS placeholder, not a genuine MapLibre map.
        if (route.pointCount == 0) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0x28070B09), Color.Transparent, Color(0x14070B09))
            )))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack, shape = RoundedCornerShape(25.dp),
                color = Color(0xDD080E0C), border = BorderStroke(1.dp, Color(0x5546574F)),
                modifier = Modifier.size(44.dp).testTag("activity-map-back")
            ) { Box(contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 25.sp, color = TracksidePalette.text)
            } }
            Spacer(Modifier.weight(1f))
            if (onChangeStyle != null) {
                Surface(
                    onClick = onChangeStyle,
                    modifier = Modifier.size(44.dp)
                        .testTag("activity-change-glass-variant")
                        .semantics { contentDescription = "Switch approved Activity appearance" },
                    shape = RoundedCornerShape(25.dp),
                    color = Color(0xDD080E0C),
                    border = BorderStroke(1.dp, Color(0x5546574F))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("•••", fontSize = 15.sp, color = TracksidePalette.text)
                    }
                }
            } else Box(Modifier.size(44.dp))
        }
        if (route.pointCount > 0) {
            val hasSpecialGpsState = route.pauseGaps.isNotEmpty() ||
                route.pausedPoints.isNotEmpty() || route.unknownGaps.isNotEmpty()
            Surface(
                color = Color(0xDD080E0C),
                border = BorderStroke(
                    1.dp,
                    if (route.unknownGaps.isEmpty()) Color(0x445A6B62)
                    else Color(0x667A8781)
                ),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 68.dp)
                    .testTag("activity-map-legend")
            ) {
                Column(
                    Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        route.gpsContinuityLabel,
                        color = if (route.unknownGaps.isEmpty())
                            Color(0xFFD8FB70) else Color(0xFFC5D0CB),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("activity-map-gps-continuity")
                    )
                    if (hasSpecialGpsState) {
                        MapLegendRow(Color(0xFFD8FB70), "Recorded route")
                        if (route.pauseGaps.isNotEmpty()) {
                            MapLegendRow(Color(0xFFFFBE7A), "Timer pause")
                        }
                        if (route.pausedPoints.isNotEmpty()) {
                            MapLegendRow(Color(0xFFFFBE7A), "GPS recorded while paused", dot = true)
                        }
                        if (route.unknownGaps.isNotEmpty()) {
                            MapLegendRow(Color(0xFFA5B4AE), "GPS gap")
                        }
                    }
                }
            }
        }
        if (route.pointCount == 0) {
            Text(
                "No recorded GPS track",
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xCC080D0C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                color = Color(0xFFB4C1BA),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MapLegendRow(color: Color, label: String, dot: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.then(
                if (dot) Modifier.size(6.dp)
                else Modifier.size(width = 12.dp, height = 2.dp)
            ).background(
                color,
                RoundedCornerShape(if (dot) 4.dp else 2.dp)
            )
        )
        Text(label, color = Color(0xFFCED8D3), fontSize = 8.sp, lineHeight = 10.sp)
    }
}

/** Approved Figma 551:235: activity summary within the premium glass sheet. */
@Composable
internal fun TracksideParityActivityOverview(activity: ActivityRecord) {
    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF253644), Color(0xFF0B1924))),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 17.dp, vertical = 16.dp)
            .testTag("activity-parity-overview"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally)
            .size(width = 48.dp, height = 5.dp)
            .background(Color(0xFF778C9B), RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(12.dp))
        Text(activity.title, fontSize = 27.sp, lineHeight = 32.sp,
            color = TracksidePalette.text, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(activity.dateLabel + " · " + activity.type,
            fontSize = 12.sp, color = TracksidePalette.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TracksideActivityTag(activity.type)
            TracksideActivityTag(activity.sourceLabel)
        }
        Surface(
            shape = RoundedCornerShape(19.dp),
            color = TracksidePalette.surface,
            border = BorderStroke(0.9f.dp, Color(0x99677987)),
            modifier = Modifier.fillMaxWidth().height(130.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(
                    Color(0xFF35232D), Color(0xFF142534), Color(0xFF0A1620)
                ))).padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TracksideActivityMetric("DISTANCE", activity.distanceM?.let { activity.distanceLabel } ?: "—",
                    Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(76.dp).background(Color(0x667F929E)))
                TracksideActivityMetric("MOVING TIME",
                    activity.movingTimeS?.let { ActivityRecord.duration(it) } ?: "—",
                    Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(76.dp).background(Color(0x667F929E)))
                TracksideActivityMetric(activity.detailPaceHeading,
                    if (activity.supportsPace) activity.detailPaceLabel else "—",
                    Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TracksideActivityTag(text: String) {
    Surface(color = Color(0xFF1A2D39), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x665B778A))) {
        Text(text, Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            fontSize = 11.sp, color = TracksidePalette.text, maxLines = 1)
    }
}

@Composable
private fun TracksideActivityMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 10.sp, lineHeight = 12.sp,
            color = TracksidePalette.textMuted, maxLines = 2)
        Text(value, fontSize = 18.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Bold, color = TracksidePalette.text,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Approved overview / splits / charts glass tabs, exact three-way composition.
 * Semantics and actual content switch with the selected state.
 */
@Composable
internal fun TracksideParityActivityTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(57.dp)
            .background(Color(0xFF13222D), RoundedCornerShape(18.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Overview", "Splits", "Charts").forEachIndexed { index, title ->
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .testTag("activity-tab-" + title.lowercase())
                    .semantics {
                        role = Role.Tab
                        this.selected = selected == index
                    },
                color = if (selected == index) Color(0xFF314351) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(title, fontSize = 13.sp,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected == index) TracksidePalette.text else TracksidePalette.textMuted)
                }
            }
        }
    }
}

@Composable
internal fun TracksideParityActivityHighlights(activity: ActivityRecord, history: List<ActivityRecord>) {
    TracksideGlassCard(compact = true) {
        Text("ACTIVITY HIGHLIGHTS", fontSize = 12.sp,
            color = TracksidePalette.textMuted, fontWeight = FontWeight.Bold)
        Text(ActivityIntelligence.narrative(activity, history),
            style = MaterialTheme.typography.bodyLarge,
            color = TracksidePalette.text)
        val comparison = ActivityIntelligence.comparison(activity, history)
        Text(if (comparison != null)
                "Compared with " + comparison.count + " earlier cached runs · preceding 90 days · similar distance"
            else "No reliable comparison available from saved runs.",
            style = MaterialTheme.typography.bodySmall, color = TracksidePalette.textMuted)
    }
    TracksideGlassCard(compact = true) {
        Text("TRAINING IMPACT", fontSize = 12.sp,
            color = TracksidePalette.textMuted, fontWeight = FontWeight.Bold)
        Text("Training-effect measurements are shown only if the recording source provides them.",
            style = MaterialTheme.typography.bodyMedium, color = TracksidePalette.text)
        Text("No training effect inferred from distance, average pace or heart rate.",
            style = MaterialTheme.typography.bodySmall, color = TracksidePalette.textMuted)
    }
}

@Composable
internal fun TracksideParityActivityLaps(detail: ActivityDetail?) {
    TracksideGlassCard(compact = true) {
        Text("SOURCE SPLITS / LAPS", style = MaterialTheme.typography.titleMedium,
            color = TracksidePalette.text)
        if (detail == null) Text("Load recorded streams to review source laps.",
            color = TracksidePalette.textMuted)
        else if (detail.laps.isEmpty()) Text("No source laps were recorded.",
            color = TracksidePalette.textMuted)
        else detail.laps.take(100).forEach { lap ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Lap " + (lap.index + 1), color = TracksidePalette.text,
                    style = MaterialTheme.typography.bodyMedium)
                Text((lap.distance?.let { "%.2f km".format(it/1000.0) } ?: "—") + " · " +
                    (lap.seconds?.let { ActivityRecord.duration(it) } ?: "—"),
                    style = MaterialTheme.typography.bodySmall, color = TracksidePalette.textMuted)
            }
        }
    }
}

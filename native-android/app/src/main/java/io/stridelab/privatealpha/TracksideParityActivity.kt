package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma approved 551:235 atmospheric glass. Real metrics only.
 * The original decorative street grid never claims to be the recorded GPS route.
 * Actual recorded GPS follows below metrics in RecordedActivityDetail.
 */
@Composable
internal fun TracksideParityActivityOverview(activity: ActivityRecord) {
    Column(
        Modifier.fillMaxWidth().testTag("activity-parity-overview"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("RECORDED ACTIVITY", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = TracksidePalette.textMuted)
        Text(activity.title, fontSize = 27.sp, lineHeight = 32.sp,
            color = TracksidePalette.text, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TracksideActivityTag(activity.type)
            TracksideActivityTag(activity.sourceLabel)
        }
        Surface(
            shape = RoundedCornerShape(19.dp),
            color = TracksidePalette.surface,
            border = BorderStroke(0.9.dp, Color(0x99677987)),
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
                TracksideActivityMetric("AVG PACE",
                    if (activity.supportsPace) activity.paceLabel else "—",
                    Modifier.weight(1f))
            }
        }
        // Figma's source map is an illustration. It is not user GPS.
        Box(
            Modifier.fillMaxWidth().height(172.dp)
                .background(Color(0xFF0B1D2A), RoundedCornerShape(15.dp))
        ) {
            Image(
                painterResource(R.drawable.trackside_activity_street_grid),
                contentDescription = "Decorative street-grid texture; not the actual recorded route",
                modifier = Modifier.fillMaxWidth().height(172.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Color(0xCC081219)).padding(10.dp)
            ) {
                Text("Map backdrop · illustrative. Recorded GPS is shown below when available.",
                    fontSize = 11.sp, color = TracksidePalette.textMuted)
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
                    .testTag("activity-tab-" + title.lowercase()),
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

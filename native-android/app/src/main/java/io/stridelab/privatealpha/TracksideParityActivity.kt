package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.abs

/** Figma approved 551:235 atmospheric glass. Real metrics only.
 * The original decorative street grid never claims to be the recorded GPS route.
 * Actual recorded GPS follows below metrics in RecordedActivityDetail.
 */
/**
 * Approved atmospheric activity: full-width source map first, 425dp high.
 * The backdrop is a decorative Figma city grid. A red route is drawn ONLY
 * from actual recorded coordinates when the source contains GPS points.
 */
@Composable
internal fun TracksideParityActivityMapHero(
    detail: ActivityDetail?,
    onBack: () -> Unit,
    heightDp: Dp = 425.dp,
    onChangeStyle: (() -> Unit)? = null
) {
    val samples = detail?.samples ?: emptyList()
    val points = samples.filter { it.latitude != null && it.longitude != null }
    Box(Modifier.fillMaxWidth().height(heightDp).testTag("activity-map-hero")) {
        Image(painterResource(R.drawable.trackside_activity_street_grid),
            contentDescription = "Decorative street-map background, not recorded GPS",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (points.size >= 2) {
            val projection = cos(Math.toRadians(points.map { it.latitude!! }.average()))
                .coerceAtLeast(.01)
            val minX = points.minOf { it.longitude!! * projection }
            val maxX = points.maxOf { it.longitude!! * projection }
            val minY = points.minOf { it.latitude!! }
            val maxY = points.maxOf { it.latitude!! }
            Canvas(Modifier.fillMaxSize()) {
                val spanX = (maxX - minX).coerceAtLeast(.00001)
                val spanY = (maxY - minY).coerceAtLeast(.00001)
                val scale = minOf((size.width - 40f) / spanX, (size.height - 40f) / spanY)
                fun pointOf(p: ActivitySample) = Offset(
                    ((size.width - spanX * scale) / 2 +
                        (p.longitude!! * projection - minX) * scale).toFloat(),
                    ((size.height - spanY * scale) / 2 +
                        (maxY - p.latitude!!) * scale).toFloat()
                )
                for ((a, b) in samples.zipWithNext()) {
                    if (a.latitude != null && a.longitude != null &&
                        b.latitude != null && b.longitude != null &&
                        b.seconds - a.seconds <= 120 && abs(a.longitude - b.longitude) < 180) {
                        drawLine(TracksidePalette.red, pointOf(a), pointOf(b), 4.dp.toPx())
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0xAA07111A), Color.Transparent, Color(0xCC08131D))
        )))
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack, shape = RoundedCornerShape(25.dp),
                color = Color(0x8813222C), border = BorderStroke(1.dp, Color(0x99768C9B)),
                modifier = Modifier.size(44.dp).testTag("activity-map-back")
            ) { Box(contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 25.sp, color = TracksidePalette.text)
            } }
            Spacer(Modifier.weight(1f))
            Text("RECORDED ACTIVITY", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = TracksidePalette.text)
            Spacer(Modifier.weight(1f))
            if (onChangeStyle != null) {
                Surface(
                    onClick = onChangeStyle,
                    modifier = Modifier.size(44.dp)
                        .testTag("activity-change-glass-variant")
                        .semantics { contentDescription = "Switch approved Activity appearance" },
                    shape = RoundedCornerShape(25.dp),
                    color = Color(0x8813222C),
                    border = BorderStroke(1.dp, Color(0x99768C9B))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("•••", fontSize = 15.sp, color = TracksidePalette.text)
                    }
                }
            } else Box(Modifier.size(44.dp))
        }
        Text(
            if (points.size >= 2) "Recorded GPS · " + points.size + " samples"
            else "Illustrative street-grid backdrop · no recorded GPS route",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            fontSize = 11.sp, color = TracksidePalette.text
        )
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
                TracksideActivityMetric("AVG PACE",
                    if (activity.supportsPace) activity.paceLabel else "—",
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

package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** APPROVED 551:365 / source 477:50.
 * Separate matte visual variant, not a recolor of 551:235. Uses real activity
 * records only; demo pace bars, training effect and synthetic route are omitted.
 */
@Composable
internal fun TracksideParityMatteActivityOverview(activity: ActivityRecord) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF091B26),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .testTag("activity-matte-overview"),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 11.dp)
                .size(width = 48.dp, height = 5.dp)
                .background(Color(0xFF778C9B), RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(43.dp).background(Color(0xFF54253A),
                    RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.trackside_figma_run),
                    contentDescription = null,
                    tint = Color(0xFFFF516B), modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(activity.title,
                    fontSize = 23.sp, lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(activity.dateLabel + " · " + activity.type,
                    fontSize = 11.sp, color = Color(0xFFB7C5CF),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(
            Modifier.fillMaxWidth().padding(start = 73.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MatteActivityTag(activity.type)
            MatteActivityTag(activity.sourceLabel)
        }
        Spacer(Modifier.height(31.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp).height(130.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1B303C),
            border = BorderStroke(1.dp, Color(0xFF537385))
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val distance = activity.distanceM?.let {
                    String.format(Locale.getDefault(), "%.2f", it / 1000.0)
                } ?: "—"
                Column(Modifier.weight(1f).fillMaxHeight()
                    .background(Color(0xFF392632))
                    .padding(start = 9.dp),
                    verticalArrangement = Arrangement.Center) {
                    MatteMetric("DISTANCE", distance, "km")
                }
                Box(Modifier.width(1.dp).height(76.dp)
                    .background(Color(0xFF657985)))
                Column(Modifier.weight(1f).padding(start = 9.dp),
                    verticalArrangement = Arrangement.Center) {
                    MatteMetric("MOVING TIME",
                        activity.movingTimeS?.let { ActivityRecord.duration(it) } ?: "—",
                        "min:sec")
                }
                Box(Modifier.width(1.dp).height(76.dp)
                    .background(Color(0xFF657985)))
                Column(Modifier.weight(1f).padding(start = 9.dp),
                    verticalArrangement = Arrangement.Center) {
                    MatteMetric("AVG PACE",
                        if (activity.supportsPace)
                            activity.paceLabel.removeSuffix("/km").trim()
                        else "—", "/km")
                }
            }
        }
        Spacer(Modifier.height(13.dp))
    }
}

@Composable
private fun MatteMetric(label: String, value: String, unit: String) {
    Text(label, fontSize = 9.sp, lineHeight = 11.sp,
        color = Color(0xFFB7C5CF), maxLines = 1)
    Spacer(Modifier.height(10.dp))
    Text(value, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold,
        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(5.dp))
    Text(unit, fontSize = 11.sp, color = Color(0xFFB7C5CF))
}

@Composable
private fun MatteActivityTag(label: String) {
    Surface(shape = RoundedCornerShape(16.dp),
        color = Color(0xFF203540),
        border = BorderStroke(1.dp, Color(0xFF536C79))) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 10.sp, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Figma matte uses a 57dp flat three-tab rail and red 3dp underline.
 * Unlike the approved atmospheric tab, it has no selected rounded pill.
 */
@Composable
internal fun TracksideParityMatteActivityTabs(
    selected: Int, onSelect: (Int) -> Unit
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(57.dp)
            .background(Color(0xFF091B26))
            .testTag("activity-matte-tabs")
    ) {
        val third = maxWidth / 3
        Row(Modifier.fillMaxSize()) {
            listOf("Overview", "Splits", "Charts").forEachIndexed { index, name ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(index) }
                    .testTag("activity-tab-" + name.lowercase()),
                    contentAlignment = Alignment.Center) {
                    Text(name, fontSize = 12.sp,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected == index) Color.White else Color(0xFFB7C5CF))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter)
            .background(Color(0xFF38515E)))
        Box(
            Modifier.width(third - 20.dp).height(3.dp)
                .align(Alignment.BottomStart)
                .offset(x = third * selected + 10.dp)
                .background(Color(0xFFFF344E), RoundedCornerShape(2.dp))
        )
    }
}

/** Matte activity intelligence: no copied Figma demo values.
 * This is a native source-data chart; without source samples it states why.
 */
@Composable
internal fun TracksideParityMatteActivityHighlights(
    activity: ActivityRecord,
    history: List<ActivityRecord>,
    detail: ActivityDetail?
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF091B26))
        .padding(horizontal = 17.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("╲╲", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = TracksidePalette.red)
            Text("Activity Highlights", fontSize = 17.sp,
                color = Color.White, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(15.dp),
            color = Color(0xFF172C39),
            border = BorderStroke(1.dp, Color(0xFF536F80))) {
            Column(Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ActivityIntelligence.narrative(activity, history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White)
                TracksideMatteSourcePaceProfile(detail)
            }
        }
        Text("Training Impact", fontSize = 17.sp,
            color = Color.White, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(15.dp), color = Color(0xFF172C39),
            border = BorderStroke(1.dp, Color(0xFF536F80))) {
            Column(Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Source-derived training effect", style = MaterialTheme.typography.titleSmall,
                    color = Color.White)
                Text("Not available from the saved source record. No aerobic or anaerobic score is inferred.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7C5CF))
            }
        }
        Text("Recording Details", fontSize = 17.sp,
            color = Color.White, fontWeight = FontWeight.Bold)
        Text(activity.sourceLabel + " · " + activity.dateLabel,
            fontSize = 12.sp, color = Color(0xFFB7C5CF))
    }
}

@Composable
private fun TracksideMatteSourcePaceProfile(detail: ActivityDetail?) {
    val values = detail?.samples?.mapNotNull { it.pace?.takeIf { pace ->
        pace.isFinite() && pace > 0.0
    } } ?: emptyList()
    Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFF0C202B),
        border = BorderStroke(1.dp, Color(0xFF354F5F))) {
        Column(Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pace profile · recorded source samples",
                fontSize = 11.sp, color = Color(0xFFB7C5CF))
            if (values.size < 2) {
                Text("No source pace stream is cached for this activity.",
                    fontSize = 11.sp, color = Color(0xFFB7C5CF))
            } else {
                val low = values.minOrNull() ?: 0.0
                val high = values.maxOrNull() ?: low
                val every = (values.size / 45).coerceAtLeast(1)
                val sampled = values.filterIndexed { i, _ -> i % every == 0 }.take(45)
                Canvas(Modifier.fillMaxWidth().height(92.dp)
                    .testTag("matte-recorded-pace-profile")) {
                    val span = (high - low).coerceAtLeast(1.0)
                    val step = size.width / sampled.size
                    sampled.forEachIndexed { index, pace ->
                        val bar = 18f + ((high - pace) / span).toFloat() * (size.height - 23f)
                        drawRect(
                            TracksidePalette.red,
                            topLeft = Offset(index * step, size.height - bar),
                            size = Size((step - 2f).coerceAtLeast(1f), bar)
                        )
                    }
                }
            }
        }
    }
}

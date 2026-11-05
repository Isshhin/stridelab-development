package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Today layout based on approved Figma 551:6. Only saved athlete information is displayed.
 * The example map, heart-rate limit and pace in that illustration are not prescriptions.
 */
@Composable
internal fun TracksideTodayHero(session: PlannedSession, onOpen: () -> Unit) {
    val running = session.discipline == "run"
    val accent = tracksideDisciplineColor(session.discipline)
    val glow = if (running) Color(0xFF321C29) else Color(0xFF182C3B)
    val dateLabel = when (session.date) {
        LocalDate.now() -> "Today"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> session.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
    if (running) {
        TracksideApprovedRunningHero(session, dateLabel, onOpen)
        return
    }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("today-hero"),
        shape = RoundedCornerShape(23.dp),
        color = TracksidePalette.surfaceLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
    ) {
        Column(
            Modifier.background(
                Brush.verticalGradient(listOf(glow, TracksidePalette.surface, TracksidePalette.surfaceLow))
            ).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) Icon(painter = painterResource(id = R.drawable.trackside_figma_run),
                    contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                else Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(4.dp)))
                Text(dateLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = TracksidePalette.textMuted)
                Surface(color = TracksidePalette.surfaceRaised, shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.dp, TracksidePalette.outline.copy(alpha = 0.6f))) {
                    Text(disciplineLabel(session.discipline), Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = TracksidePalette.text, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(session.effectiveTitle, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = TracksidePalette.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Planned workout · " + session.kind.replace('_', ' '),
                color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
            if (running) {
                // No route is stored with a future workout; do not display the Figma demo GPS trace.
                Box(
                    Modifier.fillMaxWidth().height(142.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF102A37), Color(0xFF07151F))), RoundedCornerShape(13.dp))
                        .testTag("today-route-unavailable"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Decorative running glyph, not a fabricated GPS path.
                        Icon(painter = painterResource(id = R.drawable.trackside_figma_run),
                            contentDescription = null, tint = accent.copy(alpha = 0.55f),
                            modifier = Modifier.size(30.dp))
                        Text("Route not recorded", color = TracksidePalette.text,
                            style = MaterialTheme.typography.titleMedium)
                        Text("A GPS route can appear after the run is recorded",
                            color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val middleLabel = if (running) "DISTANCE" else "EXERCISES"
            val middleValue = if (running)
                session.distanceKm?.let { String.format(Locale.getDefault(), "%.1f km", it) } ?: "Not set"
            else session.effectiveExercises.takeIf { it.isNotEmpty() }?.size?.toString() ?: "Not set"
            val status = session.status.replace('_',' ').uppercase(Locale.getDefault())
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 305.dp || LocalDensity.current.fontScale >= 1.7f) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TodayMetric("DURATION", session.effectiveMinutes.toString() + " min")
                        TodayMetric(middleLabel, middleValue)
                        TodayMetric("STATUS", status)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TodayMetric("DURATION", session.effectiveMinutes.toString() + " min", Modifier.weight(1f))
                        TodayMetric(middleLabel, middleValue, Modifier.weight(1f))
                        TodayMetric("STATUS", status, Modifier.weight(1f))
                    }
                }
            }
            Text("View workout details  ›", color = TracksidePalette.text, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun TodayMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = TracksidePalette.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, color = TracksidePalette.text, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Opens the unchanged workout route; the UI is a compact training-day summary. */
@Composable
internal fun TracksideTodaySession(session: PlannedSession, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("today-session-" + session.id),
        color = TracksidePalette.surface, shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, TracksidePalette.outline.copy(alpha = 0.6f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(9.dp).background(tracksideDisciplineColor(session.discipline), RoundedCornerShape(5.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.effectiveTitle, style = MaterialTheme.typography.titleMedium, color = TracksidePalette.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(disciplineLabel(session.discipline) + " · " + session.effectiveMinutes + " min planned · " +
                    session.status.replace('_',' '), style = MaterialTheme.typography.bodySmall, color = TracksidePalette.textMuted)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = TracksidePalette.textMuted)
        }
    }
}

/** Summary record, never a fabricated route thumbnail or inferred pace. */
@Composable
internal fun TracksideTodayActivity(
    activity: ActivityRecord, sourceLabel:String=activity.sourceLabel, onOpen: () -> Unit
) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("activity-" + activity.id),
        shape = RoundedCornerShape(13.dp), color = TracksidePalette.surface,
        border = BorderStroke(1.dp, TracksidePalette.outline.copy(alpha = 0.6f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(width = 58.dp, height = 54.dp)
                .background(Color(0xFF102A37), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center) {
                Text(activity.type.take(1).uppercase(Locale.getDefault()), color = TracksidePalette.text,
                    style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(activity.title, color = TracksidePalette.text, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(activity.distanceM?.let { activity.distanceLabel },
                    activity.durationS?.let { activity.durationLabel }).ifEmpty { listOf("Metrics not recorded") }.joinToString(" · "),
                    color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
                Text(activity.dateLabel, color = TracksidePalette.textMuted,
                    style = MaterialTheme.typography.labelSmall)
                if(sourceLabel!=activity.sourceLabel)
                    Text(sourceLabel,color=TracksidePalette.textMuted,
                        style=MaterialTheme.typography.labelSmall)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = TracksidePalette.textMuted)
        }
    }
}

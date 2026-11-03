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
internal fun TracksideTodayActivity(activity: ActivityRecord, onOpen: () -> Unit) {
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
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = TracksidePalette.textMuted)
        }
    }
}

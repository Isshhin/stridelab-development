package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Approved master gallery 653:842, active multi-session Today state.
 * Completion always comes from persisted session.status, never device assumptions.
 */
@Composable
internal fun TracksideParityTodayActiveStatus(
    plan: PlanSnapshot,
    today: List<PlannedSession>
) {
    val finished = today.count { it.status == "completed" }
    val week = java.time.temporal.ChronoUnit.WEEKS.between(
        tracksideCalendarMonday(plan.request.start),
        tracksideCalendarMonday(LocalDate.now())
    ).toInt() + 1
    Column(Modifier.fillMaxWidth().testTag("today-active-status"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("PLAN ACTIVE", fontSize = 11.sp, color = TracksidePalette.red,
                    fontWeight = FontWeight.Bold)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · d MMMM",
                    Locale.getDefault())).uppercase(Locale.getDefault()),
                    fontSize = 13.sp, color = TracksidePalette.textMuted)
                Text("Week " + week.coerceAtLeast(1) + " of " + plan.request.weeks +
                    " · Your approved training", fontSize = 11.sp,
                    color = TracksidePalette.textMuted)
            }
            Surface(color = Color(0xFF172D39), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x66758F9F))) {
                Text("ACTIVE", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 10.sp, color = TracksidePalette.text)
            }
        }
        Surface(
            shape = RoundedCornerShape(19.dp),
            color = Color(0xFF14232D),
            border = BorderStroke(1.dp, Color(0x88677987)),
            modifier = Modifier.fillMaxWidth().height(141.dp)
        ) {
            Column(Modifier.background(Brush.horizontalGradient(listOf(
                Color(0xFF25323D), Color(0xFF101B22)
            ))).padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TODAY'S TRAINING", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = TracksidePalette.textMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(finished.toString(), fontSize = 34.sp,
                        fontWeight = FontWeight.Bold, color = TracksidePalette.text)
                    Text(" / " + today.size + " completed", fontSize = 13.sp,
                        color = TracksidePalette.textMuted,
                        modifier = Modifier.padding(bottom = 7.dp))
                }
                Row(
                    Modifier.fillMaxWidth().height(13.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    today.forEach { session ->
                        Box(Modifier.weight(1f).fillMaxHeight().background(
                            if (session.status == "completed") TracksidePalette.running
                            else Color(0xFF3A4E5C),
                            RoundedCornerShape(6.dp)))
                    }
                }
                Text(finished.toString() + " completed · " +
                    (today.size - finished).coerceAtLeast(0) + " remaining",
                    fontSize = 11.sp, color = TracksidePalette.textMuted)
            }
        }
    }
}

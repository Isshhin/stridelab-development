package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.Locale

/** All values are prescribed minutes, not measured effort or completed training load. */
internal data class TracksideTrainingDay(
    val date: LocalDate,
    val sessions: List<PlannedSession>
) {
    val plannedMinutes: Int get() = sessions.sumOf { it.effectiveMinutes.coerceAtLeast(0) }
    val byDiscipline: List<Pair<String, Int>> get() = sessions
        .groupBy { it.discipline }
        .map { (discipline, rows) -> discipline to rows.sumOf { it.effectiveMinutes.coerceAtLeast(0) } }
        .filter { it.second > 0 }
}

internal fun tracksideCalendarMonday(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun tracksideTrainingWeek(plan: PlanSnapshot, selected: LocalDate): List<TracksideTrainingDay> {
    val monday = tracksideCalendarMonday(selected)
    return (0L..6L).map { offset ->
        val date = monday.plusDays(offset)
        TracksideTrainingDay(date, plan.sessions.filter { it.date == date })
    }
}

/** Figma 602:3 week chart: responsive seven-day minutes, active day, and domain stacks.
 * It is read-only and calls the existing date selection route. No plan mutation.
 */
@Composable
internal fun TracksideTrainingWeekChart(
    plan: PlanSnapshot,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val first = plan.request.start
    val last = first.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
    val firstMonday = tracksideCalendarMonday(first)
    val weekStart = tracksideCalendarMonday(selected)
    val lastMonday = tracksideCalendarMonday(last)
    val weekIndex = ChronoUnit.WEEKS.between(firstMonday, weekStart).toInt() + 1
    val totalWeeks = ChronoUnit.WEEKS.between(firstMonday, lastMonday).toInt() + 1
    val days = tracksideTrainingWeek(plan, selected)
    val total = days.sumOf { it.plannedMinutes }
    val maxDay = (days.maxOfOrNull { it.plannedMinutes } ?: 0).coerceAtLeast(1)
    val byDiscipline = days.flatMap { it.sessions }.groupBy { it.discipline }
        .map { (discipline, sessions) -> discipline to sessions.size }
        .filter { it.second > 0 }

    Column(Modifier.fillMaxWidth().testTag("training-week-chart"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { onSelect(maxOf(first, weekStart.minusWeeks(1))) },
                enabled = weekStart > firstMonday,
                modifier = Modifier.testTag("training-prev-week")
            ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val end = weekStart.plusDays(6)
                val span = if (weekStart.month == end.month)
                    "${weekStart.dayOfMonth}–${end.dayOfMonth} ${end.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())}"
                else "${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM"))}"
                Text(span, style = MaterialTheme.typography.titleMedium, color = TracksidePalette.text)
                Text("Week $weekIndex of $totalWeeks", style = MaterialTheme.typography.labelSmall,
                    color = TracksidePalette.textMuted)
            }
            TextButton(
                onClick = { onSelect(minOf(last, weekStart.plusWeeks(1))) },
                enabled = weekStart < lastMonday,
                modifier = Modifier.testTag("training-next-week")
            ) { Text("›", style = MaterialTheme.typography.headlineSmall) }
        }

        if (byDiscipline.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                byDiscipline.forEach { (discipline, count) ->
                    Box(Modifier.weight(count.toFloat()).height(8.dp)
                        .background(disciplineColor(discipline), RoundedCornerShape(4.dp)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                byDiscipline.forEach { (discipline, count) ->
                    Text("$count ${disciplineLabel(discipline)}", color = disciplineColor(discipline),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(count.toFloat()))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Planned minutes per day", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Week total ${total / 60} h ${total % 60} min",
                style = MaterialTheme.typography.labelSmall, color = TracksidePalette.textMuted)
        }

        Row(Modifier.fillMaxWidth().height(158.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            days.forEach { day ->
                val enabled = day.date in first..last
                val active = day.date == selected
                val description = "${day.date}: ${day.sessions.size} planned sessions, ${day.plannedMinutes} planned minutes"
                Surface(
                    onClick = { onSelect(day.date) },
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) Color(0xFF1A2C36) else Color.Transparent,
                    border = BorderStroke(1.dp,
                        if (active) Color(0xFFB2C5D0) else Color.Transparent),
                    modifier = Modifier.weight(1f).testTag("calendar-day-${day.date}")
                        .semantics { contentDescription = description }
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (day.plannedMinutes == 0) "–" else day.plannedMinutes.toString(),
                            color = if (enabled) TracksidePalette.text else TracksidePalette.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, textAlign = TextAlign.Center)
                        Column(
                            Modifier.fillMaxWidth().height(84.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            day.byDiscipline.forEach { (discipline, minutes) ->
                                val barHeight = (minutes.toFloat() / maxDay * 80f).coerceAtLeast(3f)
                                Box(Modifier.fillMaxWidth().height(barHeight.dp)
                                    .background(disciplineColor(discipline), RoundedCornerShape(5.dp)))
                            }
                        }
                        Text(day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(Locale.getDefault()),
                            color = TracksidePalette.textMuted, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1)
                        Text(day.date.dayOfMonth.toString(), color = TracksidePalette.text,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

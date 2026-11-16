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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
        TracksideTrainingDay(date, plan.sessions.filter { it.date == date && it.status != "unassigned" }.map(plan::cardPresentation))
    }
}

/** Figma 602:3 week chart: responsive seven-day minutes, active day, and domain stacks.
 * It is read-only and calls the existing date selection route. No plan mutation.
 */
@Composable
internal fun TracksideTrainingWeekChart(
    plan: PlanSnapshot,
    selected: LocalDate,
    showNavigation: Boolean = true,
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
    var distanceView by rememberSaveable(plan.id) { mutableStateOf(false) }
    val total = days.sumOf { it.plannedMinutes }
    val dayDistances=days.map { day -> day.sessions.sumOf {
        if(it.discipline=="run")it.distanceKm ?: 0.0 else 0.0
    } }
    val totalKm=dayDistances.sum()
    val chartValues=days.indices.map { i ->
        if(distanceView)dayDistances[i] else days[i].plannedMinutes.toDouble()
    }
    val maxDay=(chartValues.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val byDiscipline = days.flatMap { it.sessions }.groupBy { it.discipline }
        .map { (discipline, sessions) -> discipline to sessions.size }
        .filter { it.second > 0 }

    Column(Modifier.fillMaxWidth().testTag("training-week-chart"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Your training week",style=MaterialTheme.typography.titleSmall,
                color=Color(0xFFF0F4EE),fontWeight=FontWeight.Bold,
                modifier=Modifier.weight(1f))
            listOf(false to "min",true to "km").forEach { (metric,label) ->
                Surface(onClick={distanceView=metric},
                    modifier=Modifier.padding(start=3.dp)
                        .testTag("training-chart-$label"),
                    color=if(distanceView==metric)Color(0xFFD8FB70)
                        else Color(0xFF2C342C),
                    shape=RoundedCornerShape(8.dp)) {
                    Text(label,Modifier.padding(horizontal=12.dp,vertical=7.dp),
                        color=if(distanceView==metric)Color(0xFF11170D)
                            else Color(0xFFAAB5A8),
                        style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (showNavigation) {
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
        }

        if (byDiscipline.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                byDiscipline.forEach { (discipline, count) ->
                    Box(Modifier.weight(count.toFloat()).height(8.dp)
                        .background(tracksideDisciplineColor(discipline), RoundedCornerShape(4.dp)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                byDiscipline.forEach { (discipline, count) ->
                    Text("$count ${disciplineLabel(discipline)}", color = tracksideDisciplineColor(discipline),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(count.toFloat()))
                }
            }
        }

        if (LocalDensity.current.fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if(distanceView)"Planned running kilometres" else "Planned minutes per day", style = MaterialTheme.typography.titleSmall,
                    color = TracksidePalette.text, fontWeight = FontWeight.Bold)
                Text(if(distanceView)String.format(Locale.getDefault(),"%.1f km planned",totalKm)
                    else "Week total ${total / 60} h ${total % 60} min",
                    style = MaterialTheme.typography.labelSmall, color = TracksidePalette.textMuted)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if(distanceView)"Planned running kilometres" else "Planned minutes per day", style = MaterialTheme.typography.titleSmall,
                    color = TracksidePalette.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if(distanceView)String.format(Locale.getDefault(),"%.1f km planned",totalKm)
                    else "Week total ${total / 60} h ${total % 60} min",
                    style = MaterialTheme.typography.labelSmall, color = TracksidePalette.textMuted)
            }
        }

        // Seven 48dp targets cannot fit into a 316dp content area (360dp phone
        // minus screen padding). Let the row scroll rather than shrink targets.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 350.dp
            val expandedFont = LocalDensity.current.fontScale >= 1.3f
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            // Keep the selected day fully visible when seven 48dp+ targets
            // must scroll on narrow phones. Never shrink their touch targets.
            LaunchedEffect(compact, selected, scroll.maxValue) {
                if (compact && scroll.maxValue > 0) {
                    val index = ChronoUnit.DAYS.between(weekStart, selected).toInt().coerceIn(0, 6)
                    val cellStart = with(density) { (index * 51).dp.roundToPx() }
                    val cellEnd = with(density) { (index * 51 + 49).dp.roundToPx() }
                    val viewport = with(density) { maxWidth.roundToPx() }
                    val target = when {
                        cellStart < scroll.value -> cellStart
                        cellEnd > scroll.value + viewport -> cellEnd - viewport
                        else -> scroll.value
                    }
                    scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
                }
            }
            val rowModifier = Modifier.fillMaxWidth()
                .height(if (expandedFont) 188.dp else 164.dp)
                .then(if (compact) Modifier.horizontalScroll(scroll) else Modifier)
            Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                days.forEach { day ->
                val enabled = day.date in first..last
                val active = day.date == selected
                val description = "${day.date}: ${day.sessions.size} planned sessions, ${day.plannedMinutes} planned minutes"
                Surface(
                    onClick = { onSelect(day.date) },
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) Color(0xFF2B3825) else Color.Transparent,
                    border = BorderStroke(1.dp,
                        if (active) Color(0xFFD8FB70) else Color(0xFF333D33)),
                    modifier = (if (compact) Modifier.width(49.dp) else Modifier.weight(1f))
                        .testTag("calendar-day-${day.date}")
                        .semantics { contentDescription = description }
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if(chartValues[days.indexOf(day)]<=0.0) "–"
                                else if(distanceView)String.format(Locale.getDefault(),"%.1f",dayDistances[days.indexOf(day)])
                                else day.plannedMinutes.toString(),
                            color = if (enabled) TracksidePalette.text else TracksidePalette.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, textAlign = TextAlign.Center)
                        Column(
                            Modifier.fillMaxWidth().height(72.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            val index=days.indexOf(day)
                            val chartValue=chartValues[index]
                            if(chartValue>0.0) {
                                val barHeight=(chartValue/maxDay*68.0).toFloat().coerceAtLeast(3f)
                                Box(Modifier.fillMaxWidth().height(barHeight.dp)
                                    .background(if(active)Color(0xFFD8FB70)
                                        else Color(0xFFA6CB6A),RoundedCornerShape(5.dp)))
                            }
                        }
                        Text(if (compact) day.date.dayOfWeek.name.take(2) else
                            day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(Locale.getDefault()),
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
}

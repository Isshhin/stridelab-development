package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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
        TracksideTrainingDay(
            date,
            plan.sessions.filter { it.date == date && it.status != "unassigned" }.map(plan::cardPresentation)
        )
    }
}

private fun dayBarAccent(sessions: List<PlannedSession>): Color {
    if (sessions.isEmpty()) return StrideLabVisualPalette.muted
    val run = sessions.firstOrNull { it.discipline == "run" }
    if (run != null) {
        return when (run.kind) {
            "long" -> StrideLabVisualPalette.lime
            "quality" -> {
                val fam = run.runningPrescription?.family?.lowercase(Locale.ROOT).orEmpty()
                if (fam == "intervals" || fam == "hills") StrideLabVisualPalette.highCoral
                else StrideLabVisualPalette.moderateAmber
            }
            else -> StrideLabVisualPalette.easyTeal
        }
    }
    return StrideLabVisualPalette.disciplineColor(sessions.first().discipline)
}

/**
 * Art-directed interactive 7-day Training Calendar & Volume Chart.
 * Defaults to kilometers (km) for running plans, displays semantic gradient bars
 * with glowing top caps, dual Logged + Planned day dots, and a tappable Week Header pill
 * so pressing a Day or pressing the Week shows both logged activities and planned workouts.
 */
@Composable
internal fun TracksideTrainingWeekChart(
    plan: PlanSnapshot,
    selected: LocalDate,
    showNavigation: Boolean = true,
    recordedDates: Set<LocalDate> = emptySet(),
    weekScopeSelected: Boolean = false,
    onToggleWeekScope: (() -> Unit)? = null,
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
    val hasRunning = plan.request.runDays > 0 || plan.sessions.any { it.discipline == "run" }
    var distanceView by rememberSaveable(plan.id) { mutableStateOf(hasRunning) }
    val total = days.sumOf { it.plannedMinutes }

    fun activeEstimatedDistance(session: PlannedSession): Double? {
        if (session.suggestion?.let { it.accepted && it.minutes < session.minutes } == true) return null
        return session.distanceKm ?: session.runningPrescription?.estimatedDistanceKm
    }

    val dayDistances = days.map { day ->
        day.sessions.sumOf {
            if (it.discipline == "run") activeEstimatedDistance(it) ?: 0.0 else 0.0
        }
    }
    val estimatedDays = days.map { day ->
        day.sessions.any { session ->
            session.runningPrescription?.mixedSegments != null &&
                activeEstimatedDistance(session) != null
        }
    }
    val hasEstimates = estimatedDays.any { it }
    val totalKm = dayDistances.sum()
    val chartValues = days.indices.map { i ->
        if (distanceView) dayDistances[i] else days[i].plannedMinutes.toDouble()
    }
    val maxDay = (chartValues.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val completedInWeek = days.count { day ->
        day.date in recordedDates || day.sessions.any { it.status == "completed" }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .testTag("training-week-chart"),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        // Top header row: Title + km/min unit switcher
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "TRAINING CALENDAR",
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    color = StrideLabVisualPalette.muted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (distanceView) String.format(Locale.US, "%.1f km planned", totalKm)
                    else "${total / 60}h ${total % 60}m planned",
                    fontSize = 16.sp,
                    color = StrideLabVisualPalette.ink,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            val haptics = LocalHapticFeedback.current
            val unitIdx = if (distanceView) 0 else 1
            val unitPos = remember { Animatable(unitIdx.toFloat()) }
            LaunchedEffect(unitIdx) {
                unitPos.animateTo(
                    unitIdx.toFloat(),
                    animationSpec = spring(
                        dampingRatio = TracksideLiquidMotion.DAMPING,
                        stiffness = TracksideLiquidMotion.STIFFNESS
                    )
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .width(92.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StrideLabVisualPalette.surfaceRaised)
                    .border(1.dp, StrideLabVisualPalette.border, RoundedCornerShape(10.dp))
                    .padding(2.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell = size.width / 2f
                    val currentX = unitPos.value * cell
                    val targetX = unitIdx * cell
                    val p = TracksideLiquidMotion.pressure(unitPos.value, unitIdx.toFloat(), 1f)
                    val movingRight = targetX >= currentX
                    val stretch = p * 4.dp.toPx()
                    val left = (currentX - if (!movingRight) stretch else 0f).coerceIn(0f, (size.width - cell).coerceAtLeast(0f))
                    val r = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    if (p > 0.05f) {
                        val wake = p * 5.dp.toPx()
                        val wakeX = if (movingRight) (currentX - wake).coerceAtLeast(0f) else currentX + cell
                        drawRoundRect(
                            color = StrideLabVisualPalette.lime.copy(alpha = 0.22f * p),
                            topLeft = Offset(wakeX, size.height * 0.14f),
                            size = Size((wake + 3.dp.toPx()).coerceAtMost(size.width - wakeX), size.height * 0.72f),
                            cornerRadius = r
                        )
                    }
                    drawRoundRect(
                        color = StrideLabVisualPalette.lime,
                        topLeft = Offset(left, 0f),
                        size = Size((cell + stretch).coerceAtMost(size.width - left), size.height),
                        cornerRadius = r
                    )
                }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    listOf(true to "km", false to "min").forEach { (metric, label) ->
                        val isSelected = distanceView == metric
                        val labelInk by animateColorAsState(
                            if (isSelected) PlanBuilderPalette.accentOn else StrideLabVisualPalette.secondary,
                            tween(150),
                            label = "chart-unit-$label"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (distanceView != metric) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        distanceView = metric
                                    }
                                }
                                .testTag("training-chart-$label"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = labelInk,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Week Navigation + Tappable Week Focus Pill
        if (showNavigation) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { onSelect(maxOf(first, weekStart.minusWeeks(1))) },
                    enabled = weekStart > firstMonday,
                    modifier = Modifier.testTag("training-prev-week")
                ) {
                    Text(
                        "‹",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (weekStart > firstMonday) StrideLabVisualPalette.lime else StrideLabVisualPalette.muted
                    )
                }
                val end = weekStart.plusDays(6)
                val span = if (weekStart.month == end.month)
                    "${weekStart.dayOfMonth}–${end.dayOfMonth} ${end.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())}"
                else "${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM"))}"

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (weekScopeSelected) StrideLabVisualPalette.limeTint else StrideLabVisualPalette.surfaceRaised,
                    border = BorderStroke(
                        1.dp,
                        if (weekScopeSelected) StrideLabVisualPalette.lime else StrideLabVisualPalette.border
                    ),
                    modifier = Modifier
                        .then(if (onToggleWeekScope != null) Modifier.clickable { onToggleWeekScope() } else Modifier)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            span,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (weekScopeSelected) StrideLabVisualPalette.lime else StrideLabVisualPalette.ink
                        )
                        Text(
                            "Week $weekIndex of $totalWeeks" + if (onToggleWeekScope != null) {
                                if (weekScopeSelected) " · Showing full week" else " · Tap for full week"
                            } else "",
                            fontSize = 10.5.sp,
                            color = if (weekScopeSelected) StrideLabVisualPalette.lime else StrideLabVisualPalette.secondary
                        )
                    }
                }

                TextButton(
                    onClick = { onSelect(minOf(last, weekStart.plusWeeks(1))) },
                    enabled = weekStart < lastMonday,
                    modifier = Modifier.testTag("training-next-week")
                ) {
                    Text(
                        "›",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (weekStart < lastMonday) StrideLabVisualPalette.lime else StrideLabVisualPalette.muted
                    )
                }
            }
        }

        // Compact Legend + Week Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${days.sumOf { it.sessions.size }} planned · $completedInWeek logged",
                color = StrideLabVisualPalette.secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(StrideLabVisualPalette.lime))
                    Text("Logged", fontSize = 10.sp, color = StrideLabVisualPalette.secondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(StrideLabVisualPalette.easyTeal))
                    Text("Planned", fontSize = 10.sp, color = StrideLabVisualPalette.secondary)
                }
            }
        }

        // 7-Day Interactive Columns
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 350.dp
            val expandedFont = LocalDensity.current.fontScale >= 1.3f
            val scroll = rememberScrollState()
            val density = LocalDensity.current
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
            val rowModifier = Modifier
                .fillMaxWidth()
                .height(if (expandedFont) 196.dp else 172.dp)
                .then(if (compact) Modifier.horizontalScroll(scroll) else Modifier)

            Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEachIndexed { index, day ->
                    val enabled = day.date in first..last
                    val active = day.date == selected && !weekScopeSelected
                    val barAccent = dayBarAccent(day.sessions)
                    val hasLogged = day.date in recordedDates || day.sessions.any { it.status == "completed" }
                    val hasPlanned = day.sessions.isNotEmpty()
                    val description = "${day.date}: ${day.sessions.size} planned sessions, ${day.plannedMinutes} planned minutes"

                    Surface(
                        onClick = { onSelect(day.date) },
                        enabled = enabled,
                        shape = RoundedCornerShape(13.dp),
                        color = when {
                            active -> StrideLabVisualPalette.limeTint
                            weekScopeSelected && (hasLogged || hasPlanned) -> StrideLabVisualPalette.surfaceRaised
                            else -> Color.Transparent
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                active -> StrideLabVisualPalette.lime
                                weekScopeSelected && (hasLogged || hasPlanned) -> barAccent.copy(alpha = 0.45f)
                                else -> StrideLabVisualPalette.border
                            }
                        ),
                        modifier = (if (compact) Modifier.width(49.dp) else Modifier.weight(1f))
                            .testTag("calendar-day-${day.date}")
                            .semantics { contentDescription = description }
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (chartValues[index] <= 0.0) "–"
                                else if (distanceView) String.format(
                                    Locale.getDefault(),
                                    if (estimatedDays[index]) "~%.1f" else "%.1f",
                                    dayDistances[index]
                                )
                                else day.plannedMinutes.toString(),
                                color = if (active) StrideLabVisualPalette.lime
                                else if (enabled && chartValues[index] > 0.0) StrideLabVisualPalette.ink
                                else StrideLabVisualPalette.muted,
                                fontSize = 10.sp,
                                fontWeight = if (chartValues[index] > 0.0) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .height(70.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                val chartValue = chartValues[index]
                                if (chartValue > 0.0) {
                                    val targetHeight = (chartValue / maxDay * 66.0).toFloat().coerceAtLeast(6f)
                                    val animatedHeight by animateFloatAsState(
                                        targetValue = targetHeight,
                                        animationSpec = spring(
                                            dampingRatio = TracksideLiquidMotion.DAMPING,
                                            stiffness = TracksideLiquidMotion.STIFFNESS
                                        ),
                                        label = "cal-bar-${day.date}"
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxWidth(0.72f)
                                            .height(animatedHeight.dp)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                            .background(StrideLabVisualPalette.barGradient(barAccent))
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(2.5.dp)
                                                .align(Alignment.TopCenter)
                                                .background(barAccent)
                                        )
                                    }
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(0.5f)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(StrideLabVisualPalette.border)
                                    )
                                }
                            }
                            Text(
                                if (compact) day.date.dayOfWeek.name.take(2)
                                else day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(Locale.getDefault()),
                                color = if (active) StrideLabVisualPalette.lime else StrideLabVisualPalette.muted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                day.date.dayOfMonth.toString(),
                                color = if (active) StrideLabVisualPalette.lime else StrideLabVisualPalette.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            // Dual Logged + Planned status indicator dots
                            Row(
                                modifier = Modifier.height(7.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasLogged) {
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(StrideLabVisualPalette.lime)
                                    )
                                }
                                if (hasPlanned) {
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(barAccent.copy(alpha = if (hasLogged) 0.55f else 0.9f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

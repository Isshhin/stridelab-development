package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Actual current StrideLab Today palette, not a separate design system. */
private object DisciplineLook {
    val black = Color.Black
    val panel = Color(0xFF111315)
    val border = Color(0xFF303438)
    val ink = Color(0xFFF7F7F5)
    val subdued = Color(0xFF9FA4AA)
    val lime = Color(0xFFD8FB70)
    fun accent(d: TodayDiscipline): Color = when (d) {
        TodayDiscipline.RUNNING -> lime
        TodayDiscipline.STRENGTH -> Color(0xFFFF8A77)
        TodayDiscipline.CALISTHENICS -> Color(0xFFC8A8FF)
        TodayDiscipline.CONDITIONING -> Color(0xFF91BFFF)
    }
}

@Composable
internal fun TodayDisciplineTabs(
    available: List<TodayDiscipline>,
    selectedKey: String,
    reduceMotion: Boolean,
    onSelect: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("today-discipline-pages"),
        color = DisciplineLook.panel,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, DisciplineLook.border)
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val options = listOf("all" to "All") + available.map { it.key to it.title }
            options.forEach { (key, title) ->
                val active = key == selectedKey
                val background by animateColorAsState(
                    if (active) DisciplineLook.lime else Color.Transparent,
                    animationSpec = tween(if (reduceMotion) 0 else 210),
                    label = "today-page-pill-$key"
                )
                val pillScale by animateFloatAsState(
                    if (active) 1f else .97f,
                    animationSpec = if (reduceMotion) snap() else spring(
                        dampingRatio = TracksideLiquidMotion.DAMPING,
                        stiffness = TracksideLiquidMotion.STIFFNESS
                    ),
                    label = "today-page-spring-$key"
                )
                val ink by animateColorAsState(
                    if (active) DisciplineLook.black else DisciplineLook.ink,
                    animationSpec = tween(if (reduceMotion) 0 else 210),
                    label = "today-page-label-$key"
                )
                Surface(
                    onClick = {
                        if (key != selectedKey) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(key)
                        }
                    },
                    color = background,
                    shape = RoundedCornerShape(23.dp),
                    modifier = Modifier.height(42.dp)
                        .graphicsLayer {
                            scaleX = pillScale
                            scaleY = pillScale
                        }
                        .testTag("today-page-$key")
                        .semantics { selected = active }
                ) {
                    Box(
                        Modifier.padding(horizontal = 17.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, color = ink, fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisciplinePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DisciplineLook.panel,
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, DisciplineLook.border)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun km(value: Double): String =
    String.format(Locale.getDefault(), "%.1f km", value / 1000.0)

private fun time(value: Double): String {
    val rounded = (value / 60.0).toLong()
    return if (rounded >= 60) "${rounded / 60}h ${rounded % 60}m" else "${rounded}m"
}

@Composable
internal fun TodayDisciplineDashboard(
    discipline: TodayDiscipline,
    trend: List<TodayDisciplineWeek>,
    runningReview: List<RunningWeekCoach>,
    plans: List<PlanSnapshot>,
    today: LocalDate,
    reduceMotion: Boolean,
    onOpenActivity: (String) -> Unit,
    onOpenWorkout: (String, String) -> Unit
) {
    val week = trend.last()
    val tint = DisciplineLook.accent(discipline)
    val dateRange = week.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) +
        " – " + week.start.plusDays(6).format(
            DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        )
    val showDistance = discipline == TodayDiscipline.RUNNING &&
        week.recordedDistanceM != null
    val primary = when {
        showDistance -> km(week.recordedDistanceM!!)
        week.recordedSeconds != null -> time(week.recordedSeconds)
        else -> "—"
    }

    Column(
        Modifier.fillMaxWidth().testTag("today-discipline-${discipline.key}"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(discipline.title, color = DisciplineLook.ink,
                    fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(dateRange, color = DisciplineLook.subdued, fontSize = 12.sp)
            }
            Box(
                Modifier.size(37.dp)
                    .background(tint.copy(alpha = .16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Small colored geometry, not a decorative stock illustration.
                Box(Modifier.size(12.dp).background(tint, CircleShape))
            }
        }

        if (discipline == TodayDiscipline.RUNNING) {
            RunningWeekOverview(week, runningReview.lastOrNull())
        } else {
            DisciplinePanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showDistance) "WEEKLY DISTANCE" else "RECORDED TRAINING TIME",
                        Modifier.weight(1f), color = DisciplineLook.subdued, fontSize = 11.sp)
                    Text("${week.sessions} sessions", color = tint, fontSize = 11.sp)
                }
                Text(primary, color = tint, fontSize = 39.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("today-discipline-primary"))
                // One compact chart with animated bars. Zero duration/distance is
                // not drawn as a fabricated effort score.
                DisciplineWeekBars(
                    data = if (showDistance) week.distanceByDay else week.secondsByDay,
                    presence = week.sessionsByDay,
                    tint = tint,
                    reduceMotion = reduceMotion
                )
                if (week.sessions == 0) {
                    Text("No recorded ${discipline.title.lowercase(Locale.ROOT)} this week",
                        color = DisciplineLook.subdued, fontSize = 12.sp)
                } else if (week.missingDuration > 0 ||
                    (showDistance && week.missingDistance > 0)) {
                    Text("Partial totals · some sessions have missing metrics",
                        color = DisciplineLook.subdued, fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DisciplineMetric("TRAINING DAYS", "${week.activeDays}", tint, Modifier.weight(1f))
                DisciplineMetric("SESSIONS", "${week.sessions}", tint, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DisciplineMetric("RECORDED TIME",
                    week.recordedSeconds?.let(::time) ?: "—", tint, Modifier.weight(1f))
                DisciplineMetric(
                    if (discipline == TodayDiscipline.RUNNING) "AVG RUN HR" else "AVG SESSION",
                    if (discipline == TodayDiscipline.RUNNING)
                        week.averageRecordedHr?.let { "${it.toInt()} bpm" } ?: "—"
                    else {
                        val timedSessions = week.sessions - week.missingDuration
                        if (timedSessions > 0 && week.recordedSeconds != null)
                            time(week.recordedSeconds / timedSessions)
                        else "—"
                    },
                    tint, Modifier.weight(1f)
                )
            }

            DisciplinePanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Training effort", color = DisciplineLook.ink,
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        fontSize = 16.sp)
                    Text("TIME", color = tint, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                DisciplineWeekBars(week.secondsByDay, week.sessionsByDay, tint, reduceMotion)
                Text("Recorded duration by day · not an intensity or load score",
                    color = DisciplineLook.subdued, fontSize = 11.sp)
            }

        }

        if (discipline == TodayDiscipline.RUNNING) {
            RunningWeekReview(runningReview, plans, today)
        } else {
            DisciplinePanel {
                Text("4-week trend",
                    color = DisciplineLook.ink,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val values = trend.map { it.recordedSeconds?.div(60.0) }
                DisciplineTrendLine(values, tint, reduceMotion)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    trend.forEachIndexed { index, item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(item.start.format(DateTimeFormatter.ofPattern("d MMM",
                                Locale.getDefault())), color = DisciplineLook.subdued,
                                fontSize = 10.sp)
                            Text(values[index]?.toLong()?.toString() ?: "—", color = if (index == 3) tint else DisciplineLook.ink,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Recorded minutes each week",
                    color = DisciplineLook.subdued, fontSize = 11.sp)
            }

        }

        val recent = (
            week.sourceActivities.map { Triple(
                it.startedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                it.title, it.id
            ) } + week.nativeWorkouts.map {
                Triple(it.date, "Completed workout", it.planId + "|" + it.sessionId)
            }
        ).sortedByDescending { it.first }.take(4)
        if (recent.isNotEmpty()) {
            DisciplinePanel {
                Text(if (discipline == TodayDiscipline.RUNNING) "Runs this week" else "Saved sessions",
                    color = DisciplineLook.ink,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                recent.forEach { (date, label, id) ->
                    val source = week.sourceActivities.firstOrNull { it.id == id }
                    val workout = week.nativeWorkouts.firstOrNull { it.planId + "|" + it.sessionId == id }
                    Surface(
                        onClick = {
                            if (source != null) onOpenActivity(source.id)
                            else workout?.let { onOpenWorkout(it.planId, it.sessionId) }
                        },
                        color = Color(0xFF1B1E20),
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).background(tint, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, color = DisciplineLook.ink,
                                    fontSize = if (discipline == TodayDiscipline.RUNNING) 13.sp else 12.sp,
                                    maxLines = if (discipline == TodayDiscipline.RUNNING) 2 else 1,
                                    overflow = TextOverflow.Ellipsis)
                                if (discipline == TodayDiscipline.RUNNING) {
                                    val metrics = listOfNotNull(
                                        (source?.distanceM ?: workout?.distanceM)?.takeIf { it.isFinite() && it >= 0 }?.let(::km),
                                        (source?.durationS ?: workout?.durationSeconds)?.takeIf { it.isFinite() && it >= 0 }?.let(::time)
                                    )
                                    if (metrics.isNotEmpty()) Text(metrics.joinToString(" · "),
                                        color = DisciplineLook.subdued, fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(date.format(DateTimeFormatter.ofPattern("d MMM",
                                Locale.getDefault())), color = DisciplineLook.subdued,
                                fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningWeekOverview(week: TodayDisciplineWeek, review: RunningWeekCoach?) {
    var byDistance by rememberSaveable { mutableStateOf(true) }
    var selectedDay by rememberSaveable(week.start.toString()) {
        mutableStateOf(week.sessionsByDay.indexOfLast { it > 0 }.coerceAtLeast(0))
    }
    val unit = if (byDistance) "distance" else "time"
    val total = if (byDistance) week.recordedDistanceM else week.recordedSeconds
    val format: (Double) -> String = if (byDistance) ::km else ::time
    val values = if (byDistance) week.distanceByDay else week.secondsByDay
    val missing = if (byDistance) week.missingDistance else week.missingDuration
    val peak = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val zone = ZoneId.systemDefault()
    val metricsByDay = remember(week, byDistance, zone) {
        val imported = week.sourceActivities.map {
            it.startedAt.atZone(zone).toLocalDate() to if (byDistance) it.distanceM else it.durationS
        }
        val logged = week.nativeWorkouts.map {
            it.date to if (byDistance) it.distanceM else it.durationSeconds
        }
        (imported + logged).groupBy({ it.first }, { it.second })
    }
    val dayLabel = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    val sessions = if (week.sessions == 1) "1 run" else "${week.sessions} runs"
    val days = if (week.activeDays == 1) "1 day" else "${week.activeDays} days"

    fun dayValue(index: Int): String {
        val count = week.sessionsByDay[index]
        if (count == 0) return "No runs"
        val known = metricsByDay[week.start.plusDays(index.toLong())].orEmpty()
            .count { it != null && it.isFinite() && it >= 0 }
        return when {
            known == 0 -> "${if (byDistance) "Distance" else "Time"} unavailable"
            known < count -> "${format(values[index])} recorded · partial"
            else -> format(values[index])
        }
    }

    DisciplinePanel {
        Text("Your week in running", color = DisciplineLook.ink,
            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Row(Modifier.selectableGroup().fillMaxWidth()
            .background(Color(0xFF232728), RoundedCornerShape(14.dp)).padding(4.dp)) {
            listOf(true to "Distance", false to "Time").forEach { (distance, title) ->
                val active = distance == byDistance
                Box(Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (active) DisciplineLook.lime else Color.Transparent,
                        RoundedCornerShape(10.dp))
                    .selectable(active, role = Role.Tab) { byDistance = distance }
                    .testTag("today-running-metric-${title.lowercase(Locale.ROOT)}"),
                    contentAlignment = Alignment.Center) {
                    Text(title, color = if (active) DisciplineLook.black else DisciplineLook.ink,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(6.dp))
                }
            }
        }
        Text(total?.let(format) ?: if (week.sessions == 0) "No runs yet" else "—",
            color = DisciplineLook.ink, fontSize = 36.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("today-discipline-primary"))
        Text("$sessions across $days", color = DisciplineLook.subdued, fontSize = 14.sp)
        review?.mostPerformed?.let { type ->
            Text("Most common among classified runs: ${type.label}", color = runTypeColor(type), fontSize = 13.sp)
        }
        if (missing > 0) Text(
            if (total == null) "No $unit saved for these runs."
            else "$missing ${if (missing == 1) "run has" else "runs have"} no $unit recorded.",
            color = DisciplineLook.subdued, fontSize = 12.sp)

        Row(Modifier.fillMaxWidth().selectableGroup().testTag("today-discipline-week-bars")) {
            values.forEachIndexed { index, value ->
                val date = week.start.plusDays(index.toLong())
                val active = index == selectedDay
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Color(0xFF222A1D) else Color.Transparent)
                        .selectable(active, role = Role.Tab) { selectedDay = index }
                        .testTag("today-running-day-$index")
                        .semantics { contentDescription = "${date.format(dayLabel)}, ${dayValue(index)}" }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.height(92.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth(.54f).fillMaxHeight()
                            .background(Color(0xFF272D2A), RoundedCornerShape(6.dp)))
                        if (value > 0) Box(Modifier.fillMaxWidth(.54f)
                            .height((92 * value / peak).dp)
                            .background(if (active) DisciplineLook.lime else Color(0xFF7D9F50),
                                RoundedCornerShape(6.dp)))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())),
                        color = if (active) DisciplineLook.lime else DisciplineLook.subdued, fontSize = 12.sp)
                }
            }
        }

        Row(Modifier.fillMaxWidth().background(Color(0xFF1B201B), RoundedCornerShape(12.dp))
            .padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(week.start.plusDays(selectedDay.toLong()).format(dayLabel),
                color = DisciplineLook.ink, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Text(dayValue(selectedDay), color = DisciplineLook.lime, modifier = Modifier.weight(1f).testTag("today-running-day-value"),
                fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        week.averageRecordedHr?.let { hr ->
            Text("Average run heart rate: ${hr.toInt()} bpm across ${week.hrCoverage} of ${week.sessions} runs",
                color = DisciplineLook.subdued, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DisciplineMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    DisciplinePanel(modifier) {
        Text(label, color = DisciplineLook.subdued, fontSize = 10.sp)
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DisciplineWeekBars(
    data: List<Double>,
    presence: List<Int>,
    tint: Color,
    reduceMotion: Boolean
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val maximum = (data.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(
        modifier = Modifier.fillMaxWidth().testTag("today-discipline-week-bars"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        labels.forEachIndexed { index, name ->
            val known = data.getOrElse(index) { 0.0 }
            val present = presence.getOrElse(index) { 0 } > 0
            val fraction = when {
                known > 0 -> (known / maximum).toFloat().coerceIn(.06f, 1f)
                present -> .07f
                else -> .025f
            }
            val height by animateFloatAsState(
                if (entered) fraction else 0f,
                animationSpec = if (reduceMotion) snap() else spring(
                    dampingRatio = TracksideLiquidMotion.DAMPING,
                    stiffness = TracksideLiquidMotion.STIFFNESS
                ),
                label = "today-visual-bar-$index"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(29.dp).height(76.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier.width(20.dp).height((height * 72f).dp)
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                            .background(if (present) Brush.verticalGradient(
                                listOf(tint, tint.copy(alpha = .52f))
                            ) else Brush.verticalGradient(
                                listOf(Color(0xFF383F42), Color(0xFF25292B))
                            ))
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(name, color = DisciplineLook.subdued, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun DisciplineTrendLine(
    values: List<Double?>,
    tint: Color,
    reduceMotion: Boolean,
    selected: Int? = null
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val peak = values.filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val reveal by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(440),
        label = "today-trend-reveal"
    )
    Canvas(
        Modifier.fillMaxWidth().height(92.dp)
            .semantics {
                contentDescription = "Four-week recorded totals: " +
                    values.joinToString(", ") { it?.toInt()?.toString() ?: "unavailable" }
            }
            .testTag("today-discipline-trend")
    ) {
        val inset = if (selected == null) 10.dp.toPx() else size.width / (values.size * 2f)
        val w = (size.width - inset * 2).coerceAtLeast(1f)
        val h = (size.height - inset * 2).coerceAtLeast(1f)
        val points = values.mapIndexed { i, value ->
            val x = inset + i * w / (values.size - 1).coerceAtLeast(1)
            val y = size.height - inset -
                ((value ?: 0.0) / peak).toFloat().coerceIn(0f, 1f) * h * reveal
            Offset(x, y)
        }
        drawLine(Color(0xFF303438),
            Offset(inset, size.height - inset),
            Offset(size.width - inset, size.height - inset),
            strokeWidth = 1.dp.toPx())
        val line = Path()
        val available = values.mapIndexedNotNull { index, value ->
            if (value == null) null else points[index]
        }
        if (available.size > 1) {
            // No connecting line across missing weeks: an absence is not zero.
            points.forEachIndexed { i, point ->
                if (values[i] != null) {
                    if (i == 0 || values[i - 1] == null) line.moveTo(point.x, point.y)
                    else line.lineTo(point.x, point.y)
                }
            }
            drawPath(line, tint, style = Stroke(width = 3.dp.toPx(),
                cap = StrokeCap.Round))
        }
        values.forEachIndexed { index, value ->
            if (index == selected) {
                val x = points[index].x
                drawLine(tint.copy(alpha = .25f), Offset(x, 0f), Offset(x, size.height))
                if (value != null) drawCircle(tint.copy(alpha = .3f), 9.dp.toPx(), points[index])
            }
            if (value != null) drawCircle(tint, 4.dp.toPx(), points[index])
        }
    }
}
@Composable
private fun RunningWeekReview(
    reviews: List<RunningWeekCoach>,
    plans: List<PlanSnapshot>,
    today: LocalDate
) {
    val current = reviews.lastOrNull() ?: return
    val week = current.week
    val known = week.sessions - (current.types[WeeklyRunType.UNKNOWN] ?: 0)
    if (week.sessions > 0) DisciplinePanel {
        RunningReviewTitle("Training breakdown")
        Text("$known of ${week.sessions} runs classified", color = DisciplineLook.subdued, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            WeeklyRunType.entries.forEach { type ->
                val count = current.types[type] ?: 0
                if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(runTypeColor(type)))
            }
        }
        WeeklyRunType.entries.forEach { type ->
            val count = current.types[type] ?: 0
            if (count > 0 || type in listOf(WeeklyRunType.EASY, WeeklyRunType.LONG, WeeklyRunType.QUALITY)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(runTypeColor(type), CircleShape))
                    Text(type.label, color = DisciplineLook.ink, modifier = Modifier.weight(1f))
                    Text("$count", color = DisciplineLook.subdued)
                }
            }
        }
        current.qualityTypes.takeIf { it.isNotEmpty() }?.let { types ->
            Text(types.entries.joinToString(" · ") { "${it.key} ${it.value}" },
                color = DisciplineLook.subdued, fontSize = 13.sp)
        }
    }

    TrainingWeeksChart(reviews, plans, today)
    if (week.sessions > 0) DisciplinePanel { RunningCoachSummary(current) }

    val complete = reviews.filter { it.week.start.plusDays(6).isBefore(today) }
    val recent = complete.takeLast(3)
    val prior = complete.dropLast(3).takeLast(3)
    if (recent.size == 3 && prior.size == 3 && (recent + prior).all { it.week.sessions > 0 }) {
        val six = prior + recent
        DisciplinePanel {
            RunningReviewTitle("Training progression")
            Text("Latest 3 completed weeks vs the previous 3", color = DisciplineLook.subdued, fontSize = 13.sp)
            val distanceChange = if (six.all { it.completeDistance != null })
                runningPercentChange(recent.map { it.completeDistance!! }.average(),
                    prior.map { it.completeDistance!! }.average()) else null
            val dayChange = recent.map { it.week.activeDays }.average() -
                prior.map { it.week.activeDays }.average()
            Text(when {
                distanceChange != null && distanceChange > 10 && dayChange <= 0 ->
                    "You covered more ground without adding running days. Open Distance to see where the extra kilometers came from."
                distanceChange != null && distanceChange < -10 ->
                    "You covered less distance in the latest block. Check whether the drop was planned."
                dayChange > .3 -> "You ran on more days in the latest block. Open Days run to see how that changed week to week."
                else -> "Open a metric to see how the six weeks fit together."
            }, color = DisciplineLook.ink, fontSize = 13.sp)
            val lighterPlanWeeks = recent.mapNotNull { runningPlanWeekLabel(it.week.start, plans) }
                .filter { it in setOf("Plan · Taper", "Plan · Deload", "Plan · Recovery") }.distinct()
            if (lighterPlanWeeks.isNotEmpty()) {
                Text("Recent plan context: ${lighterPlanWeeks.joinToString { it.substringAfter(" · ") }}. " +
                    "A lighter week may be part of the plan.", color = DisciplineLook.subdued, fontSize = 13.sp)
            }
            RunningProgressionRow("Days run per week", six.map { it.week.activeDays.toDouble() }) {
                String.format(Locale.getDefault(), "%.1f days", it)
            }
            if (six.all { it.completeDistance != null }) {
                RunningProgressionRow("Distance per week", six.map { it.completeDistance!! }, ::runningKm)
                if (six.all { it.longestMeters != null }) {
                    RunningProgressionRow("Longest run each week", six.map { it.longestMeters!! }, ::runningKm)
                }
            }
            if (six.all { WeeklyRunType.UNKNOWN !in it.types } && six.any { WeeklyRunType.QUALITY in it.types }) {
                RunningProgressionRow("Quality runs per week", six.map { (it.types[WeeklyRunType.QUALITY] ?: 0).toDouble() }) {
                    String.format(Locale.getDefault(), "%.1f runs", it)
                }
            }
        }
    }
}

@Composable
private fun RunningCoachSummary(current: RunningWeekCoach) {
    val week = current.week
    val known = week.sessions - (current.types[WeeklyRunType.UNKNOWN] ?: 0)
    RunningReviewTitle("Coach insights")
    val dominant = current.mostPerformed?.label?.lowercase(Locale.getDefault())
    val finding = CoachingFinding(
        observation = when {
            known == 0 -> "You logged ${week.sessions} ${if (week.sessions == 1) "run" else "runs"}."
            dominant == null -> "Your classified runs covered a mix of workout types."
            else -> "Of the runs I could classify, $dominant came up most often."
        },
        interpretation = if (known == 0) "The recordings don’t show what kind of running you did yet." else "",
        evidence = listOf("$known of ${week.sessions} runs classified."),
        recommendation = when {
            known < week.sessions -> "Review any unclear workout types in Activity Analysis. That gives you a better picture of how much easy and harder running you did."
            current.types[WeeklyRunType.QUALITY] != null -> "Think back to the last hard session. If you had to push earlier than planned, try a steadier start next time."
            else -> "Let the next run’s purpose set the pace. If it’s an easy day, aim for an effort that lets you finish a full sentence."
        },
        confidence = if (known < week.sessions) "Some workout types are unknown." else "Every saved run has a workout type."
    )
    Text(listOf(finding.observation, finding.interpretation).filter { it.isNotBlank() }.joinToString(" "),
        color = DisciplineLook.ink)
    val positives = listOfNotNull(
        if (week.activeDays > 1) "You spread your running across ${week.activeDays} days." else null,
        if (known == week.sessions && current.types[WeeklyRunType.EASY] != null &&
            current.types[WeeklyRunType.QUALITY] != null)
            "You made room for easy runs as well as harder sessions." else null
    )
    if (positives.isNotEmpty()) {
        Text("What went well", color = DisciplineLook.lime, fontWeight = FontWeight.SemiBold)
        positives.forEach { Text(it, color = DisciplineLook.subdued) }
    }
    if (week.missingDistance > 0) {
        Text("Some runs are missing distance. Check those recordings before using this week’s total to plan next week.",
            color = DisciplineLook.subdued)
    }
    Text("Next focus", color = DisciplineLook.lime, fontWeight = FontWeight.SemiBold)
    Text(finding.recommendation, color = DisciplineLook.ink)
}

@Composable
private fun RunningProgressionRow(label: String, values: List<Double>, format: (Double) -> String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val earlier = values.take(3).average()
    val latest = values.takeLast(3).average()
    val change = runningPercentChange(latest, earlier)
    val peak = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(onClick = { expanded = !expanded }, shape = RoundedCornerShape(12.dp),
            color = Color(0xFF202522)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = DisciplineLook.ink, fontWeight = FontWeight.SemiBold)
                    Text("${format(earlier)} → ${format(latest)}", color = DisciplineLook.subdued, fontSize = 13.sp)
                }
                Text(listOfNotNull(change?.let(::runningPercentLabel), if (expanded) "−" else "+")
                    .joinToString("  "), color = DisciplineLook.lime)
            }
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { index, value ->
                    Box(Modifier.weight(1f).height((64 * value / peak).dp)
                        .background(if (index < 3) Color(0xFF779557) else DisciplineLook.lime,
                            RoundedCornerShape(4.dp))
                        .semantics { contentDescription = "Week ${index + 1}: ${format(value)}" })
                }
            }
            Text("Previous 3: ${values.take(3).joinToString(" · ", transform = format)}",
                color = DisciplineLook.subdued, fontSize = 12.sp)
            Text("Latest 3: ${values.takeLast(3).joinToString(" · ", transform = format)}",
                color = DisciplineLook.subdued, fontSize = 12.sp)
            Text(when {
                change == null -> "There’s no earlier baseline for a percentage comparison."
                kotlin.math.abs(change) <= 10 -> "The averages stayed close. Check the individual weeks for variation."
                change < 0 -> "The average came down. Compare the lighter weeks with what your plan called for."
                values.zipWithNext().any { (from, to) -> from > 0 && to > from * 1.25 } ->
                    "Some of the increase came in a big step. Check how that week felt before adding more."
                else -> "The average rose without a large jump between these weeks. Let how the runs felt guide what you add next."
            }, color = DisciplineLook.ink, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RunningReviewTitle(title: String) {
    Text(title, color = DisciplineLook.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

private fun runTypeColor(type: WeeklyRunType): Color = when (type) {
    WeeklyRunType.EASY -> DisciplineLook.lime
    WeeklyRunType.LONG -> Color(0xFF8ADECB)
    WeeklyRunType.QUALITY -> Color(0xFFFFBE7A)
    WeeklyRunType.OTHER -> Color(0xFFC8A8FF)
    WeeklyRunType.UNKNOWN -> Color(0xFF616B70)
}
private enum class WeeksMetric(val label: String) {
    DISTANCE("Distance"), RUNS("Runs"), LONG("Long run"), QUALITY("Quality");

    fun value(review: RunningWeekCoach): Double? = when (this) {
        DISTANCE -> review.week.recordedDistanceM
        RUNS -> review.week.sessions.toDouble()
        LONG -> review.longestMeters
        QUALITY -> if (WeeklyRunType.UNKNOWN in review.types) null
            else (review.types[WeeklyRunType.QUALITY] ?: 0).toDouble()
    }
    fun format(value: Double): String = when (this) {
        DISTANCE, LONG -> runningKm(value)
        RUNS, QUALITY -> "${value.toInt()} ${if (value == 1.0) "run" else "runs"}"
    }
    fun complete(review: RunningWeekCoach): Boolean =
        value(review) != null && (this !in listOf(DISTANCE, LONG) || review.week.missingDistance == 0)
}

@Composable
private fun TrainingWeeksChart(reviews: List<RunningWeekCoach>, plans: List<PlanSnapshot>, today: LocalDate) {
    var metricName by rememberSaveable { mutableStateOf(WeeksMetric.DISTANCE.name) }
    var selectedIndex by rememberSaveable(reviews.last().week.start) { mutableStateOf(reviews.lastIndex) }
    var details by rememberSaveable(reviews.last().week.start) { mutableStateOf(false) }
    val metric = WeeksMetric.valueOf(metricName)
    val selected = reviews[selectedIndex]
    val values = reviews.map(metric::value)
    val date = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val completed = selected.week.start.plusDays(6).isBefore(today)
    val previous = reviews.getOrNull(selectedIndex - 1)
    val status = runningPlanWeekLabel(selected.week.start, plans)
        ?: runningWeekVolumeLabel(selected, previous, today)
        ?: if (today in selected.week.start..selected.week.start.plusDays(6)) "Week in progress" else null
    val comparison = if (completed && previous != null && metric.complete(selected) && metric.complete(previous))
        runningPercentChange(values[selectedIndex]!!, values[selectedIndex - 1]!!) else null
    val latestFour = reviews.filter { it.week.start.plusDays(6).isBefore(today) }.takeLast(4)
    val average = latestFour.takeIf { it.size == 4 && it.all { week -> week.completeDistance != null } }
        ?.map { it.completeDistance!! }?.average()
    val highest = reviews.mapNotNull { it.completeDistance }.maxOrNull()
    DisciplinePanel {
        RunningReviewTitle("Training weeks")
        Text(if (metric == WeeksMetric.DISTANCE) "8 weeks of recorded distance"
            else "Last 8 weeks · ${metric.label}", color = DisciplineLook.subdued, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WeeksMetric.entries.forEach { option ->
                val active = metric == option
                Box(Modifier.weight(1f).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(9.dp)).background(if (active) DisciplineLook.lime else Color(0xFF232728))
                    .selectable(active, role = Role.Tab) { metricName = option.name }
                    .testTag("training-weeks-metric-${option.name.lowercase(Locale.ROOT)}")
                    .padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(option.label, color = if (active) Color.Black else DisciplineLook.ink,
                        fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            DisciplineTrendLine(values, DisciplineLook.lime, reduceMotion = true, selected = selectedIndex)
            Row(Modifier.matchParentSize().selectableGroup()) {
                reviews.forEachIndexed { index, review ->
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .selectable(index == selectedIndex, role = Role.Tab) {
                            selectedIndex = index
                            details = true
                        }.testTag("training-weeks-point-$index")
                        .semantics {
                            contentDescription = "Week of ${review.week.start.format(date)}, " +
                                (values[index]?.let(metric::format) ?: "unavailable")
                        })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(reviews.first().week.start.format(date), color = DisciplineLook.subdued, fontSize = 12.sp)
            Text(reviews.last().week.start.format(date), color = DisciplineLook.subdued, fontSize = 12.sp)
        }
        Text("Week of ${selected.week.start.format(date)}", color = DisciplineLook.ink, fontWeight = FontWeight.SemiBold)
        Text(values[selectedIndex]?.let(metric::format) ?: if (metric == WeeksMetric.QUALITY)
            "Workout types unavailable" else "No recorded value", color = DisciplineLook.lime, fontSize = 24.sp)
        if (status != null) Text(status, color = DisciplineLook.subdued)
        if (values[selectedIndex] != null && !metric.complete(selected))
            Text("Partial recording", color = DisciplineLook.subdued, fontSize = 12.sp)
        comparison?.let { Text("${runningPercentLabel(it)} vs the previous week", color = DisciplineLook.subdued) }
        HorizontalDivider(color = DisciplineLook.border)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("4-week distance avg.", color = DisciplineLook.subdued, fontSize = 12.sp)
                Text(average?.let(::runningKm) ?: "—", color = DisciplineLook.ink, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("Highest saved week", color = DisciplineLook.subdued, fontSize = 12.sp)
                Text(highest?.let(::runningKm) ?: "—", color = DisciplineLook.ink, fontWeight = FontWeight.Bold)
            }
        }
        TextButton(onClick = { details = !details }, modifier = Modifier.testTag("training-weeks-details")) {
            Text(if (details) "Hide week details" else "Review this week", color = DisciplineLook.lime)
        }
        if (details) {
            if (selected.week.sessions == 0) Text("No runs saved for this week.", color = DisciplineLook.subdued)
            else {
                Text(selected.types.entries.joinToString(" · ") { "${it.value} ${it.key.label.lowercase(Locale.ROOT)}" },
                    color = DisciplineLook.subdued)
                RunningCoachSummary(selected)
            }
        }
    }
}

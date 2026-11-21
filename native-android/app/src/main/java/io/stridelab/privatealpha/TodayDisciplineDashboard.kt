package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Art-directed AMOLED Today Discipline token palette. */
private object DisciplineLook {
    val black = Color(0xFF000000)
    val darkOnAccent = Color(0xFF090B09)
    val panel = StrideLabVisualPalette.surface
    val panelRaised = StrideLabVisualPalette.surfaceRaised
    val border = StrideLabVisualPalette.border
    val ink = StrideLabVisualPalette.ink
    val subdued = StrideLabVisualPalette.secondary
    val muted = StrideLabVisualPalette.muted
    val lime = StrideLabVisualPalette.lime

    fun accent(d: TodayDiscipline): Color = when (d) {
        TodayDiscipline.RUNNING -> lime
        TodayDiscipline.STRENGTH -> StrideLabVisualPalette.strengthCoral
        TodayDiscipline.CALISTHENICS -> StrideLabVisualPalette.calisthenicsViolet
        TodayDiscipline.CONDITIONING -> StrideLabVisualPalette.conditioningSky
    }

    fun accentForKey(key: String): Color = when (key) {
        "running" -> lime
        "strength" -> StrideLabVisualPalette.strengthCoral
        "calisthenics" -> StrideLabVisualPalette.calisthenicsViolet
        "conditioning" -> StrideLabVisualPalette.conditioningSky
        else -> lime
    }
}

/**
 * Elastic Stretching Top Pill Bar for Today.
 * Each unlocked discipline tab shares equal weight (`Modifier.weight(1f)`) so the bar
 * stretches dynamically across the full screen width as consistent training unlocks new pages.
 */
@Composable
internal fun TodayDisciplineTabs(
    available: List<TodayDiscipline>,
    selectedKey: String,
    reduceMotion: Boolean,
    onSelect: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val options = remember(available) {
        listOf("all" to "All") + available.map { it.key to it.title }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (reduceMotion) snap() else spring(
                    dampingRatio = TracksideLiquidMotion.DAMPING,
                    stiffness = TracksideLiquidMotion.STIFFNESS
                )
            )
            .testTag("today-discipline-pages"),
        color = DisciplineLook.panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, DisciplineLook.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (key, title) ->
                val active = key == selectedKey
                val itemAccent = DisciplineLook.accentForKey(key)
                val background by animateColorAsState(
                    if (active) itemAccent else Color.Transparent,
                    animationSpec = tween(if (reduceMotion) 0 else 210),
                    label = "today-page-pill-$key"
                )
                val pillScale by animateFloatAsState(
                    if (active) 1f else .98f,
                    animationSpec = if (reduceMotion) snap() else spring(
                        dampingRatio = TracksideLiquidMotion.DAMPING,
                        stiffness = TracksideLiquidMotion.STIFFNESS
                    ),
                    label = "today-page-spring-$key"
                )
                val ink by animateColorAsState(
                    if (active) DisciplineLook.darkOnAccent else DisciplineLook.ink,
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .graphicsLayer {
                            scaleX = pillScale
                            scaleY = pillScale
                        }
                        .testTag("today-page-$key")
                        .semantics { selected = active }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (key != "all" && !active) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(itemAccent)
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = if (options.size >= 4 && title == "Conditioning") "Cond"
                            else if (options.size >= 4 && title == "Calisthenics") "Cali"
                            else title,
                            color = ink,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisciplinePanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DisciplineLook.panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, DisciplineLook.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (accent != null) Modifier.background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.06f), Color.Transparent)
                        )
                    ) else Modifier
                )
                .padding(16.dp),
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
        Modifier
            .fillMaxWidth()
            .testTag("today-discipline-${discipline.key}"),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    discipline.title,
                    color = DisciplineLook.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    dateRange.uppercase(Locale.getDefault()),
                    color = DisciplineLook.muted,
                    fontSize = 10.5.sp,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = .15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(7.dp).background(tint, CircleShape))
                    Text(
                        "${week.sessions} THIS WK",
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        // Hero Volume / Time Panel
        DisciplinePanel(accent = tint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showDistance) "WEEKLY DISTANCE" else "RECORDED TRAINING TIME",
                    Modifier.weight(1f),
                    color = DisciplineLook.muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${week.activeDays} active day${if (week.activeDays == 1) "" else "s"}",
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                primary,
                color = tint,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                modifier = Modifier.testTag("today-discipline-primary")
            )
            DisciplineWeekBars(
                data = if (showDistance) week.distanceByDay else week.secondsByDay,
                presence = week.sessionsByDay,
                tint = tint,
                reduceMotion = reduceMotion
            )
            if (week.sessions == 0) {
                Text(
                    "No recorded ${discipline.title.lowercase(Locale.ROOT)} this week",
                    color = DisciplineLook.subdued,
                    fontSize = 12.sp
                )
            } else if (week.missingDuration > 0 || (showDistance && week.missingDistance > 0)) {
                Text(
                    "Partial totals · some sessions have missing metrics",
                    color = DisciplineLook.subdued,
                    fontSize = 11.sp
                )
            }
        }

        val completedDates = trend.flatMap { w ->
            w.sessionsByDay.mapIndexedNotNull { idx, count ->
                if (count > 0) w.start.plusDays(idx.toLong()) else null
            }
        }.toSet()
        ConsistencyHeatmapStrip(
            completedDates = completedDates,
            plannedDates = emptySet(),
            asOf = week.start.plusDays(6)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DisciplineMetric("TRAINING DAYS", "${week.activeDays}", tint, Modifier.weight(1f))
            DisciplineMetric("SESSIONS", "${week.sessions}", tint, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DisciplineMetric(
                "RECORDED TIME",
                week.recordedSeconds?.let(::time) ?: "—",
                tint,
                Modifier.weight(1f)
            )
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
                tint,
                Modifier.weight(1f)
            )
        }

        DisciplinePanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TRAINING EFFORT",
                    color = DisciplineLook.muted,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    fontSize = 10.sp
                )
                Text("DURATION", color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
            }
            DisciplineWeekBars(week.secondsByDay, week.sessionsByDay, tint, reduceMotion)
            Text(
                "Recorded duration by day",
                color = DisciplineLook.subdued,
                fontSize = 11.sp
            )
        }

        DisciplinePanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "4-WEEK TREND",
                    color = DisciplineLook.muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Bold
                )
                val distanceTrend = discipline == TodayDiscipline.RUNNING &&
                    trend.any { it.recordedDistanceM != null }
                Text(
                    if (distanceTrend) "KM / WEEK" else "MIN / WEEK",
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            val distanceTrend = discipline == TodayDiscipline.RUNNING &&
                trend.any { it.recordedDistanceM != null }
            val values = trend.map {
                if (distanceTrend) it.recordedDistanceM else
                    it.recordedSeconds?.div(60.0)
            }
            DisciplineTrendLine(values, tint, reduceMotion)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                trend.forEachIndexed { index, item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            item.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                            color = DisciplineLook.muted,
                            fontSize = 10.sp
                        )
                        Text(
                            values[index]?.let {
                                if (distanceTrend) String.format(Locale.getDefault(), "%.1f km", it / 1000)
                                else "${it.toLong()}m"
                            } ?: "—",
                            color = if (index == 3) tint else DisciplineLook.ink,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        val recent = (
            week.sourceActivities.map { act ->
                val distKm = act.distanceM?.takeIf { it.isFinite() && it > 0 }?.let {
                    String.format(Locale.US, " · %.1f km", it / 1000.0)
                }.orEmpty()
                Triple(
                    act.startedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                    act.title + distKm,
                    act.id
                )
            } + week.nativeWorkouts.map {
                val distKm = it.distanceM?.takeIf { d -> d.isFinite() && d > 0 }?.let { d ->
                    String.format(Locale.US, " · %.1f km", d / 1000.0)
                }.orEmpty()
                Triple(it.date, "Completed workout$distKm", it.planId + "|" + it.sessionId)
            }
        ).sortedByDescending { it.first }.take(4)
        if (recent.isNotEmpty()) {
            DisciplinePanel {
                Text(
                    "SAVED SESSIONS",
                    color = DisciplineLook.muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Bold
                )
                recent.forEach { (date, label, id) ->
                    Surface(
                        onClick = {
                            val source = week.sourceActivities.firstOrNull { it.id == id }
                            if (source != null) onOpenActivity(source.id)
                            else week.nativeWorkouts.firstOrNull {
                                it.planId + "|" + it.sessionId == id
                            }?.let { onOpenWorkout(it.planId, it.sessionId) }
                        },
                        color = DisciplineLook.panelRaised,
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, DisciplineLook.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).background(tint, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                label,
                                color = DisciplineLook.ink,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                                color = DisciplineLook.subdued,
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("›", color = DisciplineLook.muted, fontSize = 16.sp)
                        }
                    }
                }
            }
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
        Text(
            label,
            color = DisciplineLook.muted,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            color = accent,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today-discipline-week-bars"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        labels.forEachIndexed { index, name ->
            val known = data.getOrElse(index) { 0.0 }
            val present = presence.getOrElse(index) { 0 } > 0
            val fraction = when {
                known > 0 -> (known / maximum).toFloat().coerceIn(.10f, 1f)
                present -> .12f
                else -> .04f
            }
            val height by animateFloatAsState(
                if (entered) fraction else 0f,
                animationSpec = if (reduceMotion) snap() else spring(
                    dampingRatio = TracksideLiquidMotion.DAMPING,
                    stiffness = TracksideLiquidMotion.STIFFNESS
                ),
                label = "today-visual-bar-$index"
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(78.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.74f)
                            .height((height * 74f).dp)
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(
                                if (present) StrideLabVisualPalette.barGradient(tint)
                                else Brush.verticalGradient(
                                    listOf(DisciplineLook.border, DisciplineLook.panelRaised)
                                )
                            )
                    ) {
                        if (present) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .align(Alignment.TopCenter)
                                    .background(tint)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    name,
                    color = if (present) DisciplineLook.ink else DisciplineLook.muted,
                    fontSize = 10.sp,
                    fontWeight = if (present) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DisciplineTrendLine(
    values: List<Double?>,
    tint: Color,
    reduceMotion: Boolean
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
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .semantics {
                contentDescription = "Four-week recorded totals: " +
                    values.joinToString(", ") { it?.toInt()?.toString() ?: "unavailable" }
            }
            .testTag("today-discipline-trend")
    ) {
        val inset = 10.dp.toPx()
        val w = (size.width - inset * 2).coerceAtLeast(1f)
        val h = (size.height - inset * 2).coerceAtLeast(1f)
        val points = values.mapIndexed { i, value ->
            val x = inset + i * w / 3f
            val y = size.height - inset -
                ((value ?: 0.0) / peak).toFloat().coerceIn(0f, 1f) * h * reveal
            Offset(x, y)
        }
        drawLine(
            DisciplineLook.border,
            Offset(inset, size.height - inset),
            Offset(size.width - inset, size.height - inset),
            strokeWidth = 1.dp.toPx()
        )
        val line = Path()
        val available = values.mapIndexedNotNull { index, value ->
            if (value == null) null else points[index]
        }
        if (available.size > 1) {
            points.forEachIndexed { i, point ->
                if (values[i] != null) {
                    if (i == 0 || values[i - 1] == null) line.moveTo(point.x, point.y)
                    else line.lineTo(point.x, point.y)
                }
            }
            drawPath(
                line,
                tint,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        values.forEachIndexed { index, value ->
            if (value != null) {
                drawCircle(
                    color = tint.copy(alpha = 0.25f),
                    radius = 7.dp.toPx(),
                    center = points[index]
                )
                drawCircle(
                    color = tint,
                    radius = 4.dp.toPx(),
                    center = points[index]
                )
            }
        }
    }
}

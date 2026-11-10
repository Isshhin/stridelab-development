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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
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
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .testTag("today-discipline-pages"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf("all" to "All") + available.map { it.key to it.title }
        options.forEach { (key, title) ->
            val active = key == selectedKey
            val tone = available.firstOrNull { it.key == key }
                ?.let(DisciplineLook::accent) ?: DisciplineLook.lime
            val background by animateColorAsState(
                if (active) tone else DisciplineLook.panel,
                animationSpec = tween(if (reduceMotion) 0 else 210),
                label = "today-page-pill-$key"
            )
            val ink by animateColorAsState(
                if (active) DisciplineLook.black else DisciplineLook.ink,
                animationSpec = tween(if (reduceMotion) 0 else 210),
                label = "today-page-label-$key"
            )
            Surface(
                onClick = { onSelect(key) },
                color = background,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, if (active) tone else DisciplineLook.border),
                modifier = Modifier.height(38.dp)
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
            DisciplineMetric("ACTIVE TIME",
                week.recordedSeconds?.let(::time) ?: "—", tint, Modifier.weight(1f))
            DisciplineMetric(
                if (discipline == TodayDiscipline.RUNNING) "AVG RUN HR" else "DISTANCE",
                if (discipline == TodayDiscipline.RUNNING)
                    week.averageRecordedHr?.let { "${it.toInt()} bpm" } ?: "—"
                else week.recordedDistanceM?.let(::km) ?: "—",
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

        DisciplinePanel {
            Text("4-week trend", color = DisciplineLook.ink,
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        Text(item.start.format(DateTimeFormatter.ofPattern("d MMM",
                            Locale.getDefault())), color = DisciplineLook.subdued,
                            fontSize = 10.sp)
                        Text(values[index]?.let {
                            if (distanceTrend) String.format(Locale.getDefault(), "%.1f",
                                it / 1000) else "${it.toLong()}"
                        } ?: "—", color = if (index == 3) tint else DisciplineLook.ink,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(if (distanceTrend) "Recorded kilometres each week"
                else "Recorded minutes each week",
                color = DisciplineLook.subdued, fontSize = 11.sp)
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
                Text("Saved sessions", color = DisciplineLook.ink,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                recent.forEach { (date, label, id) ->
                    Surface(
                        onClick = {
                            val source = week.sourceActivities.firstOrNull { it.id == id }
                            if (source != null) onOpenActivity(source.id)
                            else week.nativeWorkouts.firstOrNull {
                                it.planId + "|" + it.sessionId == id
                            }?.let { onOpenWorkout(it.planId, it.sessionId) }
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
                            Text(label, color = DisciplineLook.ink, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
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
                fraction,
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
    reduceMotion: Boolean
) {
    val peak = values.filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val reveal by animateFloatAsState(
        targetValue = 1f,
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
        val inset = 10.dp.toPx()
        val w = (size.width - inset * 2).coerceAtLeast(1f)
        val h = (size.height - inset * 2).coerceAtLeast(1f)
        val points = values.mapIndexed { i, value ->
            val x = inset + i * w / 3f
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
            if (value != null) drawCircle(
                color = tint, radius = 4.dp.toPx(),
                center = points[index]
            )
        }
    }
}

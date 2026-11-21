package io.stridelab.privatealpha

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
 * Elastic Stretching Top Pill Bar for Today with signature StrideLab liquid-water motion.
 * Each unlocked discipline tab shares equal weight (`Modifier.weight(1f)`) and a single
 * fluid droplet capsule glides & stretches smoothly between tabs without checkboxes or radios.
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
    val selectedIndex = options.indexOfFirst { it.first == selectedKey }.coerceAtLeast(0)
    val activeAccent by animateColorAsState(
        targetValue = DisciplineLook.accentForKey(selectedKey),
        animationSpec = tween(if (reduceMotion) 0 else 220),
        label = "today-tab-accent"
    )
    val capsulePos = remember(options.size) { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, options.size, reduceMotion) {
        val target = selectedIndex.toFloat()
        if (reduceMotion) {
            capsulePos.snapTo(target)
        } else {
            capsulePos.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = TracksideLiquidMotion.DAMPING,
                    stiffness = TracksideLiquidMotion.STIFFNESS
                )
            )
        }
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
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DisciplineLook.border)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = options.size.coerceAtLeast(1)
                val cellWidth = size.width / count
                val currentX = capsulePos.value * cellWidth
                val targetX = selectedIndex * cellWidth
                val pressure = TracksideLiquidMotion.pressure(
                    capsulePos.value,
                    selectedIndex.toFloat(),
                    1f
                )
                val movingRight = targetX >= currentX
                val wakeTail = pressure * 12.dp.toPx()
                val h = size.height.coerceAtLeast(1f)
                val radius = CornerRadius(14.dp.toPx(), 14.dp.toPx())

                if (wakeTail > 0.5f) {
                    val wakeLeft = if (movingRight) (currentX - wakeTail).coerceAtLeast(0f) else currentX + cellWidth
                    drawRoundRect(
                        color = activeAccent.copy(alpha = 0.24f * pressure),
                        topLeft = Offset(wakeLeft, h * 0.14f),
                        size = Size((wakeTail + 6.dp.toPx()).coerceAtMost(size.width - wakeLeft), h * 0.72f),
                        cornerRadius = radius
                    )
                }
                val stretchPx = pressure * 7.dp.toPx()
                val left = (currentX - if (!movingRight) stretchPx else 0f).coerceIn(0f, (size.width - cellWidth).coerceAtLeast(0f))
                val width = (cellWidth + stretchPx).coerceAtMost(size.width - left)
                drawRoundRect(
                    color = activeAccent,
                    topLeft = Offset(left, 0f),
                    size = Size(width, h),
                    cornerRadius = radius
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { (key, title) ->
                    val active = key == selectedKey
                    val itemAccent = DisciplineLook.accentForKey(key)
                    val ink by animateColorAsState(
                        if (active) DisciplineLook.darkOnAccent else DisciplineLook.ink,
                        animationSpec = tween(if (reduceMotion) 0 else 170),
                        label = "today-page-label-$key"
                    )
                    val textScale by animateFloatAsState(
                        if (active) 1f else 0.96f,
                        animationSpec = if (reduceMotion) snap() else spring(
                            dampingRatio = TracksideLiquidMotion.DAMPING,
                            stiffness = TracksideLiquidMotion.STIFFNESS
                        ),
                        label = "today-page-text-scale-$key"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .selectable(selected = active, role = Role.Tab) {
                                if (key != selectedKey) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(key)
                                }
                            }
                            .testTag("today-page-$key")
                            .semantics { selected = active },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .graphicsLayer {
                                    scaleX = textScale
                                    scaleY = textScale
                                },
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
}

/**
 * Reusable liquid-water segmented control with palette highlighting and zero checkboxes/radios.
 */
@Composable
internal fun StrideLabLiquidSegmentedControl(
    labels: List<Pair<String, String>>,
    selectedKey: String,
    accent: Color = DisciplineLook.lime,
    reduceMotion: Boolean = false,
    testTagPrefix: String,
    onSelect: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val selectedIndex = labels.indexOfFirst { it.first == selectedKey }.coerceAtLeast(0)
    val capsulePos = remember(labels.size) { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, labels.size, reduceMotion) {
        val target = selectedIndex.toFloat()
        if (reduceMotion) capsulePos.snapTo(target)
        else capsulePos.animateTo(
            target,
            animationSpec = spring(
                dampingRatio = TracksideLiquidMotion.DAMPING,
                stiffness = TracksideLiquidMotion.STIFFNESS
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DisciplineLook.panelRaised)
            .padding(3.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val count = labels.size.coerceAtLeast(1)
            val cellWidth = size.width / count
            val currentX = capsulePos.value * cellWidth
            val targetX = selectedIndex * cellWidth
            val pressure = TracksideLiquidMotion.pressure(capsulePos.value, selectedIndex.toFloat(), 1f)
            val movingRight = targetX >= currentX
            val wake = pressure * 8.dp.toPx()
            val h = size.height.coerceAtLeast(1f)
            val r = CornerRadius(11.dp.toPx(), 11.dp.toPx())
            if (wake > 0.5f) {
                val wakeX = if (movingRight) (currentX - wake).coerceAtLeast(0f) else currentX + cellWidth
                drawRoundRect(
                    color = accent.copy(alpha = 0.22f * pressure),
                    topLeft = Offset(wakeX, h * 0.14f),
                    size = Size((wake + 4.dp.toPx()).coerceAtMost(size.width - wakeX), h * 0.72f),
                    cornerRadius = r
                )
            }
            val stretchPx = pressure * 5.dp.toPx()
            val left = (currentX - if (!movingRight) stretchPx else 0f).coerceIn(0f, (size.width - cellWidth).coerceAtLeast(0f))
            val width = (cellWidth + stretchPx).coerceAtMost(size.width - left)
            drawRoundRect(
                color = accent,
                topLeft = Offset(left, 0f),
                size = Size(width, h),
                cornerRadius = r
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEach { (key, title) ->
                val active = key == selectedKey
                val textColor by animateColorAsState(
                    if (active) DisciplineLook.darkOnAccent else DisciplineLook.ink,
                    animationSpec = tween(if (reduceMotion) 0 else 150),
                    label = "seg-$testTagPrefix-$key"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .selectable(selected = active, role = Role.Tab) {
                            if (key != selectedKey) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(key)
                            }
                        }
                        .testTag("$testTagPrefix-$key")
                        .semantics { selected = active },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
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
    runningReview: List<RunningWeekCoach> = emptyList(),
    plans: List<PlanSnapshot> = emptyList(),
    today: LocalDate = LocalDate.now(),
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

        if (discipline == TodayDiscipline.RUNNING) {
            RunningWeekOverview(
                week = week,
                review = runningReview.lastOrNull(),
                reduceMotion = reduceMotion
            )
        } else {
            // Non-Running Hero Volume / Time Panel
            DisciplinePanel(accent = tint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RECORDED TRAINING TIME",
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
                    data = week.secondsByDay,
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
                } else if (week.missingDuration > 0) {
                    Text(
                        "Partial totals · some sessions have missing metrics",
                        color = DisciplineLook.subdued,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 28-Day Consistency Heatmap across recent weeks
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

        if (discipline == TodayDiscipline.RUNNING) {
            RunningWeekReview(
                reviews = runningReview,
                plans = plans,
                today = today,
                reduceMotion = reduceMotion
            )
        } else {
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
                val timedSessions = week.sessions - week.missingDuration
                DisciplineMetric(
                    "AVG SESSION",
                    if (timedSessions > 0 && week.recordedSeconds != null) time(week.recordedSeconds / timedSessions) else "—",
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
                    Text(
                        "MIN / WEEK",
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                val values = trend.map { it.recordedSeconds?.div(60.0) }
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
                                values[index]?.toLong()?.let { "${it}m" } ?: "—",
                                color = if (index == trend.lastIndex) tint else DisciplineLook.ink,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
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
        if (discipline != TodayDiscipline.RUNNING && recent.isNotEmpty()) {
            DisciplinePanel {
                Text(
                    "SAVED SESSIONS",
                    color = DisciplineLook.muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Bold
                )
                recent.forEach { (date, label, id) ->
                    val source = week.sourceActivities.firstOrNull { it.id == id }
                    val workout = week.nativeWorkouts.firstOrNull { it.planId + "|" + it.sessionId == id }
                    Surface(
                        onClick = {
                            if (source != null) onOpenActivity(source.id)
                            else workout?.let { onOpenWorkout(it.planId, it.sessionId) }
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
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    label,
                                    color = DisciplineLook.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (discipline == TodayDiscipline.RUNNING) {
                                    val meta = listOfNotNull(
                                        (source?.distanceM ?: workout?.distanceM)?.takeIf { it.isFinite() && it >= 0 }?.let(::km),
                                        (source?.durationS ?: workout?.durationSeconds)?.takeIf { it.isFinite() && it >= 0 }?.let(::time),
                                        source?.averageHr?.takeIf { it.isFinite() && it > 0 }?.let { "${it.toInt()} bpm" }
                                    )
                                    if (meta.isNotEmpty()) {
                                        Text(
                                            meta.joinToString(" · "),
                                            color = DisciplineLook.subdued,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
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
private fun RunningWeekOverview(
    week: TodayDisciplineWeek,
    review: RunningWeekCoach?,
    reduceMotion: Boolean
) {
    val haptics = LocalHapticFeedback.current
    var metricKey by rememberSaveable { mutableStateOf("distance") }
    val byDistance = metricKey == "distance"
    var selectedDay by rememberSaveable(week.start.toString()) {
        mutableStateOf(week.sessionsByDay.indexOfLast { it > 0 }.coerceAtLeast(0))
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

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

    DisciplinePanel(accent = DisciplineLook.lime) {
        Text(
            "YOUR WEEK IN RUNNING",
            color = DisciplineLook.muted,
            fontSize = 10.sp,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Your week in running",
            color = DisciplineLook.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp
        )
        StrideLabLiquidSegmentedControl(
            labels = listOf("distance" to "Distance", "time" to "Time"),
            selectedKey = metricKey,
            accent = DisciplineLook.lime,
            reduceMotion = reduceMotion,
            testTagPrefix = "today-running-metric",
            onSelect = { metricKey = it }
        )
        Text(
            total?.let(format) ?: if (week.sessions == 0) "No runs yet" else "—",
            color = DisciplineLook.lime,
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
            modifier = Modifier.testTag("today-discipline-primary")
        )
        Text("$sessions across $days", color = DisciplineLook.subdued, fontSize = 13.5.sp)
        review?.mostPerformed?.let { type ->
            val typeAccent = runTypeColor(type)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(typeAccent.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(Modifier.size(7.dp).background(typeAccent, CircleShape))
                Text(
                    "Most common: ${type.label}",
                    color = typeAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (missing > 0) {
            Text(
                if (total == null) "No $unit saved for these runs."
                else "$missing ${if (missing == 1) "run has" else "runs have"} no $unit recorded.",
                color = DisciplineLook.subdued,
                fontSize = 12.sp
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .testTag("today-discipline-week-bars"),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            values.forEachIndexed { index, value ->
                val date = week.start.plusDays(index.toLong())
                val active = index == selectedDay
                val hasRun = week.sessionsByDay.getOrElse(index) { 0 } > 0
                val rawFrac = if (value > 0) (value / peak).toFloat().coerceIn(0.10f, 1f) else if (hasRun) 0.12f else 0f
                val animatedFrac by animateFloatAsState(
                    targetValue = if (entered) rawFrac else 0f,
                    animationSpec = if (reduceMotion) snap() else spring(
                        dampingRatio = TracksideLiquidMotion.DAMPING,
                        stiffness = TracksideLiquidMotion.STIFFNESS
                    ),
                    label = "running-week-bar-$index"
                )
                val tileBg by animateColorAsState(
                    if (active) StrideLabVisualPalette.limeTint else Color.Transparent,
                    animationSpec = tween(if (reduceMotion) 0 else 170),
                    label = "running-day-tile-$index"
                )
                val tileBorder by animateColorAsState(
                    if (active) DisciplineLook.lime.copy(alpha = 0.65f) else Color.Transparent,
                    animationSpec = tween(if (reduceMotion) 0 else 170),
                    label = "running-day-border-$index"
                )
                Surface(
                    color = tileBg,
                    shape = RoundedCornerShape(12.dp),
                    border = if (active) BorderStroke(1.dp, tileBorder) else null,
                    modifier = Modifier
                        .weight(1f)
                        .selectable(active, role = Role.Tab) {
                            if (selectedDay != index) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDay = index
                            }
                        }
                        .testTag("today-running-day-$index")
                        .semantics { contentDescription = "${date.format(dayLabel)}, ${dayValue(index)}" }
                ) {
                    Column(
                        Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .height(92.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.56f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(DisciplineLook.panelRaised)
                            )
                            if (animatedFrac > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.56f)
                                        .height((92f * animatedFrac).dp)
                                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                        .background(
                                            if (active) StrideLabVisualPalette.barGradient(DisciplineLook.lime)
                                            else StrideLabVisualPalette.barGradient(DisciplineLook.lime, alphaScale = 0.58f)
                                        )
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(2.5.dp)
                                            .align(Alignment.TopCenter)
                                            .background(if (active) DisciplineLook.lime else DisciplineLook.lime.copy(alpha = 0.72f))
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())),
                            color = if (active) DisciplineLook.lime else DisciplineLook.subdued,
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Surface(
            color = DisciplineLook.panelRaised,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DisciplineLook.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    week.start.plusDays(selectedDay.toLong()).format(dayLabel),
                    color = DisciplineLook.ink,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    dayValue(selectedDay),
                    color = DisciplineLook.lime,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("today-running-day-value"),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.End
                )
            }
        }
        week.averageRecordedHr?.let { hr ->
            Text(
                "Average run heart rate: ${hr.toInt()} bpm across ${week.hrCoverage} of ${week.sessions} runs",
                color = DisciplineLook.subdued,
                fontSize = 12.sp
            )
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
    reduceMotion: Boolean,
    selected: Int? = null
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val peak = values.filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val reveal by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(440, easing = FastOutSlowInEasing),
        label = "today-trend-reveal"
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics {
                contentDescription = "Weekly recorded totals: " +
                    values.joinToString(", ") { it?.toInt()?.toString() ?: "unavailable" }
            }
            .testTag("today-discipline-trend")
    ) {
        val inset = if (selected == null) 10.dp.toPx() else size.width / (values.size * 2f).coerceAtLeast(2f)
        val w = (size.width - inset * 2).coerceAtLeast(1f)
        val h = (size.height - inset * 2).coerceAtLeast(1f)
        val points = values.mapIndexed { i, value ->
            val x = inset + i * w / (values.size - 1).coerceAtLeast(1)
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
            if (index == selected) {
                val x = points[index].x
                drawLine(tint.copy(alpha = 0.28f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                if (value != null) drawCircle(tint.copy(alpha = 0.30f), 9.dp.toPx(), points[index])
            }
            if (value != null) drawCircle(tint, 4.dp.toPx(), points[index])
        }
    }
}

@Composable
private fun RunningWeekReview(
    reviews: List<RunningWeekCoach>,
    plans: List<PlanSnapshot>,
    today: LocalDate,
    reduceMotion: Boolean
) {
    val current = reviews.lastOrNull() ?: return
    val week = current.week
    val known = week.sessions - (current.types[WeeklyRunType.UNKNOWN] ?: 0)
    if (week.sessions > 0) {
        DisciplinePanel {
            RunningReviewTitle("Training breakdown")
            Text("$known of ${week.sessions} runs classified", color = DisciplineLook.subdued, fontSize = 13.sp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                WeeklyRunType.entries.forEach { type ->
                    val count = current.types[type] ?: 0
                    if (count > 0) Box(
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .background(runTypeColor(type))
                    )
                }
            }
            WeeklyRunType.entries.forEach { type ->
                val count = current.types[type] ?: 0
                if (count > 0 || type in listOf(WeeklyRunType.EASY, WeeklyRunType.LONG, WeeklyRunType.QUALITY)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).background(runTypeColor(type), CircleShape))
                        Text(type.label, color = DisciplineLook.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("$count", color = DisciplineLook.subdued, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            current.qualityTypes.takeIf { it.isNotEmpty() }?.let { types ->
                Text(
                    types.entries.joinToString(" · ") { "${it.key} ${it.value}" },
                    color = DisciplineLook.subdued,
                    fontSize = 12.5.sp
                )
            }
        }
    }

    TrainingWeeksChart(reviews, plans, today, reduceMotion)

    if (week.sessions > 0) {
        DisciplinePanel { RunningCoachSummary(current) }
    }

    val complete = reviews.filter { it.week.start.plusDays(6).isBefore(today) }
    val recent = complete.takeLast(3)
    val prior = complete.dropLast(3).takeLast(3)
    if (recent.size == 3 && prior.size == 3 && (recent + prior).all { it.week.sessions > 0 }) {
        RunningProgressionPanel(prior = prior, recent = recent, plans = plans, reduceMotion = reduceMotion)
    }
}

private enum class ProgressionTab(val key: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    DAYS("days", "Days Run"),
    DISTANCE("distance", "Distance"),
    LONG_RUN("long_run", "Long Run")
}

@Composable
private fun RunningProgressionPanel(
    prior: List<RunningWeekCoach>,
    recent: List<RunningWeekCoach>,
    plans: List<PlanSnapshot>,
    reduceMotion: Boolean
) {
    val six = prior + recent
    var selectedTab by rememberSaveable { mutableStateOf(ProgressionTab.OVERVIEW.key) }
    val hasDistance = six.all { it.completeDistance != null }
    val hasLongest = hasDistance && six.all { it.longestMeters != null }
    val distanceChange = if (hasDistance) {
        runningPercentChange(
            recent.map { it.completeDistance!! }.average(),
            prior.map { it.completeDistance!! }.average()
        )
    } else null
    val dayChange = recent.map { it.week.activeDays }.average() -
        prior.map { it.week.activeDays }.average()

    val tabs = buildList {
        add(ProgressionTab.OVERVIEW)
        add(ProgressionTab.DAYS)
        if (hasDistance) add(ProgressionTab.DISTANCE)
        if (hasLongest) add(ProgressionTab.LONG_RUN)
    }
    val activeTab = tabs.firstOrNull { it.key == selectedTab } ?: ProgressionTab.OVERVIEW

    DisciplinePanel {
        RunningReviewTitle("Training Progression")
        Text("Latest 3 completed weeks vs the previous 3", color = DisciplineLook.subdued, fontSize = 12.5.sp)
        StrideLabLiquidSegmentedControl(
            labels = tabs.map { it.key to it.label },
            selectedKey = activeTab.key,
            accent = DisciplineLook.lime,
            reduceMotion = reduceMotion,
            testTagPrefix = "training-progression-tab",
            onSelect = { selectedTab = it }
        )

        if (activeTab == ProgressionTab.OVERVIEW) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(0.40f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Distance change", color = DisciplineLook.subdued, fontSize = 11.5.sp)
                    Text(
                        distanceChange?.let(::runningPercentLabel) ?: "—",
                        color = DisciplineLook.lime,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text("vs. previous 3 weeks", color = DisciplineLook.muted, fontSize = 10.5.sp)
                }
                Surface(
                    modifier = Modifier.weight(0.60f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A1F12),
                    border = BorderStroke(1.dp, DisciplineLook.lime.copy(alpha = 0.25f))
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Coach interpretation",
                            color = DisciplineLook.lime,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                distanceChange != null && distanceChange > 10 && dayChange <= 0 ->
                                    "More ground covered without adding running days."
                                distanceChange != null && distanceChange < -10 ->
                                    "Volume eased down in the latest block."
                                dayChange > .3 ->
                                    "You ran on more days in the latest block. Open Days Run to compare."
                                else ->
                                    "Consistent rhythm across both 3-week blocks."
                            },
                            color = DisciplineLook.ink,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            RunningProgressionSparkCard(
                label = "Days run per week",
                values = six.map { it.week.activeDays.toDouble() },
                formatAvg = { String.format(Locale.getDefault(), "%.1f days", it) },
                formatBar = { "${it.roundToInt()}" },
                onClick = { selectedTab = ProgressionTab.DAYS.key }
            )
            if (hasDistance) {
                RunningProgressionSparkCard(
                    label = "Distance per week",
                    values = six.map { it.completeDistance!! / 1000.0 },
                    formatAvg = { String.format(Locale.getDefault(), "%.1f km", it) },
                    formatBar = { "${it.roundToInt()}" },
                    onClick = { selectedTab = ProgressionTab.DISTANCE.key }
                )
            }
            if (hasLongest) {
                RunningProgressionSparkCard(
                    label = "Longest run each week",
                    values = six.map { it.longestMeters!! / 1000.0 },
                    formatAvg = { String.format(Locale.getDefault(), "%.1f km", it) },
                    formatBar = { "${it.roundToInt()}" },
                    onClick = { selectedTab = ProgressionTab.LONG_RUN.key }
                )
            }
        } else {
            val (title, vals, fmtAvg, fmtBar) = when (activeTab) {
                ProgressionTab.DAYS -> Quad(
                    "Days run per week",
                    six.map { it.week.activeDays.toDouble() },
                    { v: Double -> String.format(Locale.getDefault(), "%.1f days", v) },
                    { v: Double -> "${v.roundToInt()}" }
                )
                ProgressionTab.DISTANCE -> Quad(
                    "Distance per week",
                    six.map { (it.completeDistance ?: 0.0) / 1000.0 },
                    { v: Double -> String.format(Locale.getDefault(), "%.1f km", v) },
                    { v: Double -> "${v.roundToInt()}" }
                )
                else -> Quad(
                    "Longest run each week",
                    six.map { (it.longestMeters ?: 0.0) / 1000.0 },
                    { v: Double -> String.format(Locale.getDefault(), "%.1f km", v) },
                    { v: Double -> "${v.roundToInt()}" }
                )
            }
            val earlier = vals.take(3).average()
            val latest = vals.takeLast(3).average()
            val change = runningPercentChange(latest, earlier)
            val peak = vals.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = DisciplineLook.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${fmtAvg(earlier)} → ${fmtAvg(latest)}", color = DisciplineLook.subdued, fontSize = 12.5.sp)
                }
                change?.let { pct ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF232C16),
                        border = BorderStroke(1.dp, DisciplineLook.lime.copy(alpha = 0.28f))
                    ) {
                        Text(
                            runningPercentLabel(pct),
                            color = DisciplineLook.lime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                vals.forEachIndexed { idx, v ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(fmtBar(v), color = if (idx < 3) DisciplineLook.muted else DisciplineLook.ink, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((64 * v / peak).dp.coerceAtLeast(6.dp))
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (idx < 3) Color(0xFF4F6034) else DisciplineLook.lime)
                        )
                    }
                }
            }
        }

        Text(
            "Muted: previous 3 weeks  ·  Lime: latest 3 weeks",
            color = DisciplineLook.muted,
            fontSize = 11.sp
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun RunningProgressionSparkCard(
    label: String,
    values: List<Double>,
    formatAvg: (Double) -> String,
    formatBar: (Double) -> String,
    onClick: () -> Unit
) {
    val earlier = values.take(3).average()
    val latest = values.takeLast(3).average()
    val change = runningPercentChange(latest, earlier)
    val peak = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = DisciplineLook.panelRaised,
        border = BorderStroke(1.dp, DisciplineLook.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, color = DisciplineLook.ink, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text("${formatAvg(earlier)} → ${formatAvg(latest)}", color = DisciplineLook.subdued, fontSize = 12.5.sp)
                }
                change?.let { pct ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF232C16),
                        border = BorderStroke(1.dp, DisciplineLook.lime.copy(alpha = 0.25f))
                    ) {
                        Text(
                            runningPercentLabel(pct),
                            color = DisciplineLook.lime,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    Modifier.width(148.dp).height(42.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    values.forEachIndexed { index, value ->
                        if (index == 3) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(DisciplineLook.border)
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                formatBar(value),
                                color = if (index < 3) DisciplineLook.muted else DisciplineLook.ink,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((24 * value / peak).dp.coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (index < 3) Color(0xFF4F6034) else DisciplineLook.lime)
                            )
                        }
                    }
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
    val summary = when {
        known == 0 -> "You logged ${week.sessions} ${if (week.sessions == 1) "run" else "runs"} across ${week.activeDays} days."
        dominant == null -> "Balanced mix of workout types across ${week.activeDays} days."
        else -> "Mostly $dominant across ${week.activeDays} active days."
    }
    val nextCue = when {
        known < week.sessions -> "Tag unclear workouts in Activity Analysis to sharpen balance."
        current.types[WeeklyRunType.QUALITY] != null -> "Keep easy days genuinely conversational between quality sessions."
        else -> "Let effort guide pace on your next easy run."
    }
    Text(summary, color = DisciplineLook.ink, fontSize = 13.5.sp)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1F12),
        border = BorderStroke(1.dp, DisciplineLook.lime.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("NEXT FOCUS", color = DisciplineLook.lime, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(nextCue, color = DisciplineLook.ink, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun RunningReviewTitle(title: String) {
    Text(title, color = DisciplineLook.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
}

private fun runTypeColor(type: WeeklyRunType): Color = when (type) {
    WeeklyRunType.EASY -> DisciplineLook.lime
    WeeklyRunType.LONG -> StrideLabVisualPalette.easyTeal
    WeeklyRunType.QUALITY -> StrideLabVisualPalette.moderateAmber
    WeeklyRunType.OTHER -> StrideLabVisualPalette.calisthenicsViolet
    WeeklyRunType.UNKNOWN -> DisciplineLook.muted
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

    fun formatCompact(value: Double): String = when (this) {
        DISTANCE, LONG -> String.format(Locale.US, "%.1f", value / 1000.0)
        RUNS, QUALITY -> "${value.toInt()}"
    }

    fun complete(review: RunningWeekCoach): Boolean =
        value(review) != null && (this !in listOf(DISTANCE, LONG) || review.week.missingDistance == 0)
}

@Composable
private fun TrainingWeeksChart(
    reviews: List<RunningWeekCoach>,
    plans: List<PlanSnapshot>,
    today: LocalDate,
    reduceMotion: Boolean
) {
    var metricName by rememberSaveable { mutableStateOf(WeeksMetric.DISTANCE.name) }
    var selectedIndex by rememberSaveable(reviews.last().week.start) { mutableStateOf(reviews.lastIndex) }
    val haptics = LocalHapticFeedback.current
    val metric = WeeksMetric.valueOf(metricName)
    val selected = reviews[selectedIndex]
    val values = reviews.map(metric::value)
    val numericValues = values.map { (it ?: 0.0).let { v -> if (metric in listOf(WeeksMetric.DISTANCE, WeeksMetric.LONG)) v / 1000.0 else v } }
    val maxVal = (numericValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val date = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val completed = selected.week.start.plusDays(6).isBefore(today)
    val previous = reviews.getOrNull(selectedIndex - 1)
    val status = runningPlanWeekLabel(selected.week.start, plans)
        ?: runningWeekVolumeLabel(selected, previous, today)
        ?: if (today in selected.week.start..selected.week.start.plusDays(6)) "Week in progress" else null
    val comparison = if (completed && previous != null && metric.complete(selected) && metric.complete(previous))
        runningPercentChange(values[selectedIndex]!!, values[selectedIndex - 1]!!) else null

    DisciplinePanel {
        RunningReviewTitle("Training Weeks")
        Text(
            if (metric == WeeksMetric.DISTANCE) "8 weeks of recorded distance"
            else "8 weeks · ${metric.label}",
            color = DisciplineLook.subdued,
            fontSize = 12.5.sp
        )
        StrideLabLiquidSegmentedControl(
            labels = WeeksMetric.entries.map { it.name.lowercase(Locale.ROOT) to it.label },
            selectedKey = metricName.lowercase(Locale.ROOT),
            accent = DisciplineLook.lime,
            reduceMotion = reduceMotion,
            testTagPrefix = "training-weeks-metric",
            onSelect = { key ->
                metricName = WeeksMetric.entries.first { it.name.equals(key, ignoreCase = true) }.name
            }
        )

        // Interactive smooth curved area-line chart with Y-axis + floating tooltip
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(178.dp)
                .padding(top = 8.dp)
        ) {
            val leftAxisWidth = 34.dp
            val chartWidth = maxWidth - leftAxisWidth
            val count = reviews.size.coerceAtLeast(1)

            // Y-axis scale labels
            Column(
                Modifier
                    .width(leftAxisWidth)
                    .fillMaxHeight()
                    .padding(top = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(1.0, 0.75, 0.5, 0.25, 0.0).forEach { frac ->
                    val v = maxVal * frac
                    val label = if (frac == 0.0) "0" else if (metric in listOf(WeeksMetric.DISTANCE, WeeksMetric.LONG))
                        String.format(Locale.US, "%.1f", v) else "${v.roundToInt()}"
                    Text(label, color = DisciplineLook.muted, fontSize = 9.5.sp, maxLines = 1)
                }
            }

            // Curved area-line Canvas
            Box(
                Modifier
                    .padding(start = leftAxisWidth)
                    .fillMaxSize()
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 18.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val stepX = if (count > 1) w / (count - 1).toFloat() else w
                    val pts = numericValues.mapIndexed { idx, v ->
                        val x = if (count > 1) idx * stepX else w / 2f
                        val y = h - (v / maxVal).toFloat().coerceIn(0f, 1f) * h
                        Offset(x, y)
                    }

                    // Baseline + vertical dashed guides
                    drawLine(
                        color = Color(0xFF282D30),
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = 1.dp.toPx()
                    )
                    pts.forEachIndexed { idx, pt ->
                        drawLine(
                            color = if (idx == selectedIndex) DisciplineLook.lime.copy(alpha = 0.35f)
                            else Color(0xFF22272A),
                            start = Offset(pt.x, 0f),
                            end = Offset(pt.x, h),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    if (pts.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 0 until pts.lastIndex) {
                                val p0 = pts[i]
                                val p1 = pts[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(pts.last().x, h)
                            lineTo(pts.first().x, h)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                listOf(DisciplineLook.lime.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = DisciplineLook.lime,
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    pts.forEachIndexed { idx, pt ->
                        val isSel = idx == selectedIndex
                        if (isSel) {
                            drawCircle(DisciplineLook.lime.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = pt)
                        }
                        drawCircle(DisciplineLook.lime, radius = if (isSel) 5.dp.toPx() else 3.8.dp.toPx(), center = pt)
                    }
                }

                // Floating callout tooltip anchored near selectedIndex
                val fracX = if (count > 1) selectedIndex.toFloat() / (count - 1).toFloat() else 0.5f
                val tooltipWidth = 98.dp
                val rawOffset = (chartWidth - tooltipWidth) * fracX
                Surface(
                    modifier = Modifier
                        .offset(x = rawOffset.coerceIn(0.dp, (chartWidth - tooltipWidth).coerceAtLeast(0.dp)), y = 0.dp)
                        .width(tooltipWidth),
                    shape = RoundedCornerShape(11.dp),
                    color = Color(0xEE141814),
                    border = BorderStroke(1.2.dp, DisciplineLook.lime)
                ) {
                    Column(
                        Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            values[selectedIndex]?.let(metric::format) ?: "—",
                            color = DisciplineLook.lime,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            selected.week.start.format(date),
                            color = DisciplineLook.subdued,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                // Tap hitboxes across the 8 weeks
                Row(Modifier.matchParentSize().selectableGroup()) {
                    reviews.forEachIndexed { index, review ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(index == selectedIndex, role = Role.Tab) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedIndex = index
                                }
                                .testTag("training-weeks-point-$index")
                                .semantics {
                                    contentDescription = "Week of ${review.week.start.format(date)}, " +
                                        (values[index]?.let(metric::format) ?: "unavailable")
                                }
                        )
                    }
                }
            }
        }

        // Clean 8-week horizontal date + value + compact trend indicator strip
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            reviews.forEachIndexed { idx, review ->
                val isSel = idx == selectedIndex
                val prev = reviews.getOrNull(idx - 1)
                val trendLabel = runningWeekVolumeLabel(review, prev, today)
                val trendSymbol = when (trendLabel) {
                    "Increasing" -> "↑"
                    "Reduced" -> "↓"
                    "Consistent" -> "•"
                    else -> null
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIndex = idx
                        }
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        review.week.start.format(date),
                        color = if (isSel) DisciplineLook.lime else DisciplineLook.muted,
                        fontSize = 8.5.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                    Text(
                        values[idx]?.let(metric::formatCompact) ?: "—",
                        color = if (isSel) DisciplineLook.lime else DisciplineLook.ink,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (trendSymbol != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (trendSymbol == "↑") Color(0xFF232C16) else Color(0xFF1A1E22)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                trendSymbol,
                                color = if (trendSymbol == "↑") DisciplineLook.lime else DisciplineLook.subdued,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (status != null || comparison != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status != null) {
                    Text(status, color = DisciplineLook.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
                comparison?.let {
                    Text("${runningPercentLabel(it)} vs prev wk", color = DisciplineLook.lime, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

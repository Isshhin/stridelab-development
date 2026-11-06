package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * AMOLED Today 2026. Visuals are decorative and all athlete values come from
 * persisted plan/activity records. The calendar selects an actual LocalDate,
 * and the concave card edge follows that exact weekday, including week edges.
 */
private object TodayLook {
    val black = Color(0xFF000000)
    val card = Color(0xFF111315)
    val outline = Color(0xFF303438)
    val text = Color(0xFFF7F7F5)
    val muted = Color(0xFF9FA4AA)
    val lime = Color(0xFFD8FB70)
    val coral = Color(0xFFFF8A77)
    val lilac = Color(0xFFC8A8FF)
    val teal = Color(0xFF9CD6C5)
    val azure = Color(0xFF91BFFF)
}

private data class TodayWeekMetrics(
    val counts: List<Int>,
    val seconds: List<Double>,
    val totalSeconds: Double?,
    val totalDistanceMeters: Double?,
    val activityCount: Int,
    val partialTimes: Boolean
)

private fun savedWeekMetrics(
    date: LocalDate,
    records: List<ActivityRecord>,
    rawHistory: List<ActivityRecord>,
    plans: List<PlanSnapshot>
): TodayWeekMetrics {
    val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = start.plusDays(6)
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val weekActivities = records.filter {
        !it.startedAt.isAfter(now) && it.startedAt.atZone(zone).toLocalDate() in start..end
    }
    val confirmed = DashboardSummary.confirmedWorkouts(plans, rawHistory, now)
        .filter { it.date in start..end }
    val counts = (0L..6L).map { index ->
        val day = start.plusDays(index)
        weekActivities.count { it.startedAt.atZone(zone).toLocalDate() == day } +
            confirmed.count { it.date == day }
    }
    val seconds = (0L..6L).map { index ->
        val day = start.plusDays(index)
        weekActivities.filter { it.startedAt.atZone(zone).toLocalDate() == day }
            .mapNotNull { it.durationS }.sum() +
            confirmed.filter { it.date == day }.mapNotNull { it.durationSeconds }.sum()
    }
    val durations = weekActivities.mapNotNull { it.durationS } + confirmed.mapNotNull { it.durationSeconds }
    val distance = weekActivities.filter(DashboardSummary::isRun).mapNotNull { it.distanceM } +
        confirmed.filter { it.discipline == "run" }.mapNotNull { it.distanceM }
    return TodayWeekMetrics(
        counts = counts,
        seconds = seconds,
        totalSeconds = durations.takeIf { it.isNotEmpty() }?.sum(),
        totalDistanceMeters = distance.takeIf { it.isNotEmpty() }?.sum(),
        activityCount = weekActivities.size + confirmed.size,
        partialTimes = durations.size < weekActivities.size + confirmed.size
    )
}

@Composable
internal fun StrideLabTodayScreen(
    state: TrainingState,
    visibleActivities: List<ActivityRecord>,
    online: Boolean,
    onOpenTraining: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenWorkout: (String, String) -> Unit,
    onOpenActivityDetail: (String) -> Unit,
    onOpenMore: () -> Unit,
    onSignIn: () -> Unit,
    expandedInsights: @Composable () -> Unit
) {
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(selectedDay)
    val todayDate = LocalDate.now()
    val isPastDate = date.isBefore(todayDate)
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val notchDuration = if (reduceMotion) 0 else 280
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // A signed-out device must never expose another account's cached training.
    val plan = if (state.account == null) null else
        state.plans.firstOrNull { it.status == "approved" }
    val safeActivities = if (state.account == null) emptyList() else visibleActivities
    val safeHistory = if (state.account == null) emptyList() else state.activities
    val safePlans = if (state.account == null) emptyList() else state.plans
    val recordedOnDate = remember(date, safeActivities) {
        if (!isPastDate) emptyList() else safeActivities.filter {
            !it.startedAt.isAfter(Instant.now()) &&
                it.startedAt.atZone(ZoneId.systemDefault()).toLocalDate() == date
        }.sortedByDescending { it.startedAt }
    }
    val confirmedOnDate = remember(date, safePlans, safeHistory) {
        if (!isPastDate) emptyList() else
            DashboardSummary.confirmedWorkouts(safePlans, safeHistory, Instant.now())
                .filter { it.date == date }
    }
    // Preserve persisted session order rather than sorting arbitrary IDs.
    val schedule = plan?.sessions?.filter { it.date == date && it.status != "unassigned" }
        ?: emptyList()
    val featured = schedule.firstOrNull { it.status != "completed" } ?: schedule.firstOrNull()
    val nextSameDay = if (schedule.size > 1) schedule.firstOrNull {
        it.id != featured?.id && it.status in setOf("planned", "in_progress")
    } else null
    val future = nextSameDay ?: plan?.sessions?.filter {
        it.date > date && it.status in setOf("planned", "in_progress")
    }?.sortedWith(compareBy<PlannedSession>({ it.date }, { it.id }))?.firstOrNull()
    val completed = schedule.count { it.status == "completed" }
    val tone = when {
        isPastDate && recordedOnDate.size + confirmedOnDate.size > 1 -> TodayLook.lilac
        isPastDate && recordedOnDate.isNotEmpty() -> todayRecordedColor(recordedOnDate.first().type)
        isPastDate && confirmedOnDate.isNotEmpty() ->
            todayDisciplineColor(confirmedOnDate.first().discipline)
        isPastDate -> TodayLook.teal
        state.account == null || plan == null -> TodayLook.lime
        schedule.size > 1 -> TodayLook.coral
        schedule.isEmpty() -> TodayLook.lilac
        else -> todayDisciplineColor(schedule.first().discipline)
    }
    val animatedTone by animateColorAsState(
        tone, tween(if (reduceMotion) 0 else 260), label = "today-accent"
    )
    val notchPosition by animateFloatAsState(
        (date.dayOfWeek.value - .5f) / 7f,
        tween(notchDuration), label = "today-notch"
    )
    val metrics = remember(weekStart, safeActivities, safeHistory, safePlans) {
        savedWeekMetrics(date, safeActivities, safeHistory, safePlans)
    }
    var showInsights by rememberSaveable { mutableStateOf(false) }
    val now = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(TodayLook.black)
            .testTag("screen").semantics { paneTitle = "Today" },
        contentPadding = PaddingValues(start = 19.dp, end = 19.dp, top = 17.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item(key = "today-header") {
            TodayHeader(onOpenMore)
        }
        item(key = "today-calendar") {
            TodayWeekCalendar(
                date = date,
                accent = animatedTone,
                selectionFraction = notchPosition,
                onSelect = { selectedDay = it.toEpochDay() },
                onPrevious = { selectedDay = date.minusWeeks(1).toEpochDay() },
                onNext = { selectedDay = date.plusWeeks(1).toEpochDay() }
            )
        }
        item(key = "today-hero") {
            if (isPastDate) {
                TodayRecordedTraining(
                    date = date,
                    accent = animatedTone,
                    notchFraction = notchPosition,
                    activities = recordedOnDate,
                    logged = confirmedOnDate,
                    plans = safePlans,
                    onOpenActivity = onOpenActivityDetail,
                    onOpenWorkout = onOpenWorkout,
                    onViewAll = onOpenActivity
                )
            } else {
                TodayWaveWorkout(
                    accent = animatedTone,
                    notchFraction = notchPosition,
                    reduceMotion = reduceMotion,
                    date = date,
                    scheduled = schedule,
                    planAvailable = plan != null,
                    signedOut = state.account == null,
                    onOpen = { session ->
                        if (plan != null) onOpenWorkout(plan.id, session.id) else onOpenTraining()
                    },
                    onPlan = onOpenTraining,
                    onSignIn = onSignIn
                )
            }
        }
        item(key = "today-next") {
            TodayNextSession(
                next = future,
                plan = plan,
                signedOut = state.account == null,
                completed = completed,
                total = schedule.size,
                onOpen = {
                    if (state.account == null) onSignIn()
                    else if (plan != null && future != null) onOpenWorkout(plan.id, future.id)
                    else onOpenTraining()
                }
            )
        }
        item(key = "today-activity") {
            TodayActivityWeek(
                metrics = metrics,
                selectedDayIndex = date.dayOfWeek.value - 1,
                reduceMotion = reduceMotion,
                onOpenActivity = onOpenActivity
            )
        }
        item(key = "today-summary") {
            TodaySummaryWeek(
                start = weekStart,
                metrics = metrics,
                onOpenActivity = onOpenActivity
            )
        }
        if (state.account != null && visibleActivities.isNotEmpty()) {
            item(key = "today-recent") {
                TodayRecentActivity(visibleActivities.take(2), onOpenActivityDetail, onOpenActivity)
            }
        }
        item(key = "today-insights-toggle") {
            TextButton(onClick = { showInsights = !showInsights }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (showInsights) "Hide additional insights  −" else "Additional insights  +",
                    color = TodayLook.muted
                )
            }
        }
        if (showInsights) {
            item(key = "today-existing-insights") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    expandedInsights()
                }
            }
        }
        if (!online) {
            item(key = "today-offline-caption") {
                Text(
                    "Offline · displaying your saved records",
                    style = MaterialTheme.typography.labelSmall,
                    color = TodayLook.muted
                )
            }
        }
    }
}

private fun todayRecordedColor(type: String): Color {
    val t = type.lowercase(Locale.ROOT)
    return when {
        t.replace(Regex("[ _-]"), "") in setOf(
            "run", "running", "trailrun", "trailrunning", "treadmill",
            "virtualrun", "walk", "walking", "hiking", "hike"
        ) -> TodayLook.lime
        "strength" in t || "weight" in t -> TodayLook.coral
        "calisthenics" in t || "bodyweight" in t -> TodayLook.lilac
        "conditioning" in t || "circuit" in t -> TodayLook.azure
        else -> TodayLook.teal
    }
}

private fun todayDisciplineColor(value: String): Color = when (value.lowercase(Locale.ROOT)) {
    "run", "running" -> TodayLook.lime
    "str", "strength" -> TodayLook.coral
    "cal", "calisthenics", "body", "bodyweight" -> TodayLook.lilac
    "cond", "conditioning" -> TodayLook.azure
    else -> TodayLook.teal
}

@Composable
private fun TodayHeader(onOpenMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 27.dp, height = 25.dp)) {
                val w = size.width
                val h = size.height
                val p = Path().apply {
                    moveTo(0f, h * .69f)
                    lineTo(w * .25f, h * .24f)
                    lineTo(w * .50f, h * .24f)
                    lineTo(w * .70f, h * .67f)
                    lineTo(w, h * .20f)
                    lineTo(w * .74f, h * .84f)
                    lineTo(w * .49f, h * .84f)
                    lineTo(w * .30f, h * .48f)
                    lineTo(w * .17f, h * .69f)
                    close()
                }
                drawPath(p, TodayLook.lime)
            }
            Spacer(Modifier.width(8.dp))
            Text("StrideLab", color = TodayLook.text, fontSize = 21.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(
                onClick = onOpenMore, shape = CircleShape,
                color = TodayLook.card, modifier = Modifier.size(39.dp)
                    .testTag("today-profile")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(21.dp)) {
                        drawCircle(
                            TodayLook.text, radius = size.width * .16f,
                            center = Offset(size.width * .5f, size.height * .28f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
                        )
                        drawArc(
                            TodayLook.text, startAngle = 195f, sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(size.width * .14f, size.height * .49f),
                            size = androidx.compose.ui.geometry.Size(size.width * .72f, size.height * .53f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val greeting = when (java.time.LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        Text(greeting, color = TodayLook.muted, fontSize = 14.sp)
        Text("Today", color = TodayLook.text, fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp,
            modifier = Modifier.semantics { heading() })
        Canvas(Modifier.size(width = 138.dp, height = 7.dp)) {
            val p = Path().apply {
                moveTo(0f, size.height * .8f)
                cubicTo(size.width * .32f, -size.height * .15f, size.width * .7f,
                    size.height * 1.25f, size.width, size.height * .30f)
            }
            drawPath(p, TodayLook.lime, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun TodayWeekCalendar(
    date: LocalDate,
    accent: Color,
    selectionFraction: Float,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val haptics = LocalHapticFeedback.current
    val selectDay: (LocalDate) -> Unit = { day ->
        if (day != date) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(day)
        }
    }
    val previousWeek: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onPrevious()
    }
    val nextWeek: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onNext()
    }

    // Horizontal gestures switch weeks without interrupting vertical Today scrolling.
    Column(
        modifier = Modifier.fillMaxWidth().pointerInput(date) {
            var dragDistance = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragDistance = 0f },
                onDragCancel = { dragDistance = 0f },
                onDragEnd = {
                    val threshold = 64.dp.toPx()
                    when {
                        dragDistance > threshold -> previousWeek()
                        dragDistance < -threshold -> nextWeek()
                    }
                    dragDistance = 0f
                },
                onHorizontalDrag = { change, amount ->
                    dragDistance += amount
                    change.consume()
                }
            )
        },
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                color = TodayLook.text, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            TodayCalendarArrow("‹", "Previous week", previousWeek)
            Spacer(Modifier.width(8.dp))
            TodayCalendarArrow("›", "Next week", nextWeek)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("today-week-picker")) {
            // Exactly one lime indicator slides across the seven equal calendar
            // columns. Its x-coordinate is shared with the notch animation.
            val diameter = 37.dp
            val indicatorX = (maxWidth * selectionFraction - diameter / 2)
                .coerceIn(0.dp, maxWidth - diameter)
            Box(
                Modifier.align(Alignment.BottomStart).offset(x = indicatorX)
                    .size(diameter).background(accent, CircleShape)
                    .testTag("today-date-indicator")
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                val letters = listOf("M", "T", "W", "T", "F", "S", "S")
                (0..6).forEach { idx ->
                    val day = monday.plusDays(idx.toLong())
                    val isSelected = day == date
                    Column(
                        modifier = Modifier.weight(1f).clickable { selectDay(day) }
                            .testTag("today-day-" + idx)
                            .semantics {
                                contentDescription = day.format(
                                    DateTimeFormatter.ofPattern(
                                        "EEEE d MMMM", Locale.getDefault()
                                    )
                                )
                                selected = isSelected
                                role = Role.Button
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            letters[idx], fontSize = 11.sp,
                            color = if (isSelected) accent else TodayLook.muted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                            Text(
                                day.dayOfMonth.toString(),
                                // Keep digits readable throughout the glide: their
                                // ink follows the indicator, not a snapped selection.
                                color = lerp(
                                    TodayLook.muted, TodayLook.black,
                                    (1f - kotlin.math.abs(
                                        selectionFraction - (idx + .5f) / 7f
                                    ) * 7f).coerceIn(0f, 1f)
                                ),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold
                                else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCalendarArrow(symbol: String, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = TodayLook.card,
        modifier = Modifier.size(34.dp).semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, color = TodayLook.text, fontSize = 22.sp)
        }
    }
}

private fun todayNotchedShape(fraction: Float) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val center = w * fraction.coerceIn(.06f, .94f)
    val baseRadius = w * .052f
    val leftRadius = minOf(baseRadius, center * .35f)
    val rightRadius = minOf(baseRadius, (w - center) * .35f)
    // A small, shallow concave scoop, not the previous deep U-shaped cutout.
    // Keep the scoop centered under the selected weekday, including edge days.
    val half = minOf(w * .065f, center - leftRadius - 2f, w - rightRadius - center - 2f)
        .coerceAtLeast(w * .012f)
    val depth = minOf(h * .035f, w * .024f)
    moveTo(leftRadius, 0f)
    lineTo(center - half, 0f)
    cubicTo(center - half * .42f, 0f, center - half * .55f, depth, center, depth)
    cubicTo(center + half * .55f, depth, center + half * .42f, 0f, center + half, 0f)
    lineTo(w - rightRadius, 0f)
    quadraticTo(w, 0f, w, rightRadius)
    lineTo(w, h - baseRadius)
    quadraticTo(w, h, w - baseRadius, h)
    lineTo(baseRadius, h)
    quadraticTo(0f, h, 0f, h - baseRadius)
    lineTo(0f, leftRadius)
    quadraticTo(0f, 0f, leftRadius, 0f)
    close()
}


/**
 * The calendar's past dates show actual saved recordings and confirmed workout
 * logs, never a historical "Today's Workout" prescription or decorative GPS map.
 * Native logs are already filtered to avoid linked-activity double counting.
 */
@Composable
private fun TodayRecordedTraining(
    date: LocalDate,
    accent: Color,
    notchFraction: Float,
    activities: List<ActivityRecord>,
    logged: List<ConfirmedWorkout>,
    plans: List<PlanSnapshot>,
    onOpenActivity: (String) -> Unit,
    onOpenWorkout: (String, String) -> Unit,
    onViewAll: () -> Unit
) {
    val shape = remember(notchFraction) { todayNotchedShape(notchFraction) }
    val count = activities.size + logged.size
    val firstActivity = activities.firstOrNull()
    val firstLog = logged.firstOrNull()
    val firstLogSession = firstLog?.let { entry ->
        plans.firstOrNull { it.id == entry.planId }
            ?.sessions?.firstOrNull { it.id == entry.sessionId }
    }
    val header = when {
        count == 0 -> "NO ACTIVITY RECORDED"
        count == 1 -> "RECORDED ACTIVITY"
        else -> "YOUR TRAINING HISTORY"
    }
    val title = when {
        count == 0 -> "Nothing recorded"
        count > 1 -> "$count recorded sessions"
        firstActivity != null -> firstActivity.title
        firstLogSession != null -> firstLogSession.effectiveTitle
        else -> "Completed workout"
    }
    val summary = when {
        count == 0 -> "No saved activity or confirmed workout log for this date."
        count > 1 -> "Your completed sessions for the selected day."
        firstActivity != null -> listOfNotNull(
            firstActivity.distanceM?.let { firstActivity.distanceLabel },
            firstActivity.durationS?.let { firstActivity.durationLabel }
        ).joinToString("  ·  ").ifBlank { "Metrics not recorded" }
        firstLog != null -> listOfNotNull(
            firstLog.distanceM?.let {
                String.format(Locale.getDefault(), "%.2f km", it / 1000)
            },
            firstLog.durationSeconds?.let(ActivityRecord::duration)
        ).joinToString("  ·  ").ifBlank { "Completed workout log" }
        else -> ""
    }
    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF121719), Color(0xFF192325), accent.copy(alpha = .32f))
                )
            )
            .border(1.dp, accent.copy(alpha = .55f), shape)
            .testTag("today-recorded-hero")
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = 19.dp, end = 17.dp, top = 32.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    header, color = accent,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                    color = TodayLook.muted,
                    fontSize = 11.sp
                )
            }
            Text(
                title, color = TodayLook.text,
                fontWeight = FontWeight.ExtraBold, fontSize = 25.sp,
                lineHeight = 30.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(summary, color = TodayLook.muted, fontSize = 12.sp, lineHeight = 17.sp)
            if (count > 1) {
                activities.forEach { item ->
                    TodayRecordedRow(
                        title = item.title,
                        subtitle = listOfNotNull(
                            item.distanceM?.let { item.distanceLabel },
                            item.durationS?.let { item.durationLabel }
                        ).joinToString(" · ").ifBlank { item.sourceLabel },
                        tint = todayRecordedColor(item.type),
                        onClick = { onOpenActivity(item.id) }
                    )
                }
                logged.forEach { entry ->
                    val session = plans.firstOrNull { it.id == entry.planId }
                        ?.sessions?.firstOrNull { it.id == entry.sessionId }
                    TodayRecordedRow(
                        title = session?.effectiveTitle ?: "Completed workout",
                        subtitle = listOfNotNull(
                            entry.distanceM?.let {
                                String.format(Locale.getDefault(), "%.2f km", it / 1000)
                            },
                            entry.durationSeconds?.let(ActivityRecord::duration)
                        ).joinToString(" · ").ifBlank { "Workout log" },
                        tint = todayDisciplineColor(entry.discipline),
                        onClick = { onOpenWorkout(entry.planId, entry.sessionId) }
                    )
                }
            }
            if (count > 0) {
                Surface(
                    onClick = {
                        when {
                            count > 1 -> onViewAll()
                            firstActivity != null -> onOpenActivity(firstActivity.id)
                            firstLog != null -> onOpenWorkout(firstLog.planId, firstLog.sessionId)
                        }
                    },
                    shape = RoundedCornerShape(25.dp),
                    color = accent,
                    modifier = Modifier.height(44.dp).testTag("today-recorded-action")
                ) {
                    Row(
                        Modifier.padding(horizontal = 17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("›", color = TodayLook.black, fontSize = 20.sp)
                        Text(
                            if (count > 1) "View activity history"
                            else if (firstActivity != null) "View activity"
                            else "View workout log",
                            color = TodayLook.black,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    "Your recorded runs and completed workout logs will appear here.",
                    color = TodayLook.muted, fontSize = 11.sp, lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TodayRecordedRow(
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = Color(0xEF202529),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("●", color = tint, fontSize = 14.sp)
            Column(Modifier.weight(1f)) {
                Text(title, color = TodayLook.text, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TodayLook.muted, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = TodayLook.text, fontSize = 19.sp)
        }
    }
}

private data class TodayHeroSnapshot(
    val date: LocalDate,
    val scheduled: List<PlannedSession>,
    val planAvailable: Boolean,
    val signedOut: Boolean
)

/**
 * Content transitions are keyed by *workout discipline*, not date or session ID.
 * Easy Run -> Tempo Run updates directly; Run -> Strength/Recovery fades.
 * For multiple workouts, fade only if the discipline mix changes.
 */
private fun todayHeroDiscipline(discipline: String): String =
    when (discipline.lowercase(Locale.ROOT)) {
        "run", "running" -> "running"
        "str", "strength" -> "strength"
        "cal", "calisthenics", "body", "bodyweight" -> "calisthenics"
        "cond", "conditioning" -> "conditioning"
        else -> discipline.lowercase(Locale.ROOT)
    }

private fun todayHeroContentKey(snapshot: TodayHeroSnapshot): String = when {
    snapshot.signedOut -> "signed-out"
    !snapshot.planAvailable -> "no-plan"
    snapshot.scheduled.isEmpty() -> "recovery"
    snapshot.scheduled.size > 1 -> "multi:" +
        snapshot.scheduled.map { todayHeroDiscipline(it.discipline) }.sorted().joinToString(",")
    else -> todayHeroDiscipline(snapshot.scheduled.first().discipline)
}

@Composable
private fun TodayWaveWorkout(
    accent: Color,
    notchFraction: Float,
    reduceMotion: Boolean,
    date: LocalDate,
    scheduled: List<PlannedSession>,
    planAvailable: Boolean,
    signedOut: Boolean,
    onOpen: (PlannedSession) -> Unit,
    onPlan: () -> Unit,
    onSignIn: () -> Unit
) {
    val shape = remember(notchFraction) { todayNotchedShape(notchFraction) }
    val multi = scheduled.size > 1
    val primary = scheduled.firstOrNull { it.status != "completed" } ?: scheduled.firstOrNull()
    val content = TodayHeroSnapshot(date, scheduled, planAvailable, signedOut)
    Box(
        Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = tween(if (reduceMotion) 0 else 240))
            .heightIn(min = if (multi) 284.dp else 228.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent, accent.copy(alpha = .98f), accent.copy(alpha = .81f))
                )
            )
            .border(1.dp, Color(0x55000000), shape)
            .testTag("today-hero")
    ) {
        Crossfade(
            targetState = when {
                signedOut || !planAvailable -> "general"
                scheduled.isEmpty() -> "recovery"
                multi -> "multi"
                else -> primary?.discipline?.let(::todayHeroDiscipline) ?: "general"
            },
            animationSpec = tween(if (reduceMotion) 0 else 240),
            modifier = Modifier.matchParentSize(),
            label = "today-workout-art"
        ) { artKind ->
            TodayDecorativeArt(
                accent = accent,
                kind = artKind,
                modifier = Modifier.fillMaxSize()
            )
        }
        AnimatedContent(
            targetState = content,
            // Different dates in the same discipline update immediately,
            // avoiding the full-card blink on Run -> Run or empty -> empty.
            contentKey = ::todayHeroContentKey,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(if (reduceMotion) 0 else 210)) +
                    slideInVertically(
                        animationSpec = tween(if (reduceMotion) 0 else 210),
                        initialOffsetY = { it / 13 }
                    )) togetherWith (
                    fadeOut(tween(if (reduceMotion) 0 else 150)) +
                    slideOutVertically(
                        animationSpec = tween(if (reduceMotion) 0 else 150),
                        targetOffsetY = { -it / 13 }
                    )
                )
            },
            label = "today-workout-details"
        ) { displayed ->
            val date = displayed.date
            val scheduled = displayed.scheduled
            val planAvailable = displayed.planAvailable
            val signedOut = displayed.signedOut
            val multi = scheduled.size > 1
            val primary = scheduled.firstOrNull { it.status != "completed" }
                ?: scheduled.firstOrNull()
            val isToday = date == LocalDate.now()
        val title = when {
            signedOut -> "Train with StrideLab"
            !planAvailable -> "Your next chapter"
            scheduled.isEmpty() -> if (isToday) "Recovery day" else "Open day"
            multi -> scheduled.size.toString() + " sessions"
            else -> primary?.effectiveTitle ?: "Workout"
        }
        val intro = when {
            signedOut -> "Sign in to see your own training schedule."
            !planAvailable -> "Build a plan that works around your week."
            scheduled.isEmpty() -> "No workout scheduled for this day."
            multi -> scheduled.count { it.status == "completed" }.toString() +
                " completed · " + (scheduled.size - scheduled.count { it.status == "completed" }) +
                " remaining"
            else -> todaySessionDetails(primary!!)
        }
        val onPrimary = when {
            signedOut -> onSignIn
            !planAvailable || scheduled.isEmpty() -> onPlan
            primary != null -> ({ onOpen(primary) })
            else -> onPlan
        }
        val button = when {
            signedOut -> "Sign in"
            !planAvailable -> "Create training plan"
            scheduled.isEmpty() -> "View training"
            !isToday -> "View workout"
            multi -> if (scheduled.all { it.status == "completed" }) "View workouts" else "Open next workout"
            primary?.status == "completed" -> "View workout"
            else -> "Start workout"
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 17.dp, top = 33.dp, bottom = 21.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (scheduled.isEmpty() && planAvailable && !signedOut) "✦" else "●",
                    color = TodayLook.black, fontSize = 14.sp
                )
                Text(
                    when {
                        signedOut -> "YOUR TRAINING"
                        !planAvailable -> "GET STARTED"
                        scheduled.isEmpty() && isToday -> "RECOVERY DAY"
                        scheduled.isEmpty() -> "NO WORKOUT SCHEDULED"
                        isToday -> "TODAY'S WORKOUT"
                        else -> "UPCOMING WORKOUT"
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TodayLook.black
                )
                Spacer(Modifier.weight(1f))
                if (scheduled.isNotEmpty()) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                        style = MaterialTheme.typography.labelSmall, color = TodayLook.black.copy(alpha = .58f)
                    )
                }
            }
            Text(title, fontSize = if (multi) 27.sp else 29.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.ExtraBold, color = TodayLook.black,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(if (multi) 1f else .83f))
            Text(intro, color = TodayLook.black.copy(alpha = .82f),
                fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth(if (multi) 1f else .75f),
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (multi) {
                Spacer(Modifier.height(1.dp))
                scheduled.forEach { session ->
                    Surface(
                        onClick = { onOpen(session) },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xD91B1D20),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 53.dp)
                            .testTag("today-session-" + session.id)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("●", color = todayDisciplineColor(session.discipline), fontSize = 16.sp)
                            Column(Modifier.weight(1f)) {
                                Text(session.effectiveTitle, color = TodayLook.text,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(todaySessionDetails(session),
                                    color = TodayLook.muted, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (session.status == "completed") {
                                Text("✓", color = TodayLook.lime, fontSize = 16.sp)
                            } else Text("›", color = TodayLook.text, fontSize = 19.sp)
                        }
                    }
                }
            } else if (scheduled.isEmpty()) {
                Text(
                    if (signedOut) "Your activities stay private."
                    else if (!planAvailable) "Personalized sessions will appear here once approved."
                    else if (isToday) "Make time to recover and come back stronger."
                    else "See your training calendar for another scheduled day.",
                    color = TodayLook.black.copy(alpha = .72f), fontSize = 11.sp, lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth(.83f)
                )
            }
            Spacer(Modifier.height(if (multi) 2.dp else 10.dp))
            Surface(
                onClick = onPrimary,
                shape = RoundedCornerShape(28.dp),
                color = if (multi) Color(0xFF181B18) else TodayLook.coral,
                modifier = Modifier.height(46.dp)
                    .testTag("today-workout-action")
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("▶", color = TodayLook.text, fontSize = 12.sp)
                    Text(button, color = TodayLook.text,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        }

    }
}

private fun todaySessionDetails(session: PlannedSession): String {
    val parts = mutableListOf<String>()
    session.distanceKm?.let { parts.add(String.format(Locale.getDefault(), "%.1f km", it)) }
    if (session.effectiveMinutes > 0) parts.add(session.effectiveMinutes.toString() + " min")
    if (session.discipline != "run" && session.effectiveExercises.isNotEmpty()) {
        parts.add(session.effectiveExercises.size.toString() + " exercises")
    }
    if (parts.isEmpty()) parts.add(session.status.replace('_', ' '))
    return parts.joinToString("  ·  ")
}

/** Decorative geometry, not a GPS route, biometric metric or training prescription. */
@Composable
private fun TodayDecorativeArt(accent: Color, kind: String, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val isRun = kind == "run" || kind == "running" || kind == "general"
        val isStrength = kind == "str" || kind == "strength"
        val isRecovery = kind == "recovery"
        val artInk = when {
            isRun -> Color(0xFF22351B)
            isStrength -> Color(0xFF6F3029)
            isRecovery -> Color(0xFF4D3871)
            else -> Color(0xFF343044)
        }
        val base = Path().apply {
            moveTo(w * .49f, h)
            cubicTo(w * .65f, h * .66f, w * .83f, h * .50f, w, h * .58f)
            lineTo(w, h)
            close()
        }
        drawPath(base, artInk.copy(alpha = .20f))
        if (isRun) {
            val ranges = listOf(
                listOf(.54f to .85f, .72f to .47f, .85f to .69f, .96f to .34f, 1f to .47f),
                listOf(.61f to 1f, .81f to .65f, .91f to .79f, 1f to .56f)
            )
            ranges.forEachIndexed { idx, coordinates ->
                val p = Path().apply {
                    moveTo(coordinates.first().first * w, coordinates.first().second * h)
                    coordinates.drop(1).forEach { (x, y) -> lineTo(x * w, y * h) }
                    lineTo(w, h)
                    lineTo(coordinates.first().first * w, h)
                    close()
                }
                drawPath(p, artInk.copy(alpha = if (idx == 0) .64f else .32f))
            }
        } else if (isStrength) {
            val center = Offset(w * .83f, h * .66f)
            drawLine(artInk.copy(alpha = .55f),
                Offset(w * .67f, h * .82f), Offset(w * .98f, h * .49f),
                strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
            listOf(
                Offset(w * .71f, h * .78f),
                Offset(w * .95f, h * .53f)
            ).forEach { pt ->
                drawCircle(artInk.copy(alpha = .30f), radius = 25.dp.toPx(), center = pt)
                drawCircle(artInk.copy(alpha = .58f), radius = 16.dp.toPx(), center = pt)
            }
        } else if (isRecovery) {
            drawCircle(artInk.copy(alpha = .30f), radius = w * .14f,
                center = Offset(w * .87f, h * .72f))
            val p = Path().apply {
                moveTo(w * .57f, h)
                quadraticTo(w * .79f, h * .54f, w, h * .84f)
                lineTo(w, h)
                close()
            }
            drawPath(p, artInk.copy(alpha = .30f))
        } else {
            repeat(3) { idx ->
                val radius = (idx + 1) * 37.dp.toPx()
                drawCircle(artInk.copy(alpha = .12f), radius = radius,
                    center = Offset(w * .90f, h * .82f))
            }
        }
    }
}

@Composable
private fun TodayNextSession(
    next: PlannedSession?,
    plan: PlanSnapshot?,
    signedOut: Boolean,
    completed: Int,
    total: Int,
    onOpen: () -> Unit
) {
    TodayDarkCard(onClick = onOpen, tag = "today-next-session") {
        Row(verticalAlignment = Alignment.Top) {
            Text("◷", fontSize = 24.sp, color = TodayLook.coral,
                modifier = Modifier.padding(end = 11.dp))
            Column(Modifier.weight(1f)) {
                Text("Next session", fontSize = 12.sp, color = TodayLook.muted)
                Text(
                    next?.effectiveTitle ?: if (signedOut) "Your schedule awaits"
                    else if (plan != null) "No upcoming sessions" else "Build your plan",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TodayLook.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", fontSize = 25.sp, color = TodayLook.muted)
        }
        if (next != null) {
            Text(
                next.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())) +
                    "  ·  " + todaySessionDetails(next),
                color = TodayLook.muted, fontSize = 12.sp, maxLines = 2
            )
        } else {
            Text(
                if (signedOut) "Sign in to see your training history."
                else if (plan == null) "Create an approved schedule to get started."
                else "Check your training schedule for future workouts.",
                color = TodayLook.muted, fontSize = 12.sp
            )
        }
        if (total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF34373D))
                ) {
                    Box(
                        Modifier.fillMaxWidth(completed.toFloat() / total.toFloat())
                            .fillMaxHeight().background(TodayLook.coral)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(completed.toString() + " of " + total + " today",
                    color = TodayLook.muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TodayActivityWeek(
    metrics: TodayWeekMetrics,
    selectedDayIndex: Int,
    reduceMotion: Boolean,
    onOpenActivity: () -> Unit
) {
    TodayDarkCard(onClick = onOpenActivity, tag = "today-activity-week") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("▥", fontSize = 25.sp, color = TodayLook.lime)
            Column(Modifier.weight(.8f)) {
                Text("Activity", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = TodayLook.text)
                Text("Your week", fontSize = 12.sp, color = TodayLook.muted)
            }
            Row(
                Modifier.weight(1.3f), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val maxSeconds = metrics.seconds.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                (0..6).forEach { idx ->
                    val height = when {
                        metrics.seconds[idx] > 0 -> (metrics.seconds[idx] / maxSeconds * 30)
                            .toFloat().coerceIn(5f, 30f)
                        metrics.counts[idx] > 0 -> 7f
                        else -> 3f
                    }
                    val barHeight by animateDpAsState(
                        targetValue = height.dp,
                        animationSpec = tween(if (reduceMotion) 0 else 220),
                        label = "today-activity-height-$idx"
                    )
                    val barColor by animateColorAsState(
                        targetValue = if (idx == selectedDayIndex) TodayLook.lime
                            else Color(0xFF7B8088),
                        animationSpec = tween(if (reduceMotion) 0 else 200),
                        label = "today-activity-color-$idx"
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(8.dp).height(33.dp),
                            contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.width(8.dp).height(barHeight)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(barColor)
                            )
                        }
                        Text(days[idx], color = TodayLook.muted, fontSize = 9.sp)
                    }
                }
            }
            Text("›", color = TodayLook.muted, fontSize = 22.sp)
        }
        if (metrics.partialTimes) {
            Text("Some saved sessions have no recorded duration.",
                style = MaterialTheme.typography.labelSmall, color = TodayLook.muted)
        }
    }
}

@Composable
private fun TodaySummaryWeek(
    start: LocalDate,
    metrics: TodayWeekMetrics,
    onOpenActivity: () -> Unit
) {
    TodayDarkCard(onClick = onOpenActivity, tag = "today-week-summary") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✧", color = TodayLook.lime, fontSize = 20.sp)
            Spacer(Modifier.width(9.dp))
            Text("Weekly summary", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = TodayLook.text, modifier = Modifier.weight(1f))
            Text(
                start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) +
                    " – " + start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                color = TodayLook.muted, fontSize = 11.sp
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            TodayStat(
                label = "Run distance",
                value = metrics.totalDistanceMeters?.let {
                    String.format(Locale.getDefault(), "%.1f km", it / 1000)
                } ?: "—",
                tint = TodayLook.lime, modifier = Modifier.weight(1f)
            )
            TodayStat(
                label = "Active time",
                value = metrics.totalSeconds?.let(::todayTimeLabel) ?: "—",
                tint = TodayLook.teal, modifier = Modifier.weight(1f)
            )
            TodayStat(
                label = "Activities",
                value = metrics.activityCount.toString(),
                tint = TodayLook.coral, modifier = Modifier.weight(1f)
            )
        }
        Text("From saved records only", color = TodayLook.muted,
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun todayTimeLabel(seconds: Double): String {
    val minutes = (seconds / 60).toLong()
    return if (minutes >= 60L) (minutes / 60).toString() + "h " +
        (minutes % 60).toString() + "m"
    else minutes.toString() + "m"
}

@Composable
private fun TodayStat(
    label: String, value: String, tint: Color, modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = TodayLook.muted, fontSize = 10.sp,
            maxLines = 2, lineHeight = 13.sp)
    }
}

@Composable
private fun TodayRecentActivity(
    recent: List<ActivityRecord>,
    onOpen: (String) -> Unit,
    onOpenActivity: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent activity", color = TodayLook.text,
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenActivity) {
                Text("View all ›", color = TodayLook.muted)
            }
        }
        recent.forEach { activity ->
            Surface(
                onClick = { onOpen(activity.id) },
                modifier = Modifier.fillMaxWidth().testTag("activity-" + activity.id),
                shape = RoundedCornerShape(17.dp),
                color = TodayLook.card
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("●", fontSize = 17.sp, color = TodayLook.lime)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(activity.title, color = TodayLook.text,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                activity.distanceM?.let { activity.distanceLabel },
                                activity.durationS?.let { activity.durationLabel }
                            ).joinToString("  ·  ").ifBlank { "Saved activity" },
                            color = TodayLook.muted, fontSize = 11.sp
                        )
                    }
                    Text("›", color = TodayLook.muted, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun TodayDarkCard(
    onClick: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        color = TodayLook.card,
        shape = RoundedCornerShape(21.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TodayLook.outline)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            content = content
        )
    }
}

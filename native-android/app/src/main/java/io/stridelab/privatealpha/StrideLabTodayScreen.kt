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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
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

/** Status markers use only the signed-in athlete's persisted workouts. */
private data class TodayDayMarker(val completed: Boolean, val pending: Boolean)

private fun todayWeekMarkers(
    start: LocalDate,
    visible: List<ActivityRecord>,
    rawHistory: List<ActivityRecord>,
    plans: List<PlanSnapshot>
): Map<LocalDate, TodayDayMarker> {
    val end = start.plusDays(6)
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val recordedDays = visible.asSequence()
        .filter { !it.startedAt.isAfter(now) }
        .map { it.startedAt.atZone(zone).toLocalDate() }
        .filter { it in start..end }.toSet() +
        DashboardSummary.confirmedWorkouts(plans, rawHistory, now)
            .map { it.date }.filter { it in start..end }
    val scheduled = plans.filter { it.status == "approved" }
        .flatMap { it.sessions }.filter { it.date in start..end && it.status != "unassigned" }
    return (0L..6L).associate { offset ->
        val day = start.plusDays(offset)
        day to TodayDayMarker(
            completed = day in recordedDays || scheduled.any {
                it.date == day && it.status == "completed"
            },
            pending = scheduled.any {
                it.date == day && it.status in setOf("planned", "in_progress")
            }
        )
    }
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
    val recordedOnDate = remember(date, todayDate, safeActivities) {
        if (date.isAfter(todayDate)) emptyList() else safeActivities.filter {
            !it.startedAt.isAfter(Instant.now()) &&
                it.startedAt.atZone(ZoneId.systemDefault()).toLocalDate() == date
        }.sortedByDescending { it.startedAt }
    }
    val confirmedOnDate = remember(date, todayDate, safePlans, safeHistory) {
        if (date.isAfter(todayDate)) emptyList() else
            DashboardSummary.confirmedWorkouts(safePlans, safeHistory, Instant.now())
                .filter { it.date == date }
    }
    // Preserve persisted session order rather than sorting arbitrary IDs.
    val schedule = plan?.sessions?.filter { it.date == date && it.status != "unassigned" }
        ?: emptyList()
    val featured = schedule.firstOrNull { it.status != "completed" } ?: schedule.firstOrNull()
    // The Next Session card always tracks real today, not the browsed date.
    val todaySchedule = plan?.sessions?.filter {
        it.date == todayDate && it.status != "unassigned"
    } ?: emptyList()
    val nextToday = todaySchedule.firstOrNull {
        it.status in setOf("planned", "in_progress")
    }
    val future = nextToday ?: plan?.sessions?.filter {
        it.date > todayDate && it.status in setOf("planned", "in_progress")
    }?.minWithOrNull(compareBy<PlannedSession>({ it.date }, { it.id }))
    val todayCompleted = todaySchedule.count { it.status == "completed" }
    // A completed day uses recorded styling only when the evidence is saved.
    // If a second session is still planned today, keep its actionable hero.
    val hasUnfinishedWorkout = schedule.any { it.status in setOf("planned", "in_progress") }
    val mixedToday = date == todayDate && hasUnfinishedWorkout &&
        (recordedOnDate.isNotEmpty() || confirmedOnDate.isNotEmpty())
    val showRecorded = isPastDate ||
        (date == todayDate && !hasUnfinishedWorkout &&
            (recordedOnDate.isNotEmpty() || confirmedOnDate.isNotEmpty()))
    val recordedKinds = recordedOnDate.map { todayRecordedKind(it.type) } +
        confirmedOnDate.map { todayHeroDiscipline(it.discipline) }
    val tone = when {
        mixedToday -> TodayLook.lime
        showRecorded && recordedKinds.distinct().size > 1 -> TodayLook.lilac
        showRecorded && recordedOnDate.isNotEmpty() -> todayRecordedColor(recordedOnDate.first().type)
        showRecorded && confirmedOnDate.isNotEmpty() ->
            todayDisciplineColor(confirmedOnDate.first().discipline)
        showRecorded -> TodayLook.teal
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
    val markers = remember(weekStart, safeActivities, safeHistory, safePlans) {
        todayWeekMarkers(weekStart, safeActivities, safeHistory, safePlans)
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
            TodayHeader(onOpenMore, online = online, loading = state.initializing)
        }
        item(key = "today-calendar") {
            TodayWeekCalendar(
                date = date,
                accent = animatedTone,
                selectionFraction = notchPosition,
                markers = markers,
                today = todayDate,
                onToday = { selectedDay = todayDate.toEpochDay() },
                onSelect = { selectedDay = it.toEpochDay() },
                onPrevious = { selectedDay = date.minusWeeks(1).toEpochDay() },
                onNext = { selectedDay = date.plusWeeks(1).toEpochDay() }
            )
        }
        item(key = "today-hero") {
            if (mixedToday) {
                TodayMixedDayCard(
                    date = date,
                    notchFraction = notchPosition,
                    activities = recordedOnDate,
                    logs = confirmedOnDate,
                    pending = schedule.filter {
                        it.status in setOf("planned", "in_progress")
                    },
                    plans = safePlans,
                    onOpenActivity = onOpenActivityDetail,
                    onOpenWorkout = onOpenWorkout
                )
            } else if (showRecorded) {
                TodayRecordedTraining(
                    selectedDate = date,
                    signedOut = state.account == null,
                    online = online,
                    accent = animatedTone,
                    notchFraction = notchPosition,
                    activities = recordedOnDate,
                    logged = confirmedOnDate,
                    plans = safePlans,
                    onOpenActivity = onOpenActivityDetail,
                    onOpenWorkout = onOpenWorkout,
                    onSignIn = onSignIn
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
                completed = todayCompleted,
                total = todaySchedule.size,
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
    }
}

/** Display-only classification of the saved activity type; never infer a workout. */
private fun todayRecordedKind(type: String): String {
    val t = type.lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")
    return when {
        t in setOf("run", "running", "trailrun", "trailrunning", "treadmill",
            "virtualrun") -> "running"
        t in setOf("walk", "walking", "hiking", "hike") -> "walking"
        "strength" in t || "weight" in t || "gym" in t -> "strength"
        "calisthenics" in t || "bodyweight" in t -> "calisthenics"
        "conditioning" in t || "circuit" in t || "hiit" in t -> "conditioning"
        else -> "other"
    }
}

private fun todayRecordedColor(type: String): Color = when (todayRecordedKind(type)) {
    "running" -> TodayLook.lime
    "strength" -> TodayLook.coral
    "calisthenics" -> TodayLook.lilac
    "conditioning" -> TodayLook.azure
    else -> TodayLook.teal
}

private fun todayDisciplineColor(value: String): Color = when (value.lowercase(Locale.ROOT)) {
    "run", "running" -> TodayLook.lime
    "str", "strength" -> TodayLook.coral
    "cal", "calisthenics", "body", "bodyweight" -> TodayLook.lilac
    "cond", "conditioning" -> TodayLook.azure
    else -> TodayLook.teal
}

@Composable
private fun TodayHeader(onOpenMore: () -> Unit, online: Boolean, loading: Boolean) {
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
            if (!online || loading) {
                Text(
                    if (!online) "Offline" else "Loading",
                    color = if (!online) TodayLook.coral else TodayLook.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 9.dp).semantics {
                        contentDescription = if (!online) "Offline, showing saved data"
                            else "Loading training data"
                    }
                )
            }
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
    markers: Map<LocalDate, TodayDayMarker>,
    today: LocalDate,
    onToday: () -> Unit,
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
            if (date != today) {
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToday()
                    },
                    shape = RoundedCornerShape(15.dp),
                    color = Color(0xFF252B27),
                    modifier = Modifier.height(34.dp).testTag("today-return")
                ) {
                    Box(
                        Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Today", color = TodayLook.lime,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
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
                // Calendar dates remain in a fixed-height row above status dots.
                Modifier.align(Alignment.TopStart).offset(x = indicatorX, y = 24.dp)
                    .size(diameter).background(accent, CircleShape)
                    .testTag("today-date-indicator")
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                val letters = listOf("M", "T", "W", "T", "F", "S", "S")
                (0..6).forEach { idx ->
                    val day = monday.plusDays(idx.toLong())
                    val isSelected = day == date
                    val marker = markers[day] ?: TodayDayMarker(false, false)
                    Column(
                        modifier = Modifier.weight(1f).clickable { selectDay(day) }
                            .testTag("today-day-" + idx)
                            .semantics {
                                contentDescription = day.format(
                                    DateTimeFormatter.ofPattern(
                                        "EEEE d MMMM", Locale.getDefault()
                                    )
                                ) + when {
                                    marker.completed && marker.pending -> ", completed and planned"
                                    marker.completed -> ", completed"
                                    marker.pending -> ", planned"
                                    else -> ""
                                }
                                selected = isSelected
                                role = Role.Button
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            letters[idx], fontSize = 11.sp,
                            color = if (isSelected) accent else TodayLook.muted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.height(17.dp)
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
                        Row(
                            modifier = Modifier.height(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (marker.completed) {
                                Box(Modifier.size(5.dp)
                                    .background(TodayLook.lime, CircleShape))
                            }
                            if (marker.pending) {
                                Box(Modifier.size(5.dp)
                                    .background(Color(0xFF8B9298), CircleShape))
                            }
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


/** Null measurements stay null, not fabricated zeroes. */
private data class TodayActualTotals(
    val distanceM: Double?,
    val durationS: Double?,
    val partialDistance: Boolean,
    val partialDuration: Boolean
)

private fun todayActualTotals(
    activities: List<ActivityRecord>,
    logs: List<ConfirmedWorkout>,
    runningOnly: Boolean = false
): TodayActualTotals {
    val records = activities.filter {
        !runningOnly || todayRecordedKind(it.type) == "running"
    }
    val native = logs.filter {
        !runningOnly || todayHeroDiscipline(it.discipline) == "running"
    }
    val distances = records.mapNotNull { it.distanceM?.takeIf(Double::isFinite) } +
        native.mapNotNull { it.distanceM?.takeIf(Double::isFinite) }
    val durations = records.mapNotNull { it.durationS?.takeIf(Double::isFinite) } +
        native.mapNotNull { it.durationSeconds?.takeIf(Double::isFinite) }
    val count = records.size + native.size
    return TodayActualTotals(
        distances.takeIf { it.isNotEmpty() }?.sum(),
        durations.takeIf { it.isNotEmpty() }?.sum(),
        distances.size < count,
        durations.size < count
    )
}

private fun todayKindLabel(kind: String): String = when (kind) {
    "running" -> "Running"
    "strength" -> "Strength"
    "calisthenics" -> "Calisthenics"
    "conditioning" -> "Conditioning"
    "walking" -> "Walking"
    else -> "Other"
}

/**
 * Multiple saved activities: one compact neutral summary, not a tall list.
 * The individual source records and native logs appear only on View Sessions.
 */
@Composable
private fun TodayMultiRecordedSummary(
    date: LocalDate,
    notchFraction: Float,
    activities: List<ActivityRecord>,
    logs: List<ConfirmedWorkout>,
    plans: List<PlanSnapshot>,
    onOpenActivity: (String) -> Unit,
    onOpenWorkout: (String, String) -> Unit
) {
    val count = activities.size + logs.size
    val kinds = activities.map { todayRecordedKind(it.type) } +
        logs.map { todayHeroDiscipline(it.discipline) }
    val uniqueKinds = kinds.distinct()
    val kind = uniqueKinds.singleOrNull() ?: "mixed"
    val totals = remember(activities, logs) { todayActualTotals(activities, logs) }
    val runningTotals = remember(activities, logs) {
        todayActualTotals(activities, logs, runningOnly = true)
    }
    val title = when (kind) {
        "running" -> count.toString() + " runs"
        else -> count.toString() + " sessions"
    }
    var showDetails by remember(date, activities, logs) { mutableStateOf(false) }
    val shape = remember(notchFraction) { todayNotchedShape(notchFraction) }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 170.dp)
            .clip(shape).background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF14191A), Color(0xFF1B2223), Color(0xFF242C2C))
                )
            )
            .border(1.dp, Color(0xFF526258), shape)
            .testTag("today-recorded-summary")
    ) {
        TodayRecordedArtwork(
            kind = if (kind == "mixed") "mixed" else kind,
            accent = Color(0xFF8C9E91),
            modifier = Modifier.matchParentSize().alpha(.22f)
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF14191A), Color(0xDD14191A), Color.Transparent)
                )
            )
        )
        Column(
            Modifier.fillMaxWidth().padding(
                start = 18.dp, end = 16.dp, top = 25.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = TodayLook.lime, fontSize = 18.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (kind == "mixed") "TRAINING COMPLETED"
                    else "RECORDED " + todayKindLabel(kind).uppercase(Locale.ROOT),
                    color = TodayLook.lime, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
            Text(
                title, color = TodayLook.text, fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp
            )
            if (kind == "mixed") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uniqueKinds.take(2).forEach { discipline ->
                        val act = activities.filter { todayRecordedKind(it.type) == discipline }
                        val native = logs.filter {
                            todayHeroDiscipline(it.discipline) == discipline
                        }
                        val group = todayActualTotals(act, native)
                        val value = when {
                            discipline == "running" && group.distanceM != null ->
                                String.format(Locale.getDefault(), "%.1f km", group.distanceM / 1000)
                            group.durationS != null -> todayTimeLabel(group.durationS)
                            else -> (act.size + native.size).toString() + " sessions"
                        }
                        Column(
                            Modifier.weight(1f).background(
                                Color(0xFF2B3334), RoundedCornerShape(10.dp)
                            ).padding(horizontal = 9.dp, vertical = 7.dp)
                        ) {
                            Text(todayKindLabel(discipline), color = TodayLook.muted,
                                fontSize = 10.sp, maxLines = 1)
                            Text(value, color = TodayLook.text, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    if (uniqueKinds.size > 2) {
                        Text("+" + (uniqueKinds.size - 2).toString(),
                            color = TodayLook.muted, fontSize = 11.sp)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    if (kind == "running" && runningTotals.distanceM != null) {
                        Column {
                            Text(if (runningTotals.partialDistance) "Known distance" else "Distance",
                                color = TodayLook.muted, fontSize = 10.sp)
                            Text(
                                String.format(Locale.getDefault(), "%.2f km",
                                    runningTotals.distanceM / 1000),
                                color = TodayLook.text, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (totals.durationS != null) {
                        Column {
                            Text(if (totals.partialDuration) "Known time" else "Duration",
                                color = TodayLook.muted, fontSize = 10.sp)
                            Text(todayTimeLabel(totals.durationS), color = TodayLook.text,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (totals.durationS == null &&
                        (kind != "running" || runningTotals.distanceM == null)
                    ) Text("Metrics unavailable", color = TodayLook.muted, fontSize = 12.sp)
                }
            }
            Surface(
                onClick = { showDetails = true },
                shape = RoundedCornerShape(24.dp),
                color = TodayLook.lime,
                modifier = Modifier.height(42.dp).testTag("today-view-sessions")
            ) {
                Row(
                    Modifier.padding(horizontal = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("›", color = TodayLook.black, fontSize = 19.sp)
                    Text("View sessions", color = TodayLook.black,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showDetails) {
        ModalBottomSheet(
            onDismissRequest = { showDetails = false },
            containerColor = TodayLook.card
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 19.dp, end = 19.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                    color = TodayLook.muted, fontSize = 12.sp
                )
                Text(title, color = TodayLook.text, fontSize = 21.sp,
                    fontWeight = FontWeight.Bold)
                activities.forEach { activity ->
                    TodayRecordedRow(
                        title = activity.title,
                        subtitle = listOfNotNull(
                            activity.distanceM?.let { activity.distanceLabel },
                            activity.durationS?.let { activity.durationLabel }
                        ).joinToString(" · ").ifBlank { activity.sourceLabel },
                        tint = todayRecordedColor(activity.type),
                        onClick = {
                            showDetails = false
                            onOpenActivity(activity.id)
                        }
                    )
                }
                logs.forEach { log ->
                    val session = plans.firstOrNull { it.id == log.planId }
                        ?.sessions?.firstOrNull { it.id == log.sessionId }
                    TodayRecordedRow(
                        title = session?.effectiveTitle ?: "Completed workout",
                        subtitle = listOfNotNull(
                            log.distanceM?.let {
                                String.format(Locale.getDefault(), "%.2f km", it / 1000)
                            },
                            log.durationSeconds?.let { ActivityRecord.duration(it) }
                        ).joinToString(" · ").ifBlank { "Completed log" },
                        tint = todayDisciplineColor(log.discipline),
                        onClick = {
                            showDetails = false
                            onOpenWorkout(log.planId, log.sessionId)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Today may have genuine recorded training AND another planned workout.
 * Surface both states without claiming that the pending workout is completed.
 */
@Composable
private fun TodayMixedDayCard(
    date: LocalDate,
    notchFraction: Float,
    activities: List<ActivityRecord>,
    logs: List<ConfirmedWorkout>,
    pending: List<PlannedSession>,
    plans: List<PlanSnapshot>,
    onOpenActivity: (String) -> Unit,
    onOpenWorkout: (String, String) -> Unit
) {
    val recordedCount = activities.size + logs.size
    val next = pending.firstOrNull()
    val shape = remember(notchFraction) { todayNotchedShape(notchFraction) }
    val runningTotals = remember(activities, logs) {
        todayActualTotals(activities, logs, runningOnly = true)
    }
    var showAll by remember(date, activities, logs, pending) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF151A1B), Color(0xFF202425), Color(0xFF2C3130))
                )
            )
            .border(1.dp, Color(0xFF52615B), shape)
            .testTag("today-mixed-progress")
    ) {
        TodayRecordedArtwork(
            kind = "mixed", accent = TodayLook.teal,
            modifier = Modifier.matchParentSize().alpha(.28f)
        )
        Column(
            Modifier.fillMaxWidth().padding(
                start = 18.dp, end = 16.dp, top = 26.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = TodayLook.lime, fontSize = 18.sp)
                Spacer(Modifier.width(7.dp))
                Text("TODAY'S TRAINING", color = TodayLook.lime,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                (recordedCount + pending.size).toString() + " sessions",
                color = TodayLook.text, fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.weight(1f)
                        .background(Color(0xFF29332D), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("✓ Recorded", color = TodayLook.lime, fontSize = 10.sp)
                    Text(
                        if (runningTotals.distanceM != null) String.format(
                            Locale.getDefault(), "%.1f km",
                            runningTotals.distanceM / 1000
                        ) else recordedCount.toString() + " done",
                        color = TodayLook.text, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    Modifier.weight(1f)
                        .background(Color(0xFF343033), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("● Remaining", color = TodayLook.coral, fontSize = 10.sp)
                    Text(
                        next?.effectiveTitle ?: pending.size.toString() + " pending",
                        color = TodayLook.text, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (next != null) {
                    Surface(
                        onClick = {
                            plans.firstOrNull { plan ->
                                plan.sessions.any { it.id == next.id }
                            }?.let { onOpenWorkout(it.id, next.id) }
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = TodayLook.lime,
                        modifier = Modifier.height(42.dp)
                    ) {
                        Box(
                            Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Open next", color = TodayLook.black,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Surface(
                    onClick = { showAll = true },
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF303638),
                    modifier = Modifier.height(42.dp).testTag("today-mixed-view-all")
                ) {
                    Box(
                        Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("View sessions", color = TodayLook.text,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showAll) {
        ModalBottomSheet(
            onDismissRequest = { showAll = false },
            containerColor = TodayLook.card
        ) {
            Column(
                Modifier.fillMaxWidth().padding(
                    start = 19.dp, end = 19.dp, bottom = 25.dp
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Today's sessions", color = TodayLook.text,
                    fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 490.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activities.forEach { activity ->
                        TodayRecordedRow(
                            title = activity.title,
                            subtitle = listOfNotNull(
                                activity.distanceM?.let { activity.distanceLabel },
                                activity.durationS?.let { activity.durationLabel }
                            ).joinToString(" · ").ifBlank { activity.sourceLabel },
                            tint = TodayLook.lime,
                            onClick = {
                                showAll = false
                                onOpenActivity(activity.id)
                            }
                        )
                    }
                    logs.forEach { log ->
                        val original = plans.firstOrNull { it.id == log.planId }
                            ?.sessions?.firstOrNull { it.id == log.sessionId }
                        TodayRecordedRow(
                            title = original?.effectiveTitle ?: "Completed workout",
                            subtitle = "Completed" +
                                (log.durationSeconds?.let {
                                    " · " + ActivityRecord.duration(it)
                                } ?: ""),
                            tint = todayDisciplineColor(log.discipline),
                            onClick = {
                                showAll = false
                                onOpenWorkout(log.planId, log.sessionId)
                            }
                        )
                    }
                    pending.forEach { session ->
                        val plan = plans.firstOrNull { p ->
                            p.sessions.any { it.id == session.id }
                        }
                        TodayRecordedRow(
                            title = session.effectiveTitle,
                            subtitle = todaySessionDetails(session) + " · " +
                                session.status.replace('_', ' '),
                            tint = todayDisciplineColor(session.discipline),
                            onClick = {
                                showAll = false
                                if (plan != null) onOpenWorkout(plan.id, session.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The calendar's past dates show actual saved recordings and confirmed workout
 * logs, never a historical "Today's Workout" prescription or decorative GPS map.
 * Native logs are already filtered to avoid linked-activity double counting.
 */
@Composable
private fun TodayRecordedTraining(
    selectedDate: LocalDate,
    signedOut: Boolean,
    online: Boolean,
    accent: Color,
    notchFraction: Float,
    activities: List<ActivityRecord>,
    logged: List<ConfirmedWorkout>,
    plans: List<PlanSnapshot>,
    onOpenActivity: (String) -> Unit,
    onOpenWorkout: (String, String) -> Unit,
    onSignIn: () -> Unit
) {
    val shape = remember(notchFraction) { todayNotchedShape(notchFraction) }
    val count = activities.size + logged.size
    if (count > 1) {
        TodayMultiRecordedSummary(
            date = selectedDate,
            notchFraction = notchFraction,
            activities = activities,
            logs = logged,
            plans = plans,
            onOpenActivity = onOpenActivity,
            onOpenWorkout = onOpenWorkout
        )
        return
    }
    val firstActivity = activities.firstOrNull()
    val firstLog = logged.firstOrNull()
    val firstLogSession = firstLog?.let { entry ->
        plans.firstOrNull { it.id == entry.planId }
            ?.sessions?.firstOrNull { it.id == entry.sessionId }
    }
    // All classification comes from persisted activity types or confirmed
    // plan logs; artwork is generic to that discipline, not a route/sensor map.
    val kinds = activities.map { todayRecordedKind(it.type) } +
        logged.map { todayHeroDiscipline(it.discipline) }
    val kind = when {
        count == 0 -> "empty"
        kinds.distinct().size > 1 -> "mixed"
        else -> kinds.firstOrNull() ?: "other"
    }
    val cardAccent = when (kind) {
        "running" -> TodayLook.lime
        "strength" -> TodayLook.coral
        "calisthenics" -> TodayLook.lilac
        "conditioning" -> TodayLook.azure
        "mixed" -> TodayLook.lilac
        else -> accent
    }
    val background = when (kind) {
        "running" -> listOf(Color(0xFF101915), Color(0xFF1A281A), Color(0xFF354A1C))
        "strength" -> listOf(Color(0xFF191315), Color(0xFF302020), Color(0xFF612C2B))
        "calisthenics" -> listOf(Color(0xFF181421), Color(0xFF2B2039), Color(0xFF4A3064))
        "conditioning" -> listOf(Color(0xFF111A23), Color(0xFF1A3040), Color(0xFF28475A))
        "mixed" -> listOf(Color(0xFF14151A), Color(0xFF232232), Color(0xFF343247))
        "empty" -> listOf(Color(0xFF121617), Color(0xFF1A2021), Color(0xFF242B2C))
        else -> listOf(Color(0xFF131719), Color(0xFF192528), Color(0xFF2B3839))
    }
    val header = when {
        signedOut -> "TRAINING HISTORY"
        count == 0 -> "NO SAVED ACTIVITY"
        count > 1 -> "RECORDED SESSIONS"
        firstActivity != null -> "RECORDED ACTIVITY"
        else -> "COMPLETED WORKOUT"
    }
    val title = when {
        signedOut -> "Sign in"
        count == 0 -> "Nothing saved"
        count > 1 -> "$count recorded sessions"
        firstActivity != null -> firstActivity.title
        firstLogSession != null -> firstLogSession.effectiveTitle
        else -> "Completed workout"
    }
    val summary = when {
        signedOut -> "Your activity is private."
        count == 0 && !online -> "Offline · saved records only"
        count == 0 -> "No saved activity for this date."
        count > 1 -> "Your completed sessions on this date."
        firstActivity != null -> listOfNotNull(
            firstActivity.distanceM?.let { firstActivity.distanceLabel },
            firstActivity.durationS?.let { firstActivity.durationLabel }
        ).joinToString("  ·  ").ifBlank { "Metrics not recorded" }
        firstLog != null -> listOfNotNull(
            firstLog.distanceM?.let {
                String.format(Locale.getDefault(), "%.2f km", it / 1000)
            },
            firstLog.durationSeconds?.let { ActivityRecord.duration(it) }
        ).joinToString("  ·  ").ifBlank { "Confirmed workout log" }
        else -> ""
    }
    val pace = if (count == 1 && firstActivity?.supportsPace == true) {
        when {
            firstActivity.recordedPace != null -> "Recorded pace · " + firstActivity.paceLabel
            firstActivity.garminMovingPaceSPerKm != null ->
                "Moving pace · " + firstActivity.detailPaceLabel
            else -> null
        }
    } else null
    Box(
        Modifier.fillMaxWidth().heightIn(min = if (count == 1) 211.dp else 173.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(background))
            .border(1.dp, cardAccent.copy(alpha = .64f), shape)
            .testTag("today-recorded-hero")
    ) {
        TodayRecordedArtwork(kind = kind, accent = cardAccent, modifier = Modifier.matchParentSize())
        // Maintain strong text contrast while keeping the equipment visible
        // on the card's right side, including on 360dp screens.
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    listOf(
                        background.first().copy(alpha = .88f),
                        background.first().copy(alpha = .57f),
                        Color.Transparent
                    )
                )
            )
        )
        Column(
            Modifier.fillMaxWidth().padding(
                start = 19.dp, end = 16.dp, top = 28.dp, bottom = 17.dp
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = cardAccent, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    header, color = cardAccent,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                title, color = TodayLook.text,
                fontWeight = FontWeight.ExtraBold, fontSize = 24.sp,
                lineHeight = 29.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(if (count == 1 && kind != "empty") .76f else 1f)
            )
            Text(
                summary, color = TodayLook.text.copy(alpha = .86f), fontSize = 12.sp,
                lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(if (count == 1 && kind != "empty") .73f else 1f)
            )
            if (pace != null) {
                Text(
                    pace, color = cardAccent.copy(alpha = .92f),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.78f)
                )
            }
            if (count > 1) {
                Spacer(Modifier.height(1.dp))
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
                            entry.durationSeconds?.let { ActivityRecord.duration(it) }
                        ).joinToString(" · ").ifBlank { "Confirmed workout log" },
                        tint = todayDisciplineColor(entry.discipline),
                        onClick = { onOpenWorkout(entry.planId, entry.sessionId) }
                    )
                }
            }
            if (signedOut || count == 1) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = {
                        when {
                            signedOut -> onSignIn()
                            firstActivity != null -> onOpenActivity(firstActivity.id)
                            firstLog != null -> onOpenWorkout(firstLog.planId, firstLog.sessionId)
                        }
                    },
                    shape = RoundedCornerShape(26.dp),
                    color = TodayLook.lime,
                    modifier = Modifier.height(42.dp).testTag("today-recorded-action")
                ) {
                    Row(
                        Modifier.padding(horizontal = 19.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("›", color = TodayLook.black, fontSize = 21.sp)
                        Text(
                            when {
                                signedOut -> "Sign in"
                                firstActivity != null -> "View activity"
                                else -> "View workout"
                            },
                            color = TodayLook.black,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visual language for completed activities: dark, results-led and distinctive.
 * These are decorative vector illustrations, NOT athlete equipment photos,
 * imported routes, recorded form, or evidence of a particular shoe brand.
 */
@Composable
private fun TodayRecordedArtwork(
    kind: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (kind == "empty") return@Canvas
        val w = size.width
        val h = size.height
        val glowCenter = Offset(w * .88f, h * .60f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = .23f), Color.Transparent),
                center = glowCenter,
                radius = w * .52f
            ),
            radius = w * .52f,
            center = glowCenter
        )
        val backdrop = Path().apply {
            moveTo(w * .39f, h)
            cubicTo(w * .51f, h * .80f, w * .73f, h * .87f, w, h * .56f)
            lineTo(w, h)
            close()
        }
        val floorTint = when (kind) {
            "running" -> Color(0xFF608525)
            "strength" -> Color(0xFF9C443F)
            "calisthenics" -> Color(0xFF8761B7)
            "conditioning" -> Color(0xFF416A89)
            else -> Color(0xFF716889)
        }
        drawPath(
            backdrop,
            Brush.verticalGradient(
                listOf(floorTint.copy(alpha = .08f), floorTint.copy(alpha = .41f)),
                startY = h * .48f, endY = h
            )
        )
        when (kind) {
            "running" -> {
                // Side-profile trail shoe: toe lower-left, heel upper-right.
                // Two-layer sole separates it clearly from decorative hills.
                val outerSole = Path().apply {
                    moveTo(w * .54f, h * .76f)
                    cubicTo(w * .64f, h * .79f, w * .74f, h * .80f, w * .85f, h * .75f)
                    cubicTo(w * .93f, h * .72f, w * .97f, h * .68f, w * .99f, h * .70f)
                    lineTo(w * .995f, h * .77f)
                    cubicTo(w * .90f, h * .87f, w * .69f, h * .92f, w * .57f, h * .87f)
                    cubicTo(w * .53f, h * .85f, w * .52f, h * .81f, w * .54f, h * .76f)
                    close()
                }
                drawPath(outerSole, Color(0xFF1C3113))
                val midSole = Path().apply {
                    moveTo(w * .54f, h * .75f)
                    cubicTo(w * .67f, h * .80f, w * .79f, h * .79f, w * .94f, h * .68f)
                    lineTo(w * .995f, h * .70f)
                    cubicTo(w * .93f, h * .82f, w * .72f, h * .88f, w * .57f, h * .83f)
                    close()
                }
                drawPath(
                    midSole,
                    Brush.verticalGradient(
                        listOf(Color(0xFFDEF59A), Color(0xFF83AD39), Color(0xFF344F1E)),
                        startY = h * .68f, endY = h * .89f
                    )
                )
                val upper = Path().apply {
                    moveTo(w * .55f, h * .74f)
                    cubicTo(w * .58f, h * .67f, w * .66f, h * .61f, w * .70f, h * .58f)
                    lineTo(w * .76f, h * .42f)
                    cubicTo(w * .79f, h * .36f, w * .83f, h * .38f, w * .86f, h * .45f)
                    cubicTo(w * .90f, h * .50f, w * .96f, h * .52f, w * .99f, h * .64f)
                    lineTo(w * .98f, h * .70f)
                    cubicTo(w * .89f, h * .76f, w * .72f, h * .83f, w * .59f, h * .79f)
                    close()
                }
                drawPath(
                    upper,
                    Brush.linearGradient(
                        listOf(Color(0xFFBEEA62), Color(0xFF779C35), Color(0xFF2B4A1B)),
                        start = Offset(w * .66f, h * .58f),
                        end = Offset(w, h * .79f)
                    )
                )
                val heelPanel = Path().apply {
                    moveTo(w * .80f, h * .43f)
                    cubicTo(w * .84f, h * .41f, w * .89f, h * .50f, w * .91f, h * .56f)
                    lineTo(w * .86f, h * .66f)
                    lineTo(w * .78f, h * .63f)
                    close()
                }
                drawPath(heelPanel, Color(0xFF33531F).copy(alpha = .87f))
                val toe = Path().apply {
                    moveTo(w * .57f, h * .73f)
                    cubicTo(w * .61f, h * .70f, w * .67f, h * .69f, w * .71f, h * .72f)
                    cubicTo(w * .68f, h * .78f, w * .60f, h * .80f, w * .56f, h * .77f)
                    close()
                }
                drawPath(toe, Color(0xFF456A25).copy(alpha = .87f))
                val sideStripe = Path().apply {
                    moveTo(w * .70f, h * .70f)
                    cubicTo(w * .79f, h * .69f, w * .85f, h * .60f, w * .90f, h * .57f)
                    cubicTo(w * .83f, h * .68f, w * .75f, h * .77f, w * .66f, h * .75f)
                    close()
                }
                drawPath(sideStripe, Color(0xFFDCF883).copy(alpha = .80f))
                // Tongue and four diagonal lace segments, all illustration only.
                drawLine(
                    Color(0xFF1E3917), Offset(w * .73f, h * .58f),
                    Offset(w * .83f, h * .63f), strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                listOf(
                    .63f to .75f, .66f to .78f, .69f to .81f, .72f to .84f
                ).forEachIndexed { index, (left, right) ->
                    val y = h * (.65f - index * .023f)
                    drawLine(
                        Color(0xFFE4FAA6), Offset(w * left, y),
                        Offset(w * right, y + h * .034f),
                        strokeWidth = 1.65.dp.toPx(), cap = StrokeCap.Round
                    )
                }
                val soleHighlight = Path().apply {
                    moveTo(w * .56f, h * .82f)
                    cubicTo(w * .68f, h * .86f, w * .88f, h * .82f, w * .98f, h * .74f)
                }
                drawPath(
                    soleHighlight, Color(0xFFD9FC85),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.9.dp.toPx(), cap = StrokeCap.Round
                    )
                )
            }
            "strength" -> {
                // Coral metallic dumbbell, matching the planned-card motif
                // but lit against a darker completed-workout backdrop.
                rotate(degrees = -26f, pivot = Offset(w * .83f, h * .65f)) {
                    val dark = Color(0xFF522322)
                    val face = Color(0xFFD56B60)
                    drawRoundRect(
                        color = Color(0xFFB9574D),
                        topLeft = Offset(w * .58f, h * .63f),
                        size = androidx.compose.ui.geometry.Size(w * .45f, h * .073f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * .03f)
                    )
                    listOf(.66f, .73f, .91f, .99f).forEachIndexed { index, x ->
                        val height = if (index == 0 || index == 3) .24f else .35f
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(dark, face, Color(0xFF812F2B))
                            ),
                            topLeft = Offset(w * x, h * (.67f - height * .50f)),
                            size = androidx.compose.ui.geometry.Size(w * .065f, h * height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * .024f)
                        )
                    }
                    drawLine(
                        Color(0xFFF1A79A).copy(alpha = .78f),
                        Offset(w * .73f, h * .61f), Offset(w * .91f, h * .61f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
            "calisthenics" -> {
                // Calisthenics bar frame, softly luminous on dark violet.
                val dark = Color(0xFF352244)
                val light = Color(0xFFCC9DFC)
                listOf(.73f, .96f).forEach { x ->
                    drawLine(
                        color = dark, start = Offset(w * x, h * .40f),
                        end = Offset(w * x, h * .98f),
                        strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round
                    )
                    drawLine(
                        color = light, start = Offset(w * x, h * .39f),
                        end = Offset(w * x, h * .98f),
                        strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
                    )
                    drawCircle(light, radius = 5.dp.toPx(), center = Offset(w * x, h * .39f))
                }
                drawLine(
                    color = dark, start = Offset(w * .71f, h * .39f),
                    end = Offset(w * .985f, h * .39f),
                    strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFE2B9FF),
                    start = Offset(w * .71f, h * .39f),
                    end = Offset(w * .985f, h * .39f),
                    strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round
                )
            }
            "conditioning" -> {
                val line = Path().apply {
                    moveTo(w * .60f, h * .73f)
                    lineTo(w * .69f, h * .73f)
                    lineTo(w * .74f, h * .55f)
                    lineTo(w * .79f, h * .84f)
                    lineTo(w * .86f, h * .60f)
                    lineTo(w * .90f, h * .73f)
                    lineTo(w, h * .73f)
                }
                drawPath(
                    line, Color(0xFFA6DAFF),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4.dp.toPx(), cap = StrokeCap.Round
                    )
                )
            }
            else -> {
                listOf(Color(0xFFCEA8FF), Color(0xFF9ECFD1), Color(0xFFFF9E8A))
                    .forEachIndexed { index, ringColor ->
                        drawCircle(
                            color = ringColor.copy(alpha = .22f),
                            radius = w * (.15f + index * .08f),
                            center = Offset(w * .90f, h * .75f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 4.dp.toPx()
                            )
                        )
                    }
            }
        }
        // Gentle foreground sweep anchors the illustration to the card edge.
        val sweep = Path().apply {
            moveTo(w * .48f, h)
            cubicTo(w * .68f, h * .86f, w * .78f, h * .94f, w, h * .76f)
            lineTo(w, h)
            close()
        }
        drawPath(sweep, floorTint.copy(alpha = .12f))
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

/**
 * Multiple planned sessions stay compact. The first two are quick previews;
 * the full persisted plan appears in a scrollable on-demand sheet.
 */
@Composable
private fun TodayMultiPlannedCard(
    date: LocalDate,
    accent: Color,
    notchFraction: Float,
    sessions: List<PlannedSession>,
    reduceMotion: Boolean,
    onOpen: (PlannedSession) -> Unit
) {
    val shape = remember(notchFraction) { todayNotchedShape(notchFraction) }
    val pending = sessions.filter { it.status in setOf("planned", "in_progress") }
    val completed = sessions.count { it.status == "completed" }
    val next = pending.firstOrNull()
    val mixed = sessions.map { todayHeroDiscipline(it.discipline) }.distinct().size > 1
    val dayIsToday = date == LocalDate.now()
    var showAll by remember(date, sessions) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent, accent.copy(alpha = .97f), accent.copy(alpha = .79f))
                )
            )
            .border(1.dp, Color(0x55000000), shape)
            .testTag("today-planned-multiple")
    ) {
        TodayDecorativeArt(
            accent = accent,
            kind = if (mixed) "mixed" else sessions.firstOrNull()?.discipline ?: "general",
            modifier = Modifier.matchParentSize().alpha(.36f)
        )
        Column(
            Modifier.fillMaxWidth().padding(
                start = 19.dp, end = 17.dp, top = 23.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TodayDisciplineGlyph(
                    kind = if (mixed) "mixed" else sessions.first().discipline,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (dayIsToday) "TODAY'S SESSIONS" else "UPCOMING SESSIONS",
                    color = TodayLook.black, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Text(
                sessions.size.toString() + " workouts",
                color = TodayLook.black, fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp, lineHeight = 30.sp
            )
            Text(
                completed.toString() + " done · " +
                    pending.size.toString() + " remaining",
                color = TodayLook.black.copy(alpha = .77f), fontSize = 12.sp
            )
            sessions.take(2).forEach { session ->
                Surface(
                    onClick = { onOpen(session) },
                    shape = RoundedCornerShape(13.dp),
                    color = Color(0xE7222526),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 43.dp)
                        .testTag("today-session-" + session.id)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("●", color = todayDisciplineColor(session.discipline),
                            fontSize = 14.sp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.effectiveTitle, color = TodayLook.text,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                todaySessionDetails(session), color = TodayLook.muted,
                                fontSize = 10.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            when (session.status) {
                                "completed" -> "✓ Done"
                                "in_progress" -> "Active"
                                else -> "Planned"
                            },
                            color = if (session.status == "completed")
                                TodayLook.lime else TodayLook.muted,
                            fontSize = 10.sp, maxLines = 1
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (next != null) {
                    Surface(
                        onClick = { onOpen(next) },
                        shape = RoundedCornerShape(25.dp),
                        color = Color(0xFF252627),
                        modifier = Modifier.height(41.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("›", color = TodayLook.text, fontSize = 18.sp)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (dayIsToday) "Open next" else "View next",
                                color = TodayLook.text, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Surface(
                    onClick = { showAll = true },
                    shape = RoundedCornerShape(25.dp),
                    color = Color(0xFF252627),
                    modifier = Modifier.height(41.dp).testTag("today-planned-view-all")
                ) {
                    Row(
                        Modifier.padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("View sessions", color = TodayLook.text,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showAll) {
        ModalBottomSheet(
            onDismissRequest = { showAll = false },
            containerColor = TodayLook.card
        ) {
            Column(
                Modifier.fillMaxWidth().padding(
                    start = 19.dp, end = 19.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                    color = TodayLook.muted, fontSize = 12.sp
                )
                Text(
                    sessions.size.toString() + " planned workouts",
                    color = TodayLook.text, fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 490.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sessions.forEach { session ->
                        TodayRecordedRow(
                            title = session.effectiveTitle,
                            subtitle = todaySessionDetails(session) + " · " +
                                session.status.replace('_', ' '),
                            tint = todayDisciplineColor(session.discipline),
                            onClick = {
                                showAll = false
                                onOpen(session)
                            }
                        )
                    }
                }
            }
        }
    }
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
    if (scheduled.size > 1 && !signedOut) {
        TodayMultiPlannedCard(
            date = date, accent = accent, notchFraction = notchFraction,
            sessions = scheduled, reduceMotion = reduceMotion, onOpen = onOpen
        )
        return
    }
    val shape = remember(notchFraction) { todayNotchedShape(notchFraction) }
    val multi = scheduled.size > 1
    val primary = scheduled.firstOrNull { it.status != "completed" } ?: scheduled.firstOrNull()
    val content = TodayHeroSnapshot(date, scheduled, planAvailable, signedOut)
    Box(
        Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = tween(if (reduceMotion) 0 else 240))
            .heightIn(min = if (multi) 275.dp else 184.dp)
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
        val limeButton = !multi && (signedOut || !planAvailable ||
            primary?.discipline?.let(::todayHeroDiscipline) == "running")
        val buttonInk = if (limeButton) TodayLook.black else TodayLook.text

        Column(
            Modifier.fillMaxWidth().padding(
                start = 19.dp, end = 16.dp, top = 23.dp, bottom = 15.dp
            ),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TodayDisciplineGlyph(
                    kind = when {
                        scheduled.isEmpty() -> "recovery"
                        multi -> "mixed"
                        else -> primary?.discipline ?: "running"
                    },
                    modifier = Modifier.size(17.dp)
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
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TodayLook.black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (primary != null && !multi) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (primary.status) {
                            "completed" -> "✓ Done"
                            "in_progress" -> "● Active"
                            else -> "Planned"
                        },
                        color = TodayLook.black.copy(alpha = .76f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
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
                            Text(
                                when (session.status) {
                                    "completed" -> "✓ Done"
                                    "in_progress" -> "● Active"
                                    else -> "Planned"
                                },
                                color = if (session.status == "completed")
                                    TodayLook.lime else TodayLook.muted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Text("›", color = TodayLook.text, fontSize = 19.sp)
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
            Spacer(Modifier.height(if (multi) 3.dp else 6.dp))
            Surface(
                onClick = onPrimary,
                shape = RoundedCornerShape(28.dp),
                color = if (limeButton) Color(0xFFEAFB77) else Color(0xFF28252B),
                modifier = Modifier.height(42.dp)
                    .testTag("today-workout-action")
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("▶", color = buttonInk, fontSize = 12.sp)
                    Text(button, color = buttonInk,
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

/** Tiny discipline-specific vector mark, not a stock athlete image. */
@Composable
private fun TodayDisciplineGlyph(kind: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ink = Color(0xFF1A2615)
        val stroke = 1.9.dp.toPx()
        when (todayHeroDiscipline(kind)) {
            "running" -> {
                drawCircle(ink, radius = w * .105f, center = Offset(w * .63f, h * .16f))
                drawLine(ink, Offset(w * .56f, h * .30f), Offset(w * .42f, h * .58f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(ink, Offset(w * .42f, h * .58f), Offset(w * .76f, h * .72f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(ink, Offset(w * .42f, h * .58f), Offset(w * .18f, h * .82f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(ink, Offset(w * .53f, h * .37f), Offset(w * .78f, h * .42f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(ink, Offset(w * .45f, h * .39f), Offset(w * .22f, h * .49f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "strength" -> {
                drawLine(ink, Offset(w * .13f, h * .50f), Offset(w * .87f, h * .50f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                listOf(.27f, .73f).forEach { x ->
                    drawLine(ink, Offset(w * x, h * .24f), Offset(w * x, h * .76f),
                        strokeWidth = 3.1.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            "calisthenics", "conditioning", "mixed" -> {
                drawCircle(ink, radius = w * .10f, center = Offset(w * .5f, h * .2f))
                drawLine(ink, Offset(w * .17f, h * .42f), Offset(w * .83f, h * .42f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(ink, Offset(w * .5f, h * .40f), Offset(w * .5f, h * .73f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(ink, Offset(w * .5f, h * .73f), Offset(w * .24f, h * .93f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(ink, Offset(w * .5f, h * .73f), Offset(w * .76f, h * .93f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
            }
            else -> {
                drawArc(
                    color = ink, startAngle = 40f, sweepAngle = 260f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke, cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

/** Decorative athletic artwork only, never a GPS route or invented athlete metric. */
@Composable
private fun TodayDecorativeArt(accent: Color, kind: String, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val running = kind in setOf("run", "running", "general")
        val strength = kind in setOf("str", "strength")
        val recovery = kind == "recovery"
        // Soft sunlit field keeps the foreground text legible on a pastel card.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = .32f), Color.White.copy(alpha = 0f)),
                center = Offset(w * .70f, h * .26f), radius = w * .55f
            ),
            radius = w * .55f,
            center = Offset(w * .70f, h * .26f)
        )
        if (running) {
            // Layered evergreen ridges, confined primarily to the card's right half.
            val distant = Path().apply {
                moveTo(w * .43f, h)
                lineTo(w * .58f, h * .68f)
                lineTo(w * .68f, h * .81f)
                lineTo(w * .89f, h * .26f)
                lineTo(w, h * .55f)
                lineTo(w, h)
                close()
            }
            drawPath(
                distant,
                Brush.verticalGradient(
                    listOf(Color(0xFF607E19).copy(alpha = .84f), Color(0xFF273E10)),
                    startY = h * .22f, endY = h
                )
            )
            val ridge = Path().apply {
                moveTo(w * .51f, h)
                lineTo(w * .69f, h * .54f)
                lineTo(w * .77f, h * .75f)
                lineTo(w * .94f, h * .43f)
                lineTo(w, h * .54f)
                lineTo(w, h)
                close()
            }
            drawPath(ridge, Color(0xFF47651B).copy(alpha = .95f))
            val foreground = Path().apply {
                moveTo(w * .48f, h)
                cubicTo(w * .64f, h * .83f, w * .70f, h * .92f, w * .79f, h * .70f)
                cubicTo(w * .91f, h * .54f, w * .96f, h * .65f, w, h * .57f)
                lineTo(w, h)
                close()
            }
            drawPath(
                foreground,
                Brush.verticalGradient(
                    listOf(Color(0xFF344D16), Color(0xFF15260F)),
                    startY = h * .55f, endY = h
                )
            )
            val softHorizon = Path().apply {
                moveTo(w * .32f, h)
                cubicTo(w * .51f, h * .92f, w * .58f, h * .85f, w * .76f, h * .93f)
                cubicTo(w * .86f, h * .98f, w * .93f, h * .89f, w, h * .84f)
                lineTo(w, h)
                close()
            }
            drawPath(softHorizon, Color(0xFF172A0E).copy(alpha = .28f))
        } else if (strength) {
            val shadow = Path().apply {
                moveTo(w * .53f, h)
                cubicTo(w * .63f, h * .69f, w * .88f, h * .37f, w, h * .47f)
                lineTo(w, h)
                close()
            }
            drawPath(shadow, Color(0xFF9D443C).copy(alpha = .22f))
            rotate(
                degrees = -35f,
                pivot = Offset(w * .82f, h * .67f)
            ) {
                val dark = Color(0xFF77372F)
                val shine = Color(0xFFB75549)
                drawRoundRect(
                    color = dark,
                    topLeft = Offset(w * .58f, h * .64f),
                    size = androidx.compose.ui.geometry.Size(w * .49f, h * .060f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * .023f)
                )
                listOf(.64f, .73f, .93f, 1.01f).forEachIndexed { index, x ->
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(dark, shine, dark)),
                        topLeft = Offset(w * x, h * (if (index % 3 == 0) .52f else .55f)),
                        size = androidx.compose.ui.geometry.Size(w * .065f, h * (if (index % 3 == 0) .30f else .24f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * .018f)
                    )
                }
            }
        } else if (recovery) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFF4E6FF).copy(alpha = .85f),
                        Color(0xFFD5BEF9).copy(alpha = .04f)),
                    center = Offset(w * .84f, h * .40f), radius = w * .25f
                ),
                radius = w * .25f, center = Offset(w * .84f, h * .40f)
            )
            val rear = Path().apply {
                moveTo(w * .35f, h)
                cubicTo(w * .56f, h * .68f, w * .67f, h * .80f, w * .81f, h * .57f)
                cubicTo(w * .92f, h * .45f, w * .96f, h * .54f, w, h * .47f)
                lineTo(w, h)
                close()
            }
            drawPath(rear, Color(0xFF9B80D5).copy(alpha = .38f))
            val front = Path().apply {
                moveTo(w * .30f, h)
                cubicTo(w * .53f, h * .81f, w * .67f, h * .94f, w * .80f, h * .76f)
                cubicTo(w * .91f, h * .64f, w * .98f, h * .73f, w, h * .66f)
                lineTo(w, h)
                close()
            }
            drawPath(front, Color(0xFF8165B7).copy(alpha = .34f))
        } else {
            // Mixed-discipline session: flowing tonal arcs rather than fake routes.
            repeat(3) { index ->
                drawCircle(
                    color = Color(0xFF3B2B35).copy(alpha = .12f + index * .04f),
                    radius = w * (.22f + index * .085f),
                    center = Offset(w * .94f, h * .82f)
                )
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
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(19.dp),
        color = TodayLook.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, TodayLook.outline),
        modifier = Modifier.fillMaxWidth().testTag("today-next-session")
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val accent = Path().apply {
                    moveTo(w * .70f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h * .94f)
                    cubicTo(w * .87f, h * .45f, w * .78f, h * .39f, w * .70f, 0f)
                    close()
                }
                drawPath(
                    accent,
                    Brush.linearGradient(
                        listOf(Color(0xFF9C3433).copy(alpha = .37f),
                            Color(0xFF692729).copy(alpha = .09f)),
                        start = Offset(w * .74f, 0f),
                        end = Offset(w, h)
                    )
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(31.dp)
                            .background(Color(0xFF3A2426), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▣", color = TodayLook.coral, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Next Session", fontSize = 11.sp, color = TodayLook.muted)
                        Text(
                            next?.effectiveTitle ?: if (signedOut) "Your schedule awaits"
                            else if (plan != null) "No upcoming sessions" else "Build your plan",
                            fontSize = 17.sp, lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold, color = TodayLook.text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        Modifier.size(27.dp)
                            .background(Color(0xFF343035), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("›", color = TodayLook.text, fontSize = 22.sp)
                    }
                }
                if (next != null) {
                    Text(
                        next.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) +
                            "  ·  " + todaySessionDetails(next),
                        color = TodayLook.muted, fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        if (signedOut) "Sign in to see your training history."
                        else if (plan == null) "Create an approved schedule to get started."
                        else "Check your training schedule for future workouts.",
                        color = TodayLook.muted, fontSize = 11.sp
                    )
                }
                if (total > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).height(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF3B3D42))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(completed.toFloat() / total.toFloat())
                                    .fillMaxHeight()
                                    .background(TodayLook.coral, RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "$completed of $total today",
                            color = TodayLook.muted, fontSize = 10.sp
                        )
                    }
                }
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
            Box(
                Modifier.size(31.dp)
                    .background(Color(0xFF272F24), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("▥", fontSize = 19.sp, color = TodayLook.lime)
            }
            Column(Modifier.weight(.8f)) {
                Text("Activity", fontSize = 16.sp, fontWeight = FontWeight.Bold,
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
            Box(
                Modifier.size(28.dp)
                    .background(Color(0xFF2E3224), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("✧", color = TodayLook.lime, fontSize = 17.sp)
            }
            Spacer(Modifier.width(9.dp))
            Text("Weekly summary", fontSize = 16.sp, fontWeight = FontWeight.Bold,
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
        Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 16.sp,
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
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B2E33))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

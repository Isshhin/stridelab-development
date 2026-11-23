package io.stridelab.privatealpha

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private object TimelineStyle {
    val background = Color(0xFF000000)
    val card = StrideLabVisualPalette.surface
    val cardRaised = StrideLabVisualPalette.surfaceRaised
    val border = StrideLabVisualPalette.border
    val ink = StrideLabVisualPalette.ink
    val secondary = StrideLabVisualPalette.secondary
    val muted = StrideLabVisualPalette.muted
    val lime = StrideLabVisualPalette.lime
}

internal data class UnifiedTrainingWorkout(val planId: String, val session: PlannedSession)
internal data class UnifiedTrainingDay(
    val date: LocalDate,
    val activities: List<DisplayActivity>,
    val workouts: List<UnifiedTrainingWorkout>
)

/**
 * Presentation-only join: preserve source records and every real session ID.
 * All dates are local to the device, including the explicit empty Today anchor.
 * Older *logged* sessions appear above Today; future prescriptions come only
 * from the selected approved plan. Linked Garmin sessions appear once.
 */
internal fun unifiedTrainingDays(
    today: LocalDate,
    groupedActivities: List<DisplayActivity>,
    plans: List<PlanSnapshot>,
    activePlanId: String?,
    zone: ZoneId
): List<UnifiedTrainingDay> {
    val activitiesByDate = groupedActivities.groupBy {
        it.primary.startedAt.atZone(zone).toLocalDate()
    }
    val visibleSourceIds = groupedActivities.flatMap { group ->
        listOf(group.primary.id) + group.additionalSources.map { it.id }
    }.toSet()
    val workoutsByDate = mutableMapOf<LocalDate, MutableList<UnifiedTrainingWorkout>>()
    for (plan in plans) {
        for (session in plan.sessions) {
            if (session.status == "unassigned") continue
            val completed = session.status == "completed"
            val upcoming = plan.id == activePlanId &&
                plan.status == "approved" &&
                session.date >= today &&
                session.status in setOf("planned", "in_progress")
            if (!completed && !upcoming) continue
            if (completed && session.linkedActivityId?.let { it in visibleSourceIds } == true) continue
            val date = if (completed) session.log?.performedOn ?: session.date else session.date
            workoutsByDate.getOrPut(date) { mutableListOf() }
                .add(UnifiedTrainingWorkout(plan.id, session))
        }
    }
    return (activitiesByDate.keys + workoutsByDate.keys + today).toSortedSet().map { date ->
        UnifiedTrainingDay(
            date = date,
            activities = activitiesByDate[date].orEmpty().sortedWith(
                compareBy<DisplayActivity> { it.primary.startedAt }.thenBy { it.primary.id }
            ),
            workouts = workoutsByDate[date].orEmpty()
        )
    }
}

/** Only actual dated training records introduce a month heading. */
internal fun unifiedTimelineMonthHeading(
    days: List<UnifiedTrainingDay>,
    position: Int
): Boolean {
    val current = days.getOrNull(position) ?: return false
    if (current.activities.isEmpty() && current.workouts.isEmpty()) return false
    val preceding = days.take(position).lastOrNull {
        it.activities.isNotEmpty() || it.workouts.isNotEmpty()
    } ?: return true
    return YearMonth.from(preceding.date) != YearMonth.from(current.date)
}

/**
 * Art-directed Calendar-First Training Tab.
 * - Pressing any Day on the calendar shows BOTH Logged Activities and Planned Workouts for that day.
 * - Pressing the Week pill or Week toggle shows BOTH Logged Activities and Planned Workouts across the week.
 */
@Composable
internal fun StrideLabUnifiedTrainingTimeline(
    state: TrainingState,
    activityGroups: List<DisplayActivity>,
    actions: PlanActions,
    onBuild: () -> Unit,
    onOpenWorkout: (String, String) -> Unit,
    onOpenActivity: (String) -> Unit,
    onSignIn: () -> Unit,
    onManage: (String, LocalDate) -> Unit
) {
    val account = state.account
    val hasLocalGarmin = state.localGarminIds.isNotEmpty()
    val plans = state.plans
    val hasHistory = account != null || hasLocalGarmin || plans.isNotEmpty()
    val approved = plans.filter { it.status == "approved" }
    val planProfile = account ?: state.localPlanOwner
    var selectedPlanId by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    var showTools by rememberSaveable(planProfile) { mutableStateOf(false) }
    val active = approved.firstOrNull { it.id == selectedPlanId } ?: approved.firstOrNull()
    val pendingReview = plans.any { it.status == "paused" }
    var agendaView by rememberSaveable(planProfile) { mutableStateOf(false) }
    var weekOnlyFilter by rememberSaveable(planProfile) { mutableStateOf(true) }
    var selectedDayText by rememberSaveable(planProfile, active?.id) {
        mutableStateOf(LocalDate.now().toString())
    }

    var noPlanFocusedDay by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    var noPlanWeekAnchorText by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val focusedNoPlanDate = noPlanFocusedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val noPlanWeekAnchor = noPlanWeekAnchorText?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: focusedNoPlanDate ?: today

    if (showTools) {
        Column(Modifier.fillMaxSize().background(TimelineStyle.background)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showTools = false },
                    color = TimelineStyle.card,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, TimelineStyle.border),
                    modifier = Modifier.testTag("training-back-to-timeline")
                ) {
                    Text(
                        "‹  Calendar",
                        color = TimelineStyle.lime,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                TrainingScreen(state, actions, onBuild, onOpenWorkout, onSignIn, onManage)
            }
        }
        return
    }
    val zone = ZoneId.systemDefault()
    val recordedDates = remember(activityGroups, plans, hasHistory, zone) {
        if (!hasHistory) emptySet()
        else activityGroups.map { it.primary.startedAt.atZone(zone).toLocalDate() }.toSet() +
            plans.flatMap { p -> p.sessions.filter { it.status == "completed" }.map { it.log?.performedOn ?: it.date } }
    }
    val days = remember(today, zone, activityGroups, plans, active?.id, hasHistory) {
        unifiedTrainingDays(
            today = today,
            groupedActivities = if (hasHistory) activityGroups else emptyList(),
            plans = plans,
            activePlanId = active?.id,
            zone = zone
        )
    }
    val focusedDate = active?.let { plan ->
        val start = plan.request.start
        val end = start.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
        runCatching { LocalDate.parse(selectedDayText) }.getOrDefault(today).coerceIn(start, end)
    } ?: today

    val visibleSourceIds = remember(activityGroups) {
        activityGroups.flatMap { g -> listOf(g.primary.id) + g.additionalSources.map { it.id } }.toSet()
    }

    // Helper that merges both Logged Activities AND Planned Workouts for any date
    // (including past dates in the active plan so pressing any day/week shows both!).
    fun combinedDayFor(targetDate: LocalDate): UnifiedTrainingDay {
        val base = days.firstOrNull { it.date == targetDate }
        val acts = base?.activities.orEmpty()
        val existingWorkouts = base?.workouts.orEmpty()
        val existingIds = existingWorkouts.map { it.session.id }.toSet()
        val extraPlanned = if (active != null && active.status == "approved") {
            active.sessions.filter { s ->
                s.date == targetDate &&
                    s.status != "unassigned" &&
                    s.id !in existingIds &&
                    !(s.status == "completed" && s.linkedActivityId != null && s.linkedActivityId in visibleSourceIds)
            }.map { UnifiedTrainingWorkout(active.id, it) }
        } else emptyList()
        return UnifiedTrainingDay(
            date = targetDate,
            activities = acts,
            workouts = existingWorkouts + extraPlanned
        )
    }

    val weekMonday = tracksideCalendarMonday(focusedDate)
    val weekSunday = weekMonday.plusDays(6)
    val noPlanWeekMonday = tracksideCalendarMonday(noPlanWeekAnchor)

    val visibleDays = remember(active, agendaView, weekOnlyFilter, focusedDate, focusedNoPlanDate, noPlanWeekMonday, days) {
        if (active != null && !agendaView) {
            listOf(combinedDayFor(focusedDate))
        } else if (active != null && agendaView && weekOnlyFilter) {
            val weekDays = (0L..6L).map { offset -> combinedDayFor(weekMonday.plusDays(offset)) }
                .filter { it.activities.isNotEmpty() || it.workouts.isNotEmpty() || it.date == focusedDate }
            weekDays.ifEmpty { listOf(combinedDayFor(focusedDate)) }
        } else if (active == null && focusedNoPlanDate != null) {
            listOf(combinedDayFor(focusedNoPlanDate))
        } else if (active == null) {
            val noPlanWeekEnd = noPlanWeekMonday.plusDays(6)
            days.filter { it.date in noPlanWeekMonday..noPlanWeekEnd }
        } else {
            days
        }
    }

    val todayIndex = visibleDays.indexOfFirst { it.date == today }.coerceAtLeast(0)
    val scroll = rememberLazyListState()
    LaunchedEffect(agendaView, active?.id, weekOnlyFilter, focusedNoPlanDate) {
        if (!state.initializing) {
            if (agendaView && !weekOnlyFilter) scroll.scrollToItem(todayIndex + if (active != null) 1 else 0)
            else scroll.scrollToItem(0)
        }
    }
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val locale = Locale.getDefault()
    val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val month = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val fullDayFmt = remember(locale) { DateTimeFormatter.ofPattern("EEEE, MMM d", locale) }

    Column(Modifier.fillMaxSize().background(TimelineStyle.background)) {
        // Clean Top Bar: Training title + Plan Calendar/Tools icon (single crisp affordance)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Training",
                color = TimelineStyle.ink,
                fontSize = 30.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp
            )
            Surface(
                onClick = { showTools = true },
                modifier = Modifier
                    .size(42.dp)
                    .testTag("training-plan-tools")
                    .semantics { contentDescription = "Training plan tools" },
                color = TimelineStyle.card,
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    if (pendingReview) StrideLabVisualPalette.moderateAmber.copy(alpha = 0.7f) else TimelineStyle.border
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.trackside_figma_calendar),
                        contentDescription = null,
                        tint = if (pendingReview) StrideLabVisualPalette.moderateAmber else TimelineStyle.lime,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val lastDateMinHeight = if (agendaView && !weekOnlyFilter)
                (maxHeight - 126.dp).coerceAtLeast(0.dp) else 0.dp

            LazyColumn(
                state = scroll,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("training-unified-timeline"),
                contentPadding = PaddingValues(
                    start = 18.dp, end = 18.dp, top = 8.dp, bottom = 116.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (active != null) item(key = "training-create-plan") {
                    OutlinedButton(onClick = onBuild, modifier = Modifier.fillMaxWidth().testTag("training-create-plan")) {
                        Text("Create another plan")
                    }
                }
                if (active == null) item(key = "training-no-plan-week-strip") {
                    TimelineNoPlanWeekStrip(
                        today = today,
                        weekAnchor = noPlanWeekAnchor,
                        focusedDate = focusedNoPlanDate,
                        recordedDates = recordedDates,
                        reduceMotion = reduceMotion,
                        onPreviousWeek = {
                            val prevMonday = tracksideCalendarMonday(noPlanWeekAnchor).minusWeeks(1)
                            noPlanWeekAnchorText = prevMonday.toString()
                        },
                        onNextWeek = {
                            val nextMonday = tracksideCalendarMonday(noPlanWeekAnchor).plusWeeks(1)
                            noPlanWeekAnchorText = nextMonday.toString()
                        },
                        onSelectDate = { tapped ->
                            noPlanWeekAnchorText = tapped.toString()
                            noPlanFocusedDay = if (focusedNoPlanDate == tapped) null else tapped.toString()
                        },
                        onShowAll = {
                            noPlanFocusedDay = null
                            noPlanWeekAnchorText = null
                        }
                    )
                }
                if (active != null) item(key = "training-hub-overview") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .testTag("training-hub-overview"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TracksideTrainingHubHeader(
                            active = active,
                            approvedPlans = approved,
                            selectedDate = focusedDate,
                            agenda = agendaView,
                            busy = state.busy,
                            actions = actions,
                            recordedDates = recordedDates,
                            onAgenda = { isWeekMode ->
                                agendaView = isWeekMode
                                if (isWeekMode) weekOnlyFilter = true
                            },
                            onSelectedDate = { date ->
                                val prevMonday = tracksideCalendarMonday(focusedDate)
                                val nextMonday = tracksideCalendarMonday(date)
                                selectedDayText = date.toString()
                                // If the user tapped a day within the same week, focus on that Day;
                                // if they tapped ‹ / › to switch weeks while in Week mode, stay in Week mode!
                                if (prevMonday == nextMonday) {
                                    agendaView = false
                                }
                            },
                            onPlanSelected = { id ->
                                selectedPlanId = id
                                selectedDayText = today.toString()
                            },
                            onManage = { onManage(active.id, focusedDate) },
                            onPlanTools = { showTools = true }
                        )

                        // Context bar below calendar showing current Day or Week scope + Logged/Planned counts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!agendaView) {
                                    focusedDate.format(fullDayFmt).uppercase(locale) + " · LOGGED & PLANNED"
                                } else if (weekOnlyFilter) {
                                    "${weekMonday.format(DateTimeFormatter.ofPattern("MMM d", locale))} – ${weekSunday.format(DateTimeFormatter.ofPattern("MMM d", locale))} · WEEK LOGS & PLANS".uppercase(locale)
                                } else {
                                    "ALL LOGGED ACTIVITIES & PLANS"
                                },
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold,
                                color = TimelineStyle.lime
                            )
                            if (agendaView) {
                                TextButton(
                                    onClick = { weekOnlyFilter = !weekOnlyFilter },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        if (weekOnlyFilter) "Show all dates" else "Selected week only",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TimelineStyle.secondary
                                    )
                                }
                            } else if (focusedDate != today) {
                                TextButton(
                                    onClick = { selectedDayText = today.toString() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        "Jump to Today",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TimelineStyle.lime
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(visibleDays, key = { _, day -> "timeline-date-${day.date}" }) { position, day ->
                    Column(
                        modifier = if (position == visibleDays.lastIndex)
                            Modifier.heightIn(min = lastDateMinHeight) else Modifier,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (day.date != today &&
                            unifiedTimelineMonthHeading(visibleDays, position)
                        ) {
                            Text(
                                day.date.format(month),
                                color = TimelineStyle.ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .padding(bottom = 2.dp)
                                    .testTag("timeline-month-${YearMonth.from(day.date)}")
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .testTag("timeline-day-${day.date}"),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TimelineDateRail(
                                date = day.date,
                                today = today,
                                weekday = weekday,
                                reduceMotion = reduceMotion
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                day.activities.forEach { group ->
                                    val cached = state.details[group.primary.id]
                                        ?: group.additionalSources.firstNotNullOfOrNull {
                                            state.details[it.id]
                                        }
                                    TimelineRecordedCard(
                                        activity = group.primary,
                                        cachedDetail = cached,
                                        onClick = { onOpenActivity(group.primary.id) }
                                    )
                                }
                                day.workouts.forEach { entry ->
                                    val presented = plans.firstOrNull { it.id == entry.planId }
                                        ?.cardPresentation(entry.session) ?: entry.session
                                    TimelinePlannedCard(
                                        session = presented,
                                        hasDraft = state.workoutDrafts.containsKey(
                                            entry.planId to entry.session.id
                                        ),
                                        onClick = {
                                            onOpenWorkout(entry.planId, entry.session.id)
                                        }
                                    )
                                }
                                if (active != null && day.activities.isEmpty() && day.workouts.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(15.dp),
                                        color = TimelineStyle.card,
                                        border = BorderStroke(1.dp, TimelineStyle.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    "Rest & recovery day",
                                                    color = TimelineStyle.ink,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "No logged activity or scheduled workout on this date.",
                                                    color = TimelineStyle.secondary,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                            if (!agendaView) {
                                                TextButton(onClick = { agendaView = true; weekOnlyFilter = true }) {
                                                    Text("View week ›", color = TimelineStyle.lime, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if ((day.date == today || focusedNoPlanDate == day.date) && active == null && !state.initializing) {
                            TimelinePromptCard(
                                title = if (pendingReview) "Your plan needs a review."
                                else "Your training starts here.",
                                description = if (pendingReview)
                                    "Continue reviewing your saved training plan."
                                else "Create a personalized training plan around your goals and schedule.",
                                action = if (pendingReview) "Review saved plan"
                                else "Create training plan",
                                onAction = {
                                    if (pendingReview) showTools = true else onBuild()
                                }
                            )
                        } else if (day.date == today && active == null && day.activities.isEmpty() &&
                            day.workouts.isEmpty() && !state.initializing
                        ) {
                            Text(
                                "No sessions scheduled for this date.",
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = TimelineStyle.secondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineDateRail(
    date: LocalDate,
    today: LocalDate,
    weekday: DateTimeFormatter,
    reduceMotion: Boolean
) {
    val isToday = date == today
    val liquid = remember(date) { Animatable(0f) }
    LaunchedEffect(isToday, reduceMotion) {
        if (isToday) {
            if (reduceMotion) liquid.snapTo(1f)
            else liquid.animateTo(
                1f,
                spring(
                    dampingRatio = .64f,
                    stiffness = TracksideLiquidMotion.STIFFNESS
                )
            )
        }
    }
    Column(
        Modifier
            .width(42.dp)
            .semantics {
                contentDescription = date.format(
                    DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())
                ) + if (isToday) ", current date" else ""
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            date.format(weekday).uppercase(Locale.getDefault()),
            color = if (isToday) TimelineStyle.lime else TimelineStyle.secondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f + (1f - liquid.value) * .19f
                            scaleY = 1f - (1f - liquid.value) * .11f
                        }
                        .clip(CircleShape)
                        .background(TimelineStyle.lime)
                        .testTag("timeline-current-date-liquid")
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(TimelineStyle.card)
                )
            }
            Text(
                date.dayOfMonth.toString().padStart(2, '0'),
                color = if (isToday) PlanBuilderPalette.accentOn else TimelineStyle.ink,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TimelineNoPlanWeekStrip(
    today: LocalDate,
    weekAnchor: LocalDate,
    focusedDate: LocalDate?,
    recordedDates: Set<LocalDate>,
    reduceMotion: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onShowAll: () -> Unit
) {
    val weekStart = remember(weekAnchor) { tracksideCalendarMonday(weekAnchor) }
    val todayWeekStart = remember(today) { tracksideCalendarMonday(today) }
    val fmtMonth = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = TimelineStyle.card,
        border = BorderStroke(1.dp, TimelineStyle.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        onClick = onPreviousWeek,
                        shape = CircleShape,
                        color = TimelineStyle.cardRaised,
                        border = BorderStroke(1.dp, TimelineStyle.border),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("‹", color = TimelineStyle.lime, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            "TRAINING WEEK",
                            fontSize = 9.5.sp,
                            letterSpacing = 1.2.sp,
                            color = TimelineStyle.muted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${weekStart.format(fmtMonth)} – ${weekStart.plusDays(6).format(fmtMonth)}",
                            color = TimelineStyle.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Surface(
                        onClick = onNextWeek,
                        shape = CircleShape,
                        color = TimelineStyle.cardRaised,
                        border = BorderStroke(1.dp, TimelineStyle.border),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("›", color = TimelineStyle.lime, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (focusedDate != null || weekStart != todayWeekStart) {
                    TextButton(
                        onClick = onShowAll,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            if (focusedDate != null) "Show all dates" else "Today",
                            color = TimelineStyle.lime,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val letters = listOf("M", "T", "W", "T", "F", "S", "S")
                (0L..6L).forEach { offset ->
                    val day = weekStart.plusDays(offset)
                    val isSelected = day == focusedDate
                    val isToday = day == today
                    val hasLog = day in recordedDates
                    val bg by animateColorAsState(
                        when {
                            isSelected -> StrideLabVisualPalette.limeTint
                            isToday -> TimelineStyle.cardRaised
                            else -> Color.Transparent
                        },
                        tween(if (reduceMotion) 0 else 160),
                        label = "noplan-day-$offset"
                    )
                    Surface(
                        onClick = { onSelectDate(day) },
                        shape = RoundedCornerShape(11.dp),
                        color = bg,
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSelected -> TimelineStyle.lime
                                isToday -> TimelineStyle.border
                                else -> Color.Transparent
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                letters[offset.toInt()],
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected || isToday) TimelineStyle.lime else TimelineStyle.muted
                            )
                            Text(
                                day.dayOfMonth.toString(),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) TimelineStyle.lime else TimelineStyle.ink
                            )
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (hasLog) TimelineStyle.lime else Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelinePromptCard(
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("training-create-plan-card"),
        shape = RoundedCornerShape(18.dp),
        color = TimelineStyle.card,
        border = BorderStroke(1.dp, TimelineStyle.border)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.trackside_figma_calendar),
                    contentDescription = null,
                    tint = TimelineStyle.lime,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    title,
                    color = TimelineStyle.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp
                )
            }
            Text(
                description,
                color = TimelineStyle.secondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
            Surface(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("training-create-plan")
                    .semantics { contentDescription = action },
                shape = RoundedCornerShape(14.dp),
                color = TimelineStyle.lime
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                    Text(
                        action,
                        color = PlanBuilderPalette.accentOn,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

/** Recorded-only visual labels. Missing values remain absent, not estimated. */
internal fun timelineRecordedMetrics(activity: ActivityRecord): Pair<String, String> {
    val hasDistance = activityHasDisplayDistance(activity)
    val duration = activityDisplayDurationSeconds(activity)
    val stationary = activityUsesStationaryDetail(activity)
    val primary = when {
        stationary && duration != null -> ActivityRecord.duration(duration)
        hasDistance -> activity.distanceLabel
        duration != null -> ActivityRecord.duration(duration)
        stationary -> "Recorded workout"
        else -> "Completed activity"
    }
    val secondary = buildList {
        if (hasDistance && duration != null) add(ActivityRecord.duration(duration))
        if (activity.supportsPace) {
            if (activity.recordedPace != null) add(activity.paceLabel)
            else if (activity.garminMovingPaceSPerKm != null) add(activity.detailPaceLabel)
        }
        activity.averageHr?.takeIf { it.isFinite() && it > 0 }?.let {
            add("${it.roundToInt()} bpm")
        }
    }.joinToString(" · ")
    return primary to secondary
}

@Composable
private fun TimelineRecordedCard(
    activity: ActivityRecord,
    cachedDetail: ActivityDetail?,
    onClick: () -> Unit
) {
    val type = activity.type.lowercase(Locale.ROOT)
    val isRun = DashboardSummary.isRun(activity)
    val kind = when {
        isRun -> "Running"
        "walk" in type -> "Walking"
        "strength" in type || "weight" in type -> "Strength"
        "calisthenics" in type -> "Calisthenics"
        "cycle" in type || "bike" in type -> "Cycling"
        else -> "Activity"
    }
    val accent = when (kind) {
        "Running", "Walking" -> TimelineStyle.lime
        "Strength" -> StrideLabVisualPalette.strengthCoral
        "Calisthenics" -> StrideLabVisualPalette.calisthenicsViolet
        else -> StrideLabVisualPalette.conditioningSky
    }
    val icon = when (kind) {
        "Running", "Walking" -> R.drawable.trackside_discipline_running
        "Strength" -> R.drawable.trackside_discipline_strength
        "Calisthenics" -> R.drawable.trackside_discipline_calisthenics
        else -> R.drawable.trackside_discipline_conditioning
    }
    val (primary, secondary) = timelineRecordedMetrics(activity)
    val gps = if (activityUsesStationaryDetail(activity)) null
        else rememberActivityThumbnail(activity.id, cachedDetail)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .testTag("timeline-recorded-${activity.id}")
            .semantics {
                contentDescription = buildString {
                    append(kind)
                    append(", ")
                    append(primary)
                    if (secondary.isNotBlank()) {
                        append(", ")
                        append(secondary)
                    }
                    append(", recorded activity")
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = TimelineStyle.card,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        activity.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TimelineStyle.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        primary,
                        color = TimelineStyle.ink,
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (secondary.isNotEmpty()) {
                        Text(
                            secondary,
                            color = TimelineStyle.secondary,
                            fontSize = 11.5.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (gps != null) {
                    Box(Modifier.size(width = 48.dp, height = 44.dp)) {
                        TodayCompactGpsThumbnail(gps, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelinePlannedCard(
    session: PlannedSession,
    hasDraft: Boolean,
    onClick: () -> Unit
) {
    val accent = when {
        session.discipline == "run" && session.kind == "long" -> StrideLabVisualPalette.lime
        session.discipline == "run" && session.kind == "quality" -> {
            val fam = session.runningPrescription?.family?.lowercase(Locale.ROOT).orEmpty()
            if (fam == "intervals" || fam == "hills") StrideLabVisualPalette.highCoral
            else StrideLabVisualPalette.moderateAmber
        }
        session.discipline == "run" -> StrideLabVisualPalette.easyTeal
        else -> StrideLabVisualPalette.disciplineColor(session.discipline)
    }
    val icon = when (session.discipline) {
        "run" -> R.drawable.trackside_discipline_running
        "str" -> R.drawable.trackside_discipline_strength
        "cal" -> R.drawable.trackside_discipline_calisthenics
        else -> R.drawable.trackside_discipline_conditioning
    }
    val recorded = session.status == "completed"
    val details = buildList {
        add(plannedDisciplineLabel(session))
        val reviewedTimeOnly = session.suggestion?.let { it.accepted && it.minutes < session.minutes } == true
        val distance = if (recorded) session.log?.distanceKm
        else if (reviewedTimeOnly) null else session.distanceKm
        val estimated = if (!recorded && !reviewedTimeOnly && distance == null)
            session.runningPrescription?.estimatedDistanceKm else null
        distance?.takeIf { it.isFinite() && it >= 0 }?.let {
            add(
                (if (recorded) "" else "Target ") +
                    String.format(Locale.getDefault(), "%.1f km", it)
            )
        }
        estimated?.let {
            add(String.format(Locale.getDefault(), "~%.1f km estimated", it))
        }
        val minutes = if (recorded) session.log?.minutes else session.effectiveMinutes.toDouble()
        minutes?.takeIf { it.isFinite() && it > 0 }?.let {
            add(
                (if (recorded) "" else "Planned ") +
                    String.format(Locale.getDefault(), "%.0f min", it)
            )
        }
        if (recorded) add("Completed")
        else if (session.status == "in_progress") add("In progress")
        if (hasDraft) add("Entry draft")
    }.joinToString(" · ")

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .testTag("timeline-planned-${session.id}")
            .semantics {
                contentDescription = "${session.effectiveTitle}, $details"
            },
        shape = RoundedCornerShape(16.dp),
        color = TimelineStyle.card,
        border = BorderStroke(1.dp, TimelineStyle.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val eyebrowTint = if (recorded) StrideLabVisualPalette.lime else accent
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(eyebrowTint)
                            )
                            Text(
                                if (recorded) "COMPLETED" else "PLANNED",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                color = eyebrowTint
                            )
                        }
                        Text(
                            session.effectiveTitle,
                            color = TimelineStyle.ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            details,
                            color = TimelineStyle.secondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text("›", color = TimelineStyle.muted, fontSize = 18.sp)
                }
                if (session.discipline == "run") {
                    val blocks = remember(session.id, session.title, session.distanceKm) {
                        WorkoutVisualBlocks.fromSession(session)
                    }
                    if (blocks.isNotEmpty()) {
                        WorkoutStructureBarChart(
                            blocks = blocks,
                            height = 16.dp,
                            interactive = false,
                            tag = "timeline-mini-bars-${session.id}"
                        )
                    }
                }
            }
        }
    }
}

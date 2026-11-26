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
internal fun trainingTrack(session: PlannedSession): String = when {
    session.kind == "structured-plyometrics" || session.title == "Plyometrics" -> "plyo"
    session.kind == "structured-isometrics" || session.title == "Isometrics" -> "iso"
    else -> session.discipline
}

internal fun trainingTrackLabel(track: String): String = when (track) {
    "plyo" -> "Plyometrics"
    "iso" -> "Isometrics"
    else -> disciplineLabel(track)
}
internal data class UnifiedTrainingDay(
    val date: LocalDate,
    val activities: List<DisplayActivity>,
    val workouts: List<UnifiedTrainingWorkout>,
    val freeform: List<FreeformWorkout> = emptyList(),
    val manual: List<ManualDisciplineSession> = emptyList(),
    val custom: List<CustomCalendarItem> = emptyList()
) {
    val hasEntries get() = activities.isNotEmpty() || workouts.isNotEmpty() ||
        freeform.isNotEmpty() || manual.isNotEmpty() || custom.isNotEmpty()
}

/**
 * Presentation-only join: preserve source records and every real session ID.
 * All dates are local to the device, including the explicit empty Today anchor.
 * Older logged sessions appear above Today. Future prescriptions come from
 * the selected approved plans. Linked Garmin sessions appear once.
 */
internal fun unifiedTrainingDays(
    today: LocalDate,
    groupedActivities: List<DisplayActivity>,
    plans: List<PlanSnapshot>,
    activePlanId: String?,
    zone: ZoneId,
    activePlanIds: Set<String> = setOfNotNull(activePlanId),
    freeform: List<FreeformWorkout> = emptyList(),
    manual: List<ManualDisciplineSession> = emptyList(),
    custom: List<CustomCalendarItem> = emptyList()
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
            val completed = session.isMarkedComplete
            val upcoming = plan.id in activePlanIds &&
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
    val freeformByDate = freeform.filter { it.isComplete }.groupBy { requireNotNull(it.date) }
    val manualByDate = manual.groupBy { it.performedOn }
    val customByDate = custom.groupBy { it.date }
    return (activitiesByDate.keys + workoutsByDate.keys + freeformByDate.keys + manualByDate.keys +
        customByDate.keys + today)
        .toSortedSet().map { date ->
        UnifiedTrainingDay(
            date = date,
            activities = activitiesByDate[date].orEmpty().sortedWith(
                compareBy<DisplayActivity> { it.primary.startedAt }.thenBy { it.primary.id }
            ),
            workouts = workoutsByDate[date].orEmpty().sortedWith(trainingWorkoutOrder),
            freeform = freeformByDate[date].orEmpty(),
            manual = manualByDate[date].orEmpty(),
            custom = customByDate[date].orEmpty()
        )
    }
}

private val trainingWorkoutOrder = compareBy<UnifiedTrainingWorkout> { it.session.isMarkedComplete }
    .thenBy { it.session.discipline != "run" }
    .thenBy { it.planId }
    .thenBy { it.session.id }

internal fun unifiedTrainingSelectedDay(
    date: LocalDate,
    base: UnifiedTrainingDay?,
    activePlans: List<PlanSnapshot>,
    visibleSourceIds: Set<String>
): UnifiedTrainingDay {
    val existing = base?.workouts.orEmpty()
    val existingIds = existing.map { it.planId to it.session.id }.toSet()
    val scheduled = activePlans.flatMap { plan ->
        plan.sessions.filter { session ->
            session.date == date && session.status != "unassigned" &&
                (plan.id to session.id) !in existingIds &&
                !(session.isMarkedComplete && session.linkedActivityId?.let { it in visibleSourceIds } == true)
        }.map { UnifiedTrainingWorkout(plan.id, it) }
    }
    val combined = (existing + scheduled).sortedWith(trainingWorkoutOrder)
    return base?.copy(workouts = combined) ?: UnifiedTrainingDay(date, emptyList(), combined)
}

/** Only actual dated training records introduce a month heading. */
internal fun unifiedTimelineMonthHeading(
    days: List<UnifiedTrainingDay>,
    position: Int
): Boolean {
    val current = days.getOrNull(position) ?: return false
    if (!current.hasEntries) return false
    val preceding = days.take(position).lastOrNull { it.hasEntries } ?: return true
    return YearMonth.from(preceding.date) != YearMonth.from(current.date)
}

/**
 * Art-directed Calendar-First Training Tab.
 * - Pressing any Day on the calendar shows BOTH Logged Activities and Planned Workouts for that day.
 * - Pressing the Week pill or Week toggle shows BOTH Logged Activities and Planned Workouts across the week.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StrideLabUnifiedTrainingTimeline(
    state: TrainingState,
    activityGroups: List<DisplayActivity>,
    actions: PlanActions,
    onBuild: () -> Unit,
    onOpenWorkout: (String, String) -> Unit,
    onOpenActivity: (String) -> Unit,
    onManage: (String, LocalDate) -> Unit,
    manualBoard: FreeformBoard? = null,
    onAddWorkout: (() -> Unit)? = null,
    onOpenManual: (String, Boolean) -> Unit = { _, _ -> },
    onOpenCustom: (CustomOccurrenceKey) -> Unit = {},
    calendarFocus: LocalDate? = null,
    clearCalendarFocus: () -> Unit = {}
) {
    val account = state.account
    val hasLocalGarmin = state.localGarminIds.isNotEmpty()
    val planProfile = account ?: state.localPlanOwner
    val plans = state.plans.filter { it.owner == planProfile && it.status != "draft" }
    val hasHistory = account != null || hasLocalGarmin || plans.isNotEmpty() || activityGroups.isNotEmpty()
    val approved = plans.filter { it.status == "approved" }
    var selectedPlanId by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    var selectedTrackKey by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    var reviewedAdaptation by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    val selectedPlan = approved.firstOrNull { it.id == selectedPlanId }
    val tracks = approved.flatMap { it.sessions.map(::trainingTrack) }.distinct()
    val selectedTrack = selectedTrackKey?.takeIf { it in tracks }
    val scopedPlans = when {
        selectedPlan != null -> listOf(selectedPlan)
        selectedTrack == null -> approved
        else -> approved.mapNotNull { plan ->
            plan.sessions.filter { trainingTrack(it) == selectedTrack }.takeIf { it.isNotEmpty() }
                ?.let { plan.copy(sessions = it) }
        }
    }
    val hasActivePlans = scopedPlans.isNotEmpty()
    val pausedPlans = plans.filter { it.status == "paused" }
    var agendaView by rememberSaveable(planProfile) { mutableStateOf(false) }
    var weekOnlyFilter by rememberSaveable(planProfile) { mutableStateOf(true) }
    var selectedDayText by rememberSaveable(planProfile) {
        mutableStateOf(LocalDate.now().toString())
    }

    var noPlanFocusedDay by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    var noPlanWeekAnchorText by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val focusedNoPlanDate = noPlanFocusedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val noPlanWeekAnchor = noPlanWeekAnchorText?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: focusedNoPlanDate ?: today

    val zone = ZoneId.systemDefault()
    val timelinePlans = if (selectedPlan != null || selectedTrack != null) scopedPlans else plans
    val visibleGroups = remember(activityGroups, selectedPlan, selectedTrack, scopedPlans) {
        if (selectedPlan == null && selectedTrack == null) activityGroups
        else {
            val sessions = scopedPlans.flatMap { it.sessions }
            val disciplines = sessions.mapNotNull { TodayDiscipline.fromLog(it.discipline) }.toSet()
            val links = sessions.mapNotNull { it.linkedActivityId }.toSet()
            activityGroups.filter { group ->
                (selectedTrack !in setOf("plyo", "iso") &&
                    TodayDiscipline.fromActivity(group.primary.type)?.let { it in disciplines } == true) ||
                    (selectedTrack != null && group.primary.type.equals(trainingTrackLabel(selectedTrack), ignoreCase = true)) ||
                    group.primary.id in links || group.additionalSources.any { it.id in links }
            }
        }
    }
    val freeform = remember(manualBoard, planProfile, selectedPlan, selectedTrack) {
        if (selectedPlan != null) emptyList() else manualBoard?.workouts.orEmpty().filter {
            it.owner == planProfile && it.isComplete && (selectedTrack == null || it.discipline.trainingTrackKey == selectedTrack)
        }
    }
    val manual = remember(manualBoard, planProfile, selectedPlan, selectedTrack) {
        if (selectedPlan != null) emptyList() else manualBoard?.legacy.orEmpty().filter {
            it.owner == planProfile && (selectedTrack == null || it.discipline.trainingTrackKey == selectedTrack)
        }
    }
    val recordedDates = remember(visibleGroups, timelinePlans, hasHistory, zone, freeform, manual) {
        val manualDates = freeform.mapNotNull { it.date }.toSet() + manual.map { it.performedOn }
        if (!hasHistory) manualDates
        else visibleGroups.map { it.primary.startedAt.atZone(zone).toLocalDate() }.toSet() +
            timelinePlans.flatMap { p -> p.sessions.filter { it.isMarkedComplete }.map { it.log?.performedOn ?: it.date } } +
            manualDates
    }
    val showCustom = selectedPlan == null && manualBoard?.schedules.orEmpty().any { it.owner == planProfile }
    fun focusCalendar(date: LocalDate) {
        selectedDayText = date.toString()
        noPlanFocusedDay = date.toString()
        noPlanWeekAnchorText = date.toString()
        agendaView = false
    }
    LaunchedEffect(calendarFocus, planProfile) {
        if (calendarFocus != null) {
            selectedPlanId = null
            selectedTrackKey = null
            focusCalendar(calendarFocus)
            clearCalendarFocus()
        }
    }

    val focusedDate = if (hasActivePlans) {
        val requested = runCatching { LocalDate.parse(selectedDayText) }.getOrDefault(today)
        if (showCustom) requested.coerceIn(customDateRange.start, customDateRange.endInclusive)
        else {
            val start = scopedPlans.minOf { it.request.start }
            val end = scopedPlans.maxOf { it.request.start.plusWeeks(it.request.weeks.toLong()).minusDays(1) }
            requested.coerceIn(start, end)
        }
    } else today
    val visibleWeek = tracksideCalendarMonday(if (hasActivePlans) focusedDate else noPlanWeekAnchor)
    val custom = remember(manualBoard, planProfile, selectedPlan, selectedTrack, visibleWeek, agendaView, weekOnlyFilter, today) {
        if (selectedPlan != null || manualBoard == null) emptyList() else {
            val scopedBoard = manualBoard.copy(
                schedules = manualBoard.schedules.filter { it.owner == planProfile },
                workouts = manualBoard.workouts.filter { it.owner == planProfile }
            )
            val week = scopedBoard.customBetween(visibleWeek, visibleWeek.plusDays(6))
            val extra = if (hasActivePlans && agendaView && !weekOnlyFilter) {
                scopedBoard.customBetween(today.minusDays(7), today.plusDays(77))
            } else emptyList()
            (week + extra).distinctBy { it.key }.filter {
                selectedTrack == null || it.discipline.trainingTrackKey == selectedTrack
            }
        }
    }
    val customDates = remember(custom) { custom.groupingBy { it.date }.eachCount() }
    val days = remember(today, zone, visibleGroups, timelinePlans, scopedPlans, hasHistory, freeform, manual, custom) {
        unifiedTrainingDays(
            today = today,
            groupedActivities = if (hasHistory) visibleGroups else emptyList(),
            plans = timelinePlans,
            activePlanId = selectedPlan?.id,
            zone = zone,
            activePlanIds = scopedPlans.map { it.id }.toSet(),
            freeform = freeform,
            manual = manual,
            custom = custom
        )
    }

    val visibleSourceIds = remember(activityGroups) {
        activityGroups.flatMap { g -> listOf(g.primary.id) + g.additionalSources.map { it.id } }.toSet()
    }

    fun combinedDayFor(targetDate: LocalDate): UnifiedTrainingDay =
        unifiedTrainingSelectedDay(targetDate, days.firstOrNull { it.date == targetDate },
            scopedPlans, visibleSourceIds)

    val weekMonday = tracksideCalendarMonday(focusedDate)
    val weekSunday = weekMonday.plusDays(6)
    val noPlanWeekMonday = tracksideCalendarMonday(noPlanWeekAnchor)

    val visibleDays = remember(scopedPlans, agendaView, weekOnlyFilter, focusedDate, focusedNoPlanDate, noPlanWeekMonday, days, visibleSourceIds) {
        if (hasActivePlans && !agendaView) {
            listOf(combinedDayFor(focusedDate))
        } else if (hasActivePlans && agendaView && weekOnlyFilter) {
            val weekDays = (0L..6L).map { offset -> combinedDayFor(weekMonday.plusDays(offset)) }
                .filter { it.hasEntries || it.date == focusedDate }
            weekDays.ifEmpty { listOf(combinedDayFor(focusedDate)) }
        } else if (!hasActivePlans && focusedNoPlanDate != null) {
            listOf(combinedDayFor(focusedNoPlanDate))
        } else if (!hasActivePlans) {
            val noPlanWeekEnd = noPlanWeekMonday.plusDays(6)
            days.filter { it.date in noPlanWeekMonday..noPlanWeekEnd }
        } else {
            days
        }
    }

    val todayIndex = visibleDays.indexOfFirst { it.date == today }.coerceAtLeast(0)
    val showSkeleton = rememberDelayedSkeletonVisible(state.initializing)
    val scroll = rememberLazyListState()
    LaunchedEffect(agendaView, selectedPlan?.id, selectedTrack, weekOnlyFilter, focusedNoPlanDate) {
        if (!state.initializing) {
            if (agendaView && !weekOnlyFilter) {
                scroll.scrollToItem(todayIndex + 1)
            }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Training",
                color = TimelineStyle.ink,
                fontSize = 30.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                modifier = Modifier.weight(1f).testTag("training-title")
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showCustom) {
                    TextButton(
                        onClick = { pickCustomDate(context, if (hasActivePlans) focusedDate else noPlanWeekAnchor, ::focusCalendar) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("training-pick-date")
                    ) { Text("Date", color = TimelineStyle.secondary) }
                }
                if (!hasActivePlans && !state.initializing) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = StrideLabSurfaceTokens.minTouchTarget)
                            .clip(RoundedCornerShape(100.dp))
                            .clickable(onClick = onBuild)
                            .testTag("training-create-plan-header"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = TimelineStyle.lime,
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "+ Create plan",
                                color = StrideLabSurfaceTokens.onLime,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
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
                    start = 18.dp, end = 18.dp, top = 6.dp, bottom = 116.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showSkeleton && !hasActivePlans) {
                    item(key = "training-skeleton") {
                        SkeletonShimmerContainer(reducedMotion = reduceMotion) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("training-skeleton")
                            ) {
                                SkeletonTrainingHeaderCard(reducedMotion = reduceMotion)
                                SkeletonWorkoutRow(reducedMotion = reduceMotion)
                                SkeletonWorkoutRow(reducedMotion = reduceMotion)
                                SkeletonWorkoutRow(reducedMotion = reduceMotion)
                                SkeletonCoachingCard(reducedMotion = reduceMotion)
                            }
                        }
                    }
                }
                if (!hasActivePlans && !showSkeleton) item(key = "training-no-plan-week-strip") {
                    TimelineNoPlanWeekStrip(
                        today = today,
                        weekAnchor = noPlanWeekAnchor,
                        focusedDate = focusedNoPlanDate,
                        recordedDates = recordedDates,
                        customDates = customDates.keys,
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
                            if (noPlanFocusedDay == null) noPlanWeekAnchorText = null
                            noPlanFocusedDay = null
                        }
                    )
                }
                if (hasActivePlans) item(key = "training-hub-overview") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .testTag("training-hub-overview"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TracksideTrainingHubHeader(
                            selectedPlan = selectedPlan,
                            approvedPlans = approved,
                            scopedPlans = scopedPlans,
                            tracks = tracks,
                            selectedTrack = selectedTrack,
                            selectedDate = focusedDate,
                            agenda = agendaView,
                            recordedDates = recordedDates,
                            customPlanned = customDates,
                            customCalendar = showCustom,
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
                                selectedTrackKey = null
                            },
                            onTrackSelected = { selectedTrackKey = it; selectedPlanId = null },
                            onManage = { onManage(it, focusedDate) }
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
                        if (agendaView && !weekOnlyFilter &&
                            day.date != today &&
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
                                    androidx.compose.runtime.key(entry.planId, entry.session.id) {
                                        TimelinePlannedCard(
                                            planId = entry.planId,
                                            session = presented,
                                            hasDraft = state.workoutDrafts.containsKey(
                                                entry.planId to entry.session.id
                                            ),
                                            onClick = { onOpenWorkout(entry.planId, entry.session.id) }
                                        )
                                    }
                                }
                                day.custom.forEach { workout ->
                                    FreeformTile(
                                        title = workout.title,
                                        supporting = "Custom · ${workout.discipline.label} · ${if (workout.inProgress) "In progress" else "Scheduled"}",
                                        color = workout.discipline.markColor,
                                        onClick = { onOpenCustom(workout.key) },
                                        modifier = Modifier.testTag("training-custom-${workout.key.workoutId}")
                                    )
                                }
                                day.freeform.forEach { workout ->
                                    FreeformTile(
                                        title = workout.title,
                                        supporting = "${workout.sourceLabel} · ${workout.discipline.label}\n${workout.summary()}",
                                        color = workout.discipline.markColor,
                                        onClick = { onOpenManual(workout.id, false) },
                                        modifier = Modifier.testTag("training-freeform-${workout.id}")
                                    )
                                }
                                day.manual.forEach { workout ->
                                    FreeformTile(
                                        title = workout.title,
                                        supporting = "Manual · ${workout.discipline.label}",
                                        color = workout.discipline.markColor,
                                        onClick = { onOpenManual(workout.id, true) },
                                        modifier = Modifier.testTag("training-manual-${workout.id}")
                                    )
                                }
                                if (hasActivePlans && !day.hasEntries) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    "Rest & recovery day",
                                                    color = TimelineStyle.ink,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "No logged activity or scheduled workout on this date.",
                                                    color = TimelineStyle.secondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            if (!agendaView) {
                                                TextButton(
                                                    onClick = { agendaView = true; weekOnlyFilter = true },
                                                    modifier = Modifier.heightIn(min = 48.dp)
                                                ) {
                                                    Text("View week ›", color = TimelineStyle.lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        StrideLabHairline()
                                    }
                                }
                            }
                        }
                        if ((day.date == today || focusedNoPlanDate == day.date) && !hasActivePlans && !state.initializing && !day.hasEntries) {
                            TimelinePromptCard(
                                title = "Your training starts here.",
                                description = "Create a personalized training plan around your goals and schedule.",
                                action = "Create training plan",
                                onAction = onBuild
                            )
                        }
                    }
                }
                if (hasActivePlans) item(key = "training-create-plan") {
                    OutlinedButton(
                        onClick = onBuild,
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("training-create-plan")
                    ) {
                        Text("Create another plan", color = TimelineStyle.ink)
                    }
                }
                for (paused in pausedPlans) item(key = "manage-paused-${paused.id}") {
                    OutlinedButton(
                        onClick = { onManage(paused.id, focusedDate) },
                        modifier = Modifier.fillMaxWidth().testTag("training-manage-paused-${paused.id}")
                    ) { Text("Review paused plan · ${paused.name ?: paused.request.goal}") }
                }
                val reviewPlans = approved.filter { plan -> scopedPlans.any { it.id == plan.id } }
                trainingCoachingItems(state, reviewPlans, actions) { reviewedAdaptation = it }
                trainingBackupItems(state, actions)
            }
        }
    }
    state.pendingAdaptationProposals[reviewedAdaptation]?.let { proposal ->
        AdaptationProposalSheet(
            proposal = proposal, busy = state.busy,
            onDismiss = { reviewedAdaptation = null },
            onApply = actions.approveAdaptationProposal,
            onDismissProposal = actions.dismissAdaptationProposal
        )
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
    customDates: Set<LocalDate>,
    reduceMotion: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onShowAll: () -> Unit
) {
    val weekStart = remember(weekAnchor) { tracksideCalendarMonday(weekAnchor) }
    val todayWeekStart = remember(today) { tracksideCalendarMonday(today) }
    val fmtMonth = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TimelineStyle.background)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPreviousWeek)
                    .testTag("training-week-prev"),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = TimelineStyle.lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "TRAINING WEEK",
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = TimelineStyle.muted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${weekStart.format(fmtMonth)} - ${weekStart.plusDays(6).format(fmtMonth)}",
                    color = TimelineStyle.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    style = TabularTextStyle,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNextWeek)
                    .testTag("training-week-next"),
                contentAlignment = Alignment.Center
            ) {
                Text("›", color = TimelineStyle.lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val letters = listOf("M", "T", "W", "T", "F", "S", "S")
            (0L..6L).forEach { offset ->
                val day = weekStart.plusDays(offset)
                val isSelected = day == focusedDate
                val isToday = day == today
                val hasLog = day in recordedDates
                val hasCustom = day in customDates
                val circleBg by animateColorAsState(
                    when {
                        isSelected -> TimelineStyle.lime
                        isToday -> TimelineStyle.cardRaised
                        else -> Color.Transparent
                    },
                    tween(if (reduceMotion) 0 else 160),
                    label = "noplan-day-$offset"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectDate(day) }
                        .padding(vertical = 6.dp)
                        .testTag("calendar-day-$day")
                        .semantics {
                            contentDescription = "$day" + if (hasCustom) ", custom workout scheduled" else ""
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        letters[offset.toInt()],
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected || isToday) TimelineStyle.lime else TimelineStyle.muted
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(circleBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            style = TabularTextStyle,
                            color = when {
                                isSelected -> PlanBuilderPalette.accentOn
                                isToday -> TimelineStyle.lime
                                else -> TimelineStyle.ink
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.height(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasLog) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(TimelineStyle.lime)
                            )
                        }
                        if (hasCustom) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(StrideLabVisualPalette.easyTeal)
                            )
                        }
                    }
                }
            }
        }

        if (focusedDate != null || weekStart != todayWeekStart) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = onShowAll,
                    shape = RoundedCornerShape(100.dp),
                    color = TimelineStyle.cardRaised,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("training-return-today")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (focusedDate != null) "Show week" else "Return to Today",
                            color = TimelineStyle.lime,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        StrideLabHairline()
    }
}

@Composable
private fun TimelinePromptCard(
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("training-create-plan-card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(TimelineStyle.lime)
            )
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.trackside_figma_calendar),
                        contentDescription = null,
                        tint = TimelineStyle.lime,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        title,
                        color = TimelineStyle.ink,
                        fontWeight = FontWeight.Bold,
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
            }
        }
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
        StrideLabHairline()
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
    val showGpsThumb = !activityUsesStationaryDetail(activity)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .strideLabPressFeedback(
                shape = RoundedCornerShape(14.dp),
                highlightBorderColor = StrideLabSurfaceTokens.lime,
                onClick = onClick
            )
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
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    if (activity.source.equals("garmin", ignoreCase = true)) "Garmin · ${activity.title}" else activity.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TimelineStyle.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    primary,
                    color = TimelineStyle.ink,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    style = TabularTextStyle,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (secondary.isNotEmpty()) {
                    Text(
                        secondary,
                        color = TimelineStyle.secondary,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                        style = TabularTextStyle,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showGpsThumb) {
                TodayCachedGpsThumbnail(
                    activityId = activity.id,
                    detail = cachedDetail,
                    modifier = Modifier.size(width = 48.dp, height = 44.dp)
                )
            }
        }
        StrideLabHairline()
    }
}

@Composable
private fun TimelinePlannedCard(
    planId: String,
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
    val recorded = session.isMarkedComplete
    val details = buildList {
        add(if (trainingTrack(session) in setOf("plyo", "iso")) trainingTrackLabel(trainingTrack(session))
            else plannedDisciplineLabel(session))
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .strideLabPressFeedback(
                shape = RoundedCornerShape(14.dp),
                highlightBorderColor = accent,
                onClick = onClick
            )
            .testTag("timeline-planned-${session.id}")
            .semantics {
                contentDescription = "${session.effectiveTitle}, $details"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("timeline-session-$planId-${session.id}"),
                verticalArrangement = Arrangement.spacedBy(5.dp)
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
                                if (recorded) "COACHED · COMPLETED" else "COACHED · PLANNED",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                color = eyebrowTint
                            )
                        }
                        Text(
                            session.effectiveTitle,
                            color = TimelineStyle.ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            details,
                            color = TimelineStyle.secondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            style = TabularTextStyle,
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
                            height = 14.dp,
                            interactive = false,
                            tag = "timeline-mini-bars-${session.id}"
                        )
                    }
                }
            }
        }
        StrideLabHairline()
    }
}

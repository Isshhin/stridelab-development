package io.stridelab.privatealpha

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private object TimelineStyle {
    val background = Color.Black
    val card = Color(0xFF191D1E)
    val border = Color(0xFF384041)
    val ink = Color(0xFFF6F7F7)
    val secondary = Color(0xFF9FA9A9)
    val lime = Color(0xFFD8FB70)
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
 * Approved Google Calendar-style sparse timeline: history above the current
 * date, upcoming training below, and no fabricated empty weekday rows.
 * Unlike Today there is no date carousel or notched hero.
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
    onManage: (String, LocalDate) -> Unit,
    onLoadCachedDetail: (suspend (String) -> Boolean)? = null
) {
    val account = state.account
    val hasLocalGarmin = state.localGarminIds.isNotEmpty()
    // The repository exposes only the active account's plans or the local
    // device-only plan namespace. Never hide the latter in guest mode.
    val plans = state.plans
    val hasHistory = account != null || hasLocalGarmin || plans.isNotEmpty()
    val approved = plans.filter { it.status == "approved" }
    val planProfile = account ?: state.localPlanOwner
    var selectedPlanId by rememberSaveable(planProfile) { mutableStateOf<String?>(null) }
    var showTools by rememberSaveable(planProfile) { mutableStateOf(false) }
    val active = approved.firstOrNull { it.id == selectedPlanId } ?: approved.firstOrNull()
    val pendingReview = plans.any { it.status == "draft" || it.status == "paused" }
    val pendingDrafts=plans.count { it.status=="draft" }
    var agendaView by rememberSaveable(planProfile) { mutableStateOf(false) }
    var selectedDayText by rememberSaveable(planProfile,active?.id) {
        mutableStateOf(LocalDate.now().toString())
    }

    if (showTools) {
        Column(Modifier.fillMaxSize().background(TimelineStyle.background)) {
            TextButton(
                onClick = { showTools = false },
                modifier = Modifier.padding(start = 12.dp)
                    .testTag("training-back-to-timeline")
            ) { Text("‹  Back to timeline", color = TimelineStyle.lime) }
            Box(Modifier.weight(1f)) {
                TrainingScreen(state, actions, onBuild, onOpenWorkout, onSignIn, onManage)
            }
        }
        return
    }

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val days = remember(today, zone, activityGroups, plans, active?.id, hasHistory) {
        unifiedTrainingDays(
            today = today,
            groupedActivities = if (hasHistory) activityGroups else emptyList(),
            plans = plans,
            activePlanId = active?.id,
            zone = zone
        )
    }
    val focusedDate=active?.let { plan ->
        val start=plan.request.start
        val end=start.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
        runCatching { LocalDate.parse(selectedDayText) }.getOrDefault(today).coerceIn(start,end)
    } ?: today
    val visibleDays=if(active!=null && !agendaView) {
        listOf(days.firstOrNull { it.date==focusedDate }
            ?: UnifiedTrainingDay(focusedDate,emptyList(),emptyList()))
    } else days
    val todayIndex = days.indexOfFirst { it.date == today }.coerceAtLeast(0)
    val scroll = rememberLazyListState()
    // Day starts at the overview. Agenda preserves the historical timeline
    // and anchors at Today; returning to Day restores the selected date.
    LaunchedEffect(agendaView,active?.id) {
        if(!state.initializing) {
            if(agendaView)scroll.scrollToItem(todayIndex+if(active!=null)1 else 0)
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

    Column(Modifier.fillMaxSize().background(TimelineStyle.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Training", color = TimelineStyle.ink,
                fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold
            )
            Surface(
                onClick = {
                    showTools = true
                },
                modifier = Modifier.size(48.dp)
                    .testTag("training-plan-tools")
                    .semantics { contentDescription = "Training plan tools" },
                color = Color(0xFF1E2223),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.trackside_figma_calendar),
                        contentDescription = null, tint = TimelineStyle.ink,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
        // A final date needs enough height to be top-aligned, but NOT so
        // much that the user can scroll below its last real card. Subtract
        // the list's 10dp top and 116dp bottom insets from the viewport.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val lastDateMinHeight = if(agendaView)(maxHeight - 126.dp).coerceAtLeast(0.dp)
            else 0.dp
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize()
                .testTag("training-unified-timeline"),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp, top = 10.dp, bottom = 116.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if(active!=null) item(key="training-hub-overview") {
                Column(Modifier.fillMaxWidth().testTag("training-hub-overview"),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    TracksideTrainingHubHeader(
                        active=active,
                        approvedPlans=approved,
                        pendingDrafts=pendingDrafts,
                        selectedDate=focusedDate,
                        agenda=agendaView,
                        busy=state.busy,
                        actions=actions,
                        onAgenda={ agendaView=it },
                        onSelectedDate={ date ->
                            selectedDayText=date.toString()
                            agendaView=false
                        },
                        onPlanSelected={ id ->
                            selectedPlanId=id
                            selectedDayText=today.toString()
                        },
                        onManage={ onManage(active.id,focusedDate) },
                        onPlanTools={ showTools=true }
                    )
                }
            }
            itemsIndexed(visibleDays, key = { _, day -> "timeline-date-${day.date}" }) { position, day ->
                Column(
                    modifier = if (position == visibleDays.lastIndex)
                        Modifier.heightIn(min = lastDateMinHeight) else Modifier,
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    if (day.date != today &&
                        unifiedTimelineMonthHeading(visibleDays, position)
                    ) {
                        Text(
                            day.date.format(month),
                            color = TimelineStyle.ink,
                            fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp)
                                .testTag("timeline-month-${YearMonth.from(day.date)}")
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().testTag("timeline-day-${day.date}"),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                if(cached == null && onLoadCachedDetail != null) {
                                    LaunchedEffect(group.primary.id) {
                                        onLoadCachedDetail(group.primary.id)
                                    }
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
                        }
                    }
                    // The prompt is an independent full-width timeline card.
                    // It deliberately sits OUTSIDE the 48dp date rail + card
                    // row, aligned with the screen's 18dp horizontal margins.
                    if (day.date == today && active == null && !state.initializing) {
                        TimelinePromptCard(
                            title = if (pendingReview) "Your plan needs a review."
                                else "Your training starts here.",
                            description = if (pendingReview)
                                "Continue reviewing your saved training plan."
                            else "Create an offline training plan. Sign-in is optional for cloud features.",
                            action = if (pendingReview) "Review saved plan"
                                else "Create training plan",
                            onAction = {
                                if (pendingReview) showTools = true else onBuild()
                            }
                        )
                    } else if (day.date == today && day.activities.isEmpty() &&
                        day.workouts.isEmpty() && !state.initializing
                    ) {
                        Text(
                            "No sessions scheduled for this date.",
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = TimelineStyle.secondary, fontSize = 12.sp
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
        Modifier.width(48.dp).semantics {
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
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold
        )
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        // Single on-entry liquid settle. No idle animation,
                        // overdraw-heavy shaders or animated layout changes.
                        scaleX = 1f + (1f - liquid.value) * .19f
                        scaleY = 1f - (1f - liquid.value) * .11f
                    }.clip(CircleShape).background(TimelineStyle.lime)
                        .testTag("timeline-current-date-liquid")
                )
            }
            Text(
                date.dayOfMonth.toString().padStart(2, '0'),
                color = if (isToday) Color(0xFF141812) else TimelineStyle.ink,
                fontSize = 21.sp, fontWeight = FontWeight.Bold,
                maxLines = 1
            )
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
    Column(
        Modifier.fillMaxWidth().background(
            Color(0xFF19201A), RoundedCornerShape(16.dp)
        ).padding(15.dp).testTag("training-create-plan-card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.trackside_figma_calendar),
                contentDescription = null, tint = TimelineStyle.lime,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(title, color = TimelineStyle.ink, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, lineHeight = 18.sp)
        }
        Text(
            description, color = TimelineStyle.secondary,
            fontSize = 12.sp, lineHeight = 17.sp
        )
        Surface(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("training-create-plan")
                .semantics { contentDescription = action },
            shape = RoundedCornerShape(26.dp),
            color = TimelineStyle.lime
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                Text(
                    action, color = Color(0xFF121711),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
            }
        }
    }
}

/** Recorded-only visual labels. Missing values remain absent, not estimated. */
internal fun timelineRecordedMetrics(activity: ActivityRecord): Pair<String, String> {
    val hasDistance = activity.distanceM?.let { it.isFinite() && it >= 0.0 } == true
    val hasDuration = activity.durationS?.let { it.isFinite() && it >= 0.0 } == true
    val primary = when {
        hasDistance -> activity.distanceLabel
        hasDuration -> activity.durationLabel
        else -> "Completed activity"
    }
    val secondary = buildList {
        if (hasDistance && hasDuration) add(activity.durationLabel)
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
        "Strength" -> Color(0xFFF09A89)
        "Calisthenics" -> Color(0xFFC4A5F5)
        else -> Color(0xFF9BCBDB)
    }
    val icon = when (kind) {
        "Running", "Walking" -> R.drawable.trackside_discipline_running
        "Strength" -> R.drawable.trackside_discipline_strength
        "Calisthenics" -> R.drawable.trackside_discipline_calisthenics
        else -> R.drawable.trackside_discipline_conditioning
    }
    val (primary, secondary) = timelineRecordedMetrics(activity)
    val gps = remember(cachedDetail) {
        cachedDetail?.let { RecordedGpsRoute.from(it.samples) }
            ?.takeIf { it.pointCount >= 2 }
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp)
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
        border = BorderStroke(1.dp, TimelineStyle.border)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(38.dp)
                        .background(accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRun) TodayRecordedRunningGlyph(Modifier.size(24.dp))
                    else Icon(
                        painterResource(icon), contentDescription = null,
                        tint = Color(0xFF19201A), modifier = Modifier.size(23.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        primary,
                        color = TimelineStyle.ink,
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (secondary.isNotEmpty()) {
                        Text(
                            secondary,
                            color = TimelineStyle.secondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.15).sp,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Keep an identical row height and metrics width whether or
                // not this recorded activity has a genuine cached GPS route.
                // Empty space is intentional; never fabricate a route preview.
                Box(Modifier.size(width = 55.dp, height = 52.dp)) {
                    if (gps != null) {
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
    val accent = tracksideDisciplineColor(session.discipline)
    val icon = when (session.discipline) {
        "run" -> R.drawable.trackside_discipline_running
        "str" -> R.drawable.trackside_discipline_strength
        "cal" -> R.drawable.trackside_discipline_calisthenics
        else -> R.drawable.trackside_discipline_conditioning
    }
    val recorded = session.status == "completed"
    val details = buildList {
        add(disciplineLabel(session.discipline))
        val reviewedTimeOnly=session.suggestion?.let { it.accepted && it.minutes<session.minutes }==true
        val distance = if(recorded) session.log?.distanceKm
            else if(reviewedTimeOnly) null else session.distanceKm
        val estimated=if(!recorded && !reviewedTimeOnly && distance==null)
            session.runningPrescription?.estimatedDistanceKm else null
        distance?.takeIf { it.isFinite() && it >= 0 }?.let {
            add((if (recorded) "" else "Target ") +
                String.format(Locale.getDefault(), "%.1f km", it))
        }
        estimated?.let {
            add(String.format(Locale.getDefault(),"~%.1f km estimated",it))
        }
        val minutes = if (recorded) session.log?.minutes else session.effectiveMinutes.toDouble()
        minutes?.takeIf { it.isFinite() && it > 0 }?.let {
            add((if (recorded) "" else "Planned ") +
                String.format(Locale.getDefault(), "%.0f min", it))
        }
        if (recorded) add("Completed")
        else if (session.status == "in_progress") add("In progress")
        if (hasDraft) add("Entry draft")
    }.joinToString(" · ")
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 75.dp)
            .testTag("timeline-planned-${session.id}")
            .semantics {
                contentDescription = "${session.effectiveTitle}, $details"
            },
        shape = RoundedCornerShape(16.dp),
        color = TimelineStyle.card,
        border = BorderStroke(1.dp, TimelineStyle.border)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(39.dp).background(
                    accent.copy(alpha = .16f), RoundedCornerShape(10.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon), contentDescription = null,
                    tint = accent, modifier = Modifier.size(23.dp)
                )
            }
            Column(
                Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    session.effectiveTitle, color = TimelineStyle.ink,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    details, color = TimelineStyle.secondary,
                    fontSize = 11.sp, lineHeight = 15.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import kotlin.math.cos

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

// Every Today hero uses the same exact footprint. Long session lists or
// accessibility text scroll inside the hero rather than moving the cards below.
private val TodayHeroHeight = 232.dp

/** Status markers use only the signed-in athlete's persisted workouts. */
private data class TodayDayMarker(val completed: Boolean, val pending: Boolean)

private fun todayWeekMarkers(
    start: LocalDate,
    visible: List<ActivityRecord>,
    logs: List<ConfirmedWorkout>,
    sessions: List<TodayScheduledSession>,
    evidence: TodaySchedule
): Map<LocalDate, TodayDayMarker> {
    val end = start.plusDays(6)
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val recordedDays = visible.asSequence()
        .filter { !it.startedAt.isAfter(now) }
        .map { it.startedAt.atZone(zone).toLocalDate() }
        .filter { it in start..end }.toSet() +
        logs.map { it.date }.filter { it in start..end }
    val scheduled = sessions.filter { it.session.date in start..end }
    return (0L..6L).associate { offset ->
        val day = start.plusDays(offset)
        day to TodayDayMarker(
            completed = day in recordedDays || scheduled.any {
                it.session.date == day && evidence.isCompleted(it)
            },
            pending = scheduled.any {
                it.session.date == day && !evidence.isCompleted(it) &&
                    it.session.status !in setOf("skipped", "missed")
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
    val partialTimes: Boolean,
    val disciplineCounts: List<Pair<String, Int>>
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
    val disciplineCounts = (
        weekActivities.map { todayRecordedKind(it.type) } +
            confirmed.map { todayHeroDiscipline(it.discipline) }
    ).groupingBy { it }.eachCount().toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    return TodayWeekMetrics(
        counts = counts,
        seconds = seconds,
        totalSeconds = durations.takeIf { it.isNotEmpty() }?.sum(),
        totalDistanceMeters = distance.takeIf { it.isNotEmpty() }?.sum(),
        activityCount = weekActivities.size + confirmed.size,
        partialTimes = durations.size < weekActivities.size + confirmed.size,
        disciplineCounts = disciplineCounts
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
    actions: PlanActions = PlanActions(),
    freeformBoard: FreeformBoard? = null
) {
    var reviewedProposal by rememberSaveable { mutableStateOf(false) }
    val adaptationProposal = state.activeAdaptationProposal
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
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // Repository state is already owner-scoped: cloud plans while signed
    // in, device-only plans while signed out, and local Garmin either way.
    // Never infer plan visibility from cloud authentication alone.
    val hasLocalGarmin = state.localGarminIds.isNotEmpty()
    val safePlans = state.plans
    val hasHistory = state.account != null || hasLocalGarmin || safePlans.isNotEmpty()

    val safeActivities = if (hasHistory) visibleActivities else emptyList()
    val safeHistory = if (hasHistory) state.activities else emptyList()
    val evidence = remember(safePlans, safeHistory, safeActivities) {
        TodaySchedule(safePlans, safeHistory + safeActivities)
    }
    var chosenPage by rememberSaveable(state.account ?: state.localPlanOwner,hasHistory) { mutableStateOf("all") }
    val eligiblePages = remember(todayDate, safeHistory, evidence) {
        (TodayDisciplineAnalytics.available(safeHistory, safePlans, Instant.now(), ZoneId.systemDefault()) +
            evidence.sessions.mapNotNull { it.discipline }).distinct().sortedBy { it.ordinal }
    }
    val selectedPage = chosenPage.takeIf { page ->
        page == "all" || eligiblePages.any { it.key == page }
    } ?: "all"
    val filter = eligiblePages.firstOrNull { it.key == selectedPage }
    val calendarActivities = remember(filter, safeActivities, evidence) {
        evidence.recordings(safeActivities).filter { filter == null || TodayDiscipline.fromActivity(it.type) == filter }
    }
    val calendarLogs = remember(filter, safeHistory, evidence) {
        evidence.confirmed(safeHistory, Instant.now()).filter { filter == null || TodayDiscipline.fromLog(it.discipline) == filter }
    }
    val calendarSessions = remember(filter, evidence) {
        evidence.sessions.filter { filter == null || it.discipline == filter }
    }
    val recordedOnDate = remember(date, todayDate, calendarActivities) {
        if (date.isAfter(todayDate)) emptyList() else calendarActivities.filter {
            !it.startedAt.isAfter(Instant.now()) &&
                it.startedAt.atZone(ZoneId.systemDefault()).toLocalDate() == date
        }
    }
    val confirmedOnDate = remember(date, todayDate, calendarLogs) {
        if (date.isAfter(todayDate)) emptyList() else calendarLogs.filter { it.date == date }
    }
    val schedule = calendarSessions.filter { it.session.date == date }.map { it.session }
    // A completed day uses recorded styling only when the evidence is saved.
    // If a second session is still planned today, keep its actionable hero.
    val hasUnfinishedWorkout = schedule.any { it.status in setOf("planned", "in_progress") }
    val mixedToday = date == todayDate && hasUnfinishedWorkout &&
        (recordedOnDate.isNotEmpty() || confirmedOnDate.isNotEmpty())
    // Historical/recorded styling uses only currently visible persisted
    // records; a local plan is sufficient without cloud authentication.
    val showRecorded = hasHistory && (isPastDate ||
        (date == todayDate && !hasUnfinishedWorkout &&
            (recordedOnDate.isNotEmpty() || confirmedOnDate.isNotEmpty())))
    val recordedKinds = recordedOnDate.map { todayRecordedKind(it.type) } +
        confirmedOnDate.map { todayHeroDiscipline(it.discipline) }
    val tone = if (selectedPage != "all") todayDisciplineColor(selectedPage) else when {
        mixedToday -> TodayLook.lime
        showRecorded && recordedKinds.distinct().size > 1 -> TodayLook.lilac
        showRecorded && recordedOnDate.isNotEmpty() -> todayRecordedColor(recordedOnDate.first().type)
        showRecorded && confirmedOnDate.isNotEmpty() ->
            todayDisciplineColor(confirmedOnDate.first().discipline)
        showRecorded -> TodayLook.teal
        !evidence.hasApprovedPlan -> TodayLook.lime
        schedule.size > 1 ->
            if (schedule.map { todayHeroDiscipline(it.discipline) }.distinct().size == 1)
                todayDisciplineColor(schedule.first().discipline)
            else TodayLook.coral
        schedule.isEmpty() -> TodayLook.lilac
        else -> todayDisciplineColor(schedule.first().discipline)
    }
    // Observe frames in the individual list items rather than at screen root.
    val animatedTone = animateColorAsState(
        tone, tween(if (reduceMotion) 0 else 260), label = "today-accent"
    )
    val notchPosition = animateFloatAsState(
        targetValue = (date.dayOfWeek.value - .5f) / 7f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = TracksideLiquidMotion.DAMPING,
            stiffness = TracksideLiquidMotion.STIFFNESS
        ),
        label = "today-notch"
    )
    val weeklyOverview = remember(weekStart, safeActivities, safeHistory, evidence, filter) {
        TodayOverviewCalculator.computeWeeklyOverview(weekStart, safeActivities, safeHistory, safePlans, discipline = filter, schedule = evidence)
    }
    val dailyTrainingState = remember(date, recordedOnDate, confirmedOnDate, evidence, filter) {
        TodayOverviewCalculator.buildDailyTrainingState(
            date = date,
            recordedOnDate = recordedOnDate,
            confirmedOnDate = confirmedOnDate,
            rawHistory = safeHistory,
            plans = safePlans,
            disciplineFilter = filter,
            schedule = evidence
        )
    }
    val consistencyByDate = remember(safeActivities, safeHistory, safePlans, freeformBoard, state.freeformOwner, todayDate) {
        projectConsistencyDisciplinesByDate(
            activities = safeActivities + safeHistory,
            plans = safePlans,
            freeformBoard = freeformBoard,
            owner = state.freeformOwner,
            asOf = todayDate
        )
    }
    val recentActivities = remember(state.activitySummariesReady, safeActivities, safeHistory, safePlans) {
        if (!state.activitySummariesReady) return@remember emptyList()
        TodayOverviewCalculator.buildRecentActivities(
            visibleActivities = safeActivities,
            rawHistory = safeHistory,
            plans = safePlans,
            limit = 5
        )
    }
    // The signed-out dashboard is a compact static view on ordinary phones.
    // The former measured-overflow toggle fed its own bottom padding back
    // into the calculation, incorrectly enabling page dragging. Preserve a
    // scrollable fallback on short screens and at accessibility font scales.
    val listState = rememberLazyListState()
    // A fresh page begins with its selector and chart rather than inheriting
    // the All-page scroll offset. Rendering still uses the retained root.
    LaunchedEffect(selectedPage) {
        if (listState.firstVisibleItemIndex != 0 ||
            listState.firstVisibleItemScrollOffset != 0
        ) listState.scrollToItem(0)
    }
    LaunchedEffect(selectedDay) {
        // Date changes happen via the visible calendar. Re-anchor it fully,
        // including the few-pixel drift that persists after a tap or swipe.
        if (listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset > 0
        ) {
            listState.scrollToItem(0, 0)
        }
    }

    val pageOrder = remember(eligiblePages) {
        listOf("all") + eligiblePages.map { it.key }
    }

    Column(Modifier.fillMaxSize().background(TodayAdaptivePalette.bg)) {
        TodayDisciplineTabs(
            available = eligiblePages,
            selectedKey = selectedPage,
            reduceMotion = reduceMotion,
            onSelect = { chosenPage = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f)
                .testTag("screen").semantics { paneTitle = "Today" },
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 4.dp, bottom = 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "today-calendar") {
                TodayWeekCalendar(
                    date = date,
                    accent = animatedTone.value,
                    selectionFractionState = notchPosition,
                    visibleActivities = calendarActivities,
                    logs = calendarLogs,
                    sessions = calendarSessions,
                    evidence = evidence,
                    reduceMotion = reduceMotion,
                    onSelect = { selectedDay = it.toEpochDay() },
                    onPrevious = { selectedDay = date.minusWeeks(1).toEpochDay() },
                    onNext = { selectedDay = date.plusWeeks(1).toEpochDay() }
                )
            }
            item(key = "today-date-context") {
                TodayDateContextHeader(date, todayDate, { selectedDay = todayDate.toEpochDay() },
                    modifier = Modifier.testTag("today-date-header"))
            }
            if ((selectedPage == "all" || selectedPage == TodayDiscipline.RUNNING.key) &&
                state.activityMatches.values.any { it.matchStatus == ActivityMatchStatus.NEEDS_CONFIRMATION }) {
                item(key = "pending-activity-matches") { PendingActivityMatchCards(state, actions) }
            }
            if (selectedPage == TodayDiscipline.RUNNING.key) {
                if (adaptationProposal?.type == AdaptationType.EASY_PACING_LOCK &&
                    state.activeAthleteDiagnostic?.recalibrationAdvisory != RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST) {
                    item(key = "running-easy-pacing-lock") { EasyPacingLockBanner() }
                }
            }
            if (selectedPage == "all" && adaptationProposal?.status == AdaptationProposalStatus.PENDING) {
                item(key = "today-adaptation-proposal") {
                    AdaptationProposalBanner(adaptationProposal, onReview = { reviewedProposal = true })
                }
            }
            state.message?.takeIf(String::isNotBlank)?.let { message ->
                val suppressLoggedOutBanner = state.account == null || state.needsSignIn
                if (!suppressLoggedOutBanner) {
                    item(key = "today-status-message") {
                        SignalCard { Text(message, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item(key = "today-hero") {
                val showHeroSkeleton = rememberDelayedSkeletonVisible(!state.todayPlanReady && dailyTrainingState is TodayDailyTrainingState.NoPlanCompanion)
                if (isPastDate && !evidence.hasApprovedPlan && dailyTrainingState is TodayDailyTrainingState.NoPlanCompanion && state.todayPlanReady) {
                    TodayCompactEmptyDay(online = online)
                } else if (!state.todayPlanReady && dailyTrainingState is TodayDailyTrainingState.NoPlanCompanion) {
                    SkeletonShimmerContainer(reducedMotion = reduceMotion || !showHeroSkeleton) {
                        SkeletonHeroTrainingCard(
                            modifier = Modifier
                                .testTag("today-hero-skeleton")
                                .semantics { contentDescription = "Loading training day" },
                            reducedMotion = reduceMotion || !showHeroSkeleton
                        )
                    }
                } else {
                    TodayDailyTrainingCard(
                        selectedDate = date,
                        todayDate = todayDate,
                        dailyState = dailyTrainingState,
                        cachedDetails = state.details,
                        hasPrimaryHeroTag = true,
                        onOpenWorkout = onOpenWorkout,
                        onOpenActivityDetail = onOpenActivityDetail,
                        onOpenTraining = onOpenTraining
                    )
                }
            }
            if (hasHistory) {
                val currentWeekStart = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekLabel = if (weekStart == currentWeekStart) {
                    "THIS WEEK"
                } else {
                    val weekEnd = weekStart.plusDays(6)
                    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                    "${weekStart.format(fmt)}–${weekEnd.format(fmt)}".uppercase(Locale.ENGLISH)
                }
                item(key = "today-training-overview") {
                    TodayTrainingOverviewCard(
                        overview = weeklyOverview,
                        weekLabel = weekLabel,
                        discipline = filter
                    )
                }
                if (selectedPage == "all") {
                    item(key = "today-all-consistency") {
                        ConsistencyHeatmapStrip(
                            disciplinesByDate = consistencyByDate,
                            asOf = todayDate
                        )
                    }
                    if (state.activitySummariesReady) {
                        item(key = "today-recent-activities") {
                            TodayRecentActivitiesSection(
                                rows = recentActivities,
                                cachedDetails = state.details,
                                onSeeAll = onOpenActivity,
                                onOpenActivityDetail = onOpenActivityDetail,
                                onOpenWorkout = onOpenWorkout
                            )
                        }
                    } else {
                        item(key = "today-recent-skeleton") {
                            val showRecentSkeleton = rememberDelayedSkeletonVisible(true)
                            if (showRecentSkeleton) {
                                SkeletonShimmerContainer(reducedMotion = reduceMotion) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        SkeletonText(widthFraction = 0.4f, height = 15.dp, reducedMotion = reduceMotion)
                                        SkeletonActivityRow(reducedMotion = reduceMotion)
                                        SkeletonActivityRow(reducedMotion = reduceMotion)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedPage == "all" && consistencyByDate.isNotEmpty()) {
                item(key = "today-all-consistency") {
                    ConsistencyHeatmapStrip(
                        disciplinesByDate = consistencyByDate,
                        asOf = todayDate
                    )
                }
            } else if (selectedPage == "all" && !state.activitySummariesReady) {
                item(key = "today-overview-skeleton") {
                    val showOverviewSkeleton = rememberDelayedSkeletonVisible(true)
                    if (showOverviewSkeleton) {
                        SkeletonShimmerContainer(reducedMotion = reduceMotion) {
                            Column(
                                modifier = Modifier.testTag("today-overview-skeleton"),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SkeletonOverviewMetricsCard(reducedMotion = reduceMotion)
                                SkeletonConsistencyHeatmap(reducedMotion = reduceMotion)
                            }
                        }
                    }
                }
            }
            if (selectedPage != "all") {
            item(key = "today-discipline-visuals") {
                var dragSum by remember(selectedPage) { mutableFloatStateOf(0f) }
                AnimatedContent(
                    targetState = selectedPage,
                    modifier = Modifier.pointerInput(pageOrder, selectedPage) {
                        if (pageOrder.size > 1) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragSum = 0f },
                                onDragEnd = {
                                    val idx = pageOrder.indexOf(selectedPage)
                                    if (dragSum < -72f && idx in 0 until pageOrder.lastIndex) {
                                        chosenPage = pageOrder[idx + 1]
                                    } else if (dragSum > 72f && idx > 0) {
                                        chosenPage = pageOrder[idx - 1]
                                    }
                                    dragSum = 0f
                                }
                            ) { _, dragAmount ->
                                dragSum += dragAmount
                            }
                        }
                    },
                    transitionSpec = {
                        val forward = pageOrder.indexOf(targetState) >= pageOrder.indexOf(initialState)
                        if (reduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (slideInHorizontally(tween(220)) { if (forward) it / 5 else -it / 5 } +
                                fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(160)) { if (forward) -it / 5 else it / 5 } +
                                    fadeOut(tween(150)))
                        }
                    },
                    label = "today-discipline-flow"
                ) { page ->
                    val discipline = TodayDiscipline.entries.firstOrNull { it.key == page }
                    if (discipline != null) {
                        val recentWeeks = remember(weekStart, discipline, safeHistory, safePlans) {
                            TodayDisciplineAnalytics.trend(
                                safeHistory, safePlans, Instant.now(), ZoneId.systemDefault(),
                                weekStart, discipline
                            )
                        }
                        val runningReview by produceState<List<RunningWeekCoach>>(
                            emptyList(),
                            discipline,
                            recentWeeks,
                            safeHistory,
                            safePlans,
                            state.details,
                            state.account,
                            state.localGarminOwner,
                            state.localPlanOwner,
                            state.localGarminIds
                        ) {
                            value = if (discipline != TodayDiscipline.RUNNING) emptyList()
                            else withContext(Dispatchers.IO) {
                                val corrections = UserActivityWorkoutLabelStore(context)
                                val ownerIds = listOfNotNull(
                                    state.account,
                                    state.localGarminOwner,
                                    state.localPlanOwner
                                ).distinct()
                                val loadedUserLabels = buildMap {
                                    safeHistory.forEach { act ->
                                        val actOwner = activityCorrectionOwner(act.id, state)
                                        val lbl = actOwner?.let { corrections.load(it, act.id) }
                                            ?: ownerIds.firstNotNullOfOrNull { corrections.load(it, act.id) }
                                        if (lbl != null) put(act.id, lbl)
                                    }
                                }
                                val detectionOwner = state.account ?: state.localGarminOwner ?: state.localPlanOwner
                                val detectedById = if (detectionOwner != null) {
                                    Engine15WorkoutDetection.detectAll(
                                        owner = detectionOwner,
                                        activities = safeHistory,
                                        allowedActivityIds = safeHistory.map { it.id }.toSet(),
                                        detailsByActivityId = state.details,
                                        plans = safePlans,
                                        userLabels = loadedUserLabels,
                                        asOf = Instant.now()
                                    ).associateBy { it.activityId }
                                } else emptyMap()
                                val preferences = AthleteHeartRatePreferences(context)
                                val garminZones = GarminZonePreferenceStore(context)
                                val manualZones = preferences.settings(state.account)
                                recentWeeks.map { week ->
                                    summarizeRunningWeek(week) { run ->
                                        val owner = activityCorrectionOwner(run.id, state)
                                        loadedUserLabels[run.id] ?: detectedById[run.id]?.takeIf {
                                            it.kind != ActivityWorkoutKind.UNKNOWN
                                        }?.let { det ->
                                            UserActivityWorkoutLabel(det.kind, det.qualitySubtype)
                                        } ?: run {
                                            val detail = state.details[run.id]
                                            val distribution = detail?.let {
                                                if (garminZones.source(owner) == DisplayHeartRateZoneSource.GARMIN) {
                                                    garminZones.load(owner)?.let { profile -> HeartRateZoneAnalysis.distribute(detail.samples, profile.profile) }
                                                } else {
                                                    HeartRateZoneAnalysis.distribute(detail.samples, manualZones)
                                                }
                                            }
                                            val detected = ActivityWorkoutIntelligence.detect(run, detail, safeHistory, distribution)
                                            if (detected.kind != ActivityWorkoutKind.UNKNOWN) {
                                                UserActivityWorkoutLabel(detected.kind, detected.qualitySubtype)
                                            } else {
                                                val dur = run.movingTimeS ?: run.durationS
                                                val z12 = distribution?.buckets?.take(2)?.sumOf { it.seconds } ?: 0.0
                                                val zTotal = distribution?.classifiedSeconds ?: 0.0
                                                if (dur != null && dur > 0 && zTotal >= dur * 0.55 && z12 >= zTotal * 0.85) {
                                                    UserActivityWorkoutLabel(ActivityWorkoutKind.EASY_RUN)
                                                } else null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        TodayDisciplineDashboard(
                            discipline = discipline,
                            trend = recentWeeks,
                            runningReview = runningReview,
                            plans = safePlans,
                            today = todayDate,
                            allHistory = safeHistory,
                            details = state.details,
                            ownerId = state.account ?: state.localGarminOwner ?: state.localPlanOwner,
                            reduceMotion = reduceMotion,
                            onOpenActivity = onOpenActivityDetail,
                            onOpenWorkout = onOpenWorkout
                        )
                    }
                }
            }
        }
        }
        if (reviewedProposal && adaptationProposal != null) {
            AdaptationProposalSheet(
                proposal = adaptationProposal,
                busy = state.busy,
                onDismiss = { reviewedProposal = false },
                onApply = actions.approveAdaptationProposal,
                onDismissProposal = actions.dismissAdaptationProposal
            )
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

/**
 * Minimal week-first Today navigation. Week switching animates only the weekday
 * strip; the card and list don't fade or slide. The selected date remains a
 * single LocalDate so a week swipe preserves its weekday and the notch anchor.
 */
@Composable
private fun TodayWeekCalendar(
    date: LocalDate,
    accent: Color,
    selectionFractionState: State<Float>,
    visibleActivities: List<ActivityRecord>,
    logs: List<ConfirmedWorkout>,
    sessions: List<TodayScheduledSession>,
    evidence: TodaySchedule,
    reduceMotion: Boolean,
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

    // Reserve a consistent calendar height across all selected weekdays with
    // clean top/bottom headroom so weekday initials and dots never clip.
    Box(
        modifier = Modifier.fillMaxWidth().height(84.dp)
            .semantics {
                contentDescription = "Weekly training calendar"
                customActions = listOf(
                    CustomAccessibilityAction("Previous week") {
                        previousWeek()
                        true
                    },
                    CustomAccessibilityAction("Next week") {
                        nextWeek()
                        true
                    }
                )
            }
            .pointerInput(monday) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = {
                        val threshold = 56.dp.toPx()
                        when {
                            dragDistance >= threshold -> previousWeek()
                            dragDistance <= -threshold -> nextWeek()
                        }
                        dragDistance = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        dragDistance += amount
                        change.consume()
                    }
                )
            }
            .testTag("today-week-swipe")
    ) {
        AnimatedContent(
            targetState = monday,
            transitionSpec = {
                when {
                    reduceMotion ->
                        EnterTransition.None togetherWith ExitTransition.None
                    else -> {
                        val forward = targetState.isAfter(initialState)
                        slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = .88f,
                                stiffness = TracksideLiquidMotion.STIFFNESS
                            )
                        ) { width ->
                            if (forward) width else -width
                        } togetherWith slideOutHorizontally(
                            animationSpec = spring(
                                dampingRatio = .88f,
                                stiffness = TracksideLiquidMotion.STIFFNESS
                            )
                        ) { width ->
                            if (forward) -width else width
                        }
                    }
                }
            },
            label = "today-week-slide"
        ) { visibleMonday ->
            // Both outgoing and incoming weeks use their own saved markers
            // throughout the slide; none temporarily inherit another week's dots.
            val markers = remember(visibleMonday, visibleActivities, logs, sessions, evidence) {
                todayWeekMarkers(visibleMonday, visibleActivities, logs, sessions, evidence)
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth()
                    .testTag("today-week-picker")
                    .semantics {
                        contentDescription = "Week of " + visibleMonday.format(
                            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
                        )
                    }
            ) {
                val diameter = 37.dp
                val restingFraction = (date.dayOfWeek.value - .5f) / 7f
                Box(
                    Modifier.align(Alignment.TopStart)
                        .size(diameter)
                        .graphicsLayer {
                            // The highlight now moves entirely in the graphics
                            // layer. No per-frame Compose placement pass.
                            val fraction = selectionFractionState.value
                            translationX = (maxWidth.toPx() * fraction -
                                diameter.toPx() / 2f).coerceIn(
                                0f, (maxWidth - diameter).coerceAtLeast(0.dp).toPx()
                            )
                            translationY = 27.dp.toPx()
                            val pressure = if (reduceMotion) 0f else
                                TracksideLiquidMotion.pressure(
                                    fraction, restingFraction, 1f / 7f
                                )
                            scaleX = TracksideLiquidMotion.stretch(pressure, .22f)
                            scaleY = TracksideLiquidMotion.squash(pressure, .10f)
                        }
                        .background(accent, CircleShape)
                        .testTag("today-date-indicator")
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
                    (0..6).forEach { idx ->
                        val day = visibleMonday.plusDays(idx.toLong())
                        val isSelected = day == date
                        val marker = markers[day] ?: TodayDayMarker(false, false)
                        val numberInk by animateColorAsState(
                            targetValue = if (isSelected) TodayLook.black else TodayLook.muted,
                            animationSpec = tween(if (reduceMotion) 0 else 160),
                            label = "today-day-ink-$idx"
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectDay(day) }
                                .padding(top = 4.dp)
                                .testTag("today-day-" + idx)
                                .semantics {
                                    contentDescription = day.format(
                                        DateTimeFormatter.ofPattern(
                                            "EEEE d MMMM", Locale.getDefault()
                                        )
                                    ) + when {
                                        marker.completed && marker.pending ->
                                            ", completed and planned"
                                        marker.completed -> ", completed"
                                        marker.pending -> ", planned"
                                        else -> ""
                                    }
                                    selected = isSelected
                                    role = Role.Button
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                Modifier.height(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    letters[idx],
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    color = if (isSelected) accent else TodayLook.muted,
                                    fontWeight = if (isSelected) FontWeight.Bold
                                        else FontWeight.Medium
                                )
                            }
                            Box(
                                Modifier.size(diameter),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    // Only the newly selected/deselected
                                    // text animates; the notch spring does not
                                    // recompose the whole week of labels.
                                    color = numberInk,
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
                                    Box(
                                        Modifier.size(5.dp).background(
                                            TodayLook.lime, CircleShape
                                        )
                                    )
                                }
                                if (marker.pending) {
                                    Box(
                                        Modifier.size(5.5.dp).border(
                                            1.2.dp, Color(0xFF8B9298), CircleShape
                                        )
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

private fun todayNotchedShape(fraction: Float) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val center = w * fraction.coerceIn(.06f, .94f)
    val baseRadius = w * .052f
    val leftRadius = minOf(baseRadius, center * .35f)
    val rightRadius = minOf(baseRadius, (w - center) * .35f)
    // A small, shallow concave scoop, not the previous deep U-shaped cutout.
    // Keep the scoop centered under the selected weekday, including edge days.
    // Stretch the concave scoop gently between date centers, then let it
    // settle to its approved original shape once the spring comes to rest.
    val wake = TracksideLiquidMotion.notchWake(fraction)
    val maxHalf = minOf(
        center - leftRadius - 2f,
        w - rightRadius - center - 2f
    ).coerceAtLeast(w * .012f)
    val half = minOf(w * .065f * (1f + wake * .20f), maxHalf)
    val depth = minOf(h * .035f * (1f + wake * .30f), w * .034f)
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
    val distances = records.mapNotNull { it.distanceM?.takeIf { value -> value.isFinite() } } +
        native.mapNotNull { it.distanceM?.takeIf { value -> value.isFinite() } }
    val durations = records.mapNotNull { it.durationS?.takeIf { value -> value.isFinite() } } +
        native.mapNotNull { it.durationSeconds?.takeIf { value -> value.isFinite() } }
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
 * Chronological UI order for sessions recorded on the selected day.
 *
 * ActivityRecords have an actual start time. Native confirmed workouts
 * contain only the performed date; their log.at is the time the completion
 * was saved, NOT necessarily when the athlete started the workout.
 * Use it for relative ordering only if it falls on the performed date.
 * Backfilled/date-only logs have unknown start order, so display them
 * after timestamped records with a deterministic ID fallback.
 */
private data class TodayRecordedDisplayEntry(
    val time: Instant?,
    val stableKey: String,
    val activity: ActivityRecord? = null,
    val confirmed: ConfirmedWorkout? = null
)

private fun todayChronologicalRecordedEntries(
    activities: List<ActivityRecord>,
    logs: List<ConfirmedWorkout>,
    plans: List<PlanSnapshot>
): List<TodayRecordedDisplayEntry> {
    val recorded = activities.map { activity ->
        TodayRecordedDisplayEntry(
            time = activity.startedAt,
            stableKey = "activity:" + activity.id,
            activity = activity
        )
    }
    val zone = ZoneId.systemDefault()
    val confirmed = logs.map { entry ->
        val savedAt = plans.firstOrNull { it.id == entry.planId }
            ?.sessions?.firstOrNull { it.id == entry.sessionId }?.log?.at
        TodayRecordedDisplayEntry(
            time = savedAt?.takeIf { it.atZone(zone).toLocalDate() == entry.date },
            stableKey = "log:" + entry.planId + ":" + entry.sessionId,
            confirmed = entry
        )
    }
    return (recorded + confirmed).sortedWith(
        compareBy<TodayRecordedDisplayEntry> { it.time == null }
            .thenBy { it.time }
            .thenBy { it.stableKey }
    )
}

/**
 * Compact, chronological recorded-day cards inspired by the owner-approved
 * reference. Only the first recorded card owns the curved weekday notch.
 *
 * No activity title, fabricated GPS track, or fixed session summary is shown.
 * A real saved ActivityRecord opens Activity Detail; a confirmed native log
 * opens its existing workout detail. Planned and recovery cards are untouched.
 */
@Composable
private fun TodayCompactRecordedCards(
    activities: List<ActivityRecord>,
    logs: List<ConfirmedWorkout>,
    plans: List<PlanSnapshot>,
    cachedDetails: Map<String, ActivityDetail>,
    notchFraction: Float,
    onOpenActivity: (String) -> Unit,
    onOpenWorkout: (String, String) -> Unit
) {
    val ordered = remember(activities, logs, plans) {
        todayChronologicalRecordedEntries(activities, logs, plans)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ordered.forEachIndexed { index, entry ->
            val activity = entry.activity
            val confirmed = entry.confirmed
            val discipline = if (activity != null) {
                todayRecordedKind(activity.type)
            } else {
                todayHeroDiscipline(confirmed?.discipline ?: "other")
            }
            val accent = if (activity != null) {
                todayRecordedColor(activity.type)
            } else {
                todayDisciplineColor(confirmed?.discipline ?: "")
            }
            val distance = (activity?.distanceM ?: confirmed?.distanceM)
                ?.takeIf { it.isFinite() && it >= 0.0 }
            val duration = (activity?.durationS ?: confirmed?.durationSeconds)
                ?.takeIf { it.isFinite() && it >= 0.0 }
            val distancePrimary = discipline in setOf("running", "walking") && distance != null
            val primary = when {
                distancePrimary -> String.format(
                    Locale.getDefault(), "%.2f km", distance!! / 1000
                )
                duration != null -> ActivityRecord.duration(duration)
                else -> "Completed session"
            }
            val pace = activity?.takeIf { it.supportsPace }?.let { record ->
                when {
                    record.recordedPace != null -> record.paceLabel
                    record.garminMovingPaceSPerKm != null ->
                        "Moving " + record.detailPaceLabel
                    else -> null
                }
            }
            val secondary = buildList {
                if (distancePrimary) {
                    duration?.let { add(ActivityRecord.duration(it)) }
                } else if (distance != null) {
                    add(String.format(Locale.getDefault(), "%.2f km", distance / 1000))
                }
                pace?.let { add(it) }
            }.joinToString("  ·  ").ifBlank {
                if (duration != null && !distancePrimary) "Recorded duration"
                else "Saved workout"
            }
            TodayCompactRecordedCard(
                first = index == 0,
                notchFraction = notchFraction,
                discipline = discipline,
                accent = accent,
                recordedDetail = activity?.let { cachedDetails[it.id] },
                primary = primary,
                secondary = secondary,
                tag = if (index == 0) "today-recorded-hero"
                    else "today-recorded-session-" + entry.stableKey,
                onClick = {
                    if (activity != null) {
                        onOpenActivity(activity.id)
                    } else if (confirmed != null) {
                        onOpenWorkout(confirmed.planId, confirmed.sessionId)
                    }
                }
            )
        }
    }
}

@Composable
private fun TodayCompactRecordedCard(
    first: Boolean,
    notchFraction: Float,
    discipline: String,
    accent: Color,
    recordedDetail: ActivityDetail?,
    primary: String,
    secondary: String,
    tag: String,
    onClick: () -> Unit
) {
    val shape = remember(first, notchFraction) {
        if (first) todayNotchedShape(notchFraction)
        else RoundedCornerShape(18.dp)
    }
    val route = remember(recordedDetail) {
        recordedDetail?.let { RecordedGpsRoute.from(it.samples) }
            ?.takeIf { it.pointCount >= 2 }
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).testTag(tag),
        shape = shape,
        color = StrideLabVisualPalette.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, StrideLabVisualPalette.border
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = if (first) 16.dp else 13.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    todayKindLabel(discipline),
                    color = TodayLook.muted,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    primary,
                    color = TodayLook.text,
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    secondary,
                    color = TodayLook.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (route != null) {
                TodayCompactGpsThumbnail(
                    route = route,
                    modifier = Modifier.size(width = 66.dp, height = 64.dp)
                )
            }
        }
    }
}

/**
 * Real recorded coordinates only: small, offline, north-up route outline.
 * Nothing is shown without an already-saved activity detail stream.
 * Segment gaps are never connected; this is not a street-map preview.
 */
@Composable
internal fun TodayCompactGpsThumbnail(
    route: RecordedGpsRoute,
    modifier: Modifier = Modifier
) {
    val segments = remember(route) {
        val lines = route.displaySegments
        if (lines.size > 1) listOf(lines.maxByOrNull { it.size }.orEmpty()) else lines
    }
    val points = remember(segments) { segments.flatten() }
    if (points.size < 2) return
    val projection = remember(points) {
        cos(Math.toRadians(points.map { it.latitude }.average()))
            .coerceAtLeast(.01)
    }
    val minX = remember(points, projection) {
        points.minOf { it.longitude * projection }
    }
    val maxX = remember(points, projection) {
        points.maxOf { it.longitude * projection }
    }
    val minY = remember(points) { points.minOf { it.latitude } }
    val maxY = remember(points) { points.maxOf { it.latitude } }
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Recorded GPS route preview, north up"
        }
    ) {
        val dx = (maxX - minX).coerceAtLeast(.000001)
        val dy = (maxY - minY).coerceAtLeast(.000001)
        val width = (size.width - 8.dp.toPx()).coerceAtLeast(1f)
        val height = (size.height - 8.dp.toPx()).coerceAtLeast(1f)
        val scale = minOf(width / dx, height / dy)
        fun position(p: RecordedGpsPoint) = Offset(
            x = ((size.width - (maxX - minX) * scale) / 2 +
                (p.longitude * projection - minX) * scale).toFloat(),
            y = ((size.height - (maxY - minY) * scale) / 2 +
                (maxY - p.latitude) * scale).toFloat()
        )
        segments.forEach { segment ->
            if (segment.size > 1) {
                val path = Path().apply {
                    moveTo(position(segment.first()).x, position(segment.first()).y)
                    segment.drop(1).forEach { point ->
                        val p = position(point)
                        lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    path = path,
                    color = TodayLook.lime.copy(alpha = 0.20f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
                drawPath(
                    path = path,
                    color = TodayLook.lime,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/**
 * Multiple saved activities: one compact neutral summary, not a tall list.
 * The individual source records and native logs appear only on View Sessions.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        Modifier.fillMaxWidth().height(TodayHeroHeight)
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
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
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 490.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    todayChronologicalRecordedEntries(activities, logs, plans)
                        .forEach { entry ->
                            val activity = entry.activity
                            val log = entry.confirmed
                            if (activity != null) {
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
                            } else if (log != null) {
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
    }
}

/**
 * Today may have genuine recorded training AND another planned workout.
 * Surface both states without claiming that the pending workout is completed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayMixedDayCard(
    date: LocalDate,
    planId: String,
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
        Modifier.fillMaxWidth().height(TodayHeroHeight)
            .clip(shape)
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
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
                        onClick = { onOpenWorkout(planId, next.id) },
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
                    todayChronologicalRecordedEntries(activities, logs, plans)
                        .forEach { entry ->
                            val activity = entry.activity
                            val log = entry.confirmed
                            if (activity != null) {
                                TodayRecordedRow(
                                    title = activity.title,
                                    subtitle = listOfNotNull(
                                        activity.distanceM?.let { activity.distanceLabel },
                                        activity.durationS?.let { activity.durationLabel }
                                    ).joinToString(" · ").ifBlank { activity.sourceLabel },
                                    tint = todayRecordedColor(activity.type),
                                    onClick = {
                                        showAll = false
                                        onOpenActivity(activity.id)
                                    }
                                )
                            } else if (log != null) {
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
                        }
                    pending.forEach { session ->
                        TodayRecordedRow(
                            title = session.effectiveTitle,
                            subtitle = todaySessionDetails(session) + " · " +
                                session.status.replace('_', ' '),
                            tint = todayDisciplineColor(session.discipline),
                            onClick = {
                                showAll = false
                                onOpenWorkout(planId, session.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Minimal empty-day status. Only shown when the selected date has neither
 * a saved activity/log nor a session in the current approved schedule.
 * It has no decorative success checkmark, fabricated workout, or dead CTA.
 */
@Composable
private fun TodayCompactEmptyDay(online: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(TodayLook.card)
            .border(1.dp, TodayLook.outline, shape)
            .testTag("today-compact-empty-day")
            .semantics {
                contentDescription = "No saved activity or scheduled session for this date"
            }
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                Modifier.size(7.dp)
                    .background(TodayLook.muted, CircleShape)
            )
            Text(
                "Nothing scheduled or saved",
                color = TodayLook.text, fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            if (online) "No activity or workout for this date."
            else "Offline · based on saved records and schedule.",
            color = TodayLook.muted, fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(start = 16.dp)
        )
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
    loading: Boolean,
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
        count == 0 && loading -> "LOADING HISTORY"
        count == 0 -> "NO SAVED ACTIVITY"
        else -> "RECORDED " + todayKindLabel(kind).uppercase(Locale.ROOT)
    }
    // Keep titles in Activity Detail. Every Today card shows the same
    // summary hierarchy whether the date contains one or multiple runs.
    val title = when {
        signedOut -> "Sign in"
        count == 0 && loading -> "Checking records"
        count == 0 -> "Nothing saved"
        kind == "running" -> "1 run"
        else -> "1 session"
    }
    val summary = when {
        signedOut -> "Your activity is private."
        count == 0 && loading -> "Loading saved history"
        count == 0 && !online -> "Offline · saved records only"
        count == 0 -> "No saved activity for this date."
        else -> ""
    }
    val distanceM = (firstActivity?.distanceM ?: firstLog?.distanceM)
        ?.takeIf { it.isFinite() && it >= 0.0 }
    val durationS = (firstActivity?.durationS ?: firstLog?.durationSeconds)
        ?.takeIf { it.isFinite() && it >= 0.0 }
    val pace = if (count == 1 && firstActivity?.supportsPace == true) {
        when {
            firstActivity.recordedPace != null -> "Recorded pace · " + firstActivity.paceLabel
            firstActivity.garminMovingPaceSPerKm != null ->
                "Moving pace · " + firstActivity.detailPaceLabel
            else -> null
        }
    } else null
    Box(
        Modifier.fillMaxWidth().height(TodayHeroHeight)
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
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
                lineHeight = 29.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(if (count == 1) .76f else 1f)
            )
            if (count == 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (kind == "running" || kind == "walking") {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Distance", color = TodayLook.muted, fontSize = 10.sp)
                            Text(
                                distanceM?.let {
                                    String.format(Locale.getDefault(), "%.2f km", it / 1000)
                                } ?: "—",
                                color = TodayLook.text, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Duration", color = TodayLook.muted, fontSize = 10.sp)
                        Text(
                            durationS?.let { ActivityRecord.duration(it) } ?: "—",
                            color = TodayLook.text, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1
                        )
                    }
                }
            } else {
                Text(
                    summary, color = TodayLook.text.copy(alpha = .86f), fontSize = 12.sp,
                    lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (pace != null) {
                Text(
                    pace, color = cardAccent.copy(alpha = .92f),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.78f)
                )
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

private fun todayHeroDiscipline(discipline: String): String =
    when (discipline.lowercase(Locale.ROOT)) {
        "run", "running" -> "running"
        "str", "strength" -> "strength"
        "cal", "calisthenics", "body", "bodyweight" -> "calisthenics"
        "cond", "conditioning" -> "conditioning"
        else -> discipline.lowercase(Locale.ROOT)
    }

private fun todaySessionDetails(session: PlannedSession): String {
    val parts = mutableListOf<String>()
    Engine15SessionPresentation.currentDistance(session)?.let { parts.add(String.format(Locale.getDefault(), "%.1f km", it)) }
    if (session.effectiveMinutes > 0) parts.add(session.effectiveMinutes.toString() + " min")
    if (session.discipline != "run" && session.effectiveExercises.isNotEmpty()) {
        parts.add(session.effectiveExercises.size.toString() + " exercises")
    }
    if (parts.isEmpty()) parts.add(session.status.replace('_', ' '))
    return parts.joinToString("  ·  ")
}

/** Crisp, small running-shoe mark used only on compact recorded cards. */
@Composable
internal fun TodayRecordedRunningGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ink = Color(0xFF1A2615)
        // Option C: minimal, outlined running shoe. No stick figure,
        // fill-heavy silhouette, or bright lace details at small sizes.
        val upper = Path().apply {
            moveTo(w * .08f, h * .69f)
            lineTo(w * .23f, h * .64f)
            lineTo(w * .36f, h * .43f)
            quadraticTo(w * .40f, h * .36f, w * .46f, h * .46f)
            lineTo(w * .62f, h * .64f)
            quadraticTo(w * .66f, h * .68f, w * .74f, h * .68f)
            lineTo(w * .84f, h * .67f)
            quadraticTo(w * .93f, h * .67f, w * .94f, h * .77f)
            quadraticTo(w * .96f, h * .85f, w * .84f, h * .88f)
            lineTo(w * .22f, h * .88f)
            quadraticTo(w * .09f, h * .87f, w * .08f, h * .78f)
            close()
        }
        drawPath(
            path = upper,
            color = ink,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        drawLine(
            color = ink,
            start = Offset(w * .20f, h * .79f),
            end = Offset(w * .87f, h * .79f),
            strokeWidth = 1.65.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = ink,
            start = Offset(w * .47f, h * .55f),
            end = Offset(w * .56f, h * .50f),
            strokeWidth = 1.65.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = ink,
            start = Offset(w * .53f, h * .61f),
            end = Offset(w * .62f, h * .56f),
            strokeWidth = 1.65.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/** Tiny discipline-specific vector mark, not a stock athlete image. */
@Composable
private fun TodayWeeklyActivity(
    start: LocalDate,
    metrics: TodayWeekMetrics,
    selectedDayIndex: Int,
    reduceMotion: Boolean,
    onOpenActivity: () -> Unit,
    signedOut: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) .985f else 1f,
        animationSpec = tween(if (reduceMotion) 0 else 100),
        label = "today-weekly-press"
    )
    val dateLabel = start.format(
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    ) + " – " + start.plusDays(6).format(
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    )

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onOpenActivity()
        },
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .testTag("today-weekly-activity"),
        interactionSource = interactionSource,
        color = TodayLook.card,
        shape = RoundedCornerShape(19.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TodayLook.outline)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = 15.dp, vertical = if (signedOut) 13.dp else 15.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (signedOut) 9.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp)
                        .background(StrideLabVisualPalette.limeTint, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.trackside_figma_calendar),
                        contentDescription = null,
                        tint = TodayLook.lime,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Weekly Activity", color = TodayLook.text, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(dateLabel, color = TodayLook.muted, fontSize = 11.sp)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val maxSeconds = metrics.seconds.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                (0..6).forEach { idx ->
                    val hasActivity = metrics.seconds[idx] > 0 || metrics.counts[idx] > 0
                    val isSelectedDay = idx == selectedDayIndex
                    val target = when {
                        metrics.seconds[idx] > 0 ->
                            (metrics.seconds[idx] / maxSeconds *
                                (if (signedOut) 38 else 46)).toFloat()
                                .coerceIn(7f, if (signedOut) 38f else 46f)
                        metrics.counts[idx] > 0 -> 7f
                        else -> 3f
                    }
                    val barHeight by animateDpAsState(
                        targetValue = target.dp,
                        animationSpec = if (reduceMotion) snap() else spring(
                            dampingRatio = TracksideLiquidMotion.DAMPING,
                            stiffness = TracksideLiquidMotion.STIFFNESS
                        ),
                        label = "today-weekly-bar-$idx"
                    )
                    val capColor by animateColorAsState(
                        targetValue = when {
                            isSelectedDay -> TodayLook.lime
                            hasActivity -> TodayLook.lime.copy(alpha = 0.72f)
                            else -> StrideLabVisualPalette.border
                        },
                        animationSpec = tween(if (reduceMotion) 0 else 180),
                        label = "today-weekly-color-$idx"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            Modifier.width(24.dp).height(if (signedOut) 42.dp else 50.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                Modifier.width(14.dp).height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(
                                        when {
                                            isSelectedDay -> StrideLabVisualPalette.barGradient(TodayLook.lime)
                                            hasActivity -> StrideLabVisualPalette.barGradient(TodayLook.lime, alphaScale = 0.45f)
                                            else -> Brush.verticalGradient(
                                                listOf(StrideLabVisualPalette.border, StrideLabVisualPalette.surfaceRaised)
                                            )
                                        }
                                    )
                            ) {
                                if (hasActivity) {
                                    Box(
                                        Modifier.fillMaxWidth().height(2.dp)
                                            .align(Alignment.TopCenter)
                                            .background(capColor)
                                    )
                                }
                            }
                        }
                        Text(
                            days[idx],
                            color = if (isSelectedDay) TodayLook.lime else if (hasActivity) TodayLook.text else TodayLook.muted,
                            fontWeight = if (isSelectedDay) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(TodayLook.outline))

            // One discipline needs just a compact statistics row. Additional
            // disciplines grow the lower section naturally, not by reserving a
            // permanently tall 95dp gap. No redundant clock icon.
            val disciplineRows = metrics.disciplineCounts.size
            val dividerHeight = when {
                disciplineRows > 3 -> 132.dp
                disciplineRows == 3 -> 112.dp
                disciplineRows == 2 -> 77.dp
                else -> 43.dp
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    Modifier.weight(1f).heightIn(min = dividerHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        metrics.totalSeconds?.let(::todayTimeLabel) ?: "—",
                        color = TodayLook.teal, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (metrics.partialTimes) "Known active time" else "Active time",
                        color = TodayLook.muted, fontSize = 10.sp,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Box(
                    Modifier.width(1.dp).height(dividerHeight).background(TodayLook.outline)
                )
                Column(
                    Modifier.weight(1.5f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (metrics.disciplineCounts.isEmpty()) {
                        Text(
                            "No recorded sessions", color = TodayLook.muted,
                            fontSize = 12.sp
                        )
                    } else {
                        metrics.disciplineCounts.take(3).forEach { (discipline, count) ->
                            val tint = todayDisciplineColor(discipline)
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(tint.copy(alpha = .13f))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    Modifier.size(7.dp)
                                        .background(tint, CircleShape)
                                )
                                Text(
                                    todayKindLabel(discipline),
                                    color = TodayLook.text, fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$count", color = TodayLook.text, fontSize = 11.sp
                                )
                            }
                        }
                        if (metrics.disciplineCounts.size > 3) {
                            Text(
                                "+${metrics.disciplineCounts.size - 3} more",
                                color = TodayLook.muted, fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun todayTimeLabel(seconds: Double): String {
    val minutes = (seconds / 60).toLong()
    return if (minutes >= 60L) (minutes / 60).toString() + "h " +
        (minutes % 60).toString() + "m"
    else minutes.toString() + "m"
}

package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal object TodayAdaptivePalette {
    val bg = Color(0xFF0B0D0F)
    val bgTop = bg
    val bgBottom = bg
    val card = Color(0xFF14181C)
    val cardElevated = Color(0xFF1A1F26)
    val border = Color(0xFF232A31)
    val divider = Color(0xFF1E242B)
    val track = Color(0xFF232A31)
    val text = Color(0xFFF5F7FA)
    val textSecondary = Color(0xFF9BA4B0)
    val textMuted = Color(0xFF8B9298)

    val runningLime = Color(0xFFD8FB70)
    val strengthPurple = Color(0xFFA855F7)
    val calisthenicsCyan = Color(0xFF2DD4BF)
    val conditioningAmber = Color(0xFFFB923C)
    val doneBadgeBg = Color(0xFF192A17)
    val doneBadgeText = Color(0xFF9AE66E)

    fun accentFor(discipline: TodayDiscipline?): Color = when (discipline) {
        TodayDiscipline.RUNNING, null -> runningLime
        TodayDiscipline.STRENGTH -> strengthPurple
        TodayDiscipline.CALISTHENICS -> calisthenicsCyan
        TodayDiscipline.CONDITIONING -> conditioningAmber
    }
}

internal sealed interface TodayDailySessionItem {
    val stableKey: String
    val discipline: TodayDiscipline?

    data class Planned(
        val planId: String,
        val session: PlannedSession,
        override val discipline: TodayDiscipline,
        val isCompleted: Boolean,
        val linkedActivity: ActivityRecord?,
        val isQualityWorkout: Boolean
    ) : TodayDailySessionItem {
        val key = planId to session.id
        override val stableKey: String get() = "planned-${planId.length}:$planId${session.id}"
    }

    data class Recorded(
        val activity: ActivityRecord,
        override val discipline: TodayDiscipline?
    ) : TodayDailySessionItem {
        override val stableKey: String get() = "recorded-${activity.id}"
    }

    data class ConfirmedLog(
        val workout: ConfirmedWorkout,
        val plannedSession: PlannedSession?,
        override val discipline: TodayDiscipline
    ) : TodayDailySessionItem {
        override val stableKey: String get() = "confirmed-${workout.planId}-${workout.sessionId}"
    }
}

internal sealed interface TodayDailyTrainingState {
    data class Sessions(
        val items: List<TodayDailySessionItem>,
        val hasActivePlan: Boolean
    ) : TodayDailyTrainingState

    data object RestDay : TodayDailyTrainingState
    data object NoPlanCompanion : TodayDailyTrainingState
}

internal data class TodayDisciplineChip(
    val discipline: TodayDiscipline,
    val count: Int
)

internal data class TodayWeeklyAdherence(
    val completedSessions: Int,
    val plannedSessions: Int,
    val completedPlannedRunKm: Double,
    val plannedRunKm: Double,
    val progress: Float
)

internal data class TodayTrainingOverview(
    val runKm: Double,
    val runSeconds: Double,
    val runCount: Int,
    val nonRunningChips: List<TodayDisciplineChip>,
    val adherence: TodayWeeklyAdherence?
)

internal data class TodayRecentRow(
    val id: String,
    val discipline: TodayDiscipline?,
    val typeLabel: String,
    val detailTitle: String,
    val subtitle: String,
    val activityId: String?,
    val planId: String?,
    val sessionId: String?,
    val sortInstant: Instant,
    val isRun: Boolean
)

internal data class TodayScheduledSession(val planId: String, val session: PlannedSession) {
    val key = planId to session.id
    val discipline = TodayDiscipline.fromLog(session.discipline)
}

internal class TodaySchedule(val plans: List<PlanSnapshot>, history: List<ActivityRecord>) {
    val hasApprovedPlan = plans.any { it.status == "approved" }
    val sessions = plans.filter { it.status == "approved" }.flatMap { plan ->
        plan.sessions.filter { it.status != "unassigned" }.map { TodayScheduledSession(plan.id, it) }
    }.distinctBy { it.key }
    private val canonicalById = ActivityDisplayConsolidation.groups(history).flatMap { group ->
        (listOf(group.primary) + group.additionalSources).map { it.id to group.primary }
    }.toMap()
    private val claims = sessions.mapNotNull { row -> recordingId(row)?.let { it to row.key } }
        .groupBy({ it.first }, { it.second })
    private val linkedKeys = plans.flatMap { plan ->
        plan.sessions.filter { it.linkedActivityId != null }.map { plan.id to it.id }
    }.toSet()

    private fun recordingId(row: TodayScheduledSession): String? =
        row.session.linkedActivityId?.let { canonicalById[it]?.id ?: it }

    fun isCompleted(row: TodayScheduledSession): Boolean {
        val id = recordingId(row) ?: return row.session.status == "completed"
        return claims[id]?.singleOrNull() == row.key
    }

    fun linkedActivity(row: TodayScheduledSession): ActivityRecord? =
        row.session.linkedActivityId?.takeIf { isCompleted(row) }?.let(canonicalById::get)

    fun recordings(visible: List<ActivityRecord>): List<ActivityRecord> =
        visible.map { canonicalById[it.id] ?: it }.distinctBy { it.id }

    fun isNativeLog(log: ConfirmedWorkout): Boolean = (log.planId to log.sessionId) !in linkedKeys

    fun confirmed(history: List<ActivityRecord>, now: Instant): List<ConfirmedWorkout> =
        DashboardSummary.confirmedWorkouts(plans, history, now).filter(::isNativeLog)
}

internal object TodayOverviewCalculator {
    private val nonRunningOrder = listOf(
        TodayDiscipline.STRENGTH,
        TodayDiscipline.CALISTHENICS,
        TodayDiscipline.CONDITIONING
    )

    fun formatRunHoursMinutes(seconds: Double): String {
        if (!seconds.isFinite() || seconds <= 0.0) return "0:00"
        val totalMinutes = (seconds / 60.0).roundToInt().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return "$hours:${mins.toString().padStart(2, '0')}"
    }

    fun computeWeeklyOverview(
        weekStart: LocalDate,
        visibleActivities: List<ActivityRecord>,
        rawHistory: List<ActivityRecord>,
        plans: List<PlanSnapshot>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        discipline: TodayDiscipline? = null,
        schedule: TodaySchedule = TodaySchedule(plans, rawHistory + visibleActivities)
    ): TodayTrainingOverview {
        val start = TodayDisciplineAnalytics.monday(weekStart)
        val end = start.plusDays(6)
        val weekActivities = schedule.recordings(visibleActivities).filter {
            !it.startedAt.isAfter(now) && it.startedAt.atZone(zone).toLocalDate() in start..end &&
                (discipline == null || TodayDiscipline.fromActivity(it.type) == discipline)
        }
        val confirmed = schedule.confirmed(rawHistory, now)
            .filter { it.date in start..end && (discipline == null || TodayDiscipline.fromLog(it.discipline) == discipline) }

        val runActs = weekActivities.filter(DashboardSummary::isRun)
        val runLogs = confirmed.filter {
            TodayDiscipline.fromLog(it.discipline) == TodayDiscipline.RUNNING
        }
        val runKm = (
            runActs.mapNotNull { it.distanceM?.takeIf { d -> d.isFinite() && d >= 0.0 } }.sum() +
                runLogs.mapNotNull { it.distanceM?.takeIf { d -> d.isFinite() && d >= 0.0 } }.sum()
            ) / 1000.0
        val runSeconds =
            runActs.mapNotNull { (it.movingTimeS ?: it.durationS)?.takeIf { s -> s.isFinite() && s >= 0.0 } }.sum() +
                runLogs.mapNotNull { it.durationSeconds?.takeIf { s -> s.isFinite() && s >= 0.0 } }.sum()
        val runCount = runActs.size + runLogs.size

        val nonRunningChips = nonRunningOrder.mapNotNull { discipline ->
            val count = weekActivities.count { TodayDiscipline.fromActivity(it.type) == discipline } +
                confirmed.count { TodayDiscipline.fromLog(it.discipline) == discipline }
            if (count > 0) TodayDisciplineChip(discipline, count) else null
        }

        val plannedInWeek = schedule.sessions.filter {
            it.session.date in start..end && (discipline == null || it.discipline == discipline)
        }

        val adherence = if (plannedInWeek.isNotEmpty()) {
            val completedCount = plannedInWeek.count(schedule::isCompleted)
            val plannedRuns = plannedInWeek.filter { it.discipline == TodayDiscipline.RUNNING }
            val plannedRunKm = plannedRuns
                .mapNotNull { it.session.distanceKm?.takeIf { d -> d.isFinite() && d > 0.0 } }
                .sum()
            val completedPlannedRunKm = plannedRuns
                .filter(schedule::isCompleted)
                .sumOf { row ->
                    val session = row.session
                    val linked = schedule.linkedActivity(row)
                    when {
                        linked?.distanceM != null && linked.distanceM.isFinite() && linked.distanceM > 0.0 ->
                            linked.distanceM / 1000.0
                        session.log?.distanceKm != null && session.log.distanceKm.isFinite() && session.log.distanceKm > 0.0 ->
                            session.log.distanceKm
                        session.distanceKm != null && session.distanceKm.isFinite() && session.distanceKm > 0.0 ->
                            session.distanceKm
                        else -> 0.0
                    }
                }
            TodayWeeklyAdherence(
                completedSessions = completedCount,
                plannedSessions = plannedInWeek.size,
                completedPlannedRunKm = completedPlannedRunKm,
                plannedRunKm = plannedRunKm,
                progress = (completedCount.toFloat() / plannedInWeek.size.coerceAtLeast(1).toFloat())
                    .coerceIn(0f, 1f)
            )
        } else {
            null
        }

        return TodayTrainingOverview(
            runKm = runKm,
            runSeconds = runSeconds,
            runCount = runCount,
            nonRunningChips = nonRunningChips,
            adherence = adherence
        )
    }

    fun buildDailyTrainingState(
        date: LocalDate,
        recordedOnDate: List<ActivityRecord>,
        confirmedOnDate: List<ConfirmedWorkout>,
        rawHistory: List<ActivityRecord>,
        plans: List<PlanSnapshot>,
        disciplineFilter: TodayDiscipline? = null,
        schedule: TodaySchedule = TodaySchedule(plans, rawHistory + recordedOnDate)
    ): TodayDailyTrainingState {
        val sessionsByKey = plans.flatMap { p -> p.sessions.map { (p.id to it.id) to it } }.toMap()
        val scheduled = schedule.sessions.filter {
            it.session.date == date && (disciplineFilter == null || it.discipline == disciplineFilter)
        }

        val items = mutableListOf<TodayDailySessionItem>()
        val linkedActivityIdsOnDate = mutableSetOf<String>()
        val representedSessions = mutableSetOf<Pair<String, String>>()

        scheduled.forEach { row ->
            val session = row.session
            val linked = schedule.linkedActivity(row)
            linked?.let { linkedActivityIdsOnDate += it.id }
            representedSessions += row.key
            val discipline = TodayDiscipline.fromLog(session.discipline) ?: TodayDiscipline.RUNNING
            val isQuality = session.quality ||
                (session.runningPrescription?.role == "QUALITY") ||
                (session.runningPrescription?.segments?.any { it.purpose == "WORK" } == true) ||
                (session.runningPrescription?.mixedSegments?.any { it.purpose == "WORK" || it.purpose == "REPEAT" } == true)
            items += TodayDailySessionItem.Planned(
                planId = row.planId,
                session = session,
                discipline = discipline,
                isCompleted = schedule.isCompleted(row),
                linkedActivity = linked,
                isQualityWorkout = isQuality
            )
        }

        schedule.recordings(recordedOnDate).forEach { act ->
            if (act.id !in linkedActivityIdsOnDate &&
                (disciplineFilter == null || TodayDiscipline.fromActivity(act.type) == disciplineFilter)) {
                items += TodayDailySessionItem.Recorded(
                    activity = act,
                    discipline = TodayDiscipline.fromActivity(act.type)
                )
            }
        }

        confirmedOnDate.forEach { conf ->
            if ((conf.planId to conf.sessionId) !in representedSessions && schedule.isNativeLog(conf) &&
                (disciplineFilter == null || TodayDiscipline.fromLog(conf.discipline) == disciplineFilter)) {
                val discipline = TodayDiscipline.fromLog(conf.discipline) ?: TodayDiscipline.RUNNING
                items += TodayDailySessionItem.ConfirmedLog(
                    workout = conf,
                    plannedSession = sessionsByKey[conf.planId to conf.sessionId],
                    discipline = discipline
                )
            }
        }

        return when {
            items.isNotEmpty() -> TodayDailyTrainingState.Sessions(
                items = items.sortedWith(compareBy<TodayDailySessionItem> {
                    it !is TodayDailySessionItem.Planned || it.isCompleted
                }.thenBy { it.discipline != TodayDiscipline.RUNNING }.thenBy { it.stableKey }),
                hasActivePlan = schedule.hasApprovedPlan
            )
            schedule.hasApprovedPlan -> TodayDailyTrainingState.RestDay
            else -> TodayDailyTrainingState.NoPlanCompanion
        }
    }

    fun buildRecentActivities(
        visibleActivities: List<ActivityRecord>,
        rawHistory: List<ActivityRecord>,
        plans: List<PlanSnapshot>,
        limit: Int = 5,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TodayRecentRow> {
        val evidence = TodaySchedule(plans, rawHistory + visibleActivities)
        val sessionsByKey = plans.flatMap { p -> p.sessions.map { (p.id to it.id) to it } }.toMap()
        val rows = mutableListOf<TodayRecentRow>()

        evidence.recordings(visibleActivities).asSequence()
            .filter { !it.startedAt.isAfter(now) }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenBy { it.id })
            .take(limit)
            .forEach { act ->
                val disc = TodayDiscipline.fromActivity(act.type)
                val isRun = DashboardSummary.isRun(act)
                val typeLabel = when {
                    isRun -> "Running"
                    disc != null -> disc.title
                    else -> act.type.trim().replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
                    }.ifBlank { "Activity" }
                }
                val distKm = act.distanceM?.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0)
                val detailTitle = when {
                    isRun && distKm != null -> String.format(Locale.US, "%.2f km", distKm)
                    act.title.isNotBlank() && !act.title.equals(typeLabel, ignoreCase = true) -> act.title.trim()
                    else -> act.source.trim().replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
                    }.ifBlank { "Completed" }
                }
                val dur = (act.movingTimeS ?: act.durationS)?.takeIf { it.isFinite() && it > 0.0 }
                val durStr = if (isRun) dur?.let { ActivityRecord.duration(it) } else dur?.let { formatMinutesLabel(it) }
                val paceStr = if (isRun) act.detailPaceLabel.takeIf { it.isNotBlank() && it != "Not recorded" } else null
                val hrStr = act.averageHr?.takeIf { it.isFinite() && it > 0.0 }?.let { "${it.roundToInt()} bpm" }
                val subtitle = listOfNotNull(durStr, paceStr, hrStr).joinToString(" • ")

                rows += TodayRecentRow(
                    id = "act:${act.id}",
                    discipline = disc,
                    typeLabel = typeLabel,
                    detailTitle = detailTitle,
                    subtitle = subtitle,
                    activityId = act.id,
                    planId = null,
                    sessionId = null,
                    sortInstant = act.startedAt,
                    isRun = isRun
                )
            }

        evidence.confirmed(rawHistory, now).forEach { conf ->
            val disc = TodayDiscipline.fromLog(conf.discipline) ?: TodayDiscipline.RUNNING
            val session = sessionsByKey[conf.planId to conf.sessionId]
            val isRun = disc == TodayDiscipline.RUNNING
            val distKm = conf.distanceM?.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0)
            val detailTitle = when {
                isRun && distKm != null -> String.format(Locale.US, "%.2f km", distKm)
                session != null && session.effectiveTitle.isNotBlank() -> session.effectiveTitle
                else -> "Completed"
            }
            val durStr = conf.durationSeconds?.takeIf { it.isFinite() && it > 0.0 }?.let {
                if (isRun) ActivityRecord.duration(it) else formatMinutesLabel(it)
            }
            val focusNote = if (!isRun) {
                session?.steps?.firstOrNull()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val subtitle = listOfNotNull(durStr, focusNote).joinToString(" • ")

            rows += TodayRecentRow(
                id = "log:${conf.planId}:${conf.sessionId}",
                discipline = disc,
                typeLabel = disc.title,
                detailTitle = detailTitle,
                subtitle = subtitle,
                activityId = null,
                planId = conf.planId,
                sessionId = conf.sessionId,
                sortInstant = conf.date.atStartOfDay(zone).toInstant(),
                isRun = isRun
            )
        }

        return rows
            .distinctBy { it.id }
            .sortedWith(compareByDescending<TodayRecentRow> { it.sortInstant }.thenBy { it.id })
            .take(limit)
    }
}

private fun formatMinutesLabel(seconds: Double): String {
    val mins = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return "$mins min"
}

private fun formatOverviewRunKm(km: Double): String {
    if (!km.isFinite() || km <= 0.0) return "0.0"
    return String.format(Locale.US, "%.1f", km)
}

private fun formatCompactKm(km: Double): String {
    val rounded = (km * 10.0).roundToInt() / 10.0
    return if (abs(rounded - rounded.roundToInt()) < 0.05) {
        "${rounded.roundToInt()}"
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

private fun sessionZoneLabel(session: PlannedSession): String? {
    val family = session.runningPrescription?.family?.lowercase(Locale.ROOT).orEmpty()
    val kind = session.kind.lowercase(Locale.ROOT)
    val title = session.effectiveTitle.lowercase(Locale.ROOT)
    return when {
        "vo2" in family || "interval" in kind || "vo2" in title -> "Zone 4–5"
        "threshold" in family || "tempo" in kind || "threshold" in title -> "Zone 3–4"
        "marathon" in family || "steady" in title -> "Zone 2–3"
        "recovery" in kind || "recovery" in title -> "Zone 1–2"
        else -> "Zone 2"
    }
}

private val DATE_CONTEXT_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)

@Composable
internal fun TodayDateContextHeader(
    selectedDate: LocalDate,
    todayDate: LocalDate,
    onReturnToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = selectedDate.format(DATE_CONTEXT_FORMATTER),
            color = TodayAdaptivePalette.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selectedDate != todayDate) {
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onReturnToToday)
                    .testTag("today-return-to-today"),
                shape = RoundedCornerShape(999.dp),
                color = TodayAdaptivePalette.card,
                border = BorderStroke(1.dp, TodayAdaptivePalette.border)
            ) {
                Text(
                    text = "Return to Today",
                    color = TodayAdaptivePalette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun TodayDailySessionItem.recordedActivity(): ActivityRecord? = when (this) {
    is TodayDailySessionItem.Recorded -> activity
    is TodayDailySessionItem.Planned -> linkedActivity.takeIf { isCompleted }
    is TodayDailySessionItem.ConfirmedLog -> null
}

@Composable
internal fun TodayDailyTrainingCard(
    selectedDate: LocalDate,
    todayDate: LocalDate,
    dailyState: TodayDailyTrainingState,
    cachedDetails: Map<String, ActivityDetail>,
    hasPrimaryHeroTag: Boolean = true,
    onOpenWorkout: (String, String) -> Unit,
    onOpenActivityDetail: (String) -> Unit,
    onOpenTraining: () -> Unit
) {
    val cardTitle = if (selectedDate == todayDate) {
        "Today's Training"
    } else {
        "${selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}'s Training"
    }

    val cardModifier = if (hasPrimaryHeroTag) {
        Modifier.fillMaxWidth().testTag("today-hero")
    } else {
        Modifier.fillMaxWidth()
    }

    val onlyItem = (dailyState as? TodayDailyTrainingState.Sessions)?.items?.singleOrNull()
    val onlyActivity = onlyItem?.recordedActivity()
    if (onlyItem != null && onlyActivity != null) {
        Surface(cardModifier, shape = RoundedCornerShape(18.dp), color = TodayAdaptivePalette.card,
            border = BorderStroke(1.dp, TodayAdaptivePalette.border)) {
            RecordedDailySessionRow(
                TodayDailySessionItem.Recorded(onlyActivity, onlyItem.discipline),
                cachedDetails[onlyActivity.id], hasPrimaryHeroTag, onOpenActivityDetail,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        return
    }

    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(18.dp),
        color = TodayAdaptivePalette.card,
        border = BorderStroke(1.dp, TodayAdaptivePalette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (dailyState !is TodayDailyTrainingState.Sessions || dailyState.items.any { it.recordedActivity() == null }) Text(
                text = cardTitle,
                color = TodayAdaptivePalette.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            when (dailyState) {
                is TodayDailyTrainingState.Sessions -> {
                    val primaryCtaKey = dailyState.items
                        .firstOrNull { it is TodayDailySessionItem.Planned }
                        ?.stableKey
                        ?: dailyState.items.firstOrNull()?.stableKey

                    dailyState.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = TodayAdaptivePalette.divider,
                                thickness = 1.dp
                            )
                        }
                        val useHeroActionTag = hasPrimaryHeroTag && item.stableKey == primaryCtaKey
                        val recorded = item.recordedActivity()
                        if (recorded != null) {
                            RecordedDailySessionRow(
                                TodayDailySessionItem.Recorded(recorded, item.discipline),
                                cachedDetails[recorded.id], useHeroActionTag, onOpenActivityDetail
                            )
                        } else when (item) {
                            is TodayDailySessionItem.Planned -> {
                                PlannedDailySessionRow(item, useHeroActionTag, onOpenWorkout)
                            }
                            is TodayDailySessionItem.Recorded -> Unit // Rendered above.
                            is TodayDailySessionItem.ConfirmedLog -> {
                                ConfirmedLogDailySessionRow(
                                    item = item,
                                    useHeroActionTag = useHeroActionTag,
                                    onOpenWorkout = onOpenWorkout
                                )
                            }
                        }
                    }

                    if (!dailyState.hasActivePlan && selectedDate == todayDate) {
                        HorizontalDivider(color = TodayAdaptivePalette.divider, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenTraining)
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ready for structured running targets?",
                                color = TodayAdaptivePalette.textMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Build a Running Plan",
                                color = TodayAdaptivePalette.runningLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                TodayDailyTrainingState.RestDay -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("today-rest-day"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Rest & recovery day",
                            color = TodayAdaptivePalette.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No sessions scheduled for this date. Prioritize recovery and mobility.",
                            color = TodayAdaptivePalette.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                TodayDailyTrainingState.NoPlanCompanion -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Companion training mode",
                                color = TodayAdaptivePalette.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Track multisport sessions freely or generate a weekly training plan tailored to your goals.",
                                color = TodayAdaptivePalette.textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "Build a Training Plan",
                                color = TodayAdaptivePalette.runningLime,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .then(
                                        if (hasPrimaryHeroTag) Modifier.testTag("today-workout-action")
                                        else Modifier
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenTraining)
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedDailySessionRow(
    item: TodayDailySessionItem.Planned,
    useHeroActionTag: Boolean,
    onOpenWorkout: (String, String) -> Unit
) {
    val session = item.session
    val accent = TodayAdaptivePalette.accentFor(item.discipline)
    val metadata = if (item.discipline == TodayDiscipline.RUNNING) {
        val dist = session.distanceKm?.takeIf { it.isFinite() && it > 0.0 }?.let { "${formatCompactKm(it)} km" }
        val zone = sessionZoneLabel(session)
        val dur = session.effectiveMinutes.takeIf { it > 0 }?.let { "$it min" }
        listOfNotNull(dist, zone, dur).joinToString(" • ")
    } else {
        val dur = session.effectiveMinutes.takeIf { it > 0 }?.let { "$it min" }
        val disc = item.discipline.title
        listOfNotNull(disc, dur).joinToString(" • ")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.isCompleted) TodayAdaptivePalette.doneBadgeBg else accent
                ) {
                    Text(
                        text = when {
                            item.isCompleted -> "DONE"
                            session.linkedActivityId != null -> "LINK CONFLICT"
                            else -> "PLAN"
                        },
                        color = if (item.isCompleted) TodayAdaptivePalette.doneBadgeText else TodayAdaptivePalette.bg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = session.effectiveTitle,
                    color = TodayAdaptivePalette.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        color = TodayAdaptivePalette.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!item.isCompleted && item.discipline == TodayDiscipline.RUNNING) {
                TodaySessionIntensityBars(
                    session = session,
                    isQuality = item.isQualityWorkout,
                    modifier = Modifier
                        .width(106.dp)
                        .height(34.dp)
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "View Workout",
                color = TodayAdaptivePalette.runningLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .then(if (useHeroActionTag) Modifier.testTag("today-workout-action") else Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenWorkout(item.planId, session.id) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RecordedDailySessionRow(
    item: TodayDailySessionItem.Recorded,
    cachedDetail: ActivityDetail?,
    useHeroActionTag: Boolean,
    onOpenActivityDetail: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(vertical = 4.dp)
) {
    val act = item.activity
    val isRun = DashboardSummary.isRun(act)
    val accent = TodayAdaptivePalette.accentFor(item.discipline)
    val badgeLabel = item.discipline?.title?.uppercase(Locale.ENGLISH)
        ?: act.type.uppercase(Locale.ENGLISH).ifBlank { "ACTIVITY" }

    val distKm = act.distanceM?.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0)
    val headline = when {
        isRun && distKm != null -> "Running • ${String.format(Locale.US, "%.2f km", distKm)}"
        act.title.isNotBlank() -> act.title.trim()
        else -> item.discipline?.title ?: act.type.ifBlank { "Recorded Session" }
    }
    val dur = (act.movingTimeS ?: act.durationS)?.takeIf { it.isFinite() && it > 0.0 }?.let {
        if (isRun) ActivityRecord.duration(it) else formatMinutesLabel(it)
    }
    val pace = if (isRun) act.detailPaceLabel.takeIf { it.isNotBlank() && it != "Not recorded" } else null
    val hr = act.averageHr?.takeIf { it.isFinite() && it > 0.0 }?.let { "${it.roundToInt()} bpm" }
    val subtitle = listOfNotNull(dur, pace, hr).joinToString(" • ")

    val route = if (isRun) rememberActivityThumbnail(act.id, cachedDetail) else null

    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (useHeroActionTag) Modifier.testTag("today-workout-action") else Modifier)
            .clickable { onOpenActivityDetail(act.id) }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accent.copy(alpha = 0.16f)
            ) {
                Text(
                    text = badgeLabel,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Text(
                text = headline,
                color = TodayAdaptivePalette.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    color = TodayAdaptivePalette.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
        if (route != null) {
            TodayCompactGpsThumbnail(
                route = route,
                modifier = Modifier.size(width = 84.dp, height = 64.dp)
            )
        }
    }
}

@Composable
private fun ConfirmedLogDailySessionRow(
    item: TodayDailySessionItem.ConfirmedLog,
    useHeroActionTag: Boolean,
    onOpenWorkout: (String, String) -> Unit
) {
    val accent = TodayAdaptivePalette.accentFor(item.discipline)
    val workout = item.workout
    val title = item.plannedSession?.effectiveTitle ?: item.discipline.title
    val dist = workout.distanceM?.takeIf { it.isFinite() && it > 0.0 }?.let {
        String.format(Locale.US, "%.2f km", it / 1000.0)
    }
    val dur = workout.durationSeconds?.takeIf { it.isFinite() && it > 0.0 }?.let {
        if (item.discipline == TodayDiscipline.RUNNING) ActivityRecord.duration(it) else formatMinutesLabel(it)
    }
    val subtitle = listOfNotNull(dist, dur, item.discipline.title).joinToString(" • ")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = accent.copy(alpha = 0.16f)
        ) {
            Text(
                text = "DONE",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            text = title,
            color = TodayAdaptivePalette.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = TodayAdaptivePalette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "View Workout",
                color = TodayAdaptivePalette.runningLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .then(if (useHeroActionTag) Modifier.testTag("today-workout-action") else Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenWorkout(workout.planId, workout.sessionId) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TodaySessionIntensityBars(
    session: PlannedSession,
    isQuality: Boolean,
    modifier: Modifier = Modifier
) {
    val profileBars = remember(session, isQuality) {
        if (!isQuality) {
            listOf(
                WorkoutProfileBar(0, "Warm-up", 1.1f, 0.34f, TodayAdaptivePalette.runningLime),
                WorkoutProfileBar(1, "Steady", 2.8f, 0.68f, TodayAdaptivePalette.runningLime),
                WorkoutProfileBar(2, "Cool-down", 1.0f, 0.30f, TodayAdaptivePalette.runningLime)
            )
        } else {
            val steps = buildWorkoutStepItems(
                session = session,
                segments = session.runningPrescription?.segments,
                mixedSegments = session.runningPrescription?.mixedSegments,
                textFallback = session.steps
            )
            flattenProfileBars(steps).ifEmpty {
                listOf(
                    WorkoutProfileBar(0, "Warm-up", 1.0f, 0.38f, WorkoutDetailPalette.warmTeal),
                    WorkoutProfileBar(1, "Work", 1.6f, 0.90f, WorkoutDetailPalette.amber),
                    WorkoutProfileBar(2, "Recover", 0.7f, 0.40f, WorkoutDetailPalette.teal),
                    WorkoutProfileBar(3, "Work", 1.6f, 0.90f, WorkoutDetailPalette.amber),
                    WorkoutProfileBar(4, "Cool-down", 1.0f, 0.36f, WorkoutDetailPalette.mint)
                )
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        profileBars.take(16).forEach { bar ->
            val heightFraction = if (isQuality) {
                bar.intensityFraction.coerceIn(0.28f, 1.0f)
            } else {
                bar.intensityFraction.coerceIn(0.28f, 0.72f)
            }
            Box(
                modifier = Modifier
                    .weight(bar.durationMinutes.coerceAtLeast(0.5f))
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                    .background(bar.accentColor)
            )
        }
    }
}

@Composable
internal fun TodayTrainingOverviewCard(
    overview: TodayTrainingOverview,
    weekLabel: String = "THIS WEEK",
    modifier: Modifier = Modifier,
    discipline: TodayDiscipline? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("today-training-overview"),
        shape = RoundedCornerShape(18.dp),
        color = TodayAdaptivePalette.card,
        border = BorderStroke(1.dp, TodayAdaptivePalette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Training Overview",
                    color = TodayAdaptivePalette.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "•",
                    color = TodayAdaptivePalette.textMuted,
                    fontSize = 13.sp
                )
                Text(
                    text = weekLabel,
                    color = TodayAdaptivePalette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
            }

            if (discipline == null || discipline == TodayDiscipline.RUNNING) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverviewMetricColumn(
                    value = formatOverviewRunKm(overview.runKm),
                    label = "Run km",
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricColumn(
                    value = TodayOverviewCalculator.formatRunHoursMinutes(overview.runSeconds),
                    label = "Run hours",
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricColumn(
                    value = "${overview.runCount}",
                    label = "Runs",
                    modifier = Modifier.weight(1f)
                )
            }

            if (overview.nonRunningChips.isNotEmpty()) {
                NonRunningChipsRow(chips = overview.nonRunningChips)
            }

            overview.adherence?.let { adherence ->
                HorizontalDivider(
                    color = TodayAdaptivePalette.divider,
                    thickness = 1.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Adherence",
                            color = TodayAdaptivePalette.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val kmPart = if (adherence.plannedRunKm > 0.05) {
                            " • ${formatCompactKm(adherence.completedPlannedRunKm)} / ${formatCompactKm(adherence.plannedRunKm)} km planned"
                        } else {
                            ""
                        }
                        Text(
                            text = "${adherence.completedSessions}/${adherence.plannedSessions} Sessions$kmPart",
                            color = TodayAdaptivePalette.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(TodayAdaptivePalette.track)
                    ) {
                        if (adherence.progress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(adherence.progress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(TodayAdaptivePalette.runningLime)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = TodayAdaptivePalette.text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = TodayAdaptivePalette.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NonRunningChipsRow(chips: List<TodayDisciplineChip>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { chip ->
            val accent = TodayAdaptivePalette.accentFor(chip.discipline)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accent, CircleShape)
                    )
                    Text(
                        text = "${chip.discipline.title} • ${chip.count}",
                        color = TodayAdaptivePalette.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun TodayRecentActivitiesSection(
    rows: List<TodayRecentRow>,
    cachedDetails: Map<String, ActivityDetail>,
    onSeeAll: () -> Unit,
    onOpenActivityDetail: (String) -> Unit,
    onOpenWorkout: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("today-recent-activities"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Activities",
                color = TodayAdaptivePalette.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "See all",
                color = TodayAdaptivePalette.runningLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("today-recent-see-all")
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = TodayAdaptivePalette.card,
            border = BorderStroke(1.dp, TodayAdaptivePalette.border)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (rows.isEmpty()) {
                    Text(
                        text = "No recent activities recorded yet.",
                        color = TodayAdaptivePalette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = TodayAdaptivePalette.divider,
                                thickness = 1.dp
                            )
                        }
                        val detail = row.activityId?.let { cachedDetails[it] }
                        TodayRecentActivityListRow(
                            row = row,
                            cachedDetail = detail,
                            onClick = {
                                when {
                                    row.activityId != null -> onOpenActivityDetail(row.activityId)
                                    row.planId != null && row.sessionId != null ->
                                        onOpenWorkout(row.planId, row.sessionId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayRecentActivityListRow(
    row: TodayRecentRow,
    cachedDetail: ActivityDetail?,
    onClick: () -> Unit
) {
    val accent = TodayAdaptivePalette.accentFor(row.discipline)
    val route = if (row.isRun) rememberActivityThumbnail(row.activityId, cachedDetail) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TodayDisciplineGlyphBadge(
            discipline = row.discipline,
            accent = accent
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "${row.typeLabel} • ${row.detailTitle}",
                color = TodayAdaptivePalette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.subtitle.isNotBlank()) {
                Text(
                    text = row.subtitle,
                    color = TodayAdaptivePalette.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (route != null) {
            TodayCompactGpsThumbnail(
                route = route,
                modifier = Modifier.size(width = 48.dp, height = 32.dp)
            )
        }
    }
}

@Composable
private fun TodayDisciplineGlyphBadge(
    discipline: TodayDiscipline?,
    accent: Color
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(accent.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            when (discipline) {
                TodayDiscipline.RUNNING, null -> {
                    val shoe = Path().apply {
                        moveTo(w * 0.15f, h * 0.68f)
                        lineTo(w * 0.85f, h * 0.68f)
                        lineTo(w * 0.78f, h * 0.48f)
                        lineTo(w * 0.50f, h * 0.44f)
                        lineTo(w * 0.36f, h * 0.24f)
                        lineTo(w * 0.18f, h * 0.40f)
                        close()
                    }
                    drawPath(shoe, color = accent, style = stroke)
                }
                TodayDiscipline.STRENGTH -> {
                    drawLine(accent, Offset(w * 0.25f, h * 0.50f), Offset(w * 0.75f, h * 0.50f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawRoundRect(accent, Offset(w * 0.12f, h * 0.28f), Size(w * 0.14f, h * 0.44f), CornerRadius(2.dp.toPx()))
                    drawRoundRect(accent, Offset(w * 0.74f, h * 0.28f), Size(w * 0.14f, h * 0.44f), CornerRadius(2.dp.toPx()))
                }
                TodayDiscipline.CALISTHENICS -> {
                    drawCircle(accent, radius = w * 0.12f, center = Offset(w * 0.50f, h * 0.24f))
                    drawLine(accent, Offset(w * 0.18f, h * 0.44f), Offset(w * 0.82f, h * 0.44f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent, Offset(w * 0.50f, h * 0.40f), Offset(w * 0.50f, h * 0.72f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.32f, h * 0.90f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.68f, h * 0.90f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                TodayDiscipline.CONDITIONING -> {
                    val bolt = Path().apply {
                        moveTo(w * 0.56f, h * 0.12f)
                        lineTo(w * 0.26f, h * 0.54f)
                        lineTo(w * 0.52f, h * 0.54f)
                        lineTo(w * 0.44f, h * 0.88f)
                        lineTo(w * 0.76f, h * 0.44f)
                        lineTo(w * 0.50f, h * 0.44f)
                        close()
                    }
                    drawPath(bolt, color = accent, style = stroke)
                }
            }
        }
    }
}

private fun previewSampleRoute(): ActivityDetail {
    val samples = listOf(
        ActivitySample(0, 0.0, null, 138.0, null, 40.7640, -73.9730),
        ActivitySample(1, 60.0, null, 142.0, null, 40.7670, -73.9700),
        ActivitySample(2, 120.0, null, 145.0, null, 40.7705, -73.9685),
        ActivitySample(3, 180.0, null, 146.0, null, 40.7712, -73.9738),
        ActivitySample(4, 240.0, null, 144.0, null, 40.7662, -73.9752),
        ActivitySample(5, 300.0, null, 143.0, null, 40.7642, -73.9731)
    )
    return ActivityDetail(
        activityId = "run-1",
        samples = samples,
        laps = emptyList(),
        fetchedAt = 0L
    )
}

@Composable
private fun PreviewTodayHarness(
    selectedDate: LocalDate,
    todayDate: LocalDate,
    dailyState: TodayDailyTrainingState,
    overview: TodayTrainingOverview,
    recentRows: List<TodayRecentRow>,
    details: Map<String, ActivityDetail> = emptyMap()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TodayAdaptivePalette.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TodayDateContextHeader(selectedDate, todayDate, {})
        TodayDailyTrainingCard(
            selectedDate = selectedDate,
            todayDate = todayDate,
            dailyState = dailyState,
            cachedDetails = details,
            onOpenWorkout = { _, _ -> },
            onOpenActivityDetail = {},
            onOpenTraining = {}
        )
        TodayTrainingOverviewCard(overview = overview)
        TodayRecentActivitiesSection(
            rows = recentRows,
            cachedDetails = details,
            onSeeAll = {},
            onOpenActivityDetail = {},
            onOpenWorkout = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, widthDp = 360, backgroundColor = 0xFF0B0D0F)
@Composable
private fun TodayPlannedEasyRunPreview() {
    val date = LocalDate.of(2026, 11, 16)
    val session = PlannedSession(
        id = "s-mon", date = date, discipline = "run", title = "Easy Aerobic Run",
        minutes = 70, distanceKm = 10.0, kind = "easy", quality = false
    )
    PreviewTodayHarness(
        selectedDate = date,
        todayDate = date,
        dailyState = TodayDailyTrainingState.Sessions(
            listOf(TodayDailySessionItem.Planned("p1", session, TodayDiscipline.RUNNING, false, null, false)),
            hasActivePlan = true
        ),
        overview = TodayTrainingOverview(
            runKm = 57.6,
            runSeconds = 22200.0,
            runCount = 6,
            nonRunningChips = listOf(
                TodayDisciplineChip(TodayDiscipline.STRENGTH, 2),
                TodayDisciplineChip(TodayDiscipline.CALISTHENICS, 1),
                TodayDisciplineChip(TodayDiscipline.CONDITIONING, 1)
            ),
            adherence = TodayWeeklyAdherence(5, 6, 57.6, 70.0, 5f / 6f)
        ),
        recentRows = listOf(
            TodayRecentRow("1", TodayDiscipline.RUNNING, "Running", "16.69 km", "1:51:47 • 6:42 /km • 144 bpm", "run-1", null, null, Instant.now(), true),
            TodayRecentRow("2", TodayDiscipline.STRENGTH, "Strength", "Lower Body", "45 min • Quads & Glutes", "str-1", null, null, Instant.now().minusSeconds(3600), false),
            TodayRecentRow("3", TodayDiscipline.CALISTHENICS, "Calisthenics", "Push & Core", "30 min • 180 reps", "cal-1", null, null, Instant.now().minusSeconds(7200), false)
        ),
        details = mapOf("run-1" to previewSampleRoute())
    )
}

@Preview(showBackground = true, widthDp = 360, backgroundColor = 0xFF0B0D0F)
@Composable
private fun TodayPlannedStructuredPreview() {
    val date = LocalDate.of(2026, 11, 18)
    val session = PlannedSession(
        id = "s-wed", date = date, discipline = "run", title = "Threshold Intervals",
        minutes = 62, distanceKm = 12.0, kind = "tempo", quality = true
    )
    PreviewTodayHarness(
        selectedDate = date,
        todayDate = date,
        dailyState = TodayDailyTrainingState.Sessions(
            listOf(TodayDailySessionItem.Planned("p1", session, TodayDiscipline.RUNNING, false, null, true)),
            hasActivePlan = true
        ),
        overview = TodayTrainingOverview(
            runKm = 24.0,
            runSeconds = 7800.0,
            runCount = 2,
            nonRunningChips = emptyList(),
            adherence = TodayWeeklyAdherence(2, 5, 24.0, 62.0, 0.4f)
        ),
        recentRows = emptyList()
    )
}

@Preview(showBackground = true, widthDp = 360, backgroundColor = 0xFF0B0D0F)
@Composable
private fun TodayCompletedPreview() {
    val date = LocalDate.of(2026, 11, 16)
    val linked = ActivityRecord(
        id = "run-1", title = "Morning Aerobic", type = "running", source = "garmin",
        startedAt = Instant.now(), distanceM = 10120.0, durationS = 3300.0,
        movingTimeS = 3280.0, recordedPace = 324.0, averageHr = 142.0, elevationM = 45.0
    )
    val session = PlannedSession(
        id = "s-mon", date = date, discipline = "run", title = "Easy Aerobic Run",
        minutes = 55, distanceKm = 10.0, kind = "easy", quality = false,
        status = "completed", linkedActivityId = "run-1"
    )
    PreviewTodayHarness(
        selectedDate = date,
        todayDate = date,
        dailyState = TodayDailyTrainingState.Sessions(
            listOf(TodayDailySessionItem.Planned("p1", session, TodayDiscipline.RUNNING, true, linked, false)),
            hasActivePlan = true
        ),
        overview = TodayTrainingOverview(10.1, 3280.0, 1, emptyList(), TodayWeeklyAdherence(1, 5, 10.1, 56.0, 0.2f)),
        recentRows = listOf(
            TodayRecentRow("1", TodayDiscipline.RUNNING, "Running", "10.12 km", "54:40 • 5:24 /km • 142 bpm", "run-1", null, null, Instant.now(), true)
        ),
        details = mapOf("run-1" to previewSampleRoute())
    )
}

@Preview(showBackground = true, widthDp = 360, backgroundColor = 0xFF0B0D0F)
@Composable
private fun TodayPastDateMultisportPreview() {
    val date = LocalDate.of(2026, 11, 16)
    val today = LocalDate.of(2026, 11, 19)
    val run = PlannedSession("s1", date, "run", "Easy Run", 45, 8.0, "easy", false)
    val str = PlannedSession("s2", date, "str", "Lower Body Strength", 45, null, "strength", true, status = "completed")
    PreviewTodayHarness(
        selectedDate = date,
        todayDate = today,
        dailyState = TodayDailyTrainingState.Sessions(
            listOf(
                TodayDailySessionItem.Planned("p1", run, TodayDiscipline.RUNNING, false, null, false),
                TodayDailySessionItem.Planned("p1", str, TodayDiscipline.STRENGTH, true, null, false)
            ),
            hasActivePlan = true
        ),
        overview = TodayTrainingOverview(18.0, 6120.0, 2, listOf(TodayDisciplineChip(TodayDiscipline.STRENGTH, 1)), TodayWeeklyAdherence(3, 6, 18.0, 50.0, 0.5f)),
        recentRows = emptyList()
    )
}

@Preview(showBackground = true, widthDp = 360, backgroundColor = 0xFF0B0D0F)
@Composable
private fun TodayNoPlanCompanionPreview() {
    val date = LocalDate.of(2026, 11, 16)
    PreviewTodayHarness(
        selectedDate = date,
        todayDate = date,
        dailyState = TodayDailyTrainingState.NoPlanCompanion,
        overview = TodayTrainingOverview(
            runKm = 14.2,
            runSeconds = 4920.0,
            runCount = 2,
            nonRunningChips = listOf(TodayDisciplineChip(TodayDiscipline.STRENGTH, 2)),
            adherence = null
        ),
        recentRows = listOf(
            TodayRecentRow("str-1", TodayDiscipline.STRENGTH, "Strength", "Upper Body", "40 min", "str-1", null, null, Instant.now(), false)
        )
    )
}

@Preview(showBackground = true, widthDp = 360, backgroundColor = 0xFF0B0D0F)
@Composable
private fun TodayRunningOnlyPreview() {
    val date = LocalDate.of(2026, 11, 16)
    val session = PlannedSession("s1", date, "run", "Steady Aerobic", 60, 10.5, "easy", false)
    PreviewTodayHarness(
        selectedDate = date,
        todayDate = date,
        dailyState = TodayDailyTrainingState.Sessions(
            listOf(TodayDailySessionItem.Planned("p1", session, TodayDiscipline.RUNNING, false, null, false)),
            hasActivePlan = true
        ),
        overview = TodayTrainingOverview(
            runKm = 31.4,
            runSeconds = 11400.0,
            runCount = 3,
            nonRunningChips = emptyList(),
            adherence = TodayWeeklyAdherence(3, 4, 31.4, 42.0, 0.75f)
        ),
        recentRows = listOf(
            TodayRecentRow("1", TodayDiscipline.RUNNING, "Running", "10.50 km", "58:12 • 5:32 /km • 146 bpm", "run-1", null, null, Instant.now(), true)
        ),
        details = mapOf("run-1" to previewSampleRoute())
    )
}

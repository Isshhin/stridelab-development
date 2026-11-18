package io.stridelab.privatealpha

import java.time.Duration
import kotlin.math.abs

internal enum class ActivityEffortConfidence {
    INSUFFICIENT, USER_REPORTED, RECORDED_HR_WITH_GAPS,
    RECORDED_HR_WITHOUT_GAPS, USER_REPORTED_AND_HR
}

internal data class ActivityEffortComparison(
    val sessionCount: Int,
    val medianReportedEffort: Double
)

internal data class ActivityEffortResult(
    val reportedEffort: Int? = null,
    val comparison: ActivityEffortComparison? = null,
    val heartRateZones: HeartRateZoneDistribution? = null,
    val confidence: ActivityEffortConfidence = ActivityEffortConfidence.INSUFFICIENT
)

/** Reports user feedback and approximate HR distribution separately; neither is a recovery score. */
internal object ActivityEffortAnalysis {
    fun assess(
        activity: ActivityRecord,
        detail: ActivityDetail?,
        matchedSourceIds: Set<String>,
        plans: List<PlanSnapshot>,
        history: List<ActivityRecord>,
        allowedPlanOwners: Set<String>,
        allowedHistoryIds: Set<String>,
        heartRateSettings: AthleteHeartRateZoneSettings? = null
    ): ActivityEffortResult {
        val sessions = ActivityWorkoutIntelligence.completedRunLinks(plans, allowedPlanOwners)
        val links = sessions.filter { it.second.linkedActivityId in matchedSourceIds }
        val current = links.singleOrNull()?.second
        val effort = current?.coachingLog()?.effort?.takeIf { it in 1..10 }
        val comparison = if (current != null && effort != null)
            compare(activity, current, sessions, history, allowedHistoryIds) else null
        val zones = if (detail?.activityId == activity.id && activity.id in allowedHistoryIds)
            heartRateSettings?.let { HeartRateZoneAnalysis.distribute(detail.samples, it) }
        else null
        val confidence = when {
            effort != null && zones != null -> ActivityEffortConfidence.USER_REPORTED_AND_HR
            effort != null -> ActivityEffortConfidence.USER_REPORTED
            zones == null -> ActivityEffortConfidence.INSUFFICIENT
            zones.excludedIntervals > 0 -> ActivityEffortConfidence.RECORDED_HR_WITH_GAPS
            else -> ActivityEffortConfidence.RECORDED_HR_WITHOUT_GAPS
        }
        return ActivityEffortResult(effort, comparison, zones, confidence)
    }

    private fun compare(
        currentActivity: ActivityRecord,
        currentSession: PlannedSession,
        sessions: List<Pair<PlanSnapshot, PlannedSession>>,
        history: List<ActivityRecord>,
        allowedIds: Set<String>
    ): ActivityEffortComparison? {
        val groups = ActivityDisplayConsolidation.groups(history + currentActivity)
            .filter { group -> group.primary.id in allowedIds }
        val byId = groups.flatMap { group ->
            (listOf(group.primary) + group.additionalSources).map { it.id to group.primary }
        }.toMap()
        val currentCanonical = byId[currentActivity.id] ?: return null
        val currentDistance = currentCanonical.distanceM?.takeIf { it.isFinite() && it > 0 } ?: return null
        val linked = sessions.mapNotNull { (plan, session) ->
            val id = session.linkedActivityId ?: return@mapNotNull null
            val record = byId[id] ?: return@mapNotNull null
            val rpe = session.coachingLog()?.effort?.takeIf { it in 1..10 } ?: return@mapNotNull null
            Triple(record, session, rpe)
        }
        val canonicalCounts = linked.groupingBy { it.first.id }.eachCount()
        val currentMatches = linked.filter { it.first.id == currentCanonical.id }
        if (currentMatches.size != 1) return null
        val currentType = comparableWorkoutKey(currentMatches.single().second)
        val prior = linked.filter { (record, session, _) ->
            record.id != currentCanonical.id && canonicalCounts[record.id] == 1 &&
                record.source == currentCanonical.source && DashboardSummary.isRun(record) &&
                record.startedAt.isBefore(currentCanonical.startedAt) &&
                Duration.between(record.startedAt, currentCanonical.startedAt).toDays() in 1..90 &&
                comparableWorkoutKey(session) == currentType &&
                record.distanceM?.let { it.isFinite() && abs(it - currentDistance) <= currentDistance * .10 } == true
        }.sortedByDescending { it.first.startedAt }.take(12)
        if (prior.size < 3) return null
        val values = prior.map { it.third }.sorted()
        val median = if (values.size % 2 == 1) values[values.size / 2].toDouble()
            else (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
        return ActivityEffortComparison(values.size, median)
    }

    private fun comparableWorkoutKey(session: PlannedSession): String =
        session.runningPrescription?.let { "${it.role}:${it.family.orEmpty()}" }
            ?: session.kind.lowercase()
}

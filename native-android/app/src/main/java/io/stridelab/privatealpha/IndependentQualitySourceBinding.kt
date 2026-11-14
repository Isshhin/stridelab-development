package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * M3 Batch 4: read-only reconciliation between an asserted quality segment,
 * its saved plan link, and actual cached source laps.
 *
 * ActivityRecord/ActivityDetail do not contain an authenticated owner field
 * after parsing; the caller MUST supply records already scoped to the correct
 * source namespace. A manually linked session does not prove interval execution.
 * Recorded FIT/Garmin laps have durations but no reliable threshold/tempo
 * classification. Even a successful reconciliation MUST NOT authorize quality.
 */
internal data class IndependentQualityLapClaim(
    val evidence: IndependentQualitySegmentEvidence,
    val recordedWorkLapIndices: List<Int>
) {
    init {
        require(recordedWorkLapIndices.size in 1..100)
        require(recordedWorkLapIndices.all { it >= 0 })
        require(recordedWorkLapIndices.distinct().size == recordedWorkLapIndices.size)
        require(recordedWorkLapIndices.zipWithNext().all { (a, b) -> a < b })
    }
}

internal sealed interface IndependentQualitySourceBindingDecision {
    data class Deferred(
        val ruleId: String,
        val explanation: String
    ) : IndependentQualitySourceBindingDecision

    data class LapDurationsReconciled(
        val activityId: String,
        val planId: String,
        val sessionId: String,
        val family: IndependentQualityFamily,
        val workLapIndices: List<Int>,
        val recordedWorkSeconds: Double,
        val ruleIds: List<String> = listOf(
            "PLAN_ACTIVITY_LINK_MATCH",
            "SOURCE_LAP_DURATIONS_MATCH",
            "SOURCE_SCOPE_CALLER_ASSERTED",
            "SEGMENT_INTENSITY_UNVERIFIED"
        )
    ) : IndependentQualitySourceBindingDecision {
        init {
            require(recordedWorkSeconds.isFinite() && recordedWorkSeconds > 0.0)
            require(workLapIndices.isNotEmpty())
        }

        /** Laps are source measurements; their workout-intensity labels are not. */
        val qualityClassificationVerified: Boolean get() = false
        val authorizedForTemplateSelection: Boolean get() = false
    }
}

/** Binds durations only; no FIT intensity, threshold or pace inference. */
internal object IndependentQualitySourceBinding {
    const val POLICY_ID = "stridelab.engine15.m3-source-lap-binding-v1"

    fun inspect(
        claim: IndependentQualityLapClaim,
        plans: List<PlanSnapshot>,
        sourceActivities: List<ActivityRecord>,
        detail: ActivityDetail?,
        asOf: LocalDate,
        zone: ZoneId
    ): IndependentQualitySourceBindingDecision {
        fun deferred(rule: String, explanation: String) =
            IndependentQualitySourceBindingDecision.Deferred(rule, explanation)

        val evidence = claim.evidence
        if (plans.any { it.ownerIdMismatch(evidence.ownerId) })
            return deferred("PLAN_OWNER_SCOPE_CONFLICT", "Plans from another owner cannot be mixed.")
        if (evidence.origin != IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED ||
            !evidence.sourceSessionMatchAsserted)
            return deferred("UNSUPPORTED_SEGMENT_ORIGIN", "Only asserted device-segment claims can be reconciled to source laps.")

        if (ChronoUnit.DAYS.between(evidence.performedOn, asOf) !in 0L..27L)
            return deferred("OUTSIDE_RECENT_WINDOW", "Future or stale sessions cannot establish recent exposure.")

        val actual = evidence.performedWorkSegmentsSeconds
            ?: return deferred("WORK_SEGMENTS_UNKNOWN", "Recorded work-segment durations were not supplied.")
        if (actual.size != claim.recordedWorkLapIndices.size)
            return deferred("WORK_LAP_COUNT_MISMATCH", "Each asserted segment must reference one source lap.")
        if (actual.isEmpty())
            return deferred("NO_WORK_SEGMENTS", "No positive work segments were recorded.")

        val linked = plans.flatMap { plan ->
            plan.sessions.filter { it.linkedActivityId == evidence.sourceRecordId }
                .map { plan to it }
        }
        if (linked.size != 1)
            return deferred("SOURCE_LINK_NOT_UNIQUE", "An activity must link to exactly one saved workout.")
        val (plan, session) = linked.single()
        if (session.id != evidence.sessionId || plan.owner != evidence.ownerId ||
            session.discipline != "run" || !session.quality || session.status != "completed")
            return deferred("PLANNED_SESSION_MISMATCH", "Linked saved session does not match the claimed completed running quality workout.")
        // A manual plan link marks a session 'completed' in the current app.
        // This checks consistency, NOT independent proof of completion.

        val source = sourceActivities.filter { it.id == evidence.sourceRecordId }
        if (source.size != 1)
            return deferred("SOURCE_RECORD_NOT_UNIQUE", "Exactly one source activity summary must match.")
        val activity = source.single()
        if (activity.source !in setOf("garmin", "fit") || !DashboardSummary.isRun(activity) ||
            !ActiveActivitySources.accepts(activity.source))
            return deferred("UNSUPPORTED_SOURCE", "A valid running FIT or Garmin source record is required.")
        if (activity.startedAt.atZone(zone).toLocalDate() != evidence.performedOn)
            return deferred("PERFORMED_DATE_MISMATCH", "Source recording date differs from claimed local session date.")

        if (detail == null || detail.activityId != activity.id)
            return deferred("SOURCE_DETAIL_UNAVAILABLE", "Matching recorded source laps are unavailable.")
        val selected = claim.recordedWorkLapIndices.map { index ->
            detail.laps.singleOrNull { it.index == index }
                ?: return deferred("SOURCE_LAP_NOT_FOUND", "Claim references a missing or duplicate source lap.")
        }
        val windows = selected.map { lap ->
            val start = lap.startSeconds
            val duration = lap.seconds
            if (start == null || duration == null || !start.isFinite() ||
                !duration.isFinite() || start < 0.0 || duration <= 0.0)
                return deferred("SOURCE_LAP_TIMING_UNKNOWN", "Elapsed lap start and duration must be recorded.")
            val end = start + duration
            if (!end.isFinite())
                return deferred("SOURCE_LAP_TIMING_INVALID", "Invalid recorded lap end.")
            start to end
        }
        if (windows.zipWithNext().any { (a, b) -> a.second > b.first })
            return deferred("SOURCE_WORK_LAPS_OVERLAP", "Selected source laps overlap or are out of time order.")
        val activityElapsed = activity.durationS
        if (activityElapsed != null && windows.any { (_, end) -> end > activityElapsed + 1.0 })
            return deferred("SOURCE_LAP_OUTSIDE_ACTIVITY", "Selected source lap ends beyond recorded elapsed duration.")
        if (windows.any { (start, end) ->
            detail.pauseIntervals.any { it.startSeconds < end && it.endSeconds > start }
        })
            return deferred("SOURCE_WORK_OVERLAPS_PAUSE", "A selected work lap intersects an explicitly recorded timer pause.")

        if (selected.zip(actual).any { (lap, asserted) ->
            abs(requireNotNull(lap.seconds) - asserted.toDouble()) > 1.0
        })
            return deferred("RECORDED_WORK_DURATION_MISMATCH", "Asserted work duration does not match the source lap duration.")

        val total = selected.sumOf { requireNotNull(it.seconds) }
        if (!total.isFinite() || total <= 0.0)
            return deferred("RECORDED_WORK_INVALID", "Invalid combined work-lap duration.")

        return IndependentQualitySourceBindingDecision.LapDurationsReconciled(
            activityId = activity.id,
            planId = plan.id,
            sessionId = session.id,
            family = evidence.family,
            workLapIndices = claim.recordedWorkLapIndices,
            recordedWorkSeconds = total
        )
    }

    private fun PlanSnapshot.ownerIdMismatch(expected: String) = owner != expected
}

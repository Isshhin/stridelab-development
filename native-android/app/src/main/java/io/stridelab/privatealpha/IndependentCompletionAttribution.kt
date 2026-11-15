package io.stridelab.privatealpha

import java.time.Duration
import java.time.ZoneId
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * M7a is a read-only review of existing *manual* plan-to-record links.
 *
 * ActivityRecord has no owner field after it has been parsed. The account
 * scope supplied here MUST come from the caller's authenticated repository
 * scope, but this component has no access to that authentication. A matching
 * link cannot independently prove exercise completion, athlete tolerance,
 * source-file integrity or workout intensity.
 */
internal data class IndependentScopedRunningRecord(
    val assertedOwnerId: String,
    val activity: ActivityRecord,
    val assertedIdentity: ImportedSourceIdentity? = null
) {
    init { require(assertedOwnerId.isNotBlank() && assertedOwnerId.length <= 256) }
}

internal sealed interface IndependentCompletionAttributionDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentCompletionAttributionDecision

    data class LinkedObservation(
        val planId: String,
        val sessionId: String,
        val activityId: String,
        val plannedOn: LocalDate,
        val observedOn: LocalDate,
        val sourceLabel: String,
        val recordedDistanceKm: Double?,
        val recordedElapsedSeconds: Double?,
        val linkedStatusReportsCompleted: Boolean,
        val hasManualWorkoutLog: Boolean,
        val assertedSourceIdentityAvailable: Boolean,
        val ruleIds: List<String> = listOf(
            "EXACT_MANUAL_PLAN_LINK",
            "CALLER_OWNER_SCOPE_NOT_AUTHENTICATED",
            "SOURCE_IDENTITY_NOT_AUTHENTICATED",
            "SESSION_STATUS_NOT_INDEPENDENT_COMPLETION",
            "QUALITY_AND_TOLERANCE_UNKNOWN",
            "NO_ADAPTIVE_LOAD_AUTHORIZATION"
        ),
        val sourceEvidenceVerified: Boolean = false,
        val workoutCompletionVerified: Boolean = false,
        val authorizedForAdaptiveLoad: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentCompletionAttributionDecision {
        init {
            require(planId.isNotBlank() && sessionId.isNotBlank() && activityId.isNotBlank())
            require(recordedDistanceKm == null ||
                recordedDistanceKm.isFinite() && recordedDistanceKm >= 0.0)
            require(recordedElapsedSeconds == null ||
                recordedElapsedSeconds.isFinite() && recordedElapsedSeconds >= 0.0)
            require(ruleIds.distinct() == ruleIds)
            require(!sourceEvidenceVerified && !workoutCompletionVerified &&
                !authorizedForAdaptiveLoad && !authorizedForProduction)
        }
    }
}

/**
 * Fail closed on conflicting IDs, cross-owner records, repeated source
 * originals, or near-duplicate events from different provider IDs.
 *
 * Do NOT automatically deduplicate distinct identities or treat a manually
 * linked status of "completed" as proof of a completed prescribed workout.
 */
internal object IndependentCompletionAttribution {
    const val POLICY_ID = "stridelab.engine15.m7a-attribution-audit-v1"

    private fun defer(rule: String, description: String) =
        IndependentCompletionAttributionDecision.Deferred(rule, description)

    fun inspect(
        ownerId: String,
        planId: String,
        sessionId: String,
        plans: List<PlanSnapshot>,
        activities: List<IndependentScopedRunningRecord>,
        zone: ZoneId
    ): IndependentCompletionAttributionDecision {
        require(ownerId.isNotBlank() && ownerId.length <= 256)
        if (plans.any { it.owner != ownerId })
            return defer("PLAN_OWNER_SCOPE_CONFLICT",
                "Plans for different accounts cannot be combined during completion attribution.")
        if (activities.any {
                it.assertedOwnerId != ownerId ||
                    it.assertedIdentity?.accountId?.let { account -> account != ownerId } == true
            })
            return defer("ACTIVITY_OWNER_SCOPE_CONFLICT",
                "The caller did not supply one consistent activity and source identity account scope.")
        val matchingPlans = plans.filter { it.id == planId }
        if (matchingPlans.size != 1)
            return defer("PLAN_NOT_UNIQUE", "Exactly one plan with this ID must be present.")
        val plan = matchingPlans.single()
        if (plan.status !in setOf("approved", "paused"))
            return defer("PLAN_NOT_APPROVED",
                "Draft or archived plans are not eligible for recorded-completion attribution.")

        val matchingSessions = plan.sessions.filter { it.id == sessionId }
        if (matchingSessions.size != 1)
            return defer("SESSION_NOT_UNIQUE", "Exactly one planned session must match.")
        val session = matchingSessions.single()
        if (session.discipline != "run")
            return defer("SESSION_NOT_RUNNING", "Only running links are supported in this research audit.")
        if (session.status != "completed")
            return defer("SESSION_NOT_REPORTED_COMPLETED",
                "A linked run's saved status must report completed before source reconciliation.")
        val linkedId = session.linkedActivityId
            ?: return defer("NO_EXPLICIT_SOURCE_LINK",
                "A date or close distance cannot substitute for an explicitly saved activity link.")
        if (plans.flatMap { it.sessions }.count { it.linkedActivityId == linkedId } != 1)
            return defer("SOURCE_LINK_NOT_UNIQUE",
                "A recorded source cannot be assigned to multiple planned sessions.")

        // Identical repeated records may occur in read-only cache snapshots.
        // Different content/identities under the same source ID is ambiguous.
        val matches = activities.filter { it.activity.id == linkedId }.distinct()
        if (matches.size != 1)
            return defer("LINKED_SOURCE_NOT_UNIQUE",
                "The linked activity summary is missing or its source identity/content conflicts.")
        val linked = matches.single()
        val record = linked.activity
        if (!ActiveActivitySources.accepts(record.source) || !DashboardSummary.isRun(record))
            return defer("LINKED_SOURCE_NOT_ACTIVE_RUN",
                "The linked source is retired or is not a recorded running activity.")
        if (record.distanceM?.let { !it.isFinite() || it < 0.0 } == true ||
            record.durationS?.let { !it.isFinite() || it < 0.0 } == true)
            return defer("INVALID_SOURCE_METRICS",
                "Invalid distance or elapsed duration cannot support completion evidence.")

        val otherActive = activities.distinct().filter {
            it.activity.id != linkedId &&
                ActiveActivitySources.accepts(it.activity.source) &&
                DashboardSummary.isRun(it.activity)
        }
        if (otherActive.any { other ->
                linked.assertedIdentity != null && other.assertedIdentity != null &&
                    linked.assertedIdentity.sameOriginal(other.assertedIdentity)
            })
            return defer("REPEATED_ORIGINAL_SOURCE_IDENTITY",
                "Different local IDs claim the same original provider event and require reconciliation.")
        if (otherActive.any { other -> possibleSameEvent(record, other.activity) })
            return defer("POSSIBLE_DUPLICATE_EVENT_REVIEW",
                "Two distinct record IDs could describe one physical run; do not count either twice.")

        val observedOn = record.startedAt.atZone(zone).toLocalDate()
        if (session.date != observedOn)
            return defer("LINKED_DATE_REVIEW_REQUIRED",
                "The source date and saved session date differ; the manual link needs review.")

        return IndependentCompletionAttributionDecision.LinkedObservation(
            planId = plan.id,
            sessionId = session.id,
            activityId = record.id,
            plannedOn = session.date,
            observedOn = observedOn,
            sourceLabel = record.source,
            recordedDistanceKm = record.distanceM?.div(1000.0),
            recordedElapsedSeconds = record.durationS,
            linkedStatusReportsCompleted = true,
            hasManualWorkoutLog = session.log != null && session.logRevision > 0,
            assertedSourceIdentityAvailable = linked.assertedIdentity != null
        )
    }

    private fun possibleSameEvent(a: ActivityRecord, b: ActivityRecord): Boolean {
        if (abs(Duration.between(a.startedAt, b.startedAt).seconds) > 5L * 60L)
            return false
        val left = a.durationS?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        val right = b.durationS?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        if (abs(left - right) > max(60.0, 0.10 * max(left, right))) return false
        val aMeters = a.distanceM?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        val bMeters = b.distanceM?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        return abs(aMeters - bMeters) <= max(200.0, 0.10 * max(aMeters, bMeters))
    }
}

package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * A review ledger is *not* complete-week activity evidence. It only accounts
 * for RUN sessions inside one saved plan, using the M7a linked-source audit.
 * Unlinked activities, missing provider days, independently unverified source
 * identities, and unverifiable training intent remain outside its authority.
 */
internal data class IndependentLedgerSession(
    val sessionId: String,
    val scheduledOn: LocalDate,
    val savedStatus: String,
    val attribution: IndependentCompletionAttributionDecision
)

internal data class IndependentWeekEvidenceLedger(
    val monday: LocalDate,
    val sessions: List<IndependentLedgerSession>,
    /** Partial sum of UNIQUE unambiguous linked source distances only. */
    val observedLinkedKmPartial: Double,
    val linkedWithKnownDistanceCount: Int,
    val attributedButMissingDistanceCount: Int,
    val deferredOrUnlinkedSessionCount: Int,
    val savedMissedOrSkippedCount: Int,
    val weekCoverageAuthenticated: Boolean = false,
    val workloadResponseVerified: Boolean = false
) {
    init {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        require(sessions.isNotEmpty())
        require(sessions.map { it.sessionId }.distinct().size == sessions.size)
        require(sessions.all { it.scheduledOn >= monday &&
            it.scheduledOn < monday.plusWeeks(1) })
        require(observedLinkedKmPartial.isFinite() && observedLinkedKmPartial >= 0.0)
        require(linkedWithKnownDistanceCount >= 0 &&
            attributedButMissingDistanceCount >= 0 &&
            deferredOrUnlinkedSessionCount >= 0 &&
            savedMissedOrSkippedCount >= 0)
        require(linkedWithKnownDistanceCount + attributedButMissingDistanceCount +
            deferredOrUnlinkedSessionCount == sessions.size)
        require(!weekCoverageAuthenticated && !workloadResponseVerified)
    }

    /** Never promote a partial plan-linked sum to full completed-week mileage. */
    val verifiedCompleteWeekKm: Double? get() = null
    val sourceCompleteForAdaptation: Boolean get() = false
}

internal sealed interface IndependentWeeklyEvidenceLedgerDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentWeeklyEvidenceLedgerDecision

    data class Candidate(
        val planId: String,
        val weeks: List<IndependentWeekEvidenceLedger>,
        val ruleIds: List<String>,
        val ownerScopeAuthenticated: Boolean = false,
        val completeWeeksVerified: Boolean = false,
        val authorizedForAdaptiveLoad: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentWeeklyEvidenceLedgerDecision {
        init {
            require(planId.isNotBlank() && weeks.isNotEmpty())
            require(weeks.map { it.monday }.distinct().size == weeks.size)
            require(ruleIds.distinct() == ruleIds)
            require(!ownerScopeAuthenticated && !completeWeeksVerified &&
                !authorizedForAdaptiveLoad && !authorizedForProduction)
        }
    }
}

/**
 * M7b provides a read-only, explainable "what can we actually account for"
 * view for each week. It never marks an unlinked/partial week as completed or
 * treats the absence of linked runs as zero observed athlete training.
 */
internal object IndependentWeeklyEvidenceLedger {
    const val POLICY_ID = "stridelab.engine15.m7b-weekly-link-ledger-v1"

    fun inspectPlan(
        ownerId: String,
        planId: String,
        plans: List<PlanSnapshot>,
        activities: List<IndependentScopedRunningRecord>,
        zone: ZoneId
    ): IndependentWeeklyEvidenceLedgerDecision {
        fun defer(rule: String, why: String) =
            IndependentWeeklyEvidenceLedgerDecision.Deferred(rule, why)
        require(ownerId.isNotBlank())
        if (plans.any { it.owner != ownerId })
            return defer("PLAN_OWNER_SCOPE_CONFLICT", "Cannot combine plans owned by different athletes.")
        if (activities.any {
                it.assertedOwnerId != ownerId ||
                    it.assertedIdentity?.accountId?.let { id -> id != ownerId } == true
            })
            return defer("ACTIVITY_OWNER_SCOPE_CONFLICT",
                "Caller-supplied activity identity and owner scope are inconsistent.")
        val matching = plans.filter { it.id == planId }
        if (matching.size != 1)
            return defer("PLAN_NOT_UNIQUE", "A unique saved plan is required.")
        val plan = matching.single()
        if (plan.status !in setOf("approved", "paused"))
            return defer("PLAN_NOT_APPROVED", "Only approved or paused saved plans may be audited.")
        val scheduled = plan.sessions.filter { it.discipline == "run" }
            .sortedWith(compareBy({ it.date }, { it.id }))
        if (scheduled.isEmpty())
            return defer("NO_RUNNING_SESSIONS", "This plan has no running sessions to audit.")
        if (scheduled.map { it.id }.distinct().size != scheduled.size)
            return defer("SESSION_NOT_UNIQUE", "Running session IDs must be unique.")

        val audits = scheduled.map { session ->
            IndependentLedgerSession(
                sessionId = session.id,
                scheduledOn = session.date,
                savedStatus = session.status,
                attribution = IndependentCompletionAttribution.inspect(
                    ownerId, plan.id, session.id, plans, activities, zone
                )
            )
        }
        val weeks = audits.groupBy {
            it.scheduledOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.toSortedMap().map { (monday, rows) ->
            val observations = rows.mapNotNull {
                it.attribution as? IndependentCompletionAttributionDecision.LinkedObservation
            }
            val recorded = observations.mapNotNull { it.recordedDistanceKm }
            val partial = recorded.sum()
            if (!partial.isFinite())
                return defer("LINKED_DISTANCE_OVERFLOW",
                    "Linked source distance totals cannot be represented.")
            IndependentWeekEvidenceLedger(
                monday = monday,
                sessions = rows,
                observedLinkedKmPartial = partial,
                linkedWithKnownDistanceCount = recorded.size,
                attributedButMissingDistanceCount = observations.count {
                    it.recordedDistanceKm == null
                },
                deferredOrUnlinkedSessionCount = rows.size - observations.size,
                savedMissedOrSkippedCount = rows.count {
                    it.savedStatus in setOf("missed", "skipped")
                }
            )
        }
        return IndependentWeeklyEvidenceLedgerDecision.Candidate(
            planId = plan.id,
            weeks = weeks,
            ruleIds = listOf(
                "PLAN_LINKS_ONLY_NOT_ALL_ATHLETE_ACTIVITY",
                "MISSING_AND_AMBIGUOUS_LINKS_EXPLICIT",
                "COMPLETE_WEEK_COVERAGE_NOT_ATTESTED",
                "ACTIVITY_OWNER_SCOPE_CALLER_ASSERTED",
                "NO_INFERRED_TRAINING_RESPONSE",
                "NO_ADAPTIVE_OR_PRODUCTION_AUTHORIZATION"
            )
        )
    }
}

package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Read-only comparison model. Does not approve or save a plan. */
internal data class Engine2ProposalPreview(
    val proposalId: String,
    val sessionId: String,
    val scheduledDate: LocalDate,
    val originalMinutes: Int,
    val proposedMinutes: Int,
    val originalWeekMinutes: Long,
    val proposedWeekMinutes: Long,
    val reason: String,
    val evidenceRecordIds: Set<String>,
    val requiresExplicitApproval: Boolean = true,
    val eligibleForApproval: Boolean
)

internal object Engine2ReviewPreview {
    fun build(plan: Engine2PlanView, proposal: Engine2DurationProposal): Engine2ProposalPreview {
        require(plan.ownerId == proposal.ownerId && plan.planId == proposal.planId)
        val current = plan.sessions.singleOrNull { it.id == proposal.sessionId }
            ?: throw IllegalArgumentException("The referenced session does not exist")
        val eligible = plan.status == Engine2PlanStatus.APPROVED &&
            plan.revision == proposal.expectedRevision &&
            proposal.status == Engine2ProposalStatus.PENDING &&
            current.status == Engine2SessionStatus.PLANNED &&
            current.date == proposal.originalDate &&
            current.minutes == proposal.originalMinutes &&
            proposal.id !in plan.appliedProposalIds
        val weekStart = proposal.originalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(7)
        val total = plan.sessions.filter {
            !it.date.isBefore(weekStart) && it.date.isBefore(weekEnd) &&
                it.status !in setOf(Engine2SessionStatus.SKIPPED, Engine2SessionStatus.MISSED)
        }.sumOf { it.minutes.toLong() }
        return Engine2ProposalPreview(
            proposal.id, proposal.sessionId, proposal.originalDate,
            proposal.originalMinutes, proposal.suggestedMinutes,
            total, if (eligible) total - proposal.originalMinutes + proposal.suggestedMinutes else total,
            proposal.reason, proposal.evidenceRecordIds,
            eligibleForApproval = eligible
        )
    }
}

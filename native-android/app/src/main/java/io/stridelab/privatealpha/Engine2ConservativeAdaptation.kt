package io.stridelab.privatealpha

import java.time.Instant

/** Explicit self-report, not a diagnosis or a numeric HRV/RHR readiness threshold. */
internal data class Engine2AthleteRequest(
    val ownerId: String,
    val lighterSessionId: String,
    val reason: String,
    val requestedAt: Instant
) {
    init {
        require(ownerId.isNotBlank() && lighterSessionId.isNotBlank())
        require(reason.trim().length in 8..500)
    }
}

/**
 * Rule P5.1: only an owner-requested optional reduction for an upcoming unchanged
 * session, supported by prior confirmed source-backed workout evidence.
 * No automatic changes to approved plans, no generated medical recommendation.
 */
internal object Engine2ConservativeAdaptation {
    fun proposeLighter(
        plan: Engine2PlanView,
        context: Engine2AthleteContext,
        request: Engine2AthleteRequest,
        proposalId: String,
        reducedMinutes: Int? = null
    ): Engine2DurationProposal {
        require(request.ownerId == plan.ownerId && context.ownerId == plan.ownerId)
        require(!request.requestedAt.isAfter(context.asOf))
        val session = plan.sessions.singleOrNull { it.id == request.lighterSessionId }
            ?: throw IllegalArgumentException("Session not found")
        require(session.status == Engine2SessionStatus.PLANNED)
        val confirmed = context.workouts.filter {
            it.status == Engine2RecordStatus.CONFIRMED &&
                !it.occurredAt.isAfter(request.requestedAt) &&
                it.measurements.isNotEmpty() &&
                it.measurements.all { m ->
                    !m.source.observedAt.isAfter(request.requestedAt) &&
                        m.source.receivedAt?.isAfter(request.requestedAt) != true
                }
        }.sortedWith(compareByDescending<Engine2WorkoutEvidence> { it.occurredAt }.thenBy { it.id })
        val latest = confirmed.firstOrNull() ?: throw IllegalArgumentException(
            "No prior source-backed confirmed workout is available. Use manual plan review instead."
        )
        // No automatic mileage growth, intensity estimates or measured-volume inference.
        val suggestion = reducedMinutes ?: maxOf(10, session.minutes * 3 / 4)
        return Engine2ProposalWorkflow.prepare(
            plan, context, proposalId, session.id, suggestion,
            "Athlete explicitly requested a lighter session: " + request.reason.trim() +
                ". This is a reviewable duration-only option, not an automatic recovery verdict.",
            setOf(latest.id), request.requestedAt
        )
    }
}

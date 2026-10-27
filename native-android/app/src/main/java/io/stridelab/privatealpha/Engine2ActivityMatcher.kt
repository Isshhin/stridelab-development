package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Produces manual review candidates only. UTC date is deliberately labeled,
 * because provider/device local timezone must be resolved before auto-linking.
 */
internal data class Engine2LinkCandidate(
    val sessionId: String,
    val workoutId: String,
    val providerKeys: Set<Engine2ProviderKey>,
    val recordedUtcDay: LocalDate,
    val reviewReason: String
)

internal data class Engine2LinkReview(
    val sessionId: String,
    val candidates: List<Engine2LinkCandidate>,
    val automaticLinkAllowed: Boolean = false,
    val warnings: List<String> = emptyList()
)

internal object Engine2ActivityMatcher {
    fun findCandidates(plan: Engine2PlanView, context: Engine2AthleteContext,
                       sessionId: String): Engine2LinkReview {
        require(plan.ownerId == context.ownerId)
        val session = plan.sessions.singleOrNull { it.id == sessionId }
            ?: throw IllegalArgumentException("Missing planned session")
        require(session.discipline == Engine2Discipline.RUNNING &&
            session.status == Engine2SessionStatus.PLANNED)
        val collisions = Engine2ProvenanceAudit.inspect(context).sourceCollisions
        val claimedKeys = collisions.map { it.key }.toSet()
        val candidates = context.workouts.filter { it.discipline == Engine2Discipline.RUNNING &&
            it.status == Engine2RecordStatus.CONFIRMED &&
            LocalDate.ofInstant(it.occurredAt, ZoneOffset.UTC) == session.date &&
            it.measurements.isNotEmpty()
        }.mapNotNull { workout ->
            val keys = workout.measurements.map {
                Engine2ProviderKey(it.source.accountId, it.source.provider, it.source.recordId)
            }.toSet()
            if (keys.isEmpty() || keys.any { it in claimedKeys }) null
            else Engine2LinkCandidate(
                sessionId, workout.id, keys,
                LocalDate.ofInstant(workout.occurredAt, ZoneOffset.UTC),
                "Date and running discipline match in UTC. Verify device timezone, event identity and actual workout before linking."
            )
        }.sortedBy { it.workoutId }
        val warnings = buildList {
            add("No automatic linking: UTC date matching alone cannot confirm an actual planned run.")
            if (collisions.isNotEmpty()) add("Duplicate provider source IDs were detected; review recorded data.")
            if (candidates.isEmpty()) add("No confirmed source-backed running candidate matched this UTC date.")
            if (candidates.size > 1) add("Multiple candidate runs require user selection.")
        }
        return Engine2LinkReview(sessionId, candidates, false, warnings)
    }
}

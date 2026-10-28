package io.stridelab.privatealpha

/** Phase 45: offline deterministic explanation; never interprets HRV as readiness. */
internal data class Engine2CoachExplanation(
    val headline: String,
    val beforeAfter: String,
    val evidence: List<String>,
    val cautions: List<String>,
    val readyForUserReview: Boolean
)

internal object Engine2CoachExplanations {
    fun forDurationProposal(
        plan:Engine2PlanView,
        context:Engine2AthleteContext,
        proposal:Engine2DurationProposal
    ):Engine2CoachExplanation {
        require(plan.ownerId==context.ownerId &&
            proposal.ownerId==plan.ownerId && proposal.planId==plan.planId)
        val preview=Engine2ReviewPreview.build(plan,proposal)
        val observedById=context.workouts.associateBy { it.id }
        val wellnessById=context.wellness.associateBy { it.id }
        val evidence=proposal.evidenceRecordIds.sorted().map { id ->
            val workout=observedById[id]
            val wellness=wellnessById[id]
            when {
                workout!=null && workout.status==Engine2RecordStatus.CONFIRMED ->
                    "Confirmed ${workout.discipline.name.lowercase()} workout ${workout.id} " +
                        "on ${workout.occurredAt}, source " +
                        "${workout.measurements.firstOrNull()?.source?.provider ?: "unavailable"}. Measurement fields: " +
                        workout.measurements.map { it.metric.name }.distinct().sorted().joinToString()
                wellness!=null -> "Observed wellness record on ${wellness.metric.source.observedAt}. " +
                    "Metric: ${wellness.metric.metric.name}."
                else -> "Evidence reference ${id} is unavailable; do not infer its values."
            }
        }
        val issues=buildList {
            add("This is a proposed training adjustment, not proof of fatigue or injury risk.")
            add("Historical actual workouts and their measurements remain unchanged.")
            if(!preview.eligibleForApproval)
                add("The plan or proposal revision is no longer eligible. Refresh before reviewing.")
            if(evidence.any { it.contains("unavailable") })
                add("One or more supporting observations are missing.")
            if(context.workouts.none { it.status==Engine2RecordStatus.CONFIRMED })
                add("No confirmed workout history is available for comparison.")
        }
        return Engine2CoachExplanation(
            headline="Review an optional shorter training session",
            beforeAfter="Planned ${preview.scheduledDate}: " +
                "${preview.originalMinutes} → ${preview.proposedMinutes} minutes. " +
                "This week: ${preview.originalWeekMinutes} → ${preview.proposedWeekMinutes} planned minutes.",
            evidence=evidence,
            cautions=issues,
            readyForUserReview=preview.eligibleForApproval &&
                evidence.none { it.contains("unavailable") }
        )
    }
}

package io.stridelab.privatealpha

/**
 * Batch E read-only display contract for the existing approved workout-detail layout.
 * A reviewed Engine 1.5 time reduction is not a new distance/pace prescription.
 * Historical original targets remain in the persisted PlanSnapshot and export.
 */
internal object Engine15SessionPresentation {
    fun currentDistance(session:PlannedSession):Double? {
        val suggestion=session.suggestion
        if(suggestion?.let { it.accepted && it.reason.startsWith(Engine15Feedback.RULE_ID) }==true) {
            if(suggestion.minutes<session.minutes) return null
            if(suggestion.minutes>session.minutes) {
                return session.distanceKm?.let { orig ->
                    if(session.minutes>0) kotlin.math.floor((orig*suggestion.minutes.toDouble()/session.minutes)*10.0+1e-8)/10.0 else orig
                }
            }
        }
        return session.distanceKm
    }

    enum class State { ORIGINAL, PENDING_REVIEW, ACCEPTED_TIME_ONLY, ACCEPTED_REST, ACCEPTED_PROGRESSION }

    data class Target(
        val state: State,
        val currentMinutes: Int,
        val currentDistanceTargetKm: Double?,
        val currentSteps: List<String>,
        val originalMinutes: Int,
        val originalDistanceKm: Double?,
        val disclosure: String?
    )

    fun forSession(plan: PlanSnapshot, sessionId: String): Target {
        val session = plan.sessions.singleOrNull { it.id == sessionId }
            ?: throw PlanConstraint("The workout is not part of this saved plan.")
        val proposed = session.suggestion
        val supported = Engine1Engines.supportsCoachingLoop(plan)
        // Other engine versions, historical snapshots and unrelated suggestions
        // keep their existing display contract without migration or reinterpretation.
        val reviewed = supported && proposed != null &&
            proposed.reason.startsWith(Engine15Feedback.RULE_ID)

        if (reviewed && proposed!!.accepted && proposed.minutes == 0 &&
            session.status == "skipped" && session.log == null
        ) {
            return Target(
                State.ACCEPTED_REST, 0, null, emptyList(),
                session.minutes, session.distanceKm,
                "You approved rest instead of this workout. Original targets remain in the saved history."
            )
        }
        if (reviewed && proposed!!.accepted && proposed.minutes in 1 until session.minutes &&
            session.status in setOf("planned","in_progress")
        ) {
            return Target(
                State.ACCEPTED_TIME_ONLY, proposed.minutes, null,
                listOf(
                    "Use the reviewed ${proposed.minutes}-minute time allowance.",
                    "Original distance, pace and structured steps are not requirements for this shorter alternative."
                ),
                session.minutes, session.distanceKm,
                "Time-only adjustment. Any original distance or structured intervals are history, not a current target."
            )
        }
        if (reviewed && proposed!!.accepted && proposed.minutes > session.minutes &&
            session.status in setOf("planned", "in_progress")
        ) {
            return Target(
                State.ACCEPTED_PROGRESSION, proposed.minutes, currentDistance(session),
                session.steps, session.minutes, session.distanceKm,
                "Approved weekly progression (+${proposed.minutes - session.minutes} min). Original prescription remains in saved history."
            )
        }
        if (reviewed && proposed!!.accepted) {
            // Invalid transitions and completed history retain the original prescription.
            return Target(
                State.ORIGINAL, session.minutes, session.distanceKm, session.steps,
                session.minutes, session.distanceKm,
                "The reviewed change cannot be shown as an active target in this workout state. Review the saved session."
            )
        }
        return Target(
            if (reviewed && proposed?.accepted == false) State.PENDING_REVIEW else State.ORIGINAL,
            session.effectiveMinutes, session.distanceKm, session.steps,
            session.minutes, session.distanceKm,
            if (reviewed && proposed?.accepted == false)
                "A proposed adjustment is awaiting your explicit decision. The original workout is still active."
            else null
        )
    }
}

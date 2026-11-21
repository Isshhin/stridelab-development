package io.stridelab.privatealpha

/** All Trackside drafts route exclusively through Engine 1.5 Independent. */
internal object TracksideCoaching {
    fun setupFor(previous: PlanSnapshot): PlanRequest =
        previous.request.copy(
            policyChoice = "engine15",
            lockedSessionDays = emptyMap(),
            allowDoubleDays = false,
            preferredQualityExplicit = false,
            qualityDistanceClaims = emptyList()
        )

    fun request(setup: PlanRequest, @Suppress("UNUSED_PARAMETER") engine15Active: Boolean = true): PlanRequest {
        val assigned = setup.weeklySlots.values.flatten()
        val needsMulti = setup.policyChoice == "multi-session" ||
            setup.allowDoubleDays ||
            assigned.distinct().size != assigned.size ||
            (setup.runDays + setup.strengthDays + setup.calDays + setup.conditioningDays) > setup.days.size
        val selected = setup.copy(
            policyChoice = "engine15",
            allowDoubleDays = needsMulti
        )
        Engine15IndependentPlanGenerator.validateRequest(selected)
        return selected
    }
}

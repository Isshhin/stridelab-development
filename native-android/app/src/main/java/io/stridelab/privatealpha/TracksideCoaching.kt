package io.stridelab.privatealpha

/** Only new Trackside drafts pass through this boundary. Restored plans retain their policy. */
internal object TracksideCoaching {
    fun setupFor(previous: PlanSnapshot): PlanRequest {
        val multi = PlanScheduleValidation.isMultiPolicy(previous.policyId)
        return previous.request.copy(
            policyChoice = if (multi) "multi-session" else "coordinated",
            lockedSessionDays = emptyMap(),
            allowDoubleDays = false
        )
    }

    fun request(setup: PlanRequest, engine15Active: Boolean = BuildConfig.ENGINE15_ACTIVE): PlanRequest {
        require(setup.policyChoice in setOf("coordinated", "multi-session"))
        val assigned = setup.weeklySlots.values.flatten()
        val needsMulti = setup.policyChoice == "multi-session" ||
            assigned.distinct().size != assigned.size ||
            (setup.runDays + setup.strengthDays + setup.calDays + setup.conditioningDays) > setup.days.size
        val selected = if (engine15Active) setup.copy(
            policyChoice = "engine15", allowDoubleDays = needsMulti
        ) else setup.copy(policyChoice = if (needsMulti) "multi-session" else setup.policyChoice)
        Engine1Engines.validate(selected)
        return selected
    }
}

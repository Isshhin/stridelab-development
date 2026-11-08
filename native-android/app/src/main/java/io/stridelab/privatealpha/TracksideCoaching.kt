package io.stridelab.privatealpha

/** Only new Trackside drafts pass through this boundary. Restored plans retain their policy. */
internal object TracksideCoaching {
    fun runningGoalNextPage(route: String) = when (route) {
        "race" -> 1
        "custom" -> 3
        else -> 5
    }

    fun trainYourWay(
        setup: PlanRequest, targetWeeklyKm: Double,
        allowedQualityTypes: Set<String>
    ) = setup.copy(
        goal = "General fitness",
        disciplineGoals = setup.disciplineGoals - "run",
        targetWeeklyKm = targetWeeklyKm,
        trainYourWay = true,
        allowedQualityTypes = allowedQualityTypes
    )

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
        if (setup.trainYourWay && !engine15Active)
            throw PlanConstraint("Train Your Way mileage targets require the active Engine 1.5 policy.")
        // Consent to double days is independent of which engine is deployed.
        if (setup.policyChoice == "coordinated") {
            Engine1.validate(setup)
            val assigned = setup.weeklySlots.values.flatten()
            if (assigned.distinct().size != assigned.size)
                throw PlanConstraint("Enable double days or move workouts to separate weekdays.")
        }
        val selected = if (engine15Active) setup.copy(
            policyChoice = "engine15", allowDoubleDays = setup.policyChoice == "multi-session"
        ) else setup
        Engine1Engines.validate(selected)
        return selected
    }
}

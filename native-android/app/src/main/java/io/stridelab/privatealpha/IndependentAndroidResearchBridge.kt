package io.stridelab.privatealpha

import java.time.DayOfWeek

/**
 * M10a: adapt *actual PlanRequest data* to isolated M6a/M6b research output.
 *
 * This intentionally does not implement PlanGenerationEngine, expose an owner,
 * create PlannedSession / PlanSnapshot, store data, or call the production
 * PlanActions.generate path. Caller-entered numbers are unverified assertions.
 */
internal data class IndependentAndroidPreviewConstraints(
    val selectedRunDays: Set<Int>,
    val explicitlyEnteredStartingWeeklyKm: Double?,
    val explicitlyEnteredWeeklyCeilingKm: Double?,
    val explicitlyEnteredMaxKmByDay: Map<Int, Double>?,
    val explicitlyEnteredPreviousLongRunKm: Double?,
    val explicitlyEnteredLongRunCeilingKm: Double?,
    val explicitlyEnteredMaxLongRunIncreaseKm: Double?
)

internal sealed interface IndependentAndroidPreviewDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentAndroidPreviewDecision

    data class ReadOnly(
        val startMonday: java.time.LocalDate,
        val mileage: IndependentConstrainedMultiweekDecision.Candidate,
        val qualitySlots: IndependentQualitySpacingDecision.Candidate,
        val ruleIds: List<String>,
        val sourceEvidenceVerified: Boolean = false,
        val executable: Boolean = false,
        val authorizedForPlanMutation: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentAndroidPreviewDecision {
        init {
            require(startMonday.dayOfWeek == DayOfWeek.MONDAY)
            require(mileage.mileage.weeks.size == qualitySlots.weeks.size)
            require(ruleIds.distinct() == ruleIds)
            require(!sourceEvidenceVerified && !executable &&
                !authorizedForPlanMutation && !authorizedForProduction)
        }
    }
}

internal object IndependentAndroidResearchBridge {
    const val POLICY_ID = "stridelab.engine15.m10a-android-shadow-preview-v1"

    private fun defer(id: String, explanation: String) =
        IndependentAndroidPreviewDecision.Deferred(id, explanation)

    fun preview(
        request: PlanRequest,
        constraints: IndependentAndroidPreviewConstraints
    ): IndependentAndroidPreviewDecision {
        val race = request.goal in setOf(
            "Improve 5K", "Improve 10K", "Half marathon", "Marathon"
        )
        if (race)
            return defer("RACE_SPECIFIC_POLICY_NOT_QUALIFIED",
                "Race plans cannot be represented by Train Your Way's generic mileage progression.")
        if (request.targetWeeklyKm == null || request.targetWeeklyKm <= 0.0)
            return defer("EXPLICIT_WEEKLY_TARGET_REQUIRED",
                "Enter a positive target weekly mileage in the real Plan Builder first.")
        if (request.runDays !in 2..6)
            return defer("RUNNING_DAY_COUNT_NOT_SUPPORTED",
                "The research planner needs two to six explicitly selected running days.")
        if (request.strengthDays > 0 || request.calDays > 0 ||
            request.conditioningDays > 0)
            return defer("MIXED_DISCIPLINE_PROGRAM_UNQUALIFIED",
                "Mixed-discipline scheduling needs a complete reviewed hard-session calendar.")
        if (request.returning || request.experience == 0)
            return defer("RETURNING_OR_BEGINNER_POLICY_UNQUALIFIED",
                "Beginner/returning-runner progression cannot reuse the established-runner mileage policy.")
        if (request.start.dayOfWeek != DayOfWeek.MONDAY)
            return defer("RESEARCH_WEEK_START_REQUIRES_MONDAY",
                "This first comparison uses Monday-start calendar weeks; do not remap dates silently.")
        val selected = constraints.selectedRunDays
        if (selected.size != request.runDays || selected.any { it !in request.days })
            return defer("EXPLICIT_RUNNING_DAYS_MISMATCH",
                "Enter exactly the Plan Builder's number of running weekdays, all within available days.")
        if (request.runDays >= 3 && request.longDay !in selected)
            return defer("LONG_DAY_NOT_SELECTED",
                "The long-run weekday must be one of the explicitly entered run days.")
        val fixed = request.weeklySlots["run"]
        if (fixed != null && (fixed.toSet() != selected || fixed.size != selected.size))
            return defer("PINNED_RUNNING_DAYS_CONFLICT",
                "Explicit running days must respect the saved pinned running weekdays.")
        val baseline = constraints.explicitlyEnteredStartingWeeklyKm
        val cap = constraints.explicitlyEnteredWeeklyCeilingKm
        val limits = constraints.explicitlyEnteredMaxKmByDay
        if (baseline == null || cap == null || limits == null)
            return defer("RESEARCH_LIMITS_INCOMPLETE",
                "Enter a separate starting-mileage assertion, weekly ceiling and day ceilings.")
        if (listOf(baseline, cap).any { !it.isFinite() || it <= 0.0 || it > 150.0 } ||
            limits.keys != selected || limits.values.any {
                !it.isFinite() || it <= 0.0 || it > 150.0
            })
            return defer("INVALID_RESEARCH_LIMITS",
                "All entered distance limits must be positive, finite and cover the exact run days.")
        if (request.runDays >= 3 && listOf(
                constraints.explicitlyEnteredPreviousLongRunKm,
                constraints.explicitlyEnteredLongRunCeilingKm,
                constraints.explicitlyEnteredMaxLongRunIncreaseKm
            ).any { it == null })
            return defer("LONG_RUN_REVIEW_LIMITS_MISSING",
                "Enter previous long-run distance, absolute ceiling and maximum increase separately.")
        val longLimits = listOfNotNull(
            constraints.explicitlyEnteredPreviousLongRunKm,
            constraints.explicitlyEnteredLongRunCeilingKm,
            constraints.explicitlyEnteredMaxLongRunIncreaseKm
        )
        if (longLimits.any { !it.isFinite() || it < 0.0 || it > 150.0 })
            return defer("INVALID_LONG_RUN_REVIEW_LIMITS",
                "Long-run inputs must be finite and nonnegative.")
        if (baseline > cap)
            return defer("BASELINE_EXCEEDS_REVIEWED_WEEKLY_CEILING",
                "The starting mileage assertion exceeds the separately entered weekly cap.")
        val target = request.targetWeeklyKm
        if (!target.isFinite() || target > 150.0)
            return defer("TARGET_OUTSIDE_RESEARCH_RANGE",
                "Target mileage must be finite and at most 150 km for this first Android adapter.")

        val plan = IndependentWeeklyMileageInput(
            approvedStartingWeeklyKm = baseline,
            approvedWeeklyCeilingKm = cap,
            requestedTargetWeeklyKm = target,
            weeks = request.weeks,
            runningDays = selected,
            preferredLongRunDay = request.longDay
        )
        val bounds = IndependentMileageFeasibilityLimits(
            approvedMaxKmByDay = limits,
            approvedPreviousLongRunKm = constraints.explicitlyEnteredPreviousLongRunKm,
            approvedLongRunCeilingKm = constraints.explicitlyEnteredLongRunCeilingKm,
            approvedMaxBuildLongRunIncreaseKm = constraints.explicitlyEnteredMaxLongRunIncreaseKm
        )
        val mileage = when (val result =
            IndependentConstrainedMultiweekPlanner.generate(plan, bounds)) {
            is IndependentConstrainedMultiweekDecision.Deferred ->
                return defer("M6A_" + result.ruleId, result.explanation)
            is IndependentConstrainedMultiweekDecision.Candidate -> result
        }
        val spacing = when (val result = IndependentQualitySpacingPlanner.propose(
            mileage,
            IndependentQualitySpacingRequest(
                proposedQualityDaysPerBuildWeek = request.qualityCount,
                preferredQualityDays = request.qualityDays
            )
        )) {
            is IndependentQualitySpacingDecision.Deferred ->
                return defer("M6B_" + result.ruleId, result.explanation)
            is IndependentQualitySpacingDecision.Candidate -> result
        }
        if (request.qualityCount > 0 && request.qualityDays.isNotEmpty() &&
            spacing.weeks.any { week ->
                !week.recoveryWeek &&
                    week.opportunities.any { it.isoDay !in request.qualityDays }
            })
            return defer("QUALITY_DAY_PIN_MISMATCH",
                "Research spacing could not honor your explicitly requested quality weekdays.")
        return IndependentAndroidPreviewDecision.ReadOnly(
            startMonday = request.start,
            mileage = mileage,
            qualitySlots = spacing,
            ruleIds = listOf(
                "REAL_ANDROID_PLAN_REQUEST_READ_ONLY",
                "INDEPENDENTLY_ENTERED_LIMITS_NOT_AUTHENTICATED",
                "M6A_MULTI_WEEK_DISTANCE_CALENDAR",
                "M6B_QUALITY_OPPORTUNITIES_ONLY",
                "M7_EVIDENCE_AND_M9_WORKOUT_ADMISSION_UNRESOLVED",
                "EXPERIMENT_NOT_EXECUTABLE_OR_SAVED",
                "LEGACY_ENGINE_UNCHANGED"
            )
        )
    }
}

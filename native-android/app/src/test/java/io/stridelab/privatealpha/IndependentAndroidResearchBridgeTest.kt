package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentAndroidResearchBridgeTest {
    private val request = PlanRequest(
        goal = "General fitness",
        experience = 2,
        start = LocalDate.of(2026, 11, 16),
        weeks = 5,
        days = setOf(1, 2, 3, 5, 6, 7),
        runDays = 6,
        strengthDays = 0, calDays = 0, conditioningDays = 0,
        longDay = 6,
        qualityCount = 1, qualityDays = setOf(2),
        weeklyKm = 60.0, targetWeeklyKm = 80.0,
        longestKm = 17.0, timeBudgetEnabled = false
    )
    private val entered = IndependentAndroidPreviewConstraints(
        selectedRunDays = request.days,
        explicitlyEnteredStartingWeeklyKm = 60.0,
        explicitlyEnteredWeeklyCeilingKm = 80.0,
        explicitlyEnteredMaxKmByDay = request.days.associateWith {
            if (it == 1) 5.0 else 25.0
        },
        explicitlyEnteredPreviousLongRunKm = 17.0,
        explicitlyEnteredLongRunCeilingKm = 25.0,
        explicitlyEnteredMaxLongRunIncreaseKm = 2.0
    )
    private fun inspect(
        r: PlanRequest = request,
        bounds: IndependentAndroidPreviewConstraints = entered
    ) = IndependentAndroidResearchBridge.preview(r, bounds)
    private fun candidate(
        r: PlanRequest = request,
        bounds: IndependentAndroidPreviewConstraints = entered
    ): IndependentAndroidPreviewDecision.ReadOnly {
        val result = inspect(r, bounds)
        assertTrue(result.toString(), result is IndependentAndroidPreviewDecision.ReadOnly)
        return result as IndependentAndroidPreviewDecision.ReadOnly
    }
    private fun reject(
        rule: String,
        r: PlanRequest = request,
        bounds: IndependentAndroidPreviewConstraints = entered
    ) {
        val result = inspect(r, bounds)
        assertTrue(result.toString(), result is IndependentAndroidPreviewDecision.Deferred)
        assertEquals(rule, (result as IndependentAndroidPreviewDecision.Deferred).ruleId)
    }

    @Test fun realPlanRequestCanShowFiveConstrainedResearchWeeksWithoutSavingAnything() {
        val result = candidate()
        assertEquals(5, result.mileage.mileage.weeks.size)
        assertEquals(60.0, result.mileage.mileage.weeks.first().weeklyKilometers, 0.00001)
        assertEquals(5.0,
            result.mileage.mileage.weeks.first().dayAllocations.first {
                it.isoDay == 1
            }.kilometers, 0.00001)
        assertEquals(listOf(2), result.qualitySlots.weeks.first()
            .opportunities.map { it.isoDay })
        assertFalse(result.authorizedForPlanMutation)
        assertFalse(result.authorizedForProduction)
        assertFalse(result.executable)
    }
    @Test fun originalPlanRequestIsNotMutatedOrConvertedIntoExperimentalPolicy() {
        val original = request.copy()
        val first = inspect()
        assertEquals(first, inspect())
        assertEquals(original, request)
        assertEquals("baseline", request.policyChoice)
        assertEquals("M6A_MULTI_WEEK_DISTANCE_CALENDAR",
            (first as IndependentAndroidPreviewDecision.ReadOnly).ruleIds[2])
    }
    @Test fun userGoalAndTargetMustBeExplicitlyMileageBased() {
        reject("EXPLICIT_WEEKLY_TARGET_REQUIRED", request.copy(targetWeeklyKm = null))
        reject("RACE_SPECIFIC_POLICY_NOT_QUALIFIED", request.copy(goal = "Improve 5K"))
        reject("RACE_SPECIFIC_POLICY_NOT_QUALIFIED", request.copy(goal = "Marathon"))
    }
    @Test fun mixedDisciplinesAndReturnToRunFailClosed() {
        reject("MIXED_DISCIPLINE_PROGRAM_UNQUALIFIED", request.copy(strengthDays = 2))
        reject("MIXED_DISCIPLINE_PROGRAM_UNQUALIFIED", request.copy(calDays = 1))
        reject("RETURNING_OR_BEGINNER_POLICY_UNQUALIFIED", request.copy(returning = true))
        reject("RETURNING_OR_BEGINNER_POLICY_UNQUALIFIED", request.copy(experience = 0))
    }
    @Test fun actualStartDateAndRunningScheduleMustBeRepresentable() {
        reject("RESEARCH_WEEK_START_REQUIRES_MONDAY",
            request.copy(start = request.start.plusDays(1)))
        reject("EXPLICIT_RUNNING_DAYS_MISMATCH",
            bounds = entered.copy(selectedRunDays = setOf(1, 2, 3)))
        reject("LONG_DAY_NOT_SELECTED",
            bounds = entered.copy(selectedRunDays = setOf(1, 2, 3, 4, 5, 7)))
    }
    @Test fun pinnedRunningDaysCannotBeIgnoredByExperimentalCalendar() {
        reject("PINNED_RUNNING_DAYS_CONFLICT",
            request.copy(weeklySlots = mapOf("run" to listOf(1, 2, 3, 5, 6))))
    }
    @Test fun missingApprovalsRemainMissingInsteadOfInferringFromRecentKm() {
        reject("RESEARCH_LIMITS_INCOMPLETE",
            bounds = entered.copy(explicitlyEnteredStartingWeeklyKm = null))
        reject("RESEARCH_LIMITS_INCOMPLETE",
            bounds = entered.copy(explicitlyEnteredWeeklyCeilingKm = null))
        reject("RESEARCH_LIMITS_INCOMPLETE",
            bounds = entered.copy(explicitlyEnteredMaxKmByDay = null))
        reject("LONG_RUN_REVIEW_LIMITS_MISSING",
            bounds = entered.copy(explicitlyEnteredPreviousLongRunKm = null))
    }
    @Test fun contradictoryReviewedBaselineAndDailyBoundsDefer() {
        reject("BASELINE_EXCEEDS_REVIEWED_WEEKLY_CEILING",
            bounds = entered.copy(explicitlyEnteredWeeklyCeilingKm = 40.0))
        reject("INVALID_RESEARCH_LIMITS",
            bounds = entered.copy(explicitlyEnteredMaxKmByDay =
                request.days.associateWith { Double.NaN }))
        reject("M6A_NO_FEASIBLE_DISTANCE_ALLOCATION",
            bounds = entered.copy(explicitlyEnteredMaxKmByDay =
                request.days.associateWith { 5.0 }))
    }
    @Test fun qualityPinnedToAnUnavailableDayIsNeverSilentlyMoved() {
        reject("QUALITY_DAY_PIN_MISMATCH",
            r = request.copy(qualityDays = setOf(5)))
    }
    @Test fun zeroQualityMileagePreviewDoesNotInventQualitySessions() {
        val r = candidate(request.copy(qualityCount = 0, qualityDays = emptySet()))
        assertTrue(r.qualitySlots.weeks.all { it.opportunities.isEmpty() })
        assertTrue(r.mileage.mileage.weeks.all {
            it.dayAllocations.sumOf { day -> day.kilometers } ==
                it.weeklyKilometers
        })
    }
    @Test fun targetAndWeekLabelsCannotExceedReviewedCeiling() {
        val result = candidate()
        assertTrue(result.mileage.mileage.weeks.all {
            it.weeklyKilometers <= 80.01
        })
        assertTrue(result.qualitySlots.weeks[3].opportunities.isEmpty())
    }
}

package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class IndependentMileageFeasibilityTest {
    private fun plan(
        baseline: Double = 60.0,
        ceiling: Double = 80.0,
        target: Double = 80.0,
        weeks: Int = 6,
        days: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        longDay: Int = 6
    ): IndependentWeeklyMileageDecision.Candidate {
        val result = IndependentWeeklyMileagePlanner.generate(
            IndependentWeeklyMileageInput(
                approvedStartingWeeklyKm = baseline,
                approvedWeeklyCeilingKm = ceiling,
                requestedTargetWeeklyKm = target,
                weeks = weeks,
                runningDays = days,
                preferredLongRunDay = longDay
            )
        )
        assertTrue(result.toString(), result is IndependentWeeklyMileageDecision.Candidate)
        return result as IndependentWeeklyMileageDecision.Candidate
    }

    private fun limits(
        dayCaps: Map<Int, Double>? = setOf(1, 2, 3, 5, 6, 7).associateWith { 25.0 },
        prior: Double? = 15.0,
        maximum: Double? = 25.0,
        increase: Double? = 2.0
    ) = IndependentMileageFeasibilityLimits(
        approvedMaxKmByDay = dayCaps,
        approvedPreviousLongRunKm = prior,
        approvedLongRunCeilingKm = maximum,
        approvedMaxBuildLongRunIncreaseKm = increase
    )

    private fun conflict(
        expectedRule: String,
        result: IndependentMileageFeasibilityResult
    ) {
        assertTrue(result.toString(), result is IndependentMileageFeasibilityResult.Conflict)
        assertEquals(expectedRule, (result as IndependentMileageFeasibilityResult.Conflict).ruleId)
    }

    private fun unchecked(
        expectedRule: String,
        result: IndependentMileageFeasibilityResult
    ) {
        assertTrue(result.toString(), result is IndependentMileageFeasibilityResult.Unchecked)
        assertEquals(expectedRule, (result as IndependentMileageFeasibilityResult.Unchecked).ruleId)
    }

    @Test fun suppliedDayAndLongRunLimitsCanBeCheckedWithoutAuthorizingProduction() {
        val result = IndependentMileageFeasibility.check(plan(), limits())
        assertTrue(result.toString(), result is IndependentMileageFeasibilityResult.Checked)
        val checked = result as IndependentMileageFeasibilityResult.Checked
        assertEquals(6, checked.checkedWeeks)
        assertFalse(checked.authorizedForProduction)
        assertFalse(checked.sourceEvidenceVerified)
        assertTrue("NO_TIME_BUDGET_INFERRED" in checked.ruleIds)
    }

    @Test fun missingOrPartialDailyCeilingsRemainUnchecked() {
        unchecked("DAY_CEILINGS_NOT_SUPPLIED",
            IndependentMileageFeasibility.check(plan(), limits(dayCaps = null)))
        unchecked("DAY_CEILINGS_INCOMPLETE",
            IndependentMileageFeasibility.check(plan(), limits(dayCaps = mapOf(6 to 25.0))))
        unchecked("DAY_CEILINGS_INCOMPLETE",
            IndependentMileageFeasibility.check(plan(),
                limits(dayCaps = (1..7).associateWith { 25.0 })))
    }

    @Test fun missingLongRunInputsDoNotReceiveAssumedLimits() {
        unchecked("PRIOR_LONG_RUN_NOT_SUPPLIED",
            IndependentMileageFeasibility.check(plan(), limits(prior = null)))
        unchecked("LONG_RUN_CEILING_NOT_SUPPLIED",
            IndependentMileageFeasibility.check(plan(), limits(maximum = null)))
        unchecked("LONG_RUN_INCREMENT_NOT_SUPPLIED",
            IndependentMileageFeasibility.check(plan(), limits(increase = null)))
    }

    @Test fun tooMuchMileageOnOneDayReturnsConflictNotAutoReallocation() {
        val cap = setOf(1, 2, 3, 5, 6, 7).associateWith { if (it == 1) 8.0 else 25.0 }
        conflict("DAY_DISTANCE_CEILING_EXCEEDED",
            IndependentMileageFeasibility.check(plan(), limits(dayCaps = cap)))
    }

    @Test fun firstWeekCannotJumpBeyondPreviousLongRunLimit() {
        conflict("LONG_RUN_BUILD_INCREMENT_EXCEEDED",
            IndependentMileageFeasibility.check(plan(), limits(prior = 12.0, increase = 1.0)))
    }

    @Test fun absoluteLongRunCeilingIsIndependentOfWeeklyShare() {
        conflict("LONG_RUN_ABSOLUTE_CEILING_EXCEEDED",
            IndependentMileageFeasibility.check(plan(), limits(maximum = 16.0)))
    }

    @Test fun recoveryWeekCannotInventLongerLongRun() {
        val base = plan()
        val changed = base.copy(weeks = base.weeks.map { week ->
            if (week.week != 4) week else {
                val old = week.dayAllocations
                val long = old.indexOfFirst { it.longRunCandidate }
                val regular = old.indexOfFirst { !it.longRunCandidate }
                val altered = old.toMutableList()
                altered[long] = old[long].copy(kilometers = old[long].kilometers + 3.0)
                altered[regular] = old[regular].copy(kilometers = old[regular].kilometers - 3.0)
                week.copy(dayAllocations = altered)
            }
        })
        // The first independent violation in a manipulated plan is caught.
        conflict("RECOVERY_LONG_RUN_INCREASE",
            IndependentMileageFeasibility.check(changed, limits()))
    }

    @Test fun twoRunningDaysNeedNoLongRunEvidence() {
        val two = plan(baseline = 20.0, ceiling = 25.0, target = 25.0,
            weeks = 3, days = setOf(3, 6), longDay = 6)
        val checks = IndependentMileageFeasibility.check(two,
            limits(dayCaps = mapOf(3 to 15.0, 6 to 15.0),
                prior = null, maximum = null, increase = null))
        assertTrue(checks.toString(), checks is IndependentMileageFeasibilityResult.Checked)
        assertTrue("TWO_DAY_NO_LONG_RUN" in
            (checks as IndependentMileageFeasibilityResult.Checked).ruleIds)
    }

    @Test fun invalidDailySumCannotBeCalledChecked() {
        val base = plan(weeks = 1)
        val first = base.weeks.single()
        val newFirst = first.copy(dayAllocations = first.dayAllocations.mapIndexed { i, day ->
            if (i == 0) day.copy(kilometers = day.kilometers + 1.0) else day
        })
        val changed = base.copy(weeks = listOf(newFirst))
        conflict("WEEKLY_TOTAL_MISMATCH",
            IndependentMileageFeasibility.check(changed, limits()))
    }

    @Test fun longRunMarkerCannotChangeWeekToWeekSilently() {
        val base = plan(weeks = 2)
        val second = base.weeks[1].copy(dayAllocations =
            base.weeks[1].dayAllocations.map {
                it.copy(longRunCandidate = it.isoDay == 5)
            })
        conflict("LONG_RUN_DAY_CHANGED",
            IndependentMileageFeasibility.check(base.copy(weeks = listOf(base.weeks[0], second)), limits()))
    }

    @Test fun candidateAllocationRemainsUnchangedAfterFailedCheck() {
        val original = plan()
        val snapshot = original.weeks.map { it.dayAllocations.toList() }
        IndependentMileageFeasibility.check(original, limits(maximum = 10.0))
        assertEquals(snapshot, original.weeks.map { it.dayAllocations })
    }

    @Test fun nonSequentialWeeksAreRejected() {
        val base = plan(weeks = 2)
        val changed = base.copy(weeks = listOf(base.weeks[0], base.weeks[1].copy(week = 3)))
        conflict("NONSEQUENTIAL_WEEKS",
            IndependentMileageFeasibility.check(changed, limits()))
    }

    @Test fun invalidSourceLimitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            limits(dayCaps = mapOf(8 to 30.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            limits(dayCaps = mapOf(6 to Double.NaN))
        }
        assertThrows(IllegalArgumentException::class.java) {
            limits(increase = -1.0)
        }
    }
}

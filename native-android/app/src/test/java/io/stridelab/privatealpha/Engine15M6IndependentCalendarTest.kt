package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** M6A dates are shadow-only; no test approves executable sessions. */
class Engine15M6IndependentCalendarTest {
    private val monday = LocalDate.of(2026, 11, 16)

    private fun source(
        recent: Double = 75.0,
        target: Double = 80.0,
        longest: Double = 20.0,
        days: Int = 6,
        weeks: Int = 8,
        returning: Boolean = false,
        recoveryHold: Boolean = false,
        basis: String = "SELF_REPORTED_AVERAGE"
    ): Engine15IndependentVolumePlanner.Result {
        val plan = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = weeks,
            days = setOf(1, 2, 3, 4, 5, 6, 7),
            runDays = days, longDay = 7, qualityCount = 0,
            weeklyKm = recent, targetWeeklyKm = target,
            longestKm = longest, returning = returning,
            timeBudgetEnabled = false, policyChoice = "engine15",
            trainYourWay = true, recentMileageBasis = basis,
            runningRecoveryHold = recoveryHold
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(plan),
            Engine15IndependentVolumePlanner.Input(
                weeks, days, target, returning, recoveryHold
            )
        )
    }

    private fun calendar(
        available: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        longDay: Int = 7,
        blocked: Set<LocalDate> = emptySet(),
        preferredEasy: Set<Int> = emptySet(),
        start: LocalDate = monday
    ) = Engine15M6CalendarInput(
        startMonday = start,
        availableIsoDays = available,
        preferredLongIsoDay = longDay,
        blockedDates = blocked,
        preferredEasyIsoDays = preferredEasy
    )

    private fun candidate(
        source: Engine15IndependentVolumePlanner.Result,
        calendar: Engine15M6CalendarInput
    ): Engine15M6CalendarDecision.Candidate {
        val decision = Engine15M6IndependentCalendar.schedule(source, calendar)
        assertTrue("Expected M6A candidate, got $decision",
            decision is Engine15M6CalendarDecision.Candidate)
        return decision as Engine15M6CalendarDecision.Candidate
    }

    private fun deferred(
        source: Engine15IndependentVolumePlanner.Result,
        calendar: Engine15M6CalendarInput,
        code: String
    ) {
        val decision = Engine15M6IndependentCalendar.schedule(source, calendar)
        assertTrue("Expected $code, got $decision",
            decision is Engine15M6CalendarDecision.Deferred)
        assertEquals(code, (decision as Engine15M6CalendarDecision.Deferred).reasonCode)
    }

    private fun checkUnmodified(
        source: Engine15IndependentVolumePlanner.Result,
        result: Engine15M6CalendarDecision.Candidate,
        input: Engine15M6CalendarInput
    ) {
        val original = Engine15M5WorkoutComposer.compose(source)
            as Engine15M5CompositionDecision.Candidate
        assertTrue(result.calendarAssigned)
        assertFalse(result.qualityScheduled)
        assertFalse(result.sourceEvidenceVerified)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        assertEquals(original.weeks.size, result.weeks.size)
        for ((i, week) in result.weeks.withIndex()) {
            val base = original.weeks[i]
            assertEquals(base.weekNumber, week.weekNumber)
            assertEquals(base.kind, week.kind)
            assertEquals(base.budgetKm, week.budgetKm, 1e-8)
            assertEquals(source.input.runningDays, week.runs.size)
            assertEquals(week.budgetKm, week.runs.sumOf { it.distanceKm }, 1e-8)
            assertEquals(base.slots.toSet(), week.runs.map { it.slot }.toSet())
            assertEquals(week.runs.size, week.runs.map { it.date }.toSet().size)
            val start = input.startMonday.plusWeeks(i.toLong())
            for (placed in week.runs) {
                assertFalse(placed.executable)
                assertFalse(placed.qualityAuthorized)
                assertEquals(placed.distanceKm * 1000.0,
                    placed.slot.workout.total(WorkoutUnit.METERS)!!, 1e-6)
                assertTrue(placed.date >= start && placed.date < start.plusWeeks(1))
                assertTrue(placed.isoDay in input.availableIsoDays)
                assertFalse(placed.date in input.blockedDates)
                assertEquals(WorkoutTarget.EASY,
                    (placed.slot.workout.elements.single() as
                        IndependentWorkoutElement.Step).value.target)
            }
            val long = week.runs.filter { it.role == Engine15M5RunRole.LONG }
            assertEquals(if (source.input.runningDays >= 3) 1 else 0, long.size)
            if (long.isNotEmpty())
                assertEquals(input.preferredLongIsoDay, long.single().isoDay)
        }
    }

    @Test fun sixDayEightWeekCalendarHonorsThursdayOffAndSundayLong() {
        val m4b = source()
        val input = calendar()
        val result = candidate(m4b, input)
        assertEquals(listOf(67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 62.9),
            result.weeks.map { it.budgetKm })
        assertEquals(listOf(1, 2, 3, 5, 6, 7),
            result.weeks.first().runs.map { it.isoDay })
        assertEquals(LocalDate.of(2026, 11, 22),
            result.weeks.first().runs.single { it.role == Engine15M5RunRole.LONG }.date)
        assertEquals(LocalDate.of(2027, 1, 10),
            result.weeks.last().runs.single { it.role == Engine15M5RunRole.LONG }.date)
        checkUnmodified(m4b, result, input)
    }

    @Test fun threeDayCalendarSpreadsSessionsAndKeepsExposureBound() {
        val m4b = source(days = 3)
        val input = calendar(available = (1..7).toSet())
        val result = candidate(m4b, input)
        assertEquals(54.6, result.weeks.first().budgetKm, 1e-8)
        assertEquals(listOf(2, 4, 7),
            result.weeks.first().runs.map { it.isoDay })
        assertEquals(listOf(16.8, 16.8, 21.0),
            result.weeks.first().runs.map { it.distanceKm })
        checkUnmodified(m4b, result, input)
    }

    @Test fun twoDayFeasibleWeekNeverInventsALongRun() {
        val m4b = source(recent = 20.0, target = 25.0, days = 2, weeks = 1)
        val input = calendar(available = setOf(1, 3, 5, 7))
        val result = candidate(m4b, input)
        assertEquals(18.0, result.weeks.first().budgetKm, 1e-8)
        assertEquals(listOf(9.0, 9.0),
            result.weeks.first().runs.map { it.distanceKm })
        assertTrue(result.weeks.first().runs.all { it.role == Engine15M5RunRole.EASY })
        checkUnmodified(m4b, result, input)
    }

    @Test fun explicitlyBlockedEasyDateIsNeverScheduledWhenAnAlternativeExists() {
        val m4b = source()
        val blocked = LocalDate.of(2026, 11, 17)
        val input = calendar(available = (1..7).toSet(), blocked = setOf(blocked))
        val result = candidate(m4b, input)
        assertTrue(result.weeks.first().runs.none { it.date == blocked })
        assertEquals(6, result.weeks.first().runs.size)
        checkUnmodified(m4b, result, input)
    }

    @Test fun blockedOrUnavailablePreferredLongRunDayDefers() {
        val m4b = source()
        deferred(m4b, calendar(
            available = (1..7).toSet(),
            blocked = setOf(LocalDate.of(2026, 11, 22))
        ), "LONG_DAY_BLOCKED")
        deferred(m4b, calendar(available = (1..6).toSet()),
            "LONG_DAY_UNAVAILABLE")
    }

    @Test fun insufficientWeekdaysAndBlockedWeekCannotSilentlyReduceRunCount() {
        val m4b = source()
        deferred(m4b, calendar(available = setOf(1, 2, 3, 5, 7)),
            "INSUFFICIENT_AVAILABLE_DAYS")
        deferred(m4b, calendar(
            blocked = setOf(LocalDate.of(2026, 11, 18))
        ), "INSUFFICIENT_WEEK_AVAILABILITY")
    }

    @Test fun wrongStartDateAndTamperedWeeklyBudgetFailClosed() {
        val m4b = source()
        deferred(m4b, calendar(start = LocalDate.of(2026, 11, 17)),
            "PLAN_START_NOT_MONDAY")
        deferred(m4b.copy(weeks = m4b.weeks.mapIndexed { i, week ->
            if (i == 0) week.copy(proposedKm = 100.0) else week
        }), calendar(), "BASE_M4B_RESULT_MISMATCH")
    }

    @Test fun missingBaselineMissingLongestRecoveryHoldAndInfeasibleTwoDayDefer() {
        deferred(source(recent = 0.0, longest = 0.0, basis = "UNKNOWN"),
            calendar(), "BASE_M4B_NON_DISTANCE_STATUS")
        deferred(source(longest = 0.0), calendar(),
            "BASE_LONGEST_RUN_EXPOSURE_UNKNOWN")
        deferred(source(recoveryHold = true), calendar(),
            "BASE_M4B_NON_DISTANCE_STATUS")
        deferred(source(days = 2), calendar(available = (1..7).toSet()),
            "BASE_TWO_DAY_EXPOSURE_CONFLICT")
    }

    @Test fun sameInputsProduceSameShadowCalendarWithoutQualityApproval() {
        val m4b = source(returning = true, recent = 40.0,
            target = 50.0, longest = 12.0, days = 4)
        val input = calendar(available = setOf(1, 2, 3, 4, 5, 6, 7),
            preferredEasy = setOf(2, 4, 6))
        val first = candidate(m4b, input)
        val second = candidate(m4b, input)
        assertEquals(first, second)
        checkUnmodified(m4b, first, input)
    }
}

package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** I9 verifies only a base-only dated preview, never a generated/saved plan. */
class Engine15I9ReadOnlyBasePlanAssemblyTest {
    private val monday = LocalDate.of(2026, 11, 16)

    private fun source(
        recent: Double = 75.0,
        target: Double = 80.0,
        longest: Double = 20.0,
        days: Int = 6,
        weeks: Int = 8
    ): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = weeks, days = (1..7).toSet(),
            runDays = days, longDay = 7, qualityCount = 0,
            weeklyKm = recent, targetWeeklyKm = target,
            longestKm = longest, returning = false,
            policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false,
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(
                weeks, days, target, false, false
            )
        )
    }

    private fun quality() = Engine15M5QualityPreviewRequest(
        weekNumber = 1,
        slotNumber = 2,
        family = IndependentQualityFamily.THRESHOLD,
        fitnessInput = IndependentQualityFitnessInput(
            confirmedRecentWorkSeconds = 1200,
            approvedWorkCeilingSeconds = 1200,
            availableSessionSeconds = 3600,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = true,
        reviewReference = "test-only-quality-claim"
    )

    private fun calendar(
        monday: LocalDate = this.monday,
        blocked: Set<LocalDate> = emptySet()
    ) = Engine15M6CalendarInput(
        startMonday = monday,
        availableIsoDays = setOf(1, 2, 3, 5, 6, 7),
        preferredLongIsoDay = 7,
        blockedDates = blocked
    )

    private fun interference(
        calendar: Engine15M6CalendarInput = calendar(),
        other: List<Engine15M6CExternalSession>? = emptyList(),
        assertedCoverage: Boolean = true,
        quality: List<Engine15M5QualityPreviewRequest> = emptyList()
    ) = Engine15M6CReviewInput(
        calendar = calendar,
        externalSessions = other,
        externalCalendarCoverageAsserted = assertedCoverage,
        qualityRequests = quality
    )

    private fun input(
        review: Engine15M6CReviewInput = interference(),
        acknowledged: Boolean = true
    ) = Engine15I9BasePlanAssemblyInput(
        interferenceReview = review,
        explicitBaseOnlyReview = acknowledged
    )

    private fun accepted(
        source: Engine15IndependentVolumePlanner.Result = source(),
        input: Engine15I9BasePlanAssemblyInput = input()
    ): Engine15I9BasePlanAssemblyDecision.ReadOnlyBasePreview {
        val result = Engine15I9ReadOnlyBasePlanAssembly.assemble(source, input)
        assertTrue("Expected read-only base preview, got $result",
            result is Engine15I9BasePlanAssemblyDecision.ReadOnlyBasePreview)
        return result as Engine15I9BasePlanAssemblyDecision.ReadOnlyBasePreview
    }

    private fun deferred(
        code: String,
        source: Engine15IndependentVolumePlanner.Result = source(),
        input: Engine15I9BasePlanAssemblyInput = input()
    ) {
        val result = Engine15I9ReadOnlyBasePlanAssembly.assemble(source, input)
        assertTrue("Expected $code, got $result",
            result is Engine15I9BasePlanAssemblyDecision.Deferred)
        assertEquals(code,
            (result as Engine15I9BasePlanAssemblyDecision.Deferred).reasonCode)
    }

    @Test fun eightWeeksBecomeFortyEightDatedBaseRunsWithoutQuality() {
        val source = source()
        val review = interference()
        val original = Engine15M6IndependentPeriodizationReview.audit(source, review)
        val result = accepted(source, input(review))
        assertEquals(original, result.originalM6D)
        assertEquals(8, result.weeks.size)
        assertEquals(48, result.weeks.sumOf { it.runs.size })
        assertEquals(
            listOf(67.5,71.2,75.0,60.0,71.2,75.0,78.7,62.9),
            result.weeks.map { it.proposedKm }
        )
        assertEquals(
            listOf(Engine15M6DPhase.RECOVERY, Engine15M6DPhase.RECOVERY),
            result.weeks.filter { it.weekNumber in listOf(4, 8) }
                .map { it.phase }
        )
        assertEquals(561.5, result.totalProposedKm, 1e-8)
        assertEquals(78.7, result.peakProposedKm, 1e-8)
        assertEquals(1.3, result.targetShortfallKm!!, 1e-8)
        assertEquals(2, result.reviewAfterCompletedWeek)
        for (week in result.weeks) {
            assertEquals(6, week.runs.size)
            assertTrue(week.runs.none { it.date.dayOfWeek.value == 4 })
            assertEquals(7, week.runs.single {
                it.role == Engine15M5RunRole.LONG
            }.date.dayOfWeek.value)
            assertEquals(week.proposedKm, week.runs.sumOf { it.distanceKm }, 1e-8)
            assertEquals(0, week.originallyAssertedExternalSessionCount)
        }
        assertTrue(result.calendarArithmeticReconciled)
        assertTrue(result.explicitBaseOnly)
        assertFalse(result.qualityIncluded)
        assertFalse(result.externalCalendarAuthenticated)
        assertFalse(result.sourceEvidenceVerified)
        assertFalse(result.coachingDoseApproved)
        assertFalse(result.executable)
        assertFalse(result.authorizedForPlanMutation)
        assertFalse(result.authorizedForProduction)
    }

    @Test fun everyDatedRunRetainsOriginalM5aTypedDistanceComposition() {
        val result = accepted()
        val original = result.originalM6D.originalConflictReview.runningWeeks
        for (week in result.weeks) {
            val datedOriginals = original[week.weekNumber-1].runs
            for (entry in week.runs) {
                val source = datedOriginals.single {
                    it.slot.slotNumber == entry.slotNumber
                }
                assertEquals(source.date, entry.date)
                assertEquals(source.slot.role, entry.role)
                assertEquals(source.slot.distanceKm, entry.distanceKm, 0.0)
                assertEquals(source.slot.workout, entry.originalDistanceWorkout)
                assertEquals(entry.distanceKm * 1000.0,
                    entry.originalDistanceWorkout.total(WorkoutUnit.METERS)!!, 1e-6)
                assertNull(entry.originalDistanceWorkout.total(WorkoutUnit.SECONDS))
                assertFalse(entry.executable)
                assertFalse(entry.qualityAuthorized)
            }
        }
    }

    @Test fun unknownBaseOnlyIntentAndAnyQualityRequestAreNotSilentlyDowngraded() {
        deferred("BASE_ONLY_SCOPE_NOT_ACKNOWLEDGED",
            input = input(acknowledged = false))
        deferred("UNQUALIFIED_QUALITY_REQUEST_NOT_ADMITTED",
            input = input(review = interference(quality = listOf(quality()))))
    }

    @Test fun missingOrUnassertedCrossDisciplineCalendarCannotBecomeFullPreview() {
        deferred("M6D_UPSTREAM_EXTERNAL_CALENDAR_NOT_ASSERTED",
            input = input(review = interference(assertedCoverage = false)))
        deferred("M6D_UPSTREAM_EXTERNAL_CALENDAR_MISSING",
            input = input(review = interference(other = null)))
    }

    @Test fun blockedLongRunOrNonMondayStartPreserveOriginalCalendarHolds() {
        deferred("M6D_UPSTREAM_CALENDAR_LONG_DAY_BLOCKED",
            input = input(review = interference(calendar = calendar(
                blocked = setOf(LocalDate.of(2026,11,22))
            ))))
        deferred("M6D_UPSTREAM_CALENDAR_PLAN_START_NOT_MONDAY",
            input = input(review = interference(calendar = calendar(
                monday = monday.plusDays(1)
            ))))
    }

    @Test fun sameDayExternalSessionIsNotSilentlyDropped() {
        val conflicting = Engine15M6CExternalSession(
            id = "tuesday-leg-day",
            date = monday.plusDays(1),
            discipline = Engine15M6CDiscipline.STRENGTH,
            load = Engine15M6CLoad.LOWER_BODY
        )
        deferred("M6D_UPSTREAM_RUN_EXTERNAL_SAME_DAY_CONFLICT",
            input = input(review = interference(other = listOf(conflicting))))
    }

    @Test fun externalUpperBodyOnFreeThursdayRemainsAuditMetadataNotNewRun() {
        val other = Engine15M6CExternalSession(
            id = "thursday-upper",
            date = monday.plusDays(3),
            discipline = Engine15M6CDiscipline.CALISTHENICS,
            load = Engine15M6CLoad.UPPER_BODY
        )
        val result = accepted(input = input(
            review = interference(other = listOf(other))
        ))
        assertEquals(1, result.weeks.first().originallyAssertedExternalSessionCount)
        assertEquals(48, result.weeks.sumOf { it.runs.size })
        assertTrue(result.weeks.flatMap { it.runs }.none {
            it.date == other.date
        })
        assertFalse(result.externalCalendarAuthenticated)
    }

    @Test fun callerTamperedM4bResultCannotBypassOriginalPlannerRecomputation() {
        val original = source()
        val replaced = original.copy(weeks = original.weeks.mapIndexed { i, w ->
            if (i == 0) w.copy(proposedKm = 79.0) else w
        })
        val decision = Engine15I9ReadOnlyBasePlanAssembly.assemble(
            replaced, input()
        )
        assertTrue("Tampered M4B must defer: $decision",
            decision is Engine15I9BasePlanAssemblyDecision.Deferred)
        assertTrue((decision as Engine15I9BasePlanAssemblyDecision.Deferred)
            .reasonCode.contains("M4B_RESULT_MISMATCH"))
    }

    @Test fun repeatedAssemblyDoesNotModifyProvisionalWeekBudgets() {
        val s = source()
        val i = input()
        val a = accepted(s,i)
        val b = accepted(s,i)
        assertEquals(a,b)
        assertEquals(s.weeks.map { it.proposedKm },
            a.weeks.map { it.proposedKm })
        assertEquals(s.reviewAfterCompletedWeek,
            a.reviewAfterCompletedWeek)
        assertTrue(a.weeks.count { it.requiresCompletedWeekReassessment } == 1)
        assertTrue(a.weeks.all { it.runs.none { run -> run.qualityAuthorized } })
        assertFalse(a.authorizedForProduction)
    }
}

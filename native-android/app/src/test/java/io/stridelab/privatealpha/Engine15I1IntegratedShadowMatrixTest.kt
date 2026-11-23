package io.stridelab.privatealpha

import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.*
import org.junit.Test

/**
 * I1 integrated SHADOW matrix. Synthetic candidate budgets and caller-asserted
 * records are NOT verified coaching outcomes or executable prescriptions.
 */
class Engine15I1IntegratedShadowMatrixTest {
    private val monday = LocalDate.of(2026, 11, 16)

    private fun source(
        typical: Double = 75.0, target: Double = 80.0,
        longest: Double = 20.0, days: Int = 6, weeks: Int = 8,
        returning: Boolean = false, hold: Boolean = false,
        basis: String = "SELF_REPORTED_AVERAGE",
        peak: Double? = null, experience: Int = 2, longDay: Int = 7
    ): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = experience,
            start = monday, weeks = weeks, days = (1..7).toSet(),
            runDays = days, longDay = longDay, qualityCount = 0,
            weeklyKm = typical, targetWeeklyKm = target, longestKm = longest,
            returning = returning, runningRecoveryHold = hold,
            policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false, recentMileageBasis = basis,
            reportedPeakWeeklyKm = peak
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(
                weeks, days, target, returning, hold
            )
        )
    }

    private fun weekdays(days: Int): Set<Int> = when (days) {
        2 -> setOf(3, 7)
        3 -> setOf(2, 4, 7)
        4 -> setOf(1, 3, 5, 7)
        5 -> setOf(1, 2, 3, 5, 7)
        6 -> setOf(1, 2, 3, 5, 6, 7)
        else -> error("I1 only covers 2..6 running days")
    }

    private fun config(
        days: Int = 6,
        quality: List<Engine15M5QualityPreviewRequest> = emptyList(),
        external: List<Engine15M6CExternalSession>? = emptyList(),
        coverageAsserted: Boolean = true,
        blocked: Set<LocalDate> = emptySet(),
        longDay: Int = 7
    ) = Engine15M6CReviewInput(
        calendar = Engine15M6CalendarInput(
            startDate = monday, availableIsoDays = weekdays(days),
            preferredLongIsoDay = longDay, blockedDates = blocked
        ),
        externalSessions = external,
        externalCalendarCoverageAsserted = coverageAsserted,
        qualityRequests = quality
    )

    private fun quality(
        week: Int, slot: Int = 2,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        reviewed: Boolean = true
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = week, slotNumber = slot, family = family,
        fitnessInput = IndependentQualityFitnessInput(
            reportedRecentWorkSeconds = 1200,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = 3600, qualityAuthorized = true
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = "synthetic-caller-review-not-source-evidence"
    )

    private fun accepted(
        s: Engine15IndependentVolumePlanner.Result,
        c: Engine15M6CReviewInput
    ): Engine15M6DReviewDecision.Candidate {
        val result = Engine15M6IndependentPeriodizationReview.audit(s, c)
        assertTrue("Expected I1 candidate; got " + result,
            result is Engine15M6DReviewDecision.Candidate)
        return (result as Engine15M6DReviewDecision.Candidate).also {
            assertInvariants(s, c, it)
        }
    }

    private fun denied(
        s: Engine15IndependentVolumePlanner.Result,
        c: Engine15M6CReviewInput,
        expectedReasonFragment: String
    ) {
        val outcome = Engine15M6IndependentPeriodizationReview.audit(s, c)
        assertTrue("Expected I1 deferral; got " + outcome,
            outcome is Engine15M6DReviewDecision.Deferred)
        val deferred = outcome as Engine15M6DReviewDecision.Deferred
        assertTrue("Unexpected I1 reason: " + deferred.reasonCode,
            deferred.reasonCode.contains(expectedReasonFragment))
        assertTrue(deferred.explanation.isNotBlank())
    }

    private fun assertInvariants(
        s: Engine15IndependentVolumePlanner.Result,
        c: Engine15M6CReviewInput,
        report: Engine15M6DReviewDecision.Candidate
    ) {
        val integrated = report.originalConflictReview
        assertEquals(Engine15IndependentVolumePlanner.Status.DISTANCE_CANDIDATE,
            s.status)
        assertEquals(s.weeks.size, report.weeks.size)
        assertEquals(s.peakProposedKm!!, report.proposedPeakKm, 1e-7)
        assertEquals(s.shortfallToRequestedTargetKm,
            report.targetBudgetShortfallKm)
        assertEquals(s.reviewAfterCompletedWeek, report.reviewAfterCompletedWeek)
        assertEquals(c.externalSessions, integrated.externalSessions)
        assertEquals(report.weeks.sumOf { it.proposedKm },
            report.proposedTotalKm, 1e-7)
        assertTrue(report.allFutureWeeksProvisional)
        assertFalse(report.verifiedAthleteResponse)
        assertFalse(report.qualityIntensityAuthenticated)
        assertFalse(report.actualDistanceVerified)
        assertFalse(report.timeBudgetValidated)
        assertFalse(report.productionReady)
        assertFalse(report.executable)
        assertFalse(report.authorizedForProduction)
        assertFalse(integrated.externalCalendarVerified)
        assertFalse(integrated.sourceEvidenceVerified)
        assertFalse(integrated.timeBudgetValidated)
        assertFalse(integrated.qualityScheduled)
        assertFalse(integrated.executable)
        assertFalse(integrated.authorizedForProduction)

        for ((index, week) in report.weeks.withIndex()) {
            val m4b = s.weeks[index]
            val dated = integrated.runningWeeks[index]
            val begin = monday.plusWeeks(index.toLong())
            assertEquals(index + 1, week.weekNumber)
            assertEquals(index + 1, dated.weekNumber)
            assertEquals(m4b.proposedKm!!, week.proposedKm, 1e-7)
            assertEquals(m4b.proposedKm!!, dated.budgetKm, 1e-7)
            assertEquals(week.proposedKm, dated.runs.sumOf { it.distanceKm }, 1e-7)
            assertEquals(s.input.runningDays, dated.runs.size)
            assertEquals(dated.runs.size, dated.runs.map { it.date }.toSet().size)
            assertEquals(kotlin.math.round(week.proposedKm * 10.0),
                week.proposedKm * 10.0, 1e-7)
            assertTrue(week.proposedKm <= m4b.existingPolicyCeilingKm!! + 1e-7)
            m4b.provisionalSessionCapacityKm?.let {
                assertTrue(week.proposedKm <= it + 1e-7)
            }
            m4b.returnFromBreakCeilingKm?.let {
                assertTrue(week.proposedKm <= it + 1e-7)
            }
            m4b.recoveryCeilingKm?.let {
                assertTrue(week.proposedKm <= it + 1e-7)
            }
            for (run in dated.runs) {
                assertTrue(run.date >= begin && run.date < begin.plusWeeks(1))
                assertTrue(run.isoDay in c.calendar.availableIsoDays)
                assertFalse(run.date in c.calendar.blockedDates)
                assertFalse(run.executable)
                assertFalse(run.qualityAuthorized)
                val step = (run.slot.workout.elements.single() as
                    IndependentWorkoutElement.Step).value
                assertEquals(WorkoutPurpose.WORK, step.purpose)
                assertEquals(WorkoutUnit.METERS, step.unit)
                assertEquals(WorkoutTarget.EASY, step.target)
                assertEquals(1, step.repetitions)
                assertEquals(run.distanceKm * 1000.0, step.amount, 1e-7)
            }
            if ((index + 1) % 4 == 0) {
                assertEquals(Engine15M6DPhase.RECOVERY, week.phase)
                assertEquals(0, week.qualityOpportunityCount)
                assertTrue(week.proposedKm <= report.weeks[index - 1].proposedKm * 0.8 + 1e-7)
            }
            val longs = dated.runs.filter { it.role == Engine15M5RunRole.LONG }
            assertEquals(if (s.input.runningDays >= 3) 1 else 0, longs.size)
            if (longs.isNotEmpty()) {
                assertEquals(c.calendar.preferredLongIsoDay, longs.single().isoDay)
                assertEquals(longs.single().distanceKm, week.longRunKm!!, 1e-7)
            } else assertNull(week.longRunKm)
        }
        val opportunities = integrated.qualityOpportunities
        assertEquals(opportunities.size,
            report.weeks.sumOf { it.qualityOpportunityCount })
        for (opportunity in opportunities) {
            assertEquals(Engine15M5RunRole.EASY, opportunity.existingRun.role)
            assertEquals(opportunity.existingRun.date, opportunity.proposedDate)
            assertEquals(opportunity.existingRun.distanceKm,
                opportunity.preview.originalEasyDistanceKm, 1e-7)
            assertFalse(opportunity.scheduledQualityWorkout)
            assertFalse(opportunity.executable)
        }
    }

    @Test fun experiencedSixDayWithQualityAndStrengthPreservesProvisionalBudgets() {
        val s = source()
        val upper = Engine15M6CExternalSession(
            "thu-upper", LocalDate.of(2026, 11, 19),
            Engine15M6CDiscipline.STRENGTH, Engine15M6CLoad.UPPER_BODY
        )
        val c = config(quality = listOf(
            quality(1), quality(2, family = IndependentQualityFamily.TEMPO)
        ), external = listOf(upper))
        val result = accepted(s, c)
        assertEquals(listOf(67.5,71.2,75.0,60.0,71.2,75.0,78.7,62.9),
            result.weeks.map { it.proposedKm })
        assertEquals(561.5, result.proposedTotalKm, 1e-7)
        assertEquals(78.7, result.proposedPeakKm, 1e-7)
        assertEquals(1.3, result.targetBudgetShortfallKm!!, 1e-7)
        assertEquals(2, result.reviewAfterCompletedWeek)
        assertEquals(listOf(LocalDate.of(2026, 11, 17),
            LocalDate.of(2026, 11, 24)),
            result.originalConflictReview.qualityOpportunities.map { it.proposedDate })
        assertEquals(result, Engine15M6IndependentPeriodizationReview.audit(s,c))
    }

    @Test fun saturdayLongSundayEasyKeepsQualityOpportunityOnCompatibleWeekday() {
        val s = source(longDay = 6)
        val requests = listOf(quality(1, slot = 1))
        val result = accepted(s, config(quality = requests, longDay = 6))
        val week = result.originalConflictReview.runningWeeks.first()
        val long = week.runs.single { it.role == Engine15M5RunRole.LONG }
        val sunday = week.runs.single { it.isoDay == 7 }
        assertEquals(6, long.isoDay)
        assertEquals(Engine15M5RunRole.EASY, sunday.role)
        assertTrue(week.runs.none { it.isoDay == 4 })
        assertEquals(listOf(1), result.originalConflictReview.qualityOpportunities
            .map { it.proposedDate.dayOfWeek.value })
        assertEquals(Engine15M5RunRole.EASY,
            result.originalConflictReview.qualityOpportunities.first().existingRun.role)
        assertTrue(result.originalConflictReview.qualityOpportunities.all {
            !it.scheduledQualityWorkout && !it.executable
        })
        assertEquals(result.weeks.sumOf { it.proposedKm }, result.proposedTotalKm, 1e-7)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
    }

    @Test fun twoSameWeekQualityRequestsRemainExplicitlyDeferred() {
        val s = source(longDay = 6)
        val requests = listOf(quality(1, slot = 1), quality(1, slot = 3,
            family = IndependentQualityFamily.TEMPO))
        denied(s, config(quality = requests, longDay = 6),
            "MULTIPLE_QUALITY_REQUESTS_IN_WEEK")
    }

    @Test fun developingLimitedDaysReturnAndBigJumpDoNotInflateVolumes() {
        val cases = listOf(
            Triple(source(typical=50.0,target=80.0,longest=16.0,
                days=6,weeks=4), 6, 30.0),
            Triple(source(typical=30.0,target=35.0,longest=10.0,
                days=4,weeks=8), 4, 3.5),
            Triple(source(days=3), 3, 25.4),
            Triple(source(typical=40.0,target=50.0,longest=12.0,
                days=4,returning=true), 4, 21.2),
            Triple(source(typical=20.0,target=25.0,longest=20.0,
                days=2,weeks=1), 2, 7.0)
        )
        for ((s, days, gap) in cases) {
            val result = accepted(s, config(days=days))
            assertEquals(gap, result.targetBudgetShortfallKm!!, 1e-7)
            assertTrue(result.proposedPeakKm < s.input.targetWeeklyKm!! + 1e-7)
            if (days == 2) assertTrue(result.weeks.all { it.longRunKm == null })
            if (s.input.returning) {
                assertEquals(2, result.reviewAfterCompletedWeek)
                assertTrue(result.weeks.all { it.qualityOpportunityCount == 0 })
            }
            if (days == 3) assertEquals(listOf(16.8,16.8,21.0),
                result.originalConflictReview.runningWeeks.first().runs
                    .map { it.distanceKm })
        }
    }

    @Test fun arithmeticDistanceSidecarIsStillNotAuthenticatedQuality() {
        val s = source()
        val requests = listOf(quality(1),quality(2,
            family=IndependentQualityFamily.TEMPO))
        val m5b = Engine15M5QualityPreviewComposer.compose(s,requests)
            as Engine15M5QualityDecision.Candidate
        val syntheticClaims = m5b.qualityPreviews.map { preview ->
            val segmentCount = preview.workout.elements.sumOf { element ->
                when (element) {
                    is IndependentWorkoutElement.Step -> element.value.repetitions
                    is IndependentWorkoutElement.Intervals ->
                        element.value.repetitions * 2 - 1
                }
            }
            // Arithmetic-only synthetic values; no pace-to-distance inference.
            val totalMeters = preview.originalEasyDistanceKm * 1000.0
            val each = totalMeters / segmentCount
            val meters = List(segmentCount - 1) { each } +
                listOf(totalMeters - each * (segmentCount - 1))
            Engine15M5SegmentDistanceClaim(preview.weekNumber,preview.slotNumber,
                meters,"synthetic-only-distance-assertion")
        }
        val arithmetic = Engine15M5TimedDistanceReconciler.reconcile(
            s,requests,syntheticClaims
        ) as Engine15M5DistanceDecision.ArithmeticOnly
        val schedule = accepted(s,config(quality=requests))
        assertEquals(arithmetic.originalQualityPreviews,
            schedule.originalConflictReview.qualityOpportunities.map { it.preview })
        assertTrue(arithmetic.matches.all {
            abs(it.sumAssertedMeters - it.originalEasySlotKm * 1000.0) <= 0.01
        })
        assertFalse(arithmetic.sourceDistancesVerified)
        assertFalse(arithmetic.qualityClassificationVerified)
        assertFalse(arithmetic.scheduled)
        assertFalse(arithmetic.executable)
        assertFalse(arithmetic.authorizedForProduction)
    }

    @Test fun contradictoryUnreviewedAndUnavailableInputsDeferWithoutFallback() {
        denied(source(days=2),config(days=2),"TWO_DAY_EXPOSURE_CONFLICT")
        denied(source(longest=0.0),config(),"LONGEST_RUN_EXPOSURE_UNKNOWN")
        denied(source(typical=0.0,basis="UNKNOWN"),config(),"M4B_NON_DISTANCE_STATUS")
        denied(source(typical=0.0,basis="PEAK_WEEK_ONLY",peak=85.0),
            config(),"M4B_NON_DISTANCE_STATUS")
        denied(source(basis="LEGACY_UNSPECIFIED"),config(),"M4B_NON_DISTANCE_STATUS")
        denied(source(typical=0.0,target=20.0,longest=0.0,
            days=3,basis="CONFIRMED_ZERO",experience=0),
            config(days=3),"M4B_NON_DISTANCE_STATUS")
        denied(source(hold=true),config(),"M4B_NON_DISTANCE_STATUS")
        denied(source(peak=60.0),config(),"M4B_NON_DISTANCE_STATUS")
        denied(source(),config(blocked=setOf(LocalDate.of(2026,11,22))),
            "LONG_DAY_BLOCKED")
        denied(source(),config(external=null),"EXTERNAL_CALENDAR_MISSING")
        denied(source(),config(coverageAsserted=false),
            "EXTERNAL_CALENDAR_NOT_ASSERTED")
        denied(source(),config(quality=listOf(quality(1,reviewed=false))),
            "QUALITY_EXTERNAL_REVIEW_MISSING")
        denied(source(),config(quality=listOf(quality(4))),
            "QUALITY_RECOVERY_WEEK_HOLD")
        val hard = Engine15M6CExternalSession(
            "lower-mon-after-long",monday.plusWeeks(1),
            Engine15M6CDiscipline.STRENGTH,Engine15M6CLoad.LOWER_BODY
        )
        denied(source(days=3),config(days=3,external=listOf(hard)),
            "EXTERNAL_HARD_TOO_CLOSE_TO_LONG")
        val original = source()
        val changed = original.copy(weeks=original.weeks.mapIndexed { i,w ->
            if (i==0) w.copy(proposedKm=100.0) else w
        })
        denied(changed,config(),"M4B_RESULT_MISMATCH")
    }

    @Test fun deterministicGridEmitsCompleteSyntheticAcceptanceLedger() {
        val lines = mutableListOf(
            "synthetic_case,decision,reason,proposed_peak_km,target_budget_gap_km,week_count"
        )
        var tested=0
        var accept=0
        var defer=0
        for (weekly in listOf(10.0,20.0,30.0,50.0,75.0,100.0))
            for (days in listOf(2,3,4,6))
                for (ratio in listOf(0.6,1.2,2.0))
                    for (returning in listOf(false,true)) {
                        val id = "i1-" + tested.toString().padStart(3,'0')
                        val longest = max(1.0,weekly / days * ratio)
                        val weeks = listOf(4,8,12)[tested % 3]
                        val target = minOf(120.0,max(weekly+5.0,80.0))
                        val s = source(typical=weekly,target=target,
                            longest=longest,days=days,weeks=weeks,
                            returning=returning)
                        val c = config(days=days)
                        when (val result =
                            Engine15M6IndependentPeriodizationReview.audit(s,c)) {
                            is Engine15M6DReviewDecision.Candidate -> {
                                accept++
                                assertInvariants(s,c,result)
                                lines += listOf(id,"ACCEPT","",s.peakProposedKm,
                                    s.shortfallToRequestedTargetKm,weeks).joinToString(",")
                            }
                            is Engine15M6DReviewDecision.Deferred -> {
                                defer++
                                assertTrue(result.reasonCode.isNotBlank())
                                assertTrue(result.explanation.isNotBlank())
                                lines += listOf(id,"DEFER",result.reasonCode,
                                    s.peakProposedKm,s.shortfallToRequestedTargetKm,
                                    weeks).joinToString(",")
                            }
                        }
                        tested++
                    }
        assertEquals(144,tested)
        assertEquals(144,accept+defer)
        assertTrue("No accepted profile",accept>0)
        assertTrue("No rejected profile",defer>0)
        assertEquals(145,lines.size)
        val output=File("build/reports/engine15-i1/integrated-synthetic-matrix.csv")
        val directory = requireNotNull(output.parentFile)
        assertTrue(directory.isDirectory || directory.mkdirs())
        output.writeText(lines.joinToString("\n")+"\n")
        assertTrue(output.isFile)
        assertEquals(145,output.readLines().size)
        assertTrue(output.readLines().all { it.split(",").size==6 })
    }
}

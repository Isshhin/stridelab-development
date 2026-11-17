package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** I5 synthetic speed envelopes never establish actual athlete pace/intensity. */
class Engine15I5TimedDistanceFeasibilityTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val asOf = LocalDate.of(2026, 11, 17)
    private val zone = ZoneId.of("Asia/Manila")
    private val owner = "synthetic-runner"
    private val recentDay = asOf.minusDays(3)
    private val olderDay = asOf.minusDays(10)

    private fun source(
        recent: Double = 30.0,
        target: Double = 35.0,
        longest: Double = 10.0,
        days: Int = 4
    ): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = 8, days = (1..7).toSet(),
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
                8, days, target, false, false
            )
        )
    }

    private fun request(
        week: Int = 1,
        slot: Int = 1,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        reviewed: Boolean = true,
        observed: Int? = 480
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = week,
        slotNumber = slot,
        family = family,
        fitnessInput = IndependentQualityFitnessInput(
            reportedRecentWorkSeconds = observed,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = 3600,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = "synthetic-caller-dose-review"
    )

    private fun plan() = PlanSnapshot(
        id = "recorded-plan", owner = owner,
        engineVersion = ENGINE15_VERSION,
        policyId = ENGINE15_POLICY,
        createdAt = Instant.EPOCH,
        request = PlanRequest(policyChoice = "engine15"),
        sessions = listOf(
            PlannedSession(
                id = "older-session", date = olderDay, discipline = "run",
                title = "Earlier synthetic recorded workout", minutes = 40,
                distanceKm = 6.0, kind = "quality", quality = true,
                status = "completed", linkedActivityId = "older-fit"
            ),
            PlannedSession(
                id = "recent-session", date = recentDay, discipline = "run",
                title = "Recent synthetic recorded workout", minutes = 40,
                distanceKm = 6.0, kind = "quality", quality = true,
                status = "completed", linkedActivityId = "recent-garmin"
            )
        ),
        explanations = emptyList(),
        status = "approved"
    )

    private fun activity(id: String, day: LocalDate, provider: String) =
        ActivityRecord(
            id = id, title = "Synthetic run", type = "running",
            source = provider,
            startedAt = day.atTime(7, 0).atZone(zone).toInstant(),
            distanceM = 6000.0,
            durationS = 2400.0, movingTimeS = null,
            recordedPace = null, averageHr = null, elevationM = null
        )

    private fun detail(id: String): ActivityDetail {
        val times = (0..24).map { 300.0 + it * 10.0 } +
            (0..24).map { 900.0 + it * 10.0 }
        return ActivityDetail(
            activityId = id,
            samples = times.mapIndexed { index, secs ->
                ActivitySample(index, secs, null, 150.0, null, null, null)
            },
            fetchedAt = 0L,
            laps = listOf(
                ActivityLap(0, 1000.0, 240.0, null, 150.0, 300.0),
                ActivityLap(1, 300.0, 120.0, null, null, 600.0),
                ActivityLap(2, 1000.0, 240.0, null, 150.0, 900.0)
            )
        )
    }

    private fun observation(
        id: String,
        session: String,
        day: LocalDate,
        family: IndependentQualityFamily
    ) = Engine15I3LinkedQualityObservation(
        lapClaim = IndependentQualityLapClaim(
            evidence = IndependentQualitySegmentEvidence(
                ownerId = owner, sourceRecordId = id, sessionId = session,
                performedOn = day, family = family,
                origin = IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
                sourceSessionMatchAsserted = true,
                performedWorkSegmentsSeconds = listOf(240, 240)
            ),
            recordedWorkLapIndices = listOf(0, 2)
        ),
        detail = detail(id)
    )

    private fun evidence(
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        altered: ((List<Engine15I3LinkedQualityObservation>) ->
            List<Engine15I3LinkedQualityObservation>)? = null
    ): Engine15I3QualityDurationInput {
        val original = listOf(
            observation("recent-garmin", "recent-session", recentDay, family),
            observation("older-fit", "older-session", olderDay, family)
        )
        return Engine15I3QualityDurationInput(
            assertedOwnerId = owner, asOf = asOf, zone = zone,
            plans = listOf(plan()),
            sourceActivities = listOf(
                activity("recent-garmin", recentDay, "garmin"),
                activity("older-fit", olderDay, "fit")
            ),
            observations = altered?.invoke(original) ?: original
        )
    }

    private fun calendar(
        days: Int = 4,
        blocked: Set<LocalDate> = emptySet()
    ) = Engine15M6QualitySpacingInput(
        calendar = Engine15M6CalendarInput(
            startDate = monday,
            availableIsoDays = when (days) {
                4 -> setOf(1, 3, 5, 7)
                6 -> setOf(1, 2, 3, 5, 6, 7)
                else -> error("Unrecognized synthetic run days")
            },
            preferredLongIsoDay = 7,
            blockedDates = blocked
        )
    )

    private fun expanded(preview: Engine15M5QualitySlotPreview):
        List<WorkoutStep> = buildList {
        for (element in preview.workout.elements) when (element) {
            is IndependentWorkoutElement.Step ->
                repeat(element.value.repetitions) { add(element.value) }
            is IndependentWorkoutElement.Intervals ->
                repeat(element.value.repetitions) { index ->
                    add(element.value.work)
                    if (index < element.value.repetitions - 1)
                        add(element.value.recovery)
                }
        }
    }

    private fun windows(
        s: Engine15IndependentVolumePlanner.Result,
        q: Engine15M5QualityPreviewRequest,
        easyMin: Double = 2.4,
        easyMax: Double = 4.2,
        qualityMin: Double = 2.8,
        qualityMax: Double = 5.0
    ): List<Engine15I5PhaseSpeedWindow> {
        val m5b = Engine15M5QualityPreviewComposer.compose(s, listOf(q))
            as Engine15M5QualityDecision.Candidate
        return expanded(m5b.qualityPreviews.single().let { it })
            .mapIndexed { i, step ->
                Engine15I5PhaseSpeedWindow(
                    phaseIndex = i,
                    purpose = step.purpose,
                    target = step.target,
                    minimumMetersPerSecond =
                        if (step.purpose == WorkoutPurpose.WORK) qualityMin else easyMin,
                    maximumMetersPerSecond =
                        if (step.purpose == WorkoutPurpose.WORK) qualityMax else easyMax
                )
            }
    }

    private fun input(
        s: Engine15IndependentVolumePlanner.Result,
        q: Engine15M5QualityPreviewRequest,
        days: Int = 4,
        reference: String? = "caller-reviewed-pace-window-not-authenticated",
        speed: List<Engine15I5PhaseSpeedWindow> = windows(s, q),
        blocked: Set<LocalDate> = emptySet()
    ) = Engine15I5DistanceFeasibilityInput(
        calendar = calendar(days, blocked),
        phaseSpeedWindows = speed,
        assertedExternalSpeedReviewReference = reference
    )

    private fun inspect(
        s: Engine15IndependentVolumePlanner.Result = source(),
        q: Engine15M5QualityPreviewRequest = request(),
        record: Engine15I3QualityDurationInput = evidence(),
        input: Engine15I5DistanceFeasibilityInput = input(s, q)
    ) = Engine15I5TimedDistanceFeasibility.review(s, q, record, input)

    private fun accepted(
        s: Engine15IndependentVolumePlanner.Result = source(),
        q: Engine15M5QualityPreviewRequest = request(),
        record: Engine15I3QualityDurationInput = evidence(),
        input: Engine15I5DistanceFeasibilityInput = input(s, q)
    ): Engine15I5DistanceFeasibilityDecision.EnvelopeReview {
        val actual = inspect(s, q, record, input)
        assertTrue("Expected mathematically feasible shadow envelope, got $actual",
            actual is Engine15I5DistanceFeasibilityDecision.EnvelopeReview)
        return actual as Engine15I5DistanceFeasibilityDecision.EnvelopeReview
    }

    private fun deferred(
        expected: String,
        s: Engine15IndependentVolumePlanner.Result = source(),
        q: Engine15M5QualityPreviewRequest = request(),
        record: Engine15I3QualityDurationInput = evidence(),
        input: Engine15I5DistanceFeasibilityInput = input(s, q)
    ) {
        val actual = inspect(s, q, record, input)
        assertTrue("Expected $expected, got $actual",
            actual is Engine15I5DistanceFeasibilityDecision.Deferred)
        assertEquals(expected,
            (actual as Engine15I5DistanceFeasibilityDecision.Deferred).reasonCode)
    }

    @Test fun developingFourDayEnvelopeCanContainOriginalSlotWithoutChangingMileage() {
        val s = source()
        val q = request()
        val record = evidence()
        val ranges = input(s, q)
        val result = accepted(s, q, record, ranges)
        assertTrue(result.minimumPossibleMeters < result.originalEasySlotMeters)
        assertTrue(result.maximumPossibleMeters > result.originalEasySlotMeters)
        assertEquals(result.originalEasySlotMeters,
            result.selectedOpportunity.existingRun.distanceKm * 1000.0, 0.01)
        assertEquals(s.weeks.first().proposedKm,
            result.originalM6B.baseCalendar.first().budgetKm)
        assertEquals(
            Engine15I4WorkLapHeartRateReview.inspect(s,q,record),
            result.originalI4
        )
        assertEquals(
            Engine15M6IndependentQualitySpacing.propose(s,ranges.calendar,listOf(q)),
            result.originalM6B
        )
        assertTrue(result.boundsExternallyAsserted)
        assertFalse(result.paceCalibrationVerified)
        assertFalse(result.qualityIntensityVerified)
        assertFalse(result.sourceDistancesVerified)
        assertFalse(result.actualSegmentDistancesVerified)
        assertFalse(result.athleteResponseVerified)
        assertFalse(result.doseAuthorized)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        assertFalse(result.originalM6B.executable)
    }

    @Test fun shortTimedQualityCannotFitHighMileageWithSyntheticBoundedSpeeds() {
        val s = source(recent=75.0, target=80.0, longest=20.0, days=6)
        val q = request(slot=2)
        deferred("ALLOCATION_ABOVE_ASSERTED_MAXIMUM",
            s=s, q=q, input=input(s,q,days=6))
    }

    @Test fun rangeWhoseMinimumDistanceExceedsOriginalSlotIsDeferred() {
        val s=source()
        val q=request()
        val tooFast=windows(s,q,easyMin=8.0,easyMax=9.0,
            qualityMin=8.0,qualityMax=9.0)
        deferred("ALLOCATION_BELOW_ASSERTED_MINIMUM",
            input=input(s,q,speed=tooFast))
    }

    @Test fun missingReferenceMissingWindowExtraWindowAndPhaseMismatchDefer() {
        val s=source()
        val q=request()
        val base=windows(s,q)
        deferred("SPEED_REVIEW_REFERENCE_MISSING",
            input=input(s,q,reference=null))
        deferred("PHASE_SPEED_WINDOW_COVERAGE_MISMATCH",
            input=input(s,q,speed=base.dropLast(1)))
        deferred("PHASE_SPEED_WINDOW_COVERAGE_MISMATCH",
            input=input(s,q,speed=base+base.last().copy(
                phaseIndex=base.size)))
        deferred("PHASE_SPEED_WINDOW_ORDER_MISMATCH",
            input=input(s,q,speed=base.mapIndexed { i,w ->
                if(i==1)w.copy(phaseIndex=3) else w
            }))
        deferred("PHASE_SPEED_WINDOW_ROLE_MISMATCH",
            input=input(s,q,speed=base.mapIndexed { i,w ->
                if(i==0)w.copy(target=WorkoutTarget.THRESHOLD) else w
            }))
    }

    @Test fun invalidNaNInfiniteReversedZeroOrExtremeSpeedDefer() {
        val s=source()
        val q=request()
        val original=windows(s,q)
        val invalid=listOf(
            original[0].copy(minimumMetersPerSecond=0.0),
            original[0].copy(minimumMetersPerSecond=Double.NaN),
            original[0].copy(maximumMetersPerSecond=Double.POSITIVE_INFINITY),
            original[0].copy(minimumMetersPerSecond=5.0,
                maximumMetersPerSecond=4.0),
            original[0].copy(maximumMetersPerSecond=20.0)
        )
        invalid.forEach { wrong ->
            deferred("INVALID_ASSERTED_PHASE_SPEED",
                input=input(s,q,speed=listOf(wrong)+original.drop(1)))
        }
    }

    @Test fun impossibleVerySlowEnvelopeCannotSatisfyEvenDevelopingSlot() {
        val s=source()
        val q=request()
        val slow=windows(s,q,easyMin=0.5,easyMax=1.5,
            qualityMin=0.5,qualityMax=1.5)
        deferred("ALLOCATION_ABOVE_ASSERTED_MAXIMUM",
            input=input(s,q,speed=slow))
    }

    @Test fun I4MissingSourceHrRemainsAStopConditionForDistanceFeasibility() {
        val missing=evidence(altered={ entries ->
            entries.mapIndexed { index, item ->
                if(index==0) item.copy(detail=requireNotNull(item.detail).copy(
                    samples=requireNotNull(item.detail).samples.map { sample ->
                        if(sample.seconds in 900.0..1140.0)
                            sample.copy(hr=null) else sample
                    }
                )) else item
            }
        })
        deferred("I4_INSUFFICIENT_WORK_LAP_HR_COVERAGE", record=missing)
        val absent=evidence(altered={ entries ->
            listOf(entries[0].copy(detail=null),entries[1])
        })
        deferred("I4_UPSTREAM_SOURCE_SOURCE_DETAIL_UNAVAILABLE",record=absent)
    }

    @Test fun blockedLongDayOrLongRunSpacingRemainsEnforcedByM6B() {
        val s=source()
        val q=request()
        deferred("M6B_CALENDAR_LONG_DAY_BLOCKED",
            input=input(s,q,blocked=setOf(LocalDate.of(2026,11,22))))
        val six=source(recent=75.0,target=80.0,longest=20.0,days=6)
        val saturday=request(slot=5)
        deferred("M6B_QUALITY_TOO_CLOSE_TO_LONG",
            s=six,q=saturday,input=input(six,saturday,days=6))
    }

    @Test fun noUnreviewedOrRecoveryQualityCanBypassI4() {
        deferred("I4_UPSTREAM_UPSTREAM_QUALITY_EXTERNAL_REVIEW_MISSING",
            q=request(reviewed=false),
            input=input(source(),request()))
        deferred("I4_UPSTREAM_UPSTREAM_QUALITY_RECOVERY_WEEK_HOLD",
            q=request(week=4),
            input=input(source(),request()))
    }

    @Test fun tempoPreviewWithMatchingCallerFamilyIsStillOnlyAFeasibilityRange() {
        val s=source()
        val q=request(family=IndependentQualityFamily.TEMPO)
        val tempoEvidence=evidence(family=IndependentQualityFamily.TEMPO)
        val result=accepted(s,q,tempoEvidence,input(s,q))
        assertEquals(3,result.phaseBounds.size)
        assertFalse(result.qualityIntensityVerified)
        assertFalse(result.executable)
    }

    @Test fun repeatedReviewIsDeterministicWithoutInventingSegmentDistances() {
        val s=source()
        val q=request()
        val e=evidence()
        val i=input(s,q)
        val one=accepted(s,q,e,i)
        val two=accepted(s,q,e,i)
        assertEquals(one,two)
        assertEquals(one.phaseBounds.size,one.phaseBounds.map { it.phaseIndex }.toSet().size)
        assertEquals(one.originalI4.originalI3.originalM5B.qualityPreviews.single(),
            one.selectedOpportunity.preview)
    }
}

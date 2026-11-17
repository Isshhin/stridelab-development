package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** I6 verifies source metric consistency only; not a coaching permission. */
class Engine15I6SourceWorkLapPaceConsistencyTest {
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
        val request = PlanRequest(
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
            Engine15AthleteBaseline.assess(request),
            Engine15IndependentVolumePlanner.Input(8, days, target, false, false)
        )
    }

    private fun request(
        slot: Int = 1,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        reviewed: Boolean = true,
        observed: Int? = 480
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = 1, slotNumber = slot, family = family,
        fitnessInput = IndependentQualityFitnessInput(
            reportedRecentWorkSeconds = observed,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = 3600,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = "synthetic-caller-reviewed-quality"
    )

    private fun plan() = PlanSnapshot(
        id = "synthetic-plan",
        owner = owner,
        engineVersion = ENGINE15_VERSION,
        policyId = ENGINE15_POLICY,
        createdAt = Instant.EPOCH,
        request = PlanRequest(policyChoice = "engine15"),
        sessions = listOf(
            PlannedSession(
                id = "older-session", date = olderDay,
                discipline = "run", title = "Synthetic earlier quality",
                minutes = 40, distanceKm = 6.0, kind = "quality",
                quality = true, status = "completed",
                linkedActivityId = "older-fit"
            ),
            PlannedSession(
                id = "recent-session", date = recentDay,
                discipline = "run", title = "Synthetic recent quality",
                minutes = 40, distanceKm = 6.0, kind = "quality",
                quality = true, status = "completed",
                linkedActivityId = "recent-garmin"
            )
        ),
        explanations = emptyList(),
        status = "approved"
    )

    private fun activity(id: String, date: LocalDate, provider: String) =
        ActivityRecord(
            id = id, title = "Synthetic source run",
            type = "running", source = provider,
            startedAt = date.atTime(7, 0).atZone(zone).toInstant(),
            distanceM = 6000.0, durationS = 2400.0,
            movingTimeS = null, recordedPace = null,
            averageHr = null, elevationM = null
        )

    private fun detail(id: String): ActivityDetail {
        val times = (0..24).map { 300.0 + it * 10.0 } +
            (0..24).map { 900.0 + it * 10.0 }
        return ActivityDetail(
            activityId = id,
            samples = times.mapIndexed { index, time ->
                ActivitySample(index, time, 240.0, 150.0, null, null, null)
            },
            fetchedAt = 0L,
            laps = listOf(
                ActivityLap(0, 1000.0, 240.0, 240.0, 150.0, 300.0),
                ActivityLap(1, 300.0, 120.0, null, null, 600.0),
                ActivityLap(2, 1000.0, 240.0, 240.0, 150.0, 900.0)
            )
        )
    }

    private fun observation(
        id: String,
        session: String,
        day: LocalDate,
        family: IndependentQualityFamily
    ): Engine15I3LinkedQualityObservation = Engine15I3LinkedQualityObservation(
        lapClaim = IndependentQualityLapClaim(
            evidence = IndependentQualitySegmentEvidence(
                ownerId = owner,
                sourceRecordId = id,
                sessionId = session,
                performedOn = day,
                family = family,
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
        change: ((List<Engine15I3LinkedQualityObservation>) ->
            List<Engine15I3LinkedQualityObservation>)? = null
    ): Engine15I3QualityDurationInput {
        val base = listOf(
            observation("recent-garmin", "recent-session", recentDay, family),
            observation("older-fit", "older-session", olderDay, family)
        )
        return Engine15I3QualityDurationInput(
            assertedOwnerId = owner,
            asOf = asOf,
            zone = zone,
            plans = listOf(plan()),
            sourceActivities = listOf(
                activity("recent-garmin", recentDay, "garmin"),
                activity("older-fit", olderDay, "fit")
            ),
            observations = change?.invoke(base) ?: base
        )
    }

    private fun expanded(preview: Engine15M5QualitySlotPreview) =
        buildList<WorkoutStep> {
            for (element in preview.workout.elements) when (element) {
                is IndependentWorkoutElement.Step ->
                    repeat(element.value.repetitions) { add(element.value) }
                is IndependentWorkoutElement.Intervals ->
                    repeat(element.value.repetitions) { i ->
                        add(element.value.work)
                        if (i < element.value.repetitions - 1)
                            add(element.value.recovery)
                    }
            }
        }

    private fun configuration(
        s: Engine15IndependentVolumePlanner.Result,
        q: Engine15M5QualityPreviewRequest,
        easyMin: Double = 2.4,
        easyMax: Double = 4.2,
        workMin: Double = 2.8,
        workMax: Double = 5.0,
        reference: String? = "synthetic-reviewed-envelopes"
    ): Engine15I5DistanceFeasibilityInput {
        val preview = (
            Engine15M5QualityPreviewComposer.compose(s, listOf(q))
                as Engine15M5QualityDecision.Candidate
            ).qualityPreviews.single()
        val windows = expanded(preview).mapIndexed { index, step ->
            Engine15I5PhaseSpeedWindow(
                phaseIndex = index,
                purpose = step.purpose,
                target = step.target,
                minimumMetersPerSecond =
                    if (step.purpose == WorkoutPurpose.WORK) workMin else easyMin,
                maximumMetersPerSecond =
                    if (step.purpose == WorkoutPurpose.WORK) workMax else easyMax
            )
        }
        return Engine15I5DistanceFeasibilityInput(
            calendar = Engine15M6QualitySpacingInput(
                calendar = Engine15M6CalendarInput(
                    startMonday = monday,
                    availableIsoDays =
                        if (s.input.runningDays == 6)
                            setOf(1, 2, 3, 5, 6, 7)
                        else setOf(1, 3, 5, 7),
                    preferredLongIsoDay = 7
                )
            ),
            phaseSpeedWindows = windows,
            assertedExternalSpeedReviewReference = reference
        )
    }

    private fun accepted(
        s: Engine15IndependentVolumePlanner.Result = source(),
        q: Engine15M5QualityPreviewRequest = request(),
        record: Engine15I3QualityDurationInput = evidence(),
        ranges: Engine15I5DistanceFeasibilityInput = configuration(s,q)
    ): Engine15I6PaceConsistencyDecision.DescriptiveSourceConsistency {
        val result = Engine15I6SourceWorkLapPaceConsistency.inspect(
            s,q,record,ranges
        )
        assertTrue("Expected I6 descriptive consistency, got $result",
            result is Engine15I6PaceConsistencyDecision.DescriptiveSourceConsistency)
        return result as Engine15I6PaceConsistencyDecision.DescriptiveSourceConsistency
    }

    private fun deferred(
        reason: String,
        s: Engine15IndependentVolumePlanner.Result = source(),
        q: Engine15M5QualityPreviewRequest = request(),
        record: Engine15I3QualityDurationInput = evidence(),
        ranges: Engine15I5DistanceFeasibilityInput = configuration(s,q)
    ) {
        val result = Engine15I6SourceWorkLapPaceConsistency.inspect(
            s,q,record,ranges
        )
        assertTrue("Expected I6 $reason, got $result",
            result is Engine15I6PaceConsistencyDecision.Deferred)
        assertEquals(reason,
            (result as Engine15I6PaceConsistencyDecision.Deferred).reasonCode)
    }

    @Test fun twoRecordedLapSourcesArePaceAndDistanceConsistentNotAuthorized() {
        val s = source()
        val q = request()
        val record = evidence()
        val ranges = configuration(s,q)
        val result = accepted(s,q,record,ranges)
        assertEquals(
            Engine15I5TimedDistanceFeasibility.review(s,q,record,ranges),
            result.originalI5
        )
        assertEquals(listOf("recent-garmin","older-fit"),
            result.recordings.map { it.activityId })
        assertEquals(4,result.recordings.sumOf { it.workLaps.size })
        result.recordings.forEach { recording ->
            assertEquals(2000.0,recording.recordedWorkMeters,0.0)
            assertEquals(480.0,recording.recordedWorkSeconds,0.0)
            recording.workLaps.forEach { lap ->
                assertEquals(1000.0,lap.recordedMeters,0.0)
                assertEquals(240.0,lap.recordedSeconds,0.0)
                assertEquals(240.0,lap.derivedLapPaceSecondsPerKm,0.0)
                assertEquals(240.0,lap.sourceReportedLapPaceSecondsPerKm!!,0.0)
                assertEquals(1.0,lap.paceCoverageFraction,0.0)
                assertEquals(4.166666666666667,
                    lap.timeWeightedSampleSpeedMetersPerSecond,1e-8)
                assertEquals(0.0,lap.relativeSampleLapSpeedError,1e-8)
            }
        }
        assertTrue(result.lapAndPaceSignalsConsistent)
        assertFalse(result.sourceOwnerAuthenticated)
        assertFalse(result.paceCalibrationVerified)
        assertFalse(result.qualityIntensityVerified)
        assertFalse(result.actualFutureSegmentDistanceVerified)
        assertFalse(result.athleteResponseVerified)
        assertFalse(result.doseAuthorized)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        assertFalse(result.originalI5.executable)
    }

    @Test fun missingLapDistanceCannotBeBackfilledFromTimeOrPace() {
        val changed = evidence(change = { items ->
            listOf(items[0].copy(
                detail=requireNotNull(items[0].detail).copy(
                    laps=requireNotNull(items[0].detail).laps.map {
                        if (it.index==0) it.copy(distance=null) else it
                    })
            ),items[1])
        })
        deferred("SOURCE_WORK_LAP_DISTANCE_UNKNOWN_OR_INVALID",record=changed)
    }

    @Test fun missingLapPaceMayBeReportedUnknownWithoutFabricatingIt() {
        val changed = evidence(change = { items ->
            listOf(items[0].copy(
                detail=requireNotNull(items[0].detail).copy(
                    laps=requireNotNull(items[0].detail).laps.map {
                        if (it.index==0) it.copy(pace=null) else it
                    })
            ),items[1])
        })
        val result=accepted(record=changed)
        assertNull(result.recordings.first().workLaps.first()
            .sourceReportedLapPaceSecondsPerKm)
        assertEquals(240.0,result.recordings.first().workLaps.first()
            .derivedLapPaceSecondsPerKm,0.0)
    }

    @Test fun sourceReportedLapPaceContradictingDistanceTimeDefers() {
        val changed=evidence(change = { items ->
            listOf(items[0].copy(
                detail=requireNotNull(items[0].detail).copy(
                    laps=requireNotNull(items[0].detail).laps.map {
                        if (it.index==0) it.copy(pace=350.0) else it
                    })
            ),items[1])
        })
        deferred("SOURCE_REPORTED_LAP_PACE_MISMATCH",record=changed)
    }

    @Test fun entirelyMissingOrGappedWorkLapPaceDoesNotCountAsCovered() {
        val missing=evidence(change = { items ->
            listOf(items[0].copy(
                detail=requireNotNull(items[0].detail).copy(
                    samples=requireNotNull(items[0].detail).samples.map {
                        if (it.seconds in 900.0..1140.0)
                            it.copy(pace=null) else it
                    })
            ),items[1])
        })
        deferred("INSUFFICIENT_WORK_LAP_PACE_COVERAGE",record=missing)
        val sparse=evidence(change = { items ->
            listOf(items[0].copy(
                detail=requireNotNull(items[0].detail).copy(
                    samples=requireNotNull(items[0].detail).samples.map {
                        if (it.seconds % 60.0 != 0.0)
                            it.copy(pace=null) else it
                    })
            ),items[1])
        })
        deferred("INSUFFICIENT_WORK_LAP_PACE_COVERAGE",record=sparse)
    }

    @Test fun impossibleOrNonfiniteRecordedPaceSampleDefers() {
        for (bad in listOf(0.0,Double.NaN,Double.POSITIVE_INFINITY,8000.0)) {
            val changed=evidence(change = { items ->
                listOf(items[0].copy(
                    detail=requireNotNull(items[0].detail).copy(
                        samples=requireNotNull(items[0].detail).samples.mapIndexed { i,v ->
                            if (i==5) v.copy(pace=bad) else v
                        })
                ),items[1])
            })
            deferred("SOURCE_WORK_LAP_PACE_SAMPLE_INVALID",record=changed)
        }
    }

    @Test fun recordedPaceSamplesDisagreeingWithSourceLapMetersDefer() {
        val changed=evidence(change = { items ->
            listOf(items[0].copy(
                detail=requireNotNull(items[0].detail).copy(
                    samples=requireNotNull(items[0].detail).samples.map {
                        it.copy(pace=400.0)
                    })
            ),items[1])
        })
        deferred("SAMPLE_VS_LAP_SPEED_INCONSISTENT",record=changed)
    }

    @Test fun recordedWorkLapSpeedOutsideAssertedWorkRangeDefers() {
        val s=source()
        val q=request()
        // A 6.3 km existing EASY slot fits the synthetic 1680-second preview:
        // 1200s of EASY at <=4.3m/s plus 480s of WORK at <=2.5m/s
        // allows up to 6360m. The previous EASY max of 4.2m/s allowed
        // only 6240m, so I5 correctly deferred before reaching I6.
        val ranges=configuration(s,q,easyMax=4.3,workMin=1.2,workMax=2.5)
        // I5 arithmetic is feasible, but recorded WORK laps (~4.17 m/s)
        // still contradict the caller-asserted WORK range (1.2..2.5).
        val feasibility=Engine15I5TimedDistanceFeasibility.review(
            s,q,evidence(),ranges
        )
        assertTrue("Expected I5 feasible before I6 review, got $feasibility",
            feasibility is Engine15I5DistanceFeasibilityDecision.EnvelopeReview)
        deferred("RECORDED_WORK_SPEED_OUTSIDE_ASSERTED_RANGE",
            s=s,q=q,ranges=ranges)
    }

    @Test fun partialEightyPercentPaceCoverageStillNeverApprovesTraining() {
        val changed=evidence(change = { items ->
            listOf(items[0].copy(
                detail=requireNotNull(items[0].detail).copy(
                    samples=requireNotNull(items[0].detail).samples.map {
                        if (it.seconds in 300.0..330.0) it.copy(pace=null) else it
                    })
            ),items[1])
        })
        val result=accepted(record=changed)
        assertEquals(200.0/240.0,result.recordings.first().workLaps.first()
            .paceCoverageFraction,1e-8)
        assertFalse(result.paceCalibrationVerified)
        assertFalse(result.qualityIntensityVerified)
        assertFalse(result.executable)
    }

    @Test fun I5DistanceAndI4HrAndI3SourceDeferralsCannotBypassI6() {
        val six=source(recent=75.0,target=80.0,longest=20.0,days=6)
        val q=request(slot=2)
        deferred("UPSTREAM_ALLOCATION_ABOVE_ASSERTED_MAXIMUM",
            s=six,q=q,ranges=configuration(six,q))
        val missingHr=evidence(change = { items ->
            listOf(items[0].copy(
                detail=requireNotNull(items[0].detail).copy(
                    samples=requireNotNull(items[0].detail).samples.map {
                        if (it.seconds in 900.0..1140.0)
                            it.copy(hr=null) else it
                    })
            ),items[1])
        })
        deferred("UPSTREAM_I4_INSUFFICIENT_WORK_LAP_HR_COVERAGE",
            record=missingHr)
        val duplicate=evidence(change = { items ->
            listOf(items[0],items[1].copy(lapClaim=items[1].lapClaim.copy(
                evidence=items[1].lapClaim.evidence.copy(
                    sourceRecordId="recent-garmin")
            )))
        })
        deferred("UPSTREAM_I4_UPSTREAM_DUPLICATE_QUALITY_SOURCE",
            record=duplicate)
        val badQuality=request(reviewed=false)
        deferred("UPSTREAM_I4_UPSTREAM_UPSTREAM_QUALITY_EXTERNAL_REVIEW_MISSING",
            q=badQuality,ranges=configuration(source(),request()))
    }

    @Test fun matchingTempoFamilyIsDescriptiveNotAnIntensityClassification() {
        val s=source()
        val q=request(family=IndependentQualityFamily.TEMPO)
        val rec=evidence(family=IndependentQualityFamily.TEMPO)
        val result=accepted(s,q,rec,configuration(s,q))
        assertEquals(3,result.originalI5.phaseBounds.size)
        assertFalse(result.qualityIntensityVerified)
        assertFalse(result.doseAuthorized)
    }

    @Test fun identicalOriginalInputsProduceIdenticalReportsWithoutPlanMutation() {
        val s=source()
        val q=request()
        val rec=evidence()
        val ranges=configuration(s,q)
        val a=accepted(s,q,rec,ranges)
        val b=accepted(s,q,rec,ranges)
        assertEquals(a,b)
        assertEquals(a.originalI5.originalM6B.baseCalendar.map { it.budgetKm },
            Engine15M6IndependentQualitySpacing.propose(
                s,ranges.calendar,listOf(q)
            ).let { (it as Engine15M6QualitySpacingDecision.Candidate)
                .baseCalendar.map { week -> week.budgetKm }
            })
    }
}

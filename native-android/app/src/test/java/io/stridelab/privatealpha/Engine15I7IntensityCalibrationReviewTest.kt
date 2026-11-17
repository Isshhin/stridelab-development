package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Synthetic calibration intake is not threshold or dose authorization. */
class Engine15I7IntensityCalibrationReviewTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val asOf = LocalDate.of(2026, 11, 17)
    private val zone = ZoneId.of("Asia/Manila")
    private val owner = "synthetic-calibration-owner"
    private val recentDay = asOf.minusDays(3)
    private val olderDay = asOf.minusDays(10)

    private fun source(): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = 8, days = (1..7).toSet(),
            runDays = 4, longDay = 7, qualityCount = 0,
            weeklyKm = 30.0, targetWeeklyKm = 35.0,
            longestKm = 10.0, returning = false,
            policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false,
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(8,4,35.0,false,false)
        )
    }

    private fun quality(
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        reviewed: Boolean = true
    ) = Engine15M5QualityPreviewRequest(
        weekNumber=1,slotNumber=1,family=family,
        fitnessInput=IndependentQualityFitnessInput(
            reportedRecentWorkSeconds=480,
            approvedWorkCeilingSeconds=1200,
            approvedIncreaseSeconds=0,
            availableSessionSeconds=3600,
            qualityAuthorized=true
        ),
        explicitlyReviewedDose=reviewed,
        reviewReference="synthetic-caller-quality-review"
    )

    private fun plan() = PlanSnapshot(
        id="original-synthetic-plan",owner=owner,
        engineVersion=ENGINE15_VERSION,policyId=ENGINE15_POLICY,
        createdAt=Instant.EPOCH,request=PlanRequest(policyChoice="engine15"),
        sessions=listOf(
            PlannedSession(id="old-session",date=olderDay,discipline="run",
                title="Old synthetic recorded effort",minutes=40,
                distanceKm=6.0,kind="quality",quality=true,
                status="completed",linkedActivityId="old-source"),
            PlannedSession(id="recent-session",date=recentDay,discipline="run",
                title="Recent synthetic recorded effort",minutes=40,
                distanceKm=6.0,kind="quality",quality=true,
                status="completed",linkedActivityId="recent-source")
        ),
        explanations=emptyList(),status="approved"
    )

    private fun activity(id:String,day:LocalDate,source:String)=ActivityRecord(
        id=id,title="Synthetic recorded run",type="running",source=source,
        startedAt=day.atTime(7,0).atZone(zone).toInstant(),
        distanceM=6000.0,durationS=2400.0,movingTimeS=null,
        recordedPace=null,averageHr=null,elevationM=null
    )

    private fun detail(id:String):ActivityDetail {
        val seconds=(0..24).map { 300.0+it*10.0 } +
            (0..24).map { 900.0+it*10.0 }
        return ActivityDetail(
            activityId=id,fetchedAt=0L,
            samples=seconds.mapIndexed { i,s ->
                ActivitySample(i,s,240.0,150.0,null,null,null)
            },
            laps=listOf(
                ActivityLap(0,1000.0,240.0,240.0,150.0,300.0),
                ActivityLap(1,300.0,120.0,null,null,600.0),
                ActivityLap(2,1000.0,240.0,240.0,150.0,900.0)
            )
        )
    }

    private fun evidence(
        family:IndependentQualityFamily=IndependentQualityFamily.THRESHOLD,
        change:((List<Engine15I3LinkedQualityObservation>)->
            List<Engine15I3LinkedQualityObservation>)?=null
    ):Engine15I3QualityDurationInput {
        fun observation(id:String,session:String,date:LocalDate)=
            Engine15I3LinkedQualityObservation(
                lapClaim=IndependentQualityLapClaim(
                    evidence=IndependentQualitySegmentEvidence(
                        ownerId=owner,sourceRecordId=id,sessionId=session,
                        performedOn=date,family=family,
                        origin=IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
                        sourceSessionMatchAsserted=true,
                        performedWorkSegmentsSeconds=listOf(240,240)
                    ),
                    recordedWorkLapIndices=listOf(0,2)
                ),
                detail=detail(id)
            )
        val all=listOf(
            observation("recent-source","recent-session",recentDay),
            observation("old-source","old-session",olderDay)
        )
        return Engine15I3QualityDurationInput(
            assertedOwnerId=owner,asOf=asOf,zone=zone,
            plans=listOf(plan()),
            sourceActivities=listOf(
                activity("recent-source",recentDay,"garmin"),
                activity("old-source",olderDay,"fit")
            ),
            observations=change?.invoke(all) ?: all
        )
    }

    private fun feasibility(
        s:Engine15IndependentVolumePlanner.Result=source(),
        q:Engine15M5QualityPreviewRequest=quality()
    ):Engine15I5DistanceFeasibilityInput {
        val preview=(
            Engine15M5QualityPreviewComposer.compose(s,listOf(q))
                as Engine15M5QualityDecision.Candidate
            ).qualityPreviews.single()
        val segments=buildList<WorkoutStep> {
            for (element in preview.workout.elements) when(element) {
                is IndependentWorkoutElement.Step ->
                    repeat(element.value.repetitions) { add(element.value) }
                is IndependentWorkoutElement.Intervals ->
                    repeat(element.value.repetitions) { index ->
                        add(element.value.work)
                        if(index<element.value.repetitions-1)add(element.value.recovery)
                    }
            }
        }
        return Engine15I5DistanceFeasibilityInput(
            calendar=Engine15M6QualitySpacingInput(
                calendar=Engine15M6CalendarInput(
                    startMonday=monday,
                    availableIsoDays=setOf(1,3,5,7),
                    preferredLongIsoDay=7
                )
            ),
            phaseSpeedWindows=segments.mapIndexed { i, step ->
                Engine15I5PhaseSpeedWindow(
                    phaseIndex=i,
                    purpose=step.purpose,target=step.target,
                    minimumMetersPerSecond=
                        if(step.purpose==WorkoutPurpose.WORK)2.8 else 2.4,
                    maximumMetersPerSecond=
                        if(step.purpose==WorkoutPurpose.WORK)5.0 else 4.2
                )
            },
            assertedExternalSpeedReviewReference="synthetic-speed-review"
        )
    }

    private fun calibration(
        ownerId:String=owner,
        family:IndependentQualityFamily=IndependentQualityFamily.THRESHOLD,
        measured:LocalDate=asOf.minusDays(2),
        origin:Engine15I7CalibrationOrigin=
            Engine15I7CalibrationOrigin.STRUCTURED_FIELD_TEST_ASSERTED
    )=Engine15I7CalibrationClaim(
        assertedOwnerId=ownerId,family=family,measuredOn=measured,origin=origin,
        subjectSpecificSourceReference="synthetic-test-record",
        testingProtocolReference="synthetic-reviewed-field-protocol",
        artifactSha256Claim="a".repeat(64),
        claimedThresholdHrBpm=172,
        claimedThresholdPaceSecondsPerKm=240.0,
        assertedRestingHrBpm=52,
        assertedMaximumHrBpm=198
    )

    private fun inspect(
        s:Engine15IndependentVolumePlanner.Result=source(),
        q:Engine15M5QualityPreviewRequest=quality(),
        evidence:Engine15I3QualityDurationInput=evidence(),
        speed:Engine15I5DistanceFeasibilityInput=feasibility(s,q),
        input:Engine15I7CalibrationReviewInput=
            Engine15I7CalibrationReviewInput(calibration())
    )=Engine15I7IntensityCalibrationReview.inspect(s,q,evidence,speed,input)

    private fun accepted(
        s:Engine15IndependentVolumePlanner.Result=source(),
        q:Engine15M5QualityPreviewRequest=quality(),
        evidence:Engine15I3QualityDurationInput=evidence(),
        speed:Engine15I5DistanceFeasibilityInput=feasibility(s,q),
        input:Engine15I7CalibrationReviewInput=
            Engine15I7CalibrationReviewInput(calibration())
    ):Engine15I7CalibrationReviewDecision.ExternalQualificationRequired {
        val got=inspect(s,q,evidence,speed,input)
        assertTrue("Expected I7 external-only packet, got $got",
            got is Engine15I7CalibrationReviewDecision.ExternalQualificationRequired)
        return got as Engine15I7CalibrationReviewDecision.ExternalQualificationRequired
    }

    private fun deferred(
        code:String,
        s:Engine15IndependentVolumePlanner.Result=source(),
        q:Engine15M5QualityPreviewRequest=quality(),
        evidence:Engine15I3QualityDurationInput=evidence(),
        speed:Engine15I5DistanceFeasibilityInput=feasibility(s,q),
        input:Engine15I7CalibrationReviewInput=
            Engine15I7CalibrationReviewInput(calibration())
    ) {
        val got=inspect(s,q,evidence,speed,input)
        assertTrue("Expected $code, got $got",
            got is Engine15I7CalibrationReviewDecision.Deferred)
        assertEquals(code,(got as Engine15I7CalibrationReviewDecision.Deferred).reasonCode)
    }

    @Test fun structuredFieldTestClaimIsStillUnverifiedAndNeverAuthorizesWorkout() {
        val s=source();val q=quality();val e=evidence();val f=feasibility(s,q)
        val result=accepted(s,q,e,f)
        assertEquals(
            Engine15I6SourceWorkLapPaceConsistency.inspect(s,q,e,f),
            result.originalI6
        )
        assertEquals(calibration(),result.originalCalibrationClaim)
        assertTrue(result.outstandingVerificationGaps.size>=5)
        assertTrue("CALIBRATION_SOURCE_BYTES_NOT_VERIFIED" in result.outstandingVerificationGaps)
        assertFalse(result.ownerSourceAuthenticated)
        assertFalse(result.calibrationArtifactVerified)
        assertFalse(result.calibrationProtocolVerified)
        assertFalse(result.thresholdIntensityVerified)
        assertFalse(result.workDoseAuthorized)
        assertFalse(result.athleteResponseVerified)
        assertFalse(result.futureDistanceVerified)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        assertFalse(result.originalI6.qualityIntensityVerified)
        assertFalse(result.originalI6.paceCalibrationVerified)
    }

    @Test fun claimedLabTestIsAnExternalReviewPacketNotARealProvenanceCertificate() {
        val result=accepted(input=Engine15I7CalibrationReviewInput(
            calibration(origin=Engine15I7CalibrationOrigin.LAB_TEST_ASSERTED)))
        assertEquals(Engine15I7CalibrationOrigin.LAB_TEST_ASSERTED,
            result.originalCalibrationClaim.origin)
        assertFalse(result.calibrationArtifactVerified)
        assertFalse(result.thresholdIntensityVerified)
    }

    @Test fun noClaimAndWatchOrManualOriginsFailClosed() {
        deferred("CALIBRATION_CLAIM_MISSING",
            input=Engine15I7CalibrationReviewInput(null))
        for(origin in listOf(
            Engine15I7CalibrationOrigin.DEVICE_ESTIMATE,
            Engine15I7CalibrationOrigin.MANUAL_ENTRY
        )) {
            deferred("CALIBRATION_ORIGIN_NOT_ELIGIBLE_FOR_REVIEW",
                input=Engine15I7CalibrationReviewInput(calibration(origin=origin)))
        }
    }

    @Test fun calibrationOwnerAndFamilyMustMatchIndependentSourceScope() {
        deferred("CALIBRATION_OWNER_SCOPE_MISMATCH",
            input=Engine15I7CalibrationReviewInput(calibration(ownerId="other-owner")))
        deferred("CALIBRATION_FAMILY_MISMATCH",
            input=Engine15I7CalibrationReviewInput(
                calibration(family=IndependentQualityFamily.TEMPO)))
    }

    @Test fun futureStaleAndBoundaryCalibrationDatesAreExplicit() {
        deferred("CALIBRATION_TEST_DATE_OUTSIDE_REVIEW_WINDOW",
            input=Engine15I7CalibrationReviewInput(
                calibration(measured=asOf.plusDays(1))))
        deferred("CALIBRATION_TEST_DATE_OUTSIDE_REVIEW_WINDOW",
            input=Engine15I7CalibrationReviewInput(
                calibration(measured=asOf.minusDays(91))))
        accepted(input=Engine15I7CalibrationReviewInput(
            calibration(measured=asOf.minusDays(90))))
        accepted(input=Engine15I7CalibrationReviewInput(
            calibration(measured=asOf)))
    }

    @Test fun missingReferencesAndInvalidArtifactFingerprintsFailClosed() {
        deferred("CALIBRATION_SOURCE_REFERENCE_MISSING",
            input=Engine15I7CalibrationReviewInput(calibration().copy(
                subjectSpecificSourceReference=" "
            )))
        deferred("CALIBRATION_PROTOCOL_REFERENCE_MISSING",
            input=Engine15I7CalibrationReviewInput(calibration().copy(
                testingProtocolReference=null
            )))
        for(hash in listOf<String?>(null,"","f".repeat(63),"G".repeat(64))) {
            deferred("CALIBRATION_ARTIFACT_FINGERPRINT_INVALID",
                input=Engine15I7CalibrationReviewInput(calibration().copy(
                    artifactSha256Claim=hash
                )))
        }
    }

    @Test fun unknownAndNumericallyInvalidThresholdHRAndPaceAreNotAssumed() {
        deferred("CALIBRATION_THRESHOLD_HR_UNKNOWN",
            input=Engine15I7CalibrationReviewInput(calibration().copy(
                claimedThresholdHrBpm=null
            )))
        deferred("CALIBRATION_THRESHOLD_HR_NUMERICALLY_INVALID",
            input=Engine15I7CalibrationReviewInput(calibration().copy(
                claimedThresholdHrBpm=270
            )))
        deferred("CALIBRATION_THRESHOLD_PACE_UNKNOWN",
            input=Engine15I7CalibrationReviewInput(calibration().copy(
                claimedThresholdPaceSecondsPerKm=null
            )))
        for(invalid in listOf(Double.NaN,Double.POSITIVE_INFINITY,0.0,1500.0)) {
            deferred("CALIBRATION_THRESHOLD_PACE_NUMERICALLY_INVALID",
                input=Engine15I7CalibrationReviewInput(calibration().copy(
                    claimedThresholdPaceSecondsPerKm=invalid
                )))
        }
    }

    @Test fun internalRestingMaximumAndThresholdConflictsDefer() {
        for(claim in listOf(
            calibration().copy(assertedRestingHrBpm=175),
            calibration().copy(assertedMaximumHrBpm=165),
            calibration().copy(assertedMaximumHrBpm=250),
            calibration().copy(assertedRestingHrBpm=10),
            calibration().copy(assertedRestingHrBpm=120,assertedMaximumHrBpm=115)
        )) {
            deferred("CALIBRATION_INTERNAL_HR_CONFLICT",
                input=Engine15I7CalibrationReviewInput(claim))
        }
    }

    @Test fun displayOnlyZoneSettingsMayReportConflictButCannotAuthorize() {
        deferred("DISPLAY_LTHR_CONTRADICTS_CALIBRATION",
            input=Engine15I7CalibrationReviewInput(calibration(),
                AthleteHeartRateZoneSettings(
                    method=HeartRateZoneMethod.LTHR_PERCENT,
                    lactateThresholdHr=170
                )
            ))
        deferred("DISPLAY_HR_BOUNDS_CONTRADICT_CALIBRATION",
            input=Engine15I7CalibrationReviewInput(calibration(),
                AthleteHeartRateZoneSettings(
                    method=HeartRateZoneMethod.MAX_BPM,
                    maximumHr=165
                )
            ))
        val packet=accepted(input=Engine15I7CalibrationReviewInput(calibration(),
            AthleteHeartRateZoneSettings(
                method=HeartRateZoneMethod.LTHR_PERCENT,
                lactateThresholdHr=172,
                maximumHr=198,
                restingHr=52
            )
        ))
        assertFalse(packet.thresholdIntensityVerified)
    }

    @Test fun brokenI6HRAndMissingDoseRemainUpstreamDeferrals() {
        val badHr=evidence(change={ list ->
            listOf(list[0].copy(detail=requireNotNull(list[0].detail).copy(
                samples=requireNotNull(list[0].detail).samples.map {
                    if (it.seconds in 900.0..1140.0) it.copy(hr=null) else it
                }
            )),list[1])
        })
        deferred("I6_UPSTREAM_I4_INSUFFICIENT_WORK_LAP_HR_COVERAGE",
            evidence=badHr)
        val q=quality(reviewed=false)
        deferred("I6_UPSTREAM_I4_UPSTREAM_UPSTREAM_QUALITY_EXTERNAL_REVIEW_MISSING",
            q=q,speed=feasibility(source(),quality()))
    }

    @Test fun identicalInputLeavesAllOriginalPlanningCandidatesUntouched() {
        val s=source();val q=quality();val e=evidence();val f=feasibility(s,q)
        val i=Engine15I7CalibrationReviewInput(calibration())
        val a=accepted(s,q,e,f,i)
        val b=accepted(s,q,e,f,i)
        assertEquals(a,b)
        assertEquals(s.weeks.first().proposedKm,
            a.originalI6.originalI5.originalM6B.baseCalendar.first().budgetKm)
        assertEquals(
            a.originalI6.originalI5.originalI4.originalI3.originalM5B.qualityPreviews.single(),
            a.originalI6.originalI5.selectedOpportunity.preview
        )
        assertFalse(a.executable)
    }
}

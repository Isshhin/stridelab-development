package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class Engine15UnifiedAthleteAssessmentTest {

    private val zone = ZoneOffset.UTC
    private val evaluatedAt = Instant.parse("2026-11-26T12:00:00Z")

    private fun garminRun(
        id: String,
        startedAtIso: String,
        distanceM: Double,
        durationS: Double
    ): ActivityRecord = ActivityRecord(
        id = id,
        title = "Morning Run",
        type = "running",
        source = "garmin",
        startedAt = Instant.parse(startedAtIso),
        distanceM = distanceM,
        durationS = durationS,
        movingTimeS = durationS,
        recordedPace = durationS / (distanceM / 1000.0),
        averageHr = 148.0,
        elevationM = 45.0,
        maximumHr = 166.0
    )

    private fun completedFreeformStrength(
        id: String,
        owner: String,
        performedOn: String,
        startedAtMs: Long,
        completedAtMs: Long
    ): FreeformWorkout = FreeformWorkout(
        id = id,
        owner = owner,
        discipline = ManualDiscipline.STRENGTH,
        performedOn = performedOn,
        startedAt = startedAtMs,
        modifiedAt = completedAtMs,
        completedAt = completedAtMs,
        durationMinutes = "45",
        workoutName = "Lower Body Strength",
        exercises = listOf(
            FreeformExercise(
                name = "Back Squat",
                sets = listOf(
                    FreeformSet(
                        target = FreeformMeasure(quantity = "5", kg = "80"),
                        actual = FreeformMeasure(quantity = "5", kg = "80"),
                        completed = true
                    )
                )
            )
        )
    )

    @Test
    fun endToEndAssemblyUnitesGarminFreeformHrAndM2ModulesWithoutCrossDisciplineLeakage() {
        val backend = InMemoryM1RecordBackend()
        val localStore = Engine15M1LocalStore(backend)
        val owner = localStore.resolveOwner(
            account = null,
            localPlanOwner = "local-plan-12345678-1234-4123-8123-1234567890ab"
        )

        val runs = listOf(
            garminRun("run-01", "2026-11-01T07:00:00Z", 10_000.0, 3_300.0),
            garminRun("run-02", "2026-11-04T07:00:00Z", 8_000.0, 2_700.0),
            garminRun("run-03", "2026-11-08T07:00:00Z", 14_000.0, 4_760.0),
            garminRun("run-04", "2026-11-12T07:00:00Z", 9_000.0, 3_060.0),
            garminRun("run-05", "2026-11-16T07:00:00Z", 10_000.0, 3_250.0),
            garminRun("run-06", "2026-11-22T07:00:00Z", 15_000.0, 5_100.0)
        )
        val strengthStart = Instant.parse("2026-11-18T17:00:00Z").toEpochMilli()
        val strengthEnd = Instant.parse("2026-11-18T17:45:00Z").toEpochMilli()
        val freeform = listOf(
            completedFreeformStrength(
                id = "strength-freeform-1",
                owner = owner,
                performedOn = "2026-11-18",
                startedAtMs = strengthStart,
                completedAtMs = strengthEnd
            ),
            // Future-completed session must be excluded from historical observed state.
            completedFreeformStrength(
                id = "strength-future",
                owner = owner,
                performedOn = "2026-11-27",
                startedAtMs = Instant.parse("2026-11-27T08:00:00Z").toEpochMilli(),
                completedAtMs = Instant.parse("2026-11-27T08:45:00Z").toEpochMilli()
            )
        )
        val hrProfile = ExecutionHrZoneProfile(
            z1FloorBpm = 115,
            z2FloorBpm = 132,
            z3FloorBpm = 150,
            z4FloorBpm = 165,
            z5FloorBpm = 178,
            maxHrBpm = 190
        )
        val race10k = M2ARacePerformanceInput(
            performanceId = "tt-10k-nov",
            accountId = owner,
            discipline = AthleteDiscipline.RUN,
            performedOn = LocalDate.of(2026, 11, 16),
            distance = Meters.fromKilometers(10.0),
            duration = Seconds.of(2_700.0),
            authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
            performanceIntent = M2APerformanceIntent.VERIFIED_TIME_TRIAL,
            sourceProvider = "garmin",
            observedAtUtc = Instant.parse("2026-11-16T07:00:00Z")
        )

        val assessment = Engine15UnifiedAthleteAssembler.assemble(
            UnifiedAthleteAssemblyInput(
                accountId = owner,
                evaluatedAtUtc = evaluatedAt,
                zoneId = zone,
                activityRecords = runs,
                freeformWorkouts = freeform,
                racePerformances = listOf(race10k),
                hrZoneProfile = hrProfile,
                localStore = localStore,
                persistDerivedAssessment = true
            )
        )

        // Running (66 km total across 6 sessions) stays completely separate from Strength.
        val runExposure = assessment.workloadAssessment.handoff.workloadVector.running28d
        assertEquals(6, runExposure.verifiedSessionCount)
        val runTotalDist = assertIsEstimatedMeters(runExposure.totalDistanceBelief)
        assertEquals(66_000.0, runTotalDist.value, 1e-3)

        val strSummary = assessment.athleteSnapshot.disciplines[AthleteDiscipline.STRENGTH]
        assertNotNull(strSummary)
        assertEquals(1, strSummary!!.evidenceIds.size)
        assertFalse(assessment.workloadAssessment.handoff.workloadVector.systemicMultimodal28d.byModality.isEmpty())

        // M2-A & M2-B produce verified benchmark calibration and preserve non-synthetic HR evidence.
        assertEquals(
            M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK,
            assessment.performanceCalibration.authorityMode
        )
        assertTrue(assessment.workloadAssessment.handoff.paceCalibrationEligible)
        val zonesEvidence = assessment.intensityCalibration.heartRateHierarchy
            .unambiguousEvidence(M2BHeartRateEvidenceKind.IMPORTED_ZONES)
        assertEquals(1, zonesEvidence.size)

        // M1 derived snapshot persisted strictly locally as kind = "workout" (zero outbox writes).
        assertNotNull(assessment.persistedAssessmentRecord)
        assertEquals(0, backend.outboxWriteCount)
        assertTrue(backend.recordedKinds.all { it == TrainingStoreM1Backend.LOCAL_KIND })
        assertTrue(localStore.loadDerivedAssessment(owner) is M1AssessmentLoadResult.Valid)
    }

    @Test
    fun safetyCapsClampRequestAndPreserveValidateSavedDeterminism() {
        val backend = InMemoryM1RecordBackend()
        val localStore = Engine15M1LocalStore(backend)
        val owner = "local-plan-22222222-3333-4444-8555-666666666666"

        val capRestriction = AthleteSafetyRestriction(
            restrictionId = "cap-achilles-return",
            accountId = owner,
            kind = SafetyRestrictionKind.MAX_WEEKLY_VOLUME_CAP,
            disciplines = setOf(AthleteDiscipline.RUN),
            severity = SafetyRestrictionSeverity.CAP_LIMITED,
            provenance = SafetyRestrictionProvenance.COACH_GUIDED_RETURN,
            maxWeeklyVolumeMeters = Meters.fromKilometers(26.0),
            maxSessionIntensityCap = SafetyIntensityCap.EASY_AEROBIC_ONLY,
            note = "Cap mileage and keep running easy during return",
            createdAtUtc = Instant.parse("2026-11-20T08:00:00Z")
        )
        localStore.saveRestriction(owner, capRestriction, evaluatedAt.toEpochMilli())

        val assessment = Engine15UnifiedAthleteAssembler.assemble(
            UnifiedAthleteAssemblyInput(
                accountId = owner,
                evaluatedAtUtc = evaluatedAt,
                zoneId = zone,
                localStore = localStore
            )
        )

        val rawRequest = PlanRequest(
            goal = "10K",
            experience = 1,
            start = LocalDate.of(2026, 11, 30),
            weeks = 4,
            minutes = 75,
            days = setOf(1, 2, 4, 6),
            runDays = 4,
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 42.0,
            longestKm = 16.0,
            targetWeeklyKm = 45.0,
            planningPace = 340.0,
            policyChoice = "engine15"
        )

        val effectiveRequest = Engine15UnifiedAthleteAssembler.applySafetyAndAssessmentToRequest(
            rawRequest,
            assessment
        )

        assertFalse(effectiveRequest.runningRecoveryHold)
        assertEquals(26.0, effectiveRequest.weeklyKm, 1e-6)
        assertEquals(26.0, effectiveRequest.targetWeeklyKm!!, 1e-6)
        assertEquals(16.0, effectiveRequest.longestKm, 1e-6)
        assertEquals(0, effectiveRequest.qualityCount)
        assertTrue(effectiveRequest.qualityDays.isEmpty())

        val generated = Engine15IndependentPlanGenerator.generateWithHistory(
            effectiveRequest,
            owner,
            emptyList()
        )
        Engine15IndependentPlanGenerator.validateSaved(generated)
        assertTrue(generated.sessions.none { it.quality })
    }

    @Test
    fun hardBlocksAndCorruptSafetyRowsFailClosedAcrossRunningAndNonRunningPlans() {
        val backend = InMemoryM1RecordBackend()
        val localStore = Engine15M1LocalStore(backend)
        val owner = "local-plan-33333333-4444-4555-8666-777777777777"

        localStore.saveRestriction(
            owner,
            AthleteSafetyRestriction(
                restrictionId = "medical-hold-all",
                accountId = owner,
                kind = SafetyRestrictionKind.MEDICAL_OR_REST_OVERRIDE,
                disciplines = setOf(AthleteDiscipline.RUN, AthleteDiscipline.STRENGTH),
                severity = SafetyRestrictionSeverity.HARD_BLOCK,
                provenance = SafetyRestrictionProvenance.MEDICAL_CLEARANCE_REQUIRED,
                createdAtUtc = Instant.parse("2026-11-24T08:00:00Z")
            ),
            evaluatedAt.toEpochMilli()
        )

        val hardBlockAssessment = Engine15UnifiedAthleteAssembler.assemble(
            UnifiedAthleteAssemblyInput(
                accountId = owner,
                evaluatedAtUtc = evaluatedAt,
                zoneId = zone,
                localStore = localStore
            )
        )

        val runRequest = PlanRequest(
            goal = "5K",
            experience = 1,
            start = LocalDate.of(2026, 11, 30),
            weeks = 4,
            minutes = 45,
            days = setOf(1, 3, 5),
            runDays = 3,
            longDay = 5,
            weeklyKm = 20.0,
            longestKm = 8.0,
            planningPace = 360.0,
            policyChoice = "engine15"
        )
        val blockedRunRequest = Engine15UnifiedAthleteAssembler.applySafetyAndAssessmentToRequest(
            runRequest,
            hardBlockAssessment
        )
        assertTrue(blockedRunRequest.runningRecoveryHold)
        val decision = Engine15IndependentPlanGenerator.previewAutoReconciled(
            blockedRunRequest,
            owner,
            "plan-blocked",
            evaluatedAt
        )
        assertTrue(decision is Engine15IndependentPlanDecision.Deferred)
        assertEquals("RUNNING_RECOVERY_HOLD", (decision as Engine15IndependentPlanDecision.Deferred).reasonCode)

        val strengthRequest = PlanRequest(
            goal = "Max strength",
            start = LocalDate.of(2026, 11, 30),
            weeks = 4,
            days = setOf(1, 4),
            runDays = 0,
            strengthDays = 2,
            policyChoice = "engine15"
        )
        assertThrows(PlanConstraint::class.java) {
            Engine15UnifiedAthleteAssembler.requireRequestedDisciplinesUnblocked(
                strengthRequest,
                hardBlockAssessment
            )
        }

        // Corrupt safety row in another account fails closed without throwing or deleting row.
        val corruptOwner = "local-plan-44444444-5555-4666-8777-888888888888"
        backend.injectRawForTest(corruptOwner, "${Engine15M1LocalStore.RESTRICTION_PREFIX}bad", "{corrupt")
        val corruptAssessment = Engine15UnifiedAthleteAssembler.assemble(
            UnifiedAthleteAssemblyInput(
                accountId = corruptOwner,
                evaluatedAtUtc = evaluatedAt,
                zoneId = zone,
                localStore = Engine15M1LocalStore(backend)
            )
        )
        assertTrue(corruptAssessment.restrictionsFailClosed)
        assertTrue(
            Engine15UnifiedAthleteAssembler.applySafetyAndAssessmentToRequest(
                runRequest,
                corruptAssessment
            ).runningRecoveryHold
        )
    }

    @Test
    fun confirmedZeroAndOfflineAssessmentRecoveryNeverSynthesizeBaselinesOrZones() {
        val backend = InMemoryM1RecordBackend()
        val storeBeforeRestart = Engine15M1LocalStore(backend)
        val owner = storeBeforeRestart.resolveOwner(
            account = null,
            localPlanOwner = "local-plan-55555555-6666-4777-8888-999999999999"
        )

        storeBeforeRestart.saveRestriction(
            owner,
            AthleteSafetyRestriction(
                restrictionId = "advisory-knee",
                accountId = owner,
                kind = SafetyRestrictionKind.INJURY_OR_PAIN,
                disciplines = setOf(AthleteDiscipline.RUN),
                severity = SafetyRestrictionSeverity.ADVISORY_CAUTION,
                createdAtUtc = Instant.parse("2026-11-25T08:00:00Z")
            ),
            evaluatedAt.toEpochMilli()
        )
        // Corrupt derived assessment cache; restriction must stay intact and cache must rebuild cleanly.
        backend.injectRawForTest(owner, Engine15M1LocalStore.assessmentRecordId(owner), "{broken-cache")

        val storeAfterRestart = Engine15M1LocalStore(backend)
        val assessment = Engine15UnifiedAthleteAssembler.assemble(
            UnifiedAthleteAssemblyInput(
                accountId = owner,
                evaluatedAtUtc = evaluatedAt,
                zoneId = zone,
                hrZoneProfile = null,
                ageYears = 30,
                localStore = storeAfterRestart,
                persistDerivedAssessment = true
            )
        )

        assertTrue(storeAfterRestart.loadDerivedAssessment(owner) is M1AssessmentLoadResult.Valid)
        assertEquals(setOf("advisory-knee"), assessment.activeRestrictions.activeRestrictionIdsAt(evaluatedAt))
        assertFalse(assessment.intensityCalibration.heartRateHierarchy.coachingAuthorized)
        assertTrue(
            assessment.intensityCalibration.heartRateHierarchy
                .unambiguousEvidence(M2BHeartRateEvidenceKind.IMPORTED_ZONES).isEmpty()
        )
        assertTrue(
            assessment.intensityCalibration.heartRateHierarchy
                .unambiguousEvidence(M2BHeartRateEvidenceKind.CONFIGURED_ZONES).isEmpty()
        )

        val zeroBeginnerRequest = PlanRequest(
            goal = "5K",
            experience = 0,
            start = LocalDate.of(2026, 11, 30),
            weeks = 4,
            minutes = 35,
            days = setOf(1, 3, 5),
            runDays = 3,
            longDay = 5,
            weeklyKm = 0.0,
            longestKm = 0.0,
            recentMileageBasis = "CONFIRMED_ZERO",
            policyChoice = "engine15"
        )
        val effective = Engine15UnifiedAthleteAssembler.applySafetyAndAssessmentToRequest(
            zeroBeginnerRequest,
            assessment
        )
        assertFalse(effective.runningRecoveryHold)
        assertEquals(0.0, effective.weeklyKm, 1e-6)
        assertTrue(Engine15TimeOnlyRunWalkBuilder.isEligible(effective))
    }

    private fun assertIsEstimatedMeters(belief: BeliefState<Meters>): Meters {
        assertTrue("Expected Estimated belief but got $belief", belief is BeliefState.Estimated)
        return (belief as BeliefState.Estimated).estimate
    }
}

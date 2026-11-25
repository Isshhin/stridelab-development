package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Complete replacement JUnit 4 suite for M2-A Performance Calibration (Corrective Pass R2).
 * Covers:
 *  - Original 18 core scenarios & Track 1A fixtures (verified F04, F05, F07, F09)
 *  - DEF-01 through DEF-14 regression verification
 *  - All 9 strict independent-review adversarial tests (preserving unweakened assertions;
 *    see M2-A-KNOWN-LIMITATIONS.md for M2A-R2-DEF-01..M2A-R2-DEF-09 exposed against R2 Section 1).
 */
class Engine15M2APerformanceCalibrationTest {

    private val accountA = "owner-m2a-alpha"
    private val accountB = "owner-m2a-beta"
    private val utc = ZoneOffset.UTC
    private val asOfDate = LocalDate.of(2026, 11, 25)
    private val asOfUtc = asOfDate.atTime(12, 0).toInstant(utc)

    private fun runObs(
        accountId: String = accountA,
        id: String,
        date: LocalDate,
        distanceM: Double,
        durationS: Double,
        source: String = "garmin",
        type: String = "running",
        hourUtc: Int = 7,
        avgHr: Double? = 152.0,
        maxHr: Double? = 172.0,
        sha256: String? = null,
        corroborationToken: String? = null
    ): RawActivityObservation = RawActivityObservation(
        accountId = accountId,
        record = ActivityRecord(
            id = id,
            title = "Run $id",
            type = type,
            source = source,
            startedAt = date.atTime(hourUtc, 0).toInstant(utc),
            distanceM = distanceM,
            durationS = durationS,
            movingTimeS = durationS,
            recordedPace = if (distanceM > 0.0) durationS / (distanceM / 1000.0) else null,
            averageHr = avgHr,
            elevationM = 20.0,
            maximumHr = maxHr
        ),
        sourceIdentity = ImportedSourceIdentity(accountId, source, externalId = id, sha256 = sha256),
        corroborationToken = corroborationToken
    )

    private fun continuousObservedWorkload(
        accountId: String = accountA,
        weeklyKm: Double,
        sessionsIn28d: Int = 20,
        longestRunKm: Double? = null,
        selfReportWeeklyKm: Double? = null
    ): ObservedWorkloadState {
        val totalM = weeklyKm * 4.0 * 1000.0
        val raw = if (longestRunKm != null && sessionsIn28d >= 2) {
            val lrM = longestRunKm * 1000.0
            val remEachM = ((totalM - lrM) / (sessionsIn28d - 1).toDouble()).coerceAtLeast(1000.0)
            (1..sessionsIn28d).map { i ->
                val dayOffset = (28L - ((i - 1) * 28L / sessionsIn28d.toLong())).coerceIn(1L, 28L)
                val distM = if (i == sessionsIn28d) lrM else remEachM
                runObs(accountId, "cont-$accountId-$i", asOfDate.minusDays(dayOffset), distM, (distM / 1000.0) * 310.0)
            }
        } else {
            val perRunM = totalM / sessionsIn28d.toDouble()
            (1..sessionsIn28d).map { i ->
                val dayOffset = (28L - ((i - 1) * 28L / sessionsIn28d.toLong())).coerceIn(1L, 28L)
                runObs(accountId, "cont-$accountId-$i", asOfDate.minusDays(dayOffset), perRunM, (perRunM / 1000.0) * 310.0)
            }
        }
        val canonical = Engine15CanonicalActivityProjector.project(raw).activities
        val srMap = if (selfReportWeeklyKm != null) {
            mapOf(
                AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(
                    discipline = AthleteDiscipline.RUN,
                    basis = RecentMileageBasis.SELF_REPORTED_AVERAGE,
                    weeklyVolume = Meters.fromKilometers(selfReportWeeklyKm)
                )
            )
        } else emptyMap()
        return Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountId,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = canonical,
            provisionalSelfReport = srMap,
            asOfUtc = asOfUtc
        )
    }

    private fun raceInput(
        id: String,
        daysAgo: Long?,
        distanceM: Double,
        durationS: Double,
        authority: EvidenceAuthorityClass = EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED,
        intent: M2APerformanceIntent = if (authority == EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL) {
            M2APerformanceIntent.SELF_REPORTED_RACE_OR_TIME_TRIAL
        } else {
            M2APerformanceIntent.VERIFIED_OFFICIAL_RACE
        },
        accountId: String = accountA,
        discipline: AthleteDiscipline = AthleteDiscipline.RUN,
        sourceProvider: String = "garmin",
        canonicalActivityId: String? = null,
        sourceQualifiedAliases: Set<M2ASourceQualifiedAlias> = emptySet(),
        corroborationToken: String? = null,
        sha256: String? = null,
        fitnessEpochId: String? = null,
        calibrationEligibility: CalibrationEligibility = CalibrationEligibility.ELIGIBLE_OBSERVED,
        courseDifficulty: VickersCourseDifficulty = VickersCourseDifficulty.AVERAGE,
        isProjected: Boolean = false
    ): M2ARacePerformanceInput = M2ARacePerformanceInput(
        performanceId = id,
        accountId = accountId,
        discipline = discipline,
        performedOn = daysAgo?.let { asOfDate.minusDays(it) },
        distance = Meters.of(distanceM),
        duration = Seconds.of(durationS),
        authority = authority,
        performanceIntent = intent,
        sourceProvider = sourceProvider,
        canonicalActivityId = canonicalActivityId,
        sourceQualifiedAliases = sourceQualifiedAliases,
        corroborationToken = corroborationToken,
        sha256 = sha256,
        fitnessEpochId = fitnessEpochId,
        calibrationEligibility = calibrationEligibility,
        courseDifficulty = courseDifficulty,
        observedAtUtc = daysAgo?.let { asOfDate.minusDays(it).atTime(8, 0).toInstant(utc) },
        isProjected = isProjected
    )

    // =========================================================================
    // PART A: CORE SCENARIOS, TRACK 1A FIXTURES (F04/F05/F07/F09) & DEF-01..14
    // =========================================================================

    @Test fun core01_confirmedZeroBeginnerFallsBackToEffortFirst() {
        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountA, asOfDate, utc, emptyList(),
            provisionalSelfReport = mapOf(
                AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(
                    AthleteDiscipline.RUN, RecentMileageBasis.CONFIRMED_ZERO, confirmedZero = true
                )
            ),
            asOfUtc = asOfUtc
        )
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, emptyList(), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.EFFORT_AND_TALK_TEST_FALLBACK, res.authorityMode)
        assertFalse(res.authorization.hasVerifiedPerformanceReference)
        assertFalse(res.authorization.eligibleToParticipateInPaceCalibration)
        assertFalse(res.authorization.provisionalPaceGuidanceAvailable)
        assertFalse(res.authorization.workoutSpecificPacePrescriptionAuthorized)
        assertFalse(res.authorization.hrTrainingZoneAuthorized)
        assertEquals(M2AEnduranceMileageAssessment.ConfirmedZero, res.enduranceMileageAssessment)
        assertTrue(res.racePredictions.values.all { it is M2ARacePredictionOutcome.Abstained })
    }

    @Test fun core02_selfReportedOnlyPerformanceProvidesProvisionalGuidanceWithoutVerifiedAuthority_def03() {
        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountA, asOfDate, utc, emptyList(),
            provisionalSelfReport = mapOf(
                AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(
                    AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE,
                    weeklyVolume = Meters.fromKilometers(50.0)
                )
            ),
            asOfUtc = asOfUtc
        )
        val sr5k = raceInput(
            id = "sr-5k", daysAgo = 45, distanceM = 5_000.0, durationS = 1_260.0,
            authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, sourceProvider = "manual"
        )
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(sr5k), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.PROVISIONAL_SELF_REPORT_BENCHMARK, res.authorityMode)
        assertFalse(res.authorization.hasVerifiedPerformanceReference)
        assertFalse(res.authorization.eligibleToParticipateInPaceCalibration)
        assertTrue(res.authorization.provisionalPaceGuidanceAvailable)
        assertFalse(res.authorization.workoutSpecificPacePrescriptionAuthorized)
        assertFalse(res.authorization.hrTrainingZoneAuthorized)
        assertEquals(1.35, res.operationalThresholdGuidanceBandFactor!!, 1e-9)

        val mar = res.racePredictions.getValue(M2ATargetRaceDistance.MARATHON) as M2ARacePredictionOutcome.Predicted
        assertTrue(mar.isProvisional)
        assertTrue(mar.isConditionalOnProvisionalMileage)
        assertFalse(mar.certifiesRaceReadiness)
        assertFalse(mar.authorizesRaceSpecificTrainingPace)
        assertTrue(mar.downstreamPrerequisitesRequired.contains("M4_MARATHON_READINESS_ASSESSMENT_REQUIRED"))
    }

    @Test fun core03_verifiedRecent5kComputesExactMod03Mod04Mod06AndNonReadinessFlags_def09Def12() {
        val observed = continuousObservedWorkload(weeklyKm = 64.37376) // 40.0 mi/wk
        val r5k = raceInput("ver-5k", daysAgo = 20, distanceM = 5_000.0, durationS = 1_200.0)
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(r5k), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK, res.authorityMode)
        assertTrue(res.authorization.hasVerifiedPerformanceReference)
        assertTrue(res.authorization.eligibleToParticipateInPaceCalibration)
        assertFalse(res.authorization.workoutSpecificPacePrescriptionAuthorized)
        assertFalse(res.authorization.hrTrainingZoneAuthorized)

        val thr = res.operationalThresholdSpeedBelief as BeliefState.Estimated
        assertEquals(3.904529, thr.estimate.value, 1e-4)

        val p10k = res.racePredictions.getValue(M2ATargetRaceDistance.RACE_10K) as M2ARacePredictionOutcome.Predicted
        assertEquals("MOD-03", p10k.modelId)
        assertEquals(2_519.32, p10k.predictedDuration.value, 0.25)
        assertEquals(p10k.predictedSpeed.value, 10_000.0 / p10k.predictedDuration.value, 1e-6)

        val pMar = res.racePredictions.getValue(M2ATargetRaceDistance.MARATHON) as M2ARacePredictionOutcome.Predicted
        assertEquals("MOD-04", pMar.modelId)
        assertFalse(pMar.certifiesRaceReadiness)
        assertFalse(pMar.authorizesRaceSpecificTrainingPace)
        assertFalse(pMar.isConditionalOnProvisionalMileage)
    }

    @Test fun core04_exactTrack1aF04Fixture_72kmWk24kmLongRun10k41Min_def14() {
        // Faithful Track 1A-F04 fixture: 20 runs, 72 km/wk, D_max_30 = 24 km, verified 10K 41:00 (2,460 s) 45d ago
        val observed = continuousObservedWorkload(weeklyKm = 72.0, sessionsIn28d = 20, longestRunKm = 24.0)
        assertEquals(24_000.0, (observed.observedLongestRunMeters30d() as BeliefState.Estimated).estimate.value, 1.0)
        val f04Race = raceInput("f04-10k-41min", daysAgo = 45, distanceM = 10_000.0, durationS = 2_460.0)

        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(f04Race), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK, res.authorityMode)
        val endur = res.enduranceMileageAssessment as M2AEnduranceMileageAssessment.VerifiedContinuousObserved
        assertEquals(72_000.0, endur.typicalWeeklyMeters.value, 1.0)

        val thr = res.operationalThresholdSpeedBelief as BeliefState.Estimated
        // 10K 41:00 (4.065041 m/s) -> 3240 s ref: 3.992454 m/s (4:10/km)
        assertEquals(3.992454, thr.estimate.value, 1e-4)

        val p5k = res.racePredictions.getValue(M2ATargetRaceDistance.RACE_5K) as M2ARacePredictionOutcome.Predicted
        assertEquals(1_171.74, p5k.predictedDuration.value, 0.25)

        val pMar = res.racePredictions.getValue(M2ATargetRaceDistance.MARATHON) as M2ARacePredictionOutcome.Predicted
        assertEquals("MOD-04", pMar.modelId)
        assertEquals(12_052.6, pMar.predictedDuration.value, 1.5)
    }

    @Test fun core05_track1aF05StaleBenchmarkAndTrack1aF07HighMileageEasyOnly() {
        val obsF05 = continuousObservedWorkload(weeklyKm = 35.0)
        val stale5k = raceInput("f05-stale", daysAgo = 240, distanceM = 5_000.0, durationS = 1_200.0)
        val resF05 = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, obsF05, listOf(stale5k), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.EFFORT_AND_TALK_TEST_FALLBACK, resF05.authorityMode)
        assertTrue(resF05.operationalThresholdSpeedBelief is BeliefState.Stale)
        assertEquals(240, (resF05.operationalThresholdSpeedBelief as BeliefState.Stale).ageDays)

        val obsF07 = continuousObservedWorkload(weeklyKm = 65.0, sessionsIn28d = 20, longestRunKm = 22.0)
        val resF07 = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, obsF07, emptyList(), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.EFFORT_AND_TALK_TEST_FALLBACK, resF07.authorityMode)
        assertTrue(resF07.operationalThresholdSpeedBelief is BeliefState.Unknown)
        assertTrue(resF07.enduranceMileageAssessment is M2AEnduranceMileageAssessment.VerifiedContinuousObserved)
    }

    @Test fun core06_track1aF09ConflictingMileageProducesConservativeCeilingAndAbstainsMarathon_def05() {
        // Track 1A-F09: 26 km/wk continuous 28d vs 65 km/wk self-report (ratio 0.40 < 0.65)
        // DEF-05: Conservative planning ceiling (31 km/wk) is NOT a typical mileage point estimate -> Marathon MUST abstain!
        val observed = continuousObservedWorkload(weeklyKm = 26.0, sessionsIn28d = 12, selfReportWeeklyKm = 65.0)
        val r10k = raceInput("f09-10k", daysAgo = 25, distanceM = 10_000.0, durationS = 2_880.0)
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(r10k), asOfUtc)
        )
        val endur = res.enduranceMileageAssessment as M2AEnduranceMileageAssessment.ConflictConservativeCeiling
        assertEquals(31_000.0, endur.conservativePlanningCeilingMeters.value, 1.0)
        assertEquals(26_000.0, endur.observedContinuousWeeklyMeters.value, 1.0)
        assertEquals(65_000.0, endur.selfReportedWeeklyMeters.value, 1.0)
        assertTrue(res.diagnosticFlags.contains("WEEKLY_MILEAGE_CONFLICT_DETECTED"))

        val mar = res.racePredictions.getValue(M2ATargetRaceDistance.MARATHON) as M2ARacePredictionOutcome.Abstained
        assertEquals("WEEKLY_MILEAGE_CONFLICT_BLOCKS_MARATHON_PREDICTION", mar.reasonCode)
        // Short/mid-distance & operational threshold still function cleanly from valid 10K!
        assertTrue(res.operationalThresholdSpeedBelief is BeliefState.Estimated)
        assertTrue(res.racePredictions.getValue(M2ATargetRaceDistance.RACE_5K) is M2ARacePredictionOutcome.Predicted)
    }

    @Test fun core07_ordinaryTrainingRunsNeverBecomeMaximalRaceBenchmarks_def13() {
        val observed = continuousObservedWorkload(weeklyKm = 60.0)
        val easyRun = raceInput(
            id = "ordinary-easy", daysAgo = 7, distanceM = 10_000.0, durationS = 2_800.0,
            authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
            intent = M2APerformanceIntent.ORDINARY_TRAINING_RUN
        )
        val intervalRun = raceInput(
            id = "interval-sess", daysAgo = 5, distanceM = 5_000.0, durationS = 1_150.0,
            authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
            intent = M2APerformanceIntent.STRUCTURED_INTERVAL_WORKOUT
        )
        val unknownIntent = raceInput(
            id = "unknown-intent", daysAgo = 3, distanceM = 10_000.0, durationS = 2_600.0,
            authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
            intent = M2APerformanceIntent.UNKNOWN_INTENT
        )
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(easyRun, intervalRun, unknownIntent), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.EFFORT_AND_TALK_TEST_FALLBACK, res.authorityMode)
        assertTrue(res.operationalThresholdSpeedBelief is BeliefState.Unknown)
        assertTrue(res.diagnosticFlags.contains("NON_MAXIMAL_TRAINING_ACTIVITY_EXCLUDED"))

        // Adapter also rejects ORDINARY_TRAINING_RUN cleanly
        val canAct = Engine15CanonicalActivityProjector.project(
            listOf(runObs(id = "train-10k", date = asOfDate.minusDays(5), distanceM = 10_000.0, durationS = 2_800.0))
        ).activities.single()
        assertNull(
            M2ARacePerformanceInput.fromVerifiedMaximalCanonicalActivity(
                canAct, utc, M2APerformanceIntent.ORDINARY_TRAINING_RUN
            )
        )
        val validTt = M2ARacePerformanceInput.fromVerifiedMaximalCanonicalActivity(
            canAct, utc, M2APerformanceIntent.VERIFIED_TIME_TRIAL
        )
        assertNotNull(validTt)
        assertEquals(EvidenceAuthorityClass.SENSOR_SUPPORTED, validTt!!.authority)
    }

    @Test fun core08_sameDayDistinctRacesWithoutCorroborationAreNotCollapsed_def01() {
        val observed = continuousObservedWorkload(weeklyKm = 55.0)
        val heat1 = raceInput(
            id = "heat-1", daysAgo = 15, distanceM = 5_000.0, durationS = 1_200.0,
            authority = EvidenceAuthorityClass.SENSOR_SUPPORTED, canonicalActivityId = "can-heat-1"
        )
        val heat2 = raceInput(
            id = "heat-2", daysAgo = 15, distanceM = 5_000.0, durationS = 1_240.0,
            authority = EvidenceAuthorityClass.SENSOR_SUPPORTED, canonicalActivityId = "can-heat-2"
        )
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(heat1, heat2), asOfUtc)
        )
        assertFalse(res.diagnosticFlags.contains("DUPLICATE_RACE_PERFORMANCE_DEDUPLICATED"))
        assertEquals(setOf("heat-1", "can-heat-1"), res.operationalThresholdBenchmark!!.mergedEvidenceIds)
    }

    @Test fun core09_transitiveIdentityDeduplicationAndProviderScopedIdCollision_def01() {
        val observed = continuousObservedWorkload(weeklyKm = 60.0)
        // Transitive chain A~B (shared sha) and B~C (shared token) collapses regardless of order
        val a = raceInput("id-a", 20, 10_000.0, 2_700.0, EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED, sourceProvider = "fit", sha256 = "f".repeat(64))
        val b = raceInput("id-b", 20, 10_020.0, 2_702.0, EvidenceAuthorityClass.SENSOR_SUPPORTED, sourceProvider = "garmin", sha256 = "f".repeat(64), corroborationToken = "tok-1")
        val c = raceInput("id-c", 20, 9_980.0, 2_705.0, EvidenceAuthorityClass.SENSOR_SUPPORTED, sourceProvider = "health_connect", corroborationToken = "tok-1")
        val res1 = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(a, b, c), asOfUtc)
        )
        val res2 = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(c, a, b), asOfUtc)
        )
        assertEquals(res1.contentHash, res2.contentHash)
        assertTrue(res1.diagnosticFlags.contains("DUPLICATE_RACE_PERFORMANCE_DEDUPLICATED"))
        assertTrue(res1.operationalThresholdBenchmark!!.mergedEvidenceIds.containsAll(setOf("id-a", "id-b", "id-c")))

        // Contradictory source-identifier collision: same (provider=garmin, externalId=dup-99) on two different days/distances!
        val col1 = raceInput("dup-99", 10, 5_000.0, 1_200.0, sourceProvider = "garmin")
        val col2 = raceInput("dup-99", 12, 10_000.0, 2_700.0, sourceProvider = "garmin")
        val resCol = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(col1, col2), asOfUtc)
        )
        assertTrue(resCol.diagnosticFlags.contains("SOURCE_IDENTIFIER_COLLISION_EXCLUDED"))
        assertTrue(resCol.operationalThresholdSpeedBelief is BeliefState.Conflicting)
    }

    @Test fun core10_historicalImprovementAndEnduranceDropOffDoNotCollapseAllCalibration_def02Def11() {
        val observed = continuousObservedWorkload(weeklyKm = 64.37376) // 40 mi/wk
        // 1. Historical improvement: 120d ago 50:00 10K -> 10d ago 20:00 5K (different epochs)
        val old10k = raceInput("old-10k", daysAgo = 120, distanceM = 10_000.0, durationS = 3_000.0)
        val new5k = raceInput("new-5k", daysAgo = 10, distanceM = 5_000.0, durationS = 1_200.0)
        val resImp = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(old10k, new5k), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK, resImp.authorityMode)
        assertTrue(resImp.operationalThresholdSpeedBelief is BeliefState.Estimated)
        assertEquals("new-5k", resImp.operationalThresholdBenchmark!!.representativeId)
        assertTrue(resImp.diagnosticFlags.contains("MOD05_SKIPPED_TEMPORALLY_INCOMPATIBLE_PAIR"))

        // 2. DEF-11: Strong 5K (19:00 = 1140s) + Weak HM (1:50:00 = 6600s) in SAME epoch -> k_between = 1.219 > 1.20!
        // Valid endurance drop-off must abstain MOD-05 ONLY, preserving MOD-06 operational threshold & MOD-04 longest-anchor fallback!
        val fast5k = raceInput("sprinter-5k", daysAgo = 14, distanceM = 5_000.0, durationS = 1_140.0, fitnessEpochId = "ep-1")
        val weakHm = raceInput("sprinter-hm", daysAgo = 21, distanceM = 21_097.5, durationS = 6_600.0, fitnessEpochId = "ep-1")
        val resEndur = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(fast5k, weakHm), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK, resEndur.authorityMode)
        assertTrue(resEndur.operationalThresholdSpeedBelief is BeliefState.Estimated)
        assertTrue(resEndur.diagnosticFlags.contains("MOD05_ABSTAINED_ENDURANCE_PROFILE_OUTSIDE_POL016"))
        val mar = resEndur.racePredictions.getValue(M2ATargetRaceDistance.MARATHON) as M2ARacePredictionOutcome.Predicted
        assertEquals("MOD-04", mar.modelId)
        assertEquals("sprinter-hm", resEndur.marathonPrimaryBenchmark!!.representativeId)
        assertTrue(mar.caveats.contains("OBSERVED_STEEP_ENDURANCE_DROP_TWO_RACE_MODEL_ABSTAINED"))
    }

    @Test fun core11_modelSpecificAnchorSelectionAndKinematic4950mNormalization_def06Def07() {
        val observed = continuousObservedWorkload(weeklyKm = 60.0)
        // DEF-06: Fresh 3K (600s < 720s POL-020 min) 5d ago + Fresh 10K (2400s) 30d ago
        val fast3k = raceInput("short-3k", daysAgo = 5, distanceM = 3_000.0, durationS = 600.0)
        val valid10k = raceInput("valid-10k", daysAgo = 30, distanceM = 10_000.0, durationS = 2_400.0)
        val resAnchor = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(fast3k, valid10k), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK, resAnchor.authorityMode)
        assertEquals("valid-10k", resAnchor.operationalThresholdBenchmark!!.representativeId)
        // 5K prediction can still utilize the closer/fresher 3K via MOD-03 (5000/3000 = 1.67 <= 4.25)!
        val p5k = resAnchor.racePredictions.getValue(M2ATargetRaceDistance.RACE_5K) as M2ARacePredictionOutcome.Predicted
        assertEquals("MOD-03", p5k.modelId)

        // DEF-07: GPS-recorded 4,950 m in 1,188 s (4.1667 m/s) evaluated against exact 5,000 m -> normalized via MOD-03!
        val gps4950 = raceInput("gps-4950", daysAgo = 14, distanceM = 4_950.0, durationS = 1_188.0)
        val res4950 = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(gps4950), asOfUtc)
        )
        val pred5k = res4950.racePredictions.getValue(M2ATargetRaceDistance.RACE_5K) as M2ARacePredictionOutcome.Predicted
        assertEquals("MOD-03", pred5k.modelId)
        assertEquals(1_200.84, pred5k.predictedDuration.value, 0.15)
        assertEquals(pred5k.predictedSpeed.value, 5_000.0 / pred5k.predictedDuration.value, 1e-6)
        assertTrue(pred5k.caveats.contains("NEAR_STANDARD_DISTANCE_NORMALIZED_VIA_MOD03"))
    }

    @Test fun core12_partialObservedLowerBoundAbstainsMarathonWithoutMislabelingSelfReport_def05() {
        val partialRaw = listOf(
            runObs(id = "p1", date = asOfDate.minusDays(18), distanceM = 30_000.0, durationS = 9_000.0),
            runObs(id = "p2", date = asOfDate.minusDays(15), distanceM = 30_000.0, durationS = 9_000.0),
            runObs(id = "p3", date = asOfDate.minusDays(12), distanceM = 30_000.0, durationS = 9_000.0),
            runObs(id = "p4", date = asOfDate.minusDays(9),  distanceM = 30_000.0, durationS = 9_000.0),
            runObs(id = "p5", date = asOfDate.minusDays(5),  distanceM = 30_000.0, durationS = 9_000.0),
            runObs(id = "p6", date = asOfDate.minusDays(2),  distanceM = 30_000.0, durationS = 9_000.0)
        )
        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountA, asOfDate, utc,
            Engine15CanonicalActivityProjector.project(partialRaw).activities,
            provisionalSelfReport = mapOf(
                AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(
                    AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE,
                    weeklyVolume = Meters.fromKilometers(40.0)
                )
            ),
            asOfUtc = asOfUtc
        )
        val r10k = raceInput("p-10k", daysAgo = 10, distanceM = 10_000.0, durationS = 2_600.0)
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(r10k), asOfUtc)
        )
        val endur = res.enduranceMileageAssessment as M2AEnduranceMileageAssessment.PartialObservedLowerBound
        assertEquals(45_000.0, endur.observed28dLowerBoundWeeklyMeters.value, 1.0)
        val mar = res.racePredictions.getValue(M2ATargetRaceDistance.MARATHON) as M2ARacePredictionOutcome.Abstained
        assertEquals("PARTIAL_OBSERVED_LOWER_BOUND_NOT_TYPICAL_MILEAGE", mar.reasonCode)
    }

    @Test fun core13_deepImmutabilityAcrossConstructorInputsReturnedCollectionsAndStructuralEquality_def08() {
        val observed = continuousObservedWorkload(weeklyKm = 64.37376)
        val callerAliases = mutableSetOf(M2ASourceQualifiedAlias("garmin", "ext-immut"))
        val r5k = M2ARacePerformanceInput(
            performanceId = "immut-5k", accountId = accountA, discipline = AthleteDiscipline.RUN,
            performedOn = asOfDate.minusDays(15), distance = Meters.of(5_000.0), duration = Seconds.of(1_200.0),
            authority = EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED,
            performanceIntent = M2APerformanceIntent.VERIFIED_OFFICIAL_RACE,
            sourceProvider = "garmin", sourceQualifiedAliases = callerAliases
        )
        callerAliases.add(M2ASourceQualifiedAlias("garmin", "mutated-after-construct"))
        assertEquals(2, r5k.sourceQualifiedAliases.size) // base + ext-immut only!

        val resA = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(r5k), asOfUtc)
        )
        val resB = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(r5k), asOfUtc)
        )
        val hashBefore = resA.contentHash
        assertEquals(resA, resB)
        assertEquals(resA.hashCode(), resB.hashCode())
        assertEquals(resA.operationalThresholdBenchmark, resB.operationalThresholdBenchmark)
        assertEquals(resA.racePredictions.getValue(M2ATargetRaceDistance.RACE_10K), resB.racePredictions.getValue(M2ATargetRaceDistance.RACE_10K))

        val pred10k = resA.racePredictions.getValue(M2ATargetRaceDistance.RACE_10K) as M2ARacePredictionOutcome.Predicted
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (resA.operationalThresholdBenchmark!!.mergedEvidenceIds as MutableSet<String>).add("TAMPER")
        }
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (pred10k.evidenceIds as MutableSet<String>).add("TAMPER")
        }
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (pred10k.caveats as MutableList<String>).add("TAMPER")
        }
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (pred10k.downstreamPrerequisitesRequired as MutableSet<String>).add("TAMPER")
        }
        val modelRes = Engine15M2AOperationalThresholdModel.estimate(Meters.of(10_000.0), Seconds.of(2_700.0)) as M2AModelResult.Computed
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (modelRes.notes as MutableList<String>).add("TAMPER")
        }
        assertEquals(hashBefore, resA.contentHash)
    }

    @Test fun core14_undatedLegacyFutureProjectedForeignAndBoundaryNumericalInputs_def09Def14() {
        val observed = continuousObservedWorkload(weeklyKm = 60.0)
        val undated = raceInput("legacy-undated", daysAgo = null, distanceM = 5_000.0, durationS = 1_200.0)
        val future = raceInput("future-race", daysAgo = -3, distanceM = 5_000.0, durationS = 1_200.0)
        val proj = raceInput("proj-race", daysAgo = 5, distanceM = 5_000.0, durationS = 1_100.0, authority = EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE, isProjected = true)
        val foreign = raceInput("foreign-race", daysAgo = 5, distanceM = 5_000.0, durationS = 1_100.0, accountId = accountB)
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(undated, future, proj, foreign), asOfUtc)
        )
        assertEquals(M2ACalibrationAuthorityMode.EFFORT_AND_TALK_TEST_FALLBACK, res.authorityMode)
        assertTrue(res.diagnosticFlags.contains("UNDATED_LEGACY_BENCHMARK_EXCLUDED"))
        assertTrue(res.diagnosticFlags.contains("FUTURE_DATED_PERFORMANCE_EXCLUDED"))
        assertTrue(res.diagnosticFlags.contains("PROJECTED_PERFORMANCE_EXCLUDED"))
        assertTrue(res.diagnosticFlags.contains("FOREIGN_ACCOUNT_PERFORMANCE_EXCLUDED"))

        // Exact MOD-04 / MOD-05 dimensional types (DEF-09) & exact boundary values (15 mi/wk, 75 mi/wk, 720s, 7200s)
        val vv1 = Engine15M2AVickersModel1.predictMarathon(
            Meters.of(21_097.5), Seconds.of(5_946.0), Meters.of(40.0 * 1_609.344)
        ) as M2AModelResult.Computed
        assertEquals("m/s", vv1.value.riegelBaseMarathonSpeed.canonicalTypeTag)
        assertEquals(3.380134, vv1.value.riegelBaseMarathonSpeed.value, 1e-4)
        assertEquals(40.0, vv1.value.modelInputWeeklyMilesPerWeek, 1e-6)

        val vv2 = Engine15M2AVickersModel2.predictMarathon(
            Meters.of(10_000.0), Seconds.of(3_000.0),
            Meters.of(21_097.5), Seconds.of(6_600.0),
            Meters.of(40.0 * 1_609.344)
        ) as M2AModelResult.Computed
        assertEquals("1", vv2.value.empiricalKBetween.canonicalTypeTag)
        assertEquals("1", vv2.value.predictedKMarathon.canonicalTypeTag)
        assertEquals(1.0561, vv2.value.empiricalKBetween.value, 1e-3)
        assertEquals(1.1433, vv2.value.predictedKMarathon.value, 1e-3)

        // Exact POL-020 boundaries: 720.0 s and 7,200.0 s pass; 719.9 s and 7,200.1 s abstain!
        assertTrue(Engine15M2AOperationalThresholdModel.estimate(Meters.of(3_000.0), Seconds.of(720.0)) is M2AModelResult.Computed)
        assertTrue(Engine15M2AOperationalThresholdModel.estimate(Meters.of(21_097.5), Seconds.of(7_200.0)) is M2AModelResult.Computed)
        assertTrue(Engine15M2AOperationalThresholdModel.estimate(Meters.of(3_000.0), Seconds.of(719.9)) is M2AModelResult.Abstained)
        assertTrue(Engine15M2AOperationalThresholdModel.estimate(Meters.of(21_097.5), Seconds.of(7_200.1)) is M2AModelResult.Abstained)

        // Prediction-specific provenance on Fresh primary + Aging secondary (DEF-04 + DEF-02 reconciliation)
        val fresh5k = raceInput("f-5k", daysAgo = 15, distanceM = 5_000.0, durationS = 1_200.0)
        val aging10k = raceInput("a-10k", daysAgo = 140, distanceM = 10_000.0, durationS = 2_520.0)
        val resMixed = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, continuousObservedWorkload(weeklyKm = 64.37376), listOf(fresh5k, aging10k), asOfUtc)
        )
        val p10k = resMixed.racePredictions.getValue(M2ATargetRaceDistance.RACE_10K) as M2ARacePredictionOutcome.Predicted
        assertEquals("DIRECT_ANCHOR", p10k.modelId)
        assertEquals(M2ABenchmarkFreshness.AGING, p10k.benchmarkFreshness)
        assertEquals(1.25, p10k.guidanceBandFactor, 1e-9)
        assertEquals(EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED, p10k.benchmarkAuthority)
        assertTrue(resMixed.diagnosticFlags.contains("MOD05_SKIPPED_TEMPORALLY_INCOMPATIBLE_PAIR"))
    }

    // =========================================================================
    // PART B: STRICT INDEPENDENT REVIEW ADVERSARIAL TESTS 1-9
    // (Assertions intentionally unweakened — expose M2A-R2-DEF-01..M2A-R2-DEF-09!)
    // =========================================================================

    @Test fun reviewAdversarial01_transitiveGroupPairwiseConsistencyMustCheckDiameter_exposesR2Def01() {
        val observed = continuousObservedWorkload(weeklyKm = 60.0)
        // A-B diff = 10.7% (<=15%), B-C diff = 11.1% (<=15%), BUT A-C diff = 20.6% (>15%!)
        val a = raceInput("tr-a", 15, 10_000.0, 2_700.0, sourceProvider = "fit",
            sourceQualifiedAliases = setOf(M2ASourceQualifiedAlias("garmin", "link-ab")))
        val b = raceInput("tr-b", 15, 11_200.0, 3_024.0, sourceProvider = "garmin",
            sourceQualifiedAliases = setOf(M2ASourceQualifiedAlias("garmin", "link-ab"), M2ASourceQualifiedAlias("health_connect", "link-bc")))
        val c = raceInput("tr-c", 15, 12_600.0, 3_402.0, sourceProvider = "health_connect",
            sourceQualifiedAliases = setOf(M2ASourceQualifiedAlias("health_connect", "link-bc")))

        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(a, b, c), asOfUtc)
        )
        // Strict expectation: A-C exceeds POL-002 15% group diameter -> MUST flag collision/inconsistency, NOT silently collapse!
        assertTrue(
            "M2A-R2-DEF-01: transitive chain A(10km)-B(11.2km)-C(12.6km) has 20.6% diameter and must trigger collision quarantine",
            res.diagnosticFlags.contains("SOURCE_IDENTIFIER_COLLISION_EXCLUDED")
        )
        assertFalse(res.authorization.eligibleToParticipateInPaceCalibration)
    }

    @Test fun reviewAdversarial02_sharedFitnessEpochIdMustNotBypassFreshnessOrMaxSeparation_exposesR2Def02() {
        val observed = continuousObservedWorkload(weeklyKm = 64.37376)
        // 130 days apart across FRESH (10d) and AGING (140d), yet erroneously given same fitnessEpochId!
        val fresh5k = raceInput("ep-5k", daysAgo = 10, distanceM = 5_000.0, durationS = 1_200.0, fitnessEpochId = "same-tag")
        val aging10k = raceInput("ep-10k", daysAgo = 140, distanceM = 10_000.0, durationS = 2_520.0, fitnessEpochId = "same-tag")

        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(
                accountA, asOfDate, utc, observed, listOf(fresh5k, aging10k), asOfUtc,
                temporalCompatibilityPolicy = M2ATemporalCompatibilityPolicy.MaxSeparationDays(45)
            )
        )
        val pMar = res.racePredictions.getValue(M2ATargetRaceDistance.MARATHON) as M2ARacePredictionOutcome.Predicted
        // Strict expectation: shared fitnessEpochId MUST NOT override a 130-day gap & FRESH-vs-AGING split to force MOD-05!
        assertNotEquals(
            "M2A-R2-DEF-02: shared fitnessEpochId must not bypass MaxSeparationDays(45) or FRESH/AGING incompatibility",
            "MOD-05",
            pMar.modelId
        )
    }

    @Test fun reviewAdversarial03_velocityInversionShouldRejectAffectedPairWithoutDestroyingValidIndividualReference_exposesR2Def03() {
        val observed = continuousObservedWorkload(weeklyKm = 64.37376)
        // Tactical/windy 5K 22:00 (3.788 m/s) + strong 10K 43:00 (3.876 m/s) in same epoch
        val slow5k = raceInput("tactical-5k", daysAgo = 20, distanceM = 5_000.0, durationS = 1_320.0, fitnessEpochId = "ep-inv")
        val strong10k = raceInput("strong-10k", daysAgo = 10, distanceM = 10_000.0, durationS = 2_580.0, fitnessEpochId = "ep-inv")

        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(slow5k, strong10k), asOfUtc)
        )
        // Strict expectation: reject MOD-05 pair cleanly while preserving valid individual reference (strong 10K) rather than wiping out all calibration!
        val pMar = res.racePredictions.getValue(M2ATargetRaceDistance.MARATHON)
        assertFalse(pMar is M2ARacePredictionOutcome.Predicted && pMar.modelId == "MOD-05")
        assertTrue(
            "M2A-R2-DEF-03: velocity inversion in a two-race pair must not discard valid individual operational threshold reference",
            res.operationalThresholdSpeedBelief is BeliefState.Estimated
        )
    }

    @Test fun reviewAdversarial04_identicalExternalIdsAcrossDifferentProvidersMustPreserveSourceQualification_exposesR2Def04() {
        val observed = continuousObservedWorkload(weeklyKm = 64.37376)
        // Same external ID "race-100" from TWO DIFFERENT providers ("garmin" 5K vs "intervals" 10K)
        val garmin5k = raceInput("race-100", daysAgo = 15, distanceM = 5_000.0, durationS = 1_200.0, sourceProvider = "garmin")
        val intervals10k = raceInput("race-100", daysAgo = 25, distanceM = 10_000.0, durationS = 2_520.0, sourceProvider = "intervals")

        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(garmin5k, intervals10k), asOfUtc)
        )
        // Strict expectation: contributing evidence IDs MUST remain source-qualified so garmin:race-100 and intervals:race-100 stay distinct!
        assertTrue(
            "M2A-R2-DEF-04: contributingEvidenceIds must retain provider qualification to distinguish garmin/race-100 from intervals/race-100",
            res.contributingEvidenceIds.any { it.contains("garmin") } &&
                res.contributingEvidenceIds.any { it.contains("intervals") }
        )
        assertTrue(
            "M2A-R2-DEF-04: representativeId must be source-qualified rather than bare external ID 'race-100'",
            res.operationalThresholdBenchmark!!.representativeId != "race-100"
        )
    }

    @Test fun reviewAdversarial05_manualUnverifiedActivityWithCallerCertifiedFlagMustNotAutoGainVerifiedAuthority_exposesR2Def05() {
        // Manual training log passed into adapter with caller-asserted officiallyCertifiedRace = true
        val manualCan = Engine15CanonicalActivityProjector.project(
            listOf(runObs(id = "manual-jog", date = asOfDate.minusDays(5), distanceM = 5_000.0, durationS = 1_200.0, source = "manual"))
        ).activities.single()

        val adapted = M2ARacePerformanceInput.fromVerifiedMaximalCanonicalActivity(
            activity = manualCan,
            zoneId = utc,
            performanceIntent = M2APerformanceIntent.VERIFIED_OFFICIAL_RACE,
            officiallyCertifiedRace = true
        )
        // Strict expectation: manual unverified source must NOT silently acquire COACH_OR_RACE_VERIFIED solely from a bare boolean!
        assertTrue(
            "M2A-R2-DEF-05: manual activity without certified provenance must not obtain COACH_OR_RACE_VERIFIED authority",
            adapted == null || adapted.authority != EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED
        )
    }

    @Test fun reviewAdversarial06_verifiedOutOfDomainBenchmarkAlongsideEligibleProvisionalBenchmark_exposesR2Def06() {
        val observed = continuousObservedWorkload(weeklyKm = 60.0)
        // Verified 3K in 600s (< 720s POL-020 min, < 5000m POL-015 min) + self-reported 10K in 40:00 (eligible for MOD-06 & MOD-04!)
        val ver3k = raceInput("ver-3k", daysAgo = 10, distanceM = 3_000.0, durationS = 600.0, authority = EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED)
        val prov10k = raceInput("prov-10k", daysAgo = 20, distanceM = 10_000.0, durationS = 2_400.0, authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, sourceProvider = "manual")

        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(ver3k, prov10k), asOfUtc)
        )
        // Strict expectation: verified 3K anchors 5K via MOD-03 while provisional 10K remains explicitly available as provisional guidance for MOD-06 / MOD-04 (never silently dropped globally!)
        val p5k = res.racePredictions.getValue(M2ATargetRaceDistance.RACE_5K) as M2ARacePredictionOutcome.Predicted
        assertEquals(EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED, p5k.benchmarkAuthority)
        assertTrue(
            "M2A-R2-DEF-06: eligible provisional 10K should supply provisional operational threshold guidance when verified 3K is outside POL-020",
            res.authorization.provisionalPaceGuidanceAvailable
        )
        assertFalse(
            "Provisional fallback must remain non-authoritative for verified zone participation",
            res.authorization.eligibleToParticipateInPaceCalibration
        )
    }

    @Test fun reviewAdversarial07_unverifiedCourseAdjustmentOverrideMustNotGrantNonProvisionalAuthority_exposesR2Def07() {
        val observed = continuousObservedWorkload(weeklyKm = 64.37376)
        val diffHm = raceInput(
            id = "diff-hm", daysAgo = 20, distanceM = 21_097.5, durationS = 5_946.0,
            courseDifficulty = VickersCourseDifficulty.DIFFICULT
        )
        // Default request abstains cleanly
        val resDefault = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(diffHm), asOfUtc)
        )
        val defaultMar = resDefault.racePredictions.getValue(M2ATargetRaceDistance.MARATHON) as M2ARacePredictionOutcome.Abstained
        assertEquals("COURSE_ADJUSTMENT_UNVERIFIED_AGAINST_PRIMARY_DOCX", defaultMar.reasonCode)

        // With override flag = true: must NEVER claim non-provisional production authority (isProvisional must be true + explicit unverified caveat!)
        val resOverride = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(
                accountA, asOfDate, utc, observed, listOf(diffHm), asOfUtc,
                allowUnverifiedSecondaryCourseAdjustments = true
            )
        )
        val overrideMar = resOverride.racePredictions.getValue(M2ATargetRaceDistance.MARATHON)
        if (overrideMar is M2ARacePredictionOutcome.Predicted) {
            assertTrue(
                "M2A-R2-DEF-07: prediction using unverified course adjustment must be marked isProvisional=true",
                overrideMar.isProvisional
            )
            assertTrue(
                "M2A-R2-DEF-07: prediction must include explicit UNVERIFIED_SECONDARY_COURSE_ADJUSTMENT_USED caveat",
                overrideMar.caveats.contains("UNVERIFIED_SECONDARY_COURSE_ADJUSTMENT_USED")
            )
        }
    }

    @Test fun reviewAdversarial08_contradictoryFitnessEpochIdsWithinDeduplicatedGroupMustBeFlaggedAndUnresolved_exposesR2Def08() {
        val observed = continuousObservedWorkload(weeklyKm = 60.0)
        val recA = raceInput(
            "dup-ep-a", daysAgo = 20, distanceM = 10_000.0, durationS = 2_700.0,
            sourceProvider = "garmin", sha256 = "e".repeat(64), fitnessEpochId = "epoch-spring"
        )
        val recB = raceInput(
            "dup-ep-b", daysAgo = 20, distanceM = 10_010.0, durationS = 2_702.0,
            sourceProvider = "health_connect", sha256 = "e".repeat(64), fitnessEpochId = "epoch-autumn"
        )
        val res = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(recA, recB), asOfUtc)
        )
        // Strict expectation: contradictory epoch IDs inside a merged physical race must clear fitnessEpochId to null AND emit flag!
        assertNull(
            "M2A-R2-DEF-08: contradictory fitnessEpochIds in duplicate group must not silently pick alphabetically first epoch",
            res.operationalThresholdBenchmark!!.fitnessEpochId
        )
        assertTrue(
            "M2A-R2-DEF-08: contradictory fitnessEpochIds must emit CONFLICTING_FITNESS_EPOCH_IN_DUPLICATE_GROUP",
            res.diagnosticFlags.contains("CONFLICTING_FITNESS_EPOCH_IN_DUPLICATE_GROUP")
        )
    }

    @Test fun reviewAdversarial09_competingRecordsWithIdenticalPrimarySortKeysMustBeOrderIndependent_exposesR2Def09() {
        val observed = continuousObservedWorkload(weeklyKm = 60.0)
        // Two distinct unknown-priority providers ("coros" vs "suunto", both priority=5), identical performanceId="evt-1",
        // identical date, identical authority, symmetric durations around 3240 s (2240 s vs 4240 s -> identical |dur - 3240| = 1000 s!),
        // identical distance 10,000 m!
        val coros = raceInput("evt-1", daysAgo = 20, distanceM = 10_000.0, durationS = 2_240.0, sourceProvider = "coros", authority = EvidenceAuthorityClass.SENSOR_SUPPORTED)
        val suunto = raceInput("evt-1", daysAgo = 20, distanceM = 10_000.0, durationS = 4_240.0, sourceProvider = "suunto", authority = EvidenceAuthorityClass.SENSOR_SUPPORTED)

        val resAB = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(coros, suunto), asOfUtc)
        )
        val resBA = Engine15M2APerformanceCalibrator.calibrate(
            M2APerformanceCalibrationRequest(accountA, asOfDate, utc, observed, listOf(suunto, coros), asOfUtc)
        )
        // Strict expectation: INV-24 permutation invariance requires identical contentHash and identical selected benchmark!
        assertEquals(
            "M2A-R2-DEF-09: competing records with identical primary sort keys must produce permutation-invariant output",
            resAB.contentHash,
            resBA.contentHash
        )
        assertEquals(resAB.operationalThresholdBenchmark, resBA.operationalThresholdBenchmark)
    }
}

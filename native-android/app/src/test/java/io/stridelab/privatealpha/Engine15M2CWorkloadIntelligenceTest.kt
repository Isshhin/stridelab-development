package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * JUnit 4 regression suite for M2-C Multidimensional Workload Intelligence.
 * Invokes actual Engine15M2CWorkloadIntelligence, Engine15M2CRollingExposureEngine,
 * Engine15M2CInternalAndDownhillEngine, and Engine15M2CQualityExposureLedgerBuilder paths
 * against frozen M1 / M2-A R3 / M2-B v3 contracts (13d0675e75d91e85920e058ce74f639869f96d27).
 */
class Engine15M2CWorkloadIntelligenceTest {

    private val accountA = "owner-m2c-alpha"
    private val accountB = "owner-m2c-beta"
    private val utc: ZoneOffset = ZoneOffset.UTC
    private val evalDate = LocalDate.of(2026, 11, 25)
    private val evalUtc = evalDate.atTime(12, 0).toInstant(utc)
    private val ctxA = M2BEvaluationContext(accountA, AthleteDiscipline.RUN, evalUtc, utc)

    private fun runCan(
        id: String,
        startedAt: Instant,
        distanceM: Double? = 10_000.0,
        durationS: Double? = 3_000.0,
        accountId: String = accountA,
        discipline: AthleteDiscipline = AthleteDiscipline.RUN,
        rawType: String = if (discipline == AthleteDiscipline.RUN) "running" else "cycling",
        provider: String = "garmin",
        hrQuality: TelemetryQualityStatus = TelemetryQualityStatus.VALID_OBSERVED,
        distQuality: TelemetryQualityStatus = TelemetryQualityStatus.VALID_OBSERVED,
        durQuality: TelemetryQualityStatus = TelemetryQualityStatus.VALID_OBSERVED,
        dedup: CanonicalDeduplicationStatus = CanonicalDeduplicationStatus.SINGLE_SOURCE,
        ambiguities: List<ActivityAmbiguityNotice> = emptyList(),
        sha256: String? = null
    ): CanonicalActivity {
        val raw = RawSourceComponent(
            id, provider, id, sha256, startedAt, rawType, "Session $id",
            distanceM, durationS, durationS,
            if (distanceM != null && durationS != null && distanceM > 0.0) durationS / (distanceM / 1000.0) else null,
            150.0, 170.0, 20.0
        )
        val spd = if (distanceM != null && durationS != null && distanceM > 0.0 && durationS > 0.0) MetersPerSecond.of(distanceM / durationS) else null
        return CanonicalActivity(
            accountId, discipline, rawType, startedAt, provider,
            NormalizedActivityMetrics(distanceM?.let(Meters::of), durationS?.let(Seconds::of), durationS?.let(Seconds::of), spd, null, null),
            TelemetryQualityAssessment(hrQuality, distQuality, durQuality),
            dedup, listOf(raw), ambiguities
        )
    }

    private fun assembleM1C(
        acts: List<CanonicalActivity>,
        ctx: M2BEvaluationContext = ctxA,
        sr: Map<AthleteDiscipline, ProvisionalSelfReportWorkload> = emptyMap()
    ): ObservedWorkloadState = Engine15ObservedWorkloadAssembler.assemble(
        accountId = ctx.accountId, asOfDate = ctx.localDate, zoneId = ctx.zone,
        canonicalActivities = acts, provisionalSelfReport = sr, asOfUtc = ctx.asOf
    )

    private fun m2bCal(
        ctx: M2BEvaluationContext = ctxA,
        wl: ObservedWorkloadState,
        restriction: M2BRestrictionStatus = M2BRestrictionStatus.NoRestrictionReported,
        verifiedRace: Boolean = false
    ): Engine15M2BIntensityCalibrator {
        val perf = if (verifiedRace) {
            val raceStart = ctx.asOf.minusSeconds(20L * 86_400L)
            M2APerformanceCalibrationRequest(
                ctx.accountId, ctx.localDate, ctx.zone, wl,
                listOf(
                    M2ARacePerformanceInput(
                        "race-10k", ctx.accountId, AthleteDiscipline.RUN, raceStart.atZone(ctx.zone).toLocalDate(),
                        Meters.of(10_000.0), Seconds.of(2_460.0), EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED,
                        M2APerformanceIntent.VERIFIED_OFFICIAL_RACE, "garmin", observedAtUtc = raceStart
                    )
                ),
                ctx.asOf
            )
        } else null
        return Engine15M2BIntensityCalibrator.calibrate(ctx, restriction = restriction, performance = perf)
    }

    private fun continuousRunActs(weeklyKm: Double, count: Int = 20, ctx: M2BEvaluationContext = ctxA): List<CanonicalActivity> =
        (1..count).map { i ->
            val dayOff = (28L - ((i - 1) * 28L / count.toLong())).coerceIn(1L, 28L)
            val distM = (weeklyKm * 4.0 * 1000.0) / count.toDouble()
            runCan("c-$i", ctx.localDate.minusDays(dayOff).atTime(7, 0).atZone(ctx.zone).toInstant(), distM, (distM / 1000.0) * 320.0, ctx.accountId)
        }



    // 1. Valid ordinary consistent runner baseline & orthogonal vector
    @Test fun test01_validOrdinaryConsistentRunnerBaselineAndVector() {
        val acts = continuousRunActs(48.0, 16)
        val wl = assembleM1C(acts)
        val supp = acts.map { a ->
            val alias = Engine15M2CCanonical.aliasesFromCanonicalActivity(a).first()
            val end = Engine15M2CCanonical.completedInstant(a.startedAtUtc, a.normalizedMetrics.duration!!.value)!!
            M2CCompletedActivityInput(a, internalLoad = M2CSessionInternalLoadInput(accountA, a.canonicalId, alias, 4.0, end.plusSeconds(1800)))
        }
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl, verifiedRace = true), supplementaryActivities = supp)
        )
        assertEquals(M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED, res.handoff.baseline.arbitrationStatus)
        assertEquals(48_000.0, (res.handoff.baseline.observedWeeklyMean as BeliefState.Estimated).estimate.value, 1.0)
        assertTrue(res.handoff.baseline.selfReportReplacementAuthorized)
        assertTrue(res.handoff.paceCalibrationEligible)
        assertFalse(res.productionAuthorized || res.workoutPrescriptionAuthorized || res.hrZoneAuthorized)
        assertFalse(res.handoff.workloadVector.collapsesToSingleCompositeScore)
    }

    // 2. Confirmed zero (T1A-F01) vs Missing unobserved (T1A-F02)
    @Test fun test02_confirmedZeroVsMissingUnobservedHistory() {
        val wlZero = assembleM1C(emptyList(), sr = mapOf(
            AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.CONFIRMED_ZERO, confirmedZero = true)
        ))
        val wlMiss = assembleM1C(emptyList())
        val rZero = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wlZero, m2bCal(wl = wlZero)))
        val rMiss = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wlMiss, m2bCal(wl = wlMiss)))

        assertEquals(M2CBaselineArbitrationStatus.CONFIRMED_ZERO, rZero.handoff.baseline.arbitrationStatus)
        assertEquals(0.0, (rZero.handoff.baseline.observedWeeklyMean as BeliefState.Estimated).estimate.value, 1e-9)
        assertEquals(M2CBaselineArbitrationStatus.MISSING_UNOBSERVED, rMiss.handoff.baseline.arbitrationStatus)
        assertTrue(rMiss.handoff.baseline.observedWeeklyMean is BeliefState.Unknown)
        assertEquals(M2CInterruptionBucket.UNCONFIRMED_MISSING_SYNC, rMiss.handoff.interruptionEvidence.bucket)
        assertNotEquals(rZero.contentHash, rMiss.contentHash)
    }

    // 3. Partial history (T1A-F03 / T2-S13) preserves credible self-report
    @Test fun test03_partialHistoryPreservesCredibleSelfReport() {
        val acts = listOf(
            runCan("p1", evalDate.minusDays(8).atTime(7, 0).atZone(utc).toInstant(), 7_000.0, 2_100.0),
            runCan("p2", evalDate.minusDays(5).atTime(7, 0).atZone(utc).toInstant(), 6_000.0, 1_800.0),
            runCan("p3", evalDate.minusDays(2).atTime(7, 0).atZone(utc).toInstant(), 7_000.0, 2_100.0)
        )
        val wl = assembleM1C(acts, sr = mapOf(
            AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE, Meters.fromKilometers(45.0))
        ))
        val res = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl)))
        assertEquals(M2CBaselineArbitrationStatus.PARTIAL_RECORDED_WITH_SELF_REPORT, res.handoff.baseline.arbitrationStatus)
        assertEquals(45_000.0, res.handoff.baseline.provisionalSelfReportWeekly!!.value, 1e-6)
        assertEquals(5_000.0, res.handoff.baseline.observedLowerBoundWeekly!!.value, 1e-6)
        assertFalse(res.handoff.baseline.selfReportReplacementAuthorized)
    }

    // 4. POL-013 forward (26 vs 65 -> ratio 0.40, ceiling 31 km) & reverse (72 vs 35 -> ratio 2.057, 72 km preserved)
    @Test fun test04_pol013ForwardAndReverseRecordedVsSelfReportConflicts() {
        val wlFwd = assembleM1C(continuousRunActs(26.0, 12), sr = mapOf(
            AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE, Meters.fromKilometers(65.0))
        ))
        val rFwd = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wlFwd, m2bCal(wl = wlFwd)))
        assertEquals(M2CBaselineArbitrationStatus.CONFLICT_CONSERVATIVE_CEILING, rFwd.handoff.baseline.arbitrationStatus)
        assertEquals(0.40, rFwd.handoff.baseline.pol013RecordedToSelfReportRatio!!.value, 1e-6)
        assertEquals(31_000.0, rFwd.handoff.baseline.conservativePolicyCeilingWeekly!!.value, 1.0)
        assertTrue(rFwd.handoff.baseline.conflictDetected)

        val wlRev = assembleM1C(continuousRunActs(72.0, 20), sr = mapOf(
            AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE, Meters.fromKilometers(35.0))
        ))
        val rRev = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wlRev, m2bCal(wl = wlRev)))
        assertEquals(M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED_CONFLICT_ABOVE_SELF_REPORT, rRev.handoff.baseline.arbitrationStatus)
        assertEquals(72.0 / 35.0, rRev.handoff.baseline.pol013RecordedToSelfReportRatio!!.value, 1e-6)
        assertEquals(72_000.0, (rRev.handoff.baseline.observedWeeklyMean as BeliefState.Estimated).estimate.value, 1.0)
        assertNull(rRev.handoff.baseline.conservativePolicyCeilingWeekly)
        assertTrue(rRev.handoff.baseline.conflictDetected)
    }

    // 5. Exact 7/28/30/56d, leap year (2024-02-29), DST, same-day, cross-midnight & future-completion boundaries
    @Test fun test05_exactDateBoundariesLeapYearDstSameDayCrossMidnightAndFutureCompletion() {
        val dstZone = ZoneId.of("America/New_York")
        // March 15, 2024 crosses leap day (Feb 29) and US spring-forward DST (Mar 10) inside 30d!
        val leapEval = LocalDate.of(2024, 3, 15).atTime(12, 0).atZone(dstZone).toInstant()
        val leapCtx = M2BEvaluationContext(accountA, AthleteDiscipline.RUN, leapEval, dstZone)
        val dMinus30 = runCan("d30", LocalDate.of(2024, 2, 14).atTime(8, 0).atZone(dstZone).toInstant(), 18_000.0, 5_400.0)
        val dMinus31 = runCan("d31", LocalDate.of(2024, 2, 13).atTime(8, 0).atZone(dstZone).toInstant(), 35_000.0, 10_500.0)
        val leapDay = runCan("leap", LocalDate.of(2024, 2, 29).atTime(7, 0).atZone(dstZone).toInstant(), 10_000.0, 3_000.0)
        val crossMid = runCan("xmid", LocalDate.of(2024, 3, 14).atTime(23, 30).atZone(dstZone).toInstant(), 9_000.0, 3_600.0)
        val sameDay = runCan("day0", LocalDate.of(2024, 3, 15).atTime(7, 0).atZone(dstZone).toInstant(), 22_000.0, 6_600.0)
        val futComp = runCan("fut", leapEval.minusSeconds(600), 15_000.0, 3_600.0)

        val wl = assembleM1C(listOf(dMinus30, dMinus31, leapDay, crossMid, sameDay), ctx = leapCtx)
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                leapCtx, wl, m2bCal(leapCtx, wl),
                supplementaryActivities = listOf(dMinus30, leapDay, crossMid, sameDay, futComp).map { M2CCompletedActivityInput(it) }
            )
        )
        assertEquals(18_000.0, (res.handoff.workloadVector.running30d.longestSessionDistanceBelief as BeliefState.Estimated).estimate.value, 1e-6)
        assertEquals(1, res.handoff.workloadVector.sameDayCompletedRunningSessions.size)
        assertEquals(22_000.0, res.handoff.workloadVector.sameDayCompletedRunningSessions.single().distance!!.value, 1e-6)
        assertTrue(res.handoff.diagnosticFlags.contains("CROSS_MIDNIGHT_DATE_ATTRIBUTION_PRESERVED_PER_M1C_START_DATE"))
        assertTrue(res.handoff.diagnosticFlags.contains("FUTURE_COMPLETION_ACTIVITY_EXCLUDED"))
    }

    // 6. MOD-10 r30 (20000/18000, cap 19800, 3999 vs 4000), 90d override, provisional rejection without throwing (M2C-C01, D1-02, D2-04)
    @Test fun test06_mod10R30ExactBoundariesOverrideAndProvisionalRejectionWithoutException() {
        val run18k = runCan("r18", evalDate.minusDays(5).atTime(7, 0).atZone(utc).toInstant(), 18_000.0, 5_400.0)
        val run24k60d = runCan("r24", evalDate.minusDays(60).atTime(7, 0).atZone(utc).toInstant(), 24_000.0, 7_200.0)
        val wl18 = assembleM1C(listOf(run18k))
        val ack = M2CSingleSessionOverrideAcknowledgment(
            accountA, ctxA, Meters.of(20_000.0), Engine15M2CPolicyRegistry.POLICY_VERSION,
            setOf(M2BRestrictionReason.NO_RESTRICTION_REPORTED), evalUtc
        )
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl18, m2bCal(wl = wl18),
                supplementaryActivities = listOf(M2CCompletedActivityInput(run18k), M2CCompletedActivityInput(run24k60d)),
                evaluatedSingleSessionDistance = Meters.of(20_000.0),
                singleSessionOverrideAcknowledgment = ack
            )
        )
        val ss = res.singleSessionAssessment!!
        assertEquals(20_000.0 / 18_000.0, ss.r30Ratio!!.value, 1e-9)
        assertEquals(19_800.0, ss.defaultGuardrailCapMeters!!.value, 1e-6)
        assertEquals(M2CSingleSessionSpikeBand.SMALL_SPIKE_GT_1_10_TO_1_30, ss.spikeBand)
        assertTrue(ss.policyGuardrailExceeded && ss.observationalRiskSignalActive)
        assertTrue(ss.overrideEligibleAndAcknowledgedPreM9)
        assertFalse(ss.overrideGrantsProductionAuthority || ss.overrideErasesRiskSignal || ss.guaranteesInjurySafety)

        // Exact POL-023 floor: 3,999 m unavailable vs 4,000 m applicable!
        val wl3999 = assembleM1C(listOf(runCan("r3999", evalDate.minusDays(2).atTime(7, 0).atZone(utc).toInstant(), 3_999.0, 1_200.0)))
        val r3999 = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl3999, m2bCal(wl = wl3999), evaluatedSingleSessionDistance = Meters.of(4_500.0)))
        assertNull(r3999.singleSessionAssessment!!.r30Ratio)
        assertEquals(M2CSingleSessionSpikeBand.NOT_APPLICABLE_BELOW_4000M_FLOOR, r3999.singleSessionAssessment!!.spikeBand)

        val wl4000 = assembleM1C(listOf(runCan("r4000", evalDate.minusDays(2).atTime(7, 0).atZone(utc).toInstant(), 4_000.0, 1_200.0)))
        val r4000 = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl4000, m2bCal(wl = wl4000), evaluatedSingleSessionDistance = Meters.of(4_400.0)))
        assertEquals(1.10, r4000.singleSessionAssessment!!.r30Ratio!!.value, 1e-9)
        assertEquals(M2CSingleSessionSpikeBand.WITHIN_DEFAULT_GUARDRAIL_LE_1_10, r4000.singleSessionAssessment!!.spikeBand)

        // M2C-C01: Provisional/confirmed-zero Estimated longest run rejected cleanly WITHOUT throwing!
        val wlZero = assembleM1C(emptyList(), sr = mapOf(
            AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.CONFIRMED_ZERO, confirmedZero = true)
        ))
        val rZero = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wlZero, m2bCal(wl = wlZero), evaluatedSingleSessionDistance = Meters.of(5_000.0)))
        assertNull(rZero.singleSessionAssessment!!.r30Ratio)
        assertEquals(M2CSingleSessionSpikeBand.NOT_APPLICABLE_UNVERIFIED_AUTHORITY, rZero.singleSessionAssessment!!.spikeBand)
    }

    // 7. MOD-11 stopping predicate & retrospective compliance authority (M2C-A03, M2C-B04)
    @Test fun test07_mod11StoppingPredicateEnforcementVsRetrospectiveCompliance() {
        val run20k = runCan("lr-20", evalDate.minusDays(1).atTime(7, 0).atZone(utc).toInstant(), 20_000.0, 7_200.0)
        val wl = assembleM1C(listOf(run20k))
        val alias = Engine15M2CCanonical.aliasesFromCanonicalActivity(run20k).first()
        val endUtc = Engine15M2CCanonical.completedInstant(run20k.startedAtUtc, 7_200.0)!!

        // Claimed app enforcement without execution -> NEVER certifies compliance!
        val preRun = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl),
                longRunDurationCap = Seconds.of(10_800.0), longRunDistanceCap = Meters.of(24_000.0),
                longRunStoppingEnforcementMode = M2CStoppingEnforcementMode.CLAIMED_OR_CONFIGURED_APP_ENFORCEMENT
            )
        ).handoff.defaultLongRunStoppingContract
        assertFalse(preRun.authenticatedDistanceComplianceCertificate || preRun.structurallyCompliantWithDistanceCap)
        assertEquals(M2CStoppingEvaluationOutcome.STOP_DURATION_CAP_REACHED, preRun.evaluatePredicate(Seconds.of(10_800.0), Meters.of(19_000.0)))
        assertEquals(M2CStoppingEvaluationOutcome.UNVERIFIABLE_DISTANCE_CAP_WITHOUT_LIVE_DISTANCE, preRun.evaluatePredicate(Seconds.of(6_000.0), null))

        // Retrospective bound observation with distance cap -> structurally compliant, pre-M9 certificate still false!
        val retro = M2CStoppingVerificationEvidence.RetrospectiveCompletedObservation(
            accountA, run20k.canonicalId, alias, endUtc, Meters.of(20_000.0), Seconds.of(7_200.0),
            TelemetryQualityStatus.VALID_OBSERVED, TelemetryQualityStatus.VALID_OBSERVED
        )
        val postRun = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(M2CCompletedActivityInput(run20k)),
                longRunDurationCap = Seconds.of(10_800.0), longRunDistanceCap = Meters.of(24_000.0),
                longRunStoppingEvidence = retro
            )
        ).handoff.defaultLongRunStoppingContract
        assertTrue(postRun.structurallyCompliantWithDistanceCap && postRun.structurallyCompliantWithDurationCap)
        assertFalse(postRun.authenticatedDistanceComplianceCertificate)

        // Duration-only cap (distanceCap == null) NEVER sets structurallyCompliantWithDistanceCap!
        val durOnly = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(M2CCompletedActivityInput(run20k)),
                longRunDurationCap = Seconds.of(10_800.0), longRunDistanceCap = null, longRunStoppingEvidence = retro
            )
        ).handoff.defaultLongRunStoppingContract
        assertFalse(durOnly.structurallyCompliantWithDistanceCap)
        assertTrue(durOnly.structurallyCompliantWithDurationCap)
    }



    @Test fun testExactMembershipOfAllFiveWindowsAndTwoCompletedSameDayRuns() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2024, 3, 15)
        val at = date.atTime(12, 0).atZone(zone).toInstant()
        val ctx = M2BEvaluationContext(accountA, AthleteDiscipline.RUN, at, zone)
        val ages = listOf(1, 5, 7, 8, 15, 28, 29, 30, 31, 56, 57, 90, 91)
        val dated = ages.associateWith { days ->
            runCan("boundary-$days", date.minusDays(days.toLong()).atTime(1, 30).atZone(zone).toInstant(),
                1000.0 + days, 3600.0)
        }
        val completed = listOf(runCan("today-first", at.minusSeconds(7200), 3500.0, 1200.0),
            runCan("today-exact-end", at.minusSeconds(1800), 4200.0, 1800.0))
        val afterCutoff = listOf(runCan("still-running", at.minusSeconds(599), 4201.0, 600.0),
            runCan("future-start", at.plusSeconds(1), 4300.0, 1800.0))
        val wl = assembleM1C(dated.values.toList(), ctx)
        val quality = listOf(56, 57).map { days ->
            val act = dated.getValue(days)
            M2CQualityFamilyObservationInput(accountA, act, Engine15M2CCanonical.aliasesFromCanonicalActivity(act).first(),
                M2CQualityWorkoutFamily.TEMPO, Seconds.of(600.0), Seconds.of(300.0),
                M2CQualityExecutionOutcome.COMPLETED_ON_TARGET, recordedAtUtc = act.startedAtUtc.plusSeconds(3700))
        }
        val result = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctx, wl, m2bCal(ctx, wl),
            supplementaryActivities = (dated.values + completed + afterCutoff).map { M2CCompletedActivityInput(it) },
            qualityObservations = quality, evaluatedSingleSessionDistance = Meters.of(5000.0)))
        val v = result.handoff.workloadVector
        for ((days, window) in listOf(7 to v.running7d, 28 to v.running28d, 30 to v.running30d)) {
            val expected = dated.filterKeys { it <= days }.values
            assertEquals(expected.map { it.canonicalId }.toSet(), window.sessions.map { it.canonicalActivityId }.toSet())
            assertEquals(expected.size, window.verifiedSessionCount)
            assertEquals(expected.sumOf { it.normalizedMetrics.distance!!.value },
                (window.totalDistanceBelief as BeliefState.Estimated).estimate.value, 1e-8)
        }
        assertEquals(setOf(dated.getValue(56).canonicalId), v.qualityLedger56d.forFamily(M2CQualityWorkoutFamily.TEMPO).canonicalActivityIds)
        assertEquals(dated.filterKeys { it <= 90 }.values.map { it.canonicalId }.toSet(), v.running90dAnchor.canonicalActivityIds)
        assertEquals(1090.0, v.running90dAnchor.exactObservedMeters!!, 0.0)
        assertEquals(completed.map { it.canonicalId }.toSet(), v.sameDayCompletedRunningSessions.map { it.canonicalActivityId }.toSet())
        assertEquals(2, v.sameDayCompletedRunningSessions.size)
        assertTrue("FUTURE_COMPLETION_ACTIVITY_EXCLUDED" in result.handoff.diagnosticFlags)
    }

    @Test fun testProvisionalPositiveLongestAndAmbiguousPairCannotEstablishR30() {
        val report = ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE,
            weeklyVolume = Meters.of(45000.0), longestSession = Meters.of(18000.0))
        val wl = assembleM1C(emptyList(), sr = mapOf(AthleteDiscipline.RUN to report))
        val provisional = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
            evaluatedSingleSessionDistance = Meters.of(20000.0))).singleSessionAssessment!!
        assertEquals(18000.0, (provisional.anchor30d.distanceBelief as BeliefState.Estimated).estimate.value, 0.0)
        assertEquals(M2CAnchorCompletenessKind.PROVISIONAL_SELF_REPORT_NON_AUTHORITATIVE, provisional.anchor30dCompletenessKind)
        assertNull(provisional.defaultGuardrailCapMeters)
        assertNull(provisional.r30Ratio)
        assertFalse(provisional.overrideEligibleAndAcknowledgedPreM9)

        val start = evalUtc.minusSeconds(2L * 86400L)
        fun candidate(id: String, other: String, meters: Double) =
            runCan(id, start, meters, 3000.0, dedup = CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE,
                ambiguities = listOf(ActivityAmbiguityNotice(ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE,
                    other, "garmin", "overlap")))
        val pair = listOf(candidate("overlap-a", "overlap-b", 8000.0), candidate("overlap-b", "overlap-a", 9000.0))
        val ambiguousWl = assembleM1C(pair)
        val result = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, ambiguousWl,
            m2bCal(wl = ambiguousWl), supplementaryActivities = pair.map { M2CCompletedActivityInput(it) },
            evaluatedSingleSessionDistance = Meters.of(20000.0)))
        val window = result.handoff.workloadVector.running30d
        assertEquals(2, window.ambiguousSessionCount)
        val total = window.totalDistanceBelief as BeliefState.Bounded
        assertEquals(8000.0, total.lower!!.value, 0.0)
        assertEquals(17000.0, total.upper!!.value, 0.0)
        assertEquals(M2CSingleSessionSpikeBand.NOT_APPLICABLE_AMBIGUOUS_BOUNDED_BASELINE, result.singleSessionAssessment!!.spikeBand)
        assertNull(result.singleSessionAssessment!!.r30Ratio)
    }

    private fun expectRollingBindingFailure(message: String, operation: () -> Unit) {
        try {
            operation()
            fail("Missing binding rejection: " + message)
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message, failure.message.orEmpty().contains(message))
        }
    }

    @Test fun testForgedInventoryAndMismatchedAnchorsRejected() {
        val run = runCan("pin", evalUtc.minusSeconds(86400))
        val older = runCan("extended", evalUtc.minusSeconds(60L * 86400L))
        val wl = assembleM1C(listOf(run))
        val req = M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
            supplementaryActivities = listOf(M2CCompletedActivityInput(run)), evaluatedSingleSessionDistance = Meters.of(11000.0))
        val plain = Engine15M2CWorkloadIntelligence.assess(req)
        val extended = Engine15M2CWorkloadIntelligence.assess(req.copy(supplementaryActivities =
            req.supplementaryActivities + M2CCompletedActivityInput(older)))
        val inv = plain.reconciledInventory
        fun forge(pinned: List<M2CReconciledRunningEntry>) = M2CReconciledEvidenceInventory.create(ctxA, wl.contentHash,
            pinned, emptyList(), emptyList(), emptyList(), emptySet(), emptySet(), emptySet())
        expectRollingBindingFailure("incomplete pinned") {
            M2CVerifiedSingleSessionAnchor.fromReconciled30dInventory(ctxA, wl, forge(emptyList()))
        }
        expectRollingBindingFailure("duplicate canonicalActivityId") {
            M2CVerifiedSingleSessionAnchor.fromReconciled30dInventory(ctxA, wl, forge(inv.pinned30dRunningEntries + inv.pinned30dRunningEntries))
        }
        expectRollingBindingFailure("same reconciledInventoryContentHash") {
            Engine15M2CRollingExposureEngine.assessSingleSessionExposure(ctxA, Meters.of(11000.0),
                plain.singleSessionAssessment!!.anchor30d, extended.singleSessionAssessment!!.anchor90d,
                plain.handoff.effectiveRestrictionState)
        }
        val otherWorkload = assembleM1C(listOf(runCan("changed-pin", evalUtc.minusSeconds(86400), 12000.0)))
        val other = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, otherWorkload,
            m2bCal(wl = otherWorkload), evaluatedSingleSessionDistance = Meters.of(11000.0)))
        expectRollingBindingFailure("same observedWorkloadContentHash") {
            Engine15M2CRollingExposureEngine.assessSingleSessionExposure(ctxA, Meters.of(11000.0),
                plain.singleSessionAssessment!!.anchor30d, other.singleSessionAssessment!!.anchor90d,
                plain.handoff.effectiveRestrictionState)
        }
        expectRollingBindingFailure("effectiveRestrictionState must match") {
            Engine15M2CRollingExposureEngine.assessSingleSessionExposure(ctxA, Meters.of(11000.0),
                plain.singleSessionAssessment!!.anchor30d, plain.singleSessionAssessment!!.anchor90d,
                other.handoff.effectiveRestrictionState)
        }
    }

    @Test fun testTrack2DatedScenariosSeparateWeeklyExposureSessionPeakAndDuration() {
        // Weekly session distances, repeated four times across the preceding 28 local dates.
        data class Fixture(val scenario: String, val weeklyKm: Int, val sessions: List<Double>,
            val longestKm: Double, val secondsPerKm: Double = 320.0)
        val cases = listOf(
            Fixture("S03",20,listOf(6.0,6.0,8.0),8.0),
            Fixture("S04",40,listOf(8.0,9.0,9.0,14.0),14.0),
            Fixture("S05",65,listOf(10.0,10.0,10.0,11.0,24.0),24.0),
            Fixture("S06",115,listOf(16.0,16.0,17.0,17.0,17.0,32.0),32.0,285.0),
            Fixture("S07",50,listOf(9.0,9.0,9.0,9.0,14.0),14.0),
            Fixture("S08",42,listOf(6.5,7.0,7.0,7.0,7.0,7.5),7.5),
            Fixture("S09",70,listOf(11.0,11.0,12.0,12.0,24.0),24.0),
            Fixture("S10",22,listOf(6.0,7.0,9.0),9.0),
            Fixture("S14",35,listOf(7.0,7.0,8.0,13.0),13.0),
            Fixture("S15",50,listOf(9.0,9.0,10.0,22.0),22.0,450.0)
        )
        for (f in cases) {
            val acts = (0..3).flatMap { week ->
                f.sessions.mapIndexed { index, km ->
                    val age = 28 - 7 * week - 6 * index / (f.sessions.size - 1)
                    runCan(f.scenario + "-" + age, evalDate.minusDays(age.toLong()).atTime(6, 0).atZone(utc).toInstant(),
                        km * 1000.0, km * f.secondsPerKm)
                }
            }
            val wl = assembleM1C(acts)
            val r = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl,
                m2bCal(wl = wl), evaluatedSingleSessionDistance = Meters.of(f.longestKm * 1100.0)))
            val v = r.handoff.workloadVector
            assertEquals(f.scenario, f.weeklyKm * 1000.0, (r.handoff.baseline.observedWeeklyMean as BeliefState.Estimated).estimate.value, 1e-6)
            assertEquals(f.scenario, f.sessions.size, v.running7d.verifiedSessionCount)
            assertEquals(f.scenario, f.longestKm * 1000.0, r.singleSessionAssessment!!.anchor30d.exactObservedMeters!!, 1e-6)
            assertEquals(f.scenario, f.longestKm * 1100.0, r.singleSessionAssessment!!.defaultGuardrailCapMeters!!.value, 1e-6)
            assertEquals(f.scenario, f.longestKm * f.secondsPerKm, (v.running30d.longestSessionDurationBelief as BeliefState.Estimated).estimate.value, 1e-6)
            assertEquals(M2CAnchorCompletenessKind.VERIFIED_MAX_AMONG_AVAILABLE_RECORDINGS, r.singleSessionAssessment!!.anchor30dCompletenessKind)
            assertFalse(r.workoutPrescriptionAuthorized)
        }
    }

    // 8. MOD-09 Session-RPE (6 * 60 = 360 AU), missing RPE, chronology & D2-01 opaque alias guard
    @Test fun test08_mod09InternalLoadRpeChronologyOpaqueIdsAndMissingness() {
        val run1 = runCan("rpe-1", evalDate.minusDays(2).atTime(7, 0).toInstant(utc), 10_000.0, 3_600.0)
        val run2 = runCan("rpe-2", evalDate.minusDays(1).atTime(7, 0).toInstant(utc), 8_000.0, 2_400.0)
        val wl = assembleM1C(listOf(run1, run2))
        val alias1 = Engine15M2CCanonical.aliasesFromCanonicalActivity(run1).first()
        val end1 = Engine15M2CCanonical.completedInstant(run1.startedAtUtc, 3_600.0)!!

        // run1 has valid RPE 6.0 (360 AU); run2 has missing RPE -> running load Bounded(360 AU, null)!
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl),
                supplementaryActivities = listOf(
                    M2CCompletedActivityInput(run1, internalLoad = M2CSessionInternalLoadInput(accountA, run1.canonicalId, alias1, 6.0, end1.plusSeconds(1800)))
                )
            )
        )
        val il = res.handoff.workloadVector.internalLoad28d
        assertEquals(1, il.validRunningRpeCount)
        assertEquals(1, il.missingRunningRpeCount)
        val bounded = il.runningLoadAu as M2CInternalLoadBelief.Bounded
        assertEquals(360.0, bounded.lower.value, 1e-9)
        assertNull(bounded.upper)
        // Missing-enrichment pinned run2 retains opaqueUpstreamRecordIds without fake "opaque_id" alias!
        val rec2 = il.records.first { it.canonicalActivityId == run2.canonicalId }
        assertNull(rec2.sourceAlias)
        assertTrue(rec2.opaqueUpstreamRecordIds.isNotEmpty())

        // Pre-completion RPE timestamp & out-of-range RPE (11.0) rejected cleanly!
        val resBad = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl),
                supplementaryActivities = listOf(
                    M2CCompletedActivityInput(run1, internalLoad = M2CSessionInternalLoadInput(accountA, run1.canonicalId, alias1, 6.0, end1.minusSeconds(60))),
                    M2CCompletedActivityInput(run2, internalLoad = M2CSessionInternalLoadInput(accountA, run2.canonicalId, Engine15M2CCanonical.aliasesFromCanonicalActivity(run2).first(), 11.0, evalUtc))
                )
            )
        )
        assertTrue(resBad.handoff.workloadVector.internalLoad28d.runningLoadAu is M2CInternalLoadBelief.Unknown)
        assertEquals(2, resBad.handoff.workloadVector.internalLoad28d.invalidRpeExcludedCount)
    }

    // 9. Partial intensity domain classification (20m of 60m run) & D2-02 ambiguous unclassified bounds
    @Test fun test09_partialIntensityDomainClassificationAndAmbiguousUnclassified() {
        val run60 = runCan("dom-60", evalDate.minusDays(2).atTime(7, 0).toInstant(utc), 10_000.0, 3_600.0)
        val wl = assembleM1C(listOf(run60))
        val alias = Engine15M2CCanonical.aliasesFromCanonicalActivity(run60).first()
        val end = Engine15M2CCanonical.completedInstant(run60.startedAtUtc, 3_600.0)!!
        val partialBd = M2CWorkoutDomainBreakdownInput(
            accountA, run60.canonicalId, alias, end.plusSeconds(600),
            moderateWorkSeconds = 1_200.0 // 40 min unclassified, other domains null!
        )
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(M2CCompletedActivityInput(run60, domainBreakdown = partialBd)))
        )
        val dom = res.handoff.workloadVector.intensityExposure28d
        assertEquals(1, dom.partiallyClassifiedDurationSessionCount)
        assertEquals(0, dom.fullyClassifiedDurationSessionCount)
        assertEquals(1_200.0, (dom.moderateWorkDuration as BeliefState.Bounded).lower!!.value, 1e-6)
        assertTrue(dom.highIntensityHeavyPlusSevereDuration is BeliefState.Unknown)
        assertTrue(dom.neuromuscularWorkDuration is BeliefState.Unknown)
        assertEquals(2_400.0, (dom.unclassifiedRunningDuration as BeliefState.Bounded).lower!!.value, 1e-6)
        assertEquals(EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, (dom.unclassifiedRunningDuration as BeliefState.Bounded).authority)
        assertFalse(dom.authenticatedExecutionAuthority)
    }

    // 10. Downhill E_down (40m + 100m = 140m under GAP vetoes), confirmed zero flat, & contradictory distance (M2C-B05)
    @Test fun test10_downhillAggregationGapRejectionConfirmedZeroAndSummaryConflict() {
        val start = evalDate.minusDays(3).atTime(7, 0).toInstant(utc)
        val hillRun = runCan("hill-1", start, 400.0, 90.0)
        val raw = hillRun.rawComponents.single()
        val origin = M2BStreamIdentity.of(hillRun, raw)
        val window = M2BRecordingWindow(
            origin, M2BDistanceSemantics.HORIZONTAL, hillRun.telemetryQuality,
            listOf(
                M2BRecordingSample(start, Meters.of(0.0), 200.0, M2BContinuityEvidence.UNKNOWN),
                M2BRecordingSample(start.plusSeconds(45), Meters.of(200.0), 160.0, M2BContinuityEvidence.ACTIVE_CONTIGUOUS), // -40m, g=-0.20 (quarantined)
                M2BRecordingSample(start.plusSeconds(90), Meters.of(400.0), 60.0, M2BContinuityEvidence.ACTIVE_CONTIGUOUS)   // -100m, g=-0.50 (OOD)
            )
        )
        val stream = M2BRunningStream(hillRun, Engine15M2CCanonical.aliasesFromCanonicalActivity(hillRun).first(), M2BSurface.NON_TECHNICAL_ROAD, emptyList(), window)
        val wl = assembleM1C(listOf(hillRun))
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(M2CCompletedActivityInput(hillRun, runningStream = stream)))
        )
        val dh = res.handoff.workloadVector.downhill28d
        assertEquals(M2CDownhillCoverageStatus.COMPLETE_RECORDING_DERIVED_DESCRIPTIVE, dh.coverageStatus)
        assertEquals(140.0, (dh.cumulativeElevationLossBelief as BeliefState.Estimated).estimate.value, 1e-6)
        assertEquals(setOf(M2BGAPRejectionReason.ECCENTRIC_DOMINATED_QUARANTINE, M2BGAPRejectionReason.GAP_OUT_OF_DOMAIN), dh.retainedGapRejectionReasons)
        assertFalse(dh.verifiedWorkloadAuthority || dh.isBiomechanicalTissueDamageEstimate)

        // Contradictory summary distance (stream covers 200 m vs summary 400 m) -> NEVER complete or confirmed zero!
        val badWindow = M2BRecordingWindow(
            origin, M2BDistanceSemantics.HORIZONTAL, hillRun.telemetryQuality,
            listOf(
                M2BRecordingSample(start, Meters.of(0.0), 100.0, M2BContinuityEvidence.UNKNOWN),
                M2BRecordingSample(start.plusSeconds(90), Meters.of(200.0), 100.0, M2BContinuityEvidence.ACTIVE_CONTIGUOUS)
            )
        )
        val badStream = M2BRunningStream(hillRun, Engine15M2CCanonical.aliasesFromCanonicalActivity(hillRun).first(), M2BSurface.NON_TECHNICAL_ROAD, emptyList(), badWindow)
        val resBad = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(M2CCompletedActivityInput(hillRun, runningStream = badStream)))
        )
        assertEquals(M2CDownhillCoverageStatus.MISSING_OR_CONFLICTING_ELEVATION_EVIDENCE, resBad.handoff.workloadVector.downhill28d.coverageStatus)
        assertEquals(1, resBad.handoff.workloadVector.downhill28d.conflictingRecordingCount)
    }

    // 11. Weekly distribution volatility, confirmed sparse week vs missing gap, conflicting confirmations & D2-03
    @Test fun test11_weeklyDistributionVolatilitySparseWeekVsMissingGapAndCoverage() {
        // 3 active weeks (30 km each) + 1 confirmed sparse rest week (week offset 1)
        val acts = listOf(0, 2, 3).flatMap { w ->
            listOf(1L, 3L, 5L).map { d ->
                val date = evalDate.minusDays(w * 7L + d)
                runCan("w$w-d$d", date.atTime(7, 0).toInstant(utc), 10_000.0, 3_000.0)
            }
        }
        val wl = assembleM1C(acts)
        val sparseStart = evalDate.minusDays(14)
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl),
                weeklyConfirmations = listOf(M2CWeeklyConfirmationInput(sparseStart, true, "TRAVEL_REST"))
            )
        )
        val dist = res.handoff.workloadVector.weeklyDistribution28d
        assertEquals(3, dist.activeWeekCount)
        assertEquals(1, dist.confirmedSparseWeekCount)
        assertEquals(0, dist.unconfirmedGapWeekCount)
        assertTrue(dist.isolatedConfirmedSparseWeekPattern)
        assertNotNull(dist.coefficientOfVariation)
        assertTrue(dist.hasUnresolvedVolatilityPolicy && !dist.authorizesStabilizationAction)

        // Contradictory confirmations (true + false for same week) -> conservatively treated as UNCONFIRMED_MISSING_DATA_GAP!
        val resConf = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl),
                weeklyConfirmations = listOf(
                    M2CWeeklyConfirmationInput(sparseStart, true),
                    M2CWeeklyConfirmationInput(sparseStart, false)
                )
            )
        )
        assertEquals(1, resConf.handoff.workloadVector.weeklyDistribution28d.unconfirmedGapWeekCount)
        assertEquals(0, resConf.handoff.workloadVector.weeklyDistribution28d.confirmedSparseWeekCount)
    }

    // 12. Ten-family quality ledger, pre-M9 recovery trust boundary & missing != confirmed absent (M2C-C02)
    @Test fun test12_tenFamilyQualityLedgerTrustBoundaryAndMissingNotConfirmedAbsent() {
        val tempoRun = runCan("q-tempo", evalDate.minusDays(5).atTime(7, 0).toInstant(utc), 10_000.0, 2_700.0)
        val wl = assembleM1C(listOf(tempoRun))
        val alias = Engine15M2CCanonical.aliasesFromCanonicalActivity(tempoRun).first()
        val end = Engine15M2CCanonical.completedInstant(tempoRun.startedAtUtc, 2_700.0)!!
        val obs = M2CQualityFamilyObservationInput(
            accountA, tempoRun, alias, M2CQualityWorkoutFamily.TEMPO, Seconds.of(1_200.0), Seconds.of(1_200.0),
            M2CQualityExecutionOutcome.COMPLETED_ON_TARGET, M2CEvidenceTrustTier.INDEPENDENTLY_AUTHENTICATED_M9,
            M2CRecoveryEvidenceState.VERIFIED_ACCEPTABLE, M2CEvidenceTrustTier.INDEPENDENTLY_AUTHENTICATED_M9, end.plusSeconds(3600)
        )
        val plannedVo2 = M2CQualityFamilyObservationInput(
            accountA, tempoRun, alias, M2CQualityWorkoutFamily.VO2MAX_INTERVALS, Seconds.of(600.0), Seconds.of(180.0),
            M2CQualityExecutionOutcome.COMPLETED_ON_TARGET, recordedAtUtc = end.plusSeconds(3600), isPlannedOrProjected = true
        )
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl),
                supplementaryActivities = listOf(M2CCompletedActivityInput(tempoRun)),
                qualityObservations = listOf(obs, plannedVo2)
            )
        )
        val ledger = res.handoff.workloadVector.qualityLedger56d
        assertEquals(10, ledger.recordsByFamily.size)
        // Pre-M9 custody inactive -> TEMPO held at OBSERVED_COMPLETED + REPORTED_ACCEPTABLE_UNAUTHENTICATED_PRE_M9!
        val tempoRec = ledger.forFamily(M2CQualityWorkoutFamily.TEMPO)
        assertEquals(M2CFamilyExposureState.OBSERVED_COMPLETED, tempoRec.exposureState)
        assertEquals(M2CRecoveryEvidenceState.REPORTED_ACCEPTABLE_UNAUTHENTICATED_PRE_M9, tempoRec.recentRecoveryEvidence)
        assertFalse(tempoRec.authorizesWorkoutDose || tempoRec.transferFabricatesCompletedExposure)
        // Planned VO2max excluded -> stays UNKNOWN_EXPOSURE (never CONFIRMED_ABSENT)!
        assertEquals(M2CFamilyExposureState.UNKNOWN_EXPOSURE, ledger.forFamily(M2CQualityWorkoutFamily.VO2MAX_INTERVALS).exposureState)
        assertEquals(1, ledger.excludedProjectedOrPlannedCount)
    }

    // 13. M2C-C02: Content-hash mismatch, absent extended quality obs, adverse conflict preservation & cross-family cap
    @Test fun test13_qualityContentHashExtendedMembershipAdverseConflictAndCrossFamilyCap() {
        val run1 = runCan("q-1", evalDate.minusDays(4).atTime(7, 0).toInstant(utc), 10_000.0, 2_700.0)
        val runExtMissing = runCan("q-ext-missing", evalDate.minusDays(40).atTime(7, 0).toInstant(utc), 10_000.0, 2_700.0)
        val wl = assembleM1C(listOf(run1))
        val alias1 = Engine15M2CCanonical.aliasesFromCanonicalActivity(run1).first()
        val end1 = Engine15M2CCanonical.completedInstant(run1.startedAtUtc, 2_700.0)!!

        // Contradictory duplicate on TEMPO: one acceptable, one ADVERSE_SYMPTOM_OR_FATIGUE -> MUST preserve adverse!
        val goodDup = M2CQualityFamilyObservationInput(
            accountA, run1, alias1, M2CQualityWorkoutFamily.TEMPO, Seconds.of(1_000.0), Seconds.of(1_000.0),
            M2CQualityExecutionOutcome.COMPLETED_ON_TARGET, recoveryEvidence = M2CRecoveryEvidenceState.REPORTED_ACCEPTABLE_UNAUTHENTICATED_PRE_M9,
            recordedAtUtc = end1.plusSeconds(100)
        )
        val adverseDup = M2CQualityFamilyObservationInput(
            accountA, run1, alias1, M2CQualityWorkoutFamily.TEMPO, Seconds.of(1_000.0), Seconds.of(1_000.0),
            M2CQualityExecutionOutcome.COMPLETED_ON_TARGET, recoveryEvidence = M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE,
            recordedAtUtc = end1.plusSeconds(200)
        )
        // Extended 40d observation NOT present in reconciled inventory -> rejected!
        val missingExtObs = M2CQualityFamilyObservationInput(
            accountA, runExtMissing, Engine15M2CCanonical.aliasesFromCanonicalActivity(runExtMissing).first(),
            M2CQualityWorkoutFamily.THRESHOLD, Seconds.of(1_000.0), Seconds.of(500.0),
            M2CQualityExecutionOutcome.COMPLETED_ON_TARGET, recordedAtUtc = evalUtc
        )
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl),
                supplementaryActivities = listOf(M2CCompletedActivityInput(run1)),
                qualityObservations = listOf(goodDup, adverseDup, missingExtObs)
            )
        )
        val tempoRec = res.handoff.workloadVector.qualityLedger56d.forFamily(M2CQualityWorkoutFamily.TEMPO)
        assertTrue(tempoRec.adverseRecoveryConflictPreserved)
        assertEquals(M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE, tempoRec.recentRecoveryEvidence)
        assertEquals(M2CFamilyExposureState.EXCESSIVE_OR_UNSUCCESSFUL, tempoRec.exposureState)
        assertEquals(M2CFamilyExposureState.UNKNOWN_EXPOSURE, res.handoff.workloadVector.qualityLedger56d.forFamily(M2CQualityWorkoutFamily.THRESHOLD).exposureState)
    }

    private fun componentVector(
        activities: List<CanonicalActivity>,
        supplemental: List<M2CCompletedActivityInput> = activities.map { M2CCompletedActivityInput(it) },
        quality: List<M2CQualityFamilyObservationInput> = emptyList(),
        weeks: List<M2CWeeklyConfirmationInput> = emptyList()
    ) = assembleM1C(activities).let { wl ->
        Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(
            ctxA, wl, m2bCal(wl = wl), supplementaryActivities = supplemental,
            qualityObservations = quality, weeklyConfirmations = weeks
        )).handoff.workloadVector
    }

    @Test fun opaqueRecordAndIncorrectProviderCannotSupplyRpeAuthority() {
        val act = runCan("opaque-rpe", evalUtc.minusSeconds(172800), durationS = 3600.0)
        val opaque = componentVector(listOf(act), emptyList()).internalLoad28d.records.single()
        assertEquals(setOf("opaque-rpe"), opaque.opaqueUpstreamRecordIds)
        assertNull(opaque.sourceAlias)
        assertNull(opaque.loadAu)

        val impostor = M2ASourceQualifiedAlias("different-provider", "opaque-rpe")
        val wrong = M2CSessionInternalLoadInput(accountA, act.canonicalId, impostor, 6.0, evalUtc)
        val rejected = componentVector(listOf(act), listOf(M2CCompletedActivityInput(act, internalLoad = wrong))).internalLoad28d
        assertEquals(1, rejected.invalidRpeExcludedCount)
        assertEquals(0, rejected.validRunningRpeCount)
        assertNull(rejected.records.single().loadAu)
        assertEquals(M2ASourceQualifiedAlias("garmin", "opaque-rpe"), rejected.records.single().sourceAlias)
    }

    private fun ambiguousRunningPair(missingDistance: Boolean, missingDuration: Boolean = false): List<CanonicalActivity> {
        fun alternative(id: String, related: String, distance: Double?, duration: Double?) =
            runCan(id, evalUtc.minusSeconds(3 * 86400L), distance, duration,
                durQuality = if (duration == null) TelemetryQualityStatus.MISSING_TELEMETRY else TelemetryQualityStatus.VALID_OBSERVED,
                distQuality = if (distance == null) TelemetryQualityStatus.MISSING_TELEMETRY else TelemetryQualityStatus.VALID_OBSERVED,
                dedup = CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE,
                ambiguities = listOf(ActivityAmbiguityNotice(ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE,
                    related, "garmin", "POSSIBLE_DUPLICATE")))
        return listOf(alternative("amb-small", "amb-large", if (missingDistance) null else 5000.0, if (missingDuration) null else 1500.0),
            alternative("amb-large", "amb-small", 8000.0, 2400.0))
    }

    @Test fun groupedIntensityAlternativeContributesMinimumAndOpenUpper() {
        val vector = componentVector(ambiguousRunningPair(false))
        val exposure = vector.intensityExposure28d
        val time = exposure.unclassifiedRunningDuration as BeliefState.Bounded
        val distance = exposure.unclassifiedRunningDistance as BeliefState.Bounded
        assertEquals(1500.0, time.lower!!.value, 1e-6)
        assertEquals(5000.0, distance.lower!!.value, 1e-6)
        assertNull(time.upper)
        assertNull(distance.upper)
        assertTrue(exposure.highIntensityHeavyPlusSevereDuration is BeliefState.Unknown)
        assertEquals(0, vector.weeklyDistribution28d.exactRecordedSubtotalWeekCount)
        assertEquals(1, vector.weeklyDistribution28d.partialOrBoundedWeekCount)
        assertNull(vector.weeklyDistribution28d.coefficientOfVariation)
    }

    @Test fun missingDistanceAlternativeWithholdsPositiveDistanceFloor() {
        val exposure = componentVector(ambiguousRunningPair(true)).intensityExposure28d
        val distance = exposure.unclassifiedRunningDistance as BeliefState.Bounded
        assertEquals(0.0, distance.lower!!.value, 1e-6)
        assertNull(distance.upper)
        assertEquals(1500.0, (exposure.unclassifiedRunningDuration as BeliefState.Bounded).lower!!.value, 1e-6)
    }

    @Test fun track2SparseAndErraticWeeklySubtotalsRetainCaptureQualification() {
        fun weekly(km: List<Int>, confirmRest: Boolean): M2CWeeklyDistributionSummary {
            val acts = km.flatMapIndexed { offset, distance ->
                if (distance == 0) emptyList() else (1..5).map { day ->
                    runCan("week-$offset-day-$day", evalUtc.minusSeconds((offset * 7L + day) * 86400L),
                        distance * 200.0, distance * 60.0)
                }
            }
            val zeroWeekStart = evalDate.minusDays((km.indexOf(0) + 1L) * 7L)
            val confirmations = if (confirmRest) listOf(M2CWeeklyConfirmationInput(zeroWeekStart, true)) else emptyList()
            return componentVector(acts, weeks = confirmations).weeklyDistribution28d
        }
        val sparse = weekly(listOf(50, 50, 0, 50), true)
        assertEquals(37500.0, sparse.meanWeeklyDistance!!.value, 1e-6)
        assertEquals(50000.0, sparse.medianWeeklyDistance!!.value, 1e-6)
        assertEquals(25000.0, sparse.sampleStdDevWeeklyDistance!!.value, 1e-6)
        assertEquals(4, sparse.exactRecordedSubtotalWeekCount)
        assertEquals(0, sparse.completeActualWeeklyWorkloadCount)
        assertTrue(sparse.isolatedConfirmedSparseWeekPattern)
        val gap = weekly(listOf(50, 50, 0, 50), false)
        assertEquals(3, gap.exactRecordedSubtotalWeekCount)
        assertEquals(1, gap.unconfirmedGapWeekCount)
        assertEquals(50000.0, gap.meanWeeklyDistance!!.value, 1e-6)
        val erratic = weekly(listOf(10, 55, 0, 48), true)
        assertEquals(28250.0, erratic.meanWeeklyDistance!!.value, 1e-6)
        assertEquals(29000.0, erratic.trimmedMeanWeeklyDistance!!.value, 1e-6)
        assertFalse(erratic.authorizesStabilizationAction)
        val continuous = componentVector(continuousRunActs(50.0)).weeklyDistribution28d
        assertEquals(4, continuous.exactRecordedSubtotalWeekCount)
        assertEquals(0, continuous.completeActualWeeklyWorkloadCount)
    }

    private fun descentInput(act: CanonicalActivity, loss: Double, surface: M2BSurface,
                            startOffset: Long = 0L): M2CCompletedActivityInput {
        val start = act.startedAtUtc
        val raw = act.rawComponents.single()
        val win = M2BRecordingWindow(M2BStreamIdentity.of(act, raw), M2BDistanceSemantics.HORIZONTAL,
            act.telemetryQuality, listOf(
                M2BRecordingSample(start.plusSeconds(startOffset), Meters.ZERO, 100.0, M2BContinuityEvidence.UNKNOWN),
                M2BRecordingSample(start.plusSeconds(60), Meters.of(200.0), 100.0 - loss, M2BContinuityEvidence.ACTIVE_CONTIGUOUS)))
        return M2CCompletedActivityInput(act, runningStream = M2BRunningStream(act,
            Engine15M2CCanonical.aliasesFromCanonicalActivity(act).single(), surface, emptyList(), win))
    }

    @Test fun independentGapVetoesPreserveMeasuredDownhillGeometry() {
        val run = runCan("gap-vetoes", evalUtc.minusSeconds(86400), 200.0, 60.0)
        val road = M2BSurface.NON_TECHNICAL_ROAD
        for ((surface, loss, reasons) in listOf(
            Triple(road, 10.0, emptySet()),
            Triple(road, 40.0, setOf(M2BGAPRejectionReason.ECCENTRIC_DOMINATED_QUARANTINE)),
            Triple(road, 100.0, setOf(M2BGAPRejectionReason.GAP_OUT_OF_DOMAIN)),
            Triple(M2BSurface.TECHNICAL_OR_UNKNOWN, 10.0, setOf(M2BGAPRejectionReason.UNSUPPORTED_SURFACE)),
            Triple(M2BSurface.TECHNICAL_OR_UNKNOWN, 40.0, setOf(M2BGAPRejectionReason.UNSUPPORTED_SURFACE,
                M2BGAPRejectionReason.ECCENTRIC_DOMINATED_QUARANTINE)))) {
            val d = componentVector(listOf(run), listOf(descentInput(run, loss, surface))).downhill28d
            assertEquals(reasons, d.retainedGapRejectionReasons)
            assertEquals(loss, (d.cumulativeElevationLossBelief as BeliefState.Estimated).estimate.value, 1e-6)
            assertEquals(200.0, (d.recordedDescentHorizontalDistance as BeliefState.Estimated).estimate.value, 1e-6)
            assertEquals(Math.hypot(200.0, loss), (d.recordedDescentSlopePathDistance as BeliefState.Estimated).estimate.value, 1e-6)
            assertEquals(60.0, (d.recordedDescentDuration as BeliefState.Estimated).estimate.value, 1e-6)
        }
    }

    @Test fun flatTracePartialWindowAndMissingElevationHaveDistinctBeliefs() {
        val act = runCan("flat", evalUtc.minusSeconds(86400), 200.0, 60.0)
        val road = M2BSurface.NON_TECHNICAL_ROAD
        val flat = componentVector(listOf(act), listOf(descentInput(act, 0.0, road))).downhill28d
        assertEquals(M2CDownhillCoverageStatus.CONFIRMED_ZERO_RECORDING_DESCENT, flat.coverageStatus)
        assertEquals(0.0, (flat.cumulativeElevationLossBelief as BeliefState.Estimated).estimate.value, 0.0)
        val partial = componentVector(listOf(act), listOf(descentInput(act, 5.0, road, 10))).downhill28d
        assertEquals(M2CDownhillCoverageStatus.PARTIAL_MEASURED_DESCENT_BOUNDED, partial.coverageStatus)
        val lower = partial.cumulativeElevationLossBelief as BeliefState.Bounded
        assertEquals(5.0, lower.lower!!.value, 1e-6)
        assertNull(lower.upper)
        val missing = componentVector(listOf(act)).downhill28d
        assertTrue(missing.cumulativeElevationLossBelief is BeliefState.Unknown)
        assertEquals(1, missing.missingElevationActivityCount)
    }

    private fun qualityRow(act: CanonicalActivity, family: M2CQualityWorkoutFamily, work: Double, bout: Double) =
        M2CQualityFamilyObservationInput(accountA, act, Engine15M2CCanonical.aliasesFromCanonicalActivity(act).single(),
            family, Seconds.of(work), Seconds.of(bout), M2CQualityExecutionOutcome.COMPLETED_ON_TARGET, recordedAtUtc = evalUtc)

    @Test fun tenFamiliesHaveIndependentCompletedObservationsAndNoUnreportedRecoveryCredit() {
        val families = M2CQualityWorkoutFamily.entries
        assertEquals(10, families.size)
        val acts = families.mapIndexed { index, _ -> runCan("family-$index", evalUtc.minusSeconds((index + 1) * 86400L)) }
        val rows = families.mapIndexed { index, family -> qualityRow(acts[index], family, 500.0 + index, 100.0 + index) }
        val ledger = componentVector(acts, quality = rows).qualityLedger56d
        families.forEachIndexed { index, family ->
            val rec = ledger.forFamily(family)
            assertEquals(family.name, M2CFamilyExposureState.OBSERVED_COMPLETED, rec.exposureState)
            assertEquals(setOf(acts[index].canonicalId), rec.canonicalActivityIds)
            assertEquals(1, rec.observedSessionCount56d)
            assertEquals(0, rec.authenticatedSuccessfulSessionCount56d)
            assertEquals(M2CRecoveryEvidenceState.MISSING_UNREPORTED, rec.recentRecoveryEvidence)
            assertEquals(500.0 + index, (rec.totalObservedWorkDuration56d as BeliefState.Estimated).estimate.value, 1e-6)
            assertEquals(100.0 + index, rec.longestObservedBoutDuration56d!!.value, 1e-6)
            assertFalse(rec.authorizesWorkoutDose || rec.transferFabricatesCompletedExposure)
        }
    }

    @Test fun crossFamilyConservationAndContradictoryReportedBouts() {
        val run = runCan("shared-family-run", evalUtc.minusSeconds(86400), durationS = 3000.0)
        val tempo = M2CQualityWorkoutFamily.TEMPO
        val threshold = M2CQualityWorkoutFamily.THRESHOLD
        val row = qualityRow(run, tempo, 1500.0, 300.0)
        val within = componentVector(listOf(run), quality = listOf(row, qualityRow(run, threshold, 1500.0, 300.0))).qualityLedger56d
        for (family in listOf(tempo, threshold)) assertEquals(1, within.forFamily(family).observedSessionCount56d)
        val excess = componentVector(listOf(run), quality = listOf(row, qualityRow(run, threshold, 1501.0, 300.0))).qualityLedger56d
        for (family in listOf(tempo, threshold)) {
            val rec = excess.forFamily(family)
            assertEquals(0, rec.observedSessionCount56d)
            assertEquals(1, rec.conflictingObservationCount56d)
            assertTrue(rec.totalObservedWorkDuration56d is BeliefState.Unknown)
        }
        val conflict = componentVector(listOf(run), quality = listOf(row, qualityRow(run, tempo, 1500.0, 450.0))).qualityLedger56d
        assertEquals(0, conflict.forFamily(tempo).observedSessionCount56d)
        assertEquals(1, conflict.forFamily(tempo).conflictingObservationCount56d)
        val changed = runCan("shared-family-run", run.startedAtUtc, durationS = 3100.0)
        val otherContent = componentVector(listOf(run), quality = listOf(qualityRow(changed, tempo, 600.0, 300.0))).qualityLedger56d
        assertNotEquals(run.contentHash, changed.contentHash)
        assertEquals(0, otherContent.forFamily(tempo).observedSessionCount56d)
    }

    @Test fun missingDurationAlternativeWithholdsPositiveUnclassifiedTimeFloor() {
        val exp = componentVector(ambiguousRunningPair(false, missingDuration = true)).intensityExposure28d
        val duration = exp.unclassifiedRunningDuration as BeliefState.Bounded
        assertEquals(0.0, duration.lower!!.value, 0.0)
        assertNull(duration.upper)
        assertEquals(5000.0, (exp.unclassifiedRunningDistance as BeliefState.Bounded).lower!!.value, 1e-6)
    }

    @Test fun numericalGapRejectionKeepsFiniteRecordedLoss() {
        val act = runCan("numeric-gap", evalUtc.minusSeconds(86400), 200.0, 60.0)
        val origin = M2BStreamIdentity.of(act, act.rawComponents.single())
        val start = act.startedAtUtc
        // A finite, in-domain grade whose speed underflows even though the measured rise is representable.
        val dx = Double.MIN_VALUE * 10.0
        val loss = Double.MIN_VALUE
        val samples = listOf(
            M2BRecordingSample(start, Meters.ZERO, 0.0, M2BContinuityEvidence.UNKNOWN),
            M2BRecordingSample(start.plusSeconds(45), Meters.of(dx), -loss, M2BContinuityEvidence.ACTIVE_CONTIGUOUS),
            M2BRecordingSample(start.plusSeconds(60), Meters.of(200.0), -loss, M2BContinuityEvidence.ACTIVE_CONTIGUOUS))
        val window = M2BRecordingWindow(origin, M2BDistanceSemantics.HORIZONTAL, act.telemetryQuality, samples)
        val stream = M2BRunningStream(act, Engine15M2CCanonical.aliasesFromCanonicalActivity(act).single(),
            M2BSurface.NON_TECHNICAL_ROAD, emptyList(), window)
        val d = componentVector(listOf(act), listOf(M2CCompletedActivityInput(act, runningStream = stream))).downhill28d
        assertEquals(setOf(M2BGAPRejectionReason.INVALID_NUMERIC_INPUT), d.retainedGapRejectionReasons)
        assertEquals(loss, (d.cumulativeElevationLossBelief as BeliefState.Estimated).estimate.value, 0.0)
        assertEquals(1, d.intervals.size)
    }

    // 14. Post-illness (POL-034/CON-02) blocks ordinary pace, superseding NO_RESTRICTION_REPORTED (D1-01, C04)
    @Test fun test14_postIllnessRestrictionBlocksOrdinaryPaceAndPreservesPersistentHolds() {
        val wl = assembleM1C(continuousRunActs(50.0, 16))
        val m2bVerified = m2bCal(wl = wl, verifiedRace = true)
        assertTrue(m2bVerified.paceCalibrationEligible)

        // Unresolved post-illness (< 14 d) MUST block ordinary pace AND quarantine HR!
        val resPostIll = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bVerified,
                confirmedInterruption = M2CConfirmedInterruptionInput(
                    M2CInterruptionKind.POST_ILLNESS, confirmedBreakDays = 10,
                    illnessResolvedAtUtc = evalUtc.minusSeconds(5L * 86_400L),
                    symptomFreeConfirmed = true, restingAndSubmaxHrStabilityConfirmed = false
                )
            )
        )
        assertTrue(resPostIll.handoff.effectiveRestrictionState.recoveryOnly)
        assertTrue(resPostIll.handoff.effectiveRestrictionState.hrQuarantined)
        assertFalse(resPostIll.handoff.paceCalibrationEligible)

        // Persistent snapshot hold cannot be cleared by caller-reported clearance!
        val snapHold = AthleteStateSnapshot(accountA, evalUtc, emptyMap(), activeRestrictionIds = setOf("H2_TIBIA"), observedWorkload = wl)
        val resHold = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bVerified, athleteSnapshot = snapHold,
                confirmedInterruption = M2CConfirmedInterruptionInput(
                    M2CInterruptionKind.POST_INJURY_USER_REPORTED, confirmedBreakDays = 21, userReportedClearanceToResumeRunning = true
                )
            )
        )
        assertTrue(resHold.handoff.effectiveRestrictionState.hold)
        assertEquals(setOf("H2_TIBIA"), resHold.handoff.persistentRestrictionIds)
        assertFalse(resHold.handoff.paceCalibrationEligible || resHold.independentlyClearsPersistentHold)
    }

    // 15. Multimodal completeness: pinned vs unpinned non-running, ambiguous non-running & missing RPE (C05, D3-02)
    @Test fun test15_multimodalPinnedVsUnpinnedAmbiguousNonRunningAndMissingRpe() {
        val bike1 = runCan("bike-1", evalDate.minusDays(4).atTime(7, 0).toInstant(utc), 30_000.0, 3_600.0, discipline = AthleteDiscipline.CONDITIONING, rawType = "cycling")
        val strUnpinned = runCan("str-unpin", evalDate.minusDays(2).atTime(7, 0).toInstant(utc), null, 2_700.0, discipline = AthleteDiscipline.STRENGTH, rawType = "strength")
        val wl = assembleM1C(listOf(bike1))
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl),
                supplementaryActivities = listOf(M2CCompletedActivityInput(bike1), M2CCompletedActivityInput(strUnpinned))
            )
        )
        // Both the pinned bike and the descriptive strength session are missing RPE.
        assertEquals(2, res.handoff.workloadVector.internalLoad28d.missingNonRunningRpeCount)
        assertTrue(res.handoff.workloadVector.systemicMultimodal28d.crossModalRunningTissueMismatchSignal)
        assertFalse(res.handoff.workloadVector.systemicMultimodal28d.nonRunningFabricatesRunningImpactTolerance)
        // Unpinned strength stays descriptive (Bounded, SELF_REPORTED_PROVISIONAL), never exact observed!
        val strSummary = res.handoff.workloadVector.systemicMultimodal28d.byModality[M2CModalityKind.STRENGTH]!!
        assertEquals(1, strSummary.unpinnedDescriptiveSessionCount28d)
        assertTrue(strSummary.totalDuration28d is BeliefState.Bounded)
    }


    @Test fun unrelatedRunningActivityInsidePinnedWindowCannotReplaceRecordedRun() {
        val trueRun = runCan("pin-1", evalDate.minusDays(3).atTime(7, 0).toInstant(utc), 10_000.0, 3_000.0)
        val wl = assembleM1C(listOf(trueRun))
        // An unrelated session must not replace the pinned run. A same-ID contradiction is exercised below.
        val unpinnedRun = runCan("unpin-1", evalDate.minusDays(2).atTime(7, 0).toInstant(utc), 15_000.0, 4_500.0)
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(M2CCompletedActivityInput(unpinnedRun)))
        )
        assertEquals(10_000.0, (res.handoff.workloadVector.running30d.longestSessionDistanceBelief as BeliefState.Estimated).estimate.value, 1e-6)
        assertTrue(res.reconciledInventory.unpinnedExcludedCanonicalIds.contains(unpinnedRun.canonicalId))
    }

    // 17. Observed/Projected separation & GAP-02 preservation (planned distance never enters observed!)
    @Test fun test17_observedProjectedSeparationAndGap02Preservation() {
        val obs10k = runCan("obs-10k", evalDate.minusDays(4).atTime(7, 0).toInstant(utc), 10_000.0, 3_000.0)
        val wl = assembleM1C(listOf(obs10k))
        val projected = ProjectedWorkloadState(
            accountA, evalDate,
            listOf(
                DatedProjectedSessionExposure(
                    "proj-25k", accountA, "plan-1", "s1", AthleteDiscipline.RUN, evalDate.plusDays(3),
                    ProjectedWorkloadAuthority.ACCEPTED_PLAN_FUTURE, Meters.of(25_000.0), Meters.of(25_000.0), Seconds.of(7_500.0), Seconds.of(7_500.0)
                )
            )
        )
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl), projectedWorkload = projected,
                supplementaryActivities = listOf(M2CCompletedActivityInput(runCan("proj-act", evalDate.minusDays(1).atTime(7, 0).toInstant(utc), 30_000.0, 9_000.0), isProjected = true))
            )
        )
        assertEquals(10_000.0, (res.handoff.workloadVector.running30d.longestSessionDistanceBelief as BeliefState.Estimated).estimate.value, 1e-6)
        assertTrue(res.handoff.projectedBoundaryContract.horizonObservedEvidenceShareDeferredToM2D)
        assertFalse(res.handoff.projectedBoundaryContract.writesToObservedWorkloadState)
    }

    // 18. HR quarantine preserves valid running distance & cross-account isolation
    @Test fun test18_hrQuarantinePreservesValidRunningDistanceAndCrossAccountIsolation() {
        val hrBadRun = runCan("hr-bad", evalDate.minusDays(2).atTime(7, 0).toInstant(utc), 12_000.0, 3_600.0, hrQuality = TelemetryQualityStatus.QUARANTINED_CADENCE_LOCK)
        val foreignRun = runCan("for-1", evalDate.minusDays(1).atTime(7, 0).toInstant(utc), 40_000.0, 12_000.0, accountId = accountB)
        val wl = assembleM1C(listOf(hrBadRun))
        val res = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(
                ctxA, wl, m2bCal(wl = wl, restriction = M2BRestrictionStatus.HeartRateQuarantine(ctxA)),
                supplementaryActivities = listOf(M2CCompletedActivityInput(hrBadRun), M2CCompletedActivityInput(foreignRun))
            )
        )
        assertEquals(12_000.0, (res.handoff.workloadVector.running30d.longestSessionDistanceBelief as BeliefState.Estimated).estimate.value, 1e-6)
        assertEquals(1, res.handoff.workloadVector.running30d.hrQuarantinedButDistanceValidCount)
        assertTrue(res.handoff.diagnosticFlags.contains("FOREIGN_ACCOUNT_ACTIVITY_EXCLUDED"))
    }

    // 19. Per-metric duplicate conflicts, permutation invariance & canonical enrichment identity (B03, C06)
    @Test fun test19_perMetricDuplicateConflictsPermutationInvarianceAndContentIdentity() {
        val run1 = runCan("dup-1", evalDate.minusDays(3).atTime(7, 0).toInstant(utc), 10_000.0, 3_000.0)
        val wl = assembleM1C(listOf(run1))
        val alias = Engine15M2CCanonical.aliasesFromCanonicalActivity(run1).first()
        val end = Engine15M2CCanonical.completedInstant(run1.startedAtUtc, 3_000.0)!!
        val inpA = M2CCompletedActivityInput(run1, internalLoad = M2CSessionInternalLoadInput(accountA, run1.canonicalId, alias, 5.0, end.plusSeconds(60)))
        val inpB = M2CCompletedActivityInput(run1, internalLoad = M2CSessionInternalLoadInput(accountA, run1.canonicalId, alias, 8.0, end.plusSeconds(120)))

        val r1 = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(inpA, inpB)))
        val r2 = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(inpB, inpA)))
        assertEquals(r1.contentHash, r2.contentHash)
        assertEquals(r1.reconciledInventory.contentHash, r2.reconciledInventory.contentHash)
        assertTrue(r1.handoff.diagnosticFlags.contains("CONTRADICTORY_DUPLICATE_RPE_EXCLUDED"))
        assertEquals(10_000.0, (r1.handoff.workloadVector.running30d.longestSessionDistanceBelief as BeliefState.Estimated).estimate.value, 1e-6)

        // Materially changed enrichment (RPE 5 vs RPE 7) MUST change reconciledInventory.contentHash!
        val r5 = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(inpA)))
        val r7 = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(
                M2CCompletedActivityInput(run1, internalLoad = M2CSessionInternalLoadInput(accountA, run1.canonicalId, alias, 7.0, end.plusSeconds(60)))
            ))
        )
        assertNotEquals(r5.reconciledInventory.contentHash, r7.reconciledInventory.contentHash)
    }

    // 20. Deep immutability & extreme/non-finite numerical guards
    @Test fun test20_deepImmutabilityAndExtremeNonFiniteGuards() {
        val run1 = runCan("imm-1", evalDate.minusDays(2).atTime(7, 0).toInstant(utc), 10_000.0, 3_000.0)
        val wl = assembleM1C(listOf(run1))
        val res = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl)))
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) { (res.handoff.diagnosticFlags as MutableSet<String>).add("TAMPER") }
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) { (res.handoff.workloadVector.qualityLedger56d.recordsByFamily as MutableMap<*, *>).clear() }
        assertThrows(IllegalArgumentException::class.java) { M2CSessionLoadAu.of(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { M2CSessionLoadAu.of(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { M2CCompletedActivityInput(runCan("b1", evalUtc.minusSeconds(7200), discipline = AthleteDiscipline.CONDITIONING), modalityRefinement = M2CModalityKind.RUNNING) }
    }

    @Test fun illnessBoundaryInjuryHoldAndOrdinaryRunnerUseDistinctRestrictionPaths() {
        val wl = assembleM1C(continuousRunActs(50.0, 16))
        val calibration = m2bCal(wl = wl, verifiedRace = true)
        fun assess(input: M2CConfirmedInterruptionInput) = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, calibration, confirmedInterruption = input)
        ).handoff
        val ordinary = assess(M2CConfirmedInterruptionInput(M2CInterruptionKind.ORDINARY_BREAK, 14))
        assertTrue(ordinary.paceCalibrationEligible)
        assertFalse(ordinary.effectiveRestrictionState.hrQuarantined)
        val resolved = M2CConfirmedInterruptionInput(
            M2CInterruptionKind.POST_ILLNESS, 14, evalUtc.minusSeconds(14L * 86400),
            symptomFreeConfirmed = true, restingAndSubmaxHrStabilityConfirmed = true
        )
        for (restricted in listOf(resolved.copy(illnessResolvedAtUtc = resolved.illnessResolvedAtUtc!!.plusSeconds(1)),
            resolved.copy(illnessResolvedAtUtc = null), resolved.copy(symptomFreeConfirmed = false),
            resolved.copy(restingAndSubmaxHrStabilityConfirmed = false))) {
            val result = assess(restricted)
            assertTrue(result.effectiveRestrictionState.hrQuarantined)
            assertTrue(result.effectiveRestrictionState.recoveryOnly)
            assertFalse(result.paceCalibrationEligible)
        }
        assertTrue(assess(resolved).paceCalibrationEligible)
        val injury = M2CConfirmedInterruptionInput(M2CInterruptionKind.POST_INJURY_USER_REPORTED, 14)
        val uncleared = assess(injury)
        assertTrue(uncleared.effectiveRestrictionState.hold)
        assertTrue(M2BRestrictionReason.RUNNING_RESTRICTION in uncleared.effectiveRestrictionState.reasons)
        val reported = assess(injury.copy(userReportedClearanceToResumeRunning = true))
        assertTrue(reported.effectiveRestrictionState.recoveryOnly)
        assertFalse(reported.effectiveRestrictionState.verifiedClearance || reported.paceCalibrationEligible)
        val upstream = m2bCal(wl = wl, restriction = M2BRestrictionStatus.PersistentActive(ctxA))
        val stillHeld = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, upstream, confirmedInterruption = resolved)
        ).handoff
        assertTrue(stillHeld.effectiveRestrictionState.hold)
        assertTrue(M2BRestrictionReason.PERSISTENT_HOLD in stillHeld.effectiveRestrictionState.reasons)
    }

    @Test fun test16_pinnedEnrichmentDisagreementQuarantinesEnrichmentPreservingM1c() {
        val activity = runCan("same-pinned-id", evalUtc.minusSeconds(3L * 86400), 10000.0, 3000.0)
        val base = assembleM1C(listOf(activity))
        val run = base.disciplines.getValue(AthleteDiscipline.RUN)
        val e = run.exposures.single()
        // Simulate an upstream row retaining its canonical key with a revised duration.
        val revised = DatedObservedSessionExposure(e.exposureId, e.accountId, e.discipline, e.localDate,
            e.startedAtUtc, e.distance, Seconds.of(3100.0), e.provenance, e.sourceRecordIds, e.ambiguityGroupKey)
        val discipline = DisciplineObservedWorkload(run.discipline, run.coverage, run.observedSpanDays, run.maxGapDays,
            run.rolling7dDistanceBelief, run.rolling28dDistanceBelief, run.rolling28dWeeklyAverageBelief,
            run.preceding30dLongestSessionBelief, run.verifiedSessionCount28d, run.ambiguousCandidateCount28d,
            run.unknownDistanceCompletedCount28d, run.selfReportConflictFlag, listOf(revised))
        val wl = ObservedWorkloadState(accountA, evalDate, utc, base.disciplines + (run.discipline to discipline))
        assertEquals(revised.exposureId, activity.canonicalId)
        val alias = Engine15M2CCanonical.aliasesFromCanonicalActivity(activity).first()
        val rpe = M2CSessionInternalLoadInput(accountA, activity.canonicalId, alias, 7.0, activity.startedAtUtc.plusSeconds(4000))
        val result = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl,
            m2bCal(wl = wl), supplementaryActivities = listOf(M2CCompletedActivityInput(activity, internalLoad = rpe))))
        assertTrue("CONTRADICTORY_SUPPLEMENTARY_ENRICHMENT_QUARANTINED" in result.handoff.diagnosticFlags)
        val entry = result.reconciledInventory.pinned30dRunningEntries.single()
        assertNull(entry.canonicalActivity)
        assertNull(entry.internalLoad)
        assertEquals(revised.distance, entry.record.distance)
        assertEquals(revised.duration, entry.record.duration)
        assertEquals(e.sourceRecordIds, entry.record.opaqueUpstreamRecordIds)
        assertEquals(0, result.handoff.workloadVector.internalLoad28d.validRunningRpeCount)
    }

    @Test fun ambiguousPinnedRidesAndDescriptiveStrengthUseGroupedBoundsUnderPermutation() {
        val start = evalUtc.minusSeconds(3 * 86400L)
        fun ride(id: String, other: String, duration: Double) = runCan(id, start, 30000.0, duration,
            discipline = AthleteDiscipline.CONDITIONING, rawType = "cycling", ambiguities = listOf(
                ActivityAmbiguityNotice(ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE, other, "garmin", "OVERLAP")))
        val first = ride("ride-a", "ride-b", 3600.0)
        val second = ride("ride-b", "ride-a", 3700.0)
        val steady = runCan("ride-steady", start.minusSeconds(86400), 25000.0, 3000.0,
            discipline = AthleteDiscipline.CONDITIONING)
        val strength = runCan("descriptive-lift", start.minusSeconds(172800), null, 2700.0,
            discipline = AthleteDiscipline.STRENGTH, rawType = "strength")
        val pinned = listOf(first, second, steady)
        val wl = assembleM1C(pinned)
        val evidence = (pinned + strength).map { M2CCompletedActivityInput(it) }
        fun assess(items: List<M2CCompletedActivityInput>) = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl), supplementaryActivities = items))
        val result = assess(evidence)
        assertEquals(result.contentHash, assess(evidence.reversed()).contentHash)
        val vector = result.handoff.workloadVector
        val rides = vector.systemicMultimodal28d.byModality.getValue(M2CModalityKind.CYCLING)
        assertEquals(2, rides.ambiguousPinnedSessionCount28d)
        assertEquals(1, rides.verifiedPinnedSessionCount28d)
        val duration = rides.totalDuration28d as BeliefState.Bounded
        assertEquals(6600.0, duration.lower!!.value, 0.0)
        assertEquals(10300.0, duration.upper!!.value, 0.0)
        assertEquals(1, vector.systemicMultimodal28d.byModality.getValue(M2CModalityKind.STRENGTH).unpinnedDescriptiveSessionCount28d)
        assertEquals(0, vector.running28d.verifiedSessionCount)
        assertTrue(vector.systemicMultimodal28d.crossModalRunningTissueMismatchSignal)
        assertTrue(assess(emptyList()).handoff.workloadVector.systemicMultimodal28d.byModality.containsKey(M2CModalityKind.OTHER_CONDITIONING))
    }

    @Test fun projectedPlansAndForeignAliasedActivitiesDoNotChangeObservedMetrics() {
        val run = runCan("overlapping-id", evalUtc.minusSeconds(2 * 86400L))
        val foreign = runCan("overlapping-id", run.startedAtUtc, 80000.0, 9000.0, accountId = accountB)
        val wl = assembleM1C(listOf(run))
        val calibration = m2bCal(wl = wl)
        fun assess(extra: List<M2CCompletedActivityInput> = emptyList(), plan: ProjectedWorkloadState? = null) =
            Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, calibration,
                supplementaryActivities = listOf(M2CCompletedActivityInput(run)) + extra, projectedWorkload = plan))
        val original = assess()
        val projected = ProjectedWorkloadState(accountA, evalDate, listOf(DatedProjectedSessionExposure(
            "future-long", accountA, "plan", "session", AthleteDiscipline.RUN, evalDate.plusDays(2),
            ProjectedWorkloadAuthority.ACCEPTED_PLAN_FUTURE, Meters.of(80000.0), Meters.of(80000.0),
            Seconds.of(15000.0), Seconds.of(15000.0))))
        val planned = assess(plan = projected)
        assertEquals(original.handoff.workloadVector, planned.handoff.workloadVector)
        assertEquals(original.reconciledInventory.contentHash, planned.reconciledInventory.contentHash)
        assertEquals(projected.contentHash, planned.handoff.projectedBoundaryContract.projectedWorkloadContentHash)
        val mixed = assess(listOf(M2CCompletedActivityInput(foreign), M2CCompletedActivityInput(run, isProjected = true)), projected)
        assertEquals(original.handoff.workloadVector.running30d, mixed.handoff.workloadVector.running30d)
        assertEquals(original.reconciledInventory.pinned30dRunningEntries, mixed.reconciledInventory.pinned30dRunningEntries)
        assertTrue(mixed.reconciledInventory.collidedCanonicalActivityIds.isEmpty())
        assertTrue("FOREIGN_ACCOUNT_ACTIVITY_EXCLUDED" in mixed.handoff.diagnosticFlags)
        assertTrue("PROJECTED_ACTIVITY_EXCLUDED_FROM_OBSERVED" in mixed.handoff.diagnosticFlags)
        assertThrows(IllegalArgumentException::class.java) { assess(plan = ProjectedWorkloadState(accountB, evalDate, emptyList())) }
    }

    @Test fun callerMutationAndNestedProvenanceMutationCannotAlterCompletedAssessment() {
        val run = runCan("deep-snapshot", evalUtc.minusSeconds(2 * 86400L))
        val wl = assembleM1C(listOf(run))
        val callerList = mutableListOf(M2CCompletedActivityInput(run))
        val result = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(
            ctxA, wl, m2bCal(wl = wl), supplementaryActivities = callerList))
        val inventory = result.reconciledInventory
        val payload = inventory.canonicalRepresentation()
        callerList.clear()
        val aliases = inventory.pinned30dRunningEntries.single().record.sourceAliases
        assertTrue(aliases.isNotEmpty())
        assertThrows(UnsupportedOperationException::class.java) { (aliases as MutableSet<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.handoff.workloadVector.running30d.sessions as MutableList<*>).clear()
        }
        assertEquals(payload, inventory.canonicalRepresentation())
    }

    @Test fun finiteSessionDistancesOverflowOnlyTheSummedModalityDistance() {
        val wl = assembleM1C(emptyList())
        val inputs = listOf(2L, 3L).map { days -> M2CCompletedActivityInput(runCan(
            "huge-ride-$days", evalUtc.minusSeconds(days * 86400), Double.MAX_VALUE, 3600.0,
            discipline = AthleteDiscipline.CONDITIONING, rawType = "cycling")) }
        val result = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(
            ctxA, wl, m2bCal(wl = wl), supplementaryActivities = inputs))
        val rides = result.handoff.workloadVector.systemicMultimodal28d.byModality.getValue(M2CModalityKind.CYCLING)
        assertEquals(2, rides.unpinnedDescriptiveSessionCount28d)
        assertEquals(BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED"), rides.totalDistance28d)
        assertEquals(7200.0, (rides.totalDuration28d as BeliefState.Bounded).lower!!.value, 0.0)
    }

    @Test fun futurePinnedCompletionIsExcludedFromEveryEffectiveReference() {
        val retained = continuousRunActs(48.0, 16)
        val excluded = runCan("unfinished-yesterday", evalDate.minusDays(1).atTime(23, 0).toInstant(utc),
            59000.0, 14 * 3600.0)
        val sr = mapOf(AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(AthleteDiscipline.RUN,
            RecentMileageBasis.SELF_REPORTED_AVERAGE, Meters.of(55000.0)))
        val wl = assembleM1C(retained + excluded, sr = sr)
        assertTrue(wl.disciplines.getValue(AthleteDiscipline.RUN).exposures.any { it.exposureId == excluded.canonicalId })
        val assessment = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl,
            m2bCal(wl = wl), supplementaryActivities = (retained + excluded).map { M2CCompletedActivityInput(it) },
            evaluatedSingleSessionDistance = Meters.of(13200.0)))
        val v = assessment.handoff.workloadVector
        for ((days, window) in listOf(7 to v.running7d, 28 to v.running28d, 30 to v.running30d)) {
            val expected = retained.filter { it.startedAtUtc >= evalDate.minusDays(days.toLong()).atStartOfDay(utc).toInstant() }
            assertEquals(expected.map { it.canonicalId }.toSet(), window.sessions.map { it.canonicalActivityId }.toSet())
            assertEquals(12000.0 * expected.size, (window.totalDistanceBelief as BeliefState.Estimated).estimate.value, 1e-6)
            assertEquals(12000.0, (window.longestSessionDistanceBelief as BeliefState.Estimated).estimate.value, 1e-6)
        }
        val baseline = assessment.handoff.baseline
        assertFalse(baseline.selfReportReplacementAuthorized)
        assertEquals(48000.0, baseline.observedLowerBoundWeekly!!.value, 1e-6)
        assertEquals(55000.0, baseline.provisionalSelfReportWeekly!!.value, 1e-6)
        assertNull((baseline.observedWeeklyMean as BeliefState.Bounded).upper)
        assertNull(baseline.conservativePolicyCeilingWeekly)
        assertEquals(16, baseline.verifiedSessionCount28d)
        assertEquals(12000.0, assessment.singleSessionAssessment!!.anchor30d.exactObservedMeters!!, 0.0)
        assertEquals(12000.0, v.running90dAnchor.exactObservedMeters!!, 0.0)
        assertEquals(192000.0, v.weeklyDistribution28d.weeklyBuckets.sumOf {
            (it.distanceBelief as BeliefState.Estimated).estimate.value
        }, 1e-6)
        assertTrue("EXCLUDED_M1C_WORKLOAD_WITHHOLDS_EXACT_BASELINE_AUTHORITY" in assessment.handoff.diagnosticFlags)
    }

    @Test fun futureSourceCollisionCannotSuppressHistoricalAnchorOrQualityEvidence() {
        val historical = runCan("older", evalUtc.minusSeconds(45L * 86400), 18000.0, 5000.0, sha256 = "a".repeat(64))
        val future = runCan("unfinished", evalUtc.minusSeconds(60), 5000.0, 1800.0, sha256 = "a".repeat(64))
        val wl = assembleM1C(emptyList())
        fun assess(activities: List<CanonicalActivity>) = Engine15M2CWorkloadIntelligence.assess(
            M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
                supplementaryActivities = activities.map { M2CCompletedActivityInput(it) },
                qualityObservations = listOf(qualityRow(historical, M2CQualityWorkoutFamily.TEMPO, 900.0, 300.0))))
        val clean = assess(listOf(historical))
        val mixed = assess(listOf(future, historical))
        assertEquals(setOf(historical.canonicalId), clean.handoff.workloadVector.running90dAnchor.canonicalActivityIds)
        assertEquals(clean.reconciledInventory.extendedHistorical31to90RunningEntries,
            mixed.reconciledInventory.extendedHistorical31to90RunningEntries)
        assertEquals(18000.0, mixed.handoff.workloadVector.running90dAnchor.exactObservedMeters!!, 0.0)
        assertFalse(historical.canonicalId in mixed.reconciledInventory.collidedCanonicalActivityIds)
        val tempo = mixed.handoff.workloadVector.qualityLedger56d.forFamily(M2CQualityWorkoutFamily.TEMPO)
        assertEquals(setOf(historical.canonicalId), tempo.canonicalActivityIds)
    }

    @Test fun t5S23ExactDecemberWindowAndT2S15DualStoppingLimits() {
        val date = LocalDate.of(2026, 12, 5)
        val ctx = ctxA.copy(asOf = date.atTime(12, 0).toInstant(utc))
        val first = runCan("nov5", Instant.parse("2026-11-05T07:00:00Z"), 14500.0)
        val earlier = runCan("nov4", Instant.parse("2026-11-04T07:00:00Z"), 20000.0)
        val wl = assembleM1C(listOf(first, earlier), ctx)
        val r = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctx, wl, m2bCal(ctx, wl),
            evaluatedSingleSessionDistance = Meters.of(15800.0), longRunDistanceCap = Meters.of(24000.0),
            longRunDurationCap = Seconds.of(10800.0)))
        val exposure = r.singleSessionAssessment!!
        assertEquals(setOf(first.canonicalId), exposure.anchor30d.canonicalActivityIds)
        assertEquals(15800.0 / 14500.0, exposure.r30Ratio!!.value, 1e-12)
        assertFalse(exposure.policyGuardrailExceeded)
        val contract = r.handoff.defaultLongRunStoppingContract
        assertEquals(M2CStoppingEvaluationOutcome.CONTINUE_WITHIN_CAPS, contract.evaluatePredicate(Seconds.of(10799.0), Meters.of(23999.0)))
        assertEquals(M2CStoppingEvaluationOutcome.STOP_DURATION_CAP_REACHED, contract.evaluatePredicate(Seconds.of(10800.0), Meters.of(23999.0)))
        assertEquals(M2CStoppingEvaluationOutcome.STOP_DISTANCE_CAP_REACHED, contract.evaluatePredicate(Seconds.of(10799.0), Meters.of(24000.0)))
        assertEquals(M2CStoppingEvaluationOutcome.STOP_BOTH_CAPS_REACHED, contract.evaluatePredicate(Seconds.of(10800.0), Meters.of(24000.0)))
    }

    @Test fun track2ReturnBucketsPreserveConfirmedBreaksAtEveryEndpoint() {
        val wl = assembleM1C(emptyList())
        val ranges = listOf(0..6, 7..13, 14..27, 28..55, 56..83, 84..120)
        val buckets = listOf(M2CInterruptionBucket.DAYS_0_TO_6, M2CInterruptionBucket.DAYS_7_TO_13,
            M2CInterruptionBucket.DAYS_14_TO_27, M2CInterruptionBucket.DAYS_28_TO_55,
            M2CInterruptionBucket.DAYS_56_TO_83, M2CInterruptionBucket.DAYS_84_PLUS)
        ranges.zip(buckets).forEach { (range, expected) ->
            for (days in setOf(range.first, range.last, 42).filter { it in range }) {
                val r = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
                    confirmedInterruption = M2CConfirmedInterruptionInput(M2CInterruptionKind.ORDINARY_BREAK, days)))
                assertEquals(expected, r.handoff.interruptionEvidence.bucket)
                assertEquals(days, r.handoff.interruptionEvidence.confirmedBreakDays)
                assertFalse(r.workoutPrescriptionAuthorized)
            }
        }
    }

    @Test fun track2StrengthAndRunningKeepSeparateMechanicalAndSystemicTotals() {
        // Four weeks of 35 km, each with two measured strength sessions. Duration and RPE supply AU.
        val runs = (0..3).flatMap { week -> listOf(7, 7, 8, 13).mapIndexed { i, km ->
            runCan("mixed-run-$week-$i", evalUtc.minusSeconds((week * 7L + 1 + i * 2) * 86400), km * 1000.0, km * 300.0)
        } }
        val lifts = (0..3).flatMap { week -> listOf(2, 5).map { day ->
            runCan("mixed-lift-$week-$day", evalUtc.minusSeconds((week * 7L + day) * 86400),
                null, 2880.0, discipline = AthleteDiscipline.STRENGTH, rawType = "strength")
        } }
        val all = runs + lifts
        val wl = assembleM1C(all)
        val items = all.map { act -> M2CCompletedActivityInput(act, internalLoad = M2CSessionInternalLoadInput(
            accountA, act.canonicalId, Engine15M2CCanonical.aliasesFromCanonicalActivity(act).first(),
            if (act.discipline == AthleteDiscipline.RUN) 4.0 else 7.0, act.startedAtUtc.plusSeconds(5000))) }
        val v = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
            supplementaryActivities = items)).handoff.workloadVector
        assertEquals(35000.0, (v.running7d.totalDistanceBelief as BeliefState.Estimated).estimate.value, 0.0)
        assertEquals(13000.0, v.running90dAnchor.exactObservedMeters!!, 0.0)
        val load = v.internalLoad28d
        assertEquals(2800.0, (load.runningLoadAu as M2CInternalLoadBelief.Estimated).value.value, 0.0)
        assertEquals(2688.0, (load.nonRunningSystemicLoadAu as M2CInternalLoadBelief.Estimated).value.value, 0.0)
        assertEquals(5488.0, (load.combinedSystemicLoadAu as M2CInternalLoadBelief.Estimated).value.value, 0.0)
        assertEquals(8, load.records.count { it.discipline == AthleteDiscipline.STRENGTH && it.loadAu?.value == 336.0 })
        assertFalse(load.impliesModalityInterchangeability || load.establishesSystemicFailureCeiling)
    }

    @Test fun testActiveM2BPaceConflictsSurviveHandoffWithThresholdEvidenceIntact() {
        val wl = assembleM1C(emptyList())
        val start = evalUtc.minusSeconds(20L * 86400L)
        fun benchmark(seconds: Double, epoch: String? = null) = M2ARacePerformanceInput(
            "benchmark", accountA, AthleteDiscipline.RUN, start.atZone(utc).toLocalDate(), Meters.of(10000.0),
            Seconds.of(seconds), EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED, M2APerformanceIntent.VERIFIED_OFFICIAL_RACE,
            "garmin", fitnessEpochId = epoch, observedAtUtc = start)
        val cases = listOf(
            M2BPaceConflict.BENCHMARK_IDENTITY_COLLISION to listOf(benchmark(2460.0), benchmark(1650.0)),
            M2BPaceConflict.SELECTED_BENCHMARK_FITNESS_EPOCH to listOf(benchmark(2460.0, "period-a"), benchmark(2460.0, "period-b"))
        )
        for ((expectedConflict, rows) in cases) {
            val performance = M2APerformanceCalibrationRequest(accountA, evalDate, utc, wl, rows, evalUtc)
            val upstream = Engine15M2BIntensityCalibrator.calibrate(ctxA, performance = performance)
            val handoff = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, upstream)).handoff
            assertEquals(setOf(expectedConflict), handoff.paceConflicts)
            assertEquals(upstream.paceConflicts, handoff.paceConflicts)
            assertFalse(handoff.paceCalibrationEligible)
            assertEquals(upstream.policyVersion, handoff.upstreamM2BPolicyVersion)
            assertEquals(upstream.contentHash, handoff.upstreamM2BContentHash)
            val threshold = handoff.operationalThresholdCalibration!!
            assertEquals(upstream.operationalThresholdCalibration!!.contentHash, threshold.contentHash)
            if (expectedConflict == M2BPaceConflict.BENCHMARK_IDENTITY_COLLISION) {
                assertTrue(threshold.operationalThresholdSpeedBelief is BeliefState.Conflicting)
            } else {
                assertTrue(threshold.operationalThresholdBenchmark!!.hasConflictingFitnessEpoch)
            }
            assertFalse(handoff.hrZoneAuthorized || handoff.workoutPrescriptionAuthorized || handoff.productionAuthorized)
        }
    }

    @Test fun testEstimatedOperationalThresholdSurvivesHandoffWithoutGrantingPaceUnderHeat() {
        val wl = assembleM1C(continuousRunActs(40.0, 16))
        val start = evalUtc.minusSeconds(30L * 86400L)
        val race = M2ARacePerformanceInput("benchmark", accountA, AthleteDiscipline.RUN, start.atZone(utc).toLocalDate(),
            Meters.of(10000.0), Seconds.of(2460.0), EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED,
            M2APerformanceIntent.VERIFIED_OFFICIAL_RACE, "garmin", observedAtUtc = start)
        val performance = M2APerformanceCalibrationRequest(accountA, evalDate, utc, wl, listOf(race), evalUtc)
        val upstream = Engine15M2BIntensityCalibrator.calibrate(ctxA, performance = performance,
            condition = M2BExecutionCondition.ENVIRONMENTAL_CONFOUNDER)
        val handoff = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, upstream)).handoff
        val threshold = handoff.operationalThresholdCalibration!!
        val estimated = threshold.operationalThresholdSpeedBelief as BeliefState.Estimated
        val expected = (10000.0 / 2460.0) * StrictMath.exp((-7.0 / 107.0) * StrictMath.log(3240.0 / 2460.0))
        assertEquals(expected, estimated.estimate.value, 1e-12)
        assertEquals(3240.0, threshold.operationalThresholdReferenceDuration.value, 0.0)
        assertEquals("garmin:benchmark", threshold.operationalThresholdBenchmark!!.representativeId)
        assertEquals(upstream.operationalThresholdCalibration!!.contentHash, threshold.contentHash)
        assertFalse(threshold.isMeasuredPhysiologicalThreshold)
        assertEquals(wl.contentHash, handoff.upstreamObservedWorkloadHash)
        assertFalse(handoff.paceCalibrationEligible || handoff.hrZoneAuthorized)
    }

    @Test fun testM2CRejectsMixedAccountZoneEvaluationInstantAndUpstreamWorkload() {
        val wl = assembleM1C(continuousRunActs(40.0, 16))
        val calibrated = m2bCal(wl = wl, verifiedRace = true)
        val sameDateOtherInstant = M2BEvaluationContext(accountA, AthleteDiscipline.RUN, evalUtc.plusSeconds(1), utc)
        val otherZone = M2BEvaluationContext(accountA, AthleteDiscipline.RUN, evalUtc, ZoneId.of("America/New_York"))
        val otherOwner = M2BEvaluationContext(accountB, AthleteDiscipline.RUN, evalUtc, utc)
        expectRollingBindingFailure("accountId mismatch") { M2CWorkloadIntelligenceRequest(otherOwner, wl, calibrated) }
        expectRollingBindingFailure("zoneId") { M2CWorkloadIntelligenceRequest(otherZone, wl, calibrated) }
        expectRollingBindingFailure("calibration context") { M2CWorkloadIntelligenceRequest(sameDateOtherInstant, wl, calibrated) }
        expectRollingBindingFailure("observedWorkloadContentHash") {
            M2CWorkloadIntelligenceRequest(ctxA, assembleM1C(emptyList()), calibrated)
        }
    }

    @Test fun testExcludedPinnedFutureCompletionAndInvalidDurationDowngradeExactBaseline() {
        val valid = continuousRunActs(40.0, 16)
        val futureCompletion = runCan("pinned-in-progress", evalDate.minusDays(1).atTime(23, 0).atZone(utc).toInstant(),
            40000.0, 14.0 * 3600)
        val invalid = runCan("pinned-invalid-duration", evalUtc.minusSeconds(12L * 86400), 90000.0,
            Engine15M2CPolicyRegistry.MAX_PLAUSIBLE_SESSION_DURATION_SECONDS + 1.0)
        val acts = valid + futureCompletion + invalid
        val sr = ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE, Meters.of(44000.0))
        val wl = assembleM1C(acts, sr = mapOf(AthleteDiscipline.RUN to sr))
        assertTrue(wl.disciplines.getValue(AthleteDiscipline.RUN).exposures.map { it.exposureId }
            .containsAll(listOf(futureCompletion.canonicalId, invalid.canonicalId)))
        val outcome = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
            supplementaryActivities = acts.map { M2CCompletedActivityInput(it) }, evaluatedSingleSessionDistance = Meters.of(11000.0)))
        val handoff = outcome.handoff
        assertEquals(valid.map { it.canonicalId }.toSet(), handoff.workloadVector.running28d.sessions.map { it.canonicalActivityId }.toSet())
        assertTrue("M1C_EXPOSURE_COMPLETION_AFTER_EVALUATION_EXCLUDED" in handoff.diagnosticFlags)
        assertTrue("INVALID_PINNED_DURATION_EXCLUDED" in handoff.diagnosticFlags)
        assertEquals(M2CBaselineArbitrationStatus.PARTIAL_RECORDED_WITH_SELF_REPORT, handoff.baseline.arbitrationStatus)
        assertEquals(40000.0, handoff.baseline.observedLowerBoundWeekly!!.value, 1e-6)
        assertEquals(44000.0, handoff.baseline.provisionalSelfReportWeekly!!.value, 1e-6)
        assertNull((handoff.baseline.observedWeeklyMean as BeliefState.Bounded).upper)
        assertFalse(handoff.baseline.selfReportReplacementAuthorized)
        assertEquals(10000.0, outcome.singleSessionAssessment!!.anchor30d.exactObservedMeters!!, 0.0)
    }

    @Test fun recentZeroSupportsOnlyExplicitFamilyAbsenceAcross56Days() {
        val wl = assembleM1C(emptyList(), sr = mapOf(AthleteDiscipline.RUN to
            ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.CONFIRMED_ZERO, confirmedZero = true)))
        assertEquals(HistoryCoverageStatus.CONFIRMED_ZERO, wl.disciplines.getValue(AthleteDiscipline.RUN).coverage)
        val tempo = M2CQualityWorkoutFamily.TEMPO
        val vo2 = M2CQualityWorkoutFamily.VO2MAX_INTERVALS
        val source = M2ASourceQualifiedAlias("self-report", "family-history")
        val report = M2CFamilySelfReportInput(accountA, vo2, evalUtc, confirmedNoExperience = true, sourceAlias = source)
        val older = runCan("tempo-before-recent-zero", evalUtc.minusSeconds(40L * 86400L))
        val ledger = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(
            ctxA, wl, m2bCal(wl = wl), supplementaryActivities = listOf(M2CCompletedActivityInput(older)),
            qualityObservations = listOf(qualityRow(older, tempo, 900.0, 450.0)), familySelfReports = listOf(report)
        )).handoff.workloadVector.qualityLedger56d
        assertEquals(M2CFamilyExposureState.OBSERVED_COMPLETED, ledger.forFamily(tempo).exposureState)
        val absent = ledger.forFamily(vo2)
        assertEquals(M2CFamilyExposureState.CONFIRMED_ABSENT, absent.exposureState)
        assertEquals(setOf(source), absent.sourceAliases)
        val zero = absent.totalObservedWorkDuration56d as BeliefState.Estimated
        assertEquals(0.0, zero.estimate.value, 0.0)
        assertEquals(EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, zero.authority)
        for (family in M2CQualityWorkoutFamily.entries.filter { it != tempo && it != vo2 }) {
            val unknown = ledger.forFamily(family)
            assertEquals(family.name, M2CFamilyExposureState.UNKNOWN_EXPOSURE, unknown.exposureState)
            assertTrue(unknown.totalObservedWorkDuration56d is BeliefState.Unknown)
        }
    }

    @Test fun sameCanonicalKeyWithDifferentFullHashRejectsQualityObservation() {
        val original = runCan("same-source-identity", evalUtc.minusSeconds(86400L), durationS = 3000.0)
        val contradictory = runCan("same-source-identity", original.startedAtUtc, durationS = 3100.0)
        assertNotEquals(original.canonicalId, contradictory.canonicalId)
        assertNotEquals(original.contentHash, contradictory.contentHash)
        // A normal constructor derives this key from a 64-bit prefix of the full hash.
        // Inject a stale key on this isolated JVM fixture to exercise the full-hash check.
        CanonicalActivity::class.java.getDeclaredField("canonicalId").apply {
            isAccessible = true
            set(contradictory, original.canonicalId)
        }
        assertEquals(original.canonicalId, contradictory.canonicalId)
        val family = M2CQualityWorkoutFamily.TEMPO
        val ledger = componentVector(listOf(original), quality = listOf(qualityRow(contradictory, family, 600.0, 300.0))).qualityLedger56d
        assertTrue("QUALITY_OBSERVATION_CONTENT_HASH_MISMATCH_EXCLUDED" in ledger.diagnosticFlags)
        assertFalse("QUALITY_OBSERVATION_ABSENT_FROM_RECONCILED_INVENTORY_EXCLUDED" in ledger.diagnosticFlags)
        assertEquals(0, ledger.forFamily(family).observedSessionCount56d)
        assertEquals(M2CFamilyExposureState.UNKNOWN_EXPOSURE, ledger.forFamily(family).exposureState)
    }

    @Test fun testForgedExtendedInventoryCannotCombineCollidingCanonicalSources() {
        val day = 86400L
        val digest = "a1".repeat(32)
        val wl = assembleM1C(emptyList())
        fun reconcile(workload: ObservedWorkloadState, vararg rows: CanonicalActivity) =
            Engine15M2CReconciledEvidenceInventoryBuilder.reconcile(ctxA, workload, rows.map { M2CCompletedActivityInput(it) }, emptyList())
        fun splice(base: M2CReconciledEvidenceInventory, rows: List<M2CReconciledRunningEntry>) =
            M2CReconciledEvidenceInventory.create(ctxA, base.observedWorkloadContentHash, base.pinned30dRunningEntries,
                base.sameDayCompletedRunningEntries, rows, base.nonRunningEntries28d, base.collidedCanonicalActivityIds,
                base.unpinnedExcludedCanonicalIds, base.diagnosticFlags)
        val first = runCan("shared-id", evalUtc.minusSeconds(60 * day), 12000.0, 3600.0, sha256 = digest)
        val base = reconcile(wl, first)
        val alternatives = listOf(
            runCan("shared-id", evalUtc.minusSeconds(50 * day), 24000.0, 7200.0),
            runCan("distinct-id", evalUtc.minusSeconds(50 * day), 24000.0, 7200.0, sha256 = digest.uppercase())
        )
        for (second in alternatives) {
            assertNotEquals(first.canonicalId, second.canonicalId)
            val legit = reconcile(wl, first, second)
            assertEquals(setOf(first.canonicalId, second.canonicalId), legit.collidedCanonicalActivityIds)
            assertTrue(legit.extendedHistorical31to90RunningEntries.isEmpty())
            val single = reconcile(wl, second)
            assertEquals(24000.0, M2CVerifiedSingleSessionAnchor.fromReconciled90dInventory(ctxA, wl, single).exactObservedMeters!!, 0.0)
            val forged = splice(base, base.extendedHistorical31to90RunningEntries + single.extendedHistorical31to90RunningEntries)
            val failure = runCatching { M2CVerifiedSingleSessionAnchor.fromReconciled90dInventory(ctxA, wl, forged) }.exceptionOrNull()
            assertTrue("Spliced source identities must not authorize a 24 km override anchor", failure is IllegalArgumentException)
        }

        val pinned = runCan("pin-with-sha", evalUtc.minusSeconds(2 * day), 8000.0, 2400.0, sha256 = digest)
        val pinnedWl = assembleM1C(listOf(pinned))
        val pinnedInv = reconcile(pinnedWl, pinned)
        val forged = splice(pinnedInv, base.extendedHistorical31to90RunningEntries)
        val failure = runCatching { M2CVerifiedSingleSessionAnchor.fromReconciled90dInventory(ctxA, pinnedWl, forged) }.exceptionOrNull()
        assertTrue("Extended evidence colliding with a pinned canonical source must be rejected", failure is IllegalArgumentException)
    }

    @Test fun testForgedExtendedRecordCannotAddAmbiguityGroupToUnambiguousCanonicalEvidence() {
        val wl = assembleM1C(emptyList())
        val a = runCan("single-origin", evalUtc.minusSeconds(60L * 86400))
        val inv = Engine15M2CReconciledEvidenceInventoryBuilder.reconcile(ctxA, wl, listOf(M2CCompletedActivityInput(a)), emptyList())
        val entry = inv.extendedHistorical31to90RunningEntries.single()
        val r = entry.record
        assertNull(r.ambiguityGroupKey)
        val altered = M2CVerifiedRunningSessionRecord(r.canonicalActivityId, r.accountId, r.startedAtUtc, r.completedAtUtc,
            r.attributedLocalDate, r.completedLocalDate, r.crossMidnightStartToEnd, r.distance, r.duration, r.distanceQuality,
            r.durationQuality, r.hrQuality, r.workloadEligibility, r.fromCompletedPlanLogOnly, r.sameDayAsEvaluation,
            "invented-group", r.sourceAliases, r.opaqueUpstreamRecordIds)
        val forged = M2CReconciledEvidenceInventory.create(ctxA, wl.contentHash, emptyList(), emptyList(),
            listOf(entry.copy(record = altered)), emptyList(), emptySet(), emptySet(), emptySet())
        val failure = runCatching { M2CVerifiedSingleSessionAnchor.fromReconciled90dInventory(ctxA, wl, forged) }.exceptionOrNull()
        assertTrue("A verified unique CanonicalActivity cannot carry an ambiguity group", failure is IllegalArgumentException)
    }

    @Test fun transferTableCannotBeChangedThroughReturnedFamily() {
        val sources = M2CQualityWorkoutFamily.TEMPO.partialTransferSources
        assertTrue("THRESHOLD" in sources)
        // Re-adding the existing member exercises mutation without changing shared test state.
        assertThrows(UnsupportedOperationException::class.java) { (sources as MutableSet<String>).add("THRESHOLD") }
    }

    @Test fun opaqueExternalIdAloneCannotValidateAnAttachedSessionRpe() {
        val act = runCan("opaque-native-id", evalUtc.minusSeconds(86400))
        val wl = assembleM1C(listOf(act))
        val assessed = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl)))
        val entry = assessed.reconciledInventory.pinned30dRunningEntries.single()
        assertTrue(entry.record.sourceAliases.isEmpty())
        val claimedRpe = M2CSessionInternalLoadInput(accountA, act.canonicalId, M2ASourceQualifiedAlias("manual", "opaque-native-id"), 7.0, evalUtc)
        val window = assessed.handoff.workloadVector.running28d
        val flags = mutableSetOf<String>()
        val (summary, _) = Engine15M2CInternalAndDownhillEngine.computeReconciledInternalLoadAndMultimodal(
            ctxA, window.bounds, listOf(entry.copy(internalLoad = claimedRpe)), emptyList(), window, flags)
        assertTrue("RPE_OPAQUE_UPSTREAM_SOURCE_UNVALIDATED_EXCLUDED" in flags)
        assertTrue(summary.runningLoadAu is M2CInternalLoadBelief.Unknown)
        assertEquals(1, summary.invalidRpeExcludedCount)
        assertNull(summary.records.single().sourceAlias)
        assertEquals(setOf("opaque-native-id"), summary.records.single().opaqueUpstreamRecordIds)
        assertEquals(10000.0, entry.record.distance!!.value, 0.0)
        assertEquals(3000.0, summary.records.single().duration.value, 0.0)
    }

    @Test fun unrepresentablePolicyQuantitiesPreserveIndependentEvidence() {
        val giant = runCan("finite-max", evalUtc.minusSeconds(86400), Double.MAX_VALUE, 3600.0)
        val wl = assembleM1C(listOf(giant))
        val session = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
            evaluatedSingleSessionDistance = Meters.of(Double.MAX_VALUE))).singleSessionAssessment!!
        assertEquals(1.0, session.r30Ratio!!.value, 0.0)
        assertNull(session.defaultGuardrailCapMeters)
        assertFalse(session.policyGuardrailExceeded)
        assertTrue("SINGLE_SESSION_DISTANCE_CAP_OVERFLOW" in session.notes)

        val history = continuousRunActs(48.0, 16)
        val tinyReport = ProvisionalSelfReportWorkload(AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE,
            Meters.of(Double.MIN_VALUE))
        val mixed = assembleM1C(history, sr = mapOf(AthleteDiscipline.RUN to tinyReport))
        val baseline = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, mixed, m2bCal(wl = mixed))).handoff.baseline
        assertEquals(48000.0, baseline.observedLowerBoundWeekly!!.value, 1e-6)
        assertTrue(baseline.conflictDetected)
        assertNull(baseline.pol013RecordedToSelfReportRatio)
        assertTrue("RECORDED_TO_SELF_REPORT_RATIO_OVERFLOW" in baseline.diagnosticFlags)
    }

    @Test fun allRecordedSessionsClassifiedStillLeavesHistoricalIntensityOpen() {
        val run = runCan("fully-classified", evalUtc.minusSeconds(86400), 8000.0, 2400.0)
        val row = M2CWorkoutDomainBreakdownInput(accountA, run.canonicalId,
            Engine15M2CCanonical.aliasesFromCanonicalActivity(run).single(), evalUtc,
            moderateWorkSeconds = 2100.0, heavyWorkSeconds = 300.0, severeWorkSeconds = 0.0,
            neuromuscularWorkSeconds = 0.0, warmUpCoolDownRecoverySeconds = 0.0,
            moderateWorkMeters = 7000.0, heavyWorkMeters = 1000.0, severeWorkMeters = 0.0,
            neuromuscularWorkMeters = 0.0, warmUpCoolDownRecoveryMeters = 0.0)
        val vector = componentVector(listOf(run), listOf(M2CCompletedActivityInput(run, domainBreakdown = row)))
        val classified = vector.intensityExposure28d
        assertEquals(1, classified.fullyClassifiedDurationSessionCount)
        assertEquals(1, classified.fullyClassifiedDistanceSessionCount)
        assertEquals(0, classified.unclassifiedOrIncompleteSessionCount)
        val duration = classified.heavyWorkDuration as BeliefState.Bounded
        val distance = classified.heavyWorkDistance as BeliefState.Bounded
        assertEquals(300.0, duration.lower!!.value, 0.0)
        assertEquals(1000.0, distance.lower!!.value, 0.0)
        assertNull(duration.upper)
        assertNull(distance.upper)
        assertTrue(classified.unclassifiedRunningDuration is BeliefState.Unknown)
        assertTrue(classified.severeWorkDistance is BeliefState.Unknown)
    }

    @Test fun onlyUnpinnedAerobicTimeCannotEstablishRunningCapacity() {
        val ride = runCan("descriptive-ride", evalUtc.minusSeconds(86400), 30000.0, 3600.0,
            discipline = AthleteDiscipline.CONDITIONING, rawType = "cycling")
        val wl = assembleM1C(emptyList())
        val r = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
            supplementaryActivities = listOf(M2CCompletedActivityInput(ride)), evaluatedSingleSessionDistance = Meters.of(6000.0)))
        val mixed = r.handoff.workloadVector.systemicMultimodal28d
        val time = mixed.byModality.getValue(M2CModalityKind.CYCLING).totalDuration28d as BeliefState.Bounded
        assertEquals(3600.0, time.lower!!.value, 0.0)
        assertEquals(EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, time.authority)
        assertFalse(mixed.crossModalRunningTissueMismatchSignal || mixed.nonRunningFabricatesRunningImpactTolerance)
        assertNull(r.singleSessionAssessment!!.r30Ratio)
        assertTrue(r.handoff.baseline.observedWeeklyMean is BeliefState.Unknown)
    }

    @Test fun t7D08MissedLongRunDoesNotRaiseEighteenPointFiveKmReference() {
        val run = runCan("last-long-run", evalUtc.minusSeconds(7 * 86400L), 18500.0, 5550.0)
        val wl = assembleM1C(listOf(run))
        val skipped = M2CCompletedActivityInput(runCan("skipped-20k", evalUtc.minusSeconds(4 * 86400L),
            20000.0, 6000.0), isProjected = true)
        for ((distance, exceeded) in listOf(22000.0 to true, 20000.0 to false)) {
            val result = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl,
                m2bCal(wl = wl), supplementaryActivities = listOf(skipped), evaluatedSingleSessionDistance = Meters.of(distance)))
            val ss = result.singleSessionAssessment!!
            assertEquals(distance / 18500.0, ss.r30Ratio!!.value, 1e-12)
            assertEquals(exceeded, ss.policyGuardrailExceeded)
            assertEquals(setOf(run.canonicalId), ss.anchor30d.canonicalActivityIds)
        }
    }

    @Test fun track2FastFiveKmKeepsVerifiedPaceAndIndependentLowMileageCeiling() {
        val acts = (0..3).flatMap { week -> listOf(6, 7, 9).mapIndexed { i, km ->
            runCan("fast-five-$week-$i", evalUtc.minusSeconds((week * 7L + 1 + 3 * i) * 86400),
                km * 1000.0, km * 300.0)
        } }
        val wl = assembleM1C(acts)
        val race = M2ARacePerformanceInput("nineteen-minute-five", accountA, AthleteDiscipline.RUN, evalDate.minusDays(10),
            Meters.of(5000.0), Seconds.of(1140.0), EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED,
            M2APerformanceIntent.VERIFIED_OFFICIAL_RACE, "garmin", observedAtUtc = evalUtc.minusSeconds(10 * 86400L))
        val upstream = Engine15M2BIntensityCalibrator.calibrate(ctxA, performance = M2APerformanceCalibrationRequest(
            accountA, evalDate, utc, wl, listOf(race), evalUtc))
        val r = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, upstream,
            evaluatedSingleSessionDistance = Meters.of(10000.0)))
        assertTrue(r.handoff.paceCalibrationEligible)
        assertEquals(22000.0, (r.handoff.baseline.observedWeeklyMean as BeliefState.Estimated).estimate.value, 1e-8)
        assertEquals(9900.0, r.singleSessionAssessment!!.defaultGuardrailCapMeters!!.value, 1e-8)
        assertTrue(r.singleSessionAssessment!!.policyGuardrailExceeded)
        assertFalse(r.handoff.effectiveRestrictionState.hold || r.handoff.effectiveRestrictionState.recoveryOnly)
    }

    @Test fun mod10SpikeBandBoundariesAndOverrideScope() {
        val recent = runCan("band-10k", evalUtc.minusSeconds(2L * 86400), 10000.0)
        val older = runCan("band-20k", evalUtc.minusSeconds(90L * 86400), 20000.0)
        val outside = runCan("excluded-30k", evalUtc.minusSeconds(91L * 86400), 30000.0)
        val wl = assembleM1C(listOf(recent))
        val evidence = listOf(recent, older, outside).map { M2CCompletedActivityInput(it) }
        val neutral = setOf(M2BRestrictionReason.NO_RESTRICTION_REPORTED)
        fun ack(metres: Double) = M2CSingleSessionOverrideAcknowledgment(accountA, ctxA, Meters.of(metres),
            Engine15M2CPolicyRegistry.POLICY_VERSION, neutral, evalUtc)
        fun assess(metres: Double, acknowledgment: M2CSingleSessionOverrideAcknowledgment? = null,
                   restriction: M2BRestrictionStatus = M2BRestrictionStatus.NoRestrictionReported) =
            Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl,
                m2bCal(wl = wl, restriction = restriction), supplementaryActivities = evidence,
                evaluatedSingleSessionDistance = Meters.of(metres), singleSessionOverrideAcknowledgment = acknowledgment)
            ).singleSessionAssessment!!
        val bands = listOf(11000.0 to M2CSingleSessionSpikeBand.WITHIN_DEFAULT_GUARDRAIL_LE_1_10,
            11000.1 to M2CSingleSessionSpikeBand.SMALL_SPIKE_GT_1_10_TO_1_30,
            13000.0 to M2CSingleSessionSpikeBand.SMALL_SPIKE_GT_1_10_TO_1_30,
            13000.1 to M2CSingleSessionSpikeBand.MODERATE_SPIKE_GT_1_30_TO_2_00,
            20000.0 to M2CSingleSessionSpikeBand.MODERATE_SPIKE_GT_1_30_TO_2_00,
            20000.1 to M2CSingleSessionSpikeBand.LARGE_SPIKE_GT_2_00)
        for ((metres, expected) in bands) {
            val a = assess(metres, ack(metres))
            assertEquals(expected, a.spikeBand)
            assertEquals(metres > 11000.0, a.observationalRiskSignalActive)
            assertEquals(metres > 11000.0, a.policyGuardrailExceeded)
            assertEquals(metres > 11000.0 && metres <= 20000.0, a.overrideEligibleAndAcknowledgedPreM9)
            assertFalse(a.overrideErasesRiskSignal || a.overrideGrantsProductionAuthority || a.isIndividualInjuryProbability)
        }
        assertEquals(M2CSingleSessionOverrideEligibility.INELIGIBLE_EXCEEDS_90D_VERIFIED_MAX,
            assess(20000.1, ack(20000.1)).overrideEligibility)
        val bound = ack(20000.0)
        val olderContext = ctxA.copy(asOf = evalUtc.minusSeconds(1))
        val foreignContext = ctxA.copy(accountId = accountB)
        for (wrong in listOf(bound.copy(proposedDistance = Meters.of(19999.0)), bound.copy(policyVersion = "superseded"),
            bound.copy(acknowledgedRestrictionReasons = setOf(M2BRestrictionReason.HR_QUARANTINE)),
            bound.copy(evaluatedFor = olderContext, acknowledgedAtUtc = olderContext.asOf),
            bound.copy(accountId = accountB, evaluatedFor = foreignContext))) {
            val a = assess(20000.0, wrong)
            assertFalse(a.overrideAcknowledgmentMatched || a.overrideEligibleAndAcknowledgedPreM9)
            assertTrue(a.observationalRiskSignalActive)
            assertTrue("OVERRIDE_ACKNOWLEDGMENT_BINDING_MISMATCH_REJECTED" in a.notes)
        }
        assertFalse(assess(20000.0).overrideEligibleAndAcknowledgedPreM9)
        val held = assess(20000.0, bound.copy(acknowledgedRestrictionReasons = setOf(M2BRestrictionReason.PERSISTENT_HOLD)),
            M2BRestrictionStatus.PersistentActive(ctxA))
        assertTrue(held.overrideAcknowledgmentMatched)
        assertTrue(held.healthRestrictionBlocksOverride && held.observationalRiskSignalActive)
        assertEquals(M2CSingleSessionOverrideEligibility.INELIGIBLE_ACTIVE_HEALTH_RESTRICTION, held.overrideEligibility)
        assertFalse(held.overrideEligibleAndAcknowledgedPreM9 || held.overrideBypassesHealthRestrictions)
    }

    @Test fun pol011And012ReplacementRequiresAllThreeInclusiveCoverageThresholds() {
        val sr = mapOf(AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(AthleteDiscipline.RUN,
            RecentMileageBasis.SELF_REPORTED_AVERAGE, Meters.of(15000.0)))
        fun baseline(offsets: List<Int>): M2CCoverageAwareBaseline {
            val acts = offsets.map { day -> runCan("cov-$day", evalUtc.minusSeconds(day * 86400L)) }
            val wl = assembleM1C(acts, sr = sr)
            return Engine15M2CWorkloadIntelligence.assess(
                M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl))).handoff.baseline
        }
        val edge = baseline(listOf(21, 11, 8, 6, 4, 2))
        assertEquals(21, edge.observedSpanDays)
        assertEquals(6, edge.verifiedSessionCount28d)
        assertEquals(10, edge.maxGapDays)
        assertTrue(edge.selfReportReplacementAuthorized)
        for (offsets in listOf(listOf(20, 10, 8, 6, 4, 2), listOf(21, 11, 8, 6, 2), listOf(21, 10, 8, 6, 4, 2))) {
            val short = baseline(offsets)
            assertEquals(M2CBaselineArbitrationStatus.PARTIAL_RECORDED_WITH_SELF_REPORT, short.arbitrationStatus)
            assertFalse(short.selfReportReplacementAuthorized)
            assertEquals(Meters.of(15000.0), short.provisionalSelfReportWeekly)
        }
    }

    @Test fun pol013ExactRatioThresholdsAreNeutralAndOutsideValuesConflict() {
        val sr = mapOf(AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(AthleteDiscipline.RUN,
            RecentMileageBasis.SELF_REPORTED_AVERAGE, Meters.of(40000.0)))
        for ((weeklyKm, state) in listOf(
            25.999 to M2CBaselineArbitrationStatus.CONFLICT_CONSERVATIVE_CEILING,
            26.0 to M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED,
            60.0 to M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED,
            60.001 to M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED_CONFLICT_ABOVE_SELF_REPORT
        )) {
            val wl = assembleM1C(continuousRunActs(weeklyKm, 16), sr = sr)
            val b = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl))).handoff.baseline
            assertEquals(state, b.arbitrationStatus)
            assertEquals(weeklyKm / 40.0, b.pol013RecordedToSelfReportRatio!!.value, 1e-12)
            assertEquals(state != M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED, b.conflictDetected)
        }
    }

    @Test fun contradictoryDomainAndDownhillDuplicatesPreserveIndependentRpeAndDistance() {
        val start = evalUtc.minusSeconds(2 * 86400L)
        val run = runCan("duplicate-metrics", start, 400.0, 90.0)
        val alias = Engine15M2CCanonical.aliasesFromCanonicalActivity(run).first()
        val origin = M2BStreamIdentity.of(run, run.rawComponents.single())
        val wl = assembleM1C(listOf(run))
        val calibration = m2bCal(wl = wl)
        val rpe = M2CSessionInternalLoadInput(accountA, run.canonicalId, alias, 6.0, start.plusSeconds(120))
        val domain = M2CWorkoutDomainBreakdownInput(accountA, run.canonicalId, alias, start.plusSeconds(120),
            moderateWorkSeconds = 60.0)
        fun stream(endElevation: Double) = M2BRunningStream(run, alias, M2BSurface.NON_TECHNICAL_ROAD,
            emptyList(), M2BRecordingWindow(origin, M2BDistanceSemantics.HORIZONTAL, run.telemetryQuality,
                listOf(M2BRecordingSample(start, Meters.ZERO, 100.0, M2BContinuityEvidence.UNKNOWN),
                    M2BRecordingSample(start.plusSeconds(90), Meters.of(400.0), endElevation,
                        M2BContinuityEvidence.ACTIVE_CONTIGUOUS))))
        val base = M2CCompletedActivityInput(run, internalLoad = rpe, domainBreakdown = domain, runningStream = stream(90.0))
        fun check(domainConflict: Boolean, streamConflict: Boolean) {
            val other = M2CCompletedActivityInput(run, domainBreakdown = if (domainConflict) domain.copy(moderateWorkSeconds = 30.0) else domain,
                runningStream = stream(if (streamConflict) 80.0 else 90.0))
            fun assess(items: List<M2CCompletedActivityInput>) = Engine15M2CWorkloadIntelligence.assess(
                M2CWorkloadIntelligenceRequest(ctxA, wl, calibration, supplementaryActivities = items))
            val res = assess(listOf(base, other))
            val entry = res.reconciledInventory.pinned30dRunningEntries.single()
            assertEquals(res.contentHash, assess(listOf(other, base)).contentHash)
            assertEquals(domainConflict, entry.domainBreakdownConflict)
            assertEquals(domainConflict, entry.domainBreakdown == null)
            assertEquals(streamConflict, entry.downhillStreamConflict)
            assertEquals(streamConflict, entry.runningStream == null)
            assertEquals(rpe, entry.internalLoad)
            assertFalse(entry.internalLoadConflict)
            val vector = res.handoff.workloadVector
            assertEquals(9.0, (vector.internalLoad28d.runningLoadAu as M2CInternalLoadBelief.Estimated).value.value, 0.0)
            assertEquals(400.0, (vector.running30d.totalDistanceBelief as BeliefState.Estimated).estimate.value, 0.0)
            assertEquals(domainConflict, "CONTRADICTORY_DUPLICATE_DOMAIN_BREAKDOWN_EXCLUDED" in res.handoff.diagnosticFlags)
            assertEquals(streamConflict, "CONTRADICTORY_DUPLICATE_DOWNHILL_STREAM_EXCLUDED" in res.handoff.diagnosticFlags)
        }
        check(true, false)
        check(false, true)
        check(true, true)
    }

    @Test fun t2S12ErraticWeeklyDispersionAndSessionPeakRemainSeparate() {
        val kilometres = listOf(listOf(5, 5), listOf(12, 12, 13, 18), emptyList(), listOf(12, 12, 12, 12))
        val runs = kilometres.flatMapIndexed { offset, week -> week.mapIndexed { day, km ->
            runCan("erratic-$offset-$day", evalUtc.minusSeconds((offset * 7L + day + 1) * 86400L),
                km * 1000.0, km * 300.0)
        } }
        val wl = assembleM1C(runs)
        val r = Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl, m2bCal(wl = wl),
            weeklyConfirmations = listOf(M2CWeeklyConfirmationInput(evalDate.minusDays(21), true)),
            evaluatedSingleSessionDistance = Meters.of(19800.0)))
        val weekly = r.handoff.workloadVector.weeklyDistribution28d
        assertEquals(28250.0, weekly.meanWeeklyDistance!!.value, 1e-9)
        assertEquals(29000.0, weekly.medianWeeklyDistance!!.value, 1e-9)
        assertEquals(29000.0, weekly.trimmedMeanWeeklyDistance!!.value, 1e-9)
        val sd = kotlin.math.sqrt(listOf(10000.0, 55000.0, 0.0, 48000.0).sumOf { (it - 28250.0) * (it - 28250.0) } / 3)
        assertEquals(sd, weekly.sampleStdDevWeeklyDistance!!.value, 1e-8)
        assertEquals(sd / 28250.0, weekly.coefficientOfVariation!!.value, 1e-12)
        assertEquals(18000.0, r.singleSessionAssessment!!.anchor30d.exactObservedMeters!!, 0.0)
        assertFalse(weekly.authorizesStabilizationAction)
    }
    private fun missingDistancePairAt(knownAt: Instant, unknownAt: Instant, unknownDuration: Double = 1500.0): List<CanonicalActivity> {
        fun member(id: String, other: String, at: Instant, distance: Double?, duration: Double) =
            runCan(id, at, distance, duration,
                distQuality = if (distance == null) TelemetryQualityStatus.MISSING_TELEMETRY else TelemetryQualityStatus.VALID_OBSERVED,
                dedup = CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE,
                ambiguities = listOf(ActivityAmbiguityNotice(ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE,
                    other, "garmin", "POSSIBLE_DUPLICATE")))
        return listOf(member("ir-known", "ir-unknown", knownAt, 8000.0, 2400.0),
            member("ir-unknown", "ir-known", unknownAt, null, unknownDuration))
    }

    private fun assessAmbiguousDistance(activities: List<CanonicalActivity>): Engine15M2CWorkloadAssessment {
        val wl = assembleM1C(activities)
        return Engine15M2CWorkloadIntelligence.assess(M2CWorkloadIntelligenceRequest(ctxA, wl,
            m2bCal(wl = wl), supplementaryActivities = activities.map { M2CCompletedActivityInput(it) },
            evaluatedSingleSessionDistance = Meters.of(9000.0)))
    }

    private fun assertDistanceFloor(belief: BeliefState<Meters>, expected: Double?) {
        if (expected == null) {
            assertTrue("Expected unknown but got $belief", belief is BeliefState.Unknown)
        } else {
            assertTrue("Expected bounded but got $belief", belief is BeliefState.Bounded)
            val bounded = belief as BeliefState.Bounded
            assertEquals(expected, bounded.lower!!.value, 0.0)
            assertNull(bounded.upper)
        }
    }

    private fun assertConsistentAmbiguousDistance(result: Engine15M2CWorkloadAssessment, verifiedMeters: Double?) {
        val v = result.handoff.workloadVector
        for (window in listOf(v.running7d, v.running28d, v.running30d)) {
            assertDistanceFloor(window.totalDistanceBelief, verifiedMeters)
            assertDistanceFloor(window.longestSessionDistanceBelief, verifiedMeters)
        }
        val weekly = v.weeklyDistribution28d
        assertDistanceFloor(weekly.weeklyBuckets.first().distanceBelief, verifiedMeters)
        assertEquals(0, weekly.exactRecordedSubtotalWeekCount)
        assertEquals(0, weekly.completeActualWeeklyWorkloadCount)
        val baseline = result.handoff.baseline
        assertEquals(verifiedMeters?.div(4), baseline.observedLowerBoundWeekly?.value)
        assertDistanceFloor(baseline.observedWeeklyMean, verifiedMeters?.div(4))
        assertFalse(baseline.selfReportReplacementAuthorized)
        val session = result.singleSessionAssessment!!
        assertDistanceFloor(session.anchor30d.distanceBelief, verifiedMeters)
        assertDistanceFloor(session.anchor90d.distanceBelief, verifiedMeters)
        assertNull(session.r30Ratio)
        assertFalse(result.productionAuthorized)
    }

    @Test fun ir01EightKilometresWithUnknownAlternativeHasNoPositiveFloor() {
        val start = evalUtc.minusSeconds(3 * 86400L)
        val pair = missingDistancePairAt(start, start)
        val frozen = assembleM1C(pair).disciplines.getValue(AthleteDiscipline.RUN)
        assertEquals(8000.0, (frozen.rolling28dDistanceBelief as BeliefState.Bounded).lower!!.value, 0.0)
        assertConsistentAmbiguousDistance(assessAmbiguousDistance(pair), null)
    }

    @Test fun ir01IndependentVerifiedRunSuppliesTheOnlyPositiveFloor() {
        val start = evalUtc.minusSeconds(3 * 86400L)
        val pair = missingDistancePairAt(start, start)
        val independent = runCan("ir-verified", evalUtc.minusSeconds(5 * 86400L), 6000.0)
        assertConsistentAmbiguousDistance(assessAmbiguousDistance(pair + independent), 6000.0)
    }

    @Test fun ir01FutureCompletionCannotRestoreAnAmbiguousGroupMinimum() {
        val start = evalUtc.minusSeconds(3 * 86400L)
        val stillActive = evalDate.minusDays(1).atTime(23, 55).toInstant(utc)
        val pair = missingDistancePairAt(start, stillActive, 14 * 3600.0)
        val independent = runCan("ir-verified", evalUtc.minusSeconds(5 * 86400L), 6000.0)
        val result = assessAmbiguousDistance(pair + independent)
        assertTrue("M1C_EXPOSURE_COMPLETION_AFTER_EVALUATION_EXCLUDED" in result.handoff.diagnosticFlags)
        assertFalse(result.handoff.workloadVector.running30d.sessions.any { it.canonicalActivityId == pair[1].canonicalId })
        assertConsistentAmbiguousDistance(result, 6000.0)
    }

    @Test fun ir01MissingAlternativeAcrossWeeklyBoundaryDoesNotCertifyRetainedDistance() {
        val pair = missingDistancePairAt(evalUtc.minusSeconds(7 * 86400L), evalUtc.minusSeconds(8 * 86400L))
        val independent = runCan("ir-verified", evalUtc.minusSeconds(5 * 86400L), 6000.0)
        assertConsistentAmbiguousDistance(assessAmbiguousDistance(pair + independent), 6000.0)
    }

    @Test fun ir01OrdinaryContinuousRecordedWorkloadRetainsAuthority() {
        val runs = continuousRunActs(48.0, 16)
        val frozen = assembleM1C(runs).disciplines.getValue(AthleteDiscipline.RUN)
        val result = assessAmbiguousDistance(runs)
        val v = result.handoff.workloadVector
        assertEquals(frozen.rolling7dDistanceBelief, v.running7d.totalDistanceBelief)
        assertEquals(frozen.rolling28dDistanceBelief, v.running28d.totalDistanceBelief)
        assertEquals(frozen.preceding30dLongestSessionBelief, v.running30d.longestSessionDistanceBelief)
        assertEquals(frozen.rolling28dWeeklyAverageBelief, result.handoff.baseline.observedWeeklyMean)
        assertEquals(M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED, result.handoff.baseline.arbitrationStatus)
        assertTrue(result.handoff.baseline.selfReportReplacementAuthorized)
        assertEquals(4, v.weeklyDistribution28d.exactRecordedSubtotalWeekCount)
        assertEquals(12000.0, result.singleSessionAssessment!!.anchor30d.exactObservedMeters!!, 0.0)
    }
}

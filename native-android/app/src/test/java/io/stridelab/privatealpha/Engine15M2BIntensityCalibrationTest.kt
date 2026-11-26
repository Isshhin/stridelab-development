package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Engine15M2BIntensityCalibrationTest {
    private val instant = Instant.parse("2026-11-26T00:00:00Z")
    private val scope = M2BEvaluationContext("athlete", AthleteDiscipline.RUN, instant, ZoneOffset.UTC)

    @Test
    fun minettiUsesHorizontalGradeAndDistanceAlongTheSlope() {
        val pathDistance = Math.hypot(100.0, 10.0)
        val result = Engine15M2BIntensityModels.minetti(
            Meters.of(100.0), 10.0, Seconds.of(pathDistance / 2.5)
        ) as M2BGradeAdjustment.Computed

        assertEquals(0.10, result.grade.value, 1e-12)
        assertEquals(pathDistance, result.pathDistance.value, 1e-12)
        assertEquals(2.5, result.observedSpeed.value, 1e-12)
        assertEquals(2.5 * 5.968214 / 3.6, result.adjustedSpeed.value, 1e-12)
    }

    @Test
    fun minettiFlatDistanceIsUnchanged() {
        val result = Engine15M2BIntensityModels.minetti(
            Meters.of(400.0), 0.0, Seconds.of(90.0)
        ) as M2BGradeAdjustment.Computed

        assertEquals(400.0, result.adjustedDistance.value, 1e-12)
        assertEquals(400.0 / 90.0, result.adjustedSpeed.value, 1e-12)
    }

    @Test
    fun minettiAbstainsAtOutOfDomainAndEccentricBoundaries() {
        fun calculate(rise: Double) = Engine15M2BIntensityModels.minetti(
            Meters.of(100.0), rise, Seconds.of(40.0)
        )

        assertTrue(calculate(45.0) is M2BGradeAdjustment.Computed)
        assertTrue(calculate(-15.0) is M2BGradeAdjustment.Computed)
        assertEquals(M2BGradeAdjustment.Abstained.ECCENTRIC_DOMINATED, calculate(-15.01))
        assertEquals(M2BGradeAdjustment.Abstained.OUT_OF_DOMAIN, calculate(-65.0))
        assertEquals(M2BGradeAdjustment.Abstained.OUT_OF_DOMAIN, calculate(45.01))
        assertEquals(M2BGradeAdjustment.Abstained.INVALID_SEGMENT, calculate(Double.NaN))
    }

    @Test
    fun tanakaIsAnEducationalPriorWithResidualFloor() {
        val result = Engine15M2BIntensityModels.tanaka(40)
        val prior = result.belief as BeliefState.PriorOnly

        assertEquals(180.0, prior.prior.toBpm(), 1e-12)
        assertEquals(10.0, result.residualFloor?.toBpm() ?: Double.NaN, 1e-12)
        assertFalse(prior.authorizesZones)
        assertFalse(prior.canParticipateInZoneAuthorization)
    }

    @Test
    fun tanakaAbstainsOutsideItsAdultDomain() {
        for (age in listOf(null, 17, 91)) {
            val result = Engine15M2BIntensityModels.tanaka(age)
            assertTrue(result.belief is BeliefState.Unknown)
            assertNull(result.residualFloor)
        }
        assertTrue(Engine15M2BIntensityModels.tanaka(18).belief is BeliefState.PriorOnly)
        assertTrue(Engine15M2BIntensityModels.tanaka(90).belief is BeliefState.PriorOnly)
    }

    @Test
    fun descriptiveHrrPreservesFormulaAndRejectsInvalidDenominator() {
        fun hrr(current: Double, rest: Double, max: Double) = Engine15M2BIntensityModels.hrrRatio(
            BeatsPerSecond.fromBpm(current), BeatsPerSecond.fromBpm(rest), BeatsPerSecond.fromBpm(max)
        )

        assertEquals(0.75, hrr(150.0, 54.0, 182.0)?.value ?: Double.NaN, 1e-12)
        assertTrue((hrr(190.0, 54.0, 182.0)?.value ?: 0.0) > 1.0)
        assertTrue((hrr(50.0, 54.0, 182.0)?.value ?: 0.0) < 0.0)
        assertThrows(IllegalArgumentException::class.java) { BeatsPerSecond.of(0.0) }
        assertNull(hrr(150.0, 54.0, 54.0))
        assertNull(hrr(150.0, 182.0, 54.0))
    }

    @Test
    fun observedEnvelopeUsesExactDurationAndExpiresAfterDaySixty() {
        val day60 = instant.minus(Duration.ofDays(60))
        val stream = runningStream(day60)
        val eligible = Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(stream))

        assertEquals(400.0 / 90.0, eligible.at(Seconds.of(90.0))?.rawSpeed?.value ?: Double.NaN, 1e-12)
        assertNull(eligible.at(Seconds.of(89.0)))
        assertNull(eligible.at(Seconds.of(91.0)))
        assertTrue(
            Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(runningStream(day60.minusSeconds(86400))))
                .bounds.isEmpty()
        )
    }

    @Test
    fun elapsedSessionAverageIsNeverAContinuousBound() {
        val stream = runningStream(instant.minusSeconds(1)).let {
            M2BRunningStream(it.activity, it.source, it.surface, emptyList())
        }
        val result = Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(stream))
        assertTrue(result.bounds.isEmpty())
        assertEquals(400.0 / 90.0, result.elapsedAverages.single().rawSpeed.value, 1e-12)
    }

    @Test
    fun unrelatedGapStreamCannotContributeEvenWithEqualDuration() {
        val reference = runningStream(instant.minusSeconds(1))
        val other = runningStream(instant.minusSeconds(1), id = "same-duration-different-record")
        val borrowed = M2BRunningStream(reference.activity, reference.source, reference.surface, other.segments)
        val result = Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(borrowed))
        assertTrue(result.bounds.isEmpty())
        assertEquals(1, result.elapsedAverages.size)
    }

    @Test
    fun envelopeRejectsFutureAndForeignEvidence() {
        val future = runningStream(instant.plusSeconds(1))
        val foreign = runningStream(instant.minusSeconds(1), account = "other")
        val result = Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(future, foreign))
        assertTrue(result.bounds.isEmpty())
        assertTrue(result.elapsedAverages.isEmpty())
    }

    @Test
    fun duplicateStreamsYieldOneImmutableBoundUnderPermutation() {
        val stream = runningStream(instant.minusSeconds(1))
        val stale = runningStream(instant.minus(Duration.ofDays(61)), id = "stale")
        val inputs = listOf(stream, stream, stale)
        val first = Engine15M2BPerformanceLowerEnvelope.build(scope, inputs)
        val second = Engine15M2BPerformanceLowerEnvelope.build(scope, inputs.reversed())
        assertEquals(first.bounds, second.bounds)
        assertEquals(1, first.bounds.size)
        assertThrows(UnsupportedOperationException::class.java) { (first.bounds as MutableList).clear() }
    }

    @Test
    fun unresolvedPersistentHoldWithholdsEvenEffortGuidance() {
        val status = M2BRestrictionStatus.PersistentActive(scope)
        val result = Engine15M2BIntensityCalibrator.calibrate(scope, restriction = status)
        assertTrue(result.hold)
        assertNull(result.guidance)

        val replay = Engine15M2BIntensityCalibrator.calibrate(
            scope.copy(asOf = scope.asOf.plusSeconds(1)), restriction = status
        )
        assertTrue(replay.hold)
        assertNull(replay.guidance)
        val claimed = Engine15M2BIntensityCalibrator.calibrate(
            scope, restriction = M2BRestrictionStatus.ClearanceClaimed(scope)
        )
        assertTrue(claimed.hold)
        assertNull(claimed.guidance)
    }

    @Test
    fun coldStartKeepsEffortFirstGuidance() {
        val result = Engine15M2BIntensityCalibrator.calibrate(scope)
        assertFalse(result.hold)
        val guidance = result.guidance as M2BGuidance.EffortFirst
        assertEquals(2..3, guidance.conversationalCr10)
        assertFalse(result.productionAuthorized)
    }

    @Test
    fun historicalAnchorsYieldDescriptiveResearchHrrWithoutCoachingAuthorization() {
        val inputs = heartRateInputs()
        val result = Engine15M2BIntensityCalibrator.calibrate(
            scope, heartRate = inputs, researchAttestations = attest(inputs)
        )
        val hrr = result.descriptiveHrr as M2BDescriptiveHeartRateReserve.ResearchOnly
        assertEquals(0.75, hrr.ratio.value, 1e-12)
        assertEquals(inputs, hrr.evidence)
        assertFalse(hrr.grantsCoachingEligibility)
        assertFalse(hrr.grantsProductionAuthority)
        assertFalse(result.hrZoneAuthorized)
        assertEquals(M2BGuidance.EffortFirst, result.guidance)
    }

    @Test
    fun changingTheMeasurementOrEvaluationContextInvalidatesResearchAttestation() {
        val inputs = heartRateInputs()
        val changedInputs = inputs.copy(maximum = inputs.maximum.copy(value = BeatsPerSecond.fromBpm(183.0)))
        val changes = listOf(
            scope to changedInputs,
            scope.copy(asOf = scope.asOf.plusSeconds(1)) to inputs,
            scope to inputs.copy(maximum = inputs.maximum.copy(modality = HeartRateSensorModality.OPTICAL_WRIST_PPG)),
            scope to inputs.copy(maximum = inputs.maximum.copy(completed = inputs.maximum.completed.minusSeconds(1)))
        )
        for ((context, measurements) in changes) {
            val result = Engine15M2BIntensityCalibrator.calibrate(
                context, heartRate = measurements, researchAttestations = attest(inputs)
            )
            assertEquals(
                M2BDescriptiveHeartRateReserve.StructurallyValidAwaitingCorroboration, result.descriptiveHrr
            )
            assertEquals(M2BGuidance.EffortFirst, result.guidance)
        }
    }

    @Test
    fun futureAndCrossAccountHrMeasurementsNeverProduceAnHrrResult() {
        val base = heartRateInputs()
        val invalidObservations = listOf(
            base.observation.copy(completed = instant.plusSeconds(1)),
            base.observation.copy(accountId = "other"),
            base.observation.copy(discipline = AthleteDiscipline.CONDITIONING),
            base.observation.copy(quality = TelemetryQualityStatus.QUARANTINED_CADENCE_LOCK)
        )
        for (observation in invalidObservations) {
            val inputs = base.copy(observation = observation)
            val result = Engine15M2BIntensityCalibrator.calibrate(
                scope, heartRate = inputs, researchAttestations = attest(inputs)
            )
            assertEquals(M2BDescriptiveHeartRateReserve.ObservationInvalid, result.descriptiveHrr)
            assertTrue(result.descriptiveHrr.structurallyValidAnchors)
            assertFalse(result.hrZoneAuthorized)
        }
    }

    @Test
    fun impossibleRestAndMaximumNeverBecomeStructurallyValidThroughAttestation() {
        val base = heartRateInputs()
        val inputs = base.copy(maximum = base.maximum.copy(value = base.rest.value))
        val result = Engine15M2BIntensityCalibrator.calibrate(
            scope, heartRate = inputs, researchAttestations = attest(inputs)
        )
        assertEquals(M2BDescriptiveHeartRateReserve.StructurallyInvalidOrMissing, result.descriptiveHrr)
        assertFalse(result.descriptiveHrr.structurallyValidAnchors)
        assertEquals(M2BGuidance.EffortFirst, result.guidance)
    }

    @Test
    fun environmentAndRecoveryWithholdProvisionalPaceWhilePreservingEffortGuidance() {
        val history = M2BConfirmedTrainingReference(
            scope, M2ASourceQualifiedAlias("manual", "comfortable-pace"), M2BRunningReferenceFamily.EASY,
            instant.minusSeconds(86400), MetersPerSecond.of(3.0)
        )
        assertEquals(
            M2BGuidance.ProvisionalTraining(history),
            Engine15M2BIntensityCalibrator.calibrate(scope, confirmedTraining = history).guidance
        )
        assertEquals(M2BGuidance.EffortFirst, Engine15M2BIntensityCalibrator.calibrate(
            scope, confirmedTraining = history, condition = M2BExecutionCondition.ENVIRONMENTAL_CONFOUNDER
        ).guidance)
        assertEquals(M2BGuidance.EffortFirst, Engine15M2BIntensityCalibrator.calibrate(
            scope, confirmedTraining = history,
            recovery = M2BRecoveryContext(scope, instant.minus(Duration.ofDays(14)), false)
        ).guidance)
        assertEquals(M2BGuidance.EffortFirst, Engine15M2BIntensityCalibrator.calibrate(
            scope, confirmedTraining = history,
            recovery = M2BRecoveryContext(scope, instant.minus(Duration.ofDays(14)).plusSeconds(1), true)
        ).guidance)
    }

    @Test
    fun m2aVerifiedAndProvisionalEvidenceKeepTheirDifferentGuidancePermissions() {
        val yesterday = instant.minus(Duration.ofDays(40))
        val verified = Engine15M2BIntensityCalibrator.calibrate(
            scope, performance = m2aRequest(yesterday, verified = true)
        )
        val verifiedGuidance = verified.guidance as M2BGuidance.VerifiedBenchmark
        assertEquals("garmin:benchmark", verifiedGuidance.evidence.representativeId)
        assertEquals(1.0, verifiedGuidance.referenceFactor, 1e-12)
        assertTrue(verified.paceCalibrationEligible)
        assertFalse(verified.workoutPrescriptionAuthorized)
        assertFalse(verified.hrZoneAuthorized)
        val provisional = Engine15M2BIntensityCalibrator.calibrate(
            scope, performance = m2aRequest(yesterday, verified = false)
        )
        assertEquals(1.35, (provisional.guidance as M2BGuidance.ProvisionalBenchmark).referenceFactor, 1e-12)
        assertFalse(provisional.paceCalibrationEligible)
    }

    @Test
    fun staleAndStillInProgressBenchmarksCannotSupplyPaceGuidance() {
        val old = instant.minus(Duration.ofDays(240))
        val during = instant.minusSeconds(30)
        for (start in listOf(old, during, instant.plusSeconds(1))) {
            val result = Engine15M2BIntensityCalibrator.calibrate(scope, performance = m2aRequest(start, true))
            assertEquals(M2BGuidance.EffortFirst, result.guidance)
            assertFalse(result.paceCalibrationEligible)
        }
    }

    private fun m2aRequest(start: Instant, verified: Boolean): M2APerformanceCalibrationRequest {
        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountId = scope.accountId, asOfDate = scope.localDate, zoneId = scope.zone,
            canonicalActivities = emptyList(), asOfUtc = scope.asOf
        )
        val input = M2ARacePerformanceInput(
            performanceId = "benchmark", accountId = scope.accountId, discipline = scope.discipline,
            performedOn = start.atZone(scope.zone).toLocalDate(),
            distance = Meters.of(10_000.0), duration = Seconds.of(2_460.0),
            authority = if (verified) EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED
                else EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
            performanceIntent = if (verified) M2APerformanceIntent.VERIFIED_OFFICIAL_RACE
                else M2APerformanceIntent.SELF_REPORTED_RACE_OR_TIME_TRIAL,
            sourceProvider = if (verified) "garmin" else "manual", observedAtUtc = start
        )
        return M2APerformanceCalibrationRequest(
            scope.accountId, scope.localDate, scope.zone, observed, listOf(input), scope.asOf
        )
    }

    private fun heartRateInputs(): M2BResearchHeartRateInputs {
        fun evidence(id: String, role: M2BHeartRateRole, bpm: Double, days: Long): M2BHeartRateMeasurement {
            val end = instant.minus(Duration.ofDays(days))
            return M2BHeartRateMeasurement(
                scope.accountId, scope.discipline, M2ASourceQualifiedAlias("garmin", id), role,
                BeatsPerSecond.fromBpm(bpm), HeartRateSensorModality.CHEST_STRAP_ECG,
                end.minusSeconds(1), end, TelemetryQualityStatus.VALID_OBSERVED
            )
        }
        return M2BResearchHeartRateInputs(
            evidence("rest", M2BHeartRateRole.REST, 54.0, 240),
            evidence("max", M2BHeartRateRole.MAXIMUM, 182.0, 240),
            evidence("obs", M2BHeartRateRole.OBSERVATION, 150.0, 1)
        )
    }

    private fun attest(inputs: M2BResearchHeartRateInputs) =
        listOf(inputs.rest, inputs.maximum, inputs.observation).map { M2BResearchHrAttestation(it, scope) }

    private fun runningStream(
        completed: Instant,
        account: String = "athlete",
        id: String = "source-run"
    ): M2BRunningStream {
        val start = completed.minusSeconds(90)
        val raw = RawSourceComponent(
            id, "garmin", null, null, start, "running", "run",
            400.0, 90.0, 90.0, null, null, null, null
        )
        val activity = CanonicalActivity(
            account, AthleteDiscipline.RUN, "running", start, "garmin",
            NormalizedActivityMetrics(Meters.of(400.0), Seconds.of(90.0), Seconds.of(90.0),
                MetersPerSecond.of(400.0 / 90.0), null, null),
            TelemetryQualityAssessment(
                distanceQuality = TelemetryQualityStatus.VALID_OBSERVED,
                durationQuality = TelemetryQualityStatus.VALID_OBSERVED,
                hrQuality = TelemetryQualityStatus.MISSING_TELEMETRY
            ),
            CanonicalDeduplicationStatus.SINGLE_SOURCE, listOf(raw)
        )
        return M2BRunningStream(
            activity, M2ASourceQualifiedAlias("garmin", id), M2BSurface.NON_TECHNICAL_ROAD,
            listOf(M2BRunningSegment(M2BStreamIdentity.of(activity, raw), start, completed, Meters.of(400.0), 0.0))
        )
    }
}

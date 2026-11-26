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
    fun exactDurationKeepsDifferentRawAndGapWinners() {
        val finish = instant.minusSeconds(1)
        val level = runningStream(finish, id = "fast-flat")
        val hill = runningStream(finish, id = "hill")
        val slowerUphill = M2BRunningStream(
            hill.activity, hill.source, hill.surface,
            hill.segments.map { it.copy(horizontalDistance = Meters.of(350.0), riseMeters = 35.0) }
        )
        val envelope = Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(level, slowerUphill))
        val exact = envelope.exactAt(Seconds.of(90.0))!!
        assertEquals("fast-flat", exact.raw.source.externalId)
        assertEquals("hill", exact.gradeAdjusted!!.source.externalId)
        assertEquals(0, envelope.recordingDerivedObservations.size)
    }

    @Test
    fun frontierPrunesDominatedDurationWithoutInventingOrDeletingItsExactEvidence() {
        val finish = instant.minusSeconds(1)
        val longer = runningStream(finish, id = "longer")
        val shorter = runningStream(finish, id = "shorter")
        val thirtySeconds = M2BRunningStream(shorter.activity, shorter.source, shorter.surface,
            shorter.segments.map { it.copy(end = it.start.plusSeconds(30), horizontalDistance = Meters.of(120.0)) })
        val result = Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(longer, thirtySeconds))
        assertEquals(listOf(90.0), result.rawFrontier.map { it.duration.value })
        assertEquals(listOf(90.0), result.gapFrontier.map { it.duration.value })
        assertEquals(4.0, result.exactAt(Seconds.of(30.0))!!.raw.rawSpeed.value, 1e-12)
        assertNull(result.exactAt(Seconds.of(29.0)))
    }

    @Test
    fun recordingDerivesItsOwnWindowAndRetainsPausesAndSourceQuality() {
        val original = runningStream(instant.minusSeconds(1))
        val segment = original.segments.single()
        val start = segment.start
        val samples = listOf(
            M2BRecordingSample(start, Meters.of(0.0), 0.0, M2BContinuityEvidence.UNKNOWN),
            M2BRecordingSample(start.plusSeconds(60), Meters.of(300.0), 30.0, M2BContinuityEvidence.ACTIVE_CONTIGUOUS),
            M2BRecordingSample(segment.end, Meters.of(400.0), 30.0, M2BContinuityEvidence.ACTIVE_CONTIGUOUS)
        )
        fun load(rows: List<M2BRecordingSample>, quality: TelemetryQualityAssessment,
            origin: M2BStreamIdentity = segment.origin, foreignSegments: List<M2BRunningSegment> = emptyList()
        ): Engine15M2BPerformanceLowerEnvelope {
            val window = M2BRecordingWindow(origin, M2BDistanceSemantics.HORIZONTAL, quality, rows)
            val stream = M2BRunningStream(original.activity, original.source, original.surface, foreignSegments,
                window, MetersPerSecond.of(0.15), M2BEffortClassification.SUBMAXIMAL_EASY_OR_STEADY,
                M2BSourceClass.WATCH_FIT_DIRECT)
            return Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(stream))
        }
        val quality = original.activity.telemetryQuality
        val accepted = load(samples, quality)
        val bound = accepted.recordingDerivedObservations.single()
        assertEquals((Math.sqrt(90_900.0) + 100.0) / 90.0, bound.rawSpeed.value, 1e-12)
        assertEquals((Math.sqrt(90_900.0) * 5.968214 / 3.6 + 100.0) / 90.0,
            bound.gradeAdjustedSpeed!!.value, 1e-12)
        assertEquals(0.15, bound.sensorTolerance!!.value, 1e-12)
        assertEquals(segment.end.plus(Duration.ofDays(60)), bound.expiresAt)
        assertEquals(M2BSourceClass.WATCH_FIT_DIRECT, bound.sourceClass)
        assertEquals(M2BEffortClassification.SUBMAXIMAL_EASY_OR_STEADY, bound.effort)
        assertFalse(bound.verifiedContinuousPerformance)
        assertTrue(load(samples, quality, foreignSegments = original.segments).exactObservations.isEmpty())
        assertTrue(load(samples, quality, segment.origin.copy(recordId = "borrowed")).exactObservations.isEmpty())
        assertTrue(load(samples.dropLast(1) + samples.last().copy(cumulativeHorizontalDistance = Meters.of(399.0)),
            quality).exactObservations.isEmpty())
        val paused = load(samples.dropLast(1) + samples.last().copy(continuity = M2BContinuityEvidence.PAUSE), quality)
        assertTrue(paused.exactObservations.isEmpty())
        assertEquals(1, paused.elapsedAverages.size)
        assertEquals(M2BContinuityEvidence.PAUSE, paused.recordingTraces.single().window.samples.last().continuity)
        val degraded = TelemetryQualityAssessment(quality.hrQuality, TelemetryQualityStatus.DEGRADED, quality.durationQuality)
        assertTrue(load(samples, degraded).recordingDerivedObservations.isEmpty())
    }

    @Test
    fun operationalThresholdBeliefAndProvenanceSurviveAnEnvironmentOverlay() {
        val request = m2aRequest(instant.minus(Duration.ofDays(30)), true)
        val result = Engine15M2BIntensityCalibrator.calibrate(scope, performance = request,
            condition = M2BExecutionCondition.ENVIRONMENTAL_CONFOUNDER)
        val threshold = result.operationalThresholdCalibration!!
        val speed = (threshold.operationalThresholdSpeedBelief as BeliefState.Estimated).estimate.value
        val expected = (10_000.0 / 2_460.0) * StrictMath.exp((-7.0 / 107.0) * StrictMath.log(3240.0 / 2460.0))
        assertEquals(expected, speed, 1e-12)
        assertEquals(3240.0, threshold.operationalThresholdReferenceDuration.value, 0.0)
        assertEquals(1.0, threshold.operationalThresholdGuidanceBandFactor!!, 0.0)
        assertEquals("garmin:benchmark", threshold.operationalThresholdBenchmark!!.representativeId)
        assertFalse(threshold.isMeasuredPhysiologicalThreshold)
        assertEquals(M2BGuidance.EffortFirst, result.guidance)
        assertFalse(result.paceCalibrationEligible)
    }

    @Test
    fun provisionalRaceRequiresMissingOrPartialHistoryAndBothCrossChecks() {
        val benchmark = m2aRequest(instant.minus(Duration.ofDays(30)), false)
        fun withCoverage(coverage: HistoryCoverageStatus): M2APerformanceCalibrationRequest {
            val unknown = BeliefState.Unknown("FIXTURE")
            val running = DisciplineObservedWorkload(AthleteDiscipline.RUN, coverage, 28, 5,
                unknown, unknown, unknown, unknown, 6, 0, 0, false, emptyList())
            return benchmark.copy(observedWorkload = ObservedWorkloadState(scope.accountId, scope.localDate, scope.zone,
                mapOf(AthleteDiscipline.RUN to running), emptyMap()))
        }
        for (coverage in listOf(HistoryCoverageStatus.MISSING_UNOBSERVED, HistoryCoverageStatus.PARTIAL_COVERAGE)) {
            val result = Engine15M2BIntensityCalibrator.calibrate(scope, performance = withCoverage(coverage))
            val guidance = result.guidance as M2BGuidance.ProvisionalBenchmark
            assertEquals(coverage, guidance.recordedCoverage)
            assertTrue(guidance.requiresCr10CrossCheck)
            assertTrue(guidance.requiresTalkTestCrossCheck)
            assertFalse(result.paceCalibrationEligible)
        }
        for (coverage in listOf(HistoryCoverageStatus.CONTINUOUS_COVERAGE, HistoryCoverageStatus.CONFIRMED_ZERO)) {
            assertEquals(M2BGuidance.EffortFirst,
                Engine15M2BIntensityCalibrator.calibrate(scope, performance = withCoverage(coverage)).guidance)
        }
    }

    @Test
    fun trainingPathsRetainFamilySpecificDescriptiveQuartilesWithoutActionableNumbers() {
        fun session(id: String, pace: Double) = runningStream(instant.minusSeconds(1), id = id).let {
            M2BTrainingObservation(it.activity, it.source, M2BRunningReferenceFamily.EASY,
                it.segments.single().end, MetersPerSecond.fromPaceSecondsPerKm(pace), 2.0,
                M2BTalkTestStage.COMFORTABLE_SENTENCES, M2BTrainingHeartRate.CLEAN_STABLE,
                true, false, sourceDistanceQuality = TelemetryQualityStatus.VALID_OBSERVED)
        }
        val easy = listOf(300.0, 320.0, 340.0, 360.0).mapIndexed { index, pace ->
            session("easy-$index", pace).copy(steadyPaceWithAlignedEffort = true)
        }
        val structured = session("cruise", 270.0).copy(family = M2BRunningReferenceFamily.CRUISE,
            completedAsIntended = true, splitExecutionStable = true, matchedIntendedRpe = true)
        val start = session("progress", 360.0).copy(declaredControlledProgression = true)
        val progression = listOf(start.copy(endedAt = start.endedAt.minusSeconds(60)),
            start.copy(family = M2BRunningReferenceFamily.STEADY, endedAt = start.endedAt.minusSeconds(30),
                cr10 = 4.0, speed = MetersPerSecond.of(3.1), talkTest = M2BTalkTestStage.SHORT_OR_EQUIVOCAL_SPEECH),
            start.copy(family = M2BRunningReferenceFamily.TEMPO, cr10 = 6.5, speed = MetersPerSecond.of(3.5),
                talkTest = M2BTalkTestStage.SHORT_OR_EQUIVOCAL_SPEECH))
        val inputs = easy + easy.first() + structured + progression
        val profile = Engine15M2BIntensityCalibrator.calibrate(scope, trainingObservations = inputs)
        val reference = profile.trainingReferences.single {
            it.pathway == M2BTrainingPathway.COMFORTABLE_AEROBIC
        }
        assertEquals(4, reference.canonicalActivityIds.size)
        assertEquals(315.0, reference.distribution.q1SecondsPerKm, 1e-12)
        assertEquals(330.0, reference.distribution.medianSecondsPerKm, 1e-12)
        assertEquals(345.0, reference.distribution.q3SecondsPerKm, 1e-12)
        assertEquals(30.0, reference.distribution.interquartileRangeSecondsPerKm, 1e-12)
        assertEquals(M2BTrainingRestriction.CONSISTENCY_POLICY_PENDING, reference.restriction)
        assertEquals(3, profile.trainingReferences.count { it.pathway == M2BTrainingPathway.CONTROLLED_PROGRESSION })
        assertEquals(M2BRunningReferenceFamily.CRUISE,
            profile.trainingReferences.single { it.pathway == M2BTrainingPathway.SUCCESSFUL_STRUCTURED }.family)
        assertEquals(4, profile.trainingReferences.single {
            it.pathway == M2BTrainingPathway.PACE_HR_RPE_CONVERGENCE
        }.canonicalActivityIds.size)
        assertEquals(M2BGuidance.EffortFirst, profile.guidance)
        assertEquals(profile.contentHash, Engine15M2BIntensityCalibrator.calibrate(scope,
            trainingObservations = inputs.reversed()).contentHash)
        val historical = M2BConfirmedTrainingReference(scope, M2ASourceQualifiedAlias("manual", "old-easy"),
            M2BRunningReferenceFamily.EASY, instant.minusSeconds(1), MetersPerSecond.of(3.0))
        val confirmed = Engine15M2BIntensityCalibrator.calibrate(scope, confirmedTraining = historical)
        assertEquals(M2BTrainingPathway.HISTORICAL_CONFIRMATION, confirmed.trainingReferences.single().pathway)
        assertTrue((confirmed.guidance as M2BGuidance.ProvisionalTraining).effortTakesPriority)
    }

    @Test
    fun effortDomainsAreDescriptorsAndRecoveryPreservesOnlyTheScopedDurationCap() {
        assertEquals(listOf(M2BEffortDomain.MODERATE, M2BEffortDomain.HEAVY, M2BEffortDomain.SEVERE),
            listOf(M2BEffortDescriptors.easy, M2BEffortDescriptors.tempo, M2BEffortDescriptors.severe).map { it.domain })
        assertEquals(M2BTalkTestStage.UNABLE_TO_SPEAK, M2BEffortDescriptors.severe.talkTest)
        val cap = M2BDurationExposureConstraint(scope, Seconds.of(80.0), M2ASourceQualifiedAlias("fixture", "exposure"))
        val recovery = Engine15M2BIntensityCalibrator.calibrate(scope,
            restriction = M2BRestrictionStatus.RecoveryIntensity(scope), durationExposureConstraint = cap)
        assertEquals(listOf(M2BEffortDescriptors.recovery), recovery.effortGuidance.offeredCues)
        assertEquals(cap, recovery.effortGuidance.durationExposureConstraint)
        assertFalse(recovery.workoutPrescriptionAuthorized)
        assertNull(Engine15M2BIntensityCalibrator.calibrate(scope.copy(asOf = instant.plusSeconds(1)),
            durationExposureConstraint = cap).effortGuidance.durationExposureConstraint)
    }

    @Test
    fun heartRateEvidenceReconcilesSeparateMetricsAndRejectsCrossPhysiologicalModality() {
        fun evidence(id: String) = M2BHeartRateEvidenceScope(scope.accountId, scope.discipline,
            scope.physiologicalModality, M2ASourceQualifiedAlias("audit", id), instant.minusSeconds(1))
        val peak = M2BHeartRateEvidence.ObservedPeak(evidence("peak"), BeatsPerSecond.fromBpm(174.0),
            HeartRateSensorModality.CHEST_STRAP_ECG, TelemetryQualityStatus.VALID_OBSERVED)
        val self = M2BHeartRateEvidence.SelfReportedMaximum(evidence("report"), BeatsPerSecond.fromBpm(191.0))
        val lab = M2BHeartRateEvidence.ReportedVerifiedAnchor(evidence("lab"), M2BHeartRateEvidenceKind.REPORTED_VERIFIED_LT2,
            BeatsPerSecond.fromBpm(164.0), HeartRateSensorModality.CHEST_STRAP_ECG,
            TelemetryQualityStatus.VALID_OBSERVED, "lab-test")
        val zones = M2BHeartRateEvidence.Zones(evidence("zones"), true,
            listOf(100.0, 120.0, 140.0, 160.0, 180.0).map(BeatsPerSecond::fromBpm))
        val foreign = peak.copy(scope = evidence("bike").copy(physiologicalModality = M2BPhysiologicalModality.CYCLING),
            value = BeatsPerSecond.fromBpm(195.0))
        val result = Engine15M2BIntensityCalibrator.calibrate(scope, ageYears = 40,
            heartRateEvidence = listOf(peak, self, zones, lab, foreign))
        val hierarchy = result.heartRateHierarchy
        assertEquals(4, hierarchy.evidence.size)
        assertEquals(174.0, (hierarchy.observedPeakLowerBound as BeliefState.Bounded).lower!!.toBpm(), 1e-12)
        assertEquals(191.0, (hierarchy.selfReportedMaximum as BeliefState.Estimated).estimate.toBpm(), 1e-12)
        assertFalse(hierarchy.coachingAuthorized)
        assertFalse(result.hrZoneAuthorized)
        val conflict = Engine15M2BIntensityCalibrator.calibrate(scope,
            heartRateEvidence = listOf(self, self.copy(value = BeatsPerSecond.fromBpm(180.0))))
        assertTrue(conflict.heartRateHierarchy.selfReportedMaximum is BeliefState.Unknown)
        assertEquals(setOf(M2BHeartRateEvidenceKind.SELF_REPORTED_MAXIMUM), conflict.heartRateHierarchy.conflicts)
    }

    @Test
    fun hrOnlyQuarantinePreservesPaceButSnapshotHoldBeatsOmissionAndClaimedClearance() {
        val benchmark = m2aRequest(instant.minus(Duration.ofDays(2)), true)
        val hrOnly = Engine15M2BIntensityCalibrator.calibrate(scope,
            restriction = M2BRestrictionStatus.HeartRateQuarantine(scope), performance = benchmark)
        assertTrue(hrOnly.paceCalibrationEligible)
        assertEquals(M2BDescriptiveHeartRateReserve.QuarantinedForEvaluation, hrOnly.descriptiveHrr)
        val heldSnapshot = AthleteStateSnapshot(scope.accountId, instant, emptyMap(),
            activeRestrictionIds = setOf("persistent-hold"), observedWorkload = benchmark.observedWorkload)
        val held = benchmark.copy(athleteStateSnapshot = heldSnapshot)
        val result = Engine15M2BIntensityCalibrator.calibrate(scope, performance = held)
        assertEquals(setOf("persistent-hold"), result.persistentRestrictionIds)
        assertTrue(result.hold)
        assertNull(result.guidance)
        assertFalse(result.restrictionDecision.verifiedClearance)
        assertNull(Engine15M2BIntensityCalibrator.calibrate(scope, performance = held,
            restriction = M2BRestrictionStatus.ClearanceClaimed(scope)).guidance)
        assertNull(Engine15M2BIntensityCalibrator.calibrate(scope, athleteSnapshot = heldSnapshot).guidance)
        assertNull(Engine15M2BIntensityCalibrator.calibrate(scope,
            restriction = M2BRestrictionStatus.RunningSpecific(scope)).guidance)
        assertNull(Engine15M2BIntensityCalibrator.calibrate(scope,
            restriction = M2BRestrictionStatus.StatusUnavailable).guidance)
        assertFalse(Engine15M2BIntensityCalibrator.calibrate(scope).restrictionDecision.verifiedClearance)
    }

    @Test
    fun contentIdentityIncludesUpstreamContextRestrictionsAndImmutableObservedEvidence() {
        val first = runningStream(instant.minusSeconds(1), id = "first")
        val second = runningStream(instant.minusSeconds(2), id = "second")
        val request = m2aRequest(instant.minus(Duration.ofDays(2)), true)
        val input = mutableListOf(first, second)
        val result = Engine15M2BIntensityCalibrator.calibrate(scope, performance = request, streams = input)
        val permuted = Engine15M2BIntensityCalibrator.calibrate(scope, performance = request, streams = input.reversed())
        assertEquals(result.contentHash, permuted.contentHash)
        assertEquals(request.observedWorkload.contentHash, result.observedWorkloadContentHash)
        input.clear()
        assertEquals(2, result.envelope.exactObservations.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.envelope.exactObservations as MutableList).clear()
        }
        val restricted = Engine15M2BIntensityCalibrator.calibrate(scope, performance = request,
            streams = listOf(first, second), restriction = M2BRestrictionStatus.HeartRateQuarantine(scope))
        assertTrue(result.contentHash != restricted.contentHash)
        assertFalse(result.productionAuthorized || result.workoutPrescriptionAuthorized || result.hrZoneAuthorized)
    }

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

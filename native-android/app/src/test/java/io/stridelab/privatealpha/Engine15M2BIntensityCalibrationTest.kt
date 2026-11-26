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
    fun crossMetricHrContradictionsPreserveEvidenceAndTheIndependentPeakBound() {
        fun key(id: String) = M2BHeartRateEvidenceScope(scope.accountId, scope.discipline,
            scope.physiologicalModality, M2ASourceQualifiedAlias("fixture", id), instant.minusSeconds(1))
        fun anchor(kind: M2BHeartRateEvidenceKind, bpm: Double) = M2BHeartRateEvidence.ReportedVerifiedAnchor(
            key(kind.name), kind, BeatsPerSecond.fromBpm(bpm), HeartRateSensorModality.CHEST_STRAP_ECG,
            TelemetryQualityStatus.VALID_OBSERVED, "reported-protocol"
        )
        val peak = M2BHeartRateEvidence.ObservedPeak(key("peak"), BeatsPerSecond.fromBpm(185.0),
            HeartRateSensorModality.CHEST_STRAP_ECG, TelemetryQualityStatus.VALID_OBSERVED)
        val self = M2BHeartRateEvidence.SelfReportedMaximum(key("claim"), BeatsPerSecond.fromBpm(175.0))
        val maximum = anchor(M2BHeartRateEvidenceKind.REPORTED_VERIFIED_MAXIMUM, 180.0)
        val lt1 = anchor(M2BHeartRateEvidenceKind.REPORTED_VERIFIED_LT1, 181.0)
        val lt2 = anchor(M2BHeartRateEvidenceKind.REPORTED_VERIFIED_LT2, 180.0)
        val evidence = listOf(peak, self, maximum, lt1, lt2)
        val performance = m2aRequest(instant.minus(Duration.ofDays(30)), true)
        val result = Engine15M2BIntensityCalibrator.calibrate(scope, performance = performance, heartRateEvidence = evidence)
        assertEquals(evidence.toSet(), result.heartRateHierarchy.evidence.toSet())
        assertEquals(setOf(
            M2BHRCrossMetricConflictReason.MAXIMUM_BELOW_OBSERVED_PEAK,
            M2BHRCrossMetricConflictReason.LT1_NOT_BELOW_LT2,
            M2BHRCrossMetricConflictReason.THRESHOLD_AT_OR_ABOVE_MAXIMUM
        ), result.heartRateHierarchy.crossMetricConflicts.map { it.reason }.toSet())
        assertEquals(5, result.heartRateHierarchy.crossMetricConflicts.size)
        assertEquals(185.0, (result.heartRateHierarchy.observedPeakLowerBound as BeliefState.Bounded).lower!!.toBpm(),
            1e-12)
        assertTrue(result.heartRateHierarchy.unambiguousEvidence(M2BHeartRateEvidenceKind.REPORTED_VERIFIED_MAXIMUM).isEmpty())
        assertTrue(result.heartRateHierarchy.selfReportedMaximum is BeliefState.Unknown)
        assertFalse(result.hrZoneAuthorized)
        assertTrue(result.paceCalibrationEligible)
        assertTrue(result.paceConflicts.isEmpty())
        assertEquals(result.contentHash,
            Engine15M2BIntensityCalibrator.calibrate(scope, performance = performance,
                heartRateEvidence = evidence.reversed()).contentHash)
    }

    @Test
    fun downhillEvidenceRetainsEveryVetoAndRawPerformance() {
        val original = runningStream(instant.minusSeconds(1))
        val first = original.segments.single()
        val split = first.start.plusSeconds(45)
        val stream = M2BRunningStream(original.activity, original.source, original.surface, emptyList(),
            M2BRecordingWindow(first.origin, M2BDistanceSemantics.HORIZONTAL, original.activity.telemetryQuality,
                listOf(M2BRecordingSample(first.start, Meters.of(0.0), 200.0, M2BContinuityEvidence.UNKNOWN),
                    M2BRecordingSample(split, Meters.of(200.0), 160.0, M2BContinuityEvidence.ACTIVE_CONTIGUOUS),
                    M2BRecordingSample(first.end, Meters.of(400.0), 60.0, M2BContinuityEvidence.ACTIVE_CONTIGUOUS))))
        val observation = Engine15M2BPerformanceLowerEnvelope.build(scope, listOf(stream)).exactObservations.single()
        assertNull(observation.gradeAdjustedSpeed)
        assertEquals((Math.hypot(200.0, 40.0) + Math.hypot(200.0, 100.0)) / 90.0, observation.rawSpeed.value, 1e-12)
        assertEquals(listOf(40.0, 100.0), observation.downhillExposure.map { it.elevationLoss.value })
        assertEquals(listOf(45.0, 45.0), observation.downhillExposure.map { it.duration.value })
        assertEquals(listOf(200.0, 200.0), observation.downhillExposure.map { it.horizontalDistance.value })
        assertEquals(Math.hypot(200.0, 100.0), observation.downhillExposure.last().pathDistance.value, 1e-12)
        assertEquals(listOf(M2BGAPRejectionReason.ECCENTRIC_DOMINATED_QUARANTINE,
            M2BGAPRejectionReason.GAP_OUT_OF_DOMAIN), observation.gapRejections.map { it.reason })
        assertFalse(observation.verifiedContinuousPerformance)
    }

    @Test
    fun contradictoryBenchmarksBlockHistoricalCoachingButStaleConflictsDoNot() {
        val recent = instant.minus(Duration.ofDays(20))
        val stale = instant.minus(Duration.ofDays(200))
        val historical = M2BConfirmedTrainingReference(scope, M2ASourceQualifiedAlias("manual", "habitual"),
            M2BRunningReferenceFamily.EASY, instant.minusSeconds(1), MetersPerSecond.of(3.0))
        fun withCollision(start: Instant): M2APerformanceCalibrationRequest {
            val original = m2aRequest(start, true)
            return original.copy(racePerformances = original.racePerformances +
                m2aRequest(start, true, durationSeconds = 1_650.0).racePerformances)
        }
        val blocked = Engine15M2BIntensityCalibrator.calibrate(scope, performance = withCollision(recent),
            confirmedTraining = historical)
        assertTrue(blocked.operationalThresholdCalibration!!.operationalThresholdSpeedBelief is BeliefState.Conflicting)
        assertEquals(setOf(M2BPaceConflict.BENCHMARK_IDENTITY_COLLISION), blocked.paceConflicts)
        assertEquals(M2BGuidance.EffortFirst, blocked.guidance)
        assertEquals(M2BTrainingPathway.HISTORICAL_CONFIRMATION, blocked.trainingReferences.single().pathway)
        assertTrue(Engine15M2BIntensityCalibrator.calibrate(scope, performance = withCollision(stale),
            confirmedTraining = historical).guidance is M2BGuidance.ProvisionalTraining)
        val epochA = m2aRequest(recent, true, fitnessEpochId = "period-a")
        val epochB = m2aRequest(recent, true, fitnessEpochId = "period-b")
        val conflictingEpoch = Engine15M2BIntensityCalibrator.calibrate(scope,
            performance = epochA.copy(racePerformances = epochA.racePerformances + epochB.racePerformances),
            confirmedTraining = historical)
        assertTrue(conflictingEpoch.operationalThresholdCalibration!!.operationalThresholdBenchmark!!.hasConflictingFitnessEpoch)
        assertEquals(setOf(M2BPaceConflict.SELECTED_BENCHMARK_FITNESS_EPOCH), conflictingEpoch.paceConflicts)
        assertEquals(M2BGuidance.EffortFirst, conflictingEpoch.guidance)
    }

    @Test
    fun benchmarkFreshnessHasExactNinetyAndOneHundredEightyDayBoundaries() {
        val cases = listOf(90L to 1.0, 91L to 1.25, 180L to 1.25, 181L to null)
        for ((days, factor) in cases) {
            val result = Engine15M2BIntensityCalibrator.calibrate(scope,
                performance = m2aRequest(instant.minus(Duration.ofDays(days)), true))
            if (factor == null) {
                assertEquals(M2BGuidance.EffortFirst, result.guidance)
                assertTrue(result.operationalThresholdCalibration!!.operationalThresholdSpeedBelief is BeliefState.Stale)
            } else {
                val guidance = result.guidance as M2BGuidance.VerifiedBenchmark
                assertEquals(factor, guidance.referenceFactor, 1e-12)
                assertEquals(if (days == 90L) M2ABenchmarkFreshness.FRESH else M2ABenchmarkFreshness.AGING,
                    guidance.evidence.freshness)
                assertTrue(result.paceCalibrationEligible)
            }
            assertFalse(result.workoutPrescriptionAuthorized)
        }
    }

    @Test
    fun missingGpsWithValidCanonicalHrKeepsThePeakAndEffortButNoPace() {
        val activity = telemetryRun("no-gps", gps = false, optical = false, corroborated = false)
        assertEquals(TelemetryQualityStatus.MISSING_TELEMETRY, activity.telemetryQuality.distanceQuality)
        assertEquals(TelemetryQualityStatus.VALID_OBSERVED, activity.telemetryQuality.hrQuality)
        val peak = peakEvidence(activity, HeartRateSensorModality.CHEST_STRAP_ECG)
        val result = Engine15M2BIntensityCalibrator.calibrate(scope,
            streams = listOf(M2BRunningStream(activity, peak.scope.source, M2BSurface.NON_TECHNICAL_ROAD, emptyList())),
            heartRateEvidence = listOf(peak))
        assertTrue(result.envelope.exactObservations.isEmpty())
        assertTrue(result.envelope.elapsedAverages.isEmpty())
        assertEquals(174.0, (result.heartRateHierarchy.observedPeakLowerBound as BeliefState.Bounded).lower!!.toBpm(), 1e-12)
        assertEquals(M2BGuidance.EffortFirst, result.guidance)
        assertFalse(result.paceCalibrationEligible || result.hrZoneAuthorized)
    }

    @Test
    fun m1bCadenceLockFlowsThroughM2bWithoutDiscardingIndependentPace() {
        for (corroborated in listOf(false, true)) {
            val activity = telemetryRun("lock-$corroborated", gps = true, optical = true, corroborated = corroborated)
            val status = if (corroborated) TelemetryQualityStatus.QUARANTINED_CADENCE_LOCK
                else TelemetryQualityStatus.SUSPECTED_CADENCE_LOCK
            assertEquals(status, activity.telemetryQuality.hrQuality)
            assertEquals(TelemetryQualityStatus.VALID_OBSERVED, activity.telemetryQuality.distanceQuality)
            val peak = peakEvidence(activity, HeartRateSensorModality.OPTICAL_WRIST_PPG)
            val observation = trainingObservation(activity, peak.scope.source).copy(
                sourceHrQuality = TelemetryQualityStatus.VALID_OBSERVED)
            val result = Engine15M2BIntensityCalibrator.calibrate(scope,
                performance = m2aRequest(instant.minus(Duration.ofDays(30)), true),
                heartRateEvidence = listOf(peak), trainingObservations = listOf(observation))
            assertTrue(result.heartRateHierarchy.evidence.isEmpty())
            assertTrue(result.heartRateHierarchy.observedPeakLowerBound is BeliefState.Unknown)
            assertEquals(listOf(M2BTrainingPathway.COMFORTABLE_AEROBIC), result.trainingReferences.map { it.pathway })
            assertTrue(result.paceCalibrationEligible)
            assertFalse(result.hrZoneAuthorized)
        }
    }

    @Test
    fun sourceHrQualityOverridesCleanStableButKeepsComfortablePace() {
        val activity = telemetryRun("clean", gps = true, optical = false, corroborated = false)
        val source = M2ASourceQualifiedAlias("garmin", "clean")
        val observation = trainingObservation(activity, source)
        val clean = Engine15M2BIntensityCalibrator.calibrate(scope, trainingObservations = listOf(observation))
        assertTrue(clean.trainingReferences.any { it.pathway == M2BTrainingPathway.PACE_HR_RPE_CONVERGENCE })
        for (quality in listOf(TelemetryQualityStatus.SUSPECTED_CADENCE_LOCK, TelemetryQualityStatus.DEGRADED,
            TelemetryQualityStatus.QUARANTINED_UNRELIABLE, TelemetryQualityStatus.MISSING_TELEMETRY)) {
            val result = Engine15M2BIntensityCalibrator.calibrate(scope,
                trainingObservations = listOf(observation.copy(sourceHrQuality = quality)))
            assertEquals(listOf(M2BTrainingPathway.COMFORTABLE_AEROBIC), result.trainingReferences.map { it.pathway })
        }
        val raw = activity.rawComponents.single()
        val trace = M2BRecordingWindow(M2BStreamIdentity.of(activity, raw), M2BDistanceSemantics.HORIZONTAL,
            TelemetryQualityAssessment(TelemetryQualityStatus.QUARANTINED_UNRELIABLE,
                TelemetryQualityStatus.VALID_OBSERVED, TelemetryQualityStatus.VALID_OBSERVED), emptyList())
        val stream = M2BRunningStream(activity, source, M2BSurface.NON_TECHNICAL_ROAD, emptyList(), trace)
        val result = Engine15M2BIntensityCalibrator.calibrate(scope, streams = listOf(stream),
            trainingObservations = listOf(observation.copy(sourceHrQuality = TelemetryQualityStatus.VALID_OBSERVED)))
        assertEquals(listOf(M2BTrainingPathway.COMFORTABLE_AEROBIC), result.trainingReferences.map { it.pathway })
    }

    @Test
    fun nestedRecordingAndCalibrationCollectionsRemainImmutable() {
        val activity = telemetryRun("collections", gps = true, optical = false, corroborated = false)
        val source = M2ASourceQualifiedAlias("garmin", "collections")
        val raw = activity.rawComponents.single()
        val end = m2bSessionEnd(raw)!!
        val samples = mutableListOf(
            M2BRecordingSample(raw.startedAtUtc, Meters.of(0.0), 10.0, M2BContinuityEvidence.UNKNOWN),
            M2BRecordingSample(end, Meters.of(8000.0), 0.0, M2BContinuityEvidence.ACTIVE_CONTIGUOUS)
        )
        val window = M2BRecordingWindow(M2BStreamIdentity.of(activity, raw), M2BDistanceSemantics.HORIZONTAL,
            activity.telemetryQuality, samples)
        val floors = mutableListOf(BeatsPerSecond.fromBpm(100.0), BeatsPerSecond.fromBpm(140.0))
        val zones = M2BHeartRateEvidence.Zones(M2BHeartRateEvidenceScope(scope.accountId, scope.discipline,
            scope.physiologicalModality, source, end), true, floors)
        val result = Engine15M2BIntensityCalibrator.calibrate(scope,
            streams = listOf(M2BRunningStream(activity, source, M2BSurface.NON_TECHNICAL_ROAD, emptyList(), window)),
            trainingObservations = listOf(trainingObservation(activity, source)), heartRateEvidence = listOf(zones))
        samples.clear()
        floors.clear()
        assertEquals(2, result.envelope.recordingTraces.single().window.samples.size)
        assertEquals(2, (result.heartRateHierarchy.evidence.single() as M2BHeartRateEvidence.Zones).floors.size)
        val trace = result.envelope.recordingTraces.single()
        val reference = result.trainingReferences.first()
        assertThrows(UnsupportedOperationException::class.java) { (trace.window.samples as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.envelope.recordingTraces as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (zones.floors as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (reference.observations as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (reference.canonicalActivityIds as MutableSet).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.envelope.bounds.single().downhillExposure as MutableList).clear() }
        for (provenance in listOf(reference.sourceEvidence, result.envelope.bounds.single().provenance)) {
            val cause = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
                provenance.javaClass.getMethod("clear").invoke(provenance)
            }.cause
            assertTrue(cause is UnsupportedOperationException)
        }
        assertEquals(setOf(source), reference.sourceEvidence)
    }

    private fun telemetryRun(id: String, gps: Boolean, optical: Boolean, corroborated: Boolean): CanonicalActivity {
        val start = instant.minusSeconds(1500)
        val samples = (0 until 20).map { i ->
            val cadence = 170.0 + i % 4
            ActivitySample(i, i * 60.0, if (gps) 150.0 else null,
                if (optical && i < 13) cadence + 1.0 else 150.0 + i % 4,
                50.0, if (gps) 37.7749 + i * 0.0008 else null,
                if (gps) -122.4194 else null, cadence)
        }
        val raw = RawActivityObservation(scope.accountId, ActivityRecord(
            id = id, title = "Telemetry", type = "running", source = "garmin", startedAt = start,
            distanceM = if (gps) 8_000.0 else null, durationS = 1_200.0, movingTimeS = 1_200.0,
            recordedPace = if (gps) 150.0 else null, averageHr = 165.0, elevationM = null, maximumHr = 174.0
        ), detail = ActivityDetail(id, samples, emptyList(), instant.toEpochMilli()),
            hrSensorModality = if (optical) HeartRateSensorModality.OPTICAL_WRIST_PPG
            else HeartRateSensorModality.CHEST_STRAP_ECG, hasCorroboratingCadenceLockSignal = corroborated)
        return Engine15CanonicalActivityProjector.project(listOf(raw)).activities.single()
    }

    private fun peakEvidence(activity: CanonicalActivity, sensor: HeartRateSensorModality): M2BHeartRateEvidence.ObservedPeak {
        val source = activity.rawComponents.single()
        return M2BHeartRateEvidence.ObservedPeak(M2BHeartRateEvidenceScope(scope.accountId, scope.discipline,
            scope.physiologicalModality, M2ASourceQualifiedAlias(source.provider, source.recordId), m2bSessionEnd(source)!!),
            BeatsPerSecond.fromBpm(source.rawMaximumHrBpm!!), sensor, activity.telemetryQuality.hrQuality)
    }

    private fun trainingObservation(activity: CanonicalActivity, source: M2ASourceQualifiedAlias) =
        M2BTrainingObservation(activity, source, M2BRunningReferenceFamily.EASY,
            m2bSessionEnd(activity.rawComponents.single())!!, MetersPerSecond.of(4.0), 2.5,
            M2BTalkTestStage.COMFORTABLE_SENTENCES, M2BTrainingHeartRate.CLEAN_STABLE,
            flatOrRolling = true, confounded = false, steadyPaceWithAlignedEffort = true,
            sourceDistanceQuality = activity.telemetryQuality.distanceQuality)

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
        fun session(id: String, pace: Double) = runningStream(instant.minusSeconds(1), id = id,
            hrQuality = TelemetryQualityStatus.VALID_OBSERVED).let {
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

    private fun m2aRequest(
        start: Instant, verified: Boolean, durationSeconds: Double = 2_460.0, fitnessEpochId: String? = null
    ): M2APerformanceCalibrationRequest {
        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountId = scope.accountId, asOfDate = scope.localDate, zoneId = scope.zone,
            canonicalActivities = emptyList(), asOfUtc = scope.asOf
        )
        val input = M2ARacePerformanceInput(
            performanceId = "benchmark", accountId = scope.accountId, discipline = scope.discipline,
            performedOn = start.atZone(scope.zone).toLocalDate(),
            distance = Meters.of(10_000.0), duration = Seconds.of(durationSeconds),
            authority = if (verified) EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED
                else EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
            performanceIntent = if (verified) M2APerformanceIntent.VERIFIED_OFFICIAL_RACE
                else M2APerformanceIntent.SELF_REPORTED_RACE_OR_TIME_TRIAL,
            sourceProvider = if (verified) "garmin" else "manual", observedAtUtc = start, fitnessEpochId = fitnessEpochId
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
        id: String = "source-run",
        hrQuality: TelemetryQualityStatus = TelemetryQualityStatus.MISSING_TELEMETRY
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
                hrQuality = hrQuality
            ),
            CanonicalDeduplicationStatus.SINGLE_SOURCE, listOf(raw)
        )
        return M2BRunningStream(
            activity, M2ASourceQualifiedAlias("garmin", id), M2BSurface.NON_TECHNICAL_ROAD,
            listOf(M2BRunningSegment(M2BStreamIdentity.of(activity, raw), start, completed, Meters.of(400.0), 0.0))
        )
    }
}

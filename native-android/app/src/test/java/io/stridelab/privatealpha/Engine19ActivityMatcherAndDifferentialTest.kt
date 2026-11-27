package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine19ActivityMatcherAndDifferentialTest {
    private val monday = LocalDate.parse("2026-11-30")
    private val baseUtc = Instant.parse("2026-11-30T06:00:00Z")

    @Test fun cd02StitchingGateRejectsStandaloneRunsAndSeparateStrengthButStitchesComplementarySegments() {
        val morningRun = StitchableRecording(
            activityId = "raw-run-1",
            discipline = "run",
            startUtc = baseUtc,
            endUtc = baseUtc.plusSeconds(1800),
            role = StitchSegmentRole.COMPLETE_STANDALONE,
            deviceSource = "garmin-965",
            endLatDeg = 1.3000,
            endLonDeg = 103.8000
        )
        val strength25MinLater = StitchableRecording(
            activityId = "raw-str-1",
            discipline = "str",
            startUtc = baseUtc.plusSeconds(1800 + 25 * 60),
            endUtc = baseUtc.plusSeconds(1800 + 25 * 60 + 2700),
            role = StitchSegmentRole.COMPLETE_STANDALONE,
            deviceSource = "garmin-965"
        )
        assertEquals(
            StitchOutcome.REJECTED_DISCIPLINE_MISMATCH,
            Engine19ActivityMatcherAndStitcher.evaluateStitchPair(morningRun, strength25MinLater).outcome
        )

        val secondStandaloneRun = morningRun.copy(
            activityId = "raw-run-2",
            startUtc = baseUtc.plusSeconds(1800 + 900),
            endUtc = baseUtc.plusSeconds(1800 + 900 + 1800),
            startLatDeg = 1.3001,
            startLonDeg = 103.8001
        )
        assertEquals(
            StitchOutcome.REJECTED_TWO_STANDALONE,
            Engine19ActivityMatcherAndStitcher.evaluateStitchPair(morningRun, secondStandaloneRun).outcome
        )

        val overlappingCopy = morningRun.copy(
            activityId = "raw-run-hc",
            startUtc = baseUtc.plusSeconds(300),
            endUtc = baseUtc.plusSeconds(2000),
            role = StitchSegmentRole.MAIN
        )
        assertEquals(
            StitchOutcome.ROUTE_TO_DEDUP_OVERLAP,
            Engine19ActivityMatcherAndStitcher.evaluateStitchPair(morningRun, overlappingCopy).outcome
        )

        // Valid Warm-Up + Main Threshold separated by 5 minutes with same watch & GPS proximity (<250 m)
        val warmUp = StitchableRecording(
            activityId = "wu-1",
            discipline = "run",
            startUtc = baseUtc,
            endUtc = baseUtc.plusSeconds(600),
            role = StitchSegmentRole.WARM_UP,
            deviceSource = "garmin-965",
            structuredWorkoutId = "wk-thr-1",
            endLatDeg = 1.3000,
            endLonDeg = 103.8000
        )
        val main = StitchableRecording(
            activityId = "main-1",
            discipline = "run",
            startUtc = baseUtc.plusSeconds(900),
            endUtc = baseUtc.plusSeconds(2400),
            role = StitchSegmentRole.MAIN,
            deviceSource = "garmin-965",
            structuredWorkoutId = "wk-thr-1",
            startLatDeg = 1.3005,
            startLonDeg = 103.8005
        )
        val stitched = Engine19ActivityMatcherAndStitcher.evaluateStitchPair(warmUp, main)
        assertEquals(StitchOutcome.STITCHED, stitched.outcome)
        assertEquals(listOf("wu-1", "main-1"), stitched.composite?.componentRawActivityIds)
    }

    @Test fun cd03Mod22GlobalHungarianMatcherEnforcesOneToOneAndPreservesAllObservedWorkload() {
        val sessWedThr = MatchablePlannedSession("s-wed-thr", "run", monday.plusDays(2), plannedMinutes = 45, isQualityPlanned = true)
        val sessFriEasy = MatchablePlannedSession("s-fri-easy", "run", monday.plusDays(4), plannedMinutes = 40, isQualityPlanned = false)
        val actJog = MatchableActivity("a-wed-jog", "run", monday.plusDays(2), durationMinutes = 12, isQualityObserved = false)
        val actTempo = MatchableActivity("a-wed-tempo", "run", monday.plusDays(2), durationMinutes = 45, isQualityObserved = true)
        val actSunShift = MatchableActivity("a-sun-shift", "run", monday.plusDays(6), durationMinutes = 90, isQualityObserved = false)
        val actUnplannedExtra = MatchableActivity("a-next-tue-extra", "run", monday.plusDays(8), durationMinutes = 50, isQualityObserved = false)

        val result = Engine19ActivityMatcherAndStitcher.matchGlobalBipartite(
            activities = listOf(actJog, actTempo, actSunShift, actUnplannedExtra),
            plannedSessions = listOf(sessWedThr, sessFriEasy)
        )
        // Tempo binds 1-to-1 to Wednesday Threshold; Sunday shifted candidate asks AMBIGUOUS_USER_CONFIRM (no auto credit);
        // Wednesday 12m jog & next-Tuesday 50m run stay UNPLANNED_EXTRA while all 4 stay in observedWorkload!
        assertEquals("s-wed-thr", result.assignmentsByActivityId.getValue("a-wed-tempo").plannedSessionId)
        assertEquals(BipartiteMatchConfidence.AUTO_MATCH_HIGH, result.assignmentsByActivityId.getValue("a-wed-tempo").confidence)
        assertEquals(BipartiteMatchConfidence.UNPLANNED_EXTRA, result.assignmentsByActivityId.getValue("a-wed-jog").confidence)
        assertEquals(BipartiteMatchConfidence.AMBIGUOUS_USER_CONFIRM, result.assignmentsByActivityId.getValue("a-sun-shift").confidence)
        assertEquals(BipartiteMatchConfidence.UNPLANNED_EXTRA, result.assignmentsByActivityId.getValue("a-next-tue-extra").confidence)
        assertEquals(setOf("a-wed-jog", "a-wed-tempo", "a-sun-shift", "a-next-tue-extra"), result.observedWorkloadActivityIds)
        assertEquals(1, result.matchedSessionIds.size)
        assertTrue("s-fri-easy" in result.missedSessionIds)
    }

    @Test fun mod21HrvStandardizationRejectsInsufficientMixedProtocolAndSdnn() {
        // D25: Only 2 valid days in 7d window -> abstain cleanly
        val twoDaysOnly = (0..1).map {
            HrvReading(monday.minusDays(it.toLong()), 52.0, HrvProtocol.WAKING_SUPINE, HrvMetricType.RMSSD_MS)
        }
        val insufficient = Engine19ExecutionAndDifferentialEngine.evaluateStandardizedHrv(twoDaysOnly, monday)
        assertTrue(insufficient is HrvEvaluationResult.InsufficientOrInvalid)
        assertEquals("HRV_INSUFFICIENT_SAMPLES", (insufficient as HrvEvaluationResult.InsufficientOrInvalid).reasonCode)

        // Mixed protocols (SUPINE + SEATED) rejected
        val mixedProto = listOf(
            HrvReading(monday, 52.0, HrvProtocol.WAKING_SUPINE, HrvMetricType.RMSSD_MS),
            HrvReading(monday.minusDays(1), 54.0, HrvProtocol.WAKING_SEATED, HrvMetricType.RMSSD_MS)
        )
        val protoErr = Engine19ExecutionAndDifferentialEngine.evaluateStandardizedHrv(mixedProto, monday)
        assertEquals("HRV_MIXED_PROTOCOLS", (protoErr as HrvEvaluationResult.InsufficientOrInvalid).reasonCode)

        // SDNN rejected
        val sdnnErr = Engine19ExecutionAndDifferentialEngine.evaluateStandardizedHrv(
            listOf(HrvReading(monday, 60.0, HrvProtocol.WAKING_SUPINE, HrvMetricType.SDNN_MS)), monday
        )
        assertEquals("HRV_MIXED_OR_UNSUPPORTED_SDNN", (sdnnErr as HrvEvaluationResult.InsufficientOrInvalid).reasonCode)

        // Valid 28-day standardized series computes scale-invariant ln(rMSSD_ms) baseline
        val fullSeries = (0 until 21).map { offset ->
            val valMs = if (offset < 4) 38.0 else 58.0
            HrvReading(monday.minusDays(offset.toLong()), valMs, HrvProtocol.WAKING_SUPINE, HrvMetricType.RMSSD_MS)
        }
        val valid = Engine19ExecutionAndDifferentialEngine.evaluateStandardizedHrv(fullSeries, monday)
        assertTrue(valid is HrvEvaluationResult.Valid)
        valid as HrvEvaluationResult.Valid
        assertEquals(21, valid.valid28DayCount)
        assertTrue(valid.belowSmallestWorthwhileChange)
    }

    @Test fun cd13AndCd14DifferentialHandlesConflictingRecoveryWithoutDiagnosticOverreach() {
        val assess = MultidimensionalExecutionAssessment(
            activityId = "act-conflict",
            plannedSessionId = "s-thr-1",
            discipline = "run",
            completion = SessionCompletionState.COMPLETE,
            stimulusOutcome = StimulusOutcome.OVERSHOT_INTENSITY_RISK,
            domainNote = "Threshold intervals exceeded target speed and RPE 9.5.",
            envHeatConfounded = false,
            envAltitudeOrTerrainConfounded = false,
            telemetryCorruptedOrQuarantined = false,
            reportedRpe = 9,
            targetRpeRange = 7..8,
            reportedRir = null,
            symptomStatus = SymptomStatus.CONFIRMED_NONE,
            recoveryEvidence = RecoveryEvidenceStatus.VERIFIED_ACCEPTABLE,
            techniqueStatus = TechniqueObservationStatus.UNOBSERVED,
            durationAdherence = DimensionValue.Measured(1.0),
            distanceAdherence = DimensionValue.Measured(1.02),
            intensityAdherence = DimensionValue.Measured(IntensityAdherenceBand.EXCESSIVE_ABOVE),
            repetitionStructureAdherence = DimensionValue.Measured(1.0),
            recoveryIntervalAdherence = DimensionValue.Measured(true),
            strengthLoadRepAdherence = DimensionValue.NotApplicable,
            calisthenicsNodeAndTechAdherence = DimensionValue.NotApplicable,
            conditioningDensityAdherence = DimensionValue.NotApplicable,
            subjectiveDifficultyAligned = DimensionValue.Measured(false),
            lateRepOrFinishFadeDetected = true,
            difficultyFromRepOne = false,
            objectiveHrOrPaceAnomalyFlag = true
        )
        val diff = Engine19ExecutionAndDifferentialEngine.evaluateDifferentialHypotheses(
            DifferentialContextInput(
                assessment = assess,
                subjectiveFeltFineDespiteObjectiveAnomaly = true
            )
        )
        assertTrue(diff.conflictingRecoveryEvidenceFlag)
        assertEquals(11, diff.hypotheses.size)
        assertTrue(diff.descriptivePattern.contains("subjective vs objective conflict flagged"))

        // CD-13: Unobserved technique NEVER claims "Your technique was clean"
        val audit = Engine19ExplanationAndNotificationPolicy.validateAndSanitizeCopy(
            rawText = "Great job — your technique was clean and your fitness improved!",
            techniqueCoachVerified = false
        )
        assertFalse(audit.clean)
        assertFalse(audit.sanitizedText.contains("your technique was clean", ignoreCase = true))
        assertFalse(audit.sanitizedText.contains("your fitness improved", ignoreCase = true))
        assertFalse(audit.sanitizedText.contains("—"))
        assertFalse(audit.sanitizedText.contains("--"))
    }
}

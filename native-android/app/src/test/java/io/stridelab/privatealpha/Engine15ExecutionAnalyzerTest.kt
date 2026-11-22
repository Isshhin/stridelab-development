package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class Engine15ExecutionAnalyzerTest {
    private val date = LocalDate.of(2026, 11, 25)

    private fun activitySummary(
        id: String = "act-1",
        distanceM: Double? = 10_000.0,
        durationS: Double? = 3_000.0,
        paceSecPerKm: Double? = 300.0,
        avgHr: Double? = 150.0,
        elevationM: Double? = 25.0
    ): ActivityRecord = ActivityRecord(
        id = id,
        title = "Recorded Run",
        type = "running",
        source = "garmin",
        startedAt = date.atTime(7, 0).toInstant(ZoneOffset.UTC),
        distanceM = distanceM,
        durationS = durationS,
        movingTimeS = durationS,
        recordedPace = paceSecPerKm,
        averageHr = avgHr,
        elevationM = elevationM
    )

    private fun buildSamplesFromBlocks(
        blocks: List<Triple<Int, Double, Triple<Double?, Double, Double>>> // (count, stepSeconds, (pace, hr, altStart))
    ): List<ActivitySample> {
        val list = ArrayList<ActivitySample>()
        var idx = 0
        var elapsed = 0.0
        for ((count, stepSec, metrics) in blocks) {
            val (pace, hr, altBase) = metrics
            for (i in 0 until count) {
                list += ActivitySample(
                    index = idx,
                    seconds = elapsed,
                    pace = pace,
                    hr = hr,
                    altitude = altBase + i * 0.1,
                    latitude = null,
                    longitude = null
                )
                idx++
                elapsed += stepSec
            }
        }
        return list
    }

    private fun thresholdSession(): PlannedSession = PlannedSession(
        id = "s-threshold-10k",
        date = date,
        discipline = "run",
        title = "Cruise Threshold 6 km",
        minutes = 50,
        distanceKm = 10.0,
        kind = "threshold",
        quality = true,
        runningPrescription = RunningPrescription(
            role = "QUALITY",
            family = "Threshold",
            segments = listOf(
                RunningSegment("WARM_UP", 2000, "Easy warm-up", RunningStepIntent.WARMUP, 340, 365),
                RunningSegment("WORK", 6000, "Sustained LT", RunningStepIntent.THRESHOLD, 255, 270),
                RunningSegment("COOL_DOWN", 2000, "Easy cool-down", RunningStepIntent.COOLDOWN, 345, 370)
            )
        )
    )

    private fun easySession(): PlannedSession = PlannedSession(
        id = "s-easy-10k",
        date = date,
        discipline = "run",
        title = "Easy Aerobic Run",
        minutes = 58,
        distanceKm = 10.0,
        kind = "easy",
        quality = false,
        runningPrescription = RunningPrescription(
            role = "EASY",
            family = null,
            segments = listOf(
                RunningSegment("EASY", 10000, "Relaxed aerobic Z2", RunningStepIntent.AEROBIC_EASY, 335, 365)
            )
        )
    )

    private fun marathonLongSession(): PlannedSession = PlannedSession(
        id = "s-mp-long-20k",
        date = date,
        discipline = "run",
        title = "Marathon-Pace Long Run",
        minutes = 112,
        distanceKm = 20.0,
        kind = "long",
        quality = false,
        runningPrescription = RunningPrescription(
            role = "LONG",
            family = null,
            segments = listOf(
                RunningSegment("EASY", 14000, "Easy aerobic cruise", RunningStepIntent.AEROBIC_EASY, 340, 365),
                RunningSegment("TRANSITION", 6000, "Marathon goal race pace finish.", RunningStepIntent.RACE_PACE, 280, 295)
            ),
            archetypeId = Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id
        )
    )

    @Test
    fun cleanThresholdWithStructuredLapsProducesOnTargetLapAndRecord() {
        val session = thresholdSession()
        val laps = listOf(
            ActivityLap(0, 2000.0, 700.0, 350.0, 138.0, startSeconds = 0.0),
            ActivityLap(1, 6000.0, 1572.0, 262.0, 169.0, startSeconds = 700.0),
            ActivityLap(2, 2000.0, 710.0, 355.0, 145.0, startSeconds = 2272.0)
        )
        val samples = buildSamplesFromBlocks(
            listOf(
                Triple(10, 70.0, Triple(350.0, 138.0, 50.0)),
                Triple(20, 78.6, Triple(262.0, 169.0, 50.0)),
                Triple(10, 71.0, Triple(355.0, 145.0, 50.0))
            )
        )
        val detail = ActivityDetail("act-thresh-clean", samples, laps, 1L)
        val activity = activitySummary("act-thresh-clean", 10_000.0, 2982.0, 298.0, 155.0)
        val hrZones = ExecutionHrZoneProfile(110, 132, 150, 165, 178, 192)

        val analysis = Engine15ExecutionAnalyzer.analyze(session, activity, detail, hrZones)

        assertEquals(ExecutionEvidenceTier.LAP_AND_RECORD, analysis.evidenceTier)
        assertEquals(ExecutionVerdict.ON_TARGET, analysis.verdict)
        assertEquals(true, analysis.qualityStimulusVerified)
        assertEquals(ExecutionPaceBias.ON_TARGET, analysis.paceBias)
        assertTrue((analysis.workStepPaceAdherencePct ?: 0.0) >= 85.0)
        assertNotNull(analysis.hrZoneCompliancePct)
    }

    @Test
    fun unlappedThresholdFallsBackToRecordWindowAndVerifiesWorkBlock() {
        val session = thresholdSession()
        // Only 1 lap covering the whole run, so RECORD_WINDOW must find the contiguous 6 km threshold block.
        val samples = buildSamplesFromBlocks(
            listOf(
                Triple(8, 87.5, Triple(355.0, 138.0, 40.0)),   // ~2km @ 5:55/km
                Triple(24, 65.5, Triple(262.0, 170.0, 40.0)),  // ~6km @ 4:22/km
                Triple(8, 87.5, Triple(355.0, 145.0, 40.0))    // ~2km @ 5:55/km
            )
        )
        val detail = ActivityDetail(
            activityId = "act-thresh-unlapped",
            samples = samples,
            laps = listOf(ActivityLap(0, 10_000.0, 2972.0, 297.0, 157.0, startSeconds = 0.0)),
            fetchedAt = 1L
        )
        val activity = activitySummary("act-thresh-unlapped", 10_000.0, 2972.0, 297.0, 157.0)

        val analysis = Engine15ExecutionAnalyzer.analyze(session, activity, detail, athleteHrZones = null)

        assertEquals(ExecutionEvidenceTier.RECORD_WINDOW, analysis.evidenceTier)
        assertEquals(ExecutionVerdict.ON_TARGET, analysis.verdict)
        assertEquals(true, analysis.qualityStimulusVerified)
        assertNull(analysis.hrZoneCompliancePct)
        assertNull(analysis.aerobicDecouplingPct)
    }

    @Test
    fun easyDayRunTooFastIsFlaggedAsIntensityMismatchTooFastNeverOverachieved() {
        val session = easySession() // 10 km @ 5:35-6:05/km (335-365 s/km)
        val samples = buildSamplesFromBlocks(
            listOf(Triple(30, 88.0, Triple(265.0, 168.0, 20.0))) // 4:25/km!
        )
        val detail = ActivityDetail("act-easy-fast", samples, emptyList(), 1L)
        val activity = activitySummary("act-easy-fast", 10_000.0, 2650.0, 265.0, 168.0)

        val analysis = Engine15ExecutionAnalyzer.analyze(session, activity, detail, null)

        assertEquals(ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST, analysis.verdict)
        assertEquals(ExecutionPaceBias.TOO_FAST, analysis.paceBias)
        assertTrue("EASY_DAY_TOO_FAST" in analysis.reasonCodes)
        assertFalse(analysis.coachingSummary.contains("overachiev", ignoreCase = true))
    }

    @Test
    fun tenKmEasyJogOnThresholdDayIsVolumeOnlyMatchWithUnverifiedQuality() {
        val session = thresholdSession() // 10 km total w/ 6 km @ 4:15-4:30/km
        val samples = buildSamplesFromBlocks(
            listOf(Triple(36, 100.0, Triple(360.0, 138.0, 30.0))) // 10 km steady @ 6:00/km
        )
        val detail = ActivityDetail(
            activityId = "act-jog-thresh",
            samples = samples,
            laps = listOf(
                ActivityLap(0, 5000.0, 1800.0, 360.0, 137.0, startSeconds = 0.0),
                ActivityLap(1, 5000.0, 1800.0, 360.0, 139.0, startSeconds = 1800.0)
            ),
            fetchedAt = 1L
        )
        val activity = activitySummary("act-jog-thresh", 10_000.0, 3600.0, 360.0, 138.0)

        val analysis = Engine15ExecutionAnalyzer.analyze(session, activity, detail, null)

        assertEquals(ExecutionVerdict.VOLUME_ONLY_MATCH, analysis.verdict)
        assertEquals(false, analysis.qualityStimulusVerified)
        assertEquals(ExecutionPaceBias.TOO_SLOW, analysis.paceBias)
        assertTrue("WORK_STAYED_IN_EASY_ZONE" in analysis.reasonCodes)
    }

    @Test
    fun cutShortWorkoutGradesAsPartiallyExecuted() {
        val session = thresholdSession()
        val activity = activitySummary("act-short", distanceM = 5_000.0, durationS = 1_500.0, paceSecPerKm = 300.0)

        val analysis = Engine15ExecutionAnalyzer.analyze(session, activity, detail = null, athleteHrZones = null)

        assertEquals(ExecutionEvidenceTier.SUMMARY_ONLY, analysis.evidenceTier)
        assertEquals(ExecutionVerdict.PARTIALLY_EXECUTED, analysis.verdict)
        assertEquals(false, analysis.qualityStimulusVerified)
    }

    @Test
    fun hillRepeatsVerifyContinuityAndElevationGainWithoutFlatPacePenalty() {
        val hillSession = PlannedSession(
            id = "s-hills",
            date = date,
            discipline = "run",
            title = "Short Steep Hill Repeats",
            minutes = 45,
            distanceKm = 8.0,
            kind = "hills",
            quality = true,
            runningPrescription = RunningPrescription(
                role = "QUALITY",
                family = "Hills",
                segments = listOf(
                    RunningSegment("WARM_UP", 2500, "Easy warm-up", RunningStepIntent.WARMUP, 340, 365),
                    RunningSegment("WORK", 3000, "Uphill hard repeats", RunningStepIntent.HILL, 260, 285),
                    RunningSegment("COOL_DOWN", 2500, "Easy cool-down", RunningStepIntent.COOLDOWN, 345, 370)
                )
            )
        )
        // Uphill pace is slow (330 s/km vs 260-285 flat eq), but altitude climbs cleanly (+95 m).
        val hillSamples = ArrayList<ActivitySample>()
        var sec = 0.0
        for (i in 0 until 15) {
            hillSamples += ActivitySample(i, sec, 350.0, 138.0, 20.0, null, null)
            sec += 60.0
        }
        for (i in 15 until 35) {
            hillSamples += ActivitySample(i, sec, 330.0, 172.0, 20.0 + (i - 14) * 5.0, null, null)
            sec += 48.0
        }
        for (i in 35 until 50) {
            hillSamples += ActivitySample(i, sec, 355.0, 145.0, 25.0, null, null)
            sec += 60.0
        }
        val detail = ActivityDetail("act-hills", hillSamples, emptyList(), 1L)
        val activity = activitySummary("act-hills", 8_000.0, 2760.0, 345.0, 152.0, elevationM = 110.0)

        val analysis = Engine15ExecutionAnalyzer.analyze(hillSession, activity, detail, null)

        assertEquals(ExecutionVerdict.ON_TARGET, analysis.verdict)
        assertEquals(true, analysis.qualityStimulusVerified)
        assertTrue("FLAT_PACE_PENALTY_WAIVED_FOR_HILLS" in analysis.reasonCodes)
    }

    @Test
    fun stridesTolerateShortGpsLagAndVerifyEffortSurges() {
        val strideSession = PlannedSession(
            id = "s-strides",
            date = date,
            discipline = "run",
            title = "Easy Aerobic + 4 x 20s Strides",
            minutes = 46,
            distanceKm = 8.0,
            kind = "strides",
            quality = true,
            runningPrescription = RunningPrescription(
                role = "QUALITY",
                family = "Strides",
                segments = emptyList(),
                mixedSegments = listOf(
                    RunningStepV2("EASY", "Easy aerobic base", meters = 7500, stepIntent = RunningStepIntent.AEROBIC_EASY, targetPaceMinSecPerKm = 335, targetPaceMaxSecPerKm = 365),
                    RunningStepV2("WORK", "Stride 1", seconds = 20, distanceEquivalentMeters = 125, stepIntent = RunningStepIntent.STRIDE, targetPaceMinSecPerKm = 195, targetPaceMaxSecPerKm = 225),
                    RunningStepV2("WORK", "Stride 2", seconds = 20, distanceEquivalentMeters = 125, stepIntent = RunningStepIntent.STRIDE, targetPaceMinSecPerKm = 195, targetPaceMaxSecPerKm = 225),
                    RunningStepV2("WORK", "Stride 3", seconds = 20, distanceEquivalentMeters = 125, stepIntent = RunningStepIntent.STRIDE, targetPaceMinSecPerKm = 195, targetPaceMaxSecPerKm = 225),
                    RunningStepV2("WORK", "Stride 4", seconds = 20, distanceEquivalentMeters = 125, stepIntent = RunningStepIntent.STRIDE, targetPaceMinSecPerKm = 195, targetPaceMaxSecPerKm = 225)
                ),
                reconciledDistanceMeters = 8000
            )
        )
        // Surges register ~245-260 s/km due to GPS lag on 20s bursts (slower than 195-225 s/km flat stride band),
        // yet clearly faster than the ~350 s/km aerobic base.
        val samples = buildSamplesFromBlocks(
            listOf(
                Triple(25, 100.0, Triple(350.0, 140.0, 20.0)),
                Triple(4, 20.0, Triple(250.0, 160.0, 20.0)),
                Triple(5, 60.0, Triple(350.0, 142.0, 20.0))
            )
        )
        val detail = ActivityDetail("act-strides", samples, emptyList(), 1L)
        val activity = activitySummary("act-strides", 8_000.0, 2880.0, 360.0, 143.0)

        val analysis = Engine15ExecutionAnalyzer.analyze(strideSession, activity, detail, null)

        assertEquals(ExecutionVerdict.ON_TARGET, analysis.verdict)
        assertEquals(true, analysis.qualityStimulusVerified)
        assertTrue("GPS_LAG_TOLERATED_FOR_SHORT_STRIDES" in analysis.reasonCodes)
    }

    @Test
    fun marathonPaceLongRunVerifiesFastFinishPositiveAndDetectsEasyFadeNegative() {
        val session = marathonLongSession() // 14 km easy (340-365) + 6 km MP finish (280-295)

        val hitLaps = listOf(
            ActivityLap(0, 14_000.0, 4_900.0, 350.0, 145.0, startSeconds = 0.0),
            ActivityLap(1, 6_000.0, 1_728.0, 288.0, 163.0, startSeconds = 4_900.0)
        )
        val hitSamples = buildSamplesFromBlocks(
            listOf(
                Triple(28, 175.0, Triple(350.0, 145.0, 30.0)),
                Triple(12, 144.0, Triple(288.0, 163.0, 30.0))
            )
        )
        val hitDetail = ActivityDetail("act-mp-hit", hitSamples, hitLaps, 1L)
        val hitActivity = activitySummary("act-mp-hit", 20_000.0, 6_628.0, 331.0, 150.0)
        val hitResult = Engine15ExecutionAnalyzer.analyze(session, hitActivity, hitDetail, null)

        assertEquals(ExecutionEvidenceTier.LAP_AND_RECORD, hitResult.evidenceTier)
        assertEquals(ExecutionVerdict.ON_TARGET, hitResult.verdict)
        assertEquals(true, hitResult.qualityStimulusVerified)

        // Negative case: ran 20 km total, but closing 6 km stayed at easy 355 s/km.
        val missLaps = listOf(
            ActivityLap(0, 14_000.0, 4_900.0, 350.0, 144.0, startSeconds = 0.0),
            ActivityLap(1, 6_000.0, 2_130.0, 355.0, 146.0, startSeconds = 4_900.0)
        )
        val missSamples = buildSamplesFromBlocks(
            listOf(
                Triple(28, 175.0, Triple(350.0, 144.0, 30.0)),
                Triple(12, 177.5, Triple(355.0, 146.0, 30.0))
            )
        )
        val missDetail = ActivityDetail("act-mp-miss", missSamples, missLaps, 1L)
        val missActivity = activitySummary("act-mp-miss", 20_000.0, 7_030.0, 352.0, 145.0)
        val missResult = Engine15ExecutionAnalyzer.analyze(session, missActivity, missDetail, null)

        assertEquals(ExecutionVerdict.VOLUME_ONLY_MATCH, missResult.verdict)
        assertEquals(false, missResult.qualityStimulusVerified)
    }

    @Test
    fun missingTelemetryProducesInsufficientTelemetryVerdict() {
        val session = easySession()
        val emptyActivity = activitySummary(
            id = "act-empty",
            distanceM = null,
            durationS = null,
            paceSecPerKm = null,
            avgHr = null
        )
        val analysis = Engine15ExecutionAnalyzer.analyze(session, emptyActivity, null, null)

        assertEquals(ExecutionVerdict.INSUFFICIENT_TELEMETRY, analysis.verdict)
        assertEquals(ExecutionPaceBias.UNKNOWN, analysis.paceBias)
        assertNull(analysis.hrZoneCompliancePct)
        assertNull(analysis.aerobicDecouplingPct)
    }

    @Test
    fun validHrProfileComputesZoneComplianceAndAerobicDecouplingForSteadyLongRun() {
        val session = easySession().copy(minutes = 55)
        // First half: 345 s/km @ 140 bpm, Second half: 355 s/km @ 150 bpm (clear positive decoupling)
        val samples = buildSamplesFromBlocks(
            listOf(
                Triple(15, 115.0, Triple(345.0, 140.0, 10.0)),
                Triple(15, 118.0, Triple(355.0, 150.0, 10.0))
            )
        )
        val detail = ActivityDetail("act-hr-steady", samples, emptyList(), 1L)
        val activity = activitySummary("act-hr-steady", 10_000.0, 3_495.0, 350.0, 145.0)
        val hrZones = ExecutionHrZoneProfile(115, 134, 152, 166, 180, 194)

        val withHr = Engine15ExecutionAnalyzer.analyze(session, activity, detail, hrZones)
        val withoutHr = Engine15ExecutionAnalyzer.analyze(session, activity, detail, null)

        assertNotNull(withHr.hrZoneCompliancePct)
        assertNotNull(withHr.aerobicDecouplingPct)
        assertTrue((withHr.hrZoneCompliancePct ?: 0.0) > 85.0)
        assertTrue((withHr.aerobicDecouplingPct ?: 0.0) > 5.0)

        // Strictly null when no verified HR profile is supplied (never fabricates 220 - age).
        assertNull(withoutHr.hrZoneCompliancePct)
        assertNull(withoutHr.aerobicDecouplingPct)
    }

    @Test
    fun workoutExecutionAnalysisJsonRoundTripAndLegacyPlanSnapshotCompat() {
        val session = thresholdSession()
        val activity = activitySummary("act-rt", 10_000.0, 3000.0, 300.0, 155.0)
        val original = Engine15ExecutionAnalyzer.analyze(session, activity, null, null, 1_750_000_000_000L)
        val encoded = original.encode(owner = "local-owner-1")
        val decoded = WorkoutExecutionAnalysis.decode(encoded, expectedOwner = "local-owner-1")
        assertEquals(original, decoded)

        // Verify pre-Step-04 PlanSnapshot JSON without any new execution fields decodes cleanly.
        val legacySessionJson = session.encode().apply {
            remove("difficultyStep")
            remove("difficultyBaseline")
        }
        val request = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = date,
            weeks = 8,
            runDays = 4,
            days = setOf(2, 4, 6, 7),
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 32.0,
            longestKm = 12.0,
            targetWeeklyKm = 40.0
        )
        val legacySnapshotJson = JSONObject()
            .put("schemaVersion", PLAN_SCHEMA)
            .put("id", "plan-legacy")
            .put("user_id", "local-owner-1")
            .put("engineVersion", "1.5")
            .put("policyId", "engine15")
            .put("createdAt", Instant.parse("2026-11-20T06:00:00Z").toString())
            .put("request", request.encode())
            .put("sessions", JSONArray().put(legacySessionJson))
            .put("explanations", JSONArray())
            .put("status", "approved")
            .put("feedback", JSONArray())
            .put("revision", 1)
        val decodedPlan = PlanSnapshot.decode(legacySnapshotJson, "local-owner-1")
        assertEquals(1, decodedPlan.sessions.size)
        assertEquals("s-threshold-10k", decodedPlan.sessions.single().id)
    }

    @Test
    fun printSampleExecutionAnalysisOutputsForFourScenarios() {
        // 1. Clean threshold
        val s1 = thresholdSession()
        val d1 = ActivityDetail(
            "a1",
            buildSamplesFromBlocks(
                listOf(
                    Triple(10, 70.0, Triple(350.0, 138.0, 50.0)),
                    Triple(20, 78.6, Triple(262.0, 169.0, 50.0)),
                    Triple(10, 71.0, Triple(355.0, 145.0, 50.0))
                )
            ),
            listOf(
                ActivityLap(0, 2000.0, 700.0, 350.0, 138.0, startSeconds = 0.0),
                ActivityLap(1, 6000.0, 1572.0, 262.0, 169.0, startSeconds = 700.0),
                ActivityLap(2, 2000.0, 710.0, 355.0, 145.0, startSeconds = 2272.0)
            ),
            1L
        )
        val r1 = Engine15ExecutionAnalyzer.analyze(s1, activitySummary("a1", 10_000.0, 2982.0, 298.0, 155.0), d1)

        // 2. Easy day too fast
        val s2 = easySession()
        val d2 = ActivityDetail("a2", buildSamplesFromBlocks(listOf(Triple(30, 88.0, Triple(265.0, 168.0, 20.0)))), emptyList(), 1L)
        val r2 = Engine15ExecutionAnalyzer.analyze(s2, activitySummary("a2", 10_000.0, 2650.0, 265.0, 168.0), d2)

        // 3. 10 km easy on threshold day
        val s3 = thresholdSession()
        val d3 = ActivityDetail(
            "a3",
            buildSamplesFromBlocks(listOf(Triple(36, 100.0, Triple(360.0, 138.0, 30.0)))),
            listOf(
                ActivityLap(0, 5000.0, 1800.0, 360.0, 137.0, startSeconds = 0.0),
                ActivityLap(1, 5000.0, 1800.0, 360.0, 139.0, startSeconds = 1800.0)
            ),
            1L
        )
        val r3 = Engine15ExecutionAnalyzer.analyze(s3, activitySummary("a3", 10_000.0, 3600.0, 360.0, 138.0), d3)

        // 4. Marathon-pace long run
        val s4 = marathonLongSession()
        val d4 = ActivityDetail(
            "a4",
            buildSamplesFromBlocks(
                listOf(
                    Triple(28, 175.0, Triple(350.0, 145.0, 30.0)),
                    Triple(12, 144.0, Triple(288.0, 163.0, 30.0))
                )
            ),
            listOf(
                ActivityLap(0, 14_000.0, 4_900.0, 350.0, 145.0, startSeconds = 0.0),
                ActivityLap(1, 6_000.0, 1_728.0, 288.0, 163.0, startSeconds = 4_900.0)
            ),
            1L
        )
        val r4 = Engine15ExecutionAnalyzer.analyze(s4, activitySummary("a4", 20_000.0, 6628.0, 331.0, 150.0), d4)

        val rows = listOf(
            "CLEAN_THRESHOLD" to r1,
            "EASY_DAY_TOO_FAST" to r2,
            "EASY_10K_ON_THRESHOLD_DAY" to r3,
            "MARATHON_PACE_LONG_RUN" to r4
        )
        println("=== STEP 04 SAMPLE EXECUTION ANALYSES ===")
        for ((label, a) in rows) {
            println(
                "$label | tier=${a.evidenceTier} | verdict=${a.verdict} | volRatio=${a.volumeRatio} | " +
                    "workAdh=${a.workStepPaceAdherencePct} | qVerified=${a.qualityStimulusVerified} | " +
                    "bias=${a.paceBias} | summary=${a.coachingSummary}"
            )
        }
        assertEquals(ExecutionVerdict.ON_TARGET, r1.verdict)
        assertEquals(ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST, r2.verdict)
        assertEquals(ExecutionVerdict.VOLUME_ONLY_MATCH, r3.verdict)
        assertEquals(ExecutionVerdict.ON_TARGET, r4.verdict)
    }
}

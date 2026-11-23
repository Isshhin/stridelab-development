package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class Engine15AthleteDiagnosticsEngineTest {
    private val zone = ZoneOffset.UTC
    private val asOf = LocalDate.of(2026, 11, 22)
    private val hrZones = ExecutionHrZoneProfile(110, 134, 152, 166, 180, 194)

    private fun basePlan(sessions: List<PlannedSession>, id: String = "plan-diag-1"): PlanSnapshot = PlanSnapshot(
        id = id,
        owner = "diag-owner",
        engineVersion = "1.5",
        policyId = "engine15",
        createdAt = asOf.minusDays(30).atTime(6, 0).toInstant(ZoneOffset.UTC),
        request = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = asOf.minusDays(27),
            weeks = 8,
            runDays = 4,
            days = setOf(2, 4, 6, 7),
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 36.0,
            longestKm = 14.0,
            targetWeeklyKm = 44.0
        ),
        sessions = sessions,
        explanations = emptyList(),
        status = "approved"
    )

    private fun easySession(id: String, date: LocalDate, km: Double = 8.0): PlannedSession = PlannedSession(
        id = id,
        date = date,
        discipline = "run",
        title = "Easy Aerobic Run",
        minutes = (km * 6).toInt(),
        distanceKm = km,
        kind = "easy",
        quality = false,
        runningPrescription = RunningPrescription(
            role = "EASY",
            family = null,
            segments = listOf(RunningSegment("EASY", (km * 1000).toInt(), "Easy Z2", RunningStepIntent.AEROBIC_EASY, 335, 365))
        )
    )

    private fun qualitySession(id: String, date: LocalDate, km: Double = 10.0): PlannedSession = PlannedSession(
        id = id,
        date = date,
        discipline = "run",
        title = "Threshold Cruise",
        minutes = 50,
        distanceKm = km,
        kind = "threshold",
        quality = true,
        runningPrescription = RunningPrescription(
            role = "QUALITY",
            family = "Threshold",
            segments = listOf(
                RunningSegment("WARM_UP", 2000, "WU", RunningStepIntent.WARMUP, 340, 365),
                RunningSegment("WORK", ((km - 4.0).coerceAtLeast(2.0) * 1000).toInt(), "LT", RunningStepIntent.THRESHOLD, 255, 270),
                RunningSegment("COOL_DOWN", 2000, "CD", RunningStepIntent.COOLDOWN, 345, 370)
            )
        )
    )

    private fun runActivity(
        id: String,
        date: LocalDate,
        km: Double = 8.0,
        minutes: Double = 45.0,
        paceSecPerKm: Double = 345.0,
        avgHr: Double = 142.0
    ): ActivityRecord = ActivityRecord(
        id = id,
        title = "Run $id",
        type = "running",
        source = "garmin",
        startedAt = date.atTime(7, 0).toInstant(ZoneOffset.UTC),
        distanceM = km * 1000.0,
        durationS = minutes * 60.0,
        movingTimeS = minutes * 60.0,
        recordedPace = paceSecPerKm,
        averageHr = avgHr,
        elevationM = 30.0
    )

    private fun linkedMatch(activityId: String, sessionId: String): ActivityMatchRecord = ActivityMatchRecord(
        activityId = activityId,
        plannedSessionId = sessionId,
        matchStatus = ActivityMatchStatus.AUTO_LINKED,
        confidenceScore = 0.94,
        dateOffsetDays = 0,
        reasonCodes = listOf("DATE_EXACT_MATCH", "VOLUME_STRONG_MATCH")
    )

    private fun easyAnalysis(
        sessionId: String,
        activityId: String,
        verdict: ExecutionVerdict,
        actualPace: Double,
        decouplingPct: Double? = 2.0,
        tier: ExecutionEvidenceTier = ExecutionEvidenceTier.LAP_AND_RECORD
    ): WorkoutExecutionAnalysis = WorkoutExecutionAnalysis(
        plannedSessionId = sessionId,
        activityId = activityId,
        evidenceTier = tier,
        verdict = verdict,
        volumeRatio = 1.0,
        plannedKm = 8.0,
        actualKm = 8.0,
        plannedMinutes = 48,
        actualMinutes = 46.0,
        workStepPaceAdherencePct = if (verdict == ExecutionVerdict.ON_TARGET) 95.0 else 15.0,
        qualityStimulusVerified = null,
        paceBias = if (verdict == ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST) ExecutionPaceBias.TOO_FAST else ExecutionPaceBias.ON_TARGET,
        hrZoneCompliancePct = 90.0,
        aerobicDecouplingPct = decouplingPct,
        stepBreakdowns = listOf(
            ExecutionStepBreakdown(
                stepIndex = 0,
                purpose = "EASY",
                stepIntent = RunningStepIntent.AEROBIC_EASY,
                plannedDistanceKm = 8.0,
                actualDistanceKm = 8.0,
                plannedSeconds = 2880,
                actualSeconds = (8.0 * actualPace).toInt(),
                targetPaceMinSecPerKm = 335,
                targetPaceMaxSecPerKm = 365,
                actualPaceSecPerKm = actualPace,
                paceAdherencePct = if (verdict == ExecutionVerdict.ON_TARGET) 95.0 else 15.0,
                paceBias = if (verdict == ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST) ExecutionPaceBias.TOO_FAST else ExecutionPaceBias.ON_TARGET
            )
        ),
        reasonCodes = listOf("TEST_EASY"),
        coachingSummary = "Easy run summary."
    )

    private fun qualityAnalysis(
        sessionId: String,
        activityId: String,
        verdict: ExecutionVerdict,
        workActualPace: Double,
        verified: Boolean,
        tier: ExecutionEvidenceTier = ExecutionEvidenceTier.LAP_AND_RECORD
    ): WorkoutExecutionAnalysis = WorkoutExecutionAnalysis(
        plannedSessionId = sessionId,
        activityId = activityId,
        evidenceTier = tier,
        verdict = verdict,
        volumeRatio = 1.0,
        plannedKm = 10.0,
        actualKm = 10.0,
        plannedMinutes = 50,
        actualMinutes = 48.0,
        workStepPaceAdherencePct = if (verified) 94.0 else 20.0,
        qualityStimulusVerified = verified,
        paceBias = when (verdict) {
            ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW, ExecutionVerdict.VOLUME_ONLY_MATCH -> ExecutionPaceBias.TOO_SLOW
            ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST -> ExecutionPaceBias.TOO_FAST
            else -> ExecutionPaceBias.ON_TARGET
        },
        hrZoneCompliancePct = 92.0,
        aerobicDecouplingPct = null,
        stepBreakdowns = listOf(
            ExecutionStepBreakdown(
                stepIndex = 1,
                purpose = "WORK",
                stepIntent = RunningStepIntent.THRESHOLD,
                plannedDistanceKm = 6.0,
                actualDistanceKm = 6.0,
                plannedSeconds = 1575,
                actualSeconds = (6.0 * workActualPace).toInt(),
                targetPaceMinSecPerKm = 255,
                targetPaceMaxSecPerKm = 270, // midpoint = 262.5 s/km
                actualPaceSecPerKm = workActualPace,
                paceAdherencePct = if (verified) 94.0 else 20.0,
                paceBias = ExecutionPaceBias.ON_TARGET
            )
        ),
        reasonCodes = listOf("TEST_QUALITY"),
        coachingSummary = "Quality workout summary."
    )

    private fun buildSparseNewAthlete(): AthleteDiagnosticSnapshot {
        val s1 = easySession("sp-s1", asOf.minusDays(4), 5.0)
        val s2 = easySession("sp-s2", asOf.minusDays(1), 6.0)
        val a1 = runActivity("sp-a1", asOf.minusDays(4), 5.0, 30.0, 360.0, 140.0)
        val a2 = runActivity("sp-a2", asOf.minusDays(1), 6.0, 36.0, 360.0, 142.0)
        val plan = basePlan(listOf(s1, s2), id = "plan-sparse")
        val matches = mapOf("sp-a1" to linkedMatch("sp-a1", "sp-s1"), "sp-a2" to linkedMatch("sp-a2", "sp-s2"))
        val analyses = mapOf(
            "sp-s1" to easyAnalysis("sp-s1", "sp-a1", ExecutionVerdict.ON_TARGET, 360.0),
            "sp-s2" to easyAnalysis("sp-s2", "sp-a2", ExecutionVerdict.ON_TARGET, 360.0)
        )
        return Engine15AthleteDiagnosticsEngine.evaluate(plan, listOf(a1, a2), matches, analyses, null, asOf, zone)
    }

    private fun buildConsistentProgressor(): AthleteDiagnosticSnapshot {
        val sessions = ArrayList<PlannedSession>()
        val activities = ArrayList<ActivityRecord>()
        val matches = LinkedHashMap<String, ActivityMatchRecord>()
        val analyses = LinkedHashMap<String, WorkoutExecutionAnalysis>()
        for (w in 0 until 4) {
            val wStart = asOf.minusDays(27L - w * 7L)
            val qDate = wStart.plusDays(1)
            val qId = "prog-q-$w"
            val qActId = "prog-qa-$w"
            sessions += qualitySession(qId, qDate, 10.0)
            activities += runActivity(qActId, qDate, 10.0, 45.0, 270.0, 168.0)
            matches[qActId] = linkedMatch(qActId, qId)
            // Faster than midpoint (262.5) by ~6.5 s/km while staying within target tolerance [255..270] -> ON_TARGET + verified
            analyses[qId] = qualityAnalysis(qId, qActId, ExecutionVerdict.ON_TARGET, 256.0, verified = true)

            for ((idx, offset) in listOf(3L, 5L, 6L).withIndex()) {
                val eDate = wStart.plusDays(offset)
                val eId = "prog-e-$w-$idx"
                val eActId = "prog-ea-$w-$idx"
                val dur = if (offset == 6L) 65.0 else 46.0
                val km = if (offset == 6L) 11.5 else 8.0
                // Improving EF across weeks (same pace ~345 s/km, HR drops 148 -> 139)
                val hr = 148.0 - w * 3.0
                sessions += easySession(eId, eDate, km)
                activities += runActivity(eActId, eDate, km, dur, 345.0, hr)
                matches[eActId] = linkedMatch(eActId, eId)
                analyses[eId] = easyAnalysis(eId, eActId, ExecutionVerdict.ON_TARGET, 345.0, decouplingPct = 2.5)
            }
        }
        return Engine15AthleteDiagnosticsEngine.evaluate(
            basePlan(sessions, id = "plan-progressor"),
            activities, matches, analyses, hrZones, asOf, zone
        )
    }

    private fun buildGrayZoneRunner(): AthleteDiagnosticSnapshot {
        val sessions = ArrayList<PlannedSession>()
        val activities = ArrayList<ActivityRecord>()
        val matches = LinkedHashMap<String, ActivityMatchRecord>()
        val analyses = LinkedHashMap<String, WorkoutExecutionAnalysis>()
        for (w in 0 until 4) {
            val wStart = asOf.minusDays(27L - w * 7L)
            val qDate = wStart.plusDays(1)
            val qId = "gz-q-$w"; val qAct = "gz-qa-$w"
            sessions += qualitySession(qId, qDate, 10.0)
            activities += runActivity(qAct, qDate, 10.0, 46.0, 276.0, 172.0)
            matches[qAct] = linkedMatch(qAct, qId)
            analyses[qId] = qualityAnalysis(qId, qAct, ExecutionVerdict.ON_TARGET, 256.0, verified = true)

            for ((idx, offset) in listOf(3L, 5L, 6L).withIndex()) {
                val eDate = wStart.plusDays(offset)
                val eId = "gz-e-$w-$idx"; val eAct = "gz-ea-$w-$idx"
                sessions += easySession(eId, eDate, 8.0)
                activities += runActivity(eAct, eDate, 8.0, 39.0, 292.0, 162.0) // 4:52/km on 5:35-6:05 easy day!
                matches[eAct] = linkedMatch(eAct, eId)
                // Make 75% of easy days TOO_FAST
                val tooFast = idx < 2 || w % 2 == 0
                analyses[eId] = easyAnalysis(
                    eId, eAct,
                    verdict = if (tooFast) ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST else ExecutionVerdict.ON_TARGET,
                    actualPace = if (tooFast) 292.0 else 348.0,
                    decouplingPct = 6.0
                )
            }
        }
        return Engine15AthleteDiagnosticsEngine.evaluate(
            basePlan(sessions, id = "plan-grayzone"),
            activities, matches, analyses, hrZones, asOf, zone
        )
    }

    private fun buildStrugglingFatiguingRunner(): AthleteDiagnosticSnapshot {
        val sessions = ArrayList<PlannedSession>()
        val activities = ArrayList<ActivityRecord>()
        val matches = LinkedHashMap<String, ActivityMatchRecord>()
        val analyses = LinkedHashMap<String, WorkoutExecutionAnalysis>()
        for (w in 0 until 4) {
            val wStart = asOf.minusDays(27L - w * 7L)
            val qDate = wStart.plusDays(1)
            val qId = "st-q-$w"; val qAct = "st-qa-$w"
            sessions += qualitySession(qId, qDate, 10.0)
            activities += runActivity(qAct, qDate, 10.0, 50.0, 300.0, 174.0)
            matches[qAct] = linkedMatch(qAct, qId)
            analyses[qId] = qualityAnalysis(qId, qAct, ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW, 282.0, verified = false)

            for ((idx, offset) in listOf(3L, 5L).withIndex()) {
                val eDate = wStart.plusDays(offset)
                val eId = "st-e-$w-$idx"; val eAct = "st-ea-$w-$idx"
                // Fatiguing HR trend: HR rises 140 -> 158 for same easy pace across weeks + high decoupling
                val hr = 140.0 + w * 6.0
                sessions += easySession(eId, eDate, 8.0)
                activities += runActivity(eAct, eDate, 8.0, 48.0, 360.0, hr)
                matches[eAct] = linkedMatch(eAct, eId)
                analyses[eId] = easyAnalysis(eId, eAct, ExecutionVerdict.ON_TARGET, 360.0, decouplingPct = 9.5)
            }
        }
        return Engine15AthleteDiagnosticsEngine.evaluate(
            basePlan(sessions, id = "plan-struggling"),
            activities, matches, analyses, hrZones, asOf, zone
        )
    }

    @Test
    fun sparseNewAthleteGatesConfidenceAndSuppressesRecalibration() {
        val snap = buildSparseNewAthlete()
        assertEquals(DiagnosticConfidence.INSUFFICIENT, snap.overallConfidence)
        assertEquals(DiagnosticConfidence.INSUFFICIENT, snap.volumeTolerance.confidence)
        assertEquals(RecalibrationAdvisory.INSUFFICIENT_DATA, snap.recalibrationAdvisory)
        assertEquals(AerobicEfficiencyTrend.UNAVAILABLE, snap.aerobicEfficiency.trend)
        assertTrue(snap.insights.any { it.message.contains("at least 3 runs") })
    }

    @Test
    fun consistentProgressorUnlocksHighConfidenceAndSuggestsFasterCheck() {
        val snap = buildConsistentProgressor()
        assertEquals(DiagnosticConfidence.HIGH, snap.qualityExecution.confidence)
        assertEquals(LongitudinalPaceTrend.FASTER_THAN_TARGET, snap.qualityExecution.paceTrend)
        assertFalse(snap.easyDiscipline.hiddenFatigueRisk)
        assertTrue(snap.aerobicEfficiency.hrDataAvailable)
        assertEquals(AerobicEfficiencyTrend.IMPROVING, snap.aerobicEfficiency.trend)
        assertEquals(RecalibrationAdvisory.SUGGEST_FASTER_CHECK, snap.recalibrationAdvisory)
    }

    @Test
    fun grayZoneRunnerBlocksFasterPromotionAndSuggestsAddressingEasyPacingFirst() {
        val snap = buildGrayZoneRunner()
        assertTrue(snap.easyDiscipline.hiddenFatigueRisk)
        assertTrue((snap.easyDiscipline.grayZoneCreepRatePct ?: 0.0) >= 60.0)
        assertEquals(RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST, snap.recalibrationAdvisory)
        assertNotEquals(RecalibrationAdvisory.SUGGEST_FASTER_CHECK, snap.recalibrationAdvisory)
    }

    @Test
    fun strugglingAndVolumeRecoveryRunnersAreSeparatedProperly() {
        // Case A: consistent volume, genuinely too-slow quality + fatiguing HR -> SUGGEST_SLOWER_CHECK
        val caseA = buildStrugglingFatiguingRunner()
        assertEquals(LongitudinalPaceTrend.SLOWER_THAN_TARGET, caseA.qualityExecution.paceTrend)
        assertEquals(AerobicEfficiencyTrend.FATIGUING, caseA.aerobicEfficiency.trend)
        assertEquals(RecalibrationAdvisory.SUGGEST_SLOWER_CHECK, caseA.recalibrationAdvisory)

        // Case B: 3+ missed sessions / sharp volume drop -> VOLUME_RECOVERY_NEEDED
        val sessionsB = (0 until 10).map { i -> easySession("miss-$i", asOf.minusDays((9 - i) * 2L), 8.0) }
        val actsB = listOf(runActivity("miss-a0", asOf.minusDays(18), 8.0, 48.0, 360.0, 140.0))
        val matchesB = mapOf("miss-a0" to linkedMatch("miss-a0", "miss-0"))
        val analysesB = mapOf("miss-0" to easyAnalysis("miss-0", "miss-a0", ExecutionVerdict.ON_TARGET, 360.0))
        val caseB = Engine15AthleteDiagnosticsEngine.evaluate(basePlan(sessionsB), actsB, matchesB, analysesB, null, asOf, zone)
        assertTrue(caseB.volumeTolerance.consecutiveMissedSessions >= 3)
        assertEquals(RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED, caseB.recalibrationAdvisory)
    }

    @Test
    fun volumeOnlySubstitutionsNeverCountAsVerifiedQualityHits() {
        val sessions = (0 until 3).map { w -> qualitySession("voq-$w", asOf.minusDays(20L - w * 7L), 10.0) }
        val acts = (0 until 3).map { w -> runActivity("voa-$w", asOf.minusDays(20L - w * 7L), 10.0, 60.0, 360.0, 140.0) }
        val matches = (0 until 3).associate { w -> "voa-$w" to linkedMatch("voa-$w", "voq-$w") }
        val analyses = (0 until 3).associate { w ->
            "voq-$w" to qualityAnalysis("voq-$w", "voa-$w", ExecutionVerdict.VOLUME_ONLY_MATCH, 355.0, verified = false)
        }
        val snap = Engine15AthleteDiagnosticsEngine.evaluate(basePlan(sessions), acts, matches, analyses, null, asOf, zone)

        assertEquals(3, snap.qualityExecution.evaluatedQualityCount)
        assertEquals(3, snap.qualityExecution.volumeOnlySubstitutionCount)
        assertEquals(0, snap.qualityExecution.verifiedHitCount)
        assertEquals(0.0, snap.qualityExecution.verifiedHitRatePct!!, 0.01)
    }

    @Test
    fun strictHrGateAndSummaryOnlyConfidenceDowngrade() {
        val sessions = (0 until 4).map { w -> qualitySession("sum-q-$w", asOf.minusDays(21L - w * 7L), 10.0) }
        val acts = (0 until 4).map { w -> runActivity("sum-a-$w", asOf.minusDays(21L - w * 7L), 10.0, 45.0, 270.0, 168.0) }
        val matches = (0 until 4).associate { w -> "sum-a-$w" to linkedMatch("sum-a-$w", "sum-q-$w") }
        val analyses = (0 until 4).associate { w ->
            "sum-q-$w" to qualityAnalysis("sum-q-$w", "sum-a-$w", ExecutionVerdict.ON_TARGET, 256.0, verified = false, tier = ExecutionEvidenceTier.SUMMARY_ONLY)
        }
        val snap = Engine15AthleteDiagnosticsEngine.evaluate(basePlan(sessions), acts, matches, analyses, null, asOf, zone)

        // Missing HR profile -> strictly null metrics
        assertFalse(snap.aerobicEfficiency.hrDataAvailable)
        assertEquals(AerobicEfficiencyTrend.UNAVAILABLE, snap.aerobicEfficiency.trend)
        assertNull(snap.aerobicEfficiency.earlyWindowEf)
        assertNull(snap.aerobicEfficiency.recentWindowEf)
        assertNull(snap.aerobicEfficiency.efChangePct)
        assertNull(snap.aerobicEfficiency.medianDecouplingPct)

        // SUMMARY_ONLY quality runs cannot unlock HIGH/MODERATE confidence or pace recalibration
        assertEquals(DiagnosticConfidence.LOW, snap.qualityExecution.confidence)
        assertTrue(snap.overallConfidence <= DiagnosticConfidence.LOW)
        assertNotEquals(RecalibrationAdvisory.SUGGEST_FASTER_CHECK, snap.recalibrationAdvisory)
    }

    @Test
    fun pendingConfirmationRunsCountTowardPhysicalLoadWithoutInflatingQuality() {
        val s1 = qualitySession("pend-q1", asOf.minusDays(5), 10.0)
        val a1 = runActivity("pend-a1", asOf.minusDays(5), 18.0, 100.0, 333.0, 150.0)
        val pendingMatch = ActivityMatchRecord(
            activityId = "pend-a1",
            plannedSessionId = "pend-q1",
            matchStatus = ActivityMatchStatus.NEEDS_CONFIRMATION,
            confidenceScore = 0.72,
            dateOffsetDays = 0,
            reasonCodes = listOf("MEDIUM_CONFIDENCE_CONFIRMATION")
        )
        // Even if a stale analysis exists, unconfirmed match must NOT enter qualityExecution.
        val staleAnalysis = qualityAnalysis("pend-q1", "pend-a1", ExecutionVerdict.ON_TARGET, 258.0, verified = true)

        val snap = Engine15AthleteDiagnosticsEngine.evaluate(
            basePlan(listOf(s1)),
            listOf(a1),
            mapOf("pend-a1" to pendingMatch),
            mapOf("pend-q1" to staleAnalysis),
            null,
            asOf,
            zone
        )

        assertEquals(1, snap.pendingConfirmationCount)
        assertEquals(18.0, snap.volumeTolerance.completedKm, 0.01)
        assertEquals(18.0, snap.volumeTolerance.longest30dKm, 0.01)
        assertEquals(0, snap.qualityExecution.evaluatedQualityCount)
        assertEquals(0, snap.qualityExecution.verifiedHitCount)
    }

    @Test
    fun diagnosticSnapshotJsonRoundTripPreservesAllFieldsAndRejectsBadOwner() {
        val original = buildConsistentProgressor()
        val encoded = original.encode(owner = "diag-owner")
        val decoded = AthleteDiagnosticSnapshot.decode(encoded, expectedOwner = "diag-owner")
        assertEquals(original, decoded)

        var rejected = false
        try {
            AthleteDiagnosticSnapshot.decode(encoded, expectedOwner = "wrong-owner")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun printCoachingReviewOutputsForFourScenarios() {
        val scenarios = listOf(
            "1_SPARSE_NEW_ATHLETE" to buildSparseNewAthlete(),
            "2_CONSISTENT_PROGRESSOR" to buildConsistentProgressor(),
            "3_GRAY_ZONE_RUNNER" to buildGrayZoneRunner(),
            "4_STRUGGLING_FATIGUING_RUNNER" to buildStrugglingFatiguingRunner()
        )
        println("=== STEP 05 ATHLETE DIAGNOSTIC SCENARIOS ===")
        for ((name, d) in scenarios) {
            println(
                "$name | overall=${d.overallConfidence} | advisory=${d.recalibrationAdvisory} | " +
                    "vol=${d.volumeTolerance.completedKm}/${d.volumeTolerance.plannedKm}km (${d.volumeTolerance.exposureStatus}) | " +
                    "qual=${d.qualityExecution.verifiedHitCount}/${d.qualityExecution.evaluatedQualityCount} (${d.qualityExecution.paceTrend}, conf=${d.qualityExecution.confidence}) | " +
                    "easyTooFast=${d.easyDiscipline.tooFastCount}/${d.easyDiscipline.evaluatedEasyCount} (risk=${d.easyDiscipline.hiddenFatigueRisk}) | " +
                    "ef=${d.aerobicEfficiency.trend} (${d.aerobicEfficiency.efChangePct}%)"
            )
            d.insights.forEach { insight ->
                println("  - [${insight.severity}] ${insight.code}: ${insight.message}")
            }
        }
    }
}

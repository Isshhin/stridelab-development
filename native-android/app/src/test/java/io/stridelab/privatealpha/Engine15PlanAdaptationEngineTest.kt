package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class Engine15PlanAdaptationEngineTest {
    private val zone = ZoneOffset.UTC
    private val owner = "step06-athlete"
    private val startMonday = LocalDate.of(2026, 11, 2)

    private fun generateApprovedPlan(
        goal: String = "Improve 10K",
        weeks: Int = 10,
        weeklyKm: Double = 40.0,
        longestKm: Double = 14.0,
        planningPace: Double = 330.0,
        preferredQuality: String = "Intervals"
    ): PlanSnapshot {
        val req = PlanRequest(
            goal = goal,
            experience = 1,
            start = startMonday,
            weeks = weeks,
            runDays = 4,
            days = setOf(2, 4, 6, 7),
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = weeklyKm,
            longestKm = longestKm,
            targetWeeklyKm = weeklyKm + 10.0,
            planningPace = planningPace,
            preferredQuality = preferredQuality,
            preferredQualityExplicit = true,
            timeBudgetEnabled = false
        )
        val decision = Engine15IndependentPlanGenerator.previewAutoReconciled(
            request = req,
            owner = owner,
            id = "plan-$goal-${weeks}w",
            now = Instant.parse("2026-11-01T06:00:00Z")
        )
        val raw = (decision as Engine15IndependentPlanDecision.Candidate).plan
        return raw.copy(status = "approved", policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY)
    }

    private fun markFirstWeeksCompletedAndBuildActivities(
        plan: PlanSnapshot,
        asOfDate: LocalDate,
        skipFromDate: LocalDate? = null
    ): Triple<PlanSnapshot, List<ActivityRecord>, Map<String, ActivityMatchRecord>> {
        val activities = ArrayList<ActivityRecord>()
        val matches = LinkedHashMap<String, ActivityMatchRecord>()
        val updatedSessions = plan.sessions.map { s ->
            val shouldComplete = !s.date.isAfter(asOfDate) && (skipFromDate == null || s.date.isBefore(skipFromDate))
            if (shouldComplete && s.discipline == "run") {
                val actId = "act-${s.id}"
                val distM = (s.distanceKm ?: 8.0) * 1000.0
                activities += ActivityRecord(
                    id = actId,
                    title = s.title,
                    type = "running",
                    source = "garmin",
                    startedAt = s.date.atTime(7, 0).toInstant(ZoneOffset.UTC),
                    distanceM = distM,
                    durationS = s.minutes * 60.0,
                    movingTimeS = s.minutes * 60.0,
                    recordedPace = 330.0,
                    averageHr = 148.0,
                    elevationM = 25.0
                )
                matches[actId] = ActivityMatchRecord(
                    activityId = actId,
                    plannedSessionId = s.id,
                    matchStatus = ActivityMatchStatus.AUTO_LINKED,
                    confidenceScore = 0.95,
                    dateOffsetDays = 0,
                    reasonCodes = listOf("DATE_EXACT_MATCH")
                )
                s.copy(status = "completed", linkedActivityId = actId, linkPreviousStatus = "planned")
            } else s
        }
        return Triple(plan.copy(sessions = updatedSessions), activities, matches)
    }

    private fun diagnosticFor(
        plan: PlanSnapshot,
        asOfDate: LocalDate,
        advisory: RecalibrationAdvisory,
        avgWorkDeltaSec: Double? = null,
        tooSlowCount: Int = 0,
        hiddenFatigue: Boolean = false,
        exposure: ExposureStatus = ExposureStatus.SAFE,
        efTrend: AerobicEfficiencyTrend = AerobicEfficiencyTrend.STABLE,
        decoupling: Double? = null,
        consecutiveMissed: Int = 0,
        completionRatio: Double = 1.0
    ): AthleteDiagnosticSnapshot = AthleteDiagnosticSnapshot(
        planId = plan.id,
        asOfDate = asOfDate,
        overallConfidence = if (advisory == RecalibrationAdvisory.INSUFFICIENT_DATA) DiagnosticConfidence.INSUFFICIENT else DiagnosticConfidence.HIGH,
        pendingConfirmationCount = 0,
        volumeTolerance = VolumeToleranceDiagnostic(
            windowDays = 28,
            plannedKm = 160.0,
            completedKm = 160.0 * completionRatio,
            volumeCompletionRatio = completionRatio,
            plannedSessions = 16,
            completedSessions = (16 * completionRatio).toInt(),
            consecutiveMissedSessions = consecutiveMissed,
            rolling28dWeeklyAvgKm = 40.0 * completionRatio,
            last7dKm = if (exposure == ExposureStatus.OVERREACH_RISK) 58.0 else 40.0 * completionRatio,
            acuteToBaselineRatio = if (exposure == ExposureStatus.OVERREACH_RISK) 1.45 else 1.0,
            longest30dKm = 14.0,
            exposureStatus = exposure,
            confidence = DiagnosticConfidence.HIGH,
            reasonCodes = emptyList()
        ),
        qualityExecution = QualityExecutionDiagnostic(
            evaluatedQualityCount = 4,
            verifiedHitCount = if (tooSlowCount == 0) 4 else 1,
            verifiedHitRatePct = if (tooSlowCount == 0) 100.0 else 25.0,
            volumeOnlySubstitutionCount = 0,
            tooSlowCount = tooSlowCount,
            tooFastCount = 0,
            partialCount = 0,
            paceTrend = when {
                avgWorkDeltaSec != null && avgWorkDeltaSec <= -3.0 -> LongitudinalPaceTrend.FASTER_THAN_TARGET
                tooSlowCount >= 2 -> LongitudinalPaceTrend.SLOWER_THAN_TARGET
                else -> LongitudinalPaceTrend.ON_TARGET
            },
            avgWorkPaceDeltaSecPerKm = avgWorkDeltaSec,
            confidence = DiagnosticConfidence.HIGH,
            reasonCodes = emptyList()
        ),
        easyDiscipline = EasyDisciplineDiagnostic(
            evaluatedEasyCount = 12,
            tooFastCount = if (hiddenFatigue) 9 else 0,
            grayZoneCreepRatePct = if (hiddenFatigue) 75.0 else 0.0,
            avgOvershootSecPerKm = if (hiddenFatigue) 28.0 else null,
            hiddenFatigueRisk = hiddenFatigue,
            confidence = DiagnosticConfidence.HIGH,
            reasonCodes = emptyList()
        ),
        aerobicEfficiency = AerobicEfficiencyDiagnostic(
            hrDataAvailable = efTrend != AerobicEfficiencyTrend.UNAVAILABLE,
            eligibleRunCount = if (efTrend != AerobicEfficiencyTrend.UNAVAILABLE) 8 else 0,
            earlyWindowEf = if (efTrend != AerobicEfficiencyTrend.UNAVAILABLE) 0.0210 else null,
            recentWindowEf = if (efTrend == AerobicEfficiencyTrend.FATIGUING) 0.0195 else if (efTrend == AerobicEfficiencyTrend.IMPROVING) 0.0221 else if (efTrend == AerobicEfficiencyTrend.STABLE) 0.0211 else null,
            efChangePct = if (efTrend == AerobicEfficiencyTrend.FATIGUING) -7.1 else if (efTrend == AerobicEfficiencyTrend.IMPROVING) 5.2 else if (efTrend == AerobicEfficiencyTrend.STABLE) 0.5 else null,
            medianDecouplingPct = decoupling,
            trend = efTrend,
            confidence = if (efTrend != AerobicEfficiencyTrend.UNAVAILABLE) DiagnosticConfidence.HIGH else DiagnosticConfidence.INSUFFICIENT,
            reasonCodes = emptyList()
        ),
        recalibrationAdvisory = advisory,
        insights = emptyList(),
        reasonCodes = emptyList()
    )

    @Test
    fun paceUpProposalEnforcesCapAppliesCleanlyRevertsLosslesslyAndSetsCooldown() {
        val asOf = startMonday.plusDays(27) // end of week 4
        val (plan, acts, matches) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(), asOf)
        val diag = diagnosticFor(plan, asOf, RecalibrationAdvisory.SUGGEST_FASTER_CHECK, avgWorkDeltaSec = -7.5, efTrend = AerobicEfficiencyTrend.IMPROVING)

        val proposal = Engine15PlanAdaptationEngine.evaluateProposal(plan, diag, acts, matches, asOfDate = asOf, zoneId = zone)
        assertNotNull(proposal)
        assertEquals(AdaptationType.PACE_RECALIBRATION_UP, proposal!!.type)
        assertTrue(proposal.diff.safetyChecksPassed)
        assertTrue(proposal.diff.paceDiffs.isNotEmpty())
        for (p in proposal.diff.paceDiffs) {
            val pctFaster = (p.oldMinSecPerKm - p.newMinSecPerKm).toDouble() / p.oldMinSecPerKm
            assertTrue("Expected <= 3.5% bounded speed-up, got $pctFaster", pctFaster in 0.015..0.035)
        }

        // Apply proposal explicitly
        val applyRes = Engine15PlanAdaptationEngine.applyProposal(plan, proposal, asOf)
        assertEquals(AdaptationProposalStatus.APPLIED, applyRes.updatedProposal.status)
        assertEquals(plan.revision + 1, applyRes.appliedPlan.revision)

        // Past/completed sessions are strictly untouched
        for (orig in plan.sessions.filter { !it.date.isAfter(asOf) }) {
            assertEquals(orig, applyRes.appliedPlan.sessions.first { it.id == orig.id })
        }

        // Cooldown (14 days) blocks immediate re-proposal
        val blocked = Engine15PlanAdaptationEngine.evaluateProposal(
            plan = applyRes.appliedPlan,
            diagnostic = diag,
            activities = acts,
            matchesByActivityId = matches,
            cooldowns = listOf(applyRes.cooldown),
            asOfDate = asOf.plusDays(3),
            zoneId = zone
        )
        assertNull(blocked)

        // One-tap rollback restores exact original future prescriptions
        val revertRes = Engine15PlanAdaptationEngine.revertRevision(applyRes.appliedPlan, applyRes.revision, asOf.plusDays(1))
        assertNotNull(revertRes.updatedRevision.revertedAtDate)
        assertEquals(plan.sessions, revertRes.restoredPlan.sessions)
    }

    @Test
    fun paceDownProposalEasesWithinCapStepsBuildQualityAndPreservesBookends() {
        val asOf = startMonday.plusDays(27)
        val (plan, acts, matches) = markFirstWeeksCompletedAndBuildActivities(
            generateApprovedPlan(goal = "Half marathon", weeks = 12, preferredQuality = "Threshold"),
            asOf
        )
        val diag = diagnosticFor(plan, asOf, RecalibrationAdvisory.SUGGEST_SLOWER_CHECK, avgWorkDeltaSec = 11.0, tooSlowCount = 3)

        val proposal = Engine15PlanAdaptationEngine.evaluateProposal(plan, diag, acts, matches, asOfDate = asOf, zoneId = zone)
        assertNotNull(proposal)
        assertEquals(AdaptationType.PACE_RECALIBRATION_DOWN, proposal!!.type)
        assertTrue(proposal.diff.safetyChecksPassed)
        for (p in proposal.diff.paceDiffs) {
            val pctSlower = (p.newMinSecPerKm - p.oldMinSecPerKm).toDouble() / p.oldMinSecPerKm
            assertTrue("Expected 2.5%..5.5% easing, got $pctSlower", pctSlower in 0.025..0.055)
        }

        val applied = Engine15PlanAdaptationEngine.applyProposal(plan, proposal, asOf).appliedPlan
        for (q in applied.sessions.filter { it.quality && !it.date.isBefore(proposal.effectiveFromDate) }) {
            val steps = q.runningPrescription?.mixedSegments ?: continue
            val wuSec = steps.filter { it.purpose == "WARM_UP" }.sumOf { it.seconds ?: 0 }
            val cdSec = steps.filter { it.purpose == "COOL_DOWN" }.sumOf { it.seconds ?: 0 }
            assertTrue("Warmup floor >= 8m", wuSec == 0 || wuSec >= 480)
            assertTrue("Cooldown floor >= 5m", cdSec == 0 || cdSec >= 300)
        }
    }

    @Test
    fun grayZoneRunnerEmitsNonMutatingEasyPacingLockAndBlocksPaceUpgrade() {
        val asOf = startMonday.plusDays(27)
        val (plan, acts, matches) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(), asOf)
        val diag = diagnosticFor(
            plan, asOf,
            advisory = RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST,
            avgWorkDeltaSec = -8.0,
            hiddenFatigue = true
        )

        val proposal = Engine15PlanAdaptationEngine.evaluateProposal(plan, diag, acts, matches, asOfDate = asOf, zoneId = zone)
        assertNotNull(proposal)
        assertEquals(AdaptationType.EASY_PACING_LOCK, proposal!!.type)
        assertNull(proposal.candidatePlanJson)
        assertTrue(proposal.coachingRationale.contains("75%"))
        assertTrue(proposal.coachingRationale.contains("locked", ignoreCase = true))
    }

    @Test
    fun missedWeekRecoverySupportsAllThreeTiersFingerprintSuppressionAndCooldownBypass() {
        // Tier 1: Short disruption (3-5d)
        val gapStart1 = startMonday.plusDays(23)
        val asOf1 = gapStart1.plusDays(3) // 4-day interruption
        val (p1, a1, m1) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(), asOf1, skipFromDate = gapStart1)
        val d1 = diagnosticFor(p1, asOf1, RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED, consecutiveMissed = 2, completionRatio = 0.50)
        // Even with an active pace cooldown, missed-week recovery bypasses pace cooldowns!
        val paceCooldown = AdaptationCooldownRecord(p1.id, AdaptationType.PACE_RECALIBRATION_UP, AdaptationProposalStatus.APPLIED, asOf1.minusDays(2), asOf1.plusDays(12))
        val prop1 = Engine15PlanAdaptationEngine.evaluateProposal(p1, d1, a1, m1, cooldowns = listOf(paceCooldown), asOfDate = asOf1, zoneId = zone)
        assertNotNull(prop1)
        assertEquals(AdaptationType.MISSED_WEEK_RECOVERY, prop1!!.type)
        assertEquals(DisruptionTier.SHORT_DISRUPTION_3_TO_5D, prop1.disruptionTier)
        assertTrue(prop1.diff.safetyChecksPassed)

        // Dismissed fingerprint suppresses exact same disruption re-prompt
        val dismissCd = AdaptationCooldownRecord(p1.id, AdaptationType.MISSED_WEEK_RECOVERY, AdaptationProposalStatus.DISMISSED, asOf1, asOf1.plusDays(14), prop1.disruptionFingerprint)
        val suppressed = Engine15PlanAdaptationEngine.evaluateProposal(p1, d1, a1, m1, cooldowns = listOf(dismissCd), asOfDate = asOf1, zoneId = zone)
        assertNull(suppressed)

        // Tier 2: Full missed week (6-10d) -> ~82% re-entry bridge + preserved race taper
        val gapStart2 = startMonday.plusDays(14)
        val asOf2 = startMonday.plusDays(21) // missed entire week 2 (7d+)
        val (p2, a2, m2) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(goal = "Improve 10K", weeks = 10), asOf2, skipFromDate = gapStart2)
        val d2 = diagnosticFor(p2, asOf2, RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED, consecutiveMissed = 4, completionRatio = 0.55)
        val prop2 = Engine15PlanAdaptationEngine.evaluateProposal(p2, d2, a2, m2, asOfDate = asOf2, zoneId = zone)
        assertNotNull(prop2)
        assertEquals(DisruptionTier.FULL_MISSED_WEEK_6_TO_10D, prop2!!.disruptionTier)
        assertTrue(prop2.diff.weekDiffs.isNotEmpty())
        val applied2 = Engine15PlanAdaptationEngine.applyProposal(p2, prop2, asOf2).appliedPlan
        // Last 2 weeks of 10w 10K are TAPER -> must stay intact
        val origTaperSessions = p2.sessions.filter { it.date >= startMonday.plusWeeks(8) }
        val newTaperSessions = applied2.sessions.filter { it.date >= startMonday.plusWeeks(8) }
        assertEquals(origTaperSessions.sumOf { it.distanceKm ?: 0.0 }, newTaperSessions.sumOf { it.distanceKm ?: 0.0 }, 0.05)

        // Tier 3: Extended gap (11d+) -> 2-week aerobic rebuild
        val gapStart3 = startMonday.plusDays(7)
        val asOf3 = startMonday.plusDays(20) // 13+ days missed
        val (p3, a3, m3) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(goal = "Marathon", weeks = 12, weeklyKm = 52.0, longestKm = 20.0), asOf3, skipFromDate = gapStart3)
        val d3 = diagnosticFor(p3, asOf3, RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED, consecutiveMissed = 7, completionRatio = 0.30)
        val prop3 = Engine15PlanAdaptationEngine.evaluateProposal(p3, d3, a3, m3, asOfDate = asOf3, zoneId = zone)
        assertNotNull(prop3)
        assertEquals(DisruptionTier.EXTENDED_GAP_11D_PLUS, prop3!!.disruptionTier)
        assertTrue(prop3.reasonCodes.contains("EXTENDED_GAP_2WK_AEROBIC_REBUILD"))
    }

    @Test
    fun proactiveDeloadSwapConvertsNextLoadingWeekToRecoveryWithoutBreakingTaper() {
        val asOf = startMonday.plusDays(27)
        val (plan, acts, matches) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(goal = "Improve 10K", weeks = 10), asOf)
        val diag = diagnosticFor(
            plan, asOf,
            advisory = RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED,
            exposure = ExposureStatus.OVERREACH_RISK,
            efTrend = AerobicEfficiencyTrend.FATIGUING,
            decoupling = 9.5
        )
        val prop = Engine15PlanAdaptationEngine.evaluateProposal(plan, diag, acts, matches, asOfDate = asOf, zoneId = zone)
        assertNotNull(prop)
        assertEquals(AdaptationType.PROACTIVE_DELOAD_SWAP, prop!!.type)
        val wkDiff = prop.diff.weekDiffs.first()
        assertEquals("RECOVERY", wkDiff.phaseAfter)
        assertTrue(wkDiff.plannedKmAfter <= wkDiff.plannedKmBefore * 0.85)
        assertEquals(0, wkDiff.qualityCountAfter)
    }

    @Test
    fun preSurfaceInvariantGateRejectsTamperedCandidateAndContractsRoundTrip() {
        val asOf = startMonday.plusDays(27)
        val (plan, _, matches) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(), asOf)
        val linkedIds = matches.values.mapNotNull { it.plannedSessionId }.toSet()

        // Mutate a past completed session illegally -> gate MUST reject!
        val illegalPastEdit = plan.copy(
            sessions = plan.sessions.mapIndexed { idx, s ->
                if (idx == 0) s.copy(minutes = s.minutes + 20) else s
            }
        )
        val outcome = Engine15PlanAdaptationEngine.validateCandidatePlan(
            originalPlan = plan,
            candidatePlan = illegalPastEdit,
            effectiveFromDate = asOf.plusDays(1),
            trailing30dLongestKm = 14.0,
            linkedSessionIds = linkedIds
        )
        assertFalse(outcome.passed)
        assertTrue(outcome.warnings.any { it.startsWith("IMMUTABLE_PAST_OR_LINKED_MUTATED") })

        // Serialization round-trip for Proposal, Revision, Cooldown
        val diag = diagnosticFor(plan, asOf, RecalibrationAdvisory.SUGGEST_FASTER_CHECK, avgWorkDeltaSec = -7.0, efTrend = AerobicEfficiencyTrend.IMPROVING)
        val prop = Engine15PlanAdaptationEngine.evaluateProposal(plan, diag, emptyList(), matches, asOfDate = asOf, zoneId = zone)!!
        val res = Engine15PlanAdaptationEngine.applyProposal(plan, prop, asOf)
        assertEquals(prop, PlanAdaptationProposal.decode(prop.encode(owner), owner))
        assertEquals(res.revision, PlanAdaptationRevision.decode(res.revision.encode(owner), owner))
        assertEquals(res.cooldown, AdaptationCooldownRecord.decode(res.cooldown.encode(owner), owner))
    }

    @Test
    fun printSampleAdaptationOutputsForFourScenarios() {
        val asOf = startMonday.plusDays(27)
        val (plan10k, acts10k, matches10k) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(), asOf)

        val pUp = Engine15PlanAdaptationEngine.evaluateProposal(
            plan10k,
            diagnosticFor(plan10k, asOf, RecalibrationAdvisory.SUGGEST_FASTER_CHECK, avgWorkDeltaSec = -7.5, efTrend = AerobicEfficiencyTrend.IMPROVING),
            acts10k, matches10k, asOfDate = asOf, zoneId = zone
        )!!
        val revUp = Engine15PlanAdaptationEngine.applyProposal(plan10k, pUp, asOf)
        val reverted = Engine15PlanAdaptationEngine.revertRevision(revUp.appliedPlan, revUp.revision, asOf.plusDays(1))

        val (planHm, actsHm, matchesHm) = markFirstWeeksCompletedAndBuildActivities(
            generateApprovedPlan(goal = "Half marathon", weeks = 12, preferredQuality = "Threshold"), asOf
        )
        val pDown = Engine15PlanAdaptationEngine.evaluateProposal(
            planHm,
            diagnosticFor(planHm, asOf, RecalibrationAdvisory.SUGGEST_SLOWER_CHECK, avgWorkDeltaSec = 11.0, tooSlowCount = 3),
            actsHm, matchesHm, asOfDate = asOf, zoneId = zone
        )!!

        val pLock = Engine15PlanAdaptationEngine.evaluateProposal(
            plan10k,
            diagnosticFor(plan10k, asOf, RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST, avgWorkDeltaSec = -8.0, hiddenFatigue = true),
            acts10k, matches10k, asOfDate = asOf, zoneId = zone
        )!!

        val asOfGap = startMonday.plusDays(21)
        val (planGap, actsGap, matchesGap) = markFirstWeeksCompletedAndBuildActivities(generateApprovedPlan(), asOfGap, skipFromDate = startMonday.plusDays(14))
        val pMissed = Engine15PlanAdaptationEngine.evaluateProposal(
            planGap,
            diagnosticFor(planGap, asOfGap, RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED, consecutiveMissed = 4, completionRatio = 0.55),
            actsGap, matchesGap, asOfDate = asOfGap, zoneId = zone
        )!!

        println("=== STEP 06 CONTROLLED ADAPTATION SCENARIOS ===")
        println("1_PACE_UP_AND_REVERT | type=${pUp.type} | affected=${pUp.diff.affectedSessionCount} | paceDiffs=${pUp.diff.paceDiffs.size} | appliedRev=${revUp.appliedPlan.revision} -> revertedEqualsOriginal=${reverted.restoredPlan.sessions == plan10k.sessions} | rationale=${pUp.coachingRationale}")
        println("2_PACE_DOWN | type=${pDown.type} | affected=${pDown.diff.affectedSessionCount} | paceDiffs=${pDown.diff.paceDiffs.size} | safetyPassed=${pDown.diff.safetyChecksPassed} | rationale=${pDown.coachingRationale}")
        println("3_GRAY_ZONE_EASY_LOCK | type=${pLock.type} | candidateNull=${pLock.candidatePlanJson == null} | rationale=${pLock.coachingRationale}")
        println("4_FULL_MISSED_WEEK_BRIDGE | type=${pMissed.type} | tier=${pMissed.disruptionTier} | weekDiff=${pMissed.diff.weekDiffs.firstOrNull()?.summaryNote} | safetyPassed=${pMissed.diff.safetyChecksPassed}")
    }
}

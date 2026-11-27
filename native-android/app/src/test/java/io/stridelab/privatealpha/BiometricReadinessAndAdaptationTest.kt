package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BiometricReadinessAndAdaptationTest {

    private fun buildRunPlan(start: LocalDate): PlanSnapshot {
        val request = PlanRequest(
            goal = "Half Marathon",
            experience = 2,
            weeklyKm = 36.0,
            longestKm = 14.0,
            runDays = 4,
            days = setOf(1, 3, 5, 7),
            longDay = 7,
            minutes = 75,
            start = start,
            weeks = 6,
            planningPace = 315.0
        )
        return Engine15IndependentPlanGenerator.generateWithHistory(
            request,
            "athlete-1",
            emptyList(),
            "bio-plan-1",
            start.atStartOfDay().toInstant(ZoneOffset.UTC)
        )
    }

    private fun wellnessSeries(
        endDate: LocalDate,
        days: Int,
        rhrBpm: Double = 50.0,
        hrvMs: Double = 65.0,
        sleepHours: Double = 7.6
    ): List<WellnessRecord> {
        val out = mutableListOf<WellnessRecord>()
        for (i in (days - 1) downTo 0) {
            val d = endDate.minusDays(i.toLong())
            val ts = d.atTime(6, 30).toInstant(ZoneOffset.UTC)
            out += WellnessRecord("rhr-$d", "resting_hr", rhrBpm, "bpm", ts, "garmin")
            out += WellnessRecord("hrv-$d", "hrv", hrvMs, "ms", ts, "garmin")
            out += WellnessRecord("slp-$d", "sleep_duration", sleepHours, "hours", ts, "garmin")
        }
        return out
    }

    @Test
    fun evaluator_abstainsHonestBaselineWhenLessThan14DaysHrv() {
        val today = LocalDate.of(2026, 11, 27)
        val records = wellnessSeries(today, days = 10, rhrBpm = 50.0, hrvMs = 60.0, sleepHours = 7.5)
        val snapshot = BiometricReadinessEvaluator.evaluate(records, today, ZoneOffset.UTC)

        assertTrue(snapshot.hrv.evaluation is HrvEvaluationResult.InsufficientOrInvalid)
        assertEquals(BiometricMetricStatus.BUILDING_BASELINE, snapshot.hrv.status)
        assertFalse(snapshot.hrv.suppressedFlag)
        assertFalse(snapshot.sustainedAutonomicStrain)
    }

    @Test
    fun evaluator_singlePoorSleepNightDoesNotTriggerSustainedStrain() {
        val today = LocalDate.of(2026, 11, 27)
        val baseline = wellnessSeries(today.minusDays(1), days = 20, rhrBpm = 50.0, hrvMs = 65.0, sleepHours = 7.6)
        val lastNight = listOf(
            WellnessRecord("rhr-$today", "resting_hr", 51.0, "bpm", today.atTime(6, 30).toInstant(ZoneOffset.UTC), "garmin"),
            WellnessRecord("hrv-$today", "hrv", 64.0, "ms", today.atTime(6, 30).toInstant(ZoneOffset.UTC), "garmin"),
            WellnessRecord("slp-$today", "sleep_duration", 4.8, "hours", today.atTime(6, 30).toInstant(ZoneOffset.UTC), "garmin")
        )
        val snapshot = BiometricReadinessEvaluator.evaluate(baseline + lastNight, today, ZoneOffset.UTC)
        assertTrue(snapshot.sleep.severeAcuteLossFlag)
        assertFalse(snapshot.sustainedAutonomicStrain)
    }

    @Test
    fun convergentStrain_triggersProactiveDeloadWithBiometricRationale() {
        val today = LocalDate.of(2026, 11, 27)
        val start = today
        val plan = buildRunPlan(start).copy(status = "approved")
        val diagnostic = Engine15AthleteDiagnosticsEngine.evaluate(
            plan = plan,
            activities = emptyList(),
            matchesByActivityId = emptyMap(),
            analysesBySessionId = emptyMap(),
            athleteHrZones = null,
            asOfDate = today
        )

        // 21 days normal baseline followed by 7 days suppressed HRV + elevated RHR
        val normal = wellnessSeries(today.minusDays(7), days = 21, rhrBpm = 49.0, hrvMs = 70.0, sleepHours = 7.7)
        val strained = wellnessSeries(today, days = 7, rhrBpm = 57.0, hrvMs = 46.0, sleepHours = 6.0)
        val biometrics = BiometricReadinessEvaluator.evaluate(normal + strained, today, ZoneOffset.UTC)

        assertTrue(biometrics.hrv.suppressedFlag)
        assertTrue(biometrics.rhr.elevatedFlag)
        assertTrue(biometrics.sustainedAutonomicStrain)

        val proposal = Engine15PlanAdaptationEngine.evaluateProposal(
            plan = plan,
            diagnostic = diagnostic,
            activities = emptyList(),
            matchesByActivityId = emptyMap(),
            asOfDate = today,
            zoneId = ZoneOffset.UTC,
            biometrics = biometrics
        )
        assertNotNull(proposal)
        assertEquals(AdaptationType.PROACTIVE_DELOAD_SWAP, proposal!!.type)
        assertTrue("BIOMETRIC_STRAIN_DELOAD" in proposal.reasonCodes)
        assertFalse(proposal.coachingRationale.contains("—"))

        // Also verify Engine 2.0 fatigue filter remains bounded and respects active health hold
        val heldPosterior = Engine20FatigueAndDoseResponseEngine.updateBayesianFatigueFilter(
            input = FatigueFilterInput(
                hrvEvaluation = biometrics.hrv.evaluation,
                rhrElevated = biometrics.rhr.elevatedFlag,
                sleepDebtAdverse = biometrics.sleep.cumulativeDebtFlag,
                activeHealthHoldPresent = true
            )
        )
        assertTrue(heldPosterior.overrideBlockedByHealthHold)
    }
}

package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class Engine15RunningMathAndProgressionTest {

    @Test fun mod03Mod04Mod05Mod06AndPol021CalibrationsMatchResearchSpec() {
        // MOD-04 Vickers & Vertosick Model 1 worked example: HM 1:39:06 @ 40 mi/wk -> ~13,082.6 s (3:38:03)
        val hmMeters = 21_097.5
        val hmSec = 5_946.0
        val fortyMiMeters = 40.0 * 1609.344
        val vv1 = Engine15PaceZoneEstimator.estimateMarathonVickersModel1(hmMeters, hmSec, fortyMiMeters)
        assertTrue(vv1 is M2AModelResult.Computed)
        val out1 = (vv1 as M2AModelResult.Computed).value
        assertEquals(13_082.6, out1.predictedMarathonDuration.value, 15.0)

        // EXP-01 EQ-DEF-01 boundary failure: slow HM 2:45:00 @ 100 mi/wk abstains
        val vv1Abstain = Engine15PaceZoneEstimator.estimateMarathonVickersModel1(
            distanceMeters = hmMeters,
            durationSeconds = 9_900.0,
            weeklyMeters = 100.0 * 1609.344
        )
        assertTrue(vv1Abstain is M2AModelResult.Abstained)

        // MOD-05 Model 2 worked example: 10K 50:00 + HM 1:50:00 @ 40 mi/wk -> ~14,579 s
        val vv2 = Engine15PaceZoneEstimator.estimateMarathonVickersModel2(
            shorterDistanceMeters = 10_000.0,
            shorterDurationSeconds = 3_000.0,
            longerDistanceMeters = hmMeters,
            longerDurationSeconds = 6_600.0,
            weeklyMeters = fortyMiMeters
        )
        assertTrue(vv2 is M2AModelResult.Computed)
        assertEquals(14_579.0, (vv2 as M2AModelResult.Computed).value.predictedMarathonDuration.value, 30.0)

        // EXP-01 EQ-DEF-02 plausibility guard: 5K 20:00 + 10K 60:00 (k_between collapses) abstains
        val vv2Abstain = Engine15PaceZoneEstimator.estimateMarathonVickersModel2(
            shorterDistanceMeters = 5_000.0,
            shorterDurationSeconds = 1_200.0,
            longerDistanceMeters = 10_000.0,
            longerDurationSeconds = 3_600.0,
            weeklyMeters = fortyMiMeters
        )
        assertTrue(vv2Abstain is M2AModelResult.Abstained)

        // MOD-06 54-min operational threshold: 10K 45:00 (2,700 s) -> ~4:33/km (~273 s/km)
        val thrPace = Engine15PaceZoneEstimator.durationAnchoredThresholdPaceSecPerKm(10_000.0, 2_700.0)
        assertNotNull(thrPace)
        assertTrue(abs(thrPace!! - 273.2) < 2.0)

        // POL-021 advisory guidance factor and Effort-First recommendation when benchmark absent
        val reqNoBench = PlanRequest(goal = "Improve 10K", weeklyKm = 35.0, longestKm = 12.0, planningPace = 340.0)
        val zonesNoBench = Engine15PaceZoneEstimator.estimate(reqNoBench)
        assertTrue(zonesNoBench.effortFirstRecommended)
        assertEquals(Engine15RunningPolicyRegistry.POL_021_GUIDANCE_BAND_PROVISIONAL, zonesNoBench.coachingGuidanceBandFactor, 1e-6)

        val reqWithBench = reqNoBench.copy(benchmarkDistanceMeters = 10_000, benchmarkDurationSeconds = 2_700)
        val zonesBench = Engine15PaceZoneEstimator.estimate(reqWithBench)
        assertFalse(zonesBench.effortFirstRecommended)
        assertEquals(Engine15RunningPolicyRegistry.POL_021_GUIDANCE_BAND_FRESH, zonesBench.coachingGuidanceBandFactor, 1e-6)
    }

    @Test fun mod12WeeklyModesConfirmedZeroBypassAndHeadroomPrecedence() {
        val zeroBypass = Engine15RunningProgressionEngine.computeWeeklyVolumeDelta(
            mode = RunningWeeklyMode.INCREASE,
            baseWeeklyMeters = 0.0,
            runDays = 3,
            confirmedZeroHistory = true
        )
        assertTrue(zeroBypass.routedToConfirmedZeroStarter)
        assertEquals(0.0, zeroBypass.nextWeekMeters, 1e-6)

        val maintain = Engine15RunningProgressionEngine.computeWeeklyVolumeDelta(
            mode = RunningWeeklyMode.MAINTAIN,
            baseWeeklyMeters = 40_000.0,
            runDays = 4
        )
        assertEquals(0.0, maintain.deltaMeters, 1e-6)

        // Headroom < deltaMin: deltaMin (1,500 m) MUST NEVER override tighter binding ceiling (+800 m)
        val tighterCeiling = Engine15RunningProgressionEngine.computeWeeklyVolumeDelta(
            mode = RunningWeeklyMode.INCREASE,
            baseWeeklyMeters = 40_000.0,
            runDays = 4,
            bindingCeilingMeters = 40_800.0
        )
        assertTrue(tighterCeiling.deltaMeters <= 800.0 + 1e-6)
        assertEquals(40_800.0, tighterCeiling.nextWeekMeters, 1e-6)

        // Ceiling already exceeded -> transitions cleanly to REDUCE
        val negativeHeadroom = Engine15RunningProgressionEngine.computeWeeklyVolumeDelta(
            mode = RunningWeeklyMode.INCREASE,
            baseWeeklyMeters = 42_000.0,
            runDays = 4,
            bindingCeilingMeters = 40_000.0
        )
        assertEquals(RunningWeeklyMode.REDUCE, negativeHeadroom.mode)
        assertEquals(40_000.0, negativeHeadroom.nextWeekMeters, 1e-6)
    }

    @Test fun mod10ExactIso30DayWindowAndPol027MultiStressorMatrix() {
        val target = LocalDate.of(2026, 12, 5)
        // Day 31 earlier is OUTSIDE [d-30 .. d-1], Day 30 earlier IS inside
        val anchors = listOf(
            DatedRunExposureAnchor(target.minusDays(31), 22_000.0, true),
            DatedRunExposureAnchor(target.minusDays(20), 14_500.0, true)
        )
        val eval = Engine15RunningProgressionEngine.evaluateRolling30DayRatio(target, 15_800.0, anchors)
        assertEquals(14_500.0, eval.dMax30Meters!!, 1e-6)
        assertEquals(15_800.0 / 14_500.0, eval.r30Ratio!!, 1e-6)
        assertFalse(eval.exceedsGuardrailL4B)

        val spiked = Engine15RunningProgressionEngine.evaluateRolling30DayRatio(target, 16_500.0, anchors)
        assertTrue(spiked.exceedsGuardrailL4B)
        assertTrue(spiked.observationalRiskSignalL7)

        // Below 4,000 m floor -> NOT_APPLICABLE
        val novice = Engine15RunningProgressionEngine.evaluateRolling30DayRatio(
            target, 4_000.0, listOf(DatedRunExposureAnchor(target.minusDays(5), 3_000.0, true))
        )
        assertEquals(Rolling30Status.NOT_APPLICABLE_BELOW_FLOOR, novice.status)

        // POL-027 MultiStressorPolicyV1 rejects stacking 2 MAJOR_STEPs (e.g. +13.3% Vol AND LR r30 = 1.08)
        val stackedMajor = Engine15RunningProgressionEngine.evaluateMultiStressorTransition(
            RunningWeekTransitionInput(
                baseWeeklyMeters = 30_000.0,
                nextWeeklyMeters = 34_000.0, // +13.3% MAJOR
                longRunR30 = 1.08            // MAJOR
            )
        )
        assertFalse(stackedMajor.valid)
        assertEquals(2, stackedMajor.majorCount)

        // Valid staggered week: 1 MAJOR (LR 1.08) + 1 MINOR (+5% Vol)
        val staggered = Engine15RunningProgressionEngine.evaluateMultiStressorTransition(
            RunningWeekTransitionInput(
                baseWeeklyMeters = 30_000.0,
                nextWeeklyMeters = 31_500.0, // +5% MINOR
                longRunR30 = 1.08            // MAJOR
            )
        )
        assertTrue(staggered.valid)
        assertEquals(1, staggered.majorCount)
        assertEquals(1, staggered.minorCount)
    }

    @Test fun pol033GaplessBucketsAndPol034CorrectedReturnExitFixScenario11() {
        // 6 gapless buckets + illness shifts 1 tier more conservative
        assertEquals(1.00, Engine15RunningProgressionEngine.returnEntryFractionForGapDays(5), 1e-6)
        assertEquals(0.90, Engine15RunningProgressionEngine.returnEntryFractionForGapDays(10), 1e-6)
        assertEquals(0.72, Engine15RunningProgressionEngine.returnEntryFractionForGapDays(20), 1e-6)
        assertEquals(0.52, Engine15RunningProgressionEngine.returnEntryFractionForGapDays(35), 1e-6)
        assertEquals(0.40, Engine15RunningProgressionEngine.returnEntryFractionForGapDays(65), 1e-6)
        assertEquals(0.30, Engine15RunningProgressionEngine.returnEntryFractionForGapDays(90), 1e-6)
        assertEquals(0.72, Engine15RunningProgressionEngine.returnEntryFractionForGapDays(10, illnessOrInjury = true), 1e-6)

        // Scenario 11 W7 bug: 60 km/wk prior, W4-W7 rolling avg (30+44+50+56)/4 = 45 km = 75% (<85%) -> MUST remain in REBUILD
        val falseW7Exit = Engine15RunningProgressionEngine.evaluateReturnExit(
            priorSustainableWeeklyMeters = 60_000.0,
            rolling4WeekMeters = listOf(30_000.0, 44_000.0, 50_000.0, 56_000.0),
            currentWeekMeters = 56_000.0,
            consecutiveCompletedRebuildNonCutbackWeeks = 3,
            consecutiveSteadyObservedWeeks = 2,
            hasEscalatingSymptoms = false,
            evaluatedOnObservedLogsOnly = true
        )
        assertEquals(ReturnExitState.REMAIN_IN_REBUILD, falseW7Exit.state)

        // Scenario 11 W9: W6-W9 (47 + 52.5 + 56 + 57) / 4 = 53.125 km = 88.5% >= 85%, current 95% >= 90%
        val validW9Projected = Engine15RunningProgressionEngine.evaluateReturnExit(
            priorSustainableWeeklyMeters = 60_000.0,
            rolling4WeekMeters = listOf(47_000.0, 52_500.0, 56_000.0, 57_000.0),
            currentWeekMeters = 57_000.0,
            consecutiveCompletedRebuildNonCutbackWeeks = 4,
            consecutiveSteadyObservedWeeks = 2,
            hasEscalatingSymptoms = false,
            evaluatedOnObservedLogsOnly = false
        )
        assertEquals(ReturnExitState.CONDITIONAL_EXIT_PENDING_OBSERVED_VERIFICATION, validW9Projected.state)
    }
}

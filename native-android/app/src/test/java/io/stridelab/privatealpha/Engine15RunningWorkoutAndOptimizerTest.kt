package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class Engine15RunningWorkoutAndOptimizerTest {

    @Test fun mod13ExactAstAccountingMod15TidAndPol028FloorsInside40MinCap() {
        // Worked example: 2 x (10 x 30s / 30s) + 3m set rec + 12m WU + 3m prep + 5m CD = 600s work, 720s rec, 2,520s total (zero phantom final rec!)
        val vo2Short = Engine15RunningWorkoutConstructor.introCandidate(RunningQualityFamily10.VO2MAX_SHORT_INTERVALS)
        assertTrue(vo2Short.totalWorkSeconds == 480) // intro 2x8x30/30 = 8m = 480s
        val custom2x10 = Engine15RunningWorkoutConstructor.buildAst(
            family = RunningQualityFamily10.VO2MAX_SHORT_INTERVALS,
            sets = 2, reps = 10, repWorkS = 30, innerRecoverySeconds = 30, setRecS = 180,
            modality = RecoveryModality.WALK, warmUpS = 720, prepS = 180, suppS = 0, coolDownS = 360
        )
        assertEquals(600, custom2x10.totalWorkSeconds)
        assertEquals(720, custom2x10.totalRecoverySeconds)
        assertEquals(2_580.0, custom2x10.totalMaxSeconds, 1e-6)

        // Orthogonal TID conservation (CON-15, INV-14: < 1e-6 s residual, WU/CD not merged with Walk)
        val tid = custom2x10.computeTidAccount()
        assertTrue(tid.conservationResidualSeconds < 1e-6)
        assertEquals(2_580.0, tid.totalSeconds, 1e-6)
        assertEquals(720.0, tid.byClassSeconds[PhysiologicalIntensityClass.WALK_STATIONARY]!!, 1e-6)

        // T3-S15 strict 40-min (2,400 s) cap with POL-028 non-overridable 10m WU + 5m CD for Threshold
        val bounded40 = Engine15RunningWorkoutConstructor.constructBoundedCandidate(
            family = RunningQualityFamily10.THRESHOLD_INTERVALS,
            targetWorkSeconds = 1_260,
            maxWorkSeconds = 1_500,
            dayMaxSeconds = 2_400
        )
        assertFalse(bounded40.usedEasyStridesFallback)
        val ast = requireNotNull(bounded40.selectedAst)
        assertTrue(ast.totalMaxSeconds <= 2_400.0 + 1e-6)
        assertTrue(ast.warmUpSeconds >= Engine15RunningPolicyRegistry.POL_028_TEMPO_THRESHOLD_MIN_WU_S)
        assertTrue(ast.coolDownSeconds >= Engine15RunningPolicyRegistry.POL_029_QUALITY_MIN_CD_S)

        // Tight 20-min cap that cannot meet POL-028/029 + min work -> clean Easy+Strides fallback
        val emptyFallback = Engine15RunningWorkoutConstructor.constructBoundedCandidate(
            family = RunningQualityFamily10.TEMPO_CONTINUOUS,
            targetWorkSeconds = 900,
            maxWorkSeconds = 1_200,
            dayMaxSeconds = 1_200
        )
        assertTrue(emptyFallback.usedEasyStridesFallback)
        assertNull(emptyFallback.selectedAst)

        // MOD-11 4-level Unknown-Pace Stopping Predicate
        val pred = UnknownPaceStoppingPredicate(
            timeCapSeconds = 10_800,
            distanceCapMeters = 24_000,
            enforcementLevel = StoppingEnforcementLevel.PRESCRIBED_INSTRUCTION
        )
        assertFalse(pred.shouldStop(5_000.0, 12_000.0))
        assertTrue(pred.shouldStop(10_800.0, 18_000.0))
        assertTrue(pred.shouldStop(9_500.0, 24_000.0))
        assertFalse(pred.grantsGuaranteedDistanceCompliance())
    }

    @Test fun mod14FormalTaperDayConservedPartition7FieldRaceAccountAnd5DimFeasibility() {
        // MOD-14 exact Exponential & Linear taper sequences
        val expTaper = Engine15RunningMacrocycleEngine.evaluateFormalTaperSequence(
            curve = TaperCurveKind.EXPONENTIAL,
            vPeakRefMeters = 123_000.0,
            taperWeeks = 3,
            parameter = 0.358
        )
        assertEquals(86_000.0, expTaper[0], 1_000.0)
        assertEquals(60_100.0, expTaper[1], 1_000.0)
        assertEquals(42_000.0, expTaper[2], 1_000.0)

        val linTaper = Engine15RunningMacrocycleEngine.evaluateFormalTaperSequence(
            curve = TaperCurveKind.LINEAR,
            vPeakRefMeters = 103_000.0,
            taperWeeks = 3,
            parameter = 0.217
        )
        assertEquals(80_649.0, linTaper[0], 50.0)
        assertEquals(35_947.0, linTaper[2], 50.0)

        // Day-conserved phase partition including Wednesday mid-week start + RaceExecution separation
        val part = Engine15RunningMacrocycleEngine.buildDayConservedPartition(
            startDate = LocalDate.of(2026, 9, 2), // Wednesday
            endDateInclusive = LocalDate.of(2026, 11, 22),
            raceGoal = "Half marathon",
            weeklyMeters = 45_000.0,
            experience = 1,
            returning = false,
            includePostRaceExtensionDays = 7
        )
        assertTrue(part.daySumConserved)
        assertEquals(MacrocycleDayPhase.RACE_EXECUTION, part.spans.last().phase)
        assertTrue(part.spans.first().isPartialMicrocycleStart)
        assertNotNull(part.postRaceExtension)

        // T5-S17 Wed 10K 7-field exposure conservation
        val raceAccount = Engine15RunningMacrocycleEngine.buildRaceWeekAccount(
            vPeakRefMeters = 50_000.0,
            referenceWeekNumbers = listOf(6, 7),
            referenceBasis = "PROJECTED_LOADING_WEEKS",
            plannedPreRaceTrainingMeters = 10_270.0,
            raceDistanceMeters = 10_000.0,
            raceWarmUpAndCooldownMeters = 3_050.0,
            plannedPostRaceTrainingMeters = 26_000.0,
            raceSpeedBandMps = 3.8 to 4.1,
            raceGoal = "Improve 10K"
        )
        assertEquals(23_320.0, raceAccount.totalRaceWeekExposureMeters, 1e-6)
        assertEquals(49_320.0, raceAccount.calendarWeekTotalExposureMeters, 1e-6)

        // 5-dim Goal Feasibility: Beginner confirmed zero 5K has UNVERIFIED_PACE_UNKNOWN
        val feas = Engine15RunningMacrocycleEngine.classifyGoalFeasibility(
            request = PlanRequest(goal = "Improve 5K", weeklyKm = 0.0, recentMileageBasis = "CONFIRMED_ZERO", experience = 0),
            hasHardClinicalHold = false,
            paceIsUnknown = true,
            projectedPeakWeeklyMeters = 0.0
        )
        assertEquals(EventCompletionPreparation.UNVERIFIED_PACE_UNKNOWN, feas.eventCompletionPreparation)
    }

    @Test fun mod18FourLevelImpossibilityCon19ContiguityMod20TransitiveAndFfinal() {
        val mon = LocalDate.of(2026, 11, 23)
        // T5-S15: 3 x 60 min @ max 4:00/km (4.167 m/s) -> max 45 km/wk < 80 km requested -> PROVEN_INFEASIBLE
        val avail3Days = listOf(mon, mon.plusDays(2), mon.plusDays(4)).map { d ->
            OptimizerDayAvailability(d, listOf(DailyTimeWindow(6 * 3600, 7 * 3600)))
        }
        val caps = avail3Days.map { Engine15RunningPlanOptimizer.computeDayCapacity(it, 1000.0 / 240.0, 1000.0 / 300.0) }
        assertEquals(
            TriStateOracleOutcome.PROVEN_INFEASIBLE_IN_MODELED_DOMAIN,
            Engine15RunningPlanOptimizer.checkOptimisticFeasibility(80_000.0, caps, 3)
        )

        // CON-19 Split two 40-min windows sum to 80 min but CANNOT fit one continuous 60-min run
        val splitDay = OptimizerDayAvailability(
            mon,
            listOf(DailyTimeWindow(6 * 3600, 6 * 3600 + 2400), DailyTimeWindow(18 * 3600, 18 * 3600 + 2400))
        )
        assertFalse(Engine15RunningPlanOptimizer.fitsPerWindowContiguity(3_600.0, splitDay))
        assertTrue(Engine15RunningPlanOptimizer.fitsPerWindowContiguity(2_400.0, splitDay))

        // POL-037 anti-filler drops optional day when residual < 3 x 2.5 km
        val pruned = Engine15RunningPlanOptimizer.pruneAndAllocateEasyDays(
            remainingMeters = 5_500.0,
            mandatoryDays = listOf(mon),
            optionalDays = listOf(mon.plusDays(1), mon.plusDays(3))
        )
        assertEquals(2, pruned.size) // drops 3rd optional day instead of creating 1.83 km junk fillers

        // MOD-20 Two-Pass strict transitivity + raw Pareto tie-break (EXP-03: 10.0 < 10.8 inside bucket 10)
        val a = ObjectiveScoreVector(0.0, 0.0, 0.0, 10_000.0, 0.0, 0.0, "h1")
        val b = ObjectiveScoreVector(0.0, 0.0, 0.0, 10_800.0, 0.0, 0.0, "h2")
        val c = ObjectiveScoreVector(0.0, 0.0, 0.0, 12_600.0, 0.0, 0.0, "h3")
        assertTrue(Engine15RunningPlanOptimizer.twoPassComparator.compare(a, b) < 0)
        assertTrue(Engine15RunningPlanOptimizer.twoPassComparator.compare(b, c) < 0)
        assertTrue(Engine15RunningPlanOptimizer.twoPassComparator.compare(a, c) < 0)

        // Standalone F_final rejects back-to-back HIGH_STRESS sessions
        val badSpacing = Engine15RunningPlanOptimizer.validateFinal(
            sessions = listOf(
                OptimizerSessionCandidate("q1", mon, "run", true, false, 1800.0, 2400.0, 6000.0, 6500.0),
                OptimizerSessionCandidate("q2", mon.plusDays(1), "run", true, false, 1800.0, 2400.0, 6000.0, 6500.0)
            ),
            availabilityByDate = emptyMap()
        )
        assertEquals(TriStateOracleOutcome.PROVEN_INFEASIBLE_IN_MODELED_DOMAIN, badSpacing.outcome)
        assertTrue(badSpacing.violationCodes.any { it.startsWith("POL_035") })
    }
}

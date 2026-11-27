package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine16StrengthModelsAndMathTest {

    private val asOfDate = LocalDate.of(2026, 11, 27)
    private val catalog = Engine16StrengthExerciseCatalog.allMetadata()

    @Test
    fun mod13ExactDurationModelsUnilateralSupersetsAndRejects40MinOverflow() {
        // Worked example from MATHEMATICAL-MODELS.md (MOD-13 #2):
        // Unilateral 3 x 8/leg @ [3.0, 4.5] s/rep, [10, 15] s switch, 90 s rest, [45, 75] s setup
        // Min: 45 + 3*(16*3.0 + 10) + 180 = 45 + 174 + 180 = 399 s
        // Max: 75 + 3*(16*4.5 + 15) + 180 = 75 + 261 + 180 = 516 s
        val unilateral = Engine16StrengthDurationCalculator.straightBlockInterval(
            laterality = StrengthLaterality.UNILATERAL,
            isIsometricHold = false,
            workingSets = 3,
            repsOrHoldSeconds = 8,
            restSeconds = 90,
            rampSets = 0,
            includeSetup = true
        )
        assertEquals(399.0, unilateral.minSeconds.value, 1e-6)
        assertEquals(516.0, unilateral.maxSeconds.value, 1e-6)

        // Paired superset (MOD-13 #3, T6-S14 tight 40m cap):
        // Verify antagonist superset fits cleanly inside 40 minutes for 2-day or Upper/Lower under tight cap.
        val tightSession = Engine16StrengthWorkoutConstructor.construct(
            input = StrengthWorkoutConstructionInput(
                objective = StrengthObjective.GENERAL_STRENGTH,
                strengthDaysPerWeek = 2,
                slotIndex = 0,
                experience = 1,
                availableEquipment = setOf("dumbbell", "dumbbells", "bench", "band"),
                dailyTimeBudgetMinutes = 40
            ),
            catalog = catalog
        )
        assertTrue(tightSession.durationInterval.fitsBudget(40))
        assertTrue(
            tightSession.pairingMode == StrengthPairingMode.ANTAGONIST_SUPERSET ||
                tightSession.isMedFallback
        )

        // MD-07: Impossible 14-minute daily budget rejects cleanly instead of silently exceeding cap!
        assertThrows(PlanConstraint::class.java) {
            Engine16StrengthWorkoutConstructor.construct(
                input = StrengthWorkoutConstructionInput(
                    objective = StrengthObjective.MAXIMAL_STRENGTH,
                    strengthDaysPerWeek = 2,
                    slotIndex = 0,
                    experience = 1,
                    availableEquipment = setOf("barbell", "rack", "safeties", "bench"),
                    dailyTimeBudgetMinutes = 14
                ),
                catalog = catalog
            )
        }
    }

    @Test
    fun mod16TierAe1RmEnforcesRirSumAndRejectsMd01() {
        // MD-01: 10 reps @ 4 RIR (r_eff = 14) must be unconditionally rejected!
        assertNull(Engine16StrengthCalibrationEngine.estimateE1Rm(100.0, reps = 10, rir = 4))
        assertNull(Engine16StrengthCalibrationEngine.estimateE1Rm(100.0, reps = 10, rir = 4, allowTierBShadowWindow = true))
        assertNull(StrengthProgressionProjector.estimateE1Rm(100.0, reps = 10, rir = 4))

        // MATHEMATICAL-MODELS.md (MOD-16 worked example): 100 kg x 6 @ 2 RIR -> r_eff = 8
        // Epley = 126.67 kg, Brzycki = 124.16 kg
        val tierA = Engine16StrengthCalibrationEngine.estimateE1Rm(100.0, reps = 6, rir = 2)
        assertNotNull(tierA)
        assertEquals(E1RmValidityTier.TIER_A_PROVISIONAL, tierA!!.tier)
        assertEquals(8, tierA.effectiveReps)
        assertEquals(126.666667, tierA.epleyKg.value, 1e-3)
        assertEquals(124.1619, tierA.brzyckiKg.value, 1e-3)
        assertFalse(tierA.isMeasuredMaxStrength)

        // Tier B window (r_eff = 11..12) rejected in Tier A, flagged TIER_B_SHADOW_ONLY when opted-in
        assertNull(Engine16StrengthCalibrationEngine.estimateE1Rm(100.0, reps = 8, rir = 3, allowTierBShadowWindow = false))
        val tierB = Engine16StrengthCalibrationEngine.estimateE1Rm(100.0, reps = 8, rir = 3, allowTierBShadowWindow = true)
        assertNotNull(tierB)
        assertEquals(E1RmValidityTier.TIER_B_SHADOW_ONLY, tierB!!.tier)

        // Non-clean technique rejected
        assertNull(
            Engine16StrengthCalibrationEngine.estimateE1Rm(
                weightKg = 100.0,
                reps = 6,
                rir = 2,
                techniqueQuality = TechniqueQuality.BREAKDOWN
            )
        )
    }

    @Test
    fun externalLoadSemanticsPreventsMd02MismatchAndContextChangeForcesRirRediscovery() {
        val dbLoad = ExternalLoadSemantics.DumbbellPerHand(Kilograms.of(15.0), hands = 2)
        val bbLoad = ExternalLoadSemantics.TotalBarbellMass(Kilograms.of(15.0))
        assertEquals(30.0, dbLoad.combinedKg.value, 1e-6)
        assertFalse(dbLoad.isComparableWith(bbLoad))

        val barbellCtx = StrengthLoadContext(
            exerciseId = "e1",
            loadSemanticsKind = ExternalLoadSemanticsKind.TOTAL_BARBELL_MASS,
            laterality = StrengthLaterality.BILATERAL
        )
        val unilatCtx = StrengthLoadContext(
            exerciseId = "e6",
            loadSemanticsKind = ExternalLoadSemanticsKind.DUMBBELL_PER_HAND,
            laterality = StrengthLaterality.UNILATERAL
        )
        val prevObs = ObservedSubmaximalPerformance(
            exerciseId = "e1",
            performedOn = asOfDate.minusDays(7),
            context = barbellCtx,
            load = ExternalLoadSemantics.TotalBarbellMass(Kilograms.of(120.0)),
            completedReps = 5,
            reportedRir = 2,
            techniqueQuality = TechniqueQuality.CLEAN,
            techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE
        )

        val mismatchCal = Engine16StrengthCalibrationEngine.calibrate(unilatCtx, asOfDate, prevObs)
        assertTrue(mismatchCal.trainingLoadRef is ProvisionalTrainingLoadRef.DiscoverByRir)
        assertNull(mismatchCal.estimatedMax)

        // Matching context preserves submax reference anchor
        val sameCal = Engine16StrengthCalibrationEngine.calibrate(barbellCtx, asOfDate, prevObs)
        assertTrue(sameCal.trainingLoadRef is ProvisionalTrainingLoadRef.AnchoredToSubmax)
        assertNotNull(sameCal.estimatedMax)

        // Body-mass shift >= 5% on weighted calisthenics invalidates transfer
        val bmOld = ExternalLoadSemantics.ExternalAddedMass(Kilograms.of(25.0), Kilograms.of(75.0))
        val bmNew = ExternalLoadSemantics.ExternalAddedMass(Kilograms.of(25.0), Kilograms.of(80.0))
        assertFalse(bmOld.isComparableWith(bmNew))
    }

    @Test
    fun unifiedMuscleLedgerAggregatesCrossDisciplineSetsWithoutLeakingProjectedWorkMd06Md14() {
        val owner = "local-plan-11111111-2222-3333-4444-555555555555"
        val freeformBench = FreeformWorkout(
            id = "ff-upper-1",
            owner = owner,
            discipline = ManualDiscipline.STRENGTH,
            performedOn = "2026-11-24",
            startedAt = Instant.parse("2026-11-24T17:00:00Z").toEpochMilli(),
            modifiedAt = Instant.parse("2026-11-24T17:45:00Z").toEpochMilli(),
            completedAt = Instant.parse("2026-11-24T17:45:00Z").toEpochMilli(),
            exercises = listOf(
                FreeformExercise(
                    name = "Barbell bench press",
                    catalogId = "str-bb-bench-press",
                    sets = listOf(
                        FreeformSet(actual = FreeformMeasure("8", kg = "80"), rir = "2", completed = true),
                        FreeformSet(actual = FreeformMeasure("8", kg = "80"), rir = "2", completed = true),
                        FreeformSet(actual = FreeformMeasure("8", kg = "80"), rir = "2", completed = true)
                    )
                )
            )
        )
        val freeformPushup = FreeformWorkout(
            id = "ff-cal-1",
            owner = owner,
            discipline = ManualDiscipline.CALISTHENICS,
            performedOn = "2026-11-25",
            startedAt = Instant.parse("2026-11-25T17:00:00Z").toEpochMilli(),
            modifiedAt = Instant.parse("2026-11-25T17:20:00Z").toEpochMilli(),
            completedAt = Instant.parse("2026-11-25T17:20:00Z").toEpochMilli(),
            exercises = listOf(
                FreeformExercise(
                    name = "Diamond push-up",
                    catalogId = "cal-diamond-pushup",
                    sets = listOf(
                        FreeformSet(actual = FreeformMeasure("12"), rir = "2", completed = true),
                        FreeformSet(actual = FreeformMeasure("12"), rir = "2", completed = true)
                    )
                )
            )
        )
        // MD-14: Planned uncompleted session in future must NEVER write to observed ledger!
        val futurePlan = PlanSnapshot(
            id = "plan-proj-1",
            owner = owner,
            engineVersion = ENGINE16_VERSION,
            policyId = ENGINE16_STRENGTH_POLICY,
            createdAt = Instant.parse("2026-11-20T08:00:00Z"),
            request = PlanRequest(goal = "Hypertrophy", runDays = 0, strengthDays = 4),
            sessions = listOf(
                PlannedSession(
                    id = "proj-1",
                    date = asOfDate.plusDays(1),
                    discipline = "str",
                    title = "Upper",
                    minutes = 45,
                    distanceKm = null,
                    kind = "strength",
                    status = "planned",
                    exercises = listOf(ExercisePrescription("str-bb-bench-press", "Barbell bench press", sets = 5, reps = 8))
                )
            ),
            explanations = emptyList(),
            status = "approved"
        )

        val observed = Engine16LocalMuscleLedgerBuilder.buildObserved(
            accountId = owner,
            asOfDate = asOfDate,
            windowDays = 7,
            plans = listOf(futurePlan),
            freeformWorkouts = listOf(freeformBench, freeformPushup)
        )
        assertFalse(observed.isProjected)
        // Chest (pectoralis_major): 3 sets bench + 2 sets diamond pushup = 5 direct sets (MD-06)
        assertEquals(5, observed.exposureFor("pectoralis_major").directHardSets)
        // Triceps: 2 direct (diamond pushup) + 3 indirect (bench press) -> Tier B shadow = 3.5 (MOD-B4)
        val triceps = observed.exposureFor("triceps")
        assertEquals(2, triceps.directHardSets)
        assertEquals(3, triceps.indirectHardSets)
        assertEquals(3.5, triceps.shadowFractionalSets, 1e-6)
    }
}

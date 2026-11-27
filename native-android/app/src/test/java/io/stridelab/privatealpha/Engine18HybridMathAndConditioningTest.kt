package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine18HybridMathAndConditioningTest {
    private val owner = "engine18-cond-tester"
    private val now = Instant.parse("2026-11-27T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-30")

    @Test fun mod19NormalizedShortfallShortCircuitsDivisionByZeroAndBoundsToTwo() {
        // Inactive or already >= T* => 0.0
        assertEquals(0.0, Engine18HybridGoalOptimizer.computeNormalizedShortfall(
            GoalShortfallInput("g1", "run", active = false, achievedDoseQ = 0.0, minimumViableM = 20.0, targetDoseTStar = 40.0)
        ), 1e-9)
        assertEquals(0.0, Engine18HybridGoalOptimizer.computeNormalizedShortfall(
            GoalShortfallInput("g1", "run", active = true, achievedDoseQ = 40.0, minimumViableM = 20.0, targetDoseTStar = 40.0)
        ), 1e-9)
        // Edge T* == m_g == 0.0
        assertEquals(0.0, Engine18HybridGoalOptimizer.computeNormalizedShortfall(
            GoalShortfallInput("g2", "str", active = true, achievedDoseQ = 0.0, minimumViableM = 0.0, targetDoseTStar = 0.0)
        ), 1e-9)
        // Edge q >= m_g == T* > 0 -> short-circuits cleanly to 0.0
        assertEquals(0.0, Engine18HybridGoalOptimizer.computeNormalizedShortfall(
            GoalShortfallInput("g3", "cal", active = true, achievedDoseQ = 10.0, minimumViableM = 10.0, targetDoseTStar = 10.0)
        ), 1e-9)
        // Below m_g -> in (1.0, 2.0]
        val belowMin = Engine18HybridGoalOptimizer.computeNormalizedShortfall(
            GoalShortfallInput("g4", "run", active = true, achievedDoseQ = 0.0, minimumViableM = 20.0, targetDoseTStar = 40.0)
        )
        assertEquals(2.0, belowMin, 1e-9)
        // Between m_g and T* -> in [0.0, 1.0]
        val mid = Engine18HybridGoalOptimizer.computeNormalizedShortfall(
            GoalShortfallInput("g5", "run", active = true, achievedDoseQ = 30.0, minimumViableM = 20.0, targetDoseTStar = 40.0)
        )
        assertEquals(0.5, mid, 1e-9)
    }

    @Test fun leximinComparatorIsStrictlyTransitiveAndPreservesSecondaryDevelopment() {
        val c1 = HybridScheduleScoreCandidate(
            candidateId = "cand-1",
            goalInputs = listOf(
                GoalShortfallInput("run", "run", true, 35.0, 20.0, 40.0), // s=0.25
                GoalShortfallInput("str", "str", true, 8.0, 6.0, 10.0)    // s=0.50
            ),
            interferencePenalty = 0.0
        )
        val c2 = HybridScheduleScoreCandidate(
            candidateId = "cand-2",
            goalInputs = listOf(
                GoalShortfallInput("run", "run", true, 40.0, 20.0, 40.0), // s=0.0
                GoalShortfallInput("str", "str", true, 4.0, 6.0, 10.0)    // s=1.333 (below min!)
            ),
            interferencePenalty = 0.0
        )
        val c3 = HybridScheduleScoreCandidate(
            candidateId = "cand-3",
            goalInputs = listOf(
                GoalShortfallInput("run", "run", true, 38.0, 20.0, 40.0), // s=0.10
                GoalShortfallInput("str", "str", true, 10.0, 6.0, 10.0)   // s=0.0
            ),
            interferencePenalty = 0.0
        )
        val sorted = listOf(c2, c1, c3).sortedWith(Engine18HybridGoalOptimizer.leximinComparator)
        assertEquals(listOf("cand-3", "cand-1", "cand-2"), sorted.map { it.candidateId })
        assertTrue(Engine18HybridGoalOptimizer.leximinComparator.compare(c3, c1) < 0)
        assertTrue(Engine18HybridGoalOptimizer.leximinComparator.compare(c1, c2) < 0)
        assertTrue(Engine18HybridGoalOptimizer.leximinComparator.compare(c3, c2) < 0)

        assertEquals(
            DisciplineEmphasisPhase.DEVELOPMENT,
            Engine18HybridGoalOptimizer.evaluateSecondaryEmphasis(primaryShortfall = 0.0, remainingWeeklyMinutes = 90, secondaryDevelopmentMinMinutes = 60)
        )
        assertEquals(
            DisciplineEmphasisPhase.MAINTENANCE,
            Engine18HybridGoalOptimizer.evaluateSecondaryEmphasis(primaryShortfall = 0.25, remainingWeeklyMinutes = 90, secondaryDevelopmentMinMinutes = 60)
        )
    }

    @Test fun alternatingBlocksParetoAlternativesAndHonestCapacityInfeasibilityWorkCleanly() {
        val blocks = Engine18HybridGoalOptimizer.assignAlternatingEmphasisBlocks(8, listOf("str", "run"), blockWeeks = 4)
        assertEquals(DisciplineEmphasisPhase.DEVELOPMENT, blocks.getValue(1).getValue("str"))
        assertEquals(DisciplineEmphasisPhase.MAINTENANCE, blocks.getValue(1).getValue("run"))
        assertEquals(DisciplineEmphasisPhase.DEVELOPMENT, blocks.getValue(5).getValue("run"))
        assertEquals(DisciplineEmphasisPhase.MAINTENANCE, blocks.getValue(5).getValue("str"))

        val pareto = Engine18HybridGoalOptimizer.buildParetoAlternatives(availableDays = 4, disciplines = listOf("run", "str"))
        assertEquals(3, pareto.size)

        val infeasible = Engine18HybridGoalOptimizer.checkMultiGoalCapacityFeasibility(
            weeklyAvailableMinutes = 120,
            goalMinDevelopmentMinutes = mapOf("run" to 150, "str" to 90),
            goalMaintenanceMinutes = mapOf("run" to 60, "str" to 40)
        )
        assertEquals(MultiGoalFeasibilityOutcome.REQUESTED_MULTI_GOAL_PLAN_INFEASIBLE, infeasible.outcome)
        assertEquals(4, infeasible.pathways.size)
        assertFalse(infeasible.note.contains("human", ignoreCase = true))
        assertTrue(infeasible.note.contains("modeled", ignoreCase = true))
    }

    @Test fun repairedPol048Emom40sCutoffAndMod17RepScalingProtectRestFloor() {
        // 40.0 s/min is strictly within the repaired <= 40s cutoff (no premature reduction)
        val okAt40 = Engine18ConditioningWorkoutConstructor.scaleEmomReps(oldReps = 10, observedWorkSeconds = 40.0)
        assertFalse(okAt40.scaled)
        assertEquals(10, okAt40.newRepsPerMinute)

        // 42.0 s/min violates the repaired > 40.0 s cutoff (even though legacy erratum had 45s) and triggers scaling
        val over40 = Engine18ConditioningWorkoutConstructor.scaleEmomReps(oldReps = 10, observedWorkSeconds = 42.0, targetRestSeconds = 25.0)
        assertTrue(over40.scaled)
        assertFalse(over40.switchedToWorkRestFormat)
        assertEquals(8, over40.newRepsPerMinute)
        assertTrue(over40.estimatedWorkSecondsPerMinute <= 40.0)

        // Extreme slow pace (< 1 rep possible within rest target) converts cleanly to 30/30 work-rest intervals
        val switched = Engine18ConditioningWorkoutConstructor.scaleEmomReps(oldReps = 1, observedWorkSeconds = 58.0, targetRestSeconds = 25.0)
        assertTrue(switched.scaled)
        assertTrue(switched.switchedToWorkRestFormat)
        assertNull(switched.newRepsPerMinute)
    }

    @Test fun md10ModalityHrIsolationBlocksRunningHrCrossApplication() {
        val verifiedRunProfile = ExecutionHrZoneProfile(
            z1FloorBpm = 120,
            z2FloorBpm = 138,
            z3FloorBpm = 152,
            z4FloorBpm = 165,
            z5FloorBpm = 176,
            maxHrBpm = 190
        )
        assertNull(
            Engine18ConditioningWorkoutConstructor.resolveModalityHrZones(
                ConditioningModality.BIKE, M2BPhysiologicalModality.RUNNING, verifiedRunProfile
            )
        )
        assertNull(
            Engine18ConditioningWorkoutConstructor.resolveModalityHrZones(
                ConditioningModality.ROW, M2BPhysiologicalModality.CYCLING, verifiedRunProfile
            )
        )
        assertNotNull(
            Engine18ConditioningWorkoutConstructor.resolveModalityHrZones(
                ConditioningModality.BIKE, M2BPhysiologicalModality.CYCLING, verifiedRunProfile
            )
        )
    }

    @Test fun md08NoviceSkillGateAndMd09SingleAxisConditioningEnforced() {
        assertFalse(Engine18ConditioningWorkoutConstructor.isMovementAllowedForConditioning("str-back-squat", "advanced", experience = 0))
        assertFalse(Engine18ConditioningWorkoutConstructor.isMovementAllowedForConditioning("cond-double-unders", "intermediate", experience = 0))
        assertTrue(Engine18ConditioningWorkoutConstructor.isMovementAllowedForConditioning("cond-step-jack", "foundation", experience = 0))
        assertTrue(Engine18ConditioningWorkoutConstructor.isMovementAllowedForConditioning("cond-double-unders", "intermediate", experience = 1))

        val singleAxisOk = Engine18ConditioningWorkoutConstructor.validateSingleAxisConditioningProgression(
            deltaDurationRel = 0.08, deltaRounds = 0, deltaLoadRel = 0.0, densityIncreased = false, switchedToNewHiitOrMixedFamily = false
        )
        assertTrue(singleAxisOk.valid)

        val multiAxisBad = Engine18ConditioningWorkoutConstructor.validateSingleAxisConditioningProgression(
            deltaDurationRel = 0.08, deltaRounds = 1, deltaLoadRel = 0.0, densityIncreased = false, switchedToNewHiitOrMixedFamily = false
        )
        assertFalse(multiAxisBad.valid)
        assertTrue(multiAxisBad.axesChanged.containsAll(listOf("DURATION", "ROUNDS")))
    }

    @Test fun allEightConditioningFamiliesComposeValidSoloPlansAndRoundTrip() {
        val goals = listOf(
            "Continuous aerobic" to ConditioningFamily8.CONTINUOUS_AEROBIC,
            "Modality HIIT intervals" to ConditioningFamily8.MODALITY_HIIT,
            "Structured circuits" to ConditioningFamily8.STRUCTURED_CIRCUITS,
            "EMOM" to ConditioningFamily8.EMOM,
            "Quality AMRAP" to ConditioningFamily8.QUALITY_PACED_AMRAP,
            "Work rest intervals" to ConditioningFamily8.WORK_REST_INTERVALS,
            "Mixed modal" to ConditioningFamily8.MIXED_MODAL_CONDITIONING,
            "Muscular endurance" to ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS
        )
        goals.forEach { (goalLabel, expectedFamily) ->
            val req = PlanRequest(
                goal = goalLabel,
                start = monday,
                weeks = 4,
                days = setOf(1, 3, 5),
                runDays = 0,
                conditioningDays = 2,
                experience = 1,
                minutes = 45,
                equipment = mapOf("erg" to 1, "kettlebells" to 1, "dumbbells" to 1, "rope" to 1),
                policyChoice = "engine15"
            )
            assertEquals(expectedFamily, Engine18ConditioningWorkoutConstructor.familyFromGoal(goalLabel, 1, hasErgOrExternalEquipment = true))
            val plan = Engine18ConditioningWorkoutConstructor.buildSoloPlan(req, owner, "cond-${expectedFamily.name.lowercase()}", now)
            assertEquals(ENGINE18_VERSION, plan.engineVersion)
            assertEquals(ENGINE18_CONDITIONING_POLICY, plan.policyId)
            assertEquals(8, plan.sessions.size)
            assertTrue(plan.sessions.all { it.discipline == "cond" && it.kind == "conditioning" && it.exercises.isNotEmpty() })
            if (expectedFamily == ConditioningFamily8.EMOM) {
                assertTrue(plan.sessions.all { s -> s.exercises.all { ex -> (ex.rest ?: 0) >= 20 } })
            }
            Engine18ConditioningWorkoutConstructor.validateSavedSolo(plan)
            val decoded = PlanSnapshot.decode(plan.encode(), owner)
            Engine15IndependentPlanGenerator.validateSaved(decoded)
        }
    }
}

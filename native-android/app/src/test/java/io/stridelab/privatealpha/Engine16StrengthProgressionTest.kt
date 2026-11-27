package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class Engine16StrengthProgressionTest {

    private val catalog = Engine16StrengthExerciseCatalog.allMetadata().associateBy { it.id }

    @Test
    fun allNineOperatorsAndRpDistributionPreservingSetAllocationWorkDeterministically() {
        val backSquat = catalog.getValue("e1")
        val dbRow = catalog.getValue("dr")

        // 1. Low-rep heavy compound -> PROGRESS_ONE_SET (3x4 -> [5, 4, 4])
        val oneSet = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = backSquat,
                objective = StrengthObjective.MAXIMAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 4,
                repRange = 3..6,
                completedSets = listOf(
                    StrengthSetExecutionSummary(4, 120.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(4, 120.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(4, 120.0, reportedRir = 2, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE
            )
        )
        assertEquals(StrengthProgressionOperator.PROGRESS_ONE_SET, oneSet.operator)
        assertEquals(listOf(5, 4, 4), oneSet.perSetTargetReps)

        // 2. Partial progression (already [5, 4, 4] -> PROGRESS_PARTIAL_SETS [5, 5, 4])
        val partialSets = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = backSquat,
                objective = StrengthObjective.MAXIMAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 4,
                repRange = 3..6,
                completedSets = listOf(
                    StrengthSetExecutionSummary(5, 120.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(4, 120.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(4, 120.0, reportedRir = 2, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE
            )
        )
        assertEquals(StrengthProgressionOperator.PROGRESS_PARTIAL_SETS, partialSets.operator)
        assertEquals(listOf(5, 5, 4), partialSets.perSetTargetReps)

        // 3. Moderate rep range climb -> PROGRESS_ALL_SETS
        val allSets = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = dbRow,
                objective = StrengthObjective.HYPERTROPHY,
                currentSets = 3,
                currentTargetReps = 9,
                repRange = 8..12,
                completedSets = listOf(
                    StrengthSetExecutionSummary(9, 25.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(9, 25.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(9, 25.0, reportedRir = 2, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE
            )
        )
        assertEquals(StrengthProgressionOperator.PROGRESS_ALL_SETS, allSets.operator)
        assertEquals(listOf(10, 10, 10), allSets.perSetTargetReps)

        // 4. Standard clean top-of-range mastery -> PROGRESS_LOAD_RESET_REPS (60 kg + 2.5 kg -> 62.5 kg x 8)
        val loadBump = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = backSquat,
                objective = StrengthObjective.GENERAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 10,
                repRange = 8..10,
                completedSets = listOf(
                    StrengthSetExecutionSummary(10, 60.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(10, 60.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(10, 60.0, reportedRir = 2, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE,
                minAvailableIncrementKg = 2.5
            )
        )
        assertEquals(StrengthProgressionOperator.PROGRESS_LOAD_RESET_REPS, loadBump.operator)
        assertEquals(62.5, loadBump.recommendedLoadKg!!, 1e-6)
        assertEquals(listOf(8, 8, 8), loadBump.perSetTargetReps)

        // 5. Bodyweight hypertrophy at top of range -> PROGRESS_SET_VOLUME
        val bwPushup = catalog.getValue("e4")
        val setBump = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = bwPushup,
                objective = StrengthObjective.HYPERTROPHY,
                currentSets = 3,
                currentTargetReps = 12,
                repRange = 8..12,
                completedSets = listOf(
                    StrengthSetExecutionSummary(12, null, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(12, null, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(12, null, reportedRir = 2, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE,
                currentWeeklyDirectMuscleSets = 10
            )
        )
        assertEquals(StrengthProgressionOperator.PROGRESS_SET_VOLUME, setBump.operator)
        assertEquals(4, setBump.recommendedSets)

        // 6. RP distribution-preserving muscle set allocation
        val allocated = Engine16StrengthProgressionEngine.allocateMuscleGroupSets(
            priorSetsByExercise = mapOf("e1" to 4, "str-leg-extension" to 2),
            deltaSets = 2
        )
        assertEquals(8, allocated.values.sum())
        assertTrue(allocated.getValue("e1") >= allocated.getValue("str-leg-extension"))

        // 7. Conditional deload (POL-049): cut sets while maintaining heavy load at 3-4 RIR
        val deload = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = backSquat,
                objective = StrengthObjective.MAXIMAL_STRENGTH,
                currentSets = 4,
                currentTargetReps = 5,
                repRange = 3..6,
                completedSets = listOf(StrengthSetExecutionSummary(5, 130.0, 1, TechniqueQuality.CLEAN)),
                fatigueTriggeredDeload = true
            )
        )
        assertEquals(StrengthProgressionOperator.REDUCE_WORKING_SETS, deload.operator)
        assertEquals(2, deload.recommendedSets)
        assertEquals(130.0, deload.recommendedLoadKg!!, 1e-6)
        assertEquals(3..4, deload.targetRir)

        // 8. Breakdown -> REDUCE_LOAD
        val backoff = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = backSquat,
                objective = StrengthObjective.GENERAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 8,
                repRange = 8..10,
                completedSets = listOf(
                    StrengthSetExecutionSummary(6, 100.0, 0, TechniqueQuality.BREAKDOWN),
                    StrengthSetExecutionSummary(6, 100.0, 0, TechniqueQuality.BREAKDOWN),
                    StrengthSetExecutionSummary(6, 100.0, 0, TechniqueQuality.BREAKDOWN)
                )
            )
        )
        assertEquals(StrengthProgressionOperator.REDUCE_LOAD, backoff.operator)
        assertTrue(backoff.recommendedLoadKg!! < 100.0)

        // 9. Equipment/pain substitution -> SUBSTITUTE_MOVEMENT
        val sub = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = backSquat,
                objective = StrengthObjective.GENERAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 8,
                repRange = 8..10,
                completedSets = emptyList(),
                equipmentBlockedOrPainful = true
            )
        )
        assertEquals(StrengthProgressionOperator.SUBSTITUTE_MOVEMENT, sub.operator)
    }

    @Test
    fun coarseIncrementBridgeHandlesFixed10To15KgDumbbellsWithoutUnsafeJumpMd03() {
        val dbPress = catalog.getValue("fp")
        // T6-S13 / MD-03: 10 kg/hand with smallest jump 5 kg/hand (+50% > 12% POL-045 threshold)
        val decision = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = dbPress,
                objective = StrengthObjective.GENERAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 12,
                repRange = 8..12,
                completedSets = listOf(
                    StrengthSetExecutionSummary(12, 10.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(12, 10.0, reportedRir = 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(12, 10.0, reportedRir = 2, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE,
                minAvailableIncrementKg = 5.0
            )
        )
        assertNotNull(decision.coarseBridgePlan)
        assertEquals(10.0, decision.recommendedLoadKg!!, 1e-6)
        assertEquals(CoarseBridgeOption.EXTEND_REP_CEILING, decision.coarseBridgePlan!!.selectedOption)
        assertEquals(listOf(13, 13, 13), decision.perSetTargetReps)
    }

    @Test
    fun minimalInputPol046ExecutionConflictAndMd04HoldWithoutFakingVerification() {
        val axialSquat = catalog.getValue("e1")

        // Messy evidence with only 1 session -> CONSOLIDATE_MAINTAIN
        val firstSession = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = axialSquat,
                objective = StrengthObjective.GENERAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 8,
                repRange = 8..10,
                completedSets = listOf(
                    StrengthSetExecutionSummary(10, 80.0, reportedRir = null, TechniqueQuality.UNOBSERVED),
                    StrengthSetExecutionSummary(10, 80.0, reportedRir = null, TechniqueQuality.UNOBSERVED),
                    StrengthSetExecutionSummary(10, 80.0, reportedRir = null, TechniqueQuality.UNOBSERVED)
                ),
                consecutiveCompletedCleanSessions = 1,
                techniqueProvenance = TechniqueProvenance.UNOBSERVED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.MISSING_UNREPORTED
            )
        )
        assertEquals(StrengthProgressionOperator.CONSOLIDATE_MAINTAIN, firstSession.operator)
        assertTrue("UNOBSERVED_TECHNIQUE" in firstSession.uncertaintyTags)

        // MD-04: After 2 completed sessions with unobserved technique on axial squat, never do aggressive load jump!
        val secondSession = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = axialSquat,
                objective = StrengthObjective.GENERAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 10,
                repRange = 8..10,
                completedSets = listOf(
                    StrengthSetExecutionSummary(10, 80.0, reportedRir = null, TechniqueQuality.UNOBSERVED),
                    StrengthSetExecutionSummary(10, 80.0, reportedRir = null, TechniqueQuality.UNOBSERVED),
                    StrengthSetExecutionSummary(10, 80.0, reportedRir = null, TechniqueQuality.UNOBSERVED)
                ),
                consecutiveCompletedCleanSessions = 2,
                verifiedSubsequentContinuity = true,
                techniqueProvenance = TechniqueProvenance.UNOBSERVED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.MISSING_UNREPORTED
            )
        )
        assertEquals(StrengthProgressionOperator.PROGRESS_ONE_SET, secondSession.operator)
        assertEquals(80.0, secondSession.recommendedLoadKg!!, 1e-6)
        assertTrue("UNOBSERVED_TECHNIQUE" in secondSession.uncertaintyTags)

        // Execution conflict: failed bottom rep (< 8) while reporting 4 RIR -> CONSOLIDATE_MAINTAIN
        val conflict = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = axialSquat,
                objective = StrengthObjective.GENERAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 8,
                repRange = 8..10,
                completedSets = listOf(
                    StrengthSetExecutionSummary(5, 80.0, reportedRir = 4, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(6, 80.0, reportedRir = 4, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(6, 80.0, reportedRir = 4, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE
            )
        )
        assertTrue(conflict.executionConflictDetected)
        assertEquals(StrengthProgressionOperator.CONSOLIDATE_MAINTAIN, conflict.operator)
    }

    @Test
    fun gaplessReturnBucketsAndRunningMajorStepHoldProtectDisciplineIsolationMd13Md15() {
        // POL-033 / POL-058 gapless buckets: 20d (inside 14..27) and 40d (inside 28..55)
        val break20 = Engine16StrengthProgressionEngine.evaluateReturnReentry(20)
        assertEquals(StrengthInterruptionBucket.DAYS_14_TO_27, break20.bucket)
        assertTrue(break20.loadMultiplier in 0.80..0.90)
        assertTrue(break20.evidenceGatedExitRequired)

        val break40 = Engine16StrengthProgressionEngine.evaluateReturnReentry(40)
        assertEquals(StrengthInterruptionBucket.DAYS_28_TO_55, break40.bucket)
        assertTrue(break40.loadMultiplier in 0.70..0.80)
        assertTrue(break40.disallowNovelEccentrics)

        // MD-15: Rising running stress holds lower-body strength steady while upper body progresses!
        val lowerSquat = catalog.getValue("e1")
        val upperPress = catalog.getValue("str-bb-bench-press")
        val lowerHold = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = lowerSquat,
                objective = StrengthObjective.RUNNING_SUPPORTIVE,
                currentSets = 3,
                currentTargetReps = 6,
                repRange = 4..6,
                completedSets = listOf(
                    StrengthSetExecutionSummary(6, 100.0, 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(6, 100.0, 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(6, 100.0, 2, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE,
                runningMajorStepActive = true
            )
        )
        assertEquals(StrengthProgressionOperator.CONSOLIDATE_MAINTAIN, lowerHold.operator)
        assertEquals(100.0, lowerHold.recommendedLoadKg!!, 1e-6)

        val upperProgress = Engine16StrengthProgressionEngine.evaluate(
            StrengthProgressionInput(
                exercise = upperPress,
                objective = StrengthObjective.GENERAL_STRENGTH,
                currentSets = 3,
                currentTargetReps = 10,
                repRange = 8..10,
                completedSets = listOf(
                    StrengthSetExecutionSummary(10, 75.0, 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(10, 75.0, 2, TechniqueQuality.CLEAN),
                    StrengthSetExecutionSummary(10, 75.0, 2, TechniqueQuality.CLEAN)
                ),
                techniqueProvenance = TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                symptomStatus = SymptomSafetyStatus.CONFIRMED_NONE,
                runningMajorStepActive = true
            )
        )
        assertEquals(StrengthProgressionOperator.PROGRESS_LOAD_RESET_REPS, upperProgress.operator)
        assertEquals(77.5, upperProgress.recommendedLoadKg!!, 1e-6)
    }
}

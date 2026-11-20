package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * Batch 35 (Stage 9): Multi-discipline qualification & stabilization matrix across
 * Running, Strength, Calisthenics, and Conditioning (solo and combined static plans).
 */
class Engine15MultiDisciplineStabilizationTest {
    private val owner = "multidiscipline-stabilization-runner"
    private val now = Instant.parse("2026-11-21T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")
    private val library by lazy {
        ExerciseLibraryV2.load { path -> File("src/main/assets", path).inputStream() }
    }

    private fun verifyRoundTrip(plan: PlanSnapshot) {
        val approved = PlanOperations.approve(plan, listOf(plan))
        assertEquals("approved", approved.status)
        val restored = PlanSnapshot.decode(approved.encode(), owner)
        assertEquals(approved, restored)
        Engine15IndependentPlanGenerator.validateSaved(restored)
    }

    private val fullEquipment = mapOf(
        "bands" to 1, "anchor" to 1, "dumbbells" to 1, "barbell" to 1, "bench" to 1, "pullup_bar" to 1
    )

    @Test fun soloStrengthCalisthenicsAndConditioningPlansGenerateApproveAndValidate() {
        val strengthReq = PlanRequest(
            goal = "General strength",
            start = monday,
            weeks = 8,
            days = setOf(1, 3, 5),
            runDays = 0,
            strengthDays = 3,
            minutes = 45,
            equipment = fullEquipment,
            policyChoice = "engine15"
        )
        val strengthPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            strengthReq, owner, emptyList(), "solo-str-8w", now, exerciseLibrary = library
        )
        assertEquals(ENGINE15_INDEPENDENT_STRENGTH_POLICY, strengthPlan.policyId)
        assertEquals(24, strengthPlan.sessions.size)
        assertTrue(strengthPlan.sessions.all { it.discipline == "str" })
        verifyRoundTrip(strengthPlan)

        val calReq = PlanRequest(
            goal = "Calisthenics foundation",
            start = monday,
            weeks = 8,
            days = setOf(1, 3, 5),
            runDays = 0,
            calDays = 3,
            minutes = 40,
            equipment = fullEquipment,
            policyChoice = "engine15"
        )
        val calPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            calReq, owner, emptyList(), "solo-cal-8w", now, exerciseLibrary = library
        )
        assertEquals(ENGINE15_INDEPENDENT_CALISTHENICS_POLICY, calPlan.policyId)
        assertEquals(24, calPlan.sessions.size)
        assertTrue(calPlan.sessions.all { it.discipline == "cal" })
        verifyRoundTrip(calPlan)

        val condReq = PlanRequest(
            goal = "Conditioning",
            start = monday,
            weeks = 8,
            days = setOf(2, 4),
            runDays = 0,
            conditioningDays = 2,
            minutes = 30,
            policyChoice = "engine15"
        )
        val condPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            condReq, owner, emptyList(), "solo-cond-8w", now, exerciseLibrary = library
        )
        assertEquals(ENGINE15_INDEPENDENT_CONDITIONING_POLICY, condPlan.policyId)
        assertEquals(16, condPlan.sessions.size)
        assertTrue(condPlan.sessions.all { it.discipline == "cond" })
        verifyRoundTrip(condPlan)
    }

    @Test fun combinedRunPlusStrengthCalisthenicsAndConditioningPlansValidateCleanly() {
        // 1. Run (3d) + Strength (2d) across 8 weeks
        val runStrength = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = monday,
            weeks = 8,
            days = setOf(1, 2, 3, 4, 6),
            runDays = 3,
            strengthDays = 2,
            longDay = 6,
            qualityCount = 0,
            weeklyKm = 24.0,
            longestKm = 10.0,
            targetWeeklyKm = 24.0,
            minutes = 60,
            dailyMinutes = 90,
            timeBudgetEnabled = false,
            equipment = fullEquipment,
            disciplineGoals = mapOf("run" to "Improve 10K", "str" to "General strength"),
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val rsPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            runStrength, owner, emptyList(), "comb-run-str", now, exerciseLibrary = library
        )
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, rsPlan.policyId)
        assertEquals(40, rsPlan.sessions.size)
        assertEquals(24, rsPlan.sessions.count { it.discipline == "run" })
        assertEquals(16, rsPlan.sessions.count { it.discipline == "str" })
        verifyRoundTrip(rsPlan)

        // 2. Run (3d) + Calisthenics (2d) with allowDoubleDays = true
        val runCal = runStrength.copy(
            days = setOf(1, 2, 4, 6),
            strengthDays = 0,
            calDays = 2,
            allowDoubleDays = true,
            disciplineGoals = mapOf("run" to "Aerobic foundation", "cal" to "Calisthenics foundation")
        )
        val rcPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            runCal, owner, emptyList(), "comb-run-cal", now, exerciseLibrary = library
        )
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, rcPlan.policyId)
        assertEquals(40, rcPlan.sessions.size)
        verifyRoundTrip(rcPlan)

        // 3. Run (3d) + Conditioning (2d) across 8 weeks
        val runCond = runStrength.copy(
            days = setOf(1, 2, 3, 4, 6),
            strengthDays = 0,
            conditioningDays = 2,
            disciplineGoals = mapOf("run" to "Half marathon", "cond" to "Conditioning")
        )
        val rcondPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            runCond, owner, emptyList(), "comb-run-cond", now, exerciseLibrary = library
        )
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, rcondPlan.policyId)
        assertEquals(40, rcondPlan.sessions.size)
        verifyRoundTrip(rcondPlan)
    }

    @Test fun fullFourDisciplineCombinedPlanCoordinatesFiveWeeklySessionsAndRejectsImpossibleFourHardSessionSpacing() {
        val allFour = PlanRequest(
            goal = "Aerobic foundation",
            experience = 1,
            start = monday,
            weeks = 8,
            days = setOf(1, 2, 3, 4, 5, 6, 7),
            runDays = 2,
            strengthDays = 1,
            calDays = 1,
            conditioningDays = 1,
            longDay = 6,
            allowDoubleDays = true,
            qualityCount = 0,
            weeklyKm = 16.0,
            longestKm = 10.0,
            targetWeeklyKm = 16.0,
            minutes = 60,
            dailyMinutes = 90,
            timeBudgetEnabled = false,
            equipment = fullEquipment,
            disciplineGoals = mapOf(
                "run" to "Aerobic foundation",
                "str" to "General strength",
                "cal" to "Calisthenics foundation",
                "cond" to "Conditioning"
            ),
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            allFour, owner, emptyList(), "comb-all-four", now, exerciseLibrary = library
        )
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, plan.policyId)
        assertEquals(40, plan.sessions.size)
        assertEquals(setOf("run", "str", "cal", "cond"), plan.sessions.map { it.discipline }.toSet())
        verifyRoundTrip(plan)

        // Adding a 3rd run introduces a long run (4th hard session in a 7-day cycle),
        // which cannot satisfy >= 2-day hard-session spacing and must fail closed.
        val fourHardSessions = allFour.copy(runDays = 3, weeklyKm = 24.0, targetWeeklyKm = 24.0)
        val conflict = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(
                fourHardSessions, owner, emptyList(), "comb-four-hard", now, exerciseLibrary = library
            )
        }
        assertTrue(conflict.message.orEmpty().contains("ENGINE15_COMBINED_SCHEDULE_CONFLICT"))
    }
}

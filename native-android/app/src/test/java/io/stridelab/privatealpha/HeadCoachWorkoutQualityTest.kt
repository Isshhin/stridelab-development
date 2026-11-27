package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HeadCoachWorkoutQualityTest {

    private val owner = "head-coach-qa-tester"
    private val now = Instant.parse("2026-11-27T08:00:00Z")
    private val monday = LocalDate.of(2026, 11, 30)
    private val fullGym = mapOf("db" to 1, "bb" to 1, "rack" to 1, "bench" to 1, "cable" to 1, "machine" to 1, "kb" to 1, "bands" to 1, "bar" to 1, "rings" to 1, "wall" to 1)
    private val library by lazy {
        ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
    }

    @Test
    fun strengthPplKeepsPushPullAnatomyCleanAndUpperLowerRotatesLeadLiftsAndVariants() {
        val catalogById = Engine16StrengthExerciseCatalog.allMetadata(library = null).associateBy { it.id }

        // 3d PPL Hypertrophy
        val pplPlan = Engine16StrengthProgramBuilder.build(
            request = PlanRequest(
                goal = "Muscle development",
                start = monday,
                weeks = 4,
                minutes = 75,
                days = setOf(1, 3, 5),
                runDays = 0,
                strengthDays = 3,
                strengthExperience = 1,
                equipment = fullGym,
                policyChoice = "engine15"
            ),
            owner = owner,
            id = "hc-ppl-3d",
            now = now
        )
        val week1Ppl = pplPlan.snapshot.sessions.take(3)
        val push = week1Ppl.first { it.title.startsWith("Push") }
        val pull = week1Ppl.first { it.title.startsWith("Pull") }
        assertTrue(
            "Push day must not contain biceps/forearm or rear-delt isolation",
            push.exercises.none { ex ->
                catalogById[ex.id]?.pattern in setOf(
                    StrengthPattern.BICEPS_FOREARM_ISOLATION,
                    StrengthPattern.REAR_DELT_UPPER_BACK
                )
            }
        )
        assertTrue(
            "Pull day must not contain triceps or front/lateral delt isolation",
            pull.exercises.none { ex ->
                catalogById[ex.id]?.pattern in setOf(
                    StrengthPattern.TRICEPS_ISOLATION,
                    StrengthPattern.LATERAL_FRONT_DELT
                )
            }
        )

        // 4d Upper / Lower A-B
        val ulPlan = Engine16StrengthProgramBuilder.build(
            request = PlanRequest(
                goal = "Muscle development",
                start = monday,
                weeks = 12,
                minutes = 75,
                days = setOf(1, 2, 4, 5),
                runDays = 0,
                strengthDays = 4,
                strengthExperience = 1,
                equipment = fullGym,
                policyChoice = "engine15"
            ),
            owner = owner,
            id = "hc-ul-12w",
            now = now
        )
        Engine16StrengthProgramBuilder.validateSaved(ulPlan.snapshot)
        val w1 = ulPlan.snapshot.sessions.take(4)
        val upperA = w1.first { it.title.startsWith("Upper A") }
        val upperB = w1.first { it.title.startsWith("Upper B") }
        val lowerA = w1.first { it.title.startsWith("Lower A") }
        val lowerB = w1.first { it.title.startsWith("Lower B") }

        assertNotEquals("Upper A and Upper B should rotate exercises", upperA.exercises.map { it.id }, upperB.exercises.map { it.id })
        assertNotEquals("Lower A and Lower B should rotate exercises", lowerA.exercises.map { it.id }, lowerB.exercises.map { it.id })
        assertEquals(StrengthPattern.HINGE, catalogById[lowerB.exercises.first().id]?.pattern)
        assertEquals(StrengthPattern.SQUAT, catalogById[lowerA.exercises.first().id]?.pattern)
        assertTrue(lowerA.steps.first().contains("lower-body prep"))
        assertTrue(upperA.steps.first().contains("upper-body prep") || upperA.steps.first().contains("upper"))

        // Execution-gated block progression (P1 fix): unexecuted Block 2 resets cleanly to Block 1 baseline instead of blindly inflating
        val w1UpperA = ulPlan.snapshot.sessions[0]
        val w2UpperA = ulPlan.snapshot.sessions[1 * 4]
        val w4UpperA = ulPlan.snapshot.sessions[3 * 4]
        val w5UpperA = ulPlan.snapshot.sessions[4 * 4]
        val w6UpperA = ulPlan.snapshot.sessions[5 * 4]
        val w1Work = w1UpperA.exercises.sumOf { it.sets * (it.reps ?: 1) }
        val w2Work = w2UpperA.exercises.sumOf { it.sets * (it.reps ?: 1) }
        val w4Work = w4UpperA.exercises.sumOf { it.sets * (it.reps ?: 1) }
        val w5Work = w5UpperA.exercises.sumOf { it.sets * (it.reps ?: 1) }
        val w6Work = w6UpperA.exercises.sumOf { it.sets * (it.reps ?: 1) }
        assertEquals("Expected Block 2 Week 5 baseline ($w5Work) to match Block 1 Week 1 ($w1Work) before execution receipts", w1Work, w5Work)
        assertEquals("Expected Block 2 Week 6 baseline ($w6Work) to match Block 1 Week 2 ($w2Work) before execution receipts", w2Work, w6Work)
        assertTrue("Expected Week 4 recovery deload ($w4Work) < Week 1 baseline ($w1Work)", w4Work < w1Work)
    }

    @Test
    fun calisthenicsUpperLowerHasZeroLegsOnUpperMasteryAndRotatesAbStimulus() {
        val catalogById = Engine17CalisthenicsDagCatalog.buildNodes().associateBy { it.exercise.id }
        val calPlan = Engine17CalisthenicsProgramBuilder.build(
            request = PlanRequest(
                goal = "Calisthenics foundation",
                start = monday,
                weeks = 8,
                days = (1..7).toSet(),
                runDays = 0,
                calDays = 4,
                calLevel = 2,
                pullLevel = 2,
                coreLevel = 2,
                minutes = 55,
                equipment = fullGym,
                policyChoice = "engine15"
            ),
            owner = owner,
            id = "hc-cal-4d",
            now = now
        )
        Engine17CalisthenicsProgramBuilder.validateSaved(calPlan.snapshot)
        val w1 = calPlan.snapshot.sessions.take(4)
        val upperA = w1[0]
        val lowerA = w1[1]
        val upperB = w1[2]
        val lowerB = w1[3]

        assertFalse("Upper Mastery A must not be marked lower-body", upperA.lower)
        assertFalse("Upper Mastery B must not be marked lower-body", upperB.lower)
        assertTrue("Upper Mastery A must have zero leg exercises", upperA.exercises.none { catalogById[it.id]?.exercise?.isLowerBody == true })
        assertTrue("Upper Mastery B must have zero leg exercises", upperB.exercises.none { catalogById[it.id]?.exercise?.isLowerBody == true })
        assertNotEquals("Upper A and Upper B should rotate calisthenics exercises", upperA.exercises.map { it.id }, upperB.exercises.map { it.id })
        assertNotEquals("Lower A and Lower B should rotate calisthenics exercises", lowerA.exercises.map { it.id }, lowerB.exercises.map { it.id })
    }

    @Test
    fun conditioningSelectsEquipmentOverStepJacksAndBuildsMultiStationWorkouts() {
        val equip = mapOf("erg" to 1, "kettlebells" to 1, "dumbbells" to 1, "sled" to 1, "ropes" to 1)
        val hiitPlan = Engine18ConditioningWorkoutConstructor.buildSoloPlan(
            PlanRequest(
                goal = "Modality HIIT intervals",
                start = monday,
                weeks = 4,
                days = setOf(1, 4),
                runDays = 0,
                conditioningDays = 2,
                experience = 1,
                minutes = 45,
                equipment = equip,
                policyChoice = "engine15"
            ),
            owner, "hc-cond-hiit", now
        )
        Engine18ConditioningWorkoutConstructor.validateSavedSolo(hiitPlan)
        assertFalse("HIIT with erg equipment should not prescribe step jacks", hiitPlan.sessions.first().exercises.any { it.id == "cond-step-jack" })

        val circuitPlan = Engine18ConditioningWorkoutConstructor.buildSoloPlan(
            PlanRequest(
                goal = "Structured circuits",
                start = monday,
                weeks = 4,
                days = setOf(1, 3, 5),
                runDays = 0,
                conditioningDays = 2,
                experience = 1,
                minutes = 45,
                equipment = equip,
                policyChoice = "engine15"
            ),
            owner, "hc-cond-circuit", now
        )
        Engine18ConditioningWorkoutConstructor.validateSavedSolo(circuitPlan)
        assertTrue("Structured circuit should contain >= 2 stations", circuitPlan.sessions.first().exercises.size >= 2)
    }

    @Test
    fun hybridStrengthPlusCalisthenicsNeverMentionsRunningFirstAndRunHybridCoordinatesTaper() {
        val strCalReq = PlanRequest(
            goal = "General strength",
            start = monday,
            weeks = 4,
            days = (1..7).toSet(),
            runDays = 0,
            strengthDays = 3,
            calDays = 2,
            strengthExperience = 1,
            calLevel = 1,
            pullLevel = 1,
            coreLevel = 1,
            equipment = fullGym,
            disciplineGoals = mapOf("str" to "General strength", "cal" to "Handstand foundations"),
            policyChoice = "engine15"
        )
        val strCalPlan = Engine18HybridProgramBuilder.build(strCalReq, owner, "hc-hyb-str-cal", now, emptyList(), library)
        Engine18HybridProgramBuilder.validateSaved(strCalPlan)
        assertTrue(
            "Non-running hybrid must never instruct prioritizing running",
            strCalPlan.sessions.none { it.reason.contains("prioritize running first", ignoreCase = true) }
        )

        val runStrReq = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            strengthExperience = 1,
            start = monday,
            weeks = 6,
            days = (1..7).toSet(),
            runDays = 3,
            strengthDays = 2,
            longDay = 6,
            qualityCount = 1,
            weeklyKm = 25.0,
            longestKm = 10.0,
            targetWeeklyKm = 34.0,
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            timeBudgetEnabled = false,
            equipment = fullGym,
            disciplineGoals = mapOf("run" to "Improve 10K", "str" to "Strength for runners"),
            policyChoice = "engine15"
        )
        val runStrPlan = Engine18HybridProgramBuilder.build(runStrReq, owner, "hc-hyb-run-str", now, emptyList(), library)
        Engine18HybridProgramBuilder.validateSaved(runStrPlan)
    }
}

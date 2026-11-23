package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15Phase3Track6Test {
    private val monday = LocalDate.of(2026, 11, 16)
    private val now = Instant.parse("2026-11-16T12:00:00Z")

    private fun confirmedZeroRequest(
        weeks: Int = 8,
        runDays: Int = 3,
        timeBudgetEnabled: Boolean = false,
        minutes: Int = 45
    ) = PlanRequest(
        goal = "Build running habit",
        experience = 0,
        start = monday,
        weeks = weeks,
        days = setOf(1, 3, 5, 7),
        runDays = runDays,
        longDay = 7,
        qualityCount = 0,
        weeklyKm = 0.0,
        longestKm = 0.0,
        targetWeeklyKm = null,
        timeBudgetEnabled = timeBudgetEnabled,
        minutes = minutes,
        dailyMinutes = minutes,
        policyChoice = "engine15",
        recentMileageBasis = "CONFIRMED_ZERO"
    )

    @Test
    fun confirmedZeroBeginnerGeneratesTrueTimeOnlyRunWalkPlanWithZeroFabricatedKm() {
        val req = confirmedZeroRequest(weeks = 8, runDays = 3)
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, "owner", emptyList(), "rw-plan", now
        )
        assertEquals(24, plan.sessions.size)
        assertTrue(plan.sessions.all { it.distanceKm == null && !it.quality })
        assertTrue(plan.sessions.all {
            val rx = it.runningPrescription!!
            rx.mixedSegments != null && rx.estimatedDistanceKm == null &&
                rx.reconciledDistanceMeters == null &&
                rx.mixedSegments.all { step -> step.seconds != null && step.meters == null }
        })
        // Week 4 recovery total minutes should be below Week 3 build total
        val w3Min = plan.sessions.filter { it.date >= monday.plusWeeks(2) && it.date < monday.plusWeeks(3) }.sumOf { it.minutes }
        val w4Min = plan.sessions.filter { it.date >= monday.plusWeeks(3) && it.date < monday.plusWeeks(4) }.sumOf { it.minutes }
        assertTrue("Week 4 ($w4Min min) should step back below Week 3 ($w3Min min)", w4Min < w3Min)

        // Validates cleanly and round-trips v4 RunningPrescription
        Engine15IndependentPlanGenerator.validateSaved(plan)
        val decoded = PlanSnapshot.decode(plan.encode(), "owner")
        assertEquals(plan, decoded)
    }

    @Test
    fun timeOnlyRunWalkRespectsTimeBudgetLocksAndDifficultyScaling() {
        val lockId = "rw-locked-w1-run0"
        val req = confirmedZeroRequest(weeks = 4, runDays = 3, timeBudgetEnabled = true, minutes = 25)
            .copy(lockedSessionDays = mapOf(lockId to monday.plusDays(6).toString()))
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, "owner", emptyList(), "rw-locked", now
        )
        assertTrue(plan.sessions.all { it.minutes <= 25 })
        assertTrue(plan.sessions.any { it.id == lockId && it.date == monday.plusDays(6) })

        val target = plan.sessions.first { it.kind == "easy" }
        val harder = PlanOperations.adjustDifficulty(plan, target.id, +2)
        val adjustedSession = harder.sessions.single { it.id == target.id }
        assertNull(adjustedSession.distanceKm)
        assertTrue(adjustedSession.minutes >= target.minutes)
        assertEquals(2, adjustedSession.difficultyStep)
        Engine15IndependentPlanGenerator.validateSaved(harder)

        val reset = PlanOperations.adjustDifficulty(harder, target.id, 0)
        assertEquals(target, reset.sessions.single { it.id == target.id })
        Engine15IndependentPlanGenerator.validateSaved(reset)
    }

    @Test
    fun confirmedZeroWithExplicitZeroTargetKmStillThrowsConstraint() {
        val req = confirmedZeroRequest(weeks = 4, runDays = 3).copy(targetWeeklyKm = 0.0)
        assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(req, "owner", emptyList(), "bad-zero", now)
        }
    }

    @Test
    fun allStrengthAndCalisthenicsMovementsHaveEasierAndHarderSwaps() {
        val ids = listOf(
            "e1", "gs", "sq", "e6", "rl", "sit-to-stand",
            "e3", "dh", "hg", "glute-bridge",
            "fp", "bp", "bb-bench", "e4", "ip", "wall-push",
            "dr", "bb-row", "br", "cable-latpulldown", "e2", "ap", "supported-hang", "supported-scapular", "prone-w",
            "e5", "fc", "plank", "hollow", "hollow-tuck", "core-hollow", "deadbug", "bird-dog", "side-plank", "pike-support"
        )
        for (id in ids) {
            val ex = ExercisePrescription(id, "Movement $id", 3, 10, null, 60, "Bodyweight", "Form cue")
            val easier = ExerciseAlternatives.easier(ex)
            val harder = ExerciseAlternatives.harder(ex)
            assertNotNull("Expected easier option for $id", easier)
            assertNotNull("Expected harder option for $id", harder)
            assertEquals("swap-$id-regression", easier!!.id)
            assertEquals("swap-$id-progression", harder!!.id)
            assertNull(ExerciseAlternatives.easier(easier))
            assertNull(ExerciseAlternatives.harder(harder))
        }
    }

    @Test
    fun harderExerciseSwapPersistsGuardsHistoryAndRoundTrips() {
        val ex = ExercisePrescription("gs", "Goblet squat", 3, 10, null, 60, "Dumbbell", "Sit back.")
        val session = PlannedSession(
            id = "s1", date = monday, discipline = "str", title = "Lower Strength",
            minutes = 35, distanceKm = null, kind = "strength", exercises = listOf(ex)
        )
        val plan = PlanSnapshot(
            id = "p-str", owner = "owner", engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_STRENGTH_POLICY, createdAt = now,
            request = PlanRequest(goal = "Lower-body strength", runDays = 0, strengthDays = 2, policyChoice = "engine15"),
            sessions = listOf(session), explanations = emptyList(), status = "approved"
        )
        val swapped = PlanOperations.swapExercise(plan, "s1", "gs", now, ExerciseAlternatives.SwapDirection.HARDER)
        val updated = swapped.sessions.single()
        assertEquals(1, updated.swaps.size)
        assertEquals("swap-gs-progression", updated.effectiveExercises.single().id)
        assertEquals(ExerciseAlternatives.HARDER_REASON, updated.swaps.single().reason)
        assertEquals(swapped, PlanSnapshot.decode(swapped.encode(), "owner"))

        assertThrows(PlanConstraint::class.java) {
            PlanOperations.swapExercise(swapped, "s1", "gs", now, ExerciseAlternatives.SwapDirection.EASIER)
        }
    }

    @Test
    fun resistanceDifficultyAdjustmentAddsRirCuesAndResetsCleanly() {
        val ex = ExercisePrescription("gs", "Goblet squat", 3, 10, null, 60, "Dumbbell", "Chest tall.")
        val session = PlannedSession(
            id = "s-rir", date = monday, discipline = "str", title = "Lower Strength",
            minutes = 35, distanceKm = null, kind = "strength", exercises = listOf(ex)
        )
        val req = PlanRequest(goal = "Lower-body strength", runDays = 0, strengthDays = 2, policyChoice = "engine15")
        val plan = PlanSnapshot(
            id = "p-rir", owner = "owner", engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_STRENGTH_POLICY, createdAt = now,
            request = req, sessions = listOf(session), explanations = emptyList(), status = "draft"
        )

        val easier = PlanOperations.adjustDifficulty(plan, "s-rir", -2).sessions.single()
        assertTrue(easier.exercises.single().cue.contains("4+ reps in reserve"))
        val harder = PlanOperations.adjustDifficulty(plan, "s-rir", +2).sessions.single()
        assertTrue(harder.exercises.single().cue.contains("1 rep in reserve"))
        val reset = Engine15WorkoutDifficultyAdjuster.adjust(harder, req, 0)
        assertEquals("Chest tall.", reset.exercises.single().cue)
    }
}

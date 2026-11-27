package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StrengthCalisthenicsProgressionTest {
    private fun exercise(
        id: String = "gs",
        name: String = "Goblet squat",
        sets: Int = 3,
        reps: Int? = 8,
        seconds: Int? = null,
        equipment: String = "Dumbbell"
    ) = ExercisePrescription(id, name, sets, reps, seconds, 75, equipment, "Controlled tempo.")

    @Test fun sessionOneUsesEffortAnchoredGuidanceWithZeroGuessedLoads() {
        val proj = StrengthProgressionProjector.project(exercise(), "str", evidence = null)
        assertTrue(proj.isSessionOne)
        assertEquals(ProgressionDecision.SESSION_ONE_EFFORT, proj.decision)
        assertTrue(proj.options.isEmpty())
        assertNull(proj.previousSummary)
        assertEquals("3 × 8–10 @ 2–3 RIR", proj.summary)
    }

    @Test fun epleyE1RmIsBoundedStrictlyToThreeToTenRepsAndOptionalRir() {
        assertNull(StrengthProgressionProjector.estimateE1Rm(60.0, 2))
        assertNull(StrengthProgressionProjector.estimateE1Rm(40.0, 11))
        assertNull(StrengthProgressionProjector.estimateE1Rm(0.0, 8))

        val e1rm = StrengthProgressionProjector.estimateE1Rm(25.0, 8)
        assertNotNull(e1rm)
        assertEquals(25.0 * (1.0 + 8.0 / 30.0), e1rm!!, 1e-6)

        val withRir = StrengthProgressionProjector.estimateE1Rm(25.0, 6, rir = 2)
        assertEquals(25.0 * (1.0 + 8.0 / 30.0), withRir!!, 1e-6)

        assertEquals(27.5, StrengthProgressionProjector.snapWeight(26.9, 2.5), 1e-6)
        assertEquals(60.0, StrengthProgressionProjector.snapWeight(58.0, 5.0), 1e-6)
    }

    @Test fun doubleProgressionClimbsBumpsAndHoldsDeterministically() {
        val squat = exercise(sets = 3, reps = 8) // repRange 8..10
        val date = LocalDate.of(2026, 11, 20)

        // Climb when sets hit 8, 9, 8
        val climb = StrengthProgressionProjector.project(
            squat, "str",
            ExerciseSessionEvidence(
                "gs", date, listOf(
                    CompletedSetEvidence(reps = 8, kg = 25.0),
                    CompletedSetEvidence(reps = 9, kg = 25.0),
                    CompletedSetEvidence(reps = 8, kg = 25.0)
                )
            )
        )
        assertEquals(ProgressionDecision.CLIMB_REPS, climb.decision)
        assertTrue(climb.options.first().label.startsWith("25 kg × 9"))
        assertTrue(climb.options.size == 2)
        assertEquals("Prev: 25 kg × 9", climb.previousSummary)

        // Bump load + reset reps when all working sets hit top (10) cleanly
        val bump = StrengthProgressionProjector.project(
            squat, "str",
            ExerciseSessionEvidence(
                "gs", date, listOf(
                    CompletedSetEvidence(reps = 10, kg = 25.0, rir = 2),
                    CompletedSetEvidence(reps = 10, kg = 25.0, rir = 2),
                    CompletedSetEvidence(reps = 10, kg = 25.0, rir = 1)
                )
            )
        )
        assertEquals(ProgressionDecision.BUMP_LOAD, bump.decision)
        assertEquals("27.5 kg × 8", bump.options.first().label)

        // Hold load when athlete misses bottom of rep range or hits 0 RIR
        val holdOnZeroRir = StrengthProgressionProjector.project(
            squat, "str",
            ExerciseSessionEvidence(
                "gs", date, listOf(
                    CompletedSetEvidence(reps = 10, kg = 25.0, rir = 0),
                    CompletedSetEvidence(reps = 10, kg = 25.0, rir = 1),
                    CompletedSetEvidence(reps = 10, kg = 25.0, rir = 1)
                )
            )
        )
        assertEquals(ProgressionDecision.HOLD_TARGET, holdOnZeroRir.decision)
        assertEquals(25.0, holdOnZeroRir.options.first().load, 1e-6)
    }

    @Test fun calisthenicsAndHoldCeilingTriggersOptInUpgradeSuggestionOnlyWhenClean() {
        val pushup = exercise(id = "e4", name = "Push-up", sets = 3, reps = 10, equipment = "Bodyweight")
        val date = LocalDate.of(2026, 11, 20)

        val tooShort = CalisthenicsProgressionProjector.suggestUpgrade(
            pushup, "cal",
            ExerciseSessionEvidence(
                "e4", date, listOf(
                    CompletedSetEvidence(reps = 10),
                    CompletedSetEvidence(reps = 9),
                    CompletedSetEvidence(reps = 10)
                )
            )
        )
        assertNull(tooShort)

        val pushupReady = CalisthenicsProgressionProjector.suggestUpgrade(
            pushup, "cal",
            ExerciseSessionEvidence(
                "e4", date, listOf(
                    CompletedSetEvidence(reps = 11),
                    CompletedSetEvidence(reps = 10),
                    CompletedSetEvidence(reps = 10, rir = 2)
                )
            )
        )
        assertNotNull(pushupReady)
        assertEquals("swap-e4-progression", pushupReady!!.suggestedExercise.id)

        val plank = exercise(id = "plank", name = "Forearm plank", sets = 3, reps = null, seconds = 30, equipment = "Mat / floor")
        val holdReady = CalisthenicsProgressionProjector.suggestUpgrade(
            plank, "str",
            ExerciseSessionEvidence(
                "plank", date, listOf(
                    CompletedSetEvidence(seconds = 45),
                    CompletedSetEvidence(seconds = 50),
                    CompletedSetEvidence(seconds = 45)
                )
            )
        )
        assertNotNull(holdReady)
        assertEquals("swap-plank-progression", holdReady!!.suggestedExercise.id)
        assertEquals(ExerciseMetricType.HOLD_SECONDS, plank.metricType("str"))
    }

    @Test fun exerciseHistoryIsKeyedStrictlyByExerciseIdAcrossSwaps() {
        val owner = "athlete-local"
        val date = LocalDate.of(2026, 11, 18)
        val squat = exercise(id = "gs", name = "Goblet squat", sets = 2, reps = 8)
        val swappedRdl = exercise(id = "dh", name = "Romanian deadlift", sets = 2, reps = 8)
        val swapSession = PlannedSession(
            id = "s1", date = date, discipline = "str", title = "Lower", minutes = 35, distanceKm = null, kind = "strength",
            exercises = listOf(squat),
            status = "completed",
            swaps = listOf(ExerciseSwap("gs", swappedRdl, "reason", Instant.parse("2026-11-18T10:00:00Z"))),
            log = WorkoutLog(
                at = Instant.parse("2026-11-18T11:00:00Z"),
                performedOn = date,
                sets = listOf(
                    SetRecord("dh", 1, reps = 8, kg = 60.0),
                    SetRecord("dh", 2, reps = 8, kg = 60.0)
                )
            )
        )
        val plan = PlanSnapshot(
            id = "p1", owner = owner, engineVersion = ENGINE15_VERSION, policyId = ENGINE15_POLICY,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            request = PlanRequest(goal = "General strength", strengthDays = 2, runDays = 0, days = setOf(1, 4)),
            sessions = listOf(swapSession), explanations = emptyList(), status = "approved"
        )
        assertNull(ExerciseProgressionHistoryReader.latestForExercise("gs", owner, listOf(plan)))
        val dhEvidence = ExerciseProgressionHistoryReader.latestForExercise("dh", owner, listOf(plan))
        assertNotNull(dhEvidence)
        assertEquals(60.0, dhEvidence!!.sets.first().kg!!, 1e-6)
    }

    @Test fun freeformCatalogBuiltinsHaveStandardizedNonEmptyDisjointMusclesAndMetricTypes() {
        val augmented = FreeformExerciseCatalog.augment(emptyList())
        assertTrue(augmented.size >= 140)
        val byId = augmented.associateBy { it.id }
        for (record in augmented) {
            assertTrue("${record.id} missing primaryMuscles", record.primaryMuscles.isNotEmpty())
            assertTrue(
                "${record.id} primary/secondary overlap",
                record.primaryMuscles.intersect(record.secondaryMuscles.toSet()).isEmpty()
            )
            assertTrue(
                "${record.id} has non-standard primary muscle",
                record.primaryMuscles.all { it in ExerciseMuscleCatalog.STANDARD_MUSCLES }
            )
            assertTrue(
                "${record.id} has non-standard secondary muscle",
                record.secondaryMuscles.all { it in ExerciseMuscleCatalog.STANDARD_MUSCLES }
            )
        }
        assertEquals(ExerciseMetricType.HOLD_SECONDS, byId.getValue("iso-wall-sit").metricType())
        assertEquals(ExerciseMetricType.HOLD_SECONDS, byId.getValue("iso-dead-hang").metricType())
        assertEquals(ExerciseMetricType.WEIGHT_AND_HOLD, byId.getValue("iso-farmer-hold").metricType())
        assertEquals(ExerciseMetricType.WEIGHT_AND_HOLD, byId.getValue("str-weighted-plank").metricType())
        assertEquals(ExerciseMetricType.REPS, byId.getValue("plyo-box-jump").metricType())
        assertEquals(ExerciseMetricType.REPS, byId.getValue("cal-chinup").metricType())
        assertEquals(ExerciseMetricType.REPS, byId.getValue("mob-90-90-switches").metricType())
        assertEquals(ExerciseMetricType.WEIGHT_AND_REPS, byId.getValue("str-bb-bench-press").metricType())
    }
}

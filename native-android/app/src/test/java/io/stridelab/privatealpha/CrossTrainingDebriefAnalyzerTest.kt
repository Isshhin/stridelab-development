package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class CrossTrainingDebriefAnalyzerTest {
    private val date = LocalDate.of(2026, 11, 23)
    private val now = Instant.parse("2026-11-23T08:00:00Z")

    private fun session(
        title: String, discipline: String, exercise: ExercisePrescription,
        log: WorkoutLog, quality: Boolean = false
    ) = PlannedSession("session", date, discipline, title, 20, null, "structured-$discipline",
        lower = discipline == "str", quality = quality, exercises = listOf(exercise), status = "completed", log = log)

    private fun plan(session: PlannedSession, id: String = "plan", owner: String = "owner", status: String = "approved") =
        PlanSnapshot(id, owner, ENGINE15_VERSION, ENGINE15_POLICY, now,
            PlanRequest(goal = session.title), listOf(session), emptyList(), status)

    private fun review(session: PlannedSession, plans: List<PlanSnapshot> = emptyList()) =
        Engine15DebriefEngine.workout(plan(session), session.id, plans, zone = ZoneOffset.UTC)

    private fun log(exercise: String, count: Int, reps: Int? = null, seconds: Int? = null, effort: Int? = null) =
        WorkoutLog(now, effort = effort, sets = (1..count).map { SetRecord(exercise, it, reps = reps, seconds = seconds) },
            performedOn = date)

    private fun hardRun(owner: String = "owner", completed: Boolean = true, actualDate: LocalDate = date.minusDays(1)) =
        plan(PlannedSession("run", date.minusDays(4), "run", "Speed", 40, 7.0, "quality", quality = true,
            status = if (completed) "completed" else "planned",
            log = if (completed) WorkoutLog(now.minusSeconds(86400), performedOn = actualDate) else null),
            "run-plan", owner)

    @Test fun strengthChecksRepsEffortAndActualHardRunSpacing() {
        val workout = session("Strength for Runners", "str", ExercisePrescription("squat", "Squat", 12, reps = 8),
            log("squat", 12, reps = 8, effort = 8))
        val result = requireNotNull(review(workout, listOf(hardRun())))
        assertEquals(DebriefTone.CAUTION, result.tone)
        assertEquals(DebriefCategory.LOAD_AND_RECOVERY, result.insights.first().category)
        val dose = result.insights.single { it.category == DebriefCategory.EXECUTION }
        assertEquals(DebriefTone.POSITIVE, dose.tone)
        assertTrue(dose.body.contains("12 sets"))
        assertTrue(result.insights.any { it.tone == DebriefTone.CAUTION && it.body.contains("8/10") })
    }

    @Test fun explicitRunnerRirUsesOnlyOneUnambiguousNoteEntry() {
        val workout = session("Strength for Runners", "str", ExercisePrescription("squat", "Squat", 3, reps = 8),
            log("squat", 3, reps = 8).copy(notes = "RIR: 1"))
        val insight = requireNotNull(review(workout)).insights.first()
        assertEquals(DebriefCategory.LOAD_AND_RECOVERY, insight.category)
        assertEquals(DebriefTone.CAUTION, insight.tone)
        assertTrue(insight.body.contains("1 rep"))
        val ambiguous = requireNotNull(review(workout.copy(log = workout.log?.copy(notes = "RIR: 1\nRIR: 3"))))
        assertEquals(DebriefCategory.EXECUTION, ambiguous.insights.single().category)
    }

    @Test fun plyometricsChecksCapAndFlagsPriorSpeedWork() {
        val workout = session("Plyometrics", "str", ExercisePrescription("pogo-hop", "Hop", 3, reps = 4),
            log("pogo-hop", 3, reps = 5))
        val result = requireNotNull(review(workout, listOf(hardRun())))
        assertEquals(2, result.insights.size)
        assertTrue(result.insights.all { it.tone == DebriefTone.CAUTION })
        val contacts = result.insights.first { it.category == DebriefCategory.EXECUTION }
        assertTrue(contacts.body.contains("15 ground contacts") && contacts.body.contains("12"))
        val safe = requireNotNull(review(workout.copy(log = log("pogo-hop", 3, reps = 4))))
        assertEquals(DebriefTone.POSITIVE, safe.tone)
        assertNull(review(workout.copy(exercises = listOf(ExercisePrescription("landing-rehearsal", "Rehearse", 3, reps = 4)),
            log = log("landing-rehearsal", 3, reps = 4))))
    }

    @Test fun isometricsUsesHoldSecondsAndRecordedEffort() {
        val workout = session("Isometrics", "str", ExercisePrescription("hold", "Wall sit", 3, seconds = 30),
            log("hold", 3, seconds = 30, effort = 5))
        val result = requireNotNull(review(workout))
        assertEquals(2, result.insights.size)
        assertTrue(result.insights.all { it.tone == DebriefTone.POSITIVE })
        assertTrue(result.insights.first { it.category == DebriefCategory.EXECUTION }.body.contains("1:30"))
        assertNull(review(workout.copy(log = log("hold", 3, reps = 8))))
    }

    @Test fun calisthenicsDistinguishesPartialEvidenceFromShortSets() {
        val workout = session("Push", "cal", ExercisePrescription("push", "Push up", 3, reps = 8),
            log("push", 3, reps = 6))
        val short = requireNotNull(review(workout))
        assertEquals(DebriefCategory.EXECUTION, short.insights.single().category)
        assertEquals(DebriefTone.CAUTION, short.tone)
        assertEquals(DebriefTone.NEUTRAL, review(workout.copy(log = log("push", 1, reps = 8)))?.tone)
    }

    @Test fun conditioningFormatsCheckTimedRoundsAndIntensity() {
        for (goal in listOf("EMOM work capacity", "AMRAP work capacity", "Threshold intervals")) {
            val workout = session(goal, "cond", ExercisePrescription("march", "March", 3, seconds = 45),
                log("march", 3, seconds = 45, effort = 9), quality = true)
            val result = requireNotNull(review(workout))
            val dose = result.insights.single { it.category == DebriefCategory.EXECUTION }
            assertEquals(DebriefTone.POSITIVE, dose.tone)
            assertTrue(dose.body.contains("2:15"))
            val effort = result.insights.single { it.category == DebriefCategory.LOAD_AND_RECOVERY }
            assertEquals(DebriefTone.CAUTION, effort.tone)
            assertTrue(effort.body.contains("9/10"))
        }
    }

    @Test fun missingOrUnrelatedLogsAndLinksAreOmitted() {
        val workout = session("Strength for Runners", "str", ExercisePrescription("squat", "Squat", 3, reps = 8),
            WorkoutLog(now, minutes = 20.0, effort = 7, performedOn = date))
        assertNull(review(workout, listOf(hardRun())))
        val complete = workout.copy(log = log("squat", 3, reps = 8), linkedActivityId = "recording")
        val result = requireNotNull(review(complete, listOf(hardRun(owner = "other"), hardRun(completed = false),
            hardRun(actualDate = date.minusDays(3)))))
        assertEquals(DebriefCategory.EXECUTION, result.insights.single().category)
        val plans = listOf(plan(complete))
        assertNull(Engine15DebriefEngine.linkedWorkout(setOf("unrelated"), "owner", plans, emptyList(), ZoneOffset.UTC))
        assertNotNull(Engine15DebriefEngine.linkedWorkout(setOf("recording"), "owner", plans, emptyList(), ZoneOffset.UTC))
    }
}

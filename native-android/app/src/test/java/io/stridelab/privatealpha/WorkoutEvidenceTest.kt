package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorkoutEvidenceTest {
    private val owner = "evidence-owner"
    private val monday = LocalDate.of(2026, 11, 23)
    private val zone = ZoneId.of("Asia/Manila")

    @Test fun confirmedCoachLinkAndConsolidatedGarminAliasesProduceOneReadOnlyEntry() {
        val at = Instant.parse("2026-11-23T01:00:00Z")
        val primary = activity("garmin-primary", at)
        val alternate = activity("garmin-alternate", at)
        val session = PlannedSession("coached", monday, "run", "Coached run", 45, 7.0, "easy",
            status = "completed", log = WorkoutLog(at, minutes = 40.0, distanceKm = 6.5, effort = 7),
            linkedActivityId = alternate.id)
        val plan = PlanSnapshot("original-plan", owner, ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY,
            at, PlanRequest(start = monday), listOf(session), emptyList(), status = "approved")
        val original = plan.encode().toString()
        val rows = projectWorkoutEvidence(
            owner, listOf(plan, plan.copy(owner = "other-owner")), null,
            listOf(DisplayActivity(primary, listOf(alternate)), DisplayActivity(alternate)), zone
        )
        val entry = rows.single()
        val link = entry.link as WorkoutEvidenceLink.Imported
        assertEquals(listOf(primary.id, alternate.id), link.recordingIds)
        assertEquals(listOf(WorkoutEvidenceLink.Coached(plan.id, session.id)), link.coached)
        assertEquals(setOf(WorkoutEvidenceSource.GARMIN_IMPORTED, WorkoutEvidenceSource.ENGINE15), entry.sources)
        assertEquals(6.0, entry.distanceKm)
        assertEquals(7, entry.effort)
        assertEquals(original, plan.encode().toString())
    }

    @Test fun onlyCompletedInScopeManualWorkoutsContributeConfirmedSets() {
        val exercise = FreeformExercise(name = "Squat", sets = listOf(
            FreeformSet(actual = FreeformMeasure(quantity = "8", kg = "10"), rpe = "7", rir = "2", completed = true),
            FreeformSet(actual = FreeformMeasure(quantity = "14", kg = "50"))
        ))
        val draft = FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, monday, 100)
            .copy(exercises = listOf(exercise))
        val workout = draft.finish(101)
        val other = workout.copy(id = "other-id", owner = "other-owner")
        val draftOnly = draft.copy(id = "ongoing")
        val board = FreeformBoard(workouts = listOf(workout, draftOnly, other))
        val row = projectWorkoutEvidence(owner, emptyList(), board, emptyList(), zone).single()
        assertEquals(listOf("1 set", "8 reps", "80 kg volume", "RPE 7/10", "RIR 2"),
            evidenceMetrics(listOf(row), ManualDiscipline.STRENGTH))
        assertEquals(1, exerciseComparisons(listOf(row)).size)
        assertNull(exerciseComparisons(listOf(row)).single().previous)
    }

    @Test fun strengthHoldContactsAndConditioningKeepSeparateUnits() {
        val holds = row("hold", monday, ManualDiscipline.ISOMETRICS).copy(
            movements = listOf(ConfirmedMovement("Plank", listOf(ConfirmedWorkoutSet(seconds = 45, kg = 5.0))))
        )
        val hops = row("hops", monday, ManualDiscipline.PLYOMETRICS).copy(
            movements = listOf(ConfirmedMovement("Hop", listOf(ConfirmedWorkoutSet(quantity = 18))))
        )
        val intervals = row("interval", monday, ManualDiscipline.CONDITIONING).copy(
            durationMinutes = 12.0, movements = listOf(ConfirmedMovement("Row", listOf(ConfirmedWorkoutSet(seconds = 60))))
        )
        assertEquals(listOf("1 set", "45 s held", "5 kg max"), holds.detail.split(" · "))
        assertTrue(hops.detail.contains("18 contacts"))
        assertEquals(listOf("12 min recorded", "1 round", "1 min work"), intervals.detail.split(" · "))
        assertThrows(IllegalStateException::class.java) {
            evidenceMetrics(listOf(holds, hops), ManualDiscipline.ISOMETRICS)
        }
    }

    @Test fun baselineUsesCompletedWeeksWithoutImputingDistanceOrTreatingCurrentWeekAsComplete() {
        val rows = listOf(
            row("last-week", monday.minusWeeks(1), distance = 8.0),
            row("two-weeks", monday.minusWeeks(2), distance = 10.0),
            row("missing-distance", monday.minusWeeks(3)),
            row("this-week", monday, distance = 16.0),
            row("future", monday.plusDays(4), distance = 25.0)
        )
        assertEquals(HistoryBaselineSuggestion(9.0, 16.0, 2, 1), suggestedHistoryBaseline(rows, monday.plusDays(1)))
        val one = suggestedHistoryBaseline(listOf(rows.first()), monday)
        assertNull(one?.weeklyKm)
        assertEquals(8.0, one?.longestKm)
        assertNull(suggestedHistoryBaseline(emptyList(), monday))
    }

    private fun row(id: String, date: LocalDate, discipline: ManualDiscipline = ManualDiscipline.RUNNING,
        distance: Double? = null) = WorkoutEvidence(id, WorkoutEvidenceSource.FREEFORM,
        WorkoutEvidenceLink.Manual(id), date, 0L, id, discipline, distanceKm = distance)

    private fun activity(id: String, at: Instant) = ActivityRecord(
        id, "Garmin run", "running", "garmin", at, 6000.0, 2400.0, null, null, null, null
    )
}

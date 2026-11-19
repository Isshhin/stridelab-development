package io.stridelab.privatealpha

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunCoachNoteTest {
    private val run = ActivityRecord("current", "Run", "run", "garmin",
        Instant.parse("2026-11-20T07:00:00Z"), 5000.0, 1800.0, null, 360.0, null, null)
    private val workout = ActivityWorkoutAssessment(
        ActivityWorkoutEvidence(ActivityWorkoutKind.EASY_RUN, evidence = listOf("Fixture")))

    @Test fun dayContextOmitsLaterAndPreviousDayRunsAndDeduplicates() {
        val prior = run.copy(id = "prior", startedAt = run.startedAt.minusSeconds(3600))
        val later = prior.copy(id = "later", startedAt = run.startedAt.plusSeconds(3600))
        val yesterday = prior.copy(id = "yesterday", startedAt = run.startedAt.minusSeconds(86400))
        assertEquals("2 runs that day up to this one, covering 10.0 km in total.",
            runCoachNote(run, null, listOf(prior, prior, later, yesterday), workout, ZoneOffset.UTC)
                .dayContext)
        assertNull(runCoachNote(run, null, listOf(later, yesterday), workout, ZoneOffset.UTC).dayContext)
    }
}

package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ActivityEffortAnalysisTest {
    private val currentDate = LocalDate.parse("2026-11-17")
    private val currentTime = Instant.parse("2026-11-17T06:00:00Z")

    private fun activity(id: String, day: Int, distanceKm: Double = 8.0) = ActivityRecord(
        id, "Run", "run", "garmin", currentTime.minusSeconds((17 - day) * 86400L),
        distanceKm * 1000.0, 45.0 * 60, 44.0 * 60, 337.5, 160.0, null
    )

    private fun plan(owner: String, id: String, kind: String, day: Int, rpe: Int?) = PlanSnapshot(
        id, owner, ENGINE15_VERSION, ENGINE15_POLICY, currentTime.minusSeconds((17 - day) * 86400L),
        PlanRequest(policyChoice = "engine15"),
        listOf(PlannedSession(
            id = "session-$id", date = currentDate.minusDays((17 - day).toLong()),
            discipline = "run", title = kind, minutes = 45, distanceKm = 8.0,
            kind = kind, status = "completed", linkedActivityId = "activity-$id",
            logRevision = 1, log = WorkoutLog(
                at = currentTime.minusSeconds((17 - day) * 86400L), distanceKm = 8.0,
                effort = rpe, performedOn = currentDate.minusDays((17 - day).toLong())
            )
        )), emptyList(), status = "approved"
    )

    @Test fun sessionEffortUsesUserReportAndComparesOnlyRepeatedComparablePlans() {
        val current = activity("activity-current", 17)
        val plans = listOf(
            plan("owner", "one", "easy", 3, 3),
            plan("owner", "two", "easy", 7, 4),
            plan("owner", "three", "easy", 11, 5),
            plan("owner", "current", "easy", 17, 7),
            plan("other-owner", "foreign", "easy", 12, 10),
            plan("owner", "quality", "quality", 13, 10)
        )
        val history = plans.mapNotNull { it.sessions.single().linkedActivityId }
            .filter { it != "activity-current" }
            .mapIndexed { index, id -> activity(id, 3 + index * 2) }

        val result = ActivityEffortAnalysis.assess(
            current, null, setOf("activity-current"), plans, history,
            setOf("owner"), history.mapTo(mutableSetOf()) { it.id } + current.id
        )

        assertEquals(7, result.reportedEffort)
        assertEquals(3, result.comparison?.sessionCount)
        assertEquals(4.0, result.comparison?.medianReportedEffort ?: -1.0, 0.0)
        assertNull(result.heartRateZones)
    }

    @Test fun averageHeartRateAloneDoesNotBecomeAnEffortScore() {
        val result = ActivityEffortAnalysis.assess(
            activity("no-effort", 17), null, emptySet(), emptyList(), emptyList(),
            setOf("owner"), setOf("activity-no-effort")
        )

        assertNull(result.reportedEffort)
        assertNull(result.heartRateZones)
        assertNull(result.comparison)
        assertEquals(ActivityEffortConfidence.INSUFFICIENT, result.confidence)
    }

    @Test fun verifiedAthleteZonesProduceOnlyAnApproximateDistribution() {
        val run = activity("zone-run", 17)
        val detail = ActivityDetail(run.id, listOf(
            ActivitySample(0, 0.0, 330.0, 160.0, null, null, null),
            ActivitySample(1, 20.0, 330.0, 164.0, null, null, null),
            ActivitySample(2, 40.0, 330.0, 170.0, null, null, null)
        ), emptyList(), 0L)
        val settings = AthleteHeartRateZoneSettings(maximumHr = 200)

        val result = ActivityEffortAnalysis.assess(
            run, detail, setOf(run.id), emptyList(), emptyList(), setOf("owner"),
            setOf(run.id), settings
        )

        assertEquals(40.0, result.heartRateZones?.classifiedSeconds ?: -1.0, 0.0)
        assertEquals(ActivityEffortConfidence.RECORDED_HR_WITHOUT_GAPS, result.confidence)
    }
}

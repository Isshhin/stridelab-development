package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedTrainingTimelineTest {
    private val manila = ZoneId.of("Asia/Manila")
    private val today = LocalDate.parse("2026-11-06")

    private fun activity(id: String, instant: String) = ActivityRecord(
        id = id, title = "Actual source record", type = "run",
        source = "garmin", startedAt = Instant.parse(instant),
        distanceM = 5000.0, durationS = 1800.0,
        movingTimeS = 1700.0, recordedPace = null,
        averageHr = null, elevationM = null
    )

    private fun session(
        id: String,
        date: String,
        status: String = "planned",
        linked: String? = null,
        performed: String? = null
    ) = PlannedSession(
        id = id, date = LocalDate.parse(date), discipline = "run",
        title = "Actual planned session", minutes = 45,
        distanceKm = 8.0, kind = "easy", status = status,
        linkedActivityId = linked,
        log = performed?.let {
            WorkoutLog(at = Instant.parse("2026-11-06T10:00:00Z"),
                performedOn = LocalDate.parse(it))
        }
    )

    private fun plan(
        status: String = "approved",
        sessions: List<PlannedSession>
    ) = PlanSnapshot(
        id = "plan-1", owner = "athlete",
        engineVersion = "test", policyId = "test",
        createdAt = Instant.parse("2026-11-01T00:00:00Z"),
        request = PlanRequest(start = today, weeks = 4),
        sessions = sessions, explanations = emptyList(), status = status
    )

    @Test fun noPlanOrActivityStillHasCurrentDateAnchor() {
        val days = unifiedTrainingDays(today, emptyList(), emptyList(), null, manila)
        assertEquals(listOf(today), days.map { it.date })
        assertTrue(days.single().activities.isEmpty())
        assertTrue(days.single().workouts.isEmpty())
    }

    @Test fun historyIsAboveTodayAndUpcomingSessionsBelowItWithoutBlankDays() {
        val sessions = listOf(
            session("future", "2026-11-09"),
            session("later", "2026-12-01")
        )
        val groups = listOf(DisplayActivity(activity("past", "2026-11-04T10:00:00Z")))
        val days = unifiedTrainingDays(
            today, groups, listOf(plan(sessions = sessions)), "plan-1", manila
        )
        assertEquals(
            listOf("2026-11-04", "2026-11-06", "2026-11-09", "2026-12-01"),
            days.map { it.date.toString() }
        )
    }

    @Test fun deviceTimezoneDeterminesTheRecordedDate() {
        // 18:00 UTC November 5 is 02:00 in Manila on November 6.
        val days = unifiedTrainingDays(
            today,
            listOf(DisplayActivity(activity("today-run", "2026-11-05T18:00:00Z"))),
            emptyList(), null, manila
        )
        assertEquals(1, days.size)
        assertEquals("today-run", days.single().activities.single().primary.id)
    }

    @Test fun linkedSourceIdentitySuppressesDuplicateCompletedPlanCard() {
        val primary = activity("garmin-owned", "2026-11-05T18:00:00Z")
        val alias = activity("garmin-legacy", "2026-11-05T18:00:01Z")
        val days = unifiedTrainingDays(
            today,
            listOf(DisplayActivity(primary, listOf(alias))),
            listOf(plan(sessions = listOf(
                session("linked", "2026-11-06", "completed", linked = alias.id),
                session("independent", "2026-11-06", "completed")
            ))),
            "plan-1", manila
        )
        assertEquals(1, days.single().activities.size)
        assertEquals(listOf("independent"),
            days.single().workouts.map { it.session.id })
    }

    @Test fun actualPerformedDateWinsOverScheduledDateForLoggedSession() {
        val days = unifiedTrainingDays(
            today, emptyList(),
            listOf(plan(sessions = listOf(
                session("completed", "2026-11-03", status = "completed",
                    performed = "2026-11-05")
            ))),
            "plan-1", manila
        )
        assertEquals(listOf("2026-11-05", "2026-11-06"),
            days.map { it.date.toString() })
    }

    @Test fun draftDoesNotInventUpcomingWorkoutsButPreservesRealLogs() {
        val days = unifiedTrainingDays(
            today, emptyList(),
            listOf(plan(status = "draft", sessions = listOf(
                session("draft-only", "2026-11-07"),
                session("logged", "2026-11-04", status = "completed")
            ))),
            null, manila
        )
        assertEquals(listOf("2026-11-04", "2026-11-06"),
            days.map { it.date.toString() })
        assertEquals("logged", days.first().workouts.single().session.id)
    }

    @Test fun completelyEmptyTodayDoesNotClaimMonthHeader() {
        val days = unifiedTrainingDays(today, emptyList(), emptyList(), null, manila)
        assertEquals(false, unifiedTimelineMonthHeading(days, 0))
    }

    @Test fun firstActualWorkoutAfterEmptyTodayIntroducesItsMonth() {
        val days = unifiedTrainingDays(
            today, emptyList(),
            listOf(plan(sessions = listOf(session("upcoming", "2026-11-07")))),
            "plan-1", manila
        )
        assertEquals(false, unifiedTimelineMonthHeading(days, 0))
        assertEquals(true, unifiedTimelineMonthHeading(days, 1))
    }

    @Test fun monthHeadingAppearsOnlyAtFirstEventInEachMonth() {
        val days = unifiedTrainingDays(
            today, emptyList(),
            listOf(plan(sessions = listOf(
                session("first", "2026-11-07"),
                session("second", "2026-11-09"),
                session("dec", "2026-12-02")
            ))), "plan-1", manila
        )
        assertEquals(listOf(false, true, false, true),
            days.indices.map { unifiedTimelineMonthHeading(days, it) })
    }

    @Test fun recordedMetricsUseDistanceAsPrimaryAndPlaceHeartRateWithPace() {
        val run = activity("run", "2026-11-06T06:00:00Z").copy(
            averageHr = 148.4, recordedPace = 390.0
        )
        val (primary, secondary) = timelineRecordedMetrics(run)
        assertEquals("5.00 km", primary)
        assertEquals("30:00 · 6:30 /km · 148 bpm", secondary)
    }

    @Test fun fallbackMovingPaceUsesCompactDisplayWithoutMovingPrefix() {
        val run = activity("run", "2026-11-06T06:00:00Z").copy(averageHr = 153.6)
        val (_, secondary) = timelineRecordedMetrics(run)
        assertEquals("30:00 · 5:40 /km · 154 bpm", secondary)
    }

    @Test fun durationBecomesPrimaryWhenDistanceIsMissing() {
        val strength = activity("strength", "2026-11-06T06:00:00Z").copy(
            type = "strength", distanceM = null, averageHr = 124.8
        )
        val (primary, secondary) = timelineRecordedMetrics(strength)
        assertEquals("30:00", primary)
        assertEquals("125 bpm", secondary)
    }

    @Test fun unavailableHeartRateIsOmittedRatherThanEstimated() {
        val run = activity("run", "2026-11-06T06:00:00Z").copy(
            averageHr = null, recordedPace = 360.0
        )
        val (_, secondary) = timelineRecordedMetrics(run)
        assertEquals("30:00 · 6:00 /km", secondary)
    }

    @Test fun missingDistanceAndDurationShowNoInventedNumbers() {
        val saved = activity("strength", "2026-11-06T06:00:00Z").copy(
            type = "strength", distanceM = null, durationS = null,
            movingTimeS = null, averageHr = null
        )
        assertEquals("Recorded workout" to "", timelineRecordedMetrics(saved))
    }

    @Test fun twoDistinctRecordedRunsRemainSeparateOnSameDate() {
        val groups = listOf(
            DisplayActivity(activity("run1", "2026-11-06T06:00:00Z")),
            DisplayActivity(activity("run2", "2026-11-06T09:00:00Z"))
        )
        val days = unifiedTrainingDays(today, groups, emptyList(), null, manila)
        assertEquals(listOf("run1", "run2"),
            days.single().activities.map { it.primary.id })
    }

    @Test fun concurrentPlansPreserveLocalIdCollisionsWithOffsetCalendarsAndRecordingAliases() {
        val primary = activity("primary", "2026-11-06T06:00:00Z")
        val fit = primary.copy(id = "fit-alias", source = "fit")
        val manual = primary.copy(id = "manual-alias", source = "manual")
        val running = plan(sessions = listOf(
            session("shared", "2026-11-06"),
            session("linked", "2026-11-06", status = "completed", linked = fit.id)
        )).copy(id = "running")
        val strength = plan(sessions = listOf(
            session("shared", "2026-11-06").copy(discipline = "str", title = "Isometrics"),
            session("other", "2026-11-27").copy(discipline = "str", title = "Plyometrics")
        )).copy(id = "support", request = running.request.copy(start = today.minusWeeks(2), weeks = 6))
        val groups = listOf(DisplayActivity(primary, listOf(fit, manual)))
        val plans = listOf(running, strength)
        val days = unifiedTrainingDays(today, groups, plans, null, manila, plans.map { it.id }.toSet())
        val selected = unifiedTrainingSelectedDay(today, days.first(), plans, setOf(primary.id, fit.id, manual.id))
        assertEquals(listOf(running.id to "shared", strength.id to "shared"), selected.workouts.map { it.planId to it.session.id })
        assertEquals(listOf("run", "iso"), selected.workouts.map { trainingTrack(it.session) })
        assertEquals("primary", selected.activities.single().primary.id)
        assertEquals("plyo", trainingTrack(days.last().workouts.single().session))
        assertEquals(2, tracksideTrainingWeek(plans, today).single { it.date == today }.sessions.count { it.id == "shared" })
        val filtered = unifiedTrainingDays(today, groups, listOf(strength), strength.id, manila)
        assertEquals("support", filtered.first().workouts.single().planId)
    }
}

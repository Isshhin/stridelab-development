package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TodayDisciplineAnalyticsTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val now = Instant.parse("2026-11-10T04:00:00Z")
    private fun activity(id: String, day: String, type: String = "running",
        distance: Double? = 5000.0, seconds: Double? = 1800.0,
        source: String = "garmin"
    ) = ActivityRecord(
        id, id, type, source, Instant.parse("${day}T01:00:00Z"),
        distance, seconds, null, null, null, null
    )

    private fun plan(vararg sessions: PlannedSession): PlanSnapshot {
        val request = PlanRequest(start = LocalDate.parse("2026-10-26"), weeks = 4)
        return PlanSnapshot("plan", "synthetic-owner", ENGINE1_VERSION, ENGINE1_POLICY,
            now, request, sessions.toList(), emptyList(), status = "approved")
    }

    private fun native(id: String, performed: String, discipline: String = "str",
        minutes: Double? = 35.0, distanceKm: Double? = null,
        linked: String? = null
    ): PlannedSession = PlannedSession(
        id, LocalDate.parse(performed), discipline, "Confirmed ${discipline}",
        40, null, "practice", status = "completed",
        log = WorkoutLog(now, minutes = minutes, distanceKm = distanceKm,
            performedOn = LocalDate.parse(performed)),
        linkedActivityId = linked
    )

    @Test fun unlockRequiresThreeDistinctDatesInEachOfLastTwoCompletedWeeks() {
        val first = listOf("2026-10-26", "2026-10-28", "2026-11-01")
        val second = listOf("2026-11-02", "2026-11-04", "2026-11-08")
        val rows = (first + second).mapIndexed { i, day ->
            activity("run-$i", day)
        } + activity("extra-on-same-day", "2026-11-02")
        assertEquals(listOf(TodayDiscipline.RUNNING),
            TodayDisciplineAnalytics.available(rows, emptyList(), now, zone))
        assertTrue(TodayDisciplineAnalytics.available(
            rows.filterNot { it.id == "run-5" }, emptyList(), now, zone
        ).isEmpty())
    }

    @Test fun currentPartialWeekAndUnclassifiedWorkoutsDoNotUnlockTabs() {
        val thisWeek = listOf("2026-11-09", "2026-11-10")
            .mapIndexed { i, day -> activity("current-$i", day) }
        val unknown = listOf("2026-10-26", "2026-10-28", "2026-11-01",
            "2026-11-02", "2026-11-04", "2026-11-08")
            .mapIndexed { i, day -> activity("generic-$i", day, "training") }
        assertTrue(TodayDisciplineAnalytics.available(
            thisWeek + unknown, emptyList(), now, zone
        ).isEmpty())
        assertNull(TodayDiscipline.fromActivity("walking"))
        assertNull(TodayDiscipline.fromActivity("training"))
        assertEquals(TodayDiscipline.CALISTHENICS,
            TodayDiscipline.fromActivity("bodyweight_training"))
    }

    @Test fun confirmedUnlinkedLogsCountButLinkedSourcesAreNotDuplicated() {
        val sessions = listOf("2026-10-26", "2026-10-28", "2026-11-01",
            "2026-11-02", "2026-11-04", "2026-11-08")
            .mapIndexed { i, day -> native("str-$i", day) }
        val strength = TodayDisciplineAnalytics.available(
            emptyList(), listOf(plan(*sessions.toTypedArray())), now, zone
        )
        assertEquals(listOf(TodayDiscipline.STRENGTH), strength)
        val source = activity("linked-run", "2026-11-02", distance = 4200.0)
        val linked = plan(native("linked", "2026-11-02", "run", 40.0,
            88.0, linked = "linked-run"))
        val week = TodayDisciplineAnalytics.week(listOf(source), listOf(linked),
            now, zone, LocalDate.parse("2026-11-02"))
            .getValue(TodayDiscipline.RUNNING)
        assertEquals(1, week.sessions)
        assertEquals(4200.0, week.recordedDistanceM!!, 0.001)
        assertEquals(1, week.activeDays)
    }

    @Test fun partialMetricsRemainMissingButActiveDaysStillCount() {
        val rows = listOf(
            activity("a", "2026-11-02", distance = null, seconds = null),
            activity("b", "2026-11-04", distance = 2500.0, seconds = 900.0),
            activity("c", "2026-11-04", distance = null, seconds = null)
        )
        val week = TodayDisciplineAnalytics.week(rows, emptyList(), now, zone,
            LocalDate.parse("2026-11-03")).getValue(TodayDiscipline.RUNNING)
        assertEquals(3, week.sessions)
        assertEquals(2, week.activeDays)
        assertEquals(2, week.missingDistance)
        assertEquals(2, week.missingDuration)
        assertEquals(2500.0, week.recordedDistanceM!!, 0.0)
        assertEquals(900.0, week.recordedSeconds!!, 0.0)
        assertEquals(listOf(1,0,2,0,0,0,0), week.sessionsByDay)
        assertNull(week.averageRecordedHr)
    }

    @Test fun timezoneAndWeekBoundaryUseLocalDatesNotUtcDurations() {
        val utcBoundary = ActivityRecord(
            "boundary", "boundary", "run", "garmin",
            Instant.parse("2026-11-01T16:30:00Z"), 1000.0, 600.0,
            null, null, null, null
        )
        val week = TodayDisciplineAnalytics.week(
            listOf(utcBoundary), emptyList(), now, zone,
            LocalDate.parse("2026-11-02")
        ).getValue(TodayDiscipline.RUNNING)
        assertEquals(1, week.sessions)
        assertEquals(1, week.sessionsByDay[0])
        assertEquals(LocalDate.parse("2026-11-02"), week.start)
    }

    @Test fun trendsKeepMissingWeeksUnreportedInsteadOfClaimingZeroTraining() {
        val weeks = TodayDisciplineAnalytics.trend(
            emptyList(), emptyList(), now, zone,
            LocalDate.parse("2026-11-10"), TodayDiscipline.RUNNING
        )
        assertEquals(8, weeks.size)
        assertEquals(LocalDate.parse("2026-09-21"), weeks.first().start)
        assertEquals(LocalDate.parse("2026-11-09"), weeks.last().start)
        assertTrue(weeks.all { it.recordedDistanceM == null &&
            it.recordedSeconds == null && it.sessions == 0 })
    }
}

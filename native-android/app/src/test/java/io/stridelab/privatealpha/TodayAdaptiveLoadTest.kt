package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayAdaptiveLoadTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val zone = ZoneOffset.UTC
    private val now = monday.plusDays(6).atTime(20, 0).toInstant(zone)

    private fun activePlan(id: String, session: PlannedSession) = PlanSnapshot(
        id, "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY,
        Instant.EPOCH, PlanRequest(start = monday), listOf(session), emptyList(), status = "approved"
    )

    @Test fun independentPlansAggregateAndFilterWithoutCollidingOnSessionIdentity() {
        val str = PlannedSession("same", monday, "str", "Training", 35, null, "strength", status = "completed")
        val run = PlannedSession("same", monday, "run", "Training", 35, 6.0, "easy")
        val plans = listOf(activePlan("strength", str), activePlan("running", run))
        fun weekly(filter: TodayDiscipline?) = TodayOverviewCalculator.computeWeeklyOverview(
            monday, emptyList(), emptyList(), plans, now, zone, discipline = filter
        ).adherence!!
        assertEquals(2, weekly(null).plannedSessions)
        assertEquals(1, weekly(null).completedSessions)
        assertEquals(1, weekly(TodayDiscipline.RUNNING).plannedSessions)
        assertEquals(0, weekly(TodayDiscipline.RUNNING).completedSessions)
        assertEquals(1, weekly(TodayDiscipline.STRENGTH).completedSessions)
        val day = TodayOverviewCalculator.buildDailyTrainingState(
            monday, emptyList(), emptyList(), emptyList(), plans
        ) as TodayDailyTrainingState.Sessions
        assertEquals(listOf("running" to "same", "strength" to "same"),
            day.items.filterIsInstance<TodayDailySessionItem.Planned>().map { it.key })
    }

    @Test fun canonicalRecordingGetsPhysicalCreditOnceAndAmbiguousClaimsGetNeitherPrescriptionCredit() {
        val old = runAct("legacy", 0, 6.0, 35.0)
        val canonical = old.copy(id = "garmin-${"a".repeat(64)}")
        val history = listOf(old, canonical)
        val run = PlannedSession("same", monday, "run", "Run", 35, 6.0, "easy",
            status = "completed", linkedActivityId = old.id,
            log = WorkoutLog(now, minutes = 35.0, distanceKm = 6.0, performedOn = monday))
        val first = activePlan("running", run)
        val second = activePlan("another", run.copy(linkedActivityId = canonical.id))
        fun weekly(plans: List<PlanSnapshot>) = TodayOverviewCalculator.computeWeeklyOverview(
            monday, history, history, plans, now, zone
        )
        assertEquals(1, weekly(listOf(first)).adherence!!.completedSessions)
        val overview = weekly(listOf(first, second))
        assertEquals(1, overview.runCount)
        assertEquals(6.0, overview.runKm, 0.0)
        assertEquals(2, overview.adherence!!.plannedSessions)
        assertEquals(0, overview.adherence.completedSessions)
        val day = TodayOverviewCalculator.buildDailyTrainingState(
            monday, history, emptyList(), history, listOf(first, second)
        ) as TodayDailyTrainingState.Sessions
        assertEquals(2, day.items.filterIsInstance<TodayDailySessionItem.Planned>().size)
        assertEquals(canonical.id, day.items.filterIsInstance<TodayDailySessionItem.Recorded>().single().activity.id)
    }

    private fun runAct(id: String, dayOffset: Long, distKm: Double, durMin: Double, hr: Double = 148.0) =
        ActivityRecord(
            id = id,
            title = "Running",
            type = "running",
            source = "garmin",
            startedAt = monday.plusDays(dayOffset).atTime(7, 0).toInstant(zone),
            distanceM = distKm * 1000.0,
            durationS = durMin * 60.0,
            movingTimeS = durMin * 60.0,
            recordedPace = (durMin * 60.0) / distKm,
            averageHr = hr,
            elevationM = 120.0
        )

    private fun gymAct(id: String, type: String, dayOffset: Long, durMin: Double) =
        ActivityRecord(
            id = id,
            title = type,
            type = type,
            source = "manual",
            startedAt = monday.plusDays(dayOffset).atTime(17, 0).toInstant(zone),
            distanceM = null,
            durationS = durMin * 60.0,
            movingTimeS = null,
            recordedPace = null,
            averageHr = 128.0,
            elevationM = null
        )

    @Test
    fun verifiedAdherenceSeparatesUnlinkedBonusActivitiesFromPlannedSessions() {
        val activities = listOf(
            runAct("r1", 0, 10.2, 56.0),
            runAct("bonus-1", 1, 6.0, 34.0),
            runAct("bonus-2", 3, 5.0, 29.0)
        )
        val req = PlanRequest(goal = "Half marathon", start = monday, weeks = 4, policyChoice = "engine15")
        val planSessions = listOf(
            PlannedSession(
                id = "s-mon", date = monday, discipline = "run", title = "Easy Aerobic",
                minutes = 55, distanceKm = 10.0, kind = "easy", status = "completed", linkedActivityId = "r1"
            ),
            PlannedSession(
                id = "s-wed", date = monday.plusDays(2), discipline = "run", title = "Threshold Run",
                minutes = 60, distanceKm = 12.0, kind = "tempo", quality = true,
                status = "completed",
                log = WorkoutLog(at = monday.plusDays(2).atTime(8, 0).toInstant(zone), performedOn = monday.plusDays(2), minutes = 60.0, distanceKm = 12.0)
            ),
            PlannedSession(
                id = "s-fri", date = monday.plusDays(4), discipline = "run", title = "Steady Run",
                minutes = 50, distanceKm = 9.0, kind = "easy", status = "planned"
            ),
            PlannedSession(
                id = "s-sun", date = monday.plusDays(6), discipline = "run", title = "Long Run",
                minutes = 95, distanceKm = 16.0, kind = "long", status = "planned"
            )
        )
        val plan = PlanSnapshot(
            "plan-1", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY,
            monday.atStartOfDay().toInstant(zone), req, planSessions, emptyList(), status = "approved"
        )

        val overview = TodayOverviewCalculator.computeWeeklyOverview(
            monday, activities, activities, listOf(plan), now, zone
        )
        assertNotNull(overview.adherence)
        // Only the 2 explicitly verified sessions count toward Adherence, never unlinked bonus runs.
        assertEquals(2, overview.adherence!!.completedSessions)
        assertEquals(4, overview.adherence!!.plannedSessions)
        assertEquals(22.2, overview.adherence!!.completedPlannedRunKm, 0.01)
        assertEquals(47.0, overview.adherence!!.plannedRunKm, 0.01)
        // Weekly volume totals still reflect all 4 completed runs (3 imported + 1 native log).
        assertEquals(4, overview.runCount)
        assertEquals(33.2, overview.runKm, 0.01)
    }

    @Test
    fun noPlanCompanionStateOmitsAdherenceAndShowsVolumeAndNonRunningChips() {
        val activities = listOf(
            runAct("r1", 0, 8.0, 45.0),
            runAct("r2", 2, 6.2, 37.0),
            gymAct("s1", "strength", 1, 45.0),
            gymAct("s2", "weight_training", 3, 40.0),
            gymAct("c1", "calisthenics", 4, 30.0)
        )

        val overview = TodayOverviewCalculator.computeWeeklyOverview(
            monday, activities, activities, emptyList(), now, zone
        )
        assertNull(overview.adherence)
        assertEquals(2, overview.runCount)
        assertEquals(14.2, overview.runKm, 0.01)
        assertEquals(
            listOf(
                TodayDisciplineChip(TodayDiscipline.STRENGTH, 2),
                TodayDisciplineChip(TodayDiscipline.CALISTHENICS, 1)
            ),
            overview.nonRunningChips
        )

        val emptyDayState = TodayOverviewCalculator.buildDailyTrainingState(
            date = monday.plusDays(5),
            recordedOnDate = emptyList(),
            confirmedOnDate = emptyList(),
            rawHistory = activities,
            plans = emptyList()
        )
        assertTrue(emptyDayState is TodayDailyTrainingState.NoPlanCompanion)

        val activeDayState = TodayOverviewCalculator.buildDailyTrainingState(
            date = monday.plusDays(1),
            recordedOnDate = listOf(activities[2]),
            confirmedOnDate = emptyList(),
            rawHistory = activities,
            plans = emptyList()
        )
        assertTrue(activeDayState is TodayDailyTrainingState.Sessions)
        val sessionsState = activeDayState as TodayDailyTrainingState.Sessions
        assertFalse(sessionsState.hasActivePlan)
        assertTrue(sessionsState.items.single() is TodayDailySessionItem.Recorded)
    }

    @Test
    fun multisportDayTracksIndependentCompletionAndEmptyPlanDayShowsRestDay() {
        val req = PlanRequest(goal = "5k", start = monday, weeks = 4, policyChoice = "engine15")
        val sessions = listOf(
            PlannedSession("s-run", monday, "run", "Easy Run", 45, 8.0, "easy", status = "planned"),
            PlannedSession(
                "s-str", monday, "str", "Lower Body Strength", 40, null, "strength", true,
                status = "completed",
                log = WorkoutLog(at = monday.atTime(18, 0).toInstant(zone), performedOn = monday, minutes = 40.0)
            )
        )
        val plan = PlanSnapshot(
            "p1", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY,
            monday.atStartOfDay().toInstant(zone), req, sessions, emptyList(), status = "approved"
        )

        val mondayState = TodayOverviewCalculator.buildDailyTrainingState(
            date = monday,
            recordedOnDate = emptyList(),
            confirmedOnDate = listOf(ConfirmedWorkout("p1", "s-str", monday, "str", 2400.0, null)),
            rawHistory = emptyList(),
            plans = listOf(plan)
        ) as TodayDailyTrainingState.Sessions
        assertEquals(2, mondayState.items.size)
        val plannedItems = mondayState.items.filterIsInstance<TodayDailySessionItem.Planned>()
        assertEquals(2, plannedItems.size)
        assertFalse(plannedItems[0].isCompleted)
        assertTrue(plannedItems[1].isCompleted)

        val tuesdayRest = TodayOverviewCalculator.buildDailyTrainingState(
            date = monday.plusDays(1),
            recordedOnDate = emptyList(),
            confirmedOnDate = emptyList(),
            rawHistory = emptyList(),
            plans = listOf(plan)
        )
        assertTrue(tuesdayRest is TodayDailyTrainingState.RestDay)
    }

    @Test
    fun recentActivitiesDeduplicatesLinkedSessionsSortsDescendingAndRespectsLimit() {
        val activities = (0L..5L).map { idx -> runAct("r-$idx", idx, 7.0 + idx, 40.0 + idx) }
        val req = PlanRequest(goal = "10k", start = monday, weeks = 4, policyChoice = "engine15")
        val linkedSession = PlannedSession(
            id = "s-linked", date = monday, discipline = "run", title = "Easy Run",
            minutes = 40, distanceKm = 7.0, kind = "easy", status = "completed",
            linkedActivityId = "r-0",
            log = WorkoutLog(at = monday.atTime(8, 0).toInstant(zone), performedOn = monday, minutes = 40.0, distanceKm = 7.0)
        )
        val plan = PlanSnapshot(
            "p1", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY,
            monday.atStartOfDay().toInstant(zone), req, listOf(linkedSession), emptyList(), status = "approved"
        )

        val recent = TodayOverviewCalculator.buildRecentActivities(
            visibleActivities = activities,
            rawHistory = activities,
            plans = listOf(plan),
            limit = 5,
            now = now,
            zone = zone
        )
        assertEquals(5, recent.size)
        // Descending chronologically: r-5, r-4, r-3, r-2, r-1 (r-0 is dropped by limit, not duplicated by s-linked)
        assertEquals(listOf("act:r-5", "act:r-4", "act:r-3", "act:r-2", "act:r-1"), recent.map { it.id })
    }

    @Test
    fun formatRunHoursMinutesUsesClockNotation() {
        assertEquals("0:00", TodayOverviewCalculator.formatRunHoursMinutes(0.0))
        assertEquals("0:45", TodayOverviewCalculator.formatRunHoursMinutes(45 * 60.0))
        assertEquals("1:00", TodayOverviewCalculator.formatRunHoursMinutes(60 * 60.0))
        assertEquals("6:10", TodayOverviewCalculator.formatRunHoursMinutes((6 * 60 + 10) * 60.0))
    }
}

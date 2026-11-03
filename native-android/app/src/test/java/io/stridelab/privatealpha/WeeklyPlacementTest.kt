package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Regression contract for real, persisted day assignments in all policies.
 * Source only: do not execute tests or assemble APK under the current hold.
 */
class WeeklyPlacementTest {
    private val monday=LocalDate.parse("2026-11-02")
    private val now=Instant.parse("2026-10-30T00:00:00Z")
    private val owner="synthetic-owner"

    private fun running() = PlanRequest(
        start=monday, weeks=2, experience=2,
        goal="Improve 10K", weeklyKm=40.0, longestKm=14.0,
        runDays=4, days=(1..7).toSet(), longDay=7,
        qualityCount=1, qualityDays=setOf(2),
        weeklySlots=mapOf("run" to listOf(7,2,4,5))
    )

    @Test fun savedPlanRequestRoundTripsAllChosenWeekdays() {
        val original=running().copy(
            strengthDays=1,calDays=1,conditioningDays=1,
            weeklySlots=running().weeklySlots+
                mapOf("str" to listOf(1),"cal" to listOf(4),
                    "cond" to listOf(6))
        )
        val decoded=PlanRequest.decode(original.encode())
        assertEquals(original.weeklySlots,decoded.weeklySlots)
        assertEquals(original.days,decoded.days)
        assertEquals(original.longDay,decoded.longDay)
        assertEquals(original.qualityDays,decoded.qualityDays)
    }

    @Test fun legacyRequestWithoutAppointmentsStillDecodesAndAutoSchedules() {
        val original=PlanRequest(start=monday,runDays=3,
            days=setOf(1,2,4,5,6))
        val json=original.encode()
        assertFalse(json.has("weeklySlots"))
        val restored=PlanRequest.decode(json)
        assertTrue(restored.weeklySlots.isEmpty())
        assertEquals(
            Engine1.generate(original,owner,"legacy",now).sessions,
            Engine1.generate(restored,owner,"legacy",now).sessions
        )
    }

    @Test fun tappingNewDaySwapsSessionsAndNeverDropsAny() {
        val before=running()
        val swap=WeeklyPlacement.put(before,"run",2,5)
        assertEquals(listOf(7,2,5,4),swap.weeklySlots["run"])
        assertEquals(before.runDays,swap.runDays)
        assertEquals(before.longDay,swap.longDay)
        assertTrue(WeeklyPlacement.full(swap,"run"))
        assertNull(WeeklyPlacement.validationError(swap))
        assertEquals(listOf(7,2,4,5),before.weeklySlots["run"])
    }

    @Test fun saturdayCanBeSelectedEvenIfInitiallyMarkedUnavailable() {
        val p=running().copy(days=setOf(1,2,3,4,5,7))
        val changed=WeeklyPlacement.put(p,"run",3,6)
        assertTrue(6 in changed.days)
        assertEquals(6,changed.weeklySlots["run"]!![3])
    }

    @Test fun incompleteOrDuplicateAssignmentsFailClosedBeforeGeneration() {
        val cleared=WeeklyPlacement.clear(running(),"run",3)
        assertEquals(0,cleared.weeklySlots["run"]!![3])
        assertFalse(WeeklyPlacement.full(cleared,"run"))
        assertNotNull(WeeklyPlacement.validationError(cleared))
        assertThrows(PlanConstraint::class.java) {
            Engine1.generate(cleared,owner,"missing",now)
        }
        val duplicate=running().copy(
            weeklySlots=mapOf("run" to listOf(7,2,4,4))
        )
        assertNotNull(WeeklyPlacement.validationError(duplicate))
    }

    @Test fun exactRunPlacementsAppearOnEveryTrainingWeek() {
        val p=Engine1.generate(running(),owner,"fixed",now)
        assertEquals(8,p.sessions.size)
        for (week in 0 until 2) {
            val run=p.sessions.filter { it.id.contains("-w" + week + "-run") }
            assertEquals(4,run.size)
            for (i in 0..3) {
                val session=run.single { it.id.endsWith("-run"+i) }
                assertEquals(listOf(7,2,4,5)[i],
                    session.date.dayOfWeek.value)
            }
        }
    }

    @Test fun unsafePinnedRecoveryConflictIsReportedNotRescheduled() {
        val p=running().copy(
            longDay=4, qualityDays=setOf(3),
            weeklySlots=mapOf("run" to listOf(4,3,1,6))
        )
        val error=assertThrows(PlanConstraint::class.java) {
            Engine1.generate(p,owner,"unsafe",now)
        }
        assertTrue(error.message.orEmpty().contains("selected weekdays"))
    }

    @Test fun strengthAndCalisthenicsPinIndependently() {
        val req=PlanRequest(
            start=monday,weeks=2,goal="General fitness",
            days=(1..7).toSet(), runDays=0,strengthDays=2,
            calDays=1,weeklySlots=mapOf(
                "str" to listOf(1,4),"cal" to listOf(6)
            )
        )
        val plan=Engine1.generate(req,owner,"hybrid",now)
        assertEquals(6,plan.sessions.size)
        plan.sessions.filter { it.discipline=="str" }.forEach {
            assertTrue(it.date.dayOfWeek.value in setOf(1,4))
        }
        plan.sessions.filter { it.discipline=="cal" }.forEach {
            assertEquals(6,it.date.dayOfWeek.value)
        }
    }

    @Test fun compatibleSameDayEasyAndCaliRequestsStayOnChosenDay() {
        val req=PlanRequest(
            start=monday,weeks=1,policyChoice="multi-session",
            goal="Improve 5K",runDays=2,calDays=1,days=setOf(1,4,7),
            longDay=7,weeklyKm=12.0,longestKm=6.0,
            dailyMinutes=120,
            weeklySlots=mapOf("run" to listOf(7,4),"cal" to listOf(4))
        )
        val plan=MultiSessionEngine1.generate(req,owner,"paired",now)
        assertEquals(3,plan.sessions.size)
        val date=monday.plusDays(3)
        val sessions=plan.sessions.filter { it.date==date }
        assertEquals(2,sessions.size)
        assertEquals(setOf("run","cal"),sessions.map { it.discipline }.toSet())
        assertTrue(sessions.sumOf { it.minutes } <= 120)
    }

    @Test fun unsafeSameDayQualityAndCaliIsRejected() {
        val req=PlanRequest(
            start=monday,weeks=1,policyChoice="multi-session",
            goal="Improve 5K",runDays=3,calDays=1,
            days=setOf(1,2,5,7),longDay=7,
            experience=2,weeklyKm=30.0,longestKm=10.0,
            qualityCount=1,qualityDays=setOf(2),
            dailyMinutes=120,
            weeklySlots=mapOf(
                "run" to listOf(7,2,5),"cal" to listOf(2)
            )
        )
        assertThrows(PlanConstraint::class.java) {
            MultiSessionEngine1.generate(req,owner,"unsafe-pair",now)
        }
    }

    @Test fun disablingCustomSlotsRestoresCurrentAutomaticScheduling() {
        val automatic=running().copy(weeklySlots=emptyMap())
        val plan=Engine1.generate(automatic,owner,"auto",now)
        assertTrue(plan.sessions.isNotEmpty())
        assertEquals(automatic.days,plan.request.days)
    }
}

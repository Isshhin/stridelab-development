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
                    "cond" to listOf(6)),
            preferredQuality="Intervals",
            allowedQualityTypes=setOf("Threshold","Intervals","Hills")
        )
        val decoded=PlanRequest.decode(original.encode())
        assertEquals(original.weeklySlots,decoded.weeklySlots)
        assertEquals(original.days,decoded.days)
        assertEquals(original.longDay,decoded.longDay)
        assertEquals(original.qualityDays,decoded.qualityDays)
        assertEquals(original.preferredQuality,decoded.preferredQuality)
        assertEquals(original.allowedQualityTypes,decoded.allowedQualityTypes)
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

    @Test fun removingAnOccupiedTrainingDayKeepsSessionsAndRequestsReassignment() {
        val original=running().copy(
            strengthDays=1,
            weeklySlots=running().weeklySlots+("str" to listOf(7))
        )
        val changed=WeeklyPlacement.makeUnavailable(original,7)
        assertFalse(7 in changed.days)
        assertEquals(listOf(0,2,4,5),changed.weeklySlots["run"])
        assertEquals(listOf(0),changed.weeklySlots["str"])
        assertEquals(original.runDays,changed.runDays)
        assertEquals(original.strengthDays,changed.strengthDays)
        assertNotNull(WeeklyPlacement.validationError(changed))
        assertThrows(PlanConstraint::class.java) {
            Engine1.generate(changed,owner,"unassigned",now)
        }
        assertEquals(listOf(7,2,4,5),original.weeklySlots["run"])
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

    @Test fun reducingRunCountDropsExcessQualityBeforePreservedEasyDay() {
        val request = running().copy(
            qualityCount = 2, qualityDays = setOf(2,4),
            weeklySlots = mapOf("run" to listOf(7,2,4,5))
        )
        val changed = WeeklyPlacement.resize(request, "run", 3)
        assertEquals(3, changed.runDays)
        assertEquals(1, changed.qualityCount)
        assertEquals(listOf(7,2,5), changed.weeklySlots["run"])
        assertEquals(7, changed.longDay)
        assertEquals(setOf(2), changed.qualityDays)
        assertNull(WeeklyPlacement.validationError(changed))
    }

    @Test fun reducingFourRunsRetainsNonChronologicalSaturdayLongRun() {
        val request = running().copy(
            longDay = 6, weeklySlots = mapOf("run" to listOf(6,2,4,5))
        )
        val changed = WeeklyPlacement.resize(request, "run", 3)
        assertEquals(listOf(6,2,4), changed.weeklySlots["run"])
        assertEquals(6, changed.longDay)
        assertEquals(setOf(2), changed.qualityDays)
        assertNull(WeeklyPlacement.validationError(changed))
    }

    @Test fun aWednesdayStartRetainsEverySessionAcrossWholePlanWeekWindows() {
        val start=LocalDate.parse("2026-11-04")
        val request=running().copy(start=start,weeks=3)
        val plan=Engine1.generate(request,owner,"wednesday",now)
        assertEquals(12,plan.sessions.size)
        val groups=(0 until request.weeks).map { week ->
            val begin=start.plusWeeks(week.toLong())
            plan.sessions.filter { it.date in begin..begin.plusDays(6) }
        }
        assertEquals(listOf(4,4,4),groups.map { it.size })
        assertEquals(plan.sessions.toSet(),groups.flatten().toSet())
        assertTrue(groups.last().any { it.date.dayOfWeek.value <= 2 })
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

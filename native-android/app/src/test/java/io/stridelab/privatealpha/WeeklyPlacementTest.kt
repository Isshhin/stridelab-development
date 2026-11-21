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

    private val library = ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
    private fun generate(p: PlanRequest, id: String) =
        Engine15IndependentPlanGenerator.generateWithHistory(p, owner, emptyList(), id, now, library)

    private fun running() = PlanRequest(
        start=monday, weeks=2, experience=2,
        goal="Improve 10K", weeklyKm=40.0, longestKm=14.0,
        runDays=4, days=(1..7).toSet(), longDay=7,
        qualityCount=1, qualityDays=setOf(2),
        policyChoice="engine15", timeBudgetEnabled=false,
        recentMileageBasis="SELF_REPORTED_AVERAGE",
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
        val original=PlanRequest(start=monday,runDays=3,policyChoice="engine15",
            days=setOf(1,2,4,5,6),weeklyKm=24.0,longestKm=10.0,timeBudgetEnabled=false,recentMileageBasis="SELF_REPORTED_AVERAGE")
        val json=original.encode()
        assertFalse(json.has("weeklySlots"))
        val restored=PlanRequest.decode(json)
        assertTrue(restored.weeklySlots.isEmpty())
        assertEquals(
            generate(original,"legacy").sessions,
            generate(restored,"legacy").sessions
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
            generate(changed,"unassigned")
        }
        assertEquals(listOf(7,2,4,5),original.weeklySlots["run"])
    }

    @Test fun incompleteOrDuplicateAssignmentsFailClosedBeforeGeneration() {
        val cleared=WeeklyPlacement.clear(running(),"run",3)
        assertEquals(0,cleared.weeklySlots["run"]!![3])
        assertFalse(WeeklyPlacement.full(cleared,"run"))
        assertNotNull(WeeklyPlacement.validationError(cleared))
        assertThrows(PlanConstraint::class.java) {
            generate(cleared,"missing")
        }
        val duplicate=running().copy(
            weeklySlots=mapOf("run" to listOf(7,2,4,4))
        )
        assertNotNull(WeeklyPlacement.validationError(duplicate))
    }

    @Test fun exactRunPlacementsAppearOnEveryTrainingWeek() {
        val p=generate(running(),"fixed")
        assertEquals(8,p.sessions.size)
        for (week in 1..2) {
            val run=p.sessions.filter { it.id.contains("-w" + week + "-run") }
            assertEquals(4,run.size)
            for (i in 0..3) {
                val session=run.single { it.id.endsWith("-run"+i) }
                assertEquals(listOf(7,2,4,5)[i],
                    session.date.dayOfWeek.value)
            }
        }
    }

    @Test fun aWednesdayStartRetainsEverySessionAcrossWholePlanWeekWindows() {
        val start=LocalDate.parse("2026-11-04")
        val request=running().copy(start=start,weeks=3)
        val plan=generate(request,"wednesday")
        assertEquals(12,plan.sessions.size)
        val groups=(0 until request.weeks).map { week ->
            val begin=start.plusWeeks(week.toLong())
            plan.sessions.filter { it.date in begin..begin.plusDays(6) }
        }
        assertEquals(listOf(4,4,4),groups.map { it.size })
        assertEquals(plan.sessions.toSet(),groups.flatten().toSet())
        assertTrue(groups.last().any { it.date.dayOfWeek.value <= 2 })
    }

    @Test fun unsafePinnedQualityAdjacentToLongIsFlaggedInQualitySelection() {
        val p=running().copy(
            longDay=4, qualityDays=setOf(3),
            weeklySlots=mapOf("run" to listOf(4,3,1,6))
        )
        val preview = Engine15IndependentPlanGenerator.preview(p, owner, "unsafe", now)
        assertTrue(preview is Engine15IndependentPlanDecision.Deferred)
        assertEquals(
            "QUALITY_SLOT_UNAVAILABLE",
            (preview as Engine15IndependentPlanDecision.Deferred).qualitySelection?.reasonCode
        )
    }

    @Test fun strengthAndCalisthenicsPinIndependently() {
        val req=PlanRequest(
            start=monday,weeks=2,goal="General strength",
            policyChoice="engine15",timeBudgetEnabled=false,
            days=(1..7).toSet(), runDays=0,strengthDays=2,
            calDays=1,equipment=mapOf("bands" to 1,"anchor" to 1),
            disciplineGoals=mapOf("str" to "General strength","cal" to "Calisthenics foundation"),
            weeklySlots=mapOf(
                "str" to listOf(1,4),"cal" to listOf(6)
            )
        )
        val plan=generate(req,"hybrid")
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
            start=monday,weeks=1,policyChoice="engine15",
            goal="Improve 5K",runDays=2,calDays=1,days=setOf(1,4,7),
            longDay=7,weeklyKm=12.0,longestKm=6.0,
            timeBudgetEnabled=false,recentMileageBasis="SELF_REPORTED_AVERAGE",
            equipment=mapOf("bands" to 1,"anchor" to 1),
            dailyMinutes=120,allowDoubleDays=true,
            weeklySlots=mapOf("run" to listOf(7,4),"cal" to listOf(4))
        )
        val plan=generate(req,"paired")
        assertEquals(3,plan.sessions.size)
        val date=monday.plusDays(3)
        val sessions=plan.sessions.filter { it.date==date }
        assertEquals(2,sessions.size)
        assertEquals(setOf("run","cal"),sessions.map { it.discipline }.toSet())
    }

    @Test fun duplicatedSlotInWeeklyAssignmentsIsRejected() {
        val req=running().copy(weeklySlots=mapOf("run" to listOf(7,2,2,5)))
        assertThrows(PlanConstraint::class.java) {
            generate(req,"dup-slot")
        }
    }

    @Test fun disablingCustomSlotsRestoresCurrentAutomaticScheduling() {
        val automatic=running().copy(weeklySlots=emptyMap())
        val plan=generate(automatic,"auto")
        assertTrue(plan.sessions.isNotEmpty())
        assertEquals(automatic.days,plan.request.days)
    }

    @Test fun sanitizeRunningScheduleDaysRepairsCoachDecidesAndAvailabilityRemoval() {
        val coachDecidesStale=running().copy(
            days=setOf(1,2,3,4,5,7),
            longDay=7,
            qualityCount=1,
            qualityDays=setOf(6,7),
            weeklySlots=emptyMap()
        )
        val fixedOne=sanitizeRunningScheduleDays(coachDecidesStale)
        assertEquals(7,fixedOne.longDay)
        assertEquals(1,fixedOne.qualityDays.size)
        assertTrue(fixedOne.qualityDays.all { it in fixedOne.days && it!=fixedOne.longDay })
        assertTrue(generate(fixedOne,"coach-decides-fixed").sessions.any { it.quality })

        val shrunkDays=coachDecidesStale.copy(
            days=setOf(1,2,3,4,5),
            longDay=7,
            qualityCount=2,
            qualityDays=emptySet()
        )
        val fixedTwo=sanitizeRunningScheduleDays(shrunkDays)
        assertTrue(fixedTwo.longDay in fixedTwo.days)
        assertEquals(2,fixedTwo.qualityDays.size)
        val sortedQ = fixedTwo.qualityDays.sorted()
        assertTrue(sortedQ[1] - sortedQ[0] >= 2)
        assertTrue(fixedTwo.qualityDays.all { it in fixedTwo.days && it!=fixedTwo.longDay })
        assertTrue(generate(fixedTwo,"coach-decides-two").sessions.isNotEmpty())
    }
}

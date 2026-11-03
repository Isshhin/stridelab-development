package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Test-first B5: only affected training moves, never unaffected or historical facts. */
class Engine15AvailabilityReconciliationTest {
    private val start = LocalDate.parse("2026-11-09")
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val owner = "reconcile-owner"
    private val days = (1..7).toSet()

    private fun fixture(withCal:Boolean=true):PlanSnapshot {
        val req=PlanRequest(
            policyChoice="engine15", goal="Improve 5K", start=start,weeks=1,
            days=days, runDays=3,strengthDays=1,calDays=if(withCal)1 else 0,
            qualityCount=1,qualityDays=setOf(2),longDay=7,
            minutes=45,dailyMinutes=120,weeklyKm=45.0,experience=2
        )
        val sessions=listOf(
            PlannedSession("quality",start.plusDays(1),"run","Quality run",45,7.0,
                "quality",quality=true),
            PlannedSession("easy-friday",start.plusDays(4),"run","Easy run",40,6.0,"easy"),
            PlannedSession("upper-sat",start.plusDays(5),"str","Upper",35,null,"strength",lower=false),
            PlannedSession("long-sun",start.plusDays(6),"run","Long run",70,12.0,"long")
        ) + if(withCal) listOf(
            PlannedSession("cal-friday",start.plusDays(4),"cal","Core",30,null,"calisthenics")
        ) else emptyList()
        val plan=PlanSnapshot("review",owner,ENGINE15_VERSION,ENGINE15_INTERFERENCE_POLICY,now,
            req,sessions.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id }),
            listOf("Synthetic reconciliation fixture"),status="approved")
        PlanScheduleValidation.validatePlan(plan)
        return plan
    }

    private fun validCalendar(plan:PlanSnapshot) {
        val active=plan.sessions.filter { it.status in setOf("planned","in_progress") }
        assertTrue(active.all { it.date.dayOfWeek.value in plan.request.days })
        for(i in active.indices) for(j in i+1 until active.size)
            assertFalse(PlanScheduleValidation.conflict(active[i],active[j],plan.policyId,plan.policyId))
        assertTrue(active.groupBy { it.date }.values.all {
            it.size<=2 && it.sumOf { s->s.effectiveMinutes }<=plan.request.dailyMinutes
        })
    }

    @Test fun oneAffectedWorkoutRelocatesWithoutMovingUnaffectedSessions() {
        val original=fixture(withCal=false)
        val change=PlanOperations.updateAvailability(original,days-5)
        val moved=change.sessions.single { it.id=="easy-friday" }
        assertEquals("planned",moved.status)
        assertTrue("A safe available date exists",moved.date!=start.plusDays(4))
        assertTrue(moved.date.dayOfWeek.value in days-5)
        assertEquals("paused",change.status)
        assertEquals(original.sessions.filterNot { it.id=="easy-friday" },
            change.sessions.filterNot { it.id=="easy-friday" })
        assertEquals(original.sessions.map { it.id }.toSet(),change.sessions.map { it.id }.toSet())
        assertEquals(original.sessions.size,change.sessions.size)
        validCalendar(change)
    }

    @Test fun twoAffectedWorkoutsRelocateSafelyAndDeterministically() {
        val original=fixture()
        val a=PlanOperations.updateAvailability(original,days-5)
        val b=PlanOperations.updateAvailability(original,days-5)
        assertEquals(a,b)
        assertEquals("planned",a.sessions.single { it.id=="easy-friday" }.status)
        assertEquals("planned",a.sessions.single { it.id=="cal-friday" }.status)
        assertTrue(a.sessions.filter { it.id in setOf("easy-friday","cal-friday") }
            .all { it.date.dayOfWeek.value!=5 })
        assertEquals(original.sessions.filter { it.id in setOf("quality","upper-sat","long-sun") },
            a.sessions.filter { it.id in setOf("quality","upper-sat","long-sun") })
        assertEquals(original.sessions.map { it.id }.toSet(),a.sessions.map { it.id }.toSet())
        validCalendar(a)
        val approved=PlanOperations.approve(a,emptyList())
        PlanScheduleValidation.validatePlan(approved)
        assertEquals(approved,PlanSnapshot.decode(approved.encode(),owner))
    }

    @Test fun impossibleRelocationNeverForgesCapacityOrDropsSessions() {
        val original=fixture()
        val change=PlanOperations.updateAvailability(original,setOf(2,7))
        assertEquals(original.sessions.map { it.id }.toSet(),change.sessions.map { it.id }.toSet())
        assertEquals(original.sessions.size,change.sessions.size)
        assertEquals("unassigned",change.sessions.single { it.id=="easy-friday" }.status)
        assertEquals("unassigned",change.sessions.single { it.id=="cal-friday" }.status)
        assertEquals(start.plusDays(4),change.sessions.single { it.id=="easy-friday" }.date)
        assertThrows(PlanConstraint::class.java) { PlanOperations.approve(change,emptyList()) }
        validCalendar(change)
    }

    @Test fun completedAndLockedWorkoutsAreNeverMoved() {
        val plan=fixture()
        val completed=plan.copy(sessions=plan.sessions.map {
            if(it.id=="easy-friday")it.copy(status="completed")else it
        })
        val changed=PlanOperations.updateAvailability(completed,days-5)
        val actual=changed.sessions.single { it.id=="easy-friday" }
        assertEquals("completed",actual.status)
        assertEquals(start.plusDays(4),actual.date)
        assertEquals(plan.sessions.size,changed.sessions.size)
        val locked=PlanOperations.setSessionLock(plan,"easy-friday",true)
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.updateAvailability(locked,days-5)
        }
    }
}

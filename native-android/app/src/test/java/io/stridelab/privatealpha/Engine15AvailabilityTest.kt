package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Explicit review of availability; no hidden new sessions or catch-up. */
class Engine15AvailabilityTest {
    private val start=LocalDate.parse("2026-11-09")
    private val now=Instant.parse("2026-11-03T00:00:00Z")
    private val owner="synthetic-owner"
    private fun request()=PlanRequest(
        goal="Improve 5K",policyChoice="engine15",start=start,weeks=4,
        days=setOf(2,3,4,5,6,7),runDays=5,strengthDays=2,calDays=2,
        longDay=7,qualityDays=setOf(2),qualityCount=1,
        weeklyKm=65.0,longestKm=16.0,experience=2,strengthExperience=2,
        minutes=45,dailyMinutes=120,equipment=mapOf("db" to 2,"support" to 1)
    )
    private fun plan()=PlanOperations.approve(
        Engine15.generate(request(),owner,"availability",now),emptyList())

    @Test fun newlyUnavailableOccupiedDayBecomesUnassignedWithoutCatchUp() {
        val original=plan()
        val days=setOf(1,2,3,4,6,7) // Monday added; Friday removed
        val preview=Engine15Availability.preview(original,days)
        assertTrue(preview.affectedSessionIds.isNotEmpty())
        assertEquals(setOf(1),preview.newlyAvailableDays)
        assertEquals(setOf(5),preview.newlyUnavailableDays)
        assertEquals("approved",original.status)
        val changed=PlanOperations.updateAvailability(original,days)
        assertEquals("paused",changed.status)
        assertEquals(days,changed.request.days)
        assertEquals(original.sessions.map { it.id },changed.sessions.map { it.id })
        assertEquals(original.sessions.size,changed.sessions.size)
        assertEquals(original.sessions.map { it.date },changed.sessions.map { it.date })
        assertEquals(preview.affectedSessionIds.toSet(),
            changed.sessions.filter { it.status=="unassigned" }.map { it.id }.toSet())
        assertEquals(changed,PlanSnapshot.decode(changed.encode(),owner))
        val pausedBackup=JSONObject().put("schemaVersion",1)
            .put("plans",JSONArray().put(changed.encode())).toString()
        assertEquals(listOf(changed),PlanBackup.read(pausedBackup,owner))
        assertThrows(PlanConstraint::class.java) {
            val forged=changed.copy(status="approved")
            val raw=JSONObject().put("schemaVersion",1)
                .put("plans",JSONArray().put(forged.encode())).toString()
            PlanBackup.read(raw,owner)
        }
        assertThrows(PlanConstraint::class.java) { PlanOperations.approve(changed,emptyList()) }

        val first=changed.sessions.first { it.status=="unassigned" }
        val monday=start // Newly available day
        val moved=PlanOperations.move(changed,first.id,monday)
        assertEquals("planned",moved.sessions.single { it.id==first.id }.status)
        assertEquals(monday,moved.sessions.single { it.id==first.id }.date)
        assertEquals(changed.sessions.size,moved.sessions.size)
        assertEquals(changed.sessions.map { it.id }.toSet(),moved.sessions.map { it.id }.toSet())
        assertTrue(moved.sessions.any { it.status=="unassigned" })
    }

    @Test fun addingAvailabilityDoesNotRegenerateOrMoveWorkout() {
        val original=plan()
        val extra=original.request.days+1
        val updated=PlanOperations.updateAvailability(original,extra)
        assertEquals("approved",updated.status)
        assertEquals(extra,updated.request.days)
        assertEquals(original.sessions,updated.sessions)
        assertEquals(updated,PlanSnapshot.decode(updated.encode(),owner))
        assertSame(updated,PlanOperations.updateAvailability(updated,extra))
    }

    @Test fun explicitLocksAndLiveWorkoutFactsCannotBeSilentlyDisplaced() {
        val original=plan()
        val friday=original.sessions.first { it.date.dayOfWeek.value==5 }
        val locked=PlanOperations.setSessionLock(original,friday.id,true)
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.updateAvailability(locked,setOf(1,2,3,4,6,7))
        }
        val started=original.copy(sessions=original.sessions.map {
            if(it.id==friday.id)it.copy(status="in_progress") else it
        })
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.updateAvailability(started,setOf(1,2,3,4,6,7))
        }
        val completed=original.copy(sessions=original.sessions.map {
            if(it.id==friday.id)it.copy(status="completed") else it
        })
        val changed=PlanOperations.updateAvailability(completed,setOf(1,2,3,4,6,7))
        assertEquals("completed",changed.sessions.single { it.id==friday.id }.status)
        assertEquals(friday.date,changed.sessions.single { it.id==friday.id }.date)
    }

    @Test fun longAndQualityPreferenceChangesRequireManualReplacement() {
        val original=plan()
        val newDays=original.request.days-7+1
        val changed=PlanOperations.updateAvailability(original,newDays)
        assertEquals("paused",changed.status)
        assertTrue(changed.sessions.any { it.kind=="long" && it.status=="unassigned" })
        assertThrows(PlanConstraint::class.java) { PlanOperations.approve(changed,emptyList()) }
        val selected=PlanOperations.setLongRunDay(changed,1)
        assertEquals(1,selected.request.longDay)
        assertEquals(changed.sessions.size,selected.sessions.size)
        assertTrue(selected.sessions.any { it.status=="unassigned" })
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.setQualityDays(selected,setOf(1))
        }
        val revised=PlanOperations.setQualityDays(selected,setOf(2,4))
        assertEquals(setOf(2,4),revised.request.qualityDays)
        assertEquals(changed.sessions.size,revised.sessions.size)
    }
}

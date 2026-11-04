package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** B6: edited snapshots, consented locks and actual records remain durable. */
class Engine15EditedPlanIntegrityTest {
    private val owner="synthetic-edited-owner"
    private val start=LocalDate.parse("2026-11-09")
    private val now=Instant.parse("2026-11-03T00:00:00Z")
    private fun req()=PlanRequest(
        goal="Improve 5K",policyChoice="engine15",start=start,weeks=4,
        minutes=45,days=setOf(2,3,4,5,6,7),runDays=5,strengthDays=2,calDays=2,
        longDay=7,qualityDays=setOf(2),qualityCount=1,weeklyKm=65.0,
        longestKm=16.0,planningPace=400.0,experience=2,strengthExperience=2,
        equipment=mapOf("db" to 2,"support" to 1),dailyMinutes=120
    )

    @Test fun unsafeInternallyEditedDraftMustNotBeApproved() {
        val generated=Engine15.generate(req(),owner,"recheck",now)
        val easy=generated.sessions.filter { it.discipline=="run" && it.kind=="easy" }
            .filter { it.date>=start && it.date<start.plusWeeks(1) }
        assertTrue(easy.size>=2)
        val altered=generated.copy(sessions=generated.sessions.map {
            if(it.id==easy[1].id)it.copy(date=easy[0].date)else it
        })
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.approve(altered,emptyList())
        }
        assertEquals(generated.sessions.size,altered.sessions.size)
    }

    @Test fun lockLogAndAvailabilityReconcileKeepHistoryAndBackupsOwnerScoped() {
        val initial=PlanOperations.approve(
            Engine15.generate(req(),owner,"stable",now),emptyList())
        val q=initial.sessions.first { it.quality }
        val locked=PlanOperations.setSessionLock(initial,q.id,true)
        val completed=locked.sessions.first {
            it.discipline=="run" && it.kind=="easy" && it.date.dayOfWeek.value!=5
        }
        val actual=WorkoutLog(now,minutes=35.0,distanceKm=5.0,effort=4)
        val recorded=PlanOperations.record(locked,completed.id,actual,true)
        val adjusted=PlanOperations.updateAvailability(
            recorded,setOf(1,2,3,4,6,7),asOf=start)
        assertEquals("paused",adjusted.status)
        assertEquals(initial.sessions.size,adjusted.sessions.size)
        assertEquals(initial.sessions.map { it.id }.toSet(),
            adjusted.sessions.map { it.id }.toSet())
        assertEquals(q.date.toString(),adjusted.request.lockedSessionDays[q.id])
        val historical=adjusted.sessions.single { it.id==completed.id }
        assertEquals("completed",historical.status)
        assertEquals(completed.date,historical.date)
        assertEquals(actual,historical.log)
        assertEquals(initial.sessions.sumOf { it.distanceKm ?: 0.0 },
            adjusted.sessions.sumOf { it.distanceKm ?: 0.0 },0.0)
        assertEquals(adjusted,PlanSnapshot.decode(adjusted.encode(),owner))
        val backup=JSONObject().put("schemaVersion",1)
            .put("plans",JSONArray().put(adjusted.encode())).toString()
        val incoming=PlanBackup.read(backup,owner)
        assertEquals(listOf(adjusted),incoming)
        assertEquals(listOf(adjusted),PlanBackup.additions(incoming,emptyList()))
        assertTrue(PlanBackup.additions(incoming,listOf(initial)).isEmpty())
        assertThrows(PlanConstraint::class.java) {
            PlanBackup.read(backup,"wrong-owner")
        }
    }
}

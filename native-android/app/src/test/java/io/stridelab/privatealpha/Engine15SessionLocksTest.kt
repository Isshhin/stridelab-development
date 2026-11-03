package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

/** B4 locking contract, independent from UI and athlete data. */
class Engine15SessionLocksTest {
    private val start=LocalDate.parse("2026-11-09")
    private val now=Instant.parse("2026-11-03T00:00:00Z")
    private fun request()=PlanRequest(
        goal="Improve 5K",policyChoice="engine15",start=start,weeks=4,
        minutes=45,days=setOf(2,3,4,5,6,7),runDays=5,strengthDays=2,calDays=2,
        longDay=7,qualityDays=setOf(2),qualityCount=1,weeklyKm=65.0,
        longestKm=16.0,experience=2,strengthExperience=2,
        equipment=mapOf("db" to 2,"support" to 1),dailyMinutes=120
    )

    @Test fun explicitLockIsStableInRegenerationAndOwnerBoundBackup() {
        val initial=PlanOperations.approve(
            Engine15.generate(request(),"owner","stable",now),emptyList())
        val target=initial.sessions.first { it.discipline=="str" && !it.lower }
        val locked=PlanOperations.setSessionLock(initial,target.id,true)
        assertEquals(target.date.toString(),locked.request.lockedSessionDays[target.id])
        assertEquals(locked,PlanSnapshot.decode(locked.encode(),"owner"))
        val generated=Engine15.generate(locked.request,"owner","stable",now)
        assertEquals(locked.policyId,generated.policyId)
        assertEquals(target.date,generated.sessions.single { it.id==target.id }.date)
        assertEquals(locked.request.lockedSessionDays,generated.request.lockedSessionDays)
        PlanScheduleValidation.validatePlan(locked)
        val backup=JSONObject().put("schemaVersion",1)
            .put("plans",JSONArray().put(locked.encode())).toString()
        assertEquals(listOf(locked),PlanBackup.read(backup,"owner"))
        assertThrows(PlanConstraint::class.java) { PlanBackup.read(backup,"other-owner") }
    }

    @Test fun moveRequiresExplicitUnlockAndDoesNotChangeHistory() {
        val plan=PlanOperations.approve(
            Engine15.generate(request(),"owner","move-lock",now),emptyList())
        val selected=plan.sessions.first { it.kind=="easy" }
        val locked=PlanOperations.setSessionLock(plan,selected.id,true)
        assertSame(locked,PlanOperations.move(locked,selected.id,selected.date))
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(locked,selected.id,selected.date.plusDays(1))
        }
        val unlocked=PlanOperations.setSessionLock(locked,selected.id,false)
        assertTrue(unlocked.request.lockedSessionDays.isEmpty())
        assertEquals(plan.sessions,unlocked.sessions)
        assertEquals(unlocked,PlanSnapshot.decode(unlocked.encode(),"owner"))
    }

    @Test fun unavailableUnknownOrInvalidLocksFailRatherThanMove() {
        val plan=Engine15.generate(request(),"owner","changes",now)
        val session=plan.sessions.first { it.discipline=="run" }
        val locked=PlanOperations.setSessionLock(plan,session.id,true)
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(locked.request.copy(
                days=locked.request.days-session.date.dayOfWeek.value),
                "owner","changes",now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(request().copy(lockedSessionDays=mapOf(
                "not-a-session" to start.plusDays(2).toString()
            )),"owner","changes",now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(request().copy(lockedSessionDays=mapOf(
                session.id to "invalid-date"
            )),"owner","changes",now)
        }
    }

    @Test fun engine10AndUnchangedSnapshotsKeepOriginalContract() {
        val legacy=Engine1.generate(request().copy(
            policyChoice="baseline",runDays=3,strengthDays=0,calDays=0
        ),"owner","legacy",now)
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.setSessionLock(legacy,legacy.sessions.first().id,true)
        }
        assertFalse(legacy.encode().getJSONObject("request").has("lockedSessionDays"))
        assertEquals(legacy,PlanSnapshot.decode(legacy.encode(),"owner"))
    }
}

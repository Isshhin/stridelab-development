package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** B7 curated existing scheduler policies and conflict invariants. */
class Engine15B7MixedScheduleMatrixTest {
    private val owner="b7-mixed-athlete"
    private val now=Instant.parse("2026-11-03T00:00:00Z")
    private val start=LocalDate.parse("2026-11-09")
    private fun req()=PlanRequest(
        goal="Improve 5K",policyChoice="engine15",start=start,weeks=4,
        days=setOf(2,3,4,5,6,7),runDays=5,strengthDays=2,calDays=2,
        longDay=7,qualityDays=setOf(2),qualityCount=1,
        weeklyKm=65.0,longestKm=16.0,planningPace=400.0,
        minutes=45,dailyMinutes=120,experience=2,strengthExperience=2,
        equipment=mapOf("db" to 2,"support" to 1)
    )

    @Test fun fourSupportedMixedDisciplinePoliciesPreserveCountsAndRecovery() {
        val scenarios=listOf(
            "single-r3-s1-c1" to req().copy(
                days=(1..7).toSet(),runDays=3,strengthDays=1,calDays=1),
            "single-r4-s1-c1" to req().copy(
                days=(1..7).toSet(),runDays=4,strengthDays=1,calDays=1),
            "multi-r5-c2" to req().copy(
                strengthDays=0),
            "interference-r5-s2-c2" to req()
        )
        var approvedCount=0
        for((name,p) in scenarios) {
            val id="b7-$name"
            val snapshot=try { Engine15.generate(p,owner,id,now) }
                catch(ex:PlanConstraint) {
                    throw AssertionError("Unexpected rejection $id request=$p: ${ex.message}",ex)
                }
            assertEquals("$id deterministic",snapshot,Engine15.generate(p,owner,id,now))
            val sessions=snapshot.sessions
            assertEquals("$id complete",p.weeks*
                (p.runDays+p.strengthDays+p.calDays+p.conditioningDays),sessions.size)
            assertEquals("$id stable IDs",sessions.size,sessions.map { it.id }.toSet().size)
            val weekly=sessions.groupBy { java.time.temporal.ChronoUnit.DAYS
                .between(start,it.date)/7L }
            for(w in 0 until p.weeks) {
                val ss=weekly[w.toLong()]?:emptyList()
                assertEquals("$id week=$w runs",p.runDays,ss.count { it.discipline=="run" })
                assertEquals("$id week=$w strength",p.strengthDays,
                    ss.count { it.discipline=="str" })
                assertEquals("$id week=$w cal",p.calDays,
                    ss.count { it.discipline=="cal" })
                assertEquals("$id week=$w long",1,ss.count { it.kind=="long" })
                val runningKm=ss.filter { it.discipline=="run" }
                    .sumOf { it.distanceKm?:0.0 }
                assertTrue("$id week=$w bounded km=$runningKm",
                    runningKm<=Engine15.runningCeiling(w,p.weeklyKm)+1e-6)
            }
            assertTrue("$id weekday",sessions.all { it.date.dayOfWeek.value in p.days })
            assertTrue("$id time",sessions.groupBy { it.date }.values.all { day ->
                day.size<=2 && day.sumOf { it.effectiveMinutes }<=p.dailyMinutes
            })
            for(i in sessions.indices)for(j in i+1 until sessions.size)
                assertFalse("$id unsafe pair ${sessions[i].id} / ${sessions[j].id}",
                    PlanScheduleValidation.conflict(
                        sessions[i],sessions[j],snapshot.policyId,snapshot.policyId))
            when(name) {
                "multi-r5-c2" -> assertEquals(ENGINE15_MULTI_POLICY,snapshot.policyId)
                "interference-r5-s2-c2" ->
                    assertEquals(ENGINE15_INTERFERENCE_POLICY,snapshot.policyId)
                else -> assertEquals(ENGINE15_POLICY,snapshot.policyId)
            }
            val approved=PlanOperations.approve(snapshot,emptyList())
            PlanScheduleValidation.validatePlan(approved)
            assertEquals("$id roundtrip",approved,PlanSnapshot.decode(approved.encode(),owner))
            approvedCount++
        }
        assertEquals(4,approvedCount)
        println("B7 mixed matrix: supported=$approvedCount; policies=single,multi,interference")
    }

    @Test fun impossibleMixedBudgetsAndUnavailableDaysRejectWithoutDroppingSessions() {
        val initial=req()
        val rejects=listOf(
            "insufficient-time" to initial.copy(dailyMinutes=15),
            "long-not-available" to initial.copy(days=setOf(2,3,4,5,6)),
            "quality-not-available" to initial.copy(qualityDays=setOf(7)),
            "infeasible-session-pair-capacity" to initial.copy(calDays=3)
        )
        for((id,p) in rejects) {
            val ex=assertThrows("$id request=$p",PlanConstraint::class.java) {
                Engine15.generate(p,owner,"b7-impossible-$id",now)
            }
            assertTrue("$id clear conflict",!ex.message.isNullOrBlank())
        }
        assertEquals(4,rejects.size)
    }

    @Test fun mixedPlanBackupDoesNotCrossOwnerOrOverwriteMatchingId() {
        val approved=PlanOperations.approve(
            Engine15.generate(req(),owner,"b7-backup",now),emptyList())
        val backup=JSONObject().put("schemaVersion",1)
            .put("plans",JSONArray().put(approved.encode())).toString()
        assertEquals(listOf(approved),PlanBackup.read(backup,owner))
        assertThrows(PlanConstraint::class.java) { PlanBackup.read(backup,"foreign") }
        assertTrue(PlanBackup.additions(listOf(approved),listOf(approved)).isEmpty())
    }
}

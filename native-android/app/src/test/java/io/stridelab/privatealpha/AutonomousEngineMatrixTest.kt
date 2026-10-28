package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/** Seeded implementation invariants, not validation of clinical or coaching outcomes. */
class AutonomousEngineMatrixTest {
    @Test fun threeHundredGeneratedOrExplicitlyRejectedRequestsKeepSnapshotAndSchedulingInvariants() {
        val rng=Random(20261028);val now=Instant.parse("2026-10-28T00:00:00Z")
        var accepted=0;var rejected=0
        repeat(300) { i ->
            val policy=listOf("baseline","coordinated","multi-session")[i%3]
            val request=PlanRequest(start=LocalDate.parse("2026-11-02"),weeks=listOf(4,8,16)[rng.nextInt(3)],minutes=listOf(30,45,60)[rng.nextInt(3)],days=(1..7).toSet(),runDays=rng.nextInt(0,5),strengthDays=rng.nextInt(0,3),calDays=rng.nextInt(0,3),conditioningDays=0,experience=rng.nextInt(3),weeklyKm=listOf(0.0,15.0,30.0)[rng.nextInt(3)],longestKm=8.0,policyChoice=policy,dailyMinutes=90,equipment=if(i%2==0)mapOf("db" to 2)else emptyMap())
            val p=try { Engine1Engines.resolve(request).generate(request,"qa-matrix-owner","matrix-$i",now) }catch(_:PlanConstraint){rejected++;return@repeat}
            accepted++
            assertEquals((request.runDays+request.strengthDays+request.calDays)*request.weeks,p.sessions.size)
            assertEquals(p.sessions.size,p.sessions.map { it.id }.distinct().size)
            assertTrue(p.sessions.all { it.status=="planned" && it.log==null && it.logRevision==0 })
            assertTrue(p.sessions.all { it.date>=request.start && it.date<request.start.plusWeeks(request.weeks.toLong()) })
            assertEquals(p,PlanSnapshot.decode(p.encode(),p.owner))
            assertEquals(p,Engine1Engines.resolve(request).generate(request,p.owner,p.id,now))
            for(a in p.sessions)for(b in p.sessions)if(a.id!=b.id)assertFalse(if(policy=="multi-session")MultiSessionScheduling.conflict(a,b)else Engine1.conflict(a,b))
            if(policy=="multi-session")assertTrue(p.sessions.groupBy { it.date }.values.all { day -> day.sumOf { it.effectiveMinutes }<=request.dailyMinutes })
        }
        println("Seed 20261028: attempted=300 accepted=$accepted explicitlyRejected=$rejected")
        assertTrue(accepted>100);assertEquals(300,accepted+rejected)
    }
}

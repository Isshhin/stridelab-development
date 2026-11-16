package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Deterministic B7 software-safety cases; never evidence of coaching efficacy. */
class Engine15B7SingleDisciplineMatrixTest {
    private val now=Instant.parse("2026-11-03T00:00:00Z")
    private val start=LocalDate.parse("2026-11-09")
    private val owner="b7-single-athlete"

    private fun base()=PlanRequest(
        goal="Improve 5K",policyChoice="engine15",start=start,weeks=4,
        days=(1..7).toSet(),runDays=4,qualityDays=setOf(2,4),
        qualityCount=1,longDay=7,minutes=90,dailyMinutes=120,
        weeklyKm=45.0,longestKm=16.0,planningPace=420.0,experience=2
    )

    @Test fun fortyFiveCuratedFrequencyVolumeLengthAndAthleteCasesRemainBounded() {
        var total=0
        var returningCases=0
        var doubleQualityCases=0
        for(run in 2..6) for(km in listOf(18.0,35.0,65.0))
            for(weeks in listOf(1,4,8)) {
                val id="b7-running-r${run}-km${km.toInt()}-w${weeks}"
                val quality=when {
                    run<3 || km<20.0 -> 0
                    run>=4 && km>=40.0 && weeks>=4 -> 2
                    else -> 1
                }
                val returning=(run+km.toInt()+weeks)%4==0
                val experience=if(km<20.0)0 else 2
                val req=base().copy(
                    runDays=run,weeklyKm=km,weeks=weeks,qualityCount=quality,
                    experience=experience,returning=returning,
                    longestKm=if(km<20.0)6.0 else 16.0
                )
                val plan=try { Engine15.generate(req,owner,id,now) }
                    catch(ex:PlanConstraint) {
                        throw AssertionError("Unexpected rejection in supported $id request=$req: ${ex.message}",ex)
                    }
                total++
                if(returning)returningCases++
                if(quality==2)doubleQualityCases++
                assertEquals(id+" deterministic",plan,Engine15.generate(req,owner,id,now))
                assertEquals(ENGINE15_VERSION,plan.engineVersion)
                assertEquals(ENGINE15_POLICY,plan.policyId)
                assertEquals(id+" session count",weeks*run,plan.sessions.size)
                assertEquals(id+" unique IDs",plan.sessions.size,
                    plan.sessions.map { it.id }.toSet().size)
                assertTrue(id+" all requested weekdays",plan.sessions.all {
                    it.date.dayOfWeek.value in req.days && it.status=="planned" &&
                        it.date>=start && it.date<start.plusWeeks(weeks.toLong())
                })
                val approved=PlanOperations.approve(plan,emptyList())
                PlanScheduleValidation.validatePlan(approved)
                assertEquals(id+" persistence",approved,
                    PlanSnapshot.decode(approved.encode(),owner))
                for(w in 0 until weeks) {
                    val first=start.plusWeeks(w.toLong())
                    val ss=plan.sessions.filter { it.date>=first &&
                        it.date<first.plusWeeks(1) }
                    assertEquals(id+" week=$w total",run,ss.size)
                    assertEquals(id+" week=$w long",1,ss.count { it.kind=="long" })
                    // Mixed-unit quality steps do not have an exact distance
                    // target. Include their explicitly labeled planning estimate
                    // when checking the weekly and long-run exposure envelopes.
                    // Ignoring them would incorrectly shrink the denominator.
                    ss.filter { it.runningPrescription?.mixedSegments!=null }.forEach { mixed ->
                        assertNull("$id week=$w mixed steps must not claim exact km",mixed.distanceKm)
                        assertTrue("$id week=$w estimate must be positive and finite",
                            mixed.runningPrescription!!.estimatedDistanceKm!!.let { it.isFinite() && it>0.0 })
                    }
                    val volume=ss.sumOf {
                        it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm ?: 0.0
                    }
                    assertTrue("$id week=$w mileage=$volume",
                        volume<=Engine15.runningCeiling(w,km)+1e-6)
                    if(run>=3 && volume>0) {
                        val long=ss.single { it.kind=="long" }.distanceKm?:0.0
                        assertTrue("$id week=$w long=$long total=$volume",
                            long<=volume*0.4+1e-6)
                    }
                    if(returning && w<3 || w%4==3 || experience==0)
                        assertTrue("$id week=$w conservative quality",
                            ss.none { it.quality })
                    assertTrue(id+" week=$w time",ss.all {
                        it.minutes<=req.minutes && it.effectiveMinutes<=req.dailyMinutes
                    })
                }
            }
        assertEquals(45,total)
        assertTrue(returningCases>0)
        assertTrue(doubleQualityCases>0)
        println("B7 single-discipline matrix: supported=$total returning=$returningCases doubleQualityRequests=$doubleQualityCases")
    }

    @Test fun unsupportedSevenRunsAndContradictoryQualityConstraintsFailExplicitly() {
        val req=base()
        val cases=listOf(
            "seven-running-days-not-supported" to req.copy(runDays=7),
            "quality-exceeds-easy-space" to req.copy(runDays=3,qualityCount=2),
            "unavailable-long-day" to req.copy(days=setOf(1,2,3,4,5,6)),
            "unavailable-quality" to req.copy(qualityDays=setOf(7)),
            "invalid-mileage" to req.copy(weeklyKm=Double.NaN),
            "invalid-policy" to req.copy(policyChoice="unsupported")
        )
        for((name,p) in cases) {
            val e=assertThrows("$name $p",PlanConstraint::class.java) {
                Engine1Engines.resolve(p).generate(p,owner,"b7-reject-$name",now)
            }
            assertTrue("$name explicit failure",!e.message.isNullOrBlank())
        }
        assertEquals(6,cases.size)
    }

    @Test fun engineOneFallbackRetainsItsOwnVersionPolicyAndGeneratedPlacements() {
        val req=base().copy(runDays=4,qualityCount=1,dailyMinutes=90)
        val legacyReq=req.copy(policyChoice="baseline")
        val baseline=Engine1.generate(legacyReq,owner,"b7-baseline",now)
        assertEquals(ENGINE1_VERSION,baseline.engineVersion)
        assertEquals(ENGINE1_POLICY,baseline.policyId)
        assertEquals(baseline,Engine1.generate(legacyReq,owner,"b7-baseline",now))
        assertEquals(baseline,PlanSnapshot.decode(baseline.encode(),owner))
        val updated=Engine15.generate(req,owner,"b7-baseline",now)
        assertEquals(baseline.sessions.map { it.id to it.date },
            updated.sessions.map { it.id to it.date })
        assertEquals(updated,PlanSnapshot.decode(updated.encode(),owner))
        val backup=JSONObject().put("schemaVersion",1)
            .put("plans",JSONArray().put(updated.encode())).toString()
        assertEquals(listOf(updated),PlanBackup.read(backup,owner))
        assertThrows(PlanConstraint::class.java) {
            PlanBackup.read(backup,"different-owner")
        }
    }
}

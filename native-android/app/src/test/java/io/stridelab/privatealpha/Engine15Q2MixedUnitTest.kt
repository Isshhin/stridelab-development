package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Q2: compatibility and structural tests, not evidence of training efficacy. */
class Engine15Q2MixedUnitTest {
    private val date=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private val owner="q2-synthetic"

    private fun request()=PlanRequest(
        goal="Aerobic foundation",experience=2,start=date,weeks=8,
        days=(1..7).toSet(),runDays=6,longDay=7,qualityDays=setOf(2,4),
        qualityCount=2,recentQualitySessionsPerWeek=2,
        weeklyKm=80.0,longestKm=24.0,targetWeeklyKm=80.0,
        planningPace=360.0,preferredQuality="Threshold",
        allowedQualityTypes=setOf("Threshold"),policyChoice="engine15",
        timeBudgetEnabled=false
    )

    @Test fun legacyVersionOneRemainsExactlyDistanceBasedAndRoundtrips() {
        val legacy=RunningPrescription("QUALITY","Threshold",listOf(
            RunningSegment("WARM_UP",1200,"Easy"),
            RunningSegment("WORK",1000,"Controlled"),
            RunningSegment("RECOVERY",300,"Easy jog"),
            RunningSegment("COOL_DOWN",800,"Easy")))
        assertEquals(1,legacy.encode().getInt("version"))
        assertEquals(3300,legacy.meters)
        assertEquals(1000,legacy.workMeters)
        assertNull(legacy.mixedSegments)
        assertEquals(legacy,RunningPrescription.decode(legacy.encode()))
        val session=PlannedSession("legacy",date,"run","Threshold",35,3.3,"quality",quality=true,
            runningPrescription=legacy)
        val plan=PlanSnapshot("legacy-plan",owner,ENGINE15_VERSION,ENGINE15_POLICY,now,
            request(),listOf(session),emptyList(),status="approved")
        assertEquals(plan,PlanSnapshot.decode(plan.encode(),owner))
        assertEquals(1,plan.encode().getJSONArray("sessions").getJSONObject(0)
            .getJSONObject("runningPrescription").getInt("version"))
    }

    @Test fun mixedVersionTwoStoresOnlyExplicitStepExtents() {
        val mixed=RunningPrescription("QUALITY","Threshold",emptyList(),
            mixedSegments=listOf(
                RunningStepV2("WARM_UP","Easy",seconds=600),
                RunningStepV2("WORK","Controlled",meters=2000),
                RunningStepV2("RECOVERY","Easy",meters=500),
                RunningStepV2("COOL_DOWN","Easy",seconds=480)),
            estimatedDistanceKm=5.5)
        val encoded=mixed.encode()
        assertEquals(2,encoded.getInt("version"))
        assertEquals("PLANNING_PACE_NOT_TARGET",encoded.getString("estimateBasis"))
        assertEquals(2000,mixed.workMeters)
        assertEquals(2500,mixed.meters)
        assertEquals(mixed,RunningPrescription.decode(encoded))
        val segments=encoded.getJSONArray("segments")
        assertTrue(segments.getJSONObject(0).has("seconds"))
        assertFalse(segments.getJSONObject(0).has("meters"))
        assertTrue(segments.getJSONObject(1).has("meters"))
        assertFalse(segments.getJSONObject(1).has("seconds"))
        val session=PlannedSession("mixed",date,"run","Threshold",33,null,"quality",quality=true,
            runningPrescription=mixed)
        val plan=PlanSnapshot("mixed-plan",owner,ENGINE15_VERSION,ENGINE15_POLICY,now,
            request(),listOf(session),emptyList(),status="approved")
        assertEquals(plan,PlanSnapshot.decode(plan.encode(),owner))
        assertNull(Engine15SessionPresentation.forSession(plan,"mixed").currentDistanceTargetKm)
    }

    @Test fun ambiguousExtentsFutureVersionsAndFalseExactTotalsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            RunningStepV2("WARM_UP","Easy",meters=1000,seconds=600)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RunningStepV2("WARM_UP","Easy")
        }
        val invalid=JSONObject().put("version",2).put("role","QUALITY")
            .put("family","Threshold").put("estimatedDistanceKm",4.0)
            .put("estimateBasis","PLANNING_PACE_NOT_TARGET")
            .put("segments",JSONArray().put(JSONObject().put("purpose","WORK")
                .put("effort","Controlled").put("meters",1000).put("seconds",240)))
        assertThrows(IllegalArgumentException::class.java) {
            RunningPrescription.decode(invalid)
        }
        val future=JSONObject().put("version",99)
        assertThrows(IllegalArgumentException::class.java) {
            RunningPrescription.decode(future)
        }
        val mixed=RunningPrescription("QUALITY","Threshold",emptyList(),
            mixedSegments=listOf(RunningStepV2("WARM_UP","Easy",seconds=600),
                RunningStepV2("WORK","Controlled",meters=1000),
                RunningStepV2("COOL_DOWN","Easy",seconds=480)),estimatedDistanceKm=4.0)
        assertThrows(IllegalArgumentException::class.java) {
            PlannedSession("incorrect",date,"run","Threshold",30,4.0,"quality",
                quality=true,runningPrescription=mixed)
        }
    }

    @Test fun actualGeneratorRemovesDistanceFilledCooldownWithoutIncreasingWork() {
        val p=request()
        val generated=Engine15.generate(p,owner,"q2",now)
        val quality=generated.sessions.filter { it.quality }
        assertTrue("80 km profile should still receive quality work",quality.isNotEmpty())
        assertTrue(generated.explanations.any { it.startsWith("MIXED_UNIT_ESTIMATES:") })
        for(session in quality) {
            val prescription=session.runningPrescription!!
            val steps=prescription.mixedSegments
            assertNotNull("Q2 quality must be version 2",steps)
            assertNull("Full mixed session distance is never exact",session.distanceKm)
            assertNotNull(prescription.estimatedDistanceKm)
            val warm=steps!!.single { it.purpose=="WARM_UP" }
            val cool=steps.single { it.purpose=="COOL_DOWN" }
            assertNull(warm.meters)
            assertNull(cool.meters)
            assertTrue(warm.seconds!! in 600..720)
            assertTrue(cool.seconds!! in 300..480)
            assertEquals(0,steps.filter { it.purpose=="COOL_DOWN" }.sumOf { it.meters ?: 0 })
            assertTrue(steps.any { it.purpose=="WORK" && (it.meters ?: 0)>0 })
            assertTrue(session.steps.any { it.contains("s warm-up") })
            assertTrue(session.steps.any { it.contains("s cool-down") })
            assertTrue(prescription.estimatedDistanceKm!!<=p.longestKm+1.0+1e-8)
        }
        assertEquals(generated,PlanSnapshot.decode(generated.encode(),owner))
        val backup=JSONObject().put("schemaVersion",1)
            .put("plans",JSONArray().put(generated.encode())).toString()
        assertEquals(listOf(generated),PlanBackup.read(backup,owner))
    }

    @Test fun mixedUnitWeeklyReconciliationRespectsOriginalMileageAndLongShare() {
        val p=request()
        val plan=Engine15.generate(p,owner,"q2-limits",now)
        for(week in 0 until p.weeks) {
            val runs=plan.sessions.filter {
                it.discipline=="run" && it.date>=date.plusWeeks(week.toLong()) &&
                    it.date<date.plusWeeks((week+1).toLong())
            }
            val km=runs.sumOf { it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm ?: 0.0 }
            val long=runs.single { it.kind=="long" }
            assertTrue("Weekly estimate exceeds original ceiling in week "+week,
                km<=Engine15.runningCeiling(week,p.weeklyKm,p.targetWeeklyKm)+1e-8)
            assertTrue("Long-run share in week "+week,long.distanceKm!!<=km*0.4+0.11)
            val work=runs.sumOf { it.runningPrescription?.workMeters ?: 0 }
            assertTrue("Quality share in week "+week,work<=km*1000*0.2+1e-8)
        }
    }

    @Test fun explicitShortTimeBudgetDoesNotOverrunItsLimit() {
        val p=request().copy(timeBudgetEnabled=true,minutes=30,dailyMinutes=30,
            qualityCount=1,recentQualitySessionsPerWeek=1)
        val plan=Engine15.generate(p,owner,"q2-available",now)
        assertTrue(plan.sessions.all { it.minutes<=30 })
        assertTrue(plan.sessions.all { session ->
            session.runningPrescription?.mixedSegments?.any { step ->
                step.purpose=="COOL_DOWN" && step.seconds?.let { it in 300..480 }==true
            } ?: true
        })
    }

    @Test fun acceptedTimeOnlyAlternativeDoesNotRestoreMixedEstimatedDistanceAsTarget() {
        val original=RunningPrescription("QUALITY","Threshold",emptyList(),
            mixedSegments=listOf(
                RunningStepV2("WARM_UP","Easy",seconds=600),
                RunningStepV2("WORK","Controlled",meters=2000),
                RunningStepV2("COOL_DOWN","Easy",seconds=480)),
            estimatedDistanceKm=4.3)
        val session=PlannedSession("mixed-target",date.plusDays(1),"run","Threshold",40,null,
            "quality",quality=true,runningPrescription=original,
            suggestion=SessionSuggestion(30,
                "${Engine15Feedback.RULE_ID}: reviewed alternative",accepted=true))
        val plan=PlanSnapshot("mixed-alternative",owner,ENGINE15_VERSION,ENGINE15_POLICY,now,
            request(),listOf(session),emptyList(),status="approved")

        val shown=Engine15SessionPresentation.forSession(plan,session.id)
        assertEquals(Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY,shown.state)
        assertNull(shown.currentDistanceTargetKm)
        val restored=PlanSnapshot.decode(plan.encode(),owner)
        assertEquals(plan,restored)
        assertNull(Engine15SessionPresentation.forSession(restored,session.id).currentDistanceTargetKm)
        assertEquals(4.3,restored.sessions.single().runningPrescription!!.estimatedDistanceKm!!,0.0)
    }
}

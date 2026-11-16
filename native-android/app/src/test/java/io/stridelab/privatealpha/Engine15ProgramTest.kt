package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15ProgramTest {
    private val start=LocalDate.of(2026,11,16)
    private fun request()=PlanRequest(goal="Aerobic foundation",experience=2,start=start,weeks=8,
        days=(1..7).toSet(),runDays=6,longDay=7,qualityDays=setOf(2,4),qualityCount=2,
        weeklyKm=80.0,longestKm=24.0,planningPace=360.0,policyChoice="engine15",timeBudgetEnabled=false,
        targetWeeklyKm=80.0,recentQualitySessionsPerWeek=2,allowedQualityTypes=setOf("Threshold","Tempo"))
    private fun generate(p:PlanRequest=request())=Engine15.generate(p,"synthetic","program",Instant.parse("2026-11-16T00:00:00Z"))
    @Test fun everyDistanceSessionHasAnExactAuditableStructure() {
        val plan=generate()
        for(s in plan.sessions.filter { it.discipline=="run" }) {
            val j=s.encode()
            assertTrue(s.title,j.has("runningPrescription"))
            val segments=j.getJSONObject("runningPrescription").getJSONArray("segments")
            val version=j.getJSONObject("runningPrescription").getInt("version")
            if(version==2) {
                assertNull("Mixed-unit distance must not be an exact target",s.distanceKm)
                assertTrue((0 until segments.length()).any { segments.getJSONObject(it).has("seconds") })
                assertTrue(j.getJSONObject("runningPrescription").getDouble("estimatedDistanceKm")>0.0)
            } else {
                val meters=(0 until segments.length()).sumOf { segments.getJSONObject(it).getInt("meters") }
                assertEquals(s.distanceKm!!*1000,meters.toDouble(),0.001)
            }
        }
        assertEquals(plan,PlanSnapshot.decode(plan.encode(),"synthetic"))
    }
    @Test fun weekHasRecoveryAndEnduranceRolesInsteadOfEqualEasyShares() {
        val week=generate(request().copy(qualityCount=1)).sessions.filter { it.date<start.plusWeeks(1) }
        assertTrue(week.any { it.title=="Recovery run" })
        assertTrue(week.any { it.title=="Aerobic run" })
        val recovery=week.first { it.title=="Recovery run" }
        val endurance=week.first { it.title=="Aerobic run" }
        val long=week.first { it.kind=="long" }
        assertTrue(recovery.distanceKm!!<endurance.distanceKm!!)
        assertTrue(endurance.distanceKm!!<=long.distanceKm!!)
    }
    @Test fun complementaryQualityAndBlockVariationAreDeterministic() {
        val p=request()
        val a=generate(p)
        assertEquals(a,generate(p))
        val first=a.sessions.filter { it.date<start.plusWeeks(1)&&it.quality }
        assertEquals(2,first.size)
        assertEquals(2,first.map { it.title }.distinct().size)
        assertTrue(a.sessions.filter { it.quality }.map { it.steps }.distinct().size>2)
        assertEquals("Aerobic foundation",a.request.goal)
    }
    @Test fun fastPlanningPaceFitsASmallerQualityDoseInsteadOfDroppingEverySession() {
        val p=request().copy(weeklyKm=30.0,longestKm=12.0,runDays=4,qualityCount=1,qualityDays=setOf(2),planningPace=240.0)
        assertTrue(generate(p).sessions.any { it.quality })
    }
    @Test fun multiSessionDailyAvailabilityAlsoBindsRunOnlyDays() {
        val p=request().copy(qualityCount=0,allowDoubleDays=true,timeBudgetEnabled=true,minutes=120,dailyMinutes=30)
        val baseline=Engine1.generate(p.copy(policyChoice="baseline",minutes=30),"synthetic","daily",Instant.EPOCH)
        assertTrue(Engine15RunningProgram.compose(p,baseline.sessions).groupBy { it.date }.values.all { day -> day.sumOf { it.minutes }<=30 })
    }
    @Test fun marathonEffortAndProgressionMatchTheirNames() {
        val marathon=generate(request().copy(goal="Marathon",allowedQualityTypes=emptySet(),qualityCount=1))
        assertTrue(marathon.sessions.filter { it.quality }.all { s -> s.steps.none { it.contains("6–7/10") } })
        val progression=generate(request().copy(allowedQualityTypes=setOf("Progression"),qualityCount=1))
        val rx=progression.sessions.first { it.quality }.runningPrescription!!
        val efforts=rx.mixedSegments?.filter { it.purpose=="WORK" }?.map { it.effort }
            ?: rx.segments.filter { it.purpose=="WORK" }.map { it.effort }
        assertTrue(efforts.distinct().size>=2)
    }
    @Test fun positiveMileageBeginnerStillHasWalkRunGuidance() {
        assertTrue(generate(request().copy(experience=0,weeklyKm=10.0,runDays=3,qualityCount=0)).sessions.all { s -> s.steps.any { it.contains("walk",true) } })
    }
    @Test fun persistedStructureCannotContradictSessionDistance() {
        val encoded=generate().encode()
        val first=encoded.getJSONArray("sessions").getJSONObject(0)
        first.put("distanceKm",first.getDouble("distanceKm")+10.0)
        try { PlanSnapshot.decode(encoded,"synthetic"); fail("Contradictory structured distance must be rejected") }
        catch(_:IllegalArgumentException) { }
    }
}

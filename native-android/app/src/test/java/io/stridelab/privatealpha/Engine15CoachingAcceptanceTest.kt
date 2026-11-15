package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15CoachingAcceptanceTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private fun request() = PlanRequest(goal="Aerobic foundation", experience=2, start=monday,
        weeks=16, days=(1..7).toSet(), runDays=6, longDay=7, qualityCount=0,
        weeklyKm=40.0, longestKm=12.0, planningPace=360.0, policyChoice="engine15",
        timeBudgetEnabled=false, targetWeeklyKm=80.0)
    private fun plan(p: PlanRequest=request()) = Engine15.generate(p,"synthetic","acceptance",now)

    @Test fun everySessionRespectsRecentLongestWhenFrequencyFalls() {
        for(days in 1..6) {
            val result=plan(request().copy(runDays=days, weeklyKm=80.0))
            assertTrue("days=$days",result.sessions.all { (it.distanceKm ?: 0.0) <= 13.0 })
            assertTrue(result.sessions.any { (it.distanceKm ?: 0.0) > 0.0 })
        }
    }
    @Test fun zeroOrRoundedAwayTargetNeverProducesPositiveRunningMinutes() {
        for(target in listOf(0.0,0.1)) {
            try { plan(request().copy(targetWeeklyKm=target)); fail("Ambiguous target must require correction") }
            catch(e:PlanConstraint) { assertTrue(e.message.orEmpty().isNotBlank()) }
        }
    }
    @Test fun recoveryReducesDeliveredDoseEvenWhenAvailabilityAlreadyCapsVolume() {
        val p=request().copy(minutes=30,timeBudgetEnabled=true)
        val result=plan(p)
        fun minutes(w:Int)=result.sessions.filter { it.date in monday.plusWeeks(w.toLong())..monday.plusWeeks(w.toLong()).plusDays(6) }.sumOf { it.minutes }
        assertTrue(minutes(3)<minutes(2))
        assertTrue(minutes(7)<minutes(6))
    }
    @Test fun timeOnlyBeginnerAlsoGetsAnActualLighterWeek() {
        val result=plan(request().copy(experience=0,weeklyKm=0.0,longestKm=0.0,runDays=3))
        val weeks=result.sessions.groupBy { (it.date.toEpochDay()-monday.toEpochDay())/7 }
        assertTrue(weeks.getValue(3).sumOf { it.minutes } < weeks.getValue(2).sumOf { it.minutes })
        assertTrue(result.sessions.all { it.distanceKm==null })
    }
    @Test fun unmetTargetAndReturnPlateauAreExplicitReviewDecisions() {
        assertTrue(plan().explanations.any { it.contains("TARGET_NOT_REACHED") })
        assertTrue(plan(request().copy(returning=true)).explanations.any { it.contains("RETURN_REASSESSMENT") })
    }
    @Test fun raceLabelDoesNotClaimReadiness() {
        val p=request().copy(goal="Marathon",weeklyKm=10.0,longestKm=4.0,weeks=4,runDays=3)
        assertTrue(plan(p).explanations.any { it.contains("RACE_READINESS_UNASSESSED") })
    }
    @Test fun explicitRunningHoldSurvivesSerializationAndBlocksPrescription() {
        val encoded=request().encode().put("runningRecoveryHold",true)
        try { plan(PlanRequest.decode(encoded)); fail("Explicit hold must not produce running") }
        catch(e:PlanConstraint) { assertTrue(e.message.orEmpty().contains("review",true)) }
    }
    @Test fun qualityFrequencyUsesReportedPatternInsteadOfFortyKmCliff() {
        fun count(km:Double):Int {
            val encoded=request().copy(weeklyKm=km,qualityCount=2,qualityDays=setOf(2,4),planningPace=480.0).encode()
                .put("recentQualitySessionsPerWeek",2)
            return plan(PlanRequest.decode(encoded)).sessions.filter { it.date < monday.plusWeeks(1) }.count { it.quality }
        }
        assertEquals(2,count(39.9))
        assertEquals(count(39.9),count(40.0))
    }
    @Test fun twoQualitySessionsNeedExplicitRecentFrequencyEvenAtHighMileage() {
        val p=request().copy(weeklyKm=80.0,longestKm=20.0,qualityCount=2,qualityDays=setOf(2,4))
        assertTrue(plan(p).sessions.filter { it.date < monday.plusWeeks(1) }.count { it.quality }<=1)
    }
    @Test fun finalRoundedRecoveryPreservesLongRunShare() {
        var checked=0
        for(km in 3..30) for(longest in 1..10) {
            val p=request().copy(weeks=4,runDays=3,weeklyKm=km.toDouble(),longestKm=longest.toDouble())
            val result=plan(p)
            for(week in result.sessions.groupBy { (it.date.toEpochDay()-monday.toEpochDay())/7 }.values) {
                val total=week.sumOf { it.distanceKm ?: 0.0 }
                val long=week.single { it.kind=="long" }.distanceKm!!
                assertTrue("km=$km longest=$longest total=$total long=$long",long<=total*0.4+1e-8)
                checked++
            }
        }
        assertEquals(1120,checked)
    }
}

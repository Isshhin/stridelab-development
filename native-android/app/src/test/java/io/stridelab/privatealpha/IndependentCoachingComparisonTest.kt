package io.stridelab.privatealpha

import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Deterministic fixtures, no private data. Each variant receives the same scenario. */
class IndependentCoachingComparisonTest {
    private data class Scenario(val name: String, val start: Double, val target: Double,
        val recent: List<Double?>, val days: Int = 6, val shortDay: Double? = null,
        val response: IndependentWeekResponse = IndependentWeekResponse.TOLERATED,
        val longCeiling: Double = 60.0)

    @Test fun compareBaselineConstraintOnlyAndResponsiveOnIdenticalFixtures() {
        val monday = LocalDate.of(2026,11,16)
        val scenarios = listOf(
            Scenario("beginner",10.0,20.0,listOf(10.0,10.0),3),
            Scenario("intermediate",40.0,80.0,listOf(40.0,40.0)),
            Scenario("experienced",100.0,150.0,listOf(100.0,100.0),7),
            Scenario("returning-zero",40.0,80.0,listOf(0.0,0.0)),
            Scenario("repeated-missed",60.0,80.0,listOf(30.0,30.0)),
            Scenario("partial-import",60.0,80.0,listOf(null,30.0)),
            Scenario("recent-spike",60.0,80.0,listOf(60.0,100.0)),
            Scenario("difficult",60.0,80.0,listOf(60.0,60.0),response=IndependentWeekResponse.DIFFICULT),
            Scenario("unknown-response",60.0,80.0,listOf(60.0,60.0),response=IndependentWeekResponse.UNKNOWN),
            Scenario("short-monday",60.0,80.0,listOf(60.0,60.0),shortDay=5.0),
            Scenario("two-days",60.0,80.0,listOf(60.0,60.0),2),
            Scenario("long-run-conflict",60.0,80.0,listOf(60.0,60.0),3,longCeiling=15.0),
            Scenario("planned-recovery-counterexample",60.0,80.0,listOf(60.0,40.0)),
            Scenario("lower-target",60.0,30.0,listOf(60.0,60.0))
        )
        val csv = mutableListOf("scenario,variant,status,km,exceeds_recent_repeat_envelope,reconciliation_error_km,rule")
        for (s in scenarios) {
            val days=(1..s.days).toSet()
            val input=IndependentWeeklyMileageInput(s.start,200.0,s.target,1,days,s.days)
            val limits=IndependentMileageFeasibilityLimits(days.associateWith { if(it==1 && s.shortDay!=null) s.shortDay else 60.0 },
                s.longCeiling,s.longCeiling,0.0)
            val history=IndependentRunningHistoryEvidence((listOf(s.start,s.start)+s.recent).mapIndexed { i, km ->
                IndependentRunningWeekEvidence(monday.minusWeeks((4-i).toLong()),km!=null,s.days,s.days,
                    km?:0.0,0,km?.div(s.days),0,km)
            })
            val variants=listOf("baseline" to IndependentWeeklyMileagePlanner.generate(input),
                "constraints" to IndependentAdaptiveWeek.allocate(input,limits),
                "responsive" to IndependentAdaptiveWeek.respond(input,limits,history,monday,s.response))
            for ((variant,result) in variants) {
                val expectedDeferral = when {
                    variant == "baseline" -> null
                    s.name == "long-run-conflict" -> "NO_FEASIBLE_DISTANCE_ALLOCATION"
                    variant != "responsive" -> null
                    s.name == "returning-zero" -> "RETURN_TO_RUN_REVIEW"
                    s.name == "partial-import" -> "RECENT_COVERAGE_UNKNOWN"
                    s.name == "difficult" -> "RECOVERY_REVIEW"
                    s.name == "unknown-response" -> "RESPONSE_UNKNOWN"
                    else -> null
                }
                assertEquals("${s.name}/$variant", expectedDeferral != null,
                    result is IndependentWeeklyMileageDecision.Deferred)
                if(result is IndependentWeeklyMileageDecision.Deferred) {
                    assertEquals(expectedDeferral, result.ruleId)
                    csv += "${s.name},$variant,deferred,,,,${result.ruleId}"
                } else {
                    result as IndependentWeeklyMileageDecision.Candidate
                    val week=result.weeks.single()
                    val checked=IndependentMileageFeasibility.check(result,limits)
                    val status=if(checked is IndependentMileageFeasibilityResult.Checked) "feasible" else "conflict"
                    assertEquals(if(variant=="baseline" && s.name in setOf("short-monday","long-run-conflict")) "conflict" else "feasible",status)
                    val expectedKm=when {
                        variant=="responsive" && s.name=="repeated-missed" -> 30.0
                        variant=="responsive" && s.name=="planned-recovery-counterexample" -> 40.0
                        else -> minOf(s.start,s.target)
                    }
                    assertEquals(expectedKm,week.weeklyKilometers,0.00001)
                    val repeat=s.recent.takeIf { it.all { km -> km!=null } }?.filterNotNull()?.minOrNull()
                    val exceeds=repeat?.let { week.weeklyKilometers>it+0.00001 }
                    val error=kotlin.math.abs(week.weeklyKilometers-week.dayAllocations.sumOf { it.kilometers })
                    assertTrue(error<0.00001)
                    if(variant!="baseline") assertEquals("feasible",status)
                    val rule=if(checked is IndependentMileageFeasibilityResult.Conflict) checked.ruleId else result.ruleIds.last()
                    csv += "${s.name},$variant,$status,${week.weeklyKilometers},$exceeds,$error,$rule"
                }
            }
        }
        val output=File("build/reports/coaching-comparison.csv")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(csv.joinToString("\n")+"\n")
        assertEquals(43,csv.size)
    }
}

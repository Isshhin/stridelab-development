package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Same synthetic inputs as the M10.5 checkpoint audit; not athlete-effectiveness evidence. */
class Engine15ProgramComparisonTest {
    private val monday=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private fun save(name:String,text:String) {
        val dir=File("build/reports/m106");dir.mkdirs();File(dir,name).writeText(text)
    }
    private fun request()=PlanRequest(goal="Aerobic foundation",experience=2,start=monday,weeks=16,
        days=(1..7).toSet(),runDays=6,longDay=7,qualityDays=setOf(2,4),qualityCount=0,
        weeklyKm=40.0,longestKm=12.0,planningPace=360.0,policyChoice="engine15",timeBudgetEnabled=false,targetWeeklyKm=80.0)
    private fun generate(p:PlanRequest)=Engine15.generate(p,"synthetic-audit","audit",now)

    @Test fun productionProfileMatrix() {
        val base=request()
        val profiles=listOf(
            "intermediate40-target80" to base,
            "experienced80-target100" to base.copy(weeklyKm=80.0,longestKm=20.0,targetWeeklyKm=100.0,qualityCount=2),
            "experienced120" to base.copy(weeklyKm=120.0,longestKm=30.0,targetWeeklyKm=120.0,qualityCount=2),
            "beginner-zero" to base.copy(experience=0,weeklyKm=0.0,longestKm=0.0,targetWeeklyKm=20.0,runDays=3),
            "beginner10" to base.copy(experience=0,weeklyKm=10.0,longestKm=4.0,targetWeeklyKm=20.0,runDays=3),
            "returning40" to base.copy(returning=true,qualityCount=1),
            "marathon-short" to base.copy(goal="Marathon",weeklyKm=10.0,longestKm=4.0,weeks=4,runDays=3),
            "marathon16" to base.copy(goal="Marathon",weeklyKm=60.0,longestKm=12.0,qualityCount=1),
            "half16" to base.copy(goal="Half marathon",weeklyKm=40.0,qualityCount=1),
            "5k16" to base.copy(goal="Improve 5K",qualityCount=1),
            "10k16" to base.copy(goal="Improve 10K",qualityCount=1),
            "two-days80" to base.copy(runDays=2,weeklyKm=80.0,longestKm=12.0),
            "one-day40" to base.copy(runDays=1,weeklyKm=40.0,longestKm=12.0),
            "zero-target" to base.copy(targetWeeklyKm=0.0),
            "tiny-target" to base.copy(targetWeeklyKm=0.1),
            "pace-fast" to base.copy(weeklyKm=30.0,runDays=4,qualityCount=1,qualityDays=setOf(2),planningPace=240.0),
            "pace-slow" to base.copy(weeklyKm=30.0,runDays=4,qualityCount=1,qualityDays=setOf(2),planningPace=600.0),
            "quality39.9" to base.copy(weeklyKm=39.9,qualityCount=2),
            "quality40" to base.copy(weeklyKm=40.0,qualityCount=2),
            "no-longest" to base.copy(longestKm=0.0),
            "mixed-strength" to base.copy(runDays=4,strengthDays=2,qualityCount=1),
            "time-cap30" to base.copy(minutes=30,timeBudgetEnabled=true,qualityCount=1),
            "notes-injury" to base.copy(limitations="Pain during every run; recent stress fracture",recoveryNotes="Exhausted and sleeping poorly")
        )
        val rejected=mutableSetOf<String>()
        val records=JSONArray()
        val weekly=mutableListOf("profile,week,km,minutes,long_km,max_session_km,quality_sessions,quality_minutes")
        for((name,p) in profiles) {
            val row=JSONObject().put("profile",name).put("input",p.encode())
            try {
                val plan=generate(p)
                row.put("status","generated").put("plan",plan.encode())
                plan.sessions.filter { it.discipline=="run" && it.distanceKm!=null }.forEach {
                    val prescription=it.runningPrescription!!
                    if(prescription.mixedSegments!=null) {
                        assertNull(it.distanceKm)
                        assertTrue(prescription.estimatedDistanceKm!!>0.0)
                    } else assertEquals(it.distanceKm!!*1000,prescription.meters.toDouble(),0.001)
                }
                for(w in 0 until p.weeks) {
                    val runs=plan.sessions.filter { it.discipline=="run" && ChronoUnit.DAYS.between(p.start,it.date)/7==w.toLong() }
                    weekly += listOf(name,w+1,runs.sumOf { it.distanceKm?:0.0 },runs.sumOf { it.minutes },
                        runs.singleOrNull { it.kind=="long" }?.distanceKm,runs.mapNotNull { it.distanceKm }.maxOrNull(),
                        runs.count { it.quality },runs.filter { it.quality }.sumOf { it.minutes }).joinToString(",")
                }
                assertEquals(plan,generate(p))
            } catch(e:IllegalArgumentException) { rejected+=name; row.put("status","rejected").put("reason",e.message) }
            records.put(row)
        }
        assertEquals(setOf("zero-target","tiny-target"),rejected)
        assertEquals(23,records.length())
        save("production-profiles.json",records.toString(2));save("production-weeks.csv",weekly.joinToString("\n"))
        assertEquals(generate(base).sessions,generate(base.copy(limitations="Pain during every run; recent stress fracture",recoveryNotes="Exhausted and sleeping poorly")).sessions)
    }

}

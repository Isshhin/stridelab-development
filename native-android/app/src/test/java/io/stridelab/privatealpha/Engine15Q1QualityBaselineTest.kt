package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Ignore

/** Read-only Q1 production-generator audit. Every input and observation is synthetic. */
@Ignore("Q1 frozen baseline belongs to PR #103; v2 uses Engine15Q2MixedUnitTest")
class Engine15Q1QualityBaselineTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val owner = "q1-synthetic"
    private data class Case(val name:String, val request:PlanRequest,
        val history:List<PlanSnapshot> = emptyList(), val evidence:String = "none")

    private fun base() = PlanRequest(
        goal="Aerobic foundation", experience=2, start=monday, weeks=8,
        days=(1..7).toSet(), runDays=6, longDay=7,
        qualityDays=setOf(2,4), qualityCount=2, recentQualitySessionsPerWeek=2,
        weeklyKm=60.0, longestKm=18.0, targetWeeklyKm=60.0,
        planningPace=360.0, preferredQuality="Threshold",
        allowedQualityTypes=setOf("Threshold"), policyChoice="engine15",
        timeBudgetEnabled=false
    )

    /**
     * The current log format stores quality WORK kilometers, NOT quality duration.
     * A label like "30-minute capacity" cannot be verified by this representation.
     * The data below is a deliberately synthetic distance proxy.
     */
    private fun thresholdHistory(label:String, workKm:Double, totalMinutes:Double,
        includeWorkDistance:Boolean=true):List<PlanSnapshot> {
        val observations = listOf(14L,7L).mapIndexed { i,daysAgo ->
            val day = monday.minusDays(daysAgo)
            val workMeters = (workKm*1000).toInt()
            val allMeters = workMeters+2000
            PlannedSession(
                id=label+"-session-"+i, date=day, discipline="run",
                title="Synthetic threshold", minutes=totalMinutes.toInt(),
                distanceKm=allMeters/1000.0, kind="quality", quality=true,
                status="completed", logRevision=1,
                runningPrescription=RunningPrescription("QUALITY","Threshold",listOf(
                    RunningSegment("WARM_UP",1000,"Easy"),
                    RunningSegment("WORK",workMeters,"Controlled"),
                    RunningSegment("COOL_DOWN",1000,"Easy"))),
                log=WorkoutLog(
                    at=day.atTime(12,0).toInstant(ZoneOffset.UTC),
                    minutes=totalMinutes, distanceKm=allMeters/1000.0, effort=6,
                    performedOn=day, completionDifficulty="As planned",
                    discomfort="None",
                    qualityWorkKm=if(includeWorkDistance)workKm else null
                )
            )
        }
        return listOf(PlanSnapshot(
            id=label+"-history",owner=owner,engineVersion=ENGINE15_VERSION,
            policyId=ENGINE15_POLICY,
            createdAt=monday.minusDays(21).atStartOfDay().toInstant(ZoneOffset.UTC),
            request=base().copy(start=monday.minusWeeks(4),weeks=4),
            sessions=observations,explanations=emptyList(),status="approved"
        ))
    }

    private fun fatigueHistory():List<PlanSnapshot> {
        val day=monday.minusDays(1)
        val session=PlannedSession(
            id="fatigue-observation",date=day,discipline="run",title="Difficult run",
            minutes=40,distanceKm=6.0,kind="easy",status="completed",
            logRevision=1,
            log=WorkoutLog(
                at=day.atTime(12,0).toInstant(ZoneOffset.UTC),
                minutes=40.0,distanceKm=6.0,effort=9,performedOn=day,
                completionDifficulty="Harder",discomfort="None"
            )
        )
        return listOf(PlanSnapshot(
            id="fatigue-history",owner=owner,engineVersion=ENGINE15_VERSION,
            policyId=ENGINE15_POLICY,createdAt=now.minusSeconds(172800),
            request=base().copy(start=monday.minusWeeks(4),weeks=4),
            sessions=listOf(session),explanations=emptyList(),status="approved"
        ))
    }

    private fun cases():List<Case> {
        val p=base()
        return listOf(
            Case("01-beginner15",p.copy(experience=0,runDays=3,weeklyKm=15.0,
                longestKm=5.0,targetWeeklyKm=15.0,qualityCount=0,recentQualitySessionsPerWeek=0)),
            Case("02-developing30",p.copy(experience=1,runDays=4,weeklyKm=30.0,
                longestKm=10.0,targetWeeklyKm=30.0,qualityCount=1,recentQualitySessionsPerWeek=1)),
            Case("03-established60",p),
            Case("04-experienced80",p.copy(weeklyKm=80.0,longestKm=24.0,targetWeeklyKm=80.0)),
            Case("05-threshold30-distance-PROXY",p,thresholdHistory("thirty",6.0,50.0),
                "Two 6 km WORK observations. 30min label assumes 5:00/km; engine does NOT verify duration."),
            Case("06-threshold60-distance-PROXY",
                p.copy(weeklyKm=80.0,longestKm=24.0,targetWeeklyKm=80.0),
                thresholdHistory("sixty",12.0,80.0),
                "Two 12 km WORK observations. 60min label assumes 5:00/km; engine does NOT verify duration."),
            Case("07-returning40",p.copy(returning=true,weeklyKm=40.0,longestKm=12.0,
                targetWeeklyKm=40.0,qualityCount=1,recentQualitySessionsPerWeek=1)),
            Case("08-fatigued60",p,fatigueHistory(),"Recent easy run effort=9, completion=Harder."),
            Case("09-three-days-target80",p.copy(runDays=3,weeklyKm=60.0,
                longestKm=16.0,targetWeeklyKm=80.0,qualityCount=1,
                recentQualitySessionsPerWeek=1,qualityDays=setOf(2))),
            Case("10-threshold30-minutes-only",p,thresholdHistory("minutes",6.0,50.0,false),
                "Two quality logs with total minutes but missing qualityWorkKm: duration cannot be admitted."),
            Case("11-quality-frequency-unknown",p.copy(recentQualitySessionsPerWeek=null))
        )
    }

    private fun csv(value:Any?):String {
        val raw=value?.toString() ?: ""
        return "\"" + raw.replace("\"","\"\"") + "\""
    }
    private fun row(vararg cells:Any?)=cells.joinToString(",") { csv(it) }

    @Test fun captureActualGeneratorOutputAcrossEightWeeks() {
        val output=File("build/reports/engine15-q1").apply { mkdirs() }
        val results=JSONArray()
        val weeks=mutableListOf(row(
            "profile","week","weekly_km","planned_total_minutes","quality_count",
            "work_meters","actual_work_minutes_NOT_RECORDED",
            "work_minutes_at_planning_pace_PROXY","work_percent_weekly_distance",
            "work_percent_quality_session_distance","warmup_m","recovery_m",
            "cooldown_m","quality_titles"
        ))
        val steps=mutableListOf(row("profile","week","date","session_id",
            "session_name","session_km","planned_session_minutes","step_number",
            "purpose","meters","effort"))
        var successful=0
        var totalQuality=0

        for(fixture in cases()) {
            val report=JSONObject()
                .put("profile",fixture.name).put("request",fixture.request.encode())
                .put("evidence",fixture.evidence).put("historyPlans",fixture.history.size)
                .put("interpretation","WORK is distance-only; there is no measured quality-work duration.")
            try {
                // Calls the real Engine 1.5 generation pipeline, not a rewritten simulation.
                val plan=Engine15.generateWithHistory(fixture.request,owner,
                    fixture.history,"q1-"+fixture.name,now)
                successful++
                val weekJson=JSONArray()
                for(week in 0 until fixture.request.weeks) {
                    val runs=plan.sessions.filter {
                        it.discipline=="run" &&
                        ChronoUnit.DAYS.between(fixture.request.start,it.date)/7==week.toLong()
                    }
                    val qualities=runs.filter { it.quality }
                    totalQuality+=qualities.size
                    val weekKm=runs.sumOf { it.distanceKm ?: 0.0 }
                    val workM=qualities.sumOf { q ->
                        q.runningPrescription?.segments?.filter { it.purpose=="WORK" }
                            ?.sumOf { it.meters } ?: 0
                    }
                    fun meters(purpose:String)=qualities.sumOf { q ->
                        q.runningPrescription?.segments?.filter { it.purpose==purpose }
                            ?.sumOf { it.meters } ?: 0
                    }
                    val qualityM=qualities.sumOf { it.runningPrescription?.meters ?: 0 }
                    val minutesProxy=workM*fixture.request.planningPace/60000.0
                    val percentWeek=if(weekKm>0)workM/(weekKm*1000)*100 else 0.0
                    val percentQuality=if(qualityM>0)workM.toDouble()/qualityM*100 else 0.0
                    weeks+=row(fixture.name,week+1,weekKm,runs.sumOf { it.minutes },
                        qualities.size,workM,"",minutesProxy,percentWeek,percentQuality,
                        meters("WARM_UP"),meters("RECOVERY"),meters("COOL_DOWN"),
                        qualities.joinToString(" | ") { it.title })
                    for(session in runs) {
                        session.runningPrescription?.segments?.forEachIndexed { index,step ->
                            steps+=row(fixture.name,week+1,session.date,session.id,
                                session.title,session.distanceKm,session.minutes,
                                index+1,step.purpose,step.meters,step.effort)
                        }
                    }
                    weekJson.put(JSONObject().put("week",week+1)
                        .put("weeklyKm",weekKm)
                        .put("qualityCount",qualities.size)
                        .put("workMeters",workM)
                        .put("measuredWorkMinutes",JSONObject.NULL)
                        .put("planningPaceMinutesProxy",minutesProxy)
                        .put("qualitySessions",JSONArray(qualities.map {
                            JSONObject().put("title",it.title).put("date",it.date.toString())
                                .put("distanceKm",it.distanceKm).put("reason",it.reason)
                                .put("segments",JSONArray(it.runningPrescription!!.segments.map { s -> s.encode() }))
                        })))
                }
                report.put("status","generated")
                    .put("weeks",weekJson)
                    .put("explanations",JSONArray(plan.explanations))
            } catch(e:PlanConstraint) {
                report.put("status","rejected_by_policy").put("reason",e.message)
            }
            results.put(report)
        }
        File(output,"q1-profiles-and-weeks.json").writeText(results.toString(2))
        File(output,"q1-weekly-quality.csv").writeText(weeks.joinToString("\n")+"\n")
        File(output,"q1-all-running-steps.csv").writeText(steps.joinToString("\n")+"\n")
        assertTrue("Expected multiple generated profiles; inspect Q1 JSON",successful>=7)
        assertTrue("Expected quality sessions in baseline; inspect Q1 JSON",totalQuality>0)
    }
}

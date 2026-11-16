package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * R0 synthetic execution evidence, not a new training policy or an athlete study.
 * Baseline outputs, per-stage decisions and mixed-unit estimates are recorded
 * separately so later guardrail reviews cannot mistake hypotheses for findings.
 */
class Engine15R0WorkloadLedgerTest {
    private val monday=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private val owner="r0-synthetic-only"
    private fun base()=PlanRequest(
        goal="Aerobic foundation",experience=2,start=monday,weeks=8,
        // Match the actual Train Your Way route (coordinated discipline goal)
        // and its default six available weekdays, not a simplified baseline-only fixture.
        days=setOf(1,2,3,4,5,7),runDays=6,longDay=7,
        disciplineGoals=mapOf("run" to "Aerobic foundation"),
        qualityDays=setOf(2),qualityCount=1,
        recentQualitySessionsPerWeek=1,
        weeklyKm=75.0,longestKm=20.0,targetWeeklyKm=80.0,
        planningPace=360.0,policyChoice="engine15",
        timeBudgetEnabled=false,trainYourWay=true
    )

    private fun write(name:String,content:String) {
        val folder=File("build/reports/engine15-r0")
        assertTrue(folder.isDirectory || folder.mkdirs())
        File(folder,name).writeText(content)
    }

    @Test fun executedSyntheticScenariosProduceStageLedgerWithoutChangingPrescriptions() {
        val p=base()
        val scenarios=listOf(
            "experienced-new-app-75-to-80" to p,
            "large-jump-50-to-80-four-weeks" to p.copy(
                weeklyKm=50.0,longestKm=16.0,weeks=4),
            "developing-30-to-35" to p.copy(
                experience=1,weeklyKm=30.0,longestKm=10.0,
                targetWeeklyKm=35.0,runDays=4,qualityCount=0),
            "beginner-zero-to-20" to p.copy(
                experience=0,weeklyKm=0.0,longestKm=0.0,
                targetWeeklyKm=20.0,runDays=3,qualityCount=0),
            "returning-40-to-50" to p.copy(
                experience=1,weeklyKm=40.0,longestKm=12.0,
                targetWeeklyKm=50.0,runDays=4,qualityCount=0,returning=true),
            "limited-three-days-75-to-80" to p.copy(
                runDays=3,qualityCount=0),
            "experienced-unverified-quality-75-to-80" to p.copy(
                recentQualitySessionsPerWeek=null,qualityCount=1)
        )
        val allEvents=mutableListOf<String>()
        val allSummaries=mutableListOf<String>()
        val overview=mutableListOf("profile,run_days,recent_km,target_km,weeks,peak_km,unknown_weeks,quality_sessions,trace_count")
        for((name,request) in scenarios) {
            val id="r0-$name"
            val unchanged=Engine15.generate(request,owner,id,now)
            val (diagnosed,trace)=Engine15.diagnoseWorkload(
                request,owner,id,now)
            assertEquals("Tracing must never modify output: $name",unchanged,diagnosed)
            assertTrue("Missing baseline stage: $name",
                trace.entries.any { it.ruleId=="ENGINE1_BASELINE_OUTPUT" })
            val weekly=trace.summaries(request,diagnosed)
            assertEquals(request.weeks,weekly.size)
            assertTrue("Missing final diagnostic: $name",
                trace.entries.any { it.ruleId=="FINAL_PRESCRIPTION" ||
                    it.ruleId=="UNKNOWN_TIME_ONLY_FOUNDATION" })
            assertTrue("Missing actual weekly audit: $name",
                weekly.all { it.week in 0 until request.weeks })
            for(summary in weekly) {
                val steps=trace.entries.filter { it.week==summary.week }
                if(request.weeklyKm>0.0) {
                    for(code in listOf(
                        "ENGINE1_BASELINE_OUTPUT","ENGINE15_WEEKLY_CEILING",
                        "SUM_SESSION_CAPS","DISTANCE_ROUNDING","FINAL_PRESCRIPTION"
                    )) {
                        assertTrue("Missing $code in $name week ${summary.week+1}",
                            steps.any { it.ruleId==code })
                    }
                    assertNotNull(summary.baselineKm)
                    assertNotNull(summary.ceilingKm)
                    assertNotNull(summary.assignedKm)
                    assertNotNull(summary.deliveredKm)
                    val final=steps.single { it.ruleId=="FINAL_PRESCRIPTION" }.outputKm
                    assertEquals("Final trace must equal final plan: $name week ${summary.week+1}",
                        final!!,summary.deliveredKm!!,1e-8)
                    val cap=steps.single { it.ruleId=="SUM_SESSION_CAPS" }
                    assertTrue("Effective capacity cannot increase input weekly budget",
                        cap.outputKm!!<=cap.inputKm!!+1e-8)
                    assertTrue("Weekly allocation should remain within the cap",
                        summary.assignedKm!!<=cap.inputKm!!+1e-8)
                } else {
                    assertNull(summary.deliveredKm)
                    assertEquals("UNKNOWN_TIME_ONLY",summary.note)
                }
                assertTrue("Candidate caps must not masquerade as executed reduction",
                    steps.filter { it.stage=="SESSION_LIMIT" }.all { it.outputKm==null })
            }
            assertTrue("Cannot invent exact baseline for a zero-distance athlete",
                request.weeklyKm!=0.0 || weekly.all { it.deliveredKm==null })
            assertTrue("A final estimate must be marked, not an exact distance",
                weekly.all { it.deliveredKm==null || it.estimateInFinal ||
                    it.note=="EXACT_DISTANCE_TARGETS" })
            val eventLines=trace.eventCsv(name).trimEnd().lines()
            val summaryLines=trace.summaryCsv(name,request,diagnosed).trimEnd().lines()
            if(allEvents.isEmpty())allEvents+=eventLines.first()
            if(allSummaries.isEmpty())allSummaries+=summaryLines.first()
            allEvents+=eventLines.drop(1)
            allSummaries+=summaryLines.drop(1)
            val peak=weekly.mapNotNull { it.deliveredKm }.maxOrNull()
            overview+=listOf(name,request.runDays,request.weeklyKm,
                request.targetWeeklyKm,request.weeks,peak,
                weekly.count { it.deliveredKm==null },
                diagnosed.sessions.count { it.quality },
                trace.entries.size).joinToString(",")
        }
        write("executed-rule-events.csv",allEvents.joinToString("\n")+"\n")
        write("executed-weekly-ledger.csv",allSummaries.joinToString("\n")+"\n")
        write("synthetic-scenario-summary.csv",overview.joinToString("\n")+"\n")
        assertEquals(7,scenarios.size)
    }

    @Test fun explicitRecoveryHoldRemainsAnUnchangedHardStop() {
        val held=base().copy(runningRecoveryHold=true)
        val exception=assertThrows(PlanConstraint::class.java) {
            Engine15.diagnoseWorkload(held,owner,"r0-recovery-hold",now)
        }
        assertTrue(exception.message.orEmpty().contains("recovery review"))
    }

    @Test fun ledgerKeepsUnknownDistinctFromZeroAndDoesNotDoubleCountPotentialCaps() {
        val ledger=Engine15WorkloadLedger()
        ledger.record(0,"SESSION_LIMIT","SESSION_EXPOSURE_CAP",
            12.0,8.0,null,"potential cap; not yet executed")
        ledger.record(0,"WEEK_CAPACITY","SUM_SESSION_CAPS",
            50.0,45.0,45.0,"actual capacity bound")
        assertNull(ledger.entries.first().outputKm)
        assertEquals(45.0,ledger.entries.last().outputKm!!,0.0)
        assertTrue(ledger.eventCsv("synthetic").contains("SESSION_EXPOSURE_CAP"))
        assertFalse(ledger.eventCsv("synthetic").contains("NaN"))
    }
}

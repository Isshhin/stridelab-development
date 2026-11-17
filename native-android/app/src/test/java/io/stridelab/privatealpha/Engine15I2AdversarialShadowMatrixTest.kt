package io.stridelab.privatealpha

import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** I2 integration counterexamples: synthetic caller assertions, NEVER coaching authorization. */
class Engine15I2AdversarialShadowMatrixTest {
    private val monday=LocalDate.of(2026,11,16)
    private fun src(usual:Double=75.0,target:Double=80.0,longest:Double=20.0,
                    days:Int=6,weeks:Int=8,returning:Boolean=false,
                    hold:Boolean=false,basis:String="SELF_REPORTED_AVERAGE",
                    peak:Double?=null,exp:Int=2):Engine15IndependentVolumePlanner.Result {
        val p=PlanRequest(goal="Aerobic foundation",experience=exp,start=monday,weeks=weeks,
            days=(1..7).toSet(),runDays=days,longDay=7,qualityCount=0,
            weeklyKm=usual,targetWeeklyKm=target,longestKm=longest,returning=returning,
            runningRecoveryHold=hold,policyChoice="engine15",trainYourWay=true,
            timeBudgetEnabled=false,recentMileageBasis=basis,reportedPeakWeeklyKm=peak)
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(weeks,days,target,returning,hold))
    }
    private fun days(n:Int)=when(n) {
        2->setOf(3,7); 3->setOf(2,4,7); 4->setOf(1,3,5,7)
        5->setOf(1,2,3,5,7); 6->setOf(1,2,3,5,6,7)
        else->error("unsupported I2 run frequency")
    }
    private fun conf(n: Int = 6, allowed: Set<Int> = days(n), start: LocalDate = monday,
                     blocked:Set<LocalDate> = emptySet(),
                     blockedQuality:Set<LocalDate> = emptySet(),
                     other:List<Engine15M6CExternalSession>?=emptyList(),
                     claimed:Boolean=true,
                     quality:List<Engine15M5QualityPreviewRequest> = emptyList(),
                     pair:Boolean=false) = Engine15M6CReviewInput(
        calendar=Engine15M6CalendarInput(start,allowed,7,blocked),
        externalSessions=other,externalCalendarCoverageAsserted=claimed,
        qualityRequests=quality,blockedQualityDates=blockedQuality,
        allowEasyUpperPair=pair)
    private fun q(week:Int=1,slot:Int=2,reviewed:Boolean=true,
                  exposure:Int?=1200,time:Int=3600)=Engine15M5QualityPreviewRequest(
        weekNumber=week,slotNumber=slot,family=IndependentQualityFamily.THRESHOLD,
        fitnessInput=IndependentQualityFitnessInput(
            reportedRecentWorkSeconds=exposure,approvedWorkCeilingSeconds=1200,
            approvedIncreaseSeconds=0,availableSessionSeconds=time,
            qualityAuthorized=true),
        explicitlyReviewedDose=reviewed,reviewReference="synthetic-caller-review")
    private fun x(id:String,date:LocalDate,load:Engine15M6CLoad,
                  discipline:Engine15M6CDiscipline=Engine15M6CDiscipline.STRENGTH)=
        Engine15M6CExternalSession(id,date,discipline,load)

    private data class Case(
        val id:String,val family:String,
        val source:Engine15IndependentVolumePlanner.Result,
        val input:Engine15M6CReviewInput,
        val expectedReason:String? // null => must produce SHADOW candidate
    )

    private fun fixtures():List<Case> {
        val s=src()
        val base=conf()
        val mon=monday
        val tue=LocalDate.of(2026,11,17)
        val thu=LocalDate.of(2026,11,19)
        val fri=LocalDate.of(2026,11,20)
        val sat=LocalDate.of(2026,11,21)
        val sun=LocalDate.of(2026,11,22)
        val cases=mutableListOf<Case>()
        fun add(id:String,family:String,source:Engine15IndependentVolumePlanner.Result=s,
                input:Engine15M6CReviewInput=base,reason:String?=null) {
            require(cases.none { it.id==id })
            cases+=Case(id,family,source,input,reason)
        }
        // Three positive controls: a reject-everything implementation must fail.
        add("ok-six-day-quality-strength","ACCEPT",input=conf(
            quality=listOf(q(1),q(2)),
            other=listOf(x("thu-upper",thu,Engine15M6CLoad.UPPER_BODY))))
        add("ok-two-day-easy","ACCEPT",source=src(
            usual=20.0,target=25.0,longest=20.0,days=2,weeks=1),input=conf(n=2))
        add("ok-returning-no-quality","ACCEPT",source=src(
            usual=40.0,target=50.0,longest=12.0,days=4,returning=true),input=conf(n=4))

        val nonnumeric="UPSTREAM_CALENDAR_BASE_M4B_NON_DISTANCE_STATUS"
        add("baseline-unknown","BASELINE",source=src(usual=0.0,basis="UNKNOWN"),reason=nonnumeric)
        add("baseline-peak-only","BASELINE",source=src(usual=0.0,basis="PEAK_WEEK_ONLY",peak=85.0),reason=nonnumeric)
        add("baseline-legacy","BASELINE",source=src(basis="LEGACY_UNSPECIFIED"),reason=nonnumeric)
        add("baseline-zero","BASELINE",source=src(usual=0.0,target=20.0,longest=0.0,days=3,
            basis="CONFIRMED_ZERO",exp=0),input=conf(n=3),reason=nonnumeric)
        add("baseline-contradictory","BASELINE",source=src(peak=60.0),reason=nonnumeric)
        add("baseline-recovery-hold","BASELINE",source=src(hold=true),reason=nonnumeric)
        add("baseline-longest-missing","BASELINE",source=src(longest=0.0),
            reason="UPSTREAM_CALENDAR_BASE_LONGEST_RUN_EXPOSURE_UNKNOWN")

        add("workload-high-two-day","WORKLOAD",source=src(days=2),input=conf(n=2),
            reason="UPSTREAM_CALENDAR_BASE_TWO_DAY_EXPOSURE_CONFLICT")
        add("workload-tampered","WORKLOAD",
            source=s.copy(weeks=s.weeks.mapIndexed { i,w ->
                if(i==0) w.copy(proposedKm=100.0) else w
            }),reason="UPSTREAM_CALENDAR_BASE_M4B_RESULT_MISMATCH")

        add("calendar-long-unavailable","CALENDAR",input=conf(allowed=(1..6).toSet()),
            reason="UPSTREAM_CALENDAR_LONG_DAY_UNAVAILABLE")
        add("calendar-long-blocked","CALENDAR",input=conf(blocked=setOf(sun)),
            reason="UPSTREAM_CALENDAR_LONG_DAY_BLOCKED")
        add("calendar-days-insufficient","CALENDAR",
            input=conf(allowed=setOf(1,2,3,5,7)),
            reason="UPSTREAM_CALENDAR_INSUFFICIENT_AVAILABLE_DAYS")
        add("calendar-blocked-easy","CALENDAR",input=conf(blocked=setOf(tue)),
            reason="UPSTREAM_CALENDAR_INSUFFICIENT_WEEK_AVAILABILITY")
        add("external-missing","EXTERNAL",input=conf(other=null),
            reason="UPSTREAM_EXTERNAL_CALENDAR_MISSING")
        add("external-unasserted","EXTERNAL",input=conf(claimed=false),
            reason="UPSTREAM_EXTERNAL_CALENDAR_NOT_ASSERTED")
        add("external-id-duplicate","EXTERNAL",input=conf(other=listOf(
            x("same",thu,Engine15M6CLoad.UPPER_BODY),
            x("same",thu.plusWeeks(1),Engine15M6CLoad.UPPER_BODY))),
            reason="UPSTREAM_DUPLICATE_EXTERNAL_ID")
        add("external-id-blank","EXTERNAL",input=conf(other=listOf(
            x(" ",thu,Engine15M6CLoad.UPPER_BODY))),reason="UPSTREAM_INVALID_EXTERNAL_ID")
        add("external-out-of-range","EXTERNAL",input=conf(other=listOf(
            x("before",mon.minusDays(2),Engine15M6CLoad.UPPER_BODY))),
            reason="UPSTREAM_EXTERNAL_DATE_OUTSIDE_REVIEW_WINDOW")
        add("external-same-date","EXTERNAL",input=conf(other=listOf(
            x("upper",thu,Engine15M6CLoad.UPPER_BODY),
            x("conditioning",thu,Engine15M6CLoad.EASY_GENERAL,
                Engine15M6CDiscipline.CONDITIONING))),
            reason="UPSTREAM_MULTIPLE_EXTERNAL_SAME_DATE")
        add("external-same-day-easy-no-optin","EXTERNAL",input=conf(other=listOf(
            x("upper",tue,Engine15M6CLoad.UPPER_BODY))),
            reason="UPSTREAM_RUN_EXTERNAL_SAME_DAY_CONFLICT")
        add("external-lower-before-long","EXTERNAL",source=src(days=3),
            input=conf(n=3,other=listOf(
                x("lower",sat,Engine15M6CLoad.LOWER_BODY))),
            reason="UPSTREAM_EXTERNAL_HARD_TOO_CLOSE_TO_LONG")
        add("external-back-to-back-hard","EXTERNAL",
            source=src(usual=20.0,target=25.0,longest=20.0,days=2,weeks=1),
            input=conf(n=2,other=listOf(
                x("lower-fri",fri,Engine15M6CLoad.LOWER_BODY),
                x("hard-sat",sat,Engine15M6CLoad.FULL_BODY_HARD,
                    Engine15M6CDiscipline.CONDITIONING))),
            reason="UPSTREAM_EXTERNAL_HARD_BACK_TO_BACK")

        add("quality-unreviewed","QUALITY",input=conf(quality=listOf(q(reviewed=false))),
            reason="UPSTREAM_QUALITY_QUALITY_QUALITY_EXTERNAL_REVIEW_MISSING")
        add("quality-exposure-unknown","QUALITY",input=conf(quality=listOf(q(exposure=null))),
            reason="UPSTREAM_QUALITY_QUALITY_QUALITY_QUALITY_EXPOSURE_UNKNOWN")
        add("quality-double-week","QUALITY",input=conf(quality=listOf(q(slot=1),q(slot=2))),
            reason="UPSTREAM_QUALITY_QUALITY_MULTIPLE_QUALITY_REQUESTS_IN_WEEK")
        add("quality-long-slot","QUALITY",input=conf(quality=listOf(q(slot=6))),
            reason="UPSTREAM_QUALITY_QUALITY_QUALITY_ON_LONG_SLOT")
        add("quality-recovery","QUALITY",input=conf(quality=listOf(q(week=4))),
            reason="UPSTREAM_QUALITY_QUALITY_QUALITY_RECOVERY_WEEK_HOLD")
        add("quality-date-blocked","QUALITY",
            input=conf(blockedQuality=setOf(tue),quality=listOf(q())),
            reason="UPSTREAM_QUALITY_QUALITY_DAY_BLOCKED")
        add("quality-saturday-before-long","QUALITY",input=conf(quality=listOf(q(slot=5))),
            reason="UPSTREAM_QUALITY_QUALITY_TOO_CLOSE_TO_LONG")
        add("quality-monday-after-long","QUALITY",
            input=conf(quality=listOf(q(week=2,slot=1))),
            reason="UPSTREAM_QUALITY_QUALITY_TOO_CLOSE_TO_LONG")
        add("quality-monday-lower-before-tuesday","QUALITY",
            input=conf(quality=listOf(q()),other=listOf(
                x("lower",mon,Engine15M6CLoad.LOWER_BODY))),
            reason="UPSTREAM_QUALITY_QUALITY_TOO_CLOSE_TO_OTHER_HARD")
        add("quality-upper-same-day-optin","QUALITY",
            input=conf(quality=listOf(q()),other=listOf(
                x("upper",tue,Engine15M6CLoad.UPPER_BODY)),pair=true),
            reason="UPSTREAM_RUN_EXTERNAL_SAME_DAY_CONFLICT")
        add("quality-returner-hold","QUALITY",
            source=src(usual=40.0,target=50.0,longest=12.0,days=4,returning=true),
            input=conf(n=4,quality=listOf(q(slot=1))),
            reason="UPSTREAM_QUALITY_QUALITY_RETURNING_RUNNER_QUALITY_HOLD")
        add("quality-two-day-hold","QUALITY",
            source=src(usual=20.0,target=25.0,longest=20.0,days=2,weeks=1),
            input=conf(n=2,quality=listOf(q(slot=1))),
            reason="UPSTREAM_QUALITY_QUALITY_INSUFFICIENT_RUN_DAYS_FOR_QUALITY")
        add("quality-no-time","QUALITY",
            input=conf(quality=listOf(q(time=600))),
            reason="UPSTREAM_QUALITY_QUALITY_QUALITY_DOSE_OR_TIME_CONFLICT")
        return cases
    }

    @Test fun allAdversarialCasesMatchTheirPredeclaredEndToEndDiagnostic() {
        val all=fixtures()
        val lines=mutableListOf("case_id,family,expected_decision,actual_decision,expected_reason,actual_reason")
        val reasons=mutableSetOf<String>()
        var accepted=0
        var deferred=0
        for (fixture in all) {
            val result=Engine15M6IndependentPeriodizationReview.audit(
                fixture.source,fixture.input)
            val expected=if(fixture.expectedReason==null)"ACCEPT" else "DEFER"
            val decision: String
            val reason: String
            when(result) {
                is Engine15M6DReviewDecision.Candidate -> {
                    assertNull(fixture.id+" unexpectedly ACCEPTED",fixture.expectedReason)
                    assertEquals(fixture.source.weeks.map { it.proposedKm },
                        result.weeks.map { it.proposedKm })
                    assertTrue(result.allFutureWeeksProvisional)
                    assertFalse(result.actualDistanceVerified)
                    assertFalse(result.qualityIntensityAuthenticated)
                    assertFalse(result.productionReady)
                    assertFalse(result.executable)
                    assertFalse(result.authorizedForProduction)
                    accepted++
                    decision="ACCEPT";reason=""
                }
                is Engine15M6DReviewDecision.Deferred -> {
                    assertNotNull(fixture.id+" unexpectedly DEFERRED: "+result.reasonCode,
                        fixture.expectedReason)
                    assertEquals(fixture.id+" wrong rejection path",
                        fixture.expectedReason,result.reasonCode)
                    assertTrue(result.explanation.isNotBlank())
                    reasons+=result.reasonCode
                    deferred++
                    decision="DEFER";reason=result.reasonCode
                }
            }
            assertEquals(fixture.id,expected,decision)
            lines+=listOf(fixture.id,fixture.family,expected,decision,
                fixture.expectedReason ?: "",reason).joinToString(",")
        }
        assertEquals(38,all.size)
        assertEquals(3,accepted)
        assertEquals(35,deferred)
        assertEquals(all.size+1,lines.size)
        assertTrue("Need genuinely diverse deferral codes",reasons.size>=19)
        assertEquals(setOf("ACCEPT","BASELINE","WORKLOAD","CALENDAR","EXTERNAL","QUALITY"),
            all.map { it.family }.toSet())
        val output=File("build/reports/engine15-i2/adversarial-synthetic-matrix.csv")
        val directory=requireNotNull(output.parentFile)
        assertTrue(directory.isDirectory || directory.mkdirs())
        output.writeText(lines.joinToString("\n")+"\n")
        assertTrue(output.isFile)
        assertEquals(all.size+1,output.readLines().size)
        assertTrue(output.readLines().all { it.split(",").size==6 })
    }

    @Test fun positiveControlsAreDeterministicAndStillNotExecutable() {
        val cases=fixtures().filter { it.expectedReason==null }
        assertEquals(3,cases.size)
        for (fixture in cases) {
            val first=Engine15M6IndependentPeriodizationReview.audit(
                fixture.source,fixture.input)
                as Engine15M6DReviewDecision.Candidate
            val second=Engine15M6IndependentPeriodizationReview.audit(
                fixture.source,fixture.input)
            assertEquals(first,second)
            assertFalse(first.verifiedAthleteResponse)
            assertFalse(first.actualDistanceVerified)
            assertFalse(first.qualityIntensityAuthenticated)
            assertFalse(first.timeBudgetValidated)
            assertFalse(first.productionReady)
            assertFalse(first.executable)
            assertFalse(first.authorizedForProduction)
        }
    }
}

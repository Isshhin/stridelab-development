package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * M4 Stage B: shadow-only prespecified numeric comparisons. No assertion
 * certifies athlete safety, workout feasibility or future target attainment.
 */
class Engine15M4BIndependentVolumePlannerTest {
    private val planner=Engine15IndependentVolumePlanner
    private val monday=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")

    private fun athlete(
        average:Double=75.0,target:Double=80.0,longest:Double=20.0,
        days:Int=6,weeks:Int=8,returning:Boolean=false,experience:Int=2,
        source:String="SELF_REPORTED_AVERAGE",peak:Double?=null,
        recoveryHold:Boolean=false
    ):PlanRequest=PlanRequest(
        goal="Aerobic foundation",experience=experience,
        start=monday,weeks=weeks,days=setOf(1,2,3,4,5,6,7),
        runDays=days,longDay=7,qualityCount=0,weeklyKm=average,
        targetWeeklyKm=target,longestKm=longest,returning=returning,
        timeBudgetEnabled=false,policyChoice="engine15",
        trainYourWay=true,recentMileageBasis=source,
        reportedPeakWeeklyKm=peak,runningRecoveryHold=recoveryHold
    )

    private fun propose(p:PlanRequest):Engine15IndependentVolumePlanner.Result =
        planner.propose(Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(p.weeks,p.runDays,p.targetWeeklyKm,p.returning,p.runningRecoveryHold))

    @Test fun experiencedSeventyFiveToEightyDoesNotUseEngineOneOrClaimEighty() {
        val result=propose(athlete(peak=85.0))
        assertEquals(Engine15IndependentVolumePlanner.Status.DISTANCE_CANDIDATE,result.status)
        val expected=listOf(67.5,71.2,75.0,60.0,71.2,75.0,78.7,62.9)
        assertEquals(expected,result.weeks.map { it.proposedKm })
        assertEquals(78.7,result.peakProposedKm!!,1e-9)
        assertEquals(1.3,result.shortfallToRequestedTargetKm!!,1e-8)
        assertEquals(Engine15IndependentVolumePlanner.TargetStatus.TARGET_NOT_REACHED_IN_BUDGET,
            result.targetStatus)
        assertEquals(2,result.reviewAfterCompletedWeek)
        assertEquals(105.0,result.weeks.first().provisionalSessionCapacityKm!!,1e-9)
        assertEquals(60.0,result.weeks[3].proposedKm!!,1e-9)
        assertEquals(62.9,result.weeks[7].proposedKm!!,1e-9)
        assertTrue(result.reasons.contains("TARGET_NOT_DELIVERED_BY_PLANNING_BUDGET"))
    }

    @Test fun largeJumpFiftyToEightyIsNotForceFilled() {
        val result=propose(athlete(average=50.0,target=80.0,longest=16.0,weeks=4))
        assertEquals(listOf(45.0,47.5,50.0,40.0),
            result.weeks.map { it.proposedKm })
        assertEquals(30.0,result.shortfallToRequestedTargetKm!!,1e-9)
        assertEquals(Engine15IndependentVolumePlanner.TargetStatus.TARGET_NOT_REACHED_IN_BUDGET,
            result.targetStatus)
    }

    @Test fun developingThirtyToThirtyFiveUsesBoundedAndDeloadedWeeks() {
        val result=propose(athlete(average=30.0,target=35.0,longest=10.0,
            days=4,experience=1))
        assertEquals(listOf(27.0,28.5,30.0,24.0),
            result.weeks.take(4).map { it.proposedKm })
        assertEquals(31.5,result.weeks[6].proposedKm!!,1e-9)
        assertEquals(3.5,result.shortfallToRequestedTargetKm!!,1e-9)
    }

    @Test fun provisionalThreeDayCapacityIsUpperBoundNotASchedule() {
        val result=propose(athlete(days=3,longest=20.0))
        assertEquals(54.6,result.weeks.first().provisionalSessionCapacityKm!!,1e-8)
        assertEquals(54.6,result.peakProposedKm!!,1e-8)
        assertTrue(result.weeks.all {
            it.proposedKm!=null && it.proposedKm!!<=54.6+1e-9
        })
        assertTrue(result.weeks.any {
            it.constraintCodes.contains("PROVISIONAL_SESSION_EXPOSURE_CAP")
        })
    }

    @Test fun userDirectedTargetsAreNotCappedByProvisionalLongestRunCapacity() {
        val p=athlete(average=75.0,target=110.0,longest=5.0,days=6,weeks=8)
        val result=planner.propose(Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(8,6,110.0,userDirected=true))
        assertEquals(listOf(110.0,110.0,110.0,88.0,110.0,110.0,110.0,88.0),
            result.weeks.map { it.proposedKm })
        assertEquals(110.0,result.peakProposedKm!!,1e-9)
        assertTrue(result.weeks.all { it.constraintCodes.contains("USER_REQUESTED_NOT_CAPACITY_VALIDATED") })
        assertTrue(result.weeks.filter { it.index % 4 != 3 }.all {
            it.constraintCodes.contains("PROVISIONAL_SESSION_EXPOSURE_WARNING")
        })
        assertEquals(Engine15IndependentVolumePlanner.TargetStatus.TARGET_REACHED_IN_BUDGET,
            result.targetStatus)
    }

    @Test fun returningRunnerUsesOneReturnBoundAndOneRecoveryCap() {
        val result=propose(athlete(average=40.0,target=50.0,longest=12.0,
            days=4,returning=true))
        assertEquals(24.0,result.weeks[0].proposedKm!!,1e-8)
        assertEquals(25.2,result.weeks[1].proposedKm!!,1e-8)
        assertEquals(26.4,result.weeks[2].proposedKm!!,1e-8)
        assertEquals(21.1,result.weeks[3].proposedKm!!,1e-8)
        assertEquals(2,result.reviewAfterCompletedWeek)
        assertTrue(result.weeks[0].constraintCodes.contains("RETURN_FROM_BREAK_CAP"))
    }

    @Test fun unknownPeakOnlyExplicitZeroAndLegacyAreDifferentOutcomes() {
        val scenarios=listOf(
            Triple(athlete(average=0.0,source="UNKNOWN"),
                Engine15IndependentVolumePlanner.Status.NEEDS_BASELINE,"MISSING_TYPICAL_RUNNING_MILEAGE"),
            Triple(athlete(average=0.0,source="PEAK_WEEK_ONLY",peak=100.0),
                Engine15IndependentVolumePlanner.Status.NEEDS_BASELINE,"MISSING_TYPICAL_RUNNING_MILEAGE"),
            Triple(athlete(average=0.0,source="CONFIRMED_ZERO",longest=0.0,
                experience=0,days=3,target=20.0),
                Engine15IndependentVolumePlanner.Status.TIME_ONLY,"CONFIRMED_ZERO_REQUIRES_TIME_ONLY_FOUNDATION"),
            Triple(athlete(source="LEGACY_UNSPECIFIED"),
                Engine15IndependentVolumePlanner.Status.NEEDS_LEGACY_CONFIRMATION,
                "LEGACY_SCALAR_NOT_CONFIRMED_AS_AVERAGE")
        )
        for((p,status,reason) in scenarios) {
            val r=propose(p)
            assertEquals(status,r.status)
            assertTrue(r.weeks.all { it.proposedKm==null })
            assertNull(r.peakProposedKm)
            assertNull(r.shortfallToRequestedTargetKm)
            assertTrue(r.reasons.contains(reason))
        }
    }

    @Test fun invalidPeakAndExplicitRecoveryHoldStopIndependentPlanning() {
        val invalid=propose(athlete(average=75.0,peak=60.0))
        assertEquals(Engine15IndependentVolumePlanner.Status.NEEDS_REVIEW,invalid.status)
        assertTrue(invalid.weeks.all { it.proposedKm==null })
        val held=propose(athlete(recoveryHold=true))
        assertEquals(Engine15IndependentVolumePlanner.Status.RECOVERY_REVIEW,held.status)
        assertTrue(held.weeks.all { it.proposedKm==null })
    }

    @Test fun confidenceIsNotFitnessOrPermissionToRaiseCeiling() {
        val p=athlete()
        val profile=Engine15AthleteBaseline.assess(p)
        val input=Engine15IndependentVolumePlanner.Input(8,6,80.0)
        val reported=planner.propose(profile,input)
        val confirmed=planner.propose(profile.copy(
            confidence=AthleteBaselineConfidence.COMPLETE_FOUR_WEEK_HISTORY,
            status=AthleteBaselineStatus.COMPLETE_HISTORY),input)
        assertEquals(reported.weeks,confirmed.weeks)
        assertEquals(2,reported.reviewAfterCompletedWeek)
        assertNull(confirmed.reviewAfterCompletedWeek)
        assertEquals(78.7,confirmed.peakProposedKm!!,1e-8)
    }

    @Test fun missingLongestRunDoesNotPretendToProveSessionCapacity() {
        val result=propose(athlete(longest=0.0))
        assertEquals(Engine15IndependentVolumePlanner.Status.DISTANCE_CANDIDATE,result.status)
        assertTrue(result.weeks.all { it.provisionalSessionCapacityKm==null })
        assertTrue(result.reasons.contains("SESSION_EXPOSURE_MUST_BE_REVIEWED_BY_M5"))
        assertTrue(result.weeks.all {
            it.constraintCodes.contains("SESSION_EXPOSURE_UNCONFIRMED")
        })
    }

    @Test fun allProposedWeeksHonorCeilingsAndNoDoubleDeload() {
        for(average in listOf(10.0,20.0,30.0,50.0,75.0,100.0))
            for(days in listOf(2,3,4,6))
                for(returning in listOf(false,true)) {
                    val longest=maxOf(1.0,average/days*1.2)
                    val r=propose(athlete(average=average,target=80.0,
                        longest=longest,days=days,returning=returning,weeks=12))
                    assertEquals(12,r.weeks.size)
                    for(i in r.weeks.indices) {
                        val row=r.weeks[i]
                        val v=row.proposedKm!!
                        assertTrue(v>=0.0 && v.isFinite())
                        assertTrue(v<=row.existingPolicyCeilingKm!!+1e-8)
                        assertTrue(v<=row.provisionalSessionCapacityKm!!+1e-8)
                        row.returnFromBreakCeilingKm?.let { assertTrue(v<=it+1e-8) }
                        if(i%4==3) {
                            val prev=r.weeks[i-1].proposedKm!!
                            assertTrue(v<=prev*0.8+1e-8)
                            assertEquals(Engine15IndependentVolumePlanner.WeekKind.RECOVERY,row.kind)
                        }
                    }
                }
    }

    @Test fun writingSyntheticShadowComparisonDoesNotAlterCurrentGenerator() {
        val source=athlete(peak=null)
        val old=Engine15.generate(source,"m4b-synthetic","m4b-legacy",now)
        val new=propose(source)
        assertEquals(78.7,new.peakProposedKm!!,1e-8)
        assertEquals(source,old.request)
        assertEquals(Engine15.generate(source,"m4b-synthetic","m4b-legacy",now),old)
        // R0 allocated values, not delivered Q2 mixed-unit estimates.
        val prior=listOf(67.5,71.2,75.0,54.1,71.2,75.0,78.7,56.4)
        val reportDir=File("build/reports/engine15-m4b")
        assertTrue(reportDir.isDirectory || reportDir.mkdirs())
        val lines=mutableListOf(
            "profile,week,existing_r0_allocated_km,m4b_shadow_km,delta_km,ceiling_km,session_upper_bound_km,is_recovery"
        )
        for(i in new.weeks.indices) {
            val w=new.weeks[i]
            val v=w.proposedKm!!
            lines+=listOf(
                "experienced-75-to-80",i+1,prior[i],v,
                "%.3f".format(java.util.Locale.US,v-prior[i]),
                w.existingPolicyCeilingKm,w.provisionalSessionCapacityKm,
                w.kind==Engine15IndependentVolumePlanner.WeekKind.RECOVERY
            ).joinToString(",")
        }
        File(reportDir,"shadow-weekly-comparison.csv")
            .writeText(lines.joinToString("\n")+"\n")
        assertEquals(9,lines.size)

        // R0 figures are FINAL mixed-unit planning-pace estimates, whereas
        // M4B figures are PRE-COMPOSITION upper budgets. Different units of
        // interpretation: never label their difference as delivered miles.
        val profileCases=listOf(
            Triple("experienced-new-app-75-to-80",athlete(),70.53333333333333),
            Triple("large-jump-50-to-80-four-weeks",
                athlete(average=50.0,longest=16.0,weeks=4),47.03333333333333),
            Triple("developing-30-to-35",
                athlete(average=30.0,target=35.0,longest=10.0,days=4),31.5),
            Triple("beginner-zero-to-20",
                athlete(average=0.0,target=20.0,longest=0.0,days=3,
                    source="CONFIRMED_ZERO",experience=0),null),
            Triple("returning-40-to-50",
                athlete(average=40.0,target=50.0,longest=12.0,days=4,returning=true),28.3),
            Triple("limited-three-days-75-to-80",
                athlete(days=3),54.6),
            Triple("experienced-unverified-quality-75-to-80",athlete(),70.53333333333333)
        )
        val summary=mutableListOf(
            "profile,r0_final_mixed_unit_peak_estimate_km,m4b_precomposition_peak_budget_km,m4b_target_gap_km,status,review_after_completed_week,comparison_basis"
        )
        for((name,profile,r0Estimate) in profileCases) {
            val candidate=propose(profile)
            fun value(km:Double?)=km?.let {
                "%.3f".format(java.util.Locale.US,it)
            } ?: ""
            summary+=listOf(
                name,value(r0Estimate),value(candidate.peakProposedKm),
                value(candidate.shortfallToRequestedTargetKm),
                candidate.status.name,candidate.reviewAfterCompletedWeek?.toString() ?: "",
                "NOT_DIRECTLY_COMPARABLE_R0_FINAL_ESTIMATE_VS_M4B_BUDGET"
            ).joinToString(",")
        }
        File(reportDir,"synthetic-profile-comparison.csv")
            .writeText(summary.joinToString("\n")+"\n")
        assertEquals(8,summary.size)
    }
}

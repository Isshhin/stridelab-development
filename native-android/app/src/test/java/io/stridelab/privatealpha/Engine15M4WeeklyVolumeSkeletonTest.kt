package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** M4 Stage A parity tests, not approval for a changed progression policy. */
class Engine15M4WeeklyVolumeSkeletonTest {
    private val monday=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private val policy=Engine15WeeklyVolumeSkeleton

    private fun request()=PlanRequest(
        goal="Aerobic foundation",experience=2,start=monday,weeks=8,
        days=setOf(1,2,3,4,5,7),runDays=6,longDay=7,
        qualityDays=setOf(2),qualityCount=1,recentQualitySessionsPerWeek=1,
        weeklyKm=75.0,longestKm=20.0,targetWeeklyKm=80.0,
        disciplineGoals=mapOf("run" to "Aerobic foundation"),
        planningPace=360.0,policyChoice="engine15",timeBudgetEnabled=false,
        trainYourWay=true,recentMileageBasis="SELF_REPORTED_AVERAGE",
        reportedPeakWeeklyKm=85.0
    )

    @Test fun predeclaredLegacyCeilingValues() {
        val expected=listOf(67.5,71.25,75.0,60.0,71.25,75.0,78.75,63.0)
        for(w in expected.indices) {
            assertEquals("Week "+(w+1),expected[w],policy.ceiling(w,75.0,80.0),1e-9)
        }
        assertEquals(45.0,policy.ceiling(0,50.0,80.0),1e-9)
        assertEquals(47.5,policy.ceiling(1,50.0,80.0),1e-9)
        assertEquals(50.0,policy.ceiling(2,50.0,80.0),1e-9)
        assertEquals(40.0,policy.ceiling(3,50.0,80.0),1e-9)
    }

    @Test fun originalUntargetedAndTargetedEquationsRemainNumericallyIdentical() {
        for(km in listOf(0.0,10.0,30.0,40.0,75.0,100.0,120.0))
            for(target in listOf(null,20.0,35.0,80.0,120.0))
                for(week in 0 until 16) {
                    val block=week/4
                    val part=week%4
                    val original=if(target==null) {
                        val factor=if(part==3) minOf(0.90,0.76+block*0.04)
                            else minOf(1.10,0.90+part*0.05+block*0.045)
                        km*factor
                    } else {
                        val peak=minOf(target,km*(1.0+block*0.05))
                        if(part==3) peak*0.80
                            else minOf(target,km*(0.90+part*0.05+block*0.05))
                    }
                    assertEquals("km=$km target=$target week=$week",original,
                        policy.ceiling(week,km,target),0.0)
                }
    }

    @Test fun existingWeeklyBudgetAndRecoveryIntersectionIsIdentical() {
        val cases=listOf(
            Triple(67.5,0,null),
            Triple(78.1,2,null),
            Triple(65.3,3,67.63333333333333),
            Triple(85.7,6,null),
            Triple(68.5,7,70.53333333333333)
        )
        for((baseline,w,previous) in cases) {
            val ceiling=policy.ceiling(w,75.0,80.0)
            val initial=kotlin.math.floor(minOf(baseline,ceiling)*10.0+1e-8).toInt()
            val old=if(w%4==3 && previous!=null)
                minOf(initial,kotlin.math.floor(previous*0.8*10.0+1e-8).toInt())
            else initial
            val decision=policy.existingWeekBudget(w,75.0,80.0,baseline,previous)
            assertEquals(old,decision.budgetTenths)
            assertEquals(initial,decision.beforeRecoveryTenths)
            assertEquals(ceiling,decision.policyCeilingKm,0.0)
            assertTrue(decision.budgetKm<=baseline+0.1)
        }
        assertEquals(54.1,policy.existingWeekBudget(
            3,75.0,80.0,65.3,67.63333333333333).budgetKm,1e-9)
        assertEquals(56.4,policy.existingWeekBudget(
            7,75.0,80.0,68.5,70.53333333333333).budgetKm,1e-9)
    }

    @Test fun typedM3ProfileProducesOnlyAnEnvelopeNotWorkouts() {
        val reported=Engine15AthleteBaseline.assess(request())
        assertEquals(AthleteBaselineStatus.PROVISIONAL_AVERAGE,reported.status)
        val outline=policy.outline(reported,80.0,8)
        assertEquals(Engine15WeeklyVolumeSkeleton.POLICY_ID,outline.policyId)
        assertEquals(Engine15WeeklyVolumeSkeleton.BaselineAvailability.DISTANCE_ENVELOPE,
            outline.availability)
        assertEquals(8,outline.weeks.size)
        assertEquals(75.0,outline.baseline.typicalWeeklyKm!!,0.0)
        assertEquals(78.75,outline.weeks[6].ceilingKm!!,1e-9)
        assertEquals(Engine15WeeklyVolumeSkeleton.WeekType.RECOVERY,
            outline.weeks[3].type)
        val higher=reported.copy(
            confidence=AthleteBaselineConfidence.COMPLETE_FOUR_WEEK_HISTORY,
            status=AthleteBaselineStatus.COMPLETE_HISTORY)
        assertEquals(outline.weeks,policy.outline(higher,80.0,8).weeks)
    }

    @Test fun peakOnlyUnknownZeroAndContradictionsNeverInventVolume() {
        val p=request()
        val cases=listOf(
            Triple(p.copy(weeklyKm=0.0,recentMileageBasis="UNKNOWN"),
                Engine15WeeklyVolumeSkeleton.BaselineAvailability.NEEDS_BASELINE,
                "M3_MISSING_TYPICAL_AVERAGE"),
            Triple(p.copy(weeklyKm=0.0,recentMileageBasis="PEAK_WEEK_ONLY"),
                Engine15WeeklyVolumeSkeleton.BaselineAvailability.NEEDS_BASELINE,
                "M3_MISSING_TYPICAL_AVERAGE"),
            Triple(p.copy(weeklyKm=0.0,recentMileageBasis="CONFIRMED_ZERO"),
                Engine15WeeklyVolumeSkeleton.BaselineAvailability.TIME_ONLY_CONFIRMED_ZERO,
                "M3_CONFIRMED_ZERO_TIME_ONLY"),
            Triple(p.copy(recentMileageBasis="SELF_REPORTED_AVERAGE",
                reportedPeakWeeklyKm=60.0),
                Engine15WeeklyVolumeSkeleton.BaselineAvailability.REQUIRES_REVIEW,
                "M3_CONTRADICTION")
        )
        for((input,availability,reason) in cases) {
            val skeleton=policy.outline(Engine15AthleteBaseline.assess(input),80.0,8)
            assertEquals(availability,skeleton.availability)
            assertTrue(skeleton.weeks.all { it.ceilingKm==null })
            assertTrue(skeleton.weeks.all { it.reasonCode==reason })
        }
    }

    @Test fun independentGeneratorProducesDeterministicDistanceAndQualitySessions() {
        val p=request().copy(recentMileageBasis="SELF_REPORTED_AVERAGE", reportedPeakWeeklyKm=null)
        val a=Engine15IndependentPlanGenerator.generateWithHistory(p,"m4-synthetic",emptyList(),"m4-r0",now)
        val b=Engine15IndependentPlanGenerator.generateWithHistory(p,"m4-synthetic",emptyList(),"m4-r0",now)
        assertEquals(a,b)
        assertTrue(a.sessions.any { it.quality })
        assertTrue(a.sessions.any { it.runningPrescription?.mixedSegments!=null })
    }

    @Test fun cannotExceedUpstreamLimitOrCreateCatchUp() {
        for(week in 0 until 12) {
            val decision=policy.existingWeekBudget(
                week,75.0,80.0,5.0,
                if(week%4==3)10.0 else null)
            assertTrue(decision.budgetKm<=5.0+1e-9)
            assertTrue(decision.budgetKm<=policy.ceiling(week,75.0,80.0)+1e-9)
            assertTrue(decision.budgetKm>=0.0)
        }
    }
}

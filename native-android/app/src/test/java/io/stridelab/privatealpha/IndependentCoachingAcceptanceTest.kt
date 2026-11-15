package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentCoachingAcceptanceTest {
    private val monday=LocalDate.of(2026,11,16)
    private val volume=IndependentWeeklyMileageInput(60.0,150.0,150.0,1,setOf(1,2,3,5,6,7),7)
    private val limits=IndependentMileageFeasibilityLimits(volume.runningDays.associateWith {60.0},60.0,60.0,0.0)
    private fun week(i:Int,done:Double=60.0,recovery:Boolean=false)=IndependentObservedTrainingWeek(
        monday.minusWeeks(i.toLong()),if(recovery)done else 60.0,done,true,
        if(recovery) IndependentObservedWeekIntent.PLANNED_RECOVERY else IndependentObservedWeekIntent.BUILD,
        true,IndependentWeekResponse.TOLERATED)
    @Test fun prolongedRecoveryRequiresRebuildingNotOldLoadResumption() {
        val weeks=listOf(week(8))+(7 downTo 1).map { week(it,20.0,true) }
        val out=IndependentMultiweekResponse.preview(IndependentMultiweekResponseInput(volume,limits,monday,weeks))
        assertTrue(out.toString(),out is IndependentMultiweekResponseDecision.Deferred)
        assertEquals("PROLONGED_RECOVERY_REBUILD_REVIEW",(out as IndependentMultiweekResponseDecision.Deferred).ruleId)
    }
    @Test fun approvalReferenceCannotAuthorizeAnUnboundedStep() {
        val out=IndependentMultiweekResponse.preview(IndependentMultiweekResponseInput(volume,limits,monday,
            listOf(week(2),week(1)),50.0,"synthetic-reference"))
        assertTrue(out.toString(),out is IndependentMultiweekResponseDecision.Deferred)
    }
    @Test fun repeatedPartialBuildsCanRepeatObservedLoadWithoutGuessingFitness() {
        for(done in listOf(44.94,45.0,50.0,56.94)) {
            val out=IndependentMultiweekResponse.preview(IndependentMultiweekResponseInput(volume,limits,monday,listOf(week(2,done),week(1,done))))
            assertTrue(out.toString(),out is IndependentMultiweekResponseDecision.Preview)
            out as IndependentMultiweekResponseDecision.Preview
            assertEquals(done,out.volume.weeks.single().weeklyKilometers,0.0001)
            assertFalse(out.authorizedForProduction)
        }
    }
    @Test fun sessionToleranceDoesNotAuthorizeDoubleWeeklyQualityFrequency() {
        val v=IndependentWeeklyMileageInput(60.0,80.0,80.0,5,setOf(1,2,3,5,6,7),6)
        val l=IndependentMileageFeasibilityLimits(v.runningDays.associateWith {25.0},17.0,25.0,2.0)
        val base=IndependentConstrainedMultiweekPlanner.generate(v,l) as IndependentConstrainedMultiweekDecision.Candidate
        val spacing=IndependentQualitySpacingPlanner.propose(base,IndependentQualitySpacingRequest(2)) as IndependentQualitySpacingDecision.Candidate
        val slots=spacing.weeks.first().opportunities.map { IndependentQualityDoseSlotRequest(it.isoDay,IndependentQualityFamily.THRESHOLD,3600,720) }
        val observations=listOf(9L,16L).map { IndependentQualityWorkObservation("synthetic","s$it","a$it",monday.minusDays(it),IndependentQualityFamily.THRESHOLD,720,IndependentWeekResponse.TOLERATED) }
        val out=IndependentQualityDoseProgression.review(base,spacing,IndependentQualityDoseReviewInput("synthetic",monday,1,slots,observations,1440,"synthetic-cap"))
        assertTrue(out.toString(),out is IndependentQualityDoseReviewDecision.Deferred)
    }
    @Test fun staleApprovalCannotInflateRebuildIncrement() {
        val history=listOf(2,1).map { week(it,20.0).copy(plannedKilometers=20.0) }
        val out=IndependentMultiweekResponse.preview(IndependentMultiweekResponseInput(volume,limits,monday,history,3.0,"synthetic"))
        assertTrue(out.toString(),out is IndependentMultiweekResponseDecision.Deferred)
    }
}

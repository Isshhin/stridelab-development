package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * Training-goal integrity only. Does not assert that a particular weekly
 * mileage is safe or achievable for a person without confirmed exposure.
 */
class Engine15MileageTargetIntegrityTest {
    private val start=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T10:00:00Z")
    private val owner="target-integrity-synthetic"
    private fun custom(
        currentKm:Double=0.0,
        targetKm:Double=80.0,
        foundation:Boolean=false,
        ownWay:Boolean=true
    )=PlanRequest(
        goal="Aerobic foundation", experience=if(currentKm==0.0)0 else 2,
        start=start,weeks=8,days=setOf(1,2,3,4,5,7),runDays=6,
        longDay=7,qualityDays=emptySet(),qualityCount=0,
        weeklyKm=currentKm,longestKm=if(currentKm==0.0)0.0 else 20.0,
        targetWeeklyKm=targetKm,planningPace=360.0,
        policyChoice="engine15",timeBudgetEnabled=false,
        trainYourWay=ownWay,foundationAcknowledged=foundation
    )
    private fun plan(p:PlanRequest)=Engine15.generate(p,owner,"reviewed",now)

    @Test fun targetEightyWithoutRecentBaselineIsNotAnEightyKilometrePlan() {
        val request=custom()
        val draft=plan(request)
        assertEquals("Train Your Way",draft.name)
        assertTrue(draft.sessions.filter { it.discipline=="run" }
            .all { it.distanceKm==null && it.minutes<=20+request.weeks*2 })
        val review=Engine15MileageTargetIntegrity.review(draft)!!
        assertEquals(80.0,review.requestedKm,0.0)
        assertEquals(0.0,review.reportedRecentKm,0.0)
        assertNull(review.estimatedPeakKm)
        assertTrue(review.requiresFoundationDecision)
        val e=assertThrows(PlanConstraint::class.java) {
            PlanOperations.approve(draft,emptyList())
        }
        assertTrue(e.message.orEmpty().contains("80.0 km/week"))
        assertTrue(e.message.orEmpty().contains("recent mileage"))
    }

    @Test fun foundationRequiresExplicitlyPersistedDecisionAndUsesHonestName() {
        val request=custom(foundation=true)
        assertEquals(request,PlanRequest.decode(request.encode()))
        val draft=plan(request)
        assertTrue(draft.name.orEmpty().startsWith("Foundation"))
        assertTrue(draft.name.orEmpty().contains("80.0 km/week"))
        assertNull(Engine15MileageTargetIntegrity.review(draft)!!.estimatedPeakKm)
        val restored=PlanSnapshot.decode(draft.encode(),owner)
        assertTrue(restored.request.foundationAcknowledged)
        assertEquals(draft,restored)
        assertEquals("approved",PlanOperations.approve(restored,emptyList()).status)
    }

    @Test fun substantialShortfallWithKnownBaselineRequiresExplicitReview() {
        val draft=plan(custom(currentKm=20.0))
        val review=Engine15MileageTargetIntegrity.review(draft)!!
        assertNotNull(review.estimatedPeakKm)
        assertTrue(review.estimatedPeakKm!!<80.0*0.75)
        assertTrue(review.requiresFoundationDecision)
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.approve(draft,emptyList())
        }
    }

    @Test fun supportedTargetAndUnrelatedLegacyModesAreNotRelabelled() {
        val target=plan(custom(currentKm=80.0,targetKm=40.0))
        assertEquals("Train Your Way",target.name)
        val review=Engine15MileageTargetIntegrity.review(target)!!
        assertFalse(review.requiresFoundationDecision)
        assertFalse(target.request.foundationAcknowledged)
        assertEquals("approved",PlanOperations.approve(target,emptyList()).status)

        val guided=plan(custom(currentKm=20.0,targetKm=80.0,ownWay=false))
        assertNull(guided.name)
        assertFalse(Engine15MileageTargetIntegrity.review(guided)!!.requiresFoundationDecision)
    }

    @Test fun manualBuilderIntentSurvivesCoachSelectionAndSerialization() {
        val fromUi=custom(currentKm=80.0).copy(policyChoice="coordinated",recentMileageBasis="SELF_REPORTED_AVERAGE",timeBudgetEnabled=false)
        val selected=TracksideCoaching.request(fromUi,engine15Active=true)
        assertEquals("engine15",selected.policyChoice)
        assertTrue(selected.trainYourWay)
        assertEquals(80.0,selected.targetWeeklyKm!!,0.0)
        assertEquals(selected,PlanRequest.decode(selected.encode()))
        assertFalse(PlanRequest.decode(custom().copy(trainYourWay=false,
            foundationAcknowledged=false).encode()).trainYourWay)
    }
}

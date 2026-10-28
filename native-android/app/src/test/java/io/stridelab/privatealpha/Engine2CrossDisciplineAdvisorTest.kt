package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine2CrossDisciplineAdvisorTest {
    private val at=Instant.parse("2026-10-28T10:00:00Z")
    private val start=LocalDate.parse("2026-11-02")

    private fun plan():PlanSnapshot {
        val base=Engine1.generate(
            PlanRequest(start=start,weeks=1,days=(1..7).toSet(),runDays=2,
                strengthDays=1,calDays=0,conditioningDays=0,longDay=7),
            "fictional-athlete","plan-a",at)
        return base.copy(status="approved",sessions=listOf(
            PlannedSession("lower",start.plusDays(5),"str","Lower body",60,null,
                "strength",lower=true),
            PlannedSession("long",start.plusDays(6),"run","Long run",120,18.0,"long")
        ))
    }

    private fun evidence():Engine2AthleteContext {
        val run=Engine2Source("fictional-athlete","manual","run-actual",
            at.minusSeconds(6L*86400))
        val strength=Engine2Source("fictional-athlete","manual","strength-actual",
            at.minusSeconds(4L*86400))
        return Engine2AthleteContext("fictional-athlete",at,listOf(
            Engine2WorkoutEvidence("run-actual",Engine2Discipline.RUNNING,
                run.observedAt,Engine2RecordStatus.CONFIRMED,listOf(
                    Engine2Measurement(Engine2Metric.DISTANCE_KM,10.0,run))),
            Engine2WorkoutEvidence("strength-actual",Engine2Discipline.STRENGTH,
                strength.observedAt,Engine2RecordStatus.CONFIRMED,listOf(
                    Engine2Measurement(Engine2Metric.REPS,8.0,strength,"squat",1)))
        ),emptyList())
    }

    @Test fun overlappingLowerBeforeLongSuggestsOnlyAnOptionalDurationReduction() {
        val source=plan()
        val before=source.encode().toString()
        val proposal=Engine2CrossDisciplineAdvisor.proposeReviewableLowerBodyReduction(
            source,evidence(),"review-1",at)!!
        assertEquals("lower",proposal.sessionId)
        assertEquals(60,proposal.originalMinutes)
        assertEquals(45,proposal.suggestedMinutes)
        assertEquals(setOf("run-actual","strength-actual"),proposal.evidenceRecordIds)
        val snapshot=Engine2PlanBridge.preview(source)
        assertTrue(Engine2ReviewPreview.build(snapshot,proposal).eligibleForApproval)
        assertEquals(before,source.encode().toString())
        assertEquals(60,source.sessions.first().minutes)
    }

    @Test fun missingStrengthObservationNeverAuthorizesInferredFatigue() {
        val reducedContext=evidence().copy(workouts=evidence().workouts.filter {
            it.discipline==Engine2Discipline.RUNNING
        })
        assertNull(Engine2CrossDisciplineAdvisor.proposeReviewableLowerBodyReduction(
            plan(),reducedContext,"review-2",at))
    }

    @Test fun completedLowerBodyWorkoutIsProtected() {
        val completed=plan().copy(sessions=plan().sessions.map {
            if(it.id=="lower")it.copy(status="completed")else it
        })
        assertNull(Engine2CrossDisciplineAdvisor.proposeReviewableLowerBodyReduction(
            completed,evidence(),"review-3",at))
    }

    @Test fun rejectionDoesNotChangePlanOrCreateReceipt() {
        val saved=plan()
        val view=Engine2PlanBridge.preview(saved)
        val proposed=Engine2CrossDisciplineAdvisor.proposeReviewableLowerBodyReduction(
            saved,evidence(),"review-4",at)!!
        val rejected=Engine2ProposalWorkflow.decide(view,proposed,Engine2Decision.REJECT,at)
        assertEquals(view,rejected.plan)
        assertEquals(Engine2ProposalStatus.REJECTED,rejected.proposal.status)
        assertTrue(rejected.plan.appliedProposalIds.isEmpty())
    }
}

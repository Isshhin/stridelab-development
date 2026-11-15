package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic safety/contract tests, not evidence of physiological efficacy. */
class Engine15FeedbackTest {
    private val today = LocalDate.parse("2026-11-03")
    private val sourceDay = today.minusDays(1)
    private val targetDay = today.plusDays(1)
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val performed = Instant.parse("2026-11-02T12:00:00Z")

    private fun logged(effort:Int?=9,minutes:Double?=35.0,day:LocalDate?=sourceDay) =
        WorkoutLog(performed,minutes=minutes,effort=effort,performedOn=day)

    private fun plan(
        source:PlannedSession=PlannedSession(
            "source",sourceDay,"run","Easy run",40,6.0,"easy",
            status="completed",log=logged(),logRevision=1
        ),
        target:PlannedSession=PlannedSession(
            "target",targetDay,"run","Easy run",60,8.0,"easy"
        ),
        version:String=ENGINE15_VERSION,
        policy:String=ENGINE15_POLICY
    ) = PlanSnapshot(
        "plan", "fixture-owner", version, policy, now,
        PlanRequest(
            goal="Improve 5K",policyChoice="engine15",start=today.minusDays(8),
            weeks=4,runDays=3,days=(1..7).toSet(),weeklyKm=35.0
        ),
        listOf(source,target),emptyList(),status="approved",revision=7
    )

    @Test fun completedHighEffortCreatesOnlyPreviewWithoutMutatingTheApprovedPlan() {
        val original=plan()
        val preview=Engine15Feedback.preview(original,today)
        assertEquals(1,preview.size)
        val p=preview.single()
        assertEquals(original.revision,p.planRevision)
        assertEquals("source",p.sourceSessionId)
        assertEquals(1,p.sourceLogRevision)
        assertEquals("target",p.targetSessionId)
        assertEquals(45,p.suggestedMinutes)
        assertTrue(p.reason.contains("9/10"))
        assertTrue(p.uncertainty.contains("not a physiological readiness score"))
        assertNull(original.sessions.last().suggestion)
        assertEquals("approved",original.status)
        assertEquals(8.0,original.sessions.last().distanceKm!!,0.0)
        assertEquals(preview,Engine15Feedback.preview(original,today))
    }

    @Test fun partialHighEffortIsExplainedAsPartialAndStillRequiresReview() {
        val original=plan(source=plan().sessions.first().copy(
            status="completed",log=logged(effort=9,minutes=20.0),logRevision=2
        ))
        val proposal=Engine15Feedback.preview(original,today).single()
        assertTrue(proposal.reason.contains("Partial workout (20 of 40 planned minutes)"))
        assertNull(original.sessions.last().suggestion)
        assertEquals(60,original.sessions.last().minutes)
        val staged=Engine15Feedback.stage(original,proposal,today)
        assertEquals(60,staged.sessions.last().effectiveMinutes)
        assertFalse(staged.sessions.last().suggestion!!.accepted)
    }

    @Test fun reviewAcceptRoundtripAndBackupPreserveOriginalTargetsAndActuals() {
        val original=plan()
        val decision=Engine15Feedback.preview(original,today).single()
        val staged=Engine15Feedback.stage(original,decision,today).copy(revision=8)
        val target=staged.sessions.last()
        assertEquals(60,target.minutes)
        assertEquals(60,target.effectiveMinutes) // Pending is never implicitly accepted.
    }

    @Test fun stagedSuggestionAcceptanceAndBackupSurviveWithoutReplanning() {
        val original=plan()
        val decision=Engine15Feedback.preview(original,today).single()
        val staged=Engine15Feedback.stage(original,decision,today).copy(revision=8)
        val pending=staged.sessions.last()
        assertFalse(pending.suggestion!!.accepted)
        assertEquals(60,pending.effectiveMinutes)
        assertEquals(8.0,pending.distanceKm!!,0.0)
        val accepted=PlanOperations.acceptSuggestion(staged,pending.id).copy(revision=9)
        assertEquals(45,accepted.sessions.last().effectiveMinutes)
        assertEquals(60,accepted.sessions.last().minutes)
        assertEquals("planned",accepted.sessions.last().status)
        assertEquals(original.sessions.first(),accepted.sessions.first())
        assertTrue(accepted.sessions.last().suggestion!!.accepted)
        assertEquals(accepted,PlanSnapshot.decode(accepted.encode(),"fixture-owner"))
        val backup=JSONObject().put("schemaVersion",1)
            .put("plans",JSONArray().put(accepted.encode())).toString()
        assertEquals(listOf(accepted),PlanBackup.read(backup,"fixture-owner"))
        assertThrows(IllegalArgumentException::class.java) {
            PlanSnapshot.decode(accepted.encode(),"other-owner")
        }
        assertTrue(Engine15Feedback.preview(accepted,today).isEmpty())
    }

    @Test fun staleOrDifferentPlanAndChangedSourceCannotStage() {
        val original=plan()
        val proposal=Engine15Feedback.preview(original,today).single()
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.stage(original.copy(revision=8),proposal,today)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.stage(original.copy(id="other"),proposal,today)
        }
        val changed=original.copy(sessions=original.sessions.map {
            if(it.id=="source")it.copy(log=logged(effort=2),logRevision=2) else it
        })
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.stage(changed,proposal,today)
        }
        assertEquals(original.sessions,plan().sessions)
    }

    @Test fun missingOrIncompleteInputsNeverCountAsMissedOrRecovered() {
        val original=plan()
        val source=original.sessions.first()
        val variants=listOf(
            source.copy(status="planned",log=null,logRevision=0),
            source.copy(status="in_progress"),
            source.copy(status="completed",log=null,linkedActivityId="source-run"),
            source.copy(log=logged(effort=null)),
            source.copy(log=logged(day=null)),
            source.copy(log=logged(effort=3)),
            source.copy(logRevision=0)
        )
        variants.forEach { s ->
            assertTrue("No proposal for ${s.status}/${s.logRevision}",
                Engine15Feedback.preview(plan(source=s),today).isEmpty())
        }
        assertTrue(Engine15Feedback.preview(plan().copy(engineVersion=ENGINE1_VERSION,policyId=ENGINE1_POLICY),today).isEmpty())
        assertTrue(Engine15Feedback.preview(plan(policy=ENGINE15_FOUNDATION_POLICY),today).isEmpty())
        assertTrue(Engine15Feedback.preview(plan().copy(status="draft"),today).isEmpty())
        assertTrue(Engine15Feedback.preview(plan().copy(status="paused"),today).isEmpty())
        assertTrue(Engine15Feedback.preview(plan(target=plan().sessions.last().copy(discipline="str")),today).isEmpty())
    }

    @Test fun delayedUploadsWrongDatesAndFutureTargetsStayUntouched() {
        val original=plan()
        val old=original.sessions.first().copy(
            date=today.minusDays(9),
            log=logged(day=today.minusDays(9))
        )
        assertTrue(Engine15Feedback.preview(plan(source=old),today).isEmpty())
        val mismatched=original.sessions.first().copy(log=logged(day=today.minusDays(5)))
        assertTrue(Engine15Feedback.preview(plan(source=mismatched),today).isEmpty())
        val finished=original.sessions.last().copy(
            status="completed",log=logged(effort=2,day=today),logRevision=1
        )
        assertTrue(Engine15Feedback.preview(plan(target=finished),today).isEmpty())
        val todayTarget=original.sessions.last().copy(date=today)
        assertTrue(Engine15Feedback.preview(plan(target=todayTarget),today).isEmpty())
        val earlier=original.sessions.last().copy(date=today.minusDays(1))
        assertTrue(Engine15Feedback.preview(plan(target=earlier),today).isEmpty())
    }

    @Test fun missedIllnessProposesRestAndOnlyApprovalSkipsTheFutureSession() {
        val source=plan().sessions.first().copy(
            status="missed",missedReason="Illness",log=null,logRevision=0
        )
        val target=plan().sessions.last().copy(kind="quality",quality=true)
        val p=plan(source=source,target=target)
        val decision=Engine15Feedback.preview(p,today).single()
        assertEquals(0,decision.suggestedMinutes)
        assertEquals("planned",p.sessions.last().status)
        val staged=Engine15Feedback.stage(p,decision,today)
        assertEquals("planned",staged.sessions.last().status)
        val accepted=PlanOperations.acceptSuggestion(staged,"target")
        assertEquals("skipped",accepted.sessions.last().status)
        assertEquals("Recovery rest chosen",accepted.sessions.last().missedReason)
        assertEquals("missed",accepted.sessions.first().status)
    }

    @Test fun otherMissedReasonsAreNotInventedAndNoCatchupIsAdded() {
        val initial=plan()
        val source=initial.sessions.first()
        assertTrue(Engine15Feedback.preview(plan(source=source.copy(
            status="missed",missedReason="Time",log=null)),today).isEmpty())
        val fatigue=plan(source=source.copy(
            status="missed",missedReason="Fatigue",log=null))
        val decision=Engine15Feedback.preview(fatigue,today).single()
        assertEquals(42,decision.suggestedMinutes)
        assertEquals(60,fatigue.sessions.last().minutes)
        assertEquals(8.0,fatigue.sessions.last().distanceKm!!,0.0)
    }

    @Test fun dismissalsStayAuditableAndSuppressRepeats() {
        val original=plan()
        val proposal=Engine15Feedback.preview(original,today).single()
        val dismissed=Engine15Feedback.dismiss(original,proposal,today,now).copy(revision=8)
        assertEquals(original.sessions,dismissed.sessions)
        assertEquals(1,dismissed.feedback.size)
        assertTrue(dismissed.feedback.single().text.contains("dismissed"))
        assertTrue(Engine15Feedback.preview(dismissed,today).isEmpty())
        assertEquals(dismissed,PlanSnapshot.decode(dismissed.encode(),"fixture-owner"))
        val staged=Engine15Feedback.stage(original,proposal,today).copy(revision=8)
        val cleared=Engine15Feedback.dismissStaged(staged,"target",now).copy(revision=9)
        assertNull(cleared.sessions.last().suggestion)
        assertTrue(cleared.feedback.single().text.contains("Evidence: session source, log revision 1"))
        assertTrue(Engine15Feedback.preview(cleared,today).isEmpty())
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.dismissStaged(cleared,"target",now)
        }
        val accepted=PlanOperations.acceptSuggestion(staged,"target")
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.dismissStaged(accepted,"target",now)
        }
    }

    @Test fun duplicateActivityLinksAndUnperformedStrengthSetsAreNotEvidence() {
        val source=plan().sessions.first().copy(linkedActivityId="same-run")
        val extra=plan().copy(
            id="different-plan",sessions=listOf(
                PlannedSession("other",today.minusDays(3),"run","Imported",30,5.0,"easy",
                    status="completed",linkedActivityId="same-run")
            )
        )
        assertTrue(Engine15Feedback.preview(plan(source=source),today,listOf(plan(source=source),extra)).isEmpty())
        val strength=plan().sessions.first().copy(
            discipline="str",exercises=listOf(ExercisePrescription("squat","Squat",2,reps=8)),
            log=logged().copy(sets=listOf(SetRecord("squat",1,reps=0)))
        )
        assertTrue(Engine15Feedback.preview(plan(source=strength),today).isEmpty())
        val performedStrength=strength.copy(log=logged().copy(
            sets=listOf(SetRecord("squat",1,reps=5))
        ))
        assertEquals(1,Engine15Feedback.preview(plan(source=performedStrength),today).size)
    }
}

package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentGoalAwareTrajectoryReplayTest {
    private val first = LocalDate.of(2026, 10, 19)
    private val ownerPlanId = "synthetic-plan-1"
    private val goal = IndependentCoachingGoalIdentity(
        IndependentCoachingGoalMode.TRAIN_YOUR_WAY_MILEAGE,
        targetWeeklyKm = 80.0
    )
    private val volume = IndependentWeeklyMileageInput(
        60.0, 80.0, 80.0, 1,
        setOf(1, 2, 3, 5, 6, 7), 6
    )
    private val limits = IndependentMileageFeasibilityLimits(
        volume.runningDays.associateWith { if (it == 1) 5.0 else 25.0 },
        17.0, 25.0, 2.0
    )
    private fun week(
        index: Int, planned: Double = 60.0, completed: Double? = planned,
        intent: IndependentObservedWeekIntent = IndependentObservedWeekIntent.BUILD,
        reviewed: Boolean = false,
        response: IndependentWeekResponse = IndependentWeekResponse.TOLERATED,
        covered: Boolean = true
    ) = IndependentObservedTrainingWeek(
        first.plusWeeks(index.toLong()),
        planned, completed, covered, intent, reviewed, response
    )
    private fun history(
        weeks: List<IndependentObservedTrainingWeek> = listOf(
            week(0), week(1), week(2), week(3)
        )
    ) = IndependentMultiweekResponseInput(
        volume = volume,
        limits = limits,
        planningMonday = weeks.last().monday.plusWeeks(1),
        completedWeeks = weeks
    )
    private fun ledger(
        weeks: List<IndependentObservedTrainingWeek> = history().completedWeeks,
        planId: String = ownerPlanId,
        dropWeek: LocalDate? = null
    ): IndependentWeeklyEvidenceLedgerDecision.Candidate {
        val audits = weeks.filter { it.monday != dropWeek }.map { recorded ->
            IndependentWeekEvidenceLedger(
                monday = recorded.monday,
                sessions = listOf(
                    IndependentLedgerSession(
                        sessionId = "session-" + recorded.monday,
                        scheduledOn = recorded.monday.plusDays(1),
                        savedStatus = "planned",
                        attribution = IndependentCompletionAttributionDecision.Deferred(
                            "NO_EXPLICIT_SOURCE_LINK", "No matching source event")
                    )
                ),
                observedLinkedKmPartial = 0.0,
                linkedWithKnownDistanceCount = 0,
                attributedButMissingDistanceCount = 0,
                deferredOrUnlinkedSessionCount = 1,
                savedMissedOrSkippedCount = 0
            )
        }
        return IndependentWeeklyEvidenceLedgerDecision.Candidate(
            planId, audits, listOf("SOURCE_COVERAGE_UNVERIFIED")
        )
    }
    private fun run(
        h: IndependentMultiweekResponseInput = history(),
        g: IndependentCoachingGoalIdentity = goal,
        a: IndependentWeeklyEvidenceLedgerDecision = ledger(h.completedWeeks)
    ) = IndependentGoalAwareTrajectoryReplay.replay(
        IndependentTrajectoryReplayInput(ownerPlanId, g, h, a)
    )
    private fun trace(
        h: IndependentMultiweekResponseInput = history(),
        g: IndependentCoachingGoalIdentity = goal,
        a: IndependentWeeklyEvidenceLedgerDecision = ledger(h.completedWeeks)
    ): IndependentTrajectoryReplayDecision.Trace {
        val r = run(h, g, a)
        assertTrue(r.toString(), r is IndependentTrajectoryReplayDecision.Trace)
        return r as IndependentTrajectoryReplayDecision.Trace
    }
    private fun reject(
        ruleId: String,
        h: IndependentMultiweekResponseInput = history(),
        g: IndependentCoachingGoalIdentity = goal,
        a: IndependentWeeklyEvidenceLedgerDecision = ledger(h.completedWeeks)
    ) {
        val result = run(h, g, a)
        assertTrue(result.toString(), result is IndependentTrajectoryReplayDecision.Deferred)
        assertEquals(ruleId, (result as IndependentTrajectoryReplayDecision.Deferred).ruleId)
    }

    @Test fun fourCompletedBuildWeeksYieldThreePastOnlyCheckpoints() {
        val out = trace()
        assertEquals(3, out.checkpoints.size)
        assertEquals(3, out.comparisonCount)
        assertEquals(0, out.deferredCount)
        assertEquals(listOf(
            first.plusWeeks(2), first.plusWeeks(3), first.plusWeeks(4)
        ), out.checkpoints.map { it.planningMonday })
        assertEquals(listOf(60.0, 60.0, 60.0), out.checkpoints.map { it.proposedKm })
        assertFalse(out.productionAuthorized)
        assertFalse(out.sourceEvidenceVerified)
    }

    @Test fun twoMissedBuildsAndTwoRebuildWeeksExposeDecisionTransition() {
        val observed = history(listOf(
            week(0, completed = 30.0),
            week(1, completed = 30.0),
            week(2, planned = 30.0),
            week(3, planned = 30.0)
        ))
        val out = trace(observed)
        assertEquals(30.0, out.checkpoints[0].proposedKm!!, 0.00001)
        assertNull(out.checkpoints[1].proposedKm)
        assertEquals("RESPONSE_MIXED_OR_PARTIAL_COMPLETION_REVIEW", out.checkpoints[1].ruleId)
        assertEquals(30.0, out.checkpoints[2].proposedKm!!, 0.00001)
        assertEquals(2, out.comparisonCount)
        assertEquals(1, out.deferredCount)
    }

    @Test fun plannedRecoveryAndFollowingBuildAreNotNaivelyAutoProgressed() {
        val observed = history(listOf(
            week(0),
            week(1, planned = 40.0,
                intent = IndependentObservedWeekIntent.PLANNED_RECOVERY,
                reviewed = true),
            week(2)
        ))
        val out = trace(observed)
        assertEquals("REVIEWED_RECOVERY_RESUME_PRIOR_BUILD_NO_CATCHUP",
            out.checkpoints[0].ruleId)
        assertEquals(60.0, out.checkpoints[0].proposedKm!!, 0.00001)
        assertNull(out.checkpoints[1].proposedKm)
        assertEquals("RESPONSE_MIXED_INTENT_REBUILD_REVIEW", out.checkpoints[1].ruleId)
    }

    @Test fun missingMiddleWeekAuditDefersOnlyCheckpointsThatNeedThatWeek() {
        val h = history()
        val a = ledger(h.completedWeeks, dropWeek = first.plusWeeks(1))
        val out = trace(h, a = a)
        assertEquals("M7_RECENT_WEEK_NOT_AUDITED", out.checkpoints[0].ruleId)
        assertEquals("M7_RECENT_WEEK_NOT_AUDITED", out.checkpoints[1].ruleId)
        assertEquals(60.0, out.checkpoints[2].proposedKm!!, 0.00001)
    }

    @Test fun earlierUnknownToleranceRemainsAVisibleReviewHold() {
        val h = history(listOf(
            week(0, response = IndependentWeekResponse.UNKNOWN),
            week(1), week(2), week(3)
        ))
        val out = trace(h)
        assertTrue(out.checkpoints.all { it.ruleId == "RESPONSE_TOLERANCE_RESPONSE_UNKNOWN" })
        assertEquals(3, out.deferredCount)
        assertNull(out.finalProposedKm)
    }

    @Test fun replayNeverUsesLaterObservedKmForEarlierDecision() {
        val original = history()
        val altered = history(original.completedWeeks.dropLast(1) + week(3, completed = 30.0))
        val earlier = trace(original).checkpoints.take(2)
        assertEquals(earlier, trace(altered).checkpoints.take(2))
    }

    @Test fun explicitLowerTargetIsPreservedAcrossEveryCheckpoint() {
        val h = history().copy(volume = volume.copy(requestedTargetWeeklyKm = 40.0))
        val g = goal.copy(targetWeeklyKm = 40.0)
        val out = trace(h, g)
        assertEquals(40.0, out.requestedTargetWeeklyKm, 0.00001)
        assertTrue(out.checkpoints.all { it.proposedKm == null || it.proposedKm!! <= 40.01 })
    }

    @Test fun missingTargetOrTargetMismatchCannotBecomeRacePreparation() {
        reject("MILEAGE_TARGET_REQUIRED", g = goal.copy(targetWeeklyKm = null))
        reject("MILEAGE_TARGET_NOT_IN_VOLUME_REQUEST",
            h = history().copy(volume = volume.copy(requestedTargetWeeklyKm = null)))
        reject("MILEAGE_TARGET_IDENTITY_MISMATCH",
            g = goal.copy(targetWeeklyKm = 70.0))
        reject("MILEAGE_GOAL_CONTAINS_RACE_FIELDS",
            g = goal.copy(raceDistanceKm = 10.0))
    }

    @Test fun raceGoalCannotSilentlyReuseWeeklyMileageProgression() {
        reject("RACE_SPECIFIC_PERIODIZATION_NOT_QUALIFIED",
            g = IndependentCoachingGoalIdentity(
                IndependentCoachingGoalMode.RACE_PREPARATION,
                raceDistanceKm = 10.0,
                raceDate = LocalDate.of(2027, 1, 10)
            ))
        reject("INVALID_RACE_GOAL_IDENTITY",
            g = IndependentCoachingGoalIdentity(
                IndependentCoachingGoalMode.RACE_PREPARATION,
                targetWeeklyKm = 80.0, raceDistanceKm = 10.0,
                raceDate = LocalDate.of(2027, 1, 10)
            ))
    }

    @Test fun historicalOrMissingRaceDateCannotQualify() {
        reject("RACE_DATE_NOT_FUTURE",
            g = IndependentCoachingGoalIdentity(
                IndependentCoachingGoalMode.RACE_PREPARATION,
                raceDistanceKm = 21.1,
                raceDate = history().planningMonday
            ))
        reject("INVALID_RACE_GOAL_IDENTITY",
            g = IndependentCoachingGoalIdentity(
                IndependentCoachingGoalMode.RACE_PREPARATION,
                raceDistanceKm = 42.195
            ))
    }

    @Test fun mismatchedLedgerOwnerPlanOrRejectedLedgerCannotReplay() {
        val h = history()
        reject("M7_LEDGER_PLAN_MISMATCH", a = ledger(h.completedWeeks, "different-plan"))
        reject("M7_LEDGER_UNAVAILABLE", a =
            IndependentWeeklyEvidenceLedgerDecision.Deferred(
                "ACTIVITY_OWNER_SCOPE_CONFLICT", "Synthetic other account"
            ))
    }

    @Test fun tooFewWeeksAndStalePlanningMondayAreRejected() {
        val onlyTwo = history(listOf(week(0), week(1)))
        reject("INSUFFICIENT_REPLAY_HISTORY", h = onlyTwo)
        val h = history()
        reject("STALE_TRAJECTORY_HISTORY",
            h = h.copy(planningMonday = h.planningMonday.plusWeeks(1)))
    }

    @Test fun noCheckpointCanTurnIntoAuthorizedOrExecutableWorkout() {
        val out = trace()
        assertTrue(out.checkpoints.all { !it.executable })
        assertFalse(out.productionAuthorized)
        assertTrue("NO_PRODUCTION_AUTHORIZATION" in out.ruleIds)
        assertEquals(out, run())
    }
}

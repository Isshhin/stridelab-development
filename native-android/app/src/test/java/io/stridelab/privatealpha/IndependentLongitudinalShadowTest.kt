package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentLongitudinalShadowTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val next = monday.plusWeeks(1)
    private val planning = next.plusWeeks(1)
    private val volume = IndependentWeeklyMileageInput(
        60.0, 80.0, 80.0, 1, setOf(1, 2, 3, 5, 6, 7), 6
    )
    private val limits = IndependentMileageFeasibilityLimits(
        volume.runningDays.associateWith { if (it == 1) 5.0 else 25.0 },
        17.0, 25.0, 2.0
    )
    private fun week(
        date: LocalDate, planned: Double = 60.0, completed: Double? = 60.0,
        intent: IndependentObservedWeekIntent = IndependentObservedWeekIntent.BUILD,
        reviewed: Boolean = false,
        tolerance: IndependentWeekResponse = IndependentWeekResponse.TOLERATED,
        covered: Boolean = true
    ) = IndependentObservedTrainingWeek(
        date, planned, completed, covered, intent, reviewed, tolerance
    )
    private fun response(
        weeks: List<IndependentObservedTrainingWeek> = listOf(
            week(monday), week(next)
        )
    ) = IndependentMultiweekResponseInput(volume, limits, planning, weeks)

    private fun ledger(
        id: String = "plan-1", linkedKm: Double = 0.0,
        hasLinks: Boolean = false, both: Boolean = true
    ): IndependentWeeklyEvidenceLedgerDecision.Candidate {
        fun entry(date: LocalDate): IndependentWeekEvidenceLedger {
            val row = IndependentLedgerSession(
                "session-" + date, date.plusDays(1),
                if (hasLinks) "completed" else "planned",
                if (hasLinks)
                    IndependentCompletionAttributionDecision.LinkedObservation(
                        id, "session-" + date, "source-" + date,
                        date.plusDays(1), date.plusDays(1), "garmin",
                        linkedKm, 1800.0, true, false, false
                    )
                else IndependentCompletionAttributionDecision.Deferred(
                    "NO_EXPLICIT_SOURCE_LINK", "Synthetic unlinked session"
                )
            )
            return IndependentWeekEvidenceLedger(
                monday = date,
                sessions = listOf(row),
                observedLinkedKmPartial = if (hasLinks) linkedKm else 0.0,
                linkedWithKnownDistanceCount = if (hasLinks) 1 else 0,
                attributedButMissingDistanceCount = 0,
                deferredOrUnlinkedSessionCount = if (hasLinks) 0 else 1,
                savedMissedOrSkippedCount = 0
            )
        }
        return IndependentWeeklyEvidenceLedgerDecision.Candidate(
            id, if (both) listOf(entry(monday), entry(next)) else listOf(entry(monday)),
            listOf("CALLER_ASSERTED_SCOPE")
        )
    }
    private fun result(
        input: IndependentMultiweekResponseInput = response(),
        evidence: IndependentWeeklyEvidenceLedgerDecision = ledger(),
        planId: String = "plan-1"
    ) = IndependentLongitudinalShadow.compare(
        IndependentLongitudinalShadowInput(planId, input, evidence)
    )
    private fun accepted(
        input: IndependentMultiweekResponseInput = response(),
        evidence: IndependentWeeklyEvidenceLedgerDecision = ledger()
    ): IndependentLongitudinalShadowDecision.Comparison {
        val r = result(input, evidence)
        assertTrue(r.toString(), r is IndependentLongitudinalShadowDecision.Comparison)
        return r as IndependentLongitudinalShadowDecision.Comparison
    }
    private fun reject(
        rule: String,
        input: IndependentMultiweekResponseInput = response(),
        evidence: IndependentWeeklyEvidenceLedgerDecision = ledger(),
        planId: String = "plan-1"
    ) {
        val r = result(input, evidence, planId)
        assertTrue(r.toString(), r is IndependentLongitudinalShadowDecision.Deferred)
        assertEquals(rule, (r as IndependentLongitudinalShadowDecision.Deferred).ruleId)
    }

    @Test fun twoUndercompletedBuildWeeksProposeOnlyAThirtyKmShadow() {
        val scenario = response(listOf(
            week(monday, completed = 30.0),
            week(next, completed = 30.0)
        ))
        val compared = accepted(scenario)
        assertEquals(60.0, compared.m6aBaselineKm, 0.00001)
        assertEquals(30.0, compared.assertedResponseKm, 0.00001)
        assertEquals(-30.0, compared.deltaKm, 0.00001)
        assertEquals("REPEATED_UNDERCOMPLETION_REDUCED_REPEAT_ONLY", compared.responseReason)
    }
    @Test fun reviewedRecoveryIsNotMisclassifiedAsMissedBuild() {
        val scenario = response(listOf(
            week(monday),
            week(next, planned = 40.0, completed = 40.0,
                intent = IndependentObservedWeekIntent.PLANNED_RECOVERY, reviewed = true)
        ))
        val compared = accepted(scenario)
        assertEquals(60.0, compared.assertedResponseKm, 0.00001)
        assertEquals("REVIEWED_RECOVERY_RESUME_PRIOR_BUILD_NO_CATCHUP", compared.responseReason)
    }
    @Test fun completedBuildWeeksDoNotIncreaseWithoutSeparateReview() {
        val compared = accepted()
        assertEquals(60.0, compared.assertedResponseKm, 0.00001)
        assertEquals(0.0, compared.deltaKm, 0.00001)
        assertFalse(compared.authorizedForProduction)
        assertFalse(compared.authorizedForPlanMutation)
    }
    @Test fun incompleteLinkedEvidenceRemainsVisibleAndUnauthorized() {
        val compared = accepted()
        assertTrue("M7_PARTIAL_OR_DISPUTED_SESSION_LINKS" in compared.unresolvedRuleIds)
        assertEquals(2, compared.partialLinkedKmByWeek.size)
        assertEquals("M7_SOURCE_AND_COVERAGE_NOT_AUTHENTICATED",
            IndependentLongitudinalShadow.productionAdmission(compared).ruleId)
    }
    @Test fun completelyLinkedPlanStillIsNotCompleteAthleteCoverage() {
        val compared = accepted(evidence = ledger(linkedKm = 50.0, hasLinks = true))
        assertFalse(compared.evidenceVerified)
        assertFalse(compared.authorizedForProduction)
        assertTrue("M7_PARTIAL_LINKED_KM_NOT_COMPLETE_WEEK" in compared.unresolvedRuleIds)
    }
    @Test fun partialLinkedSourceKilometersCannotExceedClaimedTotal() {
        val scenario = response(listOf(
            week(monday, completed = 30.0), week(next, completed = 30.0)
        ))
        reject("LINKED_DISTANCE_EXCEEDS_ASSERTED_TOTAL",
            scenario, ledger(linkedKm = 40.0, hasLinks = true))
    }
    @Test fun mismatchedPlanAndMissingWeekAuditsAreRejected() {
        reject("LEDGER_PLAN_MISMATCH", evidence = ledger(id = "another-plan"))
        reject("M7_RECENT_WEEK_NOT_AUDITED", evidence = ledger(both = false))
    }
    @Test fun unknownToleranceAndCoverageDeferInsteadOfAssumingReadiness() {
        reject("RESPONSE_TOLERANCE_RESPONSE_UNKNOWN", response(listOf(
            week(monday), week(next, tolerance = IndependentWeekResponse.UNKNOWN)
        )))
        reject("RESPONSE_COMPLETE_WEEK_EVIDENCE_UNKNOWN", response(listOf(
            week(monday), week(next, covered = false)
        )))
    }
    @Test fun anUnavailableM7LedgerCannotBecomeAnAdaptiveProposal() {
        reject("M7_LEDGER_UNAVAILABLE", evidence =
            IndependentWeeklyEvidenceLedgerDecision.Deferred(
                "PLAN_OWNER_SCOPE_CONFLICT", "Mixed accounts"
            ))
    }
    @Test fun multiweekForesightWithoutFutureCompletionIsBlocked() {
        reject("NEXT_WEEK_ONLY", response().copy(volume = volume.copy(weeks = 5)))
    }
    @Test fun repeatRunProducesIdenticalAndAlwaysNonExecutableShadow() {
        val a = result()
        val b = result()
        assertEquals(a, b)
        assertTrue(a is IndependentLongitudinalShadowDecision.Comparison)
        val comparison = a as IndependentLongitudinalShadowDecision.Comparison
        assertFalse(comparison.evidenceVerified)
        assertFalse(comparison.authorizedForPlanMutation)
        assertFalse(comparison.authorizedForProduction)
    }
}

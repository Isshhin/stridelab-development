package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentResearchDraftTest {
    private val request = PlanRequest(
        goal = "General fitness", start = LocalDate.of(2026, 11, 16),
        experience = 2, weeks = 5,
        days = setOf(1, 2, 3, 5, 6, 7), runDays = 6,
        longDay = 6, qualityCount = 1, qualityDays = setOf(2),
        weeklyKm = 60.0, targetWeeklyKm = 80.0
    )
    private val limits = IndependentAndroidPreviewConstraints(
        selectedRunDays = request.days,
        explicitlyEnteredStartingWeeklyKm = 60.0,
        explicitlyEnteredWeeklyCeilingKm = 80.0,
        explicitlyEnteredMaxKmByDay = request.days.associateWith {
            if (it == 1) 5.0 else 25.0
        },
        explicitlyEnteredPreviousLongRunKm = 17.0,
        explicitlyEnteredLongRunCeilingKm = 25.0,
        explicitlyEnteredMaxLongRunIncreaseKm = 2.0
    )
    private fun preview(): IndependentAndroidPreviewDecision.ReadOnly {
        val result = IndependentAndroidResearchBridge.preview(request, limits)
        assertTrue(result.toString(), result is IndependentAndroidPreviewDecision.ReadOnly)
        return result as IndependentAndroidPreviewDecision.ReadOnly
    }
    private fun draft() = IndependentResearchDraft.fromPreview(
        "synthetic-device-profile", request, preview(), limits,
        id = "0fa768cd-1f1a-47f8-85fb-4c4d6514dce7",
        createdAt = Instant.parse("2026-11-15T00:00:00Z")
    )

    @Test fun existingCoachIsOnlyRouteAllowedToGenerateARealPlan() {
        assertTrue(IndependentEngineRouteGate.permitsProductionGeneration(
            IndependentEngineRoute.EXISTING_COACH))
        assertFalse(IndependentEngineRouteGate.permitsProductionGeneration(
            IndependentEngineRoute.RESEARCH_COMPARISON))
        assertTrue(IndependentEngineRouteGate.permitsResearchSave(
            IndependentEngineRoute.RESEARCH_COMPARISON))
    }

    @Test fun researchCannotBecomeProductionByFallbackOrCast() {
        try {
            IndependentEngineRouteGate.requireProduction(IndependentEngineRoute.RESEARCH_COMPARISON)
            fail("Research route must not reach a PlanGenerationEngine")
        } catch (expected: PlanConstraint) {
            assertTrue(expected.message!!.contains("non-executable"))
        }
        assertFalse(draft().executable)
    }

    @Test fun previewCreatesVersionedDistanceOnlyWeeksWithoutExecutableSessions() {
        val draft = draft()
        assertEquals(5, draft.weeks.size)
        assertEquals(listOf(1,2,3,4,5), draft.weeks.map { it.week })
        assertEquals(listOf(2), draft.weeks.first().days.filter {
            it.qualityOpportunityOnly
        }.map { it.isoDay })
        assertTrue(draft.weeks[3].days.none { it.qualityOpportunityOnly })
        assertFalse(draft.executable)
        assertFalse(draft.sourceEvidenceVerified)
        assertFalse(draft.authorizedForPlanMutation)
        assertFalse(draft.authorizedForProduction)
        assertEquals(IndependentResearchDraft.POLICY_ID, draft.policyId)
        assertEquals(limits, draft.inputConstraints)
    }

    @Test fun researchDraftPreservesEveryOriginalMileageDayAndWeek() {
        val saved = draft()
        val live = preview().mileage.mileage.weeks
        for ((left, right) in saved.weeks.zip(live)) {
            assertEquals(right.weeklyKilometers, left.weeklyKilometers, 0.00001)
            assertEquals(right.dayAllocations.map { it.kilometers },
                left.days.map { it.kilometers })
            assertEquals(left.weeklyKilometers, left.days.sumOf { it.kilometers }, 0.00001)
        }
    }

    @Test fun incompatibleOwnerOrRunningScheduleCannotBeConstructed() {
        val original = draft()
        assertThrows(IllegalArgumentException::class.java) {
            original.copy(selectedRunDays = setOf(2, 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            original.copy(request = original.request.copy(weeks = 4))
        }
    }

    @Test fun duplicateDaysAndForgedQualityOnLongRunFailClosed() {
        val original = draft()
        val first = original.weeks.first()
        assertThrows(IllegalArgumentException::class.java) {
            first.copy(days = first.days.dropLast(1) + first.days.first())
        }
        assertThrows(IllegalArgumentException::class.java) {
            first.days.single { it.longRunCandidate }.copy(qualityOpportunityOnly = true)
        }
    }

    @Test fun tamperingWithDailyCapCannotMakeExistingMileageAppearFeasible() {
        val original = draft()
        assertThrows(IllegalArgumentException::class.java) {
            original.copy(inputConstraints = original.inputConstraints.copy(
                explicitlyEnteredMaxKmByDay =
                    original.inputConstraints.selectedRunDays.associateWith { 0.1 }
            ))
        }
    }

    @Test fun noLaterWeekIsMarkedExecutableEvenIfSpacingSuggestsQuality() {
        assertTrue(draft().weeks.flatMap { it.days }.all {
            !it.qualityOpportunityOnly || !it.longRunCandidate
        })
        assertEquals(draft(), draft())
    }
}

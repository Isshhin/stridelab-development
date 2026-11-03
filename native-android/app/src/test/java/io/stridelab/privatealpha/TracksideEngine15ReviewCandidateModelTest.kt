package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure display-contract tests: no athlete account, Engine 1.0 logic or persistent
 * workout is read or modified. UI candidate is intentionally not routed.
 */
class TracksideEngine15ReviewCandidateModelTest {
    private fun candidate(stage: TracksideReviewStage, proposed: Int? = 45,
        distance: Double? = 8.0
    ) = TracksideReviewCandidate(
        stage, "Easy run", "THU 05 NOV · RUNNING", 60, distance, proposed,
        "Mon 02 Nov · Recorded easy run", "35 min · RPE 9/10 (self-reported)"
    )

    @Test fun pendingAndPreviewAlwaysKeepOriginalActiveTargets() {
        for (stage in listOf(TracksideReviewStage.PREVIEW, TracksideReviewStage.PENDING,
            TracksideReviewStage.STALE)) {
            val c = candidate(stage)
            assertEquals(60, c.activeMinutes)
            assertEquals(8.0, c.activeDistanceTargetKm!!, 0.0)
            assertTrue(c.acceptedRequiresOriginalSteps)
            assertEquals("8.0 km", c.originalDistanceLabel)
        }
    }

    @Test fun acceptedShorterTimeIsNotARequiredDistanceOrOldStructuredSteps() {
        val c = candidate(TracksideReviewStage.ACCEPTED_TIME)
        assertEquals(45, c.activeMinutes)
        assertNull(c.activeDistanceTargetKm)
        assertFalse(c.acceptedRequiresOriginalSteps)
        assertEquals("8.0 km", c.originalDistanceLabel)
        assertTrue(TracksideReviewCandidateCopy.disclosure(c).contains("old distance"))
        assertEquals("View workout" to "Training", TracksideReviewCandidateCopy.actions(c))
    }

    @Test fun acceptedRestHasNoTargetAndPreservesOriginalHistory() {
        val c = candidate(TracksideReviewStage.ACCEPTED_REST, proposed = 0)
        assertEquals(0, c.activeMinutes)
        assertNull(c.activeDistanceTargetKm)
        assertFalse(c.acceptedRequiresOriginalSteps)
        assertEquals("8.0 km", c.originalDistanceLabel)
        assertEquals("Training" to "View history", TracksideReviewCandidateCopy.actions(c))
    }

    @Test fun staleCaseForcesRefreshInsteadOfReapplyingPastEvidence() {
        val c = candidate(TracksideReviewStage.STALE)
        assertEquals("Refresh review" to "Keep original", TracksideReviewCandidateCopy.actions(c))
        assertTrue(TracksideReviewCandidateCopy.disclosure(c).contains("No adjustment was accepted"))
    }

    @Test fun invalidStatesOrInventedTargetsFailClosed() {
        for (distance in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                candidate(TracksideReviewStage.ACCEPTED_TIME, distance = distance)
            }
        }
        for ((stage, proposed) in listOf(
            TracksideReviewStage.ACCEPTED_TIME to 0,
            TracksideReviewStage.ACCEPTED_REST to 45,
            TracksideReviewStage.PENDING to 60,
            TracksideReviewStage.PREVIEW to -1
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                candidate(stage, proposed)
            }
        }
    }

    @Test fun restProposalNeedsExplicitAcceptanceEvenWhenIllnessWasReported() {
        for (stage in listOf(TracksideReviewStage.PREVIEW, TracksideReviewStage.PENDING)) {
            val c = candidate(stage, proposed = 0)
            assertEquals(60, c.activeMinutes)
            assertEquals(8.0, c.activeDistanceTargetKm!!, 0.0)
            assertTrue(c.acceptedRequiresOriginalSteps)
            assertEquals("Rest instead of this run", TracksideReviewCandidateCopy.changeTitle(c))
            assertTrue(TracksideReviewCandidateCopy.disclosure(c).contains("explicit approval") ||
                TracksideReviewCandidateCopy.disclosure(c).contains("If accepted"))
        }
        assertEquals("Accept rest" to "Dismiss",
            TracksideReviewCandidateCopy.actions(candidate(TracksideReviewStage.PENDING, 0)))
    }

    @Test fun staleTextDisplaysExpiredProposalWithoutChangingActiveTargets() {
        val c = candidate(TracksideReviewStage.STALE, proposed = 45)
        assertEquals("45-minute option unavailable", TracksideReviewCandidateCopy.changeTitle(c))
        assertEquals(60, c.activeMinutes)
        assertEquals(8.0, c.activeDistanceTargetKm!!, 0.0)
    }

    @Test fun missingOriginalDistanceDoesNotInventAMileageRequirement() {
        val c = candidate(TracksideReviewStage.ACCEPTED_TIME, distance = null)
        assertEquals("None", c.originalDistanceLabel)
        assertNull(c.activeDistanceTargetKm)
        val pending = candidate(TracksideReviewStage.PENDING, distance = null)
        assertEquals("None", pending.originalDistanceLabel)
        assertNull(pending.activeDistanceTargetKm)
    }
}

package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentQualityExposureTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val owner = "synthetic-owner"

    private fun example(
        source: String, session: String, daysAgo: Long,
        workMinutes: List<Int>? = listOf(4, 4, 4),
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        origin: IndependentQualityEvidenceOrigin = IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
        matched: Boolean = true
    ) = IndependentQualitySegmentEvidence(
        owner, source, session, today.minusDays(daysAgo), family, origin, matched,
        workMinutes?.map { it * 60 }
    )

    private fun assess(
        records: List<IndependentQualitySegmentEvidence>,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD
    ) = IndependentQualityExposure.assess(records, owner, family, today)

    @Test fun repeatedStructuredWorkIsProvisionalAndNeverAutoAuthorizesHarderWork() {
        val outcome = assess(listOf(
            example("source-1", "session-1", 9, listOf(4, 4, 4)),
            example("source-2", "session-2", 2, listOf(5, 5, 5, 5))
        ))
        assertEquals(720, outcome.provisionalRepeatedWorkSeconds)
        assertEquals(2, outcome.matchedDeviceSessionCount)
        assertEquals(listOf("session-2", "session-1"), outcome.evidenceSessionIds)
        assertEquals(IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED, outcome.confidence)
        assertFalse(outcome.sourceEvidenceVerified)
        assertFalse(outcome.authorizedForTemplateSelection)
        assertTrue("MINIMUM_OF_TWO_RECENT_SESSIONS" in outcome.ruleIds)
    }

    @Test fun aSingleBigSessionCannotEstablishRepeatedQualityExposure() {
        val outcome = assess(listOf(example("solo", "one", 1, listOf(8, 8, 8))))
        assertNull(outcome.provisionalRepeatedWorkSeconds)
        assertEquals(1, outcome.matchedDeviceSessionCount)
        assertEquals(IndependentQualityExposureConfidence.INSUFFICIENT_MATCHED_DEVICE_RECORDS, outcome.confidence)
    }

    @Test fun plannedStepsAndGenericCompletedLogsAreNotAcceptedAsEvidence() {
        val missingSegments = example("unknown", "session-a", 3, null)
        val noWork = example("zero", "session-b", 4, emptyList())
        val unlinked = example("unlinked", "session-c", 5, listOf(15), matched = false)
        val result = assess(listOf(missingSegments, noWork, unlinked))
        assertEquals(0, result.matchedDeviceSessionCount)
        assertNull(result.provisionalRepeatedWorkSeconds)
        assertEquals(IndependentQualityExposureConfidence.UNAVAILABLE, result.confidence)
    }

    @Test fun selfReportedWorkDoesNotBecomeDeviceConfirmedWork() {
        val result = assess(listOf(
            example("self-1", "session-1", 1, listOf(20),
                origin = IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS),
            example("self-2", "session-2", 2, listOf(20),
                origin = IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS)
        ))
        assertEquals(2, result.selfReportedSessionCount)
        assertEquals(0, result.matchedDeviceSessionCount)
        assertNull(result.provisionalRepeatedWorkSeconds)
        assertEquals(IndependentQualityExposureConfidence.SELF_REPORTED_ONLY, result.confidence)
    }

    @Test fun exactDuplicatesAreDeduplicatedButConflictingDuplicatesFailClosed() {
        val first = example("source-a", "session-a", 3, listOf(4, 4, 4))
        val second = example("source-b", "session-b", 2, listOf(4, 4, 4))
        val clean = assess(listOf(first, first, second))
        assertEquals(2, clean.matchedDeviceSessionCount)
        assertEquals(720, clean.provisionalRepeatedWorkSeconds)

        val ambiguous = assess(listOf(first, first.copy(performedWorkSegmentsSeconds = listOf(900)), second))
        assertNull(ambiguous.provisionalRepeatedWorkSeconds)
        assertEquals(1, ambiguous.conflictingSessionCount)
        assertEquals(IndependentQualityExposureConfidence.CONFLICT_REVIEW, ambiguous.confidence)
    }

    @Test fun separateProviderIdsForOneSessionAreNotDoubleCounted() {
        val result = assess(listOf(
            example("garmin-1", "same-session", 2),
            example("fit-duplicate", "same-session", 2),
            example("other", "another-session", 4)
        ))
        assertNull(result.provisionalRepeatedWorkSeconds)
        assertEquals(1, result.conflictingSessionCount)
        assertEquals(IndependentQualityExposureConfidence.CONFLICT_REVIEW, result.confidence)
    }

    @Test fun staleFutureAndOtherQualityFamiliesAreExcluded() {
        val result = assess(listOf(
            example("current", "current-session", 1),
            example("stale", "old-session", 29),
            example("future", "future-session", -1),
            example("tempo", "tempo-session", 2, listOf(20),
                family = IndependentQualityFamily.TEMPO)
        ))
        assertEquals(1, result.matchedDeviceSessionCount)
        assertNull(result.provisionalRepeatedWorkSeconds)
    }

    @Test fun otherAccountEvidenceIsRejectedRatherThanCombined() {
        val other = example("outside", "other-session", 2).copy(ownerId = "different-athlete")
        assertThrows(IllegalArgumentException::class.java) {
            assess(listOf(example("inside", "session-a", 1), other))
        }
    }

    @Test fun invalidSegmentAndIdentityInputsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            example("bad", "session", 1).copy(performedWorkSegmentsSeconds = listOf(-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            example("bad", "session", 1).copy(performedWorkSegmentsSeconds = List(101) { 1 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            example("bad", "session", 1).copy(performedWorkSegmentsSeconds = listOf(14400, 60))
        }
        assertThrows(IllegalArgumentException::class.java) {
            example("bad", "session", 1).copy(sourceRecordId = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IndependentQualityExposure.assess(emptyList(), owner,
                IndependentQualityFamily.THRESHOLD, today, lookbackDays = 0)
        }
    }

    @Test fun familySpecificComparisonsKeepTempoSeparateFromThreshold() {
        val records = listOf(
            example("th1", "th-session", 3),
            example("tm1", "tempo-one", 4, listOf(12), family = IndependentQualityFamily.TEMPO),
            example("tm2", "tempo-two", 1, listOf(18), family = IndependentQualityFamily.TEMPO)
        )
        val tempo = assess(records, IndependentQualityFamily.TEMPO)
        assertEquals(720, tempo.provisionalRepeatedWorkSeconds)
        assertEquals(2, tempo.matchedDeviceSessionCount)
        val threshold = assess(records, IndependentQualityFamily.THRESHOLD)
        assertNull(threshold.provisionalRepeatedWorkSeconds)
        assertEquals(1, threshold.matchedDeviceSessionCount)
    }
}

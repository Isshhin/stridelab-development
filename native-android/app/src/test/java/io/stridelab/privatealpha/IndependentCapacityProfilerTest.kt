package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Provisional history comparison tests; NOT tests of exercise safety. */
class IndependentCapacityProfilerTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val asOf = LocalDate.of(2026, 11, 14)
    private val monday = LocalDate.of(2026, 10, 12)
    private val weeks = (0L..3L).map { monday.plusWeeks(it) }

    private fun history(
        distancesKm: List<Double?>,
        complete: Set<Int> = setOf(0, 1, 2, 3)
    ): IndependentRunningHistoryEvidence {
        require(distancesKm.size == 4)
        val records = distancesKm.mapIndexedNotNull { index, km ->
            km?.let {
                ActivityRecord("run-$index", "Recorded run", "running", "garmin",
                    weeks[index].plusDays(2).atTime(7, 0).atZone(zone).toInstant(),
                    it * 1000.0, 3600.0, null, null, null, null)
            }
        }
        return IndependentRunningHistory.summarize(
            records, asOf, zone, complete.map { weeks[it] }.toSet())
    }

    @Test fun consistentFourWeekHistoryProvidesOnlyProvisionalBaseline() {
        val result = IndependentCapacityProfiler.assess(history(listOf(40.0, 42.0, 43.0, 44.0)))
        assertEquals(42.25, result.proposedStartingKm!!, 0.0)
        assertEquals(43.5, result.observedRecentTwoWeekMeanKm!!, 0.0)
        assertEquals(4, result.completeWeekCount)
        assertEquals(IndependentCapacityTrend.NO_MATERIAL_DECLINE, result.trend)
        assertEquals(IndependentCapacityConfidence.FOUR_WEEKS_ASSERTED, result.confidence)
        assertFalse(result.sourceCoverageVerified)
        assertTrue("SOURCE_COVERAGE_UNVERIFIED" in result.ruleIds)
    }

    @Test fun meaningfulRecentDropUsesRecentTwoWeekMeanNotFourWeekMean() {
        val result = IndependentCapacityProfiler.assess(history(listOf(60.0, 60.0, 32.0, 20.0)))
        assertEquals(43.0, result.observedFourWeekMeanKm!!, 0.0)
        assertEquals(26.0, result.observedRecentTwoWeekMeanKm!!, 0.0)
        assertEquals(26.0, result.proposedStartingKm!!, 0.0)
        assertEquals(IndependentCapacityTrend.RECENT_DECLINE, result.trend)
        assertTrue("MATERIAL_RECENT_DECLINE" in result.ruleIds)
    }

    @Test fun partialCoverageCanUseRecentTwoWeeksButNeverClaimsFourCompleteWeeks() {
        val result = IndependentCapacityProfiler.assess(
            history(listOf(70.0, 70.0, 26.0, 28.0), complete = setOf(2, 3)))
        assertEquals(2, result.completeWeekCount)
        assertNull(result.observedFourWeekMeanKm)
        assertEquals(27.0, result.proposedStartingKm!!, 0.0)
        assertEquals(IndependentCapacityTrend.UNKNOWN, result.trend)
        assertEquals(IndependentCapacityConfidence.RECENT_TWO_WEEKS_ASSERTED, result.confidence)
        assertFalse(result.sourceCoverageVerified)
    }

    @Test fun declaredMismatchForcesReviewAndUsesLowerCandidate() {
        val result = IndependentCapacityProfiler.assess(
            history(listOf(60.0, 60.0, 60.0, 60.0)), declaredWeeklyKm = 30.0)
        assertEquals(60.0, result.observedFourWeekMeanKm!!, 0.0)
        assertEquals(30.0, result.selfReportedWeeklyKm!!, 0.0)
        assertEquals(30.0, result.proposedStartingKm!!, 0.0)
        assertEquals(IndependentCapacityConfidence.CONFLICT_REVIEW, result.confidence)
        assertTrue("DECLARATION_DISAGREEMENT_REVIEW" in result.ruleIds)
        assertTrue("CONSERVATIVE_DECLARED_LOWER" in result.ruleIds)
    }

    @Test fun declaredHigherThanObservedCannotIncreaseProvisionalBaseline() {
        val result = IndependentCapacityProfiler.assess(
            history(listOf(30.0, 30.0, 30.0, 30.0)), declaredWeeklyKm = 60.0)
        assertEquals(30.0, result.proposedStartingKm!!, 0.0)
        assertEquals(IndependentCapacityConfidence.CONFLICT_REVIEW, result.confidence)
    }

    @Test fun unknownCoverageDoesNotTurnRecordedRunsIntoVerifiedWeeklyMileage() {
        val incomplete = history(listOf(20.0, 20.0, 20.0, 20.0), complete = emptySet())
        val unknown = IndependentCapacityProfiler.assess(incomplete)
        assertNull(unknown.proposedStartingKm)
        assertEquals(IndependentCapacityConfidence.UNAVAILABLE, unknown.confidence)
        val declared = IndependentCapacityProfiler.assess(incomplete, declaredWeeklyKm = 24.0)
        assertEquals(24.0, declared.proposedStartingKm!!, 0.0)
        assertEquals(IndependentCapacityConfidence.SELF_REPORTED_ONLY, declared.confidence)
        assertTrue("SELF_REPORT_FALLBACK" in declared.ruleIds)
        assertFalse(declared.sourceCoverageVerified)
    }

    @Test fun explicitlyConfirmedZeroWeekStaysZeroInsteadOfUnknown() {
        val result = IndependentCapacityProfiler.assess(history(listOf(0.0, 0.0, 0.0, 0.0)))
        assertEquals(0.0, result.proposedStartingKm!!, 0.0)
        assertEquals(IndependentCapacityTrend.NO_MATERIAL_DECLINE, result.trend)
        assertEquals(IndependentCapacityConfidence.FOUR_WEEKS_ASSERTED, result.confidence)
    }

    @Test fun invalidSelfReportAndHeuristicConfigurationFailClosed() {
        val h = history(listOf(12.0, 12.0, 12.0, 12.0))
        for (km in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                IndependentCapacityProfiler.assess(h, declaredWeeklyKm = km)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            IndependentCapacityHeuristics(materialDeclineFraction = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IndependentCapacityHeuristics(declarationDifferenceKm = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IndependentCapacityHeuristics(declarationDifferenceFraction = -0.2)
        }
    }
}

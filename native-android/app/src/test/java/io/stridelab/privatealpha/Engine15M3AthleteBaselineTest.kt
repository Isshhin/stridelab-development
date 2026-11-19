package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * M3 contract tests. Confidence is evidence provenance, NOT a prescribed
 * mileage increase or a medical safety score. The generator still runs Q2/Q3.
 */
class Engine15M3AthleteBaselineTest {
    private val start=LocalDate.of(2026,11,16)
    private fun request()=PlanRequest(
        goal="Aerobic foundation",experience=2,start=start,weeks=8,
        days=setOf(1,2,3,4,5,7),runDays=6,longDay=7,qualityDays=setOf(2),
        qualityCount=1,recentQualitySessionsPerWeek=1,weeklyKm=75.0,
        longestKm=20.0,targetWeeklyKm=80.0,
        planningPace=360.0,policyChoice="engine15",
        trainYourWay=true,timeBudgetEnabled=false
    )
    private fun completeEvidence(complete:Int=4):IndependentRunningHistoryEvidence {
        val mondays=(0..3).map { LocalDate.of(2026,10,19).plusWeeks(it.toLong()) }
        return IndependentRunningHistoryEvidence(mondays.mapIndexed { index,monday ->
            val confirmed=index<complete
            val km=listOf(70.0,72.0,75.0,77.0)[index]
            IndependentRunningWeekEvidence(
                monday=monday,coverageConfirmed=confirmed,recordedRunCount=6,
                recordedDays=6,knownDistanceKm=km,runsWithoutDistance=0,
                longestKnownRunKm=20.0,ambiguousSourceIds=0,
                confirmedWeeklyKm=km.takeIf { confirmed }
            )
        })
    }

    @Test fun selfReportedAverageIsUsableButNeverVerifiedByItsOwnClaim() {
        val p=request().copy(
            recentMileageBasis="SELF_REPORTED_AVERAGE",reportedPeakWeeklyKm=90.0)
        val c=Engine15AthleteBaseline.assess(p)
        assertEquals(AthleteBaselineStatus.PROVISIONAL_AVERAGE,c.status)
        assertEquals(AthleteBaselineConfidence.SELF_REPORTED_PROVISIONAL,c.confidence)
        assertEquals(75.0,c.typicalWeeklyKm!!,0.0)
        assertEquals(75.0,c.reportedTypicalWeeklyKm!!,0.0)
        assertNull(c.partialRecordedWeeklyKm)
        assertNull(c.legacyUnclassifiedWeeklyKm)
        assertEquals(90.0,c.reportedPeakWeeklyKm!!,0.0)
        assertEquals(20.0,c.reportedLongestRunKm!!,0.0)
        assertNull(c.verifiedFourWeekAverageKm)
        assertEquals(0,c.completeWeekCount)
        assertTrue(c.readyForVolumeSkeleton)
    }

    @Test fun missingAveragePeakOnlyAndExplicitZeroRemainDistinct() {
        val p=request()
        val unknown=Engine15AthleteBaseline.assess(p.copy(
            weeklyKm=0.0,recentMileageBasis="UNKNOWN"))
        val peak=Engine15AthleteBaseline.assess(p.copy(
            weeklyKm=0.0,recentMileageBasis="PEAK_WEEK_ONLY",reportedPeakWeeklyKm=85.0))
        val zero=Engine15AthleteBaseline.assess(p.copy(
            weeklyKm=0.0,recentMileageBasis="CONFIRMED_ZERO"))
        assertEquals(AthleteBaselineStatus.UNKNOWN,unknown.status)
        assertNull(unknown.typicalWeeklyKm)
        assertFalse(unknown.readyForVolumeSkeleton)
        assertEquals(AthleteBaselineStatus.PEAK_ONLY,peak.status)
        assertNull(peak.typicalWeeklyKm)
        assertEquals(85.0,peak.reportedPeakWeeklyKm!!,0.0)
        assertEquals(AthleteBaselineStatus.CONFIRMED_ZERO,zero.status)
        assertEquals(0.0,zero.typicalWeeklyKm!!,0.0)
        assertTrue(zero.readyForVolumeSkeleton)
    }

    @Test fun sparseRecordedRunsCannotBecomeVerifiedWeeklyMileage() {
        val p=request().copy(recentMileageBasis="RECORDED_PARTIAL")
        val partial=Engine15AthleteBaseline.assess(p,completeEvidence(1))
        assertEquals(AthleteBaselineStatus.PROVISIONAL_AVERAGE,partial.status)
        assertEquals(AthleteBaselineConfidence.PARTIAL_ACTIVITY_ESTIMATE,partial.confidence)
        assertEquals(1,partial.completeWeekCount)
        assertNull(partial.reportedTypicalWeeklyKm)
        assertEquals(75.0,partial.partialRecordedWeeklyKm!!,0.0)
        assertNull(partial.verifiedFourWeekAverageKm)
        assertEquals(75.0,partial.typicalWeeklyKm!!,0.0)

        // Complete, explicitly proven coverage can supply a normal baseline
        // only when no contradictory report is being silently overwritten.
        val verified=Engine15AthleteBaseline.assess(p.copy(
            weeklyKm=0.0,recentMileageBasis="UNKNOWN"),completeEvidence())
        assertEquals(AthleteBaselineConfidence.COMPLETE_FOUR_WEEK_HISTORY,verified.confidence)
        assertEquals(AthleteBaselineStatus.COMPLETE_HISTORY,verified.status)
        assertEquals(73.5,verified.typicalWeeklyKm!!,0.0)
        assertEquals(73.5,verified.verifiedFourWeekAverageKm!!,0.0)
        assertEquals(4,verified.completeWeekCount)
    }

    @Test fun verifiedHistoryDoesNotSilentlyOverrideReportedTypicalAverage() {
        val p=request().copy(
            recentMileageBasis="SELF_REPORTED_AVERAGE",reportedPeakWeeklyKm=90.0)
        val c=Engine15AthleteBaseline.assess(p,completeEvidence())
        assertEquals(75.0,c.typicalWeeklyKm!!,0.0)
        assertEquals(73.5,c.verifiedFourWeekAverageKm!!,0.0)
        assertEquals(AthleteBaselineConfidence.SELF_REPORTED_PROVISIONAL,c.confidence)
    }

    @Test fun contradictionsAreVisibleAndCannotBecomeM4BudgetInputs() {
        val lowPeak=Engine15AthleteBaseline.assess(request().copy(
            recentMileageBasis="SELF_REPORTED_AVERAGE",reportedPeakWeeklyKm=60.0))
        assertEquals(AthleteBaselineStatus.NEEDS_REVIEW,lowPeak.status)
        assertTrue(lowPeak.reviewReasons.contains("PEAK_BELOW_REPORTED_AVERAGE"))
        assertNull(lowPeak.typicalWeeklyKm)
        assertFalse(lowPeak.readyForVolumeSkeleton)

        val contradictedZero=Engine15AthleteBaseline.assess(request().copy(
            weeklyKm=0.0,recentMileageBasis="CONFIRMED_ZERO"),completeEvidence())
        assertTrue(contradictedZero.reviewReasons.contains("ZERO_CONTRADICTS_COMPLETE_HISTORY"))
        assertNull(contradictedZero.typicalWeeklyKm)
        assertFalse(contradictedZero.readyForVolumeSkeleton)

        val invalidBasis=Engine15AthleteBaseline.assess(request().copy(
            recentMileageBasis="FUTURE_UNSUPPORTED_PROVENANCE"))
        assertEquals(AthleteBaselineStatus.NEEDS_REVIEW,invalidBasis.status)
        assertTrue(invalidBasis.reviewReasons.contains("UNRECOGNIZED_MILEAGE_BASIS"))
    }

    @Test fun oldPlansRemainUnclassifiedAndRoundtripWithoutNewKeys() {
        val original=request()
        val json=original.encode()
        assertFalse(json.has("recentMileageBasis"))
        assertFalse(json.has("reportedPeakWeeklyKm"))
        assertEquals(original,PlanRequest.decode(json))
        val old=Engine15AthleteBaseline.assess(original)
        assertEquals(AthleteBaselineConfidence.LEGACY_UNVERIFIED,old.confidence)
        assertNull(old.reportedTypicalWeeklyKm)
        assertNull(old.partialRecordedWeeklyKm)
        assertEquals(75.0,old.legacyUnclassifiedWeeklyKm!!,0.0)
        assertEquals(75.0,old.typicalWeeklyKm!!,0.0)
        assertNull(Engine15AthleteBaseline.assess(original.copy(weeklyKm=0.0)).typicalWeeklyKm)

        val enriched=original.copy(recentMileageBasis="SELF_REPORTED_AVERAGE",
            reportedPeakWeeklyKm=90.0)
        assertEquals(enriched,PlanRequest.decode(enriched.encode()))
        assertEquals("SELF_REPORTED_AVERAGE",enriched.encode().getString("recentMileageBasis"))
        assertEquals(90.0,enriched.encode().getDouble("reportedPeakWeeklyKm"),0.0)
    }

    @Test fun metadataDoesNotChangeAnyGeneratedWorkoutsOrSafeguards() {
        val legacy=request().copy(weeks=4)
        val reported=legacy.copy(recentMileageBasis="SELF_REPORTED_AVERAGE",
            reportedPeakWeeklyKm=90.0)
        val now=Instant.parse("2026-11-16T12:00:00Z")
        val baseline=Engine15.generate(legacy,"m3-synthetic","m3-same-id",now)
        val withMetadata=Engine15.generate(reported,"m3-synthetic","m3-same-id",now)
        assertEquals(baseline.sessions,withMetadata.sessions)
        assertEquals(baseline.explanations,withMetadata.explanations)
        assertEquals(baseline.mileageOptions,withMetadata.mileageOptions)
        assertEquals(baseline.status,withMetadata.status)
        assertEquals(reported,withMetadata.request)
        assertEquals(withMetadata,PlanSnapshot.decode(withMetadata.encode(),"m3-synthetic"))
    }
}

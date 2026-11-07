package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The display must never infer max HR from a workout's highest measured HR. */
class HeartRateZoneAnalysisTest {
    private fun sample(index: Int, seconds: Double, hr: Double?) =
        ActivitySample(index, seconds, null, hr, null, null, null)

    @Test fun fiveZonesUseEnteredMaxAndOnlyRealShortTimeIntervals() {
        val samples = listOf(
            sample(0, 0.0, 100.0),
            sample(1, 10.0, 120.0),
            sample(2, 20.0, 140.0),
            sample(3, 30.0, 160.0),
            sample(4, 40.0, 180.0),
            sample(5, 100.0, 190.0), // skip this 60-second gap
            sample(6, 110.0, 180.0)
        )
        val zones = HeartRateZoneAnalysis.distribute(samples, 200)
        assertNotNull(zones)
        assertEquals(5, zones!!.buckets.size)
        assertEquals(50.0, zones.classifiedSeconds, 0.0001)
        assertEquals(5, zones.classifiedIntervals)
        assertEquals(1, zones.excludedIntervals)
        zones.buckets.forEachIndexed { index, bucket ->
            assertEquals(index + 1, bucket.zone)
            assertEquals(10.0, bucket.seconds, 0.0001)
            assertEquals(0.2, bucket.fraction, 0.0001)
        }
        assertEquals(120, zones.buckets[1].lowerBpm)
        assertEquals(139, zones.buckets[1].upperBpm)
        assertEquals(null, zones.buckets[0].lowerBpm)
        assertEquals(null, zones.buckets[4].upperBpm)
    }

    @Test fun equalZoneCoverageDoesNotClaimSingleDominantZone() {
        val samples = listOf(
            sample(0, 0.0, 100.0),
            sample(1, 10.0, 120.0),
            sample(2, 20.0, 140.0),
            sample(3, 30.0, 160.0),
            sample(4, 40.0, 180.0),
            sample(5, 50.0, 190.0)
        )
        val zones = HeartRateZoneAnalysis.distribute(samples, 200)!!
        val insight = HeartRateZoneAnalysis.presentation(zones)
        assertTrue(insight.headline.contains("multiple heart-rate zones"))
    }

    @Test fun invalidMaxOrGappyDataNeverInventsZones() {
        val samples = listOf(sample(0, 0.0, 130.0), sample(1, 100.0, 150.0))
        assertNull(HeartRateZoneAnalysis.distribute(samples, 0))
        assertNull(HeartRateZoneAnalysis.distribute(samples, 241))
        assertNull(HeartRateZoneAnalysis.distribute(samples, 197))
        assertNull(HeartRateZoneAnalysis.distribute(listOf(sample(0, 0.0, 180.0)), 197))
    }

    @Test fun missingHeartRateExcludesThatInterval() {
        val samples = listOf(
            sample(0, 0.0, 118.0),
            sample(1, 10.0, null),
            sample(2, 20.0, 145.0),
            sample(3, 30.0, 151.0)
        )
        val zones = HeartRateZoneAnalysis.distribute(samples, 200)
        assertNotNull(zones)
        assertEquals(10.0, zones!!.classifiedSeconds, 0.0001)
        assertEquals(2, zones.excludedIntervals)
        assertEquals(1, zones.classifiedIntervals)
    }

    @Test fun lthrPercentUsesThresholdSpecificFiveZoneBoundaries() {
        val samples = listOf(
            sample(0, 0.0, 140.0),
            sample(1, 10.0, 145.0),
            sample(2, 20.0, 153.0),
            sample(3, 30.0, 162.0),
            sample(4, 40.0, 170.0),
            sample(5, 50.0, 175.0)
        )
        val zones = HeartRateZoneAnalysis.distribute(
            samples,
            AthleteHeartRateZoneSettings(
                method = HeartRateZoneMethod.LTHR_PERCENT,
                lactateThresholdHr = 170
            )
        )!!
        assertEquals(145, zones.buckets[1].lowerBpm)
        assertEquals(152, zones.buckets[1].upperBpm)
        assertEquals(170, zones.buckets[4].lowerBpm)
        assertEquals(HeartRateZoneMethod.LTHR_PERCENT, zones.method)
        assertTrue(HeartRateZoneAnalysis.methodDefinition(zones.settings).contains("85%"))
    }

    @Test fun hrrPercentUsesRestingPlusReserveForBoundaries() {
        val samples = listOf(
            sample(0, 0.0, 120.0),
            sample(1, 10.0, 130.0),
            sample(2, 20.0, 145.0),
            sample(3, 30.0, 160.0),
            sample(4, 40.0, 175.0),
            sample(5, 50.0, 185.0)
        )
        val zones = HeartRateZoneAnalysis.distribute(
            samples,
            AthleteHeartRateZoneSettings(
                method = HeartRateZoneMethod.HRR_PERCENT,
                maximumHr = 200,
                restingHr = 50
            )
        )!!
        assertEquals(140, zones.buckets[1].lowerBpm)
        assertEquals(154, zones.buckets[1].upperBpm)
        assertEquals(HeartRateZoneMethod.HRR_PERCENT, zones.method)
        assertTrue(
            HeartRateZoneAnalysis.methodSummary(zones.settings)
                .contains("200 max / 50 resting")
        )
    }

    @Test fun incompleteLthrOrHrrSettingsNeverInventZones() {
        val samples = listOf(sample(0, 0.0, 130.0), sample(1, 10.0, 150.0))
        assertNull(
            HeartRateZoneAnalysis.distribute(
                samples,
                AthleteHeartRateZoneSettings(method = HeartRateZoneMethod.LTHR_PERCENT)
            )
        )
        assertNull(
            HeartRateZoneAnalysis.distribute(
                samples,
                AthleteHeartRateZoneSettings(
                    method = HeartRateZoneMethod.HRR_PERCENT,
                    maximumHr = 197
                )
            )
        )
    }

    @Test fun zoneAnalysisExplicitlyQualifiesEstimatedSourceAndMethod() {
        val samples = listOf(
            sample(0, 0.0, 130.0),
            sample(1, 10.0, 135.0),
            sample(2, 20.0, 142.0)
        )
        val zones = HeartRateZoneAnalysis.distribute(samples, 197)!!
        val insight = HeartRateZoneAnalysis.presentation(zones)
        assertTrue(insight.headline.contains("Zone"))
        assertTrue(insight.summary.contains("MAX BPM"))
        assertTrue(insight.evidence.any { it.contains("not imported Garmin") })
        assertFalse(insight.hasComparison)
    }
}

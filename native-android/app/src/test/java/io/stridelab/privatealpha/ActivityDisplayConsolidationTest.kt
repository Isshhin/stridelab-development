package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Regression coverage for the retired Intervals.icu Android integration. */
class ActivityDisplayConsolidationTest {
    private val start=Instant.parse("2026-11-04T02:06:00Z")
    private val now=Instant.parse("2026-11-05T04:00:00Z")
    private fun row(id:String,source:String,at:Instant=start,
                    duration:Double?=3807.0,distance:Double?=9560.0,
                    type:String="running")=ActivityRecord(
        id,"Santa Mesa Running",type,source,at,distance,duration,null,null,null,null
    )

    @Test fun sameRunFromGarminAndRetiredProviderShowsOnlyOriginalGarmin() {
        val garmin=row("g-456","garmin",duration=3816.0)
        val retired=row("i-123","intervals")
        val raw=listOf(garmin,retired)
        val visible=ActivityDisplayConsolidation.groups(raw)
        assertEquals(1,visible.size)
        assertEquals(garmin,visible.single().primary)
        assertEquals(garmin,visible.single().detailedSource)
        assertEquals("Garmin",visible.single().sourceLabel)
        assertTrue(visible.single().additionalSources.isEmpty())
        assertEquals(2,raw.size) // Neither raw provider record was deleted.
        val week=DashboardSummary.window(raw,now,ZoneId.of("Asia/Manila"))
        assertEquals(1,week.sessionCount)
        assertEquals(1,week.activeDays)
        assertEquals(9560.0,week.runningDistanceM!!,0.0)
        assertEquals(3816.0,week.durationSeconds!!,0.0)
    }

    @Test fun oldIntervalsOnlyActivitiesAreHiddenAndNotCountedInMileage() {
        val retired=listOf(row("i-1","intervals"),row("i-2","INTERVALS",start.minusSeconds(86400)))
        assertTrue(ActivityDisplayConsolidation.visible(retired).isEmpty())
        val week=DashboardSummary.window(retired,now,ZoneId.of("Asia/Manila"))
        assertEquals(0,week.sessionCount)
        assertNull(week.runningDistanceM)
        assertNull(historyWeeklyKm(retired,now))
    }

    @Test fun builderVolumeIncludesGarminButNotArchivedIntervals() {
        val garmin=listOf(
            row("g-456","garmin",duration=3816.0),
            row("g-next","garmin",start.minusSeconds(86400),1800.0,5000.0),
            row("g-third","garmin",start.minusSeconds(172800),2000.0,6000.0)
        )
        val retired=listOf(
            row("i-123","intervals"),
            row("i-next","intervals",start.minusSeconds(86400),1800.0,5000.0),
            row("i-third","intervals",start.minusSeconds(172800),2000.0,6000.0)
        )
        val actual=historyWeeklyKm(garmin+retired,now)
        assertNull(actual)
        assertEquals((9560.0+5000.0+6000.0)/1000.0,observedRunningWeeks(garmin+retired,now).sumOf { it.distanceKm ?: 0.0 },0.00001)
    }

    @Test fun distinctGarminRunsRemainDistinctWithoutCrossProviderPairing() {
        val recent=row("g-new","garmin")
        val older=row("g-old","garmin",start.minusSeconds(7200),1800.0,5000.0)
        val retired=row("i-1","intervals")
        val grouped=ActivityDisplayConsolidation.groups(listOf(retired,older,recent))
        assertEquals(listOf("g-new","g-old"),grouped.map { it.primary.id })
        assertTrue(grouped.all { it.additionalSources.isEmpty() })
    }

    @Test fun manualFitAndHealthConnectRecordsStaySupported() {
        val supported=listOf(row("manual","manual"),row("fit","fit"),
            row("health","health_connect"))
        val actual=ActivityDisplayConsolidation.visible(supported)
        assertEquals(3,actual.size)
        assertEquals(supported.map{it.id}.toSet(),actual.map{it.id}.toSet())
    }

    @Test fun sameGarminRunUnderLegacyAndImportedIdUsesSourceOwnedCanonical() {
        val canonical=row(GarminHistoryImport.recordId("uuid-456"),"garmin")
        val legacy=row("backend-old-id","garmin",duration=3808.0,distance=9564.0)
        val groups=ActivityDisplayConsolidation.groups(listOf(legacy,canonical))
        assertEquals(1,groups.size)
        assertEquals(canonical.id,groups.single().primary.id)
        assertEquals(listOf(legacy.id),groups.single().additionalSources.map { it.id })
        assertEquals(2,listOf(legacy,canonical).size) // Cache remains unchanged.
    }

    @Test fun legacyGarminAndSourceOwnedIdArePairedAcrossSmallTimestampAndRoundingDifferences() {
        val original=row(GarminHistoryImport.recordId("uuid-actual"),"garmin",
            at=start,duration=3807.0,distance=9560.0)
        val old=row("garmin-legacy-backend","garmin",
            at=start.plusSeconds(63),duration=3809.0,distance=9568.0)
        val result=ActivityDisplayConsolidation.groups(listOf(old,original))
        assertEquals(1,result.size)
        assertEquals(original.id,result.single().primary.id)
        assertEquals(listOf(old.id),result.single().additionalSources.map { it.id })
        val week=DashboardSummary.window(listOf(old,original),now,
            ZoneId.of("Asia/Manila"))
        assertEquals(1,week.sessionCount)
        assertEquals(9560.0,week.runningDistanceM!!,0.001)
    }

    @Test fun separateModernGarminSourceIdsStayDistinctEvenWithSimilarMetrics() {
        val first=row(GarminHistoryImport.recordId("one"),"garmin")
        val second=row(GarminHistoryImport.recordId("two"),"garmin",
            at=start.plusSeconds(2))
        val visible=ActivityDisplayConsolidation.visible(listOf(first,second))
        assertEquals(2,visible.size)
        assertEquals(setOf(first.id,second.id),visible.map { it.id }.toSet())
    }

    @Test fun sourceReconciliationDoesNotGuessAcrossProvidersOrLargeTimeGaps() {
        val imported=row(GarminHistoryImport.recordId("uuid-actual"),"garmin")
        val later=row("legacy-next","garmin",at=start.plusSeconds(6*60))
        val otherSource=row("fit-copy","fit")
        val missingMetrics=row("legacy-missing","garmin",
            at=start.plusSeconds(40),distance=null)
        val visible=ActivityDisplayConsolidation.visible(
            listOf(imported,later,otherSource,missingMetrics))
        assertEquals(4,visible.size)
    }

    @Test fun differentActualRunsAndIndependentProvidersAreNotCollapsed() {
        val source=row("garmin-a","garmin")
        val later=row("garmin-b","garmin",at=start.plusSeconds(2))
        val different=row("garmin-c","garmin",duration=3950.0)
        val separateFit=row("fit-a","fit")
        val visible=ActivityDisplayConsolidation.visible(
            listOf(source,later,different,separateFit))
        assertEquals(4,visible.size)
        assertEquals(setOf(source.id,later.id,different.id,separateFit.id),
            visible.map { it.id }.toSet())
    }

    @Test fun repeatedSourceIdsAreDisplayedOnceWithoutMutatingHistory() {
        val run=row("garmin-123","garmin")
        assertEquals(1,ActivityDisplayConsolidation.visible(listOf(run,run)).size)
        assertEquals(2,listOf(run,run).size)
    }
}

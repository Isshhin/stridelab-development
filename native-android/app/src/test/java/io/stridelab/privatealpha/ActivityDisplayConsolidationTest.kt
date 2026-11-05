package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ActivityDisplayConsolidationTest {
    private val start=Instant.parse("2026-11-04T02:06:00Z")
    private val now=Instant.parse("2026-11-05T04:00:00Z")
    private fun row(id:String,source:String,at:Instant=start,
                    duration:Double?=3807.0,distance:Double?=9560.0,
                    type:String="running")=ActivityRecord(
        id,"Santa Mesa Running",type,source,at,distance,duration,null,null,null,null
    )

    @Test fun samePhysicalRunFromGarminAndIntervalsDisplaysOnceWithoutDeletingEitherRecord() {
        val intervals=row("i-123","intervals")
        val garmin=row("g-456","garmin",duration=3816.0)
        val saved=listOf(garmin,intervals)
        val grouped=ActivityDisplayConsolidation.groups(saved)
        assertEquals(1,grouped.size)
        assertEquals(garmin,grouped.single().primary)
        assertEquals(listOf(intervals),grouped.single().additionalSources)
        assertEquals(intervals,grouped.single().detailedSource)
        assertEquals("Garmin + Intervals.icu",grouped.single().sourceLabel)
        assertEquals(2,saved.size) // Both original provider records are still available.

        val week=DashboardSummary.window(saved,now,ZoneId.of("Asia/Manila"))
        assertEquals(1,week.sessionCount)
        assertEquals(1,week.activeDays)
        assertEquals(9560.0,week.runningDistanceM!!,0.0)
        assertEquals(3816.0,week.durationSeconds!!,0.0)
    }

    @Test fun historyVolumeUsesOneWorkoutPerConfirmedPair() {
        val intervals=row("i-123","intervals")
        val garmin=row("g-456","garmin",duration=3816.0)
        val second=row("i-next","intervals",start.minusSeconds(86400),1800.0,5000.0)
        val third=row("i-third","intervals",start.minusSeconds(172800),2000.0,6000.0)
        val actual=historyWeeklyKm(listOf(garmin,intervals,second,third),now)
        assertEquals((9560.0+5000+6000)/4000.0,actual!!,0.00001)
    }

    @Test fun sameDisplayedMinuteCanMatchDespiteDifferentProviderSeconds() {
        val intervals=row("i-1","intervals")
        val garmin=row("g-1","garmin",start.plusSeconds(55),3816.0)
        val group=ActivityDisplayConsolidation.groups(listOf(garmin,intervals)).single()
        assertEquals("Garmin + Intervals.icu",group.sourceLabel)
        assertEquals(garmin,group.primary)
    }

    @Test fun manyUnrelatedProviderActivitiesKeepTheirOwnRecordsAndOrder() {
        val first=Instant.parse("2026-01-01T00:00:00Z")
        val intervals=(0 until 500).map { n ->
            row("i-$n","intervals",first.plusSeconds(n*7200L),1800.0,5000.0)
        }
        val garmin=(0 until 500).map { n ->
            row("g-$n","garmin",first.plusSeconds(n*7200L+10),1808.0,5000.0)
        }
        val grouped=ActivityDisplayConsolidation.groups(garmin.reversed()+intervals)
        assertEquals(500,grouped.size)
        assertTrue(grouped.all { it.primary.source=="garmin" && it.additionalSources.size==1 })
        assertEquals(1000,(garmin+intervals).distinctBy { it.id }.size)
    }

    @Test fun garminFirstDoesNotReplaceSupplementarySourceMetrics() {
        val intervals=row("i-source","intervals",distance=9570.0,duration=3807.0)
        val garmin=row("g-source","garmin",distance=9560.0,duration=3816.0)
        val original=listOf(intervals,garmin)
        val group=ActivityDisplayConsolidation.groups(original).single()
        assertEquals(garmin,group.primary)
        assertEquals(intervals,group.detailedSource)
        assertEquals("Garmin + Intervals.icu",group.sourceLabel)
        assertEquals(9560.0,group.primary.distanceM!!,0.0)
        assertEquals(9570.0,group.detailedSource.distanceM!!,0.0)
        assertEquals(2,original.size)
        // Garmin distance must also win in weekly source totals.
        assertEquals(9560.0,DashboardSummary.window(original,now,ZoneId.of("Asia/Manila"))
            .runningDistanceM!!,0.0)
    }

    @Test fun intervalsOnlyHistoryRetainsItsOwnDetailIdentity() {
        val intervals=row("i-only","intervals")
        val group=ActivityDisplayConsolidation.groups(listOf(intervals)).single()
        assertEquals(intervals,group.primary)
        assertEquals(intervals,group.detailedSource)
        assertTrue(group.additionalSources.isEmpty())
    }

    @Test fun sameProviderOrDifferentWorkoutMustStaySeparate() {
        val original=row("i-1","intervals")
        val sameProvider=row("i-2","intervals",duration=3808.0)
        assertEquals(2,ActivityDisplayConsolidation.visible(listOf(original,sameProvider)).size)

        val differentStart=row("g-later","garmin",start.plusSeconds(90),3816.0)
        assertEquals(2,ActivityDisplayConsolidation.visible(listOf(original,differentStart)).size)
        val differentDistance=row("g-far","garmin",duration=3816.0,distance=11000.0)
        assertEquals(2,ActivityDisplayConsolidation.visible(listOf(original,differentDistance)).size)
        val differentDuration=row("g-short","garmin",duration=3600.0)
        assertEquals(2,ActivityDisplayConsolidation.visible(listOf(original,differentDuration)).size)
        val missingMetric=row("g-missing","garmin",duration=null)
        assertEquals(2,ActivityDisplayConsolidation.visible(listOf(original,missingMetric)).size)
        val differentSport=row("g-bike","garmin",type="cycling")
        assertEquals(2,ActivityDisplayConsolidation.visible(listOf(original,differentSport)).size)
    }

    @Test fun ambiguousSameTimeActivitiesAreNotSilentlyGrouped() {
        val intervals1=row("i-one","intervals")
        val intervals2=row("i-two","intervals",start.plusSeconds(10),duration=3809.0)
        val garmin=row("g-one","garmin",duration=3816.0)
        assertEquals(3,ActivityDisplayConsolidation.visible(
            listOf(intervals1,intervals2,garmin)).size)
    }
}

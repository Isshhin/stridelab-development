package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GarminGpsDetailTest {
    private val whenRun=Instant.parse("2026-11-05T02:48:00Z")
    private val summary=ActivityRecord(
        "garmin-record","Batis Running","running","garmin",whenRun,
        5000.0,1800.0,1780.0,null,149.0,null
    )
    private fun fit(start:Instant=whenRun,offset:Double=5000.0,
                    elapsed:Double=1800.0,timer:Double=1780.0):FitActivityDecoder.Activity {
        val id=ImportedSourceIdentity("owner","fit-file",sha256="a".repeat(64))
        val points=(0..2).map { i ->
            FitActivityDecoder.Point(start.plusSeconds(i*30L),
                i*25.0,2.5,145.0,15.0,14.60+i*.001,121.0+i*.001,null,null)
        }
        return FitActivityDecoder.Activity(id,"running",start,elapsed,timer,
            offset,145.0,167.0,points,emptyList())
    }
    @Test fun acceptedFitGpsBelongsToOriginalGarminRecordAndAccount() {
        val raw=GarminGpsDetail.payload("owner",summary,fit(),12345L)
        val parsed=ActivityDetail.parse(raw,"owner",summary.id)
        assertEquals(summary.id,parsed.activityId)
        assertEquals(3,parsed.samples.size)
        assertEquals(14.6,parsed.samples.first().latitude!!,0.0001)
        assertEquals(121.0,parsed.samples.first().longitude!!,0.0001)
        assertEquals(3,RecordedGpsRoute.from(parsed.samples).pointCount)
        assertEquals(12345L,parsed.fetchedAt)
        assertEquals("garmin",summary.source)
    }
    @Test fun unrelatedFitOrAccountMustNeverBecomeGarminRoute() {
        try { GarminGpsDetail.payload("other",summary,fit(),123L);fail("Other owner") }
        catch(_:IllegalArgumentException) {}
        try { GarminGpsDetail.payload("owner",summary,fit(whenRun.plusSeconds(3600)),123L);fail("Different run") }
        catch(_:IllegalArgumentException) {}
        try { GarminGpsDetail.payload("owner",summary,fit(offset=9000.0),123L);fail("Distance mismatch") }
        catch(_:IllegalArgumentException) {}
    }
    @Test fun indoorFitWithoutGpsPreservesMeasuredStreamsAndLaps() {
        val indoor = fit().copy(
            points = fit().points.map { it.copy(latitude = null, longitude = null) }
        )
        val raw = GarminGpsDetail.payload("owner", summary, indoor, 24680L)
        val parsed = ActivityDetail.parse(raw, "owner", summary.id)
        assertEquals(3, parsed.samples.size)
        assertEquals(0, RecordedGpsRoute.from(parsed.samples).pointCount)
        assertEquals(145.0, parsed.samples[0].hr!!, 0.01)
        assertEquals(24680L, parsed.fetchedAt)
    }

    @Test fun fitWithoutAnySamplesOrLapsIsRejected() {
        try {
            GarminGpsDetail.payload(
                "owner", summary, fit().copy(points = emptyList(), laps = emptyList()),
                123L
            )
            fail("Empty recording was accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun pausedFitUsesTimerDurationForGarminSummaryValidation() {
        val pausedSummary=summary.copy(durationS=5841.134765625,movingTimeS=5831.903961181641)
        val raw=GarminGpsDetail.payload("owner",pausedSummary,
            fit(elapsed=6903.84814453125,timer=5841.134765625),12345L)
        assertEquals(3,ActivityDetail.parse(raw,"owner",summary.id).samples.size)
    }
}

package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

/** Pure JVM coverage: chart preparation never needs an Android Bitmap or UI thread. */
class ActivityChartPreparationTest {
    @Test fun largeRecordingKeepsAllMeasuredValuesButBoundsRenderedPoints() {
        val samples = (0 until 20_000).map { i ->
            ActivitySample(
                index=i, seconds=i.toDouble(),
                pace=280.0 + (i % 500).toDouble() / 10.0,
                hr=125.0 + (i % 40).toDouble(),
                altitude=18.0 + (i % 20).toDouble(),
                latitude=null, longitude=null,
                cadenceSpm=165.0 + (i % 16).toDouble()
            )
        }
        for(title in ActivityChartPreparation.titles) {
            val result = requireNotNull(ActivityChartPreparation.prepare(samples,title))
            assertEquals(20_000,result.validSamples.size)
            assertTrue(result.renderedPoints.size <= 500)
            assertTrue(result.displaySegments.flatten().size <= result.renderedPoints.size)
            assertTrue(result.sampleAverage.isFinite())
            assertNotNull(result.insight)
            assertEquals(0.0,result.firstTime,0.0)
            assertEquals(19_999.0,result.lastTime,0.0)
        }
    }

    @Test fun sourceGapAndPauseStayDisconnectedWithTrueReadings() {
        val samples = listOf(
            ActivitySample(0,0.0,300.0,130.0,10.0,null,null),
            ActivitySample(1,10.0,305.0,132.0,10.0,null,null),
            ActivitySample(2,20.0,null,133.0,10.0,null,null),
            ActivitySample(3,30.0,315.0,135.0,10.0,null,null),
            ActivitySample(4,250.0,320.0,140.0,10.0,null,null),
            ActivitySample(5,260.0,325.0,142.0,10.0,null,null),
            ActivitySample(6,270.0,330.0,143.0,10.0,null,null),
            ActivitySample(7,280.0,335.0,144.0,10.0,null,null)
        )
        val pauses=listOf(ActivityPauseInterval(255.0,275.0))
        val result = requireNotNull(
            ActivityChartPreparation.prepare(samples,"Pace",pauses)
        )
        assertEquals(7,result.validSamples.size)
        assertEquals(2,result.pausedMetricSamples.size)
        assertTrue(result.pausedDrawSamples.size <= result.pausedMetricSamples.size)
        result.displaySegments.forEach { section ->
            section.zipWithNext().forEach { (a,b) ->
                assertTrue(b.seconds-a.seconds <= 120.0)
                assertFalse(pauses.any { p ->
                    a.seconds < p.endSeconds && b.seconds > p.startSeconds
                })
                assertNotNull(a.pace)
                assertNotNull(b.pace)
            }
        }
        assertEquals(300.0,result.lowest,0.0)
        assertEquals(335.0,result.highest,0.0)
        assertNotNull(result.insight)
        assertNull(ActivityChartPreparation.prepare(samples,"Unknown",pauses))
    }

    @Test fun emptyChartCannotManufactureMeasurements() {
        val samples=(0..3).map { i ->
            ActivitySample(i,i.toDouble(),null,null,null,null,null)
        }
        for(title in ActivityChartPreparation.titles) {
            assertNull(ActivityChartPreparation.prepare(samples,title))
        }
    }
}

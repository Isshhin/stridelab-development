package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic 15k-record long run: visual budgets never modify source points. */
class LongRunMapRenderingTest {
    private val samples = (0 until 15_000).map { index ->
        ActivitySample(
            index = index,
            seconds = index.toDouble(),
            pace = if (index == 7_777) 240.0 else 370.0,
            hr = if (index == 7_777) 185.0 else 140.0,
            altitude = 10.0 + index * .001,
            latitude = 14.6000 + index * .000001,
            longitude = 121.0000 + index * .000001,
            cadenceSpm = 168.0
        )
    }

    @Test fun longRunGpsVisualHasBoundedPointsAndFullSourceRemainsUntouched() {
        val pauses = listOf(ActivityPauseInterval(3_000.0, 3_400.0))
        val route = RecordedGpsRoute.from(samples, pauses)
        assertEquals(15_000, route.pointCount)
        assertEquals(15_000, route.allPoints.size)
        assertTrue(route.renderSegments.sumOf { it.size } <= 2_000)
        assertEquals(samples.first().latitude, route.renderSegments.first().first().latitude)
        assertEquals(samples.last().longitude, route.renderSegments.last().last().longitude)
        assertEquals(pauses, route.pauseIntervals)
        val lines = JSONObject(route.displayGeoJson())
            .getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(1, lines.length())
        assertTrue(lines.getJSONArray(0).length() <= 2_000)
        assertEquals(15_000, samples.size)
    }

    @Test fun coloredMapDoesNotEmitThousandsOfFeaturesOrChangeMetrics() {
        val analysis = activityAnalysisRoute(
            samples, emptyList(), TracksideActivityMapMode.PACE
        )
        assertTrue(analysis.edges.size <= 2_000)
        assertEquals(240.0, analysis.minimum!!, 0.0001)
        assertEquals(370.0, analysis.maximum!!, 0.0001)
        val features = JSONObject(analysis.geoJson).getJSONArray("features")
        assertTrue(features.length() <= 2_000)
        assertTrue(features.length() > 0)
        assertEquals(15_000, samples.size)
    }

    @Test fun shortRunsKeepTheirAllTheirRecordedDisplayCoordinates() {
        val short = samples.take(100)
        val route = RecordedGpsRoute.from(short)
        assertEquals(100, route.renderSegments.sumOf { it.size })
        assertEquals(100, route.displaySegments.sumOf { it.size })
        val analysis = activityAnalysisRoute(
            short, emptyList(), TracksideActivityMapMode.PACE
        )
        assertEquals(99, analysis.edges.size)
    }
}

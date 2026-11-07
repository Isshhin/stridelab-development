package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RecordedGpsRouteTest {
    private fun sample(i:Int,t:Double,lat:Double?,lon:Double?)=ActivitySample(
        i,t,null,null,null,lat,lon
    )

    @Test fun realGpsProducesGeoReferencedCoordinatesNotADecorativeRoute() {
        val route=RecordedGpsRoute.from(listOf(
            sample(0,0.0,14.6041,121.0123),
            sample(1,30.0,14.6042,121.0125),
            sample(2,60.0,14.6043,121.0129)
        ))
        assertEquals(1,route.segments.size)
        assertEquals(3,route.pointCount)
        val json=JSONObject(route.toGeoJson())
        assertEquals("FeatureCollection",json.getString("type"))
        val geom=json.getJSONArray("features").getJSONObject(0).getJSONObject("geometry")
        assertEquals("MultiLineString",geom.getString("type"))
        val segment=geom.getJSONArray("coordinates").getJSONArray(0)
        val first=segment.getJSONArray(0)
        assertEquals(121.0123,first.getDouble(0),1e-8) // GeoJSON is lon,lat
        assertEquals(14.6041,first.getDouble(1),1e-8)
    }

    @Test fun sourceGapsAndMissingCoordinatesAreNeverJoined() {
        val route=RecordedGpsRoute.from(listOf(
            sample(0,0.0,14.6,121.0),
            sample(1,30.0,14.61,121.01),
            sample(2,300.0,14.62,121.02), // source gap >2min
            sample(3,310.0,14.63,121.03),
            sample(4,315.0,null,null), // missing fix
            sample(5,330.0,14.64,121.04) // isolated fix retained as dot
        ))
        assertEquals(3,route.segments.size)
        assertEquals(5,route.pointCount)
        assertEquals(1,route.isolatedPoints.size)
        assertEquals(2,
            JSONObject(route.toGeoJson()).getJSONArray("features").getJSONObject(0)
                .getJSONObject("geometry").getJSONArray("coordinates").length())
        val isolated=JSONObject(route.isolatedGeoJson())
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(1, isolated.length())
        assertEquals(121.04, isolated.getJSONArray(0).getDouble(0), 1e-8)
        val gaps=JSONObject(route.gapsGeoJson()).getJSONArray("features")
            .getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(2,gaps.length())
    }

    @Test fun explicitTimerPauseIsSeparateFromUnknownGpsGap() {
        val route=RecordedGpsRoute.from(
            listOf(
                sample(0,0.0,14.60,121.00),
                sample(1,10.0,14.61,121.01),
                sample(2,30.0,14.62,121.02),
                sample(3,40.0,14.63,121.03)
            ),
            listOf(ActivityPauseInterval(15.0,25.0))
        )
        assertEquals(2,route.segments.size)
        assertEquals(1,route.pauseGaps.size)
        assertEquals(0,route.unknownGaps.size)
        val pauseJson=JSONObject(route.pauseGapsGeoJson())
            .getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(1,pauseJson.length())
    }

    @Test fun selectedTimeUsesNearestRealGpsFixOnly() {
        val route=RecordedGpsRoute.from(listOf(
            sample(0,0.0,14.60,121.00),
            sample(1,30.0,14.61,121.01),
            sample(2,60.0,14.62,121.02)
        ))
        val selected=requireNotNull(route.selectedPoint(28.0))
        assertEquals(30.0,selected.elapsedSeconds,0.0)
        val json=JSONObject(route.selectedPointGeoJson(28.0))
        assertEquals("Point",json.getJSONObject("geometry").getString("type"))
        assertNull(route.selectedPoint(500.0))
    }

    @Test fun loneRecordedFixIsPreservedWithoutInventingRouteLine() {
        val route=RecordedGpsRoute.from(listOf(
            sample(0,0.0,null,null),
            sample(1,30.0,14.63,121.01),
            sample(2,40.0,null,null)
        ))
        assertEquals(1,route.pointCount)
        assertEquals(1,route.isolatedPoints.size)
        val lines=JSONObject(route.toGeoJson()).getJSONArray("features")
            .getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(0,lines.length())
        val dots=JSONObject(route.isolatedGeoJson())
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(1,dots.length())
    }

    @Test fun datelineCrossingAndUnusablePointsAreNotInvented() {
        val route=RecordedGpsRoute.from(listOf(
            sample(0,0.0,10.0,179.8),
            sample(1,10.0,10.0,179.9),
            sample(2,20.0,10.0,-179.9),
            sample(3,30.0,10.0,-179.8),
            sample(4,40.0,91.0,-179.7)
        ))
        assertEquals(2,route.segments.size)
        assertEquals(4,route.pointCount)
        assertEquals(2,route.cameraPoints().size)
    }

    @Test fun summaryOnlyActivityHasNoRouteAndNoFabricatedMap() {
        val route=RecordedGpsRoute.from(emptyList())
        assertEquals(0,route.pointCount)
        assertTrue(route.segments.isEmpty())
        assertTrue(route.cameraPoints().isEmpty())
        val missing=RecordedGpsRoute.from(listOf(sample(0,0.0,null,null)))
        assertEquals(0,missing.pointCount)
    }
}

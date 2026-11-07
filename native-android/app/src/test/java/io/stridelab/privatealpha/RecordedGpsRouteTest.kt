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
        assertEquals("2 GPS gaps",route.gpsContinuityLabel)
    }

    @Test fun displayRouteConnectsExistingEndpointsAcrossPausesAndMissingFixes() {
        val source = listOf(
            sample(0, 0.0, 14.600, 121.000),
            sample(1, 30.0, 14.601, 121.001),
            sample(2, 200.0, 14.602, 121.002),
            sample(3, 230.0, null, null),
            sample(4, 240.0, 14.603, 121.003)
        )
        val pauses = listOf(ActivityPauseInterval(60.0, 180.0))
        val route = RecordedGpsRoute.from(source, pauses)
        // The authoritative recording still exposes tracking gaps.
        assertEquals(3, route.segments.size)
        assertEquals(1, route.pauseGaps.size)
        assertEquals(1, route.unknownGaps.size)
        assertEquals(pauses, route.pauseIntervals)
        assertEquals(4, route.pointCount)
        // Map and thumbnail presentation only draw through actual endpoints.
        assertEquals(1, route.displaySegments.size)
        assertEquals(4, route.displaySegments.single().size)
        val lines = JSONObject(route.displayGeoJson())
            .getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(1, lines.length())
        assertEquals(4, lines.getJSONArray(0).length())
        assertEquals(0, route.displayIsolatedPoints.size)
        assertEquals(2, JSONObject(route.toGeoJson())
            .getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates").length())
    }

    @Test fun displayRouteDoesNotJoinAcrossTheAntimeridian() {
        val route = RecordedGpsRoute.from(listOf(
            sample(0, 0.0, 10.0, 179.90),
            sample(1, 10.0, 10.01, -179.90),
            sample(2, 20.0, 10.02, -179.80)
        ))
        assertEquals(2, route.displaySegments.size)
        val displayLines = JSONObject(route.displayGeoJson())
            .getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(1, displayLines.length())
        assertEquals(2, displayLines.getJSONArray(0).length())
    }

    @Test fun pauseWithoutSourceFixesDoesNotFabricateSolidRoute() {
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
        assertEquals("1 GPS gap",route.gpsContinuityLabel)
        val pauseJson=JSONObject(route.pauseGapsGeoJson())
            .getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(1,pauseJson.length())
    }

    @Test fun pausedGpsFixesRemainRecordedButAreSeparatedFromActiveRouteDots() {
        val route=RecordedGpsRoute.from(
            listOf(
                sample(0,0.0,14.600,121.000),
                sample(1,10.0,14.601,121.001),
                sample(2,20.0,14.602,121.002),
                sample(3,25.0,14.603,121.003),
                sample(4,40.0,14.604,121.004)
            ),
            listOf(ActivityPauseInterval(15.0,30.0))
        )
        assertEquals(listOf(20.0,25.0),route.pausedPoints.map { it.elapsedSeconds })
        assertEquals(1,route.segments.size)
        assertEquals(0,route.pauseGaps.size)
        assertEquals("Continuous GPS",route.gpsContinuityLabel)
        val actualLine=JSONObject(route.toGeoJson()).getJSONArray("features")
            .getJSONObject(0).getJSONObject("geometry")
            .getJSONArray("coordinates").getJSONArray(0)
        assertEquals(5,actualLine.length())
        // Paused points are part of the same solid, source-recorded route.
        assertEquals(121.002,actualLine.getJSONArray(2).getDouble(0),1e-8)
        assertEquals(121.003,actualLine.getJSONArray(3).getDouble(0),1e-8)
        assertEquals(0,JSONObject(route.isolatedGeoJson()).getJSONObject("geometry")
            .getJSONArray("coordinates").length())
        assertTrue(route.activeIsolatedPoints.none {
            it.elapsedSeconds in 15.0..30.0
        })
        val paused=JSONObject(route.pausedPointsGeoJson())
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(2,paused.length())

        val start=JSONObject(route.startPointGeoJson())
            .getJSONObject("geometry").getJSONArray("coordinates")
        val finish=JSONObject(route.finishPointGeoJson())
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(121.000,start.getDouble(0),1e-8)
        assertEquals(121.004,finish.getDouble(0),1e-8)
    }

    @Test fun isolatedPauseFixIsRetainedAsNormalRecordedGpsPoint() {
        val route=RecordedGpsRoute.from(
            listOf(
                sample(0,10.0,null,null),
                sample(1,20.0,14.601,121.001),
                sample(2,30.0,null,null)
            ),
            listOf(ActivityPauseInterval(15.0,25.0))
        )
        assertEquals(1,route.pausedPoints.size)
        assertEquals(1,route.isolatedPoints.size)
        val coords=JSONObject(route.isolatedGeoJson()).getJSONObject("geometry")
            .getJSONArray("coordinates")
        assertEquals(1,coords.length())
        assertEquals(121.001,coords.getJSONArray(0).getDouble(0),1e-8)
        assertEquals(0,JSONObject(route.toGeoJson()).getJSONArray("features")
            .getJSONObject(0).getJSONObject("geometry")
            .getJSONArray("coordinates").length())
    }

    @Test fun pauseBoundaryFixesWithoutInteriorSamplesStayDisconnected() {
        val route=RecordedGpsRoute.from(
            listOf(
                sample(0,10.0,14.600,121.000),
                sample(1,15.0,14.601,121.001),
                sample(2,30.0,14.602,121.002),
                sample(3,40.0,14.603,121.003)
            ),
            listOf(ActivityPauseInterval(15.0,30.0))
        )
        assertEquals(2,route.segments.size)
        assertEquals(1,route.pauseGaps.size)
    }

    @Test fun presentationMarkersAndSplitHighlightUseRecordedCoordinatesOnly() {
        val route=RecordedGpsRoute.from(listOf(
            sample(0,0.0,14.6000,121.0000),
            sample(1,30.0,14.6005,121.0005),
            sample(2,60.0,14.6010,121.0010),
            sample(3,90.0,14.6015,121.0015),
            sample(4,120.0,14.6020,121.0020),
            sample(5,150.0,14.6025,121.0025)
        ))
        assertEquals("Continuous GPS",route.gpsContinuityLabel)

        val directions=JSONObject(route.directionMarkersGeoJson())
            .getJSONArray("features")
        assertTrue(directions.length() >= 1)
        val direction=directions.getJSONObject(0)
        assertTrue(direction.getJSONObject("properties").getDouble("bearing").isFinite())

        val laps=listOf(
            ActivityLap(0,1000.0,60.0,360.0,140.0,startSeconds=0.0),
            ActivityLap(1,1000.0,60.0,355.0,145.0,startSeconds=60.0),
            ActivityLap(2,1000.0,60.0,350.0,150.0,startSeconds=120.0)
        )
        val markers=JSONObject(route.splitMarkersGeoJson(laps))
            .getJSONArray("features")
        assertEquals(2,markers.length())
        assertEquals("1",markers.getJSONObject(0).getJSONObject("properties").getString("label"))
        assertEquals("2",markers.getJSONObject(1).getJSONObject("properties").getString("label"))

        val selected=JSONObject(route.rangeGeoJson(60.0 to 120.0))
            .getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(1,selected.length())
        val line=selected.getJSONArray(0)
        assertEquals(3,line.length())
        assertEquals(121.0010,line.getJSONArray(0).getDouble(0),1e-8)
        assertEquals(121.0020,line.getJSONArray(2).getDouble(0),1e-8)
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

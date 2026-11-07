package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class ShareRoutePresentationTest {
    private fun sample(i:Int, lat:Double, seconds:Double=i*10.0) =
        ActivitySample(i,seconds,350.0+i,120.0+i,10.0+i,lat,121.0)

    @Test fun trimsBeforeBridgingPauseAndKeepsSourceUnchanged() {
        val samples=(0..12).filter { it!=5 }.map { sample(it,14.0+it*.001) }
        val before=samples.toList()
        val result=shareRoutePresentation(samples,listOf(ActivityPauseInterval(45.0,55.0)),200.0,true)
        assertEquals(before,samples)
        assertEquals(1,result.route.segments.size)
        val points=result.route.allPoints
        assertTrue(points.first().latitude >= 14.002)
        assertTrue(points.last().latitude <= 14.010)
        assertTrue(points.zipWithNext().any { (a,b) -> a.elapsedSeconds==40.0 && b.elapsedSeconds==60.0 })
        result.analysis.values.flatMap { it.edges }.forEach { edge ->
            assertTrue(edge.start in points && edge.end in points)
        }
        assertEquals(points,result.route.cameraPoints())
    }

    @Test fun hidesShortRouteAndNeverReconnectsLoopThroughEndpoint() {
        assertEquals(0,shareRoutePresentation((0..2).map { sample(it,14.0+it*.001) },emptyList(),200.0,true).route.pointCount)
        val lats=listOf(14.0,14.003,14.006,14.003,14.0,14.003,14.006,14.009,14.012)
        val result=shareRoutePresentation(lats.mapIndexed { i,lat -> sample(i,lat) },emptyList(),200.0,true)
        assertTrue(result.route.segments.size>=2)
        assertTrue(result.route.segments.all { segment -> segment.none { it.latitude==14.0 } })
        assertTrue(result.route.segments.none { segment -> segment.zipWithNext().any { (a,b) -> a.elapsedSeconds<40 && b.elapsedSeconds>40 } })
    }

    @Test fun unknownGapsRemainSeparateAndOverlaysUseOnlyRetainedEdges() {
        val samples=(0..14).map { sample(it,14.0+it*.001,if(it<8) it*10.0 else it*10.0+200) }
        val result=shareRoutePresentation(samples,emptyList(),200.0,true)
        assertEquals(2,result.route.segments.size)
        val routeEdges=result.route.segments.flatMap { it.zipWithNext() }.toSet()
        assertEquals(setOf(ShareRouteMode.PACE,ShareRouteMode.HR,ShareRouteMode.ELEVATION),result.analysis.keys)
        result.analysis.values.forEach { overlay ->
            assertTrue(overlay.edges.isNotEmpty())
            assertTrue(overlay.edges.all { it.start to it.end in routeEdges })
        }
        val bounds=result.route.cameraPoints()
        assertFalse(samples.first().latitude in bounds.map { it.latitude })
        assertFalse(samples.last().latitude in bounds.map { it.latitude })
    }

    @Test fun longRunHasBoundedContinuousGeometryAndNeverMutatesSamples() {
        val samples=(0 until 25000).map { sample(it,14.0+it*.00001) }
        val before=samples.toList()
        val result=shareRoutePresentation(samples,emptyList(),200.0,true)
        assertEquals(samples,before)
        assertEquals(1,result.route.segments.size)
        assertTrue(result.route.pointCount<=2000)
        assertTrue(result.analysis.values.all { it.edges.size<=1999 })
    }

    @Test fun defaultPrivacyAndLegacyPresetCompatibility() {
        val state=ShareStudioState()
        assertEquals(ShareBackground.DARK,state.background)
        assertEquals(SharePrivacy.STANDARD,state.privacy)
        val encoded=SharePresetCodec.encode(listOf(SharePreset("old","Old",state.copy(background=ShareBackground.MAP))))
        val old=org.json.JSONObject(encoded)
        old.getJSONArray("presets").getJSONObject(0).remove("privacy")
        val restored=SharePresetCodec.decode(old.toString()).single().state
        assertEquals(SharePrivacy.STANDARD,restored.privacy)
        assertEquals(ShareBackground.MAP,restored.background)
        val changed=state.copy(privacy=SharePrivacy.MAXIMUM)
        assertEquals(changed,SharePresetCodec.decode(SharePresetCodec.encode(listOf(SharePreset("new","New",changed)))).single().state)
    }

    @Test fun loopEdgeCrossingHiddenEndpointIsCutEvenWhenBothFixesAreOutside() {
        val coords=listOf(14.0 to 121.0,14.003 to 121.0,14.003 to 121.004,14.0 to 121.004,
            14.0 to 120.996,14.004 to 120.996,14.008 to 121.0,14.012 to 121.0)
        val samples=coords.mapIndexed { i,(lat,lon) -> sample(i,lat).copy(longitude=lon) }
        val route=shareRoutePresentation(samples,emptyList(),200.0,true).route
        assertTrue(route.segments.size>=2)
        assertTrue(route.segments.none { segment -> segment.zipWithNext().any { (a,b) ->
            a.elapsedSeconds==30.0 && b.elapsedSeconds==40.0
        } })
    }
}

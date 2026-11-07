package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ShareStudioTest {
    private val activity = ActivityRecord("share-test", "Morning run", "run", "manual",
        Instant.parse("2026-11-07T00:00:00Z"), 5000.0, 1800.0, null, 360.0, null, null)
    private fun sample(i:Int, seconds:Double, hr:Double? = null) =
        ActivitySample(i, seconds, 350.0 + i, hr, 10.0 + i, 14.6+i*.001, 121.0+i*.001)
    private fun detail(samples:List<ActivitySample>, pauses:List<ActivityPauseInterval> = emptyList()) =
        ActivityDetail(activity.id, samples, emptyList(), 0, pauses)

    @Test fun routeModesRequireRecordedGeographicMetricPairs() {
        val render = ShareRenderableActivity.from(activity, detail(listOf(sample(0,0.0),sample(1,10.0))))
        assertEquals(setOf(ShareRouteMode.ROUTE, ShareRouteMode.PACE, ShareRouteMode.ELEVATION, ShareRouteMode.HIDDEN), render.availableModes)
        assertFalse(render.metrics.containsKey(ShareMetric.HR))
        assertEquals(setOf(ShareRouteMode.HIDDEN), ShareRenderableActivity.from(activity,null).availableModes)
    }

    @Test fun presetMissingFieldsAreOmittedWithoutChangingStoredConfiguration() {
        val saved = ShareStudioState(routeMode=ShareRouteMode.HR, metrics=setOf(ShareMetric.HR))
        val preset = SharePreset("id", "HR design", saved)
        val render = ShareRenderableActivity.from(activity,null)
        val effective = preset.state.forActivity(render)
        assertFalse(ShareMetric.HR in effective.metrics)
        assertTrue(effective.metrics.isNotEmpty())
        assertEquals(ShareRouteMode.HIDDEN,effective.routeMode)
        assertEquals(saved,preset.state)
    }

    @Test fun templateAndVariantSelectionPreserveRatioAndChooseDefaults() {
        val state = ShareStudioState(ratio=ShareRatio.SQUARE)
            .selectTemplate(ShareTemplate.MINIMAL).selectVariant(ShareVariant.SILHOUETTE)
        assertEquals(ShareRatio.SQUARE,state.ratio)
        assertEquals(ShareTemplate.MINIMAL,state.template)
        assertEquals(ShareVariant.SILHOUETTE,state.variant)
        assertTrue(state.metrics.size <= 3)
        assertEquals(ShareVariant.BALANCED,state.selectTemplate(ShareTemplate.STATS).variant)
    }

    @Test fun sourceDataRemainsUnchangedAndRouteBreaksAtPausesAndGaps() {
        val samples = listOf(sample(0,0.0,120.0),sample(1,10.0,125.0),sample(2,30.0,130.0),sample(3,200.0,140.0),sample(4,210.0,145.0))
        val source = detail(samples,listOf(ActivityPauseInterval(15.0,25.0)))
        val before = source.copy(samples=source.samples.toList())
        val render = ShareRenderableActivity.from(activity,source)
        assertEquals(before,source)
        assertEquals(3,render.route.segments.size)
        val edges = render.analysis.getValue(ShareRouteMode.HR).edges
        assertEquals(listOf(0.0 to 10.0,200.0 to 210.0),edges.map { it.start.elapsedSeconds to it.end.elapsedSeconds })
        assertFalse(render.metrics.containsKey(ShareMetric.HR)) // samples do not invent a summary average
    }

    @Test fun presetCodecRoundTripAndDeletion() {
        val preset = SharePreset("one","My poster",ShareStudioState(ratio=ShareRatio.PORTRAIT,
            background=ShareBackground.GRADIENT,branding=ShareBranding.MINIMAL,layout=ShareLayout.COMPACT))
        assertEquals(listOf(preset), SharePresetCodec.decode(SharePresetCodec.encode(listOf(preset))))
        assertEquals(emptyList<SharePreset>(),SharePresetCodec.decode(SharePresetCodec.encode(listOf(preset).filterNot { it.id=="one" })))
    }

    @Test fun exportDimensionsPreserveEveryRatio() {
        assertEquals(1080 to 1920,ShareRatio.STORY.dimensions)
        assertEquals(1080 to 1350,ShareRatio.PORTRAIT.dimensions)
        assertEquals(1080 to 1080,ShareRatio.SQUARE.dimensions)
    }

    @Test fun missingCoordinatesOrNonadjacentSamplesCannotExposeAnalyticalModes() {
        val samples=listOf(sample(0,0.0),sample(1,10.0).copy(latitude=null),sample(2,20.0))
        val render=ShareRenderableActivity.from(activity,detail(samples))
        assertFalse(ShareRouteMode.PACE in render.availableModes)
        assertFalse(ShareRouteMode.ELEVATION in render.availableModes)
    }

    @Test fun hrRouteRequiresRecordedPairsAndDoesNotCreateSummaryHr() {
        val render=ShareRenderableActivity.from(activity,detail(listOf(sample(0,0.0,120.0),sample(1,10.0,130.0))))
        assertTrue(ShareRouteMode.HR in render.availableModes)
        assertFalse(ShareMetric.HR in render.metrics)
    }

    @Test fun everyTemplateVariantHasValidDefaultsWithoutChangingRatio() {
        ShareTemplate.entries.forEach { template -> template.variants.forEach { variant ->
            val state=ShareStudioState(ratio=ShareRatio.PORTRAIT).selectTemplate(template).selectVariant(variant)
            assertEquals(template,state.template)
            assertEquals(variant,state.variant)
            assertEquals(ShareRatio.PORTRAIT,state.ratio)
            assertTrue(state.metrics.isNotEmpty())
        } }
    }

    @Test(expected=IllegalArgumentException::class)
    fun invalidTemplateVariantIsRejected() {
        ShareStudioState().selectVariant(ShareVariant.POSTER)
    }

    @Test(expected=IllegalArgumentException::class)
    fun futurePresetVersionDoesNotSilentlyEraseStoredDesigns() {
        SharePresetCodec.decode("{\"version\":2,\"presets\":[]}")
    }
}

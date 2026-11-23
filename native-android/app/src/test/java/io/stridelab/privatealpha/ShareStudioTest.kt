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
        val render = ShareRenderableActivity.from(activity, detail((0..8).map { sample(it,it*10.0) }))
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
        val samples = (0..12).filter { it!=4 }.map { sample(it, it*10.0 + if(it>=8) 200.0 else 0.0,120.0+it) }
        val source = detail(samples,listOf(ActivityPauseInterval(35.0,45.0)))
        val before = source.copy(samples=source.samples.toList())
        val render = ShareRenderableActivity.from(activity,source)
        assertEquals(before,source)
        assertEquals(2,render.route.segments.size)
        assertTrue(render.route.segments.first().zipWithNext().any { (a,b) -> a.elapsedSeconds==30.0 && b.elapsedSeconds==50.0 })
        val edges = render.analysis.getValue(ShareRouteMode.HR).edges
        assertTrue(edges.none { it.start.elapsedSeconds==30.0 && it.end.elapsedSeconds==50.0 })
        assertFalse(render.metrics.containsKey(ShareMetric.HR)) // samples do not invent a summary average
    }

    @Test fun defaultSharePrivacySurvivesLegacyPresets() {
        assertEquals(SharePrivacy.STANDARD,ShareStudioState().privacy)
        assertEquals(ShareBackground.MAP,ShareStudioState().background)
        ShareBackground.entries.forEach { background ->
            val state=ShareStudioState(background=background)
            assertEquals(background,state.selectTemplate(ShareTemplate.STATS).background)
            val preset=SharePreset("background","Background",state)
            assertEquals(preset,SharePresetCodec.decode(SharePresetCodec.encode(listOf(preset))).single())
        }
        val original=SharePreset("one","Original",ShareStudioState(background=ShareBackground.MAP))
        val json=org.json.JSONObject(SharePresetCodec.encode(listOf(original)))
        json.getJSONArray("presets").getJSONObject(0).remove("privacy")
        val decoded=SharePresetCodec.decode(json.toString()).single()
        assertEquals(SharePrivacy.STANDARD,decoded.state.privacy)
        assertEquals(ShareBackground.MAP,decoded.state.background)
    }

    @Test fun routeBalanceRoundTripAndLegacyDefaults() {
        val original=ShareStudioState(routeBalance=80)
        val raw=SharePresetCodec.encode(listOf(
            SharePreset("route-balance","Route balance",original)))
        assertEquals(80,SharePresetCodec.decode(raw).single().state.routeBalance)
        val old=org.json.JSONObject(raw)
        old.getJSONArray("presets").getJSONObject(0).remove("routeBalance")
        assertEquals(50,SharePresetCodec.decode(old.toString()).single().state.routeBalance)
        val corrupt=org.json.JSONObject(raw)
        corrupt.getJSONArray("presets").getJSONObject(0).put("routeBalance",999)
        assertThrows(IllegalArgumentException::class.java) {
            SharePresetCodec.decode(corrupt.toString())
        }
    }

    @Test fun metricOrderingWorksAndPreservesLegacyPresetDefaults() {
        val defaults=ShareStudioState()
        val available=setOf(ShareMetric.DISTANCE,ShareMetric.DURATION,ShareMetric.PACE)
        assertEquals(listOf(ShareMetric.DISTANCE,ShareMetric.DURATION,ShareMetric.PACE),
            defaults.visibleNumericMetrics(available))
        val moved=defaults.moveMetric(ShareMetric.PACE,-1)
            .moveMetric(ShareMetric.PACE,-1)
        assertEquals(listOf(ShareMetric.PACE,ShareMetric.DISTANCE,ShareMetric.DURATION),
            moved.visibleNumericMetrics(available))
        assertEquals(moved,SharePresetCodec.decode(
            SharePresetCodec.encode(listOf(SharePreset("metric-order","Metric order",moved)))
        ).single().state)
        val legacy=SharePreset("legacy","Unordered",defaults)
        val json=org.json.JSONObject(SharePresetCodec.encode(listOf(legacy)))
        json.getJSONArray("presets").getJSONObject(0).remove("metricOrder")
        assertEquals(emptyList<ShareMetric>(),
            SharePresetCodec.decode(json.toString()).single().state.metricOrder)
        assertEquals(defaults,SharePresetCodec.decode(json.toString()).single().state)
    }

    @Test(expected=IllegalArgumentException::class)
    fun duplicateMetricOrderIsRejectedWithoutTouchingSavedPresets() {
        val raw=org.json.JSONObject(SharePresetCodec.encode(listOf(
            SharePreset("duplicate-order","Duplicate",ShareStudioState()))))
        raw.getJSONArray("presets").getJSONObject(0)
            .put("metricOrder", org.json.JSONArray().put("PACE").put("PACE"))
        SharePresetCodec.decode(raw.toString())
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
        val render=ShareRenderableActivity.from(activity,detail((0..8).map { sample(it,it*10.0,120.0+it) }))
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

    @Test fun workoutRecapUsesUserLabelAndRecordedGarminLapsWithoutInventingSteps() {
        val garmin=activity.copy(source="garmin")
        val laps=listOf(ActivityLap(0, 1000.0, 255.0, 255.0, 165.0, 0.0),
            ActivityLap(1, 350.0, 140.0, null, null, 255.0))
        val source=ActivityDetail(garmin.id, emptyList(), laps, 0L)
        val before=source.copy(laps=source.laps.toList())
        val userLabel=UserActivityWorkoutLabel(ActivityWorkoutKind.QUALITY_RUN,ActivityQualitySubtype.INTERVALS)
        val recap=ShareRenderableActivity.from(garmin,source,userLabel=userLabel).workout!!
        assertEquals("Intervals",recap.label)
        assertEquals(ShareWorkoutSource.USER_LABEL,recap.provenance)
        assertEquals("Garmin",recap.recordingSource)
        assertEquals(2,recap.totalLaps)
        assertEquals("1.00 km",recap.laps[0].distance)
        assertEquals("4:15",recap.laps[0].duration)
        assertEquals("4:15 /km",recap.laps[0].pace)
        assertEquals("165 bpm",recap.laps[0].heartRate)
        assertNull(recap.laps[1].pace)
        assertNull(recap.laps[1].heartRate)
        assertEquals(before,source)
    }

    @Test fun unlabeledFitLapsDoNotBecomeDetectedIntervals() {
        val fit=activity.copy(source="fit")
        val source=ActivityDetail(fit.id,emptyList(),
            listOf(ActivityLap(0,400.0,95.0,null,null),
                   ActivityLap(1,200.0,80.0,null,null)),0L)
        val recap=ShareRenderableActivity.from(fit,source).workout!!
        assertNull(recap.label)
        assertEquals(ShareWorkoutSource.RECORDED_ONLY,recap.provenance)
        assertEquals("FIT import",recap.recordingSource)
        assertEquals(2,recap.totalLaps)
        assertNull(recap.laps.first().pace)
    }

    @Test fun linkedPlanWorkoutLabelIsClearlyIntentNotPerformance() {
        val assessment=ActivityWorkoutAssessment(
            detected=ActivityWorkoutEvidence(ActivityWorkoutKind.UNKNOWN,
                evidence=listOf("Unlabeled laps")),
            intended=ActivityWorkoutEvidence(ActivityWorkoutKind.QUALITY_RUN,
                ActivityQualitySubtype.THRESHOLD,listOf("Linked plan intent")))
        val source=ShareRenderableActivity.from(activity,null,assessment=assessment)
        assertEquals("Threshold",source.workout!!.label)
        assertEquals(ShareWorkoutSource.LINKED_PLAN,source.workout!!.provenance)
        assertTrue(source.workout!!.laps.isEmpty())
    }

    @Test fun detectedWorkoutEvidenceIsPreferredToUnverifiedLinkedIntent() {
        val assessment=ActivityWorkoutAssessment(
            detected=ActivityWorkoutEvidence(ActivityWorkoutKind.QUALITY_RUN,
                ActivityQualitySubtype.INTERVALS,listOf("Verified structured step source")),
            intended=ActivityWorkoutEvidence(ActivityWorkoutKind.QUALITY_RUN,
                ActivityQualitySubtype.TEMPO,listOf("Planned tempo")))
        val recap=ShareRenderableActivity.from(activity,null,assessment=assessment).workout!!
        assertEquals("Intervals",recap.label)
        assertEquals(ShareWorkoutSource.DETECTED,recap.provenance)
    }

    @Test fun workoutTemplatePersistsWithExistingPresetVersionOne() {
        val state=ShareStudioState().selectTemplate(ShareTemplate.WORKOUT)
        assertEquals(ShareVariant.RUN_RECAP,state.variant)
        assertEquals(ShareLayout.COMPACT,state.layout)
        assertTrue(ShareMetric.DISTANCE in state.metrics)
        val preset=SharePreset("workout","Lap recap",state.copy(ratio=ShareRatio.SQUARE))
        assertEquals(preset,SharePresetCodec.decode(SharePresetCodec.encode(listOf(preset))).single())
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

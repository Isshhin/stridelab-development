package io.stridelab.privatealpha

import android.graphics.BitmapFactory
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID

/** Synthetic offline fixtures only. Owner-run Android Studio coverage; no live account or map requests. */
class ShareStudioUiTest {
    @get:Rule val compose=createAndroidComposeRule<NativeMainActivity>()
    private val activity=ActivityRecord("share-ui-test","Morning run","run","manual",
        Instant.parse("2026-11-07T00:00:00Z"),5000.0,1800.0,null,360.0,null,null)
    private val detail=ActivityDetail(activity.id,listOf(
        ActivitySample(0,0.0,350.0,null,10.0,14.6,121.0),
        ActivitySample(1,10.0,360.0,null,12.0,14.601,121.001),
        ActivitySample(2,20.0,370.0,null,11.0,14.602,121.002)),
        listOf(ActivityLap(0,1000.0,360.0,360.0,null,0.0),ActivityLap(1,1000.0,360.0,360.0,null,10.0)),0L)

    @Test fun routeFocusWithoutGpsUsesRecordedStatsNotInventedTrace() {
        val source=ShareRenderableActivity.from(activity,null)
        assertEquals(0,source.route.pointCount)
        val state=ShareStudioState().forActivity(source)
        assertEquals(ShareRouteMode.HIDDEN,state.routeMode)
        val image=ShareCardRenderer.render(source,state)
        try {
            // Honest stats-first fallback uses the Performance footer, not a fake route.
            assertEquals(ShareCardPalette.LIME,image.getPixel(0,image.height-2))
        } finally { image.recycle() }
    }

    @Test fun performanceFooterIsLimeOnlyForOpaqueExport() {
        val source=ShareRenderableActivity.from(activity,null)
        val base=ShareStudioState(ratio=ShareRatio.SQUARE)
            .selectTemplate(ShareTemplate.STATS).forActivity(source)
        val opaque=ShareCardRenderer.render(source,base)
        val transparent=ShareCardRenderer.render(source,base.copy(transparent=true))
        try {
            assertEquals(ShareCardPalette.LIME,opaque.getPixel(0,opaque.height-2))
            assertEquals(0,android.graphics.Color.alpha(
                transparent.getPixel(0,transparent.height-2)))
        } finally {
            opaque.recycle(); transparent.recycle()
        }
    }

    @Test fun legacyFiveRecordedMetricsRemainRenderableAfterTwoOneUpdate() {
        val withMoreMetrics=activity.copy(averageHr=143.0,elevationM=25.0)
        val source=ShareRenderableActivity.from(withMoreMetrics,null)
        val selected=setOf(ShareMetric.DISTANCE,ShareMetric.DURATION,
            ShareMetric.PACE,ShareMetric.HR,ShareMetric.ELEVATION)
        val legacy=ShareStudioState().selectTemplate(ShareTemplate.STATS)
            .copy(metrics=selected)
        val restored=SharePresetCodec.decode(
            SharePresetCodec.encode(listOf(SharePreset("legacy","Five metrics",legacy)))
        ).single().state
        assertEquals(selected,restored.forActivity(source).metrics.intersect(selected))
        val png=ShareCardRenderer.render(source,restored.forActivity(source))
        try {
            assertEquals(1080,png.width)
            assertEquals(ShareRatio.STORY.height,png.height)
        } finally { png.recycle() }
        val fresh=ShareStudioState().selectTemplate(ShareTemplate.STATS)
        assertEquals(4,fresh.metrics.count {
            it in setOf(ShareMetric.DISTANCE,ShareMetric.DURATION,
                ShareMetric.PACE,ShareMetric.HR,ShareMetric.ELEVATION)
        })
    }

    private fun mountDetail() {
        val tab=mutableIntStateOf(0)
        compose.runOnUiThread { compose.activity.setContent {
            StrideTheme { TracksideFigmaActivityDetailSurface(activity,detail,activity,setOf(activity.id),listOf(activity),
                TrainingState(initializing=false,account="share-test",activities=listOf(activity)),
                NativeActions({}, {}, {}, {}, { _, _ -> }),false,tab.intValue,{tab.intValue=it},false,{}, {}) }
        } }
    }

    @Test fun opensAndClosesWithoutChangingCollapsedActivity() {
        mountDetail()
        compose.onNodeWithTag("activity-share").performClick()
        compose.onNodeWithTag("share-studio").assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("share-card-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("share-studio-back").performClick()
        compose.onNodeWithTag("share-studio").assertDoesNotExist()
        compose.onNodeWithTag("activity-primary-metrics").assertIsDisplayed()
        compose.onNodeWithTag("activity-sticky-pill").assertIsNotDisplayed()
    }

    @Test fun expandedSplitSelectionSurvivesShareStudio() {
        mountDetail()
        compose.onNodeWithTag("activity-sheet-handle").performClick()
        compose.onNodeWithTag("activity-tab-splits").performClick()
        compose.onNodeWithTag("activity-continuous-scroll").performScrollToNode(hasTestTag("activity-split-0"))
        compose.onNodeWithTag("activity-split-0").performClick().assert(hasContentDescription("selected on map",substring=true))
        compose.onNodeWithTag("activity-share-expanded").performClick()
        compose.onNodeWithTag("share-studio-back").performClick()
        compose.onNodeWithTag("activity-tab-splits").assertIsSelected()
        compose.onNodeWithTag("activity-split-0").assert(hasContentDescription("selected on map",substring=true))
    }

    @Test fun canvasRemainsVisibleAcrossEditingTools() {
        mountDetail()
        compose.onNodeWithTag("activity-share").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("share-card-preview").fetchSemanticsNodes().isNotEmpty() }
        // The poster is immediately visible; tools are optional, not an onboarding gate.
        compose.onNodeWithTag("share-tool-sheet").assertDoesNotExist()
        compose.onNodeWithTag("share-section-TEMPLATE").assertIsDisplayed()
        listOf("TEMPLATE","LAYOUT","MAP","STATS","STYLE","EXPORT").forEach { tool ->
            compose.onNodeWithTag("share-section-$tool").assertIsDisplayed().performClick()
            compose.onNodeWithTag("share-section-$tool").assertIsSelected()
            compose.onNodeWithTag("share-card-preview").assertIsDisplayed()
        }
        compose.onNodeWithTag("share-save-image").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("share-send").assertIsDisplayed()
    }

    @Test fun presetsPersistUpdateAndDeleteOnDisk() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="share-test-${UUID.randomUUID()}"
        val store=SharePresetStore(context,file)
        try {
            val preset=SharePreset("id","Original",ShareStudioState(routeMode=ShareRouteMode.HR,metrics=setOf(ShareMetric.HR)))
            store.save(listOf(preset))
            assertEquals(listOf(preset),SharePresetStore(context,file).load())
            val update=preset.copy(name="Updated",state=preset.state.copy(ratio=ShareRatio.SQUARE))
            store.save(listOf(update))
            assertEquals(listOf(update),SharePresetStore(context,file).load())
            store.save(emptyList())
            assertTrue(SharePresetStore(context,file).load().isEmpty())
        } finally { context.getSharedPreferences(file,0).edit().clear().commit() }
    }

    @Test fun everyTemplateRatioProducesPngAtExpectedDimensionsAndSecureShareUri() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=ShareRenderableActivity.from(activity,detail)
        ShareTemplate.entries.forEach { template -> ShareRatio.entries.forEach { ratio ->
            val state=ShareStudioState().selectTemplate(template).copy(ratio=ratio).forActivity(source)
            val bitmap=ShareCardRenderer.render(source,state)
            try {
                val bytes=ByteArrayOutputStream()
                ShareImageExport.writePng(bitmap,bytes)
                val decoded=BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size())
                assertEquals(1080,decoded.width); assertEquals(ratio.height,decoded.height)
                assertEquals(bitmap.getPixel(0,0),decoded.getPixel(0,0))
                assertTrue(bitmap.sameAs(decoded))
                decoded.recycle()
                if(template==ShareTemplate.ROUTE && ratio==ShareRatio.STORY) {
                    val intent=ShareImageExport.shareIntent(context,bitmap)
                    assertEquals(Intent.ACTION_SEND,intent.action)
                    assertEquals("image/png",intent.type)
                    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                    val uri=intent.clipData!!.getItemAt(0).uri
                    assertEquals("content",uri.scheme)
                    assertEquals(context.packageName+".share-studio",uri.authority)
                    context.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read()>=0) }
                    context.contentResolver.delete(uri,null,null)
                }
            } finally { bitmap.recycle() }
        } }
    }

    @Test fun cachedPickerImageSurvivesPreviewDisposal() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=ShareRenderableActivity.from(activity,detail)
        val bitmap=ShareCardRenderer.render(source,ShareStudioState().copy(ratio=ShareRatio.SQUARE).forActivity(source))
        val file=ShareImageExport.cachePng(context,bitmap)
        bitmap.recycle()
        try {
            val restored=java.io.File(java.io.File(context.cacheDir,"share-studio"),file.name)
            val decoded=BitmapFactory.decodeFile(restored.absolutePath)
            assertEquals(1080,decoded.width); assertEquals(1080,decoded.height)
            decoded.recycle()
        } finally { file.delete() }
    }

    @Test fun gallerySavePublishesExactPngAndCanBeRemoved() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=ShareRenderableActivity.from(activity,detail)
        val bitmap=ShareCardRenderer.render(source,ShareStudioState().forActivity(source))
        var uri:android.net.Uri?=null
        try {
            uri=ShareImageExport.saveToGallery(context,bitmap)
            context.contentResolver.openInputStream(uri)!!.use {
                val decoded=BitmapFactory.decodeStream(it)
                assertTrue(bitmap.sameAs(decoded)); decoded.recycle()
            }
            context.contentResolver.query(uri,arrayOf(android.provider.MediaStore.Images.Media.IS_PENDING),null,null,null)!!.use {
                assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0))
            }
        } finally { uri?.let { context.contentResolver.delete(it,null,null) }; bitmap.recycle() }
    }

    @Test fun offlinePausedRouteRendersAndExportMatchesPreviewBitmap() {
        val samples=(0..16).filter { it!=8 }.map { i ->
            ActivitySample(i,i*10.0,350.0+i,120.0+i,10.0+i,14.6+i*.001,121.0)
        }
        val source=ShareRenderableActivity.from(activity,ActivityDetail(activity.id,samples,emptyList(),0L,
            listOf(ActivityPauseInterval(75.0,85.0))))
        assertEquals(1,source.route.segments.size)
        assertTrue(source.route.allPoints.first().latitude>samples.first().latitude!!)
        assertTrue(source.route.allPoints.last().latitude<samples.last().latitude!!)
        val state=ShareStudioState(ratio=ShareRatio.SQUARE).forActivity(source)
        val bitmap=ShareCardRenderer.render(source,state,null)
        try {
            val geometry=ShareCardRenderer.geometry(state).route
            var routePixels=0
            for(y in geometry.top.toInt() until geometry.bottom.toInt()) {
                for(x in geometry.left.toInt() until geometry.right.toInt()) {
                    if(bitmap.getPixel(x,y)==ShareCardPalette.LIME) routePixels++
                }
            }
            assertTrue(routePixels>0)
            val bytes=ByteArrayOutputStream()
            ShareImageExport.writePng(bitmap,bytes)
            val png=BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size())
            assertTrue(bitmap.sameAs(png))
            png.recycle()
        } finally { bitmap.recycle() }
    }

    @Test fun longRunRendersAndExportsOffline() {
        val samples=(0 until 25000).map { i ->
            ActivitySample(i,i.toDouble(),350.0,125.0,10.0,14.0+i*.00001,121.0)
        }
        val source=ShareRenderableActivity.from(activity,ActivityDetail(activity.id,samples,emptyList(),0L))
        assertTrue(source.route.pointCount<=2000)
        assertTrue(source.route.pointCount>1)
        val bitmap=ShareCardRenderer.render(source,ShareStudioState(ratio=ShareRatio.SQUARE).forActivity(source),null)
        try {
            val bytes=ByteArrayOutputStream()
            ShareImageExport.writePng(bitmap,bytes)
            assertTrue(bytes.size()>0)
        } finally { bitmap.recycle() }
    }

    @Test fun premiumTemplatesUseDistinctRouteLayoutsAcrossRatios() {
        ShareRatio.entries.forEach { ratio ->
            val route=ShareCardRenderer.geometry(ShareStudioState(ratio=ratio))
            val performance=ShareCardRenderer.geometry(ShareStudioState(ratio=ratio).selectTemplate(ShareTemplate.STATS))
            val minimal=ShareCardRenderer.geometry(ShareStudioState(ratio=ratio).selectTemplate(ShareTemplate.MINIMAL))
            assertTrue(performance.route.width()<route.route.width())
            assertTrue(minimal.route.width()<route.route.width())
            listOf(route,performance,minimal).forEach { geometry ->
                assertTrue(geometry.route.left>=0 && geometry.route.right<=1080)
                assertTrue(geometry.route.top>=0 && geometry.route.bottom<=ratio.height)
                assertTrue(geometry.route.width()>0 && geometry.route.height()>0)
            }
        }
    }

    @Test fun transparentPngHasClearBackgroundAndScaledPreview() {
        val samples=(0..15).map { i -> ActivitySample(i,i*10.0,350.0,120.0,10.0,14.6+i*.001,121.0) }
        val source=ShareRenderableActivity.from(activity,ActivityDetail(activity.id,samples,emptyList(),0L))
        val state=ShareStudioState(ratio=ShareRatio.SQUARE,transparent=true).forActivity(source)
        val opaqueMap=android.graphics.Bitmap.createBitmap(10,10,android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        val full=ShareCardRenderer.render(source,state,opaqueMap)
        val preview=ShareCardRenderer.render(source,state,width=540)
        try {
            assertEquals(0,android.graphics.Color.alpha(full.getPixel(0,0)))
            assertEquals(0,android.graphics.Color.alpha(full.getPixel(500,400)))
            assertNotEquals(android.graphics.Color.MAGENTA,full.getPixel(500,400))
            assertEquals(540,preview.width)
            assertEquals(540,preview.height)
            val bytes=ByteArrayOutputStream()
            ShareImageExport.writePng(full,bytes)
            val decoded=BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size())
            assertTrue(full.sameAs(decoded)); decoded.recycle()
        } finally { full.recycle(); preview.recycle(); opaqueMap.recycle() }
    }

    @Test fun previewAndExportMatchForEveryTemplateAndRatio() {
        val samples=(0..30).map { i ->
            ActivitySample(i,i*10.0,350.0+i,120.0+i,10.0+i,14.6+i*.001,121.0+kotlin.math.sin(i/5.0)*.004)
        }
        val fixture=ActivityDetail(activity.id,samples,emptyList(),0L)
        val source=ShareRenderableActivity.from(activity,fixture)
        val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"share-review")
        assertTrue(directory.isDirectory || directory.mkdirs())
        ShareTemplate.entries.forEach { template -> ShareRatio.entries.forEach { ratio ->
            val state=ShareStudioState(ratio=ratio).selectTemplate(template).forActivity(source)
            val full=ShareCardRenderer.render(source,state)
            val preview=ShareCardRenderer.render(source,state,width=540)
            val resized=android.graphics.Bitmap.createScaledBitmap(full,preview.width,preview.height,true)
            try {
                var difference=0L; var count=0
                for(y in 0 until preview.height step 5) for(x in 0 until preview.width step 5) {
                    val a=preview.getPixel(x,y); val b=resized.getPixel(x,y)
                    difference+=kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))
                    difference+=kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))
                    difference+=kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b)); count+=3
                }
                assertTrue("$template $ratio preview mismatch",difference.toDouble()/count<8.0)
                File(directory,"${template.name.lowercase()}-${ratio.name.lowercase()}.png").outputStream().use { full.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            } finally { full.recycle(); preview.recycle(); resized.recycle() }
        } }
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { ShareStudioScreen(activity,fixture,false,{}) } } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("share-card-preview").fetchSemanticsNodes().isNotEmpty() }
        val screen=compose.onNodeWithTag("share-studio").captureToImage().asAndroidBitmap()
        File(directory,"editor.png").outputStream().use { screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun revisedEditorHasVisiblePrivacyAndExistingPresetEntry() {
        val samples=(0..22).map { i ->
            ActivitySample(i,i*10.0,350.0,120.0,10.0,14.6+i*.001,121.0)
        }
        val fixture=ActivityDetail(activity.id,samples,emptyList(),0L)
        compose.runOnUiThread { compose.activity.setContent {
            StrideTheme { ShareStudioScreen(activity,fixture,false,{}) }
        } }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("share-privacy-indicator").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("share-privacy-indicator").assertIsDisplayed()
        compose.onNodeWithTag("share-preview-status").assertDoesNotExist()
        compose.onNodeWithTag("share-section-MAP").performClick()
        compose.onNodeWithText("200 m").assertIsDisplayed()
        compose.onNodeWithText("400 m").assertIsDisplayed()
        compose.onNodeWithText("800 m").assertIsDisplayed()
        compose.onNodeWithText("Off").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("share-presets-open").performClick()
        compose.onNodeWithTag("share-presets-sheet").assertIsDisplayed()
        compose.onNodeWithTag("share-save-preset").assertIsDisplayed()
        compose.onNodeWithTag("share-card-preview").assertIsDisplayed()
    }

    @Test fun metricRowsUseWholeCardSelectionAndRecordedDurationLabel() {
        val fixture=detail
        compose.runOnUiThread { compose.activity.setContent {
            StrideTheme { ShareStudioScreen(activity,fixture,false,{}) }
        } }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("share-card-preview").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("share-section-STATS").performClick()
        compose.onNodeWithTag("share-metric-DURATION").assertIsSelected()
        compose.onNodeWithText("Moving time").assertDoesNotExist()
        compose.onNodeWithTag("share-metric-DISTANCE").assertIsSelected()
        // Stage 2 metadata visibility remains editable in the refined sheet.
        compose.onNodeWithTag("share-detail-TITLE").assertIsSelected()
        compose.onNodeWithTag("share-detail-TITLE").performClick().assertIsNotSelected()
        compose.onNodeWithTag("share-card-preview").assertIsDisplayed()
    }

    @Test fun unavailableStreetMapIsDisclosedOffline() {
        val samples=(0..15).map { i -> ActivitySample(i,i*10.0,350.0,120.0,10.0,14.6+i*.001,121.0) }
        val fixture=ActivityDetail(activity.id,samples,emptyList(),0L)
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { ShareStudioScreen(activity,fixture,false,{}) } } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("share-card-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("share-section-MAP").performClick()
        compose.onNodeWithText("Map").performClick()
        compose.onNodeWithText("Activity Dark Map").performScrollTo().performClick()
        compose.onNodeWithText("Street map unavailable · showing route only").assertIsDisplayed()
        compose.onNodeWithTag("share-card-preview").assertIsDisplayed()
    }
}

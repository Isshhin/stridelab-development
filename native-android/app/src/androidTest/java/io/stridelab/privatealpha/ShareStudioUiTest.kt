package io.stridelab.privatealpha

import android.graphics.BitmapFactory
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
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
    private val cityDetail = detail.copy(samples = (0..14).map { index ->
        ActivitySample(index, index * 30.0, 360.0, null, null,
            37.775 + index * .0005, -122.425 + index * .0006)
    })

    private fun mountDetail() {
        val tab=mutableIntStateOf(0)
        compose.runOnUiThread { compose.activity.setContent {
            StrideTheme { TracksideActivityDetailSurface(activity,detail,activity,setOf(activity.id),listOf(activity),
                TrainingState(initializing=false,account="share-test",activities=listOf(activity)),
                NativeActions({}, { _, _ -> }),false,tab.intValue,{tab.intValue=it},false,{}, {}) }
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

    @Test fun presetNameDialogRetainsItsSaveActions() {
        mountDetail()
        compose.onNodeWithTag("activity-share").performClick()
        compose.onNodeWithTag("share-section-PRESETS").performClick()
        compose.onNodeWithTag("share-studio-scroll").performScrollToNode(hasTestTag("share-save-preset"))
        compose.onNodeWithTag("share-save-preset").performClick()
        compose.onNodeWithTag("share-preset-name").assertIsDisplayed()
        compose.onNodeWithText("Save new").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test fun privacyTrimControlShowsApprovedDefaults() {
        mountDetail()
        compose.onNodeWithTag("activity-share").performClick()
        compose.onNodeWithTag("share-studio-scroll").performScrollToNode(hasTestTag("share-section-ROUTE"))
        compose.onNodeWithTag("share-section-ROUTE").performClick()
        compose.onNodeWithTag("share-studio-scroll").performScrollToNode(hasText("Trim distance from each endpoint"))
        compose.onNodeWithText("Trim distance from each endpoint").assertIsDisplayed()
        // The LazyColumn may clip choices below the viewport on a smaller emulator.
        // Verify the options and default selection via semantics, not pixel visibility.
        compose.onNodeWithTag("share-trim-switch").assertIsOn()
        compose.onNodeWithText("200 m").assertExists().assertIsSelected()
        compose.onNodeWithText("400 m").assertExists().assertIsNotSelected()
        compose.onNodeWithText("800 m").assertExists().assertIsNotSelected()
        compose.onNodeWithText("Off (full route)").assertDoesNotExist()
    }

    @Test fun routeTrimSwitchRestoresCustomDistanceAndWarnsOnFullGpsExposure() {
        mountDetail()
        compose.onNodeWithTag("activity-share").performClick()
        compose.onNodeWithTag("share-studio-scroll").performScrollToNode(hasTestTag("share-section-ROUTE"))
        compose.onNodeWithTag("share-section-ROUTE").performClick()
        compose.onNodeWithTag("share-studio-scroll").performScrollToNode(hasTestTag("share-trim-switch"))
        compose.onNodeWithTag("share-studio-scroll").performScrollToNode(hasTestTag("share-trim-400"))
        compose.onNodeWithTag("share-trim-400").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("share-trim-400").assertIsSelected()
        compose.onNodeWithTag("share-trim-switch").performClick().assertIsOff()
        compose.onNodeWithTag("share-trim-off-warning").assertExists()
        compose.onNodeWithTag("share-trim-400").assertDoesNotExist()
        compose.onNodeWithTag("share-trim-switch").performClick().assertIsOn()
        compose.waitForIdle()
        compose.onNodeWithTag("share-trim-400").assertExists().assertIsSelected()
        compose.onNodeWithTag("share-trim-off-warning").assertDoesNotExist()
    }

    @Test fun exportPngActionAvailableForOfflineActivity() {
        mountDetail()
        compose.onNodeWithTag("activity-share").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("share-card-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("share-export-png").assertExists().assertIsEnabled()
        compose.onNodeWithTag("share-export-resolution").assertTextContains("1080 x 1920", substring = true)
    }

    @Test fun workoutRecapAvailableForImportedLapsWithoutClaimingDetectedIntervals() {
        // Isolate the imported-lap fixture from persisted user workout corrections and plan
        // intent. The activity-detail entry point is covered by separate navigation tests.
        val imported = activity.copy(id = "share-ui-unclassified-fit", source = "fit")
        val importedDetail = detail.copy(activityId = imported.id)
        assertEquals(2, importedDetail.laps.size)
        val recap = ShareWorkoutRecap.from(imported, importedDetail, null, null)
        assertEquals(2, recap.totalLaps)
        assertEquals(ShareWorkoutSource.RECORDED_ONLY, recap.provenance)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    ShareStudioScreen(imported, importedDetail, online = false, onBack = {})
                }
            }
        }
        compose.onNodeWithTag("share-studio-scroll")
            .performScrollToNode(hasTestTag("share-template-WORKOUT"))
        compose.onNodeWithTag("share-template-WORKOUT").assertIsDisplayed()
            .performTouchInput { click() }
        compose.waitForIdle()
        // Verify the editor state independently from the gallery accessibility state.
        try {
            compose.onNodeWithTag("share-active-template")
                .assertTextContains("WORKOUT RECAP", substring = true)
            compose.onNodeWithTag("share-template-WORKOUT").assertIsSelected()
            compose.onNodeWithTag("share-workout-provenance").assertExists()
        } catch (error: AssertionError) {
            throw AssertionError(
                "Workout gallery did not expose the active template, selected semantics and provenance: " +
                    compose.onRoot(useUnmergedTree = true).printToString(), error)
        }
        try {
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("2 recorded laps", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "Workout source/lap summary was not populated after template selection. " +
                    "Inspect the composed semantics tree: " +
                    compose.onRoot(useUnmergedTree = true).printToString(), error)
        }
        compose.onNodeWithTag("share-workout-provenance")
            .assertTextContains("Unclassified", substring = true)
            .assertTextContains("2 recorded laps", substring = true)
        compose.onNodeWithTag("share-studio-scroll").performScrollToNode(hasTestTag("share-status"))
        compose.onNodeWithTag("share-status")
            .assertTextContains("Recorded laps not classified as intervals", substring = true)
        // An actual PNG render, not just the selected gallery item, must exist.
        compose.onNodeWithTag("share-studio-scroll").performScrollToIndex(0)
        compose.onNodeWithTag("share-card-preview").assertExists()
        compose.onNodeWithTag("share-export-png").assertExists().assertIsEnabled()
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
        val source=ShareRenderableActivity.from(activity,cityDetail)
        assertTrue(source.route.pointCount >= 2)
        ShareTemplate.entries.forEach { template -> ShareRatio.entries.forEach { ratio ->
            val state=ShareStudioState(background=ShareBackground.TRANSPARENT)
                .selectTemplate(template).copy(ratio=ratio).forActivity(source)
            val bitmap=ShareCardRenderer.render(source,state)
            try {
                val bytes=ByteArrayOutputStream()
                ShareImageExport.writePng(bitmap,bytes)
                if (InstrumentationRegistry.getArguments().getString("captureSharePng") == "true") {
                    java.io.File(context.filesDir,"qa-share-alpha-${template.name}-${ratio.name}.png")
                        .writeBytes(bytes.toByteArray())
                }
                val decoded=BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size())
                assertEquals(1080,decoded.width); assertEquals(ratio.height,decoded.height)
                assertEquals(0,android.graphics.Color.alpha(decoded.getPixel(0,0)))
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
                    context.contentResolver.openInputStream(uri)!!.use {
                        val shared=BitmapFactory.decodeStream(it)
                        try { assertTrue(bitmap.sameAs(shared)) } finally { shared.recycle() }
                    }
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

    @Test fun liveActivityDarkMapExportsStreetContext() = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(
            "Requires live OpenFreeMap access",
            InstrumentationRegistry.getArguments().getString("liveShareMap") == "true"
        )
        val context = instrumentation.targetContext
        val source = ShareRenderableActivity.from(activity, cityDetail)
        assertTrue(source.route.pointCount >= 2)
        for (ratio in ShareRatio.entries) {
            val state = ShareStudioState(ratio=ratio).forActivity(source)
            val map = shareMapSnapshot(context, source, state)
            val card = ShareCardRenderer.render(source, state, map)
            try {
                val colors = (map.height / 8 until map.height * 7 / 8 step 12).flatMap { y ->
                    (map.width / 8 until map.width * 7 / 8 step 12).map { x -> map.getPixel(x,y) }
                }.toSet()
                assertTrue("Expected rendered street tiles, found ${colors.size} colors", colors.size > 20)
                val rect = ShareCardRenderer.geometry(state).route
                assertEquals(map.getPixel(20,20),card.getPixel(rect.left.toInt()+20,rect.top.toInt()+20))
                java.io.File(context.filesDir,"qa-share-map-${ratio.name}.png").outputStream().use {
                    ShareImageExport.writePng(card,it)
                }
            } finally {
                card.recycle()
                map.recycle()
            }
        }
    }

    @Test fun gallerySavePublishesExactPngAndCanBeRemoved() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=ShareRenderableActivity.from(activity,detail)
        val bitmap=ShareCardRenderer.render(source,ShareStudioState(background=ShareBackground.TRANSPARENT).forActivity(source))
        var uri:android.net.Uri?=null
        try {
            uri=ShareImageExport.saveToGallery(context,bitmap)
            context.contentResolver.openInputStream(uri)!!.use {
                val decoded=BitmapFactory.decodeStream(it)
                assertEquals(0,android.graphics.Color.alpha(decoded.getPixel(0,0)))
                assertTrue(bitmap.sameAs(decoded)); decoded.recycle()
            }
            context.contentResolver.query(uri,arrayOf(android.provider.MediaStore.Images.Media.IS_PENDING),null,null,null)!!.use {
                assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0))
            }
        } finally { uri?.let { context.contentResolver.delete(it,null,null) }; bitmap.recycle() }
    }
    @Test fun storyGuideIsOnlyEditorOverlayAndCanBeToggled() {
        mountDetail()
        compose.onNodeWithTag("activity-share").performClick()
        compose.onNodeWithTag("share-studio-scroll")
            .performScrollToNode(hasTestTag("share-section-STYLE"))
        compose.onNodeWithTag("share-section-STYLE").performClick()
        compose.onNodeWithTag("share-studio-scroll")
            .performScrollToNode(hasTestTag("share-story-guide-switch"))
        compose.onNodeWithTag("share-story-guide-switch").assertIsOff().performClick()
        compose.onNodeWithTag("share-story-guide-switch").assertIsOn()
        compose.onNodeWithTag("share-studio-scroll")
            .performScrollToNode(hasTestTag("share-card-preview"))
        compose.onNodeWithTag("share-story-safe-guides").assertExists()
        compose.onNodeWithTag("share-export-png").assertIsEnabled()
    }

}

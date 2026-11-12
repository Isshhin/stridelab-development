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

    private fun mountDetail() {
        val tab=mutableIntStateOf(0)
        compose.runOnUiThread { compose.activity.setContent {
            StrideTheme { TracksideActivityDetailSurface(activity,detail,activity,setOf(activity.id),listOf(activity),
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
        compose.onNodeWithTag("share-section-ROUTE").performClick()
        compose.onNodeWithText("Trim distance from each endpoint").assertIsDisplayed()
        compose.onNodeWithText("200 m").assertIsDisplayed()
        compose.onNodeWithText("400 m").assertIsDisplayed()
        compose.onNodeWithText("800 m").assertIsDisplayed()
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
}

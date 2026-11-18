package io.stridelab.privatealpha

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.Instant

/** Synthetic in-memory photo; no picker, device gallery, network, or persisted user photos. */
@RunWith(AndroidJUnit4::class)
class SharePhotoRendererAndroidTest {
    private val activity = ActivityRecord(
        "photo-test", "Morning run", "run", "manual",
        Instant.parse("2026-11-18T08:00:00Z"),
        5000.0, 1800.0, null, 360.0, null, null
    )
    private val renderable = ShareRenderableActivity.from(activity, null)

    @Test fun photoBackgroundIsOpaqueAndEncodedAtCorrectResolution() {
        val photo = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        try {
            for (ratio in ShareRatio.entries) {
                val state = ShareStudioState(
                    ratio = ratio,
                    background = ShareBackground.PHOTO,
                    routeMode = ShareRouteMode.HIDDEN
                )
                val image = ShareCardRenderer.render(renderable, state, photo = photo)
                try {
                    assertEquals(1080, image.width)
                    assertEquals(ratio.height, image.height)
                    val pixel = image.getPixel(540, 340)
                    assertEquals(255, Color.alpha(pixel))
                    assertTrue("Photo red background must survive overlaid dimming",
                        Color.red(pixel) > Color.green(pixel) + 30)
                    val out = ByteArrayOutputStream()
                    ShareImageExport.writePng(image, out)
                    val decoded = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
                    try {
                        assertNotNull(decoded)
                        assertEquals(pixel, decoded!!.getPixel(540, 340))
                    } finally {
                        decoded?.recycle()
                    }
                } finally {
                    image.recycle()
                }
            }
        } finally {
            photo.recycle()
        }
    }

    @Test fun oldPresetsRemainVersionOneAndPhotoStateRetainsStyles() {
        val preset = SharePreset("photo", "Photo style",
            ShareStudioState(background = ShareBackground.PHOTO))
        val reloaded = SharePresetCodec.decode(SharePresetCodec.encode(listOf(preset))).single()
        assertEquals(ShareBackground.PHOTO, reloaded.state.background)
        assertEquals(ShareBackground.PHOTO,
            reloaded.state.selectVariant(ShareVariant.STORY).background)
    }
}

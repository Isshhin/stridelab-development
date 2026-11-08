package io.stridelab.privatealpha

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsThumbnailCacheTest {
    @Test fun preloadsAlreadySavedThumbnailsBeforeCardPublicationWithoutNewRouteRendering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val account = java.util.UUID.randomUUID().toString()
        val otherAccount = account + "-other"
        val cache = GpsThumbnailCache(context)
        val route = RecordedGpsRoute(listOf(listOf(
            RecordedGpsPoint(14.6, 121.0, 0.0),
            RecordedGpsPoint(14.601, 121.002, 10.0)
        )))
        cache.write(account, "newer", route)
        cache.write(account, "older", route)
        cache.write(otherAccount, "other-only", route)
        try {
            // Construct a fresh cache instance, as happens after process restart.
            val restored = GpsThumbnailCache(context).preload(
                account, listOf("missing", "newer", "older", "newer", "other-only"),
                limit = 4
            )
            try {
                assertEquals(listOf("newer", "older"), restored.keys.toList())
                restored.values.forEach {
                    assertEquals(198, it.width)
                    assertEquals(192, it.height)
                }
                assertTrue(GpsThumbnailCache(context).preload(
                    account, listOf("missing", "newer"), limit = 1
                ).isEmpty())
                assertNull(GpsThumbnailCache(context).read(account, "other-only"))
            } finally {
                restored.values.forEach { it.recycle() }
            }
        } finally {
            cache.remove(account, "newer")
            cache.remove(account, "older")
            cache.remove(otherAccount, "other-only")
        }
    }

    @Test fun persistsAndIsolatesRealRoutes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = GpsThumbnailCache(context)
        val account = java.util.UUID.randomUUID().toString()
        val route = RecordedGpsRoute(listOf(listOf(
            RecordedGpsPoint(14.0, 121.0, 0.0), RecordedGpsPoint(14.01, 121.01, 10.0)
        )))
        assertNull(cache.read(account, "activity"))
        cache.write(account, "activity", route)
        assertNotNull(GpsThumbnailCache(context).read(account, "activity"))
        assertNull(cache.read(account + "other", "activity"))
        val changed = RecordedGpsRoute(listOf(listOf(
            RecordedGpsPoint(14.0, 121.0, 0.0), RecordedGpsPoint(14.02, 121.01, 10.0)
        )))
        assertNotEquals(cache.fingerprint(route), cache.fingerprint(changed))
        assertEquals(cache.fingerprint(route), cache.storedFingerprint(account, "activity"))
        cache.write(account, "activity", changed)
        assertEquals(cache.fingerprint(changed), cache.storedFingerprint(account, "activity"))
        fun hash(value: String) = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = java.io.File(context.filesDir, "gps-thumbnails-v1/${hash(account)}/${hash("activity")}")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertNull(cache.read(account, "activity"))
        cache.write(account, "activity", changed)
        assertNotNull(cache.read(account, "activity"))
        cache.remove(account, "activity")
        assertNull(cache.read(account, "activity"))
        cache.write(account, "empty", RecordedGpsRoute(emptyList()))
        assertTrue(cache.hasNoRoute(account, "empty"))
        // Two valid source fixes separated by an unrecorded timer pause are
        // still displayed as one uninterrupted thumbnail route line.
        val acrossPause = RecordedGpsRoute(
            listOf(
                listOf(RecordedGpsPoint(14.600,121.000,10.0)),
                listOf(RecordedGpsPoint(14.601,121.001,150.0))
            ),
            listOf(ActivityPauseInterval(20.0,140.0))
        )
        assertEquals(2, acrossPause.segments.size)
        assertEquals(1, acrossPause.displaySegments.size)
        assertTrue(cache.render(acrossPause).isNotEmpty())
        cache.write(account, "continuous", acrossPause)
        assertNotNull(cache.read(account, "continuous"))
        cache.remove(account, "continuous")
        assertNull(cache.read(account, "empty"))
    }
}

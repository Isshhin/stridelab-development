package io.stridelab.privatealpha

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsThumbnailCacheTest {
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

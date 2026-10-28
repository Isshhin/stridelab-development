package io.stridelab.privatealpha

import androidx.test.platform.app.InstrumentationRegistry
import com.garmin.fit.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class FitImportCacheTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture():ByteArray {
        val encoder=BufferEncoder(Fit.ProtocolVersion.V2_0)
        encoder.write(FileIdMesg().apply { type=com.garmin.fit.File.ACTIVITY })
        encoder.write(SessionMesg().apply { startTime=DateTime(1_000_000_000L);sport=Sport.RUNNING;totalDistance=1000f })
        return encoder.close()
    }
    @Test fun reopensOriginalOfflineAndDeduplicatesWithoutOutbox() {
        val owner=UUID.randomUUID().toString();val cache=FitImportCache(context);val bytes=fixture()
        val a=cache.saveSelected(bytes,owner);val hash=a.identity.sha256!!
        assertEquals(a,cache.saveSelected(bytes,owner))
        assertEquals(a,FitImportCache(context).load(owner,hash))
        assertThrows(java.io.FileNotFoundException::class.java) { cache.load("other-$owner",hash) }
    }
    @Test fun interruptedWriteRestoresLastGoodOriginalAndCorruptionIsRetained() {
        val owner=UUID.randomUUID().toString();val cache=FitImportCache(context)
        val a=cache.saveSelected(fixture(),owner);val hash=a.identity.sha256!!
        val ownerHash=java.security.MessageDigest.getInstance("SHA-256").digest(owner.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val original=File(context.filesDir,"fit-import-cache-v1/$ownerHash/$hash.fit")
        val backup=File(original.path+".bak")
        assertTrue(original.renameTo(backup));original.writeBytes(byteArrayOf(1,2,3))
        assertEquals(a,cache.load(owner,hash));assertFalse(backup.exists())
        original.writeBytes(byteArrayOf(1,2,3))
        assertThrows(IllegalArgumentException::class.java) { cache.load(owner,hash) }
        assertArrayEquals(byteArrayOf(1,2,3),original.readBytes())
    }
}

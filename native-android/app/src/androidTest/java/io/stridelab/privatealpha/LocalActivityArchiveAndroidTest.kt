package io.stridelab.privatealpha

import android.content.ContentValues
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.garmin.fit.BufferEncoder
import com.garmin.fit.DateTime
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Synthetic isolated source/target databases. Never touches an owner's activity
 * database, connected Garmin service, physical device, or cloud backup.
 */
@RunWith(AndroidJUnit4::class)
class LocalActivityArchiveAndroidTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val nameA="activity-archive-a-${UUID.randomUUID()}.db"
    private val nameB="activity-archive-b-${UUID.randomUUID()}.db"
    private val source="local-garmin-${UUID.randomUUID()}"
    private val target="local-garmin-${UUID.randomUUID()}"
    private val files=mutableListOf<File>()

    private fun sourceRow(owner:String, id:String="garmin-fixture"):String = JSONObject()
        .put("user_id",owner).put("id",id).put("title","Synthetic run")
        .put("source","garmin").put("activity_type","running")
        .put("started_at",Instant.parse("2026-11-15T08:00:00Z").toString())
        .put("distance_m",10000.0).put("duration_s",3600.0)
        .toString()

    private fun detailRow(owner:String, id:String="garmin-fixture", padding:String=""):String =
        JSONObject().put("user_id",owner).put("activity_id",id).put("fetched_at",1L)
            .put("samples",JSONArray()).put("laps",JSONArray())
            .put("source_fixture_padding",padding).toString()

    private fun seed(owner:String, large:Boolean=false) {
        TrainingStore(context,nameA).use { db ->
            db.applyRemotePage(owner,"activity",
                listOf(TrainingStore.RemoteRecord("garmin-fixture",sourceRow(owner),null)),
                null,1L)
            if(!large) db.applyRemotePage(owner,"activity_detail",
                listOf(TrainingStore.RemoteRecord("garmin-fixture",detailRow(owner),null)),
                null,1L)
            else db.writableDatabase.insertOrThrow("records",null,ContentValues().apply {
                put("account",owner);put("kind","activity_detail")
                put("record_id","garmin-fixture")
                put("payload",detailRow(owner,"garmin-fixture","x".repeat(3 * 1024 * 1024)))
                putNull("server_version");put("synced_at",0L)
            })
        }
    }

    private fun temp()=File.createTempFile("activity-test-", ".zip", context.cacheDir).also {
        files += it
    }
    private fun ownerDir(owner:String):File {
        val hash=MessageDigest.getInstance("SHA-256").digest(owner.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir,"fit-import-cache-v1/$hash")
    }
    @After fun cleanup() {
        files.forEach { it.delete() }
        context.deleteDatabase(nameA)
        context.deleteDatabase(nameB)
        ownerDir(source).deleteRecursively()
        ownerDir(target).deleteRecursively()
    }

    @Test fun archiveRoundTripRemapsOnlyLocalGarminOwnerAndIsIdempotent() {
        seed(source)
        val archive=temp()
        LocalActivityArchive(context,nameA).let { service ->
            try {
                archive.outputStream().use {
                    val info=service.export(source,LocalActivityArchiveScope.LOCAL_GARMIN,it)
                    assertEquals(1,info.summaries)
                    assertEquals(1,info.details)
                    assertEquals(0,info.originals)
                }
            } finally { service.close() }
        }
        LocalActivityArchive(context,nameB).let { service ->
            try {
                val result=service.restore(archive,target,LocalActivityArchiveScope.LOCAL_GARMIN)
                assertEquals(2,result.newlyRestored)
                assertEquals(0,service.restore(
                    archive,target,LocalActivityArchiveScope.LOCAL_GARMIN).newlyRestored)
            } finally { service.close() }
        }
        TrainingStore(context,nameB).use { db ->
            val summary=checkNotNull(db.get(target,"activity","garmin-fixture")).payload
            assertEquals("garmin",ActivityRecord.parse(summary,target).source)
            val detail=db.getActivityDetailPayloadChunked(target,"garmin-fixture")
            assertEquals("garmin-fixture",
                ActivityDetail.parse(JSONObject(detail!!),target,"garmin-fixture").activityId)
            assertNull(db.get(source,"activity","garmin-fixture"))
        }
    }

    @Test fun conflictIsRejectedBeforeAnyNewRecordCanBeInserted() {
        seed(source)
        val archive=temp()
        LocalActivityArchive(context,nameA).useArchive { archive.outputStream().use {
            export(source,LocalActivityArchiveScope.LOCAL_GARMIN,it)
        } }
        TrainingStore(context,nameB).use { db ->
            db.applyRemotePage(target,"activity",
                listOf(TrainingStore.RemoteRecord("garmin-fixture",
                    JSONObject(sourceRow(target)).put("title","Different original").toString(),null)),
                null,1L)
        }
        LocalActivityArchive(context,nameB).useArchive {
            assertThrows(IllegalArgumentException::class.java) {
                restore(archive,target,LocalActivityArchiveScope.LOCAL_GARMIN)
            }
        }
        TrainingStore(context,nameB).use { db ->
            assertTrue(db.get(target,"activity","garmin-fixture")!!.payload.contains("Different original"))
            assertNull(db.getActivityDetailPayloadChunked(target,"garmin-fixture"))
        }
    }

    @Test fun tamperedEntryFailsChecksumWithoutAnyDatabaseWrite() {
        seed(source)
        val valid=temp()
        LocalActivityArchive(context,nameA).useArchive {
            valid.outputStream().use { export(source,LocalActivityArchiveScope.LOCAL_GARMIN,it) }
        }
        val corrupt=temp()
        ZipFile(valid).use { zip ->
            ZipOutputStream(corrupt.outputStream()).use { output ->
                val names=zip.entries()
                while(names.hasMoreElements()) {
                    val entry=names.nextElement()
                    val bytes=zip.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(if(entry.name.startsWith("records/activity/"))
                        bytes + " ".toByteArray() else bytes)
                    output.closeEntry()
                }
            }
        }
        LocalActivityArchive(context,nameB).useArchive {
            assertThrows(IllegalArgumentException::class.java) {
                restore(corrupt,target,LocalActivityArchiveScope.LOCAL_GARMIN)
            }
        }
        TrainingStore(context,nameB).use {
            assertNull(it.get(target,"activity","garmin-fixture"))
        }
    }

    @Test fun largeSavedDetailIsCopiedWithoutCursorWindowTruncation() {
        seed(source,large=true)
        val archive=temp()
        LocalActivityArchive(context,nameA).useArchive {
            archive.outputStream().use { export(source,LocalActivityArchiveScope.LOCAL_GARMIN,it) }
        }
        LocalActivityArchive(context,nameB).useArchive {
            assertEquals(2,restore(archive,target,LocalActivityArchiveScope.LOCAL_GARMIN).newlyRestored)
        }
        TrainingStore(context,nameB).use { db ->
            val raw=checkNotNull(db.getActivityDetailPayloadChunked(target,"garmin-fixture"))
            assertTrue(raw.length > 3 * 1024 * 1024)
        }
    }

    @Test fun largeGarminCacheWriteAndRefreshBypassSharedRecordLimitWithoutOutbox() {
        val id = "garmin-fixture"
        val payload = detailRow(source, id, padding = "x".repeat(3 * 1024 * 1024))
        TrainingStore(context, nameA).use { db ->
            assertNull(db.getActivityDetailPayloadChunked(source, id))
            db.saveLocalGarminRecording(source, id, detailRow(source, id), 1L)
            db.saveLocalGarminRecording(source, id, payload, 2L)
            assertEquals(payload, db.getActivityDetailPayloadChunked(source, id))
            val refreshed = JSONObject(payload).put("fetched_at", 3L).toString()
            db.saveLocalGarminRecording(source, id, refreshed, 3L)
            assertEquals(refreshed, db.getActivityDetailPayloadChunked(source, id))
            db.readableDatabase.rawQuery("SELECT count(*) FROM outbox", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun originalFitBytesAreIncludedOnlyWhenPreviouslyCached() {
        seed(source)
        val encoder=BufferEncoder(Fit.ProtocolVersion.V2_0)
        encoder.write(FileIdMesg().apply { type=com.garmin.fit.File.ACTIVITY })
        encoder.write(SessionMesg().apply {
            startTime=DateTime(1_000_000_000L);sport=Sport.RUNNING;totalDistance=1000f
        })
        val fitBytes=encoder.close()
        val hash=FitImportCache(context).saveSelected(fitBytes,source).identity.sha256!!
        val archive=temp()
        LocalActivityArchive(context,nameA).useArchive {
            archive.outputStream().use {
                assertEquals(1,export(source,LocalActivityArchiveScope.LOCAL_GARMIN,it).originals)
            }
        }
        LocalActivityArchive(context,nameB).useArchive {
            val result=restore(archive,target,LocalActivityArchiveScope.LOCAL_GARMIN)
            assertEquals(1,result.originals)
        }
        assertArrayEquals(fitBytes,FitImportCache(context).originalBytes(target,hash))
    }

    @Test fun signedInArchiveNeverRestoresIntoAnotherCloudOwner() {
        val owner="cloud-test-${UUID.randomUUID()}"
        TrainingStore(context,nameA).use { db ->
            val cloud=JSONObject(sourceRow(owner)).put("source","fit").toString()
            db.applyRemotePage(owner,"activity",
                listOf(TrainingStore.RemoteRecord("garmin-fixture",cloud,null)),null,1L)
        }
        val file=temp()
        LocalActivityArchive(context,nameA).useArchive {
            file.outputStream().use { export(owner,LocalActivityArchiveScope.CLOUD_ACCOUNT,it) }
        }
        LocalActivityArchive(context,nameB).useArchive {
            assertThrows(IllegalArgumentException::class.java) {
                restore(file,"other-$owner",LocalActivityArchiveScope.CLOUD_ACCOUNT)
            }
        }
        TrainingStore(context,nameB).use {
            assertNull(it.get("other-$owner","activity","garmin-fixture"))
        }
    }

    private inline fun <T> LocalActivityArchive.useArchive(block:LocalActivityArchive.()->T):T =
        try { block() } finally { close() }
}

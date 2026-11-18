package io.stridelab.privatealpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Uses a disposable test database; never touches StrideLab's real activity store. */
@RunWith(AndroidJUnit4::class)
class ManualDisciplineStoreAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "manual-test-${UUID.randomUUID()}.db"
    private lateinit var store: ManualDisciplineStore
    private val owner = "test-manual-owner"
    private val first = ManualDisciplineSession(
        "first", owner, ManualDiscipline.CALISTHENICS, "Calisthenics",
        LocalDate.parse("2026-11-18"),
        listOf(ManualExerciseEntry("Pull-up", 3, reps=5))
    )

    @Before fun setup() { store = ManualDisciplineStore(context, dbName) }
    @After fun cleanup() { store.close(); context.deleteDatabase(dbName) }

    @Test fun saveListRestoreAndOwnerIsolation() = runBlocking {
        store.save(first)
        assertEquals(listOf(first), store.list(owner))
        assertTrue(store.list("other-owner").isEmpty())

        val second = first.copy(id = "second", discipline = ManualDiscipline.STRENGTH)
        val backup = ManualDisciplineBackup.export(owner, listOf(first,second))
        assertEquals(1, store.restore(owner, backup))
        assertEquals(0, store.restore(owner, backup))
        assertEquals(2, store.list(owner).size)
        assertTrue(store.list("other-owner").isEmpty())
    }

    @Test fun conflictingDuplicateAbortsRestoreTransaction() = runBlocking {
        store.save(first)
        val new = first.copy(id = "new")
        val changed = first.copy(title = "Conflicting overwrite")
        val bad = ManualDisciplineBackup.export(owner, listOf(new, changed))
        try {
            store.restore(owner, bad)
            fail("Conflicting record must reject the entire restore")
        } catch(_: IllegalArgumentException) { }
        assertEquals(listOf(first), store.list(owner))
    }

    @Test fun malformedChecksumLeavesOriginalRowsUnchanged() = runBlocking {
        store.save(first)
        val damaged = org.json.JSONObject(ManualDisciplineBackup.export(owner, listOf(first)))
            .put("sha256", "wrong").toString()
        try {
            store.restore(owner, damaged)
            fail("Bad checksum must be rejected")
        } catch(_: IllegalArgumentException) { }
        assertEquals(listOf(first), store.list(owner))
    }

    @Test fun deletionIsLimitedToTheSpecifiedOwner() = runBlocking {
        store.save(first)
        assertFalse(store.delete("other-owner", first.id))
        assertEquals(listOf(first), store.list(owner))
        assertTrue(store.delete(owner, first.id))
        assertTrue(store.list(owner).isEmpty())
    }
    @Test fun workoutTemplatesPersistAndAreRecoveredByCombinedArchive() = runBlocking {
        val form = ManualWorkoutTemplate(
            "template-a", owner, "My strength", ManualDiscipline.STRENGTH,
            listOf(ManualExerciseEntry("Squat",3,reps=12,weightKg=15.0))
        )
        store.saveTemplate(form)
        assertEquals(listOf(form), store.listTemplates(owner))
        val archive = store.export(owner)
        assertEquals(0, store.restore(owner, archive))
        assertEquals(1, store.listTemplates(owner).size)
        assertTrue(store.listTemplates("other-owner").isEmpty())
        assertTrue(store.deleteTemplate(owner, form.id))
        assertEquals(1, store.restore(owner, archive))
        assertEquals(listOf(form), store.listTemplates(owner))
    }

    @Test fun versionOneManualDatabaseMigratesWithoutDeletingSessionRows() = runBlocking {
        // @Before constructed the helper but has not opened its database yet.
        context.openOrCreateDatabase(dbName, android.content.Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE sessions(owner TEXT NOT NULL, record_id TEXT NOT NULL, performed_on TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(owner,record_id))")
            db.setVersion(1)
        }
        assertTrue(store.listTemplates(owner).isEmpty())
        store.save(first)
        assertEquals(listOf(first), store.list(owner))
    }

    @Test fun tooManySavedSessionsCannotProduceSilentlyTruncatedArchive() = runBlocking {
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            repeat(2001) { index ->
                val record = first.copy(id = "bulk-$index")
                val values = android.content.ContentValues().apply {
                    put("owner",owner)
                    put("record_id",record.id)
                    put("performed_on",record.performedOn.toString())
                    put("payload",ManualDisciplineCodec.encode(record))
                }
                db.insertOrThrow("sessions",null,values)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        try {
            store.export(owner)
            fail("Export must reject incomplete manual history")
        } catch(_: IllegalArgumentException) { }
        db.rawQuery("SELECT count(*) FROM sessions WHERE owner=?",
            arrayOf(owner)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2001,cursor.getInt(0))
        }
    }

    @Test fun tooManySavedTemplatesCannotProduceSilentlyTruncatedArchive() = runBlocking {
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            repeat(101) { index ->
                val form = ManualWorkoutTemplate("form-$index",owner,"Form $index",
                    ManualDiscipline.STRENGTH,
                    listOf(ManualExerciseEntry("Squat",2,reps=10)))
                val values = android.content.ContentValues().apply {
                    put("owner",owner)
                    put("record_id",form.id)
                    put("name",form.name)
                    put("payload",ManualWorkoutTemplateCodec.encode(form))
                }
                db.insertOrThrow("manual_templates",null,values)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        try {
            store.export(owner)
            fail("Export must reject truncated template history")
        } catch(_: IllegalArgumentException) { }
        db.rawQuery("SELECT count(*) FROM manual_templates WHERE owner=?",
            arrayOf(owner)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(101,cursor.getInt(0))
        }
    }

}

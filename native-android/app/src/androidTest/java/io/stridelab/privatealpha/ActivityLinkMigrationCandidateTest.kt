package io.stridelab.privatealpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Executes only on disposable test DB files; never opens the owner's default DB. */
@RunWith(AndroidJUnit4::class)
class ActivityLinkMigrationCandidateTest {
    private fun plan(account:String,id:String,linkedIds:List<String?>,unsupported:Boolean=false):String {
        val sessions=JSONArray()
        linkedIds.forEachIndexed { index, activity ->
            sessions.put(JSONObject()
                .put("id","s-$index")
                .put("discipline","run")
                .put("status","planned")
                .put("linkedActivityId",activity))
        }
        return JSONObject()
            .put("schemaVersion",if(unsupported)2 else 1)
            .put("user_id",account)
            .put("id",id)
            .put("sessions",sessions).toString()
    }

    private fun withDatabase(action:(TrainingStore)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="stridelab-link-fixture-"+System.nanoTime()+".db"
        context.deleteDatabase(name)
        try {
            TrainingStore(context,name).use { store -> action(store) }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun v1RecordsAreUnchangedAndBackfilledOnAnIsolatedCopy() = withDatabase { store ->
        val a=plan("account-a","plan-a",listOf("run-1","run-2"))
        val b=plan("account-b","plan-b",listOf("run-1"))
        store.enqueue("account-a","plan","plan-a","op-a",a,null,1)
        store.enqueue("account-b","plan","plan-b","op-b",b,null,1)
        val audit=ActivityLinkMigrationCandidate.migrateIsolatedCopy(store.writableDatabase)
        assertEquals(2,audit.plans)
        assertEquals(3,audit.links)
        assertEquals(a,store.get("account-a","plan","plan-a")!!.payload)
        assertEquals(b,store.get("account-b","plan","plan-b")!!.payload)
        assertEquals(1,store.readableDatabase.version)
        store.readableDatabase.rawQuery(
            "SELECT count(*) FROM activity_links WHERE activity_id='run-1'",null
        ).use { c -> assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0)) }
        store.readableDatabase.rawQuery(
            "SELECT count(*) FROM outbox",null
        ).use { c -> assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0)) }
    }

    @Test fun duplicateLinksAcrossPlansRollbackTheEntireIndex() = withDatabase { store ->
        val a=plan("account-a","plan-a",listOf("same-run"))
        val b=plan("account-a","plan-b",listOf("same-run"))
        store.enqueue("account-a","plan","plan-a","op-a",a,null,1)
        store.enqueue("account-a","plan","plan-b","op-b",b,null,1)
        assertThrows(Exception::class.java) {
            ActivityLinkMigrationCandidate.migrateIsolatedCopy(store.writableDatabase)
        }
        store.readableDatabase.rawQuery(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='activity_links'",null
        ).use { c -> assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0)) }
        assertEquals(a,store.get("account-a","plan","plan-a")!!.payload)
        assertEquals(b,store.get("account-a","plan","plan-b")!!.payload)
    }

    @Test fun unknownPlanSchemaMustNotProducePartialIndex() = withDatabase { store ->
        val a=plan("account-a","plan-a",listOf("a"))
        val unsupported=plan("account-a","plan-b",listOf("b"),unsupported=true)
        store.enqueue("account-a","plan","plan-a","op-a",a,null,1)
        store.enqueue("account-a","plan","plan-b","op-b",unsupported,null,1)
        assertThrows(IllegalStateException::class.java) {
            ActivityLinkMigrationCandidate.migrateIsolatedCopy(store.writableDatabase)
        }
        assertEquals(unsupported,store.get("account-a","plan","plan-b")!!.payload)
        store.readableDatabase.rawQuery(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='activity_links'",null
        ).use { c -> assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0)) }
    }
}

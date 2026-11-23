package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FreeformStorageAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "freeform-store-${UUID.randomUUID()}"
    private val database = "$name.db"
    private val owner = "$name-owner"
    private val other = "$name-other"
    private val date = LocalDate.now()
    private val databases = listOf(database, "$database-manual", "$name-restore.db")

    @After fun cleanup() {
        databases.forEach { context.deleteDatabase(it) }
        context.deleteSharedPreferences(name)
        SessionVault(context).clear(owner)
        SessionVault(context).clear(other)
    }

    @Test fun concurrentCompletionAndRepeatedRestoreKeepStableIdentityAndActuals() = runBlocking {
        ManualDisciplineStore(context, database).use { store ->
            val draft = store.createFreeformDraft(FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, date, 100))
            val ready = strength(draft)
            store.saveFreeformDraft(ready)
            val outcomes = listOf(
                async { store.completeFreeform(ready, 300) },
                async { store.completeFreeform(ready, 301) }
            ).awaitAll()
            assertEquals(outcomes.first(), outcomes.last())
            val completed = store.listFreeform(owner).single()
            assertEquals(ready.id, completed.id)
            assertEquals("1 set · 7 reps · 280 kg volume", completed.summary())
            assertEquals("10", completed.exercises.single().sets.single().target.quantity)
            assertEquals("40", completed.exercises.single().sets.single().actual.kg)
            assertFalse(store.discardFreeform(owner, ready.id))
            assertTrue(store.listFreeform(other).isEmpty())

            val unfinished = store.createFreeformDraft(FreeformWorkout.start(owner, ManualDiscipline.RUNNING, date, 400))
                .copy(distanceKm = "4.", modifiedAt = 401)
            store.saveFreeformDraft(unfinished)
            val archive = store.export(owner)
            val restored = ManualDisciplineStore(context, "$name-restore.db")
            restored.use {
                assertEquals(2, it.restore(owner, archive))
                assertEquals(0, it.restore(owner, archive))
                assertEquals(store.listFreeform(owner), it.listFreeform(owner))
                assertTrue(runCatching { it.restore(other, archive) }.isFailure)
            }
        }
    }

    @Test fun versionTwoRowsAndTemplatesSurviveUpgradeAndDraftDiscardCannotBeUndoneByLateSave() = runBlocking {
        val legacy = ManualDisciplineSession("old-row", owner, ManualDiscipline.CALISTHENICS,
            "Pull day", date, listOf(ManualExerciseEntry("Pull-up", 3, reps = 8)))
        val template = ManualWorkoutTemplate("old-template", owner, "Pull routine", legacy.discipline, legacy.exercises)
        context.openOrCreateDatabase(database, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE sessions(owner TEXT NOT NULL, record_id TEXT NOT NULL, performed_on TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(owner,record_id))")
            db.execSQL("CREATE INDEX session_owner_date ON sessions(owner,performed_on DESC)")
            db.execSQL("CREATE TABLE manual_templates(owner TEXT NOT NULL, record_id TEXT NOT NULL, name TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(owner,record_id))")
            db.execSQL("INSERT INTO sessions VALUES (?,?,?,?)",
                arrayOf(owner, legacy.id, date.toString(), ManualDisciplineCodec.encode(legacy)))
            db.execSQL("INSERT INTO manual_templates VALUES (?,?,?,?)",
                arrayOf(owner, template.id, template.name, ManualWorkoutTemplateCodec.encode(template)))
            db.version = 2
        }
        ManualDisciplineStore(context, database).use { store ->
            assertEquals(listOf(legacy), store.list(owner))
            assertEquals(listOf(template), store.listTemplates(owner))
            assertEquals(3, store.readableDatabase.version)
            val draft = store.createFreeformDraft(FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, date, 100))
            assertTrue(store.discardFreeform(owner, draft.id))
            assertTrue(runCatching { store.saveFreeformDraft(strength(draft)) }.isFailure)
            assertTrue(store.listFreeform(owner).isEmpty())
        }
    }

    @Test fun realRepositorySwitchesOwnersAndFreeformLeavesStoredEngineBytesUnchanged() = runBlocking {
        val gateway = FixtureGateway(owner)
        val repo = TrainingRepository(context, gateway, database, name)
        try {
            val initialized = withTimeout(20_000) { repo.state.first { !it.initializing } }
            val guest = requireNotNull(initialized.localPlanOwner)
            assertNull(initialized.account)
            val access = repo.freeformAccess()
            val guestDraft = access.start(FreeformWorkout.start(guest, ManualDiscipline.RUNNING, date, 100))
            access.finish(guestDraft.copy(distanceKm = "4.25", durationMinutes = "25", modifiedAt = 101))

            val request = PlanRequest(goal = "Strength for Runners", start = date, weeks = 1,
                days = setOf(date.dayOfWeek.value), runDays = 0, strengthDays = 1, timeBudgetEnabled = false)
            assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
            val generated = repo.generatePlan(request)
            assertTrue(repo.state.value.message, generated)
            val preview = requireNotNull(repo.state.value.planPreview)
            assertTrue(repo.approvePlan(preview.id, preview.revision))
            val plan = repo.state.value.plans.single()
            assertEquals("approved", plan.status)
            assertTrue(access.read(owner).workouts.isEmpty())
            assertTrue(runCatching { access.read(guest) }.isFailure)
            assertTrue(runCatching { access.save(guestDraft) }.isFailure)
            val before = protectedRows()
            val logged = access.start(FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, date, 200))
            assertTrue(access.finish(strength(logged)).isComplete)
            val backup = access.export(owner)
            assertEquals(0, access.restore(owner, backup))
            assertEquals(before, protectedRows())
            assertEquals(listOf(plan.id), repo.state.value.plans.map { it.id })
            assertTrue(repo.state.value.activities.isEmpty())

            gateway.account = other
            assertTrue(repo.signIn("other@example.invalid", "fixture"))
            assertTrue(access.read(other).workouts.isEmpty())
            assertTrue(runCatching { access.read(owner) }.isFailure)
            assertTrue(runCatching { access.restore(other, backup) }.isFailure)
            gateway.account = owner
            assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
            assertEquals(logged.id, access.read(owner).workouts.single().id)
            assertTrue(repo.signOut())
            assertEquals(guestDraft.id, access.read(guest).workouts.single().id)
            assertEquals(before, protectedRows())
        } finally {
            repo.close()
        }
    }

    private fun protectedRows(): List<String> = TrainingStore(context, database).use { store ->
        val db = store.readableDatabase
        val plans = db.rawQuery("SELECT payload FROM records WHERE kind='plan' ORDER BY account,record_id", null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        val outboxCount = db.rawQuery("SELECT COUNT(*) FROM outbox", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        plans + outboxCount.toString()
    }

    private fun strength(draft: FreeformWorkout) = draft.copy(
        modifiedAt = draft.modifiedAt + 1, exercises = listOf(FreeformExercise(
            name = "Squat", sets = listOf(FreeformSet(
                target = FreeformMeasure(quantity = "10", kg = "45"),
                actual = FreeformMeasure(quantity = "7", kg = "40"),
                restSeconds = "80", rpe = "8", rir = "2", completed = true
            ))
        ))
    )

    private class FixtureGateway(var account: String) : TrainingGateway {
        override fun signIn(email: String, password: String) =
            NativeSession(account, "synthetic-token", "synthetic-refresh", Long.MAX_VALUE)
        override fun verify(session: NativeSession) = Unit
        override fun refresh(session: NativeSession) = session
        override fun activities(session: NativeSession, before: ActivityRecord?) = JSONArray()
        override fun wellness(session: NativeSession) = JSONArray()
        override fun detail(session: NativeSession, id: String) = JSONObject()
        override fun signOut(session: NativeSession) = Unit
    }
}

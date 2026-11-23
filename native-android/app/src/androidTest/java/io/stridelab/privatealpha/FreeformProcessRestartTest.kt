package io.stridelab.privatealpha

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class FreeformProcessRestartTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture: String get() = "freeform-restart-" + requireNotNull(
        InstrumentationRegistry.getArguments().getString("freeform_fixture")
    ).also { require(it.matches(Regex("[a-z0-9]{1,30}"))) }
    private val evidence get() = context.getSharedPreferences("$fixture-evidence", 0)

    @Before fun requireRestartHarness() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("freeform_fixture") != null)
    }

    @Test fun prepare() = runBlocking {
        check(!evidence.contains("pid"))
        val repo = open()
        try {
            val owner = requireNotNull(repo.state.value.localPlanOwner)
            val access = repo.freeformAccess()
            val empty = access.start(FreeformWorkout.start(owner, ManualDiscipline.RUNNING, LocalDate.now(), 100))
            val partial = access.start(FreeformWorkout.start(owner, ManualDiscipline.ISOMETRICS, LocalDate.now(), 100))
                .copy(modifiedAt = 101, performedOn = "2026-11-", exercises = listOf(
                    FreeformExercise(name = "Wall sit", sets = listOf(FreeformSet(actual = FreeformMeasure(seconds = "4."))))
                ))
            access.save(partial)
            val first = LocalDate.of(2026, 11, 23)
            val series = CustomSchedule.create(ManualWorkoutTemplate.empty(owner, ManualDiscipline.RUNNING),
                ScheduleRule(first, weekly = true), 103)
            access.saveSchedule(series)
            access.changeSchedule(owner, CustomOccurrenceKey(series.id, first),
                CustomScheduleEdit.One(first.plusDays(1), series.modifiedAt))
            check(evidence.edit().putInt("pid", Process.myPid()).putString("owner", owner)
                .putString("emptyId", empty.id).putString("partialId", partial.id).commit())
        } finally {
            repo.close()
        }
    }

    @Test fun recoverAndComplete() = runBlocking {
        val priorPid = evidence.getInt("pid", -1)
        assertTrue(priorPid > 0)
        assertNotEquals(priorPid, Process.myPid())
        val repo = open()
        val database = "$fixture.db"
        try {
            val owner = requireNotNull(repo.state.value.localPlanOwner)
            assertEquals(evidence.getString("owner", null), owner)
            val access = repo.freeformAccess()
            val board = access.read(owner)
            val drafts = board.workouts
            val first = LocalDate.of(2026, 11, 23)
            val series = board.schedules.single()
            val moved = board.customBetween(first, first.plusDays(8)).first()
            assertEquals(first.plusDays(1), moved.date)
            assertEquals(CustomOccurrenceKey(series.id, first).workoutId, moved.key.workoutId)
            assertEquals(2, drafts.size)
            val empty = drafts.single { it.id == evidence.getString("emptyId", null) }
            assertTrue(empty.distanceKm.isEmpty() && !empty.isComplete)
            val partial = drafts.single { it.id == evidence.getString("partialId", null) }
            assertEquals("2026-11-", partial.performedOn)
            val exercise = partial.exercises.single()
            val set = exercise.sets.single()
            assertEquals("4.", set.actual.seconds)
            val ready = partial.copy(modifiedAt = 102, performedOn = LocalDate.now().toString(), exercises = listOf(
                exercise.copy(sets = listOf(set.copy(actual = FreeformMeasure(seconds = "40"), completed = true)))
            ))
            val completion = access.finish(ready)
            assertEquals(completion, access.finish(ready))
            assertEquals("1 set · 40 s", completion.summary())
            assertEquals(1, access.read(owner).workouts.count { it.isComplete })
        } finally {
            repo.close()
            context.deleteDatabase(database)
            context.deleteDatabase("$database-manual")
            context.deleteSharedPreferences(fixture)
            context.deleteSharedPreferences("$fixture-evidence")
        }
    }

    private suspend fun open(): TrainingRepository = TrainingRepository(
        context, databaseName = "$fixture.db", preferencesName = fixture
    ).also { repo -> withTimeout(20_000) { repo.state.first { !it.initializing } } }
}

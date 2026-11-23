package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BlueprintWorkoutTest {
    private val date = LocalDate.of(2026, 11, 23)
    private val owner = "runner"
    private val session = PlannedSession("same-session", date, "str", "Draft workout", 20, null, "strength")
    private val draft = PlanSnapshot(
        "exact-draft", owner, ENGINE1_VERSION, ENGINE1_POLICY, Instant.EPOCH,
        PlanRequest(runDays = 0, strengthDays = 2), listOf(session), emptyList()
    )
    private val saved = draft.copy(status = "approved", sessions = listOf(session.copy(title = "Saved workout")))

    @Test fun resolvesOnlyTheExactLiveDraft() {
        val state = TrainingState(account = owner, planPreview = draft, plans = listOf(saved))
        fun resolve(value: TrainingState, id: String = draft.id) =
            resolveBlueprintWorkout(value, id, session.id, draft.createdAt.toString(), 0)
        assertEquals(draft to session, resolve(state))
        assertNull(resolve(state, "another-draft"))
        assertNull(resolve(state.copy(planPreview = null)))
        assertNull(resolve(state.copy(planPreview = saved)))
        assertNull(resolve(state.copy(account = "other-owner")))
        assertNull(resolve(state.copy(loginGeneration = 1)))
        assertNull(resolve(state.copy(planPreview = draft.copy(createdAt = Instant.EPOCH.plusSeconds(1)))))
        assertNull(resolve(state.copy(planPreview = draft.copy(sessions = emptyList()))))
    }

    @Test fun precedenceUsesExplicitEmptyProfileThenLatestCompatibleOwnerPlanThenDefaults() {
        val older = saved.copy(request = saved.request.copy(equipment = mapOf("bb" to 1)))
        val newest = older.copy(createdAt = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            request = older.request.copy(equipment = mapOf("db" to 1)))
        val otherOwner = newest.copy(owner = "someone-else", createdAt = newest.createdAt.plusSeconds(1))
        val running = newest.copy(createdAt = newest.createdAt.plusSeconds(2),
            request = PlanRequest(runDays = 3, strengthDays = 0, equipment = mapOf("bands" to 1)))
        val state = TrainingState(account = owner, plans = listOf(otherOwner, running, older, newest))
        assertEquals(emptyMap<String, Int>(), builderEquipmentFor(state.copy(savedEquipment = emptyMap()), draft.request))
        assertEquals(mapOf("db" to 1), builderEquipmentFor(state, draft.request))
        val defaults = draft.request.copy(equipment = mapOf("bench" to 1))
        assertEquals(defaults.equipment, builderEquipmentFor(state.copy(account = null, localPlanOwner = "local"), defaults))
    }
}

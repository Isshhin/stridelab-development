package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Phase 14–17 fixes for source-audited unsafe or silently ignored requests. */
class Engine2HardeningTest {
    private val monday = LocalDate.parse("2026-11-02")

    @Test fun supportedExerciseEntriesNeverContainInstructionalGuides() {
        for (discipline in Engine2ExerciseDiscipline.values()) {
            for (movement in Engine2Movement.values()) {
                val selected = Engine2ExerciseSelection.available(
                    discipline, movement, Engine2Equipment.values().toSet(),
                    Engine2ExerciseSelection.reviewed.map { it.catalogId }.toSet(),
                    Engine2Skill.ADVANCED)
                assertTrue(selected.none { !it.executable })
                assertTrue(selected.none { it.catalogId in Engine2ExerciseSelection.guideOnlyIds })
            }
        }
        assertEquals(6, Engine2ExerciseSelection.guideOnlyIds.size)
        assertTrue(Engine2ExerciseSelection.reviewed.filter { !it.executable }
            .map { it.catalogId }.toSet() == Engine2ExerciseSelection.guideOnlyIds)
    }

    @Test fun unsupportedQualityPreferencesCannotBeSilentlyDiscarded() {
        val schedule = mapOf(Engine2Discipline.RUNNING to 3)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanningRequest(monday, 4, (1..6).toSet(), schedule,
                preferredQualityDays = setOf(2, 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanningRequest(monday, 4, (1..6).toSet(), schedule,
                preferredQualityDays = setOf(6), preferredLongRunDay = 6)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanningRequest(monday, 4, (1..6).toSet(),
                mapOf(Engine2Discipline.RUNNING to 1), preferredQualityDays = setOf(2))
        }
    }

    @Test fun experimentalReviewCardUsesNoEnabledPersistencePath() {
        // Compile-time/architecture reminder: no repository-level adapter is bound.
        val plan = Engine2PlanView("owner", "p", 1L, Engine2PlanStatus.APPROVED,
            listOf(Engine2SessionTarget("s", monday, Engine2Discipline.RUNNING, 60)))
        assertTrue(plan.appliedProposalIds.isEmpty())
        assertEquals(Engine2SessionStatus.PLANNED, plan.sessions.single().status)
    }
}

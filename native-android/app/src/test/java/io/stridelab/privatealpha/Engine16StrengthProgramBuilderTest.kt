package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine16StrengthProgramBuilderTest {

    private val owner = "local-plan-66666666-7777-4888-8999-aaaaaaaaaaaa"
    private val now = Instant.parse("2026-11-27T08:00:00Z")
    private val start = LocalDate.of(2026, 11, 30)

    @Test
    fun buildsHevySplitsAcrossOneToSixDaysPerWeekWithValidateSavedDeterminism() {
        val fullGymEquipment = mapOf("db" to 1, "bb" to 1, "rack" to 1, "bench" to 1, "cable" to 1, "machine" to 1, "bands" to 1)

        // 1d Full Body MED fallback under 30 min budget
        val res1d = Engine16StrengthProgramBuilder.build(
            request = PlanRequest(
                goal = "General strength",
                start = start,
                weeks = 4,
                minutes = 30,
                days = setOf(1, 3, 5),
                runDays = 0,
                strengthDays = 1,
                strengthExperience = 0,
                equipment = fullGymEquipment,
                policyChoice = "engine15"
            ),
            owner = owner,
            id = "e16-1d",
            now = now
        )
        assertEquals(4, res1d.snapshot.sessions.size)
        assertTrue(res1d.snapshot.sessions.all { it.minutes <= 30 })
        Engine16StrengthProgramBuilder.validateSaved(res1d.snapshot)

        // 4d Upper/Lower Hypertrophy (non-failure 1-3 RIR + conditional deload in week 4)
        val res4d = Engine16StrengthProgramBuilder.build(
            request = PlanRequest(
                goal = "Muscle development",
                start = start,
                weeks = 4,
                minutes = 75,
                days = setOf(1, 2, 4, 5),
                runDays = 0,
                strengthDays = 4,
                strengthExperience = 1,
                equipment = fullGymEquipment,
                policyChoice = "engine15"
            ),
            owner = owner,
            id = "e16-4d",
            now = now
        )
        assertEquals(16, res4d.snapshot.sessions.size)
        assertEquals(StrengthMesocyclePhase.CONDITIONAL_DELOAD, res4d.weeklyPhaseByWeek[4])
        assertTrue(res4d.snapshot.sessions.take(4).map { it.title }.any { it.startsWith("Upper A") })
        assertTrue(res4d.snapshot.sessions.take(4).map { it.title }.any { it.startsWith("Lower B") })
        assertTrue(res4d.weeklyMuscleLedgers.all { it.isProjected })
        Engine16StrengthProgramBuilder.validateSaved(res4d.snapshot)

        // 6d Push / Pull / Legs A & B
        val res6d = Engine16StrengthProgramBuilder.build(
            request = PlanRequest(
                goal = "Muscle development",
                start = start,
                weeks = 2,
                minutes = 75,
                days = setOf(1, 2, 3, 4, 5, 6),
                runDays = 0,
                strengthDays = 6,
                strengthExperience = 2,
                equipment = fullGymEquipment,
                policyChoice = "engine15"
            ),
            owner = owner,
            id = "e16-6d",
            now = now
        )
        assertEquals(12, res6d.snapshot.sessions.size)
        val week1Titles = res6d.snapshot.sessions.take(6).map { it.title }
        assertTrue(week1Titles.any { it.startsWith("Push A") })
        assertTrue(week1Titles.any { it.startsWith("Legs B") })
        Engine16StrengthProgramBuilder.validateSaved(res6d.snapshot)
    }

    @Test
    fun buildsAllSevenObjectivesCleanlyWithoutEmDashesAndPassesDifficultyScaling() {
        val goals = listOf(
            "Maximal strength",
            "Muscle development",
            "General strength",
            "Strength endurance",
            "Strength maintenance",
            "Strength for runners",
            "Power development"
        )
        val equip = mapOf("db" to 1, "bb" to 1, "rack" to 1, "bench" to 1, "kb" to 1, "cable" to 1, "machine" to 1)
        for ((idx, g) in goals.withIndex()) {
            val res = Engine16StrengthProgramBuilder.build(
                request = PlanRequest(
                    goal = g,
                    start = start,
                    weeks = 4,
                    minutes = 75,
                    days = setOf(1, 3, 5),
                    runDays = 0,
                    strengthDays = if (g == "Strength maintenance") 1 else 2,
                    strengthExperience = 1,
                    equipment = equip,
                    policyChoice = "engine15"
                ),
                owner = owner,
                id = "e16-goal-$idx",
                now = now
            )
            Engine16StrengthProgramBuilder.validateSaved(res.snapshot)
            for (s in res.snapshot.sessions) {
                assertFalse("Em-dash found in title", "—" in s.title)
                assertFalse("Em-dash found in reason", "—" in s.reason)
                assertTrue(s.steps.none { "—" in it })
                assertTrue(s.exercises.none { "—" in it.cue || "—" in it.name })
            }
            assertTrue(res.snapshot.explanations.none { "—" in it })
        }

        // Bodyweight-only beginner must never crash on missing gym equipment!
        val bwPlan = Engine16StrengthProgramBuilder.build(
            request = PlanRequest(
                goal = "General strength",
                start = start,
                weeks = 4,
                minutes = 45,
                days = setOf(1, 3, 5),
                runDays = 0,
                strengthDays = 3,
                strengthExperience = 0,
                equipment = emptyMap(),
                policyChoice = "engine15"
            ),
            owner = owner,
            id = "e16-bw",
            now = now
        )
        Engine16StrengthProgramBuilder.validateSaved(bwPlan.snapshot)
        assertEquals(12, bwPlan.snapshot.sessions.size)
    }

    @Test
    fun enforcesSafetyRestrictionsAndSupportsPlanScheduleValidation() {
        val hardBlock = AthleteSafetyRestrictionSet(
            accountId = owner,
            restrictions = listOf(
                AthleteSafetyRestriction(
                    restrictionId = "str-block-1",
                    accountId = owner,
                    kind = SafetyRestrictionKind.MEDICAL_OR_REST_OVERRIDE,
                    disciplines = setOf(AthleteDiscipline.STRENGTH),
                    severity = SafetyRestrictionSeverity.HARD_BLOCK,
                    createdAtUtc = now.minusSeconds(3600)
                )
            )
        )
        assertThrows(PlanConstraint::class.java) {
            Engine16StrengthProgramBuilder.build(
                request = PlanRequest(
                    goal = "General strength",
                    start = start,
                    weeks = 4,
                    minutes = 60,
                    days = setOf(1, 4),
                    runDays = 0,
                    strengthDays = 2,
                    policyChoice = "engine15"
                ),
                owner = owner,
                id = "e16-blocked",
                now = now,
                safetyRestrictions = hardBlock
            )
        }

        // Approved Engine 1.6 plan passes PlanScheduleValidation + Engine1Engines.canManage!
        val res = Engine16StrengthProgramBuilder.build(
            request = PlanRequest(
                goal = "General strength",
                start = start,
                weeks = 4,
                minutes = 60,
                days = setOf(1, 2, 4, 5),
                runDays = 0,
                strengthDays = 4,
                strengthExperience = 1,
                equipment = mapOf("db" to 1, "bench" to 1),
                policyChoice = "engine15"
            ),
            owner = owner,
            id = "e16-approved",
            now = now
        )
        val approved = res.snapshot.copy(status = "approved")
        assertTrue(Engine1Engines.canManage(approved))
        PlanScheduleValidation.validatePlan(approved)
    }
}

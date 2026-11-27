package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine17CalisthenicsProgramBuilderTest {

    private val owner = "cal-journey-tester"
    private val now = Instant.parse("2026-11-23T08:00:00Z")
    private val monday = LocalDate.of(2026, 11, 23)

    @Test fun freeleticsSplits1To6DaysHandstandUnblockedAndFreshSkillFirst() {
        // 1..6 d/wk splits across core objectives generate, round-trip, and validate deterministically
        val equipment = mapOf("bar" to 1, "bands" to 1, "bench" to 1, "wall" to 1, "rings" to 1)
        for (calDays in 1..6) {
            val req = PlanRequest(
                goal = if (calDays == 2) "Pull-up progression" else "Calisthenics foundation",
                start = monday,
                weeks = 4,
                days = (1..7).toSet(),
                runDays = 0,
                calDays = calDays,
                calLevel = 1,
                pullLevel = if (calDays == 2) 0 else 1,
                coreLevel = 1,
                minutes = 50,
                equipment = equipment,
                policyChoice = "engine15"
            )
            val result = Engine17CalisthenicsProgramBuilder.build(req, owner, "cal-${calDays}d", now)
            assertEquals(4 * calDays, result.snapshot.sessions.size)
            assertEquals(ENGINE17_VERSION, result.snapshot.engineVersion)
            assertEquals(ENGINE17_CALISTHENICS_POLICY, result.snapshot.policyId)

            val decoded = PlanSnapshot.decode(PlanOperations.approve(result.snapshot, listOf(result.snapshot)).encode(), owner)
            Engine17CalisthenicsProgramBuilder.validateSaved(decoded)
            Engine15IndependentPlanGenerator.validateSaved(decoded)

            // Zero em-dashes in athlete-facing text
            for (s in result.snapshot.sessions) {
                assertFalse(s.title.contains("\u2014"))
                assertFalse(s.reason.contains("\u2014"))
                assertTrue(s.steps.none { "\u2014" in it })
                assertTrue(s.exercises.none { "\u2014" in it.cue || "\u2014" in it.name })
            }
        }

        // Handstand foundations is now UNBLOCKED via Engine 1.7 with Fresh Skill practice FIRST!
        val hsReq = PlanRequest(
            goal = "Handstand foundations",
            start = monday,
            weeks = 6,
            days = setOf(1, 3, 5),
            runDays = 0,
            calDays = 3,
            calLevel = 1,
            coreLevel = 1,
            minutes = 45,
            equipment = equipment,
            policyChoice = "engine15"
        )
        val hsPlan = Engine15IndependentPlanGenerator.generateWithHistory(hsReq, owner, emptyList(), "cal-hs", now)
        assertEquals(ENGINE17_CALISTHENICS_POLICY, hsPlan.policyId)
        assertEquals(18, hsPlan.sessions.size)
        val firstEx = hsPlan.sessions.first().exercises.first()
        assertTrue("Expected fresh skill exercise first, got ${firstEx.id}", firstEx.cue.contains("[Skill"))
        Engine15IndependentPlanGenerator.validateSaved(hsPlan)
    }

    @Test fun m1dHardBlockTimeComplianceAndBeginnerEccentricCapsEnforced() {
        val novReq = PlanRequest(
            goal = "Pull-up progression",
            start = monday,
            weeks = 4,
            days = setOf(1, 3, 5),
            runDays = 0,
            calDays = 2,
            calLevel = 0,
            pullLevel = 0,
            timeBudgetEnabled = true,
            minutes = 30,
            equipment = mapOf("bar" to 1, "bands" to 1),
            policyChoice = "engine15"
        )
        val res = Engine17CalisthenicsProgramBuilder.build(novReq, owner, "nov-pu", now)
        assertTrue(res.snapshot.sessions.all { it.minutes <= 30 })
        // Any eccentric pull-up in novice cluster must respect <= 2 sets x 3 reps
        res.snapshot.sessions.flatMap { it.exercises }
            .filter { it.id == "cal-negative-pullup" }
            .forEach { ex ->
                assertTrue(ex.sets <= 2)
                assertTrue((ex.reps ?: 0) <= 3)
            }

        val restriction = AthleteSafetyRestriction(
            restrictionId = "hold-cal-1",
            accountId = owner,
            kind = SafetyRestrictionKind.INJURY_OR_PAIN,
            disciplines = setOf(AthleteDiscipline.CALISTHENICS),
            severity = SafetyRestrictionSeverity.HARD_BLOCK,
            provenance = SafetyRestrictionProvenance.USER_DECLARED,
            maxSessionIntensityCap = SafetyIntensityCap.REST_ONLY,
            note = "Pause upper-body calisthenics pending recovery.",
            createdAtUtc = now
        )
        val blockedSet = AthleteSafetyRestrictionSet(owner, listOf(restriction))
        var threw = false
        try {
            Engine17CalisthenicsProgramBuilder.validateRequest(novReq, blockedSet, now)
        } catch (e: PlanConstraint) {
            threw = e.message?.contains("SAFETY_RESTRICTION_HOLD") == true
        }
        assertTrue(threw)
    }
}

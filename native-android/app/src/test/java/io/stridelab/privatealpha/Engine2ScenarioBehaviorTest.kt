package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Random

/**
 * Engine-output assertions grounded in fictional scenario classes.
 * No purported training-effectiveness labels or private athlete data.
 */
class Engine2ScenarioBehaviorTest {
    private val monday = LocalDate.parse("2026-11-02")
    private val instant = Instant.parse("2026-10-27T12:00:00Z")

    @Test fun manySeededMixedDisciplinePlansNeverDropOrCompleteSessions() {
        val random = Random(20261028L)
        repeat(250) { scenario ->
            val days = (1..7).shuffled(random).take(3 + random.nextInt(4)).toSet()
            val sessionCount = 1 + random.nextInt(days.size)
            val runCount = random.nextInt(sessionCount + 1)
            val other = sessionCount - runCount
            val strCount = random.nextInt(other + 1)
            val calCount = other - strCount
            val disciplines = mapOf(
                Engine2Discipline.RUNNING to runCount,
                Engine2Discipline.STRENGTH to strCount,
                Engine2Discipline.CALISTHENICS to calCount)
            val longDay = if (runCount > 0 && random.nextBoolean()) days.minOrNull() else null
            val eligibleQualityDays = days.filter { it != longDay }
            val quality = if (runCount >= 2 && eligibleQualityDays.isNotEmpty() &&
                random.nextBoolean()) setOf(eligibleQualityDays.first()) else emptySet()
            val request = Engine2PlanningRequest(
                monday, 1 + random.nextInt(8), days, disciplines,
                longDay, quality, 90, true)
            val generated = Engine2UnifiedPlanGenerator.generate(
                "synthetic-owner", "generated-$scenario", request)
            val all = generated.plan.sessions
            assertEquals(sessionCount * request.weeks, all.size)
            assertEquals(all.size, all.map { it.id }.distinct().size)
            assertEquals(Engine2PlanStatus.DRAFT, generated.plan.status)
            assertTrue(all.all { it.status == Engine2SessionStatus.PLANNED })
            assertTrue(all.all { it.minutes <= request.maximumDailyMinutes })
            for (week in 0 until request.weeks) {
                val weekly = all.filter { it.date >= monday.plusWeeks(week.toLong()) &&
                    it.date < monday.plusWeeks((week + 1).toLong()) }
                assertEquals(sessionCount, weekly.size)
                assertEquals(weekly.size, weekly.map { it.date }.distinct().size)
                for ((discipline, count) in disciplines) {
                    assertEquals(count, weekly.count { it.discipline == discipline })
                }
            }
            assertEquals(generated, Engine2UnifiedPlanGenerator.generate(
                "synthetic-owner", "generated-$scenario", request))
        }
    }

    @Test fun approvalDecisionTrialsNeverAlterUnselectedWorkoutsOrHistoricalActuals() {
        val original = Engine2PlanView("fictional-athlete", "plan", 4,
            Engine2PlanStatus.APPROVED,
            listOf(
                Engine2SessionTarget("unstarted", monday, Engine2Discipline.RUNNING, 60),
                Engine2SessionTarget("historical", monday.minusDays(1),
                    Engine2Discipline.STRENGTH, 40, Engine2SessionStatus.COMPLETED),
                Engine2SessionTarget("other", monday.plusDays(1),
                    Engine2Discipline.CALISTHENICS, 30)))
        val random = Random(1337L)
        repeat(120) { index ->
            val decision = when (random.nextInt(3)) {
                0 -> Engine2Decision.APPROVE
                1 -> Engine2Decision.REJECT
                else -> Engine2Decision.POSTPONE
            }
            val duration = 10 + random.nextInt(50)
            val proposal = Engine2DurationProposal(
                "case-$index", original.ownerId, original.planId, original.revision,
                "unstarted", monday, 60, duration,
                "Fictional owner requested a review of reduced duration.",
                setOf("synthetic-actual"), instant)
            val staged = Engine2ApprovalLedgerReducer.stage(
                Engine2ApprovalLedger(original), proposal)
            val candidate = Engine2ApprovalTransactions.prepareDecision(staged,
                proposal.id, decision, instant.plusSeconds(1))
            assertEquals(original.sessions[1], candidate.updated.plan.sessions[1])
            assertEquals(original.sessions[2], candidate.updated.plan.sessions[2])
            val expected = if (decision == Engine2Decision.APPROVE) duration else 60
            assertEquals(expected, candidate.updated.plan.sessions[0].minutes)
            assertEquals(candidate.updated, Engine2ApprovalLedgerReducer.decide(
                candidate.updated, proposal.id, decision, instant.plusSeconds(2)))
            assertEquals(60, original.sessions[0].minutes)
        }
    }

    @Test fun sourceIdentityCollisionNeverPermitsAutomaticRecordedRunMatching() {
        val source = Engine2Source("fictional-athlete", "synthetic", "same",
            instant.minusSeconds(3600), instant.minusSeconds(300))
        val workouts = listOf("a", "b").map { id ->
            Engine2WorkoutEvidence(id, Engine2Discipline.RUNNING,
                instant.minusSeconds(3600), Engine2RecordStatus.CONFIRMED,
                listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, 5.0, source)))
        }
        val ctx = Engine2AthleteContext("fictional-athlete", instant, workouts, emptyList())
        val plan = Engine2PlanView(ctx.ownerId, "p", 1, Engine2PlanStatus.APPROVED,
            listOf(Engine2SessionTarget("s", LocalDate.parse("2026-10-27"),
                Engine2Discipline.RUNNING, 40)))
        val review = Engine2ActivityMatcher.findCandidates(plan, ctx, "s")
        assertFalse(review.automaticLinkAllowed)
        assertTrue(review.candidates.isEmpty())
        assertTrue(Engine2ProvenanceAudit.inspect(ctx).sourceCollisions.isNotEmpty())
    }
}

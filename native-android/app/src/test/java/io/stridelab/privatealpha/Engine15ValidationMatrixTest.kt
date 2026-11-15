package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Batch D: reproducible, synthetic boundary tests.
 * These establish deterministic safety properties, not clinical efficacy or race readiness.
 */
class Engine15ValidationMatrixTest {
    private val owner = "engine15-matrix-owner"
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val start = LocalDate.parse("2026-11-09")
    private val today = LocalDate.parse("2026-11-03")

    private fun request(
        goal: String, weeklyKm: Double, experience: Int, returning: Boolean
    ) = PlanRequest(
        goal = goal, policyChoice = "engine15", start = start, weeks = 8,
        minutes = 90, days = (1..7).toSet(), runDays = 4, longDay = 6,
        qualityDays = setOf(2, 4), qualityCount = 1,
        experience = experience, weeklyKm = weeklyKm,
        longestKm = if (weeklyKm == 0.0) 0.0 else minOf(16.0, weeklyKm / 3.0),
        planningPace = 480.0, returning = returning
    )

    @Test fun sixtyFourAthleteProfilesRetainCalendarIdentityAndConservativeBounds() {
        var profiles = 0
        for (goal in listOf("Improve 5K", "Improve 10K", "Half marathon", "Marathon"))
            for (weekly in listOf(0.0, 18.0, 35.0, 65.0))
                for (experience in listOf(0, 2))
                    for (returning in listOf(false, true)) {
                        val req = request(goal, weekly, experience, returning)
                        val id = "matrix-${profiles++}"
                        val legacy = Engine1.generate(req.copy(policyChoice = "baseline"), owner, id, now)
                        val candidate = Engine15.generate(req, owner, id, now)
                        assertEquals("unchanged dates/ids: $id",
                            legacy.sessions.map { it.id to it.date },
                            candidate.sessions.map { it.id to it.date })
                        assertEquals(ENGINE15_VERSION, candidate.engineVersion)
                        assertEquals(ENGINE15_POLICY, candidate.policyId)
                        assertEquals(candidate, PlanSnapshot.decode(candidate.encode(), owner))
                        assertEquals(32, candidate.sessions.size)
                        for (w in 0 until req.weeks) {
                            val dates = candidate.sessions.filter {
                                ChronoUnit.DAYS.between(start, it.date) / 7L == w.toLong()
                            }
                            val baseline = legacy.sessions.filter {
                                ChronoUnit.DAYS.between(start, it.date) / 7L == w.toLong()
                            }
                            val run = dates.filter { it.discipline == "run" }
                            assertEquals(4, run.size)
                            assertEquals(1, run.count { it.kind == "long" })
                            val volume = run.sumOf { it.distanceKm ?: 0.0 }
                            assertTrue("$id week $w volume cap", volume <=
                                Engine15.runningCeiling(w, weekly) + 1e-6)
                            assertTrue("$id week $w no invented speed", run.all {
                                it.steps.none { step -> step.contains("bpm") || step.contains("min/km") }
                            })
                            val long = run.single { it.kind == "long" }
                            if (long.distanceKm != null) assertTrue(
                                "$id week $w long cap", long.distanceKm <= 0.4 * volume + 1e-6)
                            assertEquals(baseline.filter { it.discipline!="run" },dates.filter { it.discipline!="run" })
                            assertTrue(volume<=baseline.filter { it.discipline=="run" }.sumOf { it.distanceKm ?: 0.0 }+1e-8)
                            for (session in run) {
                                if(req.timeBudgetEnabled) assertTrue("$id availability",session.minutes<=req.minutes)
                                session.distanceKm?.let { km ->
                                    assertTrue("$id exposure",km<=req.longestKm+1.0+1e-8)
                                    assertEquals(km*1000,session.runningPrescription!!.meters.toDouble(),0.001)
                                }
                            }
                            assertTrue(run.sumOf { it.runningPrescription?.workMeters ?: 0 }<=volume*1000*0.2+1e-8)
                            if (experience == 0 || weekly < 20.0 ||
                                (returning && w < 3) || w % 4 == 3 ||
                                (goal in setOf("Half marathon", "Marathon") && w >= 6))
                                assertTrue("$id week $w hard work blocked", run.none { it.quality })
                        }
                    }
        assertEquals(64, profiles)
    }

    @Test fun mixedRunningStrengthAndCalisthenicsPreserveOriginalPrescriptions() {
        val req = request("Improve 5K", 45.0, 2, false).copy(
            weeks = 4, strengthDays = 1, calDays = 1,
            equipment = mapOf("db" to 2, "support" to 1)
        )
        val baseline = Engine1.generate(req.copy(policyChoice = "baseline"), owner, "mixed", now)
        val candidate = Engine15.generate(req, owner, "mixed", now)
        assertEquals(24, candidate.sessions.size)
        assertEquals(baseline.sessions.filter { it.discipline != "run" },
            candidate.sessions.filter { it.discipline != "run" })
        assertEquals(baseline.sessions.map { it.id to it.date },
            candidate.sessions.map { it.id to it.date })
        assertEquals(candidate, PlanSnapshot.decode(candidate.encode(), owner))
        assertTrue(candidate.sessions.any { it.discipline == "str" })
        assertTrue(candidate.sessions.any { it.discipline == "cal" })
    }

    private fun recorded(
        id: String, date: LocalDate, activity: String? = null,
        effort: Int = 9
    ) = PlannedSession(
        id, date, "run", "Recorded running", 40, 6.0, "easy",
        status = "completed", log = WorkoutLog(
            Instant.parse("2026-11-02T12:00:00Z"), minutes = 35.0,
            effort = effort, performedOn = date
        ), linkedActivityId = activity, logRevision = 1
    )

    private fun fixture(sessions: List<PlannedSession>, id: String = "candidate") = PlanSnapshot(
        id, owner, ENGINE15_VERSION, ENGINE15_POLICY, now,
        request("Improve 5K", 40.0, 2, false).copy(start = today.minusDays(8), weeks = 4),
        sessions, listOf("Synthetic evidence"), status = "approved", revision = 4
    )

    @Test fun multipleIndependentReportedSignalsProduceBoundedDistinctFutureProposals() {
        val plan = fixture(listOf(
            PlannedSession("illness", today.minusDays(2), "run", "Missed run", 30, 4.0,
                "easy", status = "missed", missedReason = "Illness"),
            PlannedSession("fatigue", today.minusDays(1), "str", "Strength session",
                35, null, "strength", status = "missed", missedReason = "Fatigue"),
            PlannedSession("next", today.plusDays(1), "run", "Easy run", 60, 9.0, "easy"),
            PlannedSession("later", today.plusDays(2), "run", "Easy run", 30, 4.5, "easy")
        ))
        val proposals = Engine15Feedback.preview(plan, today)
        assertEquals(2, proposals.size)
        assertEquals(listOf("fatigue", "illness"), proposals.map { it.sourceSessionId })
        assertEquals(listOf("next", "later"), proposals.map { it.targetSessionId })
        assertEquals(listOf(42, 0), proposals.map { it.suggestedMinutes })
        assertEquals(plan, PlanSnapshot.decode(plan.encode(), owner))
        assertTrue(plan.sessions.all { it.suggestion == null })
        assertEquals(proposals, Engine15Feedback.preview(plan, today))
        val accepted = PlanOperations.acceptSuggestion(
            Engine15Feedback.stage(plan, proposals.first(), today), "next")
        assertEquals(42, accepted.sessions.single { it.id == "next" }.effectiveMinutes)
        assertEquals(9.0, accepted.sessions.single { it.id == "next" }.distanceKm!!, 0.0)
        assertEquals(plan.sessions.take(2), accepted.sessions.take(2))
    }

    @Test fun duplicateLinkedEvidenceAcrossPlansFailsClosedAndRestoreRejectsIt() {
        val original = fixture(listOf(
            recorded("observed", today.minusDays(1), "linked-original"),
            PlannedSession("target", today.plusDays(1), "run", "Easy", 60, 8.0, "easy")
        ))
        val other = fixture(
            listOf(recorded("another-source", today.minusDays(1), "linked-original")),
            id = "imported"
        )
        val originalBytes = original.encode().toString()
        assertTrue(Engine15Feedback.preview(original, today, listOf(original, other)).isEmpty())
        assertThrows(PlanConstraint::class.java) {
            PlanBackup.additions(listOf(other), listOf(original))
        }
        assertEquals(originalBytes, original.encode().toString())
        assertEquals(1, Engine15Feedback.preview(original, today).size)
    }

    @Test fun acceptedAdjustmentsRoundtripOwnerBoundaryAndStaleEvidenceFailsClosed() {
        val original = fixture(listOf(
            recorded("observed", today.minusDays(1)),
            PlannedSession("future", today.plusDays(1), "run", "Easy", 60, 8.0, "easy")
        ))
        val proposal = Engine15Feedback.preview(original, today).single()
        val staged = Engine15Feedback.stage(original, proposal, today).copy(revision = 5)
        val accepted = PlanOperations.acceptSuggestion(staged, "future").copy(revision = 6)
        val backup = JSONObject().put("schemaVersion", 1).put(
            "plans", JSONArray().put(accepted.encode())).toString()
        assertEquals(listOf(accepted), PlanBackup.read(backup, owner))
        assertThrows(PlanConstraint::class.java) { PlanBackup.read(backup, "wrong-owner") }
        assertTrue(Engine15Feedback.preview(accepted, today).isEmpty())
        assertEquals(original.sessions.first(), accepted.sessions.first())
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.stage(original.copy(revision = 5), proposal, today)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.stage(original.copy(sessions = original.sessions.map {
                if (it.id == "observed") it.copy(logRevision = 2,
                    log = it.log?.copy(effort = 3)) else it
            }), proposal, today)
        }
    }
}

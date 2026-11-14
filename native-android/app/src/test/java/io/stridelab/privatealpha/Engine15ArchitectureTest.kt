package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Engine15ArchitectureTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val start = LocalDate.parse("2026-11-09")
    private val owner = "synthetic-owner"

    private fun request() = PlanRequest(
        goal = "Improve 5K",
        policyChoice = "engine15",
        start = start,
        weeks = 4,
        experience = 1,
        weeklyKm = 24.0,
        longestKm = 8.0,
        runDays = 3,
        qualityCount = 0
    )

    @Test fun policyIsExplicitAndAllEngine10FallbacksRemainStable() {
        val candidate = request()
        assertSame(Engine15, Engine1Engines.resolve(candidate))
        assertSame(Engine1, Engine1Engines.resolve(candidate.copy(policyChoice = "baseline")))
        assertSame(CoordinatedEngine1, Engine1Engines.resolve(candidate.copy(policyChoice = "coordinated")))
        assertSame(MultiSessionEngine1, Engine1Engines.resolve(candidate.copy(policyChoice = "multi-session")))
        Engine1Engines.validate(candidate)

        assertEquals(candidate, PlanRequest.decode(candidate.encode()))
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(candidate.copy(policyChoice = "baseline"), owner, "wrong", now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine1Engines.resolve(candidate.copy(policyChoice = "invalid"))
        }
    }

    @Test fun targetMileageRoundTripsAndLegacyRequestsUseRecentMileageAsTarget() {
        val request = request().copy(weeklyKm = 42.0, targetWeeklyKm = 65.0)
        assertEquals(request, PlanRequest.decode(request.encode()))
        val legacy = request.encode().apply { remove("targetWeeklyKm") }
        assertNull(PlanRequest.decode(legacy).targetWeeklyKm)
    }

    @Test fun explicitTargetEqualToRecentMileageRemainsExplicitInSavedPlan() {
        for (km in listOf(0.0, 24.0)) {
            val requested = request().copy(weeklyKm = km, targetWeeklyKm = km)
            assertTrue(requested.encode().has("targetWeeklyKm"))
            assertEquals(requested, PlanRequest.decode(requested.encode()))
            val snapshot = Engine15.generate(requested, owner, "target-$km", now)
            assertEquals(snapshot, PlanSnapshot.decode(snapshot.encode(), owner))
        }
        assertFalse(request().encode().has("targetWeeklyKm"))
        assertNull(PlanRequest.decode(request().encode()).targetWeeklyKm)
    }

    @Test fun tracksidePreferencesAndExactSessionLocksSurviveTheSameSnapshot() {
        val original = request().copy(
            dailyMinutes = 85,
            weeklySlots = mapOf("run" to listOf(6, 1, 2)),
            preferredQuality = "Intervals",
            allowedQualityTypes = setOf("Intervals")
        )
        val draft = Engine15.generate(original, owner, "trackside-lock", now)
        val target = draft.sessions.first()
        val locked = PlanOperations.setSessionLock(draft, target.id, true)
        val reopened = PlanSnapshot.decode(locked.encode(), owner)
        assertEquals(original.weeklySlots, reopened.request.weeklySlots)
        assertEquals(original.preferredQuality, reopened.request.preferredQuality)
        assertEquals(original.allowedQualityTypes, reopened.request.allowedQualityTypes)
        assertEquals(85, reopened.request.dailyMinutes)
        assertEquals(target.date.toString(), reopened.request.lockedSessionDays[target.id])
        assertEquals(locked, reopened)
    }

    @Test fun candidateKeepsBaselineDatesAndEquipmentWithBoundedRunChanges() {
        val request = request()
        val old = Engine1.generate(request.copy(policyChoice = "baseline"), owner, "same-id", now)
        val candidate = Engine15.generate(request, owner, "same-id", now)

        assertEquals(ENGINE15_VERSION, candidate.engineVersion)
        assertEquals(ENGINE15_POLICY, candidate.policyId)
        assertEquals("draft", candidate.status)
        assertEquals(request, candidate.request)
        assertEquals(old.sessions.map { it.id to it.date }, candidate.sessions.map { it.id to it.date })
        assertEquals(old.sessions.filter { it.discipline != "run" },
            candidate.sessions.filter { it.discipline != "run" })
        assertTrue(candidate.sessions.zip(old.sessions).all { (new, previous) ->
            new.minutes <= previous.minutes &&
                (new.distanceKm == null ||
                    (previous.distanceKm != null && new.distanceKm <= previous.distanceKm))
        })
        assertTrue(candidate.explanations.first().contains("bounded-running"))
        assertTrue(Engine1Engines.canManage(candidate))
        assertTrue(Engine1Engines.supports(ENGINE1_VERSION, ENGINE1_POLICY))
        assertFalse(Engine1Engines.supports(ENGINE15_VERSION, ENGINE1_POLICY))
        assertFalse(Engine1Engines.supports(ENGINE1_VERSION, ENGINE15_POLICY))
    }

    @Test fun candidateSurvivesOwnerBoundRoundtripAndNonDestructiveBackup() {
        val draft = Engine15.generate(request(), owner, "candidate-backup", now)
        val decoded = PlanSnapshot.decode(draft.encode(), owner)
        assertEquals(draft, decoded)
        assertNull(decoded.foreignSnapshot)

        val approved = PlanOperations.approve(decoded, emptyList())
        val first = approved.sessions.first()
        val recorded = PlanOperations.record(
            approved, first.id, WorkoutLog(now, minutes = 22.0, effort = 3), true
        )
        val backup = JSONObject()
            .put("schemaVersion", 1)
            .put("plans", JSONArray().put(recorded.encode()))
            .toString()

        assertEquals(listOf(recorded), PlanBackup.read(backup, owner))
        assertEquals(recorded, PlanSnapshot.decode(recorded.encode(), owner))
        assertTrue(PlanBackup.additions(listOf(recorded), listOf(recorded)).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            PlanSnapshot.decode(recorded.encode(), "different-owner")
        }
    }

    @Test fun unknownEngine15PolicyStaysOpaqueReadOnlyAndByteFieldsArePreserved() {
        val candidate = Engine15.generate(request(), owner, "future", now)
        val raw = candidate.encode()
            .put("policyId", "stridelab.engine15.future-policy")
            .put("futureRule", JSONObject().put("retain", 7))
        raw.getJSONArray("sessions").getJSONObject(0)
            .put("futureSession", "preserve")

        val unknown = PlanSnapshot.decode(raw, owner)
        assertFalse(Engine1Engines.canManage(unknown))
        assertEquals(7, unknown.encode().getJSONObject("futureRule").getInt("retain"))
        assertEquals(
            "preserve",
            unknown.encode().getJSONArray("sessions").getJSONObject(0)
                .getString("futureSession")
        )
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.approve(unknown, emptyList())
        }
    }

    @Test fun previouslySavedEngine10PlanRemainsEditableAndUnmodified() {
        val request = request().copy(policyChoice = "baseline")
        val old = Engine1.generate(request, owner, "old-plan", now)
        val raw = old.encode()
        val decoded = PlanSnapshot.decode(raw, owner)

        assertEquals(old, decoded)
        assertTrue(Engine1Engines.canManage(decoded))
        assertEquals(ENGINE1_VERSION, decoded.engineVersion)
        assertEquals(ENGINE1_POLICY, decoded.policyId)
        assertEquals(old.sessions, PlanOperations.approve(decoded, emptyList()).sessions)
        assertEquals(raw.toString(), decoded.encode().toString())
    }
}

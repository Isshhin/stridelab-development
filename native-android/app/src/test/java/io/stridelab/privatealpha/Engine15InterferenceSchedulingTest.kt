package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Pure deterministic B3 release gates, not a model of physiological adaptation. */
class Engine15InterferenceSchedulingTest {
    private val start = LocalDate.parse("2026-11-09")
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val owner = "synthetic-owner"
    private fun request() = PlanRequest(
        goal = "Improve 5K", policyChoice = "engine15", start = start,
        weeks = 4, minutes = 45, days = setOf(2,3,4,5,6,7),
        runDays = 5, strengthDays = 2, calDays = 2,
        longDay = 7, qualityDays = setOf(2), qualityCount = 1,
        weeklyKm = 65.0, longestKm = 16.0, planningPace = 400.0,
        experience = 2, strengthExperience = 2,
        equipment = mapOf("db" to 2, "support" to 1), dailyMinutes = 120
    )

    @Test fun nineWeeklySessionsFitSixDaysWithOnlySafePairsAndHardRecoveryRules() {
        val req = request()
        val generated = Engine15.generate(req, owner, "nine", now)
        assertEquals(ENGINE15_INTERFERENCE_POLICY, generated.policyId)
        assertEquals(ENGINE15_VERSION, generated.engineVersion)
        assertEquals(generated, Engine15.generate(req, owner, "nine", now))
        assertEquals(36, generated.sessions.size)
        assertEquals(36, generated.sessions.map { it.id }.distinct().size)
        for (week in 0..3) {
            val first = start.plusWeeks(week.toLong())
            val sessions = generated.sessions.filter { it.date >= first && it.date < first.plusWeeks(1) }
            assertEquals(9, sessions.size)
            assertEquals(5, sessions.count { it.discipline == "run" })
            assertEquals(2, sessions.count { it.discipline == "str" })
            assertEquals(2, sessions.count { it.discipline == "cal" })
            assertEquals(1, sessions.count { it.kind == "long" && it.date.dayOfWeek.value == 7 })
            assertEquals(1, sessions.count { it.discipline == "str" && it.lower })
            assertEquals(1, sessions.count { it.discipline == "str" && !it.lower })
            assertTrue(sessions.all { it.date.dayOfWeek.value in req.days })
            val doubles = sessions.groupBy { it.date }.values.filter { it.size == 2 }
            assertEquals(3, doubles.size)
            assertTrue(doubles.all { day -> day.single { it.discipline == "run" }.kind == "easy" &&
                day.any { it.discipline in setOf("str","cal") } })
            assertTrue(sessions.groupBy { it.date }.values.all { day ->
                day.size <= 2 && day.sumOf { it.effectiveMinutes } <= req.dailyMinutes
            })
            assertTrue(sessions.filter { it.discipline == "run" }.sumOf { it.distanceKm ?: 0.0 }
                <= Engine15.runningCeiling(week, req.weeklyKm) + 1e-6)
        }
        for (i in generated.sessions.indices) for (j in i + 1 until generated.sessions.size)
            assertFalse("Unsafe pair: ${generated.sessions[i].id} and ${generated.sessions[j].id}",
                Engine15InterferenceScheduling.conflict(generated.sessions[i], generated.sessions[j]))
        val approved = PlanOperations.approve(generated, emptyList())
        PlanScheduleValidation.validatePlan(approved)
        assertEquals(approved, PlanSnapshot.decode(approved.encode(), owner))
        val backup = JSONObject().put("schemaVersion",1)
            .put("plans", JSONArray().put(approved.encode())).toString()
        assertEquals(listOf(approved), PlanBackup.read(backup,owner))
    }

    @Test fun strengthAndQualityDoubleDayRulesRejectUnsafeStressCombinations() {
        val day=start.plusDays(2)
        val easy=PlannedSession("easy",day,"run","Easy",35,5.0,"easy")
        val quality=easy.copy(id="quality",kind="quality",quality=true)
        val long=easy.copy(id="long",kind="long")
        val upper=PlannedSession("upper",day,"str","Upper",25,null,"strength",lower=false)
        val heavy=upper.copy(id="lower",lower=true)
        val cal=PlannedSession("cal",day,"cal","Light",20,null,"calisthenics")
        assertFalse(Engine15InterferenceScheduling.conflict(easy,upper))
        assertFalse(Engine15InterferenceScheduling.conflict(easy,cal))
        assertTrue(Engine15InterferenceScheduling.conflict(easy,heavy))
        assertTrue(Engine15InterferenceScheduling.conflict(quality,upper))
        assertTrue(Engine15InterferenceScheduling.conflict(long,cal))
        assertTrue(Engine15InterferenceScheduling.conflict(upper,cal))
        assertTrue(Engine15InterferenceScheduling.conflict(
            quality.copy(date=day.minusDays(1)),heavy))
        assertTrue(Engine15InterferenceScheduling.conflict(
            heavy.copy(date=day.minusDays(1)),long))
    }

    @Test fun impossibleTimeAndUnavailablePreferencesFailWithoutRemovingSessions() {
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(request().copy(dailyMinutes=45),owner,"tiny",now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(request().copy(days=setOf(2,3,4,5,6),longDay=7),owner,"day",now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(request().copy(qualityDays=setOf(7)),owner,"quality",now)
        }
    }

    @Test fun existingB2AndEngine10PoliciesStayUnchangedAndUnsupportedPairsStayOpaque() {
        val noStrength=request().copy(strengthDays=0)
        val b2=Engine15.generate(noStrength,owner,"b2",now)
        assertEquals(ENGINE15_MULTI_POLICY,b2.policyId)
        val legacy=MultiSessionEngine1.generate(
            noStrength.copy(policyChoice="multi-session"),owner,"legacy",now)
        assertEquals(MULTI_SESSION_POLICY,legacy.policyId)
        assertTrue(Engine1Engines.canManage(b2))
        assertTrue(Engine1Engines.supports(ENGINE15_VERSION,ENGINE15_INTERFERENCE_POLICY))
        assertFalse(Engine1Engines.supports(ENGINE1_VERSION,ENGINE15_INTERFERENCE_POLICY))
        val b3=PlanOperations.approve(Engine15.generate(request(),owner,"b3",now),emptyList())
        assertThrows(PlanConstraint::class.java) {
            PlanScheduleValidation.validatePlan(b3.copy(
                request=b3.request.copy(dailyMinutes=20)))
        }
    }
}

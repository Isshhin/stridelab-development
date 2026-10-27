package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Versioned, synthetic-only inputs. No model training, external network or real athlete data. */
class EngineDatasetTest {
    private fun fixture(name: String): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("engine/$name")) {
            "Missing test fixture: engine/$name"
        }
        return JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun request(overrides: JSONObject): PlanRequest {
        val base = PlanRequest().encode()
        val keys = overrides.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            assertTrue("Unknown PlanRequest input: $key", base.has(key) || key == "dailyMinutes")
            base.put(key, overrides.get(key))
        }
        return PlanRequest.decode(base)
    }

    @Test fun syntheticPlanCasesAreDeterministicAndPreserveCoreEngineContracts() {
        val fixture = fixture("engine1_plan_cases_v1.json")
        assertEquals(1, fixture.getInt("schemaVersion"))
        assertEquals("synthetic-only", fixture.getString("classification"))
        val cases = fixture.getJSONArray("cases")
        assertTrue(cases.length() >= 20)
        val seen = HashSet<String>()
        val at = Instant.parse(fixture.getJSONObject("seed").getString("at"))
        for (i in 0 until cases.length()) {
            val row = cases.getJSONObject(i)
            val id = row.getString("id")
            assertTrue("Duplicate scenario ID: $id", seen.add(id))
            val req = request(row.getJSONObject("request"))
            val result = row.getString("expected")
            if (result == "reject") {
                assertThrows("Expected rejection for $id", PlanConstraint::class.java) {
                    Engine1Engines.resolve(req).generate(req, "synthetic-dataset-owner", "synthetic-$id", at)
                }
                continue
            }
            assertEquals("Unrecognized expected result for $id", "accept", result)
            val engine = Engine1Engines.resolve(req)
            val plan = try {
                engine.generate(req, "synthetic-dataset-owner", "synthetic-$id", at)
            } catch (e: Exception) {
                throw AssertionError("Accepted fixture $id failed to generate: ${e.message}", e)
            }
            assertEquals("Deterministic generation failed for $id", plan,
                engine.generate(req, "synthetic-dataset-owner", "synthetic-$id", at))
            assertEquals("Wrong policy in $id", engine.policyId, plan.policyId)
            assertEquals(req, plan.request)
            assertEquals("draft", plan.status)
            val expectedWeekly = req.runDays + req.strengthDays + req.calDays + req.conditioningDays
            assertEquals("Dropped session(s) in $id", expectedWeekly * req.weeks, plan.sessions.size)
            assertEquals(plan.sessions.size, plan.sessions.map { it.id }.toSet().size)
            assertTrue("Generated completed/recorded data in $id", plan.sessions.all {
                it.status == "planned" && it.log == null && it.linkedActivityId == null &&
                    it.date >= req.start && it.date < req.start.plusWeeks(req.weeks.toLong())
            })
            for (w in 0 until req.weeks) {
                val start = req.start.plusWeeks(w.toLong())
                val week = plan.sessions.filter { it.date >= start && it.date <= start.plusDays(6) }
                assertEquals("Weekly session count $id week $w", expectedWeekly, week.size)
                for ((discipline, count) in listOf(
                    "run" to req.runDays, "str" to req.strengthDays,
                    "cal" to req.calDays, "cond" to req.conditioningDays)) {
                    assertEquals("Wrong $discipline count for $id week $w", count,
                        week.count { it.discipline == discipline })
                }
            }
            assertTrue("Long run/quality day mismatch in $id", plan.sessions.all {
                (it.kind != "long" || it.date.dayOfWeek.value == req.longDay) &&
                    (!it.quality || it.date.dayOfWeek.value in req.qualityDays)
            })
            if (req.weeklyKm == 0.0) {
                assertTrue("Invented mileage in $id",
                    plan.sessions.filter { it.discipline == "run" }.all { it.distanceKm == null })
            }
            if (req.experience == 0 || req.returning && req.qualityCount == 0) {
                assertTrue("Unexpected quality workout $id", plan.sessions.none { it.quality })
            }
            if (req.policyChoice == "multi-session") {
                assertTrue("Daily training budget exceeded $id", plan.sessions.groupBy { it.date }.all { (_, day) ->
                    day.sumOf { it.effectiveMinutes } <= req.dailyMinutes
                })
                assertFalse("Multi-session recovery conflict $id", plan.sessions.any { a ->
                    plan.sessions.any { b -> a.id != b.id && MultiSessionScheduling.conflict(a, b) }
                })
            } else {
                assertFalse("Cross-discipline recovery conflict $id", plan.sessions.any { a ->
                    plan.sessions.any { b -> a.id != b.id && Engine1.conflict(a, b) }
                })
            }
        }
    }

    @Test fun syntheticAthleteHistoriesHaveHonestDatesAndProviderDuplicateFixtures() {
        val fixture = fixture("synthetic_athlete_scenarios_v1.json")
        assertEquals(1, fixture.getInt("schemaVersion"))
        assertEquals("synthetic-only", fixture.getString("classification"))
        val scenarios = fixture.getJSONArray("scenarios")
        assertEquals(10, scenarios.length())
        val ids = HashSet<String>()
        val recordIds = HashSet<String>()
        val linkedPhysicalEvents = HashMap<String, MutableSet<String>>()
        for (i in 0 until scenarios.length()) {
            val s = scenarios.getJSONObject(i)
            assertTrue(ids.add(s.getString("id")))
            val period = s.getJSONObject("period")
            val from = LocalDate.parse(period.getString("start"))
            val until = LocalDate.parse(period.getString("end"))
            assertTrue(!until.isBefore(from))
            assertTrue(s.getJSONArray("requiredChecks").length() > 0)
            val records = s.getJSONArray("records")
            for (j in 0 until records.length()) {
                val r = records.getJSONObject(j)
                assertTrue("Duplicate synthetic record ID: ${r.getString("id")}",
                    recordIds.add(r.getString("id")))
                val date = LocalDate.parse(r.getString("date"))
                assertTrue("Record date outside synthetic history", date >= from && date <= until)
                assertTrue(r.getString("discipline") in setOf("run", "str", "cal", "cond"))
                val status = r.getString("status")
                assertTrue(status in setOf("recorded", "skipped", "planned"))
                if (status != "recorded") {
                    assertFalse("Unrecorded workout has fabricated actual duration", r.has("durationMinutes"))
                    assertFalse("Unrecorded workout has fabricated distance", r.has("distanceKm"))
                }
                if (r.has("physicalEventKey")) {
                    val key = s.getString("id") + "/" + r.getString("physicalEventKey")
                    linkedPhysicalEvents.getOrPut(key) { HashSet() }.add(r.getString("source"))
                }
            }
            val wellness = s.getJSONArray("wellness")
            for (j in 0 until wellness.length()) {
                val date = LocalDate.parse(wellness.getJSONObject(j).getString("date"))
                assertTrue(date >= from && date <= until)
            }
        }
        assertTrue("Fixture must exercise cross-provider duplicate identity",
            linkedPhysicalEvents.values.any { it.size >= 2 })
    }
}

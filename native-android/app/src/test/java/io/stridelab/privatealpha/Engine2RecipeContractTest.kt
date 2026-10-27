package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Engine 2.0 input recipes only: this does not implement or validate adaptive coaching. */
class Engine2RecipeContractTest {
    private fun fixture(name: String): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("engine/$name")) {
            "Missing engine fixture: $name"
        }
        return JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    @Test fun longitudinalRecipesPreserveVersionAndReviewRequirements() {
        val document = fixture("engine2_longitudinal_recipes_v2.json")
        assertEquals(2, document.getInt("schemaVersion"))
        assertEquals("synthetic-only", document.getString("classification"))
        val cases = document.getJSONArray("scenarios")
        val known = document.getJSONArray("invariantIds").let { a ->
            (0 until a.length()).map { a.getString(it) }.toSet()
        }
        assertEquals(64, cases.length())
        assertTrue(known.size >= 20)
        val ids = HashSet<String>()
        val dimensions = HashMap<String, Int>()
        for (i in 0 until cases.length()) {
            val scenario = cases.getJSONObject(i)
            val id = scenario.getString("id")
            assertTrue("Duplicate scenario $id", ids.add(id))
            assertTrue(scenario.getBoolean("fictional"))
            assertTrue(scenario.getInt("weeks") in 12..16)
            val dimension = scenario.getString("dimension")
            dimensions[dimension] = (dimensions[dimension] ?: 0) + 1
            val weeks = scenario.getInt("weeks")
            val sessions = scenario.getJSONObject("weeklySessions")
            val keys = setOf("run", "str", "cal", "cond")
            assertEquals(keys, sessions.keys().asSequence().toSet())
            for (key in keys) assertTrue("Invalid $key frequency in $id",
                sessions.getInt(key) in 0..7)
            val profile = scenario.getJSONObject("profile")
            if (profile.getString("device") == "none") {
                assertEquals("none", scenario.getString("wellnessCoverage"))
            }
            val missing = scenario.getJSONArray("missingWeeks")
            for (j in 0 until missing.length()) {
                assertTrue("Invalid missing week in $id", missing.getInt(j) in 0 until weeks)
            }
            val checks = scenario.getJSONArray("checks")
            assertTrue(checks.length() > 0)
            for (j in 0 until checks.length()) {
                assertTrue("Unknown check in $id", checks.getString(j) in known)
            }
            val events = scenario.getJSONArray("events")
            for (j in 0 until events.length()) {
                val event = events.getJSONObject(j)
                assertTrue(event.getInt("week") in 0 until weeks)
                assertTrue(event.getInt("day") in 0..6)
                assertTrue(event.getString("value").startsWith("Fictional event: "))
            }
        }
        assertEquals(8, dimensions.size)
        assertTrue(dimensions.values.all { it == 8 })
    }

    @Test fun literatureRegistryIsMetadataNotApprovedPrescriptionParameters() {
        val document = fixture("engine2_evidence_registry_v1.json")
        assertEquals(1, document.getInt("schemaVersion"))
        assertEquals("engineering-reference-only", document.getString("reviewStatus"))
        val sources = document.getJSONArray("sources")
        assertTrue(sources.length() >= 3)
        val ids = HashSet<String>()
        for (i in 0 until sources.length()) {
            val source = sources.getJSONObject(i)
            assertTrue(ids.add(source.getString("id")))
            assertTrue(source.getString("url").startsWith("https://"))
            assertTrue(source.getJSONArray("prohibitedUses").length() > 0)
            assertTrue(source.getString("scope").isNotBlank())
        }
    }
}

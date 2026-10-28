package io.stridelab.privatealpha

import java.io.File
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExerciseLibraryV2Test {
    private val assets: File get() = listOf(File("src/main/assets"), File("app/src/main/assets"),
        File("native-android/app/src/main/assets")).first { File(it, "native-library/catalog.json").exists() }
    private fun text(name: String) = File(assets, "native-library/$name").readText(Charsets.UTF_8)
    private fun load() = ExerciseLibraryV2.load { File(assets, it).inputStream() }
    private fun reject(change: (JSONObject) -> Unit) {
        val companion = JSONObject(text("exercise-library-v2.json")); change(companion)
        try {
            ExerciseLibraryV2.decode(text("catalog.json"), companion.toString(), text("exercise-media-v2.json"))
            fail("Corrupt companion accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun offlineLoaderOpensOnlyThreeBundledJsonFilesAndClosesStreams() {
        val opened = mutableListOf<String>(); var closed = 0
        val library = ExerciseLibraryV2.load { path ->
            opened += path
            object : java.io.ByteArrayInputStream(File(assets, path).readBytes()) {
                override fun close() { closed++; super.close() }
            }
        }
        assertEquals(listOf("native-library/catalog.json", "native-library/exercise-library-v2.json", "native-library/exercise-media-v2.json"), opened)
        assertEquals(3, closed); assertEquals(66, library.records.size)
        assertEquals(21, library.records.count { it.kind == "guide" })
    }

    @Test fun everyLegacyIdNameAndGuideStatusSurvives() {
        val library = load(); val old = JSONObject(text("catalog.json")).getJSONArray("entries")
        for (i in 0 until old.length()) {
            val entry = old.getJSONObject(i); val row = library.find(entry.getString("id"))!!
            assertEquals(entry.getString("n"), row.name)
            assertEquals(entry.optBoolean("guide", false), row.kind == "guide")
        }
        assertNull(library.find("nonexistent"))
    }

    @Test fun orderingAndDiscoveryStayDeterministicAcrossLocaleAndInputOrder() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val library = load(); val json = JSONObject(text("exercise-library-v2.json")); val rows = json.getJSONArray("records")
            val reversed = org.json.JSONArray(); for (i in rows.length()-1 downTo 0) reversed.put(rows.getJSONObject(i))
            json.put("records", reversed)
            val other = ExerciseLibraryV2.decode(text("catalog.json"), json.toString(), text("exercise-media-v2.json"))
            assertEquals(library.records, other.records)
            repeat(5) { assertEquals(library.discover("bodyweight", emptySet()), other.discover("bodyweight", emptySet())) }
        } finally { Locale.setDefault(original) }
    }

    @Test fun equipmentOptionsUseOrBetweenAndWithinAndUnknownEquipmentGrantsNothing() {
        val library = load()
        assertFalse(library.discover("strength_gym", setOf("band")).any { it.id == "br" })
        assertTrue(library.discover("strength_gym", setOf("band", "anchor")).any { it.id == "br" })
        assertFalse(library.discover("calisthenics", setOf("pullup_bar")).any { it.id == "ap" })
        assertTrue(library.discover("calisthenics", setOf("pullup_bar", "band")).any { it.id == "ap" })
        assertEquals(library.discover("bodyweight", emptySet()), library.discover("bodyweight", setOf("unknown")))
    }

    @Test fun guidesStayExcludedEvenWithAllEquipmentAndConfirmedPrerequisites() {
        val library = load(); val equipment = library.records.flatMap { it.equipmentOptions.flatten() }.toSet()
        val skills = library.records.map { it.id }.toSet()
        for (category in library.records.flatMap { it.categories }.distinct()) {
            assertTrue(library.discover(category, equipment, skills, "advanced").all { it.kind == "exercise" })
        }
        assertFalse(library.discover("calisthenics", equipment, skills, "advanced").any { it.id == "hand-wall" })
    }

    @Test fun mediaClaimsNeverTriggerNetworkOrDisplayUnverifiedArt() {
        val library = load()
        assertTrue(library.records.all { it.media.verifiedDisplayFile == null })
        assertEquals("placeholder", library.find("machine-leg-press")!!.media.status)
        assertEquals("inherited-unverified", library.find("e1")!!.media.status)
    }

    @Test fun duplicateAndDanglingIdsAreRejected() {
        reject { it.getJSONArray("records").put(it.getJSONArray("records").getJSONObject(0)) }
        reject { it.getJSONArray("records").getJSONObject(0).getJSONArray("alternatives").put("missing") }
    }

    @Test fun prerequisiteCycleAndGuidePromotionAreRejected() {
        reject {
            val rows=it.getJSONArray("records"); val a=rows.getJSONObject(0); val b=rows.getJSONObject(1)
            a.put("prerequisites", org.json.JSONArray().put(b.getString("id")))
            b.put("prerequisites", org.json.JSONArray().put(a.getString("id")))
        }
        reject {
            val rows=it.getJSONArray("records")
            for(i in 0 until rows.length()) if(rows.getJSONObject(i).getString("id")=="hand-wall") rows.getJSONObject(i).put("kind","exercise")
        }
    }

    @Test fun incompatibleVersionAndChangedBaselineFailClosed() {
        reject { it.put("schemaVersion", 3) }
        try {
            ExerciseLibraryV2.decode(text("catalog.json")+" ", text("exercise-library-v2.json"), text("exercise-media-v2.json"))
            fail("Changed catalog accepted")
        } catch (_: IllegalArgumentException) { }
        try { load().discover("bodyweight", emptySet(), maximumDifficulty="expert"); fail("Unknown tier accepted") }
        catch (_: IllegalArgumentException) { }
    }
}

package io.stridelab.privatealpha

import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Educational metadata, deliberately separate from prescriptions and Engine2's reviewed whitelist. */
internal data class ExerciseLibraryRecordV2(
    val id: String,
    val name: String,
    val kind: String,
    val categories: List<String>,
    val movementPatterns: List<String>,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    /** OR between options, AND within an option; an empty option means no equipment. */
    val equipmentOptions: List<Set<String>>,
    val setupNotes: List<String>,
    val difficulty: String,
    val prerequisites: Set<String>,
    val prerequisiteNotes: List<String>,
    val regressions: List<String>,
    val progressions: List<String>,
    val alternatives: List<String>,
    val technique: List<String>,
    val easier: String,
    val harder: String,
    val caveats: List<String>,
    val provenance: List<ExerciseTextProvenanceV2>,
    val media: ExerciseMediaV2
)

internal data class ExerciseTextProvenanceV2(val sourceId: String, val scope: String)
internal data class ExerciseLibrarySourceV2(val id: String, val title: String, val url: String, val scope: String)
/** No URL is fetched. Unverified legacy art is only discoverable by explicit opt-in lookup. */
internal data class ExerciseMediaV2(
    val status: String, val bundledFile: String?, val author: String?, val license: String?,
    val licenseUrl: String?, val verificationUrls: List<String>, val reason: String, val fit: String
) {
    val verifiedDisplayFile: String? get() = bundledFile.takeIf { status == "verified" }
}

internal class ExerciseLibraryV2 private constructor(
    val version: String,
    val records: List<ExerciseLibraryRecordV2>,
    val sources: List<ExerciseLibrarySourceV2>
) {
    private val byId = records.associateBy { it.id }
    fun find(id: String): ExerciseLibraryRecordV2? = byId[id]

    /**
     * Read-only discovery, NOT automatic selection, clinical clearance, or a dose recommendation.
     * The caller must intersect IDs with its own reviewed whitelist and retain its own skill gates.
     * Guide IDs are never returned. Order is stable ID order, independent of JSON input order.
     */
    fun discover(category: String, equipment: Set<String>, confirmedSkills: Set<String> = emptySet(),
                 maximumDifficulty: String = "foundation"): List<ExerciseLibraryRecordV2> {
        val maximum = levels.indexOf(maximumDifficulty)
        require(maximum >= 0) { "Unknown difficulty" }
        return records.filter { r ->
            r.kind == "exercise" && category in r.categories && levels.indexOf(r.difficulty) <= maximum &&
                r.equipmentOptions.any { equipment.containsAll(it) } && confirmedSkills.containsAll(r.prerequisites)
        }
    }

    companion object {
        private val levels = listOf("foundation", "developing", "advanced")
        private const val base = "25be8f3828374d1e539c7e825afa20fbcb652b56"
        private const val folder = "native-library/"

        /** Android usage: load { path -> context.assets.open(path) }. No Context, HTTP or persistence dependency. */
        fun load(openAsset: (String) -> InputStream): ExerciseLibraryV2 {
            fun text(path: String) = openAsset(folder + path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return decode(text("catalog.json"), text("exercise-library-v2.json"), text("exercise-media-v2.json"))
        }

        /** Fail closed on incompatible or corrupt companion data; the existing readLibrary path is unaffected. */
        fun decode(legacyText: String, libraryText: String, mediaText: String): ExerciseLibraryV2 {
            val legacy = JSONObject(legacyText)
            val library = JSONObject(libraryText)
            val manifest = JSONObject(mediaText)
            require(legacy.getInt("schema") == 1 && library.getInt("schemaVersion") == 2 && manifest.getInt("schemaVersion") == 2)
            require(library.getString("libraryVersion") == "2.0.0" && library.getString("baselineCommit") == base)
            val digest = MessageDigest.getInstance("SHA-256").digest(legacyText.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            require(digest == library.getString("baselineCatalogSha256")) { "Baseline catalog mismatch" }
            val sourceObjects = library.getJSONArray("sources").objectsV2()
            val sources = sourceObjects.map { s ->
                ExerciseLibrarySourceV2(s.requiredTextV2("id"), s.requiredTextV2("title"), s.requiredTextV2("url"), s.requiredTextV2("scope"))
            }
            require(sources.map { it.id }.distinct().size == sources.size)
            val mediaObjects = manifest.getJSONArray("assets").objectsV2()
            require(mediaObjects.map { it.getString("id") }.distinct().size == mediaObjects.size)
            require(mediaObjects.map { it.getString("exerciseId") }.distinct().size == mediaObjects.size)
            val media = mediaObjects.associateBy { it.getString("id") }
            val rows = library.getJSONArray("records").objectsV2()
            val records = rows.map { r ->
                val asset = requireNotNull(media[r.requiredTextV2("mediaId")]) { "Unknown media ID" }
                require(asset.getString("exerciseId") == r.getString("id"))
                val status = asset.requiredTextV2("status")
                require(status in setOf("placeholder", "inherited-unverified", "verified"))
                val file = asset.optionalTextV2("bundledFile")
                require(file == null || (file.startsWith(folder) && file.split('/').none { it == ".." } && !file.contains('\\')))
                if (status == "placeholder") require(file == null && asset.optionalTextV2("license") == null)
                if (status == "verified") require(file != null && asset.optionalTextV2("bundledSha256") != null &&
                    asset.optionalTextV2("author") != null && asset.optionalTextV2("license") != null &&
                    asset.optionalTextV2("licenseUrl") != null && asset.stringsV2("verificationUrls").isNotEmpty() &&
                    asset.getJSONObject("rightsReview").let { review ->
                        review.requiredTextV2("reviewedBy").isNotEmpty() && review.requiredTextV2("reviewedAt").isNotEmpty() &&
                            review.requiredTextV2("scope").isNotEmpty() && review.getBoolean("fitApproved")
                    })
                val options = r.getJSONArray("equipmentOptions")
                require(options.length() > 0)
                val equipment = (0 until options.length()).map { i -> options.getJSONArray(i).stringsV2().toSet() }
                val provenance = r.getJSONArray("provenance").objectsV2().map {
                    ExerciseTextProvenanceV2(it.requiredTextV2("sourceId"), it.requiredTextV2("scope"))
                }
                require(provenance.isNotEmpty() && provenance.all { p -> sources.any { it.id == p.sourceId } })
                ExerciseLibraryRecordV2(r.requiredTextV2("id"), r.requiredTextV2("name"), r.requiredTextV2("kind"),
                    r.stringsV2("categories"), r.stringsV2("movementPatterns"), r.stringsV2("primaryMuscles"), r.stringsV2("secondaryMuscles"),
                    equipment, r.stringsV2("setupNotes"), r.requiredTextV2("difficulty"), r.stringsV2("prerequisites").toSet(),
                    r.stringsV2("prerequisiteNotes"), r.stringsV2("regressions"), r.stringsV2("progressions"), r.stringsV2("alternatives"),
                    r.stringsV2("technique"), r.requiredTextV2("easier"), r.requiredTextV2("harder"), r.stringsV2("caveats"), provenance,
                    ExerciseMediaV2(status, file, asset.optionalTextV2("author"), asset.optionalTextV2("license"),
                        asset.optionalTextV2("licenseUrl"), asset.stringsV2("verificationUrls"), asset.requiredTextV2("reason"), asset.requiredTextV2("fit")))
            }.sortedBy { it.id }
            val by = records.associateBy { it.id }
            require(records.isNotEmpty() && by.size == records.size && media.size == records.size)
            require(records.map { it.name.lowercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit) }.distinct().size == records.size)
            for (r in records) {
                require(r.kind in setOf("guide", "exercise") && r.difficulty in levels)
                require(r.id.matches(Regex("[a-z][a-z0-9-]*")) && r.categories.isNotEmpty() && r.movementPatterns.isNotEmpty())
                require(r.primaryMuscles.isNotEmpty() && r.primaryMuscles.intersect(r.secondaryMuscles.toSet()).isEmpty())
                require(r.technique.size >= 2 && r.caveats.isNotEmpty() && r.prerequisiteNotes.isNotEmpty())
                val references = r.prerequisites + r.regressions + r.progressions + r.alternatives
                require(references.all { it in by && it != r.id }) { "Invalid exercise reference" }
                require(r.progressions.all { r.id in by.getValue(it).regressions })
                require(r.regressions.all { r.id in by.getValue(it).progressions })
            }
            fun checkGraph(edges: (ExerciseLibraryRecordV2) -> Collection<String>) {
                val visiting = mutableSetOf<String>(); val done = mutableSetOf<String>()
                fun visit(id: String) {
                    if (id in done) return
                    require(visiting.add(id)) { "Exercise graph cycle" }
                    edges(by.getValue(id)).forEach { visit(it) }
                    visiting.remove(id); done.add(id)
                }
                records.forEach { visit(it.id) }
            }
            checkGraph { it.prerequisites }; checkGraph { it.progressions }; checkGraph { it.regressions }
            for (old in legacy.getJSONArray("entries").objectsV2()) {
                val r = requireNotNull(by[old.getString("id")]) { "Missing legacy ID" }
                require(r.name == old.getString("n") && (r.kind == "guide") == old.optBoolean("guide", false))
                require(r.technique == old.stringsV2("tech") && r.easier == old.getString("reg") && r.harder == old.getString("prog"))
            }
            return ExerciseLibraryV2(library.getString("libraryVersion"), records, sources)
        }
    }
}

private fun JSONObject.requiredTextV2(key: String) = getString(key).also { require(it.isNotBlank()) }
private fun JSONObject.optionalTextV2(key: String): String? = if (isNull(key)) null else requiredTextV2(key)
private fun JSONObject.stringsV2(key: String) = getJSONArray(key).stringsV2()
private fun JSONArray.stringsV2(): List<String> = (0 until length()).map { getString(it).also { s -> require(s.isNotBlank()) } }
    .also { require(it.distinct().size == it.size) }
private fun JSONArray.objectsV2(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

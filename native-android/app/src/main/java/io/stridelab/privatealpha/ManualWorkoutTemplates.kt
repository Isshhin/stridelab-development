package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate

/** A reusable user-entered form, not a completed session or a coach prescription. */
internal data class ManualWorkoutTemplate(
    val id: String,
    val owner: String,
    val name: String,
    val discipline: ManualDiscipline,
    val exercises: List<ManualExerciseEntry>
)

internal object ManualWorkoutTemplateCodec {
    fun encode(template: ManualWorkoutTemplate): String {
        val sample = ManualDisciplineSession(
            template.id, template.owner, template.discipline, template.name,
            LocalDate.of(2000,1,1), template.exercises
        )
        // Reuse the same bounded exercise validation as a manually saved session.
        val checked = JSONObject(ManualDisciplineCodec.encode(sample))
        return JSONObject()
            .put("schema", 1)
            .put("kind", "manual-template")
            .put("id", template.id)
            .put("owner", template.owner)
            .put("name", template.name)
            .put("discipline", template.discipline.name)
            .put("exercises", checked.getJSONArray("exercises"))
            .toString()
    }

    fun decode(raw: String, owner: String): ManualWorkoutTemplate {
        require(raw.length <= 200_000)
        val obj = JSONObject(raw)
        require(obj.getInt("schema") == 1 && obj.getString("kind") == "manual-template")
        require(obj.getString("owner") == owner)
        val sample = JSONObject()
            .put("schema", 1)
            .put("source", "manual")
            .put("id", obj.getString("id"))
            .put("owner", owner)
            .put("discipline", obj.getString("discipline"))
            .put("title", obj.getString("name"))
            .put("performedOn", "2000-01-01")
            .put("notes", "")
            .put("exercises", obj.getJSONArray("exercises"))
        val checked = ManualDisciplineCodec.decode(sample.toString(), owner)
        return ManualWorkoutTemplate(checked.id, owner, checked.title,
            checked.discipline, checked.exercises)
    }
}

internal data class ManualDisciplineArchiveData(
    val sessions: List<ManualDisciplineSession>,
    val templates: List<ManualWorkoutTemplate>,
    val freeform: List<FreeformWorkout> = emptyList()
)

/** Schema 3 adds Freeform drafts and completions. Schemas 1 and 2 remain readable. */
internal object ManualDisciplineArchive {
    const val MAX_FILE_BYTES = 32_000_000
    private const val MAX_BODY_CHARS = 8_000_000
    private const val MAX_FREEFORM_RECORDS = 2000

    private fun hash(text: String) =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun export(owner: String, sessions: List<ManualDisciplineSession>,
        templates: List<ManualWorkoutTemplate>,
        freeform: List<FreeformWorkout> = emptyList()
    ): String {
        RecordPolicy.identifier(owner)
        require(sessions.size <= 2000 && templates.size <= 100)
        require(sessions.all { it.owner == owner } && templates.all { it.owner == owner })
        require(sessions.map { it.id }.distinct().size == sessions.size)
        require(templates.map { it.id }.distinct().size == templates.size)
        require(freeform.size <= MAX_FREEFORM_RECORDS && freeform.all { it.owner == owner })
        require(freeform.distinctBy { it.id }.size == freeform.size)
        val body = JSONObject()
            .put("schema", if (freeform.isEmpty()) 2 else 3).put("owner", owner)
            .put("records", JSONArray(sessions.map { JSONObject(ManualDisciplineCodec.encode(it)) }))
            .put("templates", JSONArray(templates.map { JSONObject(ManualWorkoutTemplateCodec.encode(it)) }))
            .apply {
                if (freeform.isNotEmpty()) {
                    put("freeform", JSONArray(freeform.map { JSONObject(FreeformCodec.encode(it)) }))
                }
            }.toString()
        require(body.length <= MAX_BODY_CHARS) { "The workout archive exceeds the supported size." }
        return JSONObject().put("data", body).put("sha256", hash(body)).toString().also {
            require(it.toByteArray(StandardCharsets.UTF_8).size <= MAX_FILE_BYTES)
        }
    }

    fun import(raw: String, owner: String): ManualDisciplineArchiveData {
        require(raw.length <= MAX_FILE_BYTES)
        val outer = JSONObject(raw)
        val body = outer.getString("data")
        require(body.length <= MAX_BODY_CHARS && hash(body) == outer.getString("sha256")) { "Archive checksum mismatch." }
        val json = JSONObject(body)
        require(json.getString("owner") == owner)
        if(json.getInt("schema") == 1) return ManualDisciplineArchiveData(
            ManualDisciplineBackup.import(raw,owner), emptyList()
        )
        val schema = json.getInt("schema")
        require(schema == 2 || schema == 3)
        val sessions = json.getJSONArray("records")
        val templates = json.getJSONArray("templates")
        require(sessions.length() <= 2000 && templates.length() <= 100)
        val saved = (0 until sessions.length()).map {
            ManualDisciplineCodec.decode(sessions.getJSONObject(it).toString(), owner)
        }
        val forms = (0 until templates.length()).map {
            ManualWorkoutTemplateCodec.decode(templates.getJSONObject(it).toString(), owner)
        }
        require(saved.map { it.id }.distinct().size == saved.size)
        require(forms.map { it.id }.distinct().size == forms.size)
        val freeform = if (schema == 3) {
            val rows = json.getJSONArray("freeform")
            require(rows.length() <= MAX_FREEFORM_RECORDS)
            List(rows.length()) { index ->
                FreeformCodec.decode(rows.getJSONObject(index).toString(), owner)
            }.also { require(it.distinctBy(FreeformWorkout::id).size == it.size) }
        } else {
            emptyList()
        }
        return ManualDisciplineArchiveData(saved, forms, freeform)
    }
}

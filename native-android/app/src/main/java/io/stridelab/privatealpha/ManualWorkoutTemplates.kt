package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/** A reusable user-entered form, not a completed session or a coach prescription. */
internal data class ManualWorkoutTemplate(
    val id: String,
    val owner: String,
    val name: String,
    val discipline: ManualDiscipline,
    val exercises: List<ManualExerciseEntry>,
    val recipe: WorkoutRecipe? = null
) {
    val structure: WorkoutRecipe
        get() = recipe ?: WorkoutRecipe(exercises = exercises.mapIndexed { index, entry ->
            FreeformExercise(id = "exercise-$index", name = entry.name, sets = List(entry.sets) { set ->
                FreeformSet(id = "set-$set", target = FreeformMeasure(
                    quantity = entry.reps?.takeIf { it > 0 }?.toString().orEmpty(),
                    seconds = entry.seconds?.toString().orEmpty(),
                    kg = entry.weightKg?.let(::freeformNumber).orEmpty()
                ))
            })
        })

    fun startWorkout(date: LocalDate, now: Long): FreeformWorkout {
        val recipe = structure
        return FreeformWorkout.start(owner, discipline, date, now).copy(
            templateId = id, workoutName = name, notes = recipe.notes, runType = recipe.runType,
            targetDistanceKm = recipe.distanceKm, targetDurationMinutes = recipe.durationMinutes,
            exercises = recipe.exercises.map { exercise ->
                exercise.copy(id = UUID.randomUUID().toString(), sets = exercise.sets.map { it.duplicate() })
            }
        )
    }

    fun editedFrom(editor: FreeformWorkout) = copy(
        name = editor.workoutName.trim(), exercises = emptyList(),
        recipe = WorkoutRecipe(editor.notes, editor.runType, editor.targetDistanceKm,
            editor.targetDurationMinutes, editor.exercises.map { it.targetsOnly(keepNotes = true) })
    )

    fun duplicate() = copy(id = UUID.randomUUID().toString(), name = "${name.take(95)} copy")

    companion object {
        fun empty(owner: String, discipline: ManualDiscipline) =
            ManualWorkoutTemplate(UUID.randomUUID().toString(), owner, discipline.label, discipline,
                emptyList(), WorkoutRecipe())

        fun fromWorkout(workout: FreeformWorkout): ManualWorkoutTemplate {
            require(workout.isComplete)
            return empty(workout.owner, workout.discipline).copy(
                name = workout.title,
                recipe = WorkoutRecipe(runType = workout.runType,
                    distanceKm = workout.targetDistanceKm, durationMinutes = workout.targetDurationMinutes,
                    exercises = workout.exercises.map { it.targetsOnly(keepNotes = false) })
            )
        }
    }
}

internal data class WorkoutRecipe(
    val notes: String = "",
    val runType: String = "",
    val distanceKm: String = "",
    val durationMinutes: String = "",
    val exercises: List<FreeformExercise> = emptyList()
)

private fun FreeformExercise.targetsOnly(keepNotes: Boolean) = copy(
    notes = notes.takeIf { keepNotes }.orEmpty(),
    sets = sets.map {
        it.copy(actual = FreeformMeasure(), completed = false, rpe = "", rir = "", notes = "")
    }
)

internal object ManualWorkoutTemplateCodec {
    fun encode(template: ManualWorkoutTemplate): String {
        if (template.recipe != null) return encodeRoutine(template, template.recipe)

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
        require(raw.length <= 1_000_000)
        val obj = JSONObject(raw)
        require(obj.getString("kind") == "manual-template" && obj.getString("owner") == owner)
        if (obj.getInt("schema") == 2) {
            return ManualWorkoutTemplate(
                obj.getString("id"), owner, obj.getString("name"),
                ManualDiscipline.valueOf(obj.getString("discipline")), emptyList(),
                WorkoutRecipe(obj.getString("notes"), obj.getString("runType"),
                    obj.getString("distanceKm"), obj.getString("durationMinutes"),
                    FreeformCodec.readExercises(obj.getJSONArray("exercises")))
            ).also { encode(it) }
        }
        require(obj.getInt("schema") == 1 && raw.length <= 200_000)
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
    private fun encodeRoutine(template: ManualWorkoutTemplate, recipe: WorkoutRecipe): String {
        RecordPolicy.identifier(template.id)
        RecordPolicy.identifier(template.owner)
        require(template.name.isNotBlank() && template.name.length <= 100) { "Enter a template name." }
        require(template.exercises.isEmpty())
        require(recipe.distanceKm.isBlank() || recipe.distanceKm.requiredDecimal(0.0, 500.0)) {
            "Enter a valid target distance."
        }
        require(recipe.durationMinutes.isBlank() || recipe.durationMinutes.requiredDecimal(0.0, 1440.0)) {
            "Enter a valid target duration."
        }
        require(recipe.exercises.flatMap { it.sets }.all { set ->
            set.target.isValid() && set.restSeconds.optionalInt(0..86_400) && set.targetRir.optionalInt(0..10)
        }) { "Check the set targets and rest times." }
        require(recipe.exercises.all { exercise ->
            exercise == exercise.targetsOnly(keepNotes = true) &&
                (exercise.blockMinutes.isBlank() || exercise.blockMinutes.requiredDecimal(0.0, 1440.0))
        }) { "Check the exercise targets and block time." }
        val validation = FreeformWorkout(template.id, template.owner, template.discipline, "", 1, 1,
            notes = recipe.notes, runType = recipe.runType, exercises = recipe.exercises,
            targetDistanceKm = recipe.distanceKm, targetDurationMinutes = recipe.durationMinutes)
        FreeformCodec.encode(validation)
        return JSONObject().put("schema", 2).put("kind", "manual-template")
            .put("id", template.id).put("owner", template.owner).put("name", template.name)
            .put("discipline", template.discipline.name).put("notes", recipe.notes).put("runType", recipe.runType)
            .put("distanceKm", recipe.distanceKm).put("durationMinutes", recipe.durationMinutes)
            .put("exercises", FreeformCodec.writeExercises(recipe.exercises))
            .toString().also { require(it.length <= 1_000_000) { "This template is too large." } }
    }
}

internal data class ManualDisciplineArchiveData(
    val sessions: List<ManualDisciplineSession>,
    val templates: List<ManualWorkoutTemplate>,
    val freeform: List<FreeformWorkout> = emptyList(),
    val schedules: List<CustomSchedule> = emptyList()
)

/** Schema 5 carries scheduled series and custom execution links alongside earlier manual data. */
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
        freeform: List<FreeformWorkout> = emptyList(),
        schedules: List<CustomSchedule> = emptyList()
    ): String {
        RecordPolicy.identifier(owner)
        require(sessions.size <= 2000 && templates.size <= 100)
        require(sessions.all { it.owner == owner } && templates.all { it.owner == owner })
        require(sessions.map { it.id }.distinct().size == sessions.size)
        require(templates.map { it.id }.distinct().size == templates.size)
        require(freeform.size <= MAX_FREEFORM_RECORDS && freeform.all { it.owner == owner })
        require(freeform.distinctBy { it.id }.size == freeform.size)
        require(schedules.size <= 2000 && schedules.all { it.owner == owner })
        require(schedules.distinctBy(CustomSchedule::id).size == schedules.size)
        val schema = when {
            schedules.isNotEmpty() || freeform.any { it.custom != null } -> 5
            templates.any { it.recipe != null } || freeform.any { it.templateId != null } -> 4
            freeform.isNotEmpty() -> 3
            else -> 2
        }
        val body = JSONObject()
            .put("schema", schema).put("owner", owner)
            .put("records", JSONArray(sessions.map { JSONObject(ManualDisciplineCodec.encode(it)) }))
            .put("templates", JSONArray(templates.map { JSONObject(ManualWorkoutTemplateCodec.encode(it)) }))
            .apply {
                if (schema >= 3) {
                    put("freeform", JSONArray(freeform.map { JSONObject(FreeformCodec.encode(it)) }))
                }
                if (schema >= 5) {
                    put("schedules", JSONArray(schedules.map { JSONObject(CustomScheduleCodec.encode(it)) }))
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
        require(schema in 2..5)
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
        require(schema >= 4 || forms.none { it.recipe != null })
        val freeform = if (schema >= 3) {
            val rows = json.getJSONArray("freeform")
            require(rows.length() <= MAX_FREEFORM_RECORDS)
            List(rows.length()) { index ->
                FreeformCodec.decode(rows.getJSONObject(index).toString(), owner)
            }.also { require(it.distinctBy(FreeformWorkout::id).size == it.size) }
        } else {
            emptyList()
        }
        require(schema >= 5 || freeform.none { it.custom != null })
        val schedules = if (schema >= 5) {
            val rows = json.getJSONArray("schedules")
            require(rows.length() <= 2000)
            List(rows.length()) { index ->
                CustomScheduleCodec.decode(rows.getJSONObject(index).toString(), owner)
            }.also { require(it.distinctBy(CustomSchedule::id).size == it.size) }
        } else emptyList()
        return ManualDisciplineArchiveData(saved, forms, freeform, schedules)
    }
}

package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

internal val loggableManualDisciplines: List<ManualDiscipline> = listOf(
    ManualDiscipline.STRENGTH,
    ManualDiscipline.CALISTHENICS,
    ManualDiscipline.PLYOMETRICS,
    ManualDiscipline.ISOMETRICS,
    ManualDiscipline.CONDITIONING
)

internal enum class ConditioningFormat(val label: String) {
    INTERVALS("Intervals"), EMOM("EMOM"), AMRAP("AMRAP")
}

internal data class FreeformMeasure(
    val quantity: String = "",
    val seconds: String = "",
    val kg: String = ""
) {
    fun isValid(): Boolean =
        quantity.optionalInt(1..10_000) && seconds.optionalInt(1..86_400) && kg.optionalDecimal(0.0..5_000.0)

    fun hasWork(discipline: ManualDiscipline): Boolean = when (discipline) {
        ManualDiscipline.RUNNING -> false
        ManualDiscipline.STRENGTH,
        ManualDiscipline.CALISTHENICS,
        ManualDiscipline.PLYOMETRICS,
        ManualDiscipline.ISOMETRICS,
        ManualDiscipline.CONDITIONING ->
            quantity.trim().toIntOrNull() != null || seconds.trim().toIntOrNull() != null
    }

    fun label(discipline: ManualDiscipline): String = listOfNotNull(
        quantity.trim().toIntOrNull()?.let {
            "$it ${if (discipline == ManualDiscipline.PLYOMETRICS) "contacts" else "reps"}"
        },
        seconds.trim().toIntOrNull()?.let { "$it s" },
        kg.asDecimal()?.let { "${freeformNumber(it)} kg" }
    ).joinToString(" · ")
}

internal data class FreeformSet(
    val id: String = UUID.randomUUID().toString(),
    val target: FreeformMeasure = FreeformMeasure(),
    val actual: FreeformMeasure = FreeformMeasure(),
    val restSeconds: String = "",
    val rpe: String = "",
    val rir: String = "",
    val notes: String = "",
    val completed: Boolean = false,
    val targetRir: String = ""
) {
    fun validActual(discipline: ManualDiscipline, format: ConditioningFormat = ConditioningFormat.INTERVALS) =
        actual.isValid() && (actual.hasWork(discipline) ||
            discipline == ManualDiscipline.CONDITIONING && format == ConditioningFormat.AMRAP)

    fun duplicate(): FreeformSet = copy(id = UUID.randomUUID().toString(), completed = false)
}

internal data class FreeformExercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val catalogId: String? = null,
    val sets: List<FreeformSet> = listOf(FreeformSet()),
    val notes: String = "",
    val format: ConditioningFormat = ConditioningFormat.INTERVALS,
    val blockMinutes: String = ""
)

internal data class FreeformWorkout(
    val id: String,
    val owner: String,
    val discipline: ManualDiscipline,
    val performedOn: String,
    val startedAt: Long,
    val modifiedAt: Long,
    val completedAt: Long? = null,
    val notes: String = "",
    val distanceKm: String = "",
    val durationMinutes: String = "",
    val effort: String = "",
    val runType: String = "",
    val exercises: List<FreeformExercise> = emptyList(),
    val templateId: String? = null,
    val workoutName: String = "",
    val targetDistanceKm: String = "",
    val targetDurationMinutes: String = "",
    val custom: CustomWorkoutBinding? = null
) {
    val isComplete: Boolean get() = completedAt != null
    val sourceLabel: String get() = if (custom == null) "Freeform" else "Custom"
    val date: LocalDate? get() = runCatching { LocalDate.parse(performedOn) }.getOrNull()
    val title: String get() = workoutName.trim().ifEmpty {
        if (discipline == ManualDiscipline.RUNNING && runType.isNotBlank()) runType.trim() else discipline.label
    }

    fun finish(now: Long): FreeformWorkout {
        check(!isComplete)
        require(date?.year?.let { it in 2000..2100 } == true) { "Enter a valid workout date (YYYY-MM-DD)." }
        require(effort.optionalInt(1..10)) { "Workout effort must be between 1 and 10." }
        if (discipline == ManualDiscipline.RUNNING) {
            require(distanceKm.requiredDecimal(0.0, 500.0)) { "Enter a distance greater than 0 km." }
            require(durationMinutes.requiredDecimal(0.0, 1440.0)) { "Enter a duration greater than 0 minutes." }
        } else {
            require(exercises.any { exercise -> exercise.sets.any { it.completed } }) {
                "Confirm at least one set or round."
            }
            for (exercise in exercises) {
                require(exercise.blockMinutes.optionalDecimal(0.0..1440.0) &&
                    (exercise.blockMinutes.isBlank() || exercise.blockMinutes.asDecimal()!! > 0)) {
                    "Enter a valid time cap for ${exercise.name}."
                }
                for (set in exercise.sets) {
                    require(set.target.isValid() && set.restSeconds.optionalInt(0..86_400) && set.targetRir.optionalInt(0..10)) {
                        "Check the target and rest values for ${exercise.name}."
                    }
                    if (set.completed) {
                        require(set.validActual(discipline, exercise.format)) { "Check the confirmed actuals for ${exercise.name}." }
                        require(set.rpe.optionalInt(1..10) && set.rir.optionalInt(0..10)) {
                            "Check the RPE (1–10) and RIR (0–10) for ${exercise.name}."
                        }
                    }
                }
            }
        }
        val finishedAt = maxOf(now, modifiedAt + 1)
        return copy(completedAt = finishedAt, modifiedAt = finishedAt, exercises = exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { set ->
                if (set.completed) set else set.copy(actual = FreeformMeasure(), rpe = "", rir = "")
            })
        })
    }

    fun summary(): String {
        if (discipline == ManualDiscipline.RUNNING) {
            return listOfNotNull(
                distanceKm.asDecimal()?.let { "${freeformNumber(it)} km" },
                durationMinutes.asDecimal()?.let { "${freeformNumber(it)} min" }
            ).joinToString(" · ")
        }
        val performed = exercises.flatMap { exercise ->
            exercise.sets.filter { it.completed && it.validActual(discipline, exercise.format) }
        }
        val quantity = performed.sumOf { it.actual.quantity.trim().toIntOrNull() ?: 0 }
        val seconds = performed.sumOf { it.actual.seconds.trim().toIntOrNull() ?: 0 }
        val volume = performed.sumOf { (it.actual.quantity.trim().toIntOrNull() ?: 0) * (it.actual.kg.asDecimal() ?: 0.0) }
        val unit = if (discipline == ManualDiscipline.CONDITIONING) "round" else "set"
        return buildList {
            add("${performed.size} $unit${if (performed.size == 1) "" else "s"}")
            if (quantity > 0) add("$quantity ${if (discipline == ManualDiscipline.PLYOMETRICS) "contacts" else "reps"}")
            if (seconds > 0) add(if (discipline == ManualDiscipline.CONDITIONING)
                "${freeformNumber(seconds / 60.0)} min work" else "$seconds s")
            if (volume > 0) {
                add("${freeformNumber(volume)} kg volume")
            }
        }.joinToString(" · ")
    }

    companion object {
        fun start(owner: String, discipline: ManualDiscipline, today: LocalDate, now: Long) =
            FreeformWorkout(UUID.randomUUID().toString(), owner, discipline, today.toString(), now, now)
    }
}

internal fun freeformNumber(value: Double): String =
    String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

private fun String.asDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
internal fun String.optionalInt(range: IntRange): Boolean = isBlank() || trim().toIntOrNull()?.let { it in range } == true
private fun String.optionalDecimal(range: ClosedFloatingPointRange<Double>): Boolean =
    isBlank() || asDecimal()?.let { it in range } == true
internal fun String.requiredDecimal(minExclusive: Double, max: Double): Boolean =
    asDecimal()?.let { it > minExclusive && it <= max } == true

internal object FreeformCodec {
    private const val MAX_ROW = 1_000_000
    const val SOURCE = "FREEFORM"
    private const val CUSTOM_SOURCE = "CUSTOM_PLANNED"

    fun encode(workout: FreeformWorkout): String {
        workout.validateStructure()
        if (workout.isComplete) {
            val completedAt = checkNotNull(workout.completedAt)
            val draft = workout.copy(completedAt = null, modifiedAt = completedAt - 1)
            require(draft.finish(completedAt) == workout)
        }
        return JSONObject()
            .put("schema", if (workout.custom == null) 1 else 2)
            .put("source", if (workout.custom == null) SOURCE else CUSTOM_SOURCE)
            .put("owner", workout.owner).put("id", workout.id).put("discipline", workout.discipline.name)
            .put("status", if (workout.isComplete) "COMPLETED" else "DRAFT")
            .put("performedOn", workout.performedOn).put("startedAt", workout.startedAt)
            .put("modifiedAt", workout.modifiedAt).put("completedAt", workout.completedAt)
            .put("notes", workout.notes).put("distanceKm", workout.distanceKm)
            .put("durationMinutes", workout.durationMinutes).put("effort", workout.effort)
            .put("runType", workout.runType)
            .put("templateId", workout.templateId)
            .put("workoutName", workout.workoutName.ifEmpty { null })
            .put("targetDistanceKm", workout.targetDistanceKm.ifEmpty { null })
            .put("targetDurationMinutes", workout.targetDurationMinutes.ifEmpty { null })
            .put("custom", workout.custom?.let { binding ->
                JSONObject().put("seriesId", binding.key.seriesId).put("anchor", binding.key.anchor.toString())
                    .put("date", binding.date.toString())
            })
            .put("exercises", writeExercises(workout.exercises))
            .toString().also { require(it.length <= MAX_ROW) { "This workout is too large to save." } }
    }

    fun writeExercises(exercises: List<FreeformExercise>): JSONArray =
        JSONArray(exercises.map { exercise ->
            JSONObject().put("id", exercise.id).put("name", exercise.name)
                .put("catalogId", exercise.catalogId).put("notes", exercise.notes)
                .put("format", exercise.format.name).put("blockMinutes", exercise.blockMinutes)
                .put("sets", JSONArray(exercise.sets.map { set ->
                    JSONObject().put("id", set.id).put("target", measure(set.target))
                        .put("actual", measure(set.actual)).put("restSeconds", set.restSeconds)
                        .put("rpe", set.rpe).put("rir", set.rir).put("notes", set.notes)
                        .put("completed", set.completed).put("targetRir", set.targetRir.ifEmpty { null })
                }))
        })

    fun decode(raw: String, owner: String): FreeformWorkout {
        require(raw.length <= MAX_ROW)
        val json = JSONObject(raw)
        val schema = json.getInt("schema")
        require(schema in 1..2 && json.getString("owner") == owner)
        require(json.getString("source") == if (schema == 1) SOURCE else CUSTOM_SOURCE)
        val custom = if (schema == 2) json.getJSONObject("custom").let { binding ->
            CustomWorkoutBinding(
                CustomOccurrenceKey(binding.getString("seriesId"), LocalDate.parse(binding.getString("anchor"))),
                LocalDate.parse(binding.getString("date"))
            )
        } else null
        val status = json.getString("status")
        val completed = if (json.isNull("completedAt")) null else json.getLong("completedAt")
        require(status == if (completed == null) "DRAFT" else "COMPLETED")
        return FreeformWorkout(
            id = json.getString("id"), owner = owner, discipline = ManualDiscipline.valueOf(json.getString("discipline")),
            performedOn = json.getString("performedOn"), startedAt = json.getLong("startedAt"),
            modifiedAt = json.getLong("modifiedAt"), completedAt = completed, notes = json.getString("notes"),
            distanceKm = json.getString("distanceKm"), durationMinutes = json.getString("durationMinutes"),
            effort = json.getString("effort"), runType = json.getString("runType"),
            templateId = if (json.isNull("templateId")) null else json.getString("templateId"),
            workoutName = json.optString("workoutName", ""),
            targetDistanceKm = json.optString("targetDistanceKm", ""),
            targetDurationMinutes = json.optString("targetDurationMinutes", ""),
            exercises = readExercises(json.getJSONArray("exercises")),
            custom = custom
        ).also { encode(it) }
    }

    fun readExercises(array: JSONArray): List<FreeformExercise> =
        objects(array).map { exercise ->
            FreeformExercise(
                id = exercise.getString("id"), name = exercise.getString("name"),
                catalogId = if (exercise.isNull("catalogId")) null else exercise.getString("catalogId"),
                notes = exercise.getString("notes"), format = ConditioningFormat.valueOf(exercise.getString("format")),
                blockMinutes = exercise.getString("blockMinutes"),
                sets = objects(exercise.getJSONArray("sets")).map { set ->
                    FreeformSet(
                        id = set.getString("id"), target = readMeasure(set.getJSONObject("target")),
                        actual = readMeasure(set.getJSONObject("actual")), restSeconds = set.getString("restSeconds"),
                        rpe = set.getString("rpe"), rir = set.getString("rir"), notes = set.getString("notes"),
                        completed = set.getBoolean("completed"), targetRir = set.optString("targetRir", "")
                    )
                }
            )
        }

    private fun measure(values: FreeformMeasure) =
        JSONObject().put("quantity", values.quantity).put("seconds", values.seconds).put("kg", values.kg)

    private fun readMeasure(json: JSONObject) =
        FreeformMeasure(json.getString("quantity"), json.getString("seconds"), json.getString("kg"))

    private fun objects(array: JSONArray): List<JSONObject> {
        require(array.length() <= 30)
        return List(array.length(), array::getJSONObject)
    }

    private fun FreeformWorkout.validateStructure() {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)
        templateId?.let(RecordPolicy::identifier)
        custom?.let { binding ->
            RecordPolicy.identifier(binding.key.seriesId)
            require(binding.key.anchor in customDateRange && binding.date in customDateRange)
            require(id == binding.key.workoutId && templateId != null)
        }
        require(workoutName.length <= 100)
        require(startedAt in 1 until Long.MAX_VALUE - 1 && modifiedAt in startedAt until Long.MAX_VALUE)
        require(completedAt == null || completedAt == modifiedAt)
        require(performedOn.length <= 32 && notes.length <= 2000 && runType.length <= 80)
        require(listOf(distanceKm, durationMinutes, effort, targetDistanceKm, targetDurationMinutes).all { it.length <= 32 })
        require(if (discipline == ManualDiscipline.RUNNING) exercises.isEmpty()
            else listOf(distanceKm, durationMinutes, runType, targetDistanceKm, targetDurationMinutes).all { it.isEmpty() })
        require(exercises.size <= 30 && exercises.distinctBy { it.id }.size == exercises.size)
        for (exercise in exercises) {
            RecordPolicy.identifier(exercise.id)
            exercise.catalogId?.let(RecordPolicy::identifier)
            require(exercise.name.isNotBlank() && exercise.name.length <= 100)
            require(exercise.notes.length <= 1000 && exercise.blockMinutes.length <= 32)
            require(discipline == ManualDiscipline.CONDITIONING ||
                (exercise.format == ConditioningFormat.INTERVALS && exercise.blockMinutes.isEmpty()))
            require(exercise.sets.size <= 30 && exercise.sets.distinctBy { it.id }.size == exercise.sets.size)
            for (set in exercise.sets) {
                RecordPolicy.identifier(set.id)
                require(set.notes.length <= 400)
                require(listOf(set.target.quantity, set.target.seconds, set.target.kg,
                    set.actual.quantity, set.actual.seconds, set.actual.kg,
                    set.restSeconds, set.rpe, set.rir, set.targetRir).all { it.length <= 32 })
            }
        }
    }
}

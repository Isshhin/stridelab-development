package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.round

internal enum class WorkoutEvidenceSource(val label: String) {
    ENGINE15("Coached"), CUSTOM_PLANNED("Custom"), FREEFORM("Freeform"), GARMIN_IMPORTED("Garmin")
}

internal sealed interface WorkoutEvidenceLink {
    data class Coached(val planId: String, val sessionId: String) : WorkoutEvidenceLink
    data class Manual(val id: String, val legacy: Boolean = false) : WorkoutEvidenceLink
    data class Imported(
        val id: String,
        val recordingIds: List<String>,
        val coached: List<Coached>
    ) : WorkoutEvidenceLink
}

internal data class ConfirmedWorkoutSet(
    val quantity: Int? = null,
    val seconds: Int? = null,
    val kg: Double? = null,
    val rpe: Int? = null,
    val rir: Int? = null
) {
    fun label(discipline: ManualDiscipline): String = listOfNotNull(
        quantity?.let { "$it ${if (discipline == ManualDiscipline.PLYOMETRICS) "contacts" else "reps"}" },
        seconds?.let { "$it s" },
        kg?.let { "${freeformNumber(it)} kg" }
    ).joinToString(" · ").ifEmpty { "Completed round" }
}

internal data class ConfirmedMovement(val name: String, val sets: List<ConfirmedWorkoutSet>)

internal data class WorkoutEvidence(
    val key: String,
    val source: WorkoutEvidenceSource,
    val link: WorkoutEvidenceLink,
    val date: LocalDate,
    val recordedAt: Long,
    val title: String,
    val discipline: ManualDiscipline?,
    val distanceKm: Double? = null,
    val durationMinutes: Double? = null,
    val effort: Int? = null,
    val movements: List<ConfirmedMovement> = emptyList(),
    val legacySets: Int? = null,
    val originLabel: String? = null,
    val avgPaceSecPerKm: Double? = null,
    val avgHrBpm: Double? = null,
    val movingTimeSeconds: Double? = null
) {
    val sources: Set<WorkoutEvidenceSource> get() =
        if (link is WorkoutEvidenceLink.Imported && link.coached.isNotEmpty()) {
            setOf(source, WorkoutEvidenceSource.ENGINE15)
        } else setOf(source)

    val sourceLabel: String get() =
        if (link is WorkoutEvidenceLink.Manual && link.legacy) "Manual"
        else sources.joinToString(" · ") { s ->
            if (s == WorkoutEvidenceSource.GARMIN_IMPORTED) originLabel?.takeIf { it.isNotBlank() } ?: s.label
            else s.label
        }

    val detail: String get() = evidenceMetrics(listOf(this), discipline).joinToString(" · ")
}

/** A read-only join over the currently visible activity sources and the active owner's workouts. */
internal fun projectWorkoutEvidence(
    owner: String?, plans: List<PlanSnapshot>, board: FreeformBoard?, activities: List<DisplayActivity>, zone: ZoneId
): List<WorkoutEvidence> {
    val coached = plans.filter { it.owner == owner && it.status != "draft" }.flatMap { plan ->
        plan.sessions.filter { it.isMarkedComplete }.map { plan.id to it }
    }.distinctBy { (planId, session) -> planId to session.id }
    val coachedByRecording = coached.mapNotNull { row -> row.second.linkedActivityId?.let { it to row } }
        .groupBy({ it.first }, { it.second })

    val claimedRecordingIds = mutableSetOf<String>()
    val linkedCoachKeys = mutableSetOf<Pair<String, String>>()
    val imported = activities.mapNotNull { group ->
        val recording = (listOf(group.primary) + group.additionalSources).firstOrNull {
            it.source.equals("garmin", ignoreCase = true) || it.source.equals("fit", ignoreCase = true)
        } ?: return@mapNotNull null
        val ids = (listOf(group.primary.id) + group.additionalSources.map { it.id }).distinct()
        if (ids.any { it in claimedRecordingIds }) return@mapNotNull null
        claimedRecordingIds.addAll(ids)
        val matches = ids.flatMap { coachedByRecording[it].orEmpty() }.distinctBy { (planId, session) ->
            planId to session.id
        }
        val references = matches.map { (planId, session) ->
            linkedCoachKeys.add(planId to session.id)
            WorkoutEvidenceLink.Coached(planId, session.id)
        }
        val matched = matches.singleOrNull()?.second
        val origin = when {
            recording.source.equals("garmin", ignoreCase = true) -> "Garmin"
            recording.source.equals("fit", ignoreCase = true) -> "FIT"
            else -> recording.sourceLabel.removeSuffix(" import").trim().ifBlank { "Garmin" }
        }
        val movingSec = (recording.movingTimeS ?: recording.durationS)?.takeIf { it.isFinite() && it > 0.0 }
        val distM = recording.distanceM?.takeIf { it.isFinite() && it > 0.0 }
        val avgPace = if (recording.supportsPace) {
            recording.recordedPace?.takeIf { it.isFinite() && it > 0.0 }
                ?: recording.garminMovingPaceSPerKm?.takeIf { it.isFinite() && it > 0.0 }
                ?: if (distM != null && distM >= 50.0 && movingSec != null) movingSec / (distM / 1000.0) else null
        } else null
        val avgHr = recording.averageHr?.takeIf { it.isFinite() && it > 0.0 }
        WorkoutEvidence(
            "import:${recording.id}", WorkoutEvidenceSource.GARMIN_IMPORTED,
            WorkoutEvidenceLink.Imported(recording.id, ids, references),
            recording.startedAt.atZone(zone).toLocalDate(), recording.startedAt.toEpochMilli(), recording.title,
            importedDiscipline(recording.type) ?: matched?.let(::coachedDiscipline),
            (recording.distanceM?.div(1000.0)).positive() ?: matched?.log?.distanceKm.positive(),
            (recording.durationS?.div(60.0)).positive() ?: matched?.log?.minutes.positive(),
            actualEffort(matched?.runResponse?.effort ?: matched?.log?.effort),
            matched?.let(::coachedMovements).orEmpty(),
            originLabel = origin,
            avgPaceSecPerKm = avgPace,
            avgHrBpm = avgHr,
            movingTimeSeconds = movingSec
        )
    }
    val standaloneCoached = coached.filterNot { (planId, session) -> (planId to session.id) in linkedCoachKeys }
        .map { (planId, session) ->
            val log = session.log
            WorkoutEvidence(
                "coach:${planId.length}:$planId${session.id}", WorkoutEvidenceSource.ENGINE15,
                WorkoutEvidenceLink.Coached(planId, session.id),
                log?.performedOn ?: log?.at?.atZone(zone)?.toLocalDate() ?: session.date,
                log?.at?.toEpochMilli() ?: 0, session.effectiveTitle, coachedDiscipline(session),
                log?.distanceKm.positive(), log?.minutes.positive(),
                actualEffort(session.runResponse?.effort ?: log?.effort), coachedMovements(session),
                movingTimeSeconds = log?.minutes?.takeIf { it.isFinite() && it > 0.0 }?.times(60.0)
            )
        }
    val logged = board?.workouts.orEmpty().filter { it.owner == owner && it.isComplete }.mapNotNull { workout ->
        val date = workout.date ?: return@mapNotNull null
        val durationMin = workout.durationMinutes.actualDecimal()
        WorkoutEvidence(
            "manual:${workout.id}",
            if (workout.custom == null) WorkoutEvidenceSource.FREEFORM else WorkoutEvidenceSource.CUSTOM_PLANNED,
            WorkoutEvidenceLink.Manual(workout.id), date, workout.completedAt ?: workout.modifiedAt,
            workout.title, workout.discipline,
            workout.distanceKm.actualDecimal(), durationMin, actualEffort(workout.effort.trim().toIntOrNull()),
            workout.exercises.mapNotNull { exercise ->
                val actual = exercise.sets.filter { it.completed }.map { set ->
                    ConfirmedWorkoutSet(
                        set.actual.quantity.trim().toIntOrNull()?.takeIf { it > 0 },
                        set.actual.seconds.trim().toIntOrNull()?.takeIf { it > 0 },
                        set.actual.kg.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 },
                        actualEffort(set.rpe.trim().toIntOrNull()), set.rir.trim().toIntOrNull()?.takeIf { it in 0..10 }
                    )
                }
                if (actual.isNotEmpty()) ConfirmedMovement(exercise.name, actual) else null
            },
            movingTimeSeconds = durationMin?.takeIf { it.isFinite() && it > 0.0 }?.times(60.0)
        )
    }
    val legacy = board?.legacy.orEmpty().filter { it.owner == owner }.map {
        WorkoutEvidence(
            "legacy:${it.id}", WorkoutEvidenceSource.FREEFORM, WorkoutEvidenceLink.Manual(it.id, legacy = true),
            it.performedOn, 0, it.title, it.discipline, legacySets = it.exercises.sumOf { exercise -> exercise.sets }
        )
    }
    return (imported + standaloneCoached + logged + legacy).distinctBy { it.key }
        .sortedWith(compareByDescending<WorkoutEvidence> { it.date }.thenByDescending { it.recordedAt }.thenBy { it.key })
}

private fun importedDiscipline(type: String): ManualDiscipline? {
    val normalized = type.lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")
    if (normalized.contains("plyometric")) return ManualDiscipline.PLYOMETRICS
    if (normalized.contains("isometric")) return ManualDiscipline.ISOMETRICS
    return TodayDiscipline.fromActivity(type)?.let { ManualDiscipline.valueOf(it.name) }
}

private fun coachedDiscipline(session: PlannedSession) =
    ManualDiscipline.entries.firstOrNull { it.trainingTrackKey == trainingTrack(session) }

private fun coachedMovements(session: PlannedSession): List<ConfirmedMovement> {
    val prescriptions = (session.exercises + session.effectiveExercises).associateBy { it.id }
    return session.log?.sets.orEmpty()
        .filter { (it.reps ?: 0) > 0 || (it.seconds ?: 0) > 0 }
        .groupBy { it.exerciseId }
        .map { (id, records) ->
            ConfirmedMovement(prescriptions[id]?.name ?: id, records.sortedBy { it.set }.map {
                ConfirmedWorkoutSet(it.reps?.takeIf { count -> count > 0 }, it.seconds?.takeIf { time -> time > 0 },
                    it.kg?.takeIf { weight -> weight.isFinite() && weight >= 0 })
            })
        }
}

internal fun evidenceMetrics(rows: List<WorkoutEvidence>, discipline: ManualDiscipline?): List<String> {
    check(rows.all { it.discipline == discipline })
    val actual = rows.flatMap { workout -> workout.movements.flatMap { it.sets } }
    val count = actual.size.toLong() + rows.sumOf { it.legacySets?.toLong() ?: 0L }
    val quantity = actual.sumOf { it.quantity?.toLong() ?: 0L }
    val seconds = actual.sumOf { it.seconds?.toLong() ?: 0L }
    val volume = actual.sumOf { (it.quantity ?: 0) * (it.kg ?: 0.0) }
    val distance = rows.mapNotNull { it.distanceKm }.sum()
    val time = rows.mapNotNull { it.durationMinutes }.sum()
    return buildList {
        if (distance > 0) add("${freeformNumber(distance)} km")
        if (time > 0) add("${freeformNumber(time)} min recorded")
        if (count > 0) {
            val unit = if (discipline == ManualDiscipline.CONDITIONING) "round" else "set"
            add("$count $unit${if (count == 1L) "" else "s"}")
        }
        if (quantity > 0) add("$quantity ${if (discipline == ManualDiscipline.PLYOMETRICS) "contacts" else "reps"}")
        if (seconds > 0) add(when (discipline) {
            ManualDiscipline.ISOMETRICS, ManualDiscipline.CALISTHENICS -> "$seconds s held"
            ManualDiscipline.CONDITIONING -> "${freeformNumber(seconds / 60.0)} min work"
            else -> "$seconds s recorded work"
        })
        if (volume > 0 && discipline in setOf(ManualDiscipline.STRENGTH, ManualDiscipline.CALISTHENICS)) {
            add("${freeformNumber(volume)} kg volume")
        } else {
            actual.mapNotNull { it.kg }.maxOrNull()?.takeIf { it > 0 }?.let {
                add("${freeformNumber(it)} kg max")
            }
        }
        val effort = rows.firstNotNullOfOrNull { it.effort ?: it.movements.asReversed()
            .firstNotNullOfOrNull { move -> move.sets.asReversed().firstNotNullOfOrNull { it.rpe } } }
        if (effort != null) add(if (rows.size == 1) "RPE $effort/10" else "Latest RPE $effort/10")
        val rir = rows.firstNotNullOfOrNull { row ->
            row.movements.asReversed().firstNotNullOfOrNull { move ->
                move.sets.asReversed().firstNotNullOfOrNull { it.rir }
            }
        }
        if (rir != null) add(if (rows.size == 1) "RIR $rir" else "Latest RIR $rir")
    }
}

internal data class ExerciseHistoryComparison(
    val name: String, val discipline: ManualDiscipline, val latestDate: LocalDate,
    val latest: ConfirmedWorkoutSet, val previousDate: LocalDate?, val previous: ConfirmedWorkoutSet?
)

internal fun exerciseComparisons(rows: List<WorkoutEvidence>): List<ExerciseHistoryComparison> {
    data class Occurrence(val date: LocalDate, val name: String, val discipline: ManualDiscipline, val set: ConfirmedWorkoutSet)
    val byMovement = rows.flatMap { row ->
        val discipline = row.discipline ?: return@flatMap emptyList()
        row.movements.distinctBy { it.name.trim().lowercase(Locale.ROOT) }.mapNotNull { exercise ->
            exercise.sets.lastOrNull()?.let { Occurrence(row.date, exercise.name, discipline, it) }
        }
    }.groupBy { it.discipline to it.name.trim().lowercase(Locale.ROOT) }
    return byMovement.values.map { logs ->
        val latest = logs.first()
        val previous = logs.getOrNull(1)
        ExerciseHistoryComparison(latest.name, latest.discipline, latest.date, latest.set, previous?.date, previous?.set)
    }
}

internal data class HistoryBaselineSuggestion(
    val weeklyKm: Double?, val longestKm: Double?, val loggedWeeks: Int, val undistancedRuns: Int
)

internal fun suggestedHistoryBaseline(rows: List<WorkoutEvidence>, today: LocalDate): HistoryBaselineSuggestion? {
    val monday = tracksideCalendarMonday(today)
    val starts = (1L..4L).map { monday.minusWeeks(it) }
    val runs = rows.filter {
        it.discipline == ManualDiscipline.RUNNING && it.date in monday.minusWeeks(4)..today
    }
    val weeks = runs.groupBy { tracksideCalendarMonday(it.date) }
    val distances = starts.mapNotNull { start ->
        val week = weeks[start].orEmpty()
        if (week.isEmpty() || week.any { it.distanceKm == null }) null
        else week.sumOf { requireNotNull(it.distanceKm) }
    }
    val longest = runs.mapNotNull { it.distanceKm }.maxOrNull()?.let { round(it * 10) / 10 }?.takeIf { it > 0.0 }
    val average = distances.takeIf { it.size >= 2 }?.let { round(it.average() * 10) / 10 }?.takeIf { it > 0.0 }
    if (runs.isEmpty()) return null
    return HistoryBaselineSuggestion(average, longest, distances.size, runs.count { it.distanceKm == null })
}

private fun Double?.positive(): Double? = this?.takeIf { it.isFinite() && it > 0 }
private fun String.actualDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull().positive()
private fun actualEffort(value: Int?): Int? = value?.takeIf { it in 1..10 }

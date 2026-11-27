package io.stridelab.privatealpha

import java.time.LocalDate

internal data class FreeformBoard(
    val workouts: List<FreeformWorkout> = emptyList(),
    val legacy: List<ManualDisciplineSession> = emptyList(),
    val templates: List<ManualWorkoutTemplate> = emptyList(),
    val schedules: List<CustomSchedule> = emptyList()
) {
    fun updated(workout: FreeformWorkout) = copy(
        workouts = (workouts.filterNot { it.id == workout.id } + workout).sortedByDescending { it.modifiedAt }
    )

    fun withTemplate(template: ManualWorkoutTemplate) = copy(
        templates = (templates.filterNot { it.id == template.id } + template)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    )

    fun withSchedule(schedule: CustomSchedule) =
        copy(schedules = schedules.filterNot { it.id == schedule.id } + schedule)

    fun customBetween(from: LocalDate, through: LocalDate): List<CustomCalendarItem> {
        val linked = workouts.filter { it.custom != null }.associateBy { requireNotNull(it.custom).key }
        val planned = schedules.flatMap { it.between(from, through) }.filterNot { it.key in linked }
            .map { CustomCalendarItem(it.key, it.date, it.template.name, it.template.discipline) }
        val inProgress = linked.values.filterNot { it.isComplete }.mapNotNull { workout ->
            val binding = requireNotNull(workout.custom)
            if (binding.date in from..through) {
                CustomCalendarItem(binding.key, binding.date, workout.title, workout.discipline, inProgress = true)
            } else null
        }
        return (planned + inProgress).sortedWith(compareBy<CustomCalendarItem> { it.date }
            .thenBy { it.title }.thenBy { it.key.workoutId })
    }

    fun without(id: String) = copy(workouts = workouts.filterNot { it.id == id })

    fun previous(discipline: ManualDiscipline, exercise: FreeformExercise): FreeformMeasure? =
        workouts.asSequence().filter {
            it.isComplete && if (discipline == ManualDiscipline.RUNNING) it.discipline == discipline
            else it.discipline != ManualDiscipline.RUNNING
        }
            .sortedWith(compareByDescending<FreeformWorkout> { it.date }.thenByDescending { it.completedAt })
            .firstNotNullOfOrNull { workout ->
                workout.exercises.firstNotNullOfOrNull { saved ->
                    val same = if (exercise.catalogId != null && saved.catalogId != null) {
                        exercise.catalogId == saved.catalogId
                    } else {
                        exercise.name.trim().equals(saved.name.trim(), ignoreCase = true)
                    }
                    if (same) saved.sets.lastOrNull { it.completed }?.actual else null
                }
            }
}

internal class FreeformAccess(
    val read: suspend (String) -> FreeformBoard,
    val start: suspend (FreeformWorkout) -> FreeformWorkout,
    val save: suspend (FreeformWorkout) -> FreeformWorkout,
    val finish: suspend (FreeformWorkout) -> FreeformWorkout,
    val discard: suspend (String, String) -> Boolean,
    val export: suspend (String) -> String,
    val restore: suspend (String, String) -> Int,
    val saveTemplate: suspend (ManualWorkoutTemplate, ManualWorkoutTemplate?) -> Unit,
    val deleteTemplate: suspend (String, String) -> Boolean,
    val saveSchedule: suspend (CustomSchedule) -> Unit,
    val changeSchedule: suspend (String, CustomOccurrenceKey, CustomScheduleEdit) -> CustomSchedule,
    val startCustom: suspend (String, CustomOccurrenceKey, LocalDate) -> FreeformWorkout
)

internal data class CustomCalendarItem(
    val key: CustomOccurrenceKey,
    val date: LocalDate,
    val title: String,
    val discipline: ManualDiscipline,
    val inProgress: Boolean = false
)

internal sealed interface CustomScheduleEdit {
    val expectedModifiedAt: Long

    data class One(val date: LocalDate?, override val expectedModifiedAt: Long) : CustomScheduleEdit
    data class Future(val rule: ScheduleRule?, override val expectedModifiedAt: Long) : CustomScheduleEdit
}

internal val ManualDiscipline.trainingTrackKey: String get() = when (this) {
    ManualDiscipline.RUNNING -> "run"
    ManualDiscipline.STRENGTH -> "str"
    ManualDiscipline.PLYOMETRICS -> "plyo"
    ManualDiscipline.ISOMETRICS -> "iso"
    ManualDiscipline.CALISTHENICS -> "cal"
    ManualDiscipline.CONDITIONING -> "cond"
}

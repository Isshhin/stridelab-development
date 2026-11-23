package io.stridelab.privatealpha

internal data class FreeformBoard(
    val workouts: List<FreeformWorkout> = emptyList(),
    val legacy: List<ManualDisciplineSession> = emptyList()
) {
    fun updated(workout: FreeformWorkout) = copy(
        workouts = (workouts.filterNot { it.id == workout.id } + workout).sortedByDescending { it.modifiedAt }
    )

    fun without(id: String) = copy(workouts = workouts.filterNot { it.id == id })

    fun previous(discipline: ManualDiscipline, exercise: FreeformExercise): FreeformMeasure? =
        workouts.asSequence().filter { it.isComplete && it.discipline == discipline }
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
    val restore: suspend (String, String) -> Int
)

internal val ManualDiscipline.trainingTrackKey: String get() = when (this) {
    ManualDiscipline.RUNNING -> "run"
    ManualDiscipline.STRENGTH -> "str"
    ManualDiscipline.PLYOMETRICS -> "plyo"
    ManualDiscipline.ISOMETRICS -> "iso"
    ManualDiscipline.CALISTHENICS -> "cal"
    ManualDiscipline.CONDITIONING -> "cond"
}

package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.roundToInt

internal data class ExercisePrRecord(
    val exerciseId: String,
    val exerciseName: String,
    val bestWeightKg: Double,
    val repsAtBestWeight: Int,
    val estimatedOneRmKg: Double,
    val achievedOn: LocalDate
)

internal data class MuscleVolumeEntry(
    val muscle: String,
    val label: String,
    val completedSets: Int
)

internal data class StrengthWeeklySummary(
    val weeklyVolume: List<MuscleVolumeEntry>,
    val topPrs: List<ExercisePrRecord>,
    val prsByExerciseId: Map<String, ExercisePrRecord>,
    val totalCompletedSets: Int,
    val totalVolumeKg: Double
)

internal object StrengthAnalyticsProjector {
    fun summarize(
        plans: List<PlanSnapshot>,
        freeformWorkouts: List<FreeformWorkout>,
        catalog: List<ExerciseLibraryRecordV2>,
        ownerId: String?,
        weekStart: LocalDate,
        today: LocalDate = LocalDate.now()
    ): StrengthWeeklySummary {
        val weekEnd = weekStart.plusDays(6)
        val muscleSets = LinkedHashMap<String, Int>()
        val prByKey = LinkedHashMap<String, ExercisePrRecord>()
        var totalCompletedSets = 0
        var totalVolumeKg = 0.0

        fun recordPr(
            key: String,
            name: String,
            weightKg: Double,
            reps: Int,
            date: LocalDate
        ) {
            if (!weightKg.isFinite() || weightKg <= 0.0 || reps <= 0) return
            val est1Rm = if (reps == 1) weightKg else weightKg * (1.0 + reps / 30.0)
            val prev = prByKey[key]
            if (prev == null || est1Rm > prev.estimatedOneRmKg ||
                (est1Rm == prev.estimatedOneRmKg && weightKg > prev.bestWeightKg)
            ) {
                prByKey[key] = ExercisePrRecord(
                    exerciseId = key,
                    exerciseName = name,
                    bestWeightKg = weightKg,
                    repsAtBestWeight = reps,
                    estimatedOneRmKg = est1Rm,
                    achievedOn = date
                )
            }
        }

        fun recordMuscles(exerciseId: String?, exerciseName: String, setsCount: Int) {
            if (setsCount <= 0) return
            val rec = MuscleFocusProjector.resolveRecord(exerciseId ?: "", exerciseName, catalog)
            val primary = rec?.primaryMuscles.orEmpty()
            if (primary.isEmpty()) return
            primary.forEach { muscle ->
                muscleSets[muscle] = (muscleSets[muscle] ?: 0) + setsCount
            }
        }

        val relevantPlans = plans.filter { ownerId == null || it.owner == ownerId }
        for (plan in relevantPlans) {
            for (session in plan.sessions) {
                if (session.discipline != "str" || session.status != "completed") continue
                val inWeek = session.date in weekStart..weekEnd
                val log = session.log ?: continue
                if (inWeek && log.sets.isNotEmpty()) {
                    totalCompletedSets += log.sets.size
                }
                val setsByEx = log.sets.groupBy { it.exerciseId }
                for (ex in session.exercises) {
                    val matchedSets = setsByEx[ex.id].orEmpty().ifEmpty { setsByEx[ex.name].orEmpty() }
                    if (inWeek) {
                        val count = if (matchedSets.isNotEmpty()) matchedSets.size else ex.sets
                        recordMuscles(ex.id, ex.name, count)
                    }
                    for (set in matchedSets) {
                        val w = set.kg ?: 0.0
                        val r = set.reps ?: 0
                        if (inWeek && w > 0.0 && r > 0) totalVolumeKg += w * r
                        recordPr(ex.id.ifBlank { ex.name.lowercase() }, ex.name, w, r, session.date)
                    }
                }
            }
        }

        val relevantFreeform = freeformWorkouts.filter {
            (ownerId == null || it.owner == ownerId) &&
                it.completedAt != null &&
                it.discipline == ManualDiscipline.STRENGTH
        }
        for (workout in relevantFreeform) {
            val date = workout.date ?: today
            val inWeek = date in weekStart..weekEnd
            for (ex in workout.exercises) {
                val doneSets = ex.sets.filter { it.completed }
                if (inWeek && doneSets.isNotEmpty()) {
                    totalCompletedSets += doneSets.size
                    recordMuscles(ex.catalogId, ex.name, doneSets.size)
                }
                for (set in doneSets) {
                    val w = set.actual.kg.trim().toDoubleOrNull() ?: 0.0
                    val r = set.actual.quantity.trim().toIntOrNull() ?: 0
                    if (inWeek && w > 0.0 && r > 0) totalVolumeKg += w * r
                    val key = ex.catalogId ?: ex.name.lowercase()
                    recordPr(key, ex.name, w, r, date)
                }
            }
        }

        val volumeList = muscleSets.entries
            .sortedByDescending { it.value }
            .map { (muscle, count) ->
                MuscleVolumeEntry(
                    muscle = muscle,
                    label = ExerciseMuscleCatalog.formatPrimaryTag(listOf(muscle)) ?: muscle,
                    completedSets = count
                )
            }

        val topPrs = prByKey.values
            .sortedByDescending { it.estimatedOneRmKg }
            .take(4)

        return StrengthWeeklySummary(
            weeklyVolume = volumeList,
            topPrs = topPrs,
            prsByExerciseId = prByKey,
            totalCompletedSets = totalCompletedSets,
            totalVolumeKg = (totalVolumeKg * 10.0).roundToInt() / 10.0
        )
    }
}

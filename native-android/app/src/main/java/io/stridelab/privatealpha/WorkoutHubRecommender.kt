package io.stridelab.privatealpha

internal data class WorkoutHubRecommendation(
    val workout: CuratedWorkout,
    val reason: String
)

internal object WorkoutHubRecommender {
    fun recommend(
        library: List<CuratedWorkout>,
        readiness: BiometricReadinessSnapshot?,
        plannedSessionsToday: List<PlannedSession>,
        plannedSessionsTomorrow: List<PlannedSession>,
        recentFreeform: List<FreeformWorkout>,
        savedEquipment: Set<String>,
        limit: Int = 3
    ): List<WorkoutHubRecommendation> {
        if (library.isEmpty() || limit <= 0) return emptyList()

        val recentTemplateIds = recentFreeform.take(5).mapNotNull { it.templateId }.toSet()
        val hasHighStrain = readiness != null &&
            (readiness.sustainedAutonomicStrain || readiness.strainSignalCount >= 2)
        val hasMildStrain = readiness != null && readiness.strainSignalCount == 1
        val hasKeySessionTomorrow = plannedSessionsTomorrow.any { session ->
            val kind = "${session.kind} ${session.title}".lowercase()
            session.quality || "interval" in kind || "tempo" in kind || "threshold" in kind || "long" in kind || "heavy" in kind
        }
        val isRestDayToday = plannedSessionsToday.isEmpty()
        val availableGear = mapSavedEquipment(savedEquipment)

        val scored = library.mapNotNull { workout ->
            if (!isEquipmentCompatible(workout, availableGear)) return@mapNotNull null

            var score = 0
            val reason: String

            if (hasHighStrain) {
                score += when (workout.category) {
                    WorkoutHubCategory.RECOVERY -> 90
                    WorkoutHubCategory.MOBILITY -> 75
                    WorkoutHubCategory.ISOMETRICS -> 55
                    else -> if (workout.intensity == WorkoutIntensity.LOW) 30 else -50
                }
                reason = "High recovery strain detected from recent biometrics"
            } else if (hasKeySessionTomorrow) {
                score += when {
                    workout.category == WorkoutHubCategory.MOBILITY -> 80
                    workout.category == WorkoutHubCategory.RECOVERY -> 70
                    workout.category == WorkoutHubCategory.ISOMETRICS -> 65
                    workout.intensity == WorkoutIntensity.HIGH -> -40
                    workout.category == WorkoutHubCategory.PLYOMETRICS -> -30
                    else -> 35
                }
                reason = "Light prep ahead of tomorrow's key session"
            } else if (hasMildStrain) {
                score += when (workout.intensity) {
                    WorkoutIntensity.LOW -> 65
                    WorkoutIntensity.MODERATE -> 45
                    WorkoutIntensity.HIGH -> -20
                }
                reason = "Balanced effort for today's readiness"
            } else if (isRestDayToday) {
                score += when (workout.category) {
                    WorkoutHubCategory.MOBILITY -> 70
                    WorkoutHubCategory.RECOVERY -> 65
                    WorkoutHubCategory.ISOMETRICS -> 60
                    WorkoutHubCategory.CONDITIONING -> 40
                    WorkoutHubCategory.STRENGTH -> 35
                    WorkoutHubCategory.CALISTHENICS -> 35
                    else -> 25
                }
                reason = "Optional session for an open training day"
            } else {
                score += when (workout.category) {
                    WorkoutHubCategory.MOBILITY -> 65
                    WorkoutHubCategory.ISOMETRICS -> 55
                    WorkoutHubCategory.PLYOMETRICS -> 50
                    WorkoutHubCategory.CALISTHENICS -> 45
                    WorkoutHubCategory.STRENGTH -> 40
                    else -> 35
                }
                reason = "Complements today's scheduled training"
            }

            if ("curated-${workout.id}" in recentTemplateIds) {
                score -= 25
            }
            if (workout.durationMinutes in 12..35) {
                score += 10
            }

            Triple(workout, score, reason)
        }

        return scored
            .sortedByDescending { it.second }
            .distinctBy { it.first.category }
            .take(limit)
            .map { WorkoutHubRecommendation(it.first, it.third) }
    }

    private fun isEquipmentCompatible(
        workout: CuratedWorkout,
        availableGear: Set<WorkoutEquipment>
    ): Boolean {
        if (WorkoutEquipment.NONE in workout.equipment && workout.equipment.size == 1) return true
        if (availableGear.isEmpty()) {
            return WorkoutEquipment.NONE in workout.equipment ||
                workout.equipment.all { it == WorkoutEquipment.DUMBBELLS || it == WorkoutEquipment.BANDS }
        }
        return workout.equipment.all { it == WorkoutEquipment.NONE || it in availableGear }
    }

    private fun mapSavedEquipment(savedEquipment: Set<String>): Set<WorkoutEquipment> {
        if (savedEquipment.isEmpty()) return emptySet()
        val out = mutableSetOf(WorkoutEquipment.NONE)
        savedEquipment.forEach { raw ->
            val key = raw.lowercase()
            if ("dumbbell" in key) out += WorkoutEquipment.DUMBBELLS
            if ("barbell" in key) out += WorkoutEquipment.BARBELL
            if ("pull" in key || "bar" in key) out += WorkoutEquipment.PULL_UP_BAR
            if ("band" in key) out += WorkoutEquipment.BANDS
            if ("kettlebell" in key) out += WorkoutEquipment.KETTLEBELL
            if ("bench" in key) out += WorkoutEquipment.BENCH
        }
        return out
    }
}

package io.stridelab.privatealpha

import java.util.Locale

internal enum class BodySide(val label: String) {
    FRONT("FRONT"),
    BACK("BACK")
}

internal enum class AnatomicalMuscleRegion(
    val side: BodySide,
    val displayName: String
) {
    NECK_FRONT(BodySide.FRONT, "Neck"),
    UPPER_TRAPS_FRONT(BodySide.FRONT, "Traps"),
    FRONT_DELTS(BodySide.FRONT, "Anterior Delts"),
    LATERAL_DELTS_FRONT(BodySide.FRONT, "Lateral Delts"),
    CHEST(BodySide.FRONT, "Chest"),
    BICEPS(BodySide.FRONT, "Biceps"),
    FOREARMS_FRONT(BodySide.FRONT, "Forearms"),
    SERRATUS(BodySide.FRONT, "Serratus"),
    ABS(BodySide.FRONT, "Abs"),
    OBLIQUES(BodySide.FRONT, "Obliques"),
    HIP_FLEXORS(BodySide.FRONT, "Hip Flexors"),
    ADDUCTORS(BodySide.FRONT, "Adductors"),
    QUADS(BodySide.FRONT, "Quads"),
    SHINS(BodySide.FRONT, "Shins"),
    FEET_ANKLES_FRONT(BodySide.FRONT, "Feet & Ankles"),

    NECK_BACK(BodySide.BACK, "Neck"),
    UPPER_TRAPS_BACK(BodySide.BACK, "Upper Traps"),
    REAR_DELTS(BodySide.BACK, "Rear Delts"),
    LATERAL_DELTS_BACK(BodySide.BACK, "Lateral Delts"),
    TRICEPS(BodySide.BACK, "Triceps"),
    FOREARMS_BACK(BodySide.BACK, "Forearms"),
    MID_UPPER_BACK(BodySide.BACK, "Mid & Upper Back"),
    LATS(BodySide.BACK, "Lats"),
    LOWER_BACK(BodySide.BACK, "Lower Back"),
    GLUTE_MED(BodySide.BACK, "Glute Med"),
    GLUTES(BodySide.BACK, "Glutes"),
    HAMSTRINGS(BodySide.BACK, "Hamstrings"),
    CALVES(BodySide.BACK, "Calves"),
    FEET_ANKLES_BACK(BodySide.BACK, "Feet & Ankles")
}

internal data class MuscleFocusMap(
    val regionIntensities: Map<AnatomicalMuscleRegion, Float>,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val setCount: Int = 0
) {
    val isEmpty: Boolean get() = regionIntensities.isEmpty()

    companion object {
        val EMPTY = MuscleFocusMap(emptyMap(), emptyList(), emptyList(), 0)
    }
}

/**
 * Pure read-only muscle region mapper & focus projector.
 * Maps 100% of audited ExerciseMuscleCatalog canonical keys without mutating plans or schemas.
 */
internal object MuscleFocusProjector {

    private val keyToRegions: Map<String, List<AnatomicalMuscleRegion>> = mapOf(
        "abdominals" to listOf(AnatomicalMuscleRegion.ABS),
        "adductors" to listOf(AnatomicalMuscleRegion.ADDUCTORS),
        "anterior_deltoid" to listOf(AnatomicalMuscleRegion.FRONT_DELTS),
        "calves" to listOf(AnatomicalMuscleRegion.CALVES),
        "elbow_flexors" to listOf(AnatomicalMuscleRegion.BICEPS),
        "foot_stabilizers" to listOf(AnatomicalMuscleRegion.FEET_ANKLES_FRONT, AnatomicalMuscleRegion.FEET_ANKLES_BACK),
        "forearm_grip" to listOf(AnatomicalMuscleRegion.FOREARMS_FRONT, AnatomicalMuscleRegion.FOREARMS_BACK),
        "gluteus_maximus" to listOf(AnatomicalMuscleRegion.GLUTES),
        "gluteus_medius" to listOf(AnatomicalMuscleRegion.GLUTE_MED),
        "hamstrings" to listOf(AnatomicalMuscleRegion.HAMSTRINGS),
        "hip_flexors" to listOf(AnatomicalMuscleRegion.HIP_FLEXORS),
        "lateral_deltoid" to listOf(AnatomicalMuscleRegion.LATERAL_DELTS_FRONT, AnatomicalMuscleRegion.LATERAL_DELTS_BACK),
        "lateral_trunk_muscles" to listOf(AnatomicalMuscleRegion.OBLIQUES, AnatomicalMuscleRegion.LOWER_BACK),
        "latissimus_dorsi" to listOf(AnatomicalMuscleRegion.LATS),
        "lower_trapezius" to listOf(AnatomicalMuscleRegion.MID_UPPER_BACK),
        "neck_rotators" to listOf(AnatomicalMuscleRegion.NECK_FRONT, AnatomicalMuscleRegion.NECK_BACK),
        "obliques" to listOf(AnatomicalMuscleRegion.OBLIQUES),
        "pectoralis_major" to listOf(AnatomicalMuscleRegion.CHEST),
        "posterior_deltoid" to listOf(AnatomicalMuscleRegion.REAR_DELTS),
        "quadriceps" to listOf(AnatomicalMuscleRegion.QUADS),
        "rhomboids" to listOf(AnatomicalMuscleRegion.MID_UPPER_BACK),
        "serratus_anterior" to listOf(AnatomicalMuscleRegion.SERRATUS),
        "shoulder_stabilizers" to listOf(
            AnatomicalMuscleRegion.FRONT_DELTS,
            AnatomicalMuscleRegion.REAR_DELTS,
            AnatomicalMuscleRegion.MID_UPPER_BACK
        ),
        "spinal_extensors" to listOf(AnatomicalMuscleRegion.LOWER_BACK),
        "tibialis_anterior" to listOf(AnatomicalMuscleRegion.SHINS),
        "trapezius" to listOf(AnatomicalMuscleRegion.UPPER_TRAPS_FRONT, AnatomicalMuscleRegion.UPPER_TRAPS_BACK),
        "triceps" to listOf(AnatomicalMuscleRegion.TRICEPS),
        "trunk_rotators" to listOf(AnatomicalMuscleRegion.OBLIQUES, AnatomicalMuscleRegion.LOWER_BACK),
        "trunk_stabilizers" to listOf(
            AnatomicalMuscleRegion.ABS,
            AnatomicalMuscleRegion.OBLIQUES,
            AnatomicalMuscleRegion.LOWER_BACK
        )
    )

    private val legacyIdAliases: Map<String, String> = mapOf(
        "cable-latpulldown" to "cable-lat-pulldown",
        "bb-bench" to "bp",
        "bb-row" to "dr",
        "hollow" to "core-hollow",
        "hollow-tuck" to "core-tuck",
        "core-hollow" to "core-hollow",
        "deadbug" to "core-deadbug",
        "supported-hang" to "pull-hang",
        "supported-scapular" to "pull-scap",
        "pike-support" to "hand-pike",
        "prone-w" to "rh"
    )

    fun regionsForKey(muscleKey: String): List<AnatomicalMuscleRegion> =
        keyToRegions[muscleKey.trim().lowercase(Locale.ROOT)].orEmpty()

    fun resolveRecord(
        exerciseId: String?,
        fallbackName: String? = null,
        catalog: List<ExerciseLibraryRecordV2>
    ): ExerciseLibraryRecordV2? {
        if (catalog.isEmpty()) return null
        val normalizedId = exerciseId?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedId != null) {
            val baseId = when {
                normalizedId.startsWith("swap-") && normalizedId.endsWith("-regression") ->
                    normalizedId.removePrefix("swap-").removeSuffix("-regression")
                normalizedId.startsWith("swap-") && normalizedId.endsWith("-progression") ->
                    normalizedId.removePrefix("swap-").removeSuffix("-progression")
                else -> normalizedId
            }
            val candidateIds = listOfNotNull(
                normalizedId,
                baseId,
                legacyIdAliases[normalizedId],
                legacyIdAliases[baseId]
            ).distinct()
            for (candidate in candidateIds) {
                catalog.firstOrNull { it.id == candidate }?.let { return it }
            }
        }
        val cleanName = fallbackName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return catalog.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
    }

    fun forMuscles(
        primaryKeys: List<String>,
        secondaryKeys: List<String>,
        setCount: Int = 0
    ): MuscleFocusMap {
        val intensities = linkedMapOf<AnatomicalMuscleRegion, Float>()
        secondaryKeys.forEach { key ->
            regionsForKey(key).forEach { region ->
                intensities[region] = maxOf(intensities[region] ?: 0f, 0.35f)
            }
        }
        primaryKeys.forEach { key ->
            regionsForKey(key).forEach { region ->
                intensities[region] = 1.0f
            }
        }
        if (intensities.isEmpty()) return MuscleFocusMap.EMPTY
        val primaryLabels = primaryKeys.map { ExerciseMuscleCatalog.formatLabel(it) }.distinct()
        val secondaryLabels = secondaryKeys
            .filterNot { it in primaryKeys }
            .map { ExerciseMuscleCatalog.formatLabel(it) }
            .distinct()
        return MuscleFocusMap(
            regionIntensities = intensities,
            primaryMuscles = primaryLabels,
            secondaryMuscles = secondaryLabels,
            setCount = setCount
        )
    }

    fun forExercise(
        exerciseId: String?,
        fallbackName: String? = null,
        catalog: List<ExerciseLibraryRecordV2>
    ): MuscleFocusMap {
        val record = resolveRecord(exerciseId, fallbackName, catalog) ?: return MuscleFocusMap.EMPTY
        return forMuscles(record.primaryMuscles, record.secondaryMuscles)
    }

    fun forPlannedSession(
        exercises: List<ExercisePrescription>,
        catalog: List<ExerciseLibraryRecordV2>
    ): MuscleFocusMap {
        if (exercises.isEmpty()) return MuscleFocusMap.EMPTY
        val items = exercises.map { it to it.sets.coerceAtLeast(1) }
        val totalSets = exercises.sumOf { it.sets.coerceAtLeast(0) }
        return aggregateWeighted(
            weightedRecords = items.mapNotNull { (ex, weight) ->
                resolveRecord(ex.id, ex.name, catalog)?.let { it to weight.toFloat() }
            },
            setCount = totalSets
        )
    }

    fun forConfirmedSession(
        recordedSets: List<SetRecord>,
        exercises: List<ExercisePrescription> = emptyList(),
        catalog: List<ExerciseLibraryRecordV2>
    ): MuscleFocusMap {
        val byId = exercises.associateBy { it.id }
        val validSets = recordedSets.filter { (it.reps ?: 0) > 0 || (it.seconds ?: 0) > 0 }
        if (validSets.isEmpty()) return MuscleFocusMap.EMPTY
        val weighted = validSets.mapNotNull { set ->
            val fallbackName = byId[set.exerciseId]?.name
            resolveRecord(set.exerciseId, fallbackName, catalog)?.let { it to 1f }
        }
        return aggregateWeighted(weighted, setCount = validSets.size)
    }

    fun forConfirmedExecution(
        execution: WorkoutExecution,
        catalog: List<ExerciseLibraryRecordV2>
    ): MuscleFocusMap {
        // Strict boundary: exclude SKIPPED and PENDING unconfirmed partial holds.
        val confirmedSets = execution.sets.filter { it.isConfirmed }
        if (confirmedSets.isEmpty()) return MuscleFocusMap.EMPTY
        val weighted = confirmedSets.mapNotNull { set ->
            resolveRecord(set.target.id, set.target.name, catalog)?.let { it to 1f }
        }
        return aggregateWeighted(weighted, setCount = confirmedSets.size)
    }

    fun forConfirmedFreeformWorkout(
        workout: FreeformWorkout,
        catalog: List<ExerciseLibraryRecordV2>
    ): MuscleFocusMap {
        if (workout.discipline == ManualDiscipline.RUNNING) return MuscleFocusMap.EMPTY
        val weighted = mutableListOf<Pair<ExerciseLibraryRecordV2, Float>>()
        var confirmedCount = 0
        for (exercise in workout.exercises) {
            val doneSets = exercise.sets.count { it.completed }
            if (doneSets <= 0) continue
            confirmedCount += doneSets
            val record = resolveRecord(exercise.catalogId, exercise.name, catalog) ?: continue
            weighted += record to doneSets.toFloat()
        }
        return aggregateWeighted(weighted, setCount = confirmedCount)
    }

    private fun aggregateWeighted(
        weightedRecords: List<Pair<ExerciseLibraryRecordV2, Float>>,
        setCount: Int
    ): MuscleFocusMap {
        if (weightedRecords.isEmpty()) return MuscleFocusMap.EMPTY
        val regionScores = linkedMapOf<AnatomicalMuscleRegion, Float>()
        val muscleScores = linkedMapOf<String, Float>()
        val primaryKeyScores = linkedMapOf<String, Float>()

        for ((record, setsWeight) in weightedRecords) {
            val primaryWeight = 1.0f * setsWeight
            val secondaryWeight = 0.4f * setsWeight
            for (key in record.primaryMuscles) {
                muscleScores[key] = (muscleScores[key] ?: 0f) + primaryWeight
                primaryKeyScores[key] = (primaryKeyScores[key] ?: 0f) + primaryWeight
                for (region in regionsForKey(key)) {
                    regionScores[region] = (regionScores[region] ?: 0f) + primaryWeight
                }
            }
            for (key in record.secondaryMuscles) {
                muscleScores[key] = (muscleScores[key] ?: 0f) + secondaryWeight
                for (region in regionsForKey(key)) {
                    regionScores[region] = (regionScores[region] ?: 0f) + secondaryWeight
                }
            }
        }

        val maxRegion = regionScores.values.maxOrNull()?.takeIf { it > 0f } ?: return MuscleFocusMap.EMPTY
        val normalizedRegions = regionScores.mapValues { (_, score) ->
            (score / maxRegion).coerceIn(0f, 1f)
        }
        val maxMuscle = muscleScores.values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val sortedMuscles = muscleScores.entries.sortedByDescending { it.value }
        val primaryThreshold = maxMuscle * 0.55f
        val primaryKeys = sortedMuscles
            .filter { (key, score) -> score >= primaryThreshold || (primaryKeyScores[key] ?: 0f) >= maxMuscle * 0.4f }
            .take(4)
            .map { it.key }
            .ifEmpty { sortedMuscles.take(2).map { it.key } }
        val secondaryKeys = sortedMuscles
            .map { it.key }
            .filterNot { it in primaryKeys }
            .take(5)

        return MuscleFocusMap(
            regionIntensities = normalizedRegions,
            primaryMuscles = primaryKeys.map { ExerciseMuscleCatalog.formatLabel(it) },
            secondaryMuscles = secondaryKeys.map { ExerciseMuscleCatalog.formatLabel(it) },
            setCount = setCount
        )
    }
}

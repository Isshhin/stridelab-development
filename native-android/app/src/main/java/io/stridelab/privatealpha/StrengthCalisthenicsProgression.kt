package io.stridelab.privatealpha

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Per-exercise measurement contract decoupled from workout discipline. */
internal enum class ExerciseMetricType {
    REPS,
    WEIGHT_AND_REPS,
    HOLD_SECONDS,
    WEIGHT_AND_HOLD
}

private val weightedEquipmentKeywords = listOf(
    "dumbbell", "barbell", "kettlebell", "weight", "plate", "vest", "cable", "machine", "band"
)

private val bodyweightEquipmentExact = setOf(
    "", "none", "no equipment", "bodyweight", "mat", "mat / floor", "floor",
    "bar", "pull-up bar", "rings", "wall", "bodyweight / floor or solid wall",
    "elevated sturdy bench or box", "bench or box / floor"
)

internal fun ExercisePrescription.metricType(discipline: String): ExerciseMetricType {
    val eq = equipment.trim().lowercase(Locale.ROOT)
    val looksWeighted = weightedEquipmentKeywords.any { it in eq } && eq !in bodyweightEquipmentExact
    if (seconds != null) {
        return if (looksWeighted) ExerciseMetricType.WEIGHT_AND_HOLD else ExerciseMetricType.HOLD_SECONDS
    }
    if (discipline == "cal" && !looksWeighted) return ExerciseMetricType.REPS
    return if (eq in bodyweightEquipmentExact || (!looksWeighted && discipline != "str")) {
        ExerciseMetricType.REPS
    } else {
        ExerciseMetricType.WEIGHT_AND_REPS
    }
}

private val bodyweightFixtureEquipment = setOf(
    "pullup_bar", "rings", "parallel_bars", "bench", "box", "hurdles", "pad", "rope", "ropes", "wall", "floor", "mat"
)

internal fun ExerciseLibraryRecordV2.metricType(): ExerciseMetricType {
    val text = (name + " " + movementPatterns.joinToString(" ")).lowercase(Locale.ROOT)
    val isHold = "hold" in categories || "isometrics" in categories || "isometric" in categories ||
        "plank" in text || "hold" in text || "wall sit" in text || "hang" in text
    val bodyweightOnly = equipmentOptions.all { option ->
        option.all { eq -> eq.lowercase(Locale.ROOT) in bodyweightFixtureEquipment }
    }
    return when {
        isHold && bodyweightOnly -> ExerciseMetricType.HOLD_SECONDS
        isHold -> ExerciseMetricType.WEIGHT_AND_HOLD
        bodyweightOnly -> ExerciseMetricType.REPS
        else -> ExerciseMetricType.WEIGHT_AND_REPS
    }
}

internal data class CompletedSetEvidence(
    val reps: Int? = null,
    val kg: Double? = null,
    val seconds: Int? = null,
    val rir: Int? = null
)

internal data class ExerciseSessionEvidence(
    val exerciseId: String,
    val performedOn: LocalDate,
    val sets: List<CompletedSetEvidence>
)

/**
 * Reads confirmed set evidence strictly by canonical exercise ID (never by slot index),
 * preventing ad-hoc exercise swaps from polluting another movement's progression history.
 */
internal object ExerciseProgressionHistoryReader {
    fun latestForExercise(
        exerciseId: String,
        owner: String,
        plans: List<PlanSnapshot>,
        freeformWorkouts: List<FreeformWorkout> = emptyList(),
        beforeDate: LocalDate? = null,
        extraCompletedSets: List<Pair<LocalDate, CompletedSetEvidence>> = emptyList()
    ): ExerciseSessionEvidence? {
        val candidates = mutableListOf<ExerciseSessionEvidence>()

        plans.asSequence()
            .filter { it.owner == owner }
            .flatMap { it.sessions.asSequence() }
            .filter { it.status == "completed" && it.log != null }
            .forEach { session ->
                val log = session.log ?: return@forEach
                val sessionDate = log.performedOn ?: session.date
                if (beforeDate != null && sessionDate.isAfter(beforeDate)) return@forEach
                val rir = parseSessionRir(log.notes)
                val matching = log.sets
                    .filter { it.exerciseId == exerciseId && ((it.reps ?: 0) > 0 || (it.seconds ?: 0) > 0) }
                    .sortedBy { it.set }
                    .map { CompletedSetEvidence(reps = it.reps, kg = it.kg, seconds = it.seconds, rir = rir) }
                if (matching.isNotEmpty()) {
                    candidates += ExerciseSessionEvidence(exerciseId, sessionDate, matching)
                }
            }

        freeformWorkouts.asSequence()
            .filter { it.owner == owner && it.isComplete }
            .forEach { workout ->
                val date = workout.date ?: return@forEach
                if (beforeDate != null && date.isAfter(beforeDate)) return@forEach
                workout.exercises
                    .filter { it.catalogId == exerciseId }
                    .forEach { exercise ->
                        val matching = exercise.sets
                            .filter { it.completed }
                            .mapNotNull { set ->
                                val reps = set.actual.quantity.trim().toIntOrNull()
                                val seconds = set.actual.seconds.trim().toIntOrNull()
                                val kg = set.actual.kg.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                                val rir = set.rir.trim().toIntOrNull()?.takeIf { it in 0..10 }
                                if ((reps ?: 0) > 0 || (seconds ?: 0) > 0) {
                                    CompletedSetEvidence(reps = reps, kg = kg, seconds = seconds, rir = rir)
                                } else null
                            }
                        if (matching.isNotEmpty()) {
                            candidates += ExerciseSessionEvidence(exerciseId, date, matching)
                        }
                    }
            }

        extraCompletedSets
            .groupBy { it.first }
            .forEach { (date, items) ->
                if (beforeDate == null || !date.isAfter(beforeDate)) {
                    val sets = items.map { it.second }.filter { (it.reps ?: 0) > 0 || (it.seconds ?: 0) > 0 }
                    if (sets.isNotEmpty()) candidates += ExerciseSessionEvidence(exerciseId, date, sets)
                }
            }

        return candidates.maxByOrNull { it.performedOn }
    }

    private val rirPattern = Regex("""\bRIR\s*[:=]?\s*([0-9]|10)\b""", RegexOption.IGNORE_CASE)
    private fun parseSessionRir(notes: String): Int? =
        rirPattern.find(notes)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

internal enum class WeightUnit(val defaultIncrementKg: Double, val label: String) {
    METRIC(2.5, "kg"),
    IMPERIAL_LB(5.0, "lb")
}

internal enum class ProgressionDecision {
    SESSION_ONE_EFFORT,
    CLIMB_REPS,
    BUMP_LOAD,
    HOLD_TARGET
}

internal data class StrengthLoadOption(
    val load: Double,
    val targetReps: Int,
    val label: String
)

internal data class StrengthExerciseProjection(
    val exerciseId: String,
    val isSessionOne: Boolean,
    val targetSets: Int,
    val repRange: IntRange,
    val targetRir: IntRange,
    val summary: String,
    val previousSummary: String?,
    val options: List<StrengthLoadOption>,
    val decision: ProgressionDecision
)

/**
 * Pure read-only Strength load & double-progression projector:
 * - Session 1 (no history): effort-anchored guidance, never guesses loads.
 * - Session 2+: double progression within repRange + bounded Epley e1RM (strictly 3-10 reps).
 */
internal object StrengthProgressionProjector {
    private const val KG_TO_LB = 2.20462

    fun project(
        exercise: ExercisePrescription,
        discipline: String,
        evidence: ExerciseSessionEvidence?,
        unit: WeightUnit = WeightUnit.METRIC,
        increment: Double = unit.defaultIncrementKg,
        targetRir: IntRange = 2..3
    ): StrengthExerciseProjection {
        val repRange = defaultRepRange(exercise)
        val metricType = exercise.metricType(discipline)
        if (evidence == null || evidence.sets.isEmpty() || metricType == ExerciseMetricType.HOLD_SECONDS) {
            return StrengthExerciseProjection(
                exerciseId = exercise.id,
                isSessionOne = true,
                targetSets = exercise.sets,
                repRange = repRange,
                targetRir = targetRir,
                summary = "${exercise.sets} × ${repRange.first}–${repRange.last} @ ${targetRir.first}–${targetRir.last} RIR",
                previousSummary = null,
                options = emptyList(),
                decision = ProgressionDecision.SESSION_ONE_EFFORT
            )
        }

        val workingSets = evidence.sets.filter { (it.reps ?: 0) > 0 }
        val prevSummary = formatPrevious(workingSets, unit)
        val workingLoadKg = workingSets.mapNotNull { it.kg?.takeIf { k -> k > 0.0 } }.maxOrNull()
        val repsList = workingSets.mapNotNull { it.reps }
        val minReps = repsList.minOrNull() ?: repRange.first
        val maxReps = repsList.maxOrNull() ?: repRange.first
        val minRir = workingSets.mapNotNull { it.rir }.minOrNull()

        val missedBottom = workingSets.size < exercise.sets || minReps < repRange.first || minRir == 0
        val cleanCeiling = !missedBottom && repsList.all { it >= repRange.last } && (minRir == null || minRir >= 1)

        if (workingLoadKg == null) {
            val decision = when {
                missedBottom -> ProgressionDecision.HOLD_TARGET
                cleanCeiling -> ProgressionDecision.BUMP_LOAD
                else -> ProgressionDecision.CLIMB_REPS
            }
            val nextReps = when (decision) {
                ProgressionDecision.HOLD_TARGET -> maxReps.coerceIn(repRange.first, repRange.last)
                ProgressionDecision.BUMP_LOAD -> repRange.first
                ProgressionDecision.CLIMB_REPS -> (minReps + 1).coerceAtMost(repRange.last)
                ProgressionDecision.SESSION_ONE_EFFORT -> repRange.first
            }
            return StrengthExerciseProjection(
                exerciseId = exercise.id,
                isSessionOne = false,
                targetSets = exercise.sets,
                repRange = repRange,
                targetRir = targetRir,
                summary = "${exercise.sets} × $nextReps reps @ ${targetRir.first}–${targetRir.last} RIR",
                previousSummary = prevSummary,
                options = emptyList(),
                decision = decision
            )
        }

        val stepDisplay = increment.coerceAtLeast(0.5)
        val workingDisplay = if (unit == WeightUnit.IMPERIAL_LB) snapWeight(workingLoadKg * KG_TO_LB, stepDisplay)
        else snapWeight(workingLoadKg, stepDisplay)

        val (decision, primaryLoad, primaryReps) = when {
            missedBottom -> Triple(
                ProgressionDecision.HOLD_TARGET,
                workingDisplay,
                maxReps.coerceIn(repRange.first, repRange.last)
            )
            cleanCeiling -> Triple(
                ProgressionDecision.BUMP_LOAD,
                snapWeight(workingDisplay + stepDisplay, stepDisplay),
                repRange.first
            )
            else -> Triple(
                ProgressionDecision.CLIMB_REPS,
                workingDisplay,
                (minReps + 1).coerceAtMost(repRange.last)
            )
        }

        val qualifyingE1RmKg = workingSets
            .mapNotNull { set ->
                val kg = set.kg ?: return@mapNotNull null
                val reps = set.reps ?: return@mapNotNull null
                estimateE1Rm(kg, reps, set.rir)
            }
            .maxOrNull()

        val optionA = StrengthLoadOption(
            load = primaryLoad,
            targetReps = primaryReps,
            label = "${formatWeight(primaryLoad)} ${unit.label} × $primaryReps"
        )

        val options = buildList {
            add(optionA)
            if (qualifyingE1RmKg != null) {
                val e1RmDisplay = if (unit == WeightUnit.IMPERIAL_LB) qualifyingE1RmKg * KG_TO_LB else qualifyingE1RmKg
                val altReps = (primaryReps - 3).coerceIn(3, repRange.last)
                if (altReps != primaryReps) {
                    val rawAltLoad = e1RmDisplay / (1.0 + (altReps + targetRir.first) / 30.0)
                    val snappedAlt = snapWeight(
                        rawAltLoad.coerceAtLeast(primaryLoad + stepDisplay),
                        stepDisplay
                    )
                    if (abs(snappedAlt - primaryLoad) >= 0.25) {
                        add(
                            StrengthLoadOption(
                                load = snappedAlt,
                                targetReps = altReps,
                                label = "${formatWeight(snappedAlt)} ${unit.label} × $altReps"
                            )
                        )
                    }
                }
            }
        }

        val summary = "Target: ${options.joinToString(" or ") { it.label }}"
        return StrengthExerciseProjection(
            exerciseId = exercise.id,
            isSessionOne = false,
            targetSets = exercise.sets,
            repRange = repRange,
            targetRir = targetRir,
            summary = summary,
            previousSummary = prevSummary,
            options = options,
            decision = decision
        )
    }

    /** Epley e1RM strictly restricted to 3-10 hard working reps; warm-ups (<3) and endurance sets (>10) return null. */
    fun estimateE1Rm(weightKg: Double, reps: Int, rir: Int? = null): Double? {
        if (!weightKg.isFinite() || weightKg <= 0.0) return null
        if (reps !in 3..10) return null
        val effectiveReps = reps + (rir?.coerceIn(0, 4) ?: 0)
        return weightKg * (1.0 + effectiveReps / 30.0)
    }

    fun snapWeight(raw: Double, increment: Double = 2.5): Double {
        val step = increment.coerceAtLeast(0.5)
        return ((raw / step).roundToInt() * step).coerceAtLeast(step)
    }

    private fun defaultRepRange(exercise: ExercisePrescription): IntRange {
        val base = exercise.reps ?: return 8..10
        return when {
            base <= 6 -> maxOf(4, base - 1)..(base + 2)
            base <= 10 -> base..(base + 2).coerceAtLeast(10)
            else -> base..(base + 3)
        }
    }

    private fun formatPrevious(sets: List<CompletedSetEvidence>, unit: WeightUnit): String {
        val best = sets.maxWithOrNull(compareBy<CompletedSetEvidence> { it.kg ?: 0.0 }.thenBy { it.reps ?: 0 })
            ?: return ""
        val reps = best.reps ?: return ""
        val kg = best.kg?.takeIf { it > 0.0 }
        return if (kg != null) {
            val disp = if (unit == WeightUnit.IMPERIAL_LB) snapWeight(kg * KG_TO_LB, unit.defaultIncrementKg) else kg
            "Prev: ${formatWeight(disp)} ${unit.label} × $reps"
        } else {
            "Prev: $reps reps"
        }
    }

    private fun formatWeight(value: Double): String =
        if (abs(value - value.roundToInt()) < 0.05) value.roundToInt().toString()
        else String.format(Locale.US, "%.1f", value)
}

internal data class ProgressionUpgradeSuggestion(
    val currentExerciseId: String,
    val currentName: String,
    val suggestedExercise: ExercisePrescription,
    val reason: String,
    val resetTarget: String
)

/**
 * Tracks confirmed rep & timed-hold working sets; emits opt-in progression suggestions
 * when all working sets cleanly reach the movement ceiling and ExerciseAlternatives.harder exists.
 */
internal object CalisthenicsProgressionProjector {
    fun suggestUpgrade(
        exercise: ExercisePrescription,
        discipline: String,
        evidence: ExerciseSessionEvidence?
    ): ProgressionUpgradeSuggestion? {
        if (evidence == null || evidence.sets.size < exercise.sets) return null
        val next = ExerciseAlternatives.harder(exercise) ?: return null
        val metricType = exercise.metricType(discipline)
        val workingSets = evidence.sets.take(exercise.sets)
        if (workingSets.any { it.rir == 0 }) return null

        return when (metricType) {
            ExerciseMetricType.HOLD_SECONDS, ExerciseMetricType.WEIGHT_AND_HOLD -> {
                val ceilingSec = maxOf(45, exercise.seconds ?: 45)
                if (workingSets.all { (it.seconds ?: 0) >= ceilingSec }) {
                    val reset = next.seconds?.let { "${next.sets} × ${it} s" }
                        ?: "${next.sets} × ${next.reps ?: 8} reps"
                    ProgressionUpgradeSuggestion(
                        currentExerciseId = exercise.id,
                        currentName = exercise.name,
                        suggestedExercise = next,
                        reason = "All ${exercise.sets} sets reached $ceilingSec+ sec cleanly.",
                        resetTarget = reset
                    )
                } else null
            }
            ExerciseMetricType.REPS, ExerciseMetricType.WEIGHT_AND_REPS -> {
                val ceilingReps = maxOf(10, exercise.reps ?: 10)
                if (workingSets.all { (it.reps ?: 0) >= ceilingReps }) {
                    val reset = next.seconds?.let { "${next.sets} × ${it} s" }
                        ?: "${next.sets} × ${next.reps ?: 6} reps"
                    ProgressionUpgradeSuggestion(
                        currentExerciseId = exercise.id,
                        currentName = exercise.name,
                        suggestedExercise = next,
                        reason = "All ${exercise.sets} sets reached $ceilingReps+ clean reps.",
                        resetTarget = reset
                    )
                } else null
            }
        }
    }
}

/**
 * Standardized snake_case anatomical muscle taxonomy matching ExerciseLibraryV2 for
 * lightweight picker tags today and the follow-up Anatomical Muscle Map.
 */
internal object ExerciseMuscleCatalog {
    val STANDARD_MUSCLES: Set<String> = setOf(
        "abdominals", "adductors", "anterior_deltoid", "calves", "elbow_flexors",
        "foot_stabilizers", "forearm_grip", "gluteus_maximus", "gluteus_medius",
        "hamstrings", "hip_flexors", "lateral_deltoid", "lateral_trunk_muscles", "latissimus_dorsi",
        "lower_trapezius", "neck_rotators", "obliques", "pectoralis_major",
        "posterior_deltoid", "quadriceps", "rhomboids", "serratus_anterior",
        "shoulder_stabilizers", "spinal_extensors", "tibialis_anterior", "trapezius",
        "triceps", "trunk_rotators", "trunk_stabilizers"
    )

    fun formatLabel(muscleKey: String): String =
        muscleKey.split('_').joinToString(" ") { part ->
            part.replaceFirstChar { ch -> ch.uppercase(Locale.ROOT) }
        }

    fun formatPrimaryTag(primaryMuscles: List<String>, maxCount: Int = 2): String? =
        primaryMuscles.take(maxCount)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ") { formatLabel(it) }
}

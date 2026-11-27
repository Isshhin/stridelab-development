package io.stridelab.privatealpha

import java.time.LocalDate
import java.util.Collections
import java.util.TreeMap

/**
 * Per-muscle unified local exposure summary across Strength, Freeform, Calisthenics,
 * and Conditioning (Track 6 §6 / MD-06 / MOD-B4).
 *
 * Notice:
 * - [directHardSets] and [indirectHardSets] stay strictly separated in Tier A.
 * - [shadowFractionalSets] (`direct + 0.5 * indirect`, Pelland 2025/2026) is tagged Tier B
 *   shadow-only and never drives hard Tier A gating.
 */
internal data class MuscleGroupExposure(
    val muscleId: String,
    val directHardSets: Int = 0,
    val indirectHardSets: Int = 0,
    val isometricHoldSeconds: Int = 0,
    val eccentricFocusedSets: Int = 0,
    val plyometricContacts: Int = 0,
    val highRepCircuitSets: Int = 0,
    val rir0To1Sets: Int = 0,
    val rir2To3Sets: Int = 0,
    val rir4PlusSets: Int = 0,
    val novelMovementIds: Set<String> = emptySet()
) {
    val shadowFractionalSets: Double
        get() = directHardSets +
            Engine16StrengthPolicyRegistry.SHADOW_FRACTIONAL_INDIRECT_WEIGHT * indirectHardSets

    fun merge(other: MuscleGroupExposure): MuscleGroupExposure {
        require(muscleId == other.muscleId) { "Cannot merge different muscles: $muscleId vs ${other.muscleId}" }
        return MuscleGroupExposure(
            muscleId = muscleId,
            directHardSets = directHardSets + other.directHardSets,
            indirectHardSets = indirectHardSets + other.indirectHardSets,
            isometricHoldSeconds = isometricHoldSeconds + other.isometricHoldSeconds,
            eccentricFocusedSets = eccentricFocusedSets + other.eccentricFocusedSets,
            plyometricContacts = plyometricContacts + other.plyometricContacts,
            highRepCircuitSets = highRepCircuitSets + other.highRepCircuitSets,
            rir0To1Sets = rir0To1Sets + other.rir0To1Sets,
            rir2To3Sets = rir2To3Sets + other.rir2To3Sets,
            rir4PlusSets = rir4PlusSets + other.rir4PlusSets,
            novelMovementIds = novelMovementIds + other.novelMovementIds
        )
    }
}

/**
 * Explicit Observed vs. Projected muscle ledger contract (INV-02 / MD-14).
 */
internal class UnifiedLocalMuscleLedger(
    val accountId: String,
    val windowStartInclusive: LocalDate,
    val windowEndInclusive: LocalDate,
    val isProjected: Boolean,
    byMuscle: Map<String, MuscleGroupExposure>
) {
    val byMuscle: Map<String, MuscleGroupExposure>
    val totalDirectSets: Int
    val contentHash: String

    init {
        RecordPolicy.identifier(accountId)
        require(!windowStartInclusive.isAfter(windowEndInclusive))
        val sorted = TreeMap<String, MuscleGroupExposure>()
        for ((k, v) in byMuscle) {
            require(v.muscleId == k)
            sorted[k] = v
        }
        this.byMuscle = Collections.unmodifiableMap(sorted)
        this.totalDirectSets = sorted.values.sumOf { it.directHardSets }
        val payload = buildString {
            append("acct=").append(accountId)
            append("|proj=").append(isProjected)
            append("|w=").append(windowStartInclusive).append("..").append(windowEndInclusive)
            for (m in sorted.values) {
                append("|").append(m.muscleId).append(":")
                    .append(m.directHardSets).append(",")
                    .append(m.indirectHardSets).append(",")
                    .append(m.isometricHoldSeconds).append(",")
                    .append(m.eccentricFocusedSets).append(",")
                    .append(m.plyometricContacts).append(",")
                    .append(m.highRepCircuitSets)
            }
        }
        this.contentHash = Engine16Hash.sha256Hex(payload)
    }

    fun exposureFor(muscleId: String): MuscleGroupExposure =
        byMuscle[muscleId] ?: MuscleGroupExposure(muscleId)
}

internal data class ObservedStrengthSetExposureInput(
    val accountId: String,
    val performedOn: LocalDate,
    val exerciseId: String,
    val exerciseName: String = exerciseId,
    val hardSetCount: Int,
    val holdSecondsTotal: Int = 0,
    val repsPerSet: Int? = null,
    val rir: Int? = null,
    val isEccentricFocused: Boolean = false,
    val isPlyometric: Boolean = false,
    val plyoContacts: Int = 0,
    val isHighRepCircuit: Boolean = false,
    val isNovelMovement: Boolean = false
)

/**
 * Aggregates completed resistance, calisthenics, plyometric, isometric, and Freeform
 * exposures into unified canonical muscle-group ledgers without double-counting projected work.
 */
internal object Engine16LocalMuscleLedgerBuilder {

    fun buildObserved(
        accountId: String,
        asOfDate: LocalDate,
        windowDays: Int = 7,
        plans: List<PlanSnapshot> = emptyList(),
        freeformWorkouts: List<FreeformWorkout> = emptyList(),
        explicitExposures: List<ObservedStrengthSetExposureInput> = emptyList(),
        library: ExerciseLibraryV2? = null
    ): UnifiedLocalMuscleLedger {
        RecordPolicy.identifier(accountId)
        require(windowDays >= 1)
        val endInclusive = asOfDate.minusDays(1)
        val startInclusive = asOfDate.minusDays(windowDays.toLong())
        val catalog = resolveCatalog(library)
        val strengthMeta = Engine16StrengthExerciseCatalog.allMetadata(library).associateBy { it.id }
        val acc = linkedMapOf<String, MuscleGroupExposure>()

        // 1. Observed completed PlannedSessions (non-running, never projected/planned/skipped!).
        for (plan in plans) {
            if (plan.owner != accountId) continue
            for (session in plan.sessions) {
                if (session.discipline == "run" || session.status != "completed") continue
                val log = session.log ?: continue
                val doneDate = log.performedOn ?: session.date
                if (doneDate.isBefore(startInclusive) || doneDate.isAfter(endInclusive)) continue

                val byExercise = log.sets.filter { (it.reps ?: 0) > 0 || (it.seconds ?: 0) > 0 }
                    .groupBy { it.exerciseId }
                val prescribedById = session.effectiveExercises.associateBy { it.id }

                if (byExercise.isNotEmpty()) {
                    for ((exId, sets) in byExercise) {
                        val fallbackName = prescribedById[exId]?.name
                        val isPlyo = session.kind == "structured-plyometrics"
                        val contacts = if (isPlyo) sets.sumOf { it.reps ?: 0 } else 0
                        val holdSec = sets.sumOf { it.seconds ?: 0 }
                        val maxReps = sets.mapNotNull { it.reps }.maxOrNull() ?: 0
                        recordExerciseExposure(
                            acc = acc,
                            exerciseId = exId,
                            exerciseName = fallbackName,
                            setsCount = sets.size,
                            holdSeconds = holdSec,
                            isEccentric = strengthMeta[exId]?.let { MechanicalTag.HIGH_ECCENTRIC in it.mechanicalTags } == true,
                            isPlyometric = isPlyo,
                            plyoContacts = contacts,
                            isHighRepCircuit = session.discipline == "cond" || maxReps > 15,
                            rir = parseRir(log.notes),
                            isNovel = false,
                            catalog = catalog,
                            strengthMeta = strengthMeta
                        )
                    }
                } else {
                    for (ex in session.effectiveExercises) {
                        val isPlyo = session.kind == "structured-plyometrics"
                        val holdSec = (ex.seconds ?: 0) * ex.sets
                        recordExerciseExposure(
                            acc = acc,
                            exerciseId = ex.id,
                            exerciseName = ex.name,
                            setsCount = ex.sets.coerceAtLeast(1),
                            holdSeconds = holdSec,
                            isEccentric = strengthMeta[ex.id]?.let { MechanicalTag.HIGH_ECCENTRIC in it.mechanicalTags } == true,
                            isPlyometric = isPlyo,
                            plyoContacts = if (isPlyo) (ex.reps ?: 0) * ex.sets else 0,
                            isHighRepCircuit = session.discipline == "cond" || (ex.reps ?: 0) > 15,
                            rir = parseRir(log.notes),
                            isNovel = false,
                            catalog = catalog,
                            strengthMeta = strengthMeta
                        )
                    }
                }
            }
        }

        // 2. Completed Freeform/Custom workouts (read-only).
        for (workout in freeformWorkouts) {
            if (workout.owner != accountId || !workout.isComplete) continue
            if (workout.discipline == ManualDiscipline.RUNNING) continue
            val doneDate = workout.date ?: continue
            if (doneDate.isBefore(startInclusive) || doneDate.isAfter(endInclusive)) continue

            val isPlyo = workout.discipline == ManualDiscipline.PLYOMETRICS
            for (exercise in workout.exercises) {
                val doneSets = exercise.sets.filter { it.completed }
                if (doneSets.isEmpty()) continue
                val exId = exercise.catalogId ?: exercise.id
                val contacts = if (isPlyo) doneSets.sumOf { it.actual.quantity.trim().toIntOrNull() ?: 0 } else 0
                val holdSec = doneSets.sumOf { it.actual.seconds.trim().toIntOrNull() ?: 0 }
                val avgRir = doneSets.mapNotNull { it.rir.trim().toIntOrNull() }.firstOrNull()
                val highRep = workout.discipline == ManualDiscipline.CONDITIONING ||
                    doneSets.any { (it.actual.quantity.trim().toIntOrNull() ?: 0) > 15 }
                recordExerciseExposure(
                    acc = acc,
                    exerciseId = exId,
                    exerciseName = exercise.name,
                    setsCount = doneSets.size,
                    holdSeconds = holdSec,
                    isEccentric = strengthMeta[exId]?.let { MechanicalTag.HIGH_ECCENTRIC in it.mechanicalTags } == true,
                    isPlyometric = isPlyo,
                    plyoContacts = contacts,
                    isHighRepCircuit = highRep,
                    rir = avgRir,
                    isNovel = false,
                    catalog = catalog,
                    strengthMeta = strengthMeta
                )
            }
        }

        // 3. Explicit typed observed exposures.
        for (obs in explicitExposures) {
            if (obs.accountId != accountId) continue
            if (obs.performedOn.isBefore(startInclusive) || obs.performedOn.isAfter(endInclusive)) continue
            if (obs.hardSetCount <= 0 && obs.holdSecondsTotal <= 0 && obs.plyoContacts <= 0) continue
            recordExerciseExposure(
                acc = acc,
                exerciseId = obs.exerciseId,
                exerciseName = obs.exerciseName,
                setsCount = obs.hardSetCount.coerceAtLeast(0),
                holdSeconds = obs.holdSecondsTotal,
                isEccentric = obs.isEccentricFocused,
                isPlyometric = obs.isPlyometric,
                plyoContacts = obs.plyoContacts,
                isHighRepCircuit = obs.isHighRepCircuit || (obs.repsPerSet ?: 0) > 15,
                rir = obs.rir,
                isNovel = obs.isNovelMovement,
                catalog = catalog,
                strengthMeta = strengthMeta
            )
        }

        return UnifiedLocalMuscleLedger(
            accountId = accountId,
            windowStartInclusive = startInclusive,
            windowEndInclusive = endInclusive,
            isProjected = false,
            byMuscle = acc
        )
    }

    /**
     * Builds a strictly isolated projected ledger over planned candidate exercises (INV-02 / MD-14).
     */
    fun buildProjectedForWeek(
        accountId: String,
        weekStartInclusive: LocalDate,
        weekEndInclusive: LocalDate,
        plannedExercises: List<Triple<StrengthExerciseMetadata, Int, IntRange>>, // exercise, workingSets, targetRir
        library: ExerciseLibraryV2? = null
    ): UnifiedLocalMuscleLedger {
        RecordPolicy.identifier(accountId)
        val catalog = resolveCatalog(library)
        val strengthMeta = Engine16StrengthExerciseCatalog.allMetadata(library).associateBy { it.id }
        val acc = linkedMapOf<String, MuscleGroupExposure>()

        for ((meta, sets, targetRir) in plannedExercises) {
            recordExerciseExposure(
                acc = acc,
                exerciseId = meta.id,
                exerciseName = meta.name,
                setsCount = sets.coerceAtLeast(0),
                holdSeconds = if (meta.isIsometricHold) sets * 25 else 0,
                isEccentric = MechanicalTag.HIGH_ECCENTRIC in meta.mechanicalTags,
                isPlyometric = MechanicalTag.BALLISTIC in meta.mechanicalTags && sets > 0 && "pogo" in meta.id,
                plyoContacts = 0,
                isHighRepCircuit = false,
                rir = targetRir.first,
                isNovel = false,
                catalog = catalog,
                strengthMeta = strengthMeta
            )
        }

        return UnifiedLocalMuscleLedger(
            accountId = accountId,
            windowStartInclusive = weekStartInclusive,
            windowEndInclusive = weekEndInclusive,
            isProjected = true,
            byMuscle = acc
        )
    }

    private fun resolveCatalog(library: ExerciseLibraryV2?): List<ExerciseLibraryRecordV2> =
        FreeformExerciseCatalog.augment(library?.records.orEmpty())

    private fun recordExerciseExposure(
        acc: MutableMap<String, MuscleGroupExposure>,
        exerciseId: String,
        exerciseName: String?,
        setsCount: Int,
        holdSeconds: Int,
        isEccentric: Boolean,
        isPlyometric: Boolean,
        plyoContacts: Int,
        isHighRepCircuit: Boolean,
        rir: Int?,
        isNovel: Boolean,
        catalog: List<ExerciseLibraryRecordV2>,
        strengthMeta: Map<String, StrengthExerciseMetadata>
    ) {
        val meta = strengthMeta[exerciseId]
        val (primary, secondary) = if (meta != null) {
            meta.primaryMuscles to meta.secondaryMuscles
        } else {
            val resolved = MuscleFocusProjector.resolveRecord(exerciseId, exerciseName, catalog)
                ?: return
            resolved.primaryMuscles to resolved.secondaryMuscles
        }

        val isHardSet = rir == null || rir <= Engine16StrengthPolicyRegistry.HIGH_EXPOSURE_FAILURE_RIR_CUTOFF
        val countedSets = if (isHardSet && !isPlyometric) setsCount else 0
        val eccSets = if (isEccentric) countedSets else 0
        val circuitSets = if (isHighRepCircuit) countedSets else 0
        val r01 = if (rir != null && rir <= 1) countedSets else 0
        val r23 = if (rir == null || rir in 2..3) countedSets else 0
        val r4p = if (rir != null && rir >= 4) setsCount else 0
        val novelSet = if (isNovel) setOf(exerciseId) else emptySet()

        for (m in primary) {
            if (m !in ExerciseMuscleCatalog.STANDARD_MUSCLES) continue
            val delta = MuscleGroupExposure(
                muscleId = m,
                directHardSets = countedSets,
                isometricHoldSeconds = holdSeconds,
                eccentricFocusedSets = eccSets,
                plyometricContacts = plyoContacts,
                highRepCircuitSets = circuitSets,
                rir0To1Sets = r01,
                rir2To3Sets = r23,
                rir4PlusSets = r4p,
                novelMovementIds = novelSet
            )
            acc[m] = acc[m]?.merge(delta) ?: delta
        }
        for (m in secondary) {
            if (m !in ExerciseMuscleCatalog.STANDARD_MUSCLES || m in primary) continue
            val delta = MuscleGroupExposure(
                muscleId = m,
                indirectHardSets = countedSets,
                isometricHoldSeconds = 0,
                eccentricFocusedSets = 0,
                plyometricContacts = 0,
                highRepCircuitSets = 0,
                rir0To1Sets = r01,
                rir2To3Sets = r23,
                rir4PlusSets = r4p,
                novelMovementIds = novelSet
            )
            acc[m] = acc[m]?.merge(delta) ?: delta
        }
    }

    private fun parseRir(notes: String): Int? =
        Regex("""\bRIR\s*[:=]?\s*(10|[0-9])\b""", RegexOption.IGNORE_CASE)
            .find(notes)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

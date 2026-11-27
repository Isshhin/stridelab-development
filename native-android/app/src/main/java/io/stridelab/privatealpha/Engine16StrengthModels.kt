package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal enum class StrengthObjective(val label: String) {
    MAXIMAL_STRENGTH("Maximal strength"),
    HYPERTROPHY("Hypertrophy"),
    GENERAL_STRENGTH("General strength"),
    STRENGTH_ENDURANCE("Strength endurance"),
    STRENGTH_MAINTENANCE("Strength maintenance"),
    RUNNING_SUPPORTIVE("Strength for runners"),
    POWER_DEVELOPMENT("Power development");

    companion object {
        fun fromRequestGoal(goal: String): StrengthObjective {
            val normalized = goal.trim().lowercase(Locale.ROOT)
            return when {
                normalized in setOf("maximal strength", "max strength", "build max strength", "powerlifting") ->
                    MAXIMAL_STRENGTH
                normalized in setOf("muscle development", "hypertrophy", "build muscle") ->
                    HYPERTROPHY
                normalized in setOf("strength endurance", "muscular endurance") ->
                    STRENGTH_ENDURANCE
                normalized in setOf("strength maintenance", "maintenance", "maintain fitness", "maintain strength") ->
                    STRENGTH_MAINTENANCE
                normalized in setOf("strength for runners", "lower / runner durability", "running supportive") ->
                    RUNNING_SUPPORTIVE
                normalized in setOf("power", "power development", "explosive strength") ->
                    POWER_DEVELOPMENT
                else -> GENERAL_STRENGTH
            }
        }
    }
}

internal data class StrengthObjectiveSpec(
    val objective: StrengthObjective,
    val primaryRepRange: IntRange,
    val secondaryRepRange: IntRange,
    val isolationRepRange: IntRange,
    val compoundRestSeconds: Int,
    val secondaryRestSeconds: Int,
    val isolationRestSeconds: Int,
    val primarySets: Int,
    val secondarySets: Int,
    val isolationSets: Int,
    val weeklyDirectSetBand: IntRange,
    val targetRir: IntRange,
    val allowSupersetUnderTightCap: Boolean
)

internal enum class StrengthLaterality {
    BILATERAL,
    UNILATERAL
}

internal enum class TechnicalComplexity {
    LOW,
    MODERATE,
    HIGH
}

internal enum class MechanicalTag {
    AXIAL,
    HIGH_ECCENTRIC,
    LOW_SYSTEMIC,
    BALLISTIC
}

internal enum class StrengthRomTag {
    FULL_CONTROLLED,
    LONG_LENGTH_PARTIAL,
    SPECIFIC_LIMIT
}

internal enum class StrengthTempoTag(val minSecondsPerRep: Double, val maxSecondsPerRep: Double) {
    CONTROLLED_STANDARD(
        Engine16StrengthPolicyRegistry.DEFAULT_REP_CADENCE_MIN_SECONDS,
        Engine16StrengthPolicyRegistry.DEFAULT_REP_CADENCE_MAX_SECONDS
    ),
    EXPLICIT_TEMPO_PAUSE(
        Engine16StrengthPolicyRegistry.TEMPO_REP_CADENCE_MIN_SECONDS,
        Engine16StrengthPolicyRegistry.TEMPO_REP_CADENCE_MAX_SECONDS
    )
}

internal enum class MovementProficiency {
    NOVICE_LEARNING,
    COMPETENT,
    PROFICIENT
}

internal enum class ExternalLoadSemanticsKind {
    TOTAL_BARBELL_MASS,
    DUMBBELL_PER_HAND,
    MACHINE_STACK_SETTING,
    RESISTANCE_BAND_TIER,
    ASSISTED_BODYWEIGHT,
    EXTERNAL_ADDED_MASS,
    UNLOADED_BODYWEIGHT
}

/**
 * Strictly typed external load representation preventing cross-implement and per-hand bugs (MD-02).
 */
internal sealed interface ExternalLoadSemantics {
    val kind: ExternalLoadSemanticsKind
    val comparableLoadKg: Kilograms?
    fun canonicalRepresentation(): String
    fun isComparableWith(other: ExternalLoadSemantics): Boolean

    data class TotalBarbellMass(val totalKg: Kilograms) : ExternalLoadSemantics {
        init {
            require(totalKg.value > 0.0) { "TotalBarbellMass requires positive kg" }
        }
        override val kind = ExternalLoadSemanticsKind.TOTAL_BARBELL_MASS
        override val comparableLoadKg = totalKg
        override fun canonicalRepresentation() = "BARBELL:${totalKg.canonicalToken()}"
        override fun isComparableWith(other: ExternalLoadSemantics) = other is TotalBarbellMass
    }

    data class DumbbellPerHand(val perHandKg: Kilograms, val hands: Int = 2) : ExternalLoadSemantics {
        init {
            require(perHandKg.value > 0.0) { "DumbbellPerHand requires positive perHandKg" }
            require(hands in 1..2) { "Dumbbell hands must be 1 or 2" }
        }
        val combinedKg: Kilograms get() = Kilograms.of(perHandKg.value * hands)
        override val kind = ExternalLoadSemanticsKind.DUMBBELL_PER_HAND
        override val comparableLoadKg = perHandKg
        override fun canonicalRepresentation() = "DB:${hands}x${perHandKg.canonicalToken()}"
        override fun isComparableWith(other: ExternalLoadSemantics) =
            other is DumbbellPerHand && other.hands == hands
    }

    data class MachineStackSetting(
        val nominalValue: Double,
        val unitLabel: String,
        val machineId: String
    ) : ExternalLoadSemantics {
        init {
            CanonicalSiQuantity.requirePositive(nominalValue, "nominalValue")
            require(unitLabel.isNotBlank() && machineId.isNotBlank())
        }
        override val kind = ExternalLoadSemanticsKind.MACHINE_STACK_SETTING
        override val comparableLoadKg: Kilograms? =
            if (unitLabel.lowercase(Locale.ROOT) == "kg") Kilograms.of(nominalValue) else null
        override fun canonicalRepresentation() =
            "MACHINE:$machineId:${CanonicalSiQuantity.canonicalDecimalString(nominalValue)}:$unitLabel"
        override fun isComparableWith(other: ExternalLoadSemantics) =
            other is MachineStackSetting &&
                other.machineId == machineId &&
                other.unitLabel.equals(unitLabel, ignoreCase = true)
    }

    data class ResistanceBandTier(
        val bandId: String,
        val stretchAnchor: String = "standard"
    ) : ExternalLoadSemantics {
        init {
            require(bandId.isNotBlank() && stretchAnchor.isNotBlank())
        }
        override val kind = ExternalLoadSemanticsKind.RESISTANCE_BAND_TIER
        override val comparableLoadKg: Kilograms? = null
        override fun canonicalRepresentation() = "BAND:$bandId:$stretchAnchor"
        override fun isComparableWith(other: ExternalLoadSemantics) =
            other is ResistanceBandTier && other.stretchAnchor == stretchAnchor
    }

    data class AssistedBodyweight(
        val assistanceType: String,
        val assistanceKg: Kilograms? = null,
        val bandId: String? = null
    ) : ExternalLoadSemantics {
        init {
            require(assistanceType.isNotBlank())
            require(assistanceKg != null || !bandId.isNullOrBlank())
        }
        override val kind = ExternalLoadSemanticsKind.ASSISTED_BODYWEIGHT
        override val comparableLoadKg: Kilograms? = assistanceKg
        override fun canonicalRepresentation() =
            "ASSISTED:$assistanceType:${assistanceKg?.canonicalToken() ?: bandId}"
        override fun isComparableWith(other: ExternalLoadSemantics) =
            other is AssistedBodyweight && other.assistanceType == assistanceType
    }

    data class ExternalAddedMass(
        val addedKg: Kilograms,
        val recordedBodyMassKg: Kilograms? = null
    ) : ExternalLoadSemantics {
        init {
            require(addedKg.value >= 0.0)
        }
        override val kind = ExternalLoadSemanticsKind.EXTERNAL_ADDED_MASS
        override val comparableLoadKg = addedKg
        override fun canonicalRepresentation() =
            "ADDED:${addedKg.canonicalToken()}|BM:${recordedBodyMassKg?.canonicalToken() ?: "NONE"}"
        override fun isComparableWith(other: ExternalLoadSemantics): Boolean {
            if (other !is ExternalAddedMass) return false
            val bmA = recordedBodyMassKg?.value
            val bmB = other.recordedBodyMassKg?.value
            if (bmA != null && bmB != null && bmA > 0.0) {
                val shift = abs(bmB - bmA) / bmA
                if (shift >= Engine16StrengthPolicyRegistry.BODY_MASS_TRANSFER_SHIFT_RATIO) return false
            }
            return true
        }
    }

    data object UnloadedBodyweight : ExternalLoadSemantics {
        override val kind = ExternalLoadSemanticsKind.UNLOADED_BODYWEIGHT
        override val comparableLoadKg: Kilograms? = null
        override fun canonicalRepresentation() = "BODYWEIGHT"
        override fun isComparableWith(other: ExternalLoadSemantics) = other is UnloadedBodyweight
    }
}

internal enum class StrengthPattern {
    SQUAT,
    HINGE,
    UNILATERAL_LOWER,
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    HORIZONTAL_PULL,
    VERTICAL_PULL,
    CALF_ANKLE,
    CORE_STABILITY,
    QUAD_ISOLATION,
    HAMSTRING_ISOLATION,
    DELTOID_ISOLATION,
    ARM_ISOLATION,
    POWER_BALLISTIC
}

internal enum class StrengthSlotOrder(val rank: Int) {
    POWER_SKILL(0),
    PRIMARY_COMPOUND(1),
    SECONDARY_COMPOUND_OR_UNILATERAL(2),
    ISOLATION_AND_CORE(3)
}

/**
 * Minimal 8-field strength exercise descriptor plus safe instruction metadata (Track 6 §3).
 */
internal data class StrengthExerciseMetadata(
    val id: String,
    val name: String,
    val pattern: StrengthPattern,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipmentOptions: List<Set<String>>,
    val loadSemanticsKind: ExternalLoadSemanticsKind,
    val laterality: StrengthLaterality,
    val technicalComplexity: TechnicalComplexity,
    val mechanicalTags: Set<MechanicalTag>,
    val slotOrder: StrengthSlotOrder,
    val difficulty: String = "foundation",
    val defaultRom: StrengthRomTag = StrengthRomTag.FULL_CONTROLLED,
    val minIncrementKg: Double = Engine16StrengthPolicyRegistry.DEFAULT_DUMBBELL_INCREMENT_PER_HAND_KG,
    val techniqueCues: List<String> = emptyList(),
    val easierName: String = "",
    val harderName: String = "",
    val isIsometricHold: Boolean = false
) {
    val isLowerBody: Boolean
        get() = pattern in setOf(
            StrengthPattern.SQUAT,
            StrengthPattern.HINGE,
            StrengthPattern.UNILATERAL_LOWER,
            StrengthPattern.CALF_ANKLE,
            StrengthPattern.QUAD_ISOLATION,
            StrengthPattern.HAMSTRING_ISOLATION,
            StrengthPattern.POWER_BALLISTIC
        ) || primaryMuscles.any {
            it in setOf(
                "quadriceps", "hamstrings", "gluteus_maximus", "gluteus_medius",
                "calves", "adductors", "tibialis_anterior"
            )
        }

    val isAxialCompound: Boolean
        get() = MechanicalTag.AXIAL in mechanicalTags
}

internal enum class TechniqueProvenance {
    COACH_VERIFIED_TECHNIQUE,
    SENSOR_SUPPORTED_METRIC,
    ATHLETE_SELF_REPORTED_TECHNIQUE,
    UNOBSERVED_TECHNIQUE
}

internal enum class TechniqueQuality {
    CLEAN,
    BORDERLINE,
    BREAKDOWN,
    UNOBSERVED
}

internal enum class ExecutionFactor {
    MUSCULAR_CAPACITY_LIMIT,
    TECHNICAL_LIMITATION,
    INSUFFICIENT_RECOVERY,
    EQUIPMENT_OR_LOAD_STEP_LIMIT,
    TIME_CONSTRAINT,
    ENVIRONMENTAL_CONFOUNDER,
    SYMPTOM_LIMITED,
    UNKNOWN
}

internal enum class SymptomSafetyStatus {
    CONFIRMED_NONE,
    MILD_EXPECTED_SORENESS,
    CONCERNING_SYMPTOM_FLAG,
    MISSING_UNREPORTED
}

/**
 * Exact SI duration interval [minSeconds, maxSeconds] implementing MOD-13 / POL-044.
 */
internal data class StrengthDurationInterval(
    val minSeconds: Seconds,
    val maxSeconds: Seconds
) {
    init {
        require(minSeconds.value <= maxSeconds.value + 1e-9) {
            "Invalid duration interval: $minSeconds > $maxSeconds"
        }
    }

    operator fun plus(other: StrengthDurationInterval): StrengthDurationInterval =
        StrengthDurationInterval(
            minSeconds = Seconds.of(minSeconds.value + other.minSeconds.value),
            maxSeconds = Seconds.of(maxSeconds.value + other.maxSeconds.value)
        )

    fun fitsBudget(dailyBudgetMinutes: Int): Boolean =
        maxSeconds.value <= dailyBudgetMinutes * 60.0 + 1e-9

    val conservativeMinutesCeiling: Int
        get() = kotlin.math.ceil(maxSeconds.value / 60.0).toInt().coerceAtLeast(1)
}

internal sealed interface StrengthBlockDurationSpec {
    val interval: StrengthDurationInterval

    data class StraightSetBlock(
        val exercise: StrengthExerciseMetadata,
        val workingSets: Int,
        val repsOrSeconds: Int,
        val restSeconds: Int,
        val rampSets: Int = 0,
        val includeSetup: Boolean = true,
        val tempo: StrengthTempoTag = StrengthTempoTag.CONTROLLED_STANDARD
    ) : StrengthBlockDurationSpec {
        override val interval: StrengthDurationInterval =
            Engine16StrengthDurationCalculator.straightBlockInterval(
                laterality = exercise.laterality,
                isIsometricHold = exercise.isIsometricHold,
                workingSets = workingSets,
                repsOrHoldSeconds = repsOrSeconds,
                restSeconds = restSeconds,
                rampSets = rampSets,
                includeSetup = includeSetup,
                tempo = tempo
            )
    }

    data class SupersetBlock(
        val first: StrengthExerciseMetadata,
        val second: StrengthExerciseMetadata,
        val rounds: Int,
        val firstRepsOrSeconds: Int,
        val secondRepsOrSeconds: Int,
        val roundRestSeconds: Int,
        val switchSeconds: Double = Engine16StrengthPolicyRegistry.SUPERSET_SWITCH_SECONDS,
        val rampSets: Int = 0,
        val tempo: StrengthTempoTag = StrengthTempoTag.CONTROLLED_STANDARD
    ) : StrengthBlockDurationSpec {
        override val interval: StrengthDurationInterval =
            Engine16StrengthDurationCalculator.supersetBlockInterval(
                firstLaterality = first.laterality,
                firstIsHold = first.isIsometricHold,
                firstRepsOrHoldSeconds = firstRepsOrSeconds,
                secondLaterality = second.laterality,
                secondIsHold = second.isIsometricHold,
                secondRepsOrHoldSeconds = secondRepsOrSeconds,
                rounds = rounds,
                roundRestSeconds = roundRestSeconds,
                switchSeconds = switchSeconds,
                rampSets = rampSets,
                tempo = tempo
            )
    }
}

/**
 * Pure MOD-13 + POL-044 conservative session-duration accounting engine (Track 6 §9).
 * Explicitly models 2-side unilateral execution + switch pause, ramp sets, setup overhead,
 * superset switch vs. (R - 1) round rest, and unrounded SI upper-bound checks.
 */
internal object Engine16StrengthDurationCalculator {

    fun workIntervalPerSet(
        laterality: StrengthLaterality,
        isIsometricHold: Boolean,
        repsOrHoldSeconds: Int,
        tempo: StrengthTempoTag = StrengthTempoTag.CONTROLLED_STANDARD
    ): StrengthDurationInterval {
        require(repsOrHoldSeconds >= 1) { "repsOrHoldSeconds must be >= 1" }
        if (isIsometricHold) {
            val base = Seconds.of(repsOrHoldSeconds.toDouble())
            return if (laterality == StrengthLaterality.UNILATERAL) {
                StrengthDurationInterval(
                    minSeconds = Seconds.of(base.value * 2.0 + Engine16StrengthPolicyRegistry.UNILATERAL_SWITCH_MIN_SECONDS),
                    maxSeconds = Seconds.of(base.value * 2.0 + Engine16StrengthPolicyRegistry.UNILATERAL_SWITCH_MAX_SECONDS)
                )
            } else {
                StrengthDurationInterval(base, base)
            }
        }
        return if (laterality == StrengthLaterality.UNILATERAL) {
            StrengthDurationInterval(
                minSeconds = Seconds.of(
                    2.0 * repsOrHoldSeconds * tempo.minSecondsPerRep +
                        Engine16StrengthPolicyRegistry.UNILATERAL_SWITCH_MIN_SECONDS
                ),
                maxSeconds = Seconds.of(
                    2.0 * repsOrHoldSeconds * tempo.maxSecondsPerRep +
                        Engine16StrengthPolicyRegistry.UNILATERAL_SWITCH_MAX_SECONDS
                )
            )
        } else {
            StrengthDurationInterval(
                minSeconds = Seconds.of(repsOrHoldSeconds * tempo.minSecondsPerRep),
                maxSeconds = Seconds.of(repsOrHoldSeconds * tempo.maxSecondsPerRep)
            )
        }
    }

    fun straightBlockInterval(
        laterality: StrengthLaterality,
        isIsometricHold: Boolean,
        workingSets: Int,
        repsOrHoldSeconds: Int,
        restSeconds: Int,
        rampSets: Int = 0,
        includeSetup: Boolean = true,
        tempo: StrengthTempoTag = StrengthTempoTag.CONTROLLED_STANDARD
    ): StrengthDurationInterval {
        require(workingSets >= 1 && restSeconds >= 0 && rampSets >= 0)
        val perSet = workIntervalPerSet(laterality, isIsometricHold, repsOrHoldSeconds, tempo)
        val betweenSets = max(0, workingSets - 1) * restSeconds.toDouble()
        val setupMin = if (includeSetup) Engine16StrengthPolicyRegistry.SETUP_TRANSITION_MIN_SECONDS else 0.0
        val setupMax = if (includeSetup) Engine16StrengthPolicyRegistry.SETUP_TRANSITION_MAX_SECONDS else 0.0
        val rampMin = rampSets * Engine16StrengthPolicyRegistry.RAMP_SET_MIN_SECONDS
        val rampMax = rampSets * Engine16StrengthPolicyRegistry.RAMP_SET_MAX_SECONDS
        return StrengthDurationInterval(
            minSeconds = Seconds.of(setupMin + rampMin + workingSets * perSet.minSeconds.value + betweenSets),
            maxSeconds = Seconds.of(setupMax + rampMax + workingSets * perSet.maxSeconds.value + betweenSets)
        )
    }

    fun supersetBlockInterval(
        firstLaterality: StrengthLaterality,
        firstIsHold: Boolean,
        firstRepsOrHoldSeconds: Int,
        secondLaterality: StrengthLaterality,
        secondIsHold: Boolean,
        secondRepsOrHoldSeconds: Int,
        rounds: Int,
        roundRestSeconds: Int,
        switchSeconds: Double = Engine16StrengthPolicyRegistry.SUPERSET_SWITCH_SECONDS,
        rampSets: Int = 0,
        tempo: StrengthTempoTag = StrengthTempoTag.CONTROLLED_STANDARD
    ): StrengthDurationInterval {
        require(rounds >= 1 && roundRestSeconds >= 0 && switchSeconds >= 0.0 && rampSets >= 0)
        val work1 = workIntervalPerSet(firstLaterality, firstIsHold, firstRepsOrHoldSeconds, tempo)
        val work2 = workIntervalPerSet(secondLaterality, secondIsHold, secondRepsOrHoldSeconds, tempo)
        val perRoundMin = work1.minSeconds.value + switchSeconds + work2.minSeconds.value
        val perRoundMax = work1.maxSeconds.value + switchSeconds + work2.maxSeconds.value
        val roundRest = max(0, rounds - 1) * roundRestSeconds.toDouble()
        // Paired exercises still need equipment setup once per movement slot.
        val setupMin = 2 * Engine16StrengthPolicyRegistry.SETUP_TRANSITION_MIN_SECONDS
        val setupMax = 2 * Engine16StrengthPolicyRegistry.SETUP_TRANSITION_MAX_SECONDS
        val rampMin = rampSets * Engine16StrengthPolicyRegistry.RAMP_SET_MIN_SECONDS
        val rampMax = rampSets * Engine16StrengthPolicyRegistry.RAMP_SET_MAX_SECONDS
        return StrengthDurationInterval(
            minSeconds = Seconds.of(setupMin + rampMin + rounds * perRoundMin + roundRest),
            maxSeconds = Seconds.of(setupMax + rampMax + rounds * perRoundMax + roundRest)
        )
    }

    fun sessionInterval(
        blocks: List<StrengthBlockDurationSpec>,
        isMedCompact: Boolean = false
    ): StrengthDurationInterval {
        val warmup = if (isMedCompact) {
            StrengthDurationInterval(
                Seconds.of(Engine16StrengthPolicyRegistry.MED_WARMUP_MIN_SECONDS),
                Seconds.of(Engine16StrengthPolicyRegistry.MED_WARMUP_MAX_SECONDS)
            )
        } else {
            StrengthDurationInterval(
                Seconds.of(Engine16StrengthPolicyRegistry.GENERAL_WARMUP_MIN_SECONDS),
                Seconds.of(Engine16StrengthPolicyRegistry.GENERAL_WARMUP_MAX_SECONDS)
            )
        }
        val cooldown = if (isMedCompact) {
            StrengthDurationInterval(
                Seconds.of(Engine16StrengthPolicyRegistry.MED_COOLDOWN_MIN_SECONDS),
                Seconds.of(Engine16StrengthPolicyRegistry.MED_COOLDOWN_MAX_SECONDS)
            )
        } else {
            StrengthDurationInterval(
                Seconds.of(Engine16StrengthPolicyRegistry.COOLDOWN_MIN_SECONDS),
                Seconds.of(Engine16StrengthPolicyRegistry.COOLDOWN_MAX_SECONDS)
            )
        }
        return blocks.fold(warmup + cooldown) { acc, block -> acc + block.interval }
    }
}

internal object Engine16Hash {
    fun sha256Hex(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

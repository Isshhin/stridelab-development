package io.stridelab.privatealpha

import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** All 8 Core Track 6 Calisthenics Domains. */
internal enum class CalisthenicsDomain {
    PULL_UP,
    PUSH_UP,
    DIP,
    HANDSTAND_SKILL,
    CORE_AND_L_SIT,
    SINGLE_LEG_SQUAT,
    WEIGHTED_CALISTHENICS,
    ISOMETRIC_STATIC_HOLDS
}

internal enum class CalisthenicsObjective(val label: String) {
    CALISTHENICS_FOUNDATION("Calisthenics foundation"),
    PULL_UP_PROGRESSION("Pull-up progression"),
    PUSH_UP_PROGRESSION("Push-up progression"),
    DIP_AND_UPPER_STRENGTH("Dips and upper strength"),
    CORE_AND_COMPRESSION("Core strength and control"),
    HANDSTAND_FOUNDATIONS("Handstand foundations"),
    SINGLE_LEG_CONTROL("Single-leg control"),
    SKILL_AND_STATIC_HOLDS("Skill and static holds"),
    WEIGHTED_CALISTHENICS("Weighted calisthenics");

    companion object {
        fun fromGoalString(goal: String?): CalisthenicsObjective {
            val g = goal?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return when {
                g in setOf("pull-up progression", "pull") -> PULL_UP_PROGRESSION
                g in setOf("push-up progression", "push") -> PUSH_UP_PROGRESSION
                g in setOf("core strength and control", "core / anti-rotation") -> CORE_AND_COMPRESSION
                g in setOf("handstand foundations", "handstand / inversion") -> HANDSTAND_FOUNDATIONS
                g == "single-leg control" -> SINGLE_LEG_CONTROL
                "dip" in g -> DIP_AND_UPPER_STRENGTH
                "weighted" in g -> WEIGHTED_CALISTHENICS
                "skill" in g || "static" in g -> SKILL_AND_STATIC_HOLDS
                else -> CALISTHENICS_FOUNDATION
            }
        }
    }
}

/** 9 Explicit Calisthenics Progression Levers from Track 6. */
internal enum class CalisthenicsProgressionLever {
    ASSISTANCE_REDUCTION,
    LEVERAGE_ANGLE,
    RANGE_OF_MOTION,
    REPETITIONS,
    SETS,
    EXTERNAL_ADDED_MASS,
    ISOMETRIC_HOLD_DURATION,
    CONTROLLED_TEMPO_ECCENTRIC,
    SKILL_COMPLEXITY
}

/** 4 Explicit Bottleneck Types encoded on DAG prerequisites. */
internal enum class CalisthenicsBottleneck {
    STRENGTH,
    MOBILITY_ROM,
    BALANCE_PROPRIOCEPTION,
    COORDINATION_SKILL
}

internal enum class CalisthenicsBlockRole {
    WARMUP_MOBILITY_PREP,
    FRESH_SKILL_PRACTICE,
    PRIMARY_LEVERAGE_STRENGTH,
    PREREQUISITE_CLUSTER,
    ISOMETRIC_HOLD,
    TECHNIQUE_INTERVAL_FINISHER
}

internal sealed interface CalisthenicsPrerequisiteGate {
    data object None : CalisthenicsPrerequisiteGate
    data class RequireAll(val nodeIds: List<String>, val bottlenecks: Set<CalisthenicsBottleneck>) : CalisthenicsPrerequisiteGate
    data class RequireAny(val nodeIds: List<String>, val bottlenecks: Set<CalisthenicsBottleneck>) : CalisthenicsPrerequisiteGate
}

internal data class CalisthenicsDagNode(
    val exercise: StrengthExerciseMetadata,
    val domain: CalisthenicsDomain,
    val difficultyTier: Int, // non-uniform ordering hint only, never equal spacing assumption
    val gate: CalisthenicsPrerequisiteGate,
    val bypassCleanRepsRequired: Int = Engine17CalisthenicsPolicyRegistry.POL_061_ENTRY_BYPASS_MIN_CLEAN_REPS,
    val isSkillPracticeOnly: Boolean = false,
    val isVerticalSuspendedWeightedEligible: Boolean = false,
    val isEccentricHighTendonStrain: Boolean = false,
    val regressionNodeId: String? = null,
    val mobilityCheckRequired: String? = null,
    val defaultHoldSeconds: Int? = null
)

internal data class CalisthenicsNodeObservation(
    val nodeId: String,
    val completedOn: LocalDate,
    val repsCompleted: Int?,
    val holdSecondsCompleted: Int?,
    val setsCompleted: Int,
    val techniqueQuality: TechniqueQuality,
    val techniqueProvenance: TechniqueProvenance,
    val rirObserved: Int?,
    val recoveryReported: Boolean,
    val symptomsPresent: Boolean = false,
    val anteriorShoulderPain: Boolean = false,
    val elbowTendonPain: Boolean = false,
    val holdTremorOrCollapse: Boolean = false,
    val mobilityGatePassed: Boolean = true,
    val bodyweightMassKg: Double? = null,
    val externalAddedKg: Double? = null
)

internal data class CalisthenicsDurationInterval(
    val minSeconds: Int,
    val maxSeconds: Int
) {
    init {
        require(minSeconds >= 0 && maxSeconds >= minSeconds)
    }
    val conservativeMinutesCeiling: Int get() = ceil(maxSeconds / 60.0).toInt().coerceAtLeast(1)
}

internal data class CalisthenicsWorkoutSlot(
    val node: CalisthenicsDagNode,
    val blockRole: CalisthenicsBlockRole,
    val sets: Int,
    val reps: Int?,
    val holdSeconds: Int?,
    val restSeconds: Int,
    val targetRir: IntRange,
    val timeBoxMinutes: Int? = null,
    val weightedLoadSemantics: ExternalLoadSemantics.ExternalAddedMass? = null,
    val coachingTag: String? = null
)

/** MOD-13 conservative duration accounting for calisthenics sessions. */
internal object Engine17CalisthenicsDurationCalculator {
    fun estimateSessionDuration(
        slots: List<CalisthenicsWorkoutSlot>,
        isCompact: Boolean
    ): CalisthenicsDurationInterval {
        var minSec = if (isCompact) 180 else Engine17CalisthenicsPolicyRegistry.WARM_UP_MIN_S
        var maxSec = if (isCompact) 300 else Engine17CalisthenicsPolicyRegistry.WARM_UP_MAX_S

        for (slot in slots) {
            if (slot.blockRole == CalisthenicsBlockRole.FRESH_SKILL_PRACTICE && slot.timeBoxMinutes != null) {
                val exactS = slot.timeBoxMinutes * 60
                minSec += exactS
                maxSec += exactS
                continue
            }
            val meta = slot.node.exercise
            minSec += Engine17CalisthenicsPolicyRegistry.EXERCISE_SETUP_LOW_S
            maxSec += Engine17CalisthenicsPolicyRegistry.EXERCISE_SETUP_HIGH_S

            val sets = max(1, slot.sets)
            if (slot.holdSeconds != null) {
                minSec += sets * slot.holdSeconds + max(0, sets - 1) * slot.restSeconds
                maxSec += sets * slot.holdSeconds + max(0, sets - 1) * slot.restSeconds
            } else {
                val reps = max(1, slot.reps ?: 6)
                val (cLo, cHi) = if (slot.node.isEccentricHighTendonStrain) {
                    Engine17CalisthenicsPolicyRegistry.ECCENTRIC_CADENCE_LOW_S to Engine17CalisthenicsPolicyRegistry.ECCENTRIC_CADENCE_HIGH_S
                } else {
                    Engine17CalisthenicsPolicyRegistry.DYNAMIC_CADENCE_LOW_S to Engine17CalisthenicsPolicyRegistry.DYNAMIC_CADENCE_HIGH_S
                }
                if (meta.laterality == StrengthLaterality.UNILATERAL) {
                    minSec += sets * ((2 * reps * cLo).toInt() + Engine17CalisthenicsPolicyRegistry.UNILATERAL_SWITCH_LOW_S)
                    maxSec += sets * (ceil(2 * reps * cHi).toInt() + Engine17CalisthenicsPolicyRegistry.UNILATERAL_SWITCH_HIGH_S)
                } else {
                    minSec += sets * (reps * cLo).toInt()
                    maxSec += sets * ceil(reps * cHi).toInt()
                }
                minSec += max(0, sets - 1) * slot.restSeconds
                maxSec += max(0, sets - 1) * slot.restSeconds
            }
        }

        minSec += Engine17CalisthenicsPolicyRegistry.COOL_DOWN_MIN_S
        maxSec += Engine17CalisthenicsPolicyRegistry.COOL_DOWN_MAX_S
        return CalisthenicsDurationInterval(minSec, maxSec)
    }
}

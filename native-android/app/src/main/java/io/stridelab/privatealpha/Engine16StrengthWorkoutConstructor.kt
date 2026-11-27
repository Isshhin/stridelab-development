package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.max

internal enum class StrengthSplitKind {
    FULL_BODY,
    FULL_BODY_AB,
    FULL_BODY_DUP,
    UPPER_LOWER,
    UPPER_LOWER_PPL,
    PPL_ROTATION,
    RUNNER_SUPPORTIVE_SPLIT
}

internal enum class StrengthPairingMode {
    STRAIGHT_SETS,
    ANTAGONIST_SUPERSET
}

internal enum class StrengthRestTier {
    STANDARD,
    COMPACT
}

internal data class ConstructedExerciseSlot(
    val exercise: StrengthExerciseMetadata,
    val workingSets: Int,
    val rampSets: Int,
    val repRange: IntRange,
    val prescribedReps: Int?,
    val prescribedSeconds: Int?,
    val restSeconds: Int,
    val targetRir: IntRange,
    val supersetGroupLabel: String? = null
)

internal data class ConstructedStrengthSession(
    val dayLabel: String,
    val splitKind: StrengthSplitKind,
    val pairingMode: StrengthPairingMode,
    val restTier: StrengthRestTier,
    val isMedFallback: Boolean,
    val isLowerBodySession: Boolean,
    val slots: List<ConstructedExerciseSlot>,
    val durationInterval: StrengthDurationInterval,
    val rampNotes: List<String>,
    val candidateHash: String
)

internal data class StrengthWorkoutConstructionInput(
    val objective: StrengthObjective,
    val strengthDaysPerWeek: Int,
    val slotIndex: Int,
    val experience: Int,
    val availableEquipment: Set<String>,
    val excludedExerciseIds: Set<String> = emptySet(),
    val dailyTimeBudgetMinutes: Int? = null,
    val safetyIntensityCap: SafetyIntensityCap? = null,
    val setMultiplier: Double = 1.0,
    val repOverride: IntRange? = null,
    val targetRirOverride: IntRange? = null,
    val preferAlternatingVariant: Boolean = (slotIndex % 2 == 1),
    val observedMuscleLedger: UnifiedLocalMuscleLedger? = null,
    val isDeloadOrRecovery: Boolean = false
)

/**
 * Bounded deterministic strength workout candidate constructor (Track 6 §3).
 * Produces Hevy-style split variety, strict compound-before-isolation ordering,
 * finite grid search over rest tiers and antagonist supersets, and exact MOD-13
 * conservative duration compliance (T_max <= T_day_max).
 */
internal object Engine16StrengthWorkoutConstructor {

    internal data class DayBlueprint(
        val label: String,
        val splitKind: StrengthSplitKind,
        val patterns: List<StrengthPattern>,
        val isLowerBodySession: Boolean
    )

    private val patternFallbacks: Map<StrengthPattern, List<StrengthPattern>> = mapOf(
        StrengthPattern.POWER_BALLISTIC to listOf(StrengthPattern.HINGE, StrengthPattern.UNILATERAL_LOWER, StrengthPattern.SQUAT),
        StrengthPattern.QUAD_ISOLATION to listOf(StrengthPattern.SQUAT, StrengthPattern.UNILATERAL_LOWER),
        StrengthPattern.HAMSTRING_ISOLATION to listOf(StrengthPattern.HINGE, StrengthPattern.UNILATERAL_LOWER),
        StrengthPattern.UNILATERAL_LOWER to listOf(StrengthPattern.SQUAT, StrengthPattern.HINGE),
        StrengthPattern.VERTICAL_PUSH to listOf(StrengthPattern.HORIZONTAL_PUSH, StrengthPattern.DELTOID_ISOLATION),
        StrengthPattern.VERTICAL_PULL to listOf(StrengthPattern.HORIZONTAL_PULL),
        StrengthPattern.DELTOID_ISOLATION to listOf(StrengthPattern.VERTICAL_PUSH, StrengthPattern.HORIZONTAL_PULL),
        StrengthPattern.ARM_ISOLATION to listOf(StrengthPattern.HORIZONTAL_PULL, StrengthPattern.HORIZONTAL_PUSH),
        StrengthPattern.CALF_ANKLE to listOf(StrengthPattern.UNILATERAL_LOWER, StrengthPattern.CORE_STABILITY),
        StrengthPattern.SQUAT to listOf(StrengthPattern.UNILATERAL_LOWER, StrengthPattern.HINGE),
        StrengthPattern.HINGE to listOf(StrengthPattern.SQUAT, StrengthPattern.CORE_STABILITY),
        StrengthPattern.HORIZONTAL_PUSH to listOf(StrengthPattern.VERTICAL_PUSH, StrengthPattern.CORE_STABILITY),
        StrengthPattern.HORIZONTAL_PULL to listOf(StrengthPattern.VERTICAL_PULL, StrengthPattern.CORE_STABILITY)
    )

    fun dayBlueprint(
        objective: StrengthObjective,
        daysPerWeek: Int,
        slotIndex: Int,
        experience: Int
    ): DayBlueprint {
        require(daysPerWeek in 1..6) { "Strength frequency must be 1..6 days/week" }
        require(slotIndex in 0 until daysPerWeek) { "Invalid strength slotIndex $slotIndex for $daysPerWeek days" }

        if (objective == StrengthObjective.RUNNING_SUPPORTIVE && daysPerWeek <= 2) {
            return if (daysPerWeek == 1) {
                DayBlueprint(
                    label = "Runner Full Body",
                    splitKind = StrengthSplitKind.RUNNER_SUPPORTIVE_SPLIT,
                    patterns = listOf(
                        StrengthPattern.SQUAT,
                        StrengthPattern.HINGE,
                        StrengthPattern.CALF_ANKLE,
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = true
                )
            } else if (slotIndex == 0) {
                DayBlueprint(
                    label = "Runner Lower & Durability",
                    splitKind = StrengthSplitKind.RUNNER_SUPPORTIVE_SPLIT,
                    patterns = listOf(
                        StrengthPattern.POWER_BALLISTIC,
                        StrengthPattern.SQUAT,
                        StrengthPattern.HINGE,
                        StrengthPattern.CALF_ANKLE
                    ),
                    isLowerBodySession = true
                )
            } else {
                DayBlueprint(
                    label = "Runner Upper & Single-Leg",
                    splitKind = StrengthSplitKind.RUNNER_SUPPORTIVE_SPLIT,
                    patterns = listOf(
                        StrengthPattern.UNILATERAL_LOWER,
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.HORIZONTAL_PULL,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = true
                )
            }
        }

        return when (daysPerWeek) {
            1 -> DayBlueprint(
                label = "Full Body",
                splitKind = StrengthSplitKind.FULL_BODY,
                patterns = listOf(
                    StrengthPattern.SQUAT,
                    StrengthPattern.HORIZONTAL_PUSH,
                    StrengthPattern.HORIZONTAL_PULL,
                    StrengthPattern.HINGE,
                    StrengthPattern.CORE_STABILITY
                ),
                isLowerBodySession = true
            )
            2 -> if (slotIndex == 0) {
                DayBlueprint(
                    label = "Full Body A",
                    splitKind = StrengthSplitKind.FULL_BODY_AB,
                    patterns = listOf(
                        StrengthPattern.SQUAT,
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.HORIZONTAL_PULL,
                        StrengthPattern.UNILATERAL_LOWER,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = true
                )
            } else {
                DayBlueprint(
                    label = "Full Body B",
                    splitKind = StrengthSplitKind.FULL_BODY_AB,
                    patterns = listOf(
                        StrengthPattern.HINGE,
                        StrengthPattern.VERTICAL_PUSH,
                        StrengthPattern.VERTICAL_PULL,
                        StrengthPattern.CALF_ANKLE,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = true
                )
            }
            3 -> if (objective == StrengthObjective.HYPERTROPHY && experience >= 1) {
                when (slotIndex) {
                    0 -> DayBlueprint(
                        label = "Push · Chest & Shoulders",
                        splitKind = StrengthSplitKind.PPL_ROTATION,
                        patterns = listOf(
                            StrengthPattern.HORIZONTAL_PUSH,
                            StrengthPattern.VERTICAL_PUSH,
                            StrengthPattern.DELTOID_ISOLATION,
                            StrengthPattern.ARM_ISOLATION,
                            StrengthPattern.CORE_STABILITY
                        ),
                        isLowerBodySession = false
                    )
                    1 -> DayBlueprint(
                        label = "Pull · Back & Rear Delts",
                        splitKind = StrengthSplitKind.PPL_ROTATION,
                        patterns = listOf(
                            StrengthPattern.VERTICAL_PULL,
                            StrengthPattern.HORIZONTAL_PULL,
                            StrengthPattern.DELTOID_ISOLATION,
                            StrengthPattern.ARM_ISOLATION,
                            StrengthPattern.CORE_STABILITY
                        ),
                        isLowerBodySession = false
                    )
                    else -> DayBlueprint(
                        label = "Legs · Squat, Hinge & Calves",
                        splitKind = StrengthSplitKind.PPL_ROTATION,
                        patterns = listOf(
                            StrengthPattern.SQUAT,
                            StrengthPattern.HINGE,
                            StrengthPattern.UNILATERAL_LOWER,
                            StrengthPattern.HAMSTRING_ISOLATION,
                            StrengthPattern.CALF_ANKLE
                        ),
                        isLowerBodySession = true
                    )
                }
            } else {
                when (slotIndex) {
                    0 -> DayBlueprint(
                        label = "Full Body A",
                        splitKind = StrengthSplitKind.FULL_BODY_DUP,
                        patterns = listOf(
                            StrengthPattern.SQUAT,
                            StrengthPattern.HORIZONTAL_PUSH,
                            StrengthPattern.HORIZONTAL_PULL,
                            StrengthPattern.CALF_ANKLE,
                            StrengthPattern.CORE_STABILITY
                        ),
                        isLowerBodySession = true
                    )
                    1 -> DayBlueprint(
                        label = "Full Body B",
                        splitKind = StrengthSplitKind.FULL_BODY_DUP,
                        patterns = listOf(
                            StrengthPattern.HINGE,
                            StrengthPattern.VERTICAL_PUSH,
                            StrengthPattern.VERTICAL_PULL,
                            StrengthPattern.QUAD_ISOLATION,
                            StrengthPattern.ARM_ISOLATION
                        ),
                        isLowerBodySession = true
                    )
                    else -> DayBlueprint(
                        label = "Full Body C",
                        splitKind = StrengthSplitKind.FULL_BODY_DUP,
                        patterns = listOf(
                            StrengthPattern.UNILATERAL_LOWER,
                            StrengthPattern.HORIZONTAL_PUSH,
                            StrengthPattern.HORIZONTAL_PULL,
                            StrengthPattern.DELTOID_ISOLATION,
                            StrengthPattern.CORE_STABILITY
                        ),
                        isLowerBodySession = true
                    )
                }
            }
            4 -> when (slotIndex) {
                0 -> DayBlueprint(
                    label = "Upper A",
                    splitKind = StrengthSplitKind.UPPER_LOWER,
                    patterns = listOf(
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.HORIZONTAL_PULL,
                        StrengthPattern.VERTICAL_PUSH,
                        StrengthPattern.DELTOID_ISOLATION,
                        StrengthPattern.ARM_ISOLATION
                    ),
                    isLowerBodySession = false
                )
                1 -> DayBlueprint(
                    label = "Lower A",
                    splitKind = StrengthSplitKind.UPPER_LOWER,
                    patterns = listOf(
                        StrengthPattern.SQUAT,
                        StrengthPattern.HINGE,
                        StrengthPattern.UNILATERAL_LOWER,
                        StrengthPattern.CALF_ANKLE,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = true
                )
                2 -> DayBlueprint(
                    label = "Upper B",
                    splitKind = StrengthSplitKind.UPPER_LOWER,
                    patterns = listOf(
                        StrengthPattern.HORIZONTAL_PULL,
                        StrengthPattern.VERTICAL_PUSH,
                        StrengthPattern.VERTICAL_PULL,
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.ARM_ISOLATION
                    ),
                    isLowerBodySession = false
                )
                else -> DayBlueprint(
                    label = "Lower B",
                    splitKind = StrengthSplitKind.UPPER_LOWER,
                    patterns = listOf(
                        StrengthPattern.HINGE,
                        StrengthPattern.SQUAT,
                        StrengthPattern.HAMSTRING_ISOLATION,
                        StrengthPattern.QUAD_ISOLATION,
                        StrengthPattern.CALF_ANKLE
                    ),
                    isLowerBodySession = true
                )
            }
            5 -> when (slotIndex) {
                0 -> DayBlueprint(
                    label = "Upper",
                    splitKind = StrengthSplitKind.UPPER_LOWER_PPL,
                    patterns = listOf(
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.HORIZONTAL_PULL,
                        StrengthPattern.VERTICAL_PUSH,
                        StrengthPattern.VERTICAL_PULL,
                        StrengthPattern.DELTOID_ISOLATION
                    ),
                    isLowerBodySession = false
                )
                1 -> DayBlueprint(
                    label = "Lower",
                    splitKind = StrengthSplitKind.UPPER_LOWER_PPL,
                    patterns = listOf(
                        StrengthPattern.SQUAT,
                        StrengthPattern.HINGE,
                        StrengthPattern.UNILATERAL_LOWER,
                        StrengthPattern.CALF_ANKLE,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = true
                )
                2 -> DayBlueprint(
                    label = "Push",
                    splitKind = StrengthSplitKind.UPPER_LOWER_PPL,
                    patterns = listOf(
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.VERTICAL_PUSH,
                        StrengthPattern.DELTOID_ISOLATION,
                        StrengthPattern.ARM_ISOLATION,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = false
                )
                3 -> DayBlueprint(
                    label = "Pull",
                    splitKind = StrengthSplitKind.UPPER_LOWER_PPL,
                    patterns = listOf(
                        StrengthPattern.VERTICAL_PULL,
                        StrengthPattern.HORIZONTAL_PULL,
                        StrengthPattern.DELTOID_ISOLATION,
                        StrengthPattern.ARM_ISOLATION,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = false
                )
                else -> DayBlueprint(
                    label = "Legs",
                    splitKind = StrengthSplitKind.UPPER_LOWER_PPL,
                    patterns = listOf(
                        StrengthPattern.SQUAT,
                        StrengthPattern.HINGE,
                        StrengthPattern.QUAD_ISOLATION,
                        StrengthPattern.HAMSTRING_ISOLATION,
                        StrengthPattern.CALF_ANKLE
                    ),
                    isLowerBodySession = true
                )
            }
            else -> when (slotIndex) {
                0 -> DayBlueprint(
                    label = "Push A",
                    splitKind = StrengthSplitKind.PPL_ROTATION,
                    patterns = listOf(
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.VERTICAL_PUSH,
                        StrengthPattern.DELTOID_ISOLATION,
                        StrengthPattern.ARM_ISOLATION,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = false
                )
                1 -> DayBlueprint(
                    label = "Pull A",
                    splitKind = StrengthSplitKind.PPL_ROTATION,
                    patterns = listOf(
                        StrengthPattern.VERTICAL_PULL,
                        StrengthPattern.HORIZONTAL_PULL,
                        StrengthPattern.DELTOID_ISOLATION,
                        StrengthPattern.ARM_ISOLATION,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = false
                )
                2 -> DayBlueprint(
                    label = "Legs A",
                    splitKind = StrengthSplitKind.PPL_ROTATION,
                    patterns = listOf(
                        StrengthPattern.SQUAT,
                        StrengthPattern.HINGE,
                        StrengthPattern.UNILATERAL_LOWER,
                        StrengthPattern.CALF_ANKLE,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = true
                )
                3 -> DayBlueprint(
                    label = "Push B",
                    splitKind = StrengthSplitKind.PPL_ROTATION,
                    patterns = listOf(
                        StrengthPattern.VERTICAL_PUSH,
                        StrengthPattern.HORIZONTAL_PUSH,
                        StrengthPattern.DELTOID_ISOLATION,
                        StrengthPattern.ARM_ISOLATION,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = false
                )
                4 -> DayBlueprint(
                    label = "Pull B",
                    splitKind = StrengthSplitKind.PPL_ROTATION,
                    patterns = listOf(
                        StrengthPattern.HORIZONTAL_PULL,
                        StrengthPattern.VERTICAL_PULL,
                        StrengthPattern.DELTOID_ISOLATION,
                        StrengthPattern.ARM_ISOLATION,
                        StrengthPattern.CORE_STABILITY
                    ),
                    isLowerBodySession = false
                )
                else -> DayBlueprint(
                    label = "Legs B",
                    splitKind = StrengthSplitKind.PPL_ROTATION,
                    patterns = listOf(
                        StrengthPattern.HINGE,
                        StrengthPattern.SQUAT,
                        StrengthPattern.HAMSTRING_ISOLATION,
                        StrengthPattern.QUAD_ISOLATION,
                        StrengthPattern.CALF_ANKLE
                    ),
                    isLowerBodySession = true
                )
            }
        }
    }

    fun construct(
        input: StrengthWorkoutConstructionInput,
        catalog: List<StrengthExerciseMetadata>
    ): ConstructedStrengthSession {
        val blueprint = dayBlueprint(
            objective = input.objective,
            daysPerWeek = input.strengthDaysPerWeek,
            slotIndex = input.slotIndex,
            experience = input.experience
        )
        val spec = Engine16StrengthPolicyRegistry.defaultsFor(input.objective, input.experience)
        val baseRir = input.targetRirOverride ?: spec.targetRir
        val safetyCapRir = when (input.safetyIntensityCap) {
            SafetyIntensityCap.EASY_AEROBIC_ONLY -> 3..4
            SafetyIntensityCap.NO_HIGH_INTENSITY_Z3 -> max(baseRir.first, 2)..max(baseRir.last, 3)
            SafetyIntensityCap.REST_ONLY -> throw PlanConstraint(
                "SAFETY_RESTRICTION_HOLD: Strength training is paused under a rest-only safety cap."
            )
            null -> baseRir
        }

        val candidates = mutableListOf<Pair<ConstructedStrengthSession, CandidateScore>>()
        val tiers = listOf(
            CandidateGridConfig(slotCount = blueprint.patterns.size, pairing = StrengthPairingMode.STRAIGHT_SETS, rest = StrengthRestTier.STANDARD, medCompact = false),
            CandidateGridConfig(slotCount = blueprint.patterns.size, pairing = StrengthPairingMode.STRAIGHT_SETS, rest = StrengthRestTier.COMPACT, medCompact = false),
            CandidateGridConfig(slotCount = blueprint.patterns.size, pairing = StrengthPairingMode.ANTAGONIST_SUPERSET, rest = StrengthRestTier.COMPACT, medCompact = false),
            CandidateGridConfig(slotCount = 4.coerceAtMost(blueprint.patterns.size), pairing = StrengthPairingMode.ANTAGONIST_SUPERSET, rest = StrengthRestTier.COMPACT, medCompact = false),
            CandidateGridConfig(slotCount = 3.coerceAtMost(blueprint.patterns.size), pairing = StrengthPairingMode.STRAIGHT_SETS, rest = StrengthRestTier.COMPACT, medCompact = true),
            CandidateGridConfig(slotCount = 3.coerceAtMost(blueprint.patterns.size), pairing = StrengthPairingMode.ANTAGONIST_SUPERSET, rest = StrengthRestTier.COMPACT, medCompact = true)
        )

        for (cfg in tiers) {
            if (cfg.pairing == StrengthPairingMode.ANTAGONIST_SUPERSET && !spec.allowSupersetUnderTightCap) {
                continue
            }
            val candidate = buildCandidate(input, blueprint, spec, safetyCapRir, cfg, catalog) ?: continue
            val fits = input.dailyTimeBudgetMinutes == null ||
                candidate.durationInterval.fitsBudget(input.dailyTimeBudgetMinutes)
            if (fits) {
                val score = scoreCandidate(candidate, blueprint.patterns.size)
                candidates += candidate to score
            }
        }

        if (candidates.isEmpty()) {
            throw PlanConstraint(
                "STRENGTH_TIME_BUDGET_CONFLICT: No strength routine fits safely within your ${input.dailyTimeBudgetMinutes ?: 0}-minute daily limit with current equipment and restrictions."
            )
        }

        return candidates.minWith(compareBy<Pair<ConstructedStrengthSession, CandidateScore>> { it.second }
            .thenBy { it.first.candidateHash }).first
    }

    private data class CandidateGridConfig(
        val slotCount: Int,
        val pairing: StrengthPairingMode,
        val rest: StrengthRestTier,
        val medCompact: Boolean
    )

    private data class CandidateScore(
        val medPenalty: Int,
        val missingPatternPenalty: Int,
        val supersetPenalty: Int,
        val restCompressionPenalty: Int,
        val totalSetsNegative: Int
    ) : Comparable<CandidateScore> {
        override fun compareTo(other: CandidateScore): Int =
            compareBy<CandidateScore> { it.medPenalty }
                .thenBy { it.missingPatternPenalty }
                .thenBy { it.supersetPenalty }
                .thenBy { it.restCompressionPenalty }
                .thenBy { it.totalSetsNegative }
                .compare(this, other)
    }

    private fun scoreCandidate(
        session: ConstructedStrengthSession,
        desiredSlots: Int
    ): CandidateScore = CandidateScore(
        medPenalty = if (session.isMedFallback) 1 else 0,
        missingPatternPenalty = (desiredSlots - session.slots.size).coerceAtLeast(0),
        supersetPenalty = if (session.pairingMode == StrengthPairingMode.ANTAGONIST_SUPERSET) 1 else 0,
        restCompressionPenalty = if (session.restTier == StrengthRestTier.COMPACT) 1 else 0,
        totalSetsNegative = -session.slots.sumOf { it.workingSets }
    )

    private fun buildCandidate(
        input: StrengthWorkoutConstructionInput,
        blueprint: DayBlueprint,
        spec: StrengthObjectiveSpec,
        baseRir: IntRange,
        cfg: CandidateGridConfig,
        catalog: List<StrengthExerciseMetadata>
    ): ConstructedStrengthSession? {
        val chosen = mutableListOf<StrengthExerciseMetadata>()
        val usedIds = HashSet<String>(input.excludedExerciseIds)
        val allowBallistic = input.safetyIntensityCap == null && !input.isDeloadOrRecovery

        for (pattern in blueprint.patterns.take(cfg.slotCount)) {
            val picked = pickExerciseForPattern(
                pattern = pattern,
                input = input,
                usedIds = usedIds,
                allowBallistic = allowBallistic,
                catalog = catalog
            )
            if (picked != null) {
                chosen += picked
                usedIds += picked.id
            }
        }
        if (chosen.isEmpty()) return null

        // Strict canonical intra-session order: Skill/Power -> Heavy Compounds -> Secondary/Unilateral -> Isolations/Core.
        val ordered = chosen.sortedWith(
            compareBy<StrengthExerciseMetadata> { it.slotOrder.rank }
                .thenByDescending { it.isAxialCompound }
                .thenBy { it.id }
        )

        val supersetPairs = if (cfg.pairing == StrengthPairingMode.ANTAGONIST_SUPERSET) {
            pairAntagonists(ordered)
        } else {
            emptyMap()
        }

        val slots = ordered.mapIndexed { idx, ex ->
            val rawSets = when (ex.slotOrder) {
                StrengthSlotOrder.POWER_SKILL,
                StrengthSlotOrder.PRIMARY_COMPOUND -> spec.primarySets
                StrengthSlotOrder.SECONDARY_COMPOUND_OR_UNILATERAL -> spec.secondarySets
                StrengthSlotOrder.ISOLATION_AND_CORE -> spec.isolationSets
            }
            val medCappedSets = if (cfg.medCompact) rawSets.coerceAtMost(2) else rawSets
            val ledgerPenalty = input.observedMuscleLedger?.let { ledger ->
                val direct = ex.primaryMuscles.maxOfOrNull { ledger.exposureFor(it).directHardSets } ?: 0
                if (direct >= spec.weeklyDirectSetBand.last) 1 else 0
            } ?: 0
            val adjustedSets = kotlin.math.round(
                (medCappedSets - ledgerPenalty).coerceAtLeast(1) * input.setMultiplier
            ).toInt().coerceIn(1, 6)

            val repRange = input.repOverride ?: when {
                ex.isIsometricHold -> 20..40
                ex.slotOrder == StrengthSlotOrder.POWER_SKILL -> 3..5
                ex.slotOrder == StrengthSlotOrder.PRIMARY_COMPOUND -> spec.primaryRepRange
                ex.slotOrder == StrengthSlotOrder.SECONDARY_COMPOUND_OR_UNILATERAL -> spec.secondaryRepRange
                else -> spec.isolationRepRange
            }
            val prescribedReps = if (ex.isIsometricHold) null else repRange.first
            val prescribedSeconds = if (ex.isIsometricHold) repRange.first else null
            val baseRest = when (ex.slotOrder) {
                StrengthSlotOrder.POWER_SKILL,
                StrengthSlotOrder.PRIMARY_COMPOUND -> spec.compoundRestSeconds
                StrengthSlotOrder.SECONDARY_COMPOUND_OR_UNILATERAL -> spec.secondaryRestSeconds
                StrengthSlotOrder.ISOLATION_AND_CORE -> spec.isolationRestSeconds
            }
            val restSeconds = if (cfg.rest == StrengthRestTier.COMPACT) {
                when (ex.slotOrder) {
                    StrengthSlotOrder.POWER_SKILL -> baseRest
                    StrengthSlotOrder.PRIMARY_COMPOUND -> minOf(baseRest, 90)
                    else -> minOf(baseRest, 60)
                }
            } else {
                baseRest
            }
            val targetRir = if (ex.isAxialCompound && baseRir.first == 0) {
                1..max(baseRir.last, 2)
            } else if (ex.isAxialCompound) {
                max(baseRir.first, 2)..max(baseRir.last, 3)
            } else {
                baseRir
            }
            val rampSets = if (
                ex.slotOrder == StrengthSlotOrder.PRIMARY_COMPOUND &&
                !cfg.medCompact &&
                input.objective in setOf(StrengthObjective.MAXIMAL_STRENGTH, StrengthObjective.GENERAL_STRENGTH, StrengthObjective.HYPERTROPHY) &&
                idx == 0 &&
                !ex.isIsometricHold &&
                ex.loadSemanticsKind != ExternalLoadSemanticsKind.UNLOADED_BODYWEIGHT
            ) 1 else 0

            ConstructedExerciseSlot(
                exercise = ex,
                workingSets = adjustedSets,
                rampSets = rampSets,
                repRange = repRange,
                prescribedReps = prescribedReps,
                prescribedSeconds = prescribedSeconds,
                restSeconds = restSeconds,
                targetRir = targetRir,
                supersetGroupLabel = supersetPairs[idx]
            )
        }

        val durationBlocks = buildDurationBlocks(slots)
        val interval = Engine16StrengthDurationCalculator.sessionInterval(durationBlocks, isMedCompact = cfg.medCompact)
        val rampNotes = slots.filter { it.rampSets > 0 }.map { slot ->
            "1 to 2 light ramp sets on ${slot.exercise.name} before working sets."
        }
        val hashInput = slots.joinToString("|") {
            "${it.exercise.id}:${it.workingSets}x${it.prescribedReps ?: it.prescribedSeconds}@${it.restSeconds}:${it.supersetGroupLabel ?: "-"}"
        } + "|${cfg.pairing.name}|${cfg.rest.name}|${cfg.medCompact}"

        return ConstructedStrengthSession(
            dayLabel = blueprint.label,
            splitKind = blueprint.splitKind,
            pairingMode = if (supersetPairs.isNotEmpty()) StrengthPairingMode.ANTAGONIST_SUPERSET else StrengthPairingMode.STRAIGHT_SETS,
            restTier = cfg.rest,
            isMedFallback = cfg.medCompact,
            isLowerBodySession = blueprint.isLowerBodySession,
            slots = slots,
            durationInterval = interval,
            rampNotes = rampNotes,
            candidateHash = Engine16Hash.sha256Hex(hashInput)
        )
    }

    private fun buildDurationBlocks(slots: List<ConstructedExerciseSlot>): List<StrengthBlockDurationSpec> {
        val blocks = mutableListOf<StrengthBlockDurationSpec>()
        val consumed = HashSet<Int>()
        for (i in slots.indices) {
            if (i in consumed) continue
            val slot = slots[i]
            val group = slot.supersetGroupLabel
            val partnerIdx = if (group != null) {
                (i + 1 until slots.size).firstOrNull { j -> j !in consumed && slots[j].supersetGroupLabel == group }
            } else null
            if (partnerIdx != null) {
                consumed += i
                consumed += partnerIdx
                val partner = slots[partnerIdx]
                blocks += StrengthBlockDurationSpec.SupersetBlock(
                    first = slot.exercise,
                    second = partner.exercise,
                    rounds = max(slot.workingSets, partner.workingSets),
                    firstRepsOrSeconds = slot.prescribedReps ?: slot.prescribedSeconds ?: 8,
                    secondRepsOrSeconds = partner.prescribedReps ?: partner.prescribedSeconds ?: 8,
                    roundRestSeconds = max(slot.restSeconds, partner.restSeconds),
                    rampSets = slot.rampSets + partner.rampSets
                )
            } else {
                consumed += i
                blocks += StrengthBlockDurationSpec.StraightSetBlock(
                    exercise = slot.exercise,
                    workingSets = slot.workingSets,
                    repsOrSeconds = slot.prescribedReps ?: slot.prescribedSeconds ?: 8,
                    restSeconds = slot.restSeconds,
                    rampSets = slot.rampSets
                )
            }
        }
        return blocks
    }

    private fun pairAntagonists(ordered: List<StrengthExerciseMetadata>): Map<Int, String> {
        val pairs = linkedMapOf<Int, String>()
        val used = HashSet<Int>()
        var pairIndex = 0
        for (i in ordered.indices) {
            if (i in used) continue
            val a = ordered[i]
            val partnerIdx = (i + 1 until ordered.size).firstOrNull { j ->
                j !in used && areCompatibleAntagonists(a, ordered[j])
            }
            if (partnerIdx != null) {
                used += i
                used += partnerIdx
                val label = "Superset ${'A' + pairIndex}"
                pairs[i] = label
                pairs[partnerIdx] = label
                pairIndex++
            }
        }
        return pairs
    }

    private fun areCompatibleAntagonists(a: StrengthExerciseMetadata, b: StrengthExerciseMetadata): Boolean {
        if (a.primaryMuscles.intersect(b.primaryMuscles.toSet()).isNotEmpty()) return false
        val pushPatterns = setOf(StrengthPattern.HORIZONTAL_PUSH, StrengthPattern.VERTICAL_PUSH)
        val pullPatterns = setOf(StrengthPattern.HORIZONTAL_PULL, StrengthPattern.VERTICAL_PULL)
        return (a.pattern in pushPatterns && b.pattern in pullPatterns) ||
            (a.pattern in pullPatterns && b.pattern in pushPatterns) ||
            (a.isLowerBody && !b.isLowerBody) ||
            (!a.isLowerBody && b.isLowerBody)
    }

    private fun pickExerciseForPattern(
        pattern: StrengthPattern,
        input: StrengthWorkoutConstructionInput,
        usedIds: Set<String>,
        allowBallistic: Boolean,
        catalog: List<StrengthExerciseMetadata>
    ): StrengthExerciseMetadata? {
        val searchPatterns = listOf(pattern) + patternFallbacks[pattern].orEmpty()
        for (candidatePattern in searchPatterns) {
            val matches = catalog.filter { ex ->
                ex.pattern == candidatePattern &&
                    ex.id !in usedIds &&
                    Engine16StrengthExerciseCatalog.matchesEquipment(ex, input.availableEquipment) &&
                    Engine16StrengthExerciseCatalog.proficiencyAllowed(ex, input.experience) &&
                    (allowBallistic || MechanicalTag.BALLISTIC !in ex.mechanicalTags) &&
                    !(input.objective == StrengthObjective.RUNNING_SUPPORTIVE && ex.id == "str-good-morning")
            }
            if (matches.isEmpty()) continue
            val ranked = matches.sortedWith(
                compareByDescending<StrengthExerciseMetadata> {
                    !input.preferAlternatingVariant || it.difficulty == "developing" || it.laterality == StrengthLaterality.UNILATERAL
                }
                    .thenByDescending {
                        it.loadSemanticsKind in setOf(
                            ExternalLoadSemanticsKind.TOTAL_BARBELL_MASS,
                            ExternalLoadSemanticsKind.DUMBBELL_PER_HAND,
                            ExternalLoadSemanticsKind.MACHINE_STACK_SETTING
                        )
                    }
                    .thenBy { if (input.objective == StrengthObjective.RUNNING_SUPPORTIVE && MechanicalTag.HIGH_ECCENTRIC in it.mechanicalTags) 1 else 0 }
                    .thenBy { it.id }
            )
            val idx = if (input.preferAlternatingVariant && ranked.size > 1) 1 else 0
            return ranked[idx]
        }
        return null
    }
}

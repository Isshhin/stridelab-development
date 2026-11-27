package io.stridelab.privatealpha

import kotlin.math.max
import kotlin.math.roundToInt

internal enum class CalisthenicsJourneySplit {
    FULL_BODY_JOURNEY,
    FULL_BODY_A_B,
    PUSH_PULL_CONTROL,
    UPPER_LOWER_A_B,
    PPL_PLUS_SKILL_5D,
    PPL_A_B_6D
}

internal data class CalisthenicsConstructedSession(
    val dayLabel: String,
    val splitKind: CalisthenicsJourneySplit,
    val slots: List<CalisthenicsWorkoutSlot>,
    val durationInterval: CalisthenicsDurationInterval,
    val isLowerBodySession: Boolean,
    val isCompactFallback: Boolean,
    val coachNotes: List<String>
)

internal data class CalisthenicsSessionConstructionInput(
    val objective: CalisthenicsObjective,
    val calDaysPerWeek: Int,
    val slotIndex: Int,
    val experience: Int,
    val pullLevel: Int,
    val coreLevel: Int,
    val availableEquipment: Set<String>,
    val excludedExerciseIds: Set<String>,
    val dailyTimeBudgetMinutes: Int?,
    val safetyIntensityCap: SafetyIntensityCap? = null,
    val setMultiplier: Double = 1.0,
    val isometricOverrideSeconds: Int? = null,
    val targetRirOverride: IntRange? = null,
    val isDeloadOrRecovery: Boolean = false,
    val variantIndex: Int = 0
)

/**
 * Engine 1.7 Freeletics-Inspired Calisthenics Workout Constructor:
 * - Distinct Fresh Skill Block FIRST (5-10 min, POL-061), separate from Leverage Strength sets
 * - Push / Pull / Single-Leg / Isometric / Core Technique Intervals across 1..6 d/wk
 * - Unblocks "Handstand foundations" & integrates all 30 ExpandedExerciseCatalog progressions
 * - Enforces POL-047 novice slow eccentric cap & MOD-13 conservative T_max <= T_day_max
 */
internal object Engine17CalisthenicsWorkoutConstructor {

    fun splitFor(daysPerWeek: Int, objective: CalisthenicsObjective): CalisthenicsJourneySplit = when {
        daysPerWeek <= 1 -> CalisthenicsJourneySplit.FULL_BODY_JOURNEY
        daysPerWeek == 2 -> CalisthenicsJourneySplit.FULL_BODY_A_B
        daysPerWeek == 3 -> CalisthenicsJourneySplit.PUSH_PULL_CONTROL
        daysPerWeek == 4 -> CalisthenicsJourneySplit.UPPER_LOWER_A_B
        daysPerWeek == 5 -> CalisthenicsJourneySplit.PPL_PLUS_SKILL_5D
        else -> CalisthenicsJourneySplit.PPL_A_B_6D
    }

    fun construct(
        input: CalisthenicsSessionConstructionInput,
        allNodes: List<CalisthenicsDagNode> = Engine17CalisthenicsDagCatalog.buildNodes()
    ): CalisthenicsConstructedSession {
        val spec = Engine17CalisthenicsPolicyRegistry.specFor(input.objective)
        val split = splitFor(input.calDaysPerWeek, input.objective)
        val baseRir = input.targetRirOverride ?: spec.targetRir
        val isSafetyEasy = input.safetyIntensityCap == SafetyIntensityCap.EASY_AEROBIC_ONLY ||
            input.safetyIntensityCap == SafetyIntensityCap.NO_HIGH_INTENSITY_Z3
        val effRir = if (isSafetyEasy) 3..4 else baseRir

        val maxProfRank = (max(input.experience, input.pullLevel) + if (input.coreLevel >= 2) 1 else 0).coerceAtMost(2)
        val eligible = allNodes.filter { node ->
            val ex = node.exercise
            val diffRank = when (ex.difficulty) {
                "foundation" -> 0
                "developing" -> 1
                else -> 2
            }
            ex.id !in input.excludedExerciseIds &&
                ex.equipmentOptions.any { opt -> opt.isEmpty() || input.availableEquipment.containsAll(opt) } &&
                diffRank <= maxProfRank
        }

        fun pickFirst(ids: List<String>, fallbackDomain: CalisthenicsDomain? = null): CalisthenicsDagNode? {
            for (id in ids) {
                eligible.firstOrNull { it.exercise.id == id }?.let { return it }
            }
            return fallbackDomain?.let { dom -> eligible.firstOrNull { it.domain == dom } }
        }

        fun pickVariant(
            ids: List<String>,
            variant: Int = input.variantIndex,
            excludeIds: Set<String> = emptySet(),
            fallbackDomain: CalisthenicsDomain? = null
        ): CalisthenicsDagNode? {
            val matches = ids.mapNotNull { id ->
                eligible.firstOrNull { it.exercise.id == id && it.exercise.id !in excludeIds }
            }
            if (matches.isNotEmpty()) return matches[variant.coerceAtLeast(0) % matches.size]
            return fallbackDomain?.let { dom ->
                val domMatches = eligible.filter { it.domain == dom && it.exercise.id !in excludeIds }
                domMatches.takeIf { it.isNotEmpty() }?.let { it[variant.coerceAtLeast(0) % it.size] }
            }
        }

        val hasBar = "pullup_bar" in input.availableEquipment
        val hasRings = "rings" in input.availableEquipment
        val isWeightedGoal = input.objective == CalisthenicsObjective.WEIGHTED_CALISTHENICS && input.experience >= 2

        val pullPref = buildList {
            if (isWeightedGoal && hasBar) add("cal-weighted-pullup")
            when {
                hasBar && input.pullLevel >= 2 -> addAll(listOf("e2", "cal-chinup", "cal-neutral-pullup", "cal-wide-pullup", "cal-commando-pullup", "ap", "br"))
                hasBar && input.pullLevel == 1 -> addAll(listOf("ap", "cal-negative-pullup", "cal-ring-row", "br"))
                hasBar -> addAll(listOf("cal-scap-hang", "ap", "br"))
                hasRings -> addAll(listOf("cal-ring-row", "cal-archer-row", "br"))
                else -> addAll(listOf("br"))
            }
        }.distinct()

        val pushPref = buildList {
            if (isWeightedGoal) add("cal-weighted-dip")
            when (input.experience) {
                0 -> addAll(listOf("wall-push", "cal-standard-pushup", "cal-scapular-pushup", "e4"))
                1 -> addAll(listOf("cal-standard-pushup", "cal-decline-pushup", "cal-diamond-pushup", "cal-pike-pushup", "e4"))
                else -> addAll(listOf("cal-decline-pushup", "cal-diamond-pushup", "cal-pike-pushup", "cal-archer-pushup", "cal-ring-pushup", "cal-ring-dip", "e4"))
            }
        }.distinct()

        val legsPref = when (input.experience) {
            0 -> listOf("cal-split-squat", "cal-cossack-squat", "cal-sl-glute-bridge")
            1 -> listOf("cal-rfess", "cal-cossack-squat", "cal-sissy-squat", "cal-sl-glute-bridge", "cal-split-squat")
            else -> listOf("cal-pistol-squat", "cal-shrimp-squat", "cal-rfess", "cal-nordic-curl", "cal-sissy-squat", "cal-cossack-squat")
        }
        val corePref = when (input.coreLevel) {
            0 -> listOf("fc", "side-plank", "cal-russian-twist")
            1 -> listOf("core-hollow", "side-plank", "cal-v-up", "fc")
            else -> listOf("core-hollow", "cal-toes-to-bar", "cal-floor-lsit", "cal-windshield-wiper", "cal-dragon-flag-neg", "cal-v-up")
        }
        val handstandSkillPref = when (input.experience) {
            0 -> listOf("cal-wall-plank", "cal-crow-pose")
            1 -> listOf("cal-chest-to-wall-hs", "cal-crow-pose", "cal-wall-plank")
            else -> listOf("cal-chest-to-wall-hs", "cal-planche-lean", "cal-crow-pose", "cal-hspu-wall")
        }
        val staticHoldPref = listOf(
            "cal-front-lever-tuck",
            "cal-back-lever-tuck",
            "cal-skin-the-cat",
            "cal-floor-lsit",
            "cal-planche-lean",
            "cal-chest-to-wall-hs"
        )

        val dayLabel = dayLabelFor(split, input.slotIndex, input.objective)
        val skillMins = if (input.isDeloadOrRecovery) 5 else spec.skillBlockMinutes
        val slots = mutableListOf<CalisthenicsWorkoutSlot>()

        fun makeSlot(
            node: CalisthenicsDagNode,
            role: CalisthenicsBlockRole,
            baseSets: Int,
            baseReps: Int?,
            holdSec: Int?,
            restSec: Int
        ): CalisthenicsWorkoutSlot {
            var sets = if (input.isDeloadOrRecovery) {
                val minDeloadSets = if (baseSets <= 2) 1 else 2
                max(minDeloadSets, (baseSets * input.setMultiplier).roundToInt()).coerceAtMost(baseSets)
            } else {
                max(1, (baseSets * input.setMultiplier).roundToInt().coerceIn(2, 4))
            }
            var reps = baseReps
            if (node.isEccentricHighTendonStrain && input.experience <= 1 && reps != null) {
                val (cSets, cReps) = Engine17CalisthenicsProgressionEngine.capEccentricDoseForTendonSafety(true, sets, reps)
                sets = if (input.isDeloadOrRecovery) minOf(cSets, sets) else cSets
                reps = cReps
            }
            val effHold = if (node.exercise.isIsometricHold) {
                input.isometricOverrideSeconds ?: holdSec ?: node.defaultHoldSeconds ?: spec.isometricSeconds.first
            } else null
            val effReps = if (effHold != null) null else (reps ?: spec.defaultRepRange.first)
            return CalisthenicsWorkoutSlot(
                node = node,
                blockRole = role,
                sets = sets,
                reps = effReps,
                holdSeconds = effHold,
                restSeconds = restSec,
                targetRir = effRir,
                timeBoxMinutes = if (role == CalisthenicsBlockRole.FRESH_SKILL_PRACTICE) skillMins else null
            )
        }

        fun usedIds(): Set<String> = slots.map { it.node.exercise.id }.toSet()

        fun addFreshSkillSlot(prefIds: List<String>, variantOffset: Int = input.variantIndex) {
            val skillNode = pickVariant(prefIds, variantOffset, usedIds(), CalisthenicsDomain.HANDSTAND_SKILL)
                ?: pickFirst(handstandSkillPref, CalisthenicsDomain.HANDSTAND_SKILL)
                ?: pickFirst(corePref, CalisthenicsDomain.CORE_AND_L_SIT)
            if (skillNode != null) {
                val skillSets = if (input.isDeloadOrRecovery) 2 else 3
                val hold = if (skillNode.exercise.isIsometricHold) {
                    input.isometricOverrideSeconds ?: skillNode.defaultHoldSeconds ?: 20
                } else null
                val reps = if (hold != null) null else spec.defaultRepRange.first
                slots += CalisthenicsWorkoutSlot(
                    node = skillNode,
                    blockRole = CalisthenicsBlockRole.FRESH_SKILL_PRACTICE,
                    sets = skillSets,
                    reps = reps,
                    holdSeconds = hold,
                    restSeconds = 60,
                    targetRir = effRir,
                    timeBoxMinutes = skillMins,
                    coachingTag = "Fresh skill practice first ($skillMins min)"
                )
            }
        }

        // 1. Fresh Skill Practice FIRST when appropriate
        val needsFreshSkill = input.objective in setOf(
            CalisthenicsObjective.HANDSTAND_FOUNDATIONS,
            CalisthenicsObjective.SKILL_AND_STATIC_HOLDS
        ) || (split == CalisthenicsJourneySplit.PPL_PLUS_SKILL_5D && input.slotIndex == 4) ||
            (split == CalisthenicsJourneySplit.PPL_A_B_6D && input.slotIndex == 5)

        if (needsFreshSkill) {
            val skillPool = if (input.objective == CalisthenicsObjective.SKILL_AND_STATIC_HOLDS) {
                staticHoldPref + handstandSkillPref
            } else {
                handstandSkillPref
            }
            addFreshSkillSlot(skillPool, input.slotIndex + input.variantIndex)
        }

        // 2. Main Freeletics-Inspired Journey Selection by Objective & Split Slot
        val daySeed = input.slotIndex + input.variantIndex
        when (input.objective) {
            CalisthenicsObjective.PULL_UP_PROGRESSION -> {
                if (hasBar && input.pullLevel == 0 && (input.calDaysPerWeek == 1 || input.slotIndex % 2 == 0)) {
                    // Scenario S05 Prerequisite Cluster + scapular hang & antagonist push
                    listOf("ap", "cal-negative-pullup", "cal-scap-hang", "br").mapNotNull { id -> eligible.firstOrNull { it.exercise.id == id } }.forEach { n ->
                        val baseSets = if (n.isEccentricHighTendonStrain) 2 else 3
                        val baseReps = if (n.isEccentricHighTendonStrain) 3 else 6
                        slots += makeSlot(n, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, baseSets, baseReps, n.defaultHoldSeconds, 90)
                    }
                    if (input.calDaysPerWeek >= 3 && input.slotIndex == 2) {
                        pickVariant(legsPref, daySeed, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 75)
                        }
                    } else {
                        pickVariant(pushPref, daySeed, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 3, 8, it.defaultHoldSeconds, 60)
                        }
                    }
                    pickVariant(corePref, daySeed, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                    }
                } else {
                    pickVariant(pullPref, daySeed, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, spec.defaultRestSeconds)
                    }
                    pickVariant(listOf("br", "cal-ring-row", "cal-scap-hang", "cal-negative-pullup", "ap"), daySeed, usedIds())?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, it.defaultHoldSeconds, 75)
                    }
                    if (input.calDaysPerWeek >= 3 && input.slotIndex == 2) {
                        pickVariant(legsPref, daySeed, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 75)
                        }
                    } else {
                        pickVariant(pushPref, daySeed, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, it.defaultHoldSeconds, 75)
                        }
                    }
                    pickVariant(corePref, daySeed, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                    }
                }
            }
            CalisthenicsObjective.PUSH_UP_PROGRESSION -> {
                pickVariant(pushPref, daySeed, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, spec.defaultRestSeconds)
                }
                pickVariant(listOf("cal-pike-pushup", "cal-dip-assisted", "cal-support-hold", "cal-scapular-pushup", "wall-push"), daySeed, usedIds())?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, it.defaultHoldSeconds, 75)
                }
                if (input.calDaysPerWeek >= 3 && input.slotIndex % 2 == 1) {
                    pickVariant(legsPref, daySeed, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 2, 8, null, 75)
                    }
                } else {
                    pickVariant(pullPref, daySeed, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, it.defaultHoldSeconds, 75)
                    }
                }
                pickVariant(corePref, daySeed, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                }
            }
            CalisthenicsObjective.HANDSTAND_FOUNDATIONS -> {
                pickVariant(listOf("cal-pike-pushup", "cal-decline-pushup", "wall-push", "e4"), daySeed, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 6, null, 90)
                }
                if (input.calDaysPerWeek >= 3 && input.slotIndex % 2 == 1) {
                    pickVariant(legsPref, daySeed, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 2, 8, null, 75)
                    }
                } else {
                    pickVariant(pullPref, daySeed, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, it.defaultHoldSeconds, 75)
                    }
                }
                pickVariant(corePref, daySeed, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 3, 10, it.defaultHoldSeconds, 60)
                }
            }
            CalisthenicsObjective.CORE_AND_COMPRESSION -> {
                pickVariant(corePref, daySeed, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 3, 10, it.defaultHoldSeconds, 60)
                }
                pickVariant(corePref, daySeed + 1, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 3, 10, it.defaultHoldSeconds, 60)
                }
                if (input.slotIndex == 0) {
                    pickVariant(pushPref, daySeed, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 75)
                    }
                    pickVariant(pullPref, daySeed, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, it.defaultHoldSeconds, 75)
                    }
                } else if (input.slotIndex == 1) {
                    pickVariant(pullPref, daySeed, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 75)
                    }
                    pickVariant(pushPref, daySeed + 1, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, null, 75)
                    }
                } else {
                    pickVariant(legsPref, daySeed, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 75)
                    }
                    pickVariant(pullPref, daySeed, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, it.defaultHoldSeconds, 75)
                    }
                }
            }
            CalisthenicsObjective.SINGLE_LEG_CONTROL -> {
                val includeLegs = input.slotIndex != 1 || input.calDaysPerWeek < 3
                if (includeLegs) {
                    pickVariant(legsPref, daySeed, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 6, null, 90)
                    }
                    pickVariant(listOf("cal-cossack-squat", "cal-sl-glute-bridge", "cal-sissy-squat", "cal-rfess", "cal-split-squat"), daySeed + 1, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, null, 75)
                    }
                } else {
                    pickVariant(pullPref, daySeed, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 75)
                    }
                    pickVariant(pushPref, daySeed, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, null, 75)
                    }
                }
                if (input.slotIndex % 2 == 0) {
                    pickVariant(pushPref, daySeed, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, null, 75)
                    }
                } else {
                    val altUpper = pickVariant(pullPref, daySeed + 1, usedIds(), CalisthenicsDomain.PULL_UP)
                        ?: pickVariant(pushPref, daySeed + 1, usedIds(), CalisthenicsDomain.PUSH_UP)
                    altUpper?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, it.defaultHoldSeconds, 75)
                    }
                }
                pickVariant(corePref, daySeed, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                }
            }
            else -> {
                val slotMode = when (split) {
                    CalisthenicsJourneySplit.FULL_BODY_JOURNEY,
                    CalisthenicsJourneySplit.FULL_BODY_A_B -> "FULL"
                    CalisthenicsJourneySplit.PUSH_PULL_CONTROL -> when (input.slotIndex % 3) {
                        0 -> "PUSH"
                        1 -> "PULL"
                        else -> "LEGS"
                    }
                    CalisthenicsJourneySplit.UPPER_LOWER_A_B -> if (input.slotIndex % 2 == 0) "UPPER" else "LOWER"
                    CalisthenicsJourneySplit.PPL_PLUS_SKILL_5D -> when (input.slotIndex) {
                        0 -> "PUSH"
                        1 -> "PULL"
                        2 -> "LEGS"
                        3 -> "UPPER_CORE"
                        else -> "SKILL_HOLD"
                    }
                    CalisthenicsJourneySplit.PPL_A_B_6D -> when (input.slotIndex % 6) {
                        0, 3 -> "PUSH"
                        1, 4 -> "PULL"
                        else -> "LEGS"
                    }
                }

                when (slotMode) {
                    "UPPER" -> {
                        pickVariant(pushPref, input.variantIndex, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90)
                        }
                        pickVariant(pullPref, input.variantIndex, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90)
                        }
                        pickVariant(
                            listOf("cal-pike-pushup", "cal-dip-assisted", "cal-ring-dip", "br", "cal-scapular-pushup", "cal-support-hold"),
                            input.variantIndex,
                            usedIds()
                        )?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, it.defaultHoldSeconds, 75)
                        }
                        pickVariant(corePref, input.variantIndex, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                        }
                    }
                    "UPPER_CORE" -> {
                        pickVariant(pushPref, input.variantIndex + 1, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90)
                        }
                        pickVariant(pullPref, input.variantIndex + 1, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90)
                        }
                        pickVariant(corePref, input.variantIndex, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 3, 10, it.defaultHoldSeconds, 60)
                        }
                        pickVariant(corePref, input.variantIndex + 1, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                        }
                    }
                    "SKILL_HOLD" -> {
                        pickVariant(staticHoldPref, input.variantIndex + 1, usedIds())?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 3, 6, it.defaultHoldSeconds, 75)
                        }
                        pickVariant(pushPref, input.variantIndex + 1, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, null, 75)
                        }
                        pickVariant(corePref, input.variantIndex + 1, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                        }
                    }
                    "LOWER", "LEGS" -> {
                        pickVariant(legsPref, input.variantIndex, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 6, null, 90)
                        }
                        pickVariant(listOf("cal-cossack-squat", "cal-sl-glute-bridge", "cal-sissy-squat", "cal-rfess", "cal-split-squat"), input.variantIndex + 1, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, null, 75)
                        }
                        if (slots.size < 3) {
                            pickVariant(corePref, input.variantIndex, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                                slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 2, 10, it.defaultHoldSeconds, 60)
                            }
                        }
                        pickVariant(corePref, input.variantIndex + 1, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                        }
                    }
                    "PULL" -> {
                        pickVariant(pullPref, input.variantIndex, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 6, null, 105)
                        }
                        pickVariant(listOf("br", "cal-ring-row", "cal-archer-row", "cal-scap-hang", "cal-negative-pullup", "ap"), input.variantIndex, usedIds())?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, it.defaultHoldSeconds, 75)
                        }
                        pickVariant(staticHoldPref + listOf("cal-chinup", "cal-neutral-pullup", "cal-scapular-pushup"), input.variantIndex, usedIds())?.let {
                            if (!it.exercise.isLowerBody) {
                                slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 3, 8, it.defaultHoldSeconds, 75)
                            }
                        }
                        pickVariant(corePref, input.variantIndex, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                        }
                        if (slots.size < 4) {
                            pickVariant(corePref, input.variantIndex + 1, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                                slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                            }
                        }
                    }
                    "PUSH" -> {
                        pickVariant(pushPref, input.variantIndex, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 90)
                        }
                        pickVariant(listOf("cal-pike-pushup", "cal-dip-assisted", "cal-support-hold", "cal-scapular-pushup", "cal-ring-dip", "wall-push"), input.variantIndex, usedIds())?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 6, it.defaultHoldSeconds, 75)
                        }
                        pickVariant(pushPref, input.variantIndex + 1, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, it.defaultHoldSeconds, 75)
                        }
                        pickVariant(corePref, input.variantIndex, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                        }
                    }
                    else -> {
                        pickVariant(pushPref, input.variantIndex, usedIds(), CalisthenicsDomain.PUSH_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90)
                        }
                        pickVariant(pullPref, input.variantIndex, usedIds(), CalisthenicsDomain.PULL_UP)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90)
                        }
                        pickVariant(legsPref, input.variantIndex, usedIds(), CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 2, 6, null, 90)
                        }
                        pickVariant(corePref, input.variantIndex, usedIds(), CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                            slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 2, 10, it.defaultHoldSeconds, 60)
                        }
                    }
                }
            }
        }

        val distinctSlots = slots.distinctBy { it.node.exercise.id }.toMutableList()
        if (distinctSlots.size < 2) {
            pickFirst(listOf("fc", "side-plank", "wall-push"))?.let { extra ->
                if (distinctSlots.none { it.node.exercise.id == extra.exercise.id }) {
                    distinctSlots += makeSlot(extra, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 8, extra.defaultHoldSeconds, 60)
                }
            }
        }

        // Fit strictly inside MOD-13 time budget (T_max <= T_day_max)
        var compactFallback = false
        val budgetSec = input.dailyTimeBudgetMinutes?.times(60)
        if (budgetSec != null) {
            while (distinctSlots.size > 2 &&
                Engine17CalisthenicsDurationCalculator.estimateSessionDuration(distinctSlots, compactFallback).maxSeconds > budgetSec
            ) {
                distinctSlots.removeAt(distinctSlots.lastIndex)
            }
            var interval = Engine17CalisthenicsDurationCalculator.estimateSessionDuration(distinctSlots, compactFallback)
            if (interval.maxSeconds > budgetSec) {
                compactFallback = true
                for (i in distinctSlots.indices) {
                    val cur = distinctSlots[i]
                    if (cur.sets > 2) distinctSlots[i] = cur.copy(sets = 2, restSeconds = minOf(cur.restSeconds, 60))
                }
                interval = Engine17CalisthenicsDurationCalculator.estimateSessionDuration(distinctSlots, true)
                if (interval.maxSeconds > budgetSec) {
                    throw PlanConstraint("CALISTHENICS_TIME_BUDGET_CONFLICT: Even a minimum effective calisthenics dose exceeds your daily time limit.")
                }
            }
        }

        val finalDuration = Engine17CalisthenicsDurationCalculator.estimateSessionDuration(distinctSlots, compactFallback)
        val isLower = distinctSlots.any { it.node.exercise.isLowerBody }
        return CalisthenicsConstructedSession(
            dayLabel = dayLabel,
            splitKind = split,
            slots = distinctSlots,
            durationInterval = finalDuration,
            isLowerBodySession = isLower,
            isCompactFallback = compactFallback,
            coachNotes = buildList {
                add("Stop every set immediately before technical form breaks down.")
                if (distinctSlots.any { it.blockRole == CalisthenicsBlockRole.FRESH_SKILL_PRACTICE }) {
                    add("Skill balance work comes FIRST while nervous system is fresh.")
                }
            }
        )
    }

    private fun dayLabelFor(split: CalisthenicsJourneySplit, slotIndex: Int, objective: CalisthenicsObjective): String = when (split) {
        CalisthenicsJourneySplit.FULL_BODY_JOURNEY -> if (objective == CalisthenicsObjective.HANDSTAND_FOUNDATIONS) "Handstand Skill & Foundation" else "Full Body Calisthenics"
        CalisthenicsJourneySplit.FULL_BODY_A_B -> if (slotIndex % 2 == 0) "Journey Full Body A" else "Journey Full Body B"
        CalisthenicsJourneySplit.PUSH_PULL_CONTROL -> when (slotIndex % 3) {
            0 -> "Push & Balance"
            1 -> "Pull & Scapular Control"
            else -> "Core, Legs & Technique"
        }
        CalisthenicsJourneySplit.UPPER_LOWER_A_B -> when (slotIndex % 4) {
            0 -> "Upper Mastery A"
            1 -> "Single-Leg & Core A"
            2 -> "Upper Mastery B"
            else -> "Single-Leg & Core B"
        }
        CalisthenicsJourneySplit.PPL_PLUS_SKILL_5D -> when (slotIndex % 5) {
            0 -> "Push Progression"
            1 -> "Pull Progression"
            2 -> "Single-Leg Control"
            3 -> "Upper & Core Strength"
            else -> "Skill & Static Holds"
        }
        CalisthenicsJourneySplit.PPL_A_B_6D -> when (slotIndex % 6) {
            0 -> "Push A"
            1 -> "Pull A"
            2 -> "Legs & Core A"
            3 -> "Push B"
            4 -> "Pull B"
            else -> "Legs & Skill B"
        }
    }
}

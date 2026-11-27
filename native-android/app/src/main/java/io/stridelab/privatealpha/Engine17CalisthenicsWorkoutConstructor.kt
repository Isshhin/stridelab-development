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
        val pullPref = when {
            hasBar && input.pullLevel >= 2 -> listOf("e2", "cal-chinup", "cal-neutral-pullup", "cal-wide-pullup", "cal-commando-pullup", "ap", "br")
            hasBar && input.pullLevel == 1 -> listOf("ap", "cal-negative-pullup", "cal-ring-row", "br")
            hasBar -> listOf("cal-scap-hang", "ap", "br")
            hasRings -> listOf("cal-ring-row", "cal-archer-row", "br")
            else -> listOf("br", "cal-sl-glute-bridge")
        }
        val pushPref = when (input.experience) {
            0 -> listOf("wall-push", "cal-standard-pushup", "cal-scapular-pushup", "e4")
            1 -> listOf("cal-standard-pushup", "cal-decline-pushup", "cal-diamond-pushup", "cal-pike-pushup", "e4")
            else -> listOf("cal-decline-pushup", "cal-diamond-pushup", "cal-pike-pushup", "cal-archer-pushup", "cal-ring-pushup", "cal-ring-dip", "e4")
        }
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
            var sets = if (input.isDeloadOrRecovery) max(2, (baseSets * 0.60).roundToInt())
            else max(1, (baseSets * input.setMultiplier).roundToInt().coerceIn(2, 4))
            var reps = baseReps
            if (node.isEccentricHighTendonStrain && input.experience <= 1 && reps != null) {
                val (cSets, cReps) = Engine17CalisthenicsProgressionEngine.capEccentricDoseForTendonSafety(true, sets, reps)
                sets = cSets
                reps = cReps
            }
            val effHold = if (node.exercise.isIsometricHold || holdSec != null) {
                input.isometricOverrideSeconds ?: holdSec ?: node.defaultHoldSeconds ?: spec.isometricSeconds.first
            } else null
            return CalisthenicsWorkoutSlot(
                node = node,
                blockRole = role,
                sets = sets,
                reps = if (effHold != null) null else reps,
                holdSeconds = effHold,
                restSeconds = restSec,
                targetRir = effRir,
                timeBoxMinutes = if (role == CalisthenicsBlockRole.FRESH_SKILL_PRACTICE) skillMins else null
            )
        }

        // 1. Fresh Skill Practice FIRST when appropriate (Handstands, Skills, Freeletics Pull-up Skill)
        if (input.objective in setOf(
                CalisthenicsObjective.HANDSTAND_FOUNDATIONS,
                CalisthenicsObjective.SKILL_AND_STATIC_HOLDS
            ) || (split == CalisthenicsJourneySplit.PPL_PLUS_SKILL_5D && input.slotIndex == 4)
        ) {
            val skillNode = pickFirst(handstandSkillPref, CalisthenicsDomain.HANDSTAND_SKILL)
                ?: pickFirst(corePref, CalisthenicsDomain.CORE_AND_L_SIT)
            if (skillNode != null) {
                slots += CalisthenicsWorkoutSlot(
                    node = skillNode,
                    blockRole = CalisthenicsBlockRole.FRESH_SKILL_PRACTICE,
                    sets = 3,
                    reps = null,
                    holdSeconds = input.isometricOverrideSeconds ?: skillNode.defaultHoldSeconds ?: 20,
                    restSeconds = 60,
                    targetRir = 2..3,
                    timeBoxMinutes = skillMins,
                    coachingTag = "Fresh skill practice first ($skillMins min)"
                )
            }
        }

        // 2. Main Freeletics-Inspired Journey Selection by Objective & Split Slot
        when {
            input.objective == CalisthenicsObjective.PULL_UP_PROGRESSION -> {
                if (hasBar && input.pullLevel == 0) {
                    // Scenario S05 Prerequisite Cluster
                    listOf("ap", "cal-negative-pullup", "br").mapNotNull { id -> eligible.firstOrNull { it.exercise.id == id } }.forEach { n ->
                        slots += makeSlot(n, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, if (n.isEccentricHighTendonStrain) 2 else 3, if (n.isEccentricHighTendonStrain) 3 else 6, null, 90)
                    }
                } else {
                    pickFirst(pullPref, CalisthenicsDomain.PULL_UP)?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, spec.defaultRestSeconds) }
                    pickFirst(listOf("br", "cal-ring-row", "cal-scap-hang"))?.let { slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, null, 75) }
                }
                pickFirst(corePref, CalisthenicsDomain.CORE_AND_L_SIT)?.let { slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60) }
            }
            input.objective == CalisthenicsObjective.PUSH_UP_PROGRESSION -> {
                pickFirst(pushPref, CalisthenicsDomain.PUSH_UP)?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, spec.defaultRestSeconds) }
                pickFirst(listOf("cal-pike-pushup", "cal-support-hold", "wall-push"))?.let { slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 6, it.defaultHoldSeconds, 75) }
                pickFirst(corePref, CalisthenicsDomain.CORE_AND_L_SIT)?.let { slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60) }
            }
            input.objective == CalisthenicsObjective.HANDSTAND_FOUNDATIONS -> {
                pickFirst(listOf("cal-pike-pushup", "wall-push", "e4"), CalisthenicsDomain.PUSH_UP)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 6, null, 90)
                }
                pickFirst(corePref, CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                    slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 3, null, input.isometricOverrideSeconds ?: 20, 60)
                }
            }
            input.objective == CalisthenicsObjective.CORE_AND_COMPRESSION -> {
                val c1 = pickFirst(corePref, CalisthenicsDomain.CORE_AND_L_SIT)
                val c2 = eligible.firstOrNull { it.domain == CalisthenicsDomain.CORE_AND_L_SIT && it.exercise.id != c1?.exercise?.id }
                    ?: pickFirst(listOf("fc", "side-plank"))
                c1?.let { slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 3, 10, it.defaultHoldSeconds, 60) }
                c2?.let { slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 3, 10, it.defaultHoldSeconds, 60) }
            }
            else -> {
                val wantUpperOnly = (split == CalisthenicsJourneySplit.UPPER_LOWER_A_B && input.slotIndex % 2 == 0)
                val wantLowerOnly = (split == CalisthenicsJourneySplit.UPPER_LOWER_A_B && input.slotIndex % 2 == 1) ||
                    ((split == CalisthenicsJourneySplit.PPL_PLUS_SKILL_5D || split == CalisthenicsJourneySplit.PPL_A_B_6D) && input.slotIndex % 3 == 2) ||
                    input.objective == CalisthenicsObjective.SINGLE_LEG_CONTROL
                val wantPullOnly = (split == CalisthenicsJourneySplit.PUSH_PULL_CONTROL && input.slotIndex == 1) ||
                    ((split == CalisthenicsJourneySplit.PPL_PLUS_SKILL_5D || split == CalisthenicsJourneySplit.PPL_A_B_6D) && input.slotIndex % 3 == 1)
                val wantPushOnly = (split == CalisthenicsJourneySplit.PUSH_PULL_CONTROL && input.slotIndex == 0) ||
                    ((split == CalisthenicsJourneySplit.PPL_PLUS_SKILL_5D || split == CalisthenicsJourneySplit.PPL_A_B_6D) && input.slotIndex % 3 == 0)

                if (wantUpperOnly) {
                    val primaryPush = pickVariant(pushPref, input.variantIndex, emptySet(), CalisthenicsDomain.PUSH_UP)
                    primaryPush?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90) }
                    val usedUpper = slots.map { s -> s.node.exercise.id }.toSet()
                    val upperPullPref = pullPref.filter { it != "cal-sl-glute-bridge" }
                    pickVariant(upperPullPref, input.variantIndex, usedUpper, CalisthenicsDomain.PULL_UP)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90)
                    }
                    val usedAfterPull = slots.map { s -> s.node.exercise.id }.toSet()
                    pickVariant(
                        listOf("cal-pike-pushup", "cal-dip-assisted", "cal-ring-dip", "br", "cal-scapular-pushup", "cal-support-hold"),
                        input.variantIndex,
                        usedAfterPull
                    )?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, it.defaultHoldSeconds, 75)
                    }
                    val usedFinal = slots.map { s -> s.node.exercise.id }.toSet()
                    pickVariant(corePref, input.variantIndex, usedFinal, CalisthenicsDomain.CORE_AND_L_SIT)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60)
                    }
                } else if (wantLowerOnly) {
                    val legPrimary = pickVariant(legsPref, input.variantIndex, emptySet(), CalisthenicsDomain.SINGLE_LEG_SQUAT)
                    legPrimary?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 6, null, 90) }
                    val used = slots.map { s -> s.node.exercise.id }.toSet()
                    pickVariant(listOf("cal-cossack-squat", "cal-sl-glute-bridge", "cal-sissy-squat", "cal-rfess"), input.variantIndex, used)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 2, 8, null, 75)
                    }
                    pickVariant(corePref, input.variantIndex, used, CalisthenicsDomain.CORE_AND_L_SIT)?.let { slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60) }
                } else if (wantPullOnly) {
                    val pullPrimary = pickVariant(pullPref, input.variantIndex, emptySet(), CalisthenicsDomain.PULL_UP)
                    pullPrimary?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 6, null, 105) }
                    val used = slots.map { s -> s.node.exercise.id }.toSet()
                    pickVariant(listOf("br", "cal-ring-row", "cal-archer-row", "cal-scap-hang", "cal-negative-pullup"), input.variantIndex, used)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 8, it.defaultHoldSeconds, 75)
                    }
                    pickVariant(corePref, input.variantIndex, used, CalisthenicsDomain.CORE_AND_L_SIT)?.let { slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60) }
                } else if (wantPushOnly) {
                    val pushPrimary = pickVariant(pushPref, input.variantIndex, emptySet(), CalisthenicsDomain.PUSH_UP)
                    pushPrimary?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 90) }
                    val used = slots.map { s -> s.node.exercise.id }.toSet()
                    pickVariant(listOf("cal-pike-pushup", "cal-dip-assisted", "cal-support-hold", "cal-scapular-pushup", "cal-ring-dip"), input.variantIndex, used)?.let {
                        slots += makeSlot(it, CalisthenicsBlockRole.PREREQUISITE_CLUSTER, 3, 6, it.defaultHoldSeconds, 75)
                    }
                    pickVariant(corePref, input.variantIndex, used, CalisthenicsDomain.CORE_AND_L_SIT)?.let { slots += makeSlot(it, CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER, 2, 10, it.defaultHoldSeconds, 60) }
                } else {
                    pickVariant(pushPref, input.variantIndex, emptySet(), CalisthenicsDomain.PUSH_UP)?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90) }
                    val usedAfterPush = slots.map { s -> s.node.exercise.id }.toSet()
                    pickVariant(pullPref, input.variantIndex, usedAfterPush, CalisthenicsDomain.PULL_UP)?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, spec.defaultRepRange.first, null, 90) }
                    val usedAfterPull = slots.map { s -> s.node.exercise.id }.toSet()
                    pickVariant(legsPref, input.variantIndex, usedAfterPull, CalisthenicsDomain.SINGLE_LEG_SQUAT)?.let { slots += makeSlot(it, CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 2, 6, null, 90) }
                    val usedAfterLegs = slots.map { s -> s.node.exercise.id }.toSet()
                    pickVariant(corePref, input.variantIndex, usedAfterLegs, CalisthenicsDomain.CORE_AND_L_SIT)?.let { slots += makeSlot(it, CalisthenicsBlockRole.ISOMETRIC_HOLD, 2, 10, it.defaultHoldSeconds, 60) }
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

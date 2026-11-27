package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.floor

/** All 8 Conditioning Families from Track 6 §8. */
internal enum class ConditioningFamily8(
    val displayLabel: String,
    val isHighStress: Boolean,
    val isLowerBodyHeavy: Boolean
) {
    CONTINUOUS_AEROBIC("Continuous aerobic", false, false),
    MODALITY_HIIT("Modality HIIT intervals", true, true),
    STRUCTURED_CIRCUITS("Structured circuits", false, false),
    EMOM("EMOM work capacity", false, false),
    QUALITY_PACED_AMRAP("Quality-paced AMRAP", false, false),
    WORK_REST_INTERVALS("Work-rest intervals", false, true),
    MIXED_MODAL_CONDITIONING("Mixed-modal conditioning", true, true),
    MUSCULAR_ENDURANCE_CIRCUITS("Muscular endurance circuit", false, false)
}

internal enum class ConditioningModality {
    BIKE,
    ROW,
    SKI_ERG,
    SWIM,
    ELLIPTICAL,
    BODYWEIGHT_CIRCUIT,
    MIXED_MODAL
}

internal data class ConditioningExerciseDescriptor(
    val id: String,
    val name: String,
    val modality: ConditioningModality,
    val difficulty: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipmentOptions: List<Set<String>>,
    val isComplexSkillOrAxial: Boolean,
    val cue: String
)

internal data class EmomScalingDecision(
    val scaled: Boolean,
    val newRepsPerMinute: Int?,
    val estimatedWorkSecondsPerMinute: Double,
    val switchedToWorkRestFormat: Boolean,
    val cadenceProvenance: String,
    val liveStopRule: String
)

internal data class ConditioningSingleAxisEvaluation(
    val valid: Boolean,
    val axesChanged: List<String>,
    val reason: String?
)

internal data class ConditioningBlockDuration(
    val minSeconds: Int,
    val maxSeconds: Int
) {
    val conservativeMinutes: Int get() = (maxSeconds + 59) / 60
}

/**
 * Engine 1.8 Complete 8-Family Conditioning Constructor & Solo Conditioning Builder:
 * - Supports all 8 Track 6 §8 conditioning families without running benchmarks or HR
 * - Repaired POL-048 (> 40.0 s/min EMOM trigger) + MOD-17 cadence-qualified rep scaling
 * - MD-08 proficiency blocking of complex movements for novices
 * - MD-09 strict single-axis progression validation
 * - MD-10 strict modality HR isolation (zero cross-application of running HR to bike/row/swim)
 * - MD-06 shared UnifiedLocalMuscleLedger export & blockedOverlappingMuscles deduplication
 */
internal object Engine18ConditioningWorkoutConstructor {

    private val lockRegex = Regex(""".*-w([1-9][0-9]*)-cond(0|[1-9][0-9]*)$""")

    private val complexHighSkillIds = setOf(
        "cond-double-unders",
        "str-conv-deadlift",
        "str-back-squat",
        "str-front-squat",
        "str-overhead-press",
        "str-dumbbell-snatch"
    )

    fun availableEquipmentTokens(request: PlanRequest): Set<String> = buildSet {
        if ((request.equipment["erg"] ?: 0) >= 1 || (request.equipment["rower"] ?: 0) >= 1 ||
            (request.equipment["bike"] ?: 0) >= 1
        ) add("erg")
        if ((request.equipment["rope"] ?: 0) >= 1) add("rope")
        if ((request.equipment["ropes"] ?: 0) >= 1) add("ropes")
        if ((request.equipment["kb"] ?: 0) >= 1 || (request.equipment["kettlebells"] ?: 0) >= 1) add("kettlebell")
        if ((request.equipment["sled"] ?: 0) >= 1) add("sled")
        if ((request.equipment["db"] ?: 0) >= 1 || (request.equipment["dumbbells"] ?: 0) >= 1) add("dumbbells")
        if ((request.equipment["box"] ?: 0) >= 1 || (request.equipment["bench"] ?: 0) >= 1) add("box")
    }

    /** MD-10: Returns null whenever HR profile comes from RUNNING or a different physiological modality. */
    fun resolveModalityHrZones(
        targetModality: ConditioningModality,
        hrProfileModality: M2BPhysiologicalModality,
        verifiedProfile: ExecutionHrZoneProfile?
    ): ExecutionHrZoneProfile? {
        if (verifiedProfile == null) return null
        if (hrProfileModality == M2BPhysiologicalModality.RUNNING) return null
        val matches = when (targetModality) {
            ConditioningModality.BIKE -> hrProfileModality == M2BPhysiologicalModality.CYCLING
            ConditioningModality.SWIM -> hrProfileModality == M2BPhysiologicalModality.SWIMMING
            else -> false
        }
        return if (matches) verifiedProfile else null
    }

    /**
     * MOD-17 / CON-22 + Repaired POL-048 (> 40.0 s/min EMOM trigger protecting >= 20.0 s rest).
     */
    fun scaleEmomReps(
        oldReps: Int,
        observedWorkSeconds: Double,
        targetRestSeconds: Double = Engine18HybridPolicyRegistry.MOD_17_EMOM_DEFAULT_TARGET_REST_SECONDS
    ): EmomScalingDecision {
        require(oldReps >= 1 && observedWorkSeconds.isFinite() && observedWorkSeconds > 0.0 && observedWorkSeconds <= 60.0)
        require(
            targetRestSeconds.isFinite() &&
                targetRestSeconds in Engine18HybridPolicyRegistry.MOD_17_EMOM_MIN_TARGET_REST_SECONDS..Engine18HybridPolicyRegistry.MOD_17_EMOM_MAX_TARGET_REST_SECONDS
        )
        if (observedWorkSeconds <= Engine18HybridPolicyRegistry.POL_048_EMOM_MAX_WORK_SECONDS_PER_MIN + 1e-9) {
            return EmomScalingDecision(
                scaled = false,
                newRepsPerMinute = oldReps,
                estimatedWorkSecondsPerMinute = observedWorkSeconds,
                switchedToWorkRestFormat = false,
                cadenceProvenance = "WITHIN_EMOM_REST_FLOOR",
                liveStopRule = "Stop minute at 40 s if reps remain unfinished to preserve 20 s recovery."
            )
        }
        val rNew = floor(oldReps.toDouble() * ((60.0 - targetRestSeconds) / observedWorkSeconds)).toInt()
        if (rNew < 1) {
            return EmomScalingDecision(
                scaled = true,
                newRepsPerMinute = null,
                estimatedWorkSecondsPerMinute = 30.0,
                switchedToWorkRestFormat = true,
                cadenceProvenance = "APPROXIMATE_CADENCE",
                liveStopRule = "Switch to 30 s work / 30 s rest intervals to protect movement quality."
            )
        }
        val estNew = observedWorkSeconds * (rNew.toDouble() / oldReps.toDouble())
        return EmomScalingDecision(
            scaled = true,
            newRepsPerMinute = rNew,
            estimatedWorkSecondsPerMinute = estNew,
            switchedToWorkRestFormat = false,
            cadenceProvenance = "APPROXIMATE_CADENCE",
            liveStopRule = "Scaled to $rNew reps/min (~${estNew.toInt()} s work). Stop at 40 s if pace slows."
        )
    }

    /** MD-08 proficiency gate: blocks advanced/complex conditioning movements when novice. */
    fun isMovementAllowedForConditioning(
        exerciseId: String,
        difficulty: String,
        experience: Int
    ): Boolean {
        if (experience <= 0 && (exerciseId in complexHighSkillIds || difficulty == "advanced")) {
            return false
        }
        return true
    }

    /** MD-09 single-axis conditioning progression invariant. */
    fun validateSingleAxisConditioningProgression(
        deltaDurationRel: Double,
        deltaRounds: Int,
        deltaLoadRel: Double,
        densityIncreased: Boolean,
        switchedToNewHiitOrMixedFamily: Boolean
    ): ConditioningSingleAxisEvaluation {
        val axes = buildList {
            if (deltaDurationRel > 1e-6) add("DURATION")
            if (deltaRounds > 0) add("ROUNDS")
            if (deltaLoadRel > 1e-6) add("LOAD")
            if (densityIncreased) add("DENSITY")
            if (switchedToNewHiitOrMixedFamily) add("NEW_HIGH_STRESS_FAMILY")
        }
        val overSingleAxis = axes.size > 1
        val durTooBig = deltaDurationRel > Engine18HybridPolicyRegistry.POL_059_COND_MINOR_DUR_REL_CAP + 1e-6
        val valid = !overSingleAxis && !durTooBig
        val reason = when {
            overSingleAxis -> "MD-09 violation: conditioning changed multiple axes concurrently (${axes.joinToString(", ")})."
            durTooBig -> "Conditioning duration jump exceeds +10% weekly guardrail."
            else -> null
        }
        return ConditioningSingleAxisEvaluation(valid, axes, reason)
    }

    fun catalogDescriptors(): List<ConditioningExerciseDescriptor> {
        val builtIns = listOf(
            ConditioningExerciseDescriptor(
                id = "cond-step-jack",
                name = "Low-impact step jacks",
                modality = ConditioningModality.BODYWEIGHT_CIRCUIT,
                difficulty = "foundation",
                primaryMuscles = listOf("calves", "gluteus_medius"),
                secondaryMuscles = listOf("anterior_deltoid", "abdominals"),
                equipmentOptions = listOf(emptySet()),
                isComplexSkillOrAxial = false,
                cue = "Step briskly side to side at conversational Talk Test effort without jarring impact."
            ),
            ConditioningExerciseDescriptor(
                id = "cond-tempo-march",
                name = "Brisk low-impact aerobic march and shadow boxing",
                modality = ConditioningModality.BODYWEIGHT_CIRCUIT,
                difficulty = "foundation",
                primaryMuscles = listOf("quadriceps", "hip_flexors"),
                secondaryMuscles = listOf("anterior_deltoid", "trunk_stabilizers"),
                equipmentOptions = listOf(emptySet()),
                isComplexSkillOrAxial = false,
                cue = "Keep nasal/conversational breathing steady at RPE 3 to 4."
            ),
            ConditioningExerciseDescriptor(
                id = "cond-kb-dead-bug-circuit",
                name = "Alternating lower-hinge and core circuit",
                modality = ConditioningModality.BODYWEIGHT_CIRCUIT,
                difficulty = "foundation",
                primaryMuscles = listOf("gluteus_maximus", "abdominals"),
                secondaryMuscles = listOf("hamstrings", "trunk_stabilizers"),
                equipmentOptions = listOf(emptySet()),
                isComplexSkillOrAxial = false,
                cue = "Move deliberately between stations; pause early if trunk alignment slips."
            )
        )
        val expanded = ExpandedExerciseCatalog.conditioningRecords.map { rec ->
            val mod = when {
                "erg" in rec.id || "row" in rec.id || "ski" in rec.id || "bike" in rec.id -> ConditioningModality.BIKE
                "sled" in rec.id || "kettlebell" in rec.id || "carry" in rec.id || "thruster" in rec.id -> ConditioningModality.MIXED_MODAL
                else -> ConditioningModality.BODYWEIGHT_CIRCUIT
            }
            ConditioningExerciseDescriptor(
                id = rec.id,
                name = rec.name,
                modality = mod,
                difficulty = rec.difficulty,
                primaryMuscles = rec.primaryMuscles,
                secondaryMuscles = rec.secondaryMuscles,
                equipmentOptions = rec.equipmentOptions,
                isComplexSkillOrAxial = rec.id in complexHighSkillIds || rec.difficulty == "advanced",
                cue = rec.technique.firstOrNull() ?: "Maintain clean technique at controlled effort."
            )
        }
        return (builtIns + expanded).distinctBy { it.id }
    }

    fun familyFromGoal(goal: String?, experience: Int, hasErgOrExternalEquipment: Boolean): ConditioningFamily8 {
        val g = goal?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            "emom" in g -> ConditioningFamily8.EMOM
            "amrap" in g -> ConditioningFamily8.QUALITY_PACED_AMRAP
            "work rest" in g || "work-rest" in g -> ConditioningFamily8.WORK_REST_INTERVALS
            "threshold" in g || "hiit" in g || "interval" in g ->
                if (experience >= 1 && hasErgOrExternalEquipment) ConditioningFamily8.MODALITY_HIIT
                else ConditioningFamily8.WORK_REST_INTERVALS
            "mixed" in g -> ConditioningFamily8.MIXED_MODAL_CONDITIONING
            "muscular endurance" in g -> ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS
            "circuit" in g -> ConditioningFamily8.STRUCTURED_CIRCUITS
            "steady" in g || "continuous" in g || "aerobic" in g -> ConditioningFamily8.CONTINUOUS_AEROBIC
            else -> if (experience == 0) ConditioningFamily8.CONTINUOUS_AEROBIC else ConditioningFamily8.STRUCTURED_CIRCUITS
        }
    }

    fun validateSoloRequest(
        request: PlanRequest,
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = Instant.now()
    ) {
        if (safetyRestrictions?.hasHardBlock(AthleteDiscipline.CONDITIONING, evaluatedAtUtc) == true ||
            request.effectiveSafetyCap("cond", safetyRestrictions, evaluatedAtUtc) == SafetyIntensityCap.REST_ONLY
        ) {
            throw PlanConstraint("SAFETY_RESTRICTION_HOLD: Conditioning planning is paused while an active safety restriction is unresolved.")
        }
        if (request.policyChoice != "engine15" ||
            request.runDays != 0 ||
            request.strengthDays != 0 ||
            request.calDays != 0 ||
            request.conditioningDays !in 1..6 ||
            request.weeks !in 1..16 ||
            request.qualityCount != 0 ||
            request.days.isEmpty() ||
            request.days.any { it !in 1..7 } ||
            request.days.size < request.conditioningDays ||
            request.experience !in 0..2 ||
            (request.timeBudgetEnabled && request.minutes !in 15..120) ||
            request.disciplineGoals.keys.any { it != "cond" } ||
            request.weeklySlots.keys.any { it != "cond" } ||
            request.limitations.isNotBlank() ||
            request.recoveryNotes.isNotBlank()
        ) {
            throw PlanConstraint("CONDITIONING_REQUEST_UNSUPPORTED: Choose 1 to 6 conditioning sessions, valid weekdays, and resolve notes.")
        }
        val assigned = request.weeklySlots["cond"]
        if (assigned != null && (assigned.size != request.conditioningDays ||
                assigned.distinct().size != request.conditioningDays ||
                assigned.any { it !in request.days })
        ) {
            throw PlanConstraint("CONDITIONING_WEEKDAY_ASSIGNMENTS_INVALID: Assign each session to a distinct available weekday.")
        }
        if (request.lockedSessionDays.keys.any { !lockRegex.matches(it) }) {
            throw PlanConstraint("CONDITIONING_SESSION_LOCK_UNSUPPORTED: Lock ID does not match conditioning week and slot.")
        }
        Engine15SessionLocks.validateRequest(request)
    }

    fun rankCandidatesForFamily(
        candidates: List<ConditioningExerciseDescriptor>,
        family: ConditioningFamily8,
        availableTokens: Set<String>,
        experience: Int
    ): List<ConditioningExerciseDescriptor> {
        val lowImpactIntroIds = setOf("cond-step-jack", "cond-tempo-march", "cond-kb-dead-bug-circuit")
        return candidates.sortedWith(
            compareByDescending<ConditioningExerciseDescriptor> { desc ->
                when (family) {
                    ConditioningFamily8.MODALITY_HIIT,
                    ConditioningFamily8.CONTINUOUS_AEROBIC -> when {
                        desc.modality in setOf(ConditioningModality.BIKE, ConditioningModality.ROW, ConditioningModality.SKI_ERG) &&
                            desc.equipmentOptions.any { opt -> opt.isNotEmpty() && availableTokens.containsAll(opt) } -> 5
                        desc.id in setOf("cond-jump-rope-singles", "cond-double-unders", "cond-battle-ropes") &&
                            desc.equipmentOptions.any { opt -> opt.isNotEmpty() && availableTokens.containsAll(opt) } -> 4
                        desc.id in lowImpactIntroIds ->
                            if (family == ConditioningFamily8.CONTINUOUS_AEROBIC && experience == 0 && availableTokens.isEmpty()) 4 else 0
                        else -> 2
                    }
                    ConditioningFamily8.MIXED_MODAL_CONDITIONING,
                    ConditioningFamily8.EMOM,
                    ConditioningFamily8.QUALITY_PACED_AMRAP,
                    ConditioningFamily8.STRUCTURED_CIRCUITS,
                    ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS,
                    ConditioningFamily8.WORK_REST_INTERVALS -> when {
                        desc.modality == ConditioningModality.MIXED_MODAL &&
                            desc.equipmentOptions.any { opt -> opt.isNotEmpty() && availableTokens.containsAll(opt) } -> 5
                        desc.modality in setOf(ConditioningModality.BIKE, ConditioningModality.ROW, ConditioningModality.SKI_ERG) &&
                            desc.equipmentOptions.any { opt -> opt.isNotEmpty() && availableTokens.containsAll(opt) } -> 4
                        desc.equipmentOptions.any { opt -> opt.isNotEmpty() && availableTokens.containsAll(opt) } -> 4
                        desc.id in lowImpactIntroIds ->
                            if (experience == 0 && availableTokens.isEmpty()) 2 else 0
                        else -> 3
                    }
                }
            }.thenBy { it.id }
        )
    }

    private fun selectStationsForSlot(
        ranked: List<ConditioningExerciseDescriptor>,
        desiredCount: Int,
        offset: Int
    ): List<ConditioningExerciseDescriptor> {
        if (ranked.isEmpty()) return emptyList()
        val limit = desiredCount.coerceAtMost(ranked.size)
        val picked = mutableListOf<ConditioningExerciseDescriptor>()
        val usedIds = HashSet<String>()
        var cursor = offset.coerceAtLeast(0) % ranked.size
        var attempts = 0
        while (picked.size < limit && attempts < ranked.size * 2) {
            val cand = ranked[cursor % ranked.size]
            if (cand.id !in usedIds) {
                val sharesPrimary = picked.lastOrNull()?.primaryMuscles?.intersect(cand.primaryMuscles.toSet())?.isNotEmpty() == true
                if (!sharesPrimary || attempts >= ranked.size) {
                    picked += cand
                    usedIds += cand.id
                }
            }
            cursor++
            attempts++
        }
        return picked
    }

    internal data class ConditioningBlockDuration(
        val warmUpMinutes: Int,
        val mainBlockMinutes: Int,
        val coolDownMinutes: Int,
        val totalSeconds: Int
    ) {
        val conservativeMinutes: Int get() = (totalSeconds + 59) / 60
    }

    fun estimateSessionDuration(
        warmUpMinutes: Int,
        coolDownMinutes: Int,
        family: ConditioningFamily8,
        exercises: List<ExercisePrescription>
    ): ConditioningBlockDuration {
        val totalSets = exercises.sumOf { it.sets }
        val mainSec = if (family == ConditioningFamily8.EMOM) {
            totalSets * 60
        } else {
            val workSec = exercises.sumOf { it.sets * (it.seconds ?: 0) }
            val transitionRestSec = if (totalSets > 1) {
                (totalSets - 1) * (exercises.firstOrNull()?.rest ?: 0)
            } else 0
            workSec + transitionRestSec
        }
        val bookendSec = (warmUpMinutes.coerceAtLeast(0) + coolDownMinutes.coerceAtLeast(0)) * 60
        return ConditioningBlockDuration(
            warmUpMinutes = warmUpMinutes.coerceAtLeast(0),
            mainBlockMinutes = (mainSec + 59) / 60,
            coolDownMinutes = coolDownMinutes.coerceAtLeast(0),
            totalSeconds = bookendSec + mainSec
        )
    }

    internal fun bookendMinutes(
        family: ConditioningFamily8,
        timeBudgetEnabled: Boolean,
        budgetMinutes: Int
    ): Pair<Int, Int> {
        val (baseWu, baseCd) = when (family) {
            ConditioningFamily8.CONTINUOUS_AEROBIC -> 5 to 4
            ConditioningFamily8.EMOM -> 6 to 4
            ConditioningFamily8.QUALITY_PACED_AMRAP -> 6 to 4
            ConditioningFamily8.MODALITY_HIIT -> 10 to 5
            ConditioningFamily8.STRUCTURED_CIRCUITS,
            ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS -> 5 to 3
            else -> 6 to 4
        }
        if (timeBudgetEnabled && budgetMinutes < baseWu + baseCd + 8) {
            return if (family == ConditioningFamily8.MODALITY_HIIT) 5 to 3 else 4 to 2
        }
        return baseWu to baseCd
    }

    private fun composeNonEmomIntervals(
        availableMainSec: Int,
        desiredRounds: Int,
        candidates: List<ConditioningExerciseDescriptor>,
        restPerTransitionSec: Int,
        budgetMinutesForError: Int
    ): Triple<List<ConditioningExerciseDescriptor>, Int, Int> {
        var rounds = desiredRounds.coerceAtLeast(1)
        var stCount = candidates.size.coerceAtLeast(1)
        while (true) {
            val n = rounds * stCount
            val effRest = if (n > 1) restPerTransitionSec else 0
            val restTotalSec = (n - 1).coerceAtLeast(0) * effRest
            val workBudgetSec = availableMainSec - restTotalSec
            val perStationWorkSec = if (n > 0) workBudgetSec / n else 0
            if (perStationWorkSec >= 20) {
                return Triple(candidates.take(stCount), rounds, perStationWorkSec)
            }
            if (rounds > 1) {
                rounds--
            } else if (stCount > 1) {
                stCount--
            } else {
                throw PlanConstraint(
                    "CONDITIONING_TIME_BUDGET_CONFLICT: Conditioning time budget ($budgetMinutesForError min) is too tight for warm-up, cool-down, and one 20 s work block."
                )
            }
        }
    }

    fun composeSessions(
        request: PlanRequest,
        id: String,
        allowCalendarReschedule: Boolean = false,
        blockedOverlappingMuscles: Set<String> = emptySet(),
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = Instant.now()
    ): List<PlannedSession> {
        validateSoloRequest(request, safetyRestrictions, evaluatedAtUtc)
        val tokens = availableEquipmentTokens(request)
        val hasEq = tokens.isNotEmpty()
        val goal = request.disciplineGoal("cond")
        val family = familyFromGoal(goal, request.experience, hasEq)

        val eligible = catalogDescriptors().filter { desc ->
            desc.id !in request.excluded &&
                isMovementAllowedForConditioning(desc.id, desc.difficulty, request.experience) &&
                desc.equipmentOptions.any { opt -> opt.isEmpty() || tokens.containsAll(opt) } &&
                (blockedOverlappingMuscles.isEmpty() || desc.primaryMuscles.none { it in blockedOverlappingMuscles })
        }.ifEmpty {
            catalogDescriptors().filter { it.id !in request.excluded && it.equipmentOptions.any { opt -> opt.isEmpty() } }
        }
        if (eligible.isEmpty()) {
            throw PlanConstraint("CONDITIONING_EXERCISE_UNAVAILABLE: All eligible conditioning movements are excluded.")
        }

        val weekdays = request.weeklySlots["cond"] ?: spacedWeekdays(request.days, request.conditioningDays)
        val locks = lockSlots(request)
        val effectiveCap = request.effectiveSafetyCap("cond", safetyRestrictions, evaluatedAtUtc)
        val isSafetyEasy = effectiveCap in setOf(SafetyIntensityCap.EASY_AEROBIC_ONLY, SafetyIntensityCap.NO_HIGH_INTENSITY_Z3)

        val effFamilyBase = if (isSafetyEasy) ConditioningFamily8.CONTINUOUS_AEROBIC else family
        val rankedCandidates = rankCandidatesForFamily(eligible, effFamilyBase, tokens, request.experience)

        return buildList {
            for (week in 1..request.weeks) {
                val weekStart = request.start.plusWeeks((week - 1).toLong())
                val recovery = (week % 4 == 0) || (request.returning && week <= 2)
                val step = ((week - 1) % 4).coerceAtMost(2)
                val effFamily = if (isSafetyEasy) ConditioningFamily8.CONTINUOUS_AEROBIC else family

                val (wuMin, cdMin) = bookendMinutes(effFamily, request.timeBudgetEnabled, request.minutes)
                val wuCdMin = wuMin + cdMin
                if (request.timeBudgetEnabled && request.minutes <= wuCdMin) {
                    throw PlanConstraint(
                        "CONDITIONING_TIME_BUDGET_CONFLICT: Conditioning time budget (${request.minutes} min) cannot fit $wuCdMin min warm-up and cool-down plus work."
                    )
                }

                val (desiredMainMin, desiredRounds, rpeCue) = when (effFamily) {
                    ConditioningFamily8.CONTINUOUS_AEROBIC -> Triple(
                        if (recovery) 15 else (18 + step * 3),
                        1,
                        "RPE 3 to 4 conversational pace (modality HR only if verified for same erg)"
                    )
                    ConditioningFamily8.EMOM -> {
                        val emomMins = if (recovery) 10 else (12 + step * 2)
                        Triple(
                            emomMins,
                            emomMins,
                            "RPE 7 to 8 paced repeatability (at least 20 s rest every minute)"
                        )
                    }
                    ConditioningFamily8.QUALITY_PACED_AMRAP -> Triple(
                        if (recovery) 8 else (10 + step * 2),
                        1,
                        "Quality-Paced AMRAP at RPE 7 to 8 (zero maximal failure)"
                    )
                    ConditioningFamily8.MODALITY_HIIT -> Triple(
                        if (recovery) 10 else (12 + step * 2),
                        4 + step,
                        "RPE 8 to 9 controlled modality intervals (stop if power drops > 15%)"
                    )
                    ConditioningFamily8.STRUCTURED_CIRCUITS,
                    ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS -> {
                        val rds = if (recovery) 2 else (3 + (step / 2))
                        Triple(
                            rds * 4,
                            rds,
                            "RPE 6 to 7 controlled stations, 2 to 3 RIR"
                        )
                    }
                    else -> { // WORK_REST_INTERVALS or MIXED_MODAL
                        val blockM = if (recovery) 12 else (14 + step * 2)
                        Triple(
                            blockM,
                            blockM,
                            "Controlled RPE 7 to 8 on 30 s work / 30 s rest clock"
                        )
                    }
                }

                val maxMainMin = if (request.timeBudgetEnabled) {
                    (request.minutes - wuCdMin).coerceAtLeast(1)
                } else {
                    desiredMainMin
                }
                val cappedMainMin = minOf(desiredMainMin, maxMainMin)

                val multiStation = effFamily in setOf(
                    ConditioningFamily8.STRUCTURED_CIRCUITS,
                    ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS,
                    ConditioningFamily8.QUALITY_PACED_AMRAP,
                    ConditioningFamily8.EMOM,
                    ConditioningFamily8.MIXED_MODAL_CONDITIONING
                )

                for (slot in 0 until request.conditioningDays) {
                    val targetDow = weekdays[slot]
                    val naturalDate = weekStart.plusDays(((targetDow - weekStart.dayOfWeek.value + 7) % 7).toLong())
                    val lockDate = locks[week to slot]
                    if (lockDate != null && !allowCalendarReschedule && lockDate != naturalDate) {
                        throw PlanConstraint("CONDITIONING_SESSION_LOCK_CONFLICT: Locked date conflicts with weekday.")
                    }
                    val desiredStations = if (multiStation) 3 else 1
                    val stations = selectStationsForSlot(
                        ranked = rankedCandidates,
                        desiredCount = desiredStations,
                        offset = slot + (week - 1) % maxOf(1, rankedCandidates.size.coerceAtMost(3))
                    )
                    val sessionId = matchingLockId(request, week, slot) ?: "$id-w$week-cond$slot"
                    fun eqLabel(chosen: ConditioningExerciseDescriptor): String =
                        chosen.equipmentOptions.firstOrNull { opt -> opt.isEmpty() || tokens.containsAll(opt) }
                            ?.sorted()?.joinToString(" + ")?.ifBlank { "None" } ?: "None"

                    var actualRounds = desiredRounds
                    val exList = when (effFamily) {
                        ConditioningFamily8.EMOM -> {
                            val usableStations = stations.take(cappedMainMin.coerceAtLeast(1))
                            val setsPerStation = (cappedMainMin / usableStations.size.coerceAtLeast(1)).coerceIn(1, 30)
                            actualRounds = setsPerStation * usableStations.size
                            usableStations.mapIndexed { idx, chosen ->
                                val prefix = if (usableStations.size > 1) "Minute ${idx + 1}/${usableStations.size}: " else ""
                                ExercisePrescription(
                                    id = chosen.id,
                                    name = chosen.name,
                                    sets = setsPerStation,
                                    reps = 8,
                                    rest = 25,
                                    equipment = eqLabel(chosen),
                                    cue = "$prefix${chosen.cue} $rpeCue. Stop by 40 s each minute."
                                )
                            }
                        }
                        ConditioningFamily8.CONTINUOUS_AEROBIC -> {
                            val chosen = stations.first()
                            actualRounds = 1
                            listOf(
                                ExercisePrescription(
                                    id = chosen.id,
                                    name = chosen.name,
                                    sets = 1,
                                    seconds = cappedMainMin * 60,
                                    rest = 0,
                                    equipment = eqLabel(chosen),
                                    cue = "${chosen.cue} $rpeCue."
                                )
                            )
                        }
                        else -> {
                            val desiredForSlot = if (effFamily == ConditioningFamily8.MIXED_MODAL_CONDITIONING && stations.size > 1) {
                                (desiredRounds / stations.size).coerceAtLeast(2)
                            } else {
                                desiredRounds
                            }
                            val (usedStations, fittedRounds, perStationWorkSec) = composeNonEmomIntervals(
                                availableMainSec = cappedMainMin * 60,
                                desiredRounds = desiredForSlot,
                                candidates = stations,
                                restPerTransitionSec = 30,
                                budgetMinutesForError = request.minutes
                            )
                            actualRounds = fittedRounds
                            val totalIntervals = fittedRounds * usedStations.size
                            val appliedRest = if (totalIntervals > 1) 30 else 0
                            usedStations.map { chosen ->
                                ExercisePrescription(
                                    id = chosen.id,
                                    name = chosen.name,
                                    sets = fittedRounds.coerceIn(1, 30),
                                    seconds = perStationWorkSec,
                                    rest = appliedRest,
                                    equipment = eqLabel(chosen),
                                    cue = "${chosen.cue} $rpeCue."
                                )
                            }
                        }
                    }

                    val duration = estimateSessionDuration(wuMin, cdMin, effFamily, exList)
                    val finalMin = duration.conservativeMinutes
                    if (request.timeBudgetEnabled && duration.totalSeconds > request.minutes * 60) {
                        throw PlanConstraint(
                            "CONDITIONING_TIME_BUDGET_CONFLICT: Conditioning session exceeded ${request.minutes} min budget."
                        )
                    }

                    val blockSteps = when (effFamily) {
                        ConditioningFamily8.CONTINUOUS_AEROBIC -> listOf(
                            "$wuMin min progressive aerobic warm-up",
                            "${duration.mainBlockMinutes} min continuous steady effort at Talk Test pace; stop on joint discomfort",
                            "$cdMin min cool-down walk"
                        )
                        ConditioningFamily8.EMOM -> listOf(
                            "$wuMin min movement prep warm-up",
                            "${duration.mainBlockMinutes} min alternating EMOM: stop each minute cleanly at 40 s if pace slows so >= 20 s rest holds",
                            "$cdMin min relaxed breathing cool-down"
                        )
                        ConditioningFamily8.QUALITY_PACED_AMRAP -> listOf(
                            "$wuMin min warm-up",
                            "${duration.mainBlockMinutes} min quality-paced multi-station block: pause cleanly whenever form begins to drift",
                            "$cdMin min cool-down"
                        )
                        ConditioningFamily8.MODALITY_HIIT -> listOf(
                            "$wuMin min aerobic modality warm-up",
                            "${duration.mainBlockMinutes} min modality intervals ($actualRounds rounds, controlled work + 30 s recovery)",
                            "$cdMin min easy spin or row cool-down"
                        )
                        ConditioningFamily8.STRUCTURED_CIRCUITS,
                        ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS -> listOf(
                            "$wuMin min dynamic warm-up",
                            "$actualRounds rounds across alternating conditioning stations (${duration.mainBlockMinutes} min main block incl. 30 s transitions)",
                            "$cdMin min easy walk cool-down"
                        )
                        else -> listOf(
                            "$wuMin min joint prep warm-up",
                            "${duration.mainBlockMinutes} min work-rest intervals ($actualRounds rounds with 30 s recovery between efforts)",
                            "$cdMin min cool-down"
                        )
                    }

                    add(PlannedSession(
                        id = sessionId,
                        date = naturalDate,
                        discipline = "cond",
                        title = "Conditioning: ${effFamily.displayLabel}",
                        minutes = finalMin,
                        distanceKm = null,
                        kind = "conditioning",
                        lower = effFamily.isLowerBodyHeavy || stations.any { chosen ->
                            chosen.primaryMuscles.any { it in setOf("quadriceps", "hamstrings", "gluteus_maximus", "calves") }
                        },
                        quality = effFamily.isHighStress || (!isSafetyEasy && goal.lowercase(Locale.ROOT).let { "threshold" in it || "interval" in it }),
                        exercises = exList,
                        steps = blockSteps,
                        reason = if (recovery) {
                            "Deload conditioning week: volume trimmed to support systemic recovery."
                        } else {
                            "Engine 1.8 ${effFamily.displayLabel} block progressing 1 axis cleanly within recovery limits."
                        }
                    ))
                }
            }
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
    }

    fun buildSoloPlan(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        allowCalendarReschedule: Boolean = false,
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = now
    ): PlanSnapshot {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)
        val sessions = composeSessions(
            request = request,
            id = id,
            allowCalendarReschedule = allowCalendarReschedule,
            safetyRestrictions = safetyRestrictions,
            evaluatedAtUtc = evaluatedAtUtc
        )
        return PlanSnapshot(
            id = id,
            owner = owner,
            engineVersion = ENGINE18_VERSION,
            policyId = ENGINE18_CONDITIONING_POLICY,
            createdAt = now,
            request = request,
            sessions = sessions,
            explanations = listOf(
                "Engine 1.8 8-Family Conditioning plan built without assuming running pace or running HR zones.",
                "EMOM blocks enforce a 40 s work cutoff protecting >= 20 s recovery per minute (POL-048 / MOD-17).",
                "Complex skills are filtered for novice proficiency (MD-08) and progression advances 1 axis at a time (MD-09)."
            ),
            name = "Engine 1.8 ${familyFromGoal(request.disciplineGoal("cond"), request.experience, availableEquipmentTokens(request).isNotEmpty()).displayLabel} Plan"
        )
    }

    fun validateSavedSolo(plan: PlanSnapshot, allowCalendarReschedule: Boolean = false) {
        if (plan.engineVersion != ENGINE18_VERSION || plan.policyId != ENGINE18_CONDITIONING_POLICY) {
            throw PlanConstraint("The saved Engine 1.8 conditioning plan has an unsupported identity.")
        }
        val expected = composeSessions(plan.request, plan.id, allowCalendarReschedule).associateBy { it.id }
        if (plan.sessions.size != expected.size || plan.sessions.map { it.id }.toSet() != expected.keys) {
            throw PlanConstraint("The saved Engine 1.8 conditioning plan no longer matches its expected sessions.")
        }
        for (session in plan.sessions) {
            val target = expected.getValue(session.id)
            val base = session.baselineSession()
            if (base.discipline != "cond" || base.kind != "conditioning" || base.distanceKm != null ||
                base.exercises != target.exercises ||
                base.minutes != target.minutes ||
                (!allowCalendarReschedule && session.date != target.date) ||
                session.date.dayOfWeek.value !in plan.request.days ||
                !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, plan.request)
            ) {
                throw PlanConstraint("The saved Engine 1.8 conditioning plan no longer matches its contract.")
            }
        }
        if (!allowCalendarReschedule) Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
    }

    fun validateHybridConditioningRows(soloRequest: PlanRequest, rows: List<PlannedSession>) {
        validateSoloRequest(soloRequest)
        for (session in rows) {
            val base = session.baselineSession()
            if (base.discipline != "cond" || base.kind != "conditioning" || base.distanceKm != null ||
                base.exercises.isEmpty() || base.exercises.any { it.id in soloRequest.excluded || it.sets !in 1..30 } ||
                base.steps.isEmpty() || session.date.dayOfWeek.value !in soloRequest.days ||
                !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, soloRequest)
            ) {
                throw PlanConstraint("The saved Engine 1.8 conditioning rows no longer match their contract.")
            }
        }
    }

    fun exportToSharedLocalLedger(
        accountId: String,
        weekStart: LocalDate,
        weekEnd: LocalDate,
        conditioningSessions: List<PlannedSession>
    ): UnifiedLocalMuscleLedger {
        val inputs = conditioningSessions.flatMap { s ->
            s.exercises.map { ex ->
                ObservedStrengthSetExposureInput(
                    accountId = accountId,
                    performedOn = s.date,
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    hardSetCount = ex.sets.coerceAtLeast(1),
                    isHighRepCircuit = true,
                    rir = 2
                )
            }
        }
        val spanDays = (java.time.temporal.ChronoUnit.DAYS.between(weekStart, weekEnd).toInt() + 1).coerceAtLeast(1)
        val raw = Engine16LocalMuscleLedgerBuilder.buildObserved(
            accountId = accountId,
            asOfDate = weekEnd,
            windowDays = spanDays,
            explicitExposures = inputs
        )
        return UnifiedLocalMuscleLedger(accountId, weekStart, weekEnd, isProjected = true, byMuscle = raw.byMuscle)
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun matchingLockId(request: PlanRequest, week: Int, slot: Int): String? =
        request.lockedSessionDays.keys.firstOrNull { key ->
            val match = lockRegex.matchEntire(key) ?: return@firstOrNull false
            match.groupValues[1].toInt() == week && match.groupValues[2].toInt() == slot
        }

    private fun lockSlots(request: PlanRequest): Map<Pair<Int, Int>, LocalDate> =
        request.lockedSessionDays.map { (key, rawDate) ->
            val match = lockRegex.matchEntire(key)
                ?: throw PlanConstraint("CONDITIONING_SESSION_LOCK_UNSUPPORTED: Invalid lock key $key.")
            (match.groupValues[1].toInt() to match.groupValues[2].toInt()) to LocalDate.parse(rawDate)
        }.toMap()

    private fun spacedWeekdays(available: Set<Int>, count: Int): List<Int> {
        val days = available.sorted()
        if (days.size < count) throw PlanConstraint("CONDITIONING_AVAILABILITY_UNSUPPORTED: Not enough available days.")
        val options = mutableListOf<List<Int>>()
        fun pick(from: Int, chosen: List<Int>) {
            if (chosen.size == count) { options += chosen; return }
            for (i in from until days.size) pick(i + 1, chosen + days[i])
        }
        pick(0, emptyList())
        return options.maxWithOrNull(
            compareBy<List<Int>> { slots ->
                (slots.zipWithNext().map { (a, b) -> b - a } + (slots.first() + 7 - slots.last())).minOrNull() ?: 7
            }.thenBy { it.last() - it.first() }
        )!!
    }
}

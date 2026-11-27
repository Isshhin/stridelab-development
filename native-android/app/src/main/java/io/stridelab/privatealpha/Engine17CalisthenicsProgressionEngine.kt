package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.roundToInt

internal enum class CalisthenicsDecisionAction {
    PROMOTE_NEXT_DAG_NODE,
    HOLD_CONSOLIDATE_CURRENT_NODE,
    PROGRESS_HOLD_DURATION,
    PROGRESS_REPS_OR_SETS,
    PROGRESS_ADDED_MASS,
    PRESCRIBE_PREREQUISITE_CLUSTER,
    REGRESS_DAG_PARENT,
    REDUCE_HOLD_DURATION_TREMOR,
    REDUCE_ECCENTRIC_TENDON_CAP,
    RETURN_REENTRY_SCALED
}

internal data class CalisthenicsProgressionDecision(
    val nodeId: String,
    val action: CalisthenicsDecisionAction,
    val targetNodeId: String,
    val prescribedSets: Int,
    val prescribedReps: Int?,
    val prescribedHoldSeconds: Int?,
    val weightedSemantics: ExternalLoadSemantics.ExternalAddedMass?,
    val prerequisiteClusterNodeIds: List<String> = emptyList(),
    val techniqueProvenance: TechniqueProvenance,
    val recoveryReported: Boolean,
    val coachSummary: String
)

/**
 * Engine 1.7 Freeletics-Inspired Calisthenics Progression Engine (Track 6):
 * - Technical Failure != Muscular Failure (BREAKDOWN terminates advancement & regresses safely)
 * - POL-047 non-automatic DAG promotion (>= 2 consecutive CLEAN sessions + RIR + AND gates)
 * - POL-047 / McHugh (2003) beginner slow eccentric pull-up & Nordic curl cap (<= 2 sets x 2-3 reps, <= 2x/wk)
 * - Track 6 Scenario S05 zero-pull-up Prerequisite Cluster Pairing
 * - Oranchuk (2019) isometric hold 15 -> 30 -> 45 s ladder & -30% tremor regression
 * - POL-061 >= 3 strict clean reps direct entry bypass & fresh skill separation
 * - Vertical suspended weighted calisthenics ExternalAddedMass bridge
 * - Shared UnifiedLocalMuscleLedger export
 */
internal object Engine17CalisthenicsProgressionEngine {

    fun checkGateSatisfied(
        node: CalisthenicsDagNode,
        masteredNodeIds: Set<String>,
        directBypassRepsObserved: Map<String, Int>,
        mobilityChecksPassed: Set<String>
    ): Boolean {
        val bypassReps = directBypassRepsObserved[node.exercise.id] ?: 0
        if (bypassReps >= node.bypassCleanRepsRequired) return true
        if (node.mobilityCheckRequired != null && node.mobilityCheckRequired !in mobilityChecksPassed) {
            return false
        }
        return when (val g = node.gate) {
            is CalisthenicsPrerequisiteGate.None -> true
            is CalisthenicsPrerequisiteGate.RequireAll -> g.nodeIds.all { it in masteredNodeIds || (directBypassRepsObserved[it] ?: 0) >= node.bypassCleanRepsRequired }
            is CalisthenicsPrerequisiteGate.RequireAny -> g.nodeIds.any { it in masteredNodeIds || (directBypassRepsObserved[it] ?: 0) >= node.bypassCleanRepsRequired }
        }
    }

    /** Tracks POL-058 6-bucket gapless return entry for calisthenics. */
    fun evaluateReturnReentry(
        gapDays: Int,
        baselineSets: Int,
        baselineAddedKg: Double = 0.0
    ): Pair<Int, Double> {
        require(gapDays >= 0 && baselineSets >= 1)
        val fraction = Engine17CalisthenicsPolicyRegistry.RETURN_ENTRY_FRACTIONS
            .firstOrNull { (range, _) -> gapDays in range }?.second ?: 0.40
        val sets = when {
            gapDays >= 56 -> 2
            gapDays >= 7 -> (baselineSets * fraction).roundToInt().coerceIn(2, baselineSets)
            else -> baselineSets
        }
        val addedKg = when {
            gapDays >= 84 -> 0.0
            baselineAddedKg > 0.0 -> (baselineAddedKg * fraction).coerceAtLeast(0.0)
            else -> 0.0
        }
        return sets to addedKg
    }

    /** Computes safe beginner/unaccustomed eccentric sets & reps (POL-047 / McHugh 2003). */
    fun capEccentricDoseForTendonSafety(
        isNoviceOrUnaccustomed: Boolean,
        requestedSets: Int,
        requestedReps: Int
    ): Pair<Int, Int> = if (isNoviceOrUnaccustomed) {
        requestedSets.coerceAtMost(Engine17CalisthenicsPolicyRegistry.POL_047_MAX_BEGINNER_ECCENTRIC_SETS) to
            requestedReps.coerceAtMost(Engine17CalisthenicsPolicyRegistry.POL_047_MAX_BEGINNER_ECCENTRIC_REPS)
    } else {
        requestedSets to requestedReps
    }

    /** Evaluates athlete history on a DAG node cleanly without inventing unobserved certainty. */
    fun evaluateNodeProgression(
        node: CalisthenicsDagNode,
        nextNode: CalisthenicsDagNode?,
        recentObservations: List<CalisthenicsNodeObservation>,
        spec: CalisthenicsObjectiveSpec,
        isNoviceOrUnaccustomed: Boolean,
        availableEquipment: Set<String>,
        directBypassRepsObserved: Map<String, Int> = emptyMap(),
        mobilityChecksPassed: Set<String> = emptySet(),
        masteredNodeIds: Set<String> = emptySet(),
        confirmedGapDays: Int = 0
    ): CalisthenicsProgressionDecision {
        val sorted = recentObservations.filter { it.nodeId == node.exercise.id }
            .sortedBy { it.completedOn }
        val latest = sorted.lastOrNull()

        if (confirmedGapDays >= 7) {
            val baseSets = latest?.setsCompleted ?: spec.defaultWorkingSets
            val baseAdded = latest?.externalAddedKg ?: 0.0
            val (reentrySets, reentryAdded) = evaluateReturnReentry(confirmedGapDays, baseSets, baseAdded)
            val weighted = if (node.isVerticalSuspendedWeightedEligible && reentryAdded > 0.0) {
                ExternalLoadSemantics.ExternalAddedMass(
                    Kilograms.of(reentryAdded),
                    latest?.bodyweightMassKg?.let(Kilograms::of)
                )
            } else null
            return CalisthenicsProgressionDecision(
                nodeId = node.exercise.id,
                action = CalisthenicsDecisionAction.RETURN_REENTRY_SCALED,
                targetNodeId = node.exercise.id,
                prescribedSets = reentrySets,
                prescribedReps = if (node.exercise.isIsometricHold) null else spec.defaultRepRange.first,
                prescribedHoldSeconds = if (node.exercise.isIsometricHold) Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_1_SECONDS else null,
                weightedSemantics = weighted,
                techniqueProvenance = latest?.techniqueProvenance ?: TechniqueProvenance.UNOBSERVED_TECHNIQUE,
                recoveryReported = latest?.recoveryReported ?: false,
                coachSummary = "Return reentry scaling after $confirmedGapDays days away: keep 3 to 4 RIR and crisp technique."
            )
        }

        // Scenario S05 zero-pull-up prerequisite cluster pairing when strict pull-up target cannot hit >= 3 reps
        if (node.exercise.id == "e2") {
            val obsReps = latest?.repsCompleted ?: directBypassRepsObserved["e2"] ?: 0
            if (obsReps < Engine17CalisthenicsPolicyRegistry.POL_061_ENTRY_BYPASS_MIN_CLEAN_REPS &&
                "e2" !in masteredNodeIds
            ) {
                return CalisthenicsProgressionDecision(
                    nodeId = node.exercise.id,
                    action = CalisthenicsDecisionAction.PRESCRIBE_PREREQUISITE_CLUSTER,
                    targetNodeId = "ap",
                    prescribedSets = 3,
                    prescribedReps = 5,
                    prescribedHoldSeconds = null,
                    weightedSemantics = null,
                    prerequisiteClusterNodeIds = listOf("ap", "cal-negative-pullup", "br"),
                    techniqueProvenance = latest?.techniqueProvenance ?: TechniqueProvenance.UNOBSERVED_TECHNIQUE,
                    recoveryReported = latest?.recoveryReported ?: false,
                    coachSummary = "Prerequisite cluster pairing: band-assisted pull-ups, low-volume 4 s eccentrics, and inverted rows until 3 strict pull-ups are repeatable."
                )
            }
        }

        // Clinical-free symptom / form-breakdown regression
        if (latest != null) {
            val jointSymptom = latest.symptomsPresent || latest.anteriorShoulderPain || latest.elbowTendonPain
            val technicalBreak = latest.techniqueQuality == TechniqueQuality.BREAKDOWN
            if (jointSymptom || technicalBreak) {
                if (node.exercise.isIsometricHold && (latest.holdTremorOrCollapse || !jointSymptom)) {
                    val currentHold = latest.holdSecondsCompleted ?: node.defaultHoldSeconds ?: Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_2_SECONDS
                    val regressedHold = (currentHold * Engine17CalisthenicsPolicyRegistry.ISOMETRIC_TREMOR_REGRESSION_FACTOR)
                        .roundToInt().coerceAtLeast(10)
                    return CalisthenicsProgressionDecision(
                        nodeId = node.exercise.id,
                        action = CalisthenicsDecisionAction.REDUCE_HOLD_DURATION_TREMOR,
                        targetNodeId = node.exercise.id,
                        prescribedSets = 2,
                        prescribedReps = null,
                        prescribedHoldSeconds = regressedHold,
                        weightedSemantics = null,
                        techniqueProvenance = latest.techniqueProvenance,
                        recoveryReported = latest.recoveryReported,
                        coachSummary = "Hold duration eased by ~30% to protect clean alignment before advancing."
                    )
                }
                val regTarget = node.regressionNodeId ?: node.exercise.id
                return CalisthenicsProgressionDecision(
                    nodeId = node.exercise.id,
                    action = CalisthenicsDecisionAction.REGRESS_DAG_PARENT,
                    targetNodeId = regTarget,
                    prescribedSets = 2,
                    prescribedReps = spec.defaultRepRange.first,
                    prescribedHoldSeconds = if (node.exercise.isIsometricHold) Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_1_SECONDS else null,
                    weightedSemantics = null,
                    techniqueProvenance = latest.techniqueProvenance,
                    recoveryReported = latest.recoveryReported,
                    coachSummary = "Form or joint comfort limit observed; step cleanly back to $regTarget until pain-free control returns."
                )
            }
        }

        // POL-047 slow eccentric tendon cap (McHugh 2003)
        if (node.isEccentricHighTendonStrain && isNoviceOrUnaccustomed) {
            val (cSets, cReps) = capEccentricDoseForTendonSafety(true, spec.defaultWorkingSets, spec.defaultRepRange.first)
            return CalisthenicsProgressionDecision(
                nodeId = node.exercise.id,
                action = CalisthenicsDecisionAction.REDUCE_ECCENTRIC_TENDON_CAP,
                targetNodeId = node.exercise.id,
                prescribedSets = cSets,
                prescribedReps = cReps,
                prescribedHoldSeconds = null,
                weightedSemantics = null,
                techniqueProvenance = latest?.techniqueProvenance ?: TechniqueProvenance.UNOBSERVED_TECHNIQUE,
                recoveryReported = latest?.recoveryReported ?: false,
                coachSummary = "Slow eccentrics capped at $cSets x $cReps (1 to 2 times/wk max) to protect tendon adaptation."
            )
        }

        // Isometric Hold progression (Oranchuk 2019: 15 -> 30 -> 45 s cleanly before leverage step)
        if (node.exercise.isIsometricHold) {
            val currHold = latest?.holdSecondsCompleted ?: node.defaultHoldSeconds ?: Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_1_SECONDS
            val cleanStreak = sorted.takeLast(2).count { it.techniqueQuality == TechniqueQuality.CLEAN && !it.holdTremorOrCollapse }
            val nextHold = when {
                currHold < Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_2_SECONDS -> Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_2_SECONDS
                currHold < Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_3_SECONDS -> Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_3_SECONDS
                else -> currHold
            }
            if (currHold >= Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_3_SECONDS &&
                cleanStreak >= Engine17CalisthenicsPolicyRegistry.POL_047_REQUIRED_CONSECUTIVE_CLEAN_SESSIONS &&
                nextNode != null &&
                nextNode.exercise.equipmentOptions.any { opt -> opt.isEmpty() || availableEquipment.containsAll(opt) } &&
                checkGateSatisfied(nextNode, masteredNodeIds + node.exercise.id, directBypassRepsObserved, mobilityChecksPassed)
            ) {
                return CalisthenicsProgressionDecision(
                    nodeId = node.exercise.id,
                    action = CalisthenicsDecisionAction.PROMOTE_NEXT_DAG_NODE,
                    targetNodeId = nextNode.exercise.id,
                    prescribedSets = spec.defaultWorkingSets,
                    prescribedReps = if (nextNode.exercise.isIsometricHold) null else spec.defaultRepRange.first,
                    prescribedHoldSeconds = if (nextNode.exercise.isIsometricHold) Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_1_SECONDS else null,
                    weightedSemantics = null,
                    techniqueProvenance = latest?.techniqueProvenance ?: TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE,
                    recoveryReported = latest?.recoveryReported ?: false,
                    coachSummary = "45 s hold mastered across 2 clean sessions; advancing cleanly to ${nextNode.exercise.name}."
                )
            }
            return CalisthenicsProgressionDecision(
                nodeId = node.exercise.id,
                action = CalisthenicsDecisionAction.PROGRESS_HOLD_DURATION,
                targetNodeId = node.exercise.id,
                prescribedSets = spec.defaultWorkingSets,
                prescribedReps = null,
                prescribedHoldSeconds = nextHold,
                weightedSemantics = null,
                techniqueProvenance = latest?.techniqueProvenance ?: TechniqueProvenance.UNOBSERVED_TECHNIQUE,
                recoveryReported = latest?.recoveryReported ?: false,
                coachSummary = "Hold static leverage at $nextHold s with zero tremor before moving to a harder lever."
            )
        }

        // Dynamic DAG mastery & progression check (POL-047)
        val verifiedProvenances = setOf(
            TechniqueProvenance.COACH_VERIFIED_TECHNIQUE,
            TechniqueProvenance.SENSOR_SUPPORTED_METRIC,
            TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE
        )
        val lastTwo = sorted.takeLast(Engine17CalisthenicsPolicyRegistry.POL_047_REQUIRED_CONSECUTIVE_CLEAN_SESSIONS)
        val canPromoteDag = nextNode != null &&
            lastTwo.size >= Engine17CalisthenicsPolicyRegistry.POL_047_REQUIRED_CONSECUTIVE_CLEAN_SESSIONS &&
            lastTwo.all { obs ->
                obs.techniqueQuality == TechniqueQuality.CLEAN &&
                    obs.techniqueProvenance in verifiedProvenances &&
                    (obs.rirObserved ?: 0) >= Engine17CalisthenicsPolicyRegistry.POL_047_MIN_RIR_FOR_PROMOTION &&
                    (obs.repsCompleted ?: 0) >= spec.defaultRepRange.last &&
                    obs.mobilityGatePassed
            } &&
            nextNode.exercise.equipmentOptions.any { opt -> opt.isEmpty() || availableEquipment.containsAll(opt) } &&
            checkGateSatisfied(nextNode, masteredNodeIds + node.exercise.id, directBypassRepsObserved, mobilityChecksPassed)

        if (canPromoteDag) {
            return CalisthenicsProgressionDecision(
                nodeId = node.exercise.id,
                action = CalisthenicsDecisionAction.PROMOTE_NEXT_DAG_NODE,
                targetNodeId = nextNode.exercise.id,
                prescribedSets = spec.defaultWorkingSets,
                prescribedReps = if (nextNode.exercise.isIsometricHold) null else spec.defaultRepRange.first,
                prescribedHoldSeconds = if (nextNode.exercise.isIsometricHold) Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_1_SECONDS else null,
                weightedSemantics = null,
                techniqueProvenance = lastTwo.last().techniqueProvenance,
                recoveryReported = true,
                coachSummary = "Repeatable mastery verified across 2 clean sessions; advancing cleanly to ${nextNode.exercise.name}."
            )
        }

        // Vertical suspended weighted calisthenics (POL-045 / ExternalAddedMass)
        if (node.isVerticalSuspendedWeightedEligible && latest != null &&
            latest.techniqueQuality == TechniqueQuality.CLEAN &&
            (latest.repsCompleted ?: 0) >= spec.defaultRepRange.last &&
            (latest.rirObserved ?: 0) >= 2
        ) {
            val nextAddedKg = (latest.externalAddedKg ?: 0.0) + 2.5
            return CalisthenicsProgressionDecision(
                nodeId = node.exercise.id,
                action = CalisthenicsDecisionAction.PROGRESS_ADDED_MASS,
                targetNodeId = node.exercise.id,
                prescribedSets = spec.defaultWorkingSets,
                prescribedReps = spec.defaultRepRange.first,
                prescribedHoldSeconds = null,
                weightedSemantics = ExternalLoadSemantics.ExternalAddedMass(
                    Kilograms.of(nextAddedKg),
                    latest.bodyweightMassKg?.let(Kilograms::of)
                ),
                techniqueProvenance = latest.techniqueProvenance,
                recoveryReported = latest.recoveryReported,
                coachSummary = "Vertical suspended load increased to +$nextAddedKg kg (body mass tracked separately)."
            )
        }

        // Minimal-input POL-046 continuity check
        val minimalInputEligible = sorted.size >= Engine17CalisthenicsPolicyRegistry.POL_046_MIN_COMPLETED_SESSIONS &&
            sorted.takeLast(2).all { !it.symptomsPresent && it.setsCompleted >= 2 }
        if (latest == null || latest.techniqueProvenance == TechniqueProvenance.UNOBSERVED_TECHNIQUE || !latest.recoveryReported) {
            val curSets = latest?.setsCompleted ?: spec.defaultWorkingSets
            val nextSets = if (minimalInputEligible) (curSets + 1).coerceAtMost(4) else curSets
            return CalisthenicsProgressionDecision(
                nodeId = node.exercise.id,
                action = if (minimalInputEligible) CalisthenicsDecisionAction.PROGRESS_REPS_OR_SETS else CalisthenicsDecisionAction.HOLD_CONSOLIDATE_CURRENT_NODE,
                targetNodeId = node.exercise.id,
                prescribedSets = nextSets,
                prescribedReps = spec.defaultRepRange.first,
                prescribedHoldSeconds = null,
                weightedSemantics = null,
                techniqueProvenance = TechniqueProvenance.UNOBSERVED_TECHNIQUE,
                recoveryReported = false,
                coachSummary = "Minimal-input mode: consolidate reps and add 1 set only after 2 completed sessions (technique remains unverified)."
            )
        }

        val currentReps = latest.repsCompleted ?: spec.defaultRepRange.first
        val nextReps = (currentReps + 1).coerceIn(spec.defaultRepRange)
        return CalisthenicsProgressionDecision(
            nodeId = node.exercise.id,
            action = CalisthenicsDecisionAction.PROGRESS_REPS_OR_SETS,
            targetNodeId = node.exercise.id,
            prescribedSets = spec.defaultWorkingSets,
            prescribedReps = nextReps,
            prescribedHoldSeconds = null,
            weightedSemantics = latest.externalAddedKg?.let {
                ExternalLoadSemantics.ExternalAddedMass(
                    Kilograms.of(it),
                    latest.bodyweightMassKg?.let(Kilograms::of)
                )
            },
            techniqueProvenance = latest.techniqueProvenance,
            recoveryReported = latest.recoveryReported,
            coachSummary = "Build clean reps inside ${spec.defaultRepRange.first} to ${spec.defaultRepRange.last} before advancing leverage."
        )
    }

    /** Exports calisthenics working sets & holds into the shared Engine 1.6 UnifiedLocalMuscleLedger. */
    fun exportToSharedLocalLedger(
        accountId: String,
        weekStartInclusive: LocalDate,
        weekEndInclusive: LocalDate,
        slots: List<CalisthenicsWorkoutSlot>
    ): UnifiedLocalMuscleLedger {
        val plannedTriples = slots
            .filter { it.blockRole != CalisthenicsBlockRole.FRESH_SKILL_PRACTICE }
            .map { slot ->
                Triple(slot.node.exercise, slot.sets, slot.targetRir)
            }
        val baseLedger = Engine16LocalMuscleLedgerBuilder.buildProjectedForWeek(
            accountId = accountId,
            weekStartInclusive = weekStartInclusive,
            weekEndInclusive = weekEndInclusive,
            plannedExercises = plannedTriples
        )
        val eccentricSets = slots.filter { it.node.isEccentricHighTendonStrain }.sumOf { it.sets }
        val holdSecByMuscle = LinkedHashMap<String, Int>()
        for (slot in slots) {
            val h = slot.holdSeconds ?: continue
            val totalHold = slot.sets * h
            slot.node.exercise.primaryMuscles.forEach { m ->
                holdSecByMuscle[m] = (holdSecByMuscle[m] ?: 0) + totalHold
            }
        }
        val mergedStats = baseLedger.byMuscle.mapValues { (m, stat) ->
            stat.copy(
                isometricHoldSeconds = holdSecByMuscle[m] ?: stat.isometricHoldSeconds,
                eccentricFocusedSets = stat.eccentricFocusedSets + eccentricSets
            )
        }
        return UnifiedLocalMuscleLedger(
            accountId = accountId,
            windowStartInclusive = weekStartInclusive,
            windowEndInclusive = weekEndInclusive,
            isProjected = true,
            byMuscle = mergedStats
        )
    }
}

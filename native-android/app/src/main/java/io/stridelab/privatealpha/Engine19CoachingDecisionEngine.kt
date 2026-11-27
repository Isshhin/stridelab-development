package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class CoachingActionType {
    CONTINUE,
    MAINTAIN_WORKLOAD,
    CONSOLIDATE,
    PROGRESS,
    REDUCE_WORKLOAD,
    SUBSTITUTE_WORKOUT,
    CHANGE_EXECUTION_GUIDANCE,
    RECOMMEND_RECOVERY,
    RECOMMEND_DELOAD,
    RECOMMEND_RECALIBRATION,
    RECOMMEND_SCHEDULE_CHANGE,
    RECOMMEND_PLAN_REGENERATION,
    RECOMMEND_GOAL_REVISION,
    PAUSE_TRAINING,
    REQUEST_CLARIFICATION,
    ABSTAIN
}

internal enum class AdvancementPathway {
    ORDINARY_BOUNDED,
    UNDERESTIMATED_CORRECTION_UNTRAPPING,
    NEW_WORKOUT_FAMILY,
    INTENSITY_RECALIBRATION,
    RECOVERY_OF_PRIOR_EXPOSURE,
    MINIMAL_INPUT_MINOR_CONTINUITY
}

internal enum class ActionPairRelation {
    COMPATIBLE,
    DOMINATED,
    MUTUALLY_EXCLUSIVE
}

internal data class CoachingRecommendation(
    val action: CoachingActionType,
    val decisionClass: CoachingDecisionClass,
    val discipline: String,
    val title: String,
    val message: String,
    val requiresApproval: Boolean,
    val scopeLabel: String,
    val pathway: AdvancementPathway? = null,
    val abstainedTargets: Set<String> = emptySet(),
    val suppressedByStabilityHold: Boolean = false
)

internal enum class WeeklyReviewOutcome {
    HEALTH_RESTRICTION,
    REBUILD,
    REDUCE,
    REGENERATE_RECOMMENDED,
    RECALIBRATE,
    INSUFFICIENT_EVIDENCE,
    CONSOLIDATE,
    PROGRESSION_ELIGIBLE,
    CONTINUE
}

internal data class WeeklyReviewInputSummary(
    val activeHealthHoldTiers: Set<HealthEscalationTier> = emptySet(),
    val maxGapDays: Int = 0,
    val fatigueAccumulatedAcrossSessions: Boolean = false,
    val unplannedRaceOrOverloadSpike: Boolean = false,
    val structuralScheduleOrGoalDivergence: Boolean = false,
    val multiSessionPaceOrLoadMismatch: Boolean = false,
    val missingImportsDays: Int = 0,
    val partialAdherenceOrOvershoot: Boolean = false,
    val unverifiedRecoveryOnMajorStep: Boolean = false,
    val verifiedProgressionReady: Boolean = false
)

internal data class WeeklyReviewDecision(
    val outcome: WeeklyReviewOutcome,
    val requiresApproval: Boolean,
    val summary: String,
    val reasonCode: String
)

internal data class CoachingEvent(
    val eventId: String,
    val canonicalActivityId: String? = null,
    val discipline: String,
    val evaluatedAtUtc: Instant,
    val assessment: MultidimensionalExecutionAssessment? = null,
    val differentialInput: DifferentialContextInput? = null,
    val symptomReport: HealthSymptomReport? = null,
    val urgentScheduleOrLockConflict: Boolean = false,
    val unplannedRaceDetected: Boolean = false,
    val volumeSpikeDetected: Boolean = false,
    val missingDataDaysGap: Int = 0,
    val isUserRequestedReview: Boolean = false,
    val excessReserveTargetKey: String? = null,
    val excessReserveObserved: Boolean = false,
    val athleteHasEstablishedBaseCapacity: Boolean = false,
    val verifiedFreshRaceOrBenchmark: Boolean = false
)

internal data class CoachingDecisionTrace(
    val evaluatedAtUtc: Instant,
    val activeHoldIds: List<String>,
    val suppressedByStabilityHoldClasses: List<CoachingDecisionClass>,
    val uniqueConfirmationCounts: Map<String, Int>,
    val differentialResult: DifferentialEvaluationResult?
)

internal data class ProposedDecisionStateDelta(
    val nextState: DecisionStateSnapshot,
    val recordedHold: HealthHoldRecord? = null
)

internal data class CoachingDecisionBundle(
    val primary: CoachingRecommendation,
    val secondaries: List<CoachingRecommendation>,
    val scopedAbstentions: Set<String>,
    val trace: CoachingDecisionTrace,
    val notifications: List<CoachingNotificationCandidate>
)

// -----------------------------------------------------------------------------
// 8-Dimension Conditional Progression Gate & OCC Transactional Protocol (Track 7 §§7-8)
// -----------------------------------------------------------------------------

internal enum class GateSessionLifecycle { FUTURE_PLANNED, DUE_TODAY, EXECUTED, SKIPPED, EXPIRED }
internal enum class PrerequisiteStatus { SATISFIED, UNVERIFIED, FAILED }
internal enum class ContinuationBranchKind { NONE, ADVANCE, CONSERVATIVE_HOLD }

internal data class ConditionalGatePrerequisiteSpec(
    val completionRequired: Boolean = true,
    val intensityAdherenceRequired: Boolean = true,
    val techniqueCleanRequired: Boolean = false,
    val recoveryOrContinuityRequired: Boolean = true,
    val noHealthHoldRequired: Boolean = true
)

internal data class ConditionalProgressionGate(
    val gateId: String,
    val sessionLifecycle: GateSessionLifecycle,
    val spec: ConditionalGatePrerequisiteSpec,
    val statuses: Map<String, PrerequisiteStatus>,
    val validUntilUtc: Instant,
    val preApprovedBranches: Set<ContinuationBranchKind> = setOf(
        ContinuationBranchKind.ADVANCE,
        ContinuationBranchKind.CONSERVATIVE_HOLD
    ),
    val selectedBranch: ContinuationBranchKind = ContinuationBranchKind.NONE
) {
    fun withUserSimpleCompletionTap(): ConditionalProgressionGate = copy(
        statuses = statuses + ("completionRequired" to PrerequisiteStatus.SATISFIED)
    )
}

internal data class ActivationValidationContext(
    val nowUtc: Instant,
    val hasBlockingHealthHold: Boolean,
    val proposedVolumeExceedsRollingDmax30: Boolean,
    val proposedMinutesExceedsDayCap: Boolean,
    val violatesSpacing: Boolean
)

internal data class BranchActivationDecision(
    val allowed: Boolean,
    val activatedBranch: ContinuationBranchKind,
    val fallbackScopeRequired: Boolean,
    val reason: String
)

internal enum class RevisionScope {
    SCOPE_A_EXECUTION_GUIDANCE,
    SCOPE_B_SINGLE_WORKOUT_SWAP,
    SCOPE_C_WEEKLY_SCHEDULE_REVISION,
    SCOPE_D_MACROCYCLE_REVISION,
    SCOPE_E_GOAL_OR_HORIZON_REVISION,
    SCOPE_F_COMPLETE_NEW_PLAN
}

internal data class OccInputFingerprint(
    val activitiesDigest: String,
    val symptomsDigest: String,
    val restrictionsDigest: String,
    val availabilityDigest: String,
    val goalsDigest: String,
    val acceptedRevisionsCount: Int,
    val calibrationDigest: String,
    val policyVersion: String
) {
    fun computeSha256(): String {
        val raw = listOf(
            activitiesDigest,
            symptomsDigest,
            restrictionsDigest,
            availabilityDigest,
            goalsDigest,
            acceptedRevisionsCount.toString(),
            calibrationDigest,
            policyVersion
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

internal data class PlanRevisionCandidate(
    val candidateId: String,
    val scope: RevisionScope,
    val expectedBasePlanVersion: Int,
    val inputStateHash: String,
    val createdAtUtc: Instant,
    val expiresAtUtc: Instant,
    val targetSessionIds: Set<String> = emptySet(),
    val affectedWeekIndices: List<Int> = emptyList(),
    val reasonSummary: String
)

internal sealed interface OccCommitOutcome {
    data class Committed(
        val newPlanVersion: Int,
        val candidateId: String,
        val archivedPriorVersion: Int,
        val validatedWeeksCount: Int
    ) : OccCommitOutcome

    data class Rejected(
        val reasonCode: String,
        val explanation: String
    ) : OccCommitOutcome
}

/**
 * Engine 1.9 Non-Short-Circuiting 8-Stage Composable Decision Engine, Action Compatibility Matrix,
 * Conditional Branch Activation, and OCC Transactional Revision Protocol (Track 7 §§1, 2, 5, 7, 8, 9, 10, 13).
 */
internal object Engine19CoachingDecisionEngine {

    fun classifyActionPair(a: CoachingRecommendation, b: CoachingRecommendation): ActionPairRelation {
        if (a.action == b.action && a.discipline == b.discipline) return ActionPairRelation.DOMINATED
        val pair = setOf(a.action, b.action)
        // Shield calibration abstention is strictly decision-specific and compatible with acute recovery!
        if (CoachingActionType.ABSTAIN in pair && CoachingActionType.RECOMMEND_RECOVERY in pair) {
            return ActionPairRelation.COMPATIBLE
        }
        // Localized PAUSE_TRAINING on discipline X NEVER blocks CONTINUE on unaffected discipline Y!
        if ((a.action == CoachingActionType.PAUSE_TRAINING && b.action == CoachingActionType.CONTINUE && a.discipline != b.discipline) ||
            (b.action == CoachingActionType.PAUSE_TRAINING && a.action == CoachingActionType.CONTINUE && a.discipline != b.discipline)
        ) {
            return ActionPairRelation.COMPATIBLE
        }
        // Any PAUSE_TRAINING on discipline X is mutually exclusive with PROGRESS on same discipline X.
        if (a.discipline == b.discipline &&
            CoachingActionType.PAUSE_TRAINING in pair &&
            pair.any { it in setOf(CoachingActionType.PROGRESS, CoachingActionType.RECOMMEND_RECALIBRATION) }
        ) {
            return ActionPairRelation.MUTUALLY_EXCLUSIVE
        }
        return ActionPairRelation.COMPATIBLE
    }

    /**
     * Pure deterministic 8-stage composable decision evaluation (`CD-15`).
     */
    fun evaluate(
        state: DecisionStateSnapshot,
        event: CoachingEvent
    ): Pair<CoachingDecisionBundle, ProposedDecisionStateDelta> {
        var workingState = state.copy(evaluationTimestamp = event.evaluatedAtUtc)
        val notifications = mutableListOf<CoachingNotificationCandidate>()
        val candidates = mutableListOf<CoachingRecommendation>()
        val abstentions = LinkedHashSet<String>()

        // Stage 1-2: Differential Assessment & Activity Recording (deduplicated)
        if (event.canonicalActivityId != null) {
            workingState = workingState.copy(observedExecutionIds = workingState.observedExecutionIds + event.canonicalActivityId)
        }
        val differential = event.differentialInput?.let {
            Engine19ExecutionAndDifferentialEngine.evaluateDifferentialHypotheses(it)
        }

        // Stage 3-4: Persistent Health Holds Extraction & Symptom Triage (H1-H4, dwell = 0)
        var newHold: HealthHoldRecord? = null
        if (event.symptomReport != null) {
            val seq = (workingState.activeHealthHolds.values.maxOfOrNull { it.monotonicSeq } ?: 0L) + 1L
            val classified = Engine19HealthEscalationAndState.classifyHealthSymptom(
                report = event.symptomReport,
                accountId = workingState.athleteStateSnapshot?.accountId ?: "local-athlete",
                restrictionId = "hold-${event.eventId}",
                nowUtc = event.evaluatedAtUtc,
                nextSeq = seq
            )
            if (classified != null && classified.tier != HealthEscalationTier.H4_ORDINARY_FATIGUE_OR_SORENESS) {
                newHold = classified
                workingState = workingState.copy(
                    activeHealthHolds = workingState.activeHealthHolds + (classified.restrictionId to classified)
                )
            }
        }

        val discEnum = runCatching { AthleteDiscipline.fromCode(event.discipline) }.getOrElse { AthleteDiscipline.RUN }
        val blockingHold = workingState.activeHealthHolds.values.firstOrNull { hold ->
            hold.isActive && !hold.allowsUnaffectedDiscipline(discEnum)
        }

        if (blockingHold != null) {
            candidates += CoachingRecommendation(
                action = CoachingActionType.PAUSE_TRAINING,
                decisionClass = CoachingDecisionClass.HEALTH_HOLD,
                discipline = event.discipline,
                title = "Training hold active (${blockingHold.tier.name})",
                message = buildHoldMessage(blockingHold),
                requiresApproval = false,
                scopeLabel = "Hold"
            )
            notifications += CoachingNotificationCandidate(
                notificationId = "safety-${blockingHold.restrictionId}",
                notificationClass = NotificationClass.SAFETY_ALERT,
                title = "Health Hold Active",
                body = buildHoldMessage(blockingHold),
                affectsImminent48hSession = true
            )
            // Localized hold keeps unaffected disciplines eligible cleanly (D13)
            val unaffectedOther = when (blockingHold.scope) {
                is HealthRestrictionScope.Localized -> if ("str" != event.discipline && AthleteDiscipline.STRENGTH !in blockingHold.scope.disciplines) "str" else null
                is HealthRestrictionScope.Systemic -> null
            }
            if (unaffectedOther != null) {
                candidates += CoachingRecommendation(
                    action = CoachingActionType.CONTINUE,
                    decisionClass = CoachingDecisionClass.PROGRESSION_OR_CONSOLIDATION,
                    discipline = unaffectedOther,
                    title = "Continue unaffected $unaffectedOther work",
                    message = "Upper-body or unaffected sessions outside the localized hold remain eligible if symptom-free.",
                    requiresApproval = false,
                    scopeLabel = "Continue"
                )
            }
        }

        // Unobserved/missing import gap or post-febrile blocking clarification (D10, D14)
        val postFebrileUnconfirmed = blockingHold?.tier == HealthEscalationTier.H3_TEMPORARY_CONSERVATIVE_HOLD &&
            blockingHold.minHrQuarantineUntilUtc != null
        if (postFebrileUnconfirmed || event.missingDataDaysGap >= 14) {
            candidates += CoachingRecommendation(
                action = CoachingActionType.REQUEST_CLARIFICATION,
                decisionClass = CoachingDecisionClass.BLOCKING_CLARIFICATION,
                discipline = event.discipline,
                title = "Confirmation needed before escalation",
                message = if (postFebrileUnconfirmed) {
                    "Before resuming after fever, please confirm you are symptom-free with no chest, breathing, or dizziness concerns, and consult a clinician if symptoms linger."
                } else {
                    "No workouts synced for ${event.missingDataDaysGap} days. Confirm whether you trained off-app or took a break before adjusting targets."
                },
                requiresApproval = true,
                scopeLabel = "Clarification"
            )
            notifications += CoachingNotificationCandidate(
                notificationId = "clarify-${event.eventId}",
                notificationClass = NotificationClass.BLOCKING_CLARIFICATION,
                title = "Confirmation Needed",
                body = "Please confirm symptom resolution or training gap before progression.",
                isUnexplained14dGap = event.missingDataDaysGap >= 14
            )
        }

        val assess = event.assessment
        // Heat / measurement calibration shield -> decision-specific abstention ONLY!
        if (assess != null && (assess.envHeatConfounded || assess.telemetryCorruptedOrQuarantined)) {
            abstentions += "ABSTAIN_ON(RECALIBRATE_PACE_OR_HR)"
            candidates += CoachingRecommendation(
                action = CoachingActionType.ABSTAIN,
                decisionClass = CoachingDecisionClass.PROGRESSION_OR_CONSOLIDATION,
                discipline = event.discipline,
                title = "Calibration shielded",
                message = if (assess.envHeatConfounded) {
                    "Heat or environmental stress confounded pacing; pace calibration is shielded cleanly."
                } else {
                    "Telemetry quality anomaly quarantined; heart rate and pace targets remain unchanged."
                },
                requiresApproval = false,
                scopeLabel = "Scope A",
                abstainedTargets = setOf("RECALIBRATE_PACE_OR_HR")
            )
        }

        // HRV insufficient sample gate -> decision-specific abstention on HRV only (`D25`)
        if (event.differentialInput?.hrvResult is HrvEvaluationResult.InsufficientOrInvalid) {
            abstentions += "ABSTAIN_ON(HRV_READINESS_DECISION)"
        }

        // Urgent schedule/lock conflict, unplanned race, heat exhaustion, or acute volume spike (exempt from Scope D cooldown!)
        if (event.urgentScheduleOrLockConflict) {
            candidates += CoachingRecommendation(
                action = CoachingActionType.RECOMMEND_SCHEDULE_CHANGE,
                decisionClass = CoachingDecisionClass.STRUCTURAL_SCHEDULE_REPAIR,
                discipline = event.discipline,
                title = "Resolve schedule conflict",
                message = "A calendar conflict affects your upcoming session spacing or locked weekday.",
                requiresApproval = true,
                scopeLabel = "Scope C"
            )
        }

        if (event.unplannedRaceDetected || event.volumeSpikeDetected ||
            (assess != null && assess.envHeatConfounded && assess.completion == SessionCompletionState.ABORTED)
        ) {
            candidates += CoachingRecommendation(
                action = CoachingActionType.RECOMMEND_RECOVERY,
                decisionClass = CoachingDecisionClass.ACUTE_RECOVERY_OR_DELOAD,
                discipline = event.discipline,
                title = "Protect recovery next 24 to 72 hours",
                message = when {
                    event.unplannedRaceDetected -> "Logged an unplanned all-out effort. Swap immediate hard sessions to easy or rest to protect recovery."
                    assess?.envHeatConfounded == true -> "Stopping early in severe heat was prudent. Keep pace targets unchanged and take an easy recovery day tomorrow."
                    else -> "Acute workload spike logged in observed history. Hold planned recovery rather than adding load."
                },
                requiresApproval = true,
                scopeLabel = "Scope B"
            )
        }

        // Check confirmation counter & Scope D cooldown / reversal stability hold (`CD-01`, `CD-12`, `D03`)
        val scopeDCooldownUntil = workingState.decisionCooldowns[CoachingDecisionClass.SCOPE_D_RECALIBRATION]
        val scopeDReversals = workingState.recentReversalCount(CoachingDecisionClass.SCOPE_D_RECALIBRATION, event.evaluatedAtUtc)
        val scopeDSuppressed = !event.isUserRequestedReview && (
            (scopeDCooldownUntil != null && event.evaluatedAtUtc.isBefore(scopeDCooldownUntil)) ||
                scopeDReversals >= Engine19CoachingPolicyRegistry.REVERSAL_TRIGGER_COUNT
            )
        val suppressedClasses = if (scopeDSuppressed) listOf(CoachingDecisionClass.SCOPE_D_RECALIBRATION) else emptyList()

        val targetKey = event.excessReserveTargetKey ?: "${event.discipline}:progression"
        if (event.excessReserveObserved && event.canonicalActivityId != null &&
            assess?.envHeatConfounded != true && assess?.telemetryCorruptedOrQuarantined != true &&
            assess?.symptomStatus == SymptomStatus.CONFIRMED_NONE
        ) {
            workingState = workingState.withRecordedExposure(targetKey, event.canonicalActivityId, event.evaluatedAtUtc)
        }
        val uniqueConfirmations = workingState.uniqueFreshConfirmations(targetKey, event.evaluatedAtUtc).size

        // Pathway evaluation (D02, D03, D17, POL-046, CD-05)
        if (blockingHold == null && assess != null) {
            val recoveryUnverified = assess.recoveryEvidence == RecoveryEvidenceStatus.MISSING_UNREPORTED
            val techUnobserved = assess.techniqueStatus == TechniqueObservationStatus.UNOBSERVED
            val nonConfoundedSuccess = assess.completion == SessionCompletionState.COMPLETE &&
                assess.symptomStatus == SymptomStatus.CONFIRMED_NONE &&
                !assess.envHeatConfounded &&
                assess.intensityAdherence != DimensionValue.Measured(IntensityAdherenceBand.EXCESSIVE_ABOVE)

            if (nonConfoundedSuccess) {
                if (uniqueConfirmations >= Engine19CoachingPolicyRegistry.CONFIRMATION_DWELL_PATHWAY_B &&
                    !recoveryUnverified &&
                    event.athleteHasEstablishedBaseCapacity &&
                    !scopeDSuppressed
                ) {
                    // Pathway B: single-axis ONLY (`<= +20%` work OR intensity refresh, NEVER both — D03!)
                    candidates += CoachingRecommendation(
                        action = CoachingActionType.RECOMMEND_RECALIBRATION,
                        decisionClass = CoachingDecisionClass.SCOPE_D_RECALIBRATION,
                        discipline = event.discipline,
                        title = "Single-axis progression upgrade",
                        message = "Three consecutive non-confounded sessions showed consistent reserve with good recovery. Choose either up to a +20% duration step OR an intensity refresh at held duration, never both in the same week.",
                        requiresApproval = true,
                        scopeLabel = "Scope D",
                        pathway = AdvancementPathway.UNDERESTIMATED_CORRECTION_UNTRAPPING
                    )
                } else if (recoveryUnverified || techUnobserved) {
                    // POL-046 Minimal-input continuity -> capped strictly to MINOR step (`CD-05`, `CD-13`)
                    val minCountOk = uniqueConfirmations >= Engine19CoachingPolicyRegistry.CONFIRMATION_DWELL_DEFAULT ||
                        assess.completion == SessionCompletionState.COMPLETE
                    if (minCountOk) {
                        candidates += CoachingRecommendation(
                            action = CoachingActionType.PROGRESS,
                            decisionClass = CoachingDecisionClass.PROGRESSION_OR_CONSOLIDATION,
                            discipline = event.discipline,
                            title = "Conservative minimal-input minor step",
                            message = "Session completed without logged pain. Because recovery or technique details were not logged, any adjustment is capped at a minor step while preserving your baseline.",
                            requiresApproval = false,
                            scopeLabel = "Continue",
                            pathway = AdvancementPathway.MINIMAL_INPUT_MINOR_CONTINUITY
                        )
                    }
                } else if (uniqueConfirmations >= Engine19CoachingPolicyRegistry.CONFIRMATION_DWELL_DEFAULT || event.verifiedFreshRaceOrBenchmark) {
                    candidates += CoachingRecommendation(
                        action = CoachingActionType.PROGRESS,
                        decisionClass = CoachingDecisionClass.PROGRESSION_OR_CONSOLIDATION,
                        discipline = event.discipline,
                        title = "Bounded progression eligible",
                        message = "Comparable completed sessions and acceptable recovery support continuing along your progression path.",
                        requiresApproval = false,
                        scopeLabel = "Continue",
                        pathway = AdvancementPathway.ORDINARY_BOUNDED
                    )
                }
            } else if (assess.intensityAdherence == DimensionValue.Measured(IntensityAdherenceBand.EXCESSIVE_ABOVE)) {
                // Excessive overshoot NEVER earns escalation!
                candidates += CoachingRecommendation(
                    action = CoachingActionType.CONSOLIDATE,
                    decisionClass = CoachingDecisionClass.PROGRESSION_OR_CONSOLIDATION,
                    discipline = event.discipline,
                    title = "Consolidate pacing at current dose",
                    message = "This effort ran above your intended intensity band. Repeat the current structure cleanly before advancing dose.",
                    requiresApproval = false,
                    scopeLabel = "Scope A"
                )
            }
        }

        if (candidates.isEmpty()) {
            candidates += CoachingRecommendation(
                action = CoachingActionType.CONTINUE,
                decisionClass = CoachingDecisionClass.PROGRESSION_OR_CONSOLIDATION,
                discipline = event.discipline,
                title = if (scopeDSuppressed) "Stability hold active; continue current plan" else "Continue current plan",
                message = if (scopeDSuppressed) {
                    "Pausing discretionary plan-change prompts for 14 days to keep your week steady; safety alerts remain active."
                } else {
                    "Logged training aligns cleanly with your current plan."
                },
                requiresApproval = false,
                scopeLabel = "Continue",
                suppressedByStabilityHold = scopeDSuppressed
            )
        }

        // Stage 5-8: Precedence ranking & Action Compatibility Filtering
        fun precedenceRank(action: CoachingActionType): Int = when (action) {
            CoachingActionType.PAUSE_TRAINING -> 0
            CoachingActionType.REQUEST_CLARIFICATION -> 1
            CoachingActionType.RECOMMEND_RECOVERY, CoachingActionType.RECOMMEND_DELOAD -> 2
            CoachingActionType.RECOMMEND_SCHEDULE_CHANGE -> 3
            CoachingActionType.RECOMMEND_RECALIBRATION, CoachingActionType.RECOMMEND_PLAN_REGENERATION, CoachingActionType.RECOMMEND_GOAL_REVISION -> 4
            CoachingActionType.REDUCE_WORKLOAD, CoachingActionType.SUBSTITUTE_WORKOUT -> 5
            CoachingActionType.PROGRESS -> 6
            CoachingActionType.CONSOLIDATE, CoachingActionType.MAINTAIN_WORKLOAD -> 7
            CoachingActionType.CONTINUE -> 8
            CoachingActionType.CHANGE_EXECUTION_GUIDANCE -> 9
            CoachingActionType.ABSTAIN -> 10
        }

        val sorted = candidates.sortedWith(compareBy<CoachingRecommendation> { precedenceRank(it.action) }.thenBy { it.discipline })
        val surviving = mutableListOf<CoachingRecommendation>()
        for (cand in sorted) {
            val blocked = surviving.any { accepted ->
                val rel = classifyActionPair(accepted, cand)
                rel == ActionPairRelation.MUTUALLY_EXCLUSIVE || rel == ActionPairRelation.DOMINATED
            }
            if (!blocked) {
                val safeBody = Engine19ExplanationAndNotificationPolicy.validateAndSanitizeCopy(
                    rawText = cand.message,
                    techniqueCoachVerified = assess?.techniqueStatus == TechniqueObservationStatus.COACH_VERIFIED_CLEAN
                ).sanitizedText
                surviving += cand.copy(message = safeBody)
            }
        }

        val primary = surviving.first()
        val secondaries = surviving.drop(1)
        val filteredNotifications = Engine19ExplanationAndNotificationPolicy.filterAndPrioritizeNotifications(
            candidates = notifications,
            optionalAlreadySentThisSession = 0,
            optionalAlreadySentThisWeek = workingState.optionalNotificationsSentThisWeek
        )

        val trace = CoachingDecisionTrace(
            evaluatedAtUtc = event.evaluatedAtUtc,
            activeHoldIds = workingState.activeHealthHolds.filterValues { it.isActive }.keys.sorted(),
            suppressedByStabilityHoldClasses = suppressedClasses,
            uniqueConfirmationCounts = mapOf(targetKey to uniqueConfirmations),
            differentialResult = differential
        )

        val bundle = CoachingDecisionBundle(
            primary = primary,
            secondaries = secondaries,
            scopedAbstentions = abstentions,
            trace = trace,
            notifications = filteredNotifications
        )
        return bundle to ProposedDecisionStateDelta(nextState = workingState, recordedHold = newHold)
    }

    /**
     * 9-Precedence Weekly Coaching Review Synthesizer (Track 7 §9).
     */
    fun evaluateWeeklyReview(
        state: DecisionStateSnapshot,
        input: WeeklyReviewInputSummary
    ): WeeklyReviewDecision {
        val activeTier = input.activeHealthHoldTiers + state.activeHealthHolds.values.filter { it.isActive }.map { it.tier }
        return when {
            activeTier.any { it != HealthEscalationTier.H4_ORDINARY_FATIGUE_OR_SORENESS } -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.HEALTH_RESTRICTION,
                requiresApproval = true,
                summary = "An active health restriction remains in place until cleared.",
                reasonCode = "WEEKLY_HEALTH_RESTRICTION"
            )
            input.maxGapDays >= 14 -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.REBUILD,
                requiresApproval = true,
                summary = "A training break of ${input.maxGapDays} days calls for conservative discipline-specific reentry.",
                reasonCode = "WEEKLY_REBUILD_REQUIRED"
            )
            input.fatigueAccumulatedAcrossSessions || input.unplannedRaceOrOverloadSpike -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.REDUCE,
                requiresApproval = true,
                summary = "Multi-session fatigue or acute overload warrants a lighter recovery week.",
                reasonCode = "WEEKLY_REDUCE_OR_DELOAD"
            )
            input.structuralScheduleOrGoalDivergence -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.REGENERATE_RECOMMENDED,
                requiresApproval = true,
                summary = "Schedule or goal drift warrants updating the remaining plan horizon.",
                reasonCode = "WEEKLY_REGENERATION_RECOMMENDED"
            )
            input.multiSessionPaceOrLoadMismatch -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.RECALIBRATE,
                requiresApproval = true,
                summary = "Consistent multi-session execution divergence supports refreshing pace or load references.",
                reasonCode = "WEEKLY_RECALIBRATION_ELIGIBLE"
            )
            input.missingImportsDays >= 14 -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.INSUFFICIENT_EVIDENCE,
                requiresApproval = true,
                summary = "No synced sessions in ${input.missingImportsDays} days; confirm off-app activity before changing targets.",
                reasonCode = "WEEKLY_INSUFFICIENT_EVIDENCE"
            )
            input.partialAdherenceOrOvershoot || input.unverifiedRecoveryOnMajorStep -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.CONSOLIDATE,
                requiresApproval = true,
                summary = "Repeat your current training dose cleanly before stepping up volume or intensity.",
                reasonCode = "WEEKLY_CONSOLIDATE"
            )
            input.verifiedProgressionReady -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.PROGRESSION_ELIGIBLE,
                requiresApproval = false,
                summary = "Consistent completion with controlled effort keeps your scheduled step on track.",
                reasonCode = "WEEKLY_ON_TRACK_PROGRESSION"
            )
            else -> WeeklyReviewDecision(
                outcome = WeeklyReviewOutcome.CONTINUE,
                requiresApproval = false,
                summary = "Training is tracking smoothly along your approved plan.",
                reasonCode = "WEEKLY_CONTINUE"
            )
        }
    }

    /**
     * 8-Dimension Conditional Gate Activation-Time Revalidation (`CD-08`, `D26`).
     * Never activates ADVANCE or CONSERVATIVE_HOLD blindly if new pain or tighter caps appeared!
     */
    fun evaluateConditionalBranchActivation(
        gate: ConditionalProgressionGate,
        requestedBranch: ContinuationBranchKind,
        ctx: ActivationValidationContext
    ): BranchActivationDecision {
        if (ctx.hasBlockingHealthHold && gate.spec.noHealthHoldRequired) {
            return BranchActivationDecision(
                allowed = false,
                activatedBranch = ContinuationBranchKind.NONE,
                fallbackScopeRequired = true,
                reason = "HEALTH_SUSPENDED: Active health hold blocks branch activation."
            )
        }
        if (ctx.nowUtc.isAfter(gate.validUntilUtc)) {
            return BranchActivationDecision(
                allowed = false,
                activatedBranch = ContinuationBranchKind.NONE,
                fallbackScopeRequired = true,
                reason = "PREREQUISITE_EXPIRED: Gate validity window elapsed before activation."
            )
        }
        if (ctx.proposedMinutesExceedsDayCap || ctx.violatesSpacing) {
            return BranchActivationDecision(
                allowed = false,
                activatedBranch = ContinuationBranchKind.NONE,
                fallbackScopeRequired = true,
                reason = "ACTIVATION_REVALIDATION_FAILED: Current day cap or spacing cannot fit this branch cleanly."
            )
        }
        if (requestedBranch == ContinuationBranchKind.ADVANCE) {
            val missing = buildList {
                if (gate.spec.completionRequired && gate.statuses["completionRequired"] != PrerequisiteStatus.SATISFIED) add("completionRequired")
                if (gate.spec.intensityAdherenceRequired && gate.statuses["intensityAdherenceRequired"] != PrerequisiteStatus.SATISFIED) add("intensityAdherenceRequired")
                if (gate.spec.techniqueCleanRequired && gate.statuses["techniqueCleanRequired"] != PrerequisiteStatus.SATISFIED) add("techniqueCleanRequired")
                if (gate.spec.recoveryOrContinuityRequired && gate.statuses["recoveryOrContinuityRequired"] != PrerequisiteStatus.SATISFIED) add("recoveryOrContinuityRequired")
            }
            if (missing.isNotEmpty() || ctx.proposedVolumeExceedsRollingDmax30) {
                return BranchActivationDecision(
                    allowed = false,
                    activatedBranch = ContinuationBranchKind.NONE,
                    fallbackScopeRequired = missing.isEmpty(),
                    reason = if (ctx.proposedVolumeExceedsRollingDmax30) {
                        "ACTIVATION_REVALIDATION_FAILED: Step exceeds current rolling Dmax30 limit."
                    } else {
                        "PREREQ_UNVERIFIED: Missing clauses ${missing.joinToString(", ")}."
                    }
                )
            }
        }
        if (requestedBranch !in gate.preApprovedBranches) {
            return BranchActivationDecision(
                allowed = false,
                activatedBranch = ContinuationBranchKind.NONE,
                fallbackScopeRequired = true,
                reason = "BRANCH_NOT_PREAPPROVED: Branch requires explicit plan approval."
            )
        }
        return BranchActivationDecision(
            allowed = true,
            activatedBranch = requestedBranch,
            fallbackScopeRequired = false,
            reason = "Branch revalidated cleanly against current state."
        )
    }

    /**
     * OCC Transactional Plan Revision Commit & Full Transitive Downstream Revalidation (`CD-06`, `CD-07`, `CD-09`, `D23`).
     */
    fun validateAndCommitRevision(
        candidate: PlanRevisionCandidate,
        currentPlanVersion: Int,
        currentFingerprint: OccInputFingerprint,
        commitAtUtc: Instant,
        activeLockSessionIds: Set<String> = emptySet(),
        downstreamWeekValidator: (Int) -> Boolean = { true }
    ): OccCommitOutcome {
        if (commitAtUtc.isAfter(candidate.expiresAtUtc)) {
            return OccCommitOutcome.Rejected(
                reasonCode = "PROPOSAL_EXPIRED_TTL",
                explanation = "Proposal ${candidate.candidateId} expired after ${Engine19CoachingPolicyRegistry.CANDIDATE_PROPOSAL_TTL_DAYS} days."
            )
        }
        if (candidate.expectedBasePlanVersion != currentPlanVersion) {
            return OccCommitOutcome.Rejected(
                reasonCode = "OCC_VERSION_CONFLICT",
                explanation = "Plan version changed from ${candidate.expectedBasePlanVersion} to $currentPlanVersion; refresh proposal before approving."
            )
        }
        val currentHash = currentFingerprint.computeSha256()
        if (candidate.inputStateHash != currentHash) {
            return OccCommitOutcome.Rejected(
                reasonCode = "OCC_INPUT_STATE_STALE",
                explanation = "Athlete state or restrictions changed since this proposal was generated."
            )
        }
        if (candidate.targetSessionIds.any { it in activeLockSessionIds }) {
            return OccCommitOutcome.Rejected(
                reasonCode = "LOCKED_SESSION_COLLISION",
                explanation = "Proposal touches a locked session; ask the athlete before modifying locked workouts."
            )
        }
        val failedWeek = candidate.affectedWeekIndices.firstOrNull { !downstreamWeekValidator(it) }
        if (failedWeek != null) {
            return OccCommitOutcome.Rejected(
                reasonCode = "TRANSITIVE_DOWNSTREAM_INVALID_WEEK_$failedWeek",
                explanation = "Transitive downstream revalidation failed at week index $failedWeek."
            )
        }
        return OccCommitOutcome.Committed(
            newPlanVersion = currentPlanVersion + 1,
            candidateId = candidate.candidateId,
            archivedPriorVersion = currentPlanVersion,
            validatedWeeksCount = candidate.affectedWeekIndices.size
        )
    }

    fun buildUndoRevision(
        priorPlanSnapshotVersion: Int,
        currentPlanVersion: Int,
        currentFingerprint: OccInputFingerprint,
        nowUtc: Instant,
        remainingWeekIndices: List<Int>
    ): PlanRevisionCandidate = PlanRevisionCandidate(
        candidateId = "undo-v$priorPlanSnapshotVersion-at-${nowUtc.epochSecond}",
        scope = RevisionScope.SCOPE_C_WEEKLY_SCHEDULE_REVISION,
        expectedBasePlanVersion = currentPlanVersion,
        inputStateHash = currentFingerprint.computeSha256(),
        createdAtUtc = nowUtc,
        expiresAtUtc = nowUtc.plus(1, ChronoUnit.HOURS),
        affectedWeekIndices = remainingWeekIndices,
        reasonSummary = "Non-destructive restore of future planned sessions from version $priorPlanSnapshotVersion without deleting observed logs."
    )

    private fun buildHoldMessage(hold: HealthHoldRecord): String = when (hold.tier) {
        HealthEscalationTier.H1_POTENTIAL_EMERGENCY ->
            "Stop all training right away and seek urgent medical evaluation for cardiopulmonary or neurological warning symptoms."
        HealthEscalationTier.H2_CLINICAL_EVALUATION_REQUIRED ->
            "Affected training is paused pending clinical evaluation. Self-directed test sessions are disabled until clinical clearance is reported."
        HealthEscalationTier.H3_TEMPORARY_CONSERVATIVE_HOLD ->
            "A temporary conservative hold is active. Confirm symptom resolution before resuming progression or intensive efforts."
        HealthEscalationTier.H4_ORDINARY_FATIGUE_OR_SORENESS ->
            "Ordinary fatigue or soreness noted; keep effort controlled."
    }
}

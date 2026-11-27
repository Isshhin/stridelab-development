package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

internal enum class AdaptationAuthorityMode {
    SUPERVISORY_APPROVAL,
    OPT_IN_BOUNDED_AUTONOMY
}

internal enum class AdaptiveAdjustmentKind {
    DOWNWARD_RECOVERY_TRIM,
    INDIVIDUAL_TAPER_DURATION,
    DURABILITY_ENDURANCE_HOLD,
    MUSCLE_VOLUME_TRIM,
    HYBRID_INTERFERENCE_DAMPING,
    PERFORMANCE_PROGRESSION_PROPOSAL,
    HOLD_TIER_A_BASELINE
}

internal enum class AthleteResponseArchetype {
    FOUNDATION_BUILDER,
    FAST_RESPONDER,
    FATIGUE_SENSITIVE_OR_OVERREACHING,
    ENDURANCE_DURABILITY_LIMITED,
    HYBRID_BALANCER
}

internal data class AthleteAdaptiveResponseProfile(
    val accountId: String,
    val updatedAtUtc: Instant,
    val authorityMode: AdaptationAuthorityMode = AdaptationAuthorityMode.SUPERVISORY_APPROVAL,
    val consistentHistoryDays: Int = 0,
    val csDPrimeBelief: JointCsDPrimeBelief? = null,
    val durabilityProfile: DurabilityProfile? = null,
    val strengthE1rms: Map<String, BayesianE1rmState> = emptyMap(),
    val muscleRecoverableBands: Map<String, MuscleRecoverableBand> = emptyMap(),
    val fatiguePosterior: FatiguePosteriorState? = null,
    val kineticsProfile: IndividualKineticsProfile? = null,
    val promotionVerdicts: Map<String, ModelPromotionVerdict> = emptyMap()
) {
    init {
        RecordPolicy.identifier(accountId)
    }
}

internal data class AdaptiveAdjustmentAction(
    val discipline: String,
    val kind: AdaptiveAdjustmentKind,
    val volumeMultiplier: Double = 1.0,
    val setDelta: Int = 0,
    val taperDaysSuggestion: Int? = null,
    val requiresApproval: Boolean,
    val autoAppliedUnderBoundedAutonomy: Boolean,
    val undoReceiptId: String? = null,
    val explanation: String
)

internal data class AdaptivePlanEvaluationResult(
    val profile: AthleteAdaptiveResponseProfile,
    val archetype: AthleteResponseArchetype,
    val coachBriefingSummary: String,
    val accessibleNextStep: String,
    val actions: List<AdaptiveAdjustmentAction>,
    val occCandidate: PlanRevisionCandidate?,
    val autonomyAppliedCount: Int,
    val fallbackToTierAReasons: List<String>
)

/**
 * Engine 2.0 Dynamic Multi-Discipline Adaptive Engine & Governance (`E2-09`) + Local Persistence.
 *
 * Acts as an experienced all-around coach overseeing diverse athletes cleanly:
 * - Synthesizes Running CS/D' + Durability, Strength Bayesian e1RMs + Recoverable Volume Bands,
 *   3-State Fatigue Filter, and Impulse-Response Kinetics gated behind Track 8 §11 verdicts.
 * - Defaults strictly to `SUPERVISORY_APPROVAL` (emitting Engine 1.9 OCC revision candidates).
 * - Enforces tight guardrails on `OPT_IN_BOUNDED_AUTONOMY` (`>= 28 d` history, `0` active H1-H3 holds,
 *   `0` reversal instability, `!hasLockedConflictOrRaceWeek`, strictly downward recovery <= 10% easy or -1 set,
 *   always generating an immutable 1-tap `undoReceiptId`).
 */
internal object Engine20AdaptivePlanEngine {

    fun classifyArchetype(
        profile: AthleteAdaptiveResponseProfile,
        actions: List<AdaptiveAdjustmentAction>
    ): AthleteResponseArchetype {
        val fatigue = profile.fatiguePosterior
        val dur = profile.durabilityProfile
        val hasHybridOverlap = actions.any { it.kind == AdaptiveAdjustmentKind.HYBRID_INTERFERENCE_DAMPING } ||
            (profile.muscleRecoverableBands.isNotEmpty() && dur != null)
        return when {
            profile.consistentHistoryDays < 14 -> AthleteResponseArchetype.FOUNDATION_BUILDER
            hasHybridOverlap -> AthleteResponseArchetype.HYBRID_BALANCER
            fatigue != null && !fatigue.abstainedInsufficientSignals &&
                fatigue.probNonFunctionalRisk >= Engine20AdaptivePolicyRegistry.NON_FUNCTIONAL_FATIGUE_ACTION_THRESHOLD ->
                AthleteResponseArchetype.FATIGUE_SENSITIVE_OR_OVERREACHING
            dur != null && dur.tier == DurabilityTier.EARLY_DECOUPLING ->
                AthleteResponseArchetype.ENDURANCE_DURABILITY_LIMITED
            actions.any { it.kind == AdaptiveAdjustmentKind.PERFORMANCE_PROGRESSION_PROPOSAL } ->
                AthleteResponseArchetype.FAST_RESPONDER
            else -> AthleteResponseArchetype.FOUNDATION_BUILDER
        }
    }

    fun synthesizeAdaptiveAdjustments(
        profile: AthleteAdaptiveResponseProfile,
        decisionState: DecisionStateSnapshot,
        basePlanVersion: Int,
        occFingerprint: OccInputFingerprint,
        nowUtc: Instant,
        hasLockedConflictOrRaceWeek: Boolean = false
    ): AdaptivePlanEvaluationResult {
        val actions = mutableListOf<AdaptiveAdjustmentAction>()
        val fallbacks = mutableListOf<String>()

        val hasActiveSafetyHold = decisionState.activeHealthHolds.values.any { it.isActive }
        val reversalInstability = decisionState.recentReversalCount(
            CoachingDecisionClass.SCOPE_D_RECALIBRATION, nowUtc
        ) >= Engine19CoachingPolicyRegistry.REVERSAL_TRIGGER_COUNT

        val canUseBoundedAutonomy = profile.authorityMode == AdaptationAuthorityMode.OPT_IN_BOUNDED_AUTONOMY &&
            profile.consistentHistoryDays >= Engine20AdaptivePolicyRegistry.AUTONOMY_MIN_CONSISTENT_DAYS &&
            !hasActiveSafetyHold &&
            !reversalInstability &&
            !hasLockedConflictOrRaceWeek

        // Health holds supersede all autonomous or adaptive escalations cleanly
        if (hasActiveSafetyHold) {
            val holdMsg = sanitize("Active clinical or health hold takes precedence; dynamic adaptive changes are suspended until cleared.")
            val holdActions = listOf(
                AdaptiveAdjustmentAction(
                    discipline = "all",
                    kind = AdaptiveAdjustmentKind.HOLD_TIER_A_BASELINE,
                    requiresApproval = false,
                    autoAppliedUnderBoundedAutonomy = false,
                    explanation = holdMsg
                )
            )
            return AdaptivePlanEvaluationResult(
                profile = profile,
                archetype = classifyArchetype(profile, holdActions),
                coachBriefingSummary = sanitize("Coach check-in: an active health restriction protects your recovery first, so adaptive load changes are paused."),
                accessibleNextStep = sanitize("Rest the affected area or confirm clinical clearance when ready."),
                actions = holdActions,
                occCandidate = null,
                autonomyAppliedCount = 0,
                fallbackToTierAReasons = listOf("ACTIVE_HEALTH_HOLD_SUPERSEDES_ADAPTATION")
            )
        }

        // 1. Fatigue-driven downward/recovery micro-adjustment (`E2-05` + `E2-09`)
        val fatigueVerdict = profile.promotionVerdicts["E2-05"] ?: ModelPromotionVerdict.SHADOW_CALIBRATING
        val fatigue = profile.fatiguePosterior
        if (fatigue != null && !fatigue.abstainedInsufficientSignals &&
            fatigue.probNonFunctionalRisk >= Engine20AdaptivePolicyRegistry.NON_FUNCTIONAL_FATIGUE_ACTION_THRESHOLD
        ) {
            if (fatigueVerdict == ModelPromotionVerdict.ACTIVE_PERSONALIZED) {
                val autoApply = canUseBoundedAutonomy
                actions += AdaptiveAdjustmentAction(
                    discipline = "run",
                    kind = AdaptiveAdjustmentKind.DOWNWARD_RECOVERY_TRIM,
                    volumeMultiplier = 0.90,
                    setDelta = 0,
                    requiresApproval = !autoApply,
                    autoAppliedUnderBoundedAutonomy = autoApply,
                    undoReceiptId = if (autoApply) "undo-e20-fatigue-${nowUtc.epochSecond}" else null,
                    explanation = sanitize(
                        if (autoApply) {
                            "Auto-trimmed upcoming easy running volume by 10% under bounded autonomy due to elevated fatigue risk (${(fatigue.probNonFunctionalRisk * 100).toInt()}%); tap undo anytime."
                        } else {
                            "Proposing a 10% easy running volume reduction and recovery day due to elevated fatigue risk (${(fatigue.probNonFunctionalRisk * 100).toInt()}%)."
                        }
                    )
                )
            } else {
                fallbacks += "E2-05 fatigue model (${fatigueVerdict.name}) deferred to Tier A subjective trend review."
            }
        }

        // 2. Running Durability & CS/D' Adaptation (`MOD-B1`, `MOD-B2/B3`)
        val dur = profile.durabilityProfile
        if (dur != null && dur.tier == DurabilityTier.EARLY_DECOUPLING && dur.cleanSampleCount > 0) {
            val autoApply = canUseBoundedAutonomy
            actions += AdaptiveAdjustmentAction(
                discipline = "run",
                kind = AdaptiveAdjustmentKind.DURABILITY_ENDURANCE_HOLD,
                volumeMultiplier = 0.92,
                requiresApproval = !autoApply,
                autoAppliedUnderBoundedAutonomy = autoApply,
                undoReceiptId = if (autoApply) "undo-e20-dur-${nowUtc.epochSecond}" else null,
                explanation = sanitize("Observed cardiac drift around ${dur.estimatedOnsetMinutes ?: 75} min across clean ECG runs; holding long-run progression at 92% with extra fueling cues.")
            )
        }

        val csVerdict = profile.promotionVerdicts["MOD-B1"] ?: ModelPromotionVerdict.SHADOW_CALIBRATING
        val cs = profile.csDPrimeBelief
        if (cs != null && cs.valid && csVerdict == ModelPromotionVerdict.ACTIVE_PERSONALIZED &&
            (fatigue?.probNonFunctionalRisk ?: 0.0) < 0.35 &&
            (dur == null || dur.tier != DurabilityTier.EARLY_DECOUPLING)
        ) {
            // Upward progression ALWAYS requires explicit supervisory OCC approval (`requiresApproval = true`)!
            actions += AdaptiveAdjustmentAction(
                discipline = "run",
                kind = AdaptiveAdjustmentKind.PERFORMANCE_PROGRESSION_PROPOSAL,
                volumeMultiplier = 1.05,
                requiresApproval = true,
                autoAppliedUnderBoundedAutonomy = false,
                explanation = sanitize("Verified severe trials calibrate CS at ${"%.2f".format(cs.vCsMps)} m/s with low fatigue risk; proposing a conservative +5% threshold progression for approval.")
            )
        } else if (cs != null && (!cs.valid || csVerdict != ModelPromotionVerdict.ACTIVE_PERSONALIZED)) {
            fallbacks += cs.abstentionReason ?: "MOD-B1 CS/D' (${csVerdict.name}) fell back to Tier A single-anchor pace zone rules."
        }

        // 3. Individual Strength Local Recoverable Volume Band (`MOD-B4`, `E2-03`)
        val modB4Verdict = profile.promotionVerdicts["MOD-B4"] ?: ModelPromotionVerdict.SHADOW_CALIBRATING
        for ((muscleId, band) in profile.muscleRecoverableBands.entries.sortedBy { it.key }) {
            if (band.tier == MuscleVolumeToleranceTier.LOW_TOLERANCE &&
                band.fractionalWeeklySetsLatest > band.recommendedMaxFractionalSets
            ) {
                if (modB4Verdict == ModelPromotionVerdict.ACTIVE_PERSONALIZED) {
                    val autoApply = canUseBoundedAutonomy
                    actions += AdaptiveAdjustmentAction(
                        discipline = "str",
                        kind = AdaptiveAdjustmentKind.MUSCLE_VOLUME_TRIM,
                        setDelta = -Engine20AdaptivePolicyRegistry.AUTONOMY_MAX_SET_BACKOFF,
                        requiresApproval = !autoApply,
                        autoAppliedUnderBoundedAutonomy = autoApply,
                        undoReceiptId = if (autoApply) "undo-e20-str-$muscleId-${nowUtc.epochSecond}" else null,
                        explanation = sanitize("Local $muscleId fractional load (${"%.1f".format(band.fractionalWeeklySetsLatest)} sets) exceeded your observed recovery ceiling (${"%.1f".format(band.recommendedMaxFractionalSets)}); trimming 1 working set.")
                    )
                } else {
                    fallbacks += "MOD-B4 fractional sets for $muscleId (${modB4Verdict.name}) deferred to Tier A Direct/Indirect ledgers."
                }
            }
        }

        // 4. Individual Impulse-Response Taper Timing (`MOD-C1`)
        val kinetics = profile.kineticsProfile
        if (kinetics != null && kinetics.identifiable) {
            actions += AdaptiveAdjustmentAction(
                discipline = "run",
                kind = AdaptiveAdjustmentKind.INDIVIDUAL_TAPER_DURATION,
                taperDaysSuggestion = kinetics.recommendedTaperDays,
                requiresApproval = true,
                autoAppliedUnderBoundedAutonomy = false,
                explanation = sanitize("Individual fatigue clearance time constant (tau2=${kinetics.tau2FatigueDays.toInt()} d) supports a ${kinetics.recommendedTaperDays}-day race taper proposal.")
            )
        } else if (kinetics != null && !kinetics.identifiable) {
            fallbacks += kinetics.fallbackReason ?: "MOD-C1 kinetics non-identifiable; keeping Track 4 static taper."
        }

        // 5. Hybrid cross-discipline damping if both high fatigue risk AND high lower-body fractional load
        val lowerOverloaded = profile.muscleRecoverableBands.any { (m, b) ->
            m in setOf("quadriceps", "hamstrings", "gluteus_maximus", "calves") && b.tier == MuscleVolumeToleranceTier.LOW_TOLERANCE
        }
        if ((fatigue?.probNonFunctionalRisk ?: 0.0) >= 0.50 && lowerOverloaded && actions.any { it.discipline == "run" }) {
            val autoApply = canUseBoundedAutonomy
            actions += AdaptiveAdjustmentAction(
                discipline = "hybrid",
                kind = AdaptiveAdjustmentKind.HYBRID_INTERFERENCE_DAMPING,
                setDelta = -1,
                requiresApproval = !autoApply,
                autoAppliedUnderBoundedAutonomy = autoApply,
                undoReceiptId = if (autoApply) "undo-e20-hyb-${nowUtc.epochSecond}" else null,
                explanation = sanitize("Combined systemic fatigue and lower-body muscle overload detected; backing off 1 secondary lower strength set to protect run quality.")
            )
        }

        if (actions.isEmpty()) {
            actions += AdaptiveAdjustmentAction(
                discipline = "all",
                kind = AdaptiveAdjustmentKind.HOLD_TIER_A_BASELINE,
                requiresApproval = false,
                autoAppliedUnderBoundedAutonomy = false,
                explanation = sanitize("Athlete profile tracking well under Tier A baselines; no extra adaptive override needed.")
            )
        }

        val approvalActions = actions.filter { it.requiresApproval }
        val occCandidate = if (approvalActions.isNotEmpty()) {
            val topScope = if (approvalActions.any { it.kind == AdaptiveAdjustmentKind.INDIVIDUAL_TAPER_DURATION }) {
                RevisionScope.SCOPE_D_MACROCYCLE_REVISION
            } else {
                RevisionScope.SCOPE_C_WEEKLY_SCHEDULE_REVISION
            }
            PlanRevisionCandidate(
                candidateId = "e20-adapt-v$basePlanVersion-${nowUtc.epochSecond}",
                scope = topScope,
                expectedBasePlanVersion = basePlanVersion,
                inputStateHash = occFingerprint.computeSha256(),
                createdAtUtc = nowUtc,
                expiresAtUtc = nowUtc.plus(Engine19CoachingPolicyRegistry.CANDIDATE_PROPOSAL_TTL_DAYS, ChronoUnit.DAYS),
                affectedWeekIndices = listOf(1, 2, 3, 4),
                reasonSummary = approvalActions.joinToString(" ") { it.explanation }
            )
        } else null

        val autonomyCount = actions.count { it.autoAppliedUnderBoundedAutonomy }
        val archetype = classifyArchetype(profile, actions)
        val (briefing, nextStep) = buildAccessibleBriefing(archetype, actions)
        return AdaptivePlanEvaluationResult(
            profile = profile,
            archetype = archetype,
            coachBriefingSummary = sanitize(briefing),
            accessibleNextStep = sanitize(nextStep),
            actions = actions,
            occCandidate = occCandidate,
            autonomyAppliedCount = autonomyCount,
            fallbackToTierAReasons = fallbacks
        )
    }

    private fun buildAccessibleBriefing(
        archetype: AthleteResponseArchetype,
        actions: List<AdaptiveAdjustmentAction>
    ): Pair<String, String> = when (archetype) {
        AthleteResponseArchetype.FOUNDATION_BUILDER ->
            "Building your steady habit base with clear effort guidance that works whether or not you wear a watch." to
                "Complete your next session at comfortable conversational effort and log how it felt."
        AthleteResponseArchetype.FAST_RESPONDER ->
            "Consistent sessions with low fatigue reserve indicate readiness for a small, controlled progression." to
                (actions.firstOrNull()?.explanation ?: "Review and approve the next progression proposal.")
        AthleteResponseArchetype.FATIGUE_SENSITIVE_OR_OVERREACHING ->
            "Elevated fatigue signals call for protecting recovery right now so your training stays sustainable." to
                (actions.firstOrNull()?.explanation ?: "Ease today's volume and prioritize sleep before adding intensity.")
        AthleteResponseArchetype.ENDURANCE_DURABILITY_LIMITED ->
            "Late-run heart rate drift suggests consolidating steady aerobic duration and fueling before extending length." to
                "Keep your next long effort relaxed early and practice steady carbohydrate hydration."
        AthleteResponseArchetype.HYBRID_BALANCER ->
            "Balancing running and strength together: moderating local lower-body set load protects key running sessions." to
                (actions.firstOrNull()?.explanation ?: "Keep secondary lower strength controlled around 2 to 3 RIR.")
    }

    private fun sanitize(text: String): String =
        Engine19ExplanationAndNotificationPolicy.validateAndSanitizeCopy(text, techniqueCoachVerified = false).sanitizedText
}

/**
 * Durable local-only storage for `AthleteAdaptiveResponseProfile` under `m1-adaptive-profile-*`.
 * Zero SQLite schema changes, zero cloud outbox writes, identical signed-out/offline behavior.
 */
internal class Engine20AdaptiveProfileLocalStore(
    private val backend: M1LocalRecordBackend
) {
    fun saveProfile(owner: String, profile: AthleteAdaptiveResponseProfile, nowMs: Long) {
        require(profile.accountId == owner) { "INV-03 violation: profile owner ${profile.accountId} != $owner" }
        backend.putLocal(owner, recordKey(owner), encode(profile).toString(), nowMs)
    }

    fun loadProfile(owner: String, defaultUtc: Instant = Instant.EPOCH): AthleteAdaptiveResponseProfile {
        RecordPolicy.identifier(owner)
        val row = backend.getLocal(owner, recordKey(owner))
            ?: return AthleteAdaptiveResponseProfile(accountId = owner, updatedAtUtc = defaultUtc)
        return decode(row.payload, owner) ?: AthleteAdaptiveResponseProfile(accountId = owner, updatedAtUtc = defaultUtc)
    }

    private fun encode(p: AthleteAdaptiveResponseProfile): JSONObject {
        val obj = JSONObject()
            .put("accountId", p.accountId)
            .put("updatedAtUtc", p.updatedAtUtc.toString())
            .put("authorityMode", p.authorityMode.name)
            .put("consistentHistoryDays", p.consistentHistoryDays)
        val verdictsObj = JSONObject()
        for ((k, v) in p.promotionVerdicts) verdictsObj.put(k, v.name)
        obj.put("promotionVerdicts", verdictsObj)

        val e1rmArr = JSONArray()
        for ((_, st) in p.strengthE1rms) {
            e1rmArr.put(
                JSONObject()
                    .put("exerciseId", st.exerciseId)
                    .put("meanLogE1rm", st.meanLogE1rm)
                    .put("varianceLogE1rm", st.varianceLogE1rm)
                    .put("observationCount", st.observationCount)
                    .put("lastObservedOn", st.lastObservedOn.toString())
            )
        }
        obj.put("strengthE1rms", e1rmArr)
        return obj
    }

    private fun decode(raw: String, expectedOwner: String): AthleteAdaptiveResponseProfile? = runCatching {
        val obj = JSONObject(raw)
        val accountId = obj.getString("accountId")
        if (accountId != expectedOwner) return null
        val verdictsObj = obj.optJSONObject("promotionVerdicts") ?: JSONObject()
        val verdicts = LinkedHashMap<String, ModelPromotionVerdict>()
        for (k in verdictsObj.keys()) {
            verdicts[k] = ModelPromotionVerdict.valueOf(verdictsObj.getString(k))
        }
        val e1rmArr = obj.optJSONArray("strengthE1rms") ?: JSONArray()
        val e1rms = LinkedHashMap<String, BayesianE1rmState>()
        for (i in 0 until e1rmArr.length()) {
            val e = e1rmArr.getJSONObject(i)
            val st = BayesianE1rmState(
                exerciseId = e.getString("exerciseId"),
                meanLogE1rm = e.getDouble("meanLogE1rm"),
                varianceLogE1rm = e.getDouble("varianceLogE1rm"),
                observationCount = e.getInt("observationCount"),
                lastObservedOn = LocalDate.parse(e.getString("lastObservedOn"))
            )
            e1rms[st.exerciseId] = st
        }
        AthleteAdaptiveResponseProfile(
            accountId = accountId,
            updatedAtUtc = Instant.parse(obj.getString("updatedAtUtc")),
            authorityMode = AdaptationAuthorityMode.valueOf(
                obj.optString("authorityMode", AdaptationAuthorityMode.SUPERVISORY_APPROVAL.name)
            ),
            consistentHistoryDays = obj.optInt("consistentHistoryDays", 0),
            strengthE1rms = e1rms,
            promotionVerdicts = verdicts
        )
    }.getOrNull()

    companion object {
        const val PROFILE_PREFIX = "m1-adaptive-profile-"

        internal fun recordKey(owner: String): String {
            RecordPolicy.identifier(owner)
            val digest = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(StandardCharsets.UTF_8))
            return PROFILE_PREFIX + digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

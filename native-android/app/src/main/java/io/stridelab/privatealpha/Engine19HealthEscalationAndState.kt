package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

internal enum class HealthEscalationTier {
    H1_POTENTIAL_EMERGENCY,
    H2_CLINICAL_EVALUATION_REQUIRED,
    H3_TEMPORARY_CONSERVATIVE_HOLD,
    H4_ORDINARY_FATIGUE_OR_SORENESS
}

internal sealed interface HealthRestrictionScope {
    data object Systemic : HealthRestrictionScope
    data class Localized(
        val regions: Set<String>,
        val disciplines: Set<AthleteDiscipline>
    ) : HealthRestrictionScope
}

internal enum class HealthClearanceProvenance {
    NONE,
    USER_REPORTED_CLINICAL_CLEARANCE,
    USER_REPORTED_SYMPTOM_FREE
}

internal data class HealthSymptomReport(
    val chestPainOrSyncopeOrErraticPalpitations: Boolean = false,
    val focalBonyOrWorseningGaitPain: Boolean = false,
    val acuteJointSwellingLockingOrInstability: Boolean = false,
    val numbnessOrTingling: Boolean = false,
    val repeatedSymptomAbortedSessions: Boolean = false,
    val postFebrileCardiopulmonaryLingering: Boolean = false,
    val recentFebrileIllnessDaysAgo: Int? = null,
    val mildStiffnessOrUncomplicatedCold: Boolean = false,
    val ordinaryDiffuseSorenessDoms: Boolean = false,
    val localizedRegion: String? = null,
    val affectedDisciplines: Set<AthleteDiscipline> = emptySet(),
    val summaryNote: String = ""
)

internal data class HealthHoldRecord(
    val restrictionId: String,
    val accountId: String,
    val trigger: String,
    val scope: HealthRestrictionScope,
    val tier: HealthEscalationTier,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant = createdAtUtc,
    val monotonicSeq: Long = 1L,
    val clearanceProvenance: HealthClearanceProvenance = HealthClearanceProvenance.NONE,
    val clearedAtUtc: Instant? = null,
    val clearedReason: String? = null,
    val minHrQuarantineUntilUtc: Instant? = null
) {
    init {
        RecordPolicy.identifier(restrictionId)
        RecordPolicy.identifier(accountId)
        require(!updatedAtUtc.isBefore(createdAtUtc))
    }

    val isActive: Boolean
        get() = clearedAtUtc == null && tier != HealthEscalationTier.H4_ORDINARY_FATIGUE_OR_SORENESS

    val blocksSelfDirectedTesting: Boolean
        get() = isActive && tier in setOf(
            HealthEscalationTier.H1_POTENTIAL_EMERGENCY,
            HealthEscalationTier.H2_CLINICAL_EVALUATION_REQUIRED
        )

    fun allowsUnaffectedDiscipline(discipline: AthleteDiscipline): Boolean {
        if (!isActive) return true
        return when (scope) {
            is HealthRestrictionScope.Systemic -> false
            is HealthRestrictionScope.Localized ->
                tier != HealthEscalationTier.H1_POTENTIAL_EMERGENCY && discipline !in scope.disciplines
        }
    }

    fun toM1SafetyRestriction(policyVersion: String = Engine15M1PolicyRegistry.POLICY_VERSION): AthleteSafetyRestriction? {
        if (tier == HealthEscalationTier.H4_ORDINARY_FATIGUE_OR_SORENESS) return null
        val severity = when (tier) {
            HealthEscalationTier.H1_POTENTIAL_EMERGENCY,
            HealthEscalationTier.H2_CLINICAL_EVALUATION_REQUIRED -> SafetyRestrictionSeverity.HARD_BLOCK
            HealthEscalationTier.H3_TEMPORARY_CONSERVATIVE_HOLD -> SafetyRestrictionSeverity.CAP_LIMITED
            HealthEscalationTier.H4_ORDINARY_FATIGUE_OR_SORENESS -> SafetyRestrictionSeverity.ADVISORY_CAUTION
        }
        val provenance = when (clearanceProvenance) {
            HealthClearanceProvenance.USER_REPORTED_CLINICAL_CLEARANCE -> SafetyRestrictionProvenance.MEDICAL_CLEARANCE_REQUIRED
            else -> SafetyRestrictionProvenance.USER_DECLARED
        }
        val discs = when (scope) {
            is HealthRestrictionScope.Systemic -> emptySet()
            is HealthRestrictionScope.Localized -> scope.disciplines
        }
        return AthleteSafetyRestriction(
            restrictionId = restrictionId,
            accountId = accountId,
            kind = if (tier == HealthEscalationTier.H1_POTENTIAL_EMERGENCY) {
                SafetyRestrictionKind.MEDICAL_OR_REST_OVERRIDE
            } else {
                SafetyRestrictionKind.INJURY_OR_PAIN
            },
            disciplines = discs,
            severity = severity,
            provenance = provenance,
            maxSessionIntensityCap = if (tier == HealthEscalationTier.H3_TEMPORARY_CONSERVATIVE_HOLD) {
                SafetyIntensityCap.EASY_AEROBIC_ONLY
            } else null,
            note = trigger.take(AthleteSafetyRestriction.MAX_NOTE_LENGTH),
            createdAtUtc = createdAtUtc,
            updatedAtUtc = updatedAtUtc,
            clearedAtUtc = clearedAtUtc,
            clearedReason = clearedReason,
            policyVersion = policyVersion
        )
    }
}

internal enum class CoachingDecisionClass {
    HEALTH_HOLD,
    BLOCKING_CLARIFICATION,
    ACUTE_RECOVERY_OR_DELOAD,
    STRUCTURAL_SCHEDULE_REPAIR,
    SCOPE_D_RECALIBRATION,
    PROGRESSION_OR_CONSOLIDATION
}

internal data class PriorRecommendationRecord(
    val recommendationId: String,
    val decisionClass: CoachingDecisionClass,
    val summary: String,
    val status: String, // PENDING | APPROVED | REJECTED | EXPIRED
    val createdAtUtc: Instant,
    val expiresAtUtc: Instant
)

internal data class ApprovalRejectionRecord(
    val candidateId: String,
    val decisionClass: CoachingDecisionClass,
    val outcome: String, // APPROVED | REJECTED | UNDONE
    val recordedAtUtc: Instant
)

/**
 * Complete 12-Field Immutable `DecisionStateSnapshot` (Track 7 Refinements §2.1).
 *
 * `confirmationCounters` stores deduplicated `CanonicalActivityId` -> observation timestamp maps so
 * duplicate watch/Health-Connect imports NEVER double-count (`CD-01`).
 */
internal data class DecisionStateSnapshot(
    val athleteStateSnapshot: AthleteStateSnapshot? = null,
    val observedExecutionIds: Set<String> = emptySet(),
    val acceptedPlanVersion: Int = 1,
    val policyVersion: String = Engine19CoachingPolicyRegistry.POLICY_VERSION,
    val evaluationTimestamp: Instant = Instant.EPOCH,
    val activeHealthHolds: Map<String, HealthHoldRecord> = emptyMap(),
    val priorRecommendations: List<PriorRecommendationRecord> = emptyList(),
    val confirmationCounters: Map<String, Map<String, Instant>> = emptyMap(),
    val approvalAndRejectionHistory: List<ApprovalRejectionRecord> = emptyList(),
    val decisionCooldowns: Map<CoachingDecisionClass, Instant> = emptyMap(),
    val activityToPlanAssociations: Map<String, String> = emptyMap(),
    val primaryDisciplinePriority: String = "run",
    val optionalNotificationsSentThisWeek: Int = 0
) {
    fun uniqueFreshConfirmations(targetKey: String, asOfUtc: Instant): Set<String> {
        val cutoff = asOfUtc.minus(Engine19CoachingPolicyRegistry.CONFIRMATION_FRESHNESS_DAYS, ChronoUnit.DAYS)
        return confirmationCounters[targetKey].orEmpty()
            .filterValues { ts -> !ts.isBefore(cutoff) && !ts.isAfter(asOfUtc) }
            .keys
    }

    fun withRecordedExposure(targetKey: String, canonicalActivityId: String, observedAtUtc: Instant): DecisionStateSnapshot {
        val cutoff = observedAtUtc.minus(Engine19CoachingPolicyRegistry.CONFIRMATION_FRESHNESS_DAYS, ChronoUnit.DAYS)
        val current = confirmationCounters[targetKey].orEmpty()
            .filterValues { !it.isBefore(cutoff) }
            .toMutableMap()
        if (canonicalActivityId !in current) {
            current[canonicalActivityId] = observedAtUtc
        }
        return copy(confirmationCounters = confirmationCounters + (targetKey to current))
    }

    fun recentReversalCount(
        decisionClass: CoachingDecisionClass,
        asOfUtc: Instant
    ): Int {
        val windowStart = asOfUtc.minus(Engine19CoachingPolicyRegistry.REVERSAL_WINDOW_DAYS, ChronoUnit.DAYS)
        val relevant = approvalAndRejectionHistory
            .filter { it.decisionClass == decisionClass && !it.recordedAtUtc.isBefore(windowStart) && !it.recordedAtUtc.isAfter(asOfUtc) }
            .sortedBy { it.recordedAtUtc }
        var reversals = 0
        for (i in 1 until relevant.size) {
            if (relevant[i - 1].outcome == "APPROVED" && relevant[i].outcome in setOf("REJECTED", "UNDONE")) {
                reversals++
            } else if (relevant[i - 1].outcome == "REJECTED" && relevant[i].outcome == "APPROVED") {
                reversals++
            }
        }
        return reversals
    }
}

/**
 * Persistent 4-Tier Health & Clinical Escalation (`Riebe 2015`, `Schwellnus 2016`, `Elliott 2020`, `IR-10`),
 * Monotonic Anti-Rollback Safety Merge (`CD-10`, `IR-15`), and Local-Only Decision State Storage.
 */
internal object Engine19HealthEscalationAndState {

    fun classifyHealthSymptom(
        report: HealthSymptomReport,
        accountId: String,
        restrictionId: String,
        nowUtc: Instant,
        nextSeq: Long = 1L
    ): HealthHoldRecord? {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(restrictionId)

        val localizedScope = if (report.affectedDisciplines.isNotEmpty() || !report.localizedRegion.isNullOrBlank()) {
            HealthRestrictionScope.Localized(
                regions = setOfNotNull(report.localizedRegion?.trim()?.takeIf { it.isNotEmpty() }),
                disciplines = report.affectedDisciplines
            )
        } else {
            HealthRestrictionScope.Systemic
        }

        return when {
            report.chestPainOrSyncopeOrErraticPalpitations -> HealthHoldRecord(
                restrictionId = restrictionId,
                accountId = accountId,
                trigger = report.summaryNote.ifBlank { "Exertional cardiopulmonary or neurological warning signs" },
                scope = HealthRestrictionScope.Systemic,
                tier = HealthEscalationTier.H1_POTENTIAL_EMERGENCY,
                createdAtUtc = nowUtc,
                monotonicSeq = nextSeq
            )
            report.focalBonyOrWorseningGaitPain ||
                report.acuteJointSwellingLockingOrInstability ||
                report.numbnessOrTingling ||
                report.repeatedSymptomAbortedSessions ||
                report.postFebrileCardiopulmonaryLingering -> HealthHoldRecord(
                restrictionId = restrictionId,
                accountId = accountId,
                trigger = report.summaryNote.ifBlank { "Concerning focal or post-febrile symptom pattern" },
                scope = localizedScope,
                tier = HealthEscalationTier.H2_CLINICAL_EVALUATION_REQUIRED,
                createdAtUtc = nowUtc,
                monotonicSeq = nextSeq
            )
            report.recentFebrileIllnessDaysAgo != null || report.mildStiffnessOrUncomplicatedCold -> {
                val quarantineEnd = report.recentFebrileIllnessDaysAgo?.let {
                    nowUtc.plus(Engine19CoachingPolicyRegistry.POST_ILLNESS_MIN_HR_QUARANTINE_DAYS, ChronoUnit.DAYS)
                }
                HealthHoldRecord(
                    restrictionId = restrictionId,
                    accountId = accountId,
                    trigger = report.summaryNote.ifBlank { "Post-illness recovery or mild conservative hold" },
                    scope = if (report.recentFebrileIllnessDaysAgo != null) HealthRestrictionScope.Systemic else localizedScope,
                    tier = HealthEscalationTier.H3_TEMPORARY_CONSERVATIVE_HOLD,
                    createdAtUtc = nowUtc,
                    monotonicSeq = nextSeq,
                    minHrQuarantineUntilUtc = quarantineEnd
                )
            }
            report.ordinaryDiffuseSorenessDoms -> HealthHoldRecord(
                restrictionId = restrictionId,
                accountId = accountId,
                trigger = report.summaryNote.ifBlank { "Expected bilateral soreness or ordinary fatigue" },
                scope = localizedScope,
                tier = HealthEscalationTier.H4_ORDINARY_FATIGUE_OR_SORENESS,
                createdAtUtc = nowUtc,
                monotonicSeq = nextSeq
            )
            else -> null
        }
    }

    /**
     * Strictly non-overridable H1/H2 safety gate (`CD-11`, `D28`).
     * H2 CANNOT be cleared unless [provenance] is explicit [HealthClearanceProvenance.USER_REPORTED_CLINICAL_CLEARANCE].
     */
    fun attemptClearHold(
        hold: HealthHoldRecord,
        provenance: HealthClearanceProvenance,
        atUtc: Instant,
        reason: String? = null
    ): Pair<HealthHoldRecord, Boolean> {
        if (!hold.isActive) return hold to true
        val permitted = when (hold.tier) {
            HealthEscalationTier.H1_POTENTIAL_EMERGENCY -> false
            HealthEscalationTier.H2_CLINICAL_EVALUATION_REQUIRED ->
                provenance == HealthClearanceProvenance.USER_REPORTED_CLINICAL_CLEARANCE
            HealthEscalationTier.H3_TEMPORARY_CONSERVATIVE_HOLD ->
                provenance in setOf(
                    HealthClearanceProvenance.USER_REPORTED_SYMPTOM_FREE,
                    HealthClearanceProvenance.USER_REPORTED_CLINICAL_CLEARANCE
                )
            HealthEscalationTier.H4_ORDINARY_FATIGUE_OR_SORENESS -> true
        }
        if (!permitted) return hold to false
        val safeUpdated = if (atUtc.isAfter(hold.updatedAtUtc)) atUtc else hold.updatedAtUtc
        return hold.copy(
            updatedAtUtc = safeUpdated,
            monotonicSeq = hold.monotonicSeq + 1L,
            clearanceProvenance = provenance,
            clearedAtUtc = atUtc,
            clearedReason = reason?.trim()?.takeIf { it.isNotEmpty() } ?: provenance.name
        ) to true
    }

    /**
     * Causal monotonic safety merge (`CD-10`, `IR-15`).
     * Restoring an older backup never erases a newer active H1-H3 hold; equal sequence conflicts
     * resolve conservatively toward the active hold.
     */
    fun mergeHealthHoldsMonotonically(
        localHolds: Map<String, HealthHoldRecord>,
        importedBackupHolds: Map<String, HealthHoldRecord>,
        accountId: String
    ): Map<String, HealthHoldRecord> {
        val merged = LinkedHashMap<String, HealthHoldRecord>()
        val allIds = (localHolds.keys + importedBackupHolds.keys).sorted()
        for (id in allIds) {
            val local = localHolds[id]?.takeIf { it.accountId == accountId }
            val imported = importedBackupHolds[id]?.takeIf { it.accountId == accountId }
            val winner = when {
                local == null -> imported
                imported == null -> local
                local.monotonicSeq != imported.monotonicSeq ->
                    if (local.monotonicSeq > imported.monotonicSeq) local else imported
                local.updatedAtUtc != imported.updatedAtUtc ->
                    if (!local.updatedAtUtc.isBefore(imported.updatedAtUtc)) local else imported
                local.isActive && !imported.isActive -> local
                !local.isActive && imported.isActive -> imported
                else -> local
            }
            if (winner != null) {
                merged[id] = winner
            }
        }
        return merged
    }
}

/**
 * Durable local-only persistence for Engine 1.9 coaching state and H1-H3 health holds
 * (`m1-coach-hold-*` and `m1-coach-state-*`), using [M1LocalRecordBackend].
 * Never writes to cloud `outbox` and works identically signed out (`ADR-03`).
 */
internal class Engine19CoachingStateLocalStore(
    private val backend: M1LocalRecordBackend
) {
    fun saveHealthHold(owner: String, hold: HealthHoldRecord, nowMs: Long) {
        require(hold.accountId == owner) { "INV-03 violation: hold owner ${hold.accountId} != $owner" }
        backend.putLocal(owner, holdRecordKey(hold.restrictionId), encodeHold(hold).toString(), nowMs)
    }

    fun loadHealthHolds(owner: String): Map<String, HealthHoldRecord> {
        RecordPolicy.identifier(owner)
        val rows = backend.listByPrefix(owner, HOLD_PREFIX)
        val out = LinkedHashMap<String, HealthHoldRecord>()
        for (row in rows) {
            val parsed = decodeHold(row.payload, expectedOwner = owner) ?: continue
            out[parsed.restrictionId] = parsed
        }
        return out
    }

    fun saveDecisionState(owner: String, state: DecisionStateSnapshot, nowMs: Long) {
        RecordPolicy.identifier(owner)
        backend.putLocal(owner, stateRecordKey(owner), encodeState(state).toString(), nowMs)
    }

    fun loadDecisionState(owner: String): DecisionStateSnapshot {
        RecordPolicy.identifier(owner)
        val holds = loadHealthHolds(owner)
        val row = backend.getLocal(owner, stateRecordKey(owner))
            ?: return DecisionStateSnapshot(activeHealthHolds = holds)
        val decoded = decodeState(row.payload) ?: return DecisionStateSnapshot(activeHealthHolds = holds)
        return decoded.copy(activeHealthHolds = holds)
    }

    private fun encodeHold(hold: HealthHoldRecord): JSONObject {
        val obj = JSONObject()
            .put("restrictionId", hold.restrictionId)
            .put("accountId", hold.accountId)
            .put("trigger", hold.trigger)
            .put("tier", hold.tier.name)
            .put("createdAtUtc", hold.createdAtUtc.toString())
            .put("updatedAtUtc", hold.updatedAtUtc.toString())
            .put("monotonicSeq", hold.monotonicSeq)
            .put("clearanceProvenance", hold.clearanceProvenance.name)
            .put("clearedAtUtc", hold.clearedAtUtc?.toString() ?: JSONObject.NULL)
            .put("clearedReason", hold.clearedReason ?: JSONObject.NULL)
            .put("minHrQuarantineUntilUtc", hold.minHrQuarantineUntilUtc?.toString() ?: JSONObject.NULL)
        when (val s = hold.scope) {
            is HealthRestrictionScope.Systemic -> obj.put("scopeKind", "SYSTEMIC")
            is HealthRestrictionScope.Localized -> {
                obj.put("scopeKind", "LOCALIZED")
                obj.put("regions", JSONArray(s.regions.sorted()))
                obj.put("disciplines", JSONArray(s.disciplines.map { it.code }.sorted()))
            }
        }
        return obj
    }

    private fun decodeHold(raw: String, expectedOwner: String): HealthHoldRecord? = runCatching {
        val obj = JSONObject(raw)
        val owner = obj.getString("accountId")
        if (owner != expectedOwner) return null
        val scope = if (obj.optString("scopeKind", "SYSTEMIC") == "LOCALIZED") {
            val regArr = obj.optJSONArray("regions") ?: JSONArray()
            val regions = (0 until regArr.length()).mapTo(LinkedHashSet()) { regArr.getString(it) }
            val discArr = obj.optJSONArray("disciplines") ?: JSONArray()
            val discs = (0 until discArr.length()).mapTo(LinkedHashSet()) { AthleteDiscipline.fromCode(discArr.getString(it)) }
            HealthRestrictionScope.Localized(regions, discs)
        } else {
            HealthRestrictionScope.Systemic
        }
        HealthHoldRecord(
            restrictionId = obj.getString("restrictionId"),
            accountId = owner,
            trigger = obj.getString("trigger"),
            scope = scope,
            tier = HealthEscalationTier.valueOf(obj.getString("tier")),
            createdAtUtc = Instant.parse(obj.getString("createdAtUtc")),
            updatedAtUtc = Instant.parse(obj.getString("updatedAtUtc")),
            monotonicSeq = obj.optLong("monotonicSeq", 1L),
            clearanceProvenance = HealthClearanceProvenance.valueOf(
                obj.optString("clearanceProvenance", HealthClearanceProvenance.NONE.name)
            ),
            clearedAtUtc = if (obj.isNull("clearedAtUtc")) null else Instant.parse(obj.getString("clearedAtUtc")),
            clearedReason = if (obj.isNull("clearedReason")) null else obj.getString("clearedReason"),
            minHrQuarantineUntilUtc = if (obj.isNull("minHrQuarantineUntilUtc")) null else Instant.parse(obj.getString("minHrQuarantineUntilUtc"))
        )
    }.getOrNull()

    private fun encodeState(state: DecisionStateSnapshot): JSONObject {
        val countersObj = JSONObject()
        for ((k, entries) in state.confirmationCounters) {
            val inner = JSONObject()
            for ((actId, ts) in entries) inner.put(actId, ts.toString())
            countersObj.put(k, inner)
        }
        val cooldownsObj = JSONObject()
        for ((cls, until) in state.decisionCooldowns) {
            cooldownsObj.put(cls.name, until.toString())
        }
        val historyArr = JSONArray()
        for (h in state.approvalAndRejectionHistory) {
            historyArr.put(
                JSONObject()
                    .put("candidateId", h.candidateId)
                    .put("decisionClass", h.decisionClass.name)
                    .put("outcome", h.outcome)
                    .put("recordedAtUtc", h.recordedAtUtc.toString())
            )
        }
        return JSONObject()
            .put("acceptedPlanVersion", state.acceptedPlanVersion)
            .put("policyVersion", state.policyVersion)
            .put("evaluationTimestamp", state.evaluationTimestamp.toString())
            .put("primaryDisciplinePriority", state.primaryDisciplinePriority)
            .put("optionalNotificationsSentThisWeek", state.optionalNotificationsSentThisWeek)
            .put("observedExecutionIds", JSONArray(state.observedExecutionIds.sorted()))
            .put("confirmationCounters", countersObj)
            .put("decisionCooldowns", cooldownsObj)
            .put("approvalAndRejectionHistory", historyArr)
    }

    private fun decodeState(raw: String): DecisionStateSnapshot? = runCatching {
        val obj = JSONObject(raw)
        val obsArr = obj.optJSONArray("observedExecutionIds") ?: JSONArray()
        val obs = (0 until obsArr.length()).mapTo(LinkedHashSet()) { obsArr.getString(it) }
        val countersObj = obj.optJSONObject("confirmationCounters") ?: JSONObject()
        val counters = LinkedHashMap<String, Map<String, Instant>>()
        for (targetKey in countersObj.keys()) {
            val innerObj = countersObj.optJSONObject(targetKey) ?: continue
            val innerMap = LinkedHashMap<String, Instant>()
            for (actId in innerObj.keys()) {
                innerMap[actId] = Instant.parse(innerObj.getString(actId))
            }
            counters[targetKey] = innerMap
        }
        val cdObj = obj.optJSONObject("decisionCooldowns") ?: JSONObject()
        val cooldowns = LinkedHashMap<CoachingDecisionClass, Instant>()
        for (clsName in cdObj.keys()) {
            cooldowns[CoachingDecisionClass.valueOf(clsName)] = Instant.parse(cdObj.getString(clsName))
        }
        val histArr = obj.optJSONArray("approvalAndRejectionHistory") ?: JSONArray()
        val history = (0 until histArr.length()).map { idx ->
            val h = histArr.getJSONObject(idx)
            ApprovalRejectionRecord(
                candidateId = h.getString("candidateId"),
                decisionClass = CoachingDecisionClass.valueOf(h.getString("decisionClass")),
                outcome = h.getString("outcome"),
                recordedAtUtc = Instant.parse(h.getString("recordedAtUtc"))
            )
        }
        DecisionStateSnapshot(
            observedExecutionIds = obs,
            acceptedPlanVersion = obj.optInt("acceptedPlanVersion", 1),
            policyVersion = obj.optString("policyVersion", Engine19CoachingPolicyRegistry.POLICY_VERSION),
            evaluationTimestamp = Instant.parse(obj.getString("evaluationTimestamp")),
            confirmationCounters = counters,
            decisionCooldowns = cooldowns,
            approvalAndRejectionHistory = history,
            primaryDisciplinePriority = obj.optString("primaryDisciplinePriority", "run"),
            optionalNotificationsSentThisWeek = obj.optInt("optionalNotificationsSentThisWeek", 0)
        )
    }.getOrNull()

    companion object {
        const val HOLD_PREFIX = "m1-coach-hold-"
        const val STATE_PREFIX = "m1-coach-state-"

        internal fun holdRecordKey(restrictionId: String): String {
            RecordPolicy.identifier(restrictionId)
            return HOLD_PREFIX + sha256(restrictionId)
        }

        internal fun stateRecordKey(owner: String): String {
            RecordPolicy.identifier(owner)
            return STATE_PREFIX + sha256(owner)
        }

        private fun sha256(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

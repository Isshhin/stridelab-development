package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal const val ENGINE15_ADAPTATION_SCHEMA = 1

internal enum class AdaptationType {
    PACE_RECALIBRATION_UP,
    PACE_RECALIBRATION_DOWN,
    MISSED_WEEK_RECOVERY,
    PROACTIVE_DELOAD_SWAP,
    EASY_PACING_LOCK,
    RUNNER_SUPPORT_DOSE,
    STRENGTH_PROGRESSION
}

internal enum class AdaptationProposalStatus {
    PENDING,
    APPLIED,
    DISMISSED,
    EXPIRED,
    REVERTED
}

internal enum class DisruptionTier {
    NONE,
    SHORT_DISRUPTION_3_TO_5D,
    FULL_MISSED_WEEK_6_TO_10D,
    EXTENDED_GAP_11D_PLUS
}

internal data class AdaptationWeekDiff(
    val weekIndex: Int,
    val phaseBefore: String,
    val phaseAfter: String,
    val plannedKmBefore: Double,
    val plannedKmAfter: Double,
    val qualityCountBefore: Int,
    val qualityCountAfter: Int,
    val summaryNote: String
) {
    init {
        require(weekIndex >= 0)
        require(phaseBefore.isNotBlank() && phaseAfter.isNotBlank())
        require(plannedKmBefore.isFinite() && plannedKmBefore >= 0.0)
        require(plannedKmAfter.isFinite() && plannedKmAfter >= 0.0)
        require(qualityCountBefore >= 0 && qualityCountAfter >= 0)
        require(summaryNote.isNotBlank())
    }

    fun encode(): JSONObject = JSONObject()
        .put("weekIndex", weekIndex)
        .put("phaseBefore", phaseBefore)
        .put("phaseAfter", phaseAfter)
        .put("plannedKmBefore", plannedKmBefore)
        .put("plannedKmAfter", plannedKmAfter)
        .put("qualityCountBefore", qualityCountBefore)
        .put("qualityCountAfter", qualityCountAfter)
        .put("summaryNote", summaryNote)

    companion object {
        fun decode(j: JSONObject): AdaptationWeekDiff = AdaptationWeekDiff(
            weekIndex = j.getInt("weekIndex"),
            phaseBefore = j.getString("phaseBefore"),
            phaseAfter = j.getString("phaseAfter"),
            plannedKmBefore = j.getDouble("plannedKmBefore"),
            plannedKmAfter = j.getDouble("plannedKmAfter"),
            qualityCountBefore = j.getInt("qualityCountBefore"),
            qualityCountAfter = j.getInt("qualityCountAfter"),
            summaryNote = j.getString("summaryNote")
        )
    }
}

internal data class AdaptationPaceDiff(
    val intent: RunningStepIntent,
    val oldMinSecPerKm: Int,
    val oldMaxSecPerKm: Int,
    val newMinSecPerKm: Int,
    val newMaxSecPerKm: Int
) {
    init {
        require(oldMinSecPerKm in 120..1200 && oldMaxSecPerKm in oldMinSecPerKm..1200)
        require(newMinSecPerKm in 120..1200 && newMaxSecPerKm in newMinSecPerKm..1200)
    }

    fun encode(): JSONObject = JSONObject()
        .put("intent", intent.name)
        .put("oldMinSecPerKm", oldMinSecPerKm)
        .put("oldMaxSecPerKm", oldMaxSecPerKm)
        .put("newMinSecPerKm", newMinSecPerKm)
        .put("newMaxSecPerKm", newMaxSecPerKm)

    companion object {
        fun decode(j: JSONObject): AdaptationPaceDiff = AdaptationPaceDiff(
            intent = RunningStepIntent.valueOf(j.getString("intent")),
            oldMinSecPerKm = j.getInt("oldMinSecPerKm"),
            oldMaxSecPerKm = j.getInt("oldMaxSecPerKm"),
            newMinSecPerKm = j.getInt("newMinSecPerKm"),
            newMaxSecPerKm = j.getInt("newMaxSecPerKm")
        )
    }
}

internal data class PlanAdaptationDiff(
    val effectiveFromDate: LocalDate,
    val affectedSessionCount: Int,
    val totalRemainingKmBefore: Double,
    val totalRemainingKmAfter: Double,
    val weekDiffs: List<AdaptationWeekDiff>,
    val paceDiffs: List<AdaptationPaceDiff>,
    val safetyChecksPassed: Boolean,
    val invariantWarnings: List<String>
) {
    init {
        require(affectedSessionCount >= 0)
        require(totalRemainingKmBefore.isFinite() && totalRemainingKmBefore >= 0.0)
        require(totalRemainingKmAfter.isFinite() && totalRemainingKmAfter >= 0.0)
    }

    fun encode(): JSONObject = JSONObject()
        .put("effectiveFromDate", effectiveFromDate.toString())
        .put("affectedSessionCount", affectedSessionCount)
        .put("totalRemainingKmBefore", totalRemainingKmBefore)
        .put("totalRemainingKmAfter", totalRemainingKmAfter)
        .put("weekDiffs", JSONArray(weekDiffs.map { it.encode() }))
        .put("paceDiffs", JSONArray(paceDiffs.map { it.encode() }))
        .put("safetyChecksPassed", safetyChecksPassed)
        .put("invariantWarnings", JSONArray(invariantWarnings))

    companion object {
        fun decode(j: JSONObject): PlanAdaptationDiff = PlanAdaptationDiff(
            effectiveFromDate = LocalDate.parse(j.getString("effectiveFromDate")),
            affectedSessionCount = j.getInt("affectedSessionCount"),
            totalRemainingKmBefore = j.getDouble("totalRemainingKmBefore"),
            totalRemainingKmAfter = j.getDouble("totalRemainingKmAfter"),
            weekDiffs = j.optJSONArray("weekDiffs")?.objects()?.map(AdaptationWeekDiff::decode)
                ?: emptyList(),
            paceDiffs = j.optJSONArray("paceDiffs")?.objects()?.map(AdaptationPaceDiff::decode)
                ?: emptyList(),
            safetyChecksPassed = j.getBoolean("safetyChecksPassed"),
            invariantWarnings = j.optJSONArray("invariantWarnings")?.strings() ?: emptyList()
        )
    }
}

internal data class PlanAdaptationProposal(
    val schemaVersion: Int = ENGINE15_ADAPTATION_SCHEMA,
    val proposalId: String,
    val planId: String,
    val type: AdaptationType,
    val status: AdaptationProposalStatus,
    val createdAtDate: LocalDate,
    val effectiveFromDate: LocalDate,
    val expiresAtDate: LocalDate,
    val disruptionTier: DisruptionTier,
    val disruptionFingerprint: String?,
    val sourceDiagnosticDate: LocalDate,
    val sourceConfidence: DiagnosticConfidence,
    val title: String,
    val coachingRationale: String,
    val diff: PlanAdaptationDiff,
    val candidatePlanJson: String?,
    val reasonCodes: List<String>
) {
    init {
        require(schemaVersion == ENGINE15_ADAPTATION_SCHEMA)
        require(proposalId.isNotBlank() && planId.isNotBlank())
        require(!expiresAtDate.isBefore(createdAtDate))
        require(title.isNotBlank() && coachingRationale.isNotBlank())
        if (type == AdaptationType.EASY_PACING_LOCK) {
            require(candidatePlanJson == null) {
                "EASY_PACING_LOCK proposals are read-only advisories and must have null candidatePlanJson"
            }
        }
    }

    fun encode(owner: String? = null): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("proposalId", proposalId)
        .put("planId", planId)
        .put("type", type.name)
        .put("status", status.name)
        .put("createdAtDate", createdAtDate.toString())
        .put("effectiveFromDate", effectiveFromDate.toString())
        .put("expiresAtDate", expiresAtDate.toString())
        .put("disruptionTier", disruptionTier.name)
        .put("disruptionFingerprint", disruptionFingerprint ?: JSONObject.NULL)
        .put("sourceDiagnosticDate", sourceDiagnosticDate.toString())
        .put("sourceConfidence", sourceConfidence.name)
        .put("title", title)
        .put("coachingRationale", coachingRationale)
        .put("diff", diff.encode())
        .put("candidatePlanJson", candidatePlanJson ?: JSONObject.NULL)
        .put("reasonCodes", JSONArray(reasonCodes))
        .apply { if (owner != null) put("user_id", owner) }

    companion object {
        fun decode(j: JSONObject, expectedOwner: String? = null): PlanAdaptationProposal {
            require(j.getInt("schemaVersion") == ENGINE15_ADAPTATION_SCHEMA) {
                "Unsupported adaptation proposal schema"
            }
            if (expectedOwner != null) {
                require(j.optString("user_id") == expectedOwner) {
                    "Adaptation proposal owner does not match"
                }
            }
            return PlanAdaptationProposal(
                schemaVersion = j.getInt("schemaVersion"),
                proposalId = j.getString("proposalId"),
                planId = j.getString("planId"),
                type = AdaptationType.valueOf(j.getString("type")),
                status = AdaptationProposalStatus.valueOf(j.getString("status")),
                createdAtDate = LocalDate.parse(j.getString("createdAtDate")),
                effectiveFromDate = LocalDate.parse(j.getString("effectiveFromDate")),
                expiresAtDate = LocalDate.parse(j.getString("expiresAtDate")),
                disruptionTier = DisruptionTier.valueOf(j.getString("disruptionTier")),
                disruptionFingerprint = j.nullString("disruptionFingerprint"),
                sourceDiagnosticDate = LocalDate.parse(j.getString("sourceDiagnosticDate")),
                sourceConfidence = DiagnosticConfidence.valueOf(j.getString("sourceConfidence")),
                title = j.getString("title"),
                coachingRationale = j.getString("coachingRationale"),
                diff = PlanAdaptationDiff.decode(j.getJSONObject("diff")),
                candidatePlanJson = j.nullString("candidatePlanJson"),
                reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList()
            )
        }
    }
}

internal data class PlanAdaptationRevision(
    val schemaVersion: Int = ENGINE15_ADAPTATION_SCHEMA,
    val revisionId: String,
    val planId: String,
    val proposalId: String,
    val appliedAtDate: LocalDate,
    val adaptationType: AdaptationType,
    val previousPlanJson: String,
    val appliedPlanJson: String,
    val revertedAtDate: String? = null
) {
    init {
        require(schemaVersion == ENGINE15_ADAPTATION_SCHEMA)
        require(revisionId.isNotBlank() && planId.isNotBlank() && proposalId.isNotBlank())
        require(previousPlanJson.isNotBlank() && appliedPlanJson.isNotBlank())
        if (revertedAtDate != null) {
            LocalDate.parse(revertedAtDate)
        }
    }

    fun encode(owner: String? = null): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("revisionId", revisionId)
        .put("planId", planId)
        .put("proposalId", proposalId)
        .put("appliedAtDate", appliedAtDate.toString())
        .put("adaptationType", adaptationType.name)
        .put("previousPlanJson", previousPlanJson)
        .put("appliedPlanJson", appliedPlanJson)
        .put("revertedAtDate", revertedAtDate ?: JSONObject.NULL)
        .apply { if (owner != null) put("user_id", owner) }

    companion object {
        fun decode(j: JSONObject, expectedOwner: String? = null): PlanAdaptationRevision {
            require(j.getInt("schemaVersion") == ENGINE15_ADAPTATION_SCHEMA) {
                "Unsupported adaptation revision schema"
            }
            if (expectedOwner != null) {
                require(j.optString("user_id") == expectedOwner) {
                    "Adaptation revision owner does not match"
                }
            }
            return PlanAdaptationRevision(
                schemaVersion = j.getInt("schemaVersion"),
                revisionId = j.getString("revisionId"),
                planId = j.getString("planId"),
                proposalId = j.getString("proposalId"),
                appliedAtDate = LocalDate.parse(j.getString("appliedAtDate")),
                adaptationType = AdaptationType.valueOf(j.getString("adaptationType")),
                previousPlanJson = j.getString("previousPlanJson"),
                appliedPlanJson = j.getString("appliedPlanJson"),
                revertedAtDate = j.nullString("revertedAtDate")
            )
        }
    }
}

internal data class AdaptationCooldownRecord(
    val planId: String,
    val adaptationType: AdaptationType,
    val lastActionStatus: AdaptationProposalStatus,
    val actionDate: LocalDate,
    val cooldownUntilDate: LocalDate,
    val disruptionFingerprint: String? = null
) {
    init {
        require(planId.isNotBlank())
        require(lastActionStatus in setOf(AdaptationProposalStatus.APPLIED, AdaptationProposalStatus.DISMISSED))
        require(!cooldownUntilDate.isBefore(actionDate))
    }

    fun encode(owner: String? = null): JSONObject = JSONObject()
        .put("schemaVersion", ENGINE15_ADAPTATION_SCHEMA)
        .put("planId", planId)
        .put("adaptationType", adaptationType.name)
        .put("lastActionStatus", lastActionStatus.name)
        .put("actionDate", actionDate.toString())
        .put("cooldownUntilDate", cooldownUntilDate.toString())
        .put("disruptionFingerprint", disruptionFingerprint ?: JSONObject.NULL)
        .apply { if (owner != null) put("user_id", owner) }

    companion object {
        fun decode(j: JSONObject, expectedOwner: String? = null): AdaptationCooldownRecord {
            require(j.getInt("schemaVersion") == ENGINE15_ADAPTATION_SCHEMA) {
                "Unsupported adaptation cooldown schema"
            }
            if (expectedOwner != null) {
                require(j.optString("user_id") == expectedOwner) {
                    "Adaptation cooldown owner does not match"
                }
            }
            return AdaptationCooldownRecord(
                planId = j.getString("planId"),
                adaptationType = AdaptationType.valueOf(j.getString("adaptationType")),
                lastActionStatus = AdaptationProposalStatus.valueOf(j.getString("lastActionStatus")),
                actionDate = LocalDate.parse(j.getString("actionDate")),
                cooldownUntilDate = LocalDate.parse(j.getString("cooldownUntilDate")),
                disruptionFingerprint = j.nullString("disruptionFingerprint")
            )
        }
    }
}

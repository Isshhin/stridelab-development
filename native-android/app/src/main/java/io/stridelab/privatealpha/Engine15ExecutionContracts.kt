package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject

internal const val ENGINE15_EXECUTION_SCHEMA = 1

internal enum class ActivityMatchStatus {
    AUTO_LINKED,
    NEEDS_CONFIRMATION,
    UNMATCHED,
    MANUALLY_LINKED,
    MANUALLY_REJECTED
}

internal data class ActivityMatchCandidate(
    val plannedSessionId: String,
    val confidenceScore: Double,
    val dateOffsetDays: Int,
    val reasonCodes: List<String>
) {
    init {
        require(plannedSessionId.isNotBlank())
        require(confidenceScore.isFinite() && confidenceScore in 0.0..1.0)
        require(dateOffsetDays in -1..1)
    }

    fun encode(): JSONObject = JSONObject()
        .put("plannedSessionId", plannedSessionId)
        .put("confidenceScore", confidenceScore)
        .put("dateOffsetDays", dateOffsetDays)
        .put("reasonCodes", JSONArray(reasonCodes))

    companion object {
        fun decode(j: JSONObject): ActivityMatchCandidate = ActivityMatchCandidate(
            plannedSessionId = j.getString("plannedSessionId"),
            confidenceScore = j.getDouble("confidenceScore"),
            dateOffsetDays = j.getInt("dateOffsetDays"),
            reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList()
        )
    }
}

internal data class ActivityMatchRecord(
    val activityId: String,
    val plannedSessionId: String?,
    val matchStatus: ActivityMatchStatus,
    val confidenceScore: Double,
    val dateOffsetDays: Int?,
    val reasonCodes: List<String>,
    val isManualOverride: Boolean = false,
    val candidates: List<ActivityMatchCandidate> = emptyList(),
    val evaluatedAtEpochMs: Long = 0L
) {
    init {
        require(activityId.isNotBlank())
        require(confidenceScore.isFinite() && confidenceScore in 0.0..1.0)
        require(dateOffsetDays == null || dateOffsetDays in -1..1)
        when (matchStatus) {
            ActivityMatchStatus.AUTO_LINKED,
            ActivityMatchStatus.MANUALLY_LINKED -> require(!plannedSessionId.isNullOrBlank())
            ActivityMatchStatus.UNMATCHED -> require(plannedSessionId == null)
            ActivityMatchStatus.NEEDS_CONFIRMATION,
            ActivityMatchStatus.MANUALLY_REJECTED -> Unit
        }
        require(evaluatedAtEpochMs >= 0L)
    }

    fun encode(owner: String? = null): JSONObject = JSONObject()
        .put("schemaVersion", ENGINE15_EXECUTION_SCHEMA)
        .put("activityId", activityId)
        .put("plannedSessionId", plannedSessionId ?: JSONObject.NULL)
        .put("matchStatus", matchStatus.name)
        .put("confidenceScore", confidenceScore)
        .put("dateOffsetDays", dateOffsetDays ?: JSONObject.NULL)
        .put("reasonCodes", JSONArray(reasonCodes))
        .put("isManualOverride", isManualOverride)
        .put("candidates", JSONArray(candidates.map { it.encode() }))
        .put("evaluatedAtEpochMs", evaluatedAtEpochMs)
        .apply { if (owner != null) put("user_id", owner) }

    companion object {
        fun decode(j: JSONObject, expectedOwner: String? = null): ActivityMatchRecord {
            require(j.getInt("schemaVersion") == ENGINE15_EXECUTION_SCHEMA) {
                "Unsupported activity match schema"
            }
            if (expectedOwner != null) {
                require(j.optString("user_id") == expectedOwner) {
                    "Activity match owner does not match"
                }
            }
            return ActivityMatchRecord(
                activityId = j.getString("activityId"),
                plannedSessionId = j.nullString("plannedSessionId"),
                matchStatus = ActivityMatchStatus.valueOf(j.getString("matchStatus")),
                confidenceScore = j.getDouble("confidenceScore"),
                dateOffsetDays = j.nullInt("dateOffsetDays"),
                reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList(),
                isManualOverride = j.optBoolean("isManualOverride", false),
                candidates = j.optJSONArray("candidates")?.objects()?.map(ActivityMatchCandidate::decode)
                    ?: emptyList(),
                evaluatedAtEpochMs = j.optLong("evaluatedAtEpochMs", 0L)
            )
        }
    }
}

internal enum class ExecutionEvidenceTier {
    LAP_AND_RECORD,
    RECORD_WINDOW,
    SUMMARY_ONLY
}

internal enum class ExecutionVerdict {
    ON_TARGET,
    PARTIALLY_EXECUTED,
    INTENSITY_MISMATCH_TOO_FAST,
    INTENSITY_MISMATCH_TOO_SLOW,
    VOLUME_ONLY_MATCH,
    INSUFFICIENT_TELEMETRY
}

internal enum class ExecutionPaceBias {
    TOO_FAST,
    ON_TARGET,
    TOO_SLOW,
    MIXED,
    UNKNOWN
}

internal data class ExecutionStepBreakdown(
    val stepIndex: Int,
    val purpose: String,
    val stepIntent: RunningStepIntent?,
    val plannedDistanceKm: Double?,
    val actualDistanceKm: Double?,
    val plannedSeconds: Int?,
    val actualSeconds: Int?,
    val targetPaceMinSecPerKm: Int?,
    val targetPaceMaxSecPerKm: Int?,
    val actualPaceSecPerKm: Double?,
    val paceAdherencePct: Double?,
    val paceBias: ExecutionPaceBias,
    val averageHrBpm: Double? = null,
    val elevationGainM: Double? = null,
    val source: String = "SUMMARY"
) {
    init {
        require(stepIndex >= 0)
        require(purpose.isNotBlank())
        require(plannedDistanceKm == null || (plannedDistanceKm.isFinite() && plannedDistanceKm >= 0.0))
        require(actualDistanceKm == null || (actualDistanceKm.isFinite() && actualDistanceKm >= 0.0))
        require(plannedSeconds == null || plannedSeconds >= 0)
        require(actualSeconds == null || actualSeconds >= 0)
        require(actualPaceSecPerKm == null || (actualPaceSecPerKm.isFinite() && actualPaceSecPerKm > 0.0))
        require(paceAdherencePct == null || (paceAdherencePct.isFinite() && paceAdherencePct in 0.0..100.0))
        require(averageHrBpm == null || (averageHrBpm.isFinite() && averageHrBpm > 0.0))
        require(elevationGainM == null || (elevationGainM.isFinite() && elevationGainM >= 0.0))
    }

    fun encode(): JSONObject = JSONObject()
        .put("stepIndex", stepIndex)
        .put("purpose", purpose)
        .put("stepIntent", stepIntent?.name ?: JSONObject.NULL)
        .put("plannedDistanceKm", plannedDistanceKm ?: JSONObject.NULL)
        .put("actualDistanceKm", actualDistanceKm ?: JSONObject.NULL)
        .put("plannedSeconds", plannedSeconds ?: JSONObject.NULL)
        .put("actualSeconds", actualSeconds ?: JSONObject.NULL)
        .put("targetPaceMinSecPerKm", targetPaceMinSecPerKm ?: JSONObject.NULL)
        .put("targetPaceMaxSecPerKm", targetPaceMaxSecPerKm ?: JSONObject.NULL)
        .put("actualPaceSecPerKm", actualPaceSecPerKm ?: JSONObject.NULL)
        .put("paceAdherencePct", paceAdherencePct ?: JSONObject.NULL)
        .put("paceBias", paceBias.name)
        .put("averageHrBpm", averageHrBpm ?: JSONObject.NULL)
        .put("elevationGainM", elevationGainM ?: JSONObject.NULL)
        .put("source", source)

    companion object {
        fun decode(j: JSONObject): ExecutionStepBreakdown = ExecutionStepBreakdown(
            stepIndex = j.getInt("stepIndex"),
            purpose = j.getString("purpose"),
            stepIntent = j.nullString("stepIntent")?.let(RunningStepIntent::valueOf),
            plannedDistanceKm = j.nullDouble("plannedDistanceKm"),
            actualDistanceKm = j.nullDouble("actualDistanceKm"),
            plannedSeconds = j.nullInt("plannedSeconds"),
            actualSeconds = j.nullInt("actualSeconds"),
            targetPaceMinSecPerKm = j.nullInt("targetPaceMinSecPerKm"),
            targetPaceMaxSecPerKm = j.nullInt("targetPaceMaxSecPerKm"),
            actualPaceSecPerKm = j.nullDouble("actualPaceSecPerKm"),
            paceAdherencePct = j.nullDouble("paceAdherencePct"),
            paceBias = ExecutionPaceBias.valueOf(j.getString("paceBias")),
            averageHrBpm = j.nullDouble("averageHrBpm"),
            elevationGainM = j.nullDouble("elevationGainM"),
            source = j.optString("source", "SUMMARY").ifBlank { "SUMMARY" }
        )
    }
}

internal data class WorkoutExecutionAnalysis(
    val plannedSessionId: String,
    val activityId: String,
    val evidenceTier: ExecutionEvidenceTier,
    val verdict: ExecutionVerdict,
    val volumeRatio: Double,
    val plannedKm: Double?,
    val actualKm: Double?,
    val plannedMinutes: Int?,
    val actualMinutes: Double?,
    val workStepPaceAdherencePct: Double?,
    val qualityStimulusVerified: Boolean?,
    val paceBias: ExecutionPaceBias,
    val hrZoneCompliancePct: Double?,
    val aerobicDecouplingPct: Double?,
    val stepBreakdowns: List<ExecutionStepBreakdown>,
    val reasonCodes: List<String>,
    val coachingSummary: String,
    val analyzedAtEpochMs: Long = 0L
) {
    init {
        require(plannedSessionId.isNotBlank())
        require(activityId.isNotBlank())
        require(volumeRatio.isFinite() && volumeRatio >= 0.0)
        require(plannedKm == null || (plannedKm.isFinite() && plannedKm >= 0.0))
        require(actualKm == null || (actualKm.isFinite() && actualKm >= 0.0))
        require(plannedMinutes == null || plannedMinutes >= 0)
        require(actualMinutes == null || (actualMinutes.isFinite() && actualMinutes >= 0.0))
        require(workStepPaceAdherencePct == null ||
            (workStepPaceAdherencePct.isFinite() && workStepPaceAdherencePct in 0.0..100.0))
        require(hrZoneCompliancePct == null ||
            (hrZoneCompliancePct.isFinite() && hrZoneCompliancePct in 0.0..100.0))
        require(aerobicDecouplingPct == null || aerobicDecouplingPct.isFinite())
        require(coachingSummary.isNotBlank())
        require(analyzedAtEpochMs >= 0L)
    }

    fun encode(owner: String? = null): JSONObject = JSONObject()
        .put("schemaVersion", ENGINE15_EXECUTION_SCHEMA)
        .put("plannedSessionId", plannedSessionId)
        .put("activityId", activityId)
        .put("evidenceTier", evidenceTier.name)
        .put("verdict", verdict.name)
        .put("volumeRatio", volumeRatio)
        .put("plannedKm", plannedKm ?: JSONObject.NULL)
        .put("actualKm", actualKm ?: JSONObject.NULL)
        .put("plannedMinutes", plannedMinutes ?: JSONObject.NULL)
        .put("actualMinutes", actualMinutes ?: JSONObject.NULL)
        .put("workStepPaceAdherencePct", workStepPaceAdherencePct ?: JSONObject.NULL)
        .put("qualityStimulusVerified", qualityStimulusVerified ?: JSONObject.NULL)
        .put("paceBias", paceBias.name)
        .put("hrZoneCompliancePct", hrZoneCompliancePct ?: JSONObject.NULL)
        .put("aerobicDecouplingPct", aerobicDecouplingPct ?: JSONObject.NULL)
        .put("stepBreakdowns", JSONArray(stepBreakdowns.map { it.encode() }))
        .put("reasonCodes", JSONArray(reasonCodes))
        .put("coachingSummary", coachingSummary)
        .put("analyzedAtEpochMs", analyzedAtEpochMs)
        .apply { if (owner != null) put("user_id", owner) }

    companion object {
        fun decode(j: JSONObject, expectedOwner: String? = null): WorkoutExecutionAnalysis {
            require(j.getInt("schemaVersion") == ENGINE15_EXECUTION_SCHEMA) {
                "Unsupported execution analysis schema"
            }
            if (expectedOwner != null) {
                require(j.optString("user_id") == expectedOwner) {
                    "Execution analysis owner does not match"
                }
            }
            val qualityVerified = if (j.isNull("qualityStimulusVerified")) null
            else j.getBoolean("qualityStimulusVerified")
            return WorkoutExecutionAnalysis(
                plannedSessionId = j.getString("plannedSessionId"),
                activityId = j.getString("activityId"),
                evidenceTier = ExecutionEvidenceTier.valueOf(j.getString("evidenceTier")),
                verdict = ExecutionVerdict.valueOf(j.getString("verdict")),
                volumeRatio = j.getDouble("volumeRatio"),
                plannedKm = j.nullDouble("plannedKm"),
                actualKm = j.nullDouble("actualKm"),
                plannedMinutes = j.nullInt("plannedMinutes"),
                actualMinutes = j.nullDouble("actualMinutes"),
                workStepPaceAdherencePct = j.nullDouble("workStepPaceAdherencePct"),
                qualityStimulusVerified = qualityVerified,
                paceBias = ExecutionPaceBias.valueOf(j.getString("paceBias")),
                hrZoneCompliancePct = j.nullDouble("hrZoneCompliancePct"),
                aerobicDecouplingPct = j.nullDouble("aerobicDecouplingPct"),
                stepBreakdowns = j.optJSONArray("stepBreakdowns")?.objects()?.map(ExecutionStepBreakdown::decode)
                    ?: emptyList(),
                reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList(),
                coachingSummary = j.getString("coachingSummary"),
                analyzedAtEpochMs = j.optLong("analyzedAtEpochMs", 0L)
            )
        }
    }
}

/**
 * Athlete HR zone floors anchored strictly to verified Garmin or FIT zone settings.
 * Never synthesizes 220-age zones when source HR settings are absent.
 */
internal data class ExecutionHrZoneProfile(
    val z1FloorBpm: Int,
    val z2FloorBpm: Int,
    val z3FloorBpm: Int,
    val z4FloorBpm: Int,
    val z5FloorBpm: Int,
    val maxHrBpm: Int? = null
) {
    init {
        val floors = listOf(z1FloorBpm, z2FloorBpm, z3FloorBpm, z4FloorBpm, z5FloorBpm)
        require(floors.all { it in 45..240 })
        require(floors.zipWithNext().all { (a, b) -> b > a })
        require(maxHrBpm == null || (maxHrBpm in z5FloorBpm..255))
    }

    fun zoneFor(hrBpm: Double): Int = when {
        hrBpm >= z5FloorBpm -> 5
        hrBpm >= z4FloorBpm -> 4
        hrBpm >= z3FloorBpm -> 3
        hrBpm >= z2FloorBpm -> 2
        else -> 1
    }

    fun boundsForZone(zone: Int): IntRange? = when (zone) {
        1 -> z1FloorBpm until z2FloorBpm
        2 -> z2FloorBpm until z3FloorBpm
        3 -> z3FloorBpm until z4FloorBpm
        4 -> z4FloorBpm until z5FloorBpm
        5 -> z5FloorBpm..(maxHrBpm ?: 240)
        else -> null
    }

    companion object {
        fun fromGarmin(profile: GarminHeartRateZoneProfile?): ExecutionHrZoneProfile? {
            if (profile == null || profile.floorsBpm.size != 5) return null
            return runCatching {
                ExecutionHrZoneProfile(
                    z1FloorBpm = profile.floorsBpm[0],
                    z2FloorBpm = profile.floorsBpm[1],
                    z3FloorBpm = profile.floorsBpm[2],
                    z4FloorBpm = profile.floorsBpm[3],
                    z5FloorBpm = profile.floorsBpm[4],
                    maxHrBpm = profile.maxHeartRateUsed
                )
            }.getOrNull()
        }

        fun fromActivitySourceZones(zones: ActivitySourceHeartRateZones?): ExecutionHrZoneProfile? {
            if (zones == null || zones.highBpms.size != 5) return null
            val highs = zones.highBpms
            if (highs.zipWithNext().any { (a, b) -> b <= a }) return null
            val z2 = highs[0] + 1
            val z3 = highs[1] + 1
            val z4 = highs[2] + 1
            val z5 = highs[3] + 1
            val z1 = (highs[0] - (highs[1] - highs[0])).coerceIn(45, z2 - 1)
            val maxHr = zones.maximumHr ?: highs[4]
            return runCatching {
                ExecutionHrZoneProfile(
                    z1FloorBpm = z1,
                    z2FloorBpm = z2,
                    z3FloorBpm = z3,
                    z4FloorBpm = z4,
                    z5FloorBpm = z5,
                    maxHrBpm = maxHr
                )
            }.getOrNull()
        }
    }
}

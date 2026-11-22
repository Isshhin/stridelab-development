package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal const val ENGINE15_DIAGNOSTIC_SCHEMA = 1

internal enum class DiagnosticConfidence {
    INSUFFICIENT,
    LOW,
    MODERATE,
    HIGH
}

internal enum class ExposureStatus {
    SAFE,
    STRETCHED,
    OVERREACH_RISK,
    INSUFFICIENT_DATA
}

internal enum class LongitudinalPaceTrend {
    FASTER_THAN_TARGET,
    ON_TARGET,
    SLOWER_THAN_TARGET,
    MIXED,
    INSUFFICIENT_DATA
}

internal enum class AerobicEfficiencyTrend {
    IMPROVING,
    STABLE,
    FATIGUING,
    UNAVAILABLE
}

internal enum class RecalibrationAdvisory {
    HOLD,
    SUGGEST_FASTER_CHECK,
    SUGGEST_SLOWER_CHECK,
    ADDRESS_EASY_PACING_FIRST,
    VOLUME_RECOVERY_NEEDED,
    INSUFFICIENT_DATA
}

internal enum class DiagnosticInsightSeverity {
    INFO,
    CAUTION,
    ACTIONABLE
}

internal data class VolumeToleranceDiagnostic(
    val windowDays: Int,
    val plannedKm: Double,
    val completedKm: Double,
    val volumeCompletionRatio: Double,
    val plannedSessions: Int,
    val completedSessions: Int,
    val consecutiveMissedSessions: Int,
    val rolling28dWeeklyAvgKm: Double,
    val last7dKm: Double,
    val acuteToBaselineRatio: Double?,
    val longest30dKm: Double,
    val exposureStatus: ExposureStatus,
    val confidence: DiagnosticConfidence,
    val reasonCodes: List<String>
) {
    init {
        require(windowDays > 0)
        require(plannedKm.isFinite() && plannedKm >= 0.0)
        require(completedKm.isFinite() && completedKm >= 0.0)
        require(volumeCompletionRatio.isFinite() && volumeCompletionRatio >= 0.0)
        require(plannedSessions >= 0 && completedSessions >= 0 && consecutiveMissedSessions >= 0)
        require(rolling28dWeeklyAvgKm.isFinite() && rolling28dWeeklyAvgKm >= 0.0)
        require(last7dKm.isFinite() && last7dKm >= 0.0)
        require(acuteToBaselineRatio == null || (acuteToBaselineRatio.isFinite() && acuteToBaselineRatio >= 0.0))
        require(longest30dKm.isFinite() && longest30dKm >= 0.0)
    }

    fun encode(): JSONObject = JSONObject()
        .put("windowDays", windowDays)
        .put("plannedKm", plannedKm)
        .put("completedKm", completedKm)
        .put("volumeCompletionRatio", volumeCompletionRatio)
        .put("plannedSessions", plannedSessions)
        .put("completedSessions", completedSessions)
        .put("consecutiveMissedSessions", consecutiveMissedSessions)
        .put("rolling28dWeeklyAvgKm", rolling28dWeeklyAvgKm)
        .put("last7dKm", last7dKm)
        .put("acuteToBaselineRatio", acuteToBaselineRatio ?: JSONObject.NULL)
        .put("longest30dKm", longest30dKm)
        .put("exposureStatus", exposureStatus.name)
        .put("confidence", confidence.name)
        .put("reasonCodes", JSONArray(reasonCodes))

    companion object {
        fun decode(j: JSONObject): VolumeToleranceDiagnostic = VolumeToleranceDiagnostic(
            windowDays = j.getInt("windowDays"),
            plannedKm = j.getDouble("plannedKm"),
            completedKm = j.getDouble("completedKm"),
            volumeCompletionRatio = j.getDouble("volumeCompletionRatio"),
            plannedSessions = j.getInt("plannedSessions"),
            completedSessions = j.getInt("completedSessions"),
            consecutiveMissedSessions = j.getInt("consecutiveMissedSessions"),
            rolling28dWeeklyAvgKm = j.getDouble("rolling28dWeeklyAvgKm"),
            last7dKm = j.getDouble("last7dKm"),
            acuteToBaselineRatio = j.nullDouble("acuteToBaselineRatio"),
            longest30dKm = j.getDouble("longest30dKm"),
            exposureStatus = ExposureStatus.valueOf(j.getString("exposureStatus")),
            confidence = DiagnosticConfidence.valueOf(j.getString("confidence")),
            reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList()
        )
    }
}

internal data class QualityExecutionDiagnostic(
    val evaluatedQualityCount: Int,
    val verifiedHitCount: Int,
    val verifiedHitRatePct: Double?,
    val volumeOnlySubstitutionCount: Int,
    val tooSlowCount: Int,
    val tooFastCount: Int,
    val partialCount: Int,
    val paceTrend: LongitudinalPaceTrend,
    val avgWorkPaceDeltaSecPerKm: Double?,
    val confidence: DiagnosticConfidence,
    val reasonCodes: List<String>
) {
    init {
        require(evaluatedQualityCount >= 0 && verifiedHitCount >= 0)
        require(verifiedHitCount <= evaluatedQualityCount)
        require(verifiedHitRatePct == null || (verifiedHitRatePct.isFinite() && verifiedHitRatePct in 0.0..100.0))
        require(volumeOnlySubstitutionCount >= 0 && tooSlowCount >= 0 && tooFastCount >= 0 && partialCount >= 0)
        require(avgWorkPaceDeltaSecPerKm == null || avgWorkPaceDeltaSecPerKm.isFinite())
    }

    fun encode(): JSONObject = JSONObject()
        .put("evaluatedQualityCount", evaluatedQualityCount)
        .put("verifiedHitCount", verifiedHitCount)
        .put("verifiedHitRatePct", verifiedHitRatePct ?: JSONObject.NULL)
        .put("volumeOnlySubstitutionCount", volumeOnlySubstitutionCount)
        .put("tooSlowCount", tooSlowCount)
        .put("tooFastCount", tooFastCount)
        .put("partialCount", partialCount)
        .put("paceTrend", paceTrend.name)
        .put("avgWorkPaceDeltaSecPerKm", avgWorkPaceDeltaSecPerKm ?: JSONObject.NULL)
        .put("confidence", confidence.name)
        .put("reasonCodes", JSONArray(reasonCodes))

    companion object {
        fun decode(j: JSONObject): QualityExecutionDiagnostic = QualityExecutionDiagnostic(
            evaluatedQualityCount = j.getInt("evaluatedQualityCount"),
            verifiedHitCount = j.getInt("verifiedHitCount"),
            verifiedHitRatePct = j.nullDouble("verifiedHitRatePct"),
            volumeOnlySubstitutionCount = j.getInt("volumeOnlySubstitutionCount"),
            tooSlowCount = j.getInt("tooSlowCount"),
            tooFastCount = j.getInt("tooFastCount"),
            partialCount = j.getInt("partialCount"),
            paceTrend = LongitudinalPaceTrend.valueOf(j.getString("paceTrend")),
            avgWorkPaceDeltaSecPerKm = j.nullDouble("avgWorkPaceDeltaSecPerKm"),
            confidence = DiagnosticConfidence.valueOf(j.getString("confidence")),
            reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList()
        )
    }
}

internal data class EasyDisciplineDiagnostic(
    val evaluatedEasyCount: Int,
    val tooFastCount: Int,
    val grayZoneCreepRatePct: Double?,
    val avgOvershootSecPerKm: Double?,
    val hiddenFatigueRisk: Boolean,
    val confidence: DiagnosticConfidence,
    val reasonCodes: List<String>
) {
    init {
        require(evaluatedEasyCount >= 0 && tooFastCount >= 0 && tooFastCount <= evaluatedEasyCount)
        require(grayZoneCreepRatePct == null || (grayZoneCreepRatePct.isFinite() && grayZoneCreepRatePct in 0.0..100.0))
        require(avgOvershootSecPerKm == null || (avgOvershootSecPerKm.isFinite() && avgOvershootSecPerKm >= 0.0))
    }

    fun encode(): JSONObject = JSONObject()
        .put("evaluatedEasyCount", evaluatedEasyCount)
        .put("tooFastCount", tooFastCount)
        .put("grayZoneCreepRatePct", grayZoneCreepRatePct ?: JSONObject.NULL)
        .put("avgOvershootSecPerKm", avgOvershootSecPerKm ?: JSONObject.NULL)
        .put("hiddenFatigueRisk", hiddenFatigueRisk)
        .put("confidence", confidence.name)
        .put("reasonCodes", JSONArray(reasonCodes))

    companion object {
        fun decode(j: JSONObject): EasyDisciplineDiagnostic = EasyDisciplineDiagnostic(
            evaluatedEasyCount = j.getInt("evaluatedEasyCount"),
            tooFastCount = j.getInt("tooFastCount"),
            grayZoneCreepRatePct = j.nullDouble("grayZoneCreepRatePct"),
            avgOvershootSecPerKm = j.nullDouble("avgOvershootSecPerKm"),
            hiddenFatigueRisk = j.getBoolean("hiddenFatigueRisk"),
            confidence = DiagnosticConfidence.valueOf(j.getString("confidence")),
            reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList()
        )
    }
}

internal data class AerobicEfficiencyDiagnostic(
    val hrDataAvailable: Boolean,
    val eligibleRunCount: Int,
    val earlyWindowEf: Double?,
    val recentWindowEf: Double?,
    val efChangePct: Double?,
    val medianDecouplingPct: Double?,
    val trend: AerobicEfficiencyTrend,
    val confidence: DiagnosticConfidence,
    val reasonCodes: List<String>
) {
    init {
        require(eligibleRunCount >= 0)
        if (!hrDataAvailable) {
            require(earlyWindowEf == null && recentWindowEf == null && efChangePct == null && medianDecouplingPct == null) {
                "HR efficiency metrics must remain strictly null when HR data is unavailable"
            }
            require(trend == AerobicEfficiencyTrend.UNAVAILABLE)
        } else {
            require(earlyWindowEf == null || (earlyWindowEf.isFinite() && earlyWindowEf > 0.0))
            require(recentWindowEf == null || (recentWindowEf.isFinite() && recentWindowEf > 0.0))
            require(efChangePct == null || efChangePct.isFinite())
            require(medianDecouplingPct == null || medianDecouplingPct.isFinite())
        }
    }

    fun encode(): JSONObject = JSONObject()
        .put("hrDataAvailable", hrDataAvailable)
        .put("eligibleRunCount", eligibleRunCount)
        .put("earlyWindowEf", earlyWindowEf ?: JSONObject.NULL)
        .put("recentWindowEf", recentWindowEf ?: JSONObject.NULL)
        .put("efChangePct", efChangePct ?: JSONObject.NULL)
        .put("medianDecouplingPct", medianDecouplingPct ?: JSONObject.NULL)
        .put("trend", trend.name)
        .put("confidence", confidence.name)
        .put("reasonCodes", JSONArray(reasonCodes))

    companion object {
        fun decode(j: JSONObject): AerobicEfficiencyDiagnostic = AerobicEfficiencyDiagnostic(
            hrDataAvailable = j.getBoolean("hrDataAvailable"),
            eligibleRunCount = j.getInt("eligibleRunCount"),
            earlyWindowEf = j.nullDouble("earlyWindowEf"),
            recentWindowEf = j.nullDouble("recentWindowEf"),
            efChangePct = j.nullDouble("efChangePct"),
            medianDecouplingPct = j.nullDouble("medianDecouplingPct"),
            trend = AerobicEfficiencyTrend.valueOf(j.getString("trend")),
            confidence = DiagnosticConfidence.valueOf(j.getString("confidence")),
            reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList()
        )
    }
}

internal data class DiagnosticInsight(
    val code: String,
    val severity: DiagnosticInsightSeverity,
    val confidence: DiagnosticConfidence,
    val message: String
) {
    init {
        require(code.isNotBlank() && message.isNotBlank())
    }

    fun encode(): JSONObject = JSONObject()
        .put("code", code)
        .put("severity", severity.name)
        .put("confidence", confidence.name)
        .put("message", message)

    companion object {
        fun decode(j: JSONObject): DiagnosticInsight = DiagnosticInsight(
            code = j.getString("code"),
            severity = DiagnosticInsightSeverity.valueOf(j.getString("severity")),
            confidence = DiagnosticConfidence.valueOf(j.getString("confidence")),
            message = j.getString("message")
        )
    }
}

internal data class AthleteDiagnosticSnapshot(
    val schemaVersion: Int = ENGINE15_DIAGNOSTIC_SCHEMA,
    val planId: String,
    val asOfDate: LocalDate,
    val overallConfidence: DiagnosticConfidence,
    val pendingConfirmationCount: Int,
    val volumeTolerance: VolumeToleranceDiagnostic,
    val qualityExecution: QualityExecutionDiagnostic,
    val easyDiscipline: EasyDisciplineDiagnostic,
    val aerobicEfficiency: AerobicEfficiencyDiagnostic,
    val recalibrationAdvisory: RecalibrationAdvisory,
    val insights: List<DiagnosticInsight>,
    val reasonCodes: List<String>,
    val evaluatedAtEpochMs: Long = 0L
) {
    init {
        require(schemaVersion == ENGINE15_DIAGNOSTIC_SCHEMA)
        require(planId.isNotBlank())
        require(pendingConfirmationCount >= 0)
        require(insights.size <= 4)
        require(evaluatedAtEpochMs >= 0L)
    }

    fun encode(owner: String? = null): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("planId", planId)
        .put("asOfDate", asOfDate.toString())
        .put("overallConfidence", overallConfidence.name)
        .put("pendingConfirmationCount", pendingConfirmationCount)
        .put("volumeTolerance", volumeTolerance.encode())
        .put("qualityExecution", qualityExecution.encode())
        .put("easyDiscipline", easyDiscipline.encode())
        .put("aerobicEfficiency", aerobicEfficiency.encode())
        .put("recalibrationAdvisory", recalibrationAdvisory.name)
        .put("insights", JSONArray(insights.map { it.encode() }))
        .put("reasonCodes", JSONArray(reasonCodes))
        .put("evaluatedAtEpochMs", evaluatedAtEpochMs)
        .apply { if (owner != null) put("user_id", owner) }

    companion object {
        fun decode(j: JSONObject, expectedOwner: String? = null): AthleteDiagnosticSnapshot {
            require(j.getInt("schemaVersion") == ENGINE15_DIAGNOSTIC_SCHEMA) {
                "Unsupported diagnostic schema"
            }
            if (expectedOwner != null) {
                require(j.optString("user_id") == expectedOwner) {
                    "Diagnostic owner does not match"
                }
            }
            return AthleteDiagnosticSnapshot(
                schemaVersion = j.getInt("schemaVersion"),
                planId = j.getString("planId"),
                asOfDate = LocalDate.parse(j.getString("asOfDate")),
                overallConfidence = DiagnosticConfidence.valueOf(j.getString("overallConfidence")),
                pendingConfirmationCount = j.getInt("pendingConfirmationCount"),
                volumeTolerance = VolumeToleranceDiagnostic.decode(j.getJSONObject("volumeTolerance")),
                qualityExecution = QualityExecutionDiagnostic.decode(j.getJSONObject("qualityExecution")),
                easyDiscipline = EasyDisciplineDiagnostic.decode(j.getJSONObject("easyDiscipline")),
                aerobicEfficiency = AerobicEfficiencyDiagnostic.decode(j.getJSONObject("aerobicEfficiency")),
                recalibrationAdvisory = RecalibrationAdvisory.valueOf(j.getString("recalibrationAdvisory")),
                insights = j.optJSONArray("insights")?.objects()?.map(DiagnosticInsight::decode) ?: emptyList(),
                reasonCodes = j.optJSONArray("reasonCodes")?.strings() ?: emptyList(),
                evaluatedAtEpochMs = j.optLong("evaluatedAtEpochMs", 0L)
            )
        }
    }
}

package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

internal enum class BiometricMetricStatus {
    NORMAL,
    ELEVATED_STRAIN,
    SUPPRESSED_STRAIN,
    BUILDING_BASELINE,
    NO_DATA
}

internal data class RhrBiometricSummary(
    val latestDate: LocalDate?,
    val latestBpm: Double?,
    val baseline28dMeanBpm: Double?,
    val baseline28dCount: Int,
    val deltaFromBaselineBpm: Double?,
    val consecutiveElevatedDays: Int,
    val elevatedFlag: Boolean,
    val sustainedElevatedFlag: Boolean,
    val status: BiometricMetricStatus,
    val series7d: List<Pair<LocalDate, Double?>>,
    val minHrBpm: Double? = null,
    val maxHrBpm: Double? = null
)

internal data class HrvBiometricSummary(
    val latestDate: LocalDate?,
    val latestMs: Double?,
    val baseline28dMeanMs: Double?,
    val baseline28dCount: Int,
    val valid7dCount: Int,
    val weeklyAvgMs: Double?,
    val garminStatus: String?,
    val garminBaselineLowMs: Double?,
    val garminBaselineHighMs: Double?,
    val evaluation: HrvEvaluationResult,
    val suppressedFlag: Boolean,
    val status: BiometricMetricStatus,
    val series7d: List<Pair<LocalDate, Double?>>
)

internal data class SleepBiometricSummary(
    val latestDate: LocalDate?,
    val latestHours: Double?,
    val average7dHours: Double?,
    val baseline28dMedianHours: Double?,
    val rolling3dMeanHours: Double?,
    val sleepScore: Int?,
    val sleepQualifier: String?,
    val deepMinutes: Int?,
    val lightMinutes: Int?,
    val remMinutes: Int?,
    val awakeMinutes: Int?,
    val severeAcuteLossFlag: Boolean,
    val cumulativeDebtFlag: Boolean,
    val status: BiometricMetricStatus,
    val series7d: List<Pair<LocalDate, Double?>>
)

internal data class BiometricReadinessSnapshot(
    val asOfDate: LocalDate,
    val rhr: RhrBiometricSummary,
    val hrv: HrvBiometricSummary,
    val sleep: SleepBiometricSummary,
    val strainSignalCount: Int,
    val sustainedAutonomicStrain: Boolean
) {
    val hasAnyData: Boolean
        get() = rhr.latestBpm != null || hrv.latestMs != null || sleep.latestHours != null
}

/**
 * Pure, stateless biometric readiness evaluator over persisted [WellnessRecord] rows.
 *
 * Uses `MOD-21` standardized ln(rMSSD) via [Engine19ExecutionAndDifferentialEngine.evaluateStandardizedHrv],
 * individualized 28-day RHR baseline, and acute + 3-night sleep debt.
 * Never fabricates missing values or emits opaque 0-100 composite scores (`EXCL-D`).
 */
internal object BiometricReadinessEvaluator {

    fun evaluate(
        records: List<WellnessRecord>,
        asOfDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BiometricReadinessSnapshot {
        val dedupedByTypeAndDate = records
            .mapNotNull { record ->
                val day = record.recordedAt.atZone(zoneId).toLocalDate()
                if (day.isAfter(asOfDate)) null else (record.type to day) to record
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareBy<WellnessRecord> { it.recordedAt }
                        .thenBy { it.source == "garmin" }
                ).last()
            }

        val rhrByDate = dedupedByTypeAndDate
            .filterKeys { it.first == "resting_hr" }
            .mapKeys { it.key.second }
        val hrvByDate = dedupedByTypeAndDate
            .filterKeys { it.first == "hrv" }
            .mapKeys { it.key.second }
        val sleepByDate = dedupedByTypeAndDate
            .filterKeys { it.first == "sleep_duration" }
            .mapKeys { it.key.second }

        val rhrSummary = evaluateRhr(rhrByDate, asOfDate)
        val hrvSummary = evaluateHrv(hrvByDate, asOfDate)
        val sleepSummary = evaluateSleep(sleepByDate, asOfDate)

        val strainSignalCount = listOf(
            hrvSummary.suppressedFlag,
            rhrSummary.elevatedFlag,
            sleepSummary.cumulativeDebtFlag
        ).count { it }

        val sustainedAutonomicStrain = strainSignalCount >= 2 ||
            (hrvSummary.suppressedFlag && sleepSummary.severeAcuteLossFlag) ||
            rhrSummary.sustainedElevatedFlag

        return BiometricReadinessSnapshot(
            asOfDate = asOfDate,
            rhr = rhrSummary,
            hrv = hrvSummary,
            sleep = sleepSummary,
            strainSignalCount = strainSignalCount,
            sustainedAutonomicStrain = sustainedAutonomicStrain
        )
    }

    private fun evaluateRhr(
        byDate: Map<LocalDate, WellnessRecord>,
        asOfDate: LocalDate
    ): RhrBiometricSummary {
        val series7d = trailing7Days(byDate, asOfDate)
        val latestEntry = byDate.entries
            .filter { !it.key.isAfter(asOfDate) && it.key >= asOfDate.minusDays(6) }
            .maxByOrNull { it.key }
            ?: byDate.entries.maxByOrNull { it.key }

        if (latestEntry == null) {
            return RhrBiometricSummary(
                latestDate = null,
                latestBpm = null,
                baseline28dMeanBpm = null,
                baseline28dCount = 0,
                deltaFromBaselineBpm = null,
                consecutiveElevatedDays = 0,
                elevatedFlag = false,
                sustainedElevatedFlag = false,
                status = BiometricMetricStatus.NO_DATA,
                series7d = series7d
            )
        }

        val latestDate = latestEntry.key
        val latestVal = latestEntry.value.value
        val baselineValues = byDate
            .filterKeys { it in latestDate.minusDays(28)..latestDate.minusDays(1) }
            .values
            .map { it.value }
        val baselineCount = baselineValues.size
        val mean28 = if (baselineCount >= 7) baselineValues.average() else null
        val sd28 = if (baselineCount >= 7 && mean28 != null) {
            val variance = baselineValues.sumOf { (it - mean28) * (it - mean28) } / (baselineCount - 1)
            sqrt(variance.coerceAtLeast(0.0))
        } else null

        val delta = mean28?.let { latestVal - it }
        fun isElevated(bpm: Double): Boolean {
            if (mean28 == null) return false
            val d = bpm - mean28
            return d >= 5.0 || (sd28 != null && sd28 >= 1.0 && d >= 4.0 && d >= 1.5 * sd28)
        }

        val elevated = isElevated(latestVal)
        var consecutive = 0
        if (elevated) {
            var cursor = latestDate
            while (true) {
                val rec = byDate[cursor] ?: break
                if (!isElevated(rec.value)) break
                consecutive++
                cursor = cursor.minusDays(1)
            }
        }
        val sustained = consecutive >= 2
        val status = when {
            baselineCount < 7 -> BiometricMetricStatus.BUILDING_BASELINE
            elevated -> BiometricMetricStatus.ELEVATED_STRAIN
            else -> BiometricMetricStatus.NORMAL
        }
        val meta = latestEntry.value.metadata
        return RhrBiometricSummary(
            latestDate = latestDate,
            latestBpm = latestVal,
            baseline28dMeanBpm = mean28,
            baseline28dCount = baselineCount,
            deltaFromBaselineBpm = delta,
            consecutiveElevatedDays = consecutive,
            elevatedFlag = elevated,
            sustainedElevatedFlag = sustained,
            status = status,
            series7d = series7d,
            minHrBpm = meta?.optDouble("minHrBpm", Double.NaN)?.takeIf { it.isFinite() },
            maxHrBpm = meta?.optDouble("maxHrBpm", Double.NaN)?.takeIf { it.isFinite() }
        )
    }

    private fun evaluateHrv(
        byDate: Map<LocalDate, WellnessRecord>,
        asOfDate: LocalDate
    ): HrvBiometricSummary {
        val series7d = trailing7Days(byDate, asOfDate)
        val latestEntry = byDate.entries
            .filter { !it.key.isAfter(asOfDate) && it.key >= asOfDate.minusDays(6) }
            .maxByOrNull { it.key }
            ?: byDate.entries.maxByOrNull { it.key }

        val readings = byDate.entries
            .filter { it.key in asOfDate.minusDays(27)..asOfDate }
            .map { (day, rec) ->
                HrvReading(
                    date = day,
                    valueMs = rec.value,
                    protocol = HrvProtocol.NOCTURNAL_SWS,
                    metricType = HrvMetricType.RMSSD_MS
                )
            }
        val eval = Engine19ExecutionAndDifferentialEngine.evaluateStandardizedHrv(readings, asOfDate)
        val valid28 = byDate.filterKeys { it in asOfDate.minusDays(27)..asOfDate }.values.toList()
        val valid7 = byDate.filterKeys { it in asOfDate.minusDays(6)..asOfDate }.values.toList()
        val mean28 = if (valid28.isNotEmpty()) valid28.map { it.value }.average() else null

        val suppressed = eval is HrvEvaluationResult.Valid && eval.belowSmallestWorthwhileChange
        val status = when {
            latestEntry == null -> BiometricMetricStatus.NO_DATA
            eval is HrvEvaluationResult.InsufficientOrInvalid -> BiometricMetricStatus.BUILDING_BASELINE
            suppressed -> BiometricMetricStatus.SUPPRESSED_STRAIN
            else -> BiometricMetricStatus.NORMAL
        }
        val meta = latestEntry?.value?.metadata
        return HrvBiometricSummary(
            latestDate = latestEntry?.key,
            latestMs = latestEntry?.value?.value,
            baseline28dMeanMs = mean28,
            baseline28dCount = valid28.size,
            valid7dCount = valid7.size,
            weeklyAvgMs = meta?.optDouble("weeklyAvgMs", Double.NaN)?.takeIf { it.isFinite() },
            garminStatus = meta?.optString("hrvStatus")?.takeIf { it.isNotBlank() && !it.equals("null", true) },
            garminBaselineLowMs = meta?.optDouble("baselineLowMs", Double.NaN)?.takeIf { it.isFinite() },
            garminBaselineHighMs = meta?.optDouble("baselineHighMs", Double.NaN)?.takeIf { it.isFinite() },
            evaluation = eval,
            suppressedFlag = suppressed,
            status = status,
            series7d = series7d
        )
    }

    private fun evaluateSleep(
        byDate: Map<LocalDate, WellnessRecord>,
        asOfDate: LocalDate
    ): SleepBiometricSummary {
        val series7d = trailing7Days(byDate, asOfDate)
        val latestEntry = byDate.entries
            .filter { !it.key.isAfter(asOfDate) && it.key >= asOfDate.minusDays(6) }
            .maxByOrNull { it.key }
            ?: byDate.entries.maxByOrNull { it.key }

        if (latestEntry == null) {
            return SleepBiometricSummary(
                latestDate = null,
                latestHours = null,
                average7dHours = null,
                baseline28dMedianHours = null,
                rolling3dMeanHours = null,
                sleepScore = null,
                sleepQualifier = null,
                deepMinutes = null,
                lightMinutes = null,
                remMinutes = null,
                awakeMinutes = null,
                severeAcuteLossFlag = false,
                cumulativeDebtFlag = false,
                status = BiometricMetricStatus.NO_DATA,
                series7d = series7d
            )
        }

        val latestDate = latestEntry.key
        val latestHours = latestEntry.value.value
        val window7 = byDate.filterKeys { it in asOfDate.minusDays(6)..asOfDate }.values.map { it.value }
        val window3 = byDate.filterKeys { it in latestDate.minusDays(2)..latestDate }.values.map { it.value }
        val window28 = byDate.filterKeys { it in latestDate.minusDays(28)..latestDate.minusDays(1) }
            .values.map { it.value }.sorted()

        val median28 = if (window28.size >= 7) {
            val mid = window28.size / 2
            if (window28.size % 2 == 0) (window28[mid - 1] + window28[mid]) / 2.0 else window28[mid]
        } else null
        val refHours = median28 ?: 7.0
        val rolling3dMean = if (window3.size >= 2) window3.average() else null
        val severeAcute = latestHours < 5.5
        val cumulativeDebt = rolling3dMean != null &&
            (rolling3dMean < 6.25 || rolling3dMean <= refHours - 1.25)

        val status = when {
            cumulativeDebt || severeAcute -> BiometricMetricStatus.SUPPRESSED_STRAIN
            else -> BiometricMetricStatus.NORMAL
        }
        val meta = latestEntry.value.metadata
        fun optMin(key: String): Int? {
            if (meta == null || !meta.has(key) || meta.isNull(key)) return null
            val sec = meta.optInt(key, -1)
            return if (sec >= 0) sec / 60 else null
        }

        return SleepBiometricSummary(
            latestDate = latestDate,
            latestHours = latestHours,
            average7dHours = if (window7.isNotEmpty()) window7.average() else null,
            baseline28dMedianHours = median28,
            rolling3dMeanHours = rolling3dMean,
            sleepScore = meta?.optInt("sleepScore", -1)?.takeIf { it in 1..100 },
            sleepQualifier = meta?.optString("sleepQualifier")?.takeIf { it.isNotBlank() && !it.equals("null", true) },
            deepMinutes = optMin("deepSeconds"),
            lightMinutes = optMin("lightSeconds"),
            remMinutes = optMin("remSeconds"),
            awakeMinutes = optMin("awakeSeconds"),
            severeAcuteLossFlag = severeAcute,
            cumulativeDebtFlag = cumulativeDebt,
            status = status,
            series7d = series7d
        )
    }

    private fun trailing7Days(
        byDate: Map<LocalDate, WellnessRecord>,
        asOfDate: LocalDate
    ): List<Pair<LocalDate, Double?>> =
        (6L downTo 0L).map { offset ->
            val d = asOfDate.minusDays(offset)
            d to byDate[d]?.value
        }
}
